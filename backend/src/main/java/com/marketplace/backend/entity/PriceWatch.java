package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * "Bao khi giam gia": a buyer watches a product; when what they would pay drops (the seller lowers the price,
 * or a flash sale starts) they get a notification. One row per buyer and product.
 */
@Entity
@Table(name = "price_watches",
        uniqueConstraints = @UniqueConstraint(name = "uk_price_watch_user_product", columnNames = {"user_id", "product_id"}),
        indexes = @Index(name = "idx_price_watch_product", columnList = "product_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceWatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** price the buyer saw when they started watching (shown as "giam x% tu khi theo doi") */
    @Column(name = "watched_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal watchedPrice;

    /** price at the last check: a drop is measured from here, so each drop is announced once */
    @Column(name = "last_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal lastPrice;

    /** optional: only tell me when it is at or below this price */
    @Column(name = "target_price", precision = 15, scale = 2)
    private BigDecimal targetPrice;

    @Column(name = "notified_price", precision = 15, scale = 2)
    private BigDecimal notifiedPrice;

    @Column(name = "notified_at")
    private LocalDateTime notifiedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
