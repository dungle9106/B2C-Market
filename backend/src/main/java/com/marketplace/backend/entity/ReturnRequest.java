package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// "Tra hang / Hoan tien" for (part of) a delivered order, with the dispute trail.
@Entity
@Table(name = "return_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReturnType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReturnReason reason;

    @Column(length = 2000)
    private String description;

    // photos / video proving the problem
    @ElementCollection
    @CollectionTable(name = "return_request_evidence", joinColumns = @JoinColumn(name = "return_request_id"))
    @OrderColumn(name = "position")
    @Column(name = "url", length = 1000)
    @Builder.Default
    private List<String> evidenceUrls = new ArrayList<>();

    // which order lines, and how many of each
    @ElementCollection
    @CollectionTable(name = "return_request_items", joinColumns = @JoinColumn(name = "return_request_id"))
    @Builder.Default
    private List<ReturnItem> items = new ArrayList<>();

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal refundAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ReturnStatus status = ReturnStatus.REQUESTED;

    @Column(length = 1000)
    private String sellerNote;     // why the seller rejected

    @Column(length = 1000)
    private String complaint;      // buyer's message when escalating

    @Column(length = 1000)
    private String adminNote;      // platform decision

    @Column(updatable = false)
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;

    // ==== dispute (after the buyer escalated) ====
    private LocalDateTime escalatedAt;

    // the seller is asked to answer the complaint with evidence before this time
    private LocalDateTime sellerResponseDeadline;

    // admin decision: REFUND_FULL / REFUND_PARTIAL / REJECT
    @Column(length = 20)
    private String decision;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    @Embeddable
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReturnItem {
        private Long orderItemId;
        private Integer quantity;
    }
}
