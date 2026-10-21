package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// One e-mail in the outbox: queued by the application, sent by the background worker (with retries).
// Without an SMTP server configured it is kept as LOGGED, readable in the admin "Email" page (demo / dev).
@Entity
@Table(name = "email_messages", indexes = {
        @Index(name = "idx_email_status", columnList = "status, next_attempt_at"),
        @Index(name = "idx_email_created", columnList = "created_at")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailMessage {

    public enum Status { QUEUED, SENT, FAILED, LOGGED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String toAddress;

    private Long toUserId;

    @Column(nullable = false, length = 40)
    private String type;              // VERIFY_EMAIL, RESET_PASSWORD, NEW_DEVICE_LOGIN, ORDER_PLACED ...

    @Column(nullable = false, length = 250)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String html;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private Status status = Status.QUEUED;

    @Builder.Default
    private int attempts = 0;

    @Column(length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    private LocalDateTime sentAt;
}
