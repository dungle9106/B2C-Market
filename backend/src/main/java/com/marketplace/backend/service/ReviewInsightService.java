package com.marketplace.backend.service;

import com.marketplace.backend.entity.Shop;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.ReviewRepository;
import com.marketplace.backend.repository.ShopRepository;
import com.marketplace.backend.security.CurrentUser;
import com.marketplace.backend.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * "Tom tat & phan tich danh gia": aspect-based sentiment over the shop's review comments.
 * Each comment is cut into clauses; a clause mentioning an aspect (quality, size, delivery...) counts as
 * praise or complaint for it, from the positive/negative words in the clause (with negation:
 * "khong dep" is negative, "khong loi" positive) or, when the clause has none, from the star rating.
 * With an LLM configured the summary paragraph is written by the model from these statistics.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReviewInsightService {

    record Aspect(String key, String label, List<String> words, String action) { }

    static final List<Aspect> ASPECTS = List.of(
            new Aspect("QUALITY", "Chất lượng & chất liệu", List.of("chat luong", "vai", "chat lieu", "ben", "day dan", "mong", "xu", "rach",
                    "bong tron", "phai mau", "co gian", "mem", "min", "thom", "mui", "duong may", "chi thua", "loi", "bi loi", "hu hong", "bi hong",
                    "chac chan", "lam an", "ky", "cau tha"),
                    "Kiểm tra chất lượng từng lô hàng trước khi giao và mô tả chất liệu chính xác hơn"),
            new Aspect("SIZE", "Size & form dáng", List.of("size", "form", "vua", "vua van", "rong", "nho qua", "hoi nho", "to qua", "hoi to",
                    "chat qua", "hoi chat", "bi chat", "dai qua", "ngan qua", "om", "bang size", "kich thuoc", "kich co", "mac vua"),
                    "Bổ sung bảng size chi tiết (số đo, chiều cao/cân nặng) và chủ động tư vấn size qua chat"),
            new Aspect("DELIVERY", "Giao hàng", List.of("giao", "giao hang", "ship", "shipper", "van chuyen", "nhanh", "cham", "tre", "lau",
                    "nhan hang"),
                    "Rút ngắn thời gian chuẩn bị hàng và ưu tiên đơn vị vận chuyển nhanh"),
            new Aspect("PACKAGING", "Đóng gói", List.of("dong goi", "goi hang", "goi", "hop", "bao bi", "can than", "chong soc", "mop", "bep",
                    "bi vo", "bi be", "nat"),
                    "Đóng gói chống sốc kỹ hơn, dùng hộp cứng cho hàng dễ móp vỡ"),
            new Aspect("PRICE", "Giá cả", List.of("gia", "gia ca", "re", "dat", "dang tien", "xung dang", "hop ly", "tien nao cua nay"),
                    "Tạo combo/voucher hoặc làm nổi bật giá trị sản phẩm trong mô tả"),
            new Aspect("LOOK", "Mẫu mã & đúng mô tả", List.of("mau", "mau sac", "giong hinh", "nhu hinh", "dung hinh", "dung mo ta", "khac hinh",
                    "khong giong", "thiet ke", "kieu dang", "dep", "xinh", "xau"),
                    "Cập nhật ảnh chụp thật, ghi rõ màu sắc có thể chênh lệch do ánh sáng"),
            new Aspect("SERVICE", "Shop & tư vấn", List.of("tu van", "nhiet tinh", "phan hoi", "than thien", "thai do", "chu dao", "ho tro",
                    "tra loi", "dich vu"),
                    "Phản hồi tin nhắn nhanh hơn, dùng tin nhắn mẫu và tin nhắn tự động"));

    static final List<String> POSITIVE = List.of("dep", "tot", "nhanh", "chac chan", "can than", "mem", "mat", "vua", "vua van", "dung",
            "giong hinh", "nhu hinh", "dung hinh", "dung mo ta", "ung ho", "hai long", "re", "dang tien", "xung dang", "nhiet tinh", "xinh",
            "chuan", "ok", "oke", "on", "thich", "tuyet voi", "ung y", "ben", "day dan", "min", "thom", "hop ly", "chu dao", "than thien",
            "chong soc", "ky", "xin", "chat luong tot", "good", "dang mua");
    static final List<String> NEGATIVE = List.of("xau", "te", "cham", "tre", "lau", "mong", "loi", "bi loi", "rach", "khong giong", "khac hinh",
            "sai", "nho qua", "rong qua", "chat qua", "to qua", "hoi nho", "hoi to", "hoi chat", "dai qua", "ngan qua", "dat", "that vong",
            "kem", "phai mau", "bong tron", "mui", "hu hong", "bi hong", "mop", "bep", "bi vo", "bi be", "nat", "thieu", "cau tha", "xu",
            "chi thua", "khong nhu");
    private static final List<String> NEGATIONS = List.of("khong", "ko", "k", "chua", "chang", "cha");

    // syllables that make a two-word keyword meaningless ("la va", "cho minh"...)
    private static final Set<String> FILLER = Set.of("là", "và", "thì", "có", "được", "cho", "với", "một", "nhưng", "cũng", "rồi", "nên",
            "sẽ", "đi", "mà", "nhé", "nha", "ạ", "mình", "rất", "quá", "lắm", "này", "đã", "bị", "của", "khi", "thấy", "nói", "chung", "luôn",
            "hơi", "nữa", "vẫn", "để", "từ", "các", "những", "em", "anh", "chị", "bạn", "ko", "k", "không", "shop", "sp");
    private static final Pattern CLAUSES = Pattern.compile("[.,!?;\\n]+|\\s+(?i:nhưng|tuy nhiên|mà)\\s+");

    private final ReviewRepository reviewRepository;
    private final ShopRepository shopRepository;
    private final AIService aiService;

    /** days: 30 / 90 / 180 / 365 (default 90); productId: one product or the whole shop. */
    public Map<String, Object> insights(Long productId, Integer days) {
        Shop shop = shopRepository.findBySellerId(CurrentUser.id())
                .orElseThrow(() -> new ApiException("Bạn chưa có shop", HttpStatus.BAD_REQUEST));
        int d = days == null ? 90 : Math.max(7, Math.min(days, 730));
        List<Object[]> rows = reviewRepository.textsForShop(shop.getId(), productId, LocalDateTime.now().minusDays(d));

        Map<String, Stat> stats = new LinkedHashMap<>();
        ASPECTS.forEach(a -> stats.put(a.key(), new Stat(a)));
        Map<Long, ProductStat> products = new LinkedHashMap<>();
        Map<String, Integer> bigrams = new HashMap<>();
        int withComment = 0, pos = 0, neu = 0, neg = 0;
        double sum = 0;
        List<String> samples = new ArrayList<>();

        for (Object[] r : rows) {
            Long pid = (Long) r[1];
            int rating = ((Number) r[3]).intValue();
            String comment = r[4] == null ? "" : ((String) r[4]).trim();
            sum += rating;
            if (rating >= 4) pos++; else if (rating == 3) neu++; else neg++;
            ProductStat ps = products.computeIfAbsent(pid, k -> new ProductStat(pid, (String) r[2]));
            ps.reviews++;
            ps.ratingSum += rating;
            if (comment.isEmpty()) continue;
            withComment++;
            if (samples.size() < 60) samples.add(rating + "★: " + comment);
            countBigrams(comment, bigrams);
            for (String clause : CLAUSES.split(comment)) {
                String c = clause.trim();
                if (c.isEmpty()) continue;
                String words = " " + TextNormalizer.normalize(c).replaceAll("[^a-z0-9]+", " ").trim() + " ";
                int score = sentiment(words);
                int sentiment = score != 0 ? Integer.signum(score) : rating >= 4 ? 1 : rating <= 2 ? -1 : 0;
                for (Aspect a : ASPECTS) {
                    if (a.words().stream().noneMatch(w -> words.contains(" " + w + " "))) continue;
                    Stat s = stats.get(a.key());
                    s.mentions++;
                    if (sentiment > 0) {
                        s.positive++;
                        if (s.praise.size() < 3 && c.length() >= 4) s.praise.add(quote(c));
                    } else if (sentiment < 0) {
                        s.negative++;
                        ps.negative++;
                        ps.complaints.merge(a.label(), 1, Integer::sum);
                        if (s.complaints.size() < 3 && c.length() >= 4) s.complaints.add(quote(c));
                    }
                }
            }
        }

        List<Map<String, Object>> aspects = stats.values().stream().filter(s -> s.mentions > 0)
                .sorted(Comparator.comparing((Stat s) -> s.mentions).reversed()).map(Stat::view).toList();
        List<Map<String, Object>> praises = stats.values().stream().filter(s -> s.positive > 0)
                .sorted(Comparator.comparing((Stat s) -> s.positive).reversed()).limit(3).map(Stat::view).toList();
        List<Map<String, Object>> complaints = stats.values().stream().filter(s -> s.negative > 0)
                .sorted(Comparator.comparing((Stat s) -> s.negative).reversed()).limit(3).map(Stat::view).toList();
        List<Map<String, Object>> keywords = bigrams.entrySet().stream().filter(e -> e.getValue() >= 2)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(12)
                .map(e -> SellerAIService.map("word", e.getKey(), "count", e.getValue())).toList();
        List<Map<String, Object>> worst = products.values().stream().filter(p -> p.negative > 0)
                .sorted(Comparator.comparing((ProductStat p) -> p.negative).reversed()).limit(8).map(ProductStat::view).toList();

        double avg = rows.isEmpty() ? 0 : Math.round(sum / rows.size() * 10) / 10.0;
        String rule = summary(rows.size(), withComment, d, avg, praises, complaints);
        String ai = rows.isEmpty() ? null : aiService.completeOrNull(
                "Bạn là chuyên gia phân tích trải nghiệm khách hàng cho người bán trên sàn thương mại điện tử. Dựa trên thống kê và các đánh giá, "
                        + "viết 4-6 câu tiếng Việt: khách khen gì, chê gì, và 2 hành động cụ thể shop nên làm. Không bịa số liệu.",
                "Thống kê: " + rule + "\nKhía cạnh: " + aspects.stream().map(a -> a.get("label") + " +" + a.get("positive") + "/-" + a.get("negative"))
                        .collect(Collectors.joining("; ")) + "\nĐánh giá:\n" + String.join("\n", samples));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("days", d);
        out.put("productId", productId);
        out.put("productName", productId != null && !products.isEmpty() ? products.values().iterator().next().name : null);
        out.put("reviews", rows.size());
        out.put("withComment", withComment);
        out.put("average", avg);
        out.put("sentiment", SellerAIService.map("positive", pos, "neutral", neu, "negative", neg));
        out.put("summary", ai != null ? ai : rule);
        out.put("source", ai != null ? "ai" : "rule");
        out.put("aspects", aspects);
        out.put("praises", praises);
        out.put("complaints", complaints);
        out.put("keywords", keywords);
        out.put("products", worst);
        return out;
    }

    /** > 0 positive, < 0 negative; "khong" before a word flips it ("khong dep" -1, "khong bi loi" +1). */
    static int sentiment(String words) {
        int score = 0;
        for (String p : POSITIVE) score += occurrences(words, p) - 2 * negated(words, p);
        for (String n : NEGATIVE) score -= occurrences(words, n) - 2 * negated(words, n);
        return score;
    }

    private static int occurrences(String words, String phrase) {
        int n = 0;
        for (int i = words.indexOf(" " + phrase + " "); i >= 0; i = words.indexOf(" " + phrase + " ", i + 1)) n++;
        return n;
    }

    private static int negated(String words, String phrase) {
        int n = 0;
        for (String neg : NEGATIONS) {
            n += occurrences(words, neg + " " + phrase) + occurrences(words, neg + " bi " + phrase) + occurrences(words, neg + " he " + phrase);
        }
        return n;
    }

    private static void countBigrams(String comment, Map<String, Integer> counts) {
        for (String clause : CLAUSES.split(comment.toLowerCase())) {
            String[] w = clause.trim().split("[^\\p{L}\\p{N}]+");
            for (int i = 0; i + 1 < w.length; i++) {
                if (w[i].length() < 2 || w[i + 1].length() < 2 || FILLER.contains(w[i]) || FILLER.contains(w[i + 1])) continue;
                counts.merge(w[i] + " " + w[i + 1], 1, Integer::sum);
            }
        }
    }

    private static String summary(int total, int withComment, int days, double avg, List<Map<String, Object>> praises,
                                  List<Map<String, Object>> complaints) {
        if (total == 0) return "Chưa có đánh giá nào trong " + days + " ngày qua.";
        StringBuilder sb = new StringBuilder("Phân tích " + total + " đánh giá (" + withComment + " có nội dung) trong " + days
                + " ngày qua, điểm trung bình " + avg + "★. ");
        if (!praises.isEmpty()) {
            sb.append("Khách hàng khen nhiều nhất về ").append(lower(praises.get(0))).append(" (").append(praises.get(0).get("positive")).append(" lượt)");
            if (praises.size() > 1) sb.append(" và ").append(lower(praises.get(1))).append(" (").append(praises.get(1).get("positive")).append(" lượt)");
            sb.append(". ");
        }
        if (complaints.isEmpty()) {
            sb.append("Chưa có phàn nàn đáng kể – hãy duy trì chất lượng hiện tại.");
        } else {
            Map<String, Object> c = complaints.get(0);
            @SuppressWarnings("unchecked") List<String> q = (List<String>) c.get("complaints");
            sb.append("Điểm bị phàn nàn nhiều nhất là ").append(lower(c)).append(" (").append(c.get("negative")).append(" lượt")
                    .append(q.isEmpty() ? "" : ", ví dụ: “" + q.get(0) + "”").append("). Gợi ý: ").append(c.get("action")).append(".");
        }
        return sb.toString();
    }

    private static String lower(Map<String, Object> aspect) {
        String s = (String) aspect.get("label");
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static String quote(String s) {
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() <= 90 ? t : t.substring(0, 87) + "...";
    }

    private static final class Stat {
        final Aspect aspect;
        int mentions, positive, negative;
        final List<String> praise = new ArrayList<>(), complaints = new ArrayList<>();

        Stat(Aspect a) {
            this.aspect = a;
        }

        Map<String, Object> view() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", aspect.key());
            m.put("label", aspect.label());
            m.put("mentions", mentions);
            m.put("positive", positive);
            m.put("negative", negative);
            m.put("positiveRate", mentions == 0 ? 0 : Math.round(positive * 1000.0 / mentions) / 10.0);
            m.put("praise", praise);
            m.put("complaints", complaints);
            m.put("action", aspect.action());
            return m;
        }
    }

    private static final class ProductStat {
        final Long id;
        final String name;
        int reviews, ratingSum, negative;
        final Map<String, Integer> complaints = new HashMap<>();

        ProductStat(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        Map<String, Object> view() {
            String top = complaints.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
            return SellerAIService.map("id", id, "name", name, "reviews", reviews,
                    "average", Math.round(ratingSum * 10.0 / Math.max(1, reviews)) / 10.0, "negativeMentions", negative, "topComplaint", top);
        }
    }
}
