package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Ad wallet history: top-ups (simulated payment) and click charges.
@Entity
@Table(name = "ad_transactions", indexes = @Index(name = "idx_ad_tx_shop", columnList = "shop_id, id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdTransaction {

    public enum Type { TOPUP, CLICK }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Type type;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;       // + top-up, - charge

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal balanceAfter;

    @Column(length = 200)
    private String note;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
