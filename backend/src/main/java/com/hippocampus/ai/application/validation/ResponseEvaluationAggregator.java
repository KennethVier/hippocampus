package com.hippocampus.ai.application.validation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.hippocampus.ai.domain.Evaluation;
import com.hippocampus.ai.domain.EvaluationCertainty;
import com.hippocampus.ai.domain.ResponseEvaluationAssessability;
import com.hippocampus.ai.domain.ResponseEvaluationInput;
import com.hippocampus.ai.domain.ResponseEvaluationJudgment;
import com.hippocampus.ai.domain.ResponseEvaluationJudgmentStatus;
import com.hippocampus.ai.domain.ResponseEvaluationResult;
import com.hippocampus.ai.domain.ResponseEvaluationV4Result;

public final class ResponseEvaluationAggregator {

    public ResponseEvaluationResult aggregate(
            ResponseEvaluationInput input,
            ResponseEvaluationV4Result assessment) {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(assessment, "assessment must not be null");

        List<ResponseEvaluationJudgment> judgments = validateCoverage(input, assessment);
        LinkedHashSet<String> correctConcepts = new LinkedHashSet<>();
        LinkedHashSet<String> missingConcepts = new LinkedHashSet<>();
        LinkedHashSet<String> misconceptions = new LinkedHashSet<>();

        for (ResponseEvaluationJudgment judgment : judgments) {
            validateJudgment(judgment);
            correctConcepts.addAll(judgment.supportedComponents());
            misconceptions.addAll(judgment.demonstratedMisconceptions());
            String canonicalExpectedConcept = input.expectedConcepts().get(judgment.expectedConceptIndex());
            switch (judgment.status()) {
                case SUPPORTED -> {
                    // Complete coverage contributes no missing concept.
                }
                case PARTIAL -> missingConcepts.addAll(judgment.missingComponents());
                case MISSING, CONTRADICTED -> {
                    missingConcepts.add(canonicalExpectedConcept);
                    missingConcepts.addAll(judgment.missingComponents());
                }
            }
        }

        boolean evaluable = assessment.assessability() == ResponseEvaluationAssessability.EVALUABLE;
        if (assessment.limitations().stream().anyMatch(String::isBlank)
                || (!evaluable && (assessment.limitations().isEmpty()
                        || judgments.stream().anyMatch(judgment ->
                                judgment.status() != ResponseEvaluationJudgmentStatus.MISSING)))) {
            throw failure(ResponseEvaluationAggregationFailureReason.INVALID_ASSESSABILITY_SHAPE);
        }

        Evaluation evaluation;
        if (!evaluable) {
            evaluation = Evaluation.UNCERTAIN;
        } else if (judgments.stream().allMatch(judgment ->
                judgment.status() == ResponseEvaluationJudgmentStatus.SUPPORTED)) {
            evaluation = Evaluation.CORRECT;
        } else if (!correctConcepts.isEmpty()) {
            evaluation = Evaluation.PARTIAL;
        } else {
            evaluation = Evaluation.INCORRECT;
        }

        return new ResponseEvaluationResult(
                evaluation,
                List.copyOf(correctConcepts),
                List.copyOf(missingConcepts),
                List.copyOf(misconceptions),
                assessment.feedback(),
                evaluable ? EvaluationCertainty.SUFFICIENT : EvaluationCertainty.LIMITED,
                assessment.recommendedAction(),
                assessment.sourceReferences(),
                assessment.limitations());
    }

    private static List<ResponseEvaluationJudgment> validateCoverage(
            ResponseEvaluationInput input,
            ResponseEvaluationV4Result assessment) {
        List<String> expectedConcepts = input.expectedConcepts();
        if (expectedConcepts.isEmpty() || assessment.judgments().size() != expectedConcepts.size()) {
            throw failure(ResponseEvaluationAggregationFailureReason.INCOMPLETE_COVERAGE);
        }

        Set<Integer> coveredIndexes = new HashSet<>();
        List<ResponseEvaluationJudgment> ordered = new ArrayList<>(assessment.judgments());
        ordered.sort(java.util.Comparator.comparingInt(ResponseEvaluationJudgment::expectedConceptIndex));
        for (ResponseEvaluationJudgment judgment : ordered) {
            int index = judgment.expectedConceptIndex();
            if (index < 0 || index >= expectedConcepts.size()) {
                throw failure(ResponseEvaluationAggregationFailureReason.OUT_OF_RANGE_CONCEPT_INDEX);
            }
            if (!coveredIndexes.add(index)) {
                throw failure(ResponseEvaluationAggregationFailureReason.DUPLICATE_CONCEPT_INDEX);
            }
        }
        return List.copyOf(ordered);
    }

    private static void validateJudgment(ResponseEvaluationJudgment judgment) {
        ResponseEvaluationAggregationFailureReason invalidShapeReason = switch (judgment.status()) {
            case SUPPORTED -> ResponseEvaluationAggregationFailureReason.INVALID_SUPPORTED_SHAPE;
            case PARTIAL -> ResponseEvaluationAggregationFailureReason.INVALID_PARTIAL_SHAPE;
            case MISSING -> ResponseEvaluationAggregationFailureReason.INVALID_MISSING_SHAPE;
            case CONTRADICTED -> ResponseEvaluationAggregationFailureReason.INVALID_CONTRADICTED_SHAPE;
        };
        if (containsBlank(judgment.studentClaims())
                || containsBlank(judgment.supportedComponents())
                || containsBlank(judgment.missingComponents())
                || containsBlank(judgment.demonstratedMisconceptions())) {
            throw failure(invalidShapeReason);
        }

        boolean valid = switch (judgment.status()) {
            case SUPPORTED -> !judgment.studentClaims().isEmpty()
                    && !judgment.supportedComponents().isEmpty()
                    && judgment.missingComponents().isEmpty()
                    && judgment.demonstratedMisconceptions().isEmpty();
            case PARTIAL -> !judgment.studentClaims().isEmpty()
                    && !judgment.supportedComponents().isEmpty()
                    && (!judgment.missingComponents().isEmpty()
                            || !judgment.demonstratedMisconceptions().isEmpty());
            case MISSING -> judgment.supportedComponents().isEmpty()
                    && judgment.demonstratedMisconceptions().isEmpty();
            case CONTRADICTED -> !judgment.studentClaims().isEmpty()
                    && judgment.supportedComponents().isEmpty()
                    && !judgment.demonstratedMisconceptions().isEmpty();
        };
        if (!valid) {
            throw failure(invalidShapeReason);
        }
    }

    private static boolean containsBlank(List<String> values) {
        return values.stream().anyMatch(String::isBlank);
    }

    private static ResponseEvaluationAggregationException failure(
            ResponseEvaluationAggregationFailureReason reason) {
        return new ResponseEvaluationAggregationException(reason);
    }
}
