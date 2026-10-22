package com.marketplace.backend.service.reviewhub;

import com.marketplace.backend.util.TextNormalizer;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Rule-based reading of one review, always available (no AI needed): sentiment, the issues it complains about and
 * the alarming words in it. Works on accent-free text, clause by clause, with negation ("khong dep" is negative,
 * "khong loi" is not a defect). The LLM (ReviewAiService) and the FastAPI service refine it when configured.
 */
public final class ReviewTextAnalyzer {

    private ReviewTextAnalyzer() {
    }

    public record Issue(String key, String label, String fix) {
    }

    /** The issue taxonomy shown everywhere (keys are stored in ReviewHubItem.issues). */
    public static final List<Issue> ISSUES = List.of(
            new Issue("QUALITY", "Chất lượng sản phẩm", "Kiểm tra chất lượng từng lô (QC) trước khi đóng gói, làm việc lại với xưởng/nhà cung cấp"),
            new Issue("WRONG_DESCRIPTION", "Mô tả sai / không giống hình", "Chụp lại ảnh thật, ghi rõ chất liệu, kích thước, màu có thể chênh lệch"),
            new Issue("DELIVERY", "Giao hàng", "Rút ngắn thời gian chuẩn bị hàng, chọn đơn vị vận chuyển nhanh hơn, báo trước khi giao trễ"),
            new Issue("PACKAGING", "Đóng gói", "Đóng gói chống sốc, dùng hộp cứng cho hàng dễ móp vỡ"),
            new Issue("SIZE", "Size / kích thước", "Bổ sung bảng size chi tiết và tư vấn size qua chat trước khi mua"),
            new Issue("MISSING_ITEM", "Thiếu hàng / sai hàng", "Đối chiếu đơn trước khi đóng gói (checklist, quay video đóng gói)"),
            new Issue("AUTHENTICITY", "Nghi hàng giả / an toàn", "Xác minh nguồn hàng, lưu giấy tờ chứng nhận, xử lý ngay để tránh khiếu nại"),
            new Issue("PRICE", "Giá cả", "Làm rõ giá trị sản phẩm trong mô tả, cân nhắc combo/voucher"),
            new Issue("SERVICE", "Tư vấn / đổi trả", "Phản hồi chat nhanh hơn, quy trình đổi trả rõ ràng"),
            new Issue("OTHER", "Khác", "Đọc kỹ đánh giá và liên hệ khách để làm rõ"));

    public static String label(String key) {
        return ISSUES.stream().filter(i -> i.key().equals(key)).map(Issue::label).findFirst().orElse(key);
    }

    public static String fix(String key) {
        return ISSUES.stream().filter(i -> i.key().equals(key)).map(Issue::fix).findFirst().orElse("");
    }

    public static boolean isIssue(String key) {
        return ISSUES.stream().anyMatch(i -> i.key().equals(key));
    }

    // words that put a clause about this issue; the clause also has to be negative (except the always-bad phrases)
    private static final Map<String, List<String>> TOPIC = new LinkedHashMap<>();
    // phrases that are a complaint on their own
    private static final Map<String, List<String>> COMPLAINT = new LinkedHashMap<>();

