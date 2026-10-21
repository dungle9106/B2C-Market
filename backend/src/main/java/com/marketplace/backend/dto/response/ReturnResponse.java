package com.marketplace.backend.dto.response;

import com.marketplace.backend.entity.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Data
public class ReturnResponse {
    private Long id;
    private Long orderId;
    private String orderCode;
    private String shopName;
    private String buyerName;
    private ReturnType type;
    private ReturnReason reason;
    private String description;
    private List<String> evidenceUrls;
    private List<Line> items;
    private BigDecimal refundAmount;
    private ReturnStatus status;
    private String sellerNote;
    private String complaint;
    private String adminNote;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime escalatedAt;            // a dispute: when the buyer complained
    private LocalDateTime sellerResponseDeadline; // the seller should answer in the dispute thread by then
    private String decision;                      // REFUND_FULL | REFUND_PARTIAL | REJECT once Shoppi decided

    @Data
    public static class Line {
        private Long orderItemId;
        private Long productId;
        private String productName;
        private String variantName;
        private String thumbnailUrl;
        private BigDecimal price;
        private Integer quantity;
    }

    public static ReturnResponse from(ReturnRequest r) {
        Order o = r.getOrder();
        Map<Long, OrderItem> lines = o.getItems().stream().collect(Collectors.toMap(OrderItem::getId, Function.identity()));

        ReturnResponse res = new ReturnResponse();
        res.setId(r.getId());
        res.setOrderId(o.getId());
        res.setOrderCode(o.getCode());
        res.setShopName(o.getShop().getName());
        res.setBuyerName(o.getBuyer().getFullName());
        res.setType(r.getType());
        res.setReason(r.getReason());
        res.setDescription(r.getDescription());
        res.setEvidenceUrls(List.copyOf(r.getEvidenceUrls()));
        res.setItems(r.getItems().stream().filter(i -> lines.containsKey(i.getOrderItemId())).map(i -> {
            OrderItem item = lines.get(i.getOrderItemId());
            Line l = new Line();
            l.setOrderItemId(item.getId());
            l.setProductId(item.getProduct().getId());
            l.setProductName(item.getProductNameSnapshot());
            l.setVariantName(item.getVariantNameSnapshot());
            l.setThumbnailUrl(item.getProduct().getThumbnailUrl());
            l.setPrice(item.getPriceAtPurchase());
            l.setQuantity(i.getQuantity());
            return l;
        }).toList());
        res.setRefundAmount(r.getRefundAmount());
        res.setStatus(r.getStatus());
        res.setSellerNote(r.getSellerNote());
        res.setComplaint(r.getComplaint());
        res.setAdminNote(r.getAdminNote());
        res.setEscalatedAt(r.getEscalatedAt());
        res.setSellerResponseDeadline(r.getSellerResponseDeadline());
        res.setDecision(r.getDecision());
        res.setCreatedAt(r.getCreatedAt());
        res.setResolvedAt(r.getResolvedAt());
        return res;
    }
}
