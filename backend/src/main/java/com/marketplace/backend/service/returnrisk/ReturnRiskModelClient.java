package com.marketplace.backend.service.returnrisk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;

/** The anomaly models of the FastAPI service (REVIEW_AI_URL, folder review-ai-service/). Optional: rules work without it. */
@Slf4j
@Component
@RequiredArgsConstructor
class ReturnRiskModelClient {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final WebClient webClient;

    @Value("${app.review-ai.url:}")
    private String serviceUrl;

    record ModelScore(double percentile, Double abuseProbability, List<String> unusual) {}

    boolean available() {
        return serviceUrl != null && !serviceUrl.isBlank();
    }

    /** Empty when the service is off, down or has no trained model yet. */
    Optional<ModelScore> score(Map<String, Number> features) {
        if (!available()) return Optional.empty();
        try {
            String raw = webClient.post().uri(base() + "/return-risk/score").bodyValue(Map.of("features", features)).retrieve()
                    .bodyToMono(String.class).block(Duration.ofSeconds(10));
            JsonNode n = JSON.readTree(raw);
            if (!n.path("trained").asBoolean(false)) return Optional.empty();
            List<String> unusual = new ArrayList<>();
            n.path("unusual").forEach(x -> unusual.add(x.asText()));
            return Optional.of(new ModelScore(n.path("percentile").asDouble(), n.path("abuseProbability").isNumber() ? n.path("abuseProbability").asDouble() : null, unusual));
        } catch (Exception e) {
            log.warn("Return risk model unavailable: {}", e.getMessage());
            return Optional.empty();
        }
    }

    Map<String, Object> status() {
        if (!available()) return Map.of("configured", false);
        try {
            String raw = webClient.get().uri(base() + "/return-risk/status").retrieve().bodyToMono(String.class).block(Duration.ofSeconds(5));
            Map<String, Object> m = new LinkedHashMap<>(JSON.readValue(raw, Map.class));
            m.put("configured", true);
            return m;
        } catch (Exception e) {
            return Map.of("configured", true, "down", true);
        }
    }

    /** items: [{features, label}]. Throws IllegalStateException with the service's reason. */
    Map<String, Object> train(List<Map<String, Object>> items) {
        try {
            String raw = webClient.post().uri(base() + "/return-risk/train").bodyValue(Map.of("items", items)).retrieve()
                    .onStatus(s -> s.value() == 400, r -> r.bodyToMono(String.class).map(b -> new IllegalStateException(detail(b))))
                    .bodyToMono(String.class).block(Duration.ofSeconds(120));
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
