package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** The fraud check of one order (FraudService) and an admin's verdict on it. Shown to admins only. */
@Entity
@Table(name = "order_risks",
        uniqueConstraints = @UniqueConstraint(name = "uk_order_risk_order", columnNames = "order_id"),
        indexes = {@Index(name = "idx_order_risk_score", columnList = "score"), @Index(name = "idx_order_risk_buyer", columnList = "buyer_id")})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderRisk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    @Column(nullable = false)
    private int score;             // 0..100

    @Column(nullable = false, length = 10)
    private String level;          // LOW | MEDIUM | HIGH

    private int ruleScore;         // the hand-written rules alone, 0..100

    private Double probability;    // trained model: chance it is a fraud / a refused COD order (null = no model)

    private Double anomaly;        // trained model: more unusual than this share of orders (null = no model)

    @Column(columnDefinition = "text")
    private String reasons;        // JSON ["Tài khoản tạo chưa đến 1 ngày", ...]

    @Column(columnDefinition = "text")
    private String features;       // JSON { name: number }, the row the model trains on

    @Column(length = 10)
    private String verdict;        // null = not reviewed | SAFE | FRAUD

    private Long reviewedBy;
    private LocalDateTime reviewedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
