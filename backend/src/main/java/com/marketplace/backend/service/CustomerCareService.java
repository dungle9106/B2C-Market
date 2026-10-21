package com.marketplace.backend.service;

import com.marketplace.backend.entity.ReplyTemplate;
import com.marketplace.backend.entity.Shop;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.ConversationRepository;
import com.marketplace.backend.repository.ProductQuestionRepository;
import com.marketplace.backend.repository.ReplyTemplateRepository;
import com.marketplace.backend.repository.ReviewRepository;
import com.marketplace.backend.repository.ShopRepository;
import com.marketplace.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// "Cham soc khach hang" settings of a shop: reply templates, chat auto-replies, and the
// counters behind the sidebar badges.
@Service
@Transactional
@RequiredArgsConstructor
public class CustomerCareService {

    static final int MAX_TEMPLATES = 20;
    static final int REVIEW_REPLY_MAX = 255;

    private static final Map<ReplyTemplate.Kind, List<String>> DEFAULTS = Map.of(
            ReplyTemplate.Kind.CHAT, List.of(
                    "Chào {ten_khach}, {ten_shop} có thể giúp gì cho bạn ạ?",
                    "Sản phẩm vẫn còn hàng bạn nhé, bạn đặt hàng sớm để shop giao ngay nha!",
                    "Shop sẽ đóng gói và giao cho đơn vị vận chuyển trong hôm nay ạ.",
                    "Bạn gửi giúp shop mã đơn hàng để shop kiểm tra nhé.",
                    "Cảm ơn {ten_khach} đã ủng hộ {ten_shop} ❤️"),
            ReplyTemplate.Kind.QUESTION, List.of(
                    "Dạ {ten_san_pham} vẫn còn hàng ạ, bạn đặt hàng ngay để shop giao sớm nhé!",
                    "Dạ bạn tham khảo bảng size trong phần mô tả, cần tư vấn thêm bạn nhắn tin cho shop nhé.",
                    "Dạ sản phẩm được hỗ trợ đổi trả theo chính sách của shop ạ."),
            ReplyTemplate.Kind.REVIEW, List.of(
                    "Cảm ơn {ten_khach} đã tin tưởng {ten_shop}! Hẹn gặp lại bạn ở những đơn hàng sau nhé ❤️",
                    "Cảm ơn bạn đã đánh giá {ten_san_pham}, shop rất vui vì bạn hài lòng!",
                    "Shop thành thật xin lỗi vì trải nghiệm chưa tốt. Bạn nhắn tin cho shop để được hỗ trợ đổi trả ngay nhé!"));

    private final ShopRepository shopRepository;
    private final ReplyTemplateRepository templateRepository;
    private final ConversationRepository conversationRepository;
    private final ProductQuestionRepository questionRepository;
    private final ReviewRepository reviewRepository;

    // ==================== reply templates ====================

    /** All three lists; the first call creates the default templates. */
    public Map<String, List<Map<String, Object>>> templates() {
        Shop shop = myShop();
        if (!shop.isTemplatesInitialized()) {
            DEFAULTS.forEach((kind, texts) -> texts.forEach(t ->
                    templateRepository.save(ReplyTemplate.builder().shop(shop).kind(kind).content(t).build())));
            shop.setTemplatesInitialized(true);
        }
        Map<String, List<Map<String, Object>>> out = new LinkedHashMap<>();
        for (ReplyTemplate.Kind k : ReplyTemplate.Kind.values()) {
            out.put(k.name(), templateRepository.findByShopIdAndKindOrderByIdAsc(shop.getId(), k).stream()
                    .map(CustomerCareService::view).toList());
        }
        return out;
    }

    public Map<String, Object> createTemplate(String kind, String content) {
        Shop shop = myShop();
        templates(); // make sure the defaults exist first, so they don't appear later on top of this one
        ReplyTemplate.Kind k = kind(kind);
        if (templateRepository.countByShopIdAndKind(shop.getId(), k) >= MAX_TEMPLATES) {
            throw bad("Mỗi loại tối đa " + MAX_TEMPLATES + " tin nhắn mẫu");
        }
        return view(templateRepository.save(ReplyTemplate.builder().shop(shop).kind(k).content(content(k, content)).build()));
    }

    public Map<String, Object> updateTemplate(Long id, String content) {
        ReplyTemplate t = ownedTemplate(id);
        t.setContent(content(t.getKind(), content));
        return view(t);
    }

    public void deleteTemplate(Long id) {
        templateRepository.delete(ownedTemplate(id));
    }

    private ReplyTemplate ownedTemplate(Long id) {
        Long shopId = myShop().getId();
        return templateRepository.findById(id).filter(t -> t.getShop().getId().equals(shopId))
                .orElseThrow(() -> new ApiException("Tin nhắn mẫu không tồn tại", HttpStatus.NOT_FOUND));
    }

