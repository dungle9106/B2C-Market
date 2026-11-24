package com.marketplace.backend.service.forecast;

import com.marketplace.backend.entity.Product;
import com.marketplace.backend.entity.ProductVariant;
import com.marketplace.backend.entity.Shop;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.ProductRepository;
import com.marketplace.backend.repository.ShopRepository;
import com.marketplace.backend.security.CurrentUser;
import com.marketplace.backend.service.AIService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * "Du bao nhap hang (AI)" for sellers: for every SKU (a product, or each variant of it) the daily sales of the last
 * HISTORY_DAYS are fitted with StockForecaster, then
 *  - days left / stock-out date = when the forecast demand adds up to the stock on hand,
 *  - safety stock = z(service level) x sigma x sqrt(lead time),
 *  - reorder point = forecast demand during the lead time + safety stock,
 *  - suggested order = demand over (lead time + days to cover) + safety stock - stock.
 * The forecast is checked on the shop's own past (the last BACKTEST_DAYS hidden, then predicted) and compared with
 * a plain average, so the seller sees how far to trust it. An LLM, when configured, turns the table into advice.
 */
@Service
@RequiredArgsConstructor
public class InventoryForecastService {

    static final int HISTORY_DAYS = 120;
    static final int BACKTEST_DAYS = 14;
    static final int CHART_HISTORY_DAYS = 60;
    static final int CHART_FORECAST_DAYS = 30;
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DM = DateTimeFormatter.ofPattern("dd/MM");
    private static final Map<String, Integer> URGENCY = Map.of("OUT", 0, "URGENT", 1, "REORDER", 2, "OK", 3, "OVERSTOCK", 4, "SLOW", 5);

    private final ShopRepository shopRepository;
    private final ProductRepository productRepository;
    private final JdbcTemplate jdbc;
    private final AIService aiService;

    public record Params(int leadTime, int cover, int serviceLevel) {
        public static Params of(Integer leadTime, Integer cover, Integer serviceLevel) {
            int lt = leadTime == null ? 7 : leadTime;
            int cv = cover == null ? 30 : cover;
            int sl = serviceLevel == null ? 95 : serviceLevel;
            if (lt < 1 || lt > 60) throw new ApiException("Thời gian chờ hàng về phải từ 1 đến 60 ngày", HttpStatus.BAD_REQUEST);
            if (cv < 7 || cv > 120) throw new ApiException("Số ngày muốn đủ hàng phải từ 7 đến 120", HttpStatus.BAD_REQUEST);
            if (sl != 90 && sl != 95 && sl != 99) throw new ApiException("Mức an toàn chỉ nhận 90, 95 hoặc 99%", HttpStatus.BAD_REQUEST);
            return new Params(lt, cv, sl);
        }
    }

    /** one sellable unit with its stock and daily sales (oldest first, ending yesterday) */
    record Sku(Product product, ProductVariant variant, int stock, double[] sales, LocalDate firstDay) {
        String key() { return variant == null ? "p" + product.getId() : "v" + variant.getId(); }
    }

    // ==================== table ====================

    @Transactional(readOnly = true)
    public Map<String, Object> forecast(Params p) {
        Shop shop = myShop();
        LocalDate today = LocalDate.now(VN);
        List<Sku> skus = skus(shop.getId(), today);
        double z = StockForecaster.z(p.serviceLevel());

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Sku s : skus) rows.add(row(s, p, z, today));
        rows.sort(Comparator.comparing((Map<String, Object> r) -> URGENCY.get((String) r.get("status")))
                .thenComparing(r -> r.get("daysLeft") == null ? Double.MAX_VALUE : ((Number) r.get("daysLeft")).doubleValue())
                .thenComparing(r -> -((Number) r.get("forecastDaily")).doubleValue()));

