package com.marketplace.backend.dto.response;

import com.marketplace.backend.entity.Product;
import com.marketplace.backend.entity.ProductVariant;
import com.marketplace.backend.service.ProductModerationService;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
public class ProductResponse {
    // ---- summary (product cards, lists) ----
    private Long id;
    private String name;
    private BigDecimal price;          // what the buyer pays now (flash sale applied)
    private BigDecimal originalPrice;  // crossed-out price, null when not discounted
    private Integer discountPercent;   // e.g. 25 for "-25%", null when not discounted
    private boolean flashSale;         // flash sale running right now
    private LocalDateTime flashSaleEnd;
    private Integer flashSaleStock;    // flash quota (null = whole stock) and units sold at the flash price
    private Integer flashSaleSold;
    private Integer flashSaleMaxPerBuyer;
    private boolean sponsored;         // search: "Tai tro" result paid by an ad campaign
    private Long adId;
    private Integer stock;
    private Integer soldCount;
    private String thumbnailUrl;
    private boolean freeShipping;
    private Double ratingAvg;
    private Integer ratingCount;
    private Long shopId;
    private String shopName;
    private String province;           // shop's "ship from" location
    private Long categoryId;
    private String categoryName;
    private boolean active;            // false = hidden by the seller
    private LocalDateTime createdAt;

    // ---- detail only (null in lists) ----
    private String description;
    private String tier1Name;          // "Màu sắc": the buyer picks one value per group
    private String tier2Name;
    private List<String> imageUrls;
    private String videoUrl;
    private Integer returnDays;
    private List<VariantResponse> variants;
    private String shopLogoUrl;
    private Double shopRating;        // average of buyers' shop-service stars
    private Long shopRatingCount;
    // raw pricing fields so the seller's edit form can round-trip them
    private BigDecimal basePrice;
    private BigDecimal listPrice;
    private BigDecimal flashSalePrice;
    private LocalDateTime flashSaleStart;

    // ---- seller only ----
    private String sku;
    private List<Long> shopCategoryIds;
    private boolean lowStock;          // some variant (or the product) has 1..LOW_STOCK left

    public static final int LOW_STOCK = 5;

    @Data
    public static class VariantResponse {
        private Long id;
        private String name;
        private BigDecimal extraPrice;
        private BigDecimal price;      // buyer-facing unit price for this variant
        private Integer stock;
        private String option1;
        private String option2;
        private String imageUrl;
        private String sku;            // seller screens only

        static VariantResponse from(Product p, ProductVariant v) {
            VariantResponse r = new VariantResponse();
            r.setId(v.getId());
            r.setName(v.getName());
            r.setOption1(v.getOption1());
            r.setOption2(v.getOption2());
            r.setImageUrl(v.getImageUrl());
            r.setExtraPrice(v.getExtraPrice());
            r.setPrice(p.unitPriceFor(v));
            r.setStock(v.getStock());
            return r;
        }
    }

    // seller only: moderation state, why, and what the automatic content check found
    private String moderationStatus;
    private String moderationReason;
    private String violationLabel;
    private List<Map<String, Object>> moderationFlags;

    public static ProductResponse from(Product p) {
        ProductResponse r = new ProductResponse();
        r.setId(p.getId());
        r.setName(p.getName());
        r.setPrice(p.getEffectivePrice());
        BigDecimal original = p.getDisplayOriginalPrice();
        if (original != null) {
            r.setOriginalPrice(original);
            r.setDiscountPercent(original.subtract(p.getEffectivePrice())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(original, 0, java.math.RoundingMode.HALF_UP).intValue());
        }
        r.setFlashSale(p.isFlashSaleActive());
        if (p.isFlashSaleActive()) {
            r.setFlashSaleEnd(p.getFlashSaleEnd());
            r.setFlashSaleStock(p.getFlashSaleStock());
            r.setFlashSaleSold(p.getFlashSaleSold());
            r.setFlashSaleMaxPerBuyer(p.getFlashSaleMaxPerBuyer());
        }
        r.setStock(p.getStock());
        r.setSoldCount(p.getSoldCount());
        r.setThumbnailUrl(p.getThumbnailUrl());
        r.setFreeShipping(p.isFreeShipping());
        r.setRatingAvg(p.getRatingAvg());
        r.setRatingCount(p.getRatingCount());
        r.setActive(p.isActive());
        r.setCreatedAt(p.getCreatedAt());
        if (p.getShop() != null) {
            r.setShopId(p.getShop().getId());
            r.setShopName(p.getShop().getName());
            r.setProvince(p.getShop().getProvince());
        }
        if (p.getCategory() != null) {
            r.setCategoryId(p.getCategory().getId());
            r.setCategoryName(p.getCategory().getName());
        }
        return r;
    }

    public static ProductResponse detail(Product p) {
        ProductResponse r = from(p);
        r.setDescription(p.getDescription());
        r.setImageUrls(List.copyOf(p.getImageUrls()));
        r.setVideoUrl(p.getVideoUrl());
        r.setReturnDays(p.getReturnDays());
        r.setVariants(p.getVariants().stream().map(v -> VariantResponse.from(p, v)).collect(Collectors.toList()));
        r.setTier1Name(p.getTier1Name());
        r.setTier2Name(p.getTier2Name());
        r.setShopLogoUrl(p.getShop() != null ? p.getShop().getLogoUrl() : null);
        r.setBasePrice(p.getPrice());
        r.setListPrice(p.getOriginalPrice());
        r.setFlashSalePrice(p.getFlashSalePrice());
        r.setFlashSaleStart(p.getFlashSaleStart());
        if (p.getFlashSaleEnd() != null) r.setFlashSaleEnd(p.getFlashSaleEnd());
        // upcoming / sold-out flash sales too: the seller's form and the "sap dien ra" banner need them
        r.setFlashSaleStock(p.getFlashSaleStock());
        r.setFlashSaleSold(p.getFlashSaleSold());
        r.setFlashSaleMaxPerBuyer(p.getFlashSaleMaxPerBuyer());
        return r;
    }

    /** Detail + what only the owner sees (SKUs, shop categories, low-stock flag). */
    public static ProductResponse forSeller(Product p) {
        ProductResponse r = detail(p);
        r.setSku(p.getSku());
        for (int i = 0; i < p.getVariants().size(); i++) r.getVariants().get(i).setSku(p.getVariants().get(i).getSku());
        r.setShopCategoryIds(p.getShopCategories().stream().map(c -> c.getId()).sorted().toList());
        r.setModerationStatus(p.getModerationStatus().name());
        r.setModerationReason(p.getModerationReason());
        r.setViolationLabel(p.getViolationType() == null ? null : ProductModerationService.VIOLATIONS.get(p.getViolationType()));
        r.setModerationFlags(ProductModerationService.flags(p.getModerationFlags()));
        r.setLowStock(p.getVariants().isEmpty()
                ? p.getStock() > 0 && p.getStock() <= LOW_STOCK
                : p.getVariants().stream().anyMatch(v -> v.getStock() > 0 && v.getStock() <= LOW_STOCK));
        return r;
    }
}
