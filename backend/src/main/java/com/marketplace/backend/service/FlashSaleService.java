package com.marketplace.backend.service;

import com.marketplace.backend.entity.FlashSaleRegistration;
import com.marketplace.backend.entity.Product;
import com.marketplace.backend.repository.FlashSaleRegistrationRepository;
import com.marketplace.backend.entity.Shop;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.ProductRepository;
import com.marketplace.backend.repository.ShopRepository;
import com.marketplace.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * "Flash Sale cua Shop": put many products on flash sale for one time slot at once,
 * each with its flash price, a promo quantity (so many units at that price) and a
 * per-buyer limit. Uses the product's flash sale fields, so search, product pages,
 * the home-page Flash Sale strip and checkout all pick it up.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class FlashSaleService {

    public static final int MAX_ITEMS = 50;

    private final ProductRepository productRepository;
    private final ShopRepository shopRepository;
    private final FlashSaleRegistrationRepository registrationRepository;

    /** Every product of the shop with its flash sale state (NONE, UPCOMING, RUNNING, SOLD_OUT, ENDED). */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        LocalDateTime now = LocalDateTime.now();
        return productRepository.findByShopIdOrderByCreatedAtDesc(myShop().getId()).stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("productId", p.getId());
            m.put("name", p.getName());
            m.put("thumbnailUrl", p.getThumbnailUrl());
            m.put("price", p.getPrice());
            m.put("stock", p.getStock());
            m.put("active", p.isActive());
            m.put("flashSalePrice", p.getFlashSalePrice());
            m.put("flashSaleStart", p.getFlashSaleStart());
            m.put("flashSaleEnd", p.getFlashSaleEnd());
            m.put("flashSaleStock", p.getFlashSaleStock());
            m.put("flashSaleSold", p.getFlashSaleSold());
            m.put("flashSaleMaxPerBuyer", p.getFlashSaleMaxPerBuyer());
            m.put("status", status(p, now));
            m.put("shoppiEvent", p.getFlashSaleEventId() != null); // a Shoppi Flash Sale slot, not the shop's own
            return m;
        }).toList();
    }

    /** items: [{ productId, flashPrice, flashStock?, maxPerBuyer? }] for the slot [startAt, endAt). */
    public Map<String, Object> schedule(LocalDateTime startAt, LocalDateTime endAt, List<Map<String, Object>> items) {
        LocalDateTime now = LocalDateTime.now();
        if (startAt == null || endAt == null) throw bad("Vui lòng chọn khung giờ Flash Sale");
        if (startAt.isBefore(now.minusMinutes(5))) throw bad("Thời gian bắt đầu phải từ bây giờ trở đi");
        if (!endAt.isAfter(startAt)) throw bad("Thời gian kết thúc phải sau thời gian bắt đầu");
        if (endAt.isAfter(startAt.plusDays(7))) throw bad("Một đợt Flash Sale kéo dài tối đa 7 ngày");
        if (items == null || items.isEmpty()) throw bad("Vui lòng chọn sản phẩm");
        if (items.size() > MAX_ITEMS) throw bad("Mỗi đợt tối đa " + MAX_ITEMS + " sản phẩm");

        Shop shop = myShop();
        List<Product> scheduled = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Map<String, Object> item : items) {
            Long productId = number(item.get("productId"));
            if (productId == null || !seen.add(productId)) throw bad("Sản phẩm bị trùng hoặc không hợp lệ");
            Product p = productRepository.findById(productId)
                    .filter(x -> x.getShop().getId().equals(shop.getId()))
                    .orElseThrow(() -> bad("Sản phẩm không tồn tại"));
            String name = "\"" + p.getName() + "\": ";
            String st = status(p, now);
            if (st.equals("RUNNING") || st.equals("SOLD_OUT")) throw bad(name + "đang trong một đợt Flash Sale, hãy kết thúc đợt đó trước");
            if (!registrationRepository.approvedAhead(p.getId(), now, FlashSaleRegistration.Status.APPROVED).isEmpty()) {
                throw bad(name + "đã được duyệt tham gia Flash Sale của Shoppi");
            }
            if (!p.isActive()) throw bad(name + "sản phẩm đang ẩn");

            BigDecimal flashPrice = item.get("flashPrice") instanceof Number n ? BigDecimal.valueOf(n.longValue()) : null;
            if (flashPrice == null || flashPrice.compareTo(BigDecimal.valueOf(1000)) < 0) throw bad(name + "giá Flash Sale tối thiểu ₫1.000");
            if (flashPrice.compareTo(p.getPrice()) >= 0) throw bad(name + "giá Flash Sale phải thấp hơn giá bán");
            Integer flashStock = intOrNull(item.get("flashStock"));
            Integer maxPerBuyer = intOrNull(item.get("maxPerBuyer"));
            if (flashStock != null && (flashStock < 1 || flashStock > p.getStock())) {
                throw bad(name + "số suất Flash Sale từ 1 đến " + p.getStock() + " (tồn kho)");
            }
            if (maxPerBuyer != null && (maxPerBuyer < 1 || (flashStock != null && maxPerBuyer > flashStock))) {
                throw bad(name + "giới hạn mỗi khách không hợp lệ");
            }

            p.setFlashSalePrice(flashPrice);
            p.setFlashSaleStart(startAt);
            p.setFlashSaleEnd(endAt);
            p.setFlashSaleStock(flashStock);
            p.setFlashSaleSold(0);
            p.setFlashSaleMaxPerBuyer(maxPerBuyer);
            p.setFlashSaleEventId(null);
            scheduled.add(p);
        }
        return Map.of("scheduled", scheduled.size());
    }

    /** Ends a running flash sale now, or cancels an upcoming one. */
    public void end(Long productId) {
        Long shopId = myShop().getId();
        Product p = productRepository.findById(productId)
                .filter(x -> x.getShop().getId().equals(shopId))
                .orElseThrow(() -> new ApiException("Sản phẩm không tồn tại", HttpStatus.NOT_FOUND));
        LocalDateTime now = LocalDateTime.now();
        // a Shoppi Flash Sale slot is withdrawn from the "Flash Sale Shoppi" page (before it starts)
        if (p.getFlashSaleEventId() != null && !status(p, now).equals("ENDED")) {
            throw bad("Đây là Flash Sale của Shoppi, hãy rút sản phẩm ở mục Flash Sale Shoppi");
        }
        switch (status(p, now)) {
            case "RUNNING", "SOLD_OUT" -> p.setFlashSaleEnd(now);
            case "UPCOMING" -> {
                p.setFlashSalePrice(null);
                p.setFlashSaleStart(null);
                p.setFlashSaleEnd(null);
                p.setFlashSaleStock(null);
                p.setFlashSaleMaxPerBuyer(null);
                p.setFlashSaleSold(0);
            }
            default -> throw bad("Sản phẩm không có Flash Sale đang hoặc sắp diễn ra");
        }
    }

    static String status(Product p, LocalDateTime now) {
        if (p.getFlashSalePrice() == null || p.getFlashSaleStart() == null || p.getFlashSaleEnd() == null) return "NONE";
        if (!p.getFlashSaleEnd().isAfter(now)) return "ENDED";
        if (p.getFlashSaleStart().isAfter(now)) return "UPCOMING";
        return p.isFlashSaleActive() ? "RUNNING" : "SOLD_OUT";
    }

    private static Long number(Object o) {
        return o instanceof Number n ? n.longValue() : null;
    }

    private static Integer intOrNull(Object o) {
        return o instanceof Number n ? n.intValue() : null;
    }

    private Shop myShop() {
        return shopRepository.findBySellerId(CurrentUser.id())
                .orElseThrow(() -> new ApiException("Bạn chưa có shop", HttpStatus.BAD_REQUEST));
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }
}
