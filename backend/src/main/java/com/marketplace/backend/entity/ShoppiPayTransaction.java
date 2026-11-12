package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One line of the ShoppiPay history. The unique key stops the same event being applied twice. */
@Entity
@Table(name = "shoppi_pay_transactions",
        uniqueConstraints = @UniqueConstraint(name = "uk_shoppi_pay_tx", columnNames = {"user_id", "type", "ref"}),
        indexes = @Index(name = "idx_shoppi_pay_tx_user", columnList = "user_id, id"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShoppiPayTransaction {

    public enum Type {
        TOP_UP,    // simulated bank transfer into the wallet
        PAYMENT,   // paid a checkout; ref = checkout code
        REFUND     // cancelled or returned order; ref = the refund's reference
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;   // signed: negative when paid

    @Column(nullable = false, length = 40)
    private String ref;

    @Column(length = 200)
    private String note;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