    static {
        TOPIC.put("QUALITY", List.of("chat luong", "chat lieu", "vai", "duong may", "ben", "mong", "may", "pin", "hoat dong", "su dung", "mau"));
        TOPIC.put("DELIVERY", List.of("giao", "giao hang", "ship", "shipper", "van chuyen", "nhan hang", "don vi van chuyen"));
        TOPIC.put("PACKAGING", List.of("dong goi", "goi hang", "hop", "bao bi", "thung"));
        TOPIC.put("SIZE", List.of("size", "kich thuoc", "kich co", "form", "mac"));
        TOPIC.put("PRICE", List.of("gia ca", "gia tien", "muc gia", "tien"));
        TOPIC.put("SERVICE", List.of("shop", "tu van", "phan hoi", "tra loi", "thai do", "ho tro", "doi tra", "doi hang", "hoan tien", "chat"));

        COMPLAINT.put("QUALITY", List.of("bi loi", "hang loi", "loi", "hong", "hu", "hu hong", "rach", "bung chi", "bung", "phai mau", "ra mau",
                "xu", "kem chat luong", "chat luong kem", "khong hoat dong", "khong len", "khong chay", "nhanh hong", "dung vai lan", "bi gay",
                "bi nut", "nut vo", "troc", "bong troc", "chi thua", "cau tha", "bi mo", "khong sac", "nong may", "mui hoi", "moc", "bi moc",
                "het han", "da het han", "bi chay"));
        COMPLAINT.put("WRONG_DESCRIPTION", List.of("khong giong hinh", "khac hinh", "khong nhu hinh", "khong dung mo ta", "sai mo ta", "sai mau",
                "sai mau sac", "khac mau", "khong giong mo ta", "khac mo ta", "anh minh hoa", "lua dao hinh anh", "khong giong quang cao",
                "hinh mot dang hang mot neo", "khong dung nhu"));
        COMPLAINT.put("DELIVERY", List.of("giao cham", "giao lau", "giao tre", "ship cham", "ship lau", "cham tre", "lau qua", "doi lau",
                "qua lau", "giao muon", "tre hen", "shipper thai do", "khong giao", "giao nham"));
        COMPLAINT.put("PACKAGING", List.of("mop", "bep", "bi vo", "vo nat", "bi be", "nat", "dong goi so sai", "dong goi au", "dong goi khong can than",
                "khong chong soc", "rach hop", "hop rach", "mop hop"));
        COMPLAINT.put("SIZE", List.of("nho qua", "rong qua", "chat qua", "to qua", "dai qua", "ngan qua", "hoi nho", "hoi rong", "hoi chat", "hoi to", "hoi dai", "hoi ngan", "khong vua", "sai size", "lech size",
                "bang size sai", "khong dung size"));
        COMPLAINT.put("MISSING_ITEM", List.of("thieu", "giao thieu", "thieu hang", "thieu phu kien", "khong du", "giao sai", "gui sai",
                "nham hang", "sai hang", "thieu so luong", "khong co qua tang", "thieu qua"));
        COMPLAINT.put("AUTHENTICITY", List.of("hang gia", "hang nhai", "fake", "nhai", "khong chinh hang", "hang dom", "di ung", "noi man",
                "ngua", "mui hac", "chay no", "phat no", "doc hai", "nguy hiem", "giat dien", "bong da", "kich ung"));
        COMPLAINT.put("PRICE", List.of("dat qua", "khong dang tien", "mac qua", "gia cao", "gia hoi cao", "hoi dat", "gia hoi dat", "khong xung dang"));
        COMPLAINT.put("SERVICE", List.of("khong tra loi", "khong phan hoi", "thai do", "khong ho tro", "lua dao", "khong cho doi", "khong cho tra",
                "khong hoan tien", "chua hoan tien", "block", "chan tin nhan", "doi tra kho", "tu van sai"));
    }

    private static final List<String> POSITIVE = List.of("dep", "tot", "nhanh", "chac chan", "can than", "mem", "vua", "vua van",
            "dung mo ta", "giong hinh", "nhu hinh", "dung hinh", "hai long", "ung y", "re", "dang tien", "xung dang", "nhiet tinh", "xinh",
            "chuan", "ok", "oke", "on", "thich", "tuyet voi", "ben", "day dan", "min", "thom", "hop ly", "chu dao", "than thien",
            "chong soc", "ky", "chat luong tot", "good", "dang mua", "se ung ho", "ung ho", "5 sao", "10 diem", "xin", "yeu");
    private static final List<String> NEGATIVE = List.of("xau", "te", "cham", "tre", "lau", "loi", "rach", "khong giong", "khac hinh",
            "sai", "that vong", "kem", "phai mau", "hong", "hu", "mop", "bep", "vo nat", "nat", "thieu", "cau tha", "xu", "chan qua", "buc",
            "buc minh", "phi tien", "khong nen mua", "dung mua", "dung dat", "lua dao", "hang gia", "nhai", "fake", "moc", "mui hoi", "di ung", "1 sao");
    private static final Set<String> NEGATIONS = Set.of("khong", "ko", "k", "chua", "chang", "cha", "khg", "hok");
    /** words that make a review urgent whatever its stars */
    public static final List<String> SEVERE = List.of("lua dao", "hang gia", "hang nhai", "fake", "di ung", "noi man", "kich ung", "chay no",
            "giat dien", "doc hai", "nguy hiem", "bao cong an", "kien", "to cao", "boc phot", "canh bao", "dung mua", "khong nhan duoc hang",
            "mat tien", "chua hoan tien", "khong hoan tien", "het han", "moc", "bao ve nguoi tieu dung", "report", "bao cao");

