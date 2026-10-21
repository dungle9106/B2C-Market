package com.marketplace.backend.service;

import com.marketplace.backend.dto.request.VoucherRequest;
import com.marketplace.backend.dto.response.VoucherResponse;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.CategoryRepository;
import com.marketplace.backend.repository.OrderRepository;
import com.marketplace.backend.repository.PromoCampaignRepository;
import com.marketplace.backend.repository.ProductRepository;
import com.marketplace.backend.repository.ShopRepository;
import com.marketplace.backend.repository.VoucherRepository;
import com.marketplace.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class VoucherService {

    private final VoucherRepository voucherRepository;
    private final ShopRepository shopRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final NotificationService notificationService;
    private final CategoryRepository categoryRepository;
    private final PromoCampaignRepository campaignRepository;
    private final AdminAuditService auditService;

    // ==== seller: shop vouchers ====

    @Transactional(readOnly = true)
    public List<VoucherResponse> myShopVouchers() {
        Long shopId = myShop().getId();
        Map<Long, Object[]> stats = new HashMap<>();
        for (Object[] row : orderRepository.voucherStats(shopId)) stats.put((Long) row[0], row);
        return voucherRepository.findByShopIdOrderByCreatedAtDesc(shopId).stream().map(v -> {
            VoucherResponse r = VoucherResponse.from(v);
            Object[] s = stats.get(v.getId());
            r.setOrdersCount(s != null ? ((Number) s[1]).longValue() : 0L);
            r.setDiscountGiven(s != null ? (BigDecimal) s[2] : BigDecimal.ZERO);
            r.setOrderValue(s != null ? (BigDecimal) s[3] : BigDecimal.ZERO);
            return r;
        }).toList();
    }

    /** Edit: before it starts everything but the code; once running only the end time, quota and visibility. */
    public VoucherResponse updateShopVoucher(Long id, VoucherRequest req) {
        Voucher v = ownShopVoucher(id);
        if (!v.getEndAt().isAfter(LocalDateTime.now())) throw bad("Voucher đã kết thúc, không thể sửa");
        fill(v, req, LocalDateTime.now().isBefore(v.getStartAt()));
        return VoucherResponse.from(v);
    }

    /** "Ket thuc": stops it now (an upcoming one never starts). */
    public VoucherResponse endShopVoucher(Long id) {
        return VoucherResponse.from(endNow(ownShopVoucher(id)));
    }

    /** Running, public vouchers of a shop, for buyers (shop page; with productId: those that apply to it). */
    @Transactional(readOnly = true)
    public List<VoucherResponse> publicForShop(Long shopId, Long productId) {
        Product product = productId != null ? productRepository.findById(productId).orElse(null) : null;
        return voucherRepository.runningPublicOfShop(shopId, LocalDateTime.now()).stream()
                .filter(v -> !v.isUsedUp() && (product == null || v.appliesTo(product)))
                .map(VoucherResponse::from).toList();
    }

    private Voucher ownShopVoucher(Long id) {
        Long shopId = myShop().getId();
        return voucherRepository.findById(id)
                .filter(x -> x.getShop() != null && x.getShop().getId().equals(shopId))
                .orElseThrow(() -> new ApiException("Voucher không tồn tại", HttpStatus.NOT_FOUND));
    }

    private Voucher endNow(Voucher v) {
        LocalDateTime now = LocalDateTime.now();
        if (!v.getEndAt().isAfter(now)) throw bad("Voucher đã kết thúc");
        if (v.getStartAt().isAfter(now)) v.setStartAt(now);
        v.setEndAt(now);
        return v;
    }

    public VoucherResponse createShopVoucher(VoucherRequest req) {
        Shop shop = myShop();
        Voucher v = create(req, VoucherScope.SHOP, shop);
        // the shop has no followers yet, so "its customers" = everyone who has ordered from it
        if (v.isPublicVisible()) { // a hidden voucher is shared privately, never announced
            notificationService.onShopVoucher(shop, v, orderRepository.findBuyerIdsOfShop(shop.getId()));
        }
        return VoucherResponse.from(v);
    }

    public VoucherResponse toggleShopVoucher(Long id) {
        Voucher v = voucherRepository.findById(id)
                .filter(x -> x.getShop() != null && x.getShop().getId().equals(myShop().getId()))
                .orElseThrow(() -> new ApiException("Voucher không tồn tại", HttpStatus.NOT_FOUND));
        v.setActive(!v.isActive());
        return VoucherResponse.from(voucherRepository.save(v));
    }

    // ==== admin: platform vouchers ====

    /** Every Shoppi Voucher with its results: checkouts, buyers, what it cost Shoppi and the order value it drove. */
    @Transactional(readOnly = true)
    public List<VoucherResponse> platformVouchers() {
        Map<Long, Object[]> stats = new HashMap<>();
        for (Object[] row : orderRepository.platformVoucherStats()) stats.put((Long) row[0], row);
        Map<Long, String> categoryNames = categoryRepository.findAll().stream().collect(Collectors.toMap(Category::getId, Category::getName));
        return voucherRepository.findByScopeOrderByCreatedAtDesc(VoucherScope.PLATFORM).stream()
                .map(v -> withStats(v, stats.get(v.getId()), categoryNames)).toList();
    }

    @Transactional(readOnly = true)
    public VoucherResponse platformVoucherView(Long id) {
        Voucher v = platformVoucher(id);
        Object[] s = orderRepository.platformVoucherStats().stream().filter(r -> r[0].equals(id)).findFirst().orElse(null);
        return withStats(v, s, categoryRepository.findAll().stream().collect(Collectors.toMap(Category::getId, Category::getName)));
    }

    private static VoucherResponse withStats(Voucher v, Object[] s, Map<Long, String> categoryNames) {
        VoucherResponse r = VoucherResponse.from(v);
        r.setCategoryNames(v.getCategoryIds().stream().map(categoryNames::get).filter(Objects::nonNull).sorted().toList());
        r.setOrdersCount(s != null ? ((Number) s[1]).longValue() : 0L);
        r.setBuyersCount(s != null ? ((Number) s[2]).longValue() : 0L);
        BigDecimal spent = s != null ? new BigDecimal(s[3].toString()).add(new BigDecimal(s[4].toString())) : BigDecimal.ZERO;
        r.setDiscountGiven(spent);
        r.setSpent(spent);
        r.setOrderValue(s != null ? new BigDecimal(s[5].toString()) : BigDecimal.ZERO);
        return r;
    }

    public VoucherResponse createPlatformVoucher(VoucherRequest req) {
        Voucher v = create(req, VoucherScope.PLATFORM, null);
        if (v.isPublicVisible()) notificationService.onPlatformVoucher(v);
        auditService.log("CREATE_VOUCHER", "VOUCHER", v.getId(), v.getCode(), v.title()
                + (v.getBudget() != null ? " · ngân sách " + NotificationService.vnd(v.getBudget()) : ""));
        return platformVoucherView(v.getId());
    }

    public VoucherResponse updatePlatformVoucher(Long id, VoucherRequest req) {
        Voucher v = platformVoucher(id);
        if (!v.getEndAt().isAfter(LocalDateTime.now())) throw bad("Voucher đã kết thúc, không thể sửa");
        fill(v, req, LocalDateTime.now().isBefore(v.getStartAt()));
        auditService.log("UPDATE_VOUCHER", "VOUCHER", v.getId(), v.getCode(), v.title());
        return platformVoucherView(id);
    }

    public VoucherResponse endPlatformVoucher(Long id) {
        Voucher v = endNow(platformVoucher(id));
        auditService.log("END_VOUCHER", "VOUCHER", v.getId(), v.getCode(), null);
        return platformVoucherView(id);
    }

    private Voucher platformVoucher(Long id) {
        return voucherRepository.findById(id)
                .filter(x -> x.getScope() == VoucherScope.PLATFORM)
                .orElseThrow(() -> new ApiException("Voucher không tồn tại", HttpStatus.NOT_FOUND));
    }

    public VoucherResponse togglePlatformVoucher(Long id) {
        Voucher v = voucherRepository.findById(id)
                .filter(x -> x.getScope() == VoucherScope.PLATFORM)
                .orElseThrow(() -> new ApiException("Voucher không tồn tại", HttpStatus.NOT_FOUND));
        v.setActive(!v.isActive());
        voucherRepository.save(v);
        auditService.log(v.isActive() ? "RESUME_VOUCHER" : "PAUSE_VOUCHER", "VOUCHER", v.getId(), v.getCode(), null);
        return platformVoucherView(id);
    }

    private Voucher create(VoucherRequest req, VoucherScope scope, Shop shop) {
        String code = req.getCode().trim().toUpperCase(Locale.ROOT);
        if (voucherRepository.existsByCodeIgnoreCase(code)) {
            throw new ApiException("Mã voucher " + code + " đã tồn tại", HttpStatus.BAD_REQUEST);
        }
        Voucher v = Voucher.builder().code(code).scope(scope).shop(shop).active(true).build();
        fill(v, req, true);
        return voucherRepository.save(v);
    }

    /**
     * Copies the form onto the voucher. full = false once it has started: then only the end time,
     * the total quota and the visibility change (buyers already saw / used the other terms).
     */
    private void fill(Voucher v, VoucherRequest req, boolean full) {
        LocalDateTime start = full ? req.getStartAt() : v.getStartAt();
        if (!req.getEndAt().isAfter(start)) throw bad("Thời gian kết thúc phải sau thời gian bắt đầu");
        if (req.getEndAt().isBefore(LocalDateTime.now())) throw bad("Thời gian kết thúc phải ở tương lai");
        int limit = req.getUsageLimit() != null ? req.getUsageLimit() : 0;
        if (limit > 0 && v.getUsedCount() != null && limit < v.getUsedCount()) {
            throw bad("Tổng lượt sử dụng không được nhỏ hơn số lượt đã dùng (" + v.getUsedCount() + ")");
        }
        v.setEndAt(req.getEndAt());
        v.setUsageLimit(limit);
        v.setPublicVisible(req.getPublicVisible() == null || req.getPublicVisible());
        if (v.getScope() == VoucherScope.PLATFORM) {
            // budget and programme can change while it runs
            BigDecimal budget = req.getBudget() == null || req.getBudget().signum() == 0 ? null : req.getBudget();
            if (budget != null) {
                if (budget.signum() < 0) throw bad("Ngân sách không hợp lệ");
                BigDecimal spent = v.getId() == null ? BigDecimal.ZERO : orderRepository.platformVoucherSpent(v.getId());
                if (budget.compareTo(spent) < 0) throw bad("Ngân sách không được nhỏ hơn số đã chi (" + NotificationService.vnd(spent) + ")");
            }
            v.setBudget(budget);
            v.setCampaign(req.getCampaignId() == null ? null : campaignRepository.findById(req.getCampaignId())
                    .orElseThrow(() -> bad("Chương trình khuyến mãi không tồn tại")));
        } else if (req.getBudget() != null || req.getCampaignId() != null
                || req.getCategoryIds() != null && !req.getCategoryIds().isEmpty() || Boolean.TRUE.equals(req.getNewBuyerOnly())) {
            throw bad("Ngân sách, ngành hàng và khách hàng mới chỉ áp dụng cho Shoppi Voucher");
        }
        if (!full) return;

        BigDecimal value = req.getValue();
        if (req.getType() == VoucherType.PERCENT) {
            if (value.compareTo(BigDecimal.ONE) < 0 || value.compareTo(BigDecimal.valueOf(90)) > 0) {
                throw new ApiException("Mức giảm phần trăm phải từ 1% đến 90%", HttpStatus.BAD_REQUEST);
            }
        } else {
            if (value.compareTo(BigDecimal.valueOf(1000)) < 0) {
                throw new ApiException("Mức giảm tối thiểu là ₫1.000", HttpStatus.BAD_REQUEST);
            }
            if (req.getType() == VoucherType.FIXED && req.getMinOrder() != null
                    && req.getMinOrder().signum() > 0 && value.compareTo(req.getMinOrder()) > 0) {
                throw new ApiException("Mức giảm không được lớn hơn giá trị đơn tối thiểu", HttpStatus.BAD_REQUEST);
            }
        }
        v.setType(req.getType());
        v.setDiscountValue(value);
        v.setMaxDiscountAmount(req.getType() == VoucherType.PERCENT ? req.getMaxDiscount() : null);
        v.setMinOrderAmount(req.getMinOrder());
        v.setPerUserLimit(req.getPerUserLimit() != null ? req.getPerUserLimit() : 1);
        v.setStartAt(req.getStartAt());

        if (v.getScope() == VoucherScope.PLATFORM) {
            List<Long> cats = req.getCategoryIds() == null ? List.of() : req.getCategoryIds().stream().distinct().toList();
            if (!cats.isEmpty() && categoryRepository.findAllById(cats).size() != cats.size()) throw bad("Ngành hàng không hợp lệ");
            v.getCategoryIds().clear();
            v.getCategoryIds().addAll(cats);
            v.setNewBuyerOnly(Boolean.TRUE.equals(req.getNewBuyerOnly()));
        }

        // product scope: shop vouchers only, products of that shop
        v.getProducts().clear();
        List<Long> ids = req.getProductIds() == null ? List.of() : req.getProductIds().stream().distinct().toList();
        if (!ids.isEmpty()) {
            if (v.getScope() != VoucherScope.SHOP) throw bad("Voucher toàn sàn áp dụng cho mọi sản phẩm");
            List<Product> products = productRepository.findByShopIdAndIdIn(v.getShop().getId(), ids);
            if (products.size() != ids.size()) throw bad("Sản phẩm áp dụng không hợp lệ");
            v.getProducts().addAll(products);
        }
    }

    // ==== buyer ====

    /** Vouchers the buyer could pick at checkout: platform-wide + those of the given shops. */
    @Transactional(readOnly = true)
    public List<VoucherResponse> availableFor(Collection<Long> shopIds) {
        Long buyerId = CurrentUser.id();
        LocalDateTime now = LocalDateTime.now();
        return voucherRepository.findActiveForShops(shopIds.isEmpty() ? List.of(-1L) : shopIds).stream()
                // hidden vouchers are only usable by typing their code
                .filter(v -> v.isRunning(now) && !v.isUsedUp() && v.isPublicVisible())
                .filter(v -> v.getScope() == VoucherScope.SHOP || platformProblem(v, buyerId) == null)
                .sorted(Comparator.comparing((Voucher v) -> v.getScope() == VoucherScope.SHOP)
                        .thenComparing(Voucher::getEndAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(v -> {
                    VoucherResponse r = VoucherResponse.from(v);
                    r.setUsedByMe((int) usesBy(v, buyerId));
                    return r;
                })
                .toList();
    }

    /** Null when the buyer may use this voucher now, otherwise the reason (min order is checked by the caller). */
    @Transactional(readOnly = true)
    public String problemWith(Voucher v, Long buyerId) {
        LocalDateTime now = LocalDateTime.now();
        if (!v.isActive()) return "Voucher đã ngừng áp dụng";
        if (v.getStartAt() != null && now.isBefore(v.getStartAt())) return "Voucher chưa đến thời gian sử dụng";
        if (v.getEndAt() != null && !now.isBefore(v.getEndAt())) return "Voucher đã hết hạn";
        if (v.isUsedUp()) return "Voucher đã hết lượt sử dụng";
        if (v.getPerUserLimit() != null && v.getPerUserLimit() > 0 && usesBy(v, buyerId) >= v.getPerUserLimit()) {
            return "Bạn đã dùng hết lượt của voucher này";
        }
        return v.getScope() == VoucherScope.PLATFORM ? platformProblem(v, buyerId) : null;
    }

    // Shoppi Voucher rules: budget left, new buyers only
    private String platformProblem(Voucher v, Long buyerId) {
        if (v.getBudget() != null && orderRepository.platformVoucherSpent(v.getId()).compareTo(v.getBudget()) >= 0) {
            return "Voucher đã hết ngân sách khuyến mãi";
        }
        if (v.isNewBuyerOnly() && orderRepository.hasPlacedOrder(buyerId)) return "Voucher chỉ dành cho khách hàng mới";
        return null;
    }

    private long usesBy(Voucher v, Long buyerId) {
        return v.getScope() == VoucherScope.PLATFORM
                ? orderRepository.countPlatformVoucherUses(buyerId, v.getId())
                : orderRepository.countByBuyerIdAndVoucherIdAndStatusNot(buyerId, v.getId(), OrderStatus.CANCELLED);
    }

    /** Merchandise discount for PERCENT / FIXED vouchers on the given base amount (whole VND). */
    public static BigDecimal merchandiseDiscount(Voucher v, BigDecimal base) {
        BigDecimal d = switch (v.getType()) {
            case PERCENT -> {
                BigDecimal pct = base.multiply(v.getDiscountValue()).divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN);
                yield v.getMaxDiscountAmount() != null ? pct.min(v.getMaxDiscountAmount()) : pct;
            }
            case FIXED -> v.getDiscountValue();
            case SHIPPING -> BigDecimal.ZERO;
        };
        return d.min(base).max(BigDecimal.ZERO).setScale(0, RoundingMode.DOWN);
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }

    private Shop myShop() {
        return shopRepository.findBySellerId(CurrentUser.id())
                .orElseThrow(() -> new ApiException("Bạn chưa có shop", HttpStatus.BAD_REQUEST));
    }
}