        Map<String, Long> counts = rows.stream().collect(Collectors.groupingBy(r -> (String) r.get("status"), Collectors.counting()));
        Map<String, Object> summary = new LinkedHashMap<>();
        for (String st : URGENCY.keySet()) summary.put(st, counts.getOrDefault(st, 0L));
        summary.put("skus", rows.size());
        summary.put("suggestUnits", rows.stream().mapToLong(r -> ((Number) r.get("suggestQty")).longValue()).sum());
        summary.put("suggestSkus", rows.stream().filter(r -> ((Number) r.get("suggestQty")).longValue() > 0).count());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("params", Map.of("leadTime", p.leadTime(), "cover", p.cover(), "serviceLevel", p.serviceLevel()));
        out.put("summary", summary);
        out.put("accuracy", backtest(skus));
        out.put("model", "Mỗi mã hàng: thử Holt (làm mượt hàm mũ có xu hướng) với nhiều tham số và trung bình 28/56 ngày, nhân hệ số theo ngày trong tuần; chọn mô hình dự đoán đúng nhất trên các tuần đã qua rồi kết hợp với trung bình 28 ngày");
        out.put("historyDays", HISTORY_DAYS);
        out.put("items", rows);
        return out;
    }

    private Map<String, Object> row(Sku s, Params p, double z, LocalDate today) {
        StockForecaster.Fit fit = StockForecaster.fit(s.sales(), s.firstDay().getDayOfWeek().getValue() - 1);
        double[] sales = s.sales();
        int n = sales.length;
        double sold = Arrays.stream(sales).sum();
        double last28 = tailSum(sales, 28) / Math.max(1, Math.min(28, n));
        double last14 = tailSum(sales, 14);
        double next7 = fit.total(7) / 7;
        double next14 = fit.total(14);
        double safety = z * fit.sigma() * Math.sqrt(p.leadTime());
        double leadDemand = fit.total(p.leadTime());
        double reorderPoint = leadDemand + safety;
        double coverDemand = fit.total(p.leadTime() + p.cover());

        // walk the forecast until it eats the stock (capped at a year)
        Integer daysLeft = null;
        if (s.stock() <= 0) daysLeft = 0;
        else if (next7 > 0.01) {
            double cum = 0;
            for (int h = 1; h <= 365; h++) {
                cum += fit.forecast(h);
                if (cum >= s.stock()) { daysLeft = h - 1; break; }
            }
        }

        String status;
        String reason;
        long suggest = 0;
        if (sold == 0 || next7 < 0.02) {
            status = "SLOW";
            reason = s.stock() > 0 ? "Không bán được trong " + n + " ngày qua: cân nhắc giảm giá, chạy Flash Sale hoặc ẩn bớt" : "Chưa có lượt bán";
        } else if (s.stock() <= 0) {
            status = "OUT";
            reason = "Đã hết hàng, mỗi ngày đang lỡ khoảng " + fmt(next7) + " sản phẩm";
        } else if (daysLeft != null && daysLeft <= p.leadTime()) {
            status = "URGENT";
            reason = "Sẽ hết sau ~" + daysLeft + " ngày, trước khi hàng mới kịp về (" + p.leadTime() + " ngày)";
        } else if (s.stock() <= reorderPoint) {
            status = "REORDER";
            reason = "Tồn kho đã chạm điểm đặt hàng lại (" + Math.round(Math.ceil(reorderPoint)) + "): nên nhập ngay để không bị đứt hàng";
        } else if (daysLeft == null || daysLeft > Math.max(90, 3 * (p.leadTime() + p.cover()))) {
            status = "OVERSTOCK";
            reason = "Tồn quá nhiều so với tốc độ bán (" + (daysLeft == null ? "trên 1 năm" : "~" + daysLeft + " ngày") + "): tạm ngừng nhập, cân nhắc khuyến mãi";
        } else {
            status = "OK";
            reason = "Đủ bán khoảng " + daysLeft + " ngày";
        }
        if (status.equals("OUT") || status.equals("URGENT") || status.equals("REORDER")) {
            suggest = Math.max(1, (long) Math.ceil(coverDemand + safety - Math.max(0, s.stock())));
        }

        Product pr = s.product();
        ProductVariant v = s.variant();
        boolean flashSoon = pr.getFlashSaleStart() != null && pr.getFlashSaleStart().isAfter(LocalDateTime.now())
                && pr.getFlashSaleStart().isBefore(LocalDateTime.now().plusDays(p.leadTime() + p.cover()));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("key", s.key());
        r.put("productId", pr.getId());
        r.put("variantId", v == null ? null : v.getId());
        r.put("name", pr.getName());
        r.put("variantName", v == null ? null : v.getName());
        r.put("sku", v != null ? v.getSku() : pr.getSku());
        r.put("thumbnailUrl", v != null && v.getImageUrl() != null ? v.getImageUrl() : pr.getThumbnailUrl());
        r.put("active", pr.isActive());
        r.put("stock", s.stock());
        r.put("avgDaily", round1(last28));
        r.put("forecastDaily", round1(next7));
        r.put("trendPercent", last14 >= 3 ? (int) Math.round((next14 - last14) * 100 / last14) : null);
        r.put("daysLeft", daysLeft);
        r.put("stockoutDate", daysLeft == null ? null : today.plusDays(daysLeft).toString());
        r.put("safetyStock", (long) Math.ceil(safety));
        r.put("reorderPoint", (long) Math.ceil(reorderPoint));
        r.put("coverDemand", (long) Math.ceil(coverDemand));
        r.put("suggestQty", suggest);
        r.put("status", status);
        r.put("reason", reason);
        r.put("seasonal", fit.seasonal());
        r.put("confidence", n < 14 || sold < 5 ? "LOW" : fit.sigma() > Math.max(1, next7) * 1.5 ? "MEDIUM" : "HIGH");
        r.put("flashSaleSoon", flashSoon);
        return r;
    }

    /** hide the last BACKTEST_DAYS, predict them, compare (WAPE); the same for "average of the 28 days before" */
    Map<String, Object> backtest(List<Sku> skus) {
        double err = 0, naiveErr = 0, actualTotal = 0;
        int used = 0;
        for (Sku s : skus) {
            double[] y = s.sales();
            if (y.length < BACKTEST_DAYS + 28) continue;
            double actual = tailSum(y, BACKTEST_DAYS);
            double[] train = Arrays.copyOf(y, y.length - BACKTEST_DAYS);
            if (Arrays.stream(train).sum() == 0 && actual == 0) continue;
            StockForecaster.Fit fit = StockForecaster.fit(train, s.firstDay().getDayOfWeek().getValue() - 1);
            double predicted = fit.total(BACKTEST_DAYS);
            double naive = tailSum(train, 28) / 28 * BACKTEST_DAYS;
            err += Math.abs(actual - predicted);
            naiveErr += Math.abs(actual - naive);
            actualTotal += actual;
            used++;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("skus", used);
        m.put("horizonDays", BACKTEST_DAYS);
        if (actualTotal <= 0) {
            m.put("accuracy", null);
            m.put("naiveAccuracy", null);
            return m;
        }
        m.put("accuracy", Math.max(0, Math.round(1000 - err * 1000 / actualTotal) / 10.0));
        m.put("naiveAccuracy", Math.max(0, Math.round(1000 - naiveErr * 1000 / actualTotal) / 10.0));
        m.put("actualUnits", Math.round(actualTotal));
        return m;
    }

    // ==================== one SKU: chart ====================

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long productId, Long variantId, Params p) {
        Shop shop = myShop();
        LocalDate today = LocalDate.now(VN);
        Sku s = skus(shop.getId(), today).stream()
                .filter(x -> x.product().getId().equals(productId)
                        && Objects.equals(x.variant() == null ? null : x.variant().getId(), variantId))
                .findFirst()
                .orElseThrow(() -> new ApiException("Không tìm thấy mã hàng", HttpStatus.NOT_FOUND));
        StockForecaster.Fit fit = StockForecaster.fit(s.sales(), s.firstDay().getDayOfWeek().getValue() - 1);
        double band = StockForecaster.z(p.serviceLevel()) * fit.sigma();

        int hist = Math.min(CHART_HISTORY_DAYS, s.sales().length);
        List<String> labels = new ArrayList<>();
        List<Double> actual = new ArrayList<>();
        List<Double> forecast = new ArrayList<>();
        List<Double> upper = new ArrayList<>();
        for (int i = hist; i >= 1; i--) {
            labels.add(today.minusDays(i).format(DM));
            actual.add(s.sales()[s.sales().length - i]);
            forecast.add(null);
            upper.add(null);
        }
        for (int h = 1; h <= CHART_FORECAST_DAYS; h++) {
            labels.add(today.plusDays(h - 1).format(DM));
            actual.add(null);
            double f = fit.forecast(h);
            forecast.add(round1(f));
            upper.add(round1(f + band));
        }
        Map<String, Object> m = new LinkedHashMap<>(row(s, p, StockForecaster.z(p.serviceLevel()), today));
        m.put("labels", labels);
        m.put("actual", actual);
        m.put("forecast", forecast);
        m.put("upper", upper);
        m.put("weekdayIndex", Arrays.stream(fit.season()).map(InventoryForecastService::round2).boxed().toList());
        m.put("alpha", fit.alpha());
        m.put("beta", fit.beta());
        m.put("sigma", round1(fit.sigma()));
        m.put("method", fit.method());
        return m;
    }

    // ==================== advice ====================

    @Transactional(readOnly = true)
    public Map<String, Object> advice(Params p) {
        Map<String, Object> f = forecast(p);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) f.get("items");
        List<Map<String, Object>> focus = items.stream()
                .filter(r -> !"OK".equals(r.get("status"))).limit(12).toList();
        String rules = ruleAdvice(items, p);
        if (focus.isEmpty()) return Map.of("advice", rules, "engine", "RULES");

        StringBuilder table = new StringBuilder();
        for (Map<String, Object> r : focus) {
            table.append("- ").append(r.get("name")).append(r.get("variantName") == null ? "" : " (" + r.get("variantName") + ")")
                    .append(": trạng thái ").append(r.get("status")).append(", tồn ").append(r.get("stock"))
                    .append(", bán TB ").append(r.get("avgDaily")).append("/ngày, dự báo ").append(r.get("forecastDaily")).append("/ngày")
                    .append(r.get("trendPercent") == null ? "" : ", xu hướng " + r.get("trendPercent") + "%")
                    .append(r.get("daysLeft") == null ? "" : ", còn ~" + r.get("daysLeft") + " ngày")
                    .append(", nên nhập ").append(r.get("suggestQty"))
                    .append(Boolean.TRUE.equals(r.get("flashSaleSoon")) ? ", sắp có Flash Sale" : "").append('\n');
        }
        String system = """
                Bạn là chuyên gia quản lý tồn kho cho người bán trên sàn TMĐT Shoppi. Dựa trên bảng dự báo (đã tính bằng mô hình thống kê),
                viết lời khuyên ngắn gọn bằng tiếng Việt: tối đa 6 gạch đầu dòng, mỗi dòng một hành động cụ thể (nhập bao nhiêu, mã nào trước,
                mã nào nên xả hàng/khuyến mãi). Không bịa số liệu ngoài bảng. Không dùng markdown đậm, chỉ gạch đầu dòng "- ".""";
        String user = "Thời gian chờ hàng về: " + p.leadTime() + " ngày; muốn đủ hàng thêm " + p.cover() + " ngày; mức an toàn "
                + p.serviceLevel() + "%.\nTrạng thái: OUT=hết hàng, URGENT=sắp hết trước khi hàng về, REORDER=đến lúc đặt hàng, OVERSTOCK=tồn nhiều, SLOW=không bán được.\n" + table;
        String answer = aiService.completeOrNull(system, user);
        return answer == null ? Map.of("advice", rules, "engine", "RULES") : Map.of("advice", answer, "engine", "AI");
    }

    static String ruleAdvice(List<Map<String, Object>> items, Params p) {
        List<String> lines = new ArrayList<>();
        List<Map<String, Object>> buy = items.stream().filter(r -> ((Number) r.get("suggestQty")).longValue() > 0).toList();
        if (!buy.isEmpty()) {
            lines.add("- Cần nhập " + buy.size() + " mã hàng, tổng khoảng " + buy.stream().mapToLong(r -> ((Number) r.get("suggestQty")).longValue()).sum()
                    + " sản phẩm để đủ bán " + p.cover() + " ngày sau khi hàng về.");
            buy.stream().limit(3).forEach(r -> lines.add("- Ưu tiên " + label(r) + ": nhập " + r.get("suggestQty") + " (" + r.get("reason") + ")."));
        }
        long over = items.stream().filter(r -> "OVERSTOCK".equals(r.get("status"))).count();
        if (over > 0) lines.add("- " + over + " mã đang tồn nhiều: tạm ngừng nhập, cân nhắc mã giảm giá hoặc combo để xả bớt.");
        long slow = items.stream().filter(r -> "SLOW".equals(r.get("status")) && ((Number) r.get("stock")).intValue() > 0).count();
        if (slow > 0) lines.add("- " + slow + " mã không bán được dù còn hàng: xem lại giá, ảnh và mô tả, hoặc đưa vào Flash Sale.");
        if (lines.isEmpty()) lines.add("- Tồn kho đang cân đối với tốc độ bán, chưa cần nhập thêm.");
        return String.join("\n", lines);
    }

    private static String label(Map<String, Object> r) {
        return r.get("name") + (r.get("variantName") == null ? "" : " (" + r.get("variantName") + ")");
    }

    // ==================== data ====================

    List<Sku> skus(Long shopId, LocalDate today) {
        LocalDate start = today.minusDays(HISTORY_DAYS);
        // units sold per SKU and day (cancelled, refunded and unpaid orders are not demand)
        Map<String, Map<LocalDate, Double>> daily = new HashMap<>();
        jdbc.query("""
                select oi.product_id, oi.variant_id, cast(o.created_at as date) d, sum(oi.quantity)
                from order_items oi join orders o on o.id = oi.order_id
                where o.shop_id = ? and o.created_at >= ? and o.created_at < ?
                  and o.status not in ('CANCELLED', 'REFUNDED', 'AWAITING_PAYMENT')
                group by oi.product_id, oi.variant_id, cast(o.created_at as date)""",
                rs -> {
                    long pid = rs.getLong(1);
                    long vid = rs.getLong(2);
                    boolean hasVariant = !rs.wasNull();
                    LocalDate d = rs.getDate(3).toLocalDate();
                    double q = rs.getDouble(4);
                    daily.computeIfAbsent("p" + pid, k -> new HashMap<>()).merge(d, q, Double::sum);   // product total
                    if (hasVariant) daily.computeIfAbsent("v" + vid, k -> new HashMap<>()).merge(d, q, Double::sum);
                },
                shopId, Date.valueOf(start), Date.valueOf(today));

        List<Sku> out = new ArrayList<>();
        for (Product pr : productRepository.findByShopId(shopId)) {
            // a new product's series starts when it was listed, not with 120 days of zeros
            LocalDate listed = pr.getCreatedAt() == null ? start : pr.getCreatedAt().toLocalDate();
            LocalDate first = listed.isAfter(start) ? listed : start;
            if (pr.getVariants() == null || pr.getVariants().isEmpty()) {
                out.add(new Sku(pr, null, nz(pr.getStock()), series(daily.get("p" + pr.getId()), first, today), first));
            } else {
                for (ProductVariant v : pr.getVariants()) {
                    out.add(new Sku(pr, v, nz(v.getStock()), series(daily.get("v" + v.getId()), first, today), first));
                }
            }
        }
        return out;
    }

    private static double[] series(Map<LocalDate, Double> byDay, LocalDate first, LocalDate today) {
        int n = (int) Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(first, today));
        double[] y = new double[n];
        if (byDay != null) byDay.forEach((d, q) -> {
            int i = (int) java.time.temporal.ChronoUnit.DAYS.between(first, d);
            if (i >= 0 && i < n) y[i] += q;
        });
        return y;
    }

    private Shop myShop() {
        return shopRepository.findBySellerId(CurrentUser.id())
                .orElseThrow(() -> new ApiException("Bạn chưa có shop", HttpStatus.BAD_REQUEST));
    }

    private static double tailSum(double[] y, int days) {
        double s = 0;
        for (int i = Math.max(0, y.length - days); i < y.length; i++) s += y[i];
        return s;
    }

    private static int nz(Integer v) { return v == null ? 0 : v; }
    private static double round1(double v) { return Math.round(v * 10) / 10.0; }
    private static double round2(double v) { return Math.round(v * 100) / 100.0; }
    private static String fmt(double v) { return v >= 10 ? String.valueOf(Math.round(v)) : String.valueOf(round1(v)); }
}
