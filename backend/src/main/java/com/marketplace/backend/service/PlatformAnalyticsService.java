package com.marketplace.backend.service;

import com.marketplace.backend.entity.Category;
import com.marketplace.backend.entity.OrderStatus;
import com.marketplace.backend.entity.Role;
import com.marketplace.backend.repository.*;
import com.marketplace.backend.service.SellerAnalyticsService.Period;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.marketplace.backend.service.SellerAnalyticsService.*;

/**
 * "Thong ke & bao cao tong" of the admin console: the whole marketplace over a period (by day / week / month).
 *  - overview: GMV, orders, new users and shops, active buyers, what Shoppi earned (fee, estimated) and spent
 *    (Shoppi Vouchers), all compared with the previous period of the same length;
 *  - top shops / products / categories;
 *  - growth: one row per period with the change against the period before.
 * A sale is an order that was neither cancelled, refunded nor left unpaid (same rule as the seller reports).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PlatformAnalyticsService {

    private final SellerAnalyticsService sellerAnalytics;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final CategoryRepository categoryRepository;
    private final PlatformSettingService settings;

    record Row(Long id, LocalDateTime at, BigDecimal total, OrderStatus status, Long buyerId, Long shopId,
               BigDecimal subtotal, BigDecimal platformDiscount, BigDecimal shopDiscount) {
        boolean sale() {
            return !NOT_A_SALE.contains(status);
        }
    }

    public Period period(LocalDate from, LocalDate to, String granularity) {
        return sellerAnalytics.period(from, to, granularity);
    }

    // ==================== 1. overview ====================

    public Map<String, Object> overview(LocalDate from, LocalDate to, String granularity) {
        return overview(period(from, to, granularity));
    }

    Map<String, Object> overview(Period p) {
        Period prev = p.previous();
        List<Row> rows = rows(p), prevRows = rows(prev);
        List<Object[]> signups = userRepository.signups(p.start(), p.end(), Role.ADMIN);
        List<LocalDateTime> shops = shopRepository.openedBetween(p.start(), p.end());
        Map<Long, LocalDateTime> first = firstPurchases();
        BigDecimal rate = settings.commissionPercent();

        Map<LocalDate, List<Row>> salesBy = rows.stream().filter(Row::sale).collect(Collectors.groupingBy(r -> bucketOf(r.at().toLocalDate(), p.granularity())));
        Map<LocalDate, Long> usersBy = signups.stream().collect(Collectors.groupingBy(r -> bucketOf(((LocalDateTime) r[0]).toLocalDate(), p.granularity()), Collectors.counting()));
        Map<LocalDate, Long> shopsBy = shops.stream().collect(Collectors.groupingBy(t -> bucketOf(t.toLocalDate(), p.granularity()), Collectors.counting()));
        List<Map<String, Object>> series = new ArrayList<>();
        for (LocalDate b : buckets(p)) {
            List<Row> in = salesBy.getOrDefault(b, List.of());
            series.add(map("key", b.toString(), "label", label(b, p), "gmv", sum(in, Row::total), "orders", in.size(),
                    "buyers", in.stream().map(Row::buyerId).distinct().count(),
                    "newUsers", usersBy.getOrDefault(b, 0L), "newShops", shopsBy.getOrDefault(b, 0L)));
        }

        List<Map<String, Object>> statuses = new ArrayList<>();
        Map<OrderStatus, List<Row>> byStatus = rows.stream().collect(Collectors.groupingBy(Row::status));
        for (OrderStatus s : OrderStatus.values()) {
            List<Row> in = byStatus.get(s);
            if (in != null) statuses.add(map("status", s.name(), "orders", in.size(), "value", sum(in, Row::total)));
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("from", p.from());
        m.put("to", p.to());
        m.put("granularity", p.granularity().name().toLowerCase());
        m.put("previousFrom", prev.from());
        m.put("previousTo", prev.to());
        m.put("commissionRate", rate);
        m.put("summary", summary(rows, signups, shops.size(), first, p, rate));
        m.put("previous", summary(prevRows, userRepository.signups(prev.start(), prev.end(), Role.ADMIN),
                shopRepository.openedBetween(prev.start(), prev.end()).size(), first, prev, rate));
        m.put("totals", map("users", userRepository.countByCreatedAtBeforeAndRoleNot(p.end(), Role.ADMIN),
                "shops", shopRepository.countByCreatedAtBefore(p.end())));
        m.put("series", series);
        m.put("statuses", statuses);
        m.put("categories", categories(p, 8));
        return m;
    }

    private static Map<String, Object> summary(List<Row> rows, List<Object[]> signups, long newShops, Map<Long, LocalDateTime> first,
                                               Period p, BigDecimal rate) {
        List<Row> sales = rows.stream().filter(Row::sale).toList();
        long cancelled = rows.stream().filter(r -> r.status() == OrderStatus.CANCELLED || r.status() == OrderStatus.REFUNDED).count();
        BigDecimal gmv = sum(sales, Row::total);
        Set<Long> buyers = sales.stream().map(Row::buyerId).collect(Collectors.toSet());
        long firstTime = buyers.stream().filter(b -> first.get(b) != null && !first.get(b).isBefore(p.start()) && first.get(b).isBefore(p.end())).count();
        BigDecimal feeBase = sales.stream().map(r -> r.subtotal().subtract(r.shopDiscount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        return map("gmv", gmv,
                "orders", sales.size(),
                "avgOrderValue", sales.isEmpty() ? BigDecimal.ZERO : gmv.divide(BigDecimal.valueOf(sales.size()), 0, RoundingMode.HALF_UP),
                "buyers", buyers.size(),
                "firstTimeBuyers", firstTime,
                "shopsSelling", sales.stream().map(Row::shopId).distinct().count(),
                "newUsers", signups.size(),
                "newSellers", signups.stream().filter(r -> r[1] == Role.SELLER).count(),
                "newShops", newShops,
                "cancelRate", pct(cancelled, rows.size()),
                // what Shoppi keeps (fixed fee on goods after the shop's discounts, estimated with today's rate)
                // and what it spends (Shoppi Vouchers)
                "commission", feeBase.multiply(rate).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP),
                "platformVoucherCost", sum(sales, Row::platformDiscount));
    }

    // ==================== 2. top shops / products / categories ====================

    /** sort: gmv | orders (shops); units | revenue (products) */
    public Map<String, Object> top(LocalDate from, LocalDate to, int limit, String productSort) {
        return top(period(from, to, "day"), limit, productSort);
    }

    Map<String, Object> top(Period p, int limit, String productSort) {
        int n = Math.min(Math.max(limit, 1), 100);
        Period prev = p.previous();
        List<Row> sales = rows(p).stream().filter(Row::sale).toList();
        Map<Long, BigDecimal> prevGmv = rows(prev).stream().filter(Row::sale)
                .collect(Collectors.groupingBy(Row::shopId, Collectors.reducing(BigDecimal.ZERO, Row::total, BigDecimal::add)));
        Map<Long, Long> units = new HashMap<>();
        orderItemRepository.unitsByShop(p.start(), p.end(), NOT_A_SALE).forEach(r -> units.put((Long) r[0], ((Number) r[1]).longValue()));
        BigDecimal total = sum(sales, Row::total);

        Map<Long, List<Row>> byShop = sales.stream().collect(Collectors.groupingBy(Row::shopId));
        List<Long> ranked = byShop.keySet().stream()
                .sorted(Comparator.comparing((Long id) -> sum(byShop.get(id), Row::total)).reversed()).limit(n).toList();
        Map<Long, String> names = new HashMap<>();
        shopRepository.findAllById(ranked).forEach(s -> names.put(s.getId(), s.getName()));
        Map<Long, Object[]> ratings = new HashMap<>();
        if (!ranked.isEmpty()) orderRepository.shopRatings(ranked).forEach(r -> ratings.put((Long) r[0], r));
        List<Map<String, Object>> shops = new ArrayList<>();
        int rank = 1;
        for (Long id : ranked) {
            List<Row> in = byShop.get(id);
            BigDecimal gmv = sum(in, Row::total);
            BigDecimal before = prevGmv.getOrDefault(id, BigDecimal.ZERO);
            Object[] r = ratings.get(id);
            shops.add(map("rank", rank++, "shopId", id, "name", names.get(id), "gmv", gmv, "orders", in.size(),
                    "units", units.getOrDefault(id, 0L), "buyers", in.stream().map(Row::buyerId).distinct().count(),
                    "avgOrderValue", gmv.divide(BigDecimal.valueOf(in.size()), 0, RoundingMode.HALF_UP),
                    "share", total.signum() == 0 ? 0 : gmv.multiply(BigDecimal.valueOf(1000)).divide(total, 0, RoundingMode.HALF_UP).doubleValue() / 10,
                    "previousGmv", before, "growth", growth(gmv, before),
                    "rating", r == null ? null : Math.round(((Number) r[1]).doubleValue() * 10) / 10.0,
                    "ratingCount", r == null ? 0 : ((Number) r[2]).longValue()));
        }

        boolean byRevenue = "revenue".equalsIgnoreCase(productSort);
        Map<Long, Category> cats = categoryRepository.findAll().stream().collect(Collectors.toMap(Category::getId, Function.identity()));
        List<Map<String, Object>> products = orderItemRepository.productSales(p.start(), p.end(), NOT_A_SALE).stream()
                .sorted(Comparator.comparing((Object[] r) -> byRevenue ? money(r[7]) : BigDecimal.valueOf(((Number) r[6]).longValue())).reversed()
                        .thenComparing(r -> money(r[7]), Comparator.reverseOrder()))
                .limit(n).map(r -> {
                    Category c = r[5] == null ? null : cats.get((Long) r[5]);
                    return map("productId", r[0], "name", r[1], "thumbnailUrl", r[2], "shopId", r[3], "shopName", r[4],
                            "category", c == null ? null : (c.getParent() != null ? c.getParent().getName() + " › " : "") + c.getName(),
                            "units", ((Number) r[6]).longValue(), "revenue", money(r[7]), "orders", ((Number) r[8]).longValue());
                }).collect(Collectors.toCollection(ArrayList::new));
        for (int i = 0; i < products.size(); i++) products.get(i).put("rank", i + 1);

        return map("from", p.from(), "to", p.to(), "productSort", byRevenue ? "revenue" : "units", "gmv", total,
                "shops", shops, "products", products, "categories", categories(p, 20));
    }

    /** Sales per top-level category, largest first. */
    List<Map<String, Object>> categories(Period p, int limit) {
        Map<Long, Category> cats = categoryRepository.findAll().stream().collect(Collectors.toMap(Category::getId, Function.identity()));
        Map<String, BigDecimal> revenue = new HashMap<>();
        Map<String, Long> unitsBy = new HashMap<>();
        for (Object[] r : orderItemRepository.productSales(p.start(), p.end(), NOT_A_SALE)) {
            Category c = r[5] == null ? null : cats.get((Long) r[5]);
            String root = c == null ? "Chưa phân loại" : (c.getParent() != null ? c.getParent().getName() : c.getName());
            revenue.merge(root, money(r[7]), BigDecimal::add);
            unitsBy.merge(root, ((Number) r[6]).longValue(), Long::sum);
        }
        BigDecimal total = revenue.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return revenue.entrySet().stream().sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed()).limit(limit)
                .map(e -> map("name", e.getKey(), "revenue", e.getValue(), "units", unitsBy.get(e.getKey()),
                        "share", total.signum() == 0 ? 0 : e.getValue().multiply(BigDecimal.valueOf(1000)).divide(total, 0, RoundingMode.HALF_UP).doubleValue() / 10))
                .collect(Collectors.toList());
    }

    // ==================== 3. growth ====================

    /** One row per day / week / month (default: the last 12 months), each compared with the one before. */
    public Map<String, Object> growth(LocalDate from, LocalDate to, String granularity) {
        Period p = period(from, to, granularity == null || granularity.isBlank() ? "month" : granularity);
        return growth(p);
    }

    Map<String, Object> growth(Period p) {
        List<LocalDate> buckets = buckets(p);
        // one extra period before the first, so the first row has something to compare with
        LocalDate before = switch (p.granularity()) {
            case DAY -> buckets.get(0).minusDays(1);
            case WEEK -> buckets.get(0).minusWeeks(1);
            case MONTH -> buckets.get(0).minusMonths(1);
        };
        Period wide = new Period(before, p.to(), p.granularity());
        List<Row> sales = rows(wide).stream().filter(Row::sale).toList();
        Map<LocalDate, List<Row>> salesBy = sales.stream().collect(Collectors.groupingBy(r -> bucketOf(r.at().toLocalDate(), p.granularity())));
        Map<LocalDate, Long> usersBy = userRepository.signups(wide.start(), wide.end(), Role.ADMIN).stream()
                .collect(Collectors.groupingBy(r -> bucketOf(((LocalDateTime) r[0]).toLocalDate(), p.granularity()), Collectors.counting()));
        Map<LocalDate, Long> shopsBy = shopRepository.openedBetween(wide.start(), wide.end()).stream()
                .collect(Collectors.groupingBy(t -> bucketOf(t.toLocalDate(), p.granularity()), Collectors.counting()));
        Map<Long, LocalDateTime> first = firstPurchases();

        long users = userRepository.countByCreatedAtBeforeAndRoleNot(p.start(), Role.ADMIN);
        long shops = shopRepository.countByCreatedAtBefore(p.start());
        List<LocalDate> all = new ArrayList<>(List.of(before));
        all.addAll(buckets);
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> last = null;
        for (LocalDate b : all) {
            List<Row> in = salesBy.getOrDefault(b, List.of());
            BigDecimal gmv = sum(in, Row::total);
            Set<Long> buyers = in.stream().map(Row::buyerId).collect(Collectors.toSet());
            long newBuyers = buyers.stream().filter(id -> first.get(id) != null && bucketOf(first.get(id).toLocalDate(), p.granularity()).equals(b)).count();
            long nu = usersBy.getOrDefault(b, 0L), ns = shopsBy.getOrDefault(b, 0L);
            Map<String, Object> row = map("key", b.toString(), "label", label(b, p), "gmv", gmv, "orders", in.size(),
                    "avgOrderValue", in.isEmpty() ? BigDecimal.ZERO : gmv.divide(BigDecimal.valueOf(in.size()), 0, RoundingMode.HALF_UP),
                    "activeBuyers", buyers.size(), "newBuyers", newBuyers, "returningBuyers", buyers.size() - newBuyers,
                    "activeShops", in.stream().map(Row::shopId).distinct().count(), "newUsers", nu, "newShops", ns);
            if (b.equals(before)) {
                last = row;
                continue;
            }
            users += nu;
            shops += ns;
            row.put("totalUsers", users);
            row.put("totalShops", shops);
            row.put("gmvGrowth", growth(gmv, (BigDecimal) last.get("gmv")));
            row.put("ordersGrowth", growth(BigDecimal.valueOf(in.size()), BigDecimal.valueOf(((Number) last.get("orders")).longValue())));
            row.put("buyersGrowth", growth(BigDecimal.valueOf(buyers.size()), BigDecimal.valueOf(((Number) last.get("activeBuyers")).longValue())));
            row.put("newUsersGrowth", growth(BigDecimal.valueOf(nu), BigDecimal.valueOf(((Number) last.get("newUsers")).longValue())));
            rows.add(row);
            last = row;
        }

        // the whole period: first vs last row, and the average change per period (compound)
        Map<String, Object> firstRow = rows.get(0), lastRow = rows.get(rows.size() - 1);
        BigDecimal g0 = (BigDecimal) firstRow.get("gmv"), g1 = (BigDecimal) lastRow.get("gmv");
        Double compound = null;
        if (rows.size() > 1 && g0.signum() > 0 && g1.signum() > 0) {
            compound = Math.round((Math.pow(g1.doubleValue() / g0.doubleValue(), 1.0 / (rows.size() - 1)) - 1) * 1000) / 10.0;
        }
        Map<String, Object> best = rows.stream().max(Comparator.comparing(r -> (BigDecimal) r.get("gmv"))).orElse(null);
        return map("from", p.from(), "to", p.to(), "granularity", p.granularity().name().toLowerCase(), "rows", rows,
                "summary", map("gmv", rows.stream().map(r -> (BigDecimal) r.get("gmv")).reduce(BigDecimal.ZERO, BigDecimal::add),
                        "orders", rows.stream().mapToLong(r -> ((Number) r.get("orders")).longValue()).sum(),
                        "newUsers", rows.stream().mapToLong(r -> ((Number) r.get("newUsers")).longValue()).sum(),
                        "newShops", rows.stream().mapToLong(r -> ((Number) r.get("newShops")).longValue()).sum(),
                        "firstToLast", growth(g1, g0), "compoundGrowth", compound,
                        "bestLabel", best == null || ((BigDecimal) best.get("gmv")).signum() == 0 ? null : best.get("label"),
                        "bestGmv", best == null ? BigDecimal.ZERO : best.get("gmv"),
                        "totalUsers", lastRow.get("totalUsers"), "totalShops", lastRow.get("totalShops")));
    }

    // ==================== helpers ====================

    private List<Row> rows(Period p) {
        return orderRepository.platformRows(p.start(), p.end()).stream()
                .map(r -> new Row((Long) r[0], (LocalDateTime) r[1], money(r[2]), (OrderStatus) r[3], (Long) r[4], (Long) r[5],
                        money(r[6]), money(r[7]), money(r[8]).add(money(r[9]))))
                .toList();
    }

    private Map<Long, LocalDateTime> firstPurchases() {
        Map<Long, LocalDateTime> m = new HashMap<>();
        orderRepository.firstPurchases(NOT_A_SALE).forEach(r -> m.put((Long) r[0], (LocalDateTime) r[1]));
        return m;
    }

    /** % change, null when there is nothing to compare with */
    static Double growth(BigDecimal now, BigDecimal before) {
        if (before == null || before.signum() == 0) return now.signum() == 0 ? 0.0 : null;
        return now.subtract(before).multiply(BigDecimal.valueOf(1000)).divide(before, 0, RoundingMode.HALF_UP).doubleValue() / 10;
    }

    private static <T> BigDecimal sum(List<T> rows, Function<T, BigDecimal> f) {
        return rows.stream().map(f).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal money(Object o) {
        if (o == null) return BigDecimal.ZERO;
        return o instanceof BigDecimal b ? b : new BigDecimal(o.toString());
    }
}
