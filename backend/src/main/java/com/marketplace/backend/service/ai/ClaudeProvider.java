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
        return send(s, systemPrompt, userPrompt, AIProvider.estimateTokens(systemPrompt) + AIProvider.estimateTokens(userPrompt));
    }

    // image: a base64 "image" block before the text (all current Claude models read images)
    @Override
    public Completion callWithImage(AiSettings s, String systemPrompt, String userPrompt, byte[] image, String mime) {
        List<Map<String, Object>> content = List.of(
                Map.of("type", "image", "source", Map.of("type", "base64", "media_type", mime, "data", java.util.Base64.getEncoder().encodeToString(image))),
                Map.of("type", "text", "text", userPrompt));
        return send(s, systemPrompt, content, AIProvider.estimateTokens(systemPrompt) + AIProvider.estimateTokens(userPrompt) + 1000);
    }

    // Messages API with "tools": the model answers with "tool_use" content blocks {id, name, input}; the results go
    // back in the next user message as "tool_result" blocks (all results of one turn in the same message)
    @Override
    public Agent.Reply converse(AiSettings s, String systemPrompt, List<Agent.Msg> messages, List<Agent.Tool> tools) {
        if (s.claudeKey() == null || s.claudeKey().isBlank()) return Agent.Reply.failed("Chưa cấu hình Claude API key");
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        List<Map<String, Object>> pendingResults = new java.util.ArrayList<>();
        for (Agent.Msg m : messages) {
            if (m instanceof Agent.ToolResult r) {
                pendingResults.add(Map.of("type", "tool_result", "tool_use_id", r.callId(), "content", r.content()));
                continue;
            }
            if (!pendingResults.isEmpty()) {
                out.add(Map.of("role", "user", "content", List.copyOf(pendingResults)));
                pendingResults.clear();
            }
            if (m instanceof Agent.User u) {
                out.add(Map.of("role", "user", "content", u.text()));
            } else if (m instanceof Agent.Assistant a) {
                List<Map<String, Object>> blocks = new java.util.ArrayList<>();
                if (a.text() != null && !a.text().isBlank()) blocks.add(Map.of("type", "text", "text", a.text()));
                for (Agent.Call c : a.calls()) blocks.add(Map.of("type", "tool_use", "id", c.id(), "name", c.name(), "input", c.args()));
                if (!blocks.isEmpty()) out.add(Map.of("role", "assistant", "content", blocks));
            }
        }
        if (!pendingResults.isEmpty()) out.add(Map.of("role", "user", "content", List.copyOf(pendingResults)));

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("model", s.claudeModel());
        body.put("max_tokens", 1024);
        body.put("system", systemPrompt);
        body.put("messages", out);
        if (!tools.isEmpty()) {
            body.put("tools", tools.stream().map(t -> Map.of("name", t.name(), "description", t.description(), "input_schema", t.schema())).toList());
        }
        try {
            String raw = webClient.post().uri(s.claudeUrl())
                    .header("x-api-key", s.claudeKey())
                    .header("anthropic-version", VERSION)
                    .header("Content-Type", "application/json")
                    .bodyValue(body).retrieve().bodyToMono(String.class).block(Duration.ofSeconds(90));
            JsonNode root = objectMapper.readTree(raw);
            StringBuilder text = new StringBuilder();
            List<Agent.Call> calls = new java.util.ArrayList<>();
            for (JsonNode block : root.path("content")) {
                String type = block.path("type").asText();
                if ("text".equals(type)) text.append(block.path("text").asText());
                if ("tool_use".equals(type)) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> input = objectMapper.convertValue(block.path("input"), Map.class);
                    calls.add(new Agent.Call(block.path("id").asText(), block.path("name").asText(), input == null ? Map.of() : input));
                }
            }
            JsonNode usage = root.path("usage");
            return new Agent.Reply(text.isEmpty() ? null : text.toString(), calls,
                    usage.path("input_tokens").asInt(), usage.path("output_tokens").asInt(), null);
        } catch (WebClientResponseException e) {
            return Agent.Reply.failed("HTTP " + e.getStatusCode().value() + " " + OpenAiProvider.cut(e.getResponseBodyAsString()));
        } catch (Exception e) {
            return Agent.Reply.failed(e.getClass().getSimpleName() + ": " + OpenAiProvider.cut(e.getMessage()));
        }
    }

    private Completion send(AiSettings s, String systemPrompt, Object userContent, int estimatedIn) {
        if (s.claudeKey() == null || s.claudeKey().isBlank()) return Completion.failed("Chưa cấu hình Claude API key", 0);
        Map<String, Object> body = Map.of(
                "model", s.claudeModel(),
                "max_tokens", 2048,
                "system", systemPrompt,
                "messages", List.of(Map.of("role", "user", "content", userContent)),
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
