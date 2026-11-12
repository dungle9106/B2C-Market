package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** What the return abuse checks found for one return request, and an admin's verdict on it. Shown to admins only. */
@Entity
@Table(name = "return_risk_assessments",
        uniqueConstraints = @UniqueConstraint(name = "uk_return_risk_request", columnNames = "return_request_id"),
        indexes = {@Index(name = "idx_return_risk_buyer", columnList = "buyer_id"), @Index(name = "idx_return_risk_score", columnList = "score")})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRiskAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "return_request_id", nullable = false)
    private Long returnRequestId;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    @Column(nullable = false)
    private int score;             // 0..100

    @Column(nullable = false, length = 10)
    private String level;          // LOW | MEDIUM | HIGH

    @Column(columnDefinition = "text")
    private String signals;        // JSON [{code, label, detail, points}]

    @Column(columnDefinition = "text")
    private String features;       // JSON { name: number }, the row the models train on

    private Double anomalyPercentile;
    private Double abuseProbability;

    @Column(length = 40)
    private String engine;         // e.g. "RULES+MODEL+LLM"

    @Column(columnDefinition = "text")
    private String aiReview;       // JSON {summary, concerns, mitigating, verify, image}; advisory, never changes the score

    @Column(length = 10)
    private String label;          // ABUSE | LEGIT, set by an admin; null = not reviewed

    @Column(length = 500)
    private String labelNote;

    private Long labeledBy;
    private LocalDateTime labeledAt;

    // refund hold: a risky request waits for an admin before the seller can refund (ReturnRiskService.HOLD_HOURS at most)
    private LocalDateTime heldAt;
    private LocalDateTime holdReleasedAt;
    private Long holdReleasedBy;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
