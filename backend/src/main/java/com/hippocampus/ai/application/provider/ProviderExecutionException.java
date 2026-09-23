package com.hippocampus.ai.application.provider;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import com.hippocampus.ai.application.routing.ProviderId;

public final class ProviderExecutionException extends RuntimeException {
    private final ProviderId providerId;
    private final ProviderFailureType failureType;
    private final Optional<Duration> retryAfter;
    private final int retryCount;
    private final int providerInvocationCount;

    public ProviderExecutionException(ProviderId providerId, ProviderFailureType failureType) {
        this(providerId, failureType, Optional.empty());
    }

    public ProviderExecutionException(
            ProviderId providerId,
            ProviderFailureType failureType,
            Optional<Duration> retryAfter) {
        this(providerId, failureType, retryAfter, 0, 0);
    }

    private ProviderExecutionException(
            ProviderId providerId,
            ProviderFailureType failureType,
            Optional<Duration> retryAfter,
            int retryCount,
            int providerInvocationCount) {
        super("AI provider execution failed: " + Objects.requireNonNull(failureType, "failureType must not be null"));
        this.providerId = Objects.requireNonNull(providerId, "providerId must not be null");
        this.failureType = failureType;
        this.retryAfter = Objects.requireNonNull(retryAfter, "retryAfter must not be null");
        if (retryAfter.filter(duration -> duration.isZero() || duration.isNegative()).isPresent()) {
            throw new IllegalArgumentException("retryAfter must be positive when present");
        }
        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount must not be negative");
        }
        this.retryCount = retryCount;
        if (providerInvocationCount < 0) {
            throw new IllegalArgumentException("providerInvocationCount must not be negative");
        }
        this.providerInvocationCount = providerInvocationCount;
    }

    public ProviderId providerId() {
        return providerId;
    }

    public ProviderFailureType failureType() {
        return failureType;
    }

    public Optional<Duration> retryAfter() {
        return retryAfter;
    }

    public int retryCount() {
        return retryCount;
    }

    public int providerInvocationCount() {
        return providerInvocationCount;
    }

    public ProviderExecutionException withExecutionMetadata(int retries, int invocationCount) {
        return new ProviderExecutionException(
                providerId, failureType, retryAfter, retries, invocationCount);
    }
}
