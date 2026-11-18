package com.marketplace.backend.service.reco;

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
 * The recommendation model of the FastAPI service (REVIEW_AI_URL, folder review-ai-service/, app/recommender.py).
 * Optional: without it, or before it is trained, the shop falls back to its rules.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class RecommendationModelClient {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final WebClient webClient;

    @Value("${app.review-ai.url:}")
    private String serviceUrl;

    /** One recommended product; because = the product of the buyer's history that led to it (may be null). */
    record Scored(long item, double score, Long because) {}

    boolean available() {
        return serviceUrl != null && !serviceUrl.isBlank();
    }

    /** Empty when the service is off, down or not trained: the caller uses its rules. */
    Optional<List<Scored>> recommend(List<Map<String, Object>> history, Collection<Long> exclude, int k) {
        return call("/reco/recommend", Map.of("history", history, "exclude", exclude, "k", k));
    }

    Optional<List<Scored>> similar(long item, int k) {
        return call("/reco/similar", Map.of("item", item, "k", k));
    }

    private Optional<List<Scored>> call(String path, Map<String, Object> body) {
        if (!available()) return Optional.empty();
        try {
            String raw = webClient.post().uri(base() + path).bodyValue(body).retrieve()
                    .bodyToMono(String.class).block(Duration.ofSeconds(5));
            JsonNode n = JSON.readTree(raw);
            if (!n.path("trained").asBoolean(false)) return Optional.empty();
            List<Scored> out = new ArrayList<>();
            for (JsonNode x : n.path("items")) {
                out.add(new Scored(x.path("item").asLong(), x.path("score").asDouble(),
                        x.path("because").isNumber() ? x.path("because").asLong() : null));
            }
            return Optional.of(out);
        } catch (Exception e) {
            log.debug("Recommendation model unavailable: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> status() {
        if (!available()) return Map.of("configured", false);
        try {
            String raw = webClient.get().uri(base() + "/reco/status").retrieve().bodyToMono(String.class).block(Duration.ofSeconds(5));
            Map<String, Object> m = new LinkedHashMap<>(JSON.readValue(raw, Map.class));
            m.put("configured", true);
            return m;
        } catch (Exception e) {
            return Map.of("configured", true, "down", true);
        }
    }

    /** Throws IllegalStateException with the service's reason (not enough data, service down...). */
    @SuppressWarnings("unchecked")
    Map<String, Object> train(List<Map<String, Object>> interactions, List<Map<String, Object>> items) {
        if (!available()) throw new IllegalStateException("Chưa cấu hình dịch vụ AI (biến môi trường REVIEW_AI_URL)");
        try {
            String raw = webClient.post().uri(base() + "/reco/train").bodyValue(Map.of("interactions", interactions, "items", items)).retrieve()
                    .onStatus(s -> s.value() == 400, r -> r.bodyToMono(byte[].class).map(b -> new IllegalStateException(detail(new String(b, java.nio.charset.StandardCharsets.UTF_8)))))  // FastAPI sends no charset: always UTF-8
                    .bodyToMono(String.class).block(Duration.ofMinutes(10));
            return JSON.readValue(raw, Map.class);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Dịch vụ AI không huấn luyện được: " + e.getMessage());
        }
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
