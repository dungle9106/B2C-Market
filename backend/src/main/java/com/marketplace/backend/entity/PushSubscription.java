package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One device that receives push notifications for a user: a browser (Web Push) or a phone running a Shoppi app
 * (Expo push token). The endpoint identifies the device, so signing in with another account on it moves it over.
 */
@Entity
@Table(name = "push_subscriptions", indexes = @Index(name = "idx_push_user", columnList = "user_id"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PushSubscription {

    public enum Kind { WEB, EXPO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Kind kind;

    /** WEB: the push service URL; EXPO: the ExponentPushToken[...] */
    @Column(nullable = false, unique = true, length = 1000)
    private String endpoint;

    // WEB only: the browser's keys for encrypting the message (base64url)
    @Column(length = 200)
    private String p256dh;

    @Column(length = 100)
    private String auth;

    /** EXPO only: "buyer" or "seller" app, so seller news goes to the seller app */
    @Column(length = 10)
    private String app;

    @Column(length = 200)
    private String device;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime lastSuccessAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
