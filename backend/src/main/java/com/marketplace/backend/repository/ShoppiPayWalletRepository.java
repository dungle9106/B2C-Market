package com.marketplace.backend.repository;

import com.marketplace.backend.entity.ShoppiPayWallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface ShoppiPayWalletRepository extends JpaRepository<ShoppiPayWallet, Long> {

    // creates the wallet on first use
    @Modifying
    @Query(value = "INSERT INTO shoppi_pay_wallets (user_id, balance) VALUES (:userId, :amount) "
            + "ON CONFLICT (user_id) DO UPDATE SET balance = shoppi_pay_wallets.balance + :amount", nativeQuery = true)
    void credit(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    // 0 rows = the balance would go over the wallet limit
    @Modifying
    @Query("UPDATE ShoppiPayWallet w SET w.balance = w.balance + :amount WHERE w.userId = :userId AND w.balance + :amount <= :limit")
    int topUp(@Param("userId") Long userId, @Param("amount") BigDecimal amount, @Param("limit") BigDecimal limit);

    // 0 rows = not enough money
    @Modifying
    @Query("UPDATE ShoppiPayWallet w SET w.balance = w.balance - :amount WHERE w.userId = :userId AND w.balance >= :amount")
    int debit(@Param("userId") Long userId, @Param("amount") BigDecimal amount);
}
