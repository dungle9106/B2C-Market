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
        return send(s, systemPrompt, userPrompt, userPrompt, AIProvider.estimateTokens(systemPrompt) + AIProvider.estimateTokens(userPrompt));
    }

    // image: a "data:" URL part next to the text (gpt-4o / gpt-4o-mini read images); ~850 tokens for a low-detail image
    @Override
    public Completion callWithImage(AiSettings s, String systemPrompt, String userPrompt, byte[] image, String mime) {
        List<Map<String, Object>> parts = List.of(
                Map.of("type", "text", "text", userPrompt),
                Map.of("type", "image_url", "image_url", Map.of("url", "data:" + mime + ";base64," + java.util.Base64.getEncoder().encodeToString(image),
                        "detail", "low")));
        return send(s, systemPrompt, parts, userPrompt, AIProvider.estimateTokens(systemPrompt) + AIProvider.estimateTokens(userPrompt) + 850);
    }

    private Completion send(AiSettings s, String systemPrompt, Object userContent, String userPrompt, int estimatedIn) {
        if (s.openaiKey() == null || s.openaiKey().isBlank()) return Completion.failed("Chưa cấu hình API key", 0);

        Map<String, Object> body = Map.of(
                "model", s.openaiModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userContent)
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

    // Chat Completions with "tools": the model answers with message.tool_calls[{id, function: {name, arguments}}]
    // (arguments is a JSON string); each result goes back as a {role: "tool", tool_call_id} message
    @Override
    public Agent.Reply converse(AiSettings s, String systemPrompt, List<Agent.Msg> messages, List<Agent.Tool> tools) {
        if (s.openaiKey() == null || s.openaiKey().isBlank()) return Agent.Reply.failed("Chưa cấu hình API key");
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        out.add(Map.of("role", "system", "content", systemPrompt));
        for (Agent.Msg m : messages) {
            if (m instanceof Agent.User u) {
                out.add(Map.of("role", "user", "content", u.text()));
            } else if (m instanceof Agent.Assistant a) {
                Map<String, Object> msg = new java.util.HashMap<>();
                msg.put("role", "assistant");
                msg.put("content", a.text() == null ? "" : a.text());
                if (!a.calls().isEmpty()) {
                    msg.put("tool_calls", a.calls().stream().map(c -> Map.of("id", c.id(), "type", "function",
                            "function", Map.of("name", c.name(), "arguments", json(c.args())))).toList());
                }
                out.add(msg);
            } else if (m instanceof Agent.ToolResult r) {
                out.add(Map.of("role", "tool", "tool_call_id", r.callId(), "content", r.content()));
            }
        }
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("model", s.openaiModel());
        body.put("messages", out);
        body.put("temperature", 0.3);
        if (!tools.isEmpty()) {
            body.put("tools", tools.stream().map(t -> Map.of("type", "function",
                    "function", Map.of("name", t.name(), "description", t.description(), "parameters", t.schema()))).toList());
        }
        try {
            String raw = webClient.post().uri(s.openaiUrl())
                    .header("Authorization", "Bearer " + s.openaiKey())
                    .header("Content-Type", "application/json")
                    .bodyValue(body).retrieve().bodyToMono(String.class).block(Duration.ofSeconds(60));
            JsonNode root = objectMapper.readTree(raw);
            JsonNode msg = root.path("choices").path(0).path("message");
            List<Agent.Call> calls = new java.util.ArrayList<>();
            for (JsonNode c : msg.path("tool_calls")) {
                String args = c.path("function").path("arguments").asText("{}");
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = args.isBlank() ? Map.of() : objectMapper.readValue(args, Map.class);
                calls.add(new Agent.Call(c.path("id").asText(), c.path("function").path("name").asText(), parsed));
            }
            JsonNode usage = root.path("usage");
            return new Agent.Reply(msg.path("content").isTextual() ? msg.path("content").asText() : null, calls,
                    usage.path("prompt_tokens").asInt(), usage.path("completion_tokens").asInt(), null);
        } catch (WebClientResponseException e) {
            return Agent.Reply.failed("HTTP " + e.getStatusCode().value() + " " + cut(e.getResponseBodyAsString()));
        } catch (Exception e) {
            return Agent.Reply.failed(e.getClass().getSimpleName() + ": " + cut(e.getMessage()));
        }
    }

    private String json(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
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
