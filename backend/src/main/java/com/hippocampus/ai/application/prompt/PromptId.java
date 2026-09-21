package com.hippocampus.ai.application.prompt;

import com.hippocampus.ai.domain.AiTaskType;
import java.util.Optional;

public enum PromptId {
    HIPPOCAMPUS_SYSTEM_V1(1, null),
    EXPLANATION_V1(1, AiTaskType.EXPLANATION),
    QUESTION_GENERATION_V1(1, AiTaskType.QUESTION_GENERATION),
    RESPONSE_EVALUATION_V1(1, AiTaskType.RESPONSE_EVALUATION),
    CONCEPT_CONNECTION_V1(1, AiTaskType.CONCEPT_CONNECTION),
    CONTEXTUAL_APPLICATION_V1(1, AiTaskType.CONTEXTUAL_APPLICATION),
    STRUCTURED_OUTPUT_REPAIR_V1(1, AiTaskType.STRUCTURED_OUTPUT_REPAIR);

    private final int version;
    private final AiTaskType taskType;

    PromptId(int version, AiTaskType taskType) {
        this.version = version;
        this.taskType = taskType;
    }

    public int version() {
        return version;
    }

    public Optional<AiTaskType> taskType() {
        return Optional.ofNullable(taskType);
    }

    public boolean isSystemPolicy() {
        return taskType == null;
    }

    public boolean supports(AiTaskType candidate) {
        return taskType == candidate;
    }
}
