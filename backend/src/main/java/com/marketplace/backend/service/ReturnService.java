package com.marketplace.backend.service;

import com.marketplace.backend.dto.request.ReturnCreateRequest;
import com.marketplace.backend.dto.response.ReturnResponse;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.OrderRepository;
import com.marketplace.backend.repository.ReturnRequestRepository;
import com.marketplace.backend.repository.ShopRepository;
import com.marketplace.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * "Tra hang / Hoan tien" and complaints:
 *
 *   buyer requests (within the return window, with evidence)
 *     -> seller approves  -> REFUNDED (items restocked for RETURN_AND_REFUND)
 *     -> seller rejects   -> REJECTED -> buyer escalates (khieu nai) -> ESCALATED
 *                                          -> admin refunds -> REFUNDED
 *                                          -> admin rejects -> CLOSED
 *   the buyer may withdraw any open request -> CLOSED
 *
 * Money is simulated: "refunded" only updates statuses (no gateway).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ReturnService {

    private static final Set<ReturnStatus> OPEN = EnumSet.of(ReturnStatus.REQUESTED, ReturnStatus.REJECTED, ReturnStatus.ESCALATED);

    private final PlatformConfig config;
    private final RefundService refundService;
    private final NotificationService notificationService;
    private final ReturnRequestRepository returnRepository;
    private final OrderRepository orderRepository;
    private final ShopRepository shopRepository;
    private final OrderService orderService;
    private final TrackingService trackingService;

    // ==== buyer ====

    public ReturnResponse create(Long orderId, ReturnCreateRequest req) {
        Order order = orderRepository.findById(orderId)
                .filter(o -> o.getBuyer().getId().equals(CurrentUser.id()))
                .orElseThrow(() -> new ApiException("Đơn hàng không tồn tại", HttpStatus.NOT_FOUND));
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw bad("Chỉ có thể yêu cầu trả hàng/hoàn tiền cho đơn hàng đã giao");
        }
        LocalDateTime deadline = OrderService.returnDeadline(order);
        if (deadline == null || LocalDateTime.now().isAfter(deadline)) {
            throw bad("Đơn hàng đã hết thời hạn đổi trả");
        }
        boolean hasActive = returnRepository.findByOrderIdOrderByCreatedAtDesc(orderId).stream()
                .anyMatch(r -> r.getStatus() != ReturnStatus.CLOSED);
        if (hasActive) throw bad("Đơn hàng này đã có yêu cầu trả hàng/hoàn tiền");

        Map<Long, OrderItem> lines = order.getItems().stream().collect(Collectors.toMap(OrderItem::getId, i -> i));
        Map<Long, Integer> qty = new LinkedHashMap<>();
        for (ReturnCreateRequest.Item it : req.getItems()) {
            OrderItem line = lines.get(it.getOrderItemId());
            if (line == null) throw bad("Sản phẩm không thuộc đơn hàng này");
            if (line.getProduct().getReturnDays() == null || line.getProduct().getReturnDays() <= 0) {
                throw bad("Sản phẩm \"" + line.getProductNameSnapshot() + "\" không hỗ trợ đổi trả");
            }
            int q = qty.merge(line.getId(), it.getQuantity(), Integer::sum);
            if (q > line.getQuantity()) throw bad("Số lượng trả vượt quá số lượng đã mua");
        }

        List<String> evidence = req.getEvidenceUrls() == null ? List.of() : req.getEvidenceUrls().stream()
                .filter(u -> u != null && u.startsWith("/uploads/")).distinct().toList(); // only our own uploads
        if (evidence.isEmpty()) throw bad("Vui lòng thêm ít nhất 1 ảnh hoặc video làm bằng chứng");

        // whole order back -> everything paid (incl. shipping); otherwise the items' prices
        boolean everything = order.getItems().stream().allMatch(i -> Objects.equals(qty.get(i.getId()), i.getQuantity()));
        BigDecimal refund = everything ? order.getTotal()
                : qty.entrySet().stream()
                    .map(e -> lines.get(e.getKey()).getPriceAtPurchase().multiply(BigDecimal.valueOf(e.getValue())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .min(order.getTotal());

        ReturnRequest rr = returnRepository.save(ReturnRequest.builder()
                .order(order)
                .type(req.getType())
                .reason(req.getReason())
                .description(req.getDescription() == null ? null : req.getDescription().trim())
                .evidenceUrls(new ArrayList<>(evidence))
                .items(qty.entrySet().stream().map(e -> new ReturnRequest.ReturnItem(e.getKey(), e.getValue()))
                        .collect(Collectors.toCollection(ArrayList::new)))
                .refundAmount(refund)
                .status(ReturnStatus.REQUESTED)
                .build());
        order.setStatus(OrderStatus.RETURN_REQUESTED);
        trackingService.add(order, "RETURN_REQUESTED", "Người mua đã gửi yêu cầu "
                + (req.getType() == ReturnType.REFUND_ONLY ? "hoàn tiền" : "trả hàng/hoàn tiền") + " " + vnd(refund), null);
        return ReturnResponse.from(rr);
    }

    /** Buyer withdraws the request; the order goes back to "delivered". */
    public ReturnResponse withdraw(Long returnId) {
        ReturnRequest rr = mine(returnId);
        if (!OPEN.contains(rr.getStatus())) throw bad("Yêu cầu đã được xử lý xong");
        close(rr, ReturnStatus.CLOSED, "Người mua đã rút yêu cầu trả hàng/hoàn tiền");
        return ReturnResponse.from(rr);
    }

    /** "Khieu nai": after the seller rejected, ask the platform to decide. */
    public ReturnResponse escalate(Long returnId, String complaint) {
        ReturnRequest rr = mine(returnId);
        if (rr.getStatus() != ReturnStatus.REJECTED) throw bad("Chỉ có thể khiếu nại khi Người bán từ chối yêu cầu");
        if (complaint == null || complaint.isBlank()) throw bad("Vui lòng nhập nội dung khiếu nại");
        rr.setStatus(ReturnStatus.ESCALATED);
        rr.setComplaint(complaint.trim());
        // the dispute starts: the seller has 48 hours to answer with evidence (the COMPLAINT event notifies everyone)
        rr.setEscalatedAt(LocalDateTime.now());
        rr.setSellerResponseDeadline(LocalDateTime.now().plusHours(config.disputeResponseHours()));
        rr.getOrder().setStatus(OrderStatus.RETURN_REQUESTED);
        trackingService.add(rr.getOrder(), "COMPLAINT", "Người mua đã gửi khiếu nại lên Shoppi, đang chờ xử lý", null);
        return ReturnResponse.from(rr);
    }

    // ==== seller ====

    @Transactional(readOnly = true)
    public List<ReturnResponse> forMyShop() {
        return returnRepository.findByOrderShopIdOrderByCreatedAtDesc(myShop().getId()).stream().map(ReturnResponse::from).toList();
    }

    public ReturnResponse approve(Long returnId) {
        ReturnRequest rr = ofMyShop(returnId);
        if (rr.getStatus() != ReturnStatus.REQUESTED) throw bad("Yêu cầu không còn chờ xử lý");
        refund(rr, "Người bán đã chấp nhận yêu cầu");
        return ReturnResponse.from(rr);
    }

    public ReturnResponse reject(Long returnId, String note) {
        ReturnRequest rr = ofMyShop(returnId);
        if (rr.getStatus() != ReturnStatus.REQUESTED) throw bad("Yêu cầu không còn chờ xử lý");
        if (note == null || note.isBlank()) throw bad("Vui lòng nhập lý do từ chối");
        rr.setStatus(ReturnStatus.REJECTED);
        rr.setSellerNote(note.trim());
        rr.getOrder().setStatus(OrderStatus.DELIVERED); // the buyer can still escalate
        trackingService.add(rr.getOrder(), "RETURN_REJECTED", "Người bán từ chối yêu cầu trả hàng/hoàn tiền: " + note.trim(), null);
        return ReturnResponse.from(rr);
    }

    // ==== admin ====

    @Transactional(readOnly = true)
    public List<ReturnResponse> forAdmin(boolean onlyOpen) {
        Set<ReturnStatus> statuses = onlyOpen ? EnumSet.of(ReturnStatus.ESCALATED) : EnumSet.allOf(ReturnStatus.class);
        // a complaint = a request the buyer escalated (it keeps its complaint text after being resolved)
        return returnRepository.findByStatusInOrderByCreatedAtAsc(statuses).stream()
                .filter(r -> r.getComplaint() != null)
                .map(ReturnResponse::from).toList();
    }

    public ReturnResponse resolve(Long returnId, String decision, String note) {
        return resolve(returnId, decision, null, note);
    }

    /**
     * decision: REFUND_FULL (buyer wins; "REFUND" accepted too), REFUND_PARTIAL (a compromise: the buyer keeps the
     * items and gets `amount`, less than requested) or REJECT (seller wins).
     */
    public ReturnResponse resolve(Long returnId, String decision, BigDecimal amount, String note) {
        ReturnRequest rr = returnRepository.findById(returnId)
                .orElseThrow(() -> new ApiException("Yêu cầu không tồn tại", HttpStatus.NOT_FOUND));
        if (rr.getStatus() != ReturnStatus.ESCALATED) throw bad("Khiếu nại này không còn chờ xử lý");
        if (note == null || note.isBlank()) throw bad("Vui lòng nhập kết luận xử lý");
        rr.setAdminNote(note.trim());
        String d = "REFUND".equals(decision) ? "REFUND_FULL" : decision;
        switch (d == null ? "" : d) {
            case "REFUND_FULL" -> refund(rr, "Shoppi đã giải quyết khiếu nại có lợi cho Người mua", false);
            case "REFUND_PARTIAL" -> {
                if (amount == null || amount.signum() <= 0 || amount.compareTo(rr.getRefundAmount()) >= 0) {
                    throw bad("Số tiền hoàn một phần phải lớn hơn 0 và nhỏ hơn " + vnd(rr.getRefundAmount()));
                }
                rr.setRefundAmount(amount);
                refund(rr, "Shoppi đã giải quyết khiếu nại: hoàn một phần", true);
            }
            case "REJECT" -> close(rr, ReturnStatus.CLOSED, "Shoppi đã giải quyết khiếu nại: không chấp nhận hoàn tiền. " + note.trim());
            default -> throw bad("Quyết định không hợp lệ");
        }
        rr.setDecision(d);
        String outcome = switch (d) {
            case "REFUND_FULL" -> "hoàn " + vnd(rr.getRefundAmount()) + " cho người mua";
            case "REFUND_PARTIAL" -> "hoàn một phần " + vnd(rr.getRefundAmount()) + " cho người mua";
            default -> "không chấp nhận hoàn tiền";
        };
        Order o = rr.getOrder();
        notificationService.notify(o.getShop().getSeller().getId(), NotificationType.SUPPORT, "Kết quả khiếu nại đơn " + o.getCode(),
                "Shoppi quyết định " + outcome + ". " + note.trim(), "/seller/returns?tab=disputed");
        return ReturnResponse.from(rr);
    }

    // ==== shared ====

    private void refund(ReturnRequest rr, String title) {
        refund(rr, title, false);
    }

    // partial: a compromise refund, the buyer keeps the items (no restock) and the order stays delivered
    private void refund(ReturnRequest rr, String title, boolean partial) {
        Order order = rr.getOrder();
        if (!partial && rr.getType() == ReturnType.RETURN_AND_REFUND) {
            orderService.restock(order, rr.getItems().stream()
                    .collect(Collectors.toMap(ReturnRequest.ReturnItem::getOrderItemId, ReturnRequest.ReturnItem::getQuantity)),
                    InventoryReason.RETURN);
        }
        rr.setStatus(ReturnStatus.REFUNDED);
        rr.setResolvedAt(LocalDateTime.now());
        order.setStatus(partial ? OrderStatus.DELIVERED : OrderStatus.REFUNDED);
        Payment payment = order.getPayment();
        if (payment != null && rr.getRefundAmount().compareTo(order.getTotal()) >= 0) {
            payment.setStatus(PaymentStatus.REFUNDED);
        }
        refundService.create(order, rr.getId(), rr.getRefundAmount(),
                rr.getComplaint() != null ? Refund.Source.DISPUTE : Refund.Source.RETURN, title);
        trackingService.add(order, "REFUNDED", title + ". Đã hoàn " + vnd(rr.getRefundAmount()) + " cho Người mua", null);
    }

    private void close(ReturnRequest rr, ReturnStatus status, String title) {
        rr.setStatus(status);
        rr.setResolvedAt(LocalDateTime.now());
        rr.getOrder().setStatus(OrderStatus.DELIVERED);
        trackingService.add(rr.getOrder(), "RETURN_CLOSED", title, null);
    }

    private ReturnRequest mine(Long id) {
        return returnRepository.findById(id)
                .filter(r -> r.getOrder().getBuyer().getId().equals(CurrentUser.id()))
                .orElseThrow(() -> new ApiException("Yêu cầu không tồn tại", HttpStatus.NOT_FOUND));
    }

    private ReturnRequest ofMyShop(Long id) {
        Long shopId = myShop().getId();
        return returnRepository.findById(id)
                .filter(r -> r.getOrder().getShop().getId().equals(shopId))
                .orElseThrow(() -> new ApiException("Yêu cầu không tồn tại", HttpStatus.NOT_FOUND));
    }

    private Shop myShop() {
        return shopRepository.findBySellerId(CurrentUser.id())
                .orElseThrow(() -> new ApiException("Bạn chưa có shop", HttpStatus.BAD_REQUEST));
    }

    private static String vnd(BigDecimal v) {
        return "₫" + String.format("%,d", v.longValue()).replace(',', '.');
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }
}
