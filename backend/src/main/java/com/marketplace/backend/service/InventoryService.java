package com.marketplace.backend.service;

import com.marketplace.backend.dto.response.PageResponse;
import com.marketplace.backend.dto.response.ProductResponse;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.*;
import com.marketplace.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Seller's "Quan ly ton kho" page: one row per product (or per variant), what is
 * waiting in unshipped orders, manual adjustments and the movement history.
 * The page polls GET /api/seller/inventory, so stock moved by new orders shows up
 * without reloading.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class InventoryService {

    // stock already taken by these orders, but the parcel hasn't left the shop yet
    private static final Set<OrderStatus> TO_SHIP = EnumSet.of(OrderStatus.AWAITING_PAYMENT, OrderStatus.PENDING, OrderStatus.CONFIRMED);
    private static final Set<InventoryReason> ADJUST_REASONS = EnumSet.of(
            InventoryReason.RESTOCK, InventoryReason.STOCKTAKE, InventoryReason.DAMAGED, InventoryReason.OTHER);

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryLogRepository logRepository;
    private final ShopRepository shopRepository;
    private final StockService stockService;

    @Transactional(readOnly = true)
    public Map<String, Object> overview() {
        Shop shop = myShop();
        Map<String, Integer> toShip = new HashMap<>();
        for (Object[] r : orderItemRepository.quantitiesInOrders(shop.getId(), TO_SHIP)) {
            toShip.put(key((Long) r[0], (Long) r[1]), ((Number) r[2]).intValue());
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        int out = 0, low = 0, units = 0;
        BigDecimal value = BigDecimal.ZERO;
        for (Product p : productRepository.findByShopIdOrderByCreatedAtDesc(shop.getId())) {
            List<ProductVariant> variants = p.getVariants().isEmpty() ? Collections.singletonList(null) : p.getVariants();
            for (ProductVariant v : variants) {
                int stock = v != null ? v.getStock() : p.getStock();
                BigDecimal unitPrice = p.unitPriceFor(v);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("productId", p.getId());
                m.put("variantId", v != null ? v.getId() : null);
                m.put("name", p.getName());
                m.put("variantName", v != null ? v.getName() : null);
                m.put("sku", v != null && v.getSku() != null ? v.getSku() : p.getSku());
                m.put("thumbnailUrl", v != null && v.getImageUrl() != null ? v.getImageUrl() : p.getThumbnailUrl());
                m.put("active", p.isActive());
                m.put("stock", stock);
                m.put("toShip", toShip.getOrDefault(key(p.getId(), v != null ? v.getId() : null), 0));
                m.put("price", unitPrice);
                m.put("status", stock <= 0 ? "OUT" : stock <= ProductResponse.LOW_STOCK ? "LOW" : "OK");
                rows.add(m);
                if (stock <= 0) out++;
                else if (stock <= ProductResponse.LOW_STOCK) low++;
                units += Math.max(stock, 0);
                value = value.add(unitPrice.multiply(BigDecimal.valueOf(Math.max(stock, 0))));
            }
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("updatedAt", LocalDateTime.now());
        r.put("lowStockThreshold", ProductResponse.LOW_STOCK);
        r.put("summary", Map.of("skus", rows.size(), "outOfStock", out, "lowStock", low, "units", units, "value", value));
        r.put("rows", rows);
        return r;
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryLog> logs(Long productId, int page, int size) {
        Long shopId = myShop().getId();
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return PageResponse.of(productId == null
                ? logRepository.findByShopIdOrderByIdDesc(shopId, pageable)
                : logRepository.findByShopIdAndProductIdOrderByIdDesc(shopId, productId, pageable), l -> l);
    }

    /** body: productId, variantId (when the product has variants), mode ADD|SUBTRACT|SET, quantity, reason, note */
    public Map<String, Object> adjust(Long productId, Long variantId, String mode, Integer quantity, InventoryReason reason, String note) {
        Shop shop = myShop();
        Product p = productRepository.findById(productId)
                .filter(x -> x.getShop().getId().equals(shop.getId()))
                .orElseThrow(() -> new ApiException("Sản phẩm không tồn tại", HttpStatus.NOT_FOUND));
        ProductVariant v = null;
        if (!p.getVariants().isEmpty()) {
            if (variantId == null) throw bad("Vui lòng chọn phân loại");
            v = p.getVariants().stream().filter(x -> x.getId().equals(variantId)).findFirst()
                    .orElseThrow(() -> bad("Phân loại không thuộc sản phẩm này"));
        }
        if (quantity == null || quantity < 0 || quantity > 1_000_000) throw bad("Số lượng không hợp lệ");
        if (reason == null || !ADJUST_REASONS.contains(reason)) throw bad("Vui lòng chọn lý do điều chỉnh");
        if (!"SET".equals(mode) && quantity == 0) throw bad("Số lượng phải lớn hơn 0");
        int after = stockService.adjust(p, v, mode, quantity, reason, note);
        return Map.of("productId", p.getId(), "variantId", v != null ? v.getId() : "", "stock", after,
                "productStock", productRepository.currentStock(p.getId()));
    }

    private static String key(Long productId, Long variantId) {
        return productId + ":" + (variantId == null ? "" : variantId);
    }

    private Shop myShop() {
        return shopRepository.findBySellerId(CurrentUser.id())
                .orElseThrow(() -> new ApiException("Bạn chưa có shop", HttpStatus.BAD_REQUEST));
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }
}
