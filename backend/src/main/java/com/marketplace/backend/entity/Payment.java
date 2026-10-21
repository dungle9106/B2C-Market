package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Kept as its own table (rather than fields on Order) so a real gateway
// (VNPay sandbox, etc.) can be swapped in later without touching Order.
@Entity
@Table(name = "payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    private String transactionId; // filled in when a real gateway is integrated

    @Column(length = 20)
    private String provider;      // E_WALLET: MOMO / ZALOPAY / VNPAY; CARD: VISA / MASTERCARD / JCB

    @Column(length = 4)
    private String cardLast4;     // only the last 4 digits are ever stored

    private LocalDateTime paidAt;

    // ---- real payment gateway (VNPay) ----
    @Column(length = 40)
    private String gatewayTxnRef;         // our reference sent to the gateway (vnp_TxnRef), new for each attempt

    @Column(length = 30)
    private String gatewayTransactionNo;  // the gateway's transaction number (vnp_TransactionNo), needed for refunds

    @Column(length = 14)
    private String gatewayPayDate;        // yyyyMMddHHmmss (vnp_PayDate), needed for queries / refunds

    @Column(length = 20)
    private String gatewayBankCode;

    @Column(length = 120)
    private String gatewayMessage;        // last answer of the gateway (failed payments)
}
