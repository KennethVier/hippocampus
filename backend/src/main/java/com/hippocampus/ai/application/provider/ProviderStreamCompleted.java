package com.hippocampus.ai.application.provider;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import com.hippocampus.ai.application.routing.ProviderId;

public record ProviderStreamCompleted(
        ProviderId providerId,
        String modelId,
        ProviderUsage usage,
        Duration latency,
        Optional<String> finishReason) implements ProviderStreamEvent {

    public ProviderStreamCompleted {
        Objects.requireNonNull(providerId, "providerId must not be null");
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("modelId must not be blank");
        }
        Objects.requireNonNull(usage, "usage must not be null");
        Objects.requireNonNull(latency, "latency must not be null");
        if (latency.isNegative()) {
            throw new IllegalArgumentException("latency must not be negative");
        }
        Objects.requireNonNull(finishReason, "finishReason must not be null");
        finishReason = finishReason.filter(value -> !value.isBlank());
    }
}
