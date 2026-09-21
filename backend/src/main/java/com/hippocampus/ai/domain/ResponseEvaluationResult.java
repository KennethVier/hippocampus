package com.hippocampus.ai.domain;

import java.util.List;
import java.util.Objects;

public record ResponseEvaluationResult(
        Evaluation evaluation,
        List<String> correctConcepts,
        List<String> missingConcepts,
        List<String> misconceptions,
        String feedback,
        EvaluationCertainty certainty,
        RecommendedAction recommendedAction,
        List<String> sourceReferences,
        List<String> limitations) {

    public ResponseEvaluationResult {
        Objects.requireNonNull(evaluation, "evaluation must not be null");
        correctConcepts = ContractChecks.immutableList(correctConcepts, "correctConcepts");
        missingConcepts = ContractChecks.immutableList(missingConcepts, "missingConcepts");
        misconceptions = ContractChecks.immutableList(misconceptions, "misconceptions");
        feedback = ContractChecks.requiredText(feedback, "feedback");
        Objects.requireNonNull(certainty, "certainty must not be null");
        Objects.requireNonNull(recommendedAction, "recommendedAction must not be null");
        sourceReferences = ContractChecks.immutableList(sourceReferences, "sourceReferences");
        limitations = ContractChecks.immutableList(limitations, "limitations");
    }
}
