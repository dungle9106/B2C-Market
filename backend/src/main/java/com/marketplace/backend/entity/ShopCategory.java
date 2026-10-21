package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// "Danh muc cua Shop": the seller's own grouping of their products (tabs on the shop page),
// independent of the platform-wide Category tree.
// Getter/Setter rather than @Data: it lives in Product.shopCategories (a Set), so equality stays
// the default per-instance one instead of a field hash that changes once the id is assigned.
@Entity
@Table(name = "shop_categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShopCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    @ToString.Exclude
    private Shop shop;

    @Column(nullable = false, length = 60)
    private String name;

    // display order on the shop page, smallest first
    @Builder.Default
    private int position = 0;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
