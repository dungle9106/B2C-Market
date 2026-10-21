package com.marketplace.backend.service;

import com.marketplace.backend.entity.OrderStatus;
import com.marketplace.backend.entity.Product;
import com.marketplace.backend.entity.ProductEvent;
import com.marketplace.backend.entity.Shop;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.OrderItemRepository;
import com.marketplace.backend.repository.OrderRepository;
import com.marketplace.backend.repository.ProductEventRepository;
import com.marketplace.backend.repository.ProductRepository;
import com.marketplace.backend.repository.ReviewRepository;
import com.marketplace.backend.repository.ShopRepository;
import com.marketplace.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

// "Thong ke & Bao cao" of the seller centre: revenue, products & stock, traffic & conversion, reviews.
// Everything is computed in memory from the rows of the chosen period (at most 366 days of one shop).
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SellerAnalyticsService {

    // same rule as the dashboard: these orders are not sales
    static final Set<OrderStatus> NOT_A_SALE = EnumSet.of(OrderStatus.CANCELLED, OrderStatus.REFUNDED, OrderStatus.AWAITING_PAYMENT);
    static final int MAX_DAYS = 366;
    static final int LOW_STOCK = 5;
    // a product needs attention when this many people looked at it and nobody bought
    static final int ATTENTION_VISITORS = 5;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM/yyyy");

    private final ShopRepository shopRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final ProductEventRepository eventRepository;
    private final ReviewRepository reviewRepository;

    // ==================== period ====================

    public enum Granularity {
        DAY, WEEK, MONTH;

        static Granularity parse(String s) {
            if (s == null || s.isBlank()) return DAY;
            try {
                return valueOf(s.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ApiException("Kiểu thống kê không hợp lệ (day/week/month)", HttpStatus.BAD_REQUEST);
            }
        }
    }

    /** Inclusive dates. */
    public record Period(LocalDate from, LocalDate to, Granularity granularity) {
        LocalDateTime start() {
            return from.atStartOfDay();
        }

        LocalDateTime end() {
            return to.plusDays(1).atStartOfDay();
        }

        long days() {
            return ChronoUnit.DAYS.between(from, to) + 1;
        }

        // the same number of days right before this one, for "so voi ky truoc"
        Period previous() {
            return new Period(from.minusDays(days()), from.minusDays(1), granularity);
        }

        // days that already happened (a period may end in the future)
        long elapsedDays() {
            LocalDate last = to.isAfter(LocalDate.now()) ? LocalDate.now() : to;
            return Math.max(1, ChronoUnit.DAYS.between(from, last) + 1);
        }
    }

    public Period period(LocalDate from, LocalDate to, String granularity) {
        Granularity g = Granularity.parse(granularity);
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : switch (g) {
            case DAY -> end.minusDays(29);
            case WEEK -> end.with(DayOfWeek.MONDAY).minusWeeks(11);
            case MONTH -> end.withDayOfMonth(1).minusMonths(11);
        };
        if (start.isAfter(end)) throw bad("Ngày bắt đầu phải trước ngày kết thúc");
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) throw bad("Mỗi lần chỉ xem được tối đa 366 ngày");
        return new Period(start, end, g);
    }

    static LocalDate bucketOf(LocalDate d, Granularity g) {
        return switch (g) {
            case DAY -> d;
            case WEEK -> d.with(DayOfWeek.MONDAY);
            case MONTH -> d.withDayOfMonth(1);
        };
    }

    static List<LocalDate> buckets(Period p) {
        List<LocalDate> list = new ArrayList<>();
        for (LocalDate b = bucketOf(p.from(), p.granularity()); !b.isAfter(p.to()); ) {
            list.add(b);
            b = switch (p.granularity()) {
                case DAY -> b.plusDays(1);
                case WEEK -> b.plusWeeks(1);
                case MONTH -> b.plusMonths(1);
            };
        }
        return list;
    }

    static String label(LocalDate b, Period p) {
        return switch (p.granularity()) {
            case DAY -> b.format(DAY);
            case WEEK -> {
                LocalDate s = b.isBefore(p.from()) ? p.from() : b;
                LocalDate e = b.plusDays(6).isAfter(p.to()) ? p.to() : b.plusDays(6);
                yield s.format(DAY) + "–" + e.format(DAY);
            }
            case MONTH -> b.format(MONTH);
        };
    }

    // ==================== raw rows ====================

    record OrderRow(Long id, LocalDateTime at, BigDecimal total, OrderStatus status, Long buyerId,
                    Integer shopRating, Integer shippingRating) {
        boolean sale() {
            return !NOT_A_SALE.contains(status);
        }
    }

    record ItemRow(Long productId, String name, Long orderId, int quantity, BigDecimal price,
                   OrderStatus status, Long buyerId, LocalDateTime at) {
        boolean sale() {
            return !NOT_A_SALE.contains(status);
        }

        BigDecimal value() {
            return price.multiply(BigDecimal.valueOf(quantity));
        }
    }

    record EventRow(Long productId, ProductEvent.Type type, String visitor, LocalDateTime at) {
        boolean view() {
            return type == ProductEvent.Type.VIEW;
        }
    }

    record ReviewRow(Long id, Long productId, int rating, LocalDateTime at, boolean replied) {
    }

    List<OrderRow> orders(Long shopId, Period p) {
        return orderRepository.analyticsRows(shopId, p.start(), p.end()).stream()
                .map(r -> new OrderRow((Long) r[0], (LocalDateTime) r[1], money(r[2]), (OrderStatus) r[3], (Long) r[4],
                        (Integer) r[5], (Integer) r[6]))
                .toList();
    }

    List<ItemRow> items(Long shopId, Period p) {
        return orderItemRepository.analyticsRows(shopId, p.start(), p.end()).stream()
                .map(r -> new ItemRow((Long) r[0], (String) r[1], (Long) r[2], ((Number) r[3]).intValue(), money(r[4]),
                        (OrderStatus) r[5], (Long) r[6], (LocalDateTime) r[7]))
                .toList();
    }

    List<EventRow> events(Long shopId, Period p) {
        return eventRepository.rowsForShop(shopId, p.start(), p.end()).stream()
                .map(r -> new EventRow((Long) r[0], (ProductEvent.Type) r[1], (String) r[2], (LocalDateTime) r[3]))
                .toList();
    }

    List<ReviewRow> reviewRows(Long shopId, Period p) {
        return reviewRepository.analyticsRows(shopId, p.start(), p.end()).stream()
                .map(r -> new ReviewRow((Long) r[0], (Long) r[1], ((Number) r[2]).intValue(), (LocalDateTime) r[3],
                        r[4] != null && !((String) r[4]).isBlank()))
                .toList();
    }

    // ==================== 1. revenue ====================

    public Map<String, Object> revenue(LocalDate from, LocalDate to, String granularity) {
        Shop shop = myShop();
        Period p = period(from, to, granularity);
        return revenue(shop.getId(), p);
    }

    Map<String, Object> revenue(Long shopId, Period p) {
        List<OrderRow> orders = orders(shopId, p);
        Map<Long, Integer> units = unitsByOrder(items(shopId, p));
        Period prev = p.previous();
        List<OrderRow> prevOrders = orders(shopId, prev);
        Map<Long, Integer> prevUnits = unitsByOrder(items(shopId, prev));

        List<Map<String, Object>> series = new ArrayList<>();
        Map<LocalDate, List<OrderRow>> byBucket = orders.stream().filter(OrderRow::sale)
                .collect(Collectors.groupingBy(o -> bucketOf(o.at().toLocalDate(), p.granularity())));
        for (LocalDate b : buckets(p)) {
            List<OrderRow> in = byBucket.getOrDefault(b, List.of());
            series.add(map("key", b.toString(), "label", label(b, p),
                    "sales", sum(in, OrderRow::total),
                    "orders", in.size(),
                    "items", in.stream().mapToLong(o -> units.getOrDefault(o.id(), 0)).sum(),
                    "buyers", in.stream().map(OrderRow::buyerId).distinct().count()));
        }

        // every status, including the ones that are not sales, so the seller sees where orders went
        List<Map<String, Object>> statuses = new ArrayList<>();
        Map<OrderStatus, List<OrderRow>> byStatus = orders.stream().collect(Collectors.groupingBy(OrderRow::status));
        for (OrderStatus s : OrderStatus.values()) {
            List<OrderRow> in = byStatus.get(s);
            if (in != null) statuses.add(map("status", s.name(), "orders", in.size(), "value", sum(in, OrderRow::total)));
        }

        return map("from", p.from(), "to", p.to(), "granularity", p.granularity().name().toLowerCase(),
                "summary", revenueSummary(orders, units),
                "previous", revenueSummary(prevOrders, prevUnits),
                "previousFrom", prev.from(), "previousTo", prev.to(),
                "series", series,
                "statuses", statuses);
    }

    private static Map<Long, Integer> unitsByOrder(List<ItemRow> items) {
        return items.stream().collect(Collectors.groupingBy(ItemRow::orderId, Collectors.summingInt(ItemRow::quantity)));
    }

    private static Map<String, Object> revenueSummary(List<OrderRow> orders, Map<Long, Integer> units) {
        List<OrderRow> sales = orders.stream().filter(OrderRow::sale).toList();
        List<OrderRow> cancelled = orders.stream()
                .filter(o -> o.status() == OrderStatus.CANCELLED || o.status() == OrderStatus.REFUNDED).toList();
        BigDecimal total = sum(sales, OrderRow::total);
        return map("sales", total,
                "orders", sales.size(),
                "items", sales.stream().mapToLong(o -> units.getOrDefault(o.id(), 0)).sum(),
                "buyers", sales.stream().map(OrderRow::buyerId).distinct().count(),
                "avgOrderValue", sales.isEmpty() ? BigDecimal.ZERO : total.divide(BigDecimal.valueOf(sales.size()), 0, RoundingMode.HALF_UP),
                "delivered", sum(orders.stream().filter(o -> o.status() == OrderStatus.DELIVERED).toList(), OrderRow::total),
                "cancelledOrders", cancelled.size(),
                "cancelledValue", sum(cancelled, OrderRow::total),
                "cancelRate", pct(cancelled.size(), orders.size()));
    }

    // ==================== 2. products & stock ====================

    public Map<String, Object> products(LocalDate from, LocalDate to) {
        Shop shop = myShop();
        return products(shop.getId(), period(from, to, "day"));
    }

    Map<String, Object> products(Long shopId, Period p) {
        List<Product> products = productRepository.findByShopId(shopId);
        List<ItemRow> items = items(shopId, p).stream().filter(ItemRow::sale).toList();
        List<EventRow> events = events(shopId, p);
        Map<Long, List<ItemRow>> itemsByProduct = items.stream().filter(i -> i.productId() != null)
                .collect(Collectors.groupingBy(ItemRow::productId));
        Map<Long, List<EventRow>> eventsByProduct = events.stream().collect(Collectors.groupingBy(EventRow::productId));
        long days = p.elapsedDays();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Product pr : products) {
            List<ItemRow> its = itemsByProduct.getOrDefault(pr.getId(), List.of());
            List<EventRow> evs = eventsByProduct.getOrDefault(pr.getId(), List.of());
            long units = its.stream().mapToLong(ItemRow::quantity).sum();
            long visitors = evs.stream().filter(EventRow::view).map(EventRow::visitor).distinct().count();
            long cart = evs.stream().filter(e -> !e.view()).map(EventRow::visitor).distinct().count();
            long buyers = its.stream().map(ItemRow::buyerId).distinct().count();
            int stock = pr.getStock() == null ? 0 : pr.getStock();
            // how many days the current stock lasts at this period's selling pace
            Double daysOfStock = units == 0 ? null : Math.round(stock * 10.0 * days / units) / 10.0;
            rows.add(map("id", pr.getId(), "name", pr.getName(), "thumbnailUrl", pr.getThumbnailUrl(),
                    "price", pr.getPrice(), "stock", stock, "active", pr.isActive(),
                    "views", evs.stream().filter(EventRow::view).count(),
                    "visitors", visitors,
                    "cartVisitors", cart,
                    "units", units,
                    "orders", its.stream().map(ItemRow::orderId).distinct().count(),
                    "buyers", buyers,
                    "sales", sum(its, ItemRow::value),
                    "conversion", pct(buyers, visitors),
                    "cartRate", pct(cart, visitors),
                    "daysOfStock", daysOfStock,
                    "soldCount", pr.getSoldCount(),
                    "ratingAvg", pr.getRatingAvg(), "ratingCount", pr.getRatingCount()));
        }
        rows.sort(Comparator.comparing((Map<String, Object> r) -> (BigDecimal) r.get("sales")).reversed()
                .thenComparing(r -> -((Number) r.get("units")).longValue())
                .thenComparing(r -> -((Number) r.get("visitors")).longValue()));

        List<Product> active = products.stream().filter(Product::isActive).toList();
        Predicate<Product> soldOut = pr -> pr.getStock() == null || pr.getStock() <= 0;
        long visitors = events.stream().filter(EventRow::view).map(EventRow::visitor).distinct().count();
        long buyers = items.stream().map(ItemRow::buyerId).distinct().count();
        Map<String, Object> inventory = map(
                "totalStock", active.stream().mapToLong(pr -> Math.max(0, pr.getStock() == null ? 0 : pr.getStock())).sum(),
                "stockValue", active.stream().filter(soldOut.negate())
                        .map(pr -> pr.getPrice().multiply(BigDecimal.valueOf(pr.getStock())))
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                "outOfStock", active.stream().filter(soldOut).count(),
                "lowStock", active.stream().filter(pr -> !soldOut.test(pr) && pr.getStock() <= LOW_STOCK).count(),
                // in stock, on sale, yet nothing sold in the period
                "slowMovers", active.stream().filter(pr -> !soldOut.test(pr) && !itemsByProduct.containsKey(pr.getId())).count(),
                "hidden", products.size() - active.size());

        return map("from", p.from(), "to", p.to(),
                "summary", map("products", products.size(),
                        "productsSold", itemsByProduct.size(),
                        "units", items.stream().mapToLong(ItemRow::quantity).sum(),
                        "sales", sum(items, ItemRow::value),
                        "views", events.stream().filter(EventRow::view).count(),
                        "visitors", visitors,
                        "buyers", buyers,
                        "conversion", pct(buyers, visitors)),
                "inventory", inventory,
                "lowStockThreshold", LOW_STOCK,
                "products", rows);
    }

    // ==================== 3. traffic & conversion ====================

    public Map<String, Object> traffic(LocalDate from, LocalDate to, String granularity) {
        Shop shop = myShop();
        return traffic(shop.getId(), period(from, to, granularity));
    }

    Map<String, Object> traffic(Long shopId, Period p) {
        List<EventRow> events = events(shopId, p);
        List<OrderRow> sales = orders(shopId, p).stream().filter(OrderRow::sale).toList();
        Period prev = p.previous();

        Map<LocalDate, List<EventRow>> evByBucket = events.stream()
                .collect(Collectors.groupingBy(e -> bucketOf(e.at().toLocalDate(), p.granularity())));
        Map<LocalDate, List<OrderRow>> salesByBucket = sales.stream()
                .collect(Collectors.groupingBy(o -> bucketOf(o.at().toLocalDate(), p.granularity())));
        List<Map<String, Object>> series = new ArrayList<>();
        for (LocalDate b : buckets(p)) {
            Map<String, Object> s = trafficSummary(evByBucket.getOrDefault(b, List.of()), salesByBucket.getOrDefault(b, List.of()));
            s.put("key", b.toString());
            s.put("label", label(b, p));
            series.add(s);
        }

        Map<String, Object> summary = trafficSummary(events, sales);
        List<Map<String, Object>> funnel = List.of(
                map("step", "Lượt truy cập", "value", summary.get("visitors")),
                map("step", "Thêm vào giỏ hàng", "value", summary.get("cartVisitors")),
                map("step", "Đặt hàng", "value", summary.get("buyers")));

        // per product: who looked, who bought
        Map<Long, Product> products = productRepository.findByShopId(shopId).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, Set<Long>> buyersByProduct = new HashMap<>();
        for (ItemRow i : items(shopId, p)) {
            if (i.sale() && i.productId() != null) buyersByProduct.computeIfAbsent(i.productId(), k -> new HashSet<>()).add(i.buyerId());
        }
        List<Map<String, Object>> perProduct = new ArrayList<>();
        events.stream().collect(Collectors.groupingBy(EventRow::productId)).forEach((id, evs) -> {
            Product pr = products.get(id);
            if (pr == null) return;
            long visitors = evs.stream().filter(EventRow::view).map(EventRow::visitor).distinct().count();
            long cart = evs.stream().filter(e -> !e.view()).map(EventRow::visitor).distinct().count();
            long buyers = buyersByProduct.getOrDefault(id, Set.of()).size();
            perProduct.add(map("id", id, "name", pr.getName(), "thumbnailUrl", pr.getThumbnailUrl(),
                    "views", evs.stream().filter(EventRow::view).count(), "visitors", visitors, "cartVisitors", cart,
                    "buyers", buyers, "conversion", pct(buyers, visitors), "price", pr.getPrice(), "stock", pr.getStock()));
        });
        Comparator<Map<String, Object>> byVisitors = Comparator.comparing(m -> -((Number) m.get("visitors")).longValue());
        List<Map<String, Object>> top = perProduct.stream().sorted(byVisitors).limit(10).toList();
        List<Map<String, Object>> attention = perProduct.stream()
                .filter(m -> ((Number) m.get("visitors")).longValue() >= ATTENTION_VISITORS && ((Number) m.get("buyers")).longValue() == 0)
                .sorted(byVisitors).limit(5).toList();

        return map("from", p.from(), "to", p.to(), "granularity", p.granularity().name().toLowerCase(),
                "summary", summary,
                "previous", trafficSummary(events(shopId, prev), orders(shopId, prev).stream().filter(OrderRow::sale).toList()),
                "previousFrom", prev.from(), "previousTo", prev.to(),
                "series", series,
                "funnel", funnel,
                "topProducts", top,
                "attention", attention,
                "attentionThreshold", ATTENTION_VISITORS);
    }

    private static Map<String, Object> trafficSummary(List<EventRow> events, List<OrderRow> sales) {
        long views = events.stream().filter(EventRow::view).count();
        long visitors = events.stream().filter(EventRow::view).map(EventRow::visitor).distinct().count();
        long cart = events.stream().filter(e -> !e.view()).map(EventRow::visitor).distinct().count();
        long buyers = sales.stream().map(OrderRow::buyerId).distinct().count();
        return map("views", views, "visitors", visitors, "cartVisitors", cart, "buyers", buyers, "orders", sales.size(),
                "conversion", pct(buyers, visitors), "cartRate", pct(cart, visitors),
                "viewsPerVisitor", visitors == 0 ? 0.0 : Math.round(views * 100.0 / visitors) / 100.0);
    }

    /** Today and yesterday for the dashboard: [visitorsToday, visitorsYesterday, viewsToday, viewsYesterday]. */
    public long[] visitorsTodayAndYesterday(Long shopId) {
        LocalDate today = LocalDate.now();
        List<EventRow> events = events(shopId, new Period(today.minusDays(1), today, Granularity.DAY));
        long[] out = new long[4];
        for (int i = 0; i < 2; i++) {
            LocalDate d = today.minusDays(i);
            List<EventRow> views = events.stream().filter(e -> e.view() && e.at().toLocalDate().equals(d)).toList();
            out[i] = views.stream().map(EventRow::visitor).distinct().count();
            out[2 + i] = views.size();
        }
        return out;
    }

    // ==================== 4. customer reviews ====================

    public Map<String, Object> reviews(LocalDate from, LocalDate to, String granularity) {
        Shop shop = myShop();
        return reviews(shop.getId(), period(from, to, granularity));
    }

    Map<String, Object> reviews(Long shopId, Period p) {
        List<ReviewRow> rows = reviewRows(shopId, p);
        Period prev = p.previous();

        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int s = 5; s >= 1; s--) distribution.put(s, 0L);
        rows.forEach(r -> distribution.merge(r.rating(), 1L, Long::sum));

        Map<LocalDate, List<ReviewRow>> byBucket = rows.stream()
                .collect(Collectors.groupingBy(r -> bucketOf(r.at().toLocalDate(), p.granularity())));
        List<Map<String, Object>> series = new ArrayList<>();
        for (LocalDate b : buckets(p)) {
            List<ReviewRow> in = byBucket.getOrDefault(b, List.of());
            series.add(map("key", b.toString(), "label", label(b, p), "count", in.size(), "average", avg(in),
                    "negative", in.stream().filter(r -> r.rating() <= 2).count()));
        }

        Map<Long, Product> products = productRepository.findByShopId(shopId).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        List<Map<String, Object>> perProduct = new ArrayList<>();
        rows.stream().collect(Collectors.groupingBy(ReviewRow::productId)).forEach((id, in) -> {
            Product pr = products.get(id);
            if (pr == null) return;
            perProduct.add(map("id", id, "name", pr.getName(), "thumbnailUrl", pr.getThumbnailUrl(),
                    "count", in.size(), "average", avg(in),
                    "negative", in.stream().filter(r -> r.rating() <= 2).count(),
                    "unreplied", in.stream().filter(r -> !r.replied()).count()));
        });
        // worst first: lowest average, then most reviews
        perProduct.sort(Comparator.comparing((Map<String, Object> m) -> (Double) m.get("average"))
                .thenComparing(m -> -((Number) m.get("count")).longValue()));

        List<OrderRow> orders = orders(shopId, p);
        Map<String, Object> service = map(
                "shopRating", avgOf(orders, OrderRow::shopRating), "shopRatingCount", orders.stream().filter(o -> o.shopRating() != null).count(),
                "shippingRating", avgOf(orders, OrderRow::shippingRating), "shippingRatingCount", orders.stream().filter(o -> o.shippingRating() != null).count());

        Map<String, Object> summary = reviewSummary(rows);
        summary.put("withMedia", reviewRepository.countWithMediaForShop(shopId, p.start(), p.end()));
        return map("from", p.from(), "to", p.to(), "granularity", p.granularity().name().toLowerCase(),
                "summary", summary,
                "previous", reviewSummary(reviewRows(shopId, prev)),
                "previousFrom", prev.from(), "previousTo", prev.to(),
                "distribution", distribution,
                "series", series,
                "products", perProduct,
                "service", service);
    }

    private static Map<String, Object> reviewSummary(List<ReviewRow> rows) {
        long replied = rows.stream().filter(ReviewRow::replied).count();
        long positive = rows.stream().filter(r -> r.rating() >= 4).count();
        long negative = rows.stream().filter(r -> r.rating() <= 2).count();
        return map("count", rows.size(), "average", avg(rows),
                "positive", positive, "positiveRate", pct(positive, rows.size()),
                "neutral", rows.stream().filter(r -> r.rating() == 3).count(),
                "negative", negative,
                "unrepliedNegative", rows.stream().filter(r -> r.rating() <= 2 && !r.replied()).count(),
                "replied", replied, "replyRate", pct(replied, rows.size()));
    }

    private static double avg(List<ReviewRow> rows) {
        return rows.isEmpty() ? 0.0 : Math.round(rows.stream().mapToInt(ReviewRow::rating).average().orElse(0) * 10) / 10.0;
    }

    private static Double avgOf(List<OrderRow> orders, Function<OrderRow, Integer> f) {
        OptionalDouble a = orders.stream().map(f).filter(Objects::nonNull).mapToInt(Integer::intValue).average();
        return a.isPresent() ? Math.round(a.getAsDouble() * 10) / 10.0 : null;
    }

    // ==================== helpers ====================

    Shop myShop() {
        return shopRepository.findBySellerId(CurrentUser.id())
                .orElseThrow(() -> bad("Bạn chưa có shop"));
    }

    /** Percent with 2 decimals, never above 100 (a buyer may come from a link without a tracked view). */
    static double pct(long part, long whole) {
        if (whole <= 0) return 0.0;
        return Math.min(100.0, Math.round(part * 10000.0 / whole) / 100.0);
    }

    private static <T> BigDecimal sum(List<T> rows, Function<T, BigDecimal> f) {
        return rows.stream().map(f).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal money(Object o) {
        return o == null ? BigDecimal.ZERO : o instanceof BigDecimal b ? b : new BigDecimal(o.toString());
    }

    // insertion-ordered map that allows nulls (Map.of does not)
    static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }
}
