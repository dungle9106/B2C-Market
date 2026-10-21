package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// One signed-in device. The access token (JWT) carries this id; the refresh token is stored hashed and
// rotated on every refresh. Revoking the session (logout, "dang xuat thiet bi khac", password change)
// stops both at once.
@Entity
@Table(name = "user_sessions", indexes = @Index(name = "idx_sessions_user", columnList = "user_id, revoked_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSession {

    @Id
    @Column(length = 36)
    private String id;                 // UUID, the "sid" claim of the access tokens

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 64)
    private String refreshHash;        // SHA-256 of the current refresh token

    @Column(length = 64)
    private String deviceKey;          // browser + OS + network, to notice a new device

    @Column(length = 80)
    private String deviceLabel;        // "Chrome trên Windows"

    @Column(length = 64)
    private String ip;

    @Column(length = 300)
    private String userAgent;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime lastUsedAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(length = 40)
    private String revokeReason;       // LOGOUT | LOGOUT_OTHERS | PASSWORD_CHANGED | TOKEN_REUSE | ADMIN_LOCK ...

    public boolean active(LocalDateTime now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
