package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// "Doi soat": what Shoppi owes a shop for its completed orders up to a date.
// net = (merchandise - shop-funded discounts) - commission - refunds deducted
// (platform vouchers are paid by Shoppi and shipping fees go to the carrier, so neither changes the payout).
@Entity
@Table(name = "settlements", indexes = @Index(name = "idx_settlements_shop", columnList = "shop_id, id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Settlement {

    public enum Status { PENDING_PAYMENT, PAID }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40, unique = true)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    // orders delivered up to this date (inclusive)
    @Column(nullable = false)
    private LocalDate periodTo;

    private int orderCount;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal merchandise;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal shopDiscounts;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionRate;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal commission;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal refunds;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal netPayout;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING_PAYMENT;

    @Column(length = 200)
    private String paymentReference;

    private LocalDateTime paidAt;

    private Long createdBy;

    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
