package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// "Combo khuyen mai" (bundle deal): buy N or more of these products together, get X% off them,
// e.g. "Mua 2 giam 5%, mua 3 giam 10%". Applied automatically at checkout.
// Getter/Setter instead of @Data: no field-based equals/hashCode over the product list.
@Entity
@Table(name = "combo_promotions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComboPromotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    @ToString.Exclude
    private Shop shop;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    @Builder.Default
    private boolean active = true;

    // up to 3 tiers, increasing minQuantity and percent
    @ElementCollection
    @CollectionTable(name = "combo_tiers", joinColumns = @JoinColumn(name = "combo_id"))
    @OrderColumn(name = "position")
    @Builder.Default
    private List<Tier> tiers = new ArrayList<>();

    @ManyToMany
    @JoinTable(name = "combo_products",
            joinColumns = @JoinColumn(name = "combo_id"),
            inverseJoinColumns = @JoinColumn(name = "product_id"))
    @Builder.Default
    @ToString.Exclude
    private List<Product> products = new ArrayList<>();

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
    public static class Tier {
        private int minQuantity;
        private int discountPercent;
    }
}
