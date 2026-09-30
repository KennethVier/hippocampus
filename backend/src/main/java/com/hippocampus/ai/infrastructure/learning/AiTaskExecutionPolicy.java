package com.hippocampus.ai.infrastructure.learning;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import com.hippocampus.ai.domain.AiTaskType;

public final class AiTaskExecutionPolicy {

    private final Map<AiTaskType, AiTaskExecutionOptions> optionsByTask;

    public AiTaskExecutionPolicy(Map<AiTaskType, AiTaskExecutionOptions> optionsByTask) {
        Objects.requireNonNull(optionsByTask, "optionsByTask must not be null");
        EnumMap<AiTaskType, AiTaskExecutionOptions> copy = new EnumMap<>(AiTaskType.class);
        copy.putAll(optionsByTask);
        this.optionsByTask = Map.copyOf(copy);
    }

    public AiTaskExecutionOptions optionsFor(AiTaskType taskType) {
        Objects.requireNonNull(taskType, "taskType must not be null");
        AiTaskExecutionOptions options = optionsByTask.get(taskType);
        if (options == null) {
            throw new IllegalArgumentException("No AI task execution policy configured for " + taskType);
        }
        return options;
    }
}
