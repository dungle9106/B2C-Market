package com.marketplace.backend.service.returnrisk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The explainable part of the return abuse score: each rule adds or removes points and says why. Rules that remove
 * points matter as much as the others: a defective product makes honest buyers look like repeat returners.
 */
final class ReturnRiskRules {

    private ReturnRiskRules() {}

    static final int MEDIUM = 40, HIGH = 70;
    private static final double HIGH_VALUE = 1_000_000;

    record Signal(String code, String label, String detail, int points) {}

    /** Case history of one return request. "Prior" counts exclude the request itself. */
    record Features(long accountAgeDays, long orders180, long delivered180, long priorReturns90, long priorReturns180, double returnRate180,
                    double refunds180, double refundToSpend180, long sameReason180, long lostDisputes, long confirmedAbuse,
                    double minutesSinceDelivery, double refundRatio, double refundAmount, boolean refundOnly, int evidenceCount,
                    int evidenceReusedSelf, int evidenceReusedOthers, int evidenceMatchesListing, boolean duplicateTextSelf,
                    boolean duplicateTextOthers, long sharedPhoneAccounts, long otherBuyersSameProduct90, double shopReturnRate90) {

        Map<String, Number> toMap() {
            Map<String, Number> m = new LinkedHashMap<>();
            m.put("accountAgeDays", accountAgeDays);
            m.put("orders180", orders180);
            m.put("delivered180", delivered180);
            m.put("priorReturns90", priorReturns90);
            m.put("priorReturns180", priorReturns180);
            m.put("returnRate180", returnRate180);
            m.put("refunds180", refunds180);
            m.put("refundToSpend180", refundToSpend180);
            m.put("sameReason180", sameReason180);
            m.put("lostDisputes", lostDisputes);
            m.put("confirmedAbuse", confirmedAbuse);
            m.put("minutesSinceDelivery", minutesSinceDelivery);
            m.put("refundRatio", refundRatio);
            m.put("refundAmount", refundAmount);
            m.put("refundOnly", refundOnly ? 1 : 0);
            m.put("evidenceCount", evidenceCount);
            m.put("evidenceReusedSelf", evidenceReusedSelf);
            m.put("evidenceReusedOthers", evidenceReusedOthers);
            m.put("evidenceMatchesListing", evidenceMatchesListing);
            m.put("duplicateTextSelf", duplicateTextSelf ? 1 : 0);
            m.put("duplicateTextOthers", duplicateTextOthers ? 1 : 0);
            m.put("sharedPhoneAccounts", sharedPhoneAccounts);
            m.put("otherBuyersSameProduct90", otherBuyersSameProduct90);
            m.put("shopReturnRate90", shopReturnRate90);
            return m;
        }
    }

    static final Map<String, String> FEATURE_NAMES = Map.ofEntries(
            Map.entry("accountAgeDays", "tuổi tài khoản"), Map.entry("orders180", "số đơn 180 ngày"), Map.entry("delivered180", "số đơn đã giao"),
            Map.entry("priorReturns90", "số lần trả hàng 90 ngày"), Map.entry("priorReturns180", "số lần trả hàng 180 ngày"),
            Map.entry("returnRate180", "tỷ lệ trả hàng"), Map.entry("refunds180", "tổng tiền đã hoàn"), Map.entry("refundToSpend180", "tỷ lệ tiền hoàn trên chi tiêu"),
            Map.entry("sameReason180", "số lần dùng cùng lý do"), Map.entry("lostDisputes", "số khiếu nại bị bác"), Map.entry("confirmedAbuse", "số ca đã xác nhận lạm dụng"),
            Map.entry("minutesSinceDelivery", "thời gian từ lúc giao"), Map.entry("refundRatio", "tỷ lệ hoàn trên giá trị đơn"), Map.entry("refundAmount", "số tiền yêu cầu hoàn"),
            Map.entry("refundOnly", "chỉ hoàn tiền, không trả hàng"), Map.entry("evidenceCount", "số bằng chứng"), Map.entry("evidenceReusedSelf", "bằng chứng dùng lại"),
            Map.entry("evidenceReusedOthers", "bằng chứng trùng tài khoản khác"), Map.entry("evidenceMatchesListing", "bằng chứng trùng ảnh đăng bán"),
            Map.entry("duplicateTextSelf", "mô tả lặp lại"), Map.entry("duplicateTextOthers", "mô tả trùng tài khoản khác"), Map.entry("sharedPhoneAccounts", "tài khoản dùng chung số nhận hàng"),
            Map.entry("otherBuyersSameProduct90", "người khác trả cùng sản phẩm"), Map.entry("shopReturnRate90", "tỷ lệ trả hàng của shop"));

