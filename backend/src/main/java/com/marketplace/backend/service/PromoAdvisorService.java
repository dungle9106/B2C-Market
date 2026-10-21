package com.marketplace.backend.service;

import com.marketplace.backend.entity.OrderStatus;
import com.marketplace.backend.entity.Shop;
import com.marketplace.backend.repository.OrderRepository;
import com.marketplace.backend.repository.ProductEventRepository;
import com.marketplace.backend.service.SellerAnalyticsService.EventRow;
import com.marketplace.backend.service.SellerAnalyticsService.Granularity;
import com.marketplace.backend.service.SellerAnalyticsService.ItemRow;
import com.marketplace.backend.service.SellerAnalyticsService.OrderRow;
import com.marketplace.backend.service.SellerAnalyticsService.Period;
import com.marketplace.backend.entity.ProductEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * "Goi y thoi diem nen chay khuyen mai": when this shop's buyers buy (weekday, hour, part of the month,
 * last 90 days), whether sales are trending down, which shopping events are coming, and which products
 * to discount (or not). A shop with too little history is compared with the whole marketplace instead.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PromoAdvisorService {

    static final int WINDOW_DAYS = 90;
    static final int MIN_ORDERS = 10;
    private static final String[] WEEKDAYS = {"Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ Nhật"};
    // same time slots as the seller's Flash Sale page
    private static final int[][] SLOTS = {{0, 12}, {12, 18}, {18, 21}, {21, 24}};
    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    // first day of the lunar new year
    private static final Map<Integer, LocalDate> TET = Map.of(2026, LocalDate.of(2026, 2, 17), 2027, LocalDate.of(2027, 2, 6),
            2028, LocalDate.of(2028, 1, 26), 2029, LocalDate.of(2029, 2, 13), 2030, LocalDate.of(2030, 2, 3));

    private final SellerAnalyticsService analytics;
    private final OrderRepository orderRepository;
    private final ProductEventRepository eventRepository;
    private final AIService aiService;

    @SuppressWarnings("unchecked")
    public Map<String, Object> advise() {
        Shop shop = analytics.myShop();
        LocalDate today = LocalDate.now();
        Period window = new Period(today.minusDays(WINDOW_DAYS - 1), today, Granularity.DAY);
        List<OrderRow> orders = analytics.orders(shop.getId(), window).stream().filter(OrderRow::sale).toList();
        List<EventRow> events = analytics.events(shop.getId(), window);
        String scope = "shop";
        if (orders.size() < MIN_ORDERS) {
            scope = "platform";
            orders = orderRepository.analyticsRowsAll(window.start(), window.end()).stream()
                    .map(r -> new OrderRow((Long) r[0], (LocalDateTime) r[1], (BigDecimal) r[2], (OrderStatus) r[3], (Long) r[4], null, null))
                    .filter(OrderRow::sale).toList();
            events = eventRepository.rowsAll(window.start(), window.end()).stream()
                    .map(r -> new EventRow((Long) r[0], (ProductEvent.Type) r[1], (String) r[2], (LocalDateTime) r[3])).toList();
        }
        List<EventRow> views = events.stream().filter(EventRow::view).toList();

        // ---- weekday ----
        int[] occurrences = new int[7];
        for (LocalDate d = window.from(); !d.isAfter(today); d = d.plusDays(1)) occurrences[d.getDayOfWeek().getValue() - 1]++;
        double[] salesByDay = new double[7];
        int[] ordersByDay = new int[7], viewsByDay = new int[7];
        int[] ordersByHour = new int[24], viewsByHour = new int[24];
        for (OrderRow o : orders) {
            int w = o.at().getDayOfWeek().getValue() - 1;
            salesByDay[w] += o.total().doubleValue();
            ordersByDay[w]++;
            ordersByHour[o.at().getHour()]++;
        }
        for (EventRow e : views) {
            viewsByDay[e.at().getDayOfWeek().getValue() - 1]++;
            viewsByHour[e.at().getHour()]++;
        }
        double avgOrdersPerDay = orders.size() / (double) WINDOW_DAYS;
        List<Map<String, Object>> weekdays = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            double perDay = occurrences[i] == 0 ? 0 : ordersByDay[i] / (double) occurrences[i];
            weekdays.add(SellerAIService.map("name", WEEKDAYS[i], "orders", ordersByDay[i],
                    "avgSales", Math.round(occurrences[i] == 0 ? 0 : salesByDay[i] / occurrences[i]),
                    "views", viewsByDay[i], "index", avgOrdersPerDay == 0 ? 0 : Math.round(perDay / avgOrdersPerDay * 100) / 100.0));
        }
        List<Integer> dayRank = rank(i -> (double) weekdays.get(i).get("index") * 10 + viewsByDay[i] / (double) Math.max(1, views.size()), 7);
        List<String> bestWeekdays = dayRank.subList(0, 2).stream().map(i -> WEEKDAYS[i]).toList();

        // ---- hours: flash-sale slots and the best 3-hour window ----
        int bestSlot = 0;
        double bestScore = -1;
        double maxOph = 0, maxVph = 0;
        for (int[] s : SLOTS) {
            maxOph = Math.max(maxOph, sum(ordersByHour, s[0], s[1]) / (double) (s[1] - s[0]));
            maxVph = Math.max(maxVph, sum(viewsByHour, s[0], s[1]) / (double) (s[1] - s[0]));
        }
        List<Map<String, Object>> slots = new ArrayList<>();
        for (int k = 0; k < SLOTS.length; k++) {
            int[] s = SLOTS[k];
            double oph = sum(ordersByHour, s[0], s[1]) / (double) (s[1] - s[0]), vph = sum(viewsByHour, s[0], s[1]) / (double) (s[1] - s[0]);
            double score = (maxOph == 0 ? 0 : oph / maxOph) + 0.5 * (maxVph == 0 ? 0 : vph / maxVph);
            if (score > bestScore) { bestScore = score; bestSlot = k; }
            slots.add(SellerAIService.map("label", String.format("%02d:00 – %02d:00", s[0], s[1] % 24 == 0 ? 24 : s[1]), "start", s[0], "end", s[1],
                    "orders", sum(ordersByHour, s[0], s[1]), "views", sum(viewsByHour, s[0], s[1]),
                    "ordersPerHour", Math.round(oph * 100) / 100.0));
        }
        slots.get(bestSlot).put("best", true);
        int windowStart = 0, windowCount = -1;
        for (int h = 0; h < 24; h++) {
            int c = ordersByHour[h] + ordersByHour[(h + 1) % 24] + ordersByHour[(h + 2) % 24];
            if (c > windowCount) { windowCount = c; windowStart = h; }
        }
        String bestWindow = String.format("%02d:00 – %02d:00", windowStart, (windowStart + 3) % 24);

        // ---- part of the month (salary days) ----
        String[] partNames = {"Đầu tháng (1–5)", "Giữa tháng (13–17)", "Cuối tháng – ngày lương (25–31)", "Các ngày khác"};
        int[] partDays = new int[4], partOrders = new int[4];
        for (LocalDate d = window.from(); !d.isAfter(today); d = d.plusDays(1)) partDays[part(d.getDayOfMonth())]++;
        for (OrderRow o : orders) partOrders[part(o.at().getDayOfMonth())]++;
        List<Map<String, Object>> monthParts = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            double perDay = partDays[i] == 0 ? 0 : partOrders[i] / (double) partDays[i];
            monthParts.add(SellerAIService.map("label", partNames[i], "orders", partOrders[i],
                    "avgOrdersPerDay", Math.round(perDay * 100) / 100.0, "index", avgOrdersPerDay == 0 ? 0 : Math.round(perDay / avgOrdersPerDay * 100) / 100.0));
        }
        int bestPart = rank(i -> (double) monthParts.get(i).get("index"), 3).get(0);

        // ---- trend: last 4 weeks vs the 4 before (always this shop) ----
        List<OrderRow> mine = analytics.orders(shop.getId(), new Period(today.minusDays(55), today, Granularity.DAY)).stream().filter(OrderRow::sale).toList();
        LocalDate split = today.minusDays(27);
        double recent = mine.stream().filter(o -> !o.at().toLocalDate().isBefore(split)).mapToDouble(o -> o.total().doubleValue()).sum();
        double before = mine.stream().filter(o -> o.at().toLocalDate().isBefore(split)).mapToDouble(o -> o.total().doubleValue()).sum();
        Double change = before == 0 ? null : Math.round((recent - before) * 1000 / before) / 10.0;

        // ---- upcoming shopping events ----
        List<Map<String, Object>> calendar = events(today, 60);

        // ---- products: what to discount and what not ----
        Map<String, Object> products = productAdvice(shop.getId(), today);

        // ---- summary ----
        StringBuilder sb = new StringBuilder();
        sb.append(scope.equals("shop") ? "Dựa trên " + orders.size() + " đơn hàng của shop trong " + WINDOW_DAYS + " ngày qua: "
                : "Shop chưa đủ dữ liệu (dưới " + MIN_ORDERS + " đơn trong " + WINDOW_DAYS + " ngày) nên tham khảo xu hướng toàn sàn: ");
        sb.append("khách mua nhiều nhất vào ").append(bestWeekdays.get(0)).append(" và ").append(bestWeekdays.get(1))
                .append(", khung giờ ").append(slots.get(bestSlot).get("label")).append(" (cao điểm ").append(bestWindow).append(")");
        if ((double) monthParts.get(bestPart).get("index") >= 1.15) {
            sb.append(", đặc biệt ").append(Character.toLowerCase(partNames[bestPart].charAt(0))).append(partNames[bestPart].substring(1));
        }
        sb.append(". ");
        if (change != null) {
            sb.append("Doanh thu 4 tuần gần đây ").append(change >= 0 ? "tăng " : "giảm ").append(Math.abs(change)).append("% so với 4 tuần trước")
                    .append(change <= -15 ? " – nên chạy khuyến mãi sớm để kéo lại doanh số. " : change >= 15 ? " – đang tốt, chỉ cần giảm giá nhẹ, ưu tiên quảng cáo. " : ". ");
        }
        Optional<Map<String, Object>> major = calendar.stream().filter(e -> !"LOW".equals(e.get("importance"))).findFirst();
        if (major.isPresent()) {
            Map<String, Object> e = major.get();
            sb.append("Sự kiện lớn sắp tới: ").append(e.get("name")).append(" (").append(((LocalDate) e.get("date")).format(D)).append(", còn ")
                    .append(e.get("daysLeft")).append(" ngày) – nên chuẩn bị voucher và flash sale từ ").append(((LocalDate) e.get("prepFrom")).format(D)).append(".");
        }
        String rule = sb.toString().trim();
        String ai = aiService.completeOrNull(
                "Bạn là chuyên gia marketing cho người bán trên sàn thương mại điện tử Việt Nam. Từ các số liệu, viết 4-6 câu tiếng Việt "
                        + "khuyên shop nên chạy khuyến mãi khi nào, khung giờ nào và cho sản phẩm nào. Giữ nguyên các con số, không bịa thêm.",
                rule + "\nSản phẩm nên xả hàng: " + names(products.get("clearance")) + "\nSản phẩm đang tăng: " + names(products.get("rising"))
                        + "\nSắp hết hàng (không nên giảm giá): " + names(products.get("lowStock")));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scope", scope);
        out.put("ordersAnalyzed", orders.size());
        out.put("from", window.from());
        out.put("to", today);
        out.put("summary", ai != null ? ai : rule);
        out.put("source", ai != null ? "ai" : "rule");
        out.put("bestWeekdays", bestWeekdays);
        out.put("weekdays", weekdays);
        out.put("ordersByHour", Arrays.stream(ordersByHour).boxed().toList());
        out.put("viewsByHour", Arrays.stream(viewsByHour).boxed().toList());
        out.put("slots", slots);
        out.put("bestWindow", bestWindow);
        out.put("monthParts", monthParts);
        out.put("trend", SellerAIService.map("recent", Math.round(recent), "previous", Math.round(before), "change", change));
        out.put("events", calendar);
        out.put("products", products);
        return out;
    }

    // ==================== shopping calendar ====================

    /** Vietnamese e-commerce shopping days in the next `days` days, soonest first. */
    static List<Map<String, Object>> events(LocalDate today, int days) {
        LocalDate end = today.plusDays(days);
        Map<LocalDate, Object[]> found = new TreeMap<>();
        for (LocalDate d = today.withDayOfMonth(1); !d.isAfter(end); d = d.plusMonths(1)) {
            int m = d.getMonthValue();
            add(found, today, end, d.withDayOfMonth(Math.min(m, d.lengthOfMonth())), "Siêu sale " + m + "." + m, m >= 9 ? "HIGH" : "MEDIUM");
            add(found, today, end, d.withDayOfMonth(15), "Sale giữa tháng", "LOW");
            add(found, today, end, d.withDayOfMonth(25), "Sale ngày lương", "LOW");
            int y = d.getYear();
            Object[][] fixed = {
                    {MonthDay.of(2, 14), "Lễ Tình nhân (Valentine)", "MEDIUM"}, {MonthDay.of(3, 8), "Quốc tế Phụ nữ 8/3", "MEDIUM"},
                    {MonthDay.of(4, 30), "Lễ 30/4 – 1/5", "MEDIUM"}, {MonthDay.of(6, 1), "Quốc tế Thiếu nhi", "MEDIUM"},
                    {MonthDay.of(8, 15), "Mùa tựu trường", "MEDIUM"}, {MonthDay.of(10, 20), "Phụ nữ Việt Nam 20/10", "MEDIUM"},
                    {MonthDay.of(11, 20), "Nhà giáo Việt Nam 20/11", "MEDIUM"}, {MonthDay.of(12, 24), "Giáng sinh", "MEDIUM"},
                    {MonthDay.of(12, 31), "Tất niên – đón năm mới", "MEDIUM"}};
            for (Object[] f : fixed) {
                MonthDay md = (MonthDay) f[0];
                if (md.getMonthValue() == m) add(found, today, end, md.atYear(y), (String) f[1], (String) f[2]);
            }
            if (m == 11) add(found, today, end, d.with(TemporalAdjusters.lastInMonth(DayOfWeek.FRIDAY)), "Black Friday", "HIGH");
        }
        for (LocalDate tet : TET.values()) {
            add(found, today, end, tet.minusDays(21), "Mua sắm Tết Nguyên Đán", "HIGH");
            add(found, today, end, tet, "Tết Nguyên Đán (shop nên báo lịch nghỉ)", "MEDIUM");
        }
        List<Map<String, Object>> out = new ArrayList<>();
        int monthly = 0;
        for (Map.Entry<LocalDate, Object[]> e : found.entrySet()) {
            String importance = (String) e.getValue()[1];
            // the monthly mid-month / payday sales: only the next two, so big events are not pushed out of the list
            if (importance.equals("LOW") && ++monthly > 2) continue;
            int lead = importance.equals("HIGH") ? 7 : importance.equals("MEDIUM") ? 5 : 2;
            LocalDate prep = e.getKey().minusDays(lead).isBefore(today) ? today : e.getKey().minusDays(lead);
            String tips = switch (importance) {
                case "HIGH" -> "Đăng ký flash sale khung 00:00–02:00 và 12:00–14:00, phát voucher giảm 10–15% trước 3 ngày, nhập đủ hàng cho sản phẩm bán chạy";
                case "MEDIUM" -> "Tạo voucher/combo theo chủ đề sự kiện, đẩy quảng cáo từ khóa liên quan";
                default -> "Voucher nhỏ 5–10% hoặc miễn phí vận chuyển để kích cầu";
            };
            out.add(SellerAIService.map("date", e.getKey(), "name", e.getValue()[0], "importance", importance,
                    "daysLeft", ChronoUnit.DAYS.between(today, e.getKey()), "prepFrom", prep, "tips", tips));
        }
        return out.stream().limit(8).toList();
    }

    private static void add(Map<LocalDate, Object[]> found, LocalDate today, LocalDate end, LocalDate date, String name, String importance) {
        if (date.isBefore(today) || date.isAfter(end)) return;
        Object[] old = found.get(date);
        // keep the more important one when two events fall on the same day (11.11 vs "sale giua thang")
        if (old == null || weight(importance) > weight((String) old[1])) found.put(date, new Object[]{name, importance});
    }

    private static int weight(String importance) {
        return importance.equals("HIGH") ? 3 : importance.equals("MEDIUM") ? 2 : 1;
    }

    // ==================== products ====================

    @SuppressWarnings("unchecked")
    private Map<String, Object> productAdvice(Long shopId, LocalDate today) {
        List<Map<String, Object>> list = ((List<Map<String, Object>>) analytics.products(shopId,
                new Period(today.minusDays(29), today, Granularity.DAY)).get("products")).stream()
                .filter(p -> Boolean.TRUE.equals(p.get("active"))).toList();
        // units in the last 14 days vs the 14 before
        Map<Long, long[]> momentum = new HashMap<>();
        for (ItemRow i : analytics.items(shopId, new Period(today.minusDays(27), today, Granularity.DAY))) {
            if (!i.sale() || i.productId() == null) continue;
            long[] m = momentum.computeIfAbsent(i.productId(), k -> new long[2]);
            m[i.at().toLocalDate().isBefore(today.minusDays(13)) ? 0 : 1] += i.quantity();
        }
        List<Map<String, Object>> clearance = list.stream()
                .filter(p -> num(p.get("stock")) >= 10 && num(p.get("units")) == 0)
                .sorted(Comparator.comparing((Map<String, Object> p) -> num(p.get("stock")) * num(p.get("price"))).reversed()).limit(5)
                .map(p -> pick(p, "Tồn " + (long) num(p.get("stock")) + ", 30 ngày chưa bán được – giảm 15–25% hoặc đưa vào flash sale để xả hàng")).toList();
        List<Map<String, Object>> rising = list.stream().filter(p -> {
                    long[] m = momentum.get(((Number) p.get("id")).longValue());
                    return m != null && m[1] >= 3 && m[1] >= m[0] * 1.5;
                }).limit(5)
                .map(p -> {
                    long[] m = momentum.get(((Number) p.get("id")).longValue());
                    return pick(p, "Bán " + m[1] + " trong 14 ngày gần đây (trước đó " + m[0] + ") – đang tăng, ưu tiên quảng cáo thay vì giảm giá sâu");
                }).toList();
        List<Map<String, Object>> interest = list.stream()
                .filter(p -> num(p.get("visitors")) >= 10 && num(p.get("conversion")) < 2 && num(p.get("stock")) > 0)
                .sorted(Comparator.comparing((Map<String, Object> p) -> num(p.get("visitors"))).reversed()).limit(5)
                .map(p -> pick(p, (long) num(p.get("visitors")) + " lượt truy cập nhưng chuyển đổi " + num(p.get("conversion"))
                        + "% – tạo voucher riêng cho sản phẩm hoặc combo để chốt đơn")).toList();
        List<Map<String, Object>> lowStock = list.stream()
                .filter(p -> num(p.get("stock")) > 0 && p.get("daysOfStock") != null && num(p.get("daysOfStock")) < 10)
                .limit(5).map(p -> pick(p, "Chỉ còn " + (long) num(p.get("stock")) + " (đủ bán ~" + p.get("daysOfStock")
                        + " ngày) – không nên khuyến mãi, hãy nhập thêm hàng")).toList();
        return SellerAIService.map("clearance", clearance, "rising", rising, "interest", interest, "lowStock", lowStock);
    }

    private static Map<String, Object> pick(Map<String, Object> p, String reason) {
        return SellerAIService.map("id", p.get("id"), "name", p.get("name"), "thumbnailUrl", p.get("thumbnailUrl"),
                "price", p.get("price"), "stock", p.get("stock"), "reason", reason);
    }

    @SuppressWarnings("unchecked")
    private static String names(Object list) {
        List<Map<String, Object>> l = (List<Map<String, Object>>) list;
        return l.isEmpty() ? "không có" : l.stream().map(p -> (String) p.get("name")).collect(Collectors.joining(", "));
    }

    // ==================== helpers ====================

    private static int part(int dayOfMonth) {
        return dayOfMonth <= 5 ? 0 : dayOfMonth >= 13 && dayOfMonth <= 17 ? 1 : dayOfMonth >= 25 ? 2 : 3;
    }

    private static int sum(int[] a, int from, int to) {
        int s = 0;
        for (int i = from; i < to; i++) s += a[i];
        return s;
    }

    private static List<Integer> rank(java.util.function.IntToDoubleFunction score, int n) {
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < n; i++) idx.add(i);
        idx.sort(Comparator.comparingDouble((Integer i) -> score.applyAsDouble(i)).reversed());
        return idx;
    }

    private static double num(Object o) {
        return SalesAssistantService.num(o);
    }
}
