package com.marketplace.backend.service.risk;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.backend.entity.NotificationType;
import com.marketplace.backend.entity.OrderRisk;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.OrderRiskRepository;
import com.marketplace.backend.security.CurrentUser;
import com.marketplace.backend.service.AdminAuditService;
import com.marketplace.backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;

/**
 * AI order fraud detection: every new order gets a risk score a few minutes after it is placed.
 *
 *   rules (FraudRules)        new account, many orders in an hour, a basket far above the buyer's habit, a phone
 *                             shared by several accounts, earlier orders refused at the door, ...
 *   IsolationForest           how unusual the order is compared with every order seen so far (no label needed)
 *   classifier                once there is history: learned from orders that turned out badly (refused COD
 *                             orders, "bom hang") or that admins marked as fraud, against delivered ones
 *
 * High risk orders are flagged to the admins; nothing is cancelled automatically: an admin decides, and the
 * verdict (safe / fraud) becomes a training label.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FraudService {

    static final String KIND = "fraud";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final RiskModelClient model;
    private final OrderRiskRepository riskRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final NotificationService notificationService;
    private final AdminAuditService auditService;

    // the features of the orders :ids, each computed only from what was known when the order was placed
    private static final String FEATURES = """
            select o.id, o.buyer_id, o.created_at,
              o.total / 1000.0 as amount,
              (select count(*) from orders p where p.buyer_id = o.buyer_id and p.created_at < o.created_at) as prior_orders,
              coalesce(o.total / nullif((select percentile_cont(0.5) within group (order by p.total) from orders p
                  where p.buyer_id = o.buyer_id and p.created_at < o.created_at and p.status <> 'CANCELLED'), 0), 1) as amount_ratio,
              extract(epoch from (o.created_at - u.created_at)) / 86400 as account_age,
              (select count(*) from orders p where p.buyer_id = o.buyer_id
                  and p.created_at between o.created_at - interval '1 hour' and o.created_at) as orders_hour,
              (select count(*) from orders p where p.buyer_id = o.buyer_id
                  and p.created_at between o.created_at - interval '24 hours' and o.created_at) as orders_day,
              case when pay.method = 'COD' then 1 else 0 end as cod,
              coalesce((select avg(case when p.status = 'CANCELLED' and p.shipped_at is not null
                      or exists (select 1 from tracking_events t where t.order_id = p.id and t.stage = 'DELIVERY_FAILED') then 1.0 else 0 end)
                  from orders p where p.buyer_id = o.buyer_id and p.created_at < o.created_at), 0) as prior_fail,
              (select count(distinct p.recipient_phone) from orders p where p.buyer_id = o.buyer_id
                  and p.created_at between o.created_at - interval '7 days' and o.created_at) as phones_week,
              (select count(distinct p.buyer_id) from orders p where o.recipient_phone is not null
                  and p.recipient_phone = o.recipient_phone and p.buyer_id <> o.buyer_id and p.created_at <= o.created_at) as phone_shared,
              (coalesce(o.discount, 0) + coalesce(o.combo_discount, 0) + coalesce(o.coin_discount, 0)) / nullif(o.subtotal, 0) as discount_ratio,
              (select coalesce(sum(i.quantity), 0) from order_items i where i.order_id = o.id) as quantity,
              case when exists (select 1 from login_events l where l.user_id = o.buyer_id and l.new_device and l.result = 'SUCCESS'
                  and l.created_at between o.created_at - interval '24 hours' and o.created_at) then 1 else 0 end as new_device,
              (select count(*) from login_events l where l.user_id = o.buyer_id and l.result in ('BAD_PASSWORD', 'TWO_FACTOR_FAILED')
                  and l.created_at between o.created_at - interval '24 hours' and o.created_at) as failed_logins,
              case when extract(hour from o.created_at) < 5 then 1 else 0 end as night
            from orders o join users u on u.id = o.buyer_id left join payments pay on pay.order_id = o.id
            where o.id in (:ids)
            """;

    // what really happened: 1 = refused at the door / cancelled after shipping, or an admin said fraud; 0 = delivered
    private static final String LABEL = """
            case when r.verdict = 'FRAUD' then 1 when r.verdict = 'SAFE' then 0
                 when exists (select 1 from tracking_events t where t.order_id = o.id and t.stage = 'DELIVERY_FAILED')
                      or (o.status = 'CANCELLED' and o.shipped_at is not null) then 1
                 when o.status in ('DELIVERED', 'RETURN_REQUESTED', 'REFUNDED') then 0 end""";

    record Case(long orderId, long buyerId, LocalDateTime createdAt, Map<String, Double> features) {}

    // ==================== scoring ====================

    /** Every 2 minutes: the orders of the last 30 days not checked yet. */
    @Scheduled(fixedDelayString = "${app.fraud.scan-ms:120000}", initialDelay = 60_000)
    public void scanNewOrders() {
        try {
            List<Long> ids = jdbc.queryForList("""
                    select o.id from orders o where o.created_at > now() - interval '30 days'
                    and not exists (select 1 from order_risks r where r.order_id = o.id) order by o.id limit 500""",
                    Map.of(), Long.class);
            if (!ids.isEmpty()) assess(ids);
        } catch (Exception e) {
            log.warn("Fraud scan failed: {}", e.getMessage());
        }
    }

    /** Scores the orders and saves (or refreshes) their OrderRisk. Returns how many became HIGH. */
    int assess(List<Long> orderIds) {
        List<Case> cases = features(orderIds);
        Map<Long, Map<String, Double>> byId = new LinkedHashMap<>();
        cases.forEach(c -> byId.put(c.orderId(), c.features()));
        Map<Long, RiskModelClient.Score> scores = model.score(KIND, byId).orElse(Map.of());
        int high = 0;
        for (Case c : cases) {
            FraudRules.Result rules = FraudRules.evaluate(c.features());
            RiskModelClient.Score s = scores.get(c.orderId());
            Double prob = s == null ? null : s.probability();
            Double pct = s == null ? null : s.percentile();
            int score = FraudRules.combine(rules.score(), prob, pct);
            List<String> reasons = new ArrayList<>(rules.reasons());
            if (s != null) {
                List<String> odd = s.unusual().stream().filter(f -> !FraudRules.SAFE_WHEN_HIGH.contains(f))
                        .map(f -> FraudRules.LABELS.getOrDefault(f, f)).toList();
                if (!odd.isEmpty() && pct != null && pct >= 0.9) reasons.add("Khác thường so với các đơn khác: " + String.join(", ", odd).toLowerCase(Locale.ROOT));
                List<String> learned = s.factors().stream().filter(f -> !FraudRules.SAFE_WHEN_HIGH.contains(f))
                        .map(f -> FraudRules.LABELS.getOrDefault(f, f)).toList();
                if (prob != null && prob >= 0.3 && !learned.isEmpty()) reasons.add("Mô hình học từ lịch sử chú ý: " + String.join(", ", learned).toLowerCase(Locale.ROOT));
            }
            OrderRisk r = riskRepository.findByOrderId(c.orderId()).orElseGet(() -> OrderRisk.builder().orderId(c.orderId()).buyerId(c.buyerId()).build());
            boolean wasHigh = "HIGH".equals(r.getLevel());
            r.setScore(score);
            r.setLevel(FraudRules.level(score));
            r.setRuleScore(rules.score());
            r.setProbability(prob);
            r.setAnomaly(pct);
            r.setReasons(json(reasons));
            r.setFeatures(json(c.features()));
            riskRepository.save(r);
            if ("HIGH".equals(r.getLevel()) && !wasHigh && r.getVerdict() == null) high++;
        }
        if (high > 0) {
            notificationService.notifyAdmins(NotificationType.ORDER, "Phát hiện " + high + " đơn hàng có rủi ro gian lận cao",
                    "Hệ thống AI vừa gắn cờ " + high + " đơn hàng mới. Vui lòng kiểm tra trước khi giao.", "/admin/fraud");
        }
        return high;
    }

    List<Case> features(Collection<Long> orderIds) {
        if (orderIds.isEmpty()) return List.of();
        List<Case> out = new ArrayList<>();
        List<Long> ids = new ArrayList<>(orderIds);
        for (int from = 0; from < ids.size(); from += 1000) {
            out.addAll(jdbc.query(FEATURES, new MapSqlParameterSource("ids", ids.subList(from, Math.min(ids.size(), from + 1000))), FraudService::row));
        }
        return out;
    }

    private static Case row(ResultSet rs, int n) throws SQLException {
        Map<String, Double> f = new LinkedHashMap<>();
        f.put("amount", rs.getDouble("amount"));
        f.put("amountRatio", rs.getDouble("amount_ratio"));
        f.put("accountAgeDays", Math.max(0, rs.getDouble("account_age")));
        f.put("priorOrders", rs.getDouble("prior_orders"));
        f.put("ordersLastHour", rs.getDouble("orders_hour"));
        f.put("ordersLastDay", rs.getDouble("orders_day"));
        f.put("cod", rs.getDouble("cod"));
        f.put("priorFailRatio", rs.getDouble("prior_fail"));
        f.put("phonesLast7d", rs.getDouble("phones_week"));
        f.put("phoneSharedAccounts", rs.getDouble("phone_shared"));
        f.put("discountRatio", rs.getDouble("discount_ratio"));
        f.put("quantity", rs.getDouble("quantity"));
        f.put("newDeviceLogin", rs.getDouble("new_device"));
        f.put("failedLogins24h", rs.getDouble("failed_logins"));
        f.put("nightHour", rs.getDouble("night"));
        return new Case(rs.getLong("id"), rs.getLong("buyer_id"), rs.getTimestamp("created_at").toLocalDateTime(), f);
    }

    // ==================== admin ====================

    public Map<String, Object> list(String level, String verdict, int page, int size) {
        StringBuilder where = new StringBuilder(" where 1 = 1");
        MapSqlParameterSource p = new MapSqlParameterSource();
        if (level != null && !level.isBlank()) {
            where.append(" and r.level = :level");
            p.addValue("level", level);
        }
        if ("NONE".equals(verdict)) where.append(" and r.verdict is null");
        else if (verdict != null && !verdict.isBlank()) {
            where.append(" and r.verdict = :verdict");
            p.addValue("verdict", verdict);
        }
        String from = " from order_risks r join orders o on o.id = r.order_id join users u on u.id = r.buyer_id left join payments pay on pay.order_id = o.id";
        long total = Optional.ofNullable(jdbc.queryForObject("select count(*)" + from + where, p, Long.class)).orElse(0L);
        p.addValue("limit", Math.min(100, Math.max(1, size))).addValue("offset", Math.max(0, page) * Math.min(100, Math.max(1, size)));
        List<Map<String, Object>> items = jdbc.query("""
                select r.id, r.order_id, o.code, o.total, o.status, o.created_at, pay.method, r.buyer_id, u.full_name, u.email,
                  r.score, r.level, r.rule_score, r.probability, r.anomaly, r.reasons, r.verdict, r.reviewed_at""" + from + where
                        + " order by r.verdict nulls first, r.score desc, r.created_at desc limit :limit offset :offset", p,
                (rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("orderId", rs.getLong("order_id"));
                    m.put("code", rs.getString("code"));
                    m.put("total", rs.getBigDecimal("total"));
                    m.put("status", rs.getString("status"));
                    m.put("createdAt", rs.getTimestamp("created_at").toLocalDateTime());
                    m.put("paymentMethod", rs.getString("method"));
                    m.put("buyerId", rs.getLong("buyer_id"));
                    m.put("buyerName", rs.getString("full_name"));
                    m.put("buyerEmail", rs.getString("email"));
                    m.put("score", rs.getInt("score"));
                    m.put("level", rs.getString("level"));
                    m.put("ruleScore", rs.getInt("rule_score"));
                    m.put("probability", rs.getObject("probability"));
                    m.put("anomaly", rs.getObject("anomaly"));
                    m.put("reasons", parse(rs.getString("reasons")));
                    m.put("verdict", rs.getString("verdict"));
                    m.put("reviewedAt", rs.getTimestamp("reviewed_at") == null ? null : rs.getTimestamp("reviewed_at").toLocalDateTime());
                    return m;
                });
        return Map.of("items", items, "total", total, "page", page);
    }

    /** An admin's verdict: SAFE or FRAUD (null clears it). It becomes a training label. */
    public Map<String, Object> review(Long id, String verdict) {
        if (verdict != null && !Set.of("SAFE", "FRAUD").contains(verdict)) throw new ApiException("Kết luận không hợp lệ", HttpStatus.BAD_REQUEST);
        OrderRisk r = riskRepository.findById(id).orElseThrow(() -> new ApiException("Không tìm thấy", HttpStatus.NOT_FOUND));
        r.setVerdict(verdict);
        r.setReviewedBy(verdict == null ? null : CurrentUser.id());
        r.setReviewedAt(verdict == null ? null : LocalDateTime.now());
        riskRepository.save(r);
        auditService.log("FRAUD_VERDICT", "ORDER", r.getOrderId(), "Đơn #" + r.getOrderId(), verdict == null ? "Bỏ kết luận" : verdict);
        return Map.of("id", id, "verdict", verdict == null ? "" : verdict);
    }

    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>(model.status(KIND));
        m.put("data", jdbc.queryForMap("""
                select (select count(*) from orders) as orders,
                  count(*) as scored,
                  count(*) filter (where r.level = 'HIGH') as high,
                  count(*) filter (where r.level = 'MEDIUM') as medium,
                  count(*) filter (where r.level <> 'LOW' and r.verdict is null) as pending,
                  count(*) filter (where r.verdict = 'FRAUD') as fraud,
                  count(*) filter (where r.verdict = 'SAFE') as safe
                from order_risks r""", Map.of()));
        m.put("labels", jdbc.queryForMap("select count(*) filter (where l = 1) as positive, count(*) filter (where l = 0) as negative from (select "
                + LABEL + " as l from orders o left join order_risks r on r.order_id = o.id) x", Map.of()));
        return m;
    }

    /** Trains on every order of the history, then rescores the recent unreviewed orders with the new model. */
    public Map<String, Object> train() {
        List<Map<String, Object>> labelled = jdbc.queryForList("select o.id, " + LABEL + " as l from orders o left join order_risks r on r.order_id = o.id"
                + " order by o.created_at desc limit 200000", Map.of());
        Map<Long, Integer> labels = new HashMap<>();
        labelled.forEach(x -> labels.put(((Number) x.get("id")).longValue(), x.get("l") == null ? null : ((Number) x.get("l")).intValue()));
        List<RiskModelClient.Row> rows = features(labels.keySet()).stream()
                .map(c -> new RiskModelClient.Row(c.features(), labels.get(c.orderId()), FraudRules.evaluate(c.features()).score(),
                        c.createdAt().atZone(java.time.ZoneId.systemDefault()).toEpochSecond()))
                .toList();
        Map<String, Object> result = model.train(KIND, rows);
        try {
            assess(jdbc.queryForList("""
                    select r.order_id from order_risks r join orders o on o.id = r.order_id
                    where r.verdict is null and o.created_at > now() - interval '7 days'""", Map.of(), Long.class));
        } catch (Exception e) {
            log.warn("Rescoring after training failed: {}", e.getMessage());
        }
        return result;
    }

    @Scheduled(cron = "${app.fraud.train-cron:0 10 4 * * *}")
    public void nightlyTrain() {
        if (!model.available()) return;
        try {
            train();
        } catch (Exception e) {
            log.info("Nightly fraud model training skipped: {}", e.getMessage());
        }
    }

    private static String json(Object o) {
        try {
            return JSON.writeValueAsString(o);
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> parse(String s) {
        try {
            return s == null ? List.of() : JSON.readValue(s, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
