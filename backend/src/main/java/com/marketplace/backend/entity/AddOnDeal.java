package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Shopee's "Mua Kem Deal Soc": buying one of the main products unlocks the add-on items at a deal price (type ADDON),
 * or, from a minimum spend on the main products, gifts for free (type GIFT). Applied automatically at checkout.
 * Getter/Setter instead of @Data: no field-based equals/hashCode over the product list.
 */
@Entity
@Table(name = "addon_deals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddOnDeal {

    public enum Type { ADDON, GIFT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    @ToString.Exclude
    private Shop shop;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Type type;

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    @Builder.Default
    private boolean active = true;

    // GIFT: the main products' subtotal needed to get the gifts (0 = any purchase)
    @Column(precision = 12, scale = 2)
    private BigDecimal minSpend;

    // how many of each add-on item / gift one order gets at the deal price
    @Builder.Default
    @Column(nullable = false, columnDefinition = "integer default 1")
    private Integer purchaseLimit = 1;

    @ManyToMany
    @JoinTable(name = "addon_deal_main_products",
            joinColumns = @JoinColumn(name = "deal_id"),
            inverseJoinColumns = @JoinColumn(name = "product_id"))
    @Builder.Default
    @ToString.Exclude
    private List<Product> mainProducts = new ArrayList<>();

    // add-on items (ADDON: with their deal price) or gifts (GIFT: price ignored, free)
    @ElementCollection
    @CollectionTable(name = "addon_deal_items", joinColumns = @JoinColumn(name = "deal_id"))
    @OrderColumn(name = "position")
    @Builder.Default
    private List<Item> items = new ArrayList<>();

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public boolean isRunning(LocalDateTime now) {
        return active && !now.isBefore(startAt) && now.isBefore(endAt);
    }

    @Embeddable
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        @Column(name = "product_id", nullable = false)
        private Long productId;

        @Column(name = "deal_price", precision = 12, scale = 2)
        private BigDecimal dealPrice;
    }
}
