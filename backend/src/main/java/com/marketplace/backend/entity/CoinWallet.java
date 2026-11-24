package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * "Shoppi Xu" balance of a buyer (1 xu = 1 VND at checkout). A table of its own, not columns of User:
 * the balance only ever changes through the atomic updates of CoinWalletRepository, so saving a stale
 * User can never overwrite it.
 */
@Entity
@Table(name = "coin_wallets")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoinWallet {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Builder.Default
    @Column(nullable = false)
    private long balance = 0;

    // days in a row of the current check-in cycle (1..7)
    @Builder.Default
    @Column(nullable = false)
    private int streak = 0;

    private LocalDate lastCheckIn;
}
