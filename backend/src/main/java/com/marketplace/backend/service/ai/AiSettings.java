package com.marketplace.backend.service.ai;

/**
 * The AI configuration in force for one call (admin settings, falling back to the environment variables).
 * provider: openai | claude | huggingface
 */
public record AiSettings(boolean enabled, String provider, String openaiKey, String openaiUrl, String openaiModel,
                         String hfUrl, String hfToken, String claudeKey, String claudeUrl, String claudeModel) {

    public boolean huggingFace() {
        return "huggingface".equalsIgnoreCase(provider);
    }

    public boolean claude() {
        return "claude".equalsIgnoreCase(provider);
    }

    /** "openai" | "claude" | "huggingface" */
    public String providerKey() {
        return huggingFace() ? "huggingface" : claude() ? "claude" : "openai";
    }

    public String model() {
        return huggingFace() ? "vistral-7b-shoppi" : claude() ? claudeModel : openaiModel;
    }

    public boolean configured() {
        if (huggingFace()) return hfUrl != null && !hfUrl.isBlank();
        if (claude()) return claudeKey != null && !claudeKey.isBlank();
        return openaiKey != null && !openaiKey.isBlank();
    }

    /** OpenAI key available (the semantic search's embeddings use OpenAI whatever model answers the chat). */
    public boolean openaiReady() {
        return openaiKey != null && !openaiKey.isBlank();
    }
}
