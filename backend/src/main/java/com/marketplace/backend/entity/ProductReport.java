package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// "Bao cao san pham vi pham" sent by a buyer; admins resolve it by removing the product or dismissing.
@Entity
@Table(name = "product_reports", indexes = @Index(name = "idx_product_reports_product", columnList = "product_id, status"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductReport {

    public enum Status { OPEN, RESOLVED, DISMISSED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    // one of ProductModerationService.VIOLATIONS
    @Column(nullable = false, length = 40)
    private String reason;

    @Column(length = 1000)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 20)
    private Status status = Status.OPEN;

    private LocalDateTime createdAt;

    private LocalDateTime resolvedAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
