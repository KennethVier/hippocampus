package com.hippocampus.ai.domain;

import java.util.List;
import java.util.Objects;

public record QuestionGenerationInput(
        String learningObjective,
        String targetConcept,
        ActivityType activityType,
        QuestionDifficulty difficulty,
        List<String> recentQuestionIntents,
        String repetitionPurpose) implements AiTaskContext {

    public QuestionGenerationInput {
        learningObjective = ContractChecks.requiredText(learningObjective, "learningObjective");
        targetConcept = ContractChecks.requiredText(targetConcept, "targetConcept");
        Objects.requireNonNull(activityType, "activityType must not be null");
        Objects.requireNonNull(difficulty, "difficulty must not be null");
        recentQuestionIntents = ContractChecks.immutableList(
                recentQuestionIntents, "recentQuestionIntents");
        if (repetitionPurpose != null && repetitionPurpose.isBlank()) {
            throw new IllegalArgumentException("repetitionPurpose must be null or non-blank");
        }
    }
}
