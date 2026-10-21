package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// "Nhat ky quan tri": who did what to which account / shop / setting.
@Entity
@Table(name = "admin_audit_logs", indexes = @Index(name = "idx_admin_audit_target", columnList = "target_type, target_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_id")
    private Long adminId;

    @Column(length = 200)
    private String adminName;

    // LOCK_USER, UNLOCK_USER, DELETE_USER, APPROVE_SHOP, REJECT_SHOP, SUSPEND_SHOP, REINSTATE_SHOP,
    // CREATE_STAFF, UPDATE_STAFF, REVOKE_STAFF, UPDATE_SETTING
    @Column(nullable = false, length = 40)
    private String action;

    // USER, SHOP, SETTING
    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(length = 300)
    private String targetLabel;

    @Column(length = 1000)
    private String detail;

    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
