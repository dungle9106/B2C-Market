package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// Raw traffic for the seller's analytics: a product page view or an add-to-cart.
// The same visitor doing the same thing on the same product within 30 minutes is recorded once.
@Entity
@Table(name = "product_events", indexes = {
        @Index(name = "idx_product_events_shop", columnList = "shop_id, created_at"),
        @Index(name = "idx_product_events_dedupe", columnList = "product_id, visitor_key, created_at")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductEvent {

    public enum Type { VIEW, ADD_TO_CART }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    @Column(name = "user_id")
    private Long userId;

    // "u:<userId>" for a logged-in buyer, "g:<ip>:<user agent hash>" for a guest
    @Column(name = "visitor_key", nullable = false, length = 120)
    private String visitorKey;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
