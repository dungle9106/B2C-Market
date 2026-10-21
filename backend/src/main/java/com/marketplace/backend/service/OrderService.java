package com.marketplace.backend.service;

import com.marketplace.backend.dto.request.AddToCartRequest;
import com.marketplace.backend.dto.response.CartItemResponse;
import com.marketplace.backend.dto.response.OrderResponse;
import com.marketplace.backend.dto.response.ReturnResponse;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.*;
import com.marketplace.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

// Placing orders lives in CheckoutService and return/refund requests in
// ReturnService; this class handles orders afterwards.
// class-level: open-in-view is off, so mapping lazy associations to DTOs
// must happen inside the transaction
@Service
@Transactional
@RequiredArgsConstructor
public class OrderService {

    // what a seller may move an order to, from each status
    private static final Map<OrderStatus, Set<OrderStatus>> SELLER_TRANSITIONS = Map.of(
            OrderStatus.PENDING, EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
            OrderStatus.CONFIRMED, EnumSet.of(OrderStatus.SHIPPING, OrderStatus.CANCELLED),
            OrderStatus.SHIPPING, EnumSet.of(OrderStatus.DELIVERED));
    // the buyer cancels on their own until the seller confirms; after that (until the parcel is
    // handed to the carrier) they can only ask the seller to cancel, once
    private static final Set<OrderStatus> BUYER_CANCELLABLE =
            EnumSet.of(OrderStatus.AWAITING_PAYMENT, OrderStatus.PENDING);
    private static final Set<ReturnStatus> OPEN_RETURN =
            EnumSet.of(ReturnStatus.REQUESTED, ReturnStatus.REJECTED, ReturnStatus.ESCALATED, ReturnStatus.REFUNDED);

    private final GhnService ghnService;
    private final RefundService refundService;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final VoucherRepository voucherRepository;
    private final ShopRepository shopRepository;
    private final ReviewRepository reviewRepository;
    private final ReturnRequestRepository returnRepository;
    private final TrackingService trackingService;
    private final CartService cartService;
    private final StockService stockService;

    // ==== buyer ====

    public List<OrderResponse> myOrders() {
        List<Order> orders = orderRepository.findByBuyerIdOrderByCreatedAtDesc(CurrentUser.id());
        Map<Long, ReturnRequest> latestReturn = latestReturns(orders.stream().map(Order::getId).toList());
        List<OrderResponse> result = orders.stream()
                .map(o -> forBuyer(o, latestReturn.get(o.getId())))
                .collect(Collectors.toList());

        // flag lines the buyer already reviewed, so the UI shows "Danh gia" only where it's still possible
        List<Long> lineIds = result.stream().flatMap(o -> o.getItems().stream()).map(OrderResponse.ItemLine::getId).toList();
        Set<Long> reviewed = lineIds.isEmpty() ? Set.of() : new HashSet<>(reviewRepository.findReviewedOrderItemIds(lineIds));
        result.forEach(o -> o.getItems().forEach(l -> l.setReviewed(reviewed.contains(l.getId()))));
        return result;
    }

    /** Order detail page: everything in the list plus the tracking timeline. */
    public OrderResponse getMyOrder(Long orderId) {
        Order order = myOrder(orderId);
        OrderResponse r = forBuyer(order, latestReturns(List.of(orderId)).get(orderId));
        Set<Long> reviewed = new HashSet<>(reviewRepository.findReviewedOrderItemIds(
                order.getItems().stream().map(OrderItem::getId).toList()));
        r.getItems().forEach(l -> l.setReviewed(reviewed.contains(l.getId())));
        r.setEvents(trackingService.events(orderId).stream().map(OrderResponse.Event::from).toList());
        r.setRefunds(refundService.forOrder(orderId));
        return r;
    }

    private OrderResponse forBuyer(Order o, ReturnRequest latestReturn) {
        OrderResponse r = OrderResponse.from(o);
        r.setCanCancel(BUYER_CANCELLABLE.contains(o.getStatus()));
        r.setCanRequestCancel(o.getStatus() == OrderStatus.CONFIRMED && o.getCancelRequestedAt() == null
                && o.getCancelRequestRejectedReason() == null);
        r.setCanConfirmReceived(o.getStatus() == OrderStatus.SHIPPING);
        LocalDateTime deadline = returnDeadline(o);
        r.setReturnDeadline(deadline);
        r.setCanReturn(o.getStatus() == OrderStatus.DELIVERED && deadline != null
                && LocalDateTime.now().isBefore(deadline)
                && (latestReturn == null || !OPEN_RETURN.contains(latestReturn.getStatus())));
        if (latestReturn != null) r.setReturnRequest(ReturnResponse.from(latestReturn));
        return r;
    }

