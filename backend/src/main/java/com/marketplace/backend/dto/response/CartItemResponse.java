package com.marketplace.backend.dto.response;

import com.marketplace.backend.entity.CartItem;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CartItemResponse {
    private Long id;
    private Long productId;
    private String productName;
    private String thumbnailUrl;
    private Long variantId;
    private String variantName;
    private BigDecimal unitPrice;
    private Integer quantity;
    private Long shopId;
    private String shopName;
    private boolean freeShipping;
    private BigDecimal originalPrice;   // crossed-out unit price, null when not discounted
    private Integer availableStock;     // variant stock, or product stock without variants
    // false when the product was hidden/removed or sold out: shown greyed out, can't be checked out
    private boolean available;

    public static CartItemResponse from(CartItem item) {
        CartItemResponse r = new CartItemResponse();
        r.setId(item.getId());
        r.setProductId(item.getProduct().getId());
        r.setProductName(item.getProduct().getName());
        r.setThumbnailUrl(item.getProduct().getThumbnailUrl());
        r.setShopId(item.getProduct().getShop().getId());
        r.setShopName(item.getProduct().getShop().getName());
        r.setQuantity(item.getQuantity());

        if (item.getVariant() != null) {
            r.setVariantId(item.getVariant().getId());
            r.setVariantName(item.getVariant().getName());
        }
        // flash sale price while it runs, plus the variant's extra price
        r.setUnitPrice(item.getProduct().unitPriceFor(item.getVariant()));
        r.setFreeShipping(item.getProduct().isFreeShipping());
        BigDecimal original = item.getProduct().getDisplayOriginalPrice();
        if (original != null) {
            BigDecimal extra = item.getVariant() != null && item.getVariant().getExtraPrice() != null
                    ? item.getVariant().getExtraPrice() : BigDecimal.ZERO;
            r.setOriginalPrice(original.add(extra));
        }
        int stock = item.getVariant() != null ? item.getVariant().getStock() : item.getProduct().getStock();
        r.setAvailableStock(stock);
        r.setAvailable(item.getProduct().isActive() && item.getProduct().getShop().isActive() && stock > 0);
        return r;
    }
}
