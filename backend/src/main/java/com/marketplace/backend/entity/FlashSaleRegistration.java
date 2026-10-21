package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// A product a seller put forward for a platform Flash Sale slot.
@Entity
@Table(name = "flash_sale_registrations",
        uniqueConstraints = @UniqueConstraint(name = "uk_flash_reg_event_product", columnNames = {"event_id", "product_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlashSaleRegistration {

    public enum Status { PENDING, APPROVED, REJECTED, WITHDRAWN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private FlashSaleEvent event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal flashPrice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal priceAtRegistration;  // the normal price when registered, to show the discount

    private Integer flashStock;      // units at the flash price, null = all stock
    private Integer maxPerBuyer;     // null = no limit

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(length = 500)
    private String rejectReason;

    private LocalDateTime reviewedAt;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
