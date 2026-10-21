package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// One kind of unexpected server error, grouped by fingerprint (exception type + where it was thrown):
// how often it happened, first / last time, the last request and stack trace, and whether someone handled it.
@Entity
@Table(name = "error_events", indexes = @Index(name = "idx_error_last_seen", columnList = "last_seen"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorEvent {

    public enum Status { OPEN, RESOLVED, IGNORED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String fingerprint;

    @Column(nullable = false, length = 200)
    private String exceptionType;

    @Column(length = 1000)
    private String message;

    @Column(length = 300)
    private String location;         // first stack frame in our code: Class.method:line

    @Column(length = 10)
    private String lastMethod;

    @Column(length = 300)
    private String lastPath;

    private Long lastUserId;

    @Column(columnDefinition = "TEXT")
    private String stackTrace;

    @Builder.Default
    private long occurrences = 0;

    @Column(name = "first_seen", nullable = false)
    private LocalDateTime firstSeen;

    @Column(name = "last_seen", nullable = false)
    private LocalDateTime lastSeen;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private Status status = Status.OPEN;

    private LocalDateTime resolvedAt;
    private String resolvedByName;

    @Column(length = 500)
    private String note;

    @Builder.Default
    private int reopenCount = 0;    // came back after being marked resolved
}
