package com.hippocampus.ai.application.provider;

import java.time.Duration;
import java.util.Objects;

import com.hippocampus.ai.application.routing.ProviderId;

public record ProviderExecutionResult(
        ProviderId providerId,
        String modelId,
        String rawContent,
        ProviderUsage usage,
        Duration latency,
        int retryCount,
        int providerInvocationCount) {

    public ProviderExecutionResult(
            ProviderId providerId,
            String modelId,
            String rawContent,
            ProviderUsage usage,
            Duration latency) {
        this(providerId, modelId, rawContent, usage, latency, 0, 0);
    }

    public ProviderExecutionResult {
        Objects.requireNonNull(providerId, "providerId must not be null");
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("modelId must not be blank");
        }
        if (rawContent == null || rawContent.isBlank()) {
            throw new IllegalArgumentException("rawContent must not be blank");
        }
        Objects.requireNonNull(usage, "usage must not be null");
        Objects.requireNonNull(latency, "latency must not be null");
        if (latency.isNegative()) {
            throw new IllegalArgumentException("latency must not be negative");
        }
        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount must not be negative");
        }
        if (providerInvocationCount < 0) {
            throw new IllegalArgumentException("providerInvocationCount must not be negative");
        }
    }

    public ProviderExecutionResult withExecutionMetadata(int retries, int invocationCount) {
        return new ProviderExecutionResult(
                providerId, modelId, rawContent, usage, latency, retries, invocationCount);
    }
}
