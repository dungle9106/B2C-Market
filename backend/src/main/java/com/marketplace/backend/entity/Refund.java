package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// One refund to a buyer ("So hoan tien"). Online payments are refunded through the (simulated) gateway at
// once; cash-on-delivery money has to be transferred by the finance team, so those wait as PENDING until
// an admin records the bank transfer.
@Entity
@Table(name = "refunds", indexes = @Index(name = "idx_refunds_status", columnList = "status, created_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Refund {

    public enum Source { ORDER_CANCELLED, RETURN, DISPUTE, ADMIN }

    public enum Channel { ORIGINAL_METHOD, BANK_TRANSFER }

    public enum Status { PENDING, COMPLETED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "return_request_id")
    private Long returnRequestId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Source source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Channel channel;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    // gateway refund id / bank transfer reference
    @Column(length = 200)
    private String reference;

    @Column(length = 500)
    private String note;

    // the seller statement that deducted this refund (null = not deducted yet)
    @Column(name = "settlement_id")
    private Long settlementId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    private Long processedBy;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
