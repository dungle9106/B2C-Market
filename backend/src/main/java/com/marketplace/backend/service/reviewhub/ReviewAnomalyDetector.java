package com.marketplace.backend.service.reviewhub;

import com.marketplace.backend.entity.ReviewHubItem;
import com.marketplace.backend.entity.ReviewHubItem.AnomalyStatus;
import com.marketplace.backend.util.TextNormalizer;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Signs that reviews are not genuine (spam, paid reviews, a competitor's attack), computed over a shop's recent
 * reviews together. Each signal adds to a 0-100 score; 40 or more flags the review for the seller to check.
 * The seller's decision ("bình thường" / "đã báo cáo") is never overwritten by a later run.
 */
public final class ReviewAnomalyDetector {

    private ReviewAnomalyDetector() {
    }

    public static final int FLAG_AT = 40;

    public static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put("DUPLICATE", "Nội dung gần như trùng với đánh giá khác");
        LABELS.put("COORDINATED_NEGATIVE", "Nhiều đánh giá xấu dồn dập cho cùng sản phẩm (nghi bị chơi xấu)");
        LABELS.put("BURST", "Số đánh giá tăng đột biến");
        LABELS.put("SERIAL_REVIEWER", "Một người đánh giá liên tục nhiều lần");
        LABELS.put("RATING_MISMATCH", "Số sao ngược với nội dung");
        LABELS.put("AI_SUSPECT", "AI nhận định đáng ngờ");
        LABELS.put("LOW_EFFORT", "Cực đoan nhưng gần như không có nội dung");
        LABELS.put("NO_ORDER", "Đánh giá xấu không có mã đơn hàng");
    }

    private static final Map<String, Integer> WEIGHT = Map.of("DUPLICATE", 40, "COORDINATED_NEGATIVE", 35, "BURST", 30,
            "SERIAL_REVIEWER", 30, "RATING_MISMATCH", 25, "AI_SUSPECT", 25, "LOW_EFFORT", 10, "NO_ORDER", 10);

    public static String productKey(ReviewHubItem i) {
        if (i.getProductId() != null) return "p" + i.getProductId();
        return "n" + Objects.requireNonNullElse(TextNormalizer.normalize(i.getProductName()), "");
    }

    /** Recomputes the anomaly fields of every item (except those the seller already decided on). */
    public static void run(List<ReviewHubItem> items) {
        Map<Long, LinkedHashSet<String>> flags = new HashMap<>();
        Map<Long, List<String>> notes = new HashMap<>();
        items.forEach(i -> {
            LinkedHashSet<String> f = new LinkedHashSet<>();
            List<String> n = new ArrayList<>();
            // the LLM's suspicion (and its reason) survives a re-run
            if (i.getAnomalyFlags() != null && i.getAnomalyFlags().contains("AI_SUSPECT")) {
                f.add("AI_SUSPECT");
                String kept = i.getAnomalyNote() == null ? null : Arrays.stream(i.getAnomalyNote().split(" · "))
                        .filter(s -> s.startsWith("AI: ")).findFirst().orElse(null);
                n.add(kept != null ? kept : LABELS.get("AI_SUSPECT"));
            }
            flags.put(i.getId(), f);
            notes.put(i.getId(), n);
        });
        Map<Long, ReviewTextAnalyzer.Result> text = new HashMap<>();
        items.forEach(i -> text.put(i.getId(), ReviewTextAnalyzer.analyze(i.getRating(), i.getContent())));

        // ---- duplicate text from different reviewers ----
        Map<Long, Set<String>> shingles = new HashMap<>();
        for (ReviewHubItem i : items) {
            Set<String> s = shingles(i.getContent());
            if (s.size() >= 4) shingles.put(i.getId(), s);
        }
        List<ReviewHubItem> withText = items.stream().filter(i -> shingles.containsKey(i.getId())).toList();
        for (int a = 0; a < withText.size(); a++) {
            for (int b = a + 1; b < withText.size(); b++) {
                ReviewHubItem x = withText.get(a), y = withText.get(b);
                if (Objects.equals(x.getReviewerKey(), y.getReviewerKey()) && x.getReviewerKey() != null) continue;
                if (jaccard(shingles.get(x.getId()), shingles.get(y.getId())) >= 0.8) {
                    mark(flags, notes, x, "DUPLICATE", "Trùng nội dung với đánh giá của " + name(y) + " (" + day(y.getReviewedAt()) + ")");
                    mark(flags, notes, y, "DUPLICATE", "Trùng nội dung với đánh giá của " + name(x) + " (" + day(x.getReviewedAt()) + ")");
                }
            }
        }

        // ---- per product: bursts and coordinated bad reviews ----
        Map<String, List<ReviewHubItem>> byProduct = items.stream().collect(Collectors.groupingBy(ReviewAnomalyDetector::productKey));
        for (List<ReviewHubItem> list : byProduct.values()) {
            list.sort(Comparator.comparing(ReviewHubItem::getReviewedAt));
            for (int s = 0; s < list.size(); s++) {
                LocalDateTime start = list.get(s).getReviewedAt();
                int e = s;
                while (e + 1 < list.size() && Duration.between(start, list.get(e + 1).getReviewedAt()).toHours() < 24) e++;
                List<ReviewHubItem> window = list.subList(s, e + 1);
                // burst: 5+ in a day, 4x the usual daily rate of the 30 days before
                long before = list.stream().filter(i -> i.getReviewedAt().isBefore(start) && i.getReviewedAt().isAfter(start.minusDays(30))).count();
                double usual = before / 30.0;
                if (window.size() >= 5 && window.size() >= 4 * Math.max(usual, 0.25)) {
                    for (ReviewHubItem i : window) {
                        mark(flags, notes, i, "BURST", window.size() + " đánh giá trong 24 giờ (bình thường khoảng "
                                + String.format(Locale.ROOT, "%.1f", usual) + "/ngày)");
                    }
                }
                // coordinated: 3+ bad reviews in a day from different people, short or copied, no photos
                List<ReviewHubItem> bad = window.stream().filter(i -> i.getRating() <= 2 && i.getMediaCount() == 0
                        && (text.get(i.getId()).words() <= 8 || flags.get(i.getId()).contains("DUPLICATE"))).toList();
                long people = bad.stream().map(i -> Objects.requireNonNullElse(i.getReviewerKey(), "#" + i.getId())).distinct().count();
                if (bad.size() >= 3 && people >= 3) {
                    for (ReviewHubItem i : bad) {
                        mark(flags, notes, i, "COORDINATED_NEGATIVE", bad.size() + " đánh giá 1-2 sao ngắn, không ảnh trong 24 giờ cho sản phẩm này");
                    }
                }
            }
        }

        // ---- one reviewer, many reviews in a week, all at an extreme ----
        Map<String, List<ReviewHubItem>> byReviewer = items.stream().filter(i -> i.getReviewerKey() != null)
                .collect(Collectors.groupingBy(ReviewHubItem::getReviewerKey));
        for (List<ReviewHubItem> list : byReviewer.values()) {
            if (list.size() < 3) continue;
            list.sort(Comparator.comparing(ReviewHubItem::getReviewedAt));
            for (int s = 0; s + 2 < list.size(); s++) {
                List<ReviewHubItem> w = list.subList(s, s + 3);
                boolean week = Duration.between(w.get(0).getReviewedAt(), w.get(2).getReviewedAt()).toDays() < 7;
                boolean extreme = w.stream().allMatch(i -> i.getRating() == 5) || w.stream().allMatch(i -> i.getRating() == 1);
                if (week && extreme) {
                    for (ReviewHubItem i : w) mark(flags, notes, i, "SERIAL_REVIEWER", name(i) + " viết " + list.size() + " đánh giá "
                            + i.getRating() + " sao trong thời gian ngắn");
                }
            }
        }

        // ---- single review signals ----
        for (ReviewHubItem i : items) {
            ReviewTextAnalyzer.Result t = text.get(i.getId());
            if (t.words() >= 3 && ((i.getRating() <= 2 && t.textPolarity() >= 0.6) || (i.getRating() == 5 && t.textPolarity() <= -0.6))) {
                mark(flags, notes, i, "RATING_MISMATCH", i.getRating() + " sao nhưng nội dung " + (i.getRating() <= 2 ? "khen" : "chê"));
            }
            if ((i.getRating() == 1 || i.getRating() == 5) && t.words() <= 3 && i.getMediaCount() == 0) {
                mark(flags, notes, i, "LOW_EFFORT", null);
            }
            if (i.getSource() != ReviewHubItem.Source.SHOPPI && (i.getOrderRef() == null || i.getOrderRef().isBlank()) && i.getRating() <= 2) {
                mark(flags, notes, i, "NO_ORDER", null);
            }
        }

        for (ReviewHubItem i : items) {
            if (i.getAnomalyStatus() == AnomalyStatus.CONFIRMED_OK || i.getAnomalyStatus() == AnomalyStatus.REPORTED) continue;
            Set<String> f = flags.get(i.getId());
            int score = Math.min(100, f.stream().mapToInt(k -> WEIGHT.getOrDefault(k, 0)).sum());
            i.setAnomalyScore(score);
            i.setAnomalyFlags(f.isEmpty() ? null : String.join(",", f));
            List<String> n = notes.get(i.getId());
            i.setAnomalyNote(n.isEmpty() ? null : ReviewTextAnalyzer.cut(String.join(" · ", n), 600));
            i.setAnomalyStatus(score >= FLAG_AT ? AnomalyStatus.FLAGGED : AnomalyStatus.NONE);
        }
    }

    private static void mark(Map<Long, LinkedHashSet<String>> flags, Map<Long, List<String>> notes, ReviewHubItem i, String flag, String note) {
        if (flags.get(i.getId()).add(flag)) notes.get(i.getId()).add(note != null ? note : LABELS.get(flag));
    }

    static Set<String> shingles(String content) {
        String n = TextNormalizer.normalize(content == null ? "" : content).replaceAll("[^a-z0-9 ]", " ").replaceAll(" +", " ").trim();
        if (n.isEmpty()) return Set.of();
        String[] w = n.split(" ");
        if (w.length < 6) return Set.of();
        Set<String> s = new HashSet<>();
        for (int k = 0; k + 2 < w.length; k++) s.add(w[k] + " " + w[k + 1] + " " + w[k + 2]);
        return s;
    }

    static double jaccard(Set<String> a, Set<String> b) {
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        int union = a.size() + b.size() - inter.size();
        return union == 0 ? 0 : inter.size() / (double) union;
    }

    private static String name(ReviewHubItem i) {
        return i.getReviewerName() != null ? i.getReviewerName() : "người mua ẩn danh";
    }

    private static String day(LocalDateTime t) {
        return String.format("%02d/%02d", t.getDayOfMonth(), t.getMonthValue());
    }
}
