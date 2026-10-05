package com.hippocampus.ai.application.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.hippocampus.ai.domain.RecommendedAction;
import com.hippocampus.ai.domain.ResponseEvaluationAssessability;
import com.hippocampus.ai.domain.ResponseEvaluationInput;
import com.hippocampus.ai.domain.ResponseEvaluationJudgment;
import com.hippocampus.ai.domain.ResponseEvaluationJudgmentStatus;
import com.hippocampus.ai.domain.ResponseEvaluationResult;
import com.hippocampus.ai.domain.ResponseEvaluationV4Result;

class ResponseEvaluationAggregatorTests {

    private final ResponseEvaluationAggregator aggregator = new ResponseEvaluationAggregator();

    @Test
    void acceptsMissingDetailsAndUsesApplicationOwnedConceptIdentity() {
        ResponseEvaluationResult result = aggregate(judgment(
                0,
                "  provider formatting differs  ",
                ResponseEvaluationJudgmentStatus.MISSING,
                List.of(),
                List.of(),
                List.of("Mechanism was not stated"),
                List.of()));

        assertThat(result.missingConcepts()).containsExactly(
                "Canonical expected concept",
                "Mechanism was not stated");
    }

    @Test
    void acceptsContradictedJudgmentWithMissingDetails() {
        ResponseEvaluationResult result = aggregate(judgment(
                0,
                "CANONICAL EXPECTED CONCEPT.",
                ResponseEvaluationJudgmentStatus.CONTRADICTED,
                List.of("Relevant student claim"),
                List.of(),
                List.of("Correct mechanism was absent"),
                List.of("Demonstrated error")));

        assertThat(result.missingConcepts()).containsExactly(
                "Canonical expected concept",
                "Correct mechanism was absent");
        assertThat(result.misconceptions()).containsExactly("Demonstrated error");
    }

    @Test
    void rejectsIncompleteCoverage() {
        assertFailure(
                new ResponseEvaluationV4Result(
                        List.of(),
                        ResponseEvaluationAssessability.EVALUABLE,
                        "Feedback",
                        RecommendedAction.RETRY,
                        List.of(),
                        List.of()),
                ResponseEvaluationAggregationFailureReason.INCOMPLETE_COVERAGE);
    }

    @Test
    void rejectsDuplicateConceptIndex() {
        ResponseEvaluationInput twoConcepts = new ResponseEvaluationInput(
                "Question",
                List.of("First canonical concept", "Second canonical concept"),
                "Expected answer",
                "Student response");
        ResponseEvaluationJudgment first = judgment(
                0, "First", ResponseEvaluationJudgmentStatus.MISSING,
                List.of(), List.of(), List.of(), List.of());
        ResponseEvaluationJudgment duplicate = judgment(
                0, "Second", ResponseEvaluationJudgmentStatus.MISSING,
                List.of(), List.of(), List.of(), List.of());

        assertFailure(
                twoConcepts,
                assessment(List.of(first, duplicate)),
                ResponseEvaluationAggregationFailureReason.DUPLICATE_CONCEPT_INDEX);
    }

    @Test
    void rejectsOutOfRangeConceptIndex() {
        assertFailure(
                assessment(List.of(judgment(
                        -1, "Provider echo", ResponseEvaluationJudgmentStatus.MISSING,
                        List.of(), List.of(), List.of(), List.of()))),
                ResponseEvaluationAggregationFailureReason.OUT_OF_RANGE_CONCEPT_INDEX);
    }

    @Test
    void rejectsMissingJudgmentWithSupportedComponents() {
        assertJudgmentShapeFailure(
                judgment(
                        0, "Provider echo", ResponseEvaluationJudgmentStatus.MISSING,
                        List.of(), List.of("Invented support"), List.of(), List.of()),
                ResponseEvaluationAggregationFailureReason.INVALID_MISSING_SHAPE);
    }

    @Test
    void rejectsMissingJudgmentWithFabricatedMisconception() {
        assertJudgmentShapeFailure(
                judgment(
                        0, "Provider echo", ResponseEvaluationJudgmentStatus.MISSING,
                        List.of(), List.of(), List.of(), List.of("Invented misconception")),
                ResponseEvaluationAggregationFailureReason.INVALID_MISSING_SHAPE);
    }

