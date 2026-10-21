package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// DynamicUpdate: adBalance is moved by atomic UPDATE statements (AdService); saving the profile
// must not write back an old balance
@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "shops")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false, unique = true)
    private User seller;

    @Column(nullable = false)
    private String name;

    private String logoUrl;
    private String coverUrl;

    @Column(length = 1000)
    private String description;

    private String pickupAddress;

    // province the shop ships from (one of util.Provinces.ALL); powers the "Noi ban" filter
    private String province;

    // ward and district of the pickup address: the real carrier (GHN) needs them to pick up and to price parcels
    @Column(length = 80)
    private String pickupDistrict;
    @Column(length = 80)
    private String pickupWard;

    // carriers call this number when they come to pick up parcels
    @Column(length = 20)
    private String pickupPhone;

    // ==== shop policies ====
    // The DB-level defaults let ddl-auto=update add these columns to a shops table
    // that already has rows (existing shops get the defaults).

    // return window pre-filled for new products, 0 = no returns
    @Builder.Default
    @Column(nullable = false, columnDefinition = "integer default 7")
    private Integer defaultReturnDays = 7;

    // "dieu kien doi tra" shown on the shop page and every product page
    @Column(length = 1000)
    private String returnConditions;

    // carrier codes the shop ships with (ShippingService.CARRIERS), comma separated; null = all
    @Column(length = 100)
    private String carriers;

    // days needed to pack an order before handing it to the carrier (1-3); pushes the ETA back
    @Builder.Default
    @Column(nullable = false, columnDefinition = "integer default 1")
    private Integer handlingDays = 1;

    // the shop pays the economy shipping fee for orders from this amount; null = off
    @Column(precision = 12, scale = 2)
    private BigDecimal freeShipMinOrder;

    // ==== ads ("Quang cao"): prepaid balance, charged per click ====
    @Builder.Default
    @Column(nullable = false, precision = 14, scale = 2, columnDefinition = "numeric(14,2) default 0")
    private BigDecimal adBalance = BigDecimal.ZERO;

    // ==== verification (ShopVerification approved by an admin) ====
    @Builder.Default
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean verified = false;

    private LocalDateTime verifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private BusinessType businessType;

    // visible to buyers; kept equal to "status APPROVED and the seller's account not locked"
    @Builder.Default
    private boolean active = true;

    // ==== moderation ("Duyet shop") ====
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20) default 'APPROVED'")
    private ShopStatus status = ShopStatus.APPROVED;

    // why the shop was rejected or suspended (shown to the seller)
    @Column(length = 500)
    private String statusReason;

    private LocalDateTime statusChangedAt;

    // ==== chat: automatic replies ("Tin nhan tu dong") ====
    // welcome: sent once, after the buyer's first message in a conversation
    @Builder.Default
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean chatAutoReplyEnabled = false;

    @Column(length = 500)
    private String chatAutoReplyMessage;

    // away: outside chatHoursStart-chatHoursEnd ("HH:mm", may span midnight), at most every 12 hours per conversation
    @Builder.Default
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean chatAwayEnabled = false;

    @Column(length = 500)
    private String chatAwayMessage;

    @Column(length = 5)
    private String chatHoursStart;

    @Column(length = 5)
    private String chatHoursEnd;

    // the default reply templates are created once; after that the shop owns the list (may empty it)
    @Builder.Default
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean templatesInitialized = false;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
