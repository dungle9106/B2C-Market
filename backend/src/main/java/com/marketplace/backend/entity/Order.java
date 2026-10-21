package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// One Order = one shop's portion of a checkout (multi-vendor cart is split into
// one Order per shop so each seller only ever sees/manages their own orders).
@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code; // human-readable order code, e.g. ORD-20260925-0001

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    // shopDiscount + platformDiscount (kept as one column for older screens/reports)
    @Builder.Default
    @Column(precision = 12, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Builder.Default
    @Column(precision = 12, scale = 2, columnDefinition = "numeric(12,2) default 0")
    private BigDecimal shopDiscount = BigDecimal.ZERO;

    // "Combo khuyen mai" discount (buy more, save more)
    @Builder.Default
    @Column(precision = 12, scale = 2, columnDefinition = "numeric(12,2) default 0")
    private BigDecimal comboDiscount = BigDecimal.ZERO;

    // what the shop voucher gave, on goods or on shipping (seller's voucher statistics)
    @Builder.Default
    @Column(precision = 12, scale = 2, columnDefinition = "numeric(12,2) default 0")
    private BigDecimal shopVoucherDiscount = BigDecimal.ZERO;

    // this order's share of the platform voucher (split across the shops of one checkout)
    @Builder.Default
    @Column(precision = 12, scale = 2, columnDefinition = "numeric(12,2) default 0")
    private BigDecimal platformDiscount = BigDecimal.ZERO;

    @Builder.Default
    @Column(precision = 12, scale = 2)
    private BigDecimal shippingFee = BigDecimal.ZERO;

    // freeship products + shipping vouchers
    @Builder.Default
    @Column(precision = 12, scale = 2, columnDefinition = "numeric(12,2) default 0")
    private BigDecimal shippingDiscount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ShippingMethod shippingMethod;

    @Column(length = 20)
    private String carrier;          // ShippingService carrier code, e.g. GHN

    private LocalDate estimatedDeliveryFrom;
    private LocalDate estimatedDeliveryTo;

    @Column(length = 500)
    private String note;             // buyer's message to the seller

    // all orders placed in one checkout share this code (and one online payment)
    @Column(length = 40)
    private String checkoutCode;

    @Column(length = 60)
    private String shippingProvince;  // buyer's province at order time, for tracking events

    @Column(length = 30)
    private String trackingNumber;    // "ma van don", assigned when handed to the carrier

    private LocalDateTime confirmedAt;
    private LocalDateTime shippedAt;
    private LocalDateTime deliveredAt; // start of the return window

    // the seller statement ("doi soat") that paid this order out; null = not settled yet
    @Column(name = "settlement_id")
    private Long settlementId;

    @Column(name = "commission_fee", precision = 12, scale = 2)
    private BigDecimal commissionFee;   // Shoppi's fee charged on this order when it was settled
    private LocalDateTime cancelledAt;

    @Column(length = 300)
    private String cancelReason;

    @Column(length = 10)
    private String cancelledBy;       // BUYER | SELLER | SYSTEM

    // ==== seller fulfilment ("Chuan bi hang") ====
    private LocalDateTime packedAt;        // packed and waiting for the carrier
    @Column(length = 10)
    private String pickupMethod;           // PICKUP (carrier comes to the shop) | DROPOFF (shop brings it to a post office)
    private LocalDate pickupDate;
    private LocalDateTime labelPrintedAt;  // shipping label printed at least once

    // ==== buyer's cancel request once the seller has confirmed ====
    private LocalDateTime cancelRequestedAt;
    @Column(length = 300)
    private String cancelRequestReason;
    @Column(length = 300)
    private String cancelRequestRejectedReason; // set when the seller said no (a request is allowed once)

    // buyer's rating of the shop's service and of the delivery, given once with the first review
    private Integer shopRating;
    private Integer shippingRating;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    private String shippingAddressSnapshot; // copy of address text at order time

    // the delivery address in parts (copied at order time), for the real carrier API
    @Column(length = 100)
    private String recipientName;
    @Column(length = 20)
    private String recipientPhone;
    @Column(length = 255)
    private String shippingAddressLine;
    @Column(length = 80)
    private String shippingWard;
    @Column(length = 80)
    private String shippingDistrict;

    // set when the parcel was booked with a real carrier API (e.g. "GHN"): its webhooks drive the tracking, not the simulator
    @Column(length = 10)
    private String shippingGateway;
    @Column(length = 40)
    private String gatewayStatus;       // the carrier's latest status, e.g. "transporting"
    @Column(precision = 12, scale = 2)
    private BigDecimal gatewayFee;      // what the carrier charges Shoppi for this parcel

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voucher_id")
    private Voucher voucher;          // shop voucher

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "platform_voucher_id")
    private Voucher platformVoucher;

    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private Payment payment;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
