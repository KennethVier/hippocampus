package com.hippocampus.ai.infrastructure.provider.gemini;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hippocampus.ai.providers.gemini")
public final class GeminiProviderProperties {
    private boolean enabled;
    private String apiKey;
    private String defaultModel = "gemini-2.5-flash";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public void setDefaultModel(String defaultModel) {
        this.defaultModel = defaultModel;
    }

    void validateEnabledConfiguration() {
        if (!enabled || apiKey == null || apiKey.isBlank() || defaultModel == null || defaultModel.isBlank()) {
            throw new IllegalStateException("Enabled Gemini provider configuration is invalid");
        }
    }
}
