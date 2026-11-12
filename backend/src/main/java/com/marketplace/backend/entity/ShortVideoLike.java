package com.marketplace.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/** A buyer liked a Shoppi Video. Mapped so Hibernate creates the table; written with plain SQL (ShortVideoRepository). */
@Entity
@Table(name = "short_video_likes", indexes = @Index(name = "idx_short_video_like_user", columnList = "user_id"))
@IdClass(ShortVideoLike.Key.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShortVideoLike {

    @Id
    @Column(name = "video_id")
    private Long videoId;

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private Long videoId;
        private Long userId;
    }
}
