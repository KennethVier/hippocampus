package com.hippocampus.ai.application.provider;

import java.util.concurrent.CancellationException;

public final class ProviderExecutionCancellationException extends CancellationException {
    private final int providerInvocationCount;

    public ProviderExecutionCancellationException(int providerInvocationCount) {
        super("AI provider execution cancelled");
        if (providerInvocationCount < 0) {
            throw new IllegalArgumentException("providerInvocationCount must not be negative");
        }
        this.providerInvocationCount = providerInvocationCount;
    }

    public int providerInvocationCount() {
        return providerInvocationCount;
    }
}
