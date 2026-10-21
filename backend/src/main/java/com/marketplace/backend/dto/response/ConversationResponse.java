package com.marketplace.backend.dto.response;

import com.marketplace.backend.entity.ChatSide;
import com.marketplace.backend.entity.Conversation;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationResponse {
    private Long id;
    private Long shopId;
    private String shopName;
    private String shopLogoUrl;
    private boolean shopActive;
    private Long buyerId;
    private String buyerName;
    private String lastMessage;
    private LocalDateTime lastMessageAt;
    private int unread; // for whoever is asking
    private boolean awaitingReply; // the buyer wrote last, nobody from the shop answered yet

    public static ConversationResponse from(Conversation c, ChatSide viewer) {
        ConversationResponse r = new ConversationResponse();
        r.setId(c.getId());
        r.setShopId(c.getShop().getId());
        r.setShopName(c.getShop().getName());
        r.setShopLogoUrl(c.getShop().getLogoUrl());
        r.setShopActive(c.getShop().isActive());
        r.setBuyerId(c.getBuyer().getId());
        r.setBuyerName(c.getBuyer().getFullName());
        r.setLastMessage(c.getLastMessage());
        r.setLastMessageAt(c.getLastMessageAt());
        r.setUnread(viewer == ChatSide.BUYER ? c.getBuyerUnread() : c.getShopUnread());
        r.setAwaitingReply(c.isAwaitingReply());
        return r;
    }
}
