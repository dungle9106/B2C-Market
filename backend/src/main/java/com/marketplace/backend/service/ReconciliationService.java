package com.marketplace.backend.service;

import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.OrderRepository;
import com.marketplace.backend.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * "Doi soat thanh toan": compares what the payment records say with the orders and refunds, for a period.
 * Totals show the money that came in (online, COD) and went out (refunds); anomalies list the orders whose
 * payment state does not match their order state, so the finance team can fix them one by one.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReconciliationService {

    static final int REFUND_PENDING_DAYS = 3;

    private final PlatformConfig config;
    private final OrderRepository orderRepository;
    private final RefundRepository refundRepository;

    public Map<String, Object> report(LocalDate from, LocalDate to) {
        LocalDate t = to != null ? to : LocalDate.now();
        LocalDate f = from != null ? from : t.minusDays(29);
        if (f.isAfter(t)) throw new ApiException("Ngày bắt đầu phải trước ngày kết thúc", HttpStatus.BAD_REQUEST);
        LocalDateTime start = f.atStartOfDay(), end = t.plusDays(1).atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        List<Order> orders = orderRepository.withPayment(start, end);
        Map<Long, List<Refund>> refundsOf = new HashMap<>();
        if (!orders.isEmpty()) {
            refundRepository.findByOrderIdIn(orders.stream().map(Order::getId).toList())
                    .forEach(r -> refundsOf.computeIfAbsent(r.getOrder().getId(), k -> new ArrayList<>()).add(r));
        }

        BigDecimal online = BigDecimal.ZERO, cod = BigDecimal.ZERO, codPending = BigDecimal.ZERO;
        long onlineCount = 0, codCount = 0;
        Map<String, BigDecimal> byMethod = new LinkedHashMap<>();
        List<Map<String, Object>> anomalies = new ArrayList<>();
        for (Order o : orders) {
            Payment p = o.getPayment();
            boolean isCod = p.getMethod() == PaymentMethod.COD;
            // money actually received: paid, or paid and refunded later (the refund is counted on the other side)
            boolean received = p.getStatus() == PaymentStatus.PAID || p.getStatus() == PaymentStatus.REFUNDED && p.getPaidAt() != null;
            if (received) {
                if (isCod) { cod = cod.add(p.getAmount()); codCount++; } else { online = online.add(p.getAmount()); onlineCount++; }
                byMethod.merge(p.getMethod().name(), p.getAmount(), BigDecimal::add);
            } else if (isCod && p.getStatus() == PaymentStatus.PENDING && o.getStatus() != OrderStatus.CANCELLED) {
                codPending = codPending.add(p.getAmount());
            }
            List<Refund> refunds = refundsOf.getOrDefault(o.getId(), List.of());
            BigDecimal refunded = refunds.stream().filter(r -> r.getStatus() != Refund.Status.FAILED).map(Refund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

            if (o.getStatus() == OrderStatus.CANCELLED && p.getStatus() == PaymentStatus.PAID) {
                anomalies.add(anomaly("PAID_BUT_CANCELLED", "high", o, "Đơn đã hủy nhưng thanh toán vẫn ở trạng thái đã thu, chưa hoàn tiền"));
            }
            if (p.getAmount().compareTo(o.getTotal()) != 0) {
                anomalies.add(anomaly("AMOUNT_MISMATCH", "high", o, "Số tiền thanh toán " + NotificationService.vnd(p.getAmount())
                        + " khác tổng đơn " + NotificationService.vnd(o.getTotal())));
            }
            if (o.getStatus() == OrderStatus.AWAITING_PAYMENT && o.getCreatedAt().isBefore(now.minusMinutes(config.paymentWindowMinutes()))) {
                anomalies.add(anomaly("STUCK_AWAITING", "medium", o, "Chờ thanh toán quá " + config.paymentWindowMinutes() + " phút nhưng chưa bị hủy tự động"));
            }
            if (p.getStatus() == PaymentStatus.REFUNDED && refunds.isEmpty()) {
                anomalies.add(anomaly("REFUND_MISSING", "medium", o, "Thanh toán đã chuyển sang hoàn tiền nhưng không có giao dịch hoàn tiền nào"));
            }
            if (refunded.compareTo(o.getTotal()) > 0) {
                anomalies.add(anomaly("OVER_REFUNDED", "high", o, "Tổng hoàn " + NotificationService.vnd(refunded) + " vượt quá giá trị đơn"));
            }
            if (o.getStatus() == OrderStatus.DELIVERED && p.getStatus() != PaymentStatus.PAID) {
                anomalies.add(anomaly("DELIVERED_UNPAID", "high", o, "Đơn đã giao nhưng thanh toán ở trạng thái " + p.getStatus().name()));
            }
        }
        // refunds waiting for a bank transfer for too long (any order date)
        for (Refund r : refundRepository.findByStatusAndCreatedAtBefore(Refund.Status.PENDING, now.minusDays(REFUND_PENDING_DAYS))) {
            Map<String, Object> a = anomaly("REFUND_PENDING_LONG", "medium", r.getOrder(),
                    "Khoản hoàn " + NotificationService.vnd(r.getAmount()) + " chờ chuyển khoản quá " + REFUND_PENDING_DAYS + " ngày");
            a.put("refundId", r.getId());
            anomalies.add(a);
        }
        anomalies.sort(Comparator.comparing((Map<String, Object> m) -> "high".equals(m.get("severity")) ? 0 : 1));

        BigDecimal refundsDone = refundRepository.sumByStatus(Refund.Status.COMPLETED, start, end);
        BigDecimal refundsPending = refundRepository.sumByStatus(Refund.Status.PENDING, start, end);
        Map<String, Long> byType = new LinkedHashMap<>();
        anomalies.forEach(a -> byType.merge((String) a.get("type"), 1L, Long::sum));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("from", f);
        m.put("to", t);
        m.put("totals", AdminUserService.map("onlineCollected", online, "onlineCount", onlineCount, "codCollected", cod, "codCount", codCount,
                "codPending", codPending, "byMethod", byMethod, "refundsCompleted", refundsDone, "refundsPending", refundsPending,
                "net", online.add(cod).subtract(refundsDone)));
        m.put("anomalyCounts", byType);
        m.put("anomalies", anomalies);
        return m;
    }

    private static Map<String, Object> anomaly(String type, String severity, Order o, String message) {
        Payment p = o.getPayment();
        return AdminUserService.map("type", type, "severity", severity, "orderId", o.getId(), "orderCode", o.getCode(),
                "orderStatus", o.getStatus().name(), "paymentMethod", p == null ? null : p.getMethod().name(),
                "paymentStatus", p == null ? null : p.getStatus().name(), "amount", o.getTotal(), "createdAt", o.getCreatedAt(), "message", message);
    }
}
