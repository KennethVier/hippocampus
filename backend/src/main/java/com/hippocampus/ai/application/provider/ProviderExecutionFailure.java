package com.hippocampus.ai.application.provider;

import java.util.Objects;

/** Internal carrier for normalized request-manager execution metadata. */
public final class ProviderExecutionFailure extends RuntimeException {
    private final int retryCount;
    private final int providerInvocationCount;

    public ProviderExecutionFailure(
            Throwable cause,
            int retryCount,
            int providerInvocationCount) {
        super("AI provider execution failed", Objects.requireNonNull(cause, "cause must not be null"));
        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount must not be negative");
        }
        if (providerInvocationCount < 0) {
            throw new IllegalArgumentException("providerInvocationCount must not be negative");
        }
        this.retryCount = retryCount;
        this.providerInvocationCount = providerInvocationCount;
    }

    public int retryCount() {
        return retryCount;
    }

    public int providerInvocationCount() {
        return providerInvocationCount;
    }
}