    /** Delivered time + the longest return window among the order's products; null if none allows returns. */
    public static LocalDateTime returnDeadline(Order o) {
        if (o.getDeliveredAt() == null) return null;
        int days = o.getItems().stream()
                .mapToInt(i -> i.getProduct().getReturnDays() != null ? i.getProduct().getReturnDays() : 0)
                .max().orElse(0);
        return days > 0 ? o.getDeliveredAt().plusDays(days) : null;
    }

    private Map<Long, ReturnRequest> latestReturns(Collection<Long> orderIds) {
        if (orderIds.isEmpty()) return Map.of();
        Map<Long, ReturnRequest> latest = new HashMap<>();
        for (ReturnRequest rr : returnRepository.findByOrderIdIn(orderIds)) {
            latest.merge(rr.getOrder().getId(), rr, (a, b) -> a.getId() > b.getId() ? a : b);
        }
        return latest;
    }

    /** Cancels right away before the seller confirms; afterwards sends a cancel request to the seller. */
    public void cancelOrder(Long orderId, String reason) {
        Order order = myOrder(orderId);
        String why = reason == null || reason.isBlank() ? "Người mua hủy đơn" : reason.trim();
        if (BUYER_CANCELLABLE.contains(order.getStatus())) {
            cancel(order, "BUYER", why);
            return;
        }
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw bad("Đơn hàng đã được giao cho đơn vị vận chuyển, không thể hủy");
        }
        if (order.getCancelRequestedAt() != null) throw bad("Bạn đã gửi yêu cầu hủy, vui lòng chờ Người bán phản hồi");
        if (order.getCancelRequestRejectedReason() != null) throw bad("Người bán đã từ chối yêu cầu hủy đơn này");
        order.setCancelRequestedAt(LocalDateTime.now());
        order.setCancelRequestReason(NotificationService.cut(why, 300));
        trackingService.add(order, "CANCEL_REQUESTED", "Người mua đã gửi yêu cầu hủy đơn: " + why, null);
    }

    /** "Da nhan duoc hang": the buyer confirms delivery themselves. */
    public OrderResponse confirmReceived(Long orderId) {
        Order order = myOrder(orderId);
        if (order.getStatus() != OrderStatus.SHIPPING) {
            throw new ApiException("Chỉ xác nhận được khi đơn hàng đang giao", HttpStatus.BAD_REQUEST);
        }
        markDelivered(order, "Người mua đã xác nhận nhận được hàng");
        return getMyOrder(orderId);
    }

    /** "Mua lai": puts the order's still-available items back in the cart. */
    public Map<String, Object> rebuy(Long orderId) {
        Order order = myOrder(orderId);
        List<Long> cartItemIds = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            Product p = item.getProduct();
            ProductVariant variant = item.getVariantId() != null ? variantRepository.findById(item.getVariantId()).orElse(null) : null;
            boolean variantGone = item.getVariantId() != null && variant == null;
            int stock = variant != null ? variant.getStock() : p.getStock();
            if (!p.isActive() || !p.getShop().isActive() || variantGone || stock <= 0
                    || (variant == null && !p.getVariants().isEmpty())) {
                skipped.add(item.getProductNameSnapshot());
                continue;
            }
            AddToCartRequest req = new AddToCartRequest();
            req.setProductId(p.getId());
            req.setVariantId(variant != null ? variant.getId() : null);
            req.setQuantity(Math.min(item.getQuantity(), stock));
            try {
                CartItemResponse added = cartService.addItem(req);
                cartItemIds.add(added.getId());
            } catch (ApiException e) {
                skipped.add(item.getProductNameSnapshot()); // e.g. the cart already holds all remaining stock
            }
        }
        return Map.of("cartItemIds", cartItemIds, "skipped", skipped);
    }

    /**
     * Cancels an order and undoes what placing it did: stock and sold count go
     * back, voucher uses are released, and the payment is closed (or marked
     * refunded if it had been paid online). by = BUYER | SELLER | SYSTEM.
     */
    public void cancel(Order order, String by, String reason) {
        if (order.getStatus() == OrderStatus.CANCELLED) return;
        // booked with GHN but not picked up yet: cancel the booking too
        if ("GHN".equals(order.getShippingGateway()) && order.getStatus() == OrderStatus.CONFIRMED && order.getTrackingNumber() != null) {
            ghnService.cancel(order.getTrackingNumber());
        }
        Map<Long, Integer> all = order.getItems().stream().collect(Collectors.toMap(OrderItem::getId, OrderItem::getQuantity));
        restock(order, all, InventoryReason.CANCEL);
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(LocalDateTime.now());
        order.setCancelledBy(by);
        order.setCancelReason(reason);

        if (order.getVoucher() != null) releaseUse(order.getVoucher());
        // the platform voucher was used once for the whole checkout: release it when its last order goes
        if (order.getPlatformVoucher() != null && order.getCheckoutCode() != null) {
            boolean othersAlive = orderRepository.findByCheckoutCode(order.getCheckoutCode()).stream()
                    .anyMatch(o -> !o.getId().equals(order.getId()) && o.getStatus() != OrderStatus.CANCELLED);
            if (!othersAlive) releaseUse(order.getPlatformVoucher());
        }

        Payment payment = order.getPayment();
        if (payment != null) {
            boolean paid = payment.getStatus() == PaymentStatus.PAID;
            payment.setStatus(paid ? PaymentStatus.REFUNDED : PaymentStatus.CANCELLED);
            if (paid) refundService.create(order, null, order.getTotal(), Refund.Source.ORDER_CANCELLED, "Hủy đơn: " + reason);
        }
        String who = switch (by) {
            case "SELLER" -> "Người bán";
            case "SYSTEM" -> "Hệ thống";
            default -> "Người mua";
        };
        trackingService.add(order, "CANCELLED", "Đơn hàng đã bị hủy bởi " + who + ": " + reason, null);
        orderRepository.save(order);
    }

    /** Puts quantities of the given order lines back in stock (cancellation, approved returns). */
    public void restock(Order order, Map<Long, Integer> qtyByOrderItemId, InventoryReason reason) {
        for (OrderItem item : order.getItems()) {
            Integer qty = qtyByOrderItemId.get(item.getId());
            if (qty == null || qty <= 0) continue;
            stockService.putBack(item.getProduct(), item.getVariantId(), item.getVariantNameSnapshot(), qty, reason, order.getCode());
            // units bought at the flash price go back to the flash sale quota, if it's still the same flash sale
            Product p = item.getProduct();
            if (item.isFlashSale() && p.getFlashSaleStart() != null && !order.getCreatedAt().isBefore(p.getFlashSaleStart())) {
                productRepository.releaseFlashSale(p.getId(), qty);
            }
        }
    }

    private void releaseUse(Voucher v) {
        v.setUsedCount(Math.max(0, v.getUsedCount() - 1));
        voucherRepository.save(v);
    }

    private void markDelivered(Order order, String title) {
        order.setStatus(OrderStatus.DELIVERED);
        order.setDeliveredAt(LocalDateTime.now());
        // cash on delivery is collected when the parcel is delivered
        Payment payment = order.getPayment();
        if (payment != null && payment.getStatus() == PaymentStatus.PENDING) {
            payment.setStatus(PaymentStatus.PAID);
            payment.setPaidAt(LocalDateTime.now());
        }
        trackingService.add(order, "DELIVERED", title, order.getShippingProvince());
        orderRepository.save(order);
    }

    private Order myOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException("Đơn hàng không tồn tại", HttpStatus.NOT_FOUND));
        if (!order.getBuyer().getId().equals(CurrentUser.id())) {
            throw new ApiException("Bạn không có quyền xem đơn hàng này", HttpStatus.FORBIDDEN);
        }
        return order;
    }

    // ==== Seller-side order management ====

    public List<OrderResponse> shopOrders() {
        Shop shop = myShop();
        List<Order> orders = orderRepository.findByShopIdOrderByCreatedAtDesc(shop.getId());
        Map<Long, ReturnRequest> latestReturn = latestReturns(orders.stream().map(Order::getId).toList());
        return orders.stream().map(o -> {
            OrderResponse r = OrderResponse.from(o);
            if (latestReturn.containsKey(o.getId())) r.setReturnRequest(ReturnResponse.from(latestReturn.get(o.getId())));
            return r;
        }).collect(Collectors.toList());
    }

    public OrderResponse updateStatus(Long orderId, OrderStatus newStatus, String reason) {
        Shop shop = myShop();
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException("Đơn hàng không tồn tại", HttpStatus.NOT_FOUND));
        if (!order.getShop().getId().equals(shop.getId())) {
            throw new ApiException("Bạn không có quyền cập nhật đơn hàng này", HttpStatus.FORBIDDEN);
        }
        if (!SELLER_TRANSITIONS.getOrDefault(order.getStatus(), Set.of()).contains(newStatus)) {
            throw new ApiException("Không thể chuyển đơn hàng sang trạng thái này", HttpStatus.BAD_REQUEST);
        }

        LocalDateTime now = LocalDateTime.now();
        switch (newStatus) {
            case CANCELLED -> cancel(order, "SELLER", reason == null || reason.isBlank() ? "Người bán hủy đơn" : reason.trim());
            case CONFIRMED -> {
                order.setStatus(OrderStatus.CONFIRMED);
                order.setConfirmedAt(now);
                trackingService.add(order, "CONFIRMED", "Người bán đã xác nhận đơn hàng và đang chuẩn bị hàng", shop.getProvince());
            }
            case SHIPPING -> handOver(order);
            case DELIVERED -> markDelivered(order, "Giao hàng thành công");
            default -> throw new ApiException("Không thể chuyển đơn hàng sang trạng thái này", HttpStatus.BAD_REQUEST);
        }
        return OrderResponse.from(orderRepository.save(order));
    }

    private void handOver(Order order) {
        // handing the parcel over answers a pending cancel request: too late to cancel
        if (order.getCancelRequestedAt() != null) {
            rejectCancel(order, "Người bán đã giao hàng cho đơn vị vận chuyển");
        }
        order.setStatus(OrderStatus.SHIPPING);
        order.setShippedAt(LocalDateTime.now());
        if (order.getTrackingNumber() == null) {
            order.setTrackingNumber(TrackingService.newTrackingNumber(order.getCarrier()));
        }
        trackingService.add(order, "PICKED_UP", "Đơn vị vận chuyển "
                + ShippingService.carrierName(order.getCarrier()) + " đã lấy hàng. Mã vận đơn: "
                + order.getTrackingNumber(), order.getShop().getProvince());
    }

    // ---- a real carrier (GHN webhook) reports a step ----

    /** The carrier picked the parcel up: the order is now shipping. */
    public void carrierPickedUp(Order order) {
        if (order.getStatus() != OrderStatus.CONFIRMED) return;
        handOver(order);
        orderRepository.save(order);
    }

    /** The carrier delivered the parcel. */
    public void carrierDelivered(Order order) {
        if (order.getStatus() == OrderStatus.CONFIRMED) handOver(order);
        if (order.getStatus() != OrderStatus.SHIPPING) return;
        markDelivered(order, "Giao hàng thành công (" + ShippingService.carrierName(order.getCarrier()) + " xác nhận)");
    }

    /** Order page for the seller: everything plus the tracking timeline and the latest return. */
    public OrderResponse shopOrder(Long orderId) {
        Order order = ofMyShop(orderId);
        OrderResponse r = OrderResponse.from(order);
        ReturnRequest rr = latestReturns(List.of(orderId)).get(orderId);
        if (rr != null) r.setReturnRequest(ReturnResponse.from(rr));
        r.setEvents(trackingService.events(orderId).stream().map(OrderResponse.Event::from).toList());
        return r;
    }

    // ---- "Chuan bi hang": pack, choose how the carrier gets the parcel, get the tracking number ----

    public OrderResponse prepare(Long orderId, String pickupMethod, LocalDate pickupDate) {
        Order order = ofMyShop(orderId);
        prepareNow(order, pickupMethod, pickupDate);
        return OrderResponse.from(order);
    }

    private void prepareNow(Order order, String pickupMethod, LocalDate pickupDate) {
        if (order.getStatus() != OrderStatus.CONFIRMED) throw bad("Chỉ chuẩn bị hàng cho đơn đã xác nhận");
        if (order.getCancelRequestedAt() != null) throw bad("Đơn có yêu cầu hủy đang chờ, vui lòng phản hồi trước");
        String method = pickupMethod == null ? "PICKUP" : pickupMethod;
        if (!method.equals("PICKUP") && !method.equals("DROPOFF")) throw bad("Hình thức giao hàng không hợp lệ");
        LocalDate today = LocalDate.now();
        LocalDate date = pickupDate != null ? pickupDate : today.plusDays(method.equals("PICKUP") ? 1 : 0);
        if (date.isBefore(today) || date.isAfter(today.plusDays(7))) throw bad("Ngày lấy hàng phải trong 7 ngày tới");

        boolean first = order.getPackedAt() == null;
        order.setPackedAt(LocalDateTime.now());
        order.setPickupMethod(method);
        order.setPickupDate(date);
        if (order.getTrackingNumber() == null) {
            if (ghnService.handles(order)) {
                // book the parcel with the real GHN: its order code is the tracking number, its webhooks the timeline
                GhnService.Booking b = ghnService.createOrder(order);
                order.setTrackingNumber(b.orderCode());
                order.setShippingGateway("GHN");
                order.setGatewayStatus("ready_to_pick");
                order.setGatewayFee(b.fee());
            } else {
                order.setTrackingNumber(TrackingService.newTrackingNumber(order.getCarrier()));
            }
        }
        String carrier = ShippingService.carrierName(order.getCarrier());
        String d = String.format("%02d/%02d", date.getDayOfMonth(), date.getMonthValue());
        trackingService.add(order, "PACKED", (first ? "Người bán đã đóng gói đơn hàng. " : "Người bán đã đổi lịch giao hàng. ")
                + (method.equals("PICKUP") ? carrier + " sẽ đến lấy hàng ngày " + d : "Người bán sẽ gửi hàng tại bưu cục " + carrier + " ngày " + d)
                + ". Mã vận đơn: " + order.getTrackingNumber(), order.getShop().getProvince());
    }

    // ---- shipping updates from the seller (carrier hub scans are also simulated by TrackingService) ----

    public static final Map<String, String> SHIPPING_STAGES = Map.of(
            "IN_TRANSIT", "Đơn hàng đang được trung chuyển",
            "OUT_FOR_DELIVERY", "Đơn hàng đang được giao đến bạn",
            "DELIVERY_FAILED", "Giao hàng không thành công");

    public OrderResponse addShippingEvent(Long orderId, String stage, String note, String location) {
        Order order = ofMyShop(orderId);
        if (order.getStatus() != OrderStatus.SHIPPING) throw bad("Chỉ cập nhật hành trình khi đơn đang giao");
        String title = SHIPPING_STAGES.get(stage);
        if (title == null) throw bad("Trạng thái vận chuyển không hợp lệ");
        String detail = note == null || note.isBlank() ? null : NotificationService.cut(note.trim(), 200);
        if ("DELIVERY_FAILED".equals(stage) && detail == null) throw bad("Vui lòng nhập lý do giao không thành công");
        trackingService.add(order, stage, detail != null ? title + ": " + detail : title,
                location == null || location.isBlank() ? null : NotificationService.cut(location.trim(), 100));
        return shopOrder(orderId);
    }

    // ---- buyer's cancel request ----

    public OrderResponse acceptCancelRequest(Long orderId) {
        Order order = ofMyShop(orderId);
        if (order.getCancelRequestedAt() == null || order.getStatus() != OrderStatus.CONFIRMED) {
            throw bad("Đơn hàng không có yêu cầu hủy đang chờ");
        }
        trackingService.add(order, "CANCEL_REQUEST_ACCEPTED", "Người bán đã đồng ý yêu cầu hủy đơn", null);
        cancel(order, "BUYER", order.getCancelRequestReason());
        return OrderResponse.from(order);
    }

    public OrderResponse rejectCancelRequest(Long orderId, String reason) {
        Order order = ofMyShop(orderId);
        if (order.getCancelRequestedAt() == null || order.getStatus() != OrderStatus.CONFIRMED) {
            throw bad("Đơn hàng không có yêu cầu hủy đang chờ");
        }
        if (reason == null || reason.isBlank()) throw bad("Vui lòng nhập lý do từ chối");
        rejectCancel(order, reason.trim());
        return OrderResponse.from(order);
    }

    private void rejectCancel(Order order, String reason) {
        order.setCancelRequestedAt(null);
        order.setCancelRequestRejectedReason(NotificationService.cut(reason, 300));
        trackingService.add(order, "CANCEL_REQUEST_REJECTED", "Người bán từ chối yêu cầu hủy đơn: " + reason, null);
    }

    // ---- bulk actions on the orders list ----

    public enum BulkOrderAction { CONFIRM, PREPARE, SHIP }

    /** Returns { updated, skipped: [{ id, code, reason }] }; orders that can't take the action are skipped. */
    public Map<String, Object> bulk(List<Long> ids, BulkOrderAction action, String pickupMethod, LocalDate pickupDate) {
        if (ids == null || ids.isEmpty()) throw bad("Vui lòng chọn đơn hàng");
        if (action == null) throw bad("Thao tác không hợp lệ");
        Long shopId = myShop().getId();
        int updated = 0;
        List<Map<String, Object>> skipped = new ArrayList<>();
        for (Order o : orderRepository.findAllById(new LinkedHashSet<>(ids))) {
            if (!o.getShop().getId().equals(shopId)) continue;
            // called on this instance (not the proxy), so a refused order doesn't roll back the others
            try {
                switch (action) {
                    case CONFIRM -> updateStatus(o.getId(), OrderStatus.CONFIRMED, null);
                    case PREPARE -> prepareNow(o, pickupMethod, pickupDate);
                    case SHIP -> updateStatus(o.getId(), OrderStatus.SHIPPING, null);
                }
                updated++;
            } catch (ApiException e) {
                skipped.add(Map.of("id", o.getId(), "code", o.getCode(), "reason", e.getMessage()));
            }
        }
        return Map.of("updated", updated, "skipped", skipped);
    }

    // ---- shipping labels ("In phieu giao hang") ----

    /** Data for printing labels; only packed (or already shipping) orders have a tracking number to print. */
    public Map<String, Object> labels(List<Long> ids) {
        Shop shop = myShop();
        List<Map<String, Object>> labels = new ArrayList<>();
        List<Map<String, Object>> skipped = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (Order o : orderRepository.findAllById(new LinkedHashSet<>(ids == null ? List.of() : ids))) {
            if (!o.getShop().getId().equals(shop.getId())) continue;
            boolean printable = o.getTrackingNumber() != null
                    && (o.getStatus() == OrderStatus.CONFIRMED || o.getStatus() == OrderStatus.SHIPPING);
            if (!printable) {
                skipped.add(Map.of("id", o.getId(), "code", o.getCode(), "reason",
                        o.getStatus() == OrderStatus.CONFIRMED ? "Chưa chuẩn bị hàng" : "Đơn không ở trạng thái cần giao"));
                continue;
            }
            o.setLabelPrintedAt(now);
            boolean cod = o.getPayment() != null && o.getPayment().getMethod() == PaymentMethod.COD;
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("orderId", o.getId());
            l.put("orderCode", o.getCode());
            l.put("trackingNumber", o.getTrackingNumber());
            l.put("carrierName", ShippingService.carrierName(o.getCarrier()));
            l.put("shippingMethodName", ShippingService.methodName(o.getShippingMethod()));
            l.put("senderName", shop.getName());
            l.put("senderPhone", shop.getPickupPhone());
            l.put("senderAddress", shop.getPickupAddress() + (shop.getProvince() != null ? ", " + shop.getProvince() : ""));
            l.put("receiver", o.getShippingAddressSnapshot()); // "Name (phone), street, ward, district, province"
            l.put("receiverProvince", o.getShippingProvince());
            l.put("codAmount", cod ? o.getTotal() : BigDecimal.ZERO);
            l.put("note", o.getNote());
            l.put("pickupMethod", o.getPickupMethod());
            l.put("createdAt", o.getCreatedAt());
            l.put("items", o.getItems().stream().map(i -> Map.of(
                    "name", i.getProductNameSnapshot(),
                    "variant", i.getVariantNameSnapshot() != null ? i.getVariantNameSnapshot() : "",
                    "quantity", i.getQuantity())).toList());
            labels.add(l);
        }
        return Map.of("labels", labels, "skipped", skipped);
    }

    private Order ofMyShop(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException("Đơn hàng không tồn tại", HttpStatus.NOT_FOUND));
        if (!order.getShop().getId().equals(myShop().getId())) {
            throw new ApiException("Đơn hàng không tồn tại", HttpStatus.NOT_FOUND);
        }
        return order;
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }

    private Shop myShop() {
        return shopRepository.findBySellerId(CurrentUser.id())
                .orElseThrow(() -> new ApiException("Bạn chưa có shop", HttpStatus.BAD_REQUEST));
    }
}
