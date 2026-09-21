package com.hippocampus.ai.domain;

import java.util.List;
import java.util.Objects;

public record ContextualApplicationResult(
        String scenario,
        String question,
        String targetConcept,
        List<String> requiredReasoning,
        String expectedAnswer,
        List<String> feedbackPoints,
        ApplicationDifficulty difficulty,
        List<String> sourceReferences,
        List<String> limitations) {

    public ContextualApplicationResult {
        scenario = ContractChecks.requiredText(scenario, "scenario");
        question = ContractChecks.requiredText(question, "question");
        targetConcept = ContractChecks.requiredText(targetConcept, "targetConcept");
        requiredReasoning = ContractChecks.immutableList(requiredReasoning, "requiredReasoning");
        expectedAnswer = ContractChecks.requiredText(expectedAnswer, "expectedAnswer");
        feedbackPoints = ContractChecks.immutableList(feedbackPoints, "feedbackPoints");
        Objects.requireNonNull(difficulty, "difficulty must not be null");
        sourceReferences = ContractChecks.immutableList(sourceReferences, "sourceReferences");
        limitations = ContractChecks.immutableList(limitations, "limitations");
    }
}
