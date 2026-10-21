package com.marketplace.backend.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Map;

// Calls the self-hosted fine-tuned model (Vistral-7B + LoRA, merged, see
// ai-finetune/) served behind a Hugging Face Inference Endpoint (TGI).
//
// Two things make this different from a normal API call:
//  1. Prompt format: TGI expects a single "inputs" string already formatted
//     with the Mistral/Vistral chat template ([INST] ... [/INST]), not a
//     messages array like OpenAI's Chat Completions API.
//  2. Cold start: with "scale to zero" enabled (see ai-finetune/DEPLOY_HF_ENDPOINT.md)
//     the endpoint may be asleep and take 2-5 minutes to wake on the first
//     request after idling. We retry with backoff instead of failing
//     immediately on the initial 503.
// TGI does not report token usage here, so tokens are estimated from the text length.
@Component
@RequiredArgsConstructor
public class HuggingFaceProvider implements AIProvider {

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Completion call(AiSettings s, String systemPrompt, String userPrompt) {
        // Vistral/Mistral instruction format: system prompt folded into the
        // instruction since the base template only has a single [INST] turn.
        String prompt = String.format("<s>[INST] %s\n\n%s [/INST]", systemPrompt, userPrompt);
        int in = AIProvider.estimateTokens(prompt);
        if (s.hfUrl() == null || s.hfUrl().isBlank()) return Completion.failed("Chưa cấu hình endpoint Hugging Face", 0);

        Map<String, Object> body = Map.of(
                "inputs", prompt,
                "parameters", Map.of(
                        "max_new_tokens", 300,
                        "temperature", 0.7,
                        "return_full_text", false
                )
        );
        try {
            String raw = webClient.post()
                    .uri(s.hfUrl())
                    .header("Authorization", "Bearer " + (s.hfToken() == null ? "" : s.hfToken()))
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    // endpoint may be asleep (scale-to-zero) -> 503 while it wakes up;
                    // retry a few times with backoff rather than failing the user's chat turn
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(20)).filter(t -> true))
                    .block(Duration.ofMinutes(3));
            JsonNode root = objectMapper.readTree(raw);
            // TGI returns either an array of {generated_text} or a single object depending on version
            JsonNode node = root.isArray() ? root.get(0) : root;
            String text = node.path("generated_text").asText().trim();
            return new Completion(text, in, AIProvider.estimateTokens(text), true, null);
        } catch (Exception e) {
            return Completion.failed("Mô hình tự train không phản hồi (đang khởi động?): " + OpenAiProvider.cut(e.getMessage()), in);
        }
    }
}