    static List<Signal> evaluate(Features f) {
        List<Signal> s = new ArrayList<>();

        if (f.confirmedAbuse() > 0) add(s, "CONFIRMED_BEFORE", "Từng bị xác nhận lạm dụng", f.confirmedAbuse() + " ca trước đây của tài khoản này được admin xác nhận", 40);
        if (f.evidenceReusedOthers() > 0) add(s, "EVIDENCE_OTHER_BUYER", "Bằng chứng trùng với tài khoản khác", f.evidenceReusedOthers() + " ảnh/video giống hệt hồ sơ của người mua khác", 35);
        if (f.evidenceReusedSelf() > 0) add(s, "EVIDENCE_REUSED", "Dùng lại bằng chứng của đơn khác", f.evidenceReusedSelf() + " ảnh/video đã dùng cho đơn khác của chính tài khoản này", 30);
        if (f.evidenceMatchesListing() > 0) add(s, "EVIDENCE_LISTING", "Bằng chứng giống ảnh đăng bán", "Ảnh gửi lên trùng với ảnh sản phẩm của shop, không phải ảnh chụp hàng thực nhận", 30);
        if (f.sharedPhoneAccounts() >= 2) add(s, "SHARED_PHONE", "Nhiều tài khoản chung số nhận hàng", f.sharedPhoneAccounts() + " tài khoản khác cùng số điện thoại nhận hàng đã yêu cầu hoàn trong 180 ngày", 25);
        if (f.duplicateTextOthers()) add(s, "TEXT_OTHER_BUYER", "Mô tả trùng với tài khoản khác", "Nội dung gần như giống hệt yêu cầu của người mua khác", 20);
        else if (f.duplicateTextSelf()) add(s, "TEXT_REPEATED", "Mô tả lặp lại", "Nội dung gần như giống hệt yêu cầu trước đó của tài khoản này", 15);

        if (f.priorReturns180() >= 5) add(s, "MANY_RETURNS", "Trả hàng rất nhiều lần", f.priorReturns180() + " yêu cầu trong 180 ngày trước đó", 20);
        else if (f.priorReturns180() >= 3) add(s, "MANY_RETURNS", "Trả hàng nhiều lần", f.priorReturns180() + " yêu cầu trong 180 ngày trước đó", 10);
        // a few orders say nothing about a rate
        if (f.orders180() >= 4 && f.returnRate180() >= 0.5) add(s, "HIGH_RATE", "Tỷ lệ trả hàng rất cao", percent(f.returnRate180()) + " số đơn trong 180 ngày", 20);
        else if (f.orders180() >= 4 && f.returnRate180() >= 0.3) add(s, "HIGH_RATE", "Tỷ lệ trả hàng cao", percent(f.returnRate180()) + " số đơn trong 180 ngày", 10);
        if (f.priorReturns180() >= 2 && f.refundToSpend180() >= 0.7) add(s, "REFUND_HEAVY", "Phần lớn tiền chi đã được hoàn", percent(f.refundToSpend180()) + " chi tiêu 180 ngày", 10);
        if (f.sameReason180() >= 3) add(s, "SAME_REASON", "Luôn dùng cùng một lý do", f.sameReason180() + " yêu cầu trước đó cùng lý do", 10);
        if (f.lostDisputes() > 0) add(s, "LOST_DISPUTES", "Từng bị bác khiếu nại", f.lostDisputes() + " khiếu nại được Shoppi kết luận không hoàn tiền", (int) Math.min(30, f.lostDisputes() * 15));

        if (f.accountAgeDays() < 14 && f.refundAmount() >= HIGH_VALUE) add(s, "NEW_ACCOUNT", "Tài khoản mới, giá trị cao", "Tài khoản " + f.accountAgeDays() + " ngày tuổi, yêu cầu hoàn " + money(f.refundAmount()), 15);
        if (f.refundOnly() && f.refundAmount() >= HIGH_VALUE) add(s, "REFUND_ONLY_HIGH", "Chỉ hoàn tiền với giá trị cao", "Người mua giữ hàng, yêu cầu hoàn " + money(f.refundAmount()), 10);
        if (f.minutesSinceDelivery() >= 0 && f.minutesSinceDelivery() < 10) add(s, "TOO_FAST", "Yêu cầu ngay sau khi giao", "Gửi yêu cầu kèm bằng chứng sau " + Math.round(f.minutesSinceDelivery()) + " phút", 10);

        if (f.otherBuyersSameProduct90() >= 3) add(s, "PRODUCT_WIDE", "Nhiều người cùng trả sản phẩm này", f.otherBuyersSameProduct90() + " người mua khác trong 90 ngày: có thể lỗi ở sản phẩm", -20);
        if (f.shopReturnRate90() >= 0.2) add(s, "SHOP_HIGH_RATE", "Shop có tỷ lệ trả hàng cao", percent(f.shopReturnRate90()) + " đơn của shop trong 90 ngày", -10);
        if (f.delivered180() >= 10 && f.returnRate180() < 0.1 && f.confirmedAbuse() == 0) add(s, "CLEAN_HISTORY", "Lịch sử mua hàng tốt", f.delivered180() + " đơn đã giao, rất ít trả hàng", -15);
        return s;
    }

    static int clamp(int score) {
        return Math.max(0, Math.min(100, score));
    }

    static String level(int score) {
        return score >= HIGH ? "HIGH" : score >= MEDIUM ? "MEDIUM" : "LOW";
    }

    private static void add(List<Signal> list, String code, String label, String detail, int points) {
        list.add(new Signal(code, label, detail, points));
    }

    private static String percent(double v) {
        return Math.round(v * 100) + "%";
    }

    private static String money(double v) {
        return String.format("%,d", Math.round(v)).replace(',', '.') + "₫";
    }
}
