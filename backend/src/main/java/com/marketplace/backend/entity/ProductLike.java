package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** "Yeu thich": a buyer's heart on a product. */
@Entity
@Table(name = "product_likes",
        uniqueConstraints = @UniqueConstraint(name = "uk_product_like", columnNames = {"user_id", "product_id"}),
        indexes = @Index(name = "idx_product_like_product", columnList = "product_id"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
