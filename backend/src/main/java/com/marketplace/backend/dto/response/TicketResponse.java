package com.marketplace.backend.dto.response;

import com.marketplace.backend.entity.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TicketResponse {
    private Long id;
    private String code;
    private TicketCategory category;
    private TicketPriority priority;
    private TicketStatus status;
    private String subject;
    private Long requesterId;
    private String requesterName;
    private String requesterEmail;   // admin screens only
    private String requesterRole;
    private Long orderId;
    private String orderCode;
    private Long shopId;
    private String shopName;
    private boolean lastReplyByStaff;
    private Integer rating;
    private String ratingComment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime closedAt;
    private List<Message> messages;  // detail only

    @Data
    public static class Message {
        private Long id;
        private boolean staff;
        private boolean systemLine;
        private String senderName;
        private String content;
        private List<String> attachmentUrls;
        private LocalDateTime createdAt;

        public static Message from(TicketMessage m) {
            Message r = new Message();
            r.setId(m.getId());
            r.setStaff(m.isStaff());
            r.setSystemLine(m.isSystemLine());
            // staff replies are signed by the team, not by the admin's personal name
            r.setSenderName(m.isSystemLine() ? null : m.isStaff() ? "Đội ngũ hỗ trợ Shoppi"
                    : m.getSender() != null ? m.getSender().getFullName() : null);
            r.setContent(m.getContent());
            r.setAttachmentUrls(List.copyOf(m.getAttachmentUrls()));
            r.setCreatedAt(m.getCreatedAt());
            return r;
        }
    }

    public static TicketResponse from(SupportTicket t, boolean forAdmin) {
        TicketResponse r = new TicketResponse();
        r.setId(t.getId());
        r.setCode(t.getCode());
        r.setCategory(t.getCategory());
        r.setPriority(t.getPriority());
        r.setStatus(t.getStatus());
        r.setSubject(t.getSubject());
        r.setRequesterId(t.getRequester().getId());
        r.setRequesterName(t.getRequester().getFullName());
        r.setRequesterRole(t.getRequester().getRole().name());
        if (forAdmin) r.setRequesterEmail(t.getRequester().getEmail());
        if (t.getOrder() != null) {
            r.setOrderId(t.getOrder().getId());
            r.setOrderCode(t.getOrder().getCode());
        }
        if (t.getShop() != null) {
            r.setShopId(t.getShop().getId());
            r.setShopName(t.getShop().getName());
        }
        r.setLastReplyByStaff(t.isLastReplyByStaff());
        r.setRating(t.getRating());
        r.setRatingComment(t.getRatingComment());
        r.setCreatedAt(t.getCreatedAt());
        r.setUpdatedAt(t.getUpdatedAt());
        r.setResolvedAt(t.getResolvedAt());
        r.setClosedAt(t.getClosedAt());
        return r;
    }
}
