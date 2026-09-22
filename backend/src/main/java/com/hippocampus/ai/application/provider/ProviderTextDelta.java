package com.hippocampus.ai.application.provider;

public record ProviderTextDelta(String rawText) implements ProviderStreamEvent {
    public ProviderTextDelta {
        if (rawText == null || rawText.isEmpty()) {
            throw new IllegalArgumentException("rawText must not be empty");
        }
    }
}
