package com.marketplace.backend.service;

import com.marketplace.backend.service.ai.AIProvider;
import com.marketplace.backend.service.ai.AiSettings;
import com.marketplace.backend.service.ai.HuggingFaceProvider;
import com.marketplace.backend.service.ai.OpenAiProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

// Facade used by ChatbotService (buyer RAG chatbot), the seller AI tools and the reply suggestions.
// The actual LLM call is delegated to the provider chosen on the admin page "AI & chi phi LLM"
// (falling back to app.ai.provider / the environment variables):
//
//   openai        -> calls OpenAI's API (default, most reliable)
//   huggingface   -> calls the self-hosted fine-tuned Vistral-7B model (see ai-finetune/)
//
// Every call is logged with its tokens and estimated cost (AiOpsService). When AI is switched off,
// not configured or over its monthly budget, callers get no answer and use their rule-based result.
@Service
@RequiredArgsConstructor
public class AIService {

    private final OpenAiProvider openAiProvider;
    private final HuggingFaceProvider huggingFaceProvider;
    private final com.marketplace.backend.service.ai.ClaudeProvider claudeProvider;
    private final AiOpsService ops;

    public String complete(String systemPrompt, String userPrompt) {
        AiSettings s = ops.settings();
        String feature = AiOpsService.featureOfCaller();
        String blocked = ops.blockReason(s);
        if (blocked != null) {
            if (s.configured()) ops.record(feature, s, null, 0, blocked);
            return "[AI tạm dừng] " + blocked;
        }
        long start = System.nanoTime();
        AIProvider.Completion c = provider(s).call(s, systemPrompt, userPrompt);
        ops.record(feature, s, c, (int) ((System.nanoTime() - start) / 1_000_000), null);
        if (c.error() != null) return "Xin lỗi, trợ lý AI hiện không phản hồi được. Vui lòng thử lại sau.";
        return c.text();
    }

    private AIProvider provider(AiSettings s) {
        return s.huggingFace() ? huggingFaceProvider : s.claude() ? claudeProvider : openAiProvider;
    }

    public boolean isConfigured() {
        return ops.blockReason(ops.settings()) == null;
    }

    /**
     * The model's answer, or null when AI is off, not configured, over budget or the call failed
     * - callers then use their rule-based result.
     */
    public String completeOrNull(String systemPrompt, String userPrompt) {
        AiSettings s = ops.settings();
        String blocked = ops.blockReason(s);
        if (blocked != null) {
            // switched off / over budget: count the attempt so the admin sees what was refused
            if (s.configured()) ops.record(AiOpsService.featureOfCaller(), s, null, 0, blocked);
            return null;
        }
        String answer = complete(systemPrompt, userPrompt);
        if (answer == null || answer.isBlank() || answer.startsWith("[AI") || answer.startsWith("Xin lỗi, trợ lý AI")) return null;
        return answer.trim();
    }

    /** Admin "Kiem tra ket noi": one tiny call with the current settings, even when AI is switched off. */
    public Map<String, Object> testConnection() {
        AiSettings s = ops.settings();
        if (!s.configured()) return AdminUserService.map("ok", false, "message", "Chưa cấu hình " + (s.huggingFace() ? "endpoint Hugging Face" : s.claude() ? "API key Claude" : "API key OpenAI"));
        long start = System.nanoTime();
        AIProvider.Completion c = provider(s)
                .call(s, "Bạn là trợ lý kiểm tra kết nối. Chỉ trả lời đúng một từ.", "Trả lời: OK");
        int ms = (int) ((System.nanoTime() - start) / 1_000_000);
        ops.record("CONNECTION_TEST", s, c, ms, null);
        return AdminUserService.map("ok", c.error() == null, "model", s.model(), "latencyMs", ms,
                "message", c.error() == null ? "Kết nối thành công: \"" + OpenAiProviderText.cut(c.text()) + "\"" : c.error(),
                "tokens", c.promptTokens() + c.completionTokens());
    }

    private static final class OpenAiProviderText {
        static String cut(String s) {
            if (s == null) return "";
            return s.length() > 60 ? s.substring(0, 60) + "…" : s.trim();
        }
    }
}
