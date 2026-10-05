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
            switch (judgment.status()) {
                case SUPPORTED -> {
                    // Complete coverage contributes no missing concept.
                }
                case PARTIAL -> missingConcepts.addAll(judgment.missingComponents());
                case MISSING, CONTRADICTED -> missingConcepts.add(judgment.expectedConcept());
            }
        }

        boolean evaluable = assessment.assessability() == ResponseEvaluationAssessability.EVALUABLE;
        if (!evaluable && assessment.limitations().isEmpty()) {
            throw new IllegalArgumentException("non-evaluable assessment must explain its limitation");
        }
        if (!evaluable && judgments.stream().anyMatch(judgment ->
                judgment.status() != ResponseEvaluationJudgmentStatus.MISSING)) {
            throw new IllegalArgumentException(
                    "non-evaluable assessment may contain only MISSING judgments");
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
            throw new IllegalArgumentException("judgments must cover every expected concept exactly once");
        }

        Set<Integer> coveredIndexes = new HashSet<>();
        List<ResponseEvaluationJudgment> ordered = new ArrayList<>(assessment.judgments());
        ordered.sort(java.util.Comparator.comparingInt(ResponseEvaluationJudgment::expectedConceptIndex));
        for (ResponseEvaluationJudgment judgment : ordered) {
            int index = judgment.expectedConceptIndex();
            if (index >= expectedConcepts.size() || !coveredIndexes.add(index)) {
                throw new IllegalArgumentException("invalid or duplicate expected concept index");
            }
            if (!expectedConcepts.get(index).equals(judgment.expectedConcept())) {
                throw new IllegalArgumentException("judgment expected concept does not match request");
            }
        }
        return List.copyOf(ordered);
    }

    private static void validateJudgment(ResponseEvaluationJudgment judgment) {
        requireNonBlank(judgment.studentClaims(), "studentClaims");
        requireNonBlank(judgment.supportedComponents(), "supportedComponents");
        requireNonBlank(judgment.missingComponents(), "missingComponents");
        requireNonBlank(judgment.demonstratedMisconceptions(), "demonstratedMisconceptions");

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
                    && judgment.missingComponents().isEmpty()
                    && judgment.demonstratedMisconceptions().isEmpty();
            case CONTRADICTED -> !judgment.studentClaims().isEmpty()
                    && judgment.supportedComponents().isEmpty()
                    && judgment.missingComponents().isEmpty()
                    && !judgment.demonstratedMisconceptions().isEmpty();
        };
        if (!valid) {
            throw new IllegalArgumentException("judgment fields do not match status " + judgment.status());
        }
    }

    private static void requireNonBlank(List<String> values, String name) {
        if (values.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException(name + " must not contain blank values");
        }
    }
}
