package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// "Quang cao tim kiem": puts one product at the top of matching search results with a
// "Tai tro" label. Pay per click from the shop's ad balance, capped by a daily budget.
@Entity
@Table(name = "ad_campaigns")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdCampaign {

    public enum Status { ACTIVE, PAUSED, ENDED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    @ToString.Exclude
    private Shop shop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @ToString.Exclude
    private Product product;

    // accent-free keywords, comma separated ("ao thun,ao phong"); empty = match the product name
    @Column(length = 1000)
    private String keywords;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal bidPerClick;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal dailyBudget;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private Status status = Status.ACTIVE;

    @Builder.Default
    private long impressions = 0;

    @Builder.Default
    private long clicks = 0;

    @Builder.Default
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal spend = BigDecimal.ZERO;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
