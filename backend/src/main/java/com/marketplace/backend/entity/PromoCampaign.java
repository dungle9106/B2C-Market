package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// "Chuong trinh khuyen mai toan san" (e.g. "Sieu Sale 11.11"): a named period with its own landing page
// (/campaigns/{slug}) that groups Shoppi Vouchers, platform Flash Sale slots and banners.
@Entity
@Table(name = "promo_campaigns")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromoCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    // URL of the landing page: lowercase letters, digits and dashes
    @Column(nullable = false, unique = true, length = 80)
    private String slug;

    @Column(length = 200)
    private String tagline;         // one line under the title

    @Column(length = 2000)
    private String description;     // terms / rules of the programme

    private String bannerUrl;       // wide image at the top of the landing page

    @Column(length = 7)
    @Builder.Default
    private String themeColor = "#ee4d2d";

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    @Builder.Default
    private boolean active = true;

    private Long createdBy;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    /** HIDDEN (switched off), UPCOMING, RUNNING or ENDED */
    public String status(LocalDateTime now) {
        if (!active) return "HIDDEN";
        if (now.isBefore(startAt)) return "UPCOMING";
        return now.isBefore(endAt) ? "RUNNING" : "ENDED";
    }
}
