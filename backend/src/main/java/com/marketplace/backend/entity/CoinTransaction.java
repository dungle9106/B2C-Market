package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** One line of the "Shoppi Xu" history. The unique key stops the same event being credited twice. */
@Entity
@Table(name = "coin_transactions",
        uniqueConstraints = @UniqueConstraint(name = "uk_coin_tx", columnNames = {"user_id", "type", "ref"}),
        indexes = @Index(name = "idx_coin_tx_user", columnList = "user_id, id"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoinTransaction {

    public enum Type {
        CHECK_IN,      // daily check-in; ref = the date
        ORDER_EARN,    // reward of a completed order; ref = order code
        ORDER_SPEND,   // used at checkout; ref = checkout code
        ORDER_REFUND,  // given back: order cancelled (ref = order code) or returned (ref = RT + request id)
        LUCKY_SPIN     // prize of "Vong quay may man"; ref = LS + spin id
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    @Column(nullable = false)
    private long amount;   // signed: negative when spent

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
