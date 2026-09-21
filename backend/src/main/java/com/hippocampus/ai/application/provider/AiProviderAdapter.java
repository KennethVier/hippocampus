package com.hippocampus.ai.application.provider;

import com.hippocampus.ai.application.routing.ProviderId;
import com.hippocampus.ai.domain.AiTaskType;

public interface AiProviderAdapter {
    ProviderId providerId();

    boolean supports(AiTaskType taskType);

    ProviderExecutionResult execute(ProviderExecutionRequest request);
}