    @Test
    void rejectsContradictedJudgmentWithoutDemonstratedMisconception() {
        assertJudgmentShapeFailure(
                judgment(
                        0, "Provider echo", ResponseEvaluationJudgmentStatus.CONTRADICTED,
                        List.of("Relevant claim"), List.of(), List.of("Missing mechanism"), List.of()),
                ResponseEvaluationAggregationFailureReason.INVALID_CONTRADICTED_SHAPE);
    }

    @Test
    void keepsSupportedShapeStrict() {
        assertJudgmentShapeFailure(
                judgment(
                        0, "Provider echo", ResponseEvaluationJudgmentStatus.SUPPORTED,
                        List.of("Relevant claim"), List.of("Supported component"),
                        List.of("Unexpected gap"), List.of()),
                ResponseEvaluationAggregationFailureReason.INVALID_SUPPORTED_SHAPE);
    }

    @Test
    void keepsPartialShapeStrict() {
        assertJudgmentShapeFailure(
                judgment(
                        0, "Provider echo", ResponseEvaluationJudgmentStatus.PARTIAL,
                        List.of("Relevant claim"), List.of("Supported component"), List.of(), List.of()),
                ResponseEvaluationAggregationFailureReason.INVALID_PARTIAL_SHAPE);
    }

    @Test
    void rejectsBlankDiagnosticEntriesWithStatusSpecificReason() {
        assertJudgmentShapeFailure(
                judgment(
                        0, "Provider echo", ResponseEvaluationJudgmentStatus.MISSING,
                        List.of(), List.of(), List.of(" "), List.of()),
                ResponseEvaluationAggregationFailureReason.INVALID_MISSING_SHAPE);
    }

    @Test
    void keepsNonEvaluableShapeStrict() {
        ResponseEvaluationV4Result invalid = new ResponseEvaluationV4Result(
                List.of(judgment(
                        0, "Provider echo", ResponseEvaluationJudgmentStatus.PARTIAL,
                        List.of("Relevant claim"), List.of("Supported component"),
                        List.of("Gap"), List.of())),
                ResponseEvaluationAssessability.AMBIGUOUS_RESPONSE,
                "Feedback",
                RecommendedAction.MANUAL_REVIEW,
                List.of(),
                List.of("Ambiguous response"));

        assertFailure(invalid, ResponseEvaluationAggregationFailureReason.INVALID_ASSESSABILITY_SHAPE);
    }

    private ResponseEvaluationResult aggregate(ResponseEvaluationJudgment judgment) {
        return aggregator.aggregate(input(), assessment(List.of(judgment)));
    }

    private void assertJudgmentShapeFailure(
            ResponseEvaluationJudgment judgment,
            ResponseEvaluationAggregationFailureReason reason) {
        assertFailure(assessment(List.of(judgment)), reason);
    }

    private void assertFailure(
            ResponseEvaluationV4Result assessment,
            ResponseEvaluationAggregationFailureReason reason) {
        assertFailure(input(), assessment, reason);
    }

    private void assertFailure(
            ResponseEvaluationInput input,
            ResponseEvaluationV4Result assessment,
            ResponseEvaluationAggregationFailureReason reason) {
        assertThatThrownBy(() -> aggregator.aggregate(input, assessment))
                .isInstanceOfSatisfying(ResponseEvaluationAggregationException.class,
                        failure -> assertThat(failure.reason()).isEqualTo(reason));
    }

    private static ResponseEvaluationInput input() {
        return new ResponseEvaluationInput(
                "Question",
                List.of("Canonical expected concept"),
                "Expected answer",
                "Student response");
    }

    private static ResponseEvaluationV4Result assessment(
            List<ResponseEvaluationJudgment> judgments) {
        return new ResponseEvaluationV4Result(
                judgments,
                ResponseEvaluationAssessability.EVALUABLE,
                "Feedback",
                RecommendedAction.RETRY,
                List.of(),
                List.of());
    }

    private static ResponseEvaluationJudgment judgment(
            int index,
            String providerEcho,
            ResponseEvaluationJudgmentStatus status,
            List<String> claims,
            List<String> supported,
            List<String> missing,
            List<String> misconceptions) {
        return new ResponseEvaluationJudgment(
                index, providerEcho, claims, status, supported, missing, misconceptions);
    }
}
