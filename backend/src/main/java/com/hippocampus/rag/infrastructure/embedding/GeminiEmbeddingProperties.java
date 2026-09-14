package com.hippocampus.rag.infrastructure.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hippocampus.rag.embedding.gemini")
public final class GeminiEmbeddingProperties {
    private boolean enabled;
    private String model = "gemini-embedding-2";
    private int dimension = 768;
    private String apiKey;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getDimension() {
        return dimension;
    }

    public void setDimension(int dimension) {
        this.dimension = dimension;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    void validateEnabledConfiguration() {
        if (!enabled || model == null || model.isBlank() || dimension < 1 || apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Enabled Gemini embedding configuration is invalid");
        }
    }
}
