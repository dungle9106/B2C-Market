package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One "keep this buyer" action: a win-back voucher sent to a buyer the churn model flagged, or an abandoned cart
 * reminder. Kept so the admin page can tell whether it worked (did the buyer order again afterwards?).
 */
@Entity
@Table(name = "retention_actions", indexes = {
        @Index(name = "idx_retention_user", columnList = "user_id, created_at"),
        @Index(name = "idx_retention_type", columnList = "type, created_at")})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RetentionAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 20)
    private String type;            // WINBACK_VOUCHER | CART_REMINDER

    @Column(length = 40)
    private String voucherCode;

    private Double churnProbability; // the risk when it was sent

    @Column(precision = 14, scale = 2)
    private BigDecimal spendBefore;  // what the buyer had spent so far (value at stake)

    private Long sentBy;             // admin, null = automatic

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
