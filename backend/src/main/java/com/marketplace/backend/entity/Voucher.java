package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "vouchers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Voucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VoucherScope scope;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id") // null when scope = PLATFORM
    private Shop shop;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20) default 'PERCENT'")
    private VoucherType type = VoucherType.PERCENT;

    // PERCENT: 1-90 (%), FIXED / SHIPPING: amount in VND
    @Column(precision = 12, scale = 2)
    private BigDecimal discountValue;

    private BigDecimal maxDiscountAmount; // cap for PERCENT, null = no cap
    private BigDecimal minOrderAmount;    // merchandise subtotal needed, null = none

    @Builder.Default
    private Integer usageLimit = 0; // total uses, 0 = unlimited

    @Builder.Default
    private Integer usedCount = 0;

    @Builder.Default
    @Column(nullable = false, columnDefinition = "integer default 1")
    private Integer perUserLimit = 1; // 0 = unlimited

    private LocalDateTime startAt;
    private LocalDateTime endAt;

    @Builder.Default
    private boolean active = true;

    // false = "voucher an": not listed to buyers, only usable by typing the code (shared privately)
    @Builder.Default
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean publicVisible = true;

    // the products the voucher applies to; empty = every product of the shop.
    // A List (bag), not a Set: Product's Lombok hashCode walks its variants back to the product.
    @ManyToMany
    @JoinTable(name = "voucher_products",
            joinColumns = @JoinColumn(name = "voucher_id"),
            inverseJoinColumns = @JoinColumn(name = "product_id"))
    @Builder.Default
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private List<Product> products = new ArrayList<>();

    // ---- Shoppi Voucher targeting (platform scope only) ----

    // ids of platform categories the voucher applies to (a parent includes its sub-categories); empty = all
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "voucher_categories", joinColumns = @JoinColumn(name = "voucher_id"))
    @Column(name = "category_id")
    @Builder.Default
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private java.util.Set<Long> categoryIds = new java.util.HashSet<>();

    // only for buyers who never completed an order ("Khach hang moi")
    @Builder.Default
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean newBuyerOnly = false;

    // Shoppi stops the voucher once the discounts it funded reach this amount; null = no budget cap
    @Column(precision = 14, scale = 2)
    private BigDecimal budget;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id")
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private PromoCampaign campaign;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public boolean isRunning(LocalDateTime now) {
        return active
                && (startAt == null || !now.isBefore(startAt))
                && (endAt == null || now.isBefore(endAt));
    }

    public boolean appliesTo(Product p) {
        return products.isEmpty() || products.stream().anyMatch(x -> x.getId().equals(p.getId()));
    }

    /** Platform voucher: whether the product's category (or its parent) is targeted. */
    public boolean appliesToCategory(Product p) {
        if (categoryIds.isEmpty()) return true;
        Category c = p.getCategory();
        return c != null && (categoryIds.contains(c.getId()) || c.getParent() != null && categoryIds.contains(c.getParent().getId()));
    }

    public boolean isUsedUp() {
        return usageLimit != null && usageLimit > 0 && usedCount >= usageLimit;
    }

    // "Giam 10% toi da 50k", "Giam 30k", "Giam 15k phi van chuyen"
    public String title() {
        String v = shortVnd(discountValue);
        return switch (type) {
            case PERCENT -> "Giảm " + discountValue.stripTrailingZeros().toPlainString() + "%"
                    + (maxDiscountAmount != null ? " tối đa " + shortVnd(maxDiscountAmount) : "");
            case FIXED -> "Giảm " + v;
            case SHIPPING -> "Giảm " + v + " phí vận chuyển";
        };
    }

    private static String shortVnd(BigDecimal amount) {
        if (amount == null) return "";
        long v = amount.longValue();
        if (v >= 1_000_000 && v % 1_000_000 == 0) return (v / 1_000_000) + "tr";
        if (v >= 1000 && v % 1000 == 0) return (v / 1000) + "k";
        return String.format("%,d₫", v).replace(',', '.');
    }
}
