package com.hippocampus.ai.application.provider;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import com.hippocampus.ai.application.routing.ProviderId;

public final class ProviderExecutionException extends RuntimeException {
    private final ProviderId providerId;
    private final ProviderFailureType failureType;
    private final Optional<Duration> retryAfter;

    public ProviderExecutionException(ProviderId providerId, ProviderFailureType failureType) {
        this(providerId, failureType, Optional.empty());
    }

    public ProviderExecutionException(
            ProviderId providerId,
            ProviderFailureType failureType,
            Optional<Duration> retryAfter) {
        super("AI provider execution failed: " + Objects.requireNonNull(failureType, "failureType must not be null"));
        this.providerId = Objects.requireNonNull(providerId, "providerId must not be null");
        this.failureType = failureType;
        this.retryAfter = Objects.requireNonNull(retryAfter, "retryAfter must not be null");
        if (retryAfter.filter(duration -> duration.isZero() || duration.isNegative()).isPresent()) {
            throw new IllegalArgumentException("retryAfter must be positive when present");
        }
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
}
