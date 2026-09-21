package com.hippocampus.ai.domain;

import java.util.Objects;

public record ExplanationInput(
        String learningObjective,
        String targetConcept,
        ExplanationMode explanationMode) implements AiTaskContext {

    public ExplanationInput {
        learningObjective = ContractChecks.requiredText(learningObjective, "learningObjective");
        targetConcept = ContractChecks.requiredText(targetConcept, "targetConcept");
        Objects.requireNonNull(explanationMode, "explanationMode must not be null");
    }
}
