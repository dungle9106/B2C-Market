package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// History of the notifications an admin sent to many users at once.
@Entity
@Table(name = "broadcasts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Broadcast {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationType type;

    // BUYER | SELLER | null = everyone
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Role audience;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String content;

    @Column(length = 300)
    private String link;

    private int recipients;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