    private static final Pattern CLAUSES = Pattern.compile("[.,!?;\\n]+|\\s+(?:nhung|tuy nhien|ma|con)\\s+");

    public record Result(String sentiment, double score, List<String> issues, String summary, List<String> severeHits, double textPolarity,
                         int words) {
    }

    public static Result analyze(int rating, String content) {
        String norm = TextNormalizer.normalize(content == null ? "" : content)
                .replaceAll("[^a-z0-9.,!?;\\n ]", " ").replaceAll(" +", " ").trim();
        int words = norm.isEmpty() ? 0 : norm.split(" ").length;
        Set<String> issues = new LinkedHashSet<>();
        int pos = 0, neg = 0;
        String worstClause = null;
        int worstNeg = 0;
        for (String clause : CLAUSES.split(norm)) {
            String c = " " + clause.trim() + " ";
            if (c.isBlank()) continue;
            int cp = 0, cn = 0;
            for (String w : POSITIVE) if (has(c, w)) { if (negated(c, w)) cn++; else cp++; }
            for (String w : NEGATIVE) if (has(c, w) && !negated(c, w)) cn++;
            // "khong loi", "chua hong": a negated defect is praise, not a complaint
            for (Map.Entry<String, List<String>> e : COMPLAINT.entrySet()) {
                for (String w : e.getValue()) {
                    if (has(c, w) && !negated(c, w)) {
                        issues.add(e.getKey());
                        cn++;
                        break;
                    }
                }
            }
            if (cn > cp) {
                for (Map.Entry<String, List<String>> e : TOPIC.entrySet()) {
                    if (e.getValue().stream().anyMatch(w -> has(c, w))) issues.add(e.getKey());
                }
            }
            pos += cp;
            neg += cn;
            if (cn > worstNeg) {
                worstNeg = cn;
                worstClause = clause.trim();
            }
        }
        double polarity = pos + neg == 0 ? 0 : (pos - neg) / (double) (pos + neg);
        double stars = switch (rating) {
            case 1 -> -1;
            case 2 -> -0.6;
            case 3 -> 0;
            case 4 -> 0.5;
            default -> 1;
        };
        // stars carry the verdict; the words adjust it (and decide it for a 3-star review)
        double score = pos + neg == 0 ? stars : stars * 0.6 + polarity * 0.4;
        String sentiment = score <= -0.25 ? "NEGATIVE" : score >= 0.35 ? "POSITIVE" : "NEUTRAL";
        if (issues.isEmpty() && rating <= 2) issues.add("OTHER");
        if (sentiment.equals("POSITIVE") && rating >= 4 && neg == 0) issues.clear();
        String plain = " " + norm.replaceAll("[^a-z0-9 ]", " ").replaceAll(" +", " ") + " ";
        List<String> severe = SEVERE.stream().filter(w -> has(plain, w)).toList();
        String summary = worstClause == null || issues.isEmpty() ? null : summarize(content, worstClause);
        return new Result(sentiment, Math.round(score * 100) / 100.0, List.copyOf(issues), summary, severe, polarity, words);
    }

    // the original (accented) words of the worst clause, if they can be found, else the accent-free clause
    private static String summarize(String original, String clause) {
        if (original == null) return clause;
        for (String part : original.split("[.!?;\\n]+")) {
            String n = TextNormalizer.normalize(part).replaceAll("[^a-z0-9 ]", " ").replaceAll(" +", " ").trim();
            if (!n.isEmpty() && (n.contains(clause) || clause.contains(n))) return cut(part.trim(), 200);
        }
        return cut(clause, 200);
    }

    static boolean has(String text, String word) {
        return text.contains(" " + word + " ");
    }

    // "khong dep", "khong duoc dep", "chua that su tot"
    private static boolean negated(String text, String word) {
        int i = text.indexOf(" " + word + " ");
        if (i < 0) return false;
        String[] before = text.substring(0, i).trim().split(" ");
        for (int k = before.length - 1; k >= Math.max(0, before.length - 3); k--) {
            if (NEGATIONS.contains(before[k])) return true;
        }
        return false;
    }

    static String cut(String s, int max) {
        return s == null ? null : s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}
