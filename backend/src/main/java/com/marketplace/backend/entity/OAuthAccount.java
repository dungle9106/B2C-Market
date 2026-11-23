package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A Google / Facebook / GitHub account linked to a Shoppi account. One Shoppi account can have one link per
 * provider; the provider's own user id (subject) identifies it, so a changed e-mail at the provider still signs in.
 */
@Entity
@Table(name = "user_oauth_accounts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_oauth_provider_subject", columnNames = {"provider", "subject"}),
                @UniqueConstraint(name = "uk_oauth_user_provider", columnNames = {"user_id", "provider"})
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OAuthAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    @Column(nullable = false, length = 100)
    private String subject;

    private String email;

    private String name;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime lastUsedAt;
}
