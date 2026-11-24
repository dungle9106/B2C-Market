package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Penalty points ("Sao Qua Ta") of a shop: added automatically (weekly order metrics, a listing taken down) or by an
 * admin. Only points of the current quarter count; an admin can revoke one (e.g. added by mistake).
 */
@Entity
@Table(name = "shop_penalties",
        uniqueConstraints = @UniqueConstraint(name = "uk_shop_penalty_ref", columnNames = {"shop_id", "ref_key"}),
        indexes = @Index(name = "idx_shop_penalty_shop", columnList = "shop_id, created_at"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShopPenalty {

    public enum Code { NFR, LATE_SHIPMENT, LISTING, MANUAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    @ToString.Exclude
    private Shop shop;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Code code;

    @Column(nullable = false)
    private int points;

    @Column(nullable = false, length = 500)
    private String reason;

    // what triggered an automatic penalty ("NFR:2026-W44", "LISTING:123:..."), so it is never added twice
    @Column(name = "ref_key", length = 80)
    private String refKey;

    private Long createdBy;            // admin user id for a manual penalty

    @Builder.Default
    private boolean revoked = false;

    @Column(length = 300)
    private String revokeReason;

    private LocalDateTime revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
