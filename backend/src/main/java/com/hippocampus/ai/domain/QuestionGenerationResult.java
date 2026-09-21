package com.hippocampus.ai.domain;

import java.util.List;
import java.util.Objects;

public record QuestionGenerationResult(
        ActivityType activityType,
        String concept,
        String learningObjective,
        String question,
        List<QuestionOption> options,
        String correctOption,
        String expectedAnswer,
        String explanation,
        QuestionDifficulty difficulty,
        List<String> sourceReferences,
        List<String> limitations) {

    public QuestionGenerationResult {
        Objects.requireNonNull(activityType, "activityType must not be null");
        concept = ContractChecks.requiredText(concept, "concept");
        learningObjective = ContractChecks.requiredText(learningObjective, "learningObjective");
        question = ContractChecks.requiredText(question, "question");
        options = ContractChecks.immutableList(options, "options");
        if (correctOption != null && correctOption.isBlank()) {
            throw new IllegalArgumentException("correctOption must be null or non-blank");
        }
        expectedAnswer = ContractChecks.requiredText(expectedAnswer, "expectedAnswer");
        explanation = ContractChecks.requiredText(explanation, "explanation");
        Objects.requireNonNull(difficulty, "difficulty must not be null");
        sourceReferences = ContractChecks.immutableList(sourceReferences, "sourceReferences");
        limitations = ContractChecks.immutableList(limitations, "limitations");
    }
}
