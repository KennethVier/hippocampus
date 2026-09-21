package com.hippocampus.ai.infrastructure.provider;

import java.util.List;

import com.hippocampus.ai.application.prompt.PromptContext;
import com.hippocampus.ai.application.prompt.PromptId;
import com.hippocampus.ai.application.provider.ProviderExecutionRequest;
import com.hippocampus.ai.application.routing.ProviderId;
import com.hippocampus.ai.application.routing.ProviderRoute;
import com.hippocampus.ai.domain.AiOutputContract;
import com.hippocampus.ai.domain.AiTaskType;

public final class ProviderTestFixtures {
    private ProviderTestFixtures() {}

    public static ProviderExecutionRequest request(ProviderId providerId, String modelId) {
        return new ProviderExecutionRequest(
                AiTaskType.EXPLANATION,
                AiOutputContract.EXPLANATION,
                new PromptContext(
                        PromptId.HIPPOCAMPUS_SYSTEM_V1,
                        PromptId.EXPLANATION_V1,
                        "system-policy-secret-marker",
                        "student-task-secret-marker",
                        10,
                        64,
                        List.of()),
                new ProviderRoute.Target(providerId, modelId));
    }
}
