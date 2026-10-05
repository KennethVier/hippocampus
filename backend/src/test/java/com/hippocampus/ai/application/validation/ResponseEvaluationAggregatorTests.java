package com.hippocampus.ai.application.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.hippocampus.ai.domain.Evaluation;
import com.hippocampus.ai.domain.EvaluationCertainty;
import com.hippocampus.ai.domain.RecommendedAction;
import com.hippocampus.ai.domain.ResponseEvaluationAssessability;
import com.hippocampus.ai.domain.ResponseEvaluationInput;
import com.hippocampus.ai.domain.ResponseEvaluationJudgment;
import com.hippocampus.ai.domain.ResponseEvaluationJudgmentStatus;
import com.hippocampus.ai.domain.ResponseEvaluationResult;
import com.hippocampus.ai.domain.ResponseEvaluationV4Result;

class ResponseEvaluationAggregatorTests {

    private static final String NERVE = "The radial nerve supplies wrist extensors";
    private static final String EFFECT = "Loss of wrist extension produces wrist drop";

    private final ResponseEvaluationAggregator aggregator = new ResponseEvaluationAggregator();
    private final ResponseEvaluationInput input = new ResponseEvaluationInput(
            "Why can radial nerve injury cause wrist drop?",
            List.of(NERVE, EFFECT),
            "Radial nerve injury denervates wrist extensors, causing loss of extension.",
            "student response");

    @Test
    void derivesCorrectWhenEveryExpectedConceptIsSupported() {
        ResponseEvaluationResult result = aggregate(
                supported(0, NERVE, "Radial nerve supplies the wrist extensors"),
                supported(1, EFFECT, "Loss of wrist extension causes wrist drop"));

        assertThat(result.evaluation()).isEqualTo(Evaluation.CORRECT);
        assertThat(result.correctConcepts()).containsExactly(
                "Radial nerve supplies the wrist extensors",
                "Loss of wrist extension causes wrist drop");
        assertThat(result.missingConcepts()).isEmpty();
        assertThat(result.misconceptions()).isEmpty();
    }

    @Test
    void meaningfulSupportedComponentCannotDisappearOrBecomeIncorrect() {
        ResponseEvaluationResult result = aggregate(
                partial(0, NERVE, "Radial nerve injury is involved", "Wrist extensor supply is not explained"),
                missing(1, EFFECT));

        assertThat(result.evaluation()).isEqualTo(Evaluation.PARTIAL);
        assertThat(result.correctConcepts()).containsExactly("Radial nerve injury is involved");
        assertThat(result.missingConcepts()).containsExactly(
                "Wrist extensor supply is not explained", EFFECT);
    }

    @Test
    void wrongReasoningPreservesSupportedConclusionAndDerivesDemonstratedError() {
        ResponseEvaluationJudgment wrongReasoning = new ResponseEvaluationJudgment(
                1,
                EFFECT,
                List.of("Wrist drop occurs because flexors are activated"),
                ResponseEvaluationJudgmentStatus.PARTIAL,
                List.of("The conclusion identifies wrist drop after radial nerve injury"),
                List.of("Loss of wrist extension is not explained"),
                List.of("Radial nerve injury activates wrist flexors"));

        ResponseEvaluationResult result = aggregate(missing(0, NERVE), wrongReasoning);

        assertThat(result.evaluation()).isEqualTo(Evaluation.PARTIAL);
        assertThat(result.correctConcepts())
                .containsExactly("The conclusion identifies wrist drop after radial nerve injury");
        assertThat(result.missingConcepts()).containsExactly(NERVE, "Loss of wrist extension is not explained");
        assertThat(result.misconceptions()).containsExactly("Radial nerve injury activates wrist flexors");
    }

    @Test
    void uncertainButReasonableResponseWithSupportIsPartialRatherThanIncorrect() {
        ResponseEvaluationResult result = aggregate(
                partial(0, NERVE, "A nerve supplies the muscles that lift the wrist", "The radial nerve is not named"),
                missing(1, EFFECT));

        assertThat(result.evaluation()).isEqualTo(Evaluation.PARTIAL);
        assertThat(result.correctConcepts()).isNotEmpty();
    }

