package com.marketplace.backend.service;

import com.marketplace.backend.entity.Category;
import com.marketplace.backend.entity.Product;
import com.marketplace.backend.entity.Shop;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.CategoryRepository;
import com.marketplace.backend.repository.ProductRepository;
import com.marketplace.backend.repository.ShopRepository;
import com.marketplace.backend.security.CurrentUser;
import com.marketplace.backend.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

// "AI danh cho Seller": product description drafts and a reference selling price.
// Both work without an LLM (templates / statistics over the marketplace); with one configured,
// the description is written by the model from the same facts.
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SellerAIService {

    // words that say nothing about what the product is
    static final Set<String> STOPWORDS = Set.of("cho", "va", "cua", "voi", "the", "loai", "hang", "chinh", "moi", "nam", "nu",
            "sale", "gia", "re", "dep", "cao", "cap", "freeship", "chat", "luong", "size", "mau", "co", "khong", "1", "2", "3",
            "set", "bo", "sieu", "hot", "trend", "new", "phong", "cach", "kieu", "in");

    // material -> what it means for the buyer, and how to care for it
    private static final Map<String, String[]> MATERIALS = new LinkedHashMap<>();

    static {
        MATERIALS.put("cotton", new String[]{"mềm mại, thấm hút mồ hôi tốt, thoáng mát cả ngày", "Giặt ở nhiệt độ thường, lộn trái khi phơi để giữ màu"});
        MATERIALS.put("linen", new String[]{"nhẹ, thoáng khí, mát mẻ trong ngày nắng nóng", "Giặt tay hoặc chế độ nhẹ, là ở nhiệt độ trung bình"});
        MATERIALS.put("lanh", new String[]{"nhẹ, thoáng khí, mát mẻ trong ngày nắng nóng", "Giặt tay hoặc chế độ nhẹ, là ở nhiệt độ trung bình"});
        MATERIALS.put("kaki", new String[]{"đứng form, bền chắc, ít nhăn", "Giặt máy bình thường, không dùng chất tẩy mạnh"});
        MATERIALS.put("jean", new String[]{"bền chắc, càng mặc càng đẹp", "Lộn trái khi giặt, hạn chế sấy nóng để không phai màu"});
        MATERIALS.put("denim", new String[]{"bền chắc, càng mặc càng đẹp", "Lộn trái khi giặt, hạn chế sấy nóng để không phai màu"});
        MATERIALS.put("len", new String[]{"ấm áp, mềm mịn, giữ nhiệt tốt", "Giặt tay bằng nước lạnh, phơi ngang để không giãn"});
        MATERIALS.put("lua", new String[]{"mềm mượt, sang trọng, rủ nhẹ", "Giặt tay nhẹ nhàng, phơi nơi thoáng mát tránh nắng gắt"});
        MATERIALS.put("polyester", new String[]{"bền màu, ít nhăn, nhanh khô", "Giặt máy chế độ thường, không là nhiệt độ cao"});
        MATERIALS.put("thun", new String[]{"co giãn thoải mái, ôm vừa vặn", "Giặt nhẹ, tránh vắt mạnh để giữ độ đàn hồi"});
        MATERIALS.put("da", new String[]{"bền đẹp, sang trọng, càng dùng càng lên màu", "Lau bằng khăn mềm, tránh nước và để nơi khô ráo"});
        MATERIALS.put("canvas", new String[]{"dày dặn, chắc chắn, chịu lực tốt", "Giặt tay bằng nước lạnh, không ngâm lâu"});
        MATERIALS.put("inox", new String[]{"chống gỉ, bền bỉ, an toàn khi sử dụng", "Rửa bằng nước ấm, lau khô sau khi dùng"});
        MATERIALS.put("go", new String[]{"tự nhiên, chắc chắn, thân thiện môi trường", "Tránh ngâm nước, lau bằng khăn ẩm"});
        MATERIALS.put("nhua", new String[]{"nhẹ, bền, dễ vệ sinh", "Rửa bằng nước và xà phòng nhẹ, tránh nguồn nhiệt cao"});
        MATERIALS.put("silicon", new String[]{"mềm dẻo, an toàn, chịu nhiệt tốt", "Rửa bằng nước ấm và xà phòng"});
        MATERIALS.put("gom", new String[]{"tinh tế, giữ nhiệt tốt", "Rửa nhẹ tay, tránh va đập mạnh"});
    }

    private final AIService aiService;
    private final ShopRepository shopRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    // ==================== 1. product description ====================

    /**
     * tone: friendly (default, with emoji), professional (no emoji), short (a few lines).
     * Returns { reply: description, titleSuggestion, source: ai | rule }.
     */
    public Map<String, String> describe(String name, String material, String usage, String targetUser, String highlights,
                                        Long categoryId, String tone, List<String> variants) {
        if (name == null || name.isBlank()) throw new ApiException("Vui lòng nhập tên sản phẩm", HttpStatus.BAD_REQUEST);
        Shop shop = myShop();
        String category = categoryId == null ? null : categoryRepository.findById(categoryId).map(Category::getName).orElse(null);
        String t = tone == null ? "friendly" : tone.toLowerCase();
        List<String> vs = variants == null ? List.of() : variants.stream().filter(v -> v != null && !v.isBlank()).map(String::trim).distinct().limit(12).toList();
        List<String> hl = highlights == null ? List.of() : Arrays.stream(highlights.split("[\\n;]+")).map(String::trim)
                .map(s -> s.replaceFirst("^[-•*+]\\s*", "")).filter(s -> !s.isEmpty()).limit(6).toList();
        String title = title(name, material, targetUser, usage);

        String facts = "Tên sản phẩm: " + name.trim()
                + "\nNgành hàng: " + dash(category)
                + "\nChất liệu: " + dash(material)
                + "\nCông dụng: " + dash(usage)
                + "\nĐối tượng sử dụng: " + dash(targetUser)
                + "\nĐiểm nổi bật do người bán cung cấp: " + (hl.isEmpty() ? "-" : String.join("; ", hl))
                + "\nPhân loại: " + (vs.isEmpty() ? "-" : String.join(", ", vs))
                + "\nĐổi trả: " + shop.getDefaultReturnDays() + " ngày; chuẩn bị hàng " + shop.getHandlingDays() + " ngày; gửi từ " + dash(shop.getProvince());
        String style = switch (t) {
            case "professional" -> "giọng văn chuyên nghiệp, rõ ràng, không dùng emoji, dài khoảng 150-200 từ";
            case "short" -> "ngắn gọn khoảng 60-80 từ, có 3 gạch đầu dòng";
            default -> "giọng văn thân thiện, thu hút, có emoji ở tiêu đề mục, dài khoảng 150-200 từ";
        };
        String ai = aiService.completeOrNull(
                "Bạn là trợ lý viết mô tả sản phẩm cho người bán trên sàn thương mại điện tử Việt Nam. Viết mô tả bằng tiếng Việt, "
                        + style + ", chuẩn SEO, chia mục: giới thiệu, điểm nổi bật (gạch đầu dòng), thông tin sản phẩm, chính sách. "
                        + "Chỉ dùng thông tin được cung cấp, không bịa thông số. Chỉ trả về đoạn mô tả.",
                facts);
        String text = ai != null ? ai : ruleDescription(name.trim(), material, usage, targetUser, hl, category, vs, shop, t);
        Map<String, String> out = new LinkedHashMap<>();
        out.put("reply", text);
        out.put("titleSuggestion", title);
        out.put("source", ai != null ? "ai" : "rule");
        return out;
    }

    String ruleDescription(String name, String material, String usage, String targetUser, List<String> highlights,
                           String category, List<String> variants, Shop shop, String tone) {
        boolean emoji = !"professional".equals(tone);
        String[] mat = materialInfo(material);
        StringBuilder sb = new StringBuilder();

        // intro
        sb.append(name);
        List<String> intro = new ArrayList<>();
        if (!blank(material)) intro.add("làm từ " + material.trim() + (mat != null ? " " + mat[0] : ""));
        if (!blank(usage)) intro.add("phù hợp " + lowerFirst(usage.trim()));
        if (!blank(targetUser)) intro.add("dành cho " + lowerFirst(targetUser.trim()));
        sb.append(intro.isEmpty() ? " là lựa chọn đáng tin cậy với chất lượng ổn định và giá hợp lý." : " – " + String.join(", ", intro) + ".");
        sb.append("\n\n");

        // highlights
        List<String> points = new ArrayList<>();
        if (mat != null) points.add("Chất liệu " + material.trim() + ": " + mat[0]);
        else if (!blank(material)) points.add("Chất liệu " + material.trim() + " được chọn lọc kỹ");
        points.addAll(highlights);
        if (!blank(usage)) points.add("Công dụng: " + usage.trim());
        if (!blank(targetUser)) points.add("Thiết kế dành cho " + lowerFirst(targetUser.trim()));
        if (points.isEmpty()) points.add("Hoàn thiện tỉ mỉ, kiểm tra kỹ trước khi giao");

        if ("short".equals(tone)) {
            for (String p : points.subList(0, Math.min(3, points.size()))) sb.append("• ").append(p).append("\n");
            sb.append("• Đổi trả trong ").append(shop.getDefaultReturnDays()).append(" ngày nếu có lỗi từ nhà sản xuất");
            return sb.toString().trim();
        }

        sb.append(emoji ? "✨ ĐIỂM NỔI BẬT\n" : "ĐIỂM NỔI BẬT\n");
        for (String p : points) sb.append("• ").append(p).append("\n");

        sb.append(emoji ? "\n📏 THÔNG TIN SẢN PHẨM\n" : "\nTHÔNG TIN SẢN PHẨM\n");
        sb.append("• Tên sản phẩm: ").append(name).append("\n");
        if (category != null) sb.append("• Ngành hàng: ").append(category).append("\n");
        if (!blank(material)) sb.append("• Chất liệu: ").append(material.trim()).append("\n");
        if (!variants.isEmpty()) sb.append("• Phân loại: ").append(String.join(", ", variants)).append("\n");
        if (mat != null) sb.append("• Bảo quản: ").append(mat[1]).append("\n");

        sb.append(emoji ? "\n📦 CHÍNH SÁCH CỦA SHOP\n" : "\nCHÍNH SÁCH CỦA SHOP\n");
        Integer days = shop.getDefaultReturnDays();
        if (days != null && days > 0) sb.append("• Đổi trả trong ").append(days).append(" ngày nếu sản phẩm lỗi hoặc không đúng mô tả\n");
        sb.append("• Chuẩn bị hàng trong ").append(shop.getHandlingDays()).append(" ngày")
                .append(shop.getProvince() != null ? ", gửi từ " + shop.getProvince() : "").append("\n");
        sb.append("• Hình ảnh và mô tả đúng với sản phẩm thực tế\n");
        if (emoji) sb.append("\n💬 Bạn cần tư vấn thêm, hãy nhắn tin cho shop nhé!");

        String tags = hashtags(name, material);
        if (!tags.isEmpty()) sb.append("\n\n").append(tags);
        return sb.toString().trim();
    }

    // "Ao thun nam cotton – mac hang ngay": what buyers type in the search box, max 120 chars
    static String title(String name, String material, String targetUser, String usage) {
        String n = name.trim();
        String norm = TextNormalizer.normalize(n);
        StringBuilder t = new StringBuilder(n);
        if (!blank(material) && !norm.contains(TextNormalizer.normalize(material.trim()))) t.append(" ").append(material.trim());
        if (!blank(targetUser) && targetUser.trim().length() <= 20 && !norm.contains(TextNormalizer.normalize(targetUser.trim()))) {
            t.append(" ").append(lowerFirst(targetUser.trim()));
        }
        if (!blank(usage) && usage.trim().length() <= 30) t.append(" – ").append(lowerFirst(usage.trim()));
        String s = t.toString();
        return s.length() <= 120 ? s : s.substring(0, 117).trim() + "...";
    }

    private static String hashtags(String name, String material) {
        List<String> words = new ArrayList<>(tokens(name));
        if (!blank(material)) words.addAll(tokens(material));
        return words.stream().distinct().limit(5).map(w -> "#" + w).collect(Collectors.joining(" "));
    }

    private static String[] materialInfo(String material) {
        if (blank(material)) return null;
        String m = " " + TextNormalizer.normalize(material).replaceAll("[^a-z0-9]+", " ") + " ";
        for (Map.Entry<String, String[]> e : MATERIALS.entrySet()) {
            if (m.contains(" " + e.getKey() + " ")) return e.getValue();
        }
        return null;
    }

    // ==================== 2. reference price ====================

    /**
     * Price of comparable products of other shops: same category (and its siblings) plus products whose
     * name shares words with this one, scored by name similarity.
     */
    public Map<String, Object> suggestPrice(String name, Long categoryId, BigDecimal cost) {
        if (name == null || name.isBlank()) throw new ApiException("Vui lòng nhập tên sản phẩm", HttpStatus.BAD_REQUEST);
        Shop shop = myShop();
        Set<String> words = tokens(name);
        Category category = categoryId == null ? null : categoryRepository.findById(categoryId).orElse(null);

        Map<Long, Product> candidates = new LinkedHashMap<>();
        Set<Long> catIds = new HashSet<>();
        if (category != null) {
            catIds.add(category.getId());
            Long parentId = category.getParent() != null ? category.getParent().getId() : category.getId();
            catIds.add(parentId);
            categoryRepository.findByParentId(parentId).forEach(c -> catIds.add(c.getId()));
            productRepository.comparablesInCategories(shop.getId(), catIds).forEach(p -> candidates.put(p.getId(), p));
        }
        words.stream().sorted(Comparator.comparing(String::length).reversed()).limit(3).forEach(w ->
                productRepository.comparablesByWord(shop.getId(), "%" + w + "%", PageRequest.of(0, 120))
                        .forEach(p -> candidates.putIfAbsent(p.getId(), p)));

        record Scored(Product p, double score) { }
        List<Scored> scored = new ArrayList<>();
        for (Product p : candidates.values()) {
            Set<String> other = tokens(p.getName());
            double score = jaccard(words, other);
            if (category != null && p.getCategory() != null) {
                if (p.getCategory().getId().equals(category.getId())) score += 0.3;
                else if (catIds.contains(p.getCategory().getId())) score += 0.1;
            }
            scored.add(new Scored(p, score));
        }
        scored.sort(Comparator.comparing(Scored::score).reversed());
        List<Scored> similar = scored.stream().filter(s -> s.score() >= 0.4).limit(30).toList();
        String basis = "similar";
        if (similar.size() < 3) {
            // not enough look-alikes: the whole category (if any) tells the usual price level
            similar = scored.stream().filter(s -> s.score() >= 0.3 || (category != null && s.score() >= 0.1)).limit(40).toList();
            basis = "category";
        }
        if (similar.size() < 3) {
            Map<String, Object> none = new LinkedHashMap<>();
            none.put("count", similar.size());
            none.put("message", "Chưa đủ sản phẩm tương tự trên sàn để gợi ý giá. Hãy chọn ngành hàng hoặc đặt tên sản phẩm cụ thể hơn.");
            return none;
        }

        List<Double> prices = similar.stream().map(s -> s.p().getEffectivePrice().doubleValue()).sorted().toList();
        double p25 = quantile(prices, 0.25), median = quantile(prices, 0.5), p75 = quantile(prices, 0.75);
        // price of what actually sells: median over the best-selling third
        List<Scored> bySold = similar.stream().sorted(Comparator.comparing((Scored s) -> s.p().getSoldCount()).reversed()).toList();
        List<Double> topPrices = bySold.subList(0, Math.max(1, bySold.size() / 3)).stream()
                .map(s -> s.p().getEffectivePrice().doubleValue()).sorted().toList();
        double bestSellerMedian = quantile(topPrices, 0.5);
        double target = Math.min(Math.max(bestSellerMedian, p25), p75);
        long suggested = charm(target);

        List<String> notes = new ArrayList<>();
        notes.add("Phần lớn sản phẩm tương tự có giá từ " + vnd(p25) + " đến " + vnd(p75) + ", trung vị " + vnd(median) + ".");
        if (Math.abs(bestSellerMedian - median) / Math.max(1, median) > 0.08) {
            notes.add("Nhóm bán chạy nhất có giá quanh " + vnd(bestSellerMedian)
                    + (bestSellerMedian < median ? " – người mua nhạy cảm với giá, nên định giá cạnh tranh." : " – người mua sẵn sàng trả cao hơn cho sản phẩm tốt."));
        }
        notes.add("Giá " + vnd(suggested) + " (kết thúc bằng 9.000) dễ tạo cảm giác rẻ hơn trong kết quả tìm kiếm.");
        Long minPrice = null;
        if (cost != null && cost.signum() > 0) {
            minPrice = charmUp(cost.doubleValue() * 1.25);
            if (minPrice > suggested) {
                notes.add("Với giá vốn " + vnd(cost.doubleValue()) + ", để có lãi tối thiểu 20% cần bán từ " + vnd(minPrice)
                        + " – cao hơn mặt bằng; hãy nhấn mạnh điểm khác biệt (chất liệu, bảo hành, quà tặng).");
            } else {
                notes.add("Với giá vốn " + vnd(cost.doubleValue()) + ", giá đề xuất cho biên lợi nhuận khoảng "
                        + Math.round((suggested - cost.doubleValue()) * 100 / suggested) + "%.");
            }
        }

        // 5-bucket histogram between min and max
        double min = prices.get(0), max = prices.get(prices.size() - 1);
        List<Map<String, Object>> buckets = new ArrayList<>();
        double width = Math.max(1, (max - min) / 5);
        for (int i = 0; i < 5; i++) {
            double lo = min + i * width, hi = i == 4 ? max : lo + width;
            final int last = i;
            long n = prices.stream().filter(v -> v >= lo && (last == 4 ? v <= hi : v < hi)).count();
            buckets.add(map("from", Math.round(lo), "to", Math.round(hi), "count", n));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("count", similar.size());
        out.put("basis", basis);
        out.put("min", Math.round(min));
        out.put("p25", Math.round(p25));
        out.put("median", Math.round(median));
        out.put("p75", Math.round(p75));
        out.put("max", Math.round(max));
        out.put("bestSellerMedian", Math.round(bestSellerMedian));
        out.put("suggested", suggested);
        out.put("minProfitablePrice", minPrice);
        out.put("notes", notes);
        out.put("histogram", buckets);
        out.put("comparables", similar.stream().limit(8).map(s -> map(
                "id", s.p().getId(), "name", s.p().getName(), "shopName", s.p().getShop().getName(),
                "price", s.p().getEffectivePrice(), "soldCount", s.p().getSoldCount(),
                "ratingAvg", s.p().getRatingAvg(), "thumbnailUrl", s.p().getThumbnailUrl(),
                "similarity", Math.round(Math.min(1, s.score()) * 100))).toList());
        return out;
    }

    // ==================== helpers ====================

    /** Accent-free meaningful words of a product name. */
    static Set<String> tokens(String text) {
        if (text == null) return Set.of();
        return Arrays.stream(TextNormalizer.normalize(text).split("[^a-z0-9]+"))
                .filter(w -> w.length() >= 2 && !STOPWORDS.contains(w) && !w.matches("\\d+"))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        long common = a.stream().filter(b::contains).count();
        return (double) common / (a.size() + b.size() - common);
    }

    static double quantile(List<Double> sorted, double q) {
        if (sorted.isEmpty()) return 0;
        double pos = q * (sorted.size() - 1);
        int lo = (int) Math.floor(pos), hi = (int) Math.ceil(pos);
        return sorted.get(lo) + (sorted.get(hi) - sorted.get(lo)) * (pos - lo);
    }

    // 253.400 -> 249.000 ; 96.000 -> 99.000 (nearest "...9.000")
    static long charm(double price) {
        if (price < 20_000) return Math.max(1_000, Math.round(price / 1_000) * 1_000);
        return Math.max(9_000, Math.round(price / 10_000) * 10_000 - 1_000);
    }

    private static long charmUp(double price) {
        long c = charm(price);
        return c < price ? c + 10_000 : c;
    }

    static String vnd(double v) {
        return NotificationService.vnd(BigDecimal.valueOf(Math.round(v)));
    }

    private static String lowerFirst(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String dash(String s) {
        return blank(s) ? "-" : s.trim();
    }

    static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    Shop myShop() {
        return shopRepository.findBySellerId(CurrentUser.id())
                .orElseThrow(() -> new ApiException("Bạn chưa có shop", HttpStatus.BAD_REQUEST));
    }
}
