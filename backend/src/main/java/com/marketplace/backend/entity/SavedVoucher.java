package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** "Kho Voucher": a voucher the buyer saved to use later (public ones with "Luu", private ones by typing the code). */
@Entity
@Table(name = "saved_vouchers",
        uniqueConstraints = @UniqueConstraint(name = "uk_saved_voucher", columnNames = {"user_id", "voucher_id"}),
        indexes = @Index(name = "idx_saved_voucher_user", columnList = "user_id"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavedVoucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voucher_id", nullable = false)
    @ToString.Exclude
    private Voucher voucher;

    @Column(nullable = false, updatable = false)
    private LocalDateTime savedAt;

    private LocalDateTime remindedAt;   // "sap het han" sent

    @PrePersist
    public void prePersist() {
        if (savedAt == null) savedAt = LocalDateTime.now();
    }
}
