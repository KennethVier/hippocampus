package com.hippocampus.ai.application.routing;

import com.hippocampus.ai.domain.AiTaskType;
import java.util.Objects;

public final class ProviderRouteUnavailableException extends RuntimeException {

    private final AiTaskType taskType;

    public ProviderRouteUnavailableException(AiTaskType taskType) {
        super("No eligible provider route for task type " + Objects.requireNonNull(taskType, "taskType must not be null"));
        this.taskType = taskType;
    }

    public AiTaskType taskType() {
        return taskType;
    }
}
