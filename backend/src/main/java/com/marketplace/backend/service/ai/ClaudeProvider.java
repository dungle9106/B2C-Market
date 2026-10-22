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

// Anthropic Claude (Messages API). Same contract as OpenAiProvider: never throws, a failure comes back as an error.
@Component
@RequiredArgsConstructor
public class ClaudeProvider implements AIProvider {

    static final String VERSION = "2023-06-01";

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Completion call(AiSettings s, String systemPrompt, String userPrompt) {
        int estimatedIn = AIProvider.estimateTokens(systemPrompt) + AIProvider.estimateTokens(userPrompt);
        if (s.claudeKey() == null || s.claudeKey().isBlank()) return Completion.failed("Chưa cấu hình Claude API key", 0);
        Map<String, Object> body = Map.of(
                "model", s.claudeModel(),
                "max_tokens", 2048,
                "system", systemPrompt,
                "messages", List.of(Map.of("role", "user", "content", userPrompt)),
                "temperature", 0.7);
        try {
            String raw = webClient.post().uri(s.claudeUrl())
                    .header("x-api-key", s.claudeKey())
                    .header("anthropic-version", VERSION)
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve().bodyToMono(String.class).block(Duration.ofSeconds(90));
            JsonNode root = objectMapper.readTree(raw);
            StringBuilder text = new StringBuilder();
            for (JsonNode block : root.path("content")) {
                if ("text".equals(block.path("type").asText())) text.append(block.path("text").asText());
            }
            JsonNode usage = root.path("usage");
            if (usage.has("input_tokens")) {
                return new Completion(text.toString(), usage.path("input_tokens").asInt(), usage.path("output_tokens").asInt(), false, null);
            }
            return new Completion(text.toString(), estimatedIn, AIProvider.estimateTokens(text.toString()), true, null);
        } catch (WebClientResponseException e) {
            return Completion.failed("HTTP " + e.getStatusCode().value() + " " + OpenAiProvider.cut(e.getResponseBodyAsString()), estimatedIn);
        } catch (Exception e) {
            return Completion.failed(e.getClass().getSimpleName() + ": " + OpenAiProvider.cut(e.getMessage()), estimatedIn);
        }
    }
}
