package com.marketplace.backend.dto.response;

import com.marketplace.backend.entity.ChatMessage;
import com.marketplace.backend.entity.ChatSide;
import com.marketplace.backend.entity.Order;
import com.marketplace.backend.entity.Product;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ChatMessageResponse {
    private Long id;
    private Long conversationId;
    private ChatSide senderSide;
    private boolean autoReply;
    private String content;
    private String imageUrl;
    private ProductCard product;
    private OrderCard order;
    private LocalDateTime createdAt;

    @Data
    public static class ProductCard {
        private Long id;
        private String name;
        private String thumbnailUrl;
        private BigDecimal price;
    }

    @Data
    public static class OrderCard {
        private Long id;
        private String code;
        private String status;
        private BigDecimal total;
        private String thumbnailUrl;
        private int itemCount;
    }

    public static ChatMessageResponse from(ChatMessage m) {
        ChatMessageResponse r = new ChatMessageResponse();
        r.setId(m.getId());
        r.setConversationId(m.getConversation().getId());
        r.setSenderSide(m.getSenderSide());
        r.setAutoReply(m.isAutoReply());
        r.setContent(m.getContent());
        r.setImageUrl(m.getImageUrl());
        r.setCreatedAt(m.getCreatedAt());
        Product p = m.getProduct();
        if (p != null) {
            ProductCard card = new ProductCard();
            card.setId(p.getId());
            card.setName(p.getName());
            card.setThumbnailUrl(p.getThumbnailUrl());
            card.setPrice(p.getEffectivePrice());
            r.setProduct(card);
        }
        Order o = m.getOrder();
        if (o != null) {
            OrderCard card = new OrderCard();
            card.setId(o.getId());
            card.setCode(o.getCode());
            card.setStatus(o.getStatus().name());
            card.setTotal(o.getTotal());
            card.setItemCount(o.getItems().size());
            if (!o.getItems().isEmpty()) card.setThumbnailUrl(o.getItems().get(0).getProduct().getThumbnailUrl());
            r.setOrder(card);
        }
        return r;
    }
}
