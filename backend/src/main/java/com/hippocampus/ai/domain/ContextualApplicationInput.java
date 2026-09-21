package com.hippocampus.ai.domain;

import java.util.Objects;

public record ContextualApplicationInput(
        String targetConcept,
        String learningObjective,
        ApplicationLevel applicationLevel) implements AiTaskContext {

    public ContextualApplicationInput {
        targetConcept = ContractChecks.requiredText(targetConcept, "targetConcept");
        learningObjective = ContractChecks.requiredText(learningObjective, "learningObjective");
        Objects.requireNonNull(applicationLevel, "applicationLevel must not be null");
    }
}
