package com.marketplace.backend.service;

import com.marketplace.backend.entity.OrderStatus;
import com.marketplace.backend.entity.Product;
import com.marketplace.backend.entity.Shop;
import com.marketplace.backend.entity.User;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.ProductRepository;
import com.marketplace.backend.repository.UserRepository;
import com.marketplace.backend.service.SellerAnalyticsService.Granularity;
import com.marketplace.backend.service.SellerAnalyticsService.ItemRow;
import com.marketplace.backend.service.SellerAnalyticsService.OrderRow;
import com.marketplace.backend.service.SellerAnalyticsService.Period;
import com.marketplace.backend.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * "Tro ly phan tich doanh thu": the seller asks in Vietnamese ("doanh thu thang nay so voi thang truoc?",
 * "san pham nao ban chay nhat tuan nay?") and gets an answer computed from the shop's own data.
 * The question is parsed with rules (a time range, an optional comparison range, one metric, an optional
 * product); the numbers always come from the database. An LLM, when configured, only rephrases the answer.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SalesAssistantService {

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DM = DateTimeFormatter.ofPattern("dd/MM");
    private static final String[] WEEKDAYS = {"Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ Nhật"};
    private static final Set<OrderStatus> NOT_A_SALE = SellerAnalyticsService.NOT_A_SALE;

    static final List<String> EXAMPLES = List.of(
            "Doanh thu tháng này so với tháng trước?",
            "Sản phẩm nào bán chạy nhất 7 ngày qua?",
            "Ngày nào trong tuần bán được nhiều nhất?",
            "Tỷ lệ hủy đơn tháng trước là bao nhiêu?",
            "Có bao nhiêu khách mua lại trong 30 ngày qua?",
            "Sản phẩm nào sắp hết hàng?",
            "Tỷ lệ chuyển đổi tuần này thế nào?",
            "Xu hướng doanh thu 3 tháng qua?");

    enum Intent { STOCK, CANCEL, REVIEW, TRAFFIC, BEST_TIME, SLOW, TOP_PRODUCTS, CUSTOMERS, AOV, ORDERS, TREND, REVENUE, HELP }

    record Range(LocalDate from, LocalDate to, String label) {
        Period period(Granularity g) {
            return new Period(from, to, g);
        }

        String text() {
            return label + (from.equals(to) ? " (" + from.format(D) + ")" : " (" + from.format(DM) + " – " + to.format(D) + ")");
        }
    }

    private final SellerAnalyticsService analytics;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final AIService aiService;

    public Map<String, Object> ask(String question) {
        if (question == null || question.isBlank()) throw new ApiException("Vui lòng nhập câu hỏi", HttpStatus.BAD_REQUEST);
        if (question.length() > 300) throw new ApiException("Câu hỏi tối đa 300 ký tự", HttpStatus.BAD_REQUEST);
        Shop shop = analytics.myShop();
        LocalDate today = LocalDate.now();
        String n = TextNormalizer.normalize(question).replaceAll("[?!.,]+", " ").replaceAll("\\s+", " ").trim();

        // "A so voi B": B is an explicit comparison range
        String[] parts = n.split(" (?:so voi|so sanh voi|voi cung ky) ", 2);
        Range main = parseRange(parts[0], today);
        Range explicitMain = main;
        if (main == null) main = new Range(today.minusDays(29), today, "30 ngày qua");
        Range compare = parts.length > 1 ? parseRange(parts[1], today) : null;
        if (compare == null) {
            long days = ChronoUnit.DAYS.between(main.from(), main.to()) + 1;
            compare = new Range(main.from().minusDays(days), main.from().minusDays(1), "kỳ trước");
        }
        Intent intent = intent(n);
        if (intent == Intent.HELP && explicitMain != null) intent = Intent.REVENUE;
        Product product = matchProduct(n, shop.getId());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("question", question.trim());
        out.put("intent", intent.name());
        out.put("period", SellerAIService.map("from", main.from(), "to", main.to(), "label", main.label()));
        if (product != null) out.put("product", SellerAIService.map("id", product.getId(), "name", product.getName()));
        String answer = switch (intent) {
            case REVENUE, ORDERS, AOV -> product != null ? productSales(shop.getId(), product, main, compare, out)
                    : revenue(shop.getId(), main, compare, intent, out);
            case CANCEL -> cancel(shop.getId(), main, compare, out);
            case TOP_PRODUCTS -> topProducts(shop.getId(), main, n.contains("doanh thu") || n.contains("doanh so"), out);
            case SLOW -> slow(shop.getId(), main, out);
            case STOCK -> stock(shop.getId(), out);
            case BEST_TIME -> bestTime(shop.getId(), main, out);
            case CUSTOMERS -> customers(shop.getId(), main, compare, out);
            case TRAFFIC -> traffic(shop.getId(), main, compare, out);
            case REVIEW -> reviews(shop.getId(), main, compare, out);
            case TREND -> trend(shop.getId(), main, out);
            case HELP -> "Mình là trợ lý phân tích bán hàng của shop. Bạn có thể hỏi về doanh thu, đơn hàng, sản phẩm bán chạy/bán chậm, "
                    + "tồn kho, khách hàng, tỷ lệ hủy, lượt xem & chuyển đổi, đánh giá… kèm thời gian như \"hôm nay\", \"tuần này\", "
                    + "\"tháng 9\", \"30 ngày qua\". Ví dụ: \"Doanh thu tháng này so với tháng trước?\"";
        };
        if (intent != Intent.HELP) {
            String ai = aiService.completeOrNull(
                    "Bạn là trợ lý phân tích bán hàng cho chủ shop trên sàn thương mại điện tử. Viết lại câu trả lời dưới đây bằng tiếng Việt, "
                            + "tự nhiên, tối đa 5 câu, giữ nguyên chính xác mọi con số, có thể thêm 1 gợi ý hành động. Không thêm số liệu mới.",
                    "Câu hỏi: " + question + "\nCâu trả lời dựa trên dữ liệu: " + answer + tableText(out));
            out.put("answer", ai != null ? ai : answer);
            out.put("source", ai != null ? "ai" : "rule");
        } else {
            out.put("answer", answer);
            out.put("source", "rule");
        }
        out.put("suggestions", followUps(intent));
        return out;
    }

    public List<String> examples() {
        return EXAMPLES;
    }

    // ==================== question parsing ====================

    private static final Pattern LAST_N = Pattern.compile("(\\d{1,3}) (ngay|tuan|thang) (?:qua|gan day|vua qua|gan nhat|truoc|vua roi)");
    private static final Pattern QUARTER = Pattern.compile("quy ([1-4])(?: nam (\\d{4}))?");
    private static final Pattern MONTH = Pattern.compile("thang (\\d{1,2})(?:(?: nam |/)(\\d{4}))?");
    private static final Pattern BETWEEN = Pattern.compile("(\\d{1,2})/(\\d{1,2})(?:/(\\d{4}))? ?(?:den|toi|-) ?(\\d{1,2})/(\\d{1,2})(?:/(\\d{4}))?");
    private static final Pattern DAY = Pattern.compile("ngay (\\d{1,2})/(\\d{1,2})(?:/(\\d{4}))?");

    /** null when the text names no time range. */
    static Range parseRange(String n, LocalDate today) {
        Matcher m;
        if ((m = BETWEEN.matcher(n)).find()) {
            LocalDate a = date(m.group(1), m.group(2), m.group(3), today), b = date(m.group(4), m.group(5), m.group(6), today);
            if (a != null && b != null && !a.isAfter(b)) return clip(new Range(a, b, "từ " + a.format(DM) + " đến " + b.format(DM)), today);
        }
        if ((m = DAY.matcher(n)).find()) {
            LocalDate a = date(m.group(1), m.group(2), m.group(3), today);
            if (a != null) return new Range(a, a, "ngày " + a.format(DM));
        }
        if (n.contains("hom nay")) return new Range(today, today, "hôm nay");
        if (n.contains("hom qua")) return new Range(today.minusDays(1), today.minusDays(1), "hôm qua");
        if (n.contains("hom kia")) return new Range(today.minusDays(2), today.minusDays(2), "hôm kia");
        if ((m = LAST_N.matcher(n)).find()) {
            int k = Math.max(1, Integer.parseInt(m.group(1)));
            LocalDate from = switch (m.group(2)) {
                case "ngay" -> today.minusDays(k - 1L);
                case "tuan" -> today.minusWeeks(k).plusDays(1);
                default -> today.minusMonths(k).plusDays(1);
            };
            String unit = switch (m.group(2)) { case "ngay" -> "ngày"; case "tuan" -> "tuần"; default -> "tháng"; };
            return clip(new Range(from, today, k + " " + unit + " qua"), today);
        }
        if (n.contains("tuan nay")) return new Range(today.with(DayOfWeek.MONDAY), today, "tuần này");
        if (n.contains("tuan truoc") || n.contains("tuan roi")) {
            LocalDate mon = today.with(DayOfWeek.MONDAY).minusWeeks(1);
            return new Range(mon, mon.plusDays(6), "tuần trước");
        }
        if ((m = QUARTER.matcher(n)).find()) {
            int q = Integer.parseInt(m.group(1));
            int year = m.group(2) != null ? Integer.parseInt(m.group(2)) : today.getYear();
            LocalDate from = LocalDate.of(year, (q - 1) * 3 + 1, 1);
            if (m.group(2) == null && from.isAfter(today)) from = from.minusYears(1);
            return clip(new Range(from, from.plusMonths(3).minusDays(1), "quý " + q + "/" + from.getYear()), today);
        }
        if (n.contains("thang nay")) return new Range(today.withDayOfMonth(1), today, "tháng này");
        if (n.contains("thang truoc") || n.contains("thang roi")) {
            LocalDate first = today.withDayOfMonth(1).minusMonths(1);
            return new Range(first, first.with(TemporalAdjusters.lastDayOfMonth()), "tháng trước");
        }
        if ((m = MONTH.matcher(n)).find()) {
            int month = Integer.parseInt(m.group(1));
            if (month >= 1 && month <= 12) {
                int year = m.group(2) != null ? Integer.parseInt(m.group(2)) : today.getYear();
                LocalDate first = LocalDate.of(year, month, 1);
                if (m.group(2) == null && first.isAfter(today)) first = first.minusYears(1);
                return clip(new Range(first, first.with(TemporalAdjusters.lastDayOfMonth()), "tháng " + month + "/" + first.getYear()), today);
            }
        }
        if (n.contains("nam nay")) return clip(new Range(today.withDayOfYear(1), today, "năm nay"), today);
        if (n.contains("nam ngoai") || n.contains("nam truoc") || n.contains("nam roi")) {
            LocalDate first = today.withDayOfYear(1).minusYears(1);
            return new Range(first, first.plusYears(1).minusDays(1), "năm " + first.getYear());
        }
        return null;
    }

    // never in the future, at most 366 days (the analytics limit)
    private static Range clip(Range r, LocalDate today) {
        LocalDate to = r.to().isAfter(today) ? today : r.to();
        LocalDate from = r.from().isBefore(to.minusDays(365)) ? to.minusDays(365) : r.from();
        return new Range(from.isAfter(to) ? to : from, to, r.label());
    }

    private static LocalDate date(String d, String m, String y, LocalDate today) {
        try {
            LocalDate v = LocalDate.of(y != null ? Integer.parseInt(y) : today.getYear(), Integer.parseInt(m), Integer.parseInt(d));
            return y == null && v.isAfter(today) ? v.minusYears(1) : v;
        } catch (RuntimeException e) {
            return null;
        }
    }

    static Intent intent(String n) {
        String s = " " + n + " ";
        if (has(s, "ton kho", "het hang", "sap het", "con bao nhieu hang", "nhap hang", "con hang")) return Intent.STOCK;
        if (has(s, "huy", "hoan tien", "tra hang", "bom hang", "ty le huy")) return Intent.CANCEL;
        if (has(s, "danh gia", "review", "so sao", "khach che", "khach khen", "phan nan")) return Intent.REVIEW;
        if (has(s, "luot xem", "truy cap", "chuyen doi", "view", "luot click")) return Intent.TRAFFIC;
        if (has(s, "ngay nao", "thu may", "thu nao", "gio nao", "khung gio", "cao diem", "luc nao", "thoi diem")) return Intent.BEST_TIME;
        if (has(s, "ban cham", "ban e", "e am", "khong ban duoc", "it ban", "khong co don", "chua ban duoc")) return Intent.SLOW;
        if (has(s, "ban chay", "san pham nao", "mat hang nao", "top", "ban nhieu nhat", "hot nhat")) return Intent.TOP_PRODUCTS;
        if (has(s, "khach hang", "nguoi mua", "khach moi", "khach quay lai", "mua lai", "khach nao", "khach quen")) return Intent.CUSTOMERS;
        if (has(s, "trung binh", "gia tri don", "aov")) return Intent.AOV;
        if (has(s, "xu huong", "tang hay giam", "tang truong", "dang tang", "dang giam")) return Intent.TREND;
        if (has(s, "bao nhieu don", "so don", "don hang", "may don")) return Intent.ORDERS;
        if (has(s, "doanh thu", "doanh so", "ban duoc", "thu ve", "kiem duoc", "tien", "loi nhuan", "ket qua kinh doanh", "ban hang"))
            return Intent.REVENUE;
        return Intent.HELP;
    }

    private static boolean has(String s, String... keys) {
        for (String k : keys) if (s.contains(" " + k + " ")) return true;
        return false;
    }

    // a product of the shop named in the question ("ao so mi linen ban duoc bao nhieu")
    private Product matchProduct(String n, Long shopId) {
        Set<String> q = SellerAIService.tokens(n);
        Product best = null;
        double bestScore = 0;
        for (Product p : productRepository.findByShopId(shopId)) {
            String name = TextNormalizer.normalize(p.getName());
            if (n.contains(name)) return p;
            Set<String> t = SellerAIService.tokens(p.getName());
            long common = t.stream().filter(q::contains).count();
            double score = t.isEmpty() ? 0 : (double) common / t.size();
            if (common >= 2 && score >= 0.6 && score > bestScore) {
                best = p;
                bestScore = score;
            }
        }
        return best;
    }

    private static Granularity granularityFor(Range r) {
        long days = ChronoUnit.DAYS.between(r.from(), r.to()) + 1;
        return days <= 31 ? Granularity.DAY : days <= 120 ? Granularity.WEEK : Granularity.MONTH;
    }

    // ==================== answers ====================

    @SuppressWarnings("unchecked")
    private String revenue(Long shopId, Range main, Range compare, Intent intent, Map<String, Object> out) {
        Map<String, Object> rv = analytics.revenue(shopId, main.period(granularityFor(main)));
        Map<String, Object> s = (Map<String, Object>) rv.get("summary");
        Map<String, Object> p = (Map<String, Object>) analytics.revenue(shopId, compare.period(Granularity.MONTH)).get("summary");
        List<Map<String, Object>> series = (List<Map<String, Object>>) rv.get("series");
        out.put("compare", SellerAIService.map("from", compare.from(), "to", compare.to(), "label", compare.label()));
        out.put("chart", chart(series, "label", intent == Intent.ORDERS ? "orders" : "sales", intent == Intent.ORDERS ? "Đơn hàng" : "Doanh số",
                intent != Intent.ORDERS));
        String sb;
        if (intent == Intent.ORDERS) {
            sb = cap(main.text()) + " shop có " + s.get("orders") + " đơn hàng (" + s.get("items") + " sản phẩm, " + s.get("buyers")
                    + " người mua), " + change(num(s.get("orders")), num(p.get("orders"))) + " so với " + compare.text() + " (" + p.get("orders") + " đơn).";
            List<Map<String, Object>> st = (List<Map<String, Object>>) rv.get("statuses");
            out.put("table", table(List.of("Trạng thái", "Số đơn", "Giá trị"), st.stream()
                    .map(x -> List.<Object>of(statusName((String) x.get("status")), x.get("orders"), money(x.get("value")))).toList()));
        } else if (intent == Intent.AOV) {
            sb = "Giá trị trung bình mỗi đơn " + main.text() + " là " + money(s.get("avgOrderValue")) + " (" + s.get("orders") + " đơn), "
                    + change(num(s.get("avgOrderValue")), num(p.get("avgOrderValue"))) + " so với " + compare.text() + " ("
                    + money(p.get("avgOrderValue")) + ").";
            if (num(s.get("avgOrderValue")) > 0) sb += " Mẹo: dùng combo \"mua 2 giảm thêm\" hoặc voucher có điều kiện đơn tối thiểu để tăng giá trị đơn.";
        } else {
            sb = "Doanh thu " + main.text() + " đạt " + money(s.get("sales")) + " từ " + s.get("orders") + " đơn (" + s.get("items")
                    + " sản phẩm), " + change(num(s.get("sales")), num(p.get("sales"))) + " so với " + compare.text() + " (" + money(p.get("sales")) + ").";
            Optional<Map<String, Object>> peak = series.stream().filter(b -> num(b.get("sales")) > 0)
                    .max(Comparator.comparing(b -> num(b.get("sales"))));
            if (peak.isPresent() && series.size() > 1) {
                sb += " " + (granularityFor(main) == Granularity.DAY ? "Ngày" : "Giai đoạn") + " cao nhất: " + peak.get().get("label")
                        + " với " + money(peak.get().get("sales")) + ".";
            }
            if (num(s.get("cancelledOrders")) > 0) sb += " Có " + s.get("cancelledOrders") + " đơn hủy/hoàn (" + money(s.get("cancelledValue")) + ") không được tính.";
        }
        return sb;
    }

    private String productSales(Long shopId, Product product, Range main, Range compare, Map<String, Object> out) {
        List<ItemRow> cur = analytics.items(shopId, main.period(Granularity.DAY)).stream()
                .filter(i -> product.getId().equals(i.productId()) && i.sale()).toList();
        List<ItemRow> prev = analytics.items(shopId, compare.period(Granularity.DAY)).stream()
                .filter(i -> product.getId().equals(i.productId()) && i.sale()).toList();
        long units = cur.stream().mapToLong(ItemRow::quantity).sum(), unitsPrev = prev.stream().mapToLong(ItemRow::quantity).sum();
        BigDecimal value = cur.stream().map(ItemRow::value).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal valuePrev = prev.stream().map(ItemRow::value).reduce(BigDecimal.ZERO, BigDecimal::add);
        Granularity g = granularityFor(main);
        Map<LocalDate, List<ItemRow>> byBucket = cur.stream().collect(Collectors.groupingBy(i -> SellerAnalyticsService.bucketOf(i.at().toLocalDate(), g)));
        Period p = main.period(g);
        List<Map<String, Object>> series = SellerAnalyticsService.buckets(p).stream().map(b -> SellerAIService.map(
                "label", SellerAnalyticsService.label(b, p),
                "sales", byBucket.getOrDefault(b, List.of()).stream().map(ItemRow::value).reduce(BigDecimal.ZERO, BigDecimal::add))).toList();
        out.put("compare", SellerAIService.map("from", compare.from(), "to", compare.to(), "label", compare.label()));
        out.put("chart", chart(series, "label", "sales", "Doanh số " + product.getName(), true));
        return "\"" + product.getName() + "\" " + main.text() + " bán được " + units + " sản phẩm cho " + cur.stream().map(ItemRow::buyerId).distinct().count()
                + " người mua, doanh số " + money(value) + " – " + change(value.doubleValue(), valuePrev.doubleValue()) + " so với " + compare.text()
                + " (" + unitsPrev + " sản phẩm, " + money(valuePrev) + "). Tồn kho hiện tại: " + product.getStock() + ".";
    }

    @SuppressWarnings("unchecked")
    private String cancel(Long shopId, Range main, Range compare, Map<String, Object> out) {
        Map<String, Object> rv = analytics.revenue(shopId, main.period(Granularity.MONTH));
        Map<String, Object> s = (Map<String, Object>) rv.get("summary");
        Map<String, Object> p = (Map<String, Object>) analytics.revenue(shopId, compare.period(Granularity.MONTH)).get("summary");
        List<Map<String, Object>> st = (List<Map<String, Object>>) rv.get("statuses");
        out.put("table", table(List.of("Trạng thái", "Số đơn", "Giá trị"), st.stream()
                .map(x -> List.<Object>of(statusName((String) x.get("status")), x.get("orders"), money(x.get("value")))).toList()));
        double rate = num(s.get("cancelRate"));
        String answer = "Tỷ lệ hủy/hoàn tiền " + main.text() + " là " + pct(rate) + " (" + s.get("cancelledOrders") + " đơn, "
                + money(s.get("cancelledValue")) + "), " + points(rate, num(p.get("cancelRate"))) + " so với " + compare.text() + ".";
        if (rate >= 10) answer += " Tỷ lệ này khá cao – hãy xác nhận đơn sớm, cập nhật tồn kho chính xác và gọi xác nhận các đơn COD giá trị lớn.";
        else if (num(s.get("cancelledOrders")) == 0) answer += " Không có đơn nào bị hủy – rất tốt!";
        return answer;
    }

    @SuppressWarnings("unchecked")
    private String topProducts(Long shopId, Range main, boolean bySales, Map<String, Object> out) {
        List<Map<String, Object>> list = new ArrayList<>((List<Map<String, Object>>) analytics.products(shopId, main.period(Granularity.DAY)).get("products"));
        list.removeIf(p -> num(p.get("units")) == 0);
        list.sort(Comparator.comparing((Map<String, Object> p) -> num(bySales ? p.get("sales") : p.get("units"))).reversed());
        if (list.isEmpty()) return "Chưa có sản phẩm nào được bán " + main.text() + ".";
        List<Map<String, Object>> top = list.subList(0, Math.min(5, list.size()));
        out.put("table", table(List.of("#", "Sản phẩm", "Đã bán", "Doanh số", "Chuyển đổi"), top.stream()
                .map(p -> List.<Object>of(top.indexOf(p) + 1, p.get("name"), p.get("units"), money(p.get("sales")), pct(num(p.get("conversion"))))).toList()));
        out.put("chart", SellerAIService.map("type", "bar", "labels", top.stream().map(p -> cut((String) p.get("name"), 18)).toList(),
                "series", List.of(SellerAIService.map("name", bySales ? "Doanh số" : "Đã bán", "money", bySales,
                        "values", top.stream().map(p -> num(bySales ? p.get("sales") : p.get("units"))).toList()))));
        Map<String, Object> first = top.get(0);
        return "Sản phẩm bán chạy nhất " + main.text() + " là \"" + first.get("name") + "\" với " + first.get("units") + " sản phẩm ("
                + money(first.get("sales")) + ")" + (top.size() > 1 ? ", tiếp theo là \"" + top.get(1).get("name") + "\" (" + top.get(1).get("units") + " sản phẩm)" : "")
                + ". Top " + top.size() + " sản phẩm chiếm " + pct(share(top, list)) + " doanh số của shop.";
    }

    private static double share(List<Map<String, Object>> top, List<Map<String, Object>> all) {
        double t = top.stream().mapToDouble(p -> num(p.get("sales"))).sum(), a = all.stream().mapToDouble(p -> num(p.get("sales"))).sum();
        return a == 0 ? 0 : Math.round(t * 1000 / a) / 10.0;
    }

    @SuppressWarnings("unchecked")
    private String slow(Long shopId, Range main, Map<String, Object> out) {
        List<Map<String, Object>> list = ((List<Map<String, Object>>) analytics.products(shopId, main.period(Granularity.DAY)).get("products")).stream()
                .filter(p -> Boolean.TRUE.equals(p.get("active")) && num(p.get("stock")) > 0)
                .sorted(Comparator.comparing((Map<String, Object> p) -> num(p.get("units"))).thenComparing(p -> -num(p.get("stock")))).toList();
        List<Map<String, Object>> none = list.stream().filter(p -> num(p.get("units")) == 0).toList();
        List<Map<String, Object>> show = list.subList(0, Math.min(5, list.size()));
        out.put("table", table(List.of("Sản phẩm", "Đã bán", "Lượt truy cập", "Tồn kho"), show.stream()
                .map(p -> List.<Object>of(p.get("name"), p.get("units"), p.get("visitors"), p.get("stock"))).toList()));
        if (list.isEmpty()) return "Shop chưa có sản phẩm nào còn hàng đang bán.";
        if (none.isEmpty()) return "Tất cả sản phẩm còn hàng đều có đơn " + main.text() + ". Bán chậm nhất là \"" + show.get(0).get("name") + "\" ("
                + show.get(0).get("units") + " sản phẩm).";
        return none.size() + " sản phẩm còn hàng chưa bán được " + main.text() + ", ví dụ \"" + none.get(0).get("name") + "\" (tồn "
                + none.get(0).get("stock") + ", " + none.get(0).get("visitors") + " lượt truy cập). Gợi ý: tạo flash sale/voucher để xả hàng, "
                + "cải thiện ảnh và mô tả cho sản phẩm có lượt xem nhưng không có đơn.";
    }

    @SuppressWarnings("unchecked")
    private String stock(Long shopId, Map<String, Object> out) {
        LocalDate today = LocalDate.now();
        Map<String, Object> pr = analytics.products(shopId, new Period(today.minusDays(29), today, Granularity.DAY));
        List<Map<String, Object>> list = ((List<Map<String, Object>>) pr.get("products")).stream()
                .filter(p -> Boolean.TRUE.equals(p.get("active"))).toList();
        List<Map<String, Object>> out0 = list.stream().filter(p -> num(p.get("stock")) <= 0).toList();
        List<Map<String, Object>> soon = list.stream().filter(p -> num(p.get("stock")) > 0
                        && (num(p.get("stock")) <= SellerAnalyticsService.LOW_STOCK || (p.get("daysOfStock") != null && num(p.get("daysOfStock")) < 14)))
                .sorted(Comparator.comparing(p -> p.get("daysOfStock") == null ? 9999 : num(p.get("daysOfStock")))).toList();
        List<Map<String, Object>> rows = new ArrayList<>(out0);
        rows.addAll(soon);
        out.put("table", table(List.of("Sản phẩm", "Tồn kho", "Bán 30 ngày", "Đủ bán"), rows.stream().limit(8)
                .map(p -> List.<Object>of(p.get("name"), num(p.get("stock")) <= 0 ? "Hết hàng" : p.get("stock"), p.get("units"),
                        p.get("daysOfStock") == null ? "—" : p.get("daysOfStock") + " ngày")).toList()));
        Map<String, Object> inv = (Map<String, Object>) pr.get("inventory");
        String answer = "Shop đang có " + inv.get("totalStock") + " sản phẩm trong kho (giá trị " + money(inv.get("stockValue")) + "). ";
        answer += out0.isEmpty() ? "Không có sản phẩm nào hết hàng. " : out0.size() + " sản phẩm đã hết hàng (\"" + out0.get(0).get("name") + "\"...). ";
        answer += soon.isEmpty() ? "Chưa có sản phẩm nào sắp hết theo tốc độ bán 30 ngày qua."
                : soon.size() + " sản phẩm sắp hết – \"" + soon.get(0).get("name") + "\" còn " + soon.get(0).get("stock")
                + (soon.get(0).get("daysOfStock") != null ? ", đủ bán khoảng " + soon.get(0).get("daysOfStock") + " ngày" : "") + ". Nên nhập thêm hàng sớm.";
        return answer;
    }

    private String bestTime(Long shopId, Range main, Map<String, Object> out) {
        Range r = ChronoUnit.DAYS.between(main.from(), main.to()) < 13 ? new Range(main.to().minusDays(89), main.to(), "90 ngày qua") : main;
        List<OrderRow> sales = analytics.orders(shopId, r.period(Granularity.DAY)).stream().filter(OrderRow::sale).toList();
        if (sales.isEmpty()) return "Chưa có đơn hàng nào " + r.text() + " để phân tích thời điểm bán chạy.";
        double[] byDay = new double[7];
        int[] ordersByDay = new int[7];
        int[] occurrences = new int[7];
        for (LocalDate d = r.from(); !d.isAfter(r.to()); d = d.plusDays(1)) occurrences[d.getDayOfWeek().getValue() - 1]++;
        int[] hours = new int[24];
        for (OrderRow o : sales) {
            int w = o.at().getDayOfWeek().getValue() - 1;
            byDay[w] += o.total().doubleValue();
            ordersByDay[w]++;
            hours[o.at().getHour()]++;
        }
        List<List<Object>> rows = new ArrayList<>();
        int best = 0;
        for (int i = 0; i < 7; i++) {
            double avg = occurrences[i] == 0 ? 0 : byDay[i] / occurrences[i];
            if (avg > (occurrences[best] == 0 ? 0 : byDay[best] / occurrences[best])) best = i;
            rows.add(List.<Object>of(WEEKDAYS[i], ordersByDay[i], money(byDay[i]), money(avg)));
        }
        out.put("table", table(List.of("Thứ", "Số đơn", "Doanh số", "TB mỗi ngày"), rows));
        out.put("chart", SellerAIService.map("type", "bar", "labels", Arrays.asList(WEEKDAYS), "series", List.of(SellerAIService.map(
                "name", "Doanh số TB mỗi ngày", "money", true,
                "values", Arrays.stream(new int[]{0, 1, 2, 3, 4, 5, 6}).mapToObj(i -> occurrences[i] == 0 ? 0 : Math.round(byDay[i] / occurrences[i])).toList()))));
        // best 3-hour window
        int bestStart = 0, bestCount = -1;
        for (int h = 0; h < 24; h++) {
            int c = hours[h] + hours[(h + 1) % 24] + hours[(h + 2) % 24];
            if (c > bestCount) { bestCount = c; bestStart = h; }
        }
        Map<LocalDate, Double> perDate = sales.stream().collect(Collectors.groupingBy(o -> o.at().toLocalDate(),
                Collectors.summingDouble(o -> o.total().doubleValue())));
        Map.Entry<LocalDate, Double> top = perDate.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow();
        return "Phân tích " + sales.size() + " đơn " + r.text() + ": " + WEEKDAYS[best] + " là ngày bán tốt nhất (trung bình "
                + money(occurrences[best] == 0 ? 0 : byDay[best] / occurrences[best]) + "/ngày). Khung giờ nhiều đơn nhất là "
                + String.format("%02d:00–%02d:00", bestStart, (bestStart + 3) % 24) + " (" + bestCount + " đơn). Ngày có doanh thu cao nhất: "
                + top.getKey().format(D) + " (" + money(top.getValue()) + "). Nên lên lịch flash sale và quảng cáo vào khung giờ này.";
    }

    private String customers(Long shopId, Range main, Range compare, Map<String, Object> out) {
        List<OrderRow> sales = analytics.orders(shopId, main.period(Granularity.DAY)).stream().filter(OrderRow::sale).toList();
        if (sales.isEmpty()) return "Chưa có khách nào đặt hàng " + main.text() + ".";
        Set<Long> before = analytics.orders(shopId, new Period(main.from().minusDays(365), main.from().minusDays(1), Granularity.DAY)).stream()
                .filter(OrderRow::sale).map(OrderRow::buyerId).collect(Collectors.toSet());
        Map<Long, List<OrderRow>> byBuyer = sales.stream().collect(Collectors.groupingBy(OrderRow::buyerId));
        long returning = byBuyer.keySet().stream().filter(before::contains).count();
        long repeatInPeriod = byBuyer.values().stream().filter(l -> l.size() > 1).count();
        long prevBuyers = analytics.orders(shopId, compare.period(Granularity.DAY)).stream().filter(OrderRow::sale).map(OrderRow::buyerId).distinct().count();
        Map<Long, String> names = userRepository.findAllById(byBuyer.keySet()).stream().collect(Collectors.toMap(User::getId, User::getFullName));
        List<Map.Entry<Long, List<OrderRow>>> top = byBuyer.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<Long, List<OrderRow>> e) -> e.getValue().stream().mapToDouble(o -> o.total().doubleValue()).sum()).reversed())
                .limit(5).toList();
        out.put("table", table(List.of("Khách hàng", "Số đơn", "Chi tiêu", "Loại"), top.stream().map(e -> List.<Object>of(
                names.getOrDefault(e.getKey(), "Khách #" + e.getKey()), e.getValue().size(),
                money(e.getValue().stream().mapToDouble(o -> o.total().doubleValue()).sum()), before.contains(e.getKey()) ? "Quay lại" : "Mới")).toList()));
        return main.text().substring(0, 1).toUpperCase() + main.text().substring(1) + " có " + byBuyer.size() + " khách đặt hàng ("
                + change(byBuyer.size(), prevBuyers) + " so với " + compare.text() + "): " + (byBuyer.size() - returning) + " khách mới và " + returning
                + " khách quay lại (" + pct(Math.round(returning * 1000.0 / byBuyer.size()) / 10.0) + "). " + repeatInPeriod
                + " khách đặt từ 2 đơn trở lên trong kỳ. Khách chi tiêu nhiều nhất: " + names.getOrDefault(top.get(0).getKey(), "—") + ".";
    }

    @SuppressWarnings("unchecked")
    private String traffic(Long shopId, Range main, Range compare, Map<String, Object> out) {
        Map<String, Object> tr = analytics.traffic(shopId, main.period(granularityFor(main)));
        Map<String, Object> s = (Map<String, Object>) tr.get("summary");
        Map<String, Object> p = (Map<String, Object>) analytics.traffic(shopId, compare.period(Granularity.MONTH)).get("summary");
        out.put("compare", SellerAIService.map("from", compare.from(), "to", compare.to(), "label", compare.label()));
        out.put("chart", chart((List<Map<String, Object>>) tr.get("series"), "label", "visitors", "Lượt truy cập", false));
        String answer = cap(main.text()) + " shop có " + s.get("visitors") + " lượt truy cập (" + s.get("views") + " lượt xem trang), "
                + change(num(s.get("visitors")), num(p.get("visitors"))) + " so với " + compare.text() + ". Tỷ lệ chuyển đổi đạt "
                + pct(num(s.get("conversion"))) + " (" + points(num(s.get("conversion")), num(p.get("conversion"))) + "), tỷ lệ thêm vào giỏ "
                + pct(num(s.get("cartRate"))) + ".";
        List<Map<String, Object>> attention = (List<Map<String, Object>>) tr.get("attention");
        if (!attention.isEmpty()) answer += " Sản phẩm \"" + attention.get(0).get("name") + "\" có " + attention.get(0).get("visitors")
                + " lượt truy cập nhưng chưa có đơn – nên xem lại giá, ảnh hoặc tạo voucher.";
        return answer;
    }

    @SuppressWarnings("unchecked")
    private String reviews(Long shopId, Range main, Range compare, Map<String, Object> out) {
        Map<String, Object> rv = analytics.reviews(shopId, main.period(Granularity.MONTH));
        Map<String, Object> s = (Map<String, Object>) rv.get("summary");
        Map<String, Object> p = (Map<String, Object>) analytics.reviews(shopId, compare.period(Granularity.MONTH)).get("summary");
        Map<Integer, Long> dist = (Map<Integer, Long>) rv.get("distribution");
        out.put("table", table(List.of("Số sao", "Số đánh giá"), dist.entrySet().stream().map(e -> List.<Object>of(e.getKey() + " ★", e.getValue())).toList()));
        if (num(s.get("count")) == 0) return "Chưa có đánh giá mới " + main.text() + ".";
        return cap(main.text()) + " shop nhận " + s.get("count") + " đánh giá, điểm trung bình " + s.get("average") + "★ (kỳ trước "
                + p.get("average") + "★), " + pct(num(s.get("positiveRate"))) + " hài lòng, " + s.get("negative") + " đánh giá tiêu cực. Tỷ lệ phản hồi "
                + pct(num(s.get("replyRate"))) + "." + (num(s.get("unrepliedNegative")) > 0 ? " Còn " + s.get("unrepliedNegative")
                + " đánh giá tiêu cực chưa trả lời – nên phản hồi sớm." : "") + " Xem trang \"Phân tích đánh giá AI\" để biết khách khen/chê điều gì.";
    }

    @SuppressWarnings("unchecked")
    private String trend(Long shopId, Range main, Map<String, Object> out) {
        Range r = ChronoUnit.DAYS.between(main.from(), main.to()) < 27 ? new Range(main.to().minusDays(55), main.to(), "8 tuần qua") : main;
        Granularity g = ChronoUnit.DAYS.between(r.from(), r.to()) > 120 ? Granularity.MONTH : Granularity.WEEK;
        List<Map<String, Object>> series = (List<Map<String, Object>>) analytics.revenue(shopId, r.period(g)).get("series");
        out.put("chart", chart(series, "label", "sales", "Doanh số", true));
        out.put("period", SellerAIService.map("from", r.from(), "to", r.to(), "label", r.label()));
        if (series.size() < 2) return "Chưa đủ dữ liệu để đánh giá xu hướng.";
        int half = series.size() / 2;
        double first = series.subList(0, half).stream().mapToDouble(b -> num(b.get("sales"))).sum();
        double second = series.subList(series.size() - half, series.size()).stream().mapToDouble(b -> num(b.get("sales"))).sum();
        // least-squares slope over the buckets, as % of the average bucket
        double n = series.size(), sx = 0, sy = 0, sxy = 0, sxx = 0;
        for (int i = 0; i < series.size(); i++) {
            double y = num(series.get(i).get("sales"));
            sx += i; sy += y; sxy += i * y; sxx += (double) i * i;
        }
        double slope = (n * sxy - sx * sy) / Math.max(1, n * sxx - sx * sx);
        double avg = sy / n;
        String dir = avg == 0 ? "chưa có doanh thu" : slope > avg * 0.05 ? "đang tăng" : slope < -avg * 0.05 ? "đang giảm" : "khá ổn định";
        String unit = g == Granularity.MONTH ? "tháng" : "tuần";
        return "Doanh thu " + r.text() + " " + dir + ": nửa sau đạt " + money(second) + " so với " + money(first) + " ở nửa đầu ("
                + change(second, first) + "), trung bình " + money(avg) + "/" + unit + "."
                + (dir.equals("đang giảm") ? " Nên chạy khuyến mãi hoặc quảng cáo để kéo lại doanh số – xem \"Gợi ý thời điểm khuyến mãi\"." : "");
    }

    // ==================== formatting ====================

    private static Map<String, Object> chart(List<Map<String, Object>> series, String labelKey, String valueKey, String name, boolean money) {
        return SellerAIService.map("type", series.size() > 12 ? "line" : "bar",
                "labels", series.stream().map(b -> b.get(labelKey)).toList(),
                "series", List.of(SellerAIService.map("name", name, "money", money, "values", series.stream().map(b -> num(b.get(valueKey))).toList())));
    }

    private static Map<String, Object> table(List<String> columns, List<List<Object>> rows) {
        return SellerAIService.map("columns", columns, "rows", rows);
    }

    @SuppressWarnings("unchecked")
    private static String tableText(Map<String, Object> out) {
        Map<String, Object> t = (Map<String, Object>) out.get("table");
        if (t == null) return "";
        return "\nBảng: " + t.get("columns") + " " + ((List<List<Object>>) t.get("rows")).stream().limit(5).map(Object::toString).collect(Collectors.joining("; "));
    }

    private static String change(double cur, double prev) {
        if (prev == 0) return cur == 0 ? "không đổi" : "tăng mới hoàn toàn";
        double pct = (cur - prev) * 100 / prev;
        if (Math.abs(pct) < 0.05) return "không đổi";
        return (pct > 0 ? "tăng " : "giảm ") + String.format(Locale.forLanguageTag("vi-VN"), "%.1f", Math.abs(pct)) + "%";
    }

    private static String points(double cur, double prev) {
        double d = Math.round((cur - prev) * 100) / 100.0;
        if (d == 0) return "không đổi";
        return (d > 0 ? "tăng " : "giảm ") + String.format(Locale.forLanguageTag("vi-VN"), "%.2f", Math.abs(d)) + " điểm %";
    }

    private static String pct(double v) {
        return String.format(Locale.forLanguageTag("vi-VN"), "%.2f", v).replaceAll(",00$", "") + "%";
    }

    static String money(Object v) {
        return NotificationService.vnd(BigDecimal.valueOf(Math.round(num(v))));
    }

    static double num(Object o) {
        return o == null ? 0 : o instanceof Number n ? n.doubleValue() : Double.parseDouble(o.toString());
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String statusName(String s) {
        return switch (s) {
            case "AWAITING_PAYMENT" -> "Chờ thanh toán";
            case "PENDING" -> "Chờ xác nhận";
            case "CONFIRMED" -> "Chờ lấy hàng";
            case "SHIPPING" -> "Đang giao";
            case "DELIVERED" -> "Đã giao";
            case "CANCELLED" -> "Đã hủy";
            case "RETURN_REQUESTED" -> "Yêu cầu trả hàng";
            default -> "Đã hoàn tiền";
        };
    }

    private static List<String> followUps(Intent i) {
        return switch (i) {
            case REVENUE, ORDERS, AOV, TREND -> List.of("Sản phẩm nào bán chạy nhất tháng này?", "Ngày nào trong tuần bán được nhiều nhất?", "Tỷ lệ hủy đơn tháng này?");
            case TOP_PRODUCTS, SLOW -> List.of("Sản phẩm nào sắp hết hàng?", "Sản phẩm nào bán chậm 30 ngày qua?", "Tỷ lệ chuyển đổi tuần này thế nào?");
            case CUSTOMERS -> List.of("Giá trị trung bình mỗi đơn tháng này?", "Doanh thu tháng này so với tháng trước?");
            default -> List.of("Doanh thu tháng này so với tháng trước?", "Sản phẩm nào bán chạy nhất 7 ngày qua?", "Xu hướng doanh thu 3 tháng qua?");
        };
    }
}
