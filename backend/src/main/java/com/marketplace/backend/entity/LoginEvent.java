package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// One sign-in attempt, successful or not (lich su dang nhap / phat hien do mat khau).
@Entity
@Table(name = "login_events", indexes = {
        @Index(name = "idx_login_user", columnList = "user_id, created_at"),
        @Index(name = "idx_login_time", columnList = "created_at")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginEvent {

    public enum Result { SUCCESS, BAD_PASSWORD, UNKNOWN_EMAIL, LOCKED, TEMP_LOCKED, RATE_LIMITED, TWO_FACTOR_FAILED, TWO_FACTOR_REQUIRED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(length = 120)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Result result;

    @Column(length = 20)
    private String method;             // PASSWORD | GOOGLE | TWO_FACTOR | BACKUP_CODE | REFRESH

    @Column(length = 64)
    private String ip;

    @Column(length = 80)
    private String deviceLabel;

    private boolean newDevice;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
