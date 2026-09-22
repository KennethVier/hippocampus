package com.hippocampus.ai.application.provider;

import java.util.Objects;

import com.hippocampus.ai.application.routing.ProviderId;

public final class ProviderExecutionException extends RuntimeException {
    private final ProviderId providerId;
    private final ProviderFailureType failureType;

    public ProviderExecutionException(ProviderId providerId, ProviderFailureType failureType) {
        super("AI provider execution failed: " + Objects.requireNonNull(failureType, "failureType must not be null"));
        this.providerId = Objects.requireNonNull(providerId, "providerId must not be null");
        this.failureType = failureType;
    }

    public ProviderId providerId() {
        return providerId;
    }

    public ProviderFailureType failureType() {
        return failureType;
    }
}
