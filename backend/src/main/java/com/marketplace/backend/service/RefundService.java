package com.marketplace.backend.service;

import com.marketplace.backend.dto.response.PageResponse;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.OrderRepository;
import com.marketplace.backend.repository.RefundRepository;
import com.marketplace.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * "Quan ly hoan tien": every refund to a buyer is recorded here.
 *  - paid online (e-wallet / card / bank transfer at checkout): refunded through the (simulated) gateway at once;
 *  - cash on delivery: the finance team transfers the money and records the bank reference (PENDING until then).
 * Refunds born from returns and disputes are later deducted from the seller's statement (SettlementService);
 * refunds of cancelled orders are not, since such orders are never paid out.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class RefundService {

    private final VnpayService vnpayService;
    private final RefundRepository refundRepository;
    private final OrderRepository orderRepository;
    private final NotificationService notificationService;
    private final AdminAuditService auditService;

    /** Records a refund; returnRequestId is set for returns/disputes. */
    public Refund create(Order order, Long returnRequestId, BigDecimal amount, Refund.Source source, String note) {
        Payment p = order.getPayment();
        boolean online = p != null && p.getMethod() != PaymentMethod.COD;
        String reference = online ? "GW-RF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase() : null;
        // paid through the real VNPay gateway: refund through its API; if VNPay refuses, finance transfers it by hand
        if (online && p.getMethod() == PaymentMethod.VNPAY && p.getGatewayTransactionNo() != null) {
            try {
                reference = vnpayService.refund(order, amount, note);
            } catch (Exception e) {
                online = false;
                note = cut((note == null ? "" : note + " · ") + e.getMessage(), 500);
            }
        }
        Refund r = refundRepository.save(Refund.builder()
                .order(order).returnRequestId(returnRequestId).amount(amount).source(source)
                .channel(online ? Refund.Channel.ORIGINAL_METHOD : Refund.Channel.BANK_TRANSFER)
                .status(online ? Refund.Status.COMPLETED : Refund.Status.PENDING)
                .reference(online ? reference : null)
                .completedAt(online ? LocalDateTime.now() : null)
                .note(note)
                .build());
        if (!online) {
            notificationService.notify(order.getBuyer().getId(), NotificationType.ORDER, "Shoppi đang xử lý hoàn tiền",
                    "Đơn " + order.getCode() + " thanh toán khi nhận hàng: Shoppi sẽ chuyển khoản hoàn " + NotificationService.vnd(amount)
                            + " cho bạn trong 3-5 ngày làm việc. Hãy kiểm tra số tài khoản trong hồ sơ hoặc liên hệ Trung tâm hỗ trợ.",
                    "/orders/" + order.getId());
            auditService.notifyAdmins(AdminPermission.FINANCE, "Hoàn tiền COD cần chuyển khoản",
                    "Đơn " + order.getCode() + ": hoàn " + NotificationService.vnd(amount) + " cho người mua", "/admin/refunds?status=PENDING");
        }
        return r;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> forOrder(Long orderId) {
        return refundRepository.findByOrderIdOrderByIdAsc(orderId).stream().map(RefundService::view).toList();
    }

    /** Total already refunded (or being refunded) for an order. */
    @Transactional(readOnly = true)
    public BigDecimal refundedTotal(Long orderId) {
        return refundRepository.findByOrderIdOrderByIdAsc(orderId).stream().filter(r -> r.getStatus() != Refund.Status.FAILED)
                .map(Refund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ==================== admin ====================

    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> list(String status, String q, int page, int size) {
        Refund.Status st = null;
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            try {
                st = Refund.Status.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw bad("Trạng thái không hợp lệ");
            }
        }
        // the work queue (pending) oldest first
        Sort sort = st == Refund.Status.PENDING ? Sort.by("createdAt").ascending() : Sort.by("id").descending();
        return PageResponse.of(refundRepository.search(st, q == null ? "" : q.trim().toLowerCase(),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), sort)), RefundService::view);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> counts() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Refund.Status s : Refund.Status.values()) m.put(s.name(), refundRepository.countByStatus(s));
        return m;
    }

    /** The finance team transferred the money (COD), or retried a failed refund: reference required. */
    public Map<String, Object> complete(Long id, String reference, String note) {
        Refund r = find(id);
        if (r.getStatus() == Refund.Status.COMPLETED) throw bad("Khoản hoàn tiền đã hoàn tất");
        if (reference == null || reference.trim().length() < 3) throw bad("Vui lòng nhập mã giao dịch chuyển khoản");
        r.setStatus(Refund.Status.COMPLETED);
        r.setReference(reference.trim());
        if (note != null && !note.isBlank()) r.setNote(note.trim());
        r.setCompletedAt(LocalDateTime.now());
        r.setProcessedBy(CurrentUser.id());
        notificationService.notify(r.getOrder().getBuyer().getId(), NotificationType.ORDER, "Đã hoàn tiền thành công",
                "Shoppi đã chuyển khoản hoàn " + NotificationService.vnd(r.getAmount()) + " cho đơn " + r.getOrder().getCode()
                        + " (mã giao dịch " + r.getReference() + ")", "/orders/" + r.getOrder().getId());
        auditService.log("COMPLETE_REFUND", "ORDER", r.getOrder().getId(), r.getOrder().getCode(),
                "Hoàn " + NotificationService.vnd(r.getAmount()) + " – mã " + r.getReference());
        return view(r);
    }

    /** e.g. the buyer's bank account is wrong: the refund waits for a new attempt. */
    public Map<String, Object> fail(Long id, String reason) {
        Refund r = find(id);
        if (r.getStatus() != Refund.Status.PENDING) throw bad("Chỉ đánh dấu thất bại cho khoản đang chờ xử lý");
        if (reason == null || reason.trim().length() < 5) throw bad("Vui lòng nhập lý do (ít nhất 5 ký tự)");
        r.setStatus(Refund.Status.FAILED);
        r.setNote(reason.trim());
        r.setProcessedBy(CurrentUser.id());
        notificationService.notify(r.getOrder().getBuyer().getId(), NotificationType.ORDER, "Hoàn tiền chưa thành công",
                "Đơn " + r.getOrder().getCode() + ": " + reason.trim() + ". Vui lòng liên hệ Trung tâm hỗ trợ để cập nhật thông tin nhận tiền.",
                "/support");
        auditService.log("FAIL_REFUND", "ORDER", r.getOrder().getId(), r.getOrder().getCode(), reason.trim());
        return view(r);
    }

    /** A goodwill / manual refund decided by an admin (amount ≤ what is left of the order total). */
    public Map<String, Object> manual(Long orderId, BigDecimal amount, String note) {
        Order o = orderRepository.findById(orderId).orElseThrow(() -> new ApiException("Đơn hàng không tồn tại", HttpStatus.NOT_FOUND));
        if (o.getPayment() == null || o.getPayment().getStatus() != PaymentStatus.PAID) {
            throw bad("Chỉ hoàn tiền cho đơn đã được thanh toán");
        }
        if (amount == null || amount.signum() <= 0) throw bad("Số tiền hoàn phải lớn hơn 0");
        BigDecimal left = o.getTotal().subtract(refundedTotal(orderId));
        if (amount.compareTo(left) > 0) throw bad("Số tiền hoàn tối đa còn lại là " + NotificationService.vnd(left));
        if (note == null || note.trim().length() < 5) throw bad("Vui lòng nhập lý do (ít nhất 5 ký tự)");
        Refund r = create(o, null, amount, Refund.Source.ADMIN, note.trim());
        auditService.log("MANUAL_REFUND", "ORDER", o.getId(), o.getCode(), "Hoàn " + NotificationService.vnd(amount) + " – " + note.trim());
        return view(r);
    }

    static Map<String, Object> view(Refund r) {
        Order o = r.getOrder();
        return AdminUserService.map("id", r.getId(), "orderId", o.getId(), "orderCode", o.getCode(), "shopName", o.getShop().getName(),
                "buyerName", o.getBuyer().getFullName(), "buyerPhone", o.getBuyer().getPhone(),
                "paymentMethod", o.getPayment() == null ? null : o.getPayment().getMethod().name(),
                "amount", r.getAmount(), "source", r.getSource().name(), "channel", r.getChannel().name(), "status", r.getStatus().name(),
                "reference", r.getReference(), "note", r.getNote(), "returnRequestId", r.getReturnRequestId(),
                "settlementId", r.getSettlementId(), "createdAt", r.getCreatedAt(), "completedAt", r.getCompletedAt());
    }

    private static String cut(String s, int max) {
        return s == null ? null : s.length() > max ? s.substring(0, max) : s;
    }

    private Refund find(Long id) {
        return refundRepository.findById(id).orElseThrow(() -> new ApiException("Khoản hoàn tiền không tồn tại", HttpStatus.NOT_FOUND));
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }
}
