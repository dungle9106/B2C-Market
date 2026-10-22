package com.marketplace.backend.service.reviewhub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.backend.entity.ReviewHubItem;
import com.marketplace.backend.service.AIService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;

/**
 * The AI side of the review hub:
 *  - classify(): the LLM (OpenAI or Claude, whichever the admin chose) reads reviews in batches and returns their
 *    sentiment, issues, a one-line summary and whether a review looks fake;
 *  - draft(): a reply written for the situation (issue, stars, marketplace), for the seller to approve;
 *  - rootCause(): what a cluster of similar complaints says about the product, and what to fix;
 *  - the optional FastAPI service (REVIEW_AI_URL, folder review-ai-service/): fast local classification without
 *    LLM cost, and a model the platform can train on the labelled reviews.
 * Every method falls back to rules / templates when nothing is configured or a call fails.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewAiService {

    private static final ObjectMapper JSON = new ObjectMapper();
    public static final int SHOPPI_REPLY_MAX = 255;   // ReviewService.REPLY_MAX

    private final AIService aiService;
    private final WebClient webClient;

    @Value("${app.review-ai.url:}")
    private String serviceUrl;

    public boolean llmAvailable() {
        return aiService.isConfigured();
    }

    public boolean serviceAvailable() {
        return serviceUrl != null && !serviceUrl.isBlank();
    }

    // ==================== classification ====================

    public record Classified(String sentiment, Double score, List<String> issues, String summary, boolean suspicious, String suspiciousReason) {
    }

    /** LLM classification of a batch; empty map when AI is unavailable or the answer can't be read. */
    public Map<Long, Classified> classify(List<ReviewHubItem> items) {
        if (items.isEmpty() || !llmAvailable()) return Map.of();
        StringBuilder user = new StringBuilder("Các đánh giá cần phân loại (JSON):\n[");
        for (int k = 0; k < items.size(); k++) {
            ReviewHubItem i = items.get(k);
            if (k > 0) user.append(",\n");
            user.append(JSON.createObjectNode().put("id", i.getId()).put("stars", i.getRating())
                    .put("product", Objects.requireNonNullElse(i.getProductName(), "")).put("text", Objects.requireNonNullElse(i.getContent(), "")));
        }
        user.append("]");
        String system = """
                Bạn là chuyên viên chăm sóc khách hàng của một gian hàng thương mại điện tử. Nhiệm vụ: PHÂN LOẠI ĐÁNH GIÁ.
                Với mỗi đánh giá, trả về đúng một mảng JSON (không giải thích, không markdown), mỗi phần tử:
                {"id": <id>, "sentiment": "POSITIVE|NEUTRAL|NEGATIVE", "score": <số từ -1 đến 1>,
                 "issues": [<0 hoặc nhiều mã>], "summary": "<một câu ngắn tiếng Việt: khách phàn nàn/khen điều gì>",
                 "suspicious": <true|false>, "suspicious_reason": "<lý do ngắn nếu nghi đánh giá giả/spam/chơi xấu, ngược lại để trống>"}
                Mã vấn đề hợp lệ: %s.
                Chỉ gắn vấn đề khi khách thực sự phàn nàn; đánh giá khen thì issues là [].
                """.formatted(String.join(", ", ReviewTextAnalyzer.ISSUES.stream().map(i -> i.key() + " (" + i.label() + ")").toList()));
        String answer = aiService.completeOrNull(system, user.toString());
        if (answer == null) return Map.of();
        try {
            JsonNode arr = JSON.readTree(jsonArray(answer));
            Map<Long, Classified> out = new HashMap<>();
            for (JsonNode n : arr) {
                long id = n.path("id").asLong(-1);
                if (id < 0) continue;
                List<String> issues = new ArrayList<>();
                n.path("issues").forEach(x -> {
                    String k = x.asText().trim().toUpperCase(Locale.ROOT);
                    if (ReviewTextAnalyzer.isIssue(k) && !issues.contains(k)) issues.add(k);
                });
                String s = n.path("sentiment").asText("").toUpperCase(Locale.ROOT);
                if (!Set.of("POSITIVE", "NEUTRAL", "NEGATIVE").contains(s)) s = null;
                out.put(id, new Classified(s, n.has("score") ? Math.max(-1, Math.min(1, n.path("score").asDouble())) : null, issues,
                        blankToNull(ReviewTextAnalyzer.cut(n.path("summary").asText(""), 300)), n.path("suspicious").asBoolean(false),
                        blankToNull(ReviewTextAnalyzer.cut(n.path("suspicious_reason").asText(""), 200))));
            }
            return out;
        } catch (Exception e) {
            log.warn("Review classification: unreadable answer ({})", e.getMessage());
            return Map.of();
        }
    }

    // ==================== FastAPI service ====================

    /** Classification by the FastAPI service; empty when it is not configured or does not answer. */
    public Map<Long, Classified> classifyByService(List<ReviewHubItem> items) {
        if (items.isEmpty() || !serviceAvailable()) return Map.of();
        try {
            List<Map<String, Object>> body = items.stream().map(i -> Map.<String, Object>of("id", i.getId(), "rating", i.getRating(),
                    "content", Objects.requireNonNullElse(i.getContent(), ""))).toList();
            String raw = webClient.post().uri(base() + "/analyze").bodyValue(Map.of("items", body)).retrieve()
                    .bodyToMono(String.class).block(Duration.ofSeconds(15));
            Map<Long, Classified> out = new HashMap<>();
            for (JsonNode n : JSON.readTree(raw).path("results")) {
                List<String> issues = new ArrayList<>();
                n.path("issues").forEach(x -> { if (ReviewTextAnalyzer.isIssue(x.asText())) issues.add(x.asText()); });
                out.put(n.path("id").asLong(), new Classified(n.path("sentiment").asText(null), n.path("score").asDouble(0), issues,
                        blankToNull(n.path("summary").asText("")), false, null));
            }
            return out;
        } catch (Exception e) {
            log.warn("Review AI service unavailable: {}", e.getMessage());
            return Map.of();
        }
    }

    public Map<String, Object> serviceHealth() {
        if (!serviceAvailable()) return Map.of("configured", false);
        try {
            String raw = webClient.get().uri(base() + "/health").retrieve().bodyToMono(String.class).block(Duration.ofSeconds(5));
            Map<String, Object> m = new LinkedHashMap<>(JSON.readValue(raw, Map.class));
            m.put("configured", true);
            m.put("url", serviceUrl);
            return m;
        } catch (Exception e) {
            return Map.of("configured", true, "url", serviceUrl, "status", "DOWN", "error", ReviewTextAnalyzer.cut(String.valueOf(e.getMessage()), 200));
        }
    }

    /** Sends the labelled reviews to the service, which trains its classifier; returns its report. */
    public Map<String, Object> train(List<ReviewHubItem> labelled) {
        if (!serviceAvailable()) throw new IllegalStateException("Chưa cấu hình dịch vụ AI (REVIEW_AI_URL)");
        List<Map<String, Object>> rows = labelled.stream().filter(i -> i.getContent() != null && !i.getContent().isBlank())
                .map(i -> Map.<String, Object>of("content", i.getContent(), "rating", i.getRating(),
                        "issues", i.getIssues() == null || i.getIssues().isBlank() ? List.of() : List.of(i.getIssues().split(","))))
                .toList();
        try {
            String raw = webClient.post().uri(base() + "/train").bodyValue(Map.of("items", rows)).retrieve()
                    .bodyToMono(String.class).block(Duration.ofSeconds(120));
            return JSON.readValue(raw, Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("Dịch vụ AI không huấn luyện được: " + e.getMessage());
        }
    }

    private String base() {
        return serviceUrl.replaceAll("/+$", "");
    }

    // ==================== reply drafts ====================

    public record Draft(String text, String source) {
    }

    /** A reply for this review; the LLM writes it when available, else a template for the situation. */
    public Draft draft(ReviewHubItem i, String shopName) {
        int max = i.getSource() == ReviewHubItem.Source.SHOPPI ? SHOPPI_REPLY_MAX - 15 : 600;
        if (llmAvailable()) {
            String system = """
                    Bạn là chủ gian hàng "%s" trên sàn thương mại điện tử. Nhiệm vụ: SOẠN PHẢN HỒI ĐÁNH GIÁ của khách, để chủ shop duyệt trước khi đăng.
                    Quy tắc:
                    - Tiếng Việt, xưng "shop", gọi khách là "bạn" (hoặc tên khách nếu có), lịch sự, chân thành, không sáo rỗng.
                    - Đánh giá tốt: cảm ơn cụ thể điều khách khen, mời quay lại.
                    - Đánh giá xấu: xin lỗi đúng vấn đề khách gặp, nêu cách shop xử lý (đổi/trả, gửi bù, kiểm tra lại), mời khách nhắn tin cho shop để giải quyết.
                      Không đổ lỗi cho khách, không tranh cãi, không hứa điều shop không chắc làm được, không đưa số điện thoại hay link ngoài sàn.
                    - Nghi hàng giả/an toàn: nghiêm túc, đề nghị khách gửi ảnh qua tin nhắn để shop kiểm tra ngay.
                    - Tối đa %d ký tự. Chỉ trả về nội dung phản hồi, không kèm giải thích.
                    """.formatted(shopName, max);
            String user = "Sàn: " + i.getSource() + "\nSản phẩm: " + Objects.requireNonNullElse(i.getProductName(), "") + "\nKhách: "
                    + Objects.requireNonNullElse(i.getReviewerName(), "ẩn danh") + "\nSố sao: " + i.getRating() + "\nVấn đề: "
                    + labels(i.getIssues()) + "\nĐánh giá: " + Objects.requireNonNullElse(i.getContent(), "(không có nội dung)");
            String text = aiService.completeOrNull(system, user);
            if (text != null) {
                text = text.replaceAll("^\"|\"$", "").trim();
                return new Draft(ReviewTextAnalyzer.cut(text, max), "AI");
            }
        }
        return new Draft(ReviewTextAnalyzer.cut(template(i), max), "TEMPLATE");
    }

    /** The situation templates used without AI. */
    static String template(ReviewHubItem i) {
        String name = i.getReviewerName() != null && !i.getReviewerName().isBlank() && !i.getReviewerName().contains("*") ? i.getReviewerName() : "bạn";
        List<String> issues = i.getIssues() == null || i.getIssues().isBlank() ? List.of() : List.of(i.getIssues().split(","));
        if (i.getRating() >= 4 && issues.isEmpty()) {
            return "Cảm ơn " + name + " đã tin tưởng và dành thời gian đánh giá sản phẩm! Shop rất vui khi bạn hài lòng, hẹn gặp lại bạn ở những đơn hàng tiếp theo nhé ❤️";
        }
        String first = issues.isEmpty() ? "OTHER" : issues.get(0);
        String body = switch (first) {
            case "QUALITY" -> "Shop thành thật xin lỗi vì sản phẩm bạn nhận được chưa đạt chất lượng. Bạn nhắn tin kèm ảnh giúp shop, shop sẽ đổi sản phẩm mới hoặc hoàn tiền cho bạn ngay.";
            case "WRONG_DESCRIPTION" -> "Shop xin lỗi vì sản phẩm chưa đúng như mô tả khiến bạn thất vọng. Bạn nhắn tin cho shop để được đổi đúng mẫu hoặc hỗ trợ trả hàng nhé.";
            case "DELIVERY" -> "Shop xin lỗi vì đơn hàng đến tay bạn chậm hơn mong đợi. Shop đã làm việc lại với đơn vị vận chuyển để những đơn sau được giao nhanh hơn.";
            case "PACKAGING" -> "Shop xin lỗi vì khâu đóng gói chưa cẩn thận. Bạn nhắn tin kèm ảnh giúp shop, shop sẽ gửi bù/đổi mới cho bạn và đóng gói kỹ hơn.";
            case "SIZE" -> "Shop xin lỗi vì size chưa vừa với bạn. Bạn nhắn tin số đo cho shop, shop sẽ hỗ trợ đổi size phù hợp nhé.";
            case "MISSING_ITEM" -> "Shop thành thật xin lỗi vì đơn hàng bị thiếu/giao nhầm. Bạn nhắn tin kèm ảnh kiện hàng, shop sẽ gửi bù ngay cho bạn.";
            case "AUTHENTICITY" -> "Shop rất coi trọng phản hồi của bạn. Bạn vui lòng nhắn tin kèm ảnh sản phẩm để shop kiểm tra ngay và hỗ trợ đổi trả/hoàn tiền nếu có sai sót.";
            case "PRICE" -> "Cảm ơn bạn đã góp ý về giá. Shop sẽ có thêm ưu đãi cho khách quen, bạn theo dõi shop để nhận voucher nhé.";
            case "SERVICE" -> "Shop xin lỗi vì trải nghiệm chưa tốt khi trao đổi với shop. Bạn nhắn tin lại giúp shop, shop sẽ ưu tiên xử lý yêu cầu của bạn ngay hôm nay.";
            default -> "Shop xin lỗi vì trải nghiệm chưa tốt của bạn. Bạn nhắn tin cho shop để shop hiểu rõ vấn đề và hỗ trợ bạn sớm nhất nhé.";
        };
        return "Chào " + name + ", " + Character.toLowerCase(body.charAt(0)) + body.substring(1);
    }

    // ==================== recurring issues ====================

    /** Likely cause and fix for a cluster of complaints; the LLM reads the samples, or the issue's standard fix. */
    public Map<String, Object> rootCause(String product, String issue, List<String> samples) {
        if (llmAvailable() && !samples.isEmpty()) {
            String system = """
                    Bạn là chuyên gia vận hành gian hàng thương mại điện tử. Nhiệm vụ: PHÂN TÍCH NGUYÊN NHÂN GỐC của các đánh giá phàn nàn lặp lại.
                    Trả về đúng JSON: {"cause": "<nguyên nhân có thể, 1-2 câu>", "actions": ["<việc cần làm 1>", "<việc 2>", "<việc 3>"],
                    "is_product_defect": <true nếu có dấu hiệu lỗi sản phẩm thật, cần kiểm tra lô hàng>}. Tiếng Việt, cụ thể, không chung chung.
                    """;
            String user = "Sản phẩm: " + product + "\nNhóm vấn đề: " + ReviewTextAnalyzer.label(issue) + "\nCác đánh giá:\n- "
                    + String.join("\n- ", samples.subList(0, Math.min(12, samples.size())));
            String answer = aiService.completeOrNull(system, user);
            if (answer != null) {
                try {
                    JsonNode n = JSON.readTree(jsonObject(answer));
                    List<String> actions = new ArrayList<>();
                    n.path("actions").forEach(a -> actions.add(a.asText()));
                    if (!n.path("cause").asText("").isBlank()) {
                        return Map.of("cause", n.path("cause").asText(), "actions", actions, "productDefect", n.path("is_product_defect").asBoolean(false),
                                "source", "AI");
                    }
                } catch (Exception e) {
                    log.warn("Root cause: unreadable answer ({})", e.getMessage());
                }
            }
        }
        return Map.of("cause", "Nhiều khách cùng gặp vấn đề \"" + ReviewTextAnalyzer.label(issue).toLowerCase(Locale.ROOT) + "\" với sản phẩm này.",
                "actions", List.of(ReviewTextAnalyzer.fix(issue)), "productDefect", Set.of("QUALITY", "AUTHENTICITY").contains(issue), "source", "RULE");
    }

    // ==================== helpers ====================

    static String labels(String issues) {
        if (issues == null || issues.isBlank()) return "không";
        return String.join(", ", Arrays.stream(issues.split(",")).map(ReviewTextAnalyzer::label).toList());
    }

    static String jsonArray(String s) {
        int a = s.indexOf('['), b = s.lastIndexOf(']');
        if (a < 0 || b <= a) throw new IllegalArgumentException("no JSON array");
        return s.substring(a, b + 1);
    }

    static String jsonObject(String s) {
        int a = s.indexOf('{'), b = s.lastIndexOf('}');
        if (a < 0 || b <= a) throw new IllegalArgumentException("no JSON object");
        return s.substring(a, b + 1);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
