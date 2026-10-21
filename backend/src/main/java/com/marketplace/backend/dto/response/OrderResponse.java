package com.marketplace.backend.dto.response;

import com.marketplace.backend.entity.Order;
import com.marketplace.backend.entity.OrderItem;
import com.marketplace.backend.entity.TrackingEvent;
import com.marketplace.backend.service.ShippingService;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class OrderResponse {
    private Long id;
    private String code;
    private String status;
    private Long shopId;
    private String shopName;
    private BigDecimal subtotal;
    private BigDecimal discount;          // shopDiscount + platformDiscount
    private BigDecimal shopDiscount;
    private BigDecimal platformDiscount;
    private BigDecimal comboDiscount;
    private BigDecimal shippingFee;
    private BigDecimal shippingDiscount;
    private BigDecimal total;
    private String shopVoucherCode;
    private String platformVoucherCode;
    private String shippingMethod;
    private String shippingMethodName;
    private String carrier;
    private String carrierName;
    private LocalDate estimatedDeliveryFrom;
    private LocalDate estimatedDeliveryTo;
    private String note;
    private String checkoutCode;
    private String paymentMethod;
    private String paymentProvider;
    private String paymentStatus;

    // ---- order management ----
    private String trackingNumber;
    private String shippingGateway;   // "GHN" when booked with the real carrier
    private String gatewayStatus;
    private LocalDateTime confirmedAt;
    private LocalDateTime shippedAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime cancelledAt;
    private String cancelReason;
    private String cancelledBy;
    private Integer shopRating;              // null until the buyer rated the shop
    private String buyerName;
    // seller fulfilment
    private LocalDateTime packedAt;          // "Chuan bi hang" done
    private String pickupMethod;             // PICKUP | DROPOFF
    private LocalDate pickupDate;
    private LocalDateTime labelPrintedAt;
    // buyer's cancel request after the seller confirmed
    private LocalDateTime cancelRequestedAt;
    private String cancelRequestReason;
    private String cancelRequestRejectedReason;
    private boolean canRequestCancel;
    private LocalDateTime returnDeadline;    // delivered + longest return window of the items
    // what the buyer can do now (filled by OrderService for the buyer's own views)
    private boolean canCancel;
    private boolean canConfirmReceived;
    private boolean canReturn;
    private ReturnResponse returnRequest;    // latest return/refund request, if any
    private List<Event> events;              // detail view only
    private List<java.util.Map<String, Object>> refunds; // buyer detail: refund records (amount, status, reference)

    @Data
    public static class Event {
        private String stage;
        private String title;
        private String location;
        private LocalDateTime time;

        public static Event from(TrackingEvent e) {
            Event ev = new Event();
            ev.setStage(e.getStage());
            ev.setTitle(e.getTitle());
            ev.setLocation(e.getLocation());
            ev.setTime(e.getCreatedAt());
            return ev;
        }
    }
    private String shippingAddress;
    private LocalDateTime createdAt;
    private List<ItemLine> items;

    @Data
    public static class ItemLine {
        private Long id;
        private Long productId;
        private String thumbnailUrl;
        // buyer's order list only: whether this line was already reviewed (null elsewhere)
        private Boolean reviewed;
        private String productName;
        private String variantName;
        private BigDecimal priceAtPurchase;
        private Integer quantity;
        private boolean returnable;  // the product allows returns (returnDays > 0)

        static ItemLine from(OrderItem item) {
            ItemLine l = new ItemLine();
            l.setId(item.getId());
            l.setProductId(item.getProduct().getId());
            l.setThumbnailUrl(item.getProduct().getThumbnailUrl());
            l.setProductName(item.getProductNameSnapshot());
            l.setVariantName(item.getVariantNameSnapshot());
            l.setPriceAtPurchase(item.getPriceAtPurchase());
            l.setQuantity(item.getQuantity());
            Integer days = item.getProduct().getReturnDays();
            l.setReturnable(days != null && days > 0);
            return l;
        }
    }

    public static OrderResponse from(Order order) {
        OrderResponse r = new OrderResponse();
        r.setId(order.getId());
        r.setCode(order.getCode());
        r.setStatus(order.getStatus().name());
        r.setShopId(order.getShop().getId());
        r.setShopName(order.getShop().getName());
        r.setSubtotal(order.getSubtotal());
        r.setDiscount(order.getDiscount());
        r.setShopDiscount(order.getShopDiscount());
        r.setPlatformDiscount(order.getPlatformDiscount());
        r.setComboDiscount(order.getComboDiscount());
        r.setShippingFee(order.getShippingFee());
        r.setShippingDiscount(order.getShippingDiscount());
        r.setTotal(order.getTotal());
        r.setShopVoucherCode(order.getVoucher() != null ? order.getVoucher().getCode() : null);
        r.setPlatformVoucherCode(order.getPlatformVoucher() != null ? order.getPlatformVoucher().getCode() : null);
        if (order.getShippingMethod() != null) {
            r.setShippingMethod(order.getShippingMethod().name());
            r.setShippingMethodName(ShippingService.methodName(order.getShippingMethod()));
        }
        r.setCarrier(order.getCarrier());
        r.setCarrierName(order.getCarrier() != null ? ShippingService.carrierName(order.getCarrier()) : null);
        r.setEstimatedDeliveryFrom(order.getEstimatedDeliveryFrom());
        r.setEstimatedDeliveryTo(order.getEstimatedDeliveryTo());
        r.setNote(order.getNote());
        r.setCheckoutCode(order.getCheckoutCode());
        if (order.getPayment() != null) {
            r.setPaymentMethod(order.getPayment().getMethod().name());
            r.setPaymentProvider(order.getPayment().getProvider());
            r.setPaymentStatus(order.getPayment().getStatus().name());
        }
        r.setTrackingNumber(order.getTrackingNumber());
        r.setShippingGateway(order.getShippingGateway());
        r.setGatewayStatus(order.getGatewayStatus());
        r.setConfirmedAt(order.getConfirmedAt());
        r.setShippedAt(order.getShippedAt());
        r.setDeliveredAt(order.getDeliveredAt());
        r.setCancelledAt(order.getCancelledAt());
        r.setCancelReason(order.getCancelReason());
        r.setCancelledBy(order.getCancelledBy());
        r.setShopRating(order.getShopRating());
        r.setBuyerName(order.getBuyer().getFullName());
        r.setPackedAt(order.getPackedAt());
        r.setPickupMethod(order.getPickupMethod());
        r.setPickupDate(order.getPickupDate());
        r.setLabelPrintedAt(order.getLabelPrintedAt());
        r.setCancelRequestedAt(order.getCancelRequestedAt());
        r.setCancelRequestReason(order.getCancelRequestReason());
        r.setCancelRequestRejectedReason(order.getCancelRequestRejectedReason());
        r.setShippingAddress(order.getShippingAddressSnapshot());
        r.setCreatedAt(order.getCreatedAt());
        r.setItems(order.getItems().stream().map(ItemLine::from).collect(Collectors.toList()));
        return r;
    }
}
