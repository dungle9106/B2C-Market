package com.marketplace.backend.service.risk;

import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.RetentionActionRepository;
import com.marketplace.backend.repository.SavedVoucherRepository;
import com.marketplace.backend.repository.UserRepository;
import com.marketplace.backend.repository.VoucherRepository;
import com.marketplace.backend.security.CurrentUser;
import com.marketplace.backend.service.AdminAuditService;
import com.marketplace.backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * AI customer churn prediction ("du doan roi bo") and the actions that keep buyers:
 *
 *   training  the history is replayed: at monthly cut-off dates in the past, each buyer's behaviour until then
 *             (recency, frequency, spend, browsing, logins, returns, bad reviews...) -> did they order in the
 *             next 60 days? A classifier learns which buyers stop (review-ai-service/app/tabular.py).
 *   today     every buyer gets a probability of leaving; the admin page lists the risky ones by value at stake
 *             and sends them a private win-back voucher (wallet + push notification).
 *   carts     products left in the cart for more than a day trigger a reminder (at most every 3 days).
 *
 * Every action is recorded (RetentionAction) so the page shows how many buyers came back afterwards.
 * Without the model the rule is "days since the last order".
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChurnService {

    static final String KIND = "churn";
    static final int HORIZON_DAYS = 60;   // "left" = no order in the next 60 days
    private static final int CUTOFFS = 12; // monthly snapshots replayed for training
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RiskModelClient model;
    private final NamedParameterJdbcTemplate jdbc;
    private final VoucherRepository voucherRepository;
    private final SavedVoucherRepository savedVoucherRepository;
    private final UserRepository userRepository;
    private final RetentionActionRepository actionRepository;
    private final NotificationService notificationService;
    private final AdminAuditService auditService;

    @Value("${app.retention.cart-reminder:true}")
    private boolean cartReminders;

    public static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("recencyDays", "Lâu chưa mua hàng"),
            Map.entry("recencyRatio", "Chưa mua lâu hơn nhịp mua thường ngày"),
            Map.entry("ordersTotal", "Số đơn đã mua"),
            Map.entry("orders90d", "Số đơn 90 ngày qua"),
            Map.entry("spendTotal", "Tổng chi tiêu"),
            Map.entry("avgGapDays", "Khoảng cách giữa các lần mua"),
            Map.entry("views30d", "Lượt xem sản phẩm 30 ngày qua"),
            Map.entry("carts30d", "Lượt thêm giỏ 30 ngày qua"),
            Map.entry("likes30d", "Lượt thích 30 ngày qua"),
            Map.entry("logins30d", "Lượt đăng nhập 30 ngày qua"),
            Map.entry("cancelRatio", "Tỉ lệ đơn bị hủy"),
            Map.entry("returns", "Đã trả hàng / hoàn tiền"),
            Map.entry("badReviews", "Có đánh giá 1-2 sao"),
            Map.entry("avgRating", "Điểm đánh giá trung bình"),
            Map.entry("tenureDays", "Đã là thành viên lâu"));

    // each buyer who had ordered before the cut-off {T}, with their behaviour until then
    private static final String FEATURES = """
            with b as (
              select o.buyer_id as u, max(o.created_at) as last_at, min(o.created_at) as first_at, count(*) as n,
                count(*) filter (where o.created_at >= {T} - interval '90 days') as n90, sum(o.total) as spend
              from orders o where o.created_at < {T} and o.status <> 'CANCELLED' group by o.buyer_id
            )
            select b.u, u.full_name, u.email, b.spend, b.n, b.n90,
              extract(epoch from ({T} - b.last_at)) / 86400 as recency,
              case when b.n > 1 then extract(epoch from (b.last_at - b.first_at)) / 86400 / (b.n - 1) end as gap,
              (select count(*) from orders o where o.buyer_id = b.u and o.created_at < {T} and o.status = 'CANCELLED') as cancelled,
              (select count(*) from view_history v where v.user_id = b.u and v.viewed_at >= {T} - interval '30 days' and v.viewed_at < {T}) as views,
              (select count(*) from product_events e where e.user_id = b.u and e.type = 'ADD_TO_CART'
                  and e.created_at >= {T} - interval '30 days' and e.created_at < {T}) as carts,
              (select count(*) from product_likes l where l.user_id = b.u and l.created_at >= {T} - interval '30 days' and l.created_at < {T}) as likes,
              (select count(*) from login_events l where l.user_id = b.u and l.result = 'SUCCESS'
                  and l.created_at >= {T} - interval '30 days' and l.created_at < {T}) as logins,
              (select count(*) from return_requests r join orders o on o.id = r.order_id where o.buyer_id = b.u and r.created_at < {T}) as returns,
              (select count(*) from reviews r where r.buyer_id = b.u and r.rating <= 2 and r.created_at < {T}) as bad,
              (select avg(r.rating) from reviews r where r.buyer_id = b.u and r.created_at < {T}) as rating,
              extract(epoch from ({T} - u.created_at)) / 86400 as tenure
            from b join users u on u.id = b.u
            where u.role = 'BUYER' and u.deleted_at is null
            """.replace("{T}", "cast(:t as timestamp)");

    record Buyer(long userId, String name, String email, BigDecimal spend, Map<String, Double> features) {}

    private volatile Map<String, Object> cache;
    private volatile long cachedAt;

    // ==================== features ====================

    List<Buyer> buyersAt(LocalDateTime t) {
        return jdbc.query(FEATURES, new MapSqlParameterSource("t", Timestamp.valueOf(t)), ChurnService::row);
    }

    private static Buyer row(ResultSet rs, int n) throws SQLException {
        double recency = Math.max(0, rs.getDouble("recency"));
        double orders = rs.getDouble("n");
        double gap = rs.getDouble("gap");
        double usual = rs.wasNull() ? HORIZON_DAYS : Math.max(1, gap);
        double cancelled = rs.getDouble("cancelled");
        Map<String, Double> f = new LinkedHashMap<>();
        f.put("recencyDays", recency);
        f.put("recencyRatio", recency / usual);
        f.put("ordersTotal", orders);
        f.put("orders90d", rs.getDouble("n90"));
        f.put("spendTotal", rs.getDouble("spend") / 1000.0);
        f.put("avgGapDays", usual);
        f.put("views30d", rs.getDouble("views"));
        f.put("carts30d", rs.getDouble("carts"));
        f.put("likes30d", rs.getDouble("likes"));
        f.put("logins30d", rs.getDouble("logins"));
        f.put("cancelRatio", cancelled / (orders + cancelled));
        f.put("returns", rs.getDouble("returns"));
        f.put("badReviews", rs.getDouble("bad"));
        f.put("avgRating", rs.getDouble("rating"));
        f.put("tenureDays", Math.max(0, rs.getDouble("tenure")));
        return new Buyer(rs.getLong("u"), rs.getString("full_name"), rs.getString("email"), rs.getBigDecimal("spend"), f);
    }

    /** Without a model: the longer since the last order, the riskier (120 days = certain). */
    static double ruleProbability(Map<String, Double> f) {
        return Math.min(1.0, f.getOrDefault("recencyDays", 0.0) / 120.0);
    }

    static String level(double p) {
        return p >= 0.6 ? "HIGH" : p >= 0.35 ? "MEDIUM" : "LOW";
    }

    // ==================== training ====================

    public Map<String, Object> train() {
        LocalDateTime latest = LocalDateTime.now().minusDays(HORIZON_DAYS);
        List<RiskModelClient.Row> rows = new ArrayList<>();
        for (int k = 0; k < CUTOFFS; k++) {
            LocalDateTime t = latest.minusDays(30L * k);
            Set<Long> came = new HashSet<>(jdbc.queryForList("""
                    select distinct buyer_id from orders where status <> 'CANCELLED'
                    and created_at >= :t and created_at < cast(:t as timestamp) + interval '60 days'""",
                    new MapSqlParameterSource("t", Timestamp.valueOf(t)), Long.class));
            double ts = t.atZone(ZoneId.systemDefault()).toEpochSecond();
            for (Buyer b : buyersAt(t)) {
                rows.add(new RiskModelClient.Row(b.features(), came.contains(b.userId()) ? 0 : 1, b.features().get("recencyDays"), ts));
            }
        }
        Map<String, Object> result = model.train(KIND, rows);
        cache = null;
        return result;
    }

    @Scheduled(cron = "${app.churn.train-cron:0 20 4 * * MON}")
    public void weeklyTrain() {
        if (!model.available()) return;
        try {
            train();
        } catch (Exception e) {
            log.info("Weekly churn model training skipped: {}", e.getMessage());
        }
    }

    // ==================== today ====================

    /** Every buyer with an order, scored now; sort = "risk" or "value" (spend x risk). Cached for 10 minutes. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> atRisk(String level, String sort, int limit) {
        Map<String, Object> all = cache;
        if (all == null || System.currentTimeMillis() - cachedAt > 600_000) {
            all = scoreEveryone();
            cache = all;
            cachedAt = System.currentTimeMillis();
        }
        List<Map<String, Object>> items = new ArrayList<>((List<Map<String, Object>>) all.get("items"));
        if (level != null && !level.isBlank()) items.removeIf(m -> !level.equals(m.get("level")));
        Comparator<Map<String, Object>> by = "value".equals(sort)
                ? Comparator.comparingDouble(m -> -((Double) m.get("valueAtRisk")))
                : Comparator.comparingDouble(m -> -((Double) m.get("probability")));
        items.sort(by);
        Map<String, Object> out = new LinkedHashMap<>(all);
        out.put("items", items.subList(0, Math.min(items.size(), Math.max(1, Math.min(500, limit)))));
        out.put("matching", items.size());
        return out;
    }

    private Map<String, Object> scoreEveryone() {
        List<Buyer> buyers = buyersAt(LocalDateTime.now());
        Map<Long, Map<String, Double>> cases = new LinkedHashMap<>();
        buyers.forEach(b -> cases.put(b.userId(), b.features()));
        Optional<Map<Long, RiskModelClient.Score>> scored = model.score(KIND, cases);
        Map<Long, LocalDateTime> lastSent = new HashMap<>();
        jdbc.query("select user_id, max(created_at) as at from retention_actions where type = 'WINBACK_VOUCHER' group by user_id",
                Map.of(), rs -> {
                    lastSent.put(rs.getLong("user_id"), rs.getTimestamp("at").toLocalDateTime());
                });
        List<Map<String, Object>> items = new ArrayList<>();
        int high = 0, medium = 0;
        double valueAtRisk = 0;
        for (Buyer b : buyers) {
            RiskModelClient.Score s = scored.map(m -> m.get(b.userId())).orElse(null);
            double p = s != null && s.probability() != null ? s.probability() : ruleProbability(b.features());
            List<String> why = new ArrayList<>();
            why.add("Chưa mua " + Math.round(b.features().get("recencyDays")) + " ngày" + (b.features().get("ordersTotal") > 1
                    ? " (thường " + Math.round(b.features().get("avgGapDays")) + " ngày/lần)" : " (mới mua 1 đơn)"));
            if (s != null) s.factors().stream().filter(f -> !"recencyDays".equals(f) && !"recencyRatio".equals(f))
                    .map(f -> LABELS.getOrDefault(f, f)).forEach(why::add);
            String lv = level(p);
            if ("HIGH".equals(lv)) high++;
            else if ("MEDIUM".equals(lv)) medium++;
            double spend = b.spend() == null ? 0 : b.spend().doubleValue();
            valueAtRisk += p * spend;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", b.userId());
            m.put("name", b.name());
            m.put("email", b.email());
            m.put("spend", b.spend());
            m.put("orders", b.features().get("ordersTotal").intValue());
            m.put("recencyDays", Math.round(b.features().get("recencyDays")));
            m.put("probability", Math.round(p * 1000) / 1000.0);
            m.put("level", lv);
            m.put("valueAtRisk", Math.round(p * spend) * 1.0);
            m.put("reasons", why);
            m.put("lastWinback", lastSent.get(b.userId()));
            items.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("learned", scored.isPresent());
        out.put("buyers", buyers.size());
        out.put("high", high);
        out.put("medium", medium);
        out.put("valueAtRisk", Math.round(valueAtRisk));
        out.put("scoredAt", LocalDateTime.now());
        out.put("items", items);
        return out;
    }

    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>(model.status(KIND));
        m.put("data", jdbc.queryForMap("""
                select count(distinct buyer_id) as buyers, count(*) as orders, min(created_at) as since
                from orders where status <> 'CANCELLED'""", Map.of()));
        m.put("horizonDays", HORIZON_DAYS);
        m.put("results", results());
        return m;
    }

    /** How many buyers ordered again within 30 days of each kind of action. */
    private List<Map<String, Object>> results() {
        return jdbc.queryForList("""
                select a.type, count(*) as sent,
                  count(*) filter (where exists (select 1 from orders o where o.buyer_id = a.user_id and o.status <> 'CANCELLED'
                      and o.created_at > a.created_at and o.created_at < a.created_at + interval '30 days')) as returned,
                  count(*) filter (where a.created_at > now() - interval '30 days') as recent
                from retention_actions a group by a.type order by a.type""", Map.of());
    }

    // ==================== win-back voucher ====================

    /**
     * A private platform voucher, put straight into each buyer's wallet with a push notification.
     * Buyers who got one in the last 14 days are skipped.
     */
    @Transactional
    public Map<String, Object> sendWinback(List<Long> userIds, int percent, long maxDiscount, long minOrder, int days) {
        if (userIds == null || userIds.isEmpty()) throw bad("Chưa chọn người mua nào");
        if (userIds.size() > 1000) throw bad("Tối đa 1000 người mua mỗi lần");
        if (percent < 1 || percent > 50) throw bad("Mức giảm từ 1% đến 50%");
        if (days < 1 || days > 60) throw bad("Hạn dùng từ 1 đến 60 ngày");
        LocalDateTime now = LocalDateTime.now();
        Set<Long> recent = new HashSet<>(jdbc.queryForList("""
                select user_id from retention_actions where type = 'WINBACK_VOUCHER' and created_at > now() - interval '14 days'
                and user_id in (:ids)""", new MapSqlParameterSource("ids", userIds), Long.class));
        List<Long> targets = userIds.stream().distinct().filter(id -> !recent.contains(id)).toList();
        if (targets.isEmpty()) return Map.of("sent", 0, "skipped", userIds.size(), "code", "");

        Map<Long, Map<String, Object>> scores = new HashMap<>();
        if (cache != null) {
            @SuppressWarnings("unchecked") List<Map<String, Object>> items = (List<Map<String, Object>>) cache.get("items");
            items.forEach(m -> scores.put((Long) m.get("userId"), m));
        }
        String code;
        do {
            code = "GIUCHAN" + Integer.toString(100000 + RANDOM.nextInt(900000));
        } while (voucherRepository.existsByCodeIgnoreCase(code));
        Voucher v = Voucher.builder().code(code).scope(VoucherScope.PLATFORM).type(VoucherType.PERCENT)
                .discountValue(BigDecimal.valueOf(percent)).maxDiscountAmount(maxDiscount > 0 ? BigDecimal.valueOf(maxDiscount) : null)
                .minOrderAmount(minOrder > 0 ? BigDecimal.valueOf(minOrder) : null)
                .usageLimit(targets.size()).usedCount(0).perUserLimit(1).startAt(now).endAt(now.plusDays(days))
                .active(true).publicVisible(false).build();
        voucherRepository.save(v);
        String text = "Đã lâu không gặp! Shoppi tặng bạn mã giảm " + percent + "%"
                + (maxDiscount > 0 ? " (tối đa " + String.format(Locale.ROOT, "%,d", maxDiscount).replace(',', '.') + "đ)" : "")
                + ", đã có sẵn trong ví voucher. Hạn dùng " + days + " ngày.";
        for (Long id : targets) {
            savedVoucherRepository.save(SavedVoucher.builder().user(userRepository.getReferenceById(id)).voucher(v).savedAt(now).build());
            Map<String, Object> s = scores.get(id);
            actionRepository.save(RetentionAction.builder().userId(id).type("WINBACK_VOUCHER").voucherCode(code)
                    .churnProbability(s == null ? null : (Double) s.get("probability"))
                    .spendBefore(s == null ? null : (BigDecimal) s.get("spend")).sentBy(CurrentUser.id()).createdAt(now).build());
            notificationService.notify(id, NotificationType.PROMOTION, "Shoppi nhớ bạn! Tặng mã giảm " + percent + "%", text, "/account/vouchers");
        }
        auditService.log("SEND_WINBACK", "VOUCHER", v.getId(), code, targets.size() + " người mua, giảm " + percent + "%");
        return Map.of("sent", targets.size(), "skipped", userIds.size() - targets.size(), "code", code);
    }

    // ==================== abandoned carts ====================

    /** Every hour: carts untouched for 1 to 7 days, no order since, no reminder in the last 3 days. */
    @Scheduled(cron = "${app.retention.cart-cron:0 15 * * * *}")
    public void remindAbandonedCarts() {
        if (!cartReminders) return;
        try {
            List<Map<String, Object>> carts = jdbc.queryForList("""
                    select c.user_id, count(*) as items, min(p.name) as name, max(e.at) as last_added
                    from cart_items c join products p on p.id = c.product_id
                    join (select user_id, product_id, max(created_at) as at from product_events where type = 'ADD_TO_CART'
                          group by user_id, product_id) e on e.user_id = c.user_id and e.product_id = c.product_id
                    where p.active
                    group by c.user_id
                    having max(e.at) < now() - interval '24 hours' and max(e.at) > now() - interval '7 days'
                      and not exists (select 1 from orders o where o.buyer_id = c.user_id and o.created_at > max(e.at))
                      and not exists (select 1 from retention_actions a where a.user_id = c.user_id and a.type = 'CART_REMINDER'
                          and a.created_at > now() - interval '3 days')
                    limit 2000""", Map.of());
            for (Map<String, Object> c : carts) {
                long userId = ((Number) c.get("user_id")).longValue();
                long n = ((Number) c.get("items")).longValue();
                String name = (String) c.get("name");
                notificationService.notify(userId, NotificationType.PROMOTION, "Bạn còn " + n + " sản phẩm trong giỏ hàng",
                        "\"" + cut(name) + "\"" + (n > 1 ? " và " + (n - 1) + " sản phẩm khác" : "") + " đang chờ bạn. Đặt ngay kẻo hết hàng!", "/cart");
                actionRepository.save(RetentionAction.builder().userId(userId).type("CART_REMINDER").build());
            }
            if (!carts.isEmpty()) log.info("Abandoned cart reminders sent: {}", carts.size());
        } catch (Exception e) {
            log.warn("Abandoned cart reminders failed: {}", e.getMessage());
        }
    }

    private static String cut(String s) {
        return s == null ? "" : s.length() > 60 ? s.substring(0, 57) + "..." : s;
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }

}
