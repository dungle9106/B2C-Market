package com.marketplace.backend.service;

import com.marketplace.backend.dto.response.OrderResponse;
import com.marketplace.backend.dto.response.PageResponse;
import com.marketplace.backend.dto.response.ReturnResponse;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.OrderRepository;
import com.marketplace.backend.repository.ReturnRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * "Theo doi toan bo don hang tren san": every order with filters, a "late" view (an order stuck at a step for
 * too long), the full detail of one order (timeline, payment, returns, refunds) and an admin cancellation.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AdminOrderService {

    // an order is late when it waited longer than this at a step
    static final int LATE_CONFIRM_HOURS = 24;    // placed, not confirmed by the seller
    static final int LATE_SHIP_HOURS = 72;       // confirmed, not handed to the carrier
    static final int LATE_DELIVERY_DAYS = 7;     // shipping, not delivered
    private static final Set<OrderStatus> ADMIN_CANCELLABLE = EnumSet.of(OrderStatus.AWAITING_PAYMENT, OrderStatus.PENDING, OrderStatus.CONFIRMED);

    private final OrderRepository orderRepository;
    private final ReturnRequestRepository returnRepository;
    private final OrderService orderService;
    private final TrackingService trackingService;
    private final RefundService refundService;
    private final AdminAuditService auditService;

    /** status: an OrderStatus, LATE or ALL; method: a PaymentMethod or null; dates inclusive (default: last 30 days). */
    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> list(String status, String method, Long shopId, String q, LocalDate from, LocalDate to,
                                                  int page, int size) {
        String st = status == null || status.isBlank() ? "ALL" : status.toUpperCase();
        OrderStatus os = null;
        boolean late = st.equals("LATE");
        if (!late && !st.equals("ALL")) os = parse(OrderStatus.class, st, "Trạng thái không hợp lệ");
        PaymentMethod pm = method == null || method.isBlank() ? null : parse(PaymentMethod.class, method.toUpperCase(), "Phương thức thanh toán không hợp lệ");
        LocalDateTime now = LocalDateTime.now();
        // a stuck order matters however old it is: the late view ignores the date filter
        LocalDateTime[] range = late ? new LocalDateTime[]{LocalDateTime.of(2000, 1, 1, 0, 0), now.plusDays(1)} : range(from, to);
        // late orders: the longest waiting first
        Sort sort = late ? Sort.by("createdAt").ascending() : Sort.by("id").descending();
        var result = orderRepository.searchForAdmin(os, late, OrderStatus.PENDING, now.minusHours(LATE_CONFIRM_HOURS),
                OrderStatus.CONFIRMED, now.minusHours(LATE_SHIP_HOURS), OrderStatus.SHIPPING, now.minusDays(LATE_DELIVERY_DAYS),
                pm, shopId, range[0], range[1], q == null ? "" : q.trim().toLowerCase(),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), sort));
        return PageResponse.of(result, this::row);
    }

    /** Counts per status and money totals for the period, plus how many orders are late right now. */
    @Transactional(readOnly = true)
    public Map<String, Object> summary(LocalDate from, LocalDate to) {
        LocalDateTime[] range = range(from, to);
        Map<String, Object> byStatus = new LinkedHashMap<>();
        for (OrderStatus s : OrderStatus.values()) byStatus.put(s.name(), 0L);
        long count = 0;
        BigDecimal gmv = BigDecimal.ZERO;
        long cancelled = 0;
        for (Object[] r : orderRepository.statusTotals(range[0], range[1])) {
            OrderStatus s = (OrderStatus) r[0];
            long n = ((Number) r[1]).longValue();
            byStatus.put(s.name(), n);
            count += n;
            if (s == OrderStatus.CANCELLED) cancelled += n;
            if (s != OrderStatus.CANCELLED && s != OrderStatus.AWAITING_PAYMENT) gmv = gmv.add(new BigDecimal(r[2].toString()));
        }
        LocalDateTime now = LocalDateTime.now();
        long late = orderRepository.searchForAdmin(null, true, OrderStatus.PENDING, now.minusHours(LATE_CONFIRM_HOURS),
                OrderStatus.CONFIRMED, now.minusHours(LATE_SHIP_HOURS), OrderStatus.SHIPPING, now.minusDays(LATE_DELIVERY_DAYS),
                null, null, LocalDateTime.of(2000, 1, 1, 0, 0), now.plusDays(1), "", PageRequest.of(0, 1)).getTotalElements();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("from", range[0].toLocalDate());
        m.put("to", range[1].toLocalDate().minusDays(1));
        m.put("orders", count);
        m.put("gmv", gmv);
        m.put("avgOrderValue", count - cancelled == 0 ? BigDecimal.ZERO : gmv.divide(BigDecimal.valueOf(Math.max(1, count - cancelled)), 0, java.math.RoundingMode.HALF_UP));
        m.put("cancelRate", count == 0 ? 0 : Math.round(cancelled * 1000.0 / count) / 10.0);
        m.put("byStatus", byStatus);
        m.put("late", late);
        m.put("lateRules", AdminUserService.map("confirmHours", LATE_CONFIRM_HOURS, "shipHours", LATE_SHIP_HOURS, "deliveryDays", LATE_DELIVERY_DAYS));
        return m;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long id) {
        Order o = find(id);
        OrderResponse r = OrderResponse.from(o);
        r.setEvents(trackingService.events(id).stream().map(OrderResponse.Event::from).toList());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("order", r);
        m.put("buyer", AdminUserService.map("id", o.getBuyer().getId(), "fullName", o.getBuyer().getFullName(), "email", o.getBuyer().getEmail(),
                "phone", o.getBuyer().getPhone()));
        m.put("shop", AdminUserService.map("id", o.getShop().getId(), "name", o.getShop().getName(), "sellerId", o.getShop().getSeller().getId(),
                "phone", o.getShop().getPickupPhone()));
        Payment p = o.getPayment();
        m.put("payment", p == null ? null : AdminUserService.map("method", p.getMethod().name(), "status", p.getStatus().name(), "amount", p.getAmount(),
                "provider", p.getProvider(), "transactionId", p.getTransactionId(), "paidAt", p.getPaidAt()));
        m.put("returns", returnRepository.findByOrderIdOrderByCreatedAtDesc(id).stream().map(ReturnResponse::from).toList());
        m.put("refunds", refundService.forOrder(id));
        m.put("refundable", p != null && p.getStatus() == PaymentStatus.PAID ? o.getTotal().subtract(refundService.refundedTotal(id)) : BigDecimal.ZERO);
        m.put("late", lateReason(o, LocalDateTime.now()));
        m.put("canCancel", ADMIN_CANCELLABLE.contains(o.getStatus()));
        m.put("settlementId", o.getSettlementId());
        m.put("history", auditService.forTarget("ORDER", id, 0, 20).getItems());
        return m;
    }

    /** Before the parcel leaves (e.g. fraud, an unreachable seller): stock back, online payment refunded. */
    public Map<String, Object> cancel(Long id, String reason) {
        Order o = find(id);
        if (!ADMIN_CANCELLABLE.contains(o.getStatus())) throw bad("Chỉ hủy được đơn chưa giao cho đơn vị vận chuyển");
        String r = reason == null ? "" : reason.trim();
        if (r.length() < 5) throw bad("Vui lòng nhập lý do (ít nhất 5 ký tự)");
        orderService.cancel(o, "SYSTEM", "Shoppi hủy đơn – " + r);
        auditService.log("CANCEL_ORDER", "ORDER", o.getId(), o.getCode(), r);
        return detail(id);
    }

    // ==================== helpers ====================

    private Map<String, Object> row(Order o) {
        Payment p = o.getPayment();
        return AdminUserService.map("id", o.getId(), "code", o.getCode(), "status", o.getStatus().name(), "total", o.getTotal(),
                "itemCount", o.getItems().size(), "createdAt", o.getCreatedAt(),
                "buyerName", o.getBuyer().getFullName(), "buyerEmail", o.getBuyer().getEmail(),
                "shopId", o.getShop().getId(), "shopName", o.getShop().getName(),
                "paymentMethod", p == null ? null : p.getMethod().name(), "paymentStatus", p == null ? null : p.getStatus().name(),
                "late", lateReason(o, LocalDateTime.now()));
    }

    /** Why the order counts as late, or null. */
    static String lateReason(Order o, LocalDateTime now) {
        return switch (o.getStatus()) {
            case PENDING -> o.getCreatedAt().isBefore(now.minusHours(LATE_CONFIRM_HOURS)) ? "Quá " + LATE_CONFIRM_HOURS + " giờ chưa xác nhận" : null;
            case CONFIRMED -> (o.getConfirmedAt() != null ? o.getConfirmedAt() : o.getCreatedAt()).isBefore(now.minusHours(LATE_SHIP_HOURS))
                    ? "Quá " + LATE_SHIP_HOURS + " giờ chưa giao cho ĐVVC" : null;
            case SHIPPING -> (o.getShippedAt() != null ? o.getShippedAt() : o.getCreatedAt()).isBefore(now.minusDays(LATE_DELIVERY_DAYS))
                    ? "Quá " + LATE_DELIVERY_DAYS + " ngày chưa giao xong" : null;
            default -> null;
        };
    }

    private static LocalDateTime[] range(LocalDate from, LocalDate to) {
        LocalDate t = to != null ? to : LocalDate.now();
        LocalDate f = from != null ? from : t.minusDays(29);
        if (f.isAfter(t)) throw bad("Ngày bắt đầu phải trước ngày kết thúc");
        return new LocalDateTime[]{f.atStartOfDay(), t.plusDays(1).atStartOfDay()};
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String v, String error) {
        try {
            return Enum.valueOf(type, v);
        } catch (IllegalArgumentException e) {
            throw bad(error);
        }
    }

    private Order find(Long id) {
        return orderRepository.findById(id).orElseThrow(() -> new ApiException("Đơn hàng không tồn tại", HttpStatus.NOT_FOUND));
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }
}
