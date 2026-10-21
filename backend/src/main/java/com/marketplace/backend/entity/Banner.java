package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// A promotional banner of the buyer home page, scheduled and ordered by the marketing team.
@Entity
@Table(name = "banners", indexes = @Index(name = "idx_banners_placement", columnList = "placement, sort_order"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Banner {

    public enum Placement {
        HOME_CAROUSEL, // the big sliding banner at the top of the home page
        HOME_SIDE      // the two small banners next to it
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String title;           // alt text, and the label in the admin list

    @Column(nullable = false)
    private String imageUrl;

    private String linkUrl;         // an in-site path ("/campaigns/sieu-sale") or null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Placement placement;

    @Builder.Default
    private Integer sortOrder = 0;

    private LocalDateTime startAt;  // null = no limit
    private LocalDateTime endAt;

    @Builder.Default
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id")
    private PromoCampaign campaign;

    @Builder.Default
    private long views = 0;

    @Builder.Default
    private long clicks = 0;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    /** HIDDEN, SCHEDULED, SHOWING or EXPIRED */
    public String status(LocalDateTime now) {
        if (!active) return "HIDDEN";
        if (startAt != null && now.isBefore(startAt)) return "SCHEDULED";
        if (endAt != null && !now.isBefore(endAt)) return "EXPIRED";
        return "SHOWING";
    }
}
