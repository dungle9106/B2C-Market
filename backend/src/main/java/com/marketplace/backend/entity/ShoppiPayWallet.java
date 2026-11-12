package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/** "Vi ShoppiPay" of a buyer. The balance only changes through the atomic updates of ShoppiPayWalletRepository. */
@Entity
@Table(name = "shoppi_pay_wallets")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShoppiPayWallet {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Builder.Default
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;
}
