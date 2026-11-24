package com.marketplace.backend.service.risk;

import java.util.*;

/**
 * The hand-written order fraud rules: the baseline the trained model is compared with, and what decides alone
 * while there is no model. The same rules are in review-ai-service/app/benchmark_risk.py (fraud_rule).
 *
 * Features of an order (all computed from data before the order):
 *   amount (thousand VND), amountRatio (vs the buyer's usual order), accountAgeDays, priorOrders,
 *   ordersLastHour, ordersLastDay, cod (1/0), priorFailRatio (earlier orders cancelled or refused at the door),
 *   phonesLast7d, phoneSharedAccounts (other accounts with the same recipient phone), discountRatio,
 *   quantity, newDeviceLogin (1/0, last 24 h), failedLogins24h, nightHour (1/0, 0h-5h)
 */
public final class FraudRules {

    private FraudRules() {
    }

    public static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("amount", "Giá trị đơn"),
            Map.entry("amountRatio", "Giá trị đơn so với thói quen của người mua"),
            Map.entry("accountAgeDays", "Tuổi tài khoản"),
            Map.entry("priorOrders", "Số đơn trước đó"),
            Map.entry("ordersLastHour", "Số đơn trong 1 giờ"),
            Map.entry("ordersLastDay", "Số đơn trong 24 giờ"),
            Map.entry("cod", "Thanh toán khi nhận hàng"),
            Map.entry("priorFailRatio", "Tỉ lệ đơn trước bị hủy / bom hàng"),
            Map.entry("phonesLast7d", "Số SĐT nhận hàng khác nhau trong 7 ngày"),
            Map.entry("phoneSharedAccounts", "Số tài khoản khác dùng cùng SĐT"),
            Map.entry("discountRatio", "Tỉ lệ giảm giá trên đơn"),
            Map.entry("quantity", "Số lượng sản phẩm"),
            Map.entry("newDeviceLogin", "Đăng nhập từ thiết bị mới"),
            Map.entry("failedLogins24h", "Đăng nhập sai trong 24 giờ"),
            Map.entry("nightHour", "Đặt lúc 0h-5h"));

    // a high value of these is reassuring, not suspicious: never shown as "unusual"
    static final Set<String> SAFE_WHEN_HIGH = Set.of("accountAgeDays", "priorOrders");

    public record Result(int score, List<String> reasons) {}

    public static Result evaluate(Map<String, Double> f) {
        int s = 0;
        List<String> why = new ArrayList<>();
        double age = f.getOrDefault("accountAgeDays", 999.0);
        double prior = f.getOrDefault("priorOrders", 0.0);
        if (age < 1) {
            s += 25;
            why.add("Tài khoản tạo chưa đến 1 ngày");
        } else if (age < 7) {
            s += 10;
            why.add("Tài khoản mới (dưới 7 ngày)");
        }
        if (f.getOrDefault("ordersLastHour", 0.0) >= 3) {
            s += 20;
            why.add("Đặt " + fmt(f.get("ordersLastHour")) + " đơn trong 1 giờ");
        }
        if (prior > 0 && f.getOrDefault("amountRatio", 1.0) >= 5) {
            s += 20;
            why.add("Giá trị gấp " + fmt(f.get("amountRatio")) + " lần đơn thường ngày của người mua");
        }
        if (f.getOrDefault("cod", 0.0) >= 1 && f.getOrDefault("amount", 0.0) >= 5000) {
            s += 10;
            why.add("Đơn COD giá trị lớn (từ 5 triệu)");
        }
        double shared = f.getOrDefault("phoneSharedAccounts", 0.0);
        if (shared >= 2) {
            s += shared >= 3 ? 20 : 10;
            why.add("SĐT nhận hàng được " + fmt(shared) + " tài khoản khác sử dụng");
        }
        if (prior >= 2 && f.getOrDefault("priorFailRatio", 0.0) >= 0.5) {
            s += 25;
            why.add(Math.round(100 * f.get("priorFailRatio")) + "% đơn trước đó bị hủy hoặc bom hàng");
        }
        if (f.getOrDefault("discountRatio", 0.0) >= 0.5) {
            s += 10;
            why.add("Được giảm " + Math.round(100 * f.get("discountRatio")) + "% giá trị đơn");
        }
        if (f.getOrDefault("newDeviceLogin", 0.0) >= 1 && f.getOrDefault("failedLogins24h", 0.0) >= 3) {
            s += 25;
            why.add("Đăng nhập từ thiết bị mới sau " + fmt(f.get("failedLogins24h")) + " lần nhập sai mật khẩu");
        }
        if (f.getOrDefault("quantity", 0.0) >= 20) {
            s += 10;
            why.add("Số lượng lớn bất thường (" + fmt(f.get("quantity")) + " sản phẩm)");
        }
        return new Result(Math.min(100, s), why);
    }

    /**
     * The final score: the trained model when there is one (probability), otherwise the rules, nudged up by how
     * unusual the order is compared with all others (anomaly percentile, needs no labelled case).
     */
    public static int combine(int rule, Double probability, Double percentile) {
        if (probability != null) return (int) Math.round(Math.max(100 * probability, rule * 0.5));
        if (percentile != null && percentile > 0.9) return Math.min(100, rule + (int) Math.round((percentile - 0.9) * 300));
        return rule;
    }

    public static String level(int score) {
        return score >= 60 ? "HIGH" : score >= 30 ? "MEDIUM" : "LOW";
    }

    private static String fmt(Double d) {
        if (d == null) return "0";
        return d == Math.rint(d) ? String.valueOf(d.longValue()) : String.format(Locale.ROOT, "%.1f", d);
    }
}
