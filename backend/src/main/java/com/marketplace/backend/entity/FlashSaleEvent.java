package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// "Flash Sale cua Shoppi": a platform time slot (e.g. 12:00-14:00). Sellers register products with a flash
// price before the deadline, the marketing team reviews them, and approved products go on flash sale for
// the slot (the product's own flash sale fields, with flashSaleEventId pointing here).
@Entity
@Table(name = "flash_sale_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlashSaleEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    @Column(nullable = false)
    private LocalDateTime registrationDeadline;

    // the flash price must be at least this % below the normal price
    @Builder.Default
    private Integer minDiscountPercent = 10;

    // products one shop may register (pending + approved)
    @Builder.Default
    private Integer maxProductsPerShop = 10;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id")
    private PromoCampaign campaign;

    @Builder.Default
    private boolean cancelled = false;

    private Long createdBy;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    /** CANCELLED, REGISTERING (sellers can still register), REVIEWING (deadline passed), RUNNING, ENDED */
    public String status(LocalDateTime now) {
        if (cancelled) return "CANCELLED";
        if (!now.isBefore(endAt)) return "ENDED";
        if (!now.isBefore(startAt)) return "RUNNING";
        return now.isBefore(registrationDeadline) ? "REGISTERING" : "REVIEWING";
    }
}
