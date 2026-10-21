package com.marketplace.backend.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

// Calls a pre-trained LLM over HTTP (OpenAI Chat Completions API, or any
// OpenAI-compatible endpoint). No model is trained here.
@Component
@RequiredArgsConstructor
public class OpenAiProvider implements AIProvider {

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Completion call(AiSettings s, String systemPrompt, String userPrompt) {
        int estimatedIn = AIProvider.estimateTokens(systemPrompt) + AIProvider.estimateTokens(userPrompt);
        if (s.openaiKey() == null || s.openaiKey().isBlank()) return Completion.failed("Chưa cấu hình API key", 0);

        Map<String, Object> body = Map.of(
                "model", s.openaiModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "temperature", 0.7
        );
        try {
            String raw = webClient.post()
                    .uri(s.openaiUrl())
                    .header("Authorization", "Bearer " + s.openaiKey())
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(60));
            JsonNode root = objectMapper.readTree(raw);
            String text = root.path("choices").get(0).path("message").path("content").asText();
            JsonNode usage = root.path("usage");
            if (usage.has("prompt_tokens")) {
                return new Completion(text, usage.path("prompt_tokens").asInt(), usage.path("completion_tokens").asInt(), false, null);
            }
            return new Completion(text, estimatedIn, AIProvider.estimateTokens(text), true, null);
        } catch (WebClientResponseException e) {
            // 401 = wrong key, 429 = quota / rate limit: worth showing to the admin as is
            return Completion.failed("HTTP " + e.getStatusCode().value() + " " + cut(e.getResponseBodyAsString()), estimatedIn);
        } catch (Exception e) {
            // Fail soft: never let an AI/network hiccup break the buyer/seller flow.
            return Completion.failed(e.getClass().getSimpleName() + ": " + cut(e.getMessage()), estimatedIn);
        }
    }

    /** Vectors of the texts, in order. */
    public record Embeddings(List<float[]> vectors, int tokens, String error) {
    }

    /**
     * OpenAI embeddings API (text-embedding-3-small, shortened to `dims` numbers) on the same host as the chat
     * endpoint: ".../chat/completions" -> ".../embeddings". Never throws.
     */
    public Embeddings embed(AiSettings s, String model, int dims, List<String> texts) {
        if (s.openaiKey() == null || s.openaiKey().isBlank()) return new Embeddings(List.of(), 0, "Chưa cấu hình API key");
        String url = s.openaiUrl().replaceAll("/chat/completions/?$", "") + "/embeddings";
        try {
            String raw = webClient.post().uri(url)
                    .header("Authorization", "Bearer " + s.openaiKey())
                    .header("Content-Type", "application/json")
                    .bodyValue(Map.of("model", model, "input", texts, "dimensions", dims))
                    .retrieve().bodyToMono(String.class).block(Duration.ofSeconds(60));
            JsonNode root = objectMapper.readTree(raw);
            float[][] out = new float[texts.size()][];
            for (JsonNode d : root.path("data")) {
                JsonNode e = d.path("embedding");
                float[] v = new float[e.size()];
                for (int i = 0; i < v.length; i++) v[i] = (float) e.get(i).asDouble();
                out[d.path("index").asInt()] = v;
            }
            for (float[] v : out) if (v == null) return new Embeddings(List.of(), 0, "Thiếu vector trong kết quả");
            return new Embeddings(List.of(out), root.path("usage").path("total_tokens").asInt(), null);
        } catch (WebClientResponseException e) {
            return new Embeddings(List.of(), 0, "HTTP " + e.getStatusCode().value() + " " + cut(e.getResponseBodyAsString()));
        } catch (Exception e) {
            return new Embeddings(List.of(), 0, e.getClass().getSimpleName() + ": " + cut(e.getMessage()));
        }
    }

    static String cut(String s) {
        if (s == null) return "";
        s = s.replaceAll("\\s+", " ").trim();
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }
}
