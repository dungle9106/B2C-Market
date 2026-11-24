package com.marketplace.backend.service;

import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.*;
import com.marketplace.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/**
 * "Hieu qua hoat dong" of a shop, like Shopee's:
 *  - metrics over the last 30 days: non-fulfilment rate (orders the seller cancelled), late shipment rate (handed to the
 *    carrier after the shop's packing time + 1 day), chat response rate, shop rating;
 *  - penalty points ("Sao Qua Ta"), counted per quarter: added every week when the non-fulfilment or late shipment rate
 *    of that week is over the threshold, at once when an admin takes a listing down (counterfeit / prohibited: 2 points),
 *    or by an admin. 3+ points: no "Yeu thich" badge; 6+: no Shoppi Flash Sale; 12+: admins asked to consider a suspension;
 *  - the "Shop Yeu Thich" badge, re-checked every day against criteria the admin can tune.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ShopPerformanceService {

    // thresholds (platform settings, editable on the admin page)
    static final Map<String, Double> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put("perf.penalty.nfrPercent", 10.0);      // weekly non-fulfilment rate that costs 1 point
        DEFAULTS.put("perf.penalty.latePercent", 10.0);     // weekly late shipment rate that costs 1 point
        DEFAULTS.put("perf.penalty.minOrders", 5.0);        // orders needed in the week before a rate counts
        DEFAULTS.put("perf.favorite.minOrders", 20.0);      // delivered orders in 30 days
        DEFAULTS.put("perf.favorite.minRating", 4.5);       // shop rating
        DEFAULTS.put("perf.favorite.maxNfrPercent", 5.0);
        DEFAULTS.put("perf.favorite.maxLatePercent", 5.0);
        DEFAULTS.put("perf.favorite.minChatPercent", 70.0);
        DEFAULTS.put("perf.favorite.minAgeDays", 30.0);
    }

    public static final int LEVEL_NO_FAVORITE = 3, LEVEL_NO_FLASH_SALE = 6, LEVEL_REVIEW = 12;

    private final ShopRepository shopRepository;
    private final OrderRepository orderRepository;
    private final ShopPenaltyRepository penaltyRepository;
    private final PlatformSettingRepository settingRepository;
    private final ChatService chatService;
    private final NotificationService notificationService;
    private final AdminAuditService auditService;

    // ==================== metrics ====================

    /** The shop's numbers over [from, to), in percent (null when there is nothing to measure). */
    @Transactional(readOnly = true)
    public Map<String, Object> metrics(Shop shop, LocalDateTime from, LocalDateTime to) {
        long orders = orderRepository.countForPerformance(shop.getId(), from, to);
        long cancelled = orderRepository.countSellerCancelled(shop.getId(), from, to);
        List<Object[]> shipped = orderRepository.shippingTimes(shop.getId(), from, to);
        int handling = shop.getHandlingDays() != null ? shop.getHandlingDays() : 1;
        long late = shipped.stream().filter(r -> isLate((LocalDateTime) r[0], (LocalDateTime) r[1], handling)).count();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orders", orders);
        m.put("sellerCancelled", cancelled);
        m.put("nfrPercent", orders == 0 ? null : pct(cancelled, orders));
        m.put("shipped", (long) shipped.size());
        m.put("lateShipped", late);
        m.put("latePercent", shipped.isEmpty() ? null : pct(late, shipped.size()));
        return m;
    }

    // handed to the carrier after the promised packing days, plus one day of grace
    static boolean isLate(LocalDateTime created, LocalDateTime shipped, int handlingDays) {
        return created != null && shipped != null && shipped.isAfter(created.plusDays(handlingDays + 1L));
    }

    private static double pct(long part, long whole) {
        return Math.round(part * 1000.0 / whole) / 10.0;
    }

    /** Everything about one shop: 30-day metrics, chat, rating, penalty points of the quarter, the badge and its checklist. */
    @Transactional(readOnly = true)
    public Map<String, Object> report(Shop shop) {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("shopId", shop.getId());
        m.put("shopName", shop.getName());
        Map<String, Object> metrics = metrics(shop, now.minusDays(30), now);
        Map<String, Object> chat = chatService.responseStats(shop.getId());
        metrics.put("chatResponsePercent", chat.get("responseRate"));
        Object[] rating = orderRepository.shopRating(shop.getId()).stream().findFirst().orElse(new Object[]{null, 0L});
        metrics.put("rating", rating[0] == null ? null : Math.round(((Number) rating[0]).doubleValue() * 10) / 10.0);
        metrics.put("ratingCount", rating[1] == null ? 0 : ((Number) rating[1]).longValue());
        metrics.put("delivered", orderRepository.countDeliveredSince(shop.getId(), now.minusDays(30)));
        m.put("metrics", metrics);
        long points = penaltyRepository.pointsSince(shop.getId(), quarterStart(now));
        m.put("points", points);
        m.put("level", level(points));
        m.put("quarterEnds", quarterStart(now).plusMonths(3).minusSeconds(1));
        m.put("penalties", penaltyRepository.findByShopIdOrderByCreatedAtDesc(shop.getId()).stream().limit(50).map(this::penaltyView).toList());
        m.put("favorite", shop.isFavorite());
        m.put("favoriteSince", shop.getFavoriteSince());
        m.put("favoriteCriteria", criteria(shop, metrics, points));
        return m;
    }

    /** What the points mean right now. */
    public static Map<String, Object> level(long points) {
        String key = points >= LEVEL_REVIEW ? "REVIEW" : points >= LEVEL_NO_FLASH_SALE ? "RESTRICTED" : points >= LEVEL_NO_FAVORITE ? "WARNING" : "GOOD";
        String text = switch (key) {
            case "REVIEW" -> "Shop bị xem xét đình chỉ, không được tham gia Flash Sale Shoppi và không được nhãn Yêu thích";
            case "RESTRICTED" -> "Không được đăng ký Flash Sale Shoppi và không được nhãn Yêu thích";
            case "WARNING" -> "Cảnh báo: không được xét nhãn Shop Yêu Thích";
            default -> "Tốt";
        };
        return Map.of("key", key, "text", text);
    }

    // ==================== "Shop Yeu Thich" ====================

    private List<Map<String, Object>> criteria(Shop shop, Map<String, Object> metrics, long points) {
        List<Map<String, Object>> list = new ArrayList<>();
        double minOrders = setting("perf.favorite.minOrders"), minRating = setting("perf.favorite.minRating"),
                maxNfr = setting("perf.favorite.maxNfrPercent"), maxLate = setting("perf.favorite.maxLatePercent"),
                minChat = setting("perf.favorite.minChatPercent"), minAge = setting("perf.favorite.minAgeDays");
        long ageDays = shop.getCreatedAt() == null ? 0 : Duration.between(shop.getCreatedAt(), LocalDateTime.now()).toDays();
        long delivered = ((Number) metrics.get("delivered")).longValue();
        Double rating = (Double) metrics.get("rating");
        Double nfr = (Double) metrics.get("nfrPercent"), late = (Double) metrics.get("latePercent"), chat = (Double) metrics.get("chatResponsePercent");
        list.add(item("AGE", "Hoạt động từ " + fmt(minAge) + " ngày", ageDays + " ngày", ageDays >= minAge));
        list.add(item("ORDERS", "Từ " + fmt(minOrders) + " đơn giao thành công trong 30 ngày", delivered + " đơn", delivered >= minOrders));
        list.add(item("RATING", "Đánh giá shop từ " + minRating + "★", rating == null ? "Chưa có" : rating + "★", rating != null && rating >= minRating));
        list.add(item("NFR", "Tỷ lệ đơn không thành công ≤ " + fmt(maxNfr) + "%", nfr == null ? "Chưa có đơn" : nfr + "%", nfr == null || nfr <= maxNfr));
        list.add(item("LATE", "Tỷ lệ giao hàng trễ ≤ " + fmt(maxLate) + "%", late == null ? "Chưa có đơn" : late + "%", late == null || late <= maxLate));
        list.add(item("CHAT", "Tỷ lệ phản hồi chat ≥ " + fmt(minChat) + "%", chat == null ? "Chưa có tin nhắn" : chat + "%", chat == null || chat >= minChat));
        list.add(item("POINTS", "Không có điểm phạt trong quý", points + " điểm", points == 0));
        return list;
    }

    private static Map<String, Object> item(String key, String label, String value, boolean ok) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("label", label);
        m.put("value", value);
        m.put("ok", ok);
        return m;
    }

    private static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    /** Grants or removes the badge from the criteria; the seller hears about any change. */
    public boolean refreshFavorite(Shop shop) {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> metrics = metrics(shop, now.minusDays(30), now);
        metrics.put("chatResponsePercent", chatService.responseStats(shop.getId()).get("responseRate"));
        Object[] rating = orderRepository.shopRating(shop.getId()).stream().findFirst().orElse(new Object[]{null, 0L});
        metrics.put("rating", rating[0] == null ? null : Math.round(((Number) rating[0]).doubleValue() * 10) / 10.0);
        metrics.put("delivered", orderRepository.countDeliveredSince(shop.getId(), now.minusDays(30)));
        long points = penaltyRepository.pointsSince(shop.getId(), quarterStart(now));
        boolean earned = shop.isActive() && criteria(shop, metrics, points).stream().allMatch(c -> (Boolean) c.get("ok"));
        if (earned == shop.isFavorite()) return earned;
        shop.setFavorite(earned);
        shop.setFavoriteSince(earned ? now : null);
        notifySeller(shop, earned ? "Chúc mừng! Shop đạt nhãn Shop Yêu Thích" : "Shop không còn nhãn Shop Yêu Thích",
                earned ? "Nhãn Yêu thích hiển thị trên trang shop và sản phẩm, giúp người mua tin tưởng hơn."
                        : "Shop chưa đáp ứng đủ tiêu chí. Xem chi tiết trong Hiệu Quả Hoạt Động để cải thiện.");
        return earned;
    }

    // ==================== penalty points ====================

    /** The week's checks for one shop (idempotent: one penalty per metric per week). Returns the points added. */
    public int evaluateWeek(Shop shop, LocalDate weekStart) {
        LocalDateTime from = weekStart.atStartOfDay(), to = from.plusDays(7);
        Map<String, Object> m = metrics(shop, from, to);
        String week = weekStart.get(IsoFields.WEEK_BASED_YEAR) + "-W" + String.format("%02d", weekStart.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
        double minOrders = setting("perf.penalty.minOrders");
        int added = 0;
        Double nfr = (Double) m.get("nfrPercent");
        if (nfr != null && (Long) m.get("orders") >= minOrders && nfr >= setting("perf.penalty.nfrPercent")) {
            added += add(shop, ShopPenalty.Code.NFR, 1, "Tỷ lệ đơn không thành công tuần " + week + ": " + nfr + "% (" + m.get("sellerCancelled") + "/"
                    + m.get("orders") + " đơn do shop hủy)", "NFR:" + week, null);
        }
        Double late = (Double) m.get("latePercent");
        if (late != null && (Long) m.get("shipped") >= minOrders && late >= setting("perf.penalty.latePercent")) {
            added += add(shop, ShopPenalty.Code.LATE_SHIPMENT, 1, "Tỷ lệ giao hàng trễ tuần " + week + ": " + late + "% (" + m.get("lateShipped") + "/"
                    + m.get("shipped") + " đơn giao cho ĐVVC trễ hạn)", "LATE:" + week, null);
        }
        return added;
    }

    /** A listing taken down for a violation: counterfeit / prohibited goods 2 points, anything else 1. */
    public void onListingRemoved(Product p, String violation, String violationLabel) {
        int points = "COUNTERFEIT".equals(violation) || "PROHIBITED".equals(violation) ? 2 : 1;
        add(p.getShop(), ShopPenalty.Code.LISTING, points, "Sản phẩm \"" + p.getName() + "\" bị gỡ: " + violationLabel,
                "LISTING:" + p.getId() + ":" + System.currentTimeMillis(), null);
    }

    /** The listing was restored (the take-down was a mistake): its penalty no longer counts. */
    public void onListingRestored(Product p) {
        penaltyRepository.findFirstByShopIdAndCodeAndRefKeyStartingWithAndRevokedFalseOrderByCreatedAtDesc(
                        p.getShop().getId(), ShopPenalty.Code.LISTING, "LISTING:" + p.getId() + ":")
                .ifPresent(x -> {
                    x.setRevoked(true);
                    x.setRevokedAt(LocalDateTime.now());
                    x.setRevokeReason("Sản phẩm đã được khôi phục");
                });
    }

    private int add(Shop shop, ShopPenalty.Code code, int points, String reason, String refKey, Long adminId) {
        if (refKey != null && penaltyRepository.existsByShopIdAndRefKey(shop.getId(), refKey)) return 0;
        long before = penaltyRepository.pointsSince(shop.getId(), quarterStart(LocalDateTime.now()));
        try {
            penaltyRepository.saveAndFlush(ShopPenalty.builder().shop(shop).code(code).points(points).reason(reason).refKey(refKey).createdBy(adminId).build());
        } catch (DataIntegrityViolationException e) {
            return 0;   // added meanwhile by another run
        }
        long after = before + points;
        Map<String, Object> lv = level(after);
        notifySeller(shop, "Shop bị cộng " + points + " điểm phạt (Sao Quả Tạ)",
                reason + ". Tổng điểm quý này: " + after + ". " + ("GOOD".equals(lv.get("key")) ? "" : lv.get("text") + "."));
        if (after >= LEVEL_NO_FAVORITE && shop.isFavorite()) {
            shop.setFavorite(false);
            shop.setFavoriteSince(null);
        }
        if (before < LEVEL_REVIEW && after >= LEVEL_REVIEW) {
            auditService.notifyAdmins(AdminPermission.SHOPS, "Shop đạt " + after + " điểm phạt",
                    "\"" + shop.getName() + "\" có " + after + " điểm phạt trong quý. Vui lòng xem xét đình chỉ shop.", "/admin/shop-performance?shop=" + shop.getId());
        }
        return points;
    }

    /** Shoppi Flash Sale registration is closed to shops with 6+ points. */
    @Transactional(readOnly = true)
    public void checkCanJoinFlashSale(Shop shop) {
        long points = penaltyRepository.pointsSince(shop.getId(), quarterStart(LocalDateTime.now()));
        if (points >= LEVEL_NO_FLASH_SALE) {
            throw new ApiException("Shop có " + points + " điểm phạt trong quý nên tạm thời không được đăng ký Flash Sale của Shoppi", HttpStatus.BAD_REQUEST);
        }
    }

    // ==================== admin ====================

    @Transactional(readOnly = true)
    public Map<String, Object> adminList(String q, String sort, int page) {
        LocalDateTime now = LocalDateTime.now();
        List<Shop> shops = shopRepository.findAll().stream()
                .filter(s -> q == null || q.isBlank() || s.getName().toLowerCase().contains(q.trim().toLowerCase())).toList();
        Map<Long, Long> points = new HashMap<>();
        if (!shops.isEmpty()) {
            for (Object[] r : penaltyRepository.pointsSince(shops.stream().map(Shop::getId).toList(), quarterStart(now))) {
                points.put((Long) r[0], ((Number) r[1]).longValue());
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Shop s : shops) {
            Map<String, Object> m = metrics(s, now.minusDays(30), now);
            m.put("shopId", s.getId());
            m.put("shopName", s.getName());
            m.put("active", s.isActive());
            m.put("favorite", s.isFavorite());
            long p = points.getOrDefault(s.getId(), 0L);
            m.put("points", p);
            m.put("level", level(p));
            rows.add(m);
        }
        Comparator<Map<String, Object>> by = switch (sort == null ? "points" : sort) {
            case "nfr" -> Comparator.comparing(r -> r.get("nfrPercent") == null ? -1.0 : (Double) r.get("nfrPercent"));
            case "late" -> Comparator.comparing(r -> r.get("latePercent") == null ? -1.0 : (Double) r.get("latePercent"));
            case "orders" -> Comparator.comparing(r -> (Long) r.get("orders"));
            default -> Comparator.comparing(r -> (Long) r.get("points"));
        };
        rows.sort(by.reversed());
        int size = 20, from = Math.max(0, page) * size;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", rows.subList(Math.min(from, rows.size()), Math.min(from + size, rows.size())));
        out.put("page", Math.max(0, page));
        out.put("totalPages", (rows.size() + size - 1) / size);
        out.put("totalItems", rows.size());
        out.put("summary", Map.of("favorite", rows.stream().filter(r -> (Boolean) r.get("favorite")).count(),
                "warned", rows.stream().filter(r -> (Long) r.get("points") >= LEVEL_NO_FAVORITE).count(),
                "review", rows.stream().filter(r -> (Long) r.get("points") >= LEVEL_REVIEW).count()));
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> adminShop(Long shopId) {
        return report(shop(shopId));
    }

    public Map<String, Object> addManual(Long shopId, Integer points, String reason) {
        Shop shop = shop(shopId);
        if (points == null || points < 1 || points > 5) throw bad("Điểm phạt từ 1 đến 5");
        if (reason == null || reason.trim().length() < 10) throw bad("Vui lòng ghi lý do (ít nhất 10 ký tự)");
        add(shop, ShopPenalty.Code.MANUAL, points, reason.trim(), null, CurrentUser.id());
        auditService.log("ADD_SHOP_PENALTY", "SHOP", shop.getId(), shop.getName(), points + " điểm – " + reason.trim());
        return report(shop);
    }

    public Map<String, Object> revoke(Long penaltyId, String reason) {
        ShopPenalty p = penaltyRepository.findById(penaltyId).orElseThrow(() -> new ApiException("Điểm phạt không tồn tại", HttpStatus.NOT_FOUND));
        if (p.isRevoked()) throw bad("Điểm phạt đã được hủy");
        if (reason == null || reason.trim().length() < 5) throw bad("Vui lòng ghi lý do hủy");
        p.setRevoked(true);
        p.setRevokedAt(LocalDateTime.now());
        p.setRevokeReason(reason.trim());
        auditService.log("REVOKE_SHOP_PENALTY", "SHOP", p.getShop().getId(), p.getShop().getName(), p.getPoints() + " điểm – " + reason.trim());
        notifySeller(p.getShop(), "Shoppi đã hủy " + p.getPoints() + " điểm phạt của shop", p.getReason() + ". Lý do hủy: " + reason.trim());
        return report(p.getShop());
    }

    /** Runs the checks now: last week's penalties (if not done yet) and every badge. */
    public Map<String, Object> evaluateAll() {
        LocalDate lastWeek = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
        int penalties = 0, favorites = 0;
        for (Shop s : shopRepository.findAll()) {
            penalties += evaluateWeek(s, lastWeek) > 0 ? 1 : 0;
            if (refreshFavorite(s)) favorites++;
        }
        return Map.of("shopsPenalized", penalties, "favoriteShops", favorites, "week", lastWeek.toString());
    }

    public Map<String, Object> settings() {
        Map<String, Object> m = new LinkedHashMap<>();
        DEFAULTS.keySet().forEach(k -> m.put(k.substring("perf.".length()), setting(k)));
        return m;
    }

    public Map<String, Object> updateSettings(Map<String, Object> body) {
        for (Map.Entry<String, Object> e : body.entrySet()) {
            String key = "perf." + e.getKey();
            if (!DEFAULTS.containsKey(key)) throw bad("Tham số không hợp lệ: " + e.getKey());
            double v;
            try {
                v = Double.parseDouble(String.valueOf(e.getValue()));
            } catch (NumberFormatException ex) {
                throw bad("Giá trị không hợp lệ: " + e.getKey());
            }
            if (v < 0 || (key.endsWith("Percent") && v > 100) || (key.endsWith("minRating") && v > 5)) throw bad("Giá trị ngoài phạm vi: " + e.getKey());
            PlatformSetting s = settingRepository.findById(key).orElseGet(PlatformSetting::new);
            s.setKey(key);
            s.setValue(String.valueOf(v));
            s.setUpdatedAt(LocalDateTime.now());
            settingRepository.save(s);
        }
        auditService.log("UPDATE_PERFORMANCE_RULES", "SETTING", null, "Hiệu quả hoạt động shop", body.toString());
        return settings();
    }

    // ==================== seller ====================

    @Transactional(readOnly = true)
    public Map<String, Object> mine() {
        Shop shop = shopRepository.findBySellerId(CurrentUser.id())
                .orElseThrow(() -> new ApiException("Bạn chưa có shop", HttpStatus.BAD_REQUEST));
        return report(shop);
    }

    // ==================== schedule ====================

    /** Every Monday 01:00: last week's penalties; every day 02:00: badges. */
    @Scheduled(cron = "0 0 1 * * MON")
    public void weekly() {
        LocalDate lastWeek = LocalDate.now().minusWeeks(1).with(DayOfWeek.MONDAY);
        for (Shop s : shopRepository.findAll()) {
            try {
                evaluateWeek(s, lastWeek);
            } catch (Exception e) {
                log.warn("Performance check failed for shop {}: {}", s.getId(), e.getMessage());
            }
        }
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void daily() {
        for (Shop s : shopRepository.findAll()) {
            try {
                refreshFavorite(s);
            } catch (Exception e) {
                log.warn("Favourite check failed for shop {}: {}", s.getId(), e.getMessage());
            }
        }
    }

    // ==================== helpers ====================

    static LocalDateTime quarterStart(LocalDateTime now) {
        int firstMonth = ((now.getMonthValue() - 1) / 3) * 3 + 1;
        return LocalDate.of(now.getYear(), firstMonth, 1).atStartOfDay();
    }

    private Map<String, Object> penaltyView(ShopPenalty p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("code", p.getCode());
        m.put("points", p.getPoints());
        m.put("reason", p.getReason());
        m.put("createdAt", p.getCreatedAt());
        m.put("revoked", p.isRevoked());
        m.put("revokeReason", p.getRevokeReason());
        m.put("counted", !p.isRevoked() && !p.getCreatedAt().isBefore(quarterStart(LocalDateTime.now())));
        return m;
    }

    private double setting(String key) {
        try {
            return settingRepository.findById(key).map(s -> Double.parseDouble(s.getValue())).orElse(DEFAULTS.get(key));
        } catch (NumberFormatException e) {
            return DEFAULTS.get(key);
        }
    }

    private void notifySeller(Shop shop, String title, String content) {
        if (shop.getSeller() == null) return;
        notificationService.notify(shop.getSeller().getId(), NotificationType.SYSTEM, title, content, "/seller/performance");
    }

    private Shop shop(Long id) {
        return shopRepository.findById(id).orElseThrow(() -> new ApiException("Shop không tồn tại", HttpStatus.NOT_FOUND));
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }
}
