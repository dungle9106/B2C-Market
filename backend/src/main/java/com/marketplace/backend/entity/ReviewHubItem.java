package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One review in the seller's review hub ("Trung tâm đánh giá"): reviews written on Shoppi (synced) and reviews
 * imported from other marketplaces (Shopee, Lazada, Tiki, TikTok Shop...) in one place, each with its analysis
 * (sentiment, issues, priority, anomaly signals) and its reply workflow (draft -> approved reply).
 */
@Entity
@Table(name = "review_hub_items",
        uniqueConstraints = @UniqueConstraint(name = "uk_review_hub_source", columnNames = {"shop_id", "source", "external_id"}),
        indexes = {@Index(name = "idx_review_hub_shop_time", columnList = "shop_id, reviewed_at"),
                @Index(name = "idx_review_hub_priority", columnList = "shop_id, status, priority")})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewHubItem {

    public enum Source { SHOPPI, SHOPEE, LAZADA, TIKI, TIKTOK, OTHER }

    public enum Sentiment { POSITIVE, NEUTRAL, NEGATIVE }

    public enum Level { URGENT, HIGH, NORMAL, LOW }

    public enum Status { NEW, DRAFTED, REPLIED, IGNORED }

    public enum AnomalyStatus { NONE, FLAGGED, CONFIRMED_OK, REPORTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Source source;

    // the review's id on its marketplace (Shoppi: the review id); imports without one get a content hash
    @Column(name = "external_id", nullable = false, length = 120)
    private String externalId;

    private Long reviewId;              // Shoppi review (source SHOPPI)
    private Long productId;             // matched Shoppi product, if any
    @Column(length = 300)
    private String productName;
    @Column(length = 120)
    private String reviewerName;
    @Column(length = 64)
    private String reviewerKey;         // normalised reviewer, to spot one account reviewing many times
    @Column(length = 60)
    private String orderRef;

    @Column(nullable = false)
    private Integer rating;
    @Column(length = 2000)
    private String content;
    private int mediaCount;

    @Column(name = "reviewed_at", nullable = false)
    private LocalDateTime reviewedAt;
    private LocalDateTime importedAt;
    @Column(length = 40)
    private String importBatch;

    // ==== analysis ====
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Sentiment sentiment;
    private Double sentimentScore;      // -1 .. 1
    @Column(length = 200)
    private String issues;              // comma separated issue keys (ReviewTextAnalyzer.ISSUES)
    private boolean issuesManual;       // corrected by the seller: kept by later analyses, used to train the model
    @Column(length = 300)
    private String issueSummary;        // one line: what went wrong
    private int priority;               // 0 .. 100
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Level priorityLevel;
    @Column(length = 500)
    private String priorityReasons;     // " | " separated
    private LocalDateTime replyDueAt;
    private int anomalyScore;           // 0 .. 100
    @Column(length = 300)
    private String anomalyFlags;        // comma separated
    @Column(length = 600)
    private String anomalyNote;
    @Enumerated(EnumType.STRING)
    @Column(length = 15)
    private AnomalyStatus anomalyStatus;
    @Column(length = 20)
    private String analyzedBy;          // RULE | SERVICE (FastAPI) | AI
    private LocalDateTime analyzedAt;
    private boolean aiPending;          // waiting for the LLM pass

    // ==== reply workflow ====
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;
    @Column(length = 1000)
    private String draftReply;
    @Column(length = 20)
    private String draftSource;         // AI | TEMPLATE | SELLER
    @Column(length = 1000)
    private String replyText;
    private LocalDateTime repliedAt;
    @Column(length = 20)
    private String replyChannel;        // SHOPPI (published on the review) | EXTERNAL (posted by the seller on that marketplace)
    @Column(length = 500)
    private String sellerNote;
}
