package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** "Theo doi shop": a buyer following a shop (news, follower-only vouchers). */
@Entity
@Table(name = "shop_follows",
        uniqueConstraints = @UniqueConstraint(name = "uk_shop_follow", columnNames = {"user_id", "shop_id"}),
        indexes = @Index(name = "idx_shop_follow_shop", columnList = "shop_id"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShopFollow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