    private static String content(ReplyTemplate.Kind kind, String content) {
        String c = content == null ? "" : content.trim();
        if (c.isEmpty()) throw bad("Vui lòng nhập nội dung");
        int max = kind == ReplyTemplate.Kind.REVIEW ? REVIEW_REPLY_MAX : 500;
        if (c.length() > max) throw bad("Nội dung tối đa " + max + " ký tự");
        return c;
    }

    private static ReplyTemplate.Kind kind(String kind) {
        try {
            return ReplyTemplate.Kind.valueOf(String.valueOf(kind).toUpperCase());
        } catch (IllegalArgumentException e) {
            throw bad("Loại tin nhắn mẫu không hợp lệ");
        }
    }

    private static Map<String, Object> view(ReplyTemplate t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("kind", t.getKind().name());
        m.put("content", t.getContent());
        return m;
    }

    // ==================== chat auto-replies ====================

    @Transactional(readOnly = true)
    public Map<String, Object> chatSettings() {
        return chatSettingsView(myShop());
    }

    /** body: autoReplyEnabled, autoReplyMessage, awayEnabled, awayMessage, hoursStart "HH:mm", hoursEnd "HH:mm" */
    public Map<String, Object> updateChatSettings(Map<String, Object> body) {
        Shop shop = myShop();
        boolean auto = Boolean.TRUE.equals(body.get("autoReplyEnabled"));
        boolean away = Boolean.TRUE.equals(body.get("awayEnabled"));
        String autoMsg = text(body.get("autoReplyMessage"));
        String awayMsg = text(body.get("awayMessage"));
        if (auto && autoMsg == null) throw bad("Vui lòng nhập tin nhắn chào tự động");
        if (away && awayMsg == null) throw bad("Vui lòng nhập tin nhắn ngoài giờ làm việc");
        if ((autoMsg != null && autoMsg.length() > 500) || (awayMsg != null && awayMsg.length() > 500)) {
            throw bad("Tin nhắn tự động tối đa 500 ký tự");
        }
        String start = time(body.get("hoursStart"));
        String end = time(body.get("hoursEnd"));
        if (away && (start == null || end == null)) throw bad("Vui lòng chọn giờ làm việc");
        if (start != null && start.equals(end)) throw bad("Giờ bắt đầu và kết thúc không được trùng nhau");
        shop.setChatAutoReplyEnabled(auto);
        shop.setChatAutoReplyMessage(autoMsg);
        shop.setChatAwayEnabled(away);
        shop.setChatAwayMessage(awayMsg);
        shop.setChatHoursStart(start);
        shop.setChatHoursEnd(end);
        return chatSettingsView(shop);
    }

    /** Working hours may span midnight (e.g. 20:00 - 02:00). No hours set = always open. */
    public static boolean withinHours(Shop shop, LocalTime now) {
        if (shop.getChatHoursStart() == null || shop.getChatHoursEnd() == null) return true;
        LocalTime start = LocalTime.parse(shop.getChatHoursStart());
        LocalTime end = LocalTime.parse(shop.getChatHoursEnd());
        return start.isBefore(end)
                ? !now.isBefore(start) && now.isBefore(end)
                : !now.isBefore(start) || now.isBefore(end);
    }

    private static Map<String, Object> chatSettingsView(Shop s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("autoReplyEnabled", s.isChatAutoReplyEnabled());
        m.put("autoReplyMessage", s.getChatAutoReplyMessage());
        m.put("awayEnabled", s.isChatAwayEnabled());
        m.put("awayMessage", s.getChatAwayMessage());
        m.put("hoursStart", s.getChatHoursStart());
        m.put("hoursEnd", s.getChatHoursEnd());
        m.put("openNow", withinHours(s, LocalTime.now()));
        return m;
    }

    private static String text(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static String time(Object o) {
        String s = text(o);
        if (s == null) return null;
        try {
            return LocalTime.parse(s).toString().substring(0, 5);
        } catch (DateTimeParseException e) {
            throw bad("Giờ không hợp lệ (HH:mm)");
        }
    }

    // ==================== sidebar badges ====================

    @Transactional(readOnly = true)
    public Map<String, Object> summary() {
        Long shopId = myShop().getId();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("unreadChats", conversationRepository.countByShopIdAndShopUnreadGreaterThan(shopId, 0));
        m.put("awaitingChats", conversationRepository.countByShopIdAndAwaitingReplyTrue(shopId));
        m.put("unansweredQuestions", questionRepository.countByProductShopIdAndAnswerIsNullAndHiddenFalse(shopId));
        m.put("unrepliedReviews", reviewRepository.countByProductShopIdAndSellerReplyIsNull(shopId));
        m.put("unrepliedNegativeReviews", reviewRepository.countByProductShopIdAndSellerReplyIsNullAndRatingLessThanEqual(shopId, 2));
        return m;
    }

    Shop myShop() {
        return shopRepository.findBySellerId(CurrentUser.id())
                .orElseThrow(() -> bad("Bạn chưa có shop"));
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }
}
