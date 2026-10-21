package com.marketplace.backend.service;

import com.marketplace.backend.dto.request.ComboRequest;
import com.marketplace.backend.dto.response.ProductResponse;
import com.marketplace.backend.entity.CartItem;
import com.marketplace.backend.entity.ComboPromotion;
import com.marketplace.backend.entity.Product;
import com.marketplace.backend.entity.Shop;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.ComboPromotionRepository;
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
import java.util.stream.Collectors;

/**
 * "Combo khuyen mai" (bundle deals): "Mua 2 giam 5%, mua 3 giam 10%" on a set of a
 * shop's products. At checkout the units of those products in one shop order are
 * counted together and the best reached tier takes its percentage off them.
 * A product belongs to at most one combo at any moment, so discounts never stack.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ComboService {

    private final ComboPromotionRepository comboRepository;
    private final ProductRepository productRepository;
    private final ShopRepository shopRepository;

    // ==== seller ====

    @Transactional(readOnly = true)
    public List<Map<String, Object>> mine() {
        return comboRepository.findByShopIdOrderByCreatedAtDesc(myShop().getId()).stream().map(c -> view(c, true)).toList();
    }

    public Map<String, Object> create(ComboRequest req) {
        Shop shop = myShop();
        ComboPromotion c = ComboPromotion.builder().shop(shop).active(true).build();
        fill(c, req);
        return view(comboRepository.save(c), true);
    }

    public Map<String, Object> update(Long id, ComboRequest req) {
        ComboPromotion c = mine(id);
        if (!c.getEndAt().isAfter(LocalDateTime.now())) throw bad("Combo đã kết thúc, không thể sửa");
        fill(c, req);
        return view(c, true);
    }

    public Map<String, Object> toggle(Long id) {
        ComboPromotion c = mine(id);
        if (!c.isActive()) checkOverlap(c.getShop(), c.getId(), c.getProducts(), c.getStartAt(), c.getEndAt());
        c.setActive(!c.isActive());
        return view(c, true);
    }

    public Map<String, Object> end(Long id) {
        ComboPromotion c = mine(id);
        LocalDateTime now = LocalDateTime.now();
        if (!c.getEndAt().isAfter(now)) throw bad("Combo đã kết thúc");
        if (c.getStartAt().isAfter(now)) c.setStartAt(now);
        c.setEndAt(now);
        return view(c, true);
    }

    private void fill(ComboPromotion c, ComboRequest req) {
        LocalDateTime now = LocalDateTime.now();
        if (!req.getEndAt().isAfter(req.getStartAt())) throw bad("Thời gian kết thúc phải sau thời gian bắt đầu");
        if (!req.getEndAt().isAfter(now)) throw bad("Thời gian kết thúc phải ở tương lai");
        if (req.getEndAt().isAfter(req.getStartAt().plusDays(90))) throw bad("Combo kéo dài tối đa 90 ngày");

        List<ComboRequest.TierRequest> tiers = req.getTiers().stream()
                .sorted(Comparator.comparingInt(ComboRequest.TierRequest::getMinQuantity)).toList();
        for (int i = 1; i < tiers.size(); i++) {
            if (tiers.get(i).getMinQuantity().equals(tiers.get(i - 1).getMinQuantity())) throw bad("Các mức giảm phải có số lượng khác nhau");
            if (tiers.get(i).getDiscountPercent() <= tiers.get(i - 1).getDiscountPercent()) {
                throw bad("Mua nhiều hơn phải được giảm nhiều hơn");
            }
        }
        List<Long> ids = req.getProductIds().stream().distinct().toList();
        List<Product> products = productRepository.findByShopIdAndIdIn(c.getShop().getId(), ids);
        if (products.size() != ids.size()) throw bad("Sản phẩm trong combo không hợp lệ");
        checkOverlap(c.getShop(), c.getId(), products, req.getStartAt(), req.getEndAt());

        c.setName(req.getName().trim());
        c.setStartAt(req.getStartAt());
        c.setEndAt(req.getEndAt());
        c.getTiers().clear();
        tiers.forEach(t -> c.getTiers().add(new ComboPromotion.Tier(t.getMinQuantity(), t.getDiscountPercent())));
        c.getProducts().clear();
        c.getProducts().addAll(products);
    }

    // a product may be in only one active combo at a time
    private void checkOverlap(Shop shop, Long selfId, List<Product> products, LocalDateTime start, LocalDateTime end) {
        Set<Long> ids = products.stream().map(Product::getId).collect(Collectors.toSet());
        for (ComboPromotion other : comboRepository.findByShopIdOrderByCreatedAtDesc(shop.getId())) {
            if (other.getId().equals(selfId) || !other.isActive()) continue;
            boolean overlaps = other.getStartAt().isBefore(end) && start.isBefore(other.getEndAt());
            if (!overlaps) continue;
            for (Product p : other.getProducts()) {
                if (ids.contains(p.getId())) {
                    throw bad("Sản phẩm \"" + p.getName() + "\" đã có trong combo \"" + other.getName() + "\" cùng thời gian");
                }
            }
        }
    }

    // ==== buyer ====

    /** Running or upcoming combos containing the product, with up to 12 of their products. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> forProduct(Long productId) {
        return comboRepository.liveForProduct(productId, LocalDateTime.now()).stream().map(c -> {
            Map<String, Object> m = view(c, false);
            m.put("products", c.getProducts().stream().filter(Product::isActive).limit(12).map(ProductResponse::from).toList());
            return m;
        }).toList();
    }

    // ==== checkout ====

    /** Discount per cart line and a note per combo reached (or nearly reached) in this shop order. */
    public record Result(Map<Long, BigDecimal> discountByCartItem, List<Map<String, Object>> notes) {
        public BigDecimal total() {
            return discountByCartItem.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    /** lineTotals: cart item id -> unit price x quantity (what the buyer pays before discounts). */
    // no readOnly: it runs inside the checkout's read-write transaction
    public Result apply(Long shopId, List<CartItem> items, Map<Long, BigDecimal> lineTotals) {
        Map<Long, BigDecimal> discounts = new HashMap<>();
        List<Map<String, Object>> notes = new ArrayList<>();
        for (ComboPromotion c : comboRepository.runningForShop(shopId, LocalDateTime.now())) {
            Set<Long> inCombo = c.getProducts().stream().map(Product::getId).collect(Collectors.toSet());
            List<CartItem> lines = items.stream().filter(i -> inCombo.contains(i.getProduct().getId())).toList();
            if (lines.isEmpty()) continue;
            int qty = lines.stream().mapToInt(CartItem::getQuantity).sum();
            ComboPromotion.Tier reached = null, next = null;
            for (ComboPromotion.Tier t : c.getTiers()) {
                if (qty >= t.getMinQuantity()) reached = t;
                else if (next == null) next = t;
            }
            if (reached != null) {
                BigDecimal pct = BigDecimal.valueOf(reached.getDiscountPercent());
                for (CartItem line : lines) {
                    discounts.put(line.getId(), lineTotals.get(line.getId()).multiply(pct)
                            .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN));
                }
            }
            Map<String, Object> note = new LinkedHashMap<>();
            note.put("comboId", c.getId());
            note.put("name", c.getName());
            note.put("quantity", qty);
            note.put("percent", reached != null ? reached.getDiscountPercent() : 0);
            if (next != null) {
                note.put("nextMinQuantity", next.getMinQuantity());
                note.put("nextPercent", next.getDiscountPercent());
            }
            notes.add(note);
        }
        return new Result(discounts, notes);
    }

    // ==== helpers ====

    private Map<String, Object> view(ComboPromotion c, boolean forSeller) {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("name", c.getName());
        m.put("startAt", c.getStartAt());
        m.put("endAt", c.getEndAt());
        m.put("active", c.isActive());
        m.put("status", !c.getEndAt().isAfter(now) ? "ENDED" : !c.isActive() ? "PAUSED"
                : c.getStartAt().isAfter(now) ? "UPCOMING" : "RUNNING");
        m.put("tiers", c.getTiers().stream().map(t -> Map.of("minQuantity", t.getMinQuantity(), "discountPercent", t.getDiscountPercent())).toList());
        if (forSeller) {
            m.put("productIds", c.getProducts().stream().map(Product::getId).toList());
            m.put("productNames", c.getProducts().stream().map(Product::getName).limit(5).toList());
            m.put("productCount", c.getProducts().size());
        }
        return m;
    }

    private ComboPromotion mine(Long id) {
        Long shopId = myShop().getId();
        return comboRepository.findById(id).filter(c -> c.getShop().getId().equals(shopId))
                .orElseThrow(() -> new ApiException("Combo không tồn tại", HttpStatus.NOT_FOUND));
    }

    private Shop myShop() {
        return shopRepository.findBySellerId(CurrentUser.id())
                .orElseThrow(() -> new ApiException("Bạn chưa có shop", HttpStatus.BAD_REQUEST));
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }
}
