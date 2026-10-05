package com.hippocampus.ai.domain;

import java.util.List;
import java.util.Objects;

public record ResponseEvaluationV4Result(
        List<ResponseEvaluationJudgment> judgments,
        ResponseEvaluationAssessability assessability,
        String feedback,
        RecommendedAction recommendedAction,
        List<String> sourceReferences,
        List<String> limitations) {

    public ResponseEvaluationV4Result {
        judgments = ContractChecks.immutableList(judgments, "judgments");
        Objects.requireNonNull(assessability, "assessability must not be null");
        feedback = ContractChecks.requiredText(feedback, "feedback");
        Objects.requireNonNull(recommendedAction, "recommendedAction must not be null");
        sourceReferences = ContractChecks.immutableList(sourceReferences, "sourceReferences");
        limitations = ContractChecks.immutableList(limitations, "limitations");
    }
}
