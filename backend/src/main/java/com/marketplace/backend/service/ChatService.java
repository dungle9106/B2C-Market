package com.marketplace.backend.service;

import com.marketplace.backend.dto.request.SendMessageRequest;
import com.marketplace.backend.dto.response.ChatMessageResponse;
import com.marketplace.backend.dto.response.ConversationResponse;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.*;
import com.marketplace.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.marketplace.backend.util.ReplyPlaceholders;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "Chat voi Shop": one conversation per (buyer, shop). The buyer side is
 * reached through /api/buyer/chats, the shop side through /api/seller/chats;
 * every method takes the side so ownership is checked the same way for both.
 *
 * Delivery is by polling (the open thread asks for messages after the last id
 * it has every few seconds), which keeps the stack free of websockets.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ChatService {

    private static final int PAGE = 30;
    private static final long RESPONSE_WINDOW_MINUTES = 24 * 60;
    private static final Set<OrderStatus> NOT_A_SALE = EnumSet.of(OrderStatus.CANCELLED, OrderStatus.REFUNDED, OrderStatus.AWAITING_PAYMENT);

    private final org.springframework.context.ApplicationEventPublisher events;
    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final ShopRepository shopRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final ReviewRepository reviewRepository;
    private final ReplySuggestService suggestService;

    /** "Chat ngay" on a product / shop / order: returns the existing thread with that shop or opens one. */
    public ConversationResponse start(Long shopId) {
        if (shopId == null) throw new ApiException("Thiếu shop", HttpStatus.BAD_REQUEST);
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ApiException("Shop không tồn tại", HttpStatus.NOT_FOUND));
        Long buyerId = CurrentUser.id();
        Conversation c = conversationRepository.findByBuyerIdAndShopId(buyerId, shopId).orElseGet(() -> {
            if (!shop.isActive()) throw new ApiException("Shop đang tạm ngưng hoạt động", HttpStatus.BAD_REQUEST);
            return conversationRepository.save(Conversation.builder()
                    .buyer(userRepository.getReferenceById(buyerId))
                    .shop(shop)
                    .build());
        });
        return ConversationResponse.from(c, ChatSide.BUYER);
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> list(ChatSide side) {
        List<Conversation> list = side == ChatSide.BUYER
                ? conversationRepository.forBuyer(CurrentUser.id())
                : conversationRepository.forShop(myShop().getId());
        return list.stream().map(c -> ConversationResponse.from(c, side)).toList();
    }

    @Transactional(readOnly = true)
    public ConversationResponse get(ChatSide side, Long conversationId) {
        return ConversationResponse.from(owned(side, conversationId), side);
    }

    /**
     * afterId: only messages newer than this (polling). beforeId: the page of
     * older messages ("xem tin nhan cu"). Neither: the latest page.
     * Result is always oldest-first.
     */
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> messages(ChatSide side, Long conversationId, Long afterId, Long beforeId) {
        Conversation c = owned(side, conversationId);
        List<ChatMessage> list;
        if (afterId != null) {
            list = messageRepository.findByConversationIdAndIdGreaterThanOrderByIdAsc(c.getId(), afterId);
        } else {
            list = new ArrayList<>(beforeId != null
                    ? messageRepository.findByConversationIdAndIdLessThanOrderByIdDesc(c.getId(), beforeId, PageRequest.of(0, PAGE))
                    : messageRepository.findByConversationIdOrderByIdDesc(c.getId(), PageRequest.of(0, PAGE)));
            Collections.reverse(list);
        }
        return list.stream().map(ChatMessageResponse::from).toList();
    }

    public ChatMessageResponse send(ChatSide side, Long conversationId, SendMessageRequest req) {
        Conversation c = owned(side, conversationId);
        String content = req.getContent() == null ? null : req.getContent().trim();
        if (content != null && content.isEmpty()) content = null;
        String imageUrl = req.getImageUrl();
        if (imageUrl != null && !imageUrl.startsWith("/uploads/")) {
            throw new ApiException("Ảnh không hợp lệ", HttpStatus.BAD_REQUEST); // only our own uploads
        }
        if (content == null && imageUrl == null && req.getProductId() == null && req.getOrderId() == null) {
            throw new ApiException("Tin nhắn trống", HttpStatus.BAD_REQUEST);
        }
        if (side == ChatSide.BUYER && !c.getShop().isActive()) {
            throw new ApiException("Shop đang tạm ngưng hoạt động", HttpStatus.BAD_REQUEST);
        }

        Product product = null;
        if (req.getProductId() != null) {
            product = productRepository.findById(req.getProductId())
                    .filter(p -> p.getShop().getId().equals(c.getShop().getId()))
                    .orElseThrow(() -> new ApiException("Sản phẩm không thuộc shop này", HttpStatus.BAD_REQUEST));
        }
        Order order = null;
        if (req.getOrderId() != null) {
            order = orderRepository.findById(req.getOrderId())
                    .filter(o -> o.getShop().getId().equals(c.getShop().getId())
                            && o.getBuyer().getId().equals(c.getBuyer().getId()))
                    .orElseThrow(() -> new ApiException("Đơn hàng không thuộc cuộc trò chuyện này", HttpStatus.BAD_REQUEST));
        }

        ChatMessage m = messageRepository.save(ChatMessage.builder()
                .conversation(c)
                .sender(userRepository.getReferenceById(CurrentUser.id()))
                .senderSide(side)
                .content(content)
                .imageUrl(imageUrl)
                .product(product)
                .order(order)
                .build());

        String preview = preview(m);
        c.setLastMessage(NotificationService.cut(preview, 300));
        c.setLastMessageAt(m.getCreatedAt());
        if (side == ChatSide.BUYER) {
            c.setShopUnread(c.getShopUnread() + 1);
            c.setBuyerUnread(0);
            c.setAwaitingReply(true);
            notificationService.notifyCollapsed(c.getShop().getSeller().getId(), NotificationType.MESSAGE,
                    "Tin nhắn mới từ " + c.getBuyer().getFullName(), preview, sellerLink(c));
            autoReply(c);
        } else {
            c.setBuyerUnread(c.getBuyerUnread() + 1);
            c.setShopUnread(0);
            c.setAwaitingReply(false);
            notificationService.notifyCollapsed(c.getBuyer().getId(), NotificationType.MESSAGE,
                    "Tin nhắn mới từ " + c.getShop().getName(), preview, buyerLink(c));
        }
        // the other side's open chat window reloads at once (WebSocket)
        Long to = side == ChatSide.BUYER ? c.getShop().getSeller().getId() : c.getBuyer().getId();
        events.publishEvent(new com.marketplace.backend.realtime.RealtimeEvent(List.of(to), "chat",
                Map.of("conversationId", c.getId(), "messageId", m.getId())));
        return ChatMessageResponse.from(m);
    }

    /**
     * "Tin nhan tu dong" after a buyer's message: the away message outside working hours (at most every
     * 12 hours per thread), otherwise the welcome message on the first message of a thread.
     * Auto messages don't mark the thread as answered and don't count in the response rate.
     */
    private void autoReply(Conversation c) {
        Shop shop = c.getShop();
        String text = null;
        if (shop.isChatAwayEnabled() && shop.getChatAwayMessage() != null
                && !CustomerCareService.withinHours(shop, LocalTime.now())
                && !messageRepository.existsByConversationIdAndAutoReplyTrueAndCreatedAtAfter(c.getId(), LocalDateTime.now().minusHours(12))) {
            text = shop.getChatAwayMessage();
        } else if (shop.isChatAutoReplyEnabled() && shop.getChatAutoReplyMessage() != null
                && messageRepository.countByConversationId(c.getId()) == 1) {
            text = shop.getChatAutoReplyMessage();
        }
        if (text == null) return;
        text = ReplyPlaceholders.fill(text, c.getBuyer().getFullName(), shop.getName(), null);
        ChatMessage auto = messageRepository.save(ChatMessage.builder()
                .conversation(c)
                .sender(shop.getSeller())
                .senderSide(ChatSide.SHOP)
                .content(text)
                .autoReply(true)
                .build());
        c.setLastMessage(NotificationService.cut(text, 300));
        c.setLastMessageAt(auto.getCreatedAt());
        c.setBuyerUnread(c.getBuyerUnread() + 1);
        events.publishEvent(new com.marketplace.backend.realtime.RealtimeEvent(List.of(c.getBuyer().getId()), "chat",
                Map.of("conversationId", c.getId(), "messageId", auto.getId())));
    }

    // ==== seller tools ====

    /**
     * Chat response rate of a shop over the last 30 days. Each time a buyer writes after the shop's last
     * reply (or first), the shop is expected to answer within 24 hours; questions younger than 24 hours
     * that are still open are not counted yet. Auto-replies don't count as answers.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> responseStats(Long shopId) {
        LocalDateTime now = LocalDateTime.now();
        Map<Long, List<Object[]>> byThread = new LinkedHashMap<>();
        for (Object[] r : messageRepository.timeline(shopId, now.minusDays(30))) {
            byThread.computeIfAbsent((Long) r[0], k -> new ArrayList<>()).add(r);
        }
        long turns = 0, answered = 0, minutes = 0;
        for (List<Object[]> rows : byThread.values()) {
            LocalDateTime waiting = null;
            for (Object[] r : rows) {
                LocalDateTime at = (LocalDateTime) r[3];
                if (r[1] == ChatSide.BUYER) {
                    if (waiting == null) waiting = at;
                } else if (!(Boolean) r[2] && waiting != null) {
                    long m = Duration.between(waiting, at).toMinutes();
                    turns++;
                    if (m <= RESPONSE_WINDOW_MINUTES) {
                        answered++;
                        minutes += m;
                    }
                    waiting = null;
                }
            }
            if (waiting != null && Duration.between(waiting, now).toMinutes() > RESPONSE_WINDOW_MINUTES) turns++;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("turns", turns);
        m.put("answered", answered);
        m.put("responseRate", turns == 0 ? null : Math.round(answered * 1000.0 / turns) / 10.0);
        Long avg = answered == 0 ? null : minutes / answered;
        m.put("avgResponseMinutes", avg);
        m.put("responseTime", avg == null ? null : avg <= 60 ? "Trong vài phút" : "Trong vài giờ");
        return m;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> myResponseStats() {
        return responseStats(myShop().getId());
    }

    /** Customer panel next to a thread: who the buyer is and what they bought from this shop. */
    @Transactional(readOnly = true)
    public Map<String, Object> customer(Long conversationId) {
        Conversation c = owned(ChatSide.SHOP, conversationId);
        User buyer = c.getBuyer();
        List<Order> orders = orderRepository.findByBuyerIdAndShopIdOrderByCreatedAtDesc(buyer.getId(), c.getShop().getId());
        List<Order> real = orders.stream().filter(o -> !NOT_A_SALE.contains(o.getStatus())).toList();
        Object[] rv = reviewRepository.buyerStatsForShop(buyer.getId(), c.getShop().getId()).stream().findFirst().orElse(new Object[]{0L, null});
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("buyerId", buyer.getId());
        m.put("name", buyer.getFullName());
        m.put("memberSince", buyer.getCreatedAt());
        m.put("orderCount", real.size());
        m.put("deliveredCount", real.stream().filter(o -> o.getStatus() == OrderStatus.DELIVERED).count());
        m.put("cancelledCount", orders.stream().filter(o -> o.getStatus() == OrderStatus.CANCELLED).count());
        m.put("totalSpent", real.stream().map(Order::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add));
        m.put("reviewCount", rv[0] == null ? 0 : ((Number) rv[0]).longValue());
        m.put("reviewAvg", rv[1] == null ? null : Math.round(((Number) rv[1]).doubleValue() * 10) / 10.0);
        m.put("orders", orders.stream().limit(5).map(o -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("id", o.getId());
            x.put("code", o.getCode());
            x.put("status", o.getStatus().name());
            x.put("total", o.getTotal());
            x.put("createdAt", o.getCreatedAt());
            x.put("itemCount", o.getItems().size());
            x.put("thumbnailUrl", o.getItems().isEmpty() || o.getItems().get(0).getProduct() == null ? null
                    : o.getItems().get(0).getProduct().getThumbnailUrl());
            return x;
        }).toList());
        return m;
    }

    /** "Goi y tra loi" for the buyer's latest message, about the product last shared in the thread. */
    @Transactional(readOnly = true)
    public Map<String, String> suggest(Long conversationId) {
        Conversation c = owned(ChatSide.SHOP, conversationId);
        ChatMessage last = messageRepository.findFirstByConversationIdAndSenderSideAndContentIsNotNullOrderByIdDesc(c.getId(), ChatSide.BUYER)
                .orElseThrow(() -> new ApiException("Khách chưa gửi tin nhắn nào để gợi ý", HttpStatus.BAD_REQUEST));
        Product product = messageRepository.findFirstByConversationIdAndProductIsNotNullOrderByIdDesc(c.getId())
                .map(ChatMessage::getProduct).orElse(null);
        return suggestService.suggest(last.getContent(), product, c.getShop(), c.getBuyer().getFullName());
    }

    /** Opening a thread clears its unread counter and its "tin nhan moi" notification. */
    public void markRead(ChatSide side, Long conversationId) {
        Conversation c = owned(side, conversationId);
        if (side == ChatSide.BUYER) {
            c.setBuyerUnread(0);
            notificationService.markReadByLink(CurrentUser.id(), buyerLink(c));
        } else {
            c.setShopUnread(0);
            notificationService.markReadByLink(CurrentUser.id(), sellerLink(c));
        }
    }

    // ==== helpers ====

    private static String buyerLink(Conversation c) {
        return "/messages?c=" + c.getId();
    }

    private static String sellerLink(Conversation c) {
        return "/seller/chat?c=" + c.getId();
    }

    private static String preview(ChatMessage m) {
        if (m.getContent() != null) return m.getContent();
        if (m.getImageUrl() != null) return "[Hình ảnh]";
        if (m.getProduct() != null) return "[Sản phẩm] " + m.getProduct().getName();
        return "[Đơn hàng] " + m.getOrder().getCode();
    }

    private Conversation owned(ChatSide side, Long id) {
        Conversation c = conversationRepository.findById(id)
                .orElseThrow(() -> new ApiException("Cuộc trò chuyện không tồn tại", HttpStatus.NOT_FOUND));
        boolean mine = side == ChatSide.BUYER
                ? c.getBuyer().getId().equals(CurrentUser.id())
                : c.getShop().getId().equals(myShop().getId());
        if (!mine) throw new ApiException("Cuộc trò chuyện không tồn tại", HttpStatus.NOT_FOUND);
        return c;
    }

    private Shop myShop() {
        return shopRepository.findBySellerId(CurrentUser.id())
                .orElseThrow(() -> new ApiException("Bạn chưa có shop", HttpStatus.BAD_REQUEST));
    }
}
