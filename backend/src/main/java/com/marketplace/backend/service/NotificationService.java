package com.marketplace.backend.service;

import com.marketplace.backend.dto.request.BroadcastRequest;
import com.marketplace.backend.dto.response.NotificationResponse;
import com.marketplace.backend.dto.response.PageResponse;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.*;
import com.marketplace.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/**
 * In-app notifications ("Thong bao"): order status, promotions, messages from
 * shops, support replies. The frontend polls /api/notifications/unread for the
 * badges, so there is no websocket to keep alive.
 *
 * The on...() hooks are called from inside other services' transactions
 * (orders, returns, vouchers). They swallow their own errors: a notification
 * must never make placing or updating an order fail.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class NotificationService {

    private final org.springframework.context.ApplicationEventPublisher events;
    private final PlatformConfig config;
    private final NotificationRepository notificationRepository;
    private final BroadcastRepository broadcastRepository;
    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final ConversationRepository conversationRepository;
    private final JdbcTemplate jdbc;

    // ==== creating ====

    public void notify(Long userId, NotificationType type, String title, String content, String link, String imageUrl) {
        if (userId == null) return;
        pushed(List.of(userId), type, title);
        notificationRepository.save(Notification.builder()
                .user(userRepository.getReferenceById(userId))
                .type(type)
                .title(cut(title, 200))
                .content(cut(content, 1000))
                .link(cut(link, 300))
                .imageUrl(imageUrl)
                .build());
    }

    // the bell updates at once for connected users (WebSocket, after commit)
    private void pushed(Collection<Long> userIds, NotificationType type, String title) {
        events.publishEvent(new com.marketplace.backend.realtime.RealtimeEvent(userIds, "notification",
                Map.of("kind", type.name(), "title", cut(title, 200))));
    }

    public void notify(Long userId, NotificationType type, String title, String content, String link) {
        notify(userId, type, title, content, link, null);
    }

    /** Like notify(), but an unread notification with the same link is refreshed instead of piling up (chat threads). */
    public void notifyCollapsed(Long userId, NotificationType type, String title, String content, String link) {
        if (userId == null) return;
        Optional<Notification> existing = notificationRepository.findFirstByUserIdAndLinkAndReadFalseOrderByIdDesc(userId, link);
        if (existing.isPresent()) {
            Notification n = existing.get();
            n.setTitle(cut(title, 200));
            n.setContent(cut(content, 1000));
            n.setCreatedAt(LocalDateTime.now()); // jumps back to the top of the list
            pushed(List.of(userId), type, title);
            return;
        }
        notify(userId, type, title, content, link);
    }

    /** Same notification for many users, in one JDBC batch. */
    public int notifyMany(Collection<Long> userIds, NotificationType type, String title, String content, String link) {
        List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return 0;
        pushed(ids, type, title);
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbc.batchUpdate("INSERT INTO notifications (user_id, type, title, content, link, is_read, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, false, ?)",
                ids, 500, (ps, id) -> {
                    ps.setLong(1, id);
                    ps.setString(2, type.name());
                    ps.setString(3, cut(title, 200));
                    ps.setString(4, cut(content, 1000));
                    ps.setString(5, cut(link, 300));
                    ps.setTimestamp(6, now);
                });
        return ids.size();
    }

    public void notifyAdmins(NotificationType type, String title, String content, String link) {
        notifyMany(userRepository.findEnabledIdsByRole(Role.ADMIN), type, title, content, link);
    }

    // ==== admin: broadcast ====

    public Map<String, Object> broadcast(BroadcastRequest req) {
        if (req.getAudience() == Role.ADMIN) throw new ApiException("Đối tượng nhận không hợp lệ", HttpStatus.BAD_REQUEST);
        String link = req.getLink() == null || req.getLink().isBlank() ? null : req.getLink().trim();
        String content = req.getContent() == null ? null : req.getContent().trim();
        // one INSERT ... SELECT instead of loading every user
        String sql = "INSERT INTO notifications (user_id, type, title, content, link, is_read, created_at) "
                + "SELECT id, ?, ?, ?, ?, false, ? FROM users WHERE enabled = true AND "
                + (req.getAudience() == null ? "role <> 'ADMIN'" : "role = ?");
        List<Object> args = new ArrayList<>(List.of(req.getType().name(), req.getTitle().trim()));
        args.add(content);
        args.add(link);
        args.add(Timestamp.valueOf(LocalDateTime.now()));
        if (req.getAudience() != null) args.add(req.getAudience().name());
        int recipients = jdbc.update(sql, args.toArray());

        Broadcast b = broadcastRepository.save(Broadcast.builder()
                .type(req.getType())
                .audience(req.getAudience())
                .title(req.getTitle().trim())
                .content(content)
                .link(link)
                .recipients(recipients)
                .createdBy(userRepository.getReferenceById(CurrentUser.id()))
                .build());
        return broadcastView(b);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> broadcasts() {
        return broadcastRepository.findTop50ByOrderByCreatedAtDesc().stream().map(this::broadcastView).toList();
    }

    private Map<String, Object> broadcastView(Broadcast b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.getId());
        m.put("type", b.getType());
        m.put("audience", b.getAudience());
        m.put("title", b.getTitle());
        m.put("content", b.getContent());
        m.put("link", b.getLink());
        m.put("recipients", b.getRecipients());
        m.put("createdAt", b.getCreatedAt());
        return m;
    }

    // ==== reading (current user) ====

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> mine(NotificationType type, int page, int size) {
        Long userId = CurrentUser.id();
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
        var result = type == null
                ? notificationRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable)
                : notificationRepository.findByUserIdAndTypeOrderByCreatedAtDescIdDesc(userId, type, pageable);
        return PageResponse.of(result, NotificationResponse::from);
    }

    /** Badges: { total, byType: { ORDER: 2, ... }, chat: unread chat messages } */
    @Transactional(readOnly = true)
    public Map<String, Object> unreadSummary() {
        Long userId = CurrentUser.id();
        Map<String, Long> byType = new LinkedHashMap<>();
        for (NotificationType t : NotificationType.values()) byType.put(t.name(), 0L);
        long total = 0;
        for (Object[] row : notificationRepository.countUnreadByType(userId)) {
            long n = ((Number) row[1]).longValue();
            byType.put(((NotificationType) row[0]).name(), n);
            total += n;
        }
        long chat = switch (CurrentUser.role()) {
            case "BUYER" -> conversationRepository.buyerUnread(userId);
            case "SELLER" -> shopRepository.findBySellerId(userId).map(s -> conversationRepository.shopUnread(s.getId())).orElse(0L);
            default -> 0L;
        };
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("total", total);
        r.put("byType", byType);
        r.put("chat", chat);
        return r;
    }

    public NotificationResponse markRead(Long id) {
        Notification n = mine(id);
        n.setRead(true);
        return NotificationResponse.from(n);
    }

    public int markAllRead(NotificationType type) {
        Long userId = CurrentUser.id();
        return type == null ? notificationRepository.markAllRead(userId) : notificationRepository.markAllRead(userId, type);
    }

    public void markReadByLink(Long userId, String link) {
        notificationRepository.markReadByLink(userId, link);
    }

    public void delete(Long id) {
        notificationRepository.delete(mine(id));
    }

    private Notification mine(Long id) {
        return notificationRepository.findById(id)
                .filter(n -> n.getUser().getId().equals(CurrentUser.id()))
                .orElseThrow(() -> new ApiException("Thông báo không tồn tại", HttpStatus.NOT_FOUND));
    }

    // ==== hooks from other features ====

    /**
     * Called by TrackingService for every step recorded on an order's timeline,
     * so order and return/refund updates reach the buyer and the seller.
     */
    public void onOrderEvent(Order o, String stage, String detail) {
        try {
            // the buyer also gets an e-mail for the main steps (EmailEventListener, after commit)
            events.publishEvent(new OrderStageEvent(o.getId(), stage, detail));
            Long buyerId = o.getBuyer().getId();
            Long sellerId = o.getShop().getSeller().getId();
            String code = o.getCode();
            String shop = o.getShop().getName();
            String buyerLink = "/orders/" + o.getId();
            String img = o.getItems().isEmpty() ? null : o.getItems().get(0).getProduct().getThumbnailUrl();
            NotificationType t = NotificationType.ORDER;

            switch (stage) {
                case "PLACED" -> {
                    if (o.getStatus() == OrderStatus.AWAITING_PAYMENT) {
                        notify(buyerId, t, "Đơn hàng " + code + " đang chờ thanh toán",
                                "Vui lòng hoàn tất thanh toán trong " + config.paymentWindowMinutes()
                                        + " phút, sau thời gian này đơn hàng sẽ tự động bị hủy.", buyerLink, img);
                    } else {
                        notify(buyerId, t, "Đặt hàng thành công",
                                "Đơn hàng " + code + " đã được đặt và đang chờ " + shop + " xác nhận.", buyerLink, img);
                        newOrderForSeller(sellerId, o, img);
                    }
                }
                case "PAID" -> {
                    notify(buyerId, t, "Thanh toán thành công", detail + " cho đơn hàng " + code + ".", buyerLink, img);
                    newOrderForSeller(sellerId, o, img);
                }
                case "CONFIRMED" -> notify(buyerId, t, "Đơn hàng đã được xác nhận",
                        shop + " đã xác nhận đơn hàng " + code + " và đang chuẩn bị hàng.", buyerLink, img);
                case "PICKED_UP" -> notify(buyerId, t, "Đơn hàng đang được vận chuyển",
                        "Đơn hàng " + code + ": " + detail, buyerLink, img);
                case "OUT_FOR_DELIVERY" -> notify(buyerId, t, "Đơn hàng sắp được giao đến bạn",
                        "Đơn hàng " + code + ": " + detail, buyerLink, img);
                case "DELIVERED" -> {
                    notify(buyerId, t, "Giao hàng thành công",
                            "Đơn hàng " + code + " đã được giao. Đánh giá sản phẩm để giúp người mua khác nhé!", buyerLink, img);
                    notify(sellerId, t, "Đơn hàng " + code + " đã giao thành công", detail, "/seller/orders", img);
                }
                case "CANCELLED" -> {
                    String by = o.getCancelledBy();
                    if (!"BUYER".equals(by)) {
                        notify(buyerId, t, "Đơn hàng " + code + " đã bị hủy", detail, buyerLink, img);
                    }
                    // an online order that was never paid was never shown to the seller
                    Payment p = o.getPayment();
                    boolean sellerSawIt = p == null || p.getMethod() == PaymentMethod.COD || p.getStatus() == PaymentStatus.REFUNDED;
                    if (!"SELLER".equals(by) && sellerSawIt) {
                        notify(sellerId, t, "Đơn hàng " + code + " đã bị hủy", detail, "/seller/orders?status=RETURNS", img);
                    }
                }
                case "CANCEL_REQUESTED" -> notify(sellerId, t, "Người mua yêu cầu hủy đơn " + code,
                        detail + ". Vui lòng chấp nhận hoặc từ chối trước khi giao hàng.", "/seller/orders?status=CANCEL_REQUESTS", img);
                case "CANCEL_REQUEST_ACCEPTED" -> notify(buyerId, t, "Yêu cầu hủy đơn đã được chấp nhận",
                        "Đơn hàng " + code + " đã được hủy theo yêu cầu của bạn.", buyerLink, img);
                case "CANCEL_REQUEST_REJECTED" -> notify(buyerId, t, "Yêu cầu hủy đơn bị từ chối", "Đơn hàng " + code + ": " + detail, buyerLink, img);
                case "PACKED" -> notify(buyerId, t, "Đơn hàng đã được đóng gói", "Đơn hàng " + code + ": " + detail, buyerLink, img);
                case "DELIVERY_FAILED" -> notify(buyerId, t, "Giao hàng không thành công",
                        "Đơn hàng " + code + ": " + detail + ". Shipper sẽ liên hệ giao lại.", buyerLink, img);
                case "RETURN_REQUESTED" -> notify(sellerId, t, "Yêu cầu Trả hàng/Hoàn tiền mới",
                        "Đơn hàng " + code + ": " + detail + ". Vui lòng phản hồi sớm.", "/seller/returns", img);
                case "COMPLAINT" -> {
                    notify(sellerId, t, "Người mua đã khiếu nại lên Shoppi",
                            "Đơn hàng " + code + " đang được Shoppi xem xét. Hãy gửi giải trình và bằng chứng trong "
                                    + config.disputeResponseHours() + " giờ.", "/seller/returns?tab=disputed", img);
                    notifyAdmins(NotificationType.SUPPORT, "Tranh chấp mới cần xử lý",
                            "Đơn hàng " + code + " của " + shop, "/admin/disputes");
                }
                case "RETURN_REJECTED" -> notify(buyerId, t, "Yêu cầu Trả hàng/Hoàn tiền bị từ chối",
                        detail + ". Nếu không đồng ý, bạn có thể khiếu nại lên Shoppi.", buyerLink, img);
                case "REFUNDED" -> {
                    notify(buyerId, t, "Hoàn tiền thành công", "Đơn hàng " + code + ": " + detail, buyerLink, img);
                    notify(sellerId, t, "Đơn hàng " + code + " đã được hoàn tiền", detail, "/seller/returns", img);
                }
                case "RETURN_CLOSED" -> {
                    // when the buyer withdrew the request themselves only the seller needs to know
                    if (!detail.startsWith("Người mua")) {
                        notify(buyerId, t, "Yêu cầu Trả hàng/Hoàn tiền đã đóng", "Đơn hàng " + code + ": " + detail, buyerLink, img);
                    }
                    notify(sellerId, t, "Yêu cầu Trả hàng/Hoàn tiền đã đóng", "Đơn hàng " + code + ": " + detail, "/seller/returns", img);
                }
                default -> { /* other timeline steps (carrier hub scans) are too noisy to notify */ }
            }
        } catch (Exception e) {
            log.warn("Could not create notification for order {} ({}): {}", o.getId(), stage, e.getMessage());
        }
    }

    private void newOrderForSeller(Long sellerId, Order o, String img) {
        notify(sellerId, NotificationType.ORDER, "Bạn có đơn hàng mới",
                "Đơn hàng " + o.getCode() + " · " + vnd(o.getTotal()) + " đang chờ xác nhận.", "/seller/orders?status=PENDING", img);
    }

    /** A shop created a voucher: tell the buyers who have ordered from it before. */
    public void onShopVoucher(Shop shop, Voucher v, Collection<Long> buyerIds) {
        try {
            notifyMany(buyerIds, NotificationType.PROMOTION, shop.getName() + " vừa tung mã giảm giá " + v.getCode(),
                    describe(v) + ". Áp dụng đến " + date(v.getEndAt()) + ", số lượng có hạn!",
                    "/search?shopId=" + shop.getId());
        } catch (Exception e) {
            log.warn("Could not notify about voucher {}: {}", v.getCode(), e.getMessage());
        }
    }

    /** A platform-wide voucher: every buyer. */
    public void onPlatformVoucher(Voucher v) {
        try {
            notifyMany(userRepository.findEnabledIdsByRole(Role.BUYER), NotificationType.PROMOTION,
                    "Shoppi Voucher mới: " + v.getCode(),
                    describe(v) + " cho mọi shop trên Shoppi. Áp dụng đến " + date(v.getEndAt()) + ".",
                    v.getCampaign() != null ? "/campaigns/" + v.getCampaign().getSlug() : "/search?q=");
        } catch (Exception e) {
            log.warn("Could not notify about voucher {}: {}", v.getCode(), e.getMessage());
        }
    }

    private static String describe(Voucher v) {
        String min = v.getMinOrderAmount() != null && v.getMinOrderAmount().signum() > 0
                ? " cho đơn từ " + vnd(v.getMinOrderAmount()) : "";
        return switch (v.getType()) {
            case PERCENT -> "Giảm " + v.getDiscountValue().stripTrailingZeros().toPlainString() + "%"
                    + (v.getMaxDiscountAmount() != null ? " (tối đa " + vnd(v.getMaxDiscountAmount()) + ")" : "") + min;
            case FIXED -> "Giảm " + vnd(v.getDiscountValue()) + min;
            case SHIPPING -> "Giảm " + vnd(v.getDiscountValue()) + " phí vận chuyển" + min;
        };
    }

    private static String date(LocalDateTime d) {
        return d == null ? "khi hết lượt" : String.format("%02d/%02d/%d", d.getDayOfMonth(), d.getMonthValue(), d.getYear());
    }

    static String vnd(BigDecimal v) {
        return "₫" + String.format("%,d", v == null ? 0 : v.longValue()).replace(',', '.');
    }

    static String cut(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
