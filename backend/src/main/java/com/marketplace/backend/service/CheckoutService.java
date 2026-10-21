package com.marketplace.backend.service;

import com.marketplace.backend.dto.request.CheckoutRequest;
import com.marketplace.backend.dto.response.CartItemResponse;
import com.marketplace.backend.dto.response.CheckoutQuote;
import com.marketplace.backend.dto.response.CheckoutQuote.ShopQuote;
import com.marketplace.backend.dto.response.OrderResponse;
import com.marketplace.backend.dto.response.VoucherResponse;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.*;
import com.marketplace.backend.security.CurrentUser;
import com.marketplace.backend.service.ShippingService.ShippingOption;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Multi-shop checkout. The same pricing code serves both the live preview on
 * the checkout page and the actual order placement, so what the buyer sees is
 * exactly what they're charged:
 *
 *   per shop:  subtotal - shop voucher - share of platform voucher
 *              + shipping fee - shipping discount (freeship products, shipping vouchers)
 *
 * A platform ("Shoppi") voucher applies to the whole checkout and is split
 * across the shops' orders in proportion to their amounts.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class CheckoutService {

    private static final Set<String> WALLETS = Set.of("MOMO", "ZALOPAY", "VNPAY");

    private final GhnService ghnService;
    private final VnpayService vnpayService;
    private final CartItemRepository cartItemRepository;
    private final AddressRepository addressRepository;
    private final VoucherRepository voucherRepository;
    private final VoucherService voucherService;
    private final ShippingService shippingService;
    private final StockService stockService;
    private final ComboService comboService;
    private final OrderItemRepository orderItemRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final TrackingService trackingService;

    // everything the pricing step works out, kept for placeOrders()
    private record Priced(CheckoutQuote quote, List<List<CartItem>> items, Address address,
                          List<Voucher> shopVouchers, Voucher platformVoucher) {}

    @Transactional(readOnly = true)
    public CheckoutQuote preview(CheckoutRequest req) {
        return price(req, false).quote();
    }

    public Map<String, Object> placeOrders(CheckoutRequest req) {
        if (req.getAddressId() == null) throw bad("Vui lòng chọn địa chỉ nhận hàng");
        if (req.getPaymentMethod() == null || req.getPaymentMethod() == PaymentMethod.MOCK_ONLINE) {
            throw bad("Vui lòng chọn phương thức thanh toán");
        }
        String provider = null;
        if (req.getPaymentMethod() == PaymentMethod.E_WALLET) {
            provider = req.getPaymentProvider() == null ? null : req.getPaymentProvider().toUpperCase(Locale.ROOT);
            if (!WALLETS.contains(provider)) throw bad("Vui lòng chọn ví điện tử (MoMo, ZaloPay hoặc VNPay)");
        }
        if (req.getPaymentMethod() == PaymentMethod.VNPAY) {
            if (!vnpayService.available()) throw bad("Cổng thanh toán VNPay đang tạm ngưng, vui lòng chọn phương thức khác");
            provider = "VNPAY";
        }

        Priced p = price(req, true);
        boolean payNow = req.getPaymentMethod() != PaymentMethod.COD;
        String checkoutCode = "CK" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmmss"))
                + (new Random().nextInt(900) + 100);
        User buyer = userRepository.getReferenceById(CurrentUser.id());

        List<OrderResponse> created = new ArrayList<>();
        for (int i = 0; i < p.quote().getShops().size(); i++) {
            ShopQuote sq = p.quote().getShops().get(i);
            List<CartItem> items = p.items().get(i);
            ShippingOption ship = sq.getShipping();

            Order order = Order.builder()
                    .code(orderCode())
                    .checkoutCode(checkoutCode)
                    .buyer(buyer)
                    .shop(items.get(0).getProduct().getShop())
                    .subtotal(sq.getSubtotal())
                    .shopDiscount(sq.getShopDiscount())
                    .platformDiscount(sq.getPlatformDiscount())
                    .comboDiscount(sq.getComboDiscount())
                    .shopVoucherDiscount(sq.getShopVoucherDiscount())
                    .discount(sq.getShopDiscount().add(sq.getPlatformDiscount()).add(sq.getComboDiscount()))
                    .shippingFee(sq.getShippingFee())
                    .shippingDiscount(sq.getShippingDiscount())
                    .total(sq.getTotal())
                    .shippingMethod(ship.getMethod())
                    .carrier(ship.getCarrier())
                    .estimatedDeliveryFrom(ship.getEtaFrom())
                    .estimatedDeliveryTo(ship.getEtaTo())
                    .note(blankToNull(req.getShops().get(i).getNote()))
                    // online payments wait for the (simulated) gateway before the seller sees them as new
                    .status(payNow ? OrderStatus.AWAITING_PAYMENT : OrderStatus.PENDING)
                    .shippingAddressSnapshot(formatAddress(p.address()))
                    .shippingProvince(p.address().getProvince())
                    .recipientName(p.address().getRecipientName())
                    .recipientPhone(p.address().getPhone())
                    .shippingAddressLine(p.address().getAddressLine())
                    .shippingWard(blankToNull(p.address().getWard()))
                    .shippingDistrict(blankToNull(p.address().getDistrict()))
                    .voucher(p.shopVouchers().get(i))
                    // every order of the checkout points at it; usage is counted per checkout
                    .platformVoucher(p.platformVoucher())
                    .build();

            for (CartItem item : items) {
                Product product = item.getProduct();
                ProductVariant variant = item.getVariant();
                // atomic: fails here if someone else bought the last items since the cart was checked
                stockService.take(product, variant, item.getQuantity(), order.getCode());
                // flash price: take units from the flash sale quota too (same idea, also atomic)
                boolean flash = product.isFlashSaleActive();
                if (flash && productRepository.takeFlashSale(product.getId(), item.getQuantity()) == 0) {
                    throw bad("Suất Flash Sale của \"" + product.getName() + "\" vừa hết, giá đã thay đổi. Vui lòng tải lại trang thanh toán");
                }

                order.getItems().add(OrderItem.builder()
                        .order(order)
                        .product(product)
                        .productNameSnapshot(product.getName())
                        .variantNameSnapshot(variant != null ? variant.getName() : null)
                        .variantId(variant != null ? variant.getId() : null)
                        .priceAtPurchase(product.unitPriceFor(variant))
                        .quantity(item.getQuantity())
                        .flashSale(flash)
                        .build());
            }
            order.setPayment(Payment.builder()
                    .order(order)
                    .method(req.getPaymentMethod())
                    .provider(provider)
                    .status(PaymentStatus.PENDING)
                    .amount(sq.getTotal())
                    .build());

            Voucher shopVoucher = p.shopVouchers().get(i);
            if (shopVoucher != null) shopVoucher.setUsedCount(shopVoucher.getUsedCount() + 1);

            Order saved = orderRepository.save(order);
            trackingService.add(saved, "PLACED", payNow ? "Đơn hàng đã được đặt, đang chờ thanh toán"
                    : "Đơn hàng đã được đặt, chờ Người bán xác nhận", null);
            created.add(OrderResponse.from(saved));
            cartItemRepository.deleteAll(items);
        }
        if (p.platformVoucher() != null) {
            p.platformVoucher().setUsedCount(p.platformVoucher().getUsedCount() + 1); // once per checkout
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("checkoutCode", checkoutCode);
        result.put("paymentRequired", payNow);
        result.put("amount", p.quote().getTotal());
        result.put("orders", created);
        return result;
    }

    // ==== pricing ====

    private Priced price(CheckoutRequest req, boolean strict) {
        Long buyerId = CurrentUser.id();
        Address address = null;
        if (req.getAddressId() != null) {
            address = addressRepository.findById(req.getAddressId())
                    .filter(a -> a.getUser().getId().equals(buyerId))
                    .orElseThrow(() -> bad("Địa chỉ không tồn tại"));
        }

        CheckoutQuote quote = new CheckoutQuote();
        List<List<CartItem>> groups = new ArrayList<>();
        List<Voucher> shopVouchers = new ArrayList<>();
        Set<Long> seenShops = new HashSet<>();

        for (CheckoutRequest.ShopPart part : req.getShops()) {
            List<CartItem> items = loadItems(part.getCartItemIds(), buyerId);
            Shop shop = items.get(0).getProduct().getShop();
            if (items.stream().anyMatch(i -> !i.getProduct().getShop().getId().equals(shop.getId()))) {
                throw bad("Mỗi nhóm đặt hàng chỉ được gồm sản phẩm của một shop");
            }
            if (!seenShops.add(shop.getId())) throw bad("Shop " + shop.getName() + " bị lặp trong đơn hàng");

            ShopQuote sq = new ShopQuote();
            sq.setShopId(shop.getId());
            sq.setShopName(shop.getName());
            sq.setShopProvince(shop.getProvince());
            Map<Long, BigDecimal> lineTotals = new HashMap<>();
            for (CartItem item : items) {
                CartItemResponse line = CartItemResponse.from(item);
                sq.getItems().add(line);
                BigDecimal lineTotal = line.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
                lineTotals.put(item.getId(), lineTotal);
                sq.setSubtotal(sq.getSubtotal().add(lineTotal));
                sq.setQuantity(sq.getQuantity() + item.getQuantity());
            }
            checkFlashSaleLimits(items, buyerId, sq, strict);

            // combo deals (buy more, save more)
            ComboService.Result combo = comboService.apply(shop.getId(), items, lineTotals);
            sq.setComboDiscount(combo.total());
            sq.setCombos(combo.notes());

            // shipping
            // limited to the shop's carriers, dates pushed back by its packing time (shop policy)
            List<ShippingOption> options = shippingService.options(shop.getProvince(),
                    address != null ? address.getProvince() : null, sq.getQuantity(),
                    ShippingService.carrierCodes(shop.getCarriers()), shop.getHandlingDays() != null ? shop.getHandlingDays() : 1);
            // GHN connected: its parcels are priced by GHN's own API (falls back to the table if GHN can't quote)
            options = ghnService.adjustOptions(options, shop, address, sq.getQuantity(), sq.getSubtotal());
            sq.setShippingOptions(options);
            ShippingOption chosen = options.stream()
                    .filter(o -> o.getCode().equals(part.getShippingOption())).findFirst()
                    .orElse(options.get(0));
            if (strict && part.getShippingOption() != null && !chosen.getCode().equals(part.getShippingOption())) {
                throw bad("Phương thức vận chuyển không khả dụng cho shop " + shop.getName());
            }
            sq.setShipping(chosen);
            sq.setShippingFee(chosen.getFee());
            // freeship products, or an order above the shop's free-shipping threshold:
            // the shop covers up to the cheapest economy fee
            sq.setFreeShippingProducts(items.stream().allMatch(i -> i.getProduct().isFreeShipping()));
            BigDecimal threshold = shop.getFreeShipMinOrder();
            if (threshold != null && threshold.signum() > 0) {
                sq.setFreeShipMinOrder(threshold);
                sq.setFreeShippingByShop(sq.getSubtotal().compareTo(threshold) >= 0);
            }
            if (sq.isFreeShippingProducts() || sq.isFreeShippingByShop()) {
                sq.setShippingDiscount(chosen.getFee().min(shippingService.economyFee(options)));
            }

            // shop voucher
            Voucher shopVoucher = null;
            if (!isBlank(part.getVoucherCode())) {
                Voucher v = voucherRepository.findByCodeIgnoreCase(part.getVoucherCode().trim()).orElse(null);
                String problem = v == null ? "Mã voucher không tồn tại"
                        : v.getScope() != VoucherScope.SHOP || !v.getShop().getId().equals(shop.getId())
                            ? "Voucher không áp dụng cho shop này"
                            : voucherService.problemWith(v, buyerId);
                // a voucher for some products only counts those lines (after their combo discount)
                BigDecimal eligible = BigDecimal.ZERO;
                if (problem == null) {
                    for (CartItem i : items) {
                        if (v.appliesTo(i.getProduct())) {
                            eligible = eligible.add(lineTotals.get(i.getId())
                                    .subtract(combo.discountByCartItem().getOrDefault(i.getId(), BigDecimal.ZERO)));
                        }
                    }
                    if (eligible.signum() == 0) problem = "Voucher không áp dụng cho sản phẩm bạn chọn";
                }
                if (problem == null && v.getMinOrderAmount() != null && eligible.compareTo(v.getMinOrderAmount()) < 0) {
                    problem = (v.getProducts().isEmpty() ? "Đơn hàng của shop" : "Sản phẩm áp dụng voucher")
                            + " chưa đạt giá trị tối thiểu " + vnd(v.getMinOrderAmount());
                }
                if (problem != null) {
                    if (strict) throw bad(problem + " (" + part.getVoucherCode().trim().toUpperCase(Locale.ROOT) + ")");
                    sq.setShopVoucherError(problem);
                } else {
                    shopVoucher = v;
                    sq.setShopVoucher(VoucherResponse.from(v));
                    if (v.getType() == VoucherType.SHIPPING) {
                        BigDecimal remaining = sq.getShippingFee().subtract(sq.getShippingDiscount());
                        BigDecimal off = v.getDiscountValue().min(remaining);
                        sq.setShippingDiscount(sq.getShippingDiscount().add(off));
                        sq.setShopVoucherDiscount(off);
                    } else {
                        sq.setShopDiscount(VoucherService.merchandiseDiscount(v, eligible));
                        sq.setShopVoucherDiscount(sq.getShopDiscount());
                    }
                }
            }

            quote.getShops().add(sq);
            groups.add(items);
            shopVouchers.add(shopVoucher);
        }

        Voucher platformVoucher = applyPlatformVoucher(req.getPlatformVoucherCode(), quote, groups, buyerId, strict);

        // totals
        for (ShopQuote sq : quote.getShops()) {
            BigDecimal total = sq.getSubtotal().subtract(sq.getComboDiscount()).subtract(sq.getShopDiscount())
                    .subtract(sq.getPlatformDiscount())
                    .add(sq.getShippingFee()).subtract(sq.getShippingDiscount()).max(BigDecimal.ZERO);
            sq.setTotal(total);
            quote.setMerchandiseTotal(quote.getMerchandiseTotal().add(sq.getSubtotal()));
            quote.setShippingFeeTotal(quote.getShippingFeeTotal().add(sq.getShippingFee()));
            quote.setShippingDiscountTotal(quote.getShippingDiscountTotal().add(sq.getShippingDiscount()));
            quote.setShopDiscountTotal(quote.getShopDiscountTotal().add(sq.getShopDiscount()));
            quote.setComboDiscountTotal(quote.getComboDiscountTotal().add(sq.getComboDiscount()));
            quote.setPlatformDiscountTotal(quote.getPlatformDiscountTotal().add(sq.getPlatformDiscount()));
            quote.setTotal(quote.getTotal().add(total));
            quote.setItemCount(quote.getItemCount() + sq.getQuantity());
        }
        return new Priced(quote, groups, address, shopVouchers, platformVoucher);
    }

    private Voucher applyPlatformVoucher(String code, CheckoutQuote quote, List<List<CartItem>> groups, Long buyerId, boolean strict) {
        if (isBlank(code)) return null;
        Voucher v = voucherRepository.findByCodeIgnoreCase(code.trim()).orElse(null);
        // merchandise left after shop vouchers, per shop; a voucher for some categories counts the share of
        // those products only
        List<BigDecimal> bases = new ArrayList<>();
        for (int i = 0; i < quote.getShops().size(); i++) {
            ShopQuote s = quote.getShops().get(i);
            BigDecimal left = s.getSubtotal().subtract(s.getComboDiscount()).subtract(s.getShopDiscount());
            if (v != null && !v.getCategoryIds().isEmpty() && s.getSubtotal().signum() > 0) {
                BigDecimal eligible = BigDecimal.ZERO;
                List<CartItem> items = groups.get(i);
                for (int j = 0; j < items.size(); j++) {
                    if (v.appliesToCategory(items.get(j).getProduct())) {
                        CartItemResponse line = s.getItems().get(j);
                        eligible = eligible.add(line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity())));
                    }
                }
                left = left.multiply(eligible).divide(s.getSubtotal(), 0, java.math.RoundingMode.DOWN);
            }
            bases.add(left.max(BigDecimal.ZERO));
        }
        BigDecimal base = bases.stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        String problem = v == null ? "Mã voucher không tồn tại"
                : v.getScope() != VoucherScope.PLATFORM ? "Đây là voucher của shop, hãy nhập ở mục Voucher của Shop"
                : voucherService.problemWith(v, buyerId);
        if (problem == null && !v.getCategoryIds().isEmpty() && base.signum() == 0) {
            problem = "Voucher không áp dụng cho ngành hàng của sản phẩm bạn chọn";
        }
        if (problem == null && v.getMinOrderAmount() != null && base.compareTo(v.getMinOrderAmount()) < 0) {
            problem = (v.getCategoryIds().isEmpty() ? "Đơn hàng" : "Sản phẩm thuộc ngành hàng áp dụng")
                    + " chưa đạt giá trị tối thiểu " + vnd(v.getMinOrderAmount());
        }
        if (problem != null) {
            if (strict) throw bad(problem + " (" + code.trim().toUpperCase(Locale.ROOT) + ")");
            quote.setPlatformVoucherError(problem);
            return null;
        }

        quote.setPlatformVoucher(VoucherResponse.from(v));
        if (v.getType() == VoucherType.SHIPPING) {
            List<BigDecimal> remaining = quote.getShops().stream()
                    .map(s -> s.getShippingFee().subtract(s.getShippingDiscount())).toList();
            BigDecimal total = v.getDiscountValue().min(remaining.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
            List<BigDecimal> shares = split(total, remaining);
            for (int i = 0; i < shares.size(); i++) {
                ShopQuote sq = quote.getShops().get(i);
                sq.setShippingDiscount(sq.getShippingDiscount().add(shares.get(i)));
            }
        } else {
            List<BigDecimal> shares = split(VoucherService.merchandiseDiscount(v, base), bases);
            for (int i = 0; i < shares.size(); i++) quote.getShops().get(i).setPlatformDiscount(shares.get(i));
        }
        return v;
    }

    /**
     * Flash sale limits: units left in the quota, and the per-buyer cap counted over the buyer's
     * orders since the flash sale started. Strict (placing the order) fails; the preview shows a notice.
     */
    private void checkFlashSaleLimits(List<CartItem> items, Long buyerId, ShopQuote sq, boolean strict) {
        Map<Long, Integer> qtyByProduct = new LinkedHashMap<>();
        Map<Long, Product> products = new HashMap<>();
        for (CartItem i : items) {
            if (!i.getProduct().isFlashSaleActive()) continue;
            qtyByProduct.merge(i.getProduct().getId(), i.getQuantity(), Integer::sum);
            products.put(i.getProduct().getId(), i.getProduct());
        }
        for (var e : qtyByProduct.entrySet()) {
            Product p = products.get(e.getKey());
            String problem = null;
            if (p.getFlashSaleStock() != null && p.getFlashSaleStock() - p.getFlashSaleSold() < e.getValue()) {
                problem = "Chỉ còn " + (p.getFlashSaleStock() - p.getFlashSaleSold()) + " suất Flash Sale cho \"" + p.getName() + "\"";
            } else if (p.getFlashSaleMaxPerBuyer() != null) {
                long bought = orderItemRepository.flashUnitsBought(p.getId(), buyerId, p.getFlashSaleStart());
                if (bought + e.getValue() > p.getFlashSaleMaxPerBuyer()) {
                    problem = "Mỗi khách chỉ được mua tối đa " + p.getFlashSaleMaxPerBuyer() + " \"" + p.getName()
                            + "\" giá Flash Sale" + (bought > 0 ? " (bạn đã mua " + bought + ")" : "");
                }
            }
            if (problem != null) {
                if (strict) throw bad(problem);
                sq.getNotices().add(problem);
            }
        }
    }

    /** Splits a whole-VND amount proportionally to the weights; rounding leftovers go to the largest weight. */
    static List<BigDecimal> split(BigDecimal total, List<BigDecimal> weights) {
        BigDecimal sum = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        List<BigDecimal> shares = new ArrayList<>();
        if (sum.signum() <= 0 || total.signum() <= 0) {
            weights.forEach(w -> shares.add(BigDecimal.ZERO));
            return shares;
        }
        BigDecimal given = BigDecimal.ZERO;
        int largest = 0;
        for (int i = 0; i < weights.size(); i++) {
            BigDecimal s = total.multiply(weights.get(i)).divide(sum, 0, RoundingMode.DOWN);
            shares.add(s);
            given = given.add(s);
            if (weights.get(i).compareTo(weights.get(largest)) > 0) largest = i;
        }
        shares.set(largest, shares.get(largest).add(total.subtract(given)));
        return shares;
    }

    private List<CartItem> loadItems(List<Long> ids, Long buyerId) {
        List<CartItem> items = new ArrayList<>();
        for (Long id : new LinkedHashSet<>(ids)) {
            CartItem item = cartItemRepository.findById(id)
                    .filter(i -> i.getUser().getId().equals(buyerId))
                    .orElseThrow(() -> bad("Sản phẩm trong giỏ hàng không tồn tại, vui lòng tải lại giỏ hàng"));
            Product product = item.getProduct();
            if (!product.isActive() || !product.getShop().isActive()) {
                throw bad("Sản phẩm \"" + product.getName() + "\" đã ngừng bán");
            }
            if (!product.getVariants().isEmpty() && item.getVariant() == null) {
                throw bad("Vui lòng chọn phân loại cho \"" + product.getName() + "\"");
            }
            int stock = item.getVariant() != null ? item.getVariant().getStock() : product.getStock();
            if (stock < item.getQuantity()) {
                throw bad(stock <= 0 ? "Sản phẩm \"" + product.getName() + "\" đã hết hàng"
                        : "Sản phẩm \"" + product.getName() + "\" chỉ còn " + stock + " sản phẩm");
            }
            items.add(item);
        }
        if (items.isEmpty()) throw bad("Vui lòng chọn sản phẩm để đặt hàng");
        return items;
    }

    private String orderCode() {
        String datePart = java.time.LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String code;
        do {
            code = "ORD-" + datePart + "-" + (new Random().nextInt(90000) + 10000);
        } while (orderRepository.findByCode(code).isPresent());
        return code;
    }

    private static String formatAddress(Address a) {
        StringJoiner j = new StringJoiner(", ");
        j.add(a.getRecipientName() + " (" + a.getPhone() + ")");
        for (String part : new String[]{a.getAddressLine(), a.getWard(), a.getDistrict(), a.getProvince()}) {
            if (!isBlank(part)) j.add(part.trim());
        }
        return j.toString();
    }

    private static String vnd(BigDecimal v) {
        return "₫" + String.format("%,d", v.longValue()).replace(',', '.');
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    private static String blankToNull(String s) { return isBlank(s) ? null : s.trim(); }

    private static ApiException bad(String message) { return new ApiException(message, HttpStatus.BAD_REQUEST); }
}
