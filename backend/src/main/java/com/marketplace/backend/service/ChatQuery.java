package com.marketplace.backend.service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Reads a buyer's message the way they actually type it, before the chatbot's search (ChatbotService):
 * abbreviations and teen code ("đt", "kcn", "mk", "ko"), filler words of a change of mind ("đổi sang", "à thôi"),
 * typos (a word not in the catalogue becomes the closest catalogue word: "samsug" -> "samsung"), and small talk the
 * shop has nothing for ("thời tiết hôm nay"). Input and output are accent-free (TextNormalizer).
 * Found and tested with scripts/chatbot-eval.mjs.
 */
public final class ChatQuery {

    private ChatQuery() {}

    // one word -> what it stands for (accent-free). Kept to abbreviations that mean one thing in a shop.
    static final Map<String, String> ABBREVIATIONS = Map.ofEntries(
            Map.entry("dt", "dien thoai"), Map.entry("dthoai", "dien thoai"), Map.entry("dienthoai", "dien thoai"),
            Map.entry("tn", "tai nghe"), Map.entry("lap", "laptop"), Map.entry("ip", "iphone"), Map.entry("ss", "samsung"),
            Map.entry("dp", "du phong"), Map.entry("sdp", "sac du phong"), Map.entry("kcn", "kem chong nang"),
            Map.entry("srm", "sua rua mat"), Map.entry("mk", "minh"), Map.entry("mik", "minh"), Map.entry("mn", "moi nguoi"),
            Map.entry("ko", "khong"), Map.entry("k", "khong"), Map.entry("kh", "khong"), Map.entry("hok", "khong"),
            Map.entry("dc", "duoc"), Map.entry("dk", "duoc"),
            Map.entry("bn", "bao nhieu"), Map.entry("j", "gi"), Map.entry("z", "vay"), Map.entry("v", "vay"),
            Map.entry("ntn", "nhu the nao"), Map.entry("sp", "san pham"), Map.entry("tk", "tai khoan"),
            Map.entry("mng", "moi nguoi"), Map.entry("nv", "nhan vien"), Map.entry("sdt", "so dien thoai"));

    // "đh": an order when it is about delivery or the buyer's own, a watch otherwise ("đh nam dây da")
    private static final Pattern ORDER_CONTEXT = Pattern.compile("\\b(giao|chua|cua (minh|toi|em)|o dau|huy|ma)\\b");

    // phrases that only say "not that, this": "à thôi", "đổi sang", "ý mình là"
    private static final List<String> FILLERS = List.of("doi sang", "chuyen sang", "doi qua", "y minh la", "y toi la", "a ma",
            "khong phai", "nham roi", "nham", "cho minh xem", "cho xem", "xem thu", "tu van", "goi y");

    // small talk: nothing in the catalogue fits, whatever word happens to match
    private static final List<String> OFF_TOPIC = List.of("thoi tiet", "hom nay troi", "ban la ai", "ban ten", "may gio",
            "bong da", "tin tuc", "xo so", "ke chuyen", "lam tho", "ket qua");

    /** Abbreviations expanded, change-of-mind fillers removed. */
    public static String rewrite(String norm) {
        if (norm == null) return "";
        String[] words = norm.replaceAll("[^a-z0-9]+", " ").trim().split(" ");
        boolean aboutOrder = ORDER_CONTEXT.matcher(norm).find();
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            String r = w.equals("dh") ? (aboutOrder ? "don hang" : "dong ho") : ABBREVIATIONS.getOrDefault(w, w);
            sb.append(r).append(' ');
        }
        String s = " " + sb.toString().trim() + " ";
        for (String f : FILLERS) s = s.replace(" " + f + " ", " ");
        // "thôi" closes a change of mind ("à thôi", "5 triệu thôi"), except in "son thỏi"
        s = s.replaceAll("(?<!son) thoi(?= )", " ");
        return s.replaceAll("\\s+", " ").trim();
    }

    public static boolean offTopic(String norm) {
        String s = " " + norm + " ";
        return OFF_TOPIC.stream().anyMatch(p -> s.contains(" " + p + " "));
    }

    /**
     * vocabulary: a sorted set (TreeSet). Words not in the catalogue become the closest catalogue word (one edit for 4-6 letters, two from 7; swapping
     * two neighbours counts as one). Short words and `keep` (stop words, prices) are left alone.
     */
    public static String correct(String norm, Set<String> vocabulary, Set<String> keep) {
        if (vocabulary.isEmpty()) return norm;
        StringBuilder sb = new StringBuilder();
        for (String w : norm.split(" ")) {
            String out = w;
            if (w.length() >= 4 && !vocabulary.contains(w) && !keep.contains(w) && !w.matches(".*\\d.*")) {
                int limit = w.length() >= 7 ? 2 : 1, best = limit + 1;
                for (String v : vocabulary) {
                    // never into a short or stop word: "lanh" (tủ lạnh) must not become "anh"
                    if (v.length() < 4 || keep.contains(v) || Math.abs(v.length() - w.length()) > limit) continue;
                    int d = distance(w, v, limit);
                    if (d < best) {   // vocabulary sorted: ties go to the first word, the same every time
                        best = d;
                        out = v;
                    }
                }
                if (best > limit) out = w;
            }
            sb.append(out).append(' ');
        }
        return sb.toString().trim();
    }

    /** Edit distance with adjacent swaps (optimal string alignment); stops early above `limit`. */
    static int distance(String a, String b, int limit) {
        int n = a.length(), m = b.length();
        int[][] d = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) d[i][0] = i;
        for (int j = 0; j <= m; j++) d[0][j] = j;
        for (int i = 1; i <= n; i++) {
            int rowMin = Integer.MAX_VALUE;
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
                rowMin = Math.min(rowMin, d[i][j]);
            }
            if (rowMin > limit) return limit + 1;
        }
        return d[n][m];
    }

    /**
     * Does a product matching `matched` of the `words` the buyer typed answer the question? At least half of them
     * ("tủ lạnh samsung" is not a Samsung phone). relaxed: a follow-up on an earlier subject ("rẻ hơn 1tr"): one is enough.
     */
    public static boolean enough(int matched, int words, boolean relaxed) {
        return matched >= 1 && (relaxed || matched * 2 >= words);
    }
}
