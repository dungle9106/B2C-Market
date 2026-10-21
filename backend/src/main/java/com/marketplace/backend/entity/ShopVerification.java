package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// "Xac minh thong tin": identity / business documents a seller submits, reviewed by an admin.
// Every submission is kept (a rejected one stays as history); the newest one is the current state.
@Entity
@Table(name = "shop_verifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShopVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BusinessType businessType;

    // owner's full name (individual) or registered business name (household / company)
    @Column(nullable = false, length = 200)
    private String legalName;

    // CCCD of the owner / legal representative, 12 digits
    @Column(nullable = false, length = 12)
    private String idNumber;

    // ma so thue: 10 digits, or 10 + "-" + 3 for a branch
    @Column(length = 14)
    private String taxCode;

    // so giay chung nhan dang ky kinh doanh (household / company only)
    @Column(length = 30)
    private String licenseNumber;

    @Column(length = 300)
    private String businessAddress;

    // CCCD front/back, business licence scans
    @ElementCollection
    @CollectionTable(name = "shop_verification_documents", joinColumns = @JoinColumn(name = "verification_id"))
    @OrderColumn(name = "position")
    @Column(name = "url", length = 1000)
    @Builder.Default
    private List<String> documentUrls = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private VerificationStatus status = VerificationStatus.PENDING;

    @Column(length = 500)
    private String rejectReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(updatable = false)
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;

    @PrePersist
    public void prePersist() {
        this.submittedAt = LocalDateTime.now();
    }
}
