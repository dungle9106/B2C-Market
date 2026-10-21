package com.marketplace.backend.service;

import com.marketplace.backend.dto.response.PageResponse;
import com.marketplace.backend.dto.response.ReturnResponse;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.DisputeMessageRepository;
import com.marketplace.backend.repository.ReturnRequestRepository;
import com.marketplace.backend.repository.ShopRepository;
import com.marketplace.backend.repository.UserRepository;
import com.marketplace.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * "Xu ly tranh chap giua buyer va seller": a return request the buyer escalated after the seller refused it.
 * The three parties talk in one thread (with photos as evidence); the seller is expected to answer within
 * 48 hours; the admin decides: full refund, partial refund (a compromise) or no refund (ReturnService.resolve).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class DisputeService {

    private static final Set<ReturnStatus> RESOLVED = EnumSet.of(ReturnStatus.REFUNDED, ReturnStatus.CLOSED);

    private final ReturnRequestRepository returnRepository;
    private final DisputeMessageRepository messageRepository;
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final ReturnService returnService;
    private final RefundService refundService;
    private final NotificationService notificationService;
    private final AdminAuditService auditService;

    // ==================== thread (buyer / seller / admin) ====================

    @Transactional(readOnly = true)
    public List<Map<String, Object>> messages(Long returnId, DisputeMessage.Side side) {
        ReturnRequest rr = accessible(returnId, side);
        return messageRepository.findByReturnRequestIdOrderByIdAsc(rr.getId()).stream().map(DisputeService::view).toList();
    }

    public Map<String, Object> post(Long returnId, DisputeMessage.Side side, String content, List<String> attachments) {
        ReturnRequest rr = accessible(returnId, side);
        if (rr.getStatus() != ReturnStatus.ESCALATED) throw bad("Tranh chấp đã kết thúc");
        String text = content == null ? "" : content.trim();
        List<String> files = attachments == null ? List.of() : attachments.stream().filter(u -> u != null && u.startsWith("/uploads/")).distinct().toList();
        if (text.isEmpty() && files.isEmpty()) throw bad("Vui lòng nhập nội dung hoặc đính kèm bằng chứng");
        if (text.length() > 2000) throw bad("Nội dung tối đa 2000 ký tự");
        if (files.size() > 5) throw bad("Tối đa 5 tệp đính kèm mỗi tin nhắn");
        User me = userRepository.findById(CurrentUser.id()).orElseThrow();
        DisputeMessage m = messageRepository.save(DisputeMessage.builder().returnRequestId(rr.getId()).side(side).authorId(me.getId())
                .authorName(side == DisputeMessage.Side.ADMIN ? "Shoppi" : me.getFullName()).content(text.isEmpty() ? null : text)
                .attachments(new ArrayList<>(files)).build());

        // tell the other parties
        Order o = rr.getOrder();
        String title = "Tranh chấp đơn " + o.getCode() + ": " + (side == DisputeMessage.Side.ADMIN ? "Shoppi" : side == DisputeMessage.Side.BUYER ? "người mua" : "người bán") + " vừa phản hồi";
        String preview = text.isEmpty() ? "[Bằng chứng đính kèm]" : NotificationService.cut(text, 120);
        if (side != DisputeMessage.Side.BUYER) {
            notificationService.notify(o.getBuyer().getId(), NotificationType.SUPPORT, title, preview, "/orders/" + o.getId());
        }
        if (side != DisputeMessage.Side.SELLER) {
            notificationService.notify(o.getShop().getSeller().getId(), NotificationType.SUPPORT, title, preview, "/seller/returns?tab=disputed");
        }
        if (side != DisputeMessage.Side.ADMIN) {
            auditService.notifyAdmins(AdminPermission.ORDERS, title, preview, "/admin/disputes?id=" + rr.getId());
        }
        return view(m);
    }

    // ==================== admin ====================

    /** status: OPEN (waiting for a decision), RESOLVED, ALL */
    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> list(String status, int page, int size) {
        String st = status == null ? "OPEN" : status.toUpperCase();
        Set<ReturnStatus> statuses = switch (st) {
            case "OPEN" -> EnumSet.of(ReturnStatus.ESCALATED);
            case "RESOLVED" -> RESOLVED;
            case "ALL" -> EnumSet.of(ReturnStatus.ESCALATED, ReturnStatus.REFUNDED, ReturnStatus.CLOSED);
            default -> throw bad("Trạng thái không hợp lệ");
        };
        // open disputes: the longest waiting first
        Sort sort = st.equals("OPEN") ? Sort.by("escalatedAt").ascending().and(Sort.by("id")) : Sort.by("id").descending();
        return PageResponse.of(returnRepository.disputes(statuses, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), sort)), this::row);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> counts() {
        long open = returnRepository.countByComplaintIsNotNullAndStatus(ReturnStatus.ESCALATED);
        long resolved = returnRepository.countByComplaintIsNotNullAndStatus(ReturnStatus.REFUNDED)
                + returnRepository.countByComplaintIsNotNullAndStatus(ReturnStatus.CLOSED);
        return AdminUserService.map("OPEN", open, "RESOLVED", resolved, "ALL", open + resolved);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long id) {
        ReturnRequest rr = find(id);
        if (rr.getComplaint() == null) throw new ApiException("Không phải tranh chấp", HttpStatus.NOT_FOUND);
        Map<String, Object> m = row(rr);
        m.put("request", ReturnResponse.from(rr));
        Order o = rr.getOrder();
        m.put("order", AdminUserService.map("id", o.getId(), "code", o.getCode(), "total", o.getTotal(), "subtotal", o.getSubtotal(),
                "status", o.getStatus().name(), "createdAt", o.getCreatedAt(), "deliveredAt", o.getDeliveredAt(),
                "paymentMethod", o.getPayment() == null ? null : o.getPayment().getMethod().name(),
                "paymentStatus", o.getPayment() == null ? null : o.getPayment().getStatus().name(),
                "buyerEmail", o.getBuyer().getEmail(), "buyerPhone", o.getBuyer().getPhone(), "sellerId", o.getShop().getSeller().getId()));
        m.put("messages", messageRepository.findByReturnRequestIdOrderByIdAsc(id).stream().map(DisputeService::view).toList());
        m.put("refunds", refundService.forOrder(o.getId()));
        // the shop's track record helps the decision
        long shopDisputes = returnRepository.findByOrderShopIdOrderByCreatedAtDesc(o.getShop().getId()).stream().filter(r -> r.getComplaint() != null).count();
        m.put("shopDisputes", shopDisputes);
        return m;
    }

    public Map<String, Object> resolve(Long id, String decision, BigDecimal amount, String note) {
        ReturnRequest rr = find(id);
        returnService.resolve(id, decision, amount, note);
        String d = rr.getDecision();
        auditService.log("RESOLVE_DISPUTE", "ORDER", rr.getOrder().getId(), rr.getOrder().getCode(),
                ("REFUND_FULL".equals(d) ? "Hoàn toàn bộ " : "REFUND_PARTIAL".equals(d) ? "Hoàn một phần " : "Từ chối hoàn tiền")
                        + ("REJECT".equals(d) ? "" : NotificationService.vnd(rr.getRefundAmount())) + " – " + note.trim());
        // the conclusion also goes into the thread, so all parties read the same thing
        messageRepository.save(DisputeMessage.builder().returnRequestId(id).side(DisputeMessage.Side.ADMIN).authorId(CurrentUser.id())
                .authorName("Shoppi").content("Kết luận: " + note.trim()).build());
        return detail(id);
    }

    // ==================== helpers ====================

    private Map<String, Object> row(ReturnRequest rr) {
        Order o = rr.getOrder();
        LocalDateTime now = LocalDateTime.now();
        boolean sellerAnswered = messageRepository.existsByReturnRequestIdAndSide(rr.getId(), DisputeMessage.Side.SELLER);
        Map<String, Object> m = AdminUserService.map("id", rr.getId(), "orderId", o.getId(), "orderCode", o.getCode(),
                "shopName", o.getShop().getName(), "buyerName", o.getBuyer().getFullName(), "type", rr.getType().name(),
                "reason", rr.getReason().name(), "refundAmount", rr.getRefundAmount(), "orderTotal", o.getTotal(), "status", rr.getStatus().name(),
                "complaint", rr.getComplaint(), "sellerNote", rr.getSellerNote(), "adminNote", rr.getAdminNote(), "decision", rr.getDecision(),
                "createdAt", rr.getCreatedAt(), "escalatedAt", rr.getEscalatedAt(), "resolvedAt", rr.getResolvedAt(),
                "sellerResponseDeadline", rr.getSellerResponseDeadline(), "sellerAnswered", sellerAnswered,
                "messageCount", messageRepository.countByReturnRequestId(rr.getId()),
                "evidenceCount", rr.getEvidenceUrls().size());
        // how long it has been waiting, and whether the seller let the deadline pass
        m.put("waitingHours", rr.getEscalatedAt() == null || RESOLVED.contains(rr.getStatus()) ? null : Duration.between(rr.getEscalatedAt(), now).toHours());
        m.put("sellerOverdue", rr.getStatus() == ReturnStatus.ESCALATED && !sellerAnswered && rr.getSellerResponseDeadline() != null
                && rr.getSellerResponseDeadline().isBefore(now));
        return m;
    }

    // buyer: own order; seller: own shop; admin: any - and only complaints
    private ReturnRequest accessible(Long id, DisputeMessage.Side side) {
        ReturnRequest rr = find(id);
        Long me = CurrentUser.id();
        boolean ok = switch (side) {
            case BUYER -> rr.getOrder().getBuyer().getId().equals(me);
            case SELLER -> shopRepository.findBySellerId(me).map(s -> s.getId().equals(rr.getOrder().getShop().getId())).orElse(false);
            case ADMIN -> true;
        };
        if (!ok || rr.getComplaint() == null) throw new ApiException("Tranh chấp không tồn tại", HttpStatus.NOT_FOUND);
        return rr;
    }

    private ReturnRequest find(Long id) {
        return returnRepository.findById(id).orElseThrow(() -> new ApiException("Yêu cầu không tồn tại", HttpStatus.NOT_FOUND));
    }

    static Map<String, Object> view(DisputeMessage m) {
        return AdminUserService.map("id", m.getId(), "side", m.getSide().name(), "authorName", m.getAuthorName(), "content", m.getContent(),
                "attachments", List.copyOf(m.getAttachments()), "createdAt", m.getCreatedAt());
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }
}
