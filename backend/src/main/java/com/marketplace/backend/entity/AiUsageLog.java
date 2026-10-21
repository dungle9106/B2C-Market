package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// One LLM call: which feature asked, which model answered, tokens, estimated cost and time taken.
@Entity
@Table(name = "ai_usage_logs", indexes = @Index(name = "idx_ai_usage_time", columnList = "created_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiUsageLog {

    public enum Status { SUCCESS, FAILED, BLOCKED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String feature;          // e.g. CHATBOT, PRODUCT_WRITER (see AiOpsService.FEATURES)

    @Column(length = 20)
    private String provider;

    @Column(length = 60)
    private String model;

    @Builder.Default
    private int promptTokens = 0;

    @Builder.Default
    private int completionTokens = 0;

    @Builder.Default
    private boolean estimated = false;   // tokens estimated from text length (the API did not report them)

    @Column(precision = 14, scale = 6)
    @Builder.Default
    private BigDecimal costUsd = BigDecimal.ZERO;

    private Integer latencyMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @Column(length = 300)
    private String error;

    private Long userId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
