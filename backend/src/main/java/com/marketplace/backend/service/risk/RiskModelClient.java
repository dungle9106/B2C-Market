package com.marketplace.backend.service.risk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;

/**
 * The churn and fraud models of the FastAPI service (REVIEW_AI_URL, review-ai-service/app/tabular.py).
 * Optional: without the service, or before a model is trained, the rules decide alone.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class RiskModelClient {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final WebClient webClient;

    @Value("${app.review-ai.url:}")
    private String serviceUrl;

    /** A case as the model sees it. label: 1 = it happened (left / fraud), 0 = it did not, null = unknown yet. */
    record Row(Map<String, Double> features, Integer label, double rule, double ts) {}

    /** What the model says of one case; probability / percentile are null when that part is not trained. */
    record Score(long id, Double probability, Double percentile, List<String> factors, List<String> unusual) {}

    boolean available() {
        return serviceUrl != null && !serviceUrl.isBlank();
    }

    /** Empty when the service is off, down or not trained. */
    Optional<Map<Long, Score>> score(String kind, Map<Long, Map<String, Double>> cases) {
        if (!available() || cases.isEmpty()) return Optional.empty();
        try {
            List<Map<String, Object>> items = new ArrayList<>();
            cases.forEach((id, f) -> items.add(Map.of("id", id, "features", f)));
            String raw = webClient.post().uri(base() + "/risk/" + kind + "/score").bodyValue(Map.of("items", items)).retrieve()
                    .bodyToMono(String.class).block(Duration.ofSeconds(20));
            JsonNode n = JSON.readTree(raw);
            if (!n.path("trained").asBoolean(false)) return Optional.empty();
            Map<Long, Score> out = new HashMap<>();
            for (JsonNode x : n.path("items")) {
                out.put(x.path("id").asLong(), new Score(x.path("id").asLong(), num(x.path("probability")), num(x.path("percentile")),
                        strings(x.path("factors")), strings(x.path("unusual"))));
            }
            return Optional.of(out);
        } catch (Exception e) {
            log.debug("{} model unavailable: {}", kind, e.getMessage());
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> status(String kind) {
        if (!available()) return Map.of("configured", false);
        try {
            String raw = webClient.get().uri(base() + "/risk/" + kind + "/status").retrieve().bodyToMono(String.class).block(Duration.ofSeconds(5));
            Map<String, Object> m = new LinkedHashMap<>(JSON.readValue(raw, Map.class));
            m.put("configured", true);
            return m;
        } catch (Exception e) {
            return Map.of("configured", true, "down", true);
        }
    }

    /** Throws IllegalStateException with the service's reason (not enough data, service down...). */
    @SuppressWarnings("unchecked")
    Map<String, Object> train(String kind, List<Row> rows) {
        if (!available()) throw new IllegalStateException("Chưa cấu hình dịch vụ AI (biến môi trường REVIEW_AI_URL)");
        try {
            List<Map<String, Object>> body = rows.stream().map(r -> {
                Map<String, Object> m = new HashMap<>();
                m.put("features", r.features());
                m.put("label", r.label());
                m.put("rule", r.rule());
                m.put("ts", r.ts());
                return m;
            }).toList();
            String raw = webClient.post().uri(base() + "/risk/" + kind + "/train").bodyValue(Map.of("rows", body)).retrieve()
                    .onStatus(s -> s.value() == 400, r -> r.bodyToMono(byte[].class).map(b -> new IllegalStateException(detail(new String(b, java.nio.charset.StandardCharsets.UTF_8)))))  // FastAPI sends no charset: always UTF-8
                    .bodyToMono(String.class).block(Duration.ofMinutes(10));
            return JSON.readValue(raw, Map.class);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Dịch vụ AI không huấn luyện được: " + e.getMessage());
        }
    }

    private static Double num(JsonNode n) {
        return n.isNumber() ? n.asDouble() : null;
    }

    private static List<String> strings(JsonNode n) {
        List<String> out = new ArrayList<>();
        n.forEach(x -> out.add(x.asText()));
        return out;
    }

    private static String detail(String body) {
        try {
            return JSON.readTree(body).path("detail").asText(body);
        } catch (Exception e) {
            return body;
        }
    }

    private String base() {
        return serviceUrl.replaceAll("/+$", "");
    }
}
