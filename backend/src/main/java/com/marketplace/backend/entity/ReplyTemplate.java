package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// A shop's saved answer ("Tin nhan mau") for chat, product questions or reviews.
// May contain {ten_khach}, {ten_shop}, {ten_san_pham}, filled in when used.
@Entity
@Table(name = "reply_templates", indexes = @Index(name = "idx_reply_templates_shop", columnList = "shop_id, kind"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReplyTemplate {

    public enum Kind { CHAT, QUESTION, REVIEW }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Kind kind;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
