package com.hippocampus.ai.application.provider;

import java.util.Objects;

import com.hippocampus.ai.application.prompt.PromptContext;
import com.hippocampus.ai.application.routing.ProviderRoute;
import com.hippocampus.ai.domain.AiOutputContract;
import com.hippocampus.ai.domain.AiTaskType;

public record ProviderExecutionRequest(
        AiTaskType taskType,
        AiOutputContract outputContract,
        PromptContext promptContext,
        ProviderRoute.Target target) {

    public ProviderExecutionRequest {
        Objects.requireNonNull(taskType, "taskType must not be null");
        Objects.requireNonNull(outputContract, "outputContract must not be null");
        Objects.requireNonNull(promptContext, "promptContext must not be null");
        Objects.requireNonNull(target, "target must not be null");
    }
}
