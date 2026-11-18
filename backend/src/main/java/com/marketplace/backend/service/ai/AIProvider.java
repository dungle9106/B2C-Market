package com.marketplace.backend.service.ai;

// Strategy interface so AIService can switch between LLM backends (OpenAI API,
// a self-hosted fine-tuned model via Hugging Face Inference Endpoint, etc.)
// purely through configuration (admin "AI & chi phi LLM" page, or app.ai.provider),
// with no change needed in ChatbotService or SellerAIController.
public interface AIProvider {

    /**
     * One call. Never throws: a failure comes back with error set (and text null).
     * Token counts come from the API when it reports them; otherwise they are estimated (estimated = true).
     */
    Completion call(AiSettings settings, String systemPrompt, String userPrompt);

    /** The same with one image (vision models: OpenAI, Claude). Providers without vision say so. */
    default Completion callWithImage(AiSettings settings, String systemPrompt, String userPrompt, byte[] image, String mime) {
        return Completion.failed("Mô hình hiện tại không hỗ trợ đọc ảnh", 0);
    }

    /** One step of a conversation with tools (see Agent). Providers without function calling say so. */
    default Agent.Reply converse(AiSettings settings, String systemPrompt, java.util.List<Agent.Msg> messages, java.util.List<Agent.Tool> tools) {
        return Agent.Reply.failed("Mô hình hiện tại không hỗ trợ gọi công cụ");
    }

    record Completion(String text, int promptTokens, int completionTokens, boolean estimated, String error) {
        public static Completion failed(String error, int promptTokens) {
            return new Completion(null, promptTokens, 0, true, error);
        }
    }

    // ~4 characters per token: good enough to estimate the cost when the API doesn't say
    static int estimateTokens(String s) {
        return s == null ? 0 : Math.max(1, s.length() / 4);
    }
}
