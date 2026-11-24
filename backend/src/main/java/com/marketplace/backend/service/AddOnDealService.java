package com.marketplace.backend.service;

import com.marketplace.backend.dto.request.AddOnDealRequest;
import com.marketplace.backend.dto.response.ProductResponse;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.AddOnDealRepository;
import com.marketplace.backend.repository.ProductRepository;
import com.marketplace.backend.repository.ShopRepository;
import com.marketplace.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * "Mua Kem Deal Soc" / "Mua de nhan qua". The seller picks main products and add-on items (with a deal price) or gifts;
 * at checkout, a shop order containing a main product gets the add-on items at the deal price, or the gifts free once
 * the main products reach the minimum spend, up to purchaseLimit of each. The saving is a shop-funded discount, added
 * to the order's combo discount so totals, settlements and reports already account for it.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AddOnDealService {

    private final AddOnDealRepository dealRepository;
    private final ProductRepository productRepository;
    private final ShopRepository shopRepository;

    // ==== seller ====

    @Transactional(readOnly = true)
    public List<Map<String, Object>> mine() {
        return dealRepository.findByShopIdOrderByCreatedAtDesc(myShop().getId()).stream().map(d -> view(d, true)).toList();
    }

    public Map<String, Object> create(AddOnDealRequest req) {
        AddOnDeal d = AddOnDeal.builder().shop(myShop()).active(true).build();
        fill(d, req);
        return view(dealRepository.save(d), true);
    }

    public Map<String, Object> update(Long id, AddOnDealRequest req) {
        AddOnDeal d = mine(id);
        if (!d.getEndAt().isAfter(LocalDateTime.now())) throw bad("Chương trình đã kết thúc, không thể sửa");
        fill(d, req);
        return view(d, true);
    }

    public Map<String, Object> toggle(Long id) {
        AddOnDeal d = mine(id);
        d.setActive(!d.isActive());
        return view(d, true);
    }

    public Map<String, Object> end(Long id) {
        AddOnDeal d = mine(id);
        LocalDateTime now = LocalDateTime.now();
        if (!d.getEndAt().isAfter(now)) throw bad("Chương trình đã kết thúc");
        if (d.getStartAt().isAfter(now)) d.setStartAt(now);
        d.setEndAt(now);
        return view(d, true);
    }

    private void fill(AddOnDeal d, AddOnDealRequest req) {
        LocalDateTime now = LocalDateTime.now();
        if (!req.getEndAt().isAfter(req.getStartAt())) throw bad("Thời gian kết thúc phải sau thời gian bắt đầu");
        if (!req.getEndAt().isAfter(now)) throw bad("Thời gian kết thúc phải ở tương lai");
        if (req.getEndAt().isAfter(req.getStartAt().plusDays(90))) throw bad("Chương trình kéo dài tối đa 90 ngày");
        Long shopId = d.getShop().getId();

        List<Long> mainIds = req.getMainProductIds().stream().distinct().toList();
        List<Product> mains = productRepository.findByShopIdAndIdIn(shopId, mainIds);
        if (mains.size() != mainIds.size()) throw bad("Sản phẩm chính không hợp lệ");

        List<Long> itemIds = req.getItems().stream().map(AddOnDealRequest.ItemRequest::getProductId).distinct().toList();
        if (itemIds.size() != req.getItems().size()) throw bad("Mỗi sản phẩm mua kèm chỉ chọn một lần");
        Map<Long, Product> items = productRepository.findByShopIdAndIdIn(shopId, itemIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        if (items.size() != itemIds.size()) throw bad("Sản phẩm mua kèm / quà tặng không hợp lệ");
        if (itemIds.stream().anyMatch(mainIds::contains)) throw bad("Một sản phẩm không thể vừa là sản phẩm chính vừa là sản phẩm mua kèm");

        List<AddOnDeal.Item> list = new ArrayList<>();
        for (AddOnDealRequest.ItemRequest it : req.getItems()) {
            Product p = items.get(it.getProductId());
            BigDecimal price = null;
            if (req.getType() == AddOnDeal.Type.ADDON) {
                price = it.getDealPrice();
                if (price == null || price.compareTo(BigDecimal.valueOf(1000)) < 0) throw bad("Giá mua kèm của \"" + p.getName() + "\" phải từ ₫1.000");
                if (price.compareTo(lowestPrice(p)) >= 0) {
                    throw bad("Giá mua kèm của \"" + p.getName() + "\" phải thấp hơn giá bán (" + NotificationService.vnd(lowestPrice(p)) + ")");
                }
                price = price.setScale(0, RoundingMode.DOWN);
            }
            list.add(new AddOnDeal.Item(p.getId(), price));
        }
        BigDecimal minSpend = req.getType() == AddOnDeal.Type.GIFT && req.getMinSpend() != null ? req.getMinSpend().max(BigDecimal.ZERO) : null;

        d.setName(req.getName().trim());
        d.setType(req.getType());
        d.setStartAt(req.getStartAt());
        d.setEndAt(req.getEndAt());
        d.setMinSpend(minSpend);
        d.setPurchaseLimit(req.getPurchaseLimit() != null ? req.getPurchaseLimit() : 1);
        d.getMainProducts().clear();
        d.getMainProducts().addAll(mains);
        d.getItems().clear();
        d.getItems().addAll(list);
    }

    // the cheapest way to buy the product (its own price or a variant's)
    private static BigDecimal lowestPrice(Product p) {
        BigDecimal min = p.getEffectivePrice();
        if (p.getVariants() != null) {
            for (ProductVariant v : p.getVariants()) min = min.min(p.unitPriceFor(v));
        }
        return min;
    }

    // ==== buyer ====

    /** Deals unlocked by buying this product: the add-on items / gifts with their deal price. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> forProduct(Long productId) {
        return dealRepository.runningForMainProduct(productId, LocalDateTime.now()).stream().map(d -> {
            Map<String, Object> m = view(d, false);
            Map<Long, Product> products = productRepository.findAllById(d.getItems().stream().map(AddOnDeal.Item::getProductId).toList())
                    .stream().collect(Collectors.toMap(Product::getId, Function.identity()));
            List<Map<String, Object>> items = new ArrayList<>();
            for (AddOnDeal.Item it : d.getItems()) {
                Product p = products.get(it.getProductId());
                if (p == null || !p.isActive() || p.getStock() == null || p.getStock() <= 0) continue;
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("product", ProductResponse.from(p));
                x.put("dealPrice", d.getType() == AddOnDeal.Type.GIFT ? BigDecimal.ZERO : it.getDealPrice());
                x.put("hasVariants", p.getVariants() != null && !p.getVariants().isEmpty());
                items.add(x);
            }
            m.put("items", items);
            return m;
        }).filter(m -> !((List<?>) m.get("items")).isEmpty()).toList();
    }

    // ==== checkout ====

    /**
     * Saving per cart line from the shop's running deals. Lines already discounted (combo) are left alone.
     * notes: one per deal that applies or is close, for the checkout page.
     */
    public ComboService.Result apply(Long shopId, List<CartItem> cart, Map<Long, BigDecimal> lineTotals, Map<Long, BigDecimal> alreadyDiscounted) {
        Map<Long, BigDecimal> discounts = new HashMap<>();
        List<Map<String, Object>> notes = new ArrayList<>();
        for (AddOnDeal d : dealRepository.runningForShop(shopId, LocalDateTime.now())) {
            Set<Long> mainIds = d.getMainProducts().stream().map(Product::getId).collect(Collectors.toSet());
            Map<Long, AddOnDeal.Item> byProduct = d.getItems().stream().collect(Collectors.toMap(AddOnDeal.Item::getProductId, Function.identity()));
            List<CartItem> mains = cart.stream().filter(c -> mainIds.contains(c.getProduct().getId())).toList();
            List<CartItem> extras = cart.stream().filter(c -> byProduct.containsKey(c.getProduct().getId())).toList();
            if (extras.isEmpty()) continue;
            Map<String, Object> note = new LinkedHashMap<>();
            note.put("comboId", "deal-" + d.getId());
            note.put("name", d.getName());
            note.put("type", d.getType().name());
            if (mains.isEmpty()) {
                note.put("hint", "Mua kèm một sản phẩm chính của chương trình để được " + (d.getType() == AddOnDeal.Type.GIFT ? "nhận quà" : "giá deal sốc"));
                notes.add(note);
                continue;
            }
            BigDecimal mainSpend = mains.stream().map(c -> lineTotals.get(c.getId())).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (d.getType() == AddOnDeal.Type.GIFT && d.getMinSpend() != null && mainSpend.compareTo(d.getMinSpend()) < 0) {
                note.put("hint", "Mua thêm " + NotificationService.vnd(d.getMinSpend().subtract(mainSpend)) + " sản phẩm chính để nhận quà");
                notes.add(note);
                continue;
            }
            BigDecimal saved = BigDecimal.ZERO;
            int count = 0;
            for (CartItem line : extras) {
                if (discounts.containsKey(line.getId()) || alreadyDiscounted.getOrDefault(line.getId(), BigDecimal.ZERO).signum() > 0) continue;
                int qty = Math.min(line.getQuantity(), d.getPurchaseLimit());
                BigDecimal unit = lineTotals.get(line.getId()).divide(BigDecimal.valueOf(line.getQuantity()), 0, RoundingMode.DOWN);
                BigDecimal dealPrice = d.getType() == AddOnDeal.Type.GIFT ? BigDecimal.ZERO : byProduct.get(line.getProduct().getId()).getDealPrice();
                BigDecimal off = unit.subtract(dealPrice).max(BigDecimal.ZERO).multiply(BigDecimal.valueOf(qty));
                if (off.signum() <= 0) continue;
                discounts.put(line.getId(), off);
                saved = saved.add(off);
                count += qty;
            }
            if (saved.signum() > 0) {
                note.put("message", d.getType() == AddOnDeal.Type.GIFT
                        ? "Tặng miễn phí " + count + " quà (tiết kiệm " + NotificationService.vnd(saved) + ")"
                        : "Giá mua kèm cho " + count + " sản phẩm (tiết kiệm " + NotificationService.vnd(saved) + ")");
                note.put("saved", saved);
            }
            notes.add(note);
        }
        return new ComboService.Result(discounts, notes);
    }

    // ==== helpers ====

    private Map<String, Object> view(AddOnDeal d, boolean forSeller) {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("name", d.getName());
        m.put("type", d.getType());
        m.put("startAt", d.getStartAt());
        m.put("endAt", d.getEndAt());
        m.put("active", d.isActive());
        m.put("status", !d.getEndAt().isAfter(now) ? "ENDED" : !d.isActive() ? "PAUSED" : d.getStartAt().isAfter(now) ? "UPCOMING" : "RUNNING");
        m.put("minSpend", d.getMinSpend());
        m.put("purchaseLimit", d.getPurchaseLimit());
        if (forSeller) {
            m.put("mainProductIds", d.getMainProducts().stream().map(Product::getId).toList());
            m.put("mainProductNames", d.getMainProducts().stream().map(Product::getName).limit(4).toList());
            m.put("mainProductCount", d.getMainProducts().size());
            Map<Long, String> names = productRepository.findAllById(d.getItems().stream().map(AddOnDeal.Item::getProductId).toList())
                    .stream().collect(Collectors.toMap(Product::getId, Product::getName));
            m.put("items", d.getItems().stream().map(it -> {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("productId", it.getProductId());
                x.put("productName", names.getOrDefault(it.getProductId(), "(đã xóa)"));
                x.put("dealPrice", it.getDealPrice());
                return x;
            }).toList());
        }
        return m;
    }

    private AddOnDeal mine(Long id) {
        Long shopId = myShop().getId();
        return dealRepository.findById(id).filter(d -> d.getShop().getId().equals(shopId))
                .orElseThrow(() -> new ApiException("Chương trình không tồn tại", HttpStatus.NOT_FOUND));
    }

    private Shop myShop() {
        return shopRepository.findBySellerId(CurrentUser.id())
                .orElseThrow(() -> new ApiException("Bạn chưa có shop", HttpStatus.BAD_REQUEST));
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }
}
