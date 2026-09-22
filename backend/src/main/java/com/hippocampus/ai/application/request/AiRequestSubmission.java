package com.hippocampus.ai.application.request;

import java.util.Objects;
import java.util.UUID;

import com.hippocampus.ai.application.provider.ProviderExecutionRequest;

/** Application-owned submission metadata kept outside provider execution contracts. */
public record AiRequestSubmission(
        UUID userId,
        ProviderExecutionRequest providerRequest) {

    public AiRequestSubmission {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(providerRequest, "providerRequest must not be null");
    }
}
