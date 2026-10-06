package com.hippocampus.ai.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.hippocampus.ai.domain.ActivityType;
import com.hippocampus.ai.domain.Evaluation;
import com.hippocampus.ai.domain.EvaluationCertainty;
import com.hippocampus.ai.domain.ExplanationResult;
import com.hippocampus.ai.domain.QuestionGenerationResult;
import com.hippocampus.ai.domain.QuestionDifficulty;
import com.hippocampus.ai.domain.RecommendedAction;
import com.hippocampus.ai.domain.ResponseEvaluationResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GoldenAiSemanticEvaluatorTests {

    private final GoldenAiSemanticEvaluator evaluator = new GoldenAiSemanticEvaluator();
    private GoldenAiDataset.All dataset;

    @BeforeEach
    void loadDataset() {
        dataset = new GoldenAiDatasetLoader().loadAll();
    }

    @Test
    void acceptsKnownGoodStructuredFixturesForEveryTaskFamily() {
        GoldenAiDataset.ExplanationCase explanationCase = explanation("P7-07-ANAT-001");
        ExplanationResult explanation = new ExplanationResult(
                "Radial nerve injury and wrist drop",
                "Radial nerve injury denervates wrist extensors, causing loss of wrist extension so the hand falls into flexion as wrist drop.",
                List.of("The radial nerve supplies wrist extensors.", "Loss of wrist extension produces wrist drop."),
                List.of(),
                List.of(explanationCase.sourceEvidence().getFirst().sourceId()),
                false,
                List.of());

        GoldenAiDataset.QuestionCase questionCase = question("P7-08-SA-001");
        QuestionGenerationResult question = new QuestionGenerationResult(
                ActivityType.SHORT_ANSWER,
                "sinoatrial node",
                questionCase.learningObjective(),
                "What structure normally initiates the heartbeat and serves as the cardiac pacemaker?",
                List.of(),
                null,
                "The sinoatrial node (SA node).",
                "The SA node normally initiates the heartbeat.",
                QuestionDifficulty.FOUNDATIONAL,
                List.of(questionCase.sourceEvidence().getFirst().sourceId()),
                List.of());

        GoldenAiDataset.ResponseEvaluationCase responseCase = response("P7-09-CORRECT-001");
        ResponseEvaluationResult response = new ResponseEvaluationResult(
                Evaluation.CORRECT,
                List.of("Radial nerve supplies the wrist extensors", "Loss of wrist extension causes wrist drop"),
                List.of(),
                List.of(),
                "Correct: loss of radial nerve input removes wrist extension.",
                EvaluationCertainty.SUFFICIENT,
                RecommendedAction.CONTINUE,
                List.of(responseCase.sourceEvidence().getFirst().sourceId()),
                List.of());

        assertThat(evaluator.evaluate(explanationCase, explanation).passed()).isTrue();
        assertThat(evaluator.evaluate(questionCase, question).passed()).isTrue();
        assertThat(evaluator.evaluate(responseCase, response).passed()).isTrue();
    }

    @Test
    void rejectsFabricatedExplanationClaimAndReferenceOutsideEvidence() {
        GoldenAiDataset.ExplanationCase golden = explanation("P7-07-ANAT-001");
        ExplanationResult bad = new ExplanationResult(
                "Radial nerve injury and wrist drop",
                "The median nerve causes wrist drop despite normal wrist extension.",
                List.of("The radial nerve is nearby."),
                List.of(),
                List.of("00000000-0000-0000-0000-000000009999"),
                false,
                List.of());

        GoldenAiSemanticEvaluator.Result result = evaluator.evaluate(golden, bad);

        assertThat(result.passed()).isFalse();
        assertThat(result.failedRules())
                .anyMatch(value -> value.startsWith("forbidden-claim"))
                .contains("source-reference-outside-supplied-evidence");
    }

    @Test
    void rejectsQuestionThatLeaksItsAnswer() {
        GoldenAiDataset.QuestionCase golden = question("P7-08-SA-001");
        QuestionGenerationResult bad = new QuestionGenerationResult(
                ActivityType.SHORT_ANSWER,
                "sinoatrial node",
                golden.learningObjective(),
                "Why is the sinoatrial node the normal cardiac pacemaker?",
                List.of(),
                null,
                "The sinoatrial node.",
                "It normally initiates the heartbeat.",
                QuestionDifficulty.FOUNDATIONAL,
                List.of(golden.sourceEvidence().getFirst().sourceId()),
                List.of());

        assertThat(evaluator.evaluate(golden, bad).failedRules())
                .anyMatch(value -> value.startsWith("answer-leakage"));
    }

    @Test
    void rejectsQuestionThatDuplicatesARecentIntent() {
        GoldenAiDataset.QuestionCase golden = question("P7-08-REPEAT-001");
        QuestionGenerationResult bad = new QuestionGenerationResult(
                ActivityType.SHORT_ANSWER,
                "atrioventricular nodal delay",
                golden.learningObjective(),
                "Identify the structure that delays atrioventricular conduction.",
                List.of(),
                null,
                "AV nodal delay supports ventricular filling.",
                "The delay lets atrial contraction finish before ventricular systole.",
                QuestionDifficulty.INTERMEDIATE,
                List.of(golden.sourceEvidence().getFirst().sourceId()),
                List.of());

        assertThat(evaluator.evaluate(golden, bad).failedRules())
                .contains("duplicates-recent-question-intent");
    }

    @Test
    void rejectsFalsePositiveCorrectForWrongReasoning() {
        GoldenAiDataset.ResponseEvaluationCase golden = response("P7-09-WRONG-REASONING-001");
        ResponseEvaluationResult bad = new ResponseEvaluationResult(
                Evaluation.CORRECT,
                List.of("radial nerve", "wrist drop"),
                List.of(),
                List.of(),
                "Correct.",
                EvaluationCertainty.SUFFICIENT,
                RecommendedAction.CONTINUE,
                List.of(golden.sourceEvidence().getFirst().sourceId()),
                List.of());

        assertThat(evaluator.evaluate(golden, bad).failedRules())
                .contains("evaluation-not-allowed", "forbidden-evaluation")
                .anyMatch(value -> value.startsWith("required-missing-concept"))
                .anyMatch(value -> value.startsWith("feedback-does-not-identify-gap"));
    }

    @Test
    void assertionMatchingAcceptsHarmlessArticles() {
        assertThat(GoldenAiSemanticEvaluator.matchesSemanticGroup(
                        "activates wrist flexors", List.of("activates the wrist flexors")))
                .isTrue();
        assertThat(GoldenAiSemanticEvaluator.matchesSemanticGroup(
                        "a radial nerve lesion", List.of("radial nerve lesion")))
                .isTrue();
        assertThat(GoldenAiSemanticEvaluator.matchesSemanticGroup(
                        "an extensor injury", List.of("extensor injury")))
                .isTrue();

        GoldenAiDataset.ResponseEvaluationCase golden = response("P7-09-WRONG-REASONING-001");
        assertThat(evaluator.evaluate(
                        golden,
                        wrongReasoningResult(
                                golden, "Radial nerve injury activates wrist flexors to overpower extensors"))
                .passed()).isTrue();
    }

    @Test
    void assertionMatchingRejectsNegatedOrRelationshipIncompleteClaims() {
        assertThat(GoldenAiSemanticEvaluator.matchesSemanticGroup(
                        "does not activate wrist flexors", List.of("activate the wrist flexors")))
                .isFalse();
        assertThat(GoldenAiSemanticEvaluator.matchesSemanticGroup(
                        "wrist flexors", List.of("activates the wrist flexors")))
                .isFalse();

        GoldenAiDataset.ResponseEvaluationCase golden = response("P7-09-WRONG-REASONING-001");
        assertThat(evaluator.evaluate(
                        golden,
                        wrongReasoningResult(
                                golden, "Radial nerve injury does not activate wrist flexors"))
                .failedRules()).contains("misconception-not-demonstrated-by-response");
    }

    @Test
    void gapMentionMatchingAcceptsNegativeMissingPhrasingAndHarmlessArticles() {
        assertThat(GoldenAiSemanticEvaluator.matchesSemanticGroup(
                        "did not name the radial nerve",
                        List.of("radial nerve"),
                        GoldenAiSemanticEvaluator.SemanticMatchMode.GAP_MENTION))
                .isTrue();
        assertThat(GoldenAiSemanticEvaluator.matchesSemanticGroup(
                        "No response provided",
                        List.of("response"),
                        GoldenAiSemanticEvaluator.SemanticMatchMode.GAP_MENTION))
                .isTrue();
        assertThat(GoldenAiSemanticEvaluator.matchesSemanticGroup(
                        "the wrist extensors were not identified",
                        List.of("wrist extensors"),
                        GoldenAiSemanticEvaluator.SemanticMatchMode.GAP_MENTION))
                .isTrue();
    }

    @Test
    void responseEvaluationUsesAssertionAndGapMentionModesForTheirOwnedFields() {
        GoldenAiDataset.ResponseEvaluationCase uncertain = response("P7-09-UNCERTAIN-001");
        ResponseEvaluationResult gapMentions = new ResponseEvaluationResult(
                Evaluation.UNCERTAIN,
                List.of("wrist extensors"),
                List.of("did not name the radial nerve"),
                List.of(),
                "You did not name the radial nerve specifically.",
                EvaluationCertainty.LIMITED,
                RecommendedAction.RETRY,
                List.of(uncertain.sourceEvidence().getFirst().sourceId()),
                List.of());

        assertThat(evaluator.evaluate(uncertain, gapMentions).passed()).isTrue();

        ResponseEvaluationResult negatedCorrectAssertion = new ResponseEvaluationResult(
                Evaluation.UNCERTAIN,
                List.of("did not identify wrist extensors"),
                List.of("did not name the radial nerve"),
                List.of(),
                "You did not name the radial nerve specifically.",
                EvaluationCertainty.LIMITED,
                RecommendedAction.RETRY,
                List.of(uncertain.sourceEvidence().getFirst().sourceId()),
                List.of());

        assertThat(evaluator.evaluate(uncertain, negatedCorrectAssertion).failedRules())
                .anyMatch(rule -> rule.startsWith("required-correct-concept"));

        GoldenAiDataset.ResponseEvaluationCase empty = response("P7-09-EMPTY-001");
        ResponseEvaluationResult emptyGapMentions = new ResponseEvaluationResult(
                Evaluation.INCORRECT,
                List.of(),
                List.of("The radial nerve and wrist extensors were not identified"),
                List.of(),
                "No response provided. Both expected concepts are missing.",
                EvaluationCertainty.SUFFICIENT,
                RecommendedAction.RETRY,
                List.of(empty.sourceEvidence().getFirst().sourceId()),
                List.of());

        assertThat(evaluator.evaluate(empty, emptyGapMentions).passed()).isTrue();
    }

    @Test
    void acceptsConfirmedPartialDenervationWordingWithoutAcceptingSupplyAlone() {
        GoldenAiDataset.ResponseEvaluationCase partial = response("P7-09-PARTIAL-001");
        ResponseEvaluationResult acceptable = new ResponseEvaluationResult(
                Evaluation.PARTIAL,
                List.of("The radial nerve innervates the wrist extensors (injury causes denervation)"),
                List.of("wrist drop"),
                List.of(),
                "The missing consequence is loss of wrist extension and wrist drop.",
                EvaluationCertainty.SUFFICIENT,
                RecommendedAction.RETRY,
                List.of(partial.sourceEvidence().getFirst().sourceId()),
                List.of());
        ResponseEvaluationResult supplyAlone = new ResponseEvaluationResult(
                Evaluation.PARTIAL,
                List.of("the radial nerve supplies the wrist extensors"),
                List.of("wrist drop"),
                List.of(),
                "The missing consequence is loss of wrist extension and wrist drop.",
                EvaluationCertainty.SUFFICIENT,
                RecommendedAction.RETRY,
                List.of(partial.sourceEvidence().getFirst().sourceId()),
                List.of());

        assertThat(evaluator.evaluate(partial, acceptable).passed()).isTrue();
        assertThat(evaluator.evaluate(partial, supplyAlone).failedRules())
                .anyMatch(rule -> rule.startsWith("required-correct-concept"));
    }

    @Test
    void acceptsConfirmedUncertainWristExtensorWordingButRejectsNegatedAndReversedClaims() {
        GoldenAiDataset.ResponseEvaluationCase uncertain = response("P7-09-UNCERTAIN-001");
        ResponseEvaluationResult acceptable = uncertainResult(
                uncertain, "A nerve supplies the muscles that extend or lift the wrist");
        ResponseEvaluationResult negated = uncertainResult(
                uncertain, "A nerve does not supply the muscles that extend or lift the wrist");
        ResponseEvaluationResult reversed = uncertainResult(
                uncertain, "The wrist supplies the nerve that lifts the muscles");

        assertThat(evaluator.evaluate(uncertain, acceptable).passed()).isTrue();
        assertThat(evaluator.evaluate(uncertain, negated).failedRules())
                .anyMatch(rule -> rule.startsWith("required-correct-concept"));
        assertThat(evaluator.evaluate(uncertain, reversed).failedRules())
                .anyMatch(rule -> rule.startsWith("required-correct-concept"));
    }

    @Test
    void forbiddenTermMatchingRemainsUnchanged() {
        GoldenAiDataset.ResponseEvaluationCase golden = response("P7-09-WRONG-REASONING-001");
        ResponseEvaluationResult result = new ResponseEvaluationResult(
                Evaluation.PARTIAL,
                List.of("radial nerve injury", "wrist drop"),
                List.of("denervation of wrist extensors", "loss of wrist extension"),
                List.of("activates wrist flexors"),
                "The median nerve is not involved. Radial nerve injury causes wrist drop by denervating wrist extensors and causing loss of wrist extension.",
                EvaluationCertainty.SUFFICIENT,
                RecommendedAction.TARGETED_EXPLANATION,
                List.of(golden.sourceEvidence().getFirst().sourceId()),
                List.of());

        assertThat(evaluator.evaluate(golden, result).failedRules())
                .contains("forbidden-output-term:median nerve");
    }

    @Test
    void rejectsFabricatedMisconceptionsForEmptyAndOffTopicResponses() {
        for (String caseId : List.of("P7-09-EMPTY-001", "P7-09-OFFTOPIC-001")) {
            GoldenAiDataset.ResponseEvaluationCase golden = response(caseId);
            ResponseEvaluationResult bad = new ResponseEvaluationResult(
                    Evaluation.INCORRECT,
                    List.of(),
                    List.of("radial nerve and wrist extension"),
                    List.of("The learner believes the median nerve supplies wrist extensors"),
                    "The response does not explain the radial nerve and wrist extensors.",
                    EvaluationCertainty.LIMITED,
                    RecommendedAction.RETRY,
                    List.of(golden.sourceEvidence().getFirst().sourceId()),
                    List.of());

            assertThat(evaluator.evaluate(golden, bad).failedRules())
                    .contains("fabricated-misconception", "misconception-not-demonstrated-by-response");
        }
    }

    @Test
    void allowsNonEmptyLimitationsWhenRequireLimitationIsFalse() {
        GoldenAiDataset.ExplanationCase golden = explanation("P7-07-ANAT-001");
        assertThat(golden.requireLimitation()).isFalse();

        ExplanationResult resultWithLimitation = new ExplanationResult(
                "Radial nerve injury and wrist drop",
                "Radial nerve injury denervates wrist extensors, causing loss of wrist extension so the hand falls into flexion as wrist drop.",
                List.of("The radial nerve supplies wrist extensors.", "Loss of wrist extension produces wrist drop."),
                List.of(),
                List.of(golden.sourceEvidence().getFirst().sourceId()),
                false,
                List.of("Source does not discuss sensory branch deficit."));

        GoldenAiSemanticEvaluator.Result evaluation = evaluator.evaluate(golden, resultWithLimitation);
        assertThat(evaluation.passed()).isTrue();
        assertThat(evaluation.failedRules()).doesNotContain("unexpected-limitation");
    }

    @Test
    void acceptsMedicallyEquivalentPhrasingForAvNodalDelayAndAlveolarStability() {
        GoldenAiDataset.ExplanationCase avDelayCase = explanation("P7-07-PHYS-001");
        ExplanationResult avDelayResult = new ExplanationResult(
                "Atrioventricular nodal delay",
                "The AV node conducts the impulse slowly, creating a delay before the ventricles begin to contract.",
                List.of("This delay supports ventricular filling."),
                List.of(),
                List.of(avDelayCase.sourceEvidence().getFirst().sourceId()),
                false,
                List.of());

        GoldenAiSemanticEvaluator.Result avEvaluation = evaluator.evaluate(avDelayCase, avDelayResult);
        assertThat(avEvaluation.passed()).isTrue();

        GoldenAiDataset.ExplanationCase surfactantCase = explanation("P7-07-PHYS-002");
        ExplanationResult preventsCollapseResult = new ExplanationResult(
                "pulmonary surfactant and alveolar stability",
                "Surfactant lowers surface tension and prevents the alveoli from collapsing.",
                List.of("Surfactant lowers surface tension."),
                List.of(),
                List.of(surfactantCase.sourceEvidence().getFirst().sourceId()),
                false,
                List.of());
        ExplanationResult stopsCollapseResult = new ExplanationResult(
                "pulmonary surfactant and alveolar stability",
                "Surfactant reduces surface tension and stops alveoli from collapsing.",
                List.of("Surfactant reduces surface tension."),
                List.of(),
                List.of(surfactantCase.sourceEvidence().getFirst().sourceId()),
                false,
                List.of());

        assertThat(evaluator.evaluate(surfactantCase, preventsCollapseResult).passed()).isTrue();
        assertThat(evaluator.evaluate(surfactantCase, stopsCollapseResult).passed()).isTrue();
    }

    @Test
    void preservesHardGatesForMissingMandatoryLimitationAndForbiddenClaims() {
        GoldenAiDataset.ExplanationCase limitCase = explanation("P7-07-LIMIT-001");
        assertThat(limitCase.requireLimitation()).isTrue();

        ExplanationResult missingLimitation = new ExplanationResult(
                limitCase.targetConcept(),
                "Coronary dominance determines AV nodal supply based on whether the RCA or LCx gives off the PDA.",
                List.of("RCA supplies AV node in right dominance."),
                List.of(),
                List.of(),
                false,
                List.of());

        GoldenAiSemanticEvaluator.Result limitEvaluation = evaluator.evaluate(limitCase, missingLimitation);
        assertThat(limitEvaluation.passed()).isFalse();
        assertThat(limitEvaluation.failedRules()).contains("required-limitation-missing");

        GoldenAiDataset.ExplanationCase avCase = explanation("P7-07-PHYS-001");
        ExplanationResult forbiddenClaim = new ExplanationResult(
                avCase.targetConcept(),
                "The AV node initiates normal sinus rhythm and delays conduction for ventricular filling.",
                List.of("AV node delays conduction."),
                List.of(),
                List.of(avCase.sourceEvidence().getFirst().sourceId()),
                false,
                List.of());

        GoldenAiSemanticEvaluator.Result forbiddenEvaluation = evaluator.evaluate(avCase, forbiddenClaim);
        assertThat(forbiddenEvaluation.passed()).isFalse();
        assertThat(forbiddenEvaluation.failedRules())
                .anyMatch(rule -> rule.startsWith("forbidden-claim:AV node initiates normal sinus rhythm"));
    }

    @Test
    void allowsEmptyKeyPointsWhenLimitationIsRequired() {
        GoldenAiDataset.ExplanationCase golden = explanation("P7-07-LIMIT-001");
        ExplanationResult limitationOnly = new ExplanationResult(
                golden.targetConcept(),
                "The source does not establish how coronary dominance determines atrioventricular nodal supply.",
                List.of(),
                List.of(),
                List.of(),
                false,
                List.of("No source evidence was supplied, so a grounded explanation cannot be given."));

        GoldenAiSemanticEvaluator.Result result = evaluator.evaluate(golden, limitationOnly);

        assertThat(result.passed()).isTrue();
        assertThat(result.failedRules()).doesNotContain("key-points-must-be-non-empty");
    }

    @Test
    void rejectsBlankKeyPointWhenLimitationIsRequired() {
        GoldenAiDataset.ExplanationCase golden = explanation("P7-07-LIMIT-001");
        ExplanationResult blankKeyPoint = new ExplanationResult(
                golden.targetConcept(),
                "The source does not establish how coronary dominance determines atrioventricular nodal supply.",
                List.of(" "),
                List.of(),
                List.of(),
                false,
                List.of("No source evidence was supplied, so a grounded explanation cannot be given."));

        assertThat(evaluator.evaluate(golden, blankKeyPoint).failedRules())
                .contains("key-points-must-be-non-empty");
    }

    @Test
    void rejectsEmptyKeyPointsForOrdinaryExplanation() {
        GoldenAiDataset.ExplanationCase golden = explanation("P7-07-ANAT-001");
        ExplanationResult emptyKeyPoints = new ExplanationResult(
                golden.targetConcept(),
                "Radial nerve injury impairs wrist extension, so the hand falls into flexion as wrist drop.",
                List.of(),
                List.of(),
                List.of(golden.sourceEvidence().getFirst().sourceId()),
                false,
                List.of());

        assertThat(evaluator.evaluate(golden, emptyKeyPoints).failedRules())
                .contains("key-points-must-be-non-empty");
    }

    private GoldenAiDataset.ExplanationCase explanation(String caseId) {
        return dataset.explanations().stream()
                .filter(value -> value.caseId().equals(caseId))
                .findFirst()
                .orElseThrow();
    }

    private GoldenAiDataset.QuestionCase question(String caseId) {
        return dataset.questions().stream()
                .filter(value -> value.caseId().equals(caseId))
                .findFirst()
                .orElseThrow();
    }

    private GoldenAiDataset.ResponseEvaluationCase response(String caseId) {
        return dataset.responseEvaluations().stream()
                .filter(value -> value.caseId().equals(caseId))
                .findFirst()
                .orElseThrow();
    }

    private static ResponseEvaluationResult wrongReasoningResult(
            GoldenAiDataset.ResponseEvaluationCase golden, String misconception) {
        return new ResponseEvaluationResult(
                Evaluation.PARTIAL,
                List.of("radial nerve injury", "wrist drop"),
                List.of("denervation of wrist extensors", "loss of wrist extension"),
                List.of(misconception),
                "You correctly identified radial nerve injury and wrist drop. The injury denervates wrist extensors, causing loss of wrist extension.",
                EvaluationCertainty.SUFFICIENT,
                RecommendedAction.TARGETED_EXPLANATION,
                List.of(golden.sourceEvidence().getFirst().sourceId()),
                List.of());
    }

    private static ResponseEvaluationResult uncertainResult(
            GoldenAiDataset.ResponseEvaluationCase golden, String correctConcept) {
        return new ResponseEvaluationResult(
                Evaluation.UNCERTAIN,
                List.of(correctConcept),
                List.of("radial nerve"),
                List.of(),
                "The radial nerve still needs to be identified.",
                EvaluationCertainty.LIMITED,
                RecommendedAction.RETRY,
                List.of(golden.sourceEvidence().getFirst().sourceId()),
                List.of());
    }
}
