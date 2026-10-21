package com.marketplace.backend.service;

import com.marketplace.backend.dto.request.TicketCreateRequest;
import com.marketplace.backend.dto.request.TicketReplyRequest;
import com.marketplace.backend.dto.response.PageResponse;
import com.marketplace.backend.dto.response.TicketResponse;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.*;
import com.marketplace.backend.security.CurrentUser;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * "Trung tam ho tro" tickets between a user (buyer or seller) and Shoppi's
 * support staff (admins):
 *
 *   user opens -> OPEN -> staff replies -> IN_PROGRESS / WAITING_CUSTOMER
 *     -> staff marks RESOLVED -> user confirms (CLOSED) or replies (back to OPEN)
 *     -> nobody touches it for AUTO_CLOSE_DAYS -> CLOSED
 *
 * Return/refund disputes keep their own flow (ReturnService); a ticket can
 * still point at an order for anything else that went wrong with it.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class SupportTicketService {

    public static final int AUTO_CLOSE_DAYS = 3;
    private static final Set<TicketStatus> ACTIVE = EnumSet.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS,
            TicketStatus.WAITING_CUSTOMER, TicketStatus.RESOLVED);
    private static final Map<TicketStatus, String> STATUS_NAME = Map.of(
            TicketStatus.OPEN, "Chờ xử lý",
            TicketStatus.IN_PROGRESS, "Đang xử lý",
            TicketStatus.WAITING_CUSTOMER, "Chờ bạn phản hồi",
            TicketStatus.RESOLVED, "Đã giải quyết",
            TicketStatus.CLOSED, "Đã đóng");

    private final SupportTicketRepository ticketRepository;
    private final TicketMessageRepository messageRepository;
    private final OrderRepository orderRepository;
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    // ==== requester (buyer or seller) ====

    public TicketResponse create(TicketCreateRequest req) {
        Long userId = CurrentUser.id();
        User me = userRepository.findById(userId).orElseThrow();

        Order order = null;
        if (req.getOrderId() != null) {
            Optional<Shop> myShop = shopRepository.findBySellerId(userId);
            order = orderRepository.findById(req.getOrderId())
                    // a buyer's own order, or an order placed at the seller's shop
                    .filter(o -> o.getBuyer().getId().equals(userId)
                            || myShop.map(s -> s.getId().equals(o.getShop().getId())).orElse(false))
                    .orElseThrow(() -> bad("Đơn hàng không tồn tại"));
        }
        Shop shop = null;
        if (req.getShopId() != null) {
            shop = shopRepository.findById(req.getShopId()).orElseThrow(() -> bad("Shop không tồn tại"));
        } else if (order != null && req.getCategory() == TicketCategory.REPORT) {
            shop = order.getShop();
        }

        SupportTicket t = ticketRepository.save(SupportTicket.builder()
                .code(newCode())
                .requester(me)
                .category(req.getCategory())
                .priority(defaultPriority(req.getCategory()))
                .subject(req.getSubject().trim())
                .order(order)
                .shop(shop)
                .build());
        addMessage(t, me, false, false, req.getContent().trim(), req.getAttachmentUrls());

        notificationService.notifyAdmins(NotificationType.SUPPORT, "Yêu cầu hỗ trợ mới " + t.getCode(),
                me.getFullName() + ": " + t.getSubject(), adminLink(t));
        return detailFor(t, false);
    }

    @Transactional(readOnly = true)
    public PageResponse<TicketResponse> mine(boolean active, int page, int size) {
        Set<TicketStatus> statuses = active ? ACTIVE : EnumSet.of(TicketStatus.CLOSED);
        return PageResponse.of(ticketRepository.findByRequesterIdAndStatusInOrderByUpdatedAtDesc(
                        CurrentUser.id(), statuses, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50))),
                t -> TicketResponse.from(t, false));
    }

    // not read-only: viewing the ticket also marks its notifications read
    public TicketResponse myTicket(Long id) {
        return detailFor(mine(id), false);
    }

    public TicketResponse reply(Long id, TicketReplyRequest req) {
        SupportTicket t = mine(id);
        if (t.getStatus() == TicketStatus.CLOSED) {
            throw bad("Yêu cầu đã đóng. Vui lòng tạo yêu cầu mới nếu bạn cần hỗ trợ thêm.");
        }
        User me = t.getRequester();
        addMessage(t, me, false, false, req.getContent().trim(), req.getAttachmentUrls());
        t.setLastReplyByStaff(false);
        // the requester answering puts it back in the staff's queue
        if (t.getStatus() != TicketStatus.OPEN && t.getStatus() != TicketStatus.IN_PROGRESS) {
            changeStatus(t, TicketStatus.OPEN, me.getFullName() + " đã phản hồi, yêu cầu được mở lại");
        }
        notificationService.notifyAdmins(NotificationType.SUPPORT, "Phản hồi mới cho yêu cầu " + t.getCode(),
                me.getFullName() + ": " + NotificationService.cut(req.getContent().trim(), 150), adminLink(t));
        return detailFor(t, false);
    }

    /** "Van de da duoc giai quyet": the requester closes it themselves. */
    public TicketResponse close(Long id) {
        SupportTicket t = mine(id);
        if (t.getStatus() == TicketStatus.CLOSED) throw bad("Yêu cầu đã đóng");
        changeStatus(t, TicketStatus.CLOSED, "Người gửi đã đóng yêu cầu");
        return detailFor(t, false);
    }

    public TicketResponse rate(Long id, Integer rating, String comment) {
        SupportTicket t = mine(id);
        if (t.getStatus() != TicketStatus.RESOLVED && t.getStatus() != TicketStatus.CLOSED) {
            throw bad("Chỉ có thể đánh giá khi yêu cầu đã được giải quyết");
        }
        if (t.getRating() != null) throw bad("Bạn đã đánh giá yêu cầu này");
        if (rating == null || rating < 1 || rating > 5) throw bad("Vui lòng chọn từ 1 đến 5 sao");
        t.setRating(rating);
        t.setRatingComment(comment == null || comment.isBlank() ? null : NotificationService.cut(comment.trim(), 500));
        if (t.getStatus() == TicketStatus.RESOLVED) changeStatus(t, TicketStatus.CLOSED, "Người gửi đã xác nhận và đánh giá");
        return detailFor(t, false);
    }

    // ==== staff (admin) ====

    @Transactional(readOnly = true)
    public PageResponse<TicketResponse> forAdmin(TicketStatus status, TicketCategory category, String q, int page, int size) {
        Specification<SupportTicket> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (status != null) where.add(cb.equal(root.get("status"), status));
            if (category != null) where.add(cb.equal(root.get("category"), category));
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                var requester = root.join("requester");
                where.add(cb.or(
                        cb.like(cb.lower(root.get("code")), like),
                        cb.like(cb.lower(root.get("subject")), like),
                        cb.like(cb.lower(requester.get("email")), like),
                        cb.like(cb.lower(requester.get("fullName")), like)));
            }
            // open work: most urgent first, then the ones waiting longest. Priority is stored as text,
            // so it is ranked explicitly (alphabetical order would put HIGH before URGENT).
            // Skipped for the count query, which must not be ordered.
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                if (status == TicketStatus.CLOSED || status == TicketStatus.RESOLVED) {
                    query.orderBy(cb.desc(root.get("updatedAt")));
                } else {
                    var rank = cb.selectCase(root.get("priority"))
                            .when(TicketPriority.URGENT, 0).when(TicketPriority.HIGH, 1)
                            .when(TicketPriority.NORMAL, 2).otherwise(3);
                    var done = cb.selectCase(root.get("status"))
                            .when(TicketStatus.RESOLVED, 1).when(TicketStatus.CLOSED, 2).otherwise(0);
                    query.orderBy(cb.asc(done), cb.asc(rank), cb.asc(root.get("updatedAt")));
                }
            }
            return cb.and(where.toArray(new Predicate[0]));
        };
        return PageResponse.of(ticketRepository.findAll(spec, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50))),
                t -> TicketResponse.from(t, true));
    }

    @Transactional(readOnly = true)
    public TicketResponse adminDetail(Long id) {
        return detailFor(byId(id), true);
    }

    public TicketResponse adminReply(Long id, TicketReplyRequest req) {
        SupportTicket t = byId(id);
        if (t.getStatus() == TicketStatus.CLOSED) throw bad("Yêu cầu đã đóng");
        User staff = userRepository.getReferenceById(CurrentUser.id());
        addMessage(t, staff, true, false, req.getContent().trim(), req.getAttachmentUrls());
        t.setLastReplyByStaff(true);

        TicketStatus next = req.getStatus() != null ? req.getStatus()
                : t.getStatus() == TicketStatus.OPEN ? TicketStatus.IN_PROGRESS : t.getStatus();
        if (next != t.getStatus()) changeStatus(t, next, null);

        notificationService.notify(t.getRequester().getId(), NotificationType.SUPPORT,
                "Shoppi đã phản hồi yêu cầu " + t.getCode(),
                NotificationService.cut(req.getContent().trim(), 200), requesterLink(t));
        return detailFor(t, true);
    }

    public TicketResponse adminUpdate(Long id, TicketStatus status, TicketPriority priority) {
        SupportTicket t = byId(id);
        if (priority != null) t.setPriority(priority);
        if (status != null && status != t.getStatus()) {
            changeStatus(t, status, null);
            notificationService.notify(t.getRequester().getId(), NotificationType.SUPPORT,
                    "Yêu cầu " + t.getCode() + ": " + STATUS_NAME.get(status), t.getSubject(), requesterLink(t));
        }
        t.setUpdatedAt(LocalDateTime.now());
        return detailFor(t, true);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> stats() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (TicketStatus s : TicketStatus.values()) byStatus.put(s.name(), 0L);
        for (Object[] row : ticketRepository.countByStatus()) byStatus.put(((TicketStatus) row[0]).name(), ((Number) row[1]).longValue());
        Object[] rating = ticketRepository.ratingSummary().stream().findFirst().orElse(new Object[]{null, 0L});
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("byStatus", byStatus);
        r.put("ratingAvg", rating[0]);
        r.put("ratingCount", rating[1]);
        return r;
    }

    // RESOLVED with no reaction from the requester -> CLOSED
    @Scheduled(fixedDelay = 60 * 60_000, initialDelay = 60_000)
    public void autoClose() {
        List<SupportTicket> stale = ticketRepository.findByStatusAndResolvedAtBefore(
                TicketStatus.RESOLVED, LocalDateTime.now().minusDays(AUTO_CLOSE_DAYS));
        stale.forEach(t -> changeStatus(t, TicketStatus.CLOSED,
                "Yêu cầu tự động đóng sau " + AUTO_CLOSE_DAYS + " ngày không có phản hồi"));
        if (!stale.isEmpty()) log.info("Auto-closed {} resolved support ticket(s)", stale.size());
    }

    // ==== shared ====

    private void changeStatus(SupportTicket t, TicketStatus status, String line) {
        t.setStatus(status);
        LocalDateTime now = LocalDateTime.now();
        t.setUpdatedAt(now);
        if (status == TicketStatus.RESOLVED) t.setResolvedAt(now);
        if (status == TicketStatus.CLOSED) t.setClosedAt(now);
        if (status != TicketStatus.CLOSED) t.setClosedAt(null);
        addMessage(t, null, false, true, line != null ? line : "Trạng thái: " + STATUS_NAME.get(status), null);
    }

    private void addMessage(SupportTicket t, User sender, boolean staff, boolean systemLine, String content, List<String> attachments) {
        List<String> files = attachments == null ? List.of() : attachments.stream()
                .filter(u -> u != null && u.startsWith("/uploads/")).distinct().limit(5).toList(); // only our own uploads
        messageRepository.save(TicketMessage.builder()
                .ticket(t)
                .sender(sender)
                .staff(staff)
                .systemLine(systemLine)
                .content(content)
                .attachmentUrls(new ArrayList<>(files))
                .build());
        if (!systemLine) t.setUpdatedAt(LocalDateTime.now());
    }

    private TicketResponse detailFor(SupportTicket t, boolean forAdmin) {
        TicketResponse r = TicketResponse.from(t, forAdmin);
        r.setMessages(messageRepository.findByTicketIdOrderByIdAsc(t.getId()).stream().map(TicketResponse.Message::from).toList());
        if (!forAdmin) notificationService.markReadByLink(t.getRequester().getId(), requesterLink(t));
        return r;
    }

    private static TicketPriority defaultPriority(TicketCategory c) {
        return switch (c) {
            case PAYMENT, RETURN_REFUND, REPORT -> TicketPriority.HIGH;
            case OTHER -> TicketPriority.LOW;
            default -> TicketPriority.NORMAL;
        };
    }

    private String newCode() {
        String day = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        Random random = new Random();
        String code;
        do {
            code = "HT-" + day + "-" + (1000 + random.nextInt(9000));
        } while (ticketRepository.existsByCode(code));
        return code;
    }

    // sellers read their tickets inside the seller centre, everyone else on /help
    private static String requesterLink(SupportTicket t) {
        return (t.getRequester().getRole() == Role.SELLER ? "/seller/support/" : "/help/tickets/") + t.getId();
    }

    private static String adminLink(SupportTicket t) {
        return "/admin/support?id=" + t.getId();
    }

    private SupportTicket mine(Long id) {
        return ticketRepository.findById(id)
                .filter(t -> t.getRequester().getId().equals(CurrentUser.id()))
                .orElseThrow(() -> new ApiException("Yêu cầu không tồn tại", HttpStatus.NOT_FOUND));
    }

    private SupportTicket byId(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new ApiException("Yêu cầu không tồn tại", HttpStatus.NOT_FOUND));
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }
}
