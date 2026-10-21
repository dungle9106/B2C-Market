package com.marketplace.backend.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

// at least one of content / imageUrl / productId / orderId (checked by ChatService)
@Data
public class SendMessageRequest {
    @Size(max = 2000, message = "Tin nhắn tối đa 2000 ký tự")
    private String content;

    private String imageUrl;

    private Long productId;

    private Long orderId;
}
