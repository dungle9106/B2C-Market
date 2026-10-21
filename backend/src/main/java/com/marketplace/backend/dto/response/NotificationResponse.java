package com.marketplace.backend.dto.response;

import com.marketplace.backend.entity.Notification;
import com.marketplace.backend.entity.NotificationType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NotificationResponse {
    private Long id;
    private NotificationType type;
    private String title;
    private String content;
    private String link;
    private String imageUrl;
    private boolean read;
    private LocalDateTime createdAt;

    public static NotificationResponse from(Notification n) {
        NotificationResponse r = new NotificationResponse();
        r.setId(n.getId());
        r.setType(n.getType());
        r.setTitle(n.getTitle());
        r.setContent(n.getContent());
        r.setLink(n.getLink());
        r.setImageUrl(n.getImageUrl());
        r.setRead(n.isRead());
        r.setCreatedAt(n.getCreatedAt());
        return r;
    }
}
