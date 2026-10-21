package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// One click on a sponsored result. Repeated clicks by the same visitor are kept but not charged.
@Entity
@Table(name = "ad_clicks", indexes = @Index(name = "idx_ad_clicks_campaign", columnList = "campaign_id, created_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdClick {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    private Long userId;          // null for guests

    @Column(nullable = false, length = 100)
    private String visitorKey;    // user id, or ip + browser for guests

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal cost = BigDecimal.ZERO;

    private boolean charged;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
