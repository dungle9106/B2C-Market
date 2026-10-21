package com.marketplace.backend.service.ai;

/**
 * The AI configuration in force for one call (admin settings, falling back to the environment variables).
 * provider: openai | huggingface
 */
public record AiSettings(boolean enabled, String provider, String openaiKey, String openaiUrl, String openaiModel,
                         String hfUrl, String hfToken) {

    public boolean huggingFace() {
        return "huggingface".equalsIgnoreCase(provider);
    }

    public String model() {
        return huggingFace() ? "vistral-7b-shoppi" : openaiModel;
    }

    public boolean configured() {
        return huggingFace() ? hfUrl != null && !hfUrl.isBlank() : openaiKey != null && !openaiKey.isBlank();
    }
}