    @Test
    void genuinelyAmbiguousResponseWithoutReliableSupportIsUncertain() {
        ResponseEvaluationV4Result assessment = assessment(
                ResponseEvaluationAssessability.AMBIGUOUS_RESPONSE,
                missing(0, NERVE),
                missing(1, EFFECT));

        ResponseEvaluationResult result = aggregator.aggregate(input, assessment);

        assertThat(result.evaluation()).isEqualTo(Evaluation.UNCERTAIN);
        assertThat(result.certainty()).isEqualTo(EvaluationCertainty.LIMITED);
        assertThat(result.correctConcepts()).isEmpty();
        assertThat(result.missingConcepts()).containsExactly(NERVE, EFFECT);
    }

    @Test
    void emptyOrOffTopicResponseCannotFabricateCorrectConceptsOrMisconceptions() {
        ResponseEvaluationResult result = aggregate(missing(0, NERVE), missing(1, EFFECT));

        assertThat(result.evaluation()).isEqualTo(Evaluation.INCORRECT);
        assertThat(result.correctConcepts()).isEmpty();
        assertThat(result.missingConcepts()).containsExactly(NERVE, EFFECT);
        assertThat(result.misconceptions()).isEmpty();
    }

    @Test
    void rejectsIncompleteDuplicateUnknownAndMismatchedCoverage() {
        assertThatThrownBy(() -> aggregator.aggregate(input, assessment(
                ResponseEvaluationAssessability.EVALUABLE, supported(0, NERVE, "radial nerve"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> aggregator.aggregate(input, assessment(
                ResponseEvaluationAssessability.EVALUABLE,
                supported(0, NERVE, "radial nerve"),
                supported(0, NERVE, "wrist extensors"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> aggregator.aggregate(input, assessment(
                ResponseEvaluationAssessability.EVALUABLE,
                supported(0, NERVE, "radial nerve"),
                supported(2, EFFECT, "wrist drop"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> aggregator.aggregate(input, assessment(
                ResponseEvaluationAssessability.EVALUABLE,
                supported(0, EFFECT, "radial nerve"),
                supported(1, EFFECT, "wrist drop"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsJudgmentFieldsThatContradictTheirStatus() {
        ResponseEvaluationJudgment fabricatedMissing = new ResponseEvaluationJudgment(
                0, NERVE, List.of(), ResponseEvaluationJudgmentStatus.MISSING,
                List.of("fabricated support"), List.of(), List.of());

        assertThatThrownBy(() -> aggregate(fabricatedMissing, missing(1, EFFECT)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ResponseEvaluationResult aggregate(ResponseEvaluationJudgment... judgments) {
        return aggregator.aggregate(
                input, assessment(ResponseEvaluationAssessability.EVALUABLE, judgments));
    }

    private static ResponseEvaluationV4Result assessment(
            ResponseEvaluationAssessability assessability,
            ResponseEvaluationJudgment... judgments) {
        return new ResponseEvaluationV4Result(
                List.of(judgments),
                assessability,
                "Use the supported component and address the remaining gap.",
                RecommendedAction.RETRY,
                List.of(),
                assessability == ResponseEvaluationAssessability.EVALUABLE
                        ? List.of()
                        : List.of("The response cannot be evaluated reliably."));
    }

    private static ResponseEvaluationJudgment supported(int index, String expected, String supported) {
        return new ResponseEvaluationJudgment(
                index, expected, List.of(supported), ResponseEvaluationJudgmentStatus.SUPPORTED,
                List.of(supported), List.of(), List.of());
    }

    private static ResponseEvaluationJudgment partial(
            int index, String expected, String supported, String missing) {
        return new ResponseEvaluationJudgment(
                index, expected, List.of(supported), ResponseEvaluationJudgmentStatus.PARTIAL,
                List.of(supported), List.of(missing), List.of());
    }

    private static ResponseEvaluationJudgment missing(int index, String expected) {
        return new ResponseEvaluationJudgment(
                index, expected, List.of(), ResponseEvaluationJudgmentStatus.MISSING,
                List.of(), List.of(), List.of());
    }
}
