package com.marketplace.backend.repository;

import com.marketplace.backend.entity.CoinWallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface CoinWalletRepository extends JpaRepository<CoinWallet, Long> {

    // creates the wallet on first use
    @Modifying
    @Query(value = "INSERT INTO coin_wallets (user_id, balance, streak) VALUES (:userId, :amount, 0) "
            + "ON CONFLICT (user_id) DO UPDATE SET balance = coin_wallets.balance + :amount", nativeQuery = true)
    void credit(@Param("userId") Long userId, @Param("amount") long amount);

    // 0 rows = not enough coins
    @Modifying
    @Query("UPDATE CoinWallet w SET w.balance = w.balance - :amount WHERE w.userId = :userId AND w.balance >= :amount")
    int debit(@Param("userId") Long userId, @Param("amount") long amount);

    // 0 rows = already checked in today (also when two requests race)
    @Modifying
    @Query("UPDATE CoinWallet w SET w.streak = :streak, w.lastCheckIn = :today "
            + "WHERE w.userId = :userId AND (w.lastCheckIn IS NULL OR w.lastCheckIn < :today)")
    int markCheckIn(@Param("userId") Long userId, @Param("streak") int streak, @Param("today") LocalDate today);
}
