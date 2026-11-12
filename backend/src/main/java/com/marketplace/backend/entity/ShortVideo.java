package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** "Shoppi Video": a short video a shop posts, tagged with the products it shows (bought straight from the feed). */
@Entity
@Table(name = "short_videos", indexes = {
        @Index(name = "idx_short_video_shop", columnList = "shop_id"),
        @Index(name = "idx_short_video_feed", columnList = "status, created_at")})
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShortVideo {

    public enum Status { ACTIVE, HIDDEN }

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(nullable = false, length = 1000)
    private String videoUrl;          // our own upload (/uploads/...mp4|webm)

    @Column(length = 300)
    private String caption;

    @ElementCollection
    @CollectionTable(name = "short_video_products", joinColumns = @JoinColumn(name = "video_id"))
    @OrderColumn(name = "position")
    @Column(name = "product_id")
    @Builder.Default
    private List<Long> productIds = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private Status status = Status.ACTIVE;

    @Column(length = 300)
    private String hiddenReason;      // set by an admin

    @Builder.Default
    @Column(nullable = false)
    private long viewCount = 0;

    @Builder.Default
    @Column(nullable = false)
    private long likeCount = 0;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
