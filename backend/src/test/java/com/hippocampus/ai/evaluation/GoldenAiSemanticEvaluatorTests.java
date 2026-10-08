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
        dataset = new GoldenAiDatasetLoader().loadAll("v4");
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
    void acceptsExactGemini31ProWrongReasoningValidatedOutput() {
        ResponseEvaluationResult actual = new tools.jackson.databind.ObjectMapper().readValue("""
                {
                  "evaluation": "PARTIAL",
                  "correctConcepts": ["Radial nerve injury causes wrist drop"],
                  "missingConcepts": [
                    "The radial nerve supplies wrist extensors",
                    "The radial nerve supplies the wrist extensors",
                    "Radial nerve injury denervates the wrist extensors",
                    "Wrist drop is produced by the loss of wrist extension (denervation of extensors)"
                  ],
                  "misconceptions": [
                    "Radial nerve injury activates the wrist flexors",
                    "Wrist extensors remain normal during a radial nerve injury causing wrist drop",
                    "Wrist drop is caused by activated flexors overpowering normal extensors"
                  ],
                  "feedback": "You correctly identified that a radial nerve injury causes wrist drop. However, your mechanism is incorrect. The radial nerve supplies the wrist extensors. Therefore, a radial nerve injury causes wrist drop by eliminating wrist extension (denervating the extensors), not by activating the wrist flexors to overpower normal extensors.",
                  "certainty": "SUFFICIENT",
                  "recommendedAction": "TARGETED_EXPLANATION",
                  "sourceReferences": ["00000000-0000-0000-0000-000000000304"],
                  "limitations": []
                }
                """, ResponseEvaluationResult.class);
        assertThat(evaluator.evaluate(response("P7-09-WRONG-REASONING-001"), actual).failedRules()).isEmpty();
    }

    @Test
    void latestMisconceptionParaphrasesPreserveNegationRelationshipAndFabricationGuards() {
        GoldenAiDataset.ResponseEvaluationCase golden = response("P7-09-WRONG-REASONING-001");
        for (String misconception : List.of(
                "Radial nerve injury activates the wrist flexors",
                "Wrist extensors remain normal during a radial nerve injury causing wrist drop",
                "Wrist drop is caused by activated flexors overpowering normal extensors")) {
            assertThat(evaluator.evaluate(golden, wrongReasoningResult(golden, misconception)).passed()).isTrue();
        }
        for (String misconception : List.of(
                "Radial nerve injury does not activate the wrist flexors",
                "Wrist extensors do not remain normal during a radial nerve injury causing wrist drop",
                "Wrist drop is not caused by activated flexors overpowering normal extensors",
                "Normal extensors are not overpowered by activated flexors",
                "Normal wrist extensors remain injured during radial nerve function",
                "Wrist flexors activate radial nerve injury",
                "Radial nerve injury activates sensory neurons",
                "radial nerve")) {
            assertThat(evaluator.evaluate(golden, wrongReasoningResult(golden, misconception)).failedRules())
                    .contains("misconception-not-demonstrated-by-response");
        }
        assertThat(evaluator.evaluate(golden, wrongReasoningResult(golden,
                "Median nerve injury activates wrist flexors")).failedRules())
                .contains("forbidden-output-term:median nerve");
        assertThat(GoldenAiSemanticEvaluator.matchesSemanticGroup(
                "does not cause eliminating wrist extension", List.of("loss of wrist extension"))).isFalse();
        assertThat(GoldenAiSemanticEvaluator.matchesSemanticGroup(
                "wrist extension eliminates radial nerve injury", List.of("loss of wrist extension"))).isFalse();
    }

    @Test
    void rejectsExactGemini35FlashOutputWithMechanismOnlyInFeedback() {
        ResponseEvaluationResult actual = new tools.jackson.databind.ObjectMapper().readValue("""
                {
                  "evaluation": "PARTIAL",
                  "correctConcepts": [
                    "radial nerve injury causes wrist drop"
                  ],
                  "missingConcepts": [
                    "The radial nerve supplies wrist extensors",
                    "the radial nerve supplies the wrist extensors",
                    "wrist drop is produced by the loss of wrist extension"
                  ],
                  "misconceptions": [
                    "radial nerve injury leaves wrist extensors normal and activates wrist flexors",
                    "wrist drop is caused by active wrist flexors overpowering normal extensors"
                  ],
                  "feedback": "You correctly identified that a radial nerve injury leads to wrist drop. However, your explanation of the mechanism is incorrect. The radial nerve innervates the wrist extensors, so an injury causes a loss of wrist extension rather than actively stimulating the wrist flexors to overpower normal extensors.",
                  "certainty": "SUFFICIENT",
                  "recommendedAction": "TARGETED_EXPLANATION",
                  "sourceReferences": [
                    "00000000-0000-0000-0000-000000000304"
                  ],
                  "limitations": []
                }
                """, ResponseEvaluationResult.class);

        assertThat(evaluator.evaluate(response("P7-09-WRONG-REASONING-001"), actual).failedRules())
                .anyMatch(rule -> rule.startsWith("required-missing-concept:denervation"));
    }

    @Test
    void acceptsLatestExactGemini31ProCoordinatedDenervationAndFunctionalLossOutput() {
        ResponseEvaluationResult actual = new tools.jackson.databind.ObjectMapper().readValue("""
                {
                  "evaluation": "PARTIAL",
                  "correctConcepts": ["The radial nerve is the injured nerve", "The condition is wrist drop"],
                  "missingConcepts": [
                    "The radial nerve supplies wrist extensors",
                    "Radial nerve injury denervates or eliminates wrist extensors",
                    "Wrist drop is caused by the loss of wrist extension"
                  ],
                  "misconceptions": [
                    "Radial nerve injury leaves wrist extensors normal",
                    "Radial nerve injury activates wrist flexors",
                    "Wrist drop is caused by activated flexors overpowering normal extensors"
                  ],
                  "feedback": "You correctly identified that a radial nerve injury causes wrist drop. However, your mechanism is incorrect. The radial nerve supplies the wrist extensors. An injury to this nerve does not activate the flexors while leaving the extensors normal; rather, it eliminates wrist extension entirely, causing the hand to drop into flexion.",
                  "certainty": "SUFFICIENT",
                  "recommendedAction": "TARGETED_EXPLANATION",
                  "sourceReferences": ["00000000-0000-0000-0000-000000000304"],
                  "limitations": []
                }
                """, ResponseEvaluationResult.class);
        assertThat(evaluator.evaluate(response("P7-09-WRONG-REASONING-001"), actual).failedRules()).isEmpty();
    }

    @Test
    void coordinatedDenervationRequiresAffirmativeRadialInjuryToExtensorRelationship() {
        GoldenAiDataset.ResponseEvaluationCase golden = response("P7-09-WRONG-REASONING-001");
        String feedback = "You identified radial nerve injury and wrist drop. "
                + "The radial nerve supplies wrist extensors. Explain the loss of wrist extension.";
        for (String valid : List.of(
                "Radial nerve injury denervates or eliminates wrist extensors",
                "Radial nerve injury denervates the wrist extensors")) {
            assertThat(evaluator.evaluate(golden, wrongReasoningMechanismResult(valid, feedback)).failedRules())
                    .as(valid).isEmpty();
        }
        for (String invalid : List.of(
                "Radial nerve injury does not denervate wrist extensors",
                "Radial nerve injury does not denervate or eliminate wrist extensors",
                "Wrist extensors denervate the radial nerve",
                "Loss of wrist extension causes radial nerve injury",
                "Radial nerve injury eliminates wrist extensors",
                "A brain tumor denervates wrist extensors",
                "A tendon rupture denervates or eliminates wrist extensors")) {
            assertThat(evaluator.evaluate(golden, wrongReasoningMechanismResult(invalid, feedback)).failedRules())
                    .as(invalid).anyMatch(rule -> rule.startsWith("required-missing-concept:denervation"));
        }
    }

    @Test
    void functionalLossFeedbackCannotReplaceMissingStructuredMechanism() {
        GoldenAiDataset.ResponseEvaluationCase golden = response("P7-09-WRONG-REASONING-001");
        for (String feedback : List.of(
                "The radial nerve supplies the wrist extensors. An injury to this nerve does not activate the flexors "
                        + "while leaving the extensors normal; rather, it eliminates wrist extension entirely, "
                        + "causing the hand to drop into flexion. Radial nerve injury causes wrist drop.",
                "The radial nerve supplies wrist extensors. An injury to this nerve eliminates wrist extension, "
                        + "causing the hand to drop into flexion. Radial nerve injury causes wrist drop.",
                "Radial nerve injury eliminates wrist extension entirely, causing the hand to drop into flexion. "
                        + "The radial nerve supplies wrist extensors and wrist drop is identified.",
                "Radial nerve injury eliminates wrist extension, producing wrist drop. "
                        + "The radial nerve supplies wrist extensors.")) {
            assertThat(evaluator.evaluate(golden, wrongReasoningMechanismResult(
                    "The radial nerve supplies wrist extensors", feedback)).failedRules())
                    .as(feedback).anyMatch(rule -> rule.startsWith("required-missing-concept:denervation"));
        }
    }

    @Test
    void functionalLossRejectsNegationDisconnectionReversalAndUnrelatedCauses() {
        GoldenAiDataset.ResponseEvaluationCase golden = response("P7-09-WRONG-REASONING-001");
        for (String feedback : List.of(
                "Loss of wrist extension causes radial nerve injury and wrist drop.",
                "Radial nerve injury preserves wrist extension, causing wrist drop.",
                "Wrist extension remains normal after radial nerve injury and wrist drop.",
                "The radial nerve supplies wrist extensors. An injury to this nerve does not eliminate wrist extension, "
                        + "causing the hand to drop into flexion. Radial nerve injury causes wrist drop.",
                "Radial nerve injury. It eliminates wrist extension, causing the hand to drop into flexion. Wrist drop is identified.",
                "A brain tumor eliminates wrist extension, causing the hand to drop into flexion. "
                        + "Radial nerve injury and wrist drop are identified.",
                "An injury to this nerve eliminates wrist extension, causing the hand to drop into flexion. "
                        + "Radial nerve injury causes wrist drop.",
                "The radial nerve supplies wrist extensors. A different nerve is injured. "
                        + "An injury to this nerve eliminates wrist extension, causing the hand to drop into flexion. Wrist drop is identified.",
                "The radial nerve does not supply wrist extensors. An injury to this nerve eliminates wrist extension, "
                        + "causing the hand to drop into flexion. Wrist drop is identified.",
                "Radial nerve injury eliminates wrist extensors, causing wrist drop.")) {
            assertThat(evaluator.evaluate(golden, wrongReasoningMechanismResult(
                    "The radial nerve supplies wrist extensors", feedback)).failedRules())
                    .as(feedback).anyMatch(rule -> rule.startsWith("required-missing-concept:denervation"));
        }
        for (String contradiction : List.of(
                "Loss of wrist extension causes radial nerve injury.",
                "Wrist extension is preserved after radial nerve injury.")) {
            assertThat(evaluator.evaluate(golden, wrongReasoningMechanismResult(
                    "Radial nerve injury denervates or eliminates wrist extensors",
                    "Radial nerve injury causes wrist drop. " + contradiction)).failedRules())
                    .as(contradiction).anyMatch(rule -> rule.startsWith("feedback-does-not-identify-gap"));
        }
    }

    @Test
    void standaloneNormalExtensorMisconceptionRequiresTheDemonstratedInjurySubject() {
        GoldenAiDataset.ResponseEvaluationCase golden = response("P7-09-WRONG-REASONING-001");
        for (String misconception : List.of(
                "Radial nerve injury leaves wrist extensors normal",
                "Radial nerve injury activates wrist flexors",
                "Wrist drop is caused by activated flexors overpowering normal extensors")) {
            assertThat(evaluator.evaluate(golden, wrongReasoningResult(golden, misconception)).passed())
                    .as(misconception).isTrue();
        }
        for (String invalid : List.of(
                "A brain tumor leaves wrist extensors normal",
                "A tendon rupture leaves wrist extensors normal",
                "Wrist extensors remain normal during a brain tumor causing wrist drop",
                "Radial nerve injury leaves wrist extensors normal because of a brain tumor",
                "Radial nerve injury does not leave wrist extensors normal",
                "Normal wrist extensors cause radial nerve injury")) {
            assertThat(evaluator.evaluate(golden, wrongReasoningResult(golden, invalid)).failedRules())
                    .as(invalid).contains("misconception-not-demonstrated-by-response");
        }
    }

    private static ResponseEvaluationResult wrongReasoningMechanismResult(String mechanism, String feedback) {
        return new ResponseEvaluationResult(Evaluation.PARTIAL,
                List.of("The radial nerve is the injured nerve", "The condition is wrist drop"),
                List.of(mechanism, "Wrist drop is caused by the loss of wrist extension"),
                List.of("Radial nerve injury activates wrist flexors"), feedback,
                EvaluationCertainty.SUFFICIENT, RecommendedAction.TARGETED_EXPLANATION,
                List.of("00000000-0000-0000-0000-000000000304"), List.of());
    }

    @Test
    void collectiveMechanismCannotBorrowFeedbackToSatisfyMissingConcepts() {
        GoldenAiDataset.ResponseEvaluationCase golden = response("P7-09-WRONG-REASONING-001");
        ResponseEvaluationResult valid = collectiveWrongReasoningResult(golden,
                "radial nerve injury causes wrist drop", "The radial nerve supplies wrist extensors",
                "Radial nerve injury causes loss of wrist extension and wrist drop rather than active flexor stimulation.");
        assertThat(evaluator.evaluate(golden, valid).failedRules())
                .anyMatch(rule -> rule.startsWith("required-missing-concept:denervation"));
        for (String supply : List.of("radial nerve", "The radial nerve does not supply wrist extensors",
                "Wrist extensors supply the radial nerve", "The radial nerve supplies wrist flexors")) {
            assertThat(evaluator.evaluate(golden, collectiveWrongReasoningResult(golden,
                    "radial nerve injury causes wrist drop", supply, valid.feedback())).failedRules())
                    .anyMatch(rule -> rule.startsWith("required-missing-concept:denervation"));
        }
        assertThat(evaluator.evaluate(golden, collectiveWrongReasoningResult(golden,
                "radial nerve and wrist drop", "The radial nerve supplies wrist extensors",
                "The radial nerve and wrist drop require further explanation of loss of wrist extension.")).failedRules())
                .anyMatch(rule -> rule.startsWith("required-missing-concept:denervation"));
        ResponseEvaluationResult supplyAlone = new ResponseEvaluationResult(Evaluation.PARTIAL,
                List.of("radial nerve injury causes wrist drop"),
                List.of("The radial nerve supplies wrist extensors"), List.of("activates wrist flexors"),
                "The radial nerve supplies wrist extensors; wrist drop is identified.",
                EvaluationCertainty.SUFFICIENT, RecommendedAction.TARGETED_EXPLANATION,
                List.of(golden.sourceEvidence().getFirst().sourceId()), List.of());
        assertThat(evaluator.evaluate(golden, supplyAlone).failedRules())
                .anyMatch(rule -> rule.startsWith("required-missing-concept:denervation"));
        ResponseEvaluationResult negatedLoss = new ResponseEvaluationResult(Evaluation.PARTIAL,
                valid.correctConcepts(), List.of("The radial nerve supplies wrist extensors",
                        "does not lose wrist extension"), valid.misconceptions(),
                "Radial nerve injury does not cause loss of wrist extension; wrist drop is identified.",
                valid.certainty(), valid.recommendedAction(), valid.sourceReferences(), List.of());
        assertThat(evaluator.evaluate(golden, negatedLoss).failedRules())
                .anyMatch(rule -> rule.startsWith("required-missing-concept:denervation"));
        assertThat(GoldenAiSemanticEvaluator.matchesSemanticGroup(
                "radial nerve supplies wrist extensors", List.of("denervation of wrist extensors"))).isFalse();
    }

    private static ResponseEvaluationResult collectiveWrongReasoningResult(
            GoldenAiDataset.ResponseEvaluationCase golden, String injury, String supply, String feedback) {
        return new ResponseEvaluationResult(Evaluation.PARTIAL, List.of(injury),
                List.of(supply, "loss of wrist extension"), List.of("activates wrist flexors"), feedback,
                EvaluationCertainty.SUFFICIENT, RecommendedAction.TARGETED_EXPLANATION,
                List.of(golden.sourceEvidence().getFirst().sourceId()), List.of());
    }

    @Test
    void explicitDenervationPassesWithoutComposedSupplyEvidence() {
        GoldenAiDataset.ResponseEvaluationCase golden = response("P7-09-WRONG-REASONING-001");
        assertThat(evaluator.evaluate(golden, wrongReasoningResult(golden, "activates wrist flexors")).passed())
                .isTrue();
    }

    @Test
    void compoundMechanismInFeedbackCannotReplaceStructuredGap() {
        GoldenAiDataset.ResponseEvaluationCase golden = response("P7-09-WRONG-REASONING-001");
        for (String feedback : List.of(
                "Radial nerve injury causes loss of wrist extension and wrist drop.",
                "Loss of radial nerve function leads to loss of wrist extension and wrist drop.",
                "Radial nerve injury causes denervation of wrist extensors and loss of wrist extension, producing wrist drop.",
                "The radial nerve supplies wrist extensors, so an injury causes a loss of wrist extension and wrist drop.")) {
            assertThat(evaluator.evaluate(golden, collectiveWrongReasoningResult(golden,
                    "radial nerve injury causes wrist drop", "The radial nerve supplies wrist extensors", feedback))
                    .failedRules()).as(feedback)
                    .anyMatch(rule -> rule.startsWith("required-missing-concept:denervation"));
        }
    }

    @Test
    void compoundMechanismRejectsReviewReversedCausalityCounterexample() {
        GoldenAiDataset.ResponseEvaluationCase golden = response("P7-09-WRONG-REASONING-001");
        ResponseEvaluationResult reversed = collectiveWrongReasoningResult(golden,
                "radial nerve injury causes wrist drop", "The radial nerve supplies wrist extensors",
                "The radial nerve supplies wrist extensors. Loss of wrist extension causes radial nerve injury and wrist drop.");
        assertThat(evaluator.evaluate(golden, reversed).failedRules())
                .anyMatch(rule -> rule.startsWith("required-missing-concept:denervation"));
    }

    @Test
    void compoundMechanismRequiresCausalConnectionWithinOneStatement() {
        GoldenAiDataset.ResponseEvaluationCase golden = response("P7-09-WRONG-REASONING-001");
        for (String feedback : List.of(
                "The radial nerve supplies wrist extensors. Wrist extension is lost. Wrist drop is identified.",
                "Radial nerve injury. Causes loss of wrist extension and wrist drop.",
                "The radial nerve supplies wrist extensors. An injury causes loss of wrist extension and wrist drop.",
                "Radial nerve injury does not cause loss of wrist extension or wrist drop.")) {
            assertThat(evaluator.evaluate(golden, collectiveWrongReasoningResult(golden,
                    "radial nerve injury causes wrist drop", "The radial nerve supplies wrist extensors", feedback))
                    .failedRules()).as(feedback)
                    .anyMatch(rule -> rule.startsWith("required-missing-concept:denervation"));
        }
        assertThat(evaluator.evaluate(golden, collectiveWrongReasoningResult(golden,
                "radial nerve injury causes wrist drop", "The radial nerve supplies wrist extensors",
                "The radial nerve supplies wrist extensors. Wrist extension is lost. "
                        + "Radial nerve injury causes loss of wrist extension and wrist drop.")).failedRules())
                .anyMatch(rule -> rule.startsWith("required-missing-concept:denervation"));
    }

    @Test
    void preservedExtensorInputOrFunctionCannotPassEvenWithOtherMechanismEvidence() {
        GoldenAiDataset.ResponseEvaluationCase golden = response("P7-09-WRONG-REASONING-001");
        for (String contradiction : List.of(
                "Radial nerve injury preserves wrist extensor input.",
                "Radial nerve injury maintains input to wrist extensors.",
                "Radial nerve injury preserves wrist extensor function.",
                "Radial nerve injury preserves their input.",
                "Wrist extensors remain normal after radial nerve injury.",
                "Loss of wrist extension causes radial nerve injury.")) {
            String feedback = "Radial nerve injury causes loss of wrist extension and wrist drop. " + contradiction;
            assertThat(evaluator.evaluate(golden, collectiveWrongReasoningResult(golden,
                    "radial nerve injury causes wrist drop", "The radial nerve supplies wrist extensors", feedback))
                    .failedRules()).as(contradiction)
                    .anyMatch(rule -> rule.startsWith("required-missing-concept:denervation"));
            ResponseEvaluationResult explicit = new ResponseEvaluationResult(Evaluation.PARTIAL,
                    List.of("radial nerve injury causes wrist drop"),
                    List.of("denervation of wrist extensors", "loss of wrist extension"),
                    List.of("activates wrist flexors"), feedback,
                    EvaluationCertainty.SUFFICIENT, RecommendedAction.TARGETED_EXPLANATION,
                    List.of(golden.sourceEvidence().getFirst().sourceId()), List.of());
            assertThat(evaluator.evaluate(golden, explicit).failedRules()).as(contradiction)
                    .anyMatch(rule -> rule.startsWith("required-missing-concept:denervation"));
        }
        assertThat(evaluator.evaluate(golden, collectiveWrongReasoningResult(golden,
                "radial nerve injury causes wrist drop", "The radial nerve supplies wrist extensors",
                "Radial nerve injury causes loss of wrist extension and wrist drop. "
                        + "Radial nerve injury does not preserve wrist extensor function.")).failedRules())
                .anyMatch(rule -> rule.startsWith("required-missing-concept:denervation"));
    }

    @Test
    void misconceptionParaphrasesRequireTheCompleteDemonstratedCausalContext() {
        GoldenAiDataset.ResponseEvaluationCase golden = response("P7-09-WRONG-REASONING-001");
        for (String valid : List.of(
                "Wrist extensors remain normal during a radial nerve injury causing wrist drop.",
                "Radial nerve injury leaves wrist extensors normal and activates wrist flexors.",
                "Wrist drop is caused by active wrist flexors overpowering normal extensors.",
                "Radial nerve injury activates wrist flexors to overpower extensors.")) {
            assertThat(evaluator.evaluate(golden, wrongReasoningResult(golden, valid)).passed())
                    .as(valid).isTrue();
        }
        for (String fabricatedOrInvalid : List.of(
                "Wrist extensors remain normal during a brain tumor causing wrist drop.",
                "Wrist extensors remain normal during a tendon rupture causing wrist drop.",
                "Wrist extensors remain normal during a radial nerve injury causing wrist drop because of a brain tumor.",
                "A brain tumor activates wrist flexors.",
                "Radial nerve injury activates wrist flexors because of a tendon rupture.",
                "Wrist extensors remain normal.",
                "Wrist extensors do not remain normal during a radial nerve injury causing wrist drop.",
                "Wrist extensors remain normal and cause radial nerve injury and wrist drop.",
                "Radial nerve injury leaves wrist extensors injured and activates wrist flexors.")) {
            assertThat(evaluator.evaluate(golden, wrongReasoningResult(golden, fabricatedOrInvalid)).failedRules())
                    .as(fabricatedOrInvalid).contains("misconception-not-demonstrated-by-response");
        }
    }

    @Test
    void acceptsLatestPartialFeedbackAndParentheticalWristExtensionWording() {
        GoldenAiDataset.ResponseEvaluationCase partial = response("P7-09-PARTIAL-001");
        ResponseEvaluationResult result = new ResponseEvaluationResult(
                Evaluation.PARTIAL, List.of("It denervates the wrist extensors."),
                List.of("loss of wrist extension"), List.of(),
                "What specific movement is lost as a result of this denervation that leads to the hand dropping?",
                EvaluationCertainty.SUFFICIENT, RecommendedAction.RETRY,
                List.of(partial.sourceEvidence().getFirst().sourceId()), List.of());
        assertThat(evaluator.evaluate(partial, result).passed()).isTrue();

        GoldenAiDataset.ResponseEvaluationCase uncertain = response("P7-09-UNCERTAIN-001");
        assertThat(evaluator.evaluate(uncertain,
                uncertainResult(uncertain, "A nerve supplies the muscles that extend (lift) the wrist"))
                .passed()).isTrue();
        for (String incorrect : List.of(
                "A nerve does not supply the muscles that extend (lift) the wrist",
                "The wrist supplies the nerve that extends (lifts) the muscles",
                "radial nerve")) {
            assertThat(evaluator.evaluate(uncertain, uncertainResult(uncertain, incorrect)).failedRules())
                    .anyMatch(rule -> rule.startsWith("required-correct-concept"));
        }
        assertThat(GoldenAiSemanticEvaluator.matchesSemanticGroup(
                "does not lose wrist extension", List.of("wrist extension"))).isFalse();
        assertThat(GoldenAiSemanticEvaluator.matchesSemanticGroup(
                "does not lead to the hand dropping", List.of("wrist drop"))).isFalse();
    }

    @Test
    void acceptsExactGemini31ProPartialPositionConsequenceFeedback() {
        ResponseEvaluationResult actual = new tools.jackson.databind.ObjectMapper().readValue("""
                {
                  "evaluation": "PARTIAL",
                  "correctConcepts": ["The radial nerve supplies wrist extensors"],
                  "missingConcepts": ["Loss of wrist extension produces wrist drop"],
                  "misconceptions": [],
                  "feedback": "You correctly identified that radial nerve injury denervates the wrist extensors. To complete your answer, what happens tothe wrist position as a result of losing extension?",
                  "certainty": "SUFFICIENT",
                  "recommendedAction": "GUIDED_REASONING",
                  "sourceReferences": ["00000000-0000-0000-0000-000000000303"],
                  "limitations": []
                }
                """, ResponseEvaluationResult.class);
        assertThat(evaluator.evaluate(response("P7-09-PARTIAL-001"), actual).failedRules()).isEmpty();
    }

    @Test
    void partialFeedbackAcceptsBoundedPositionConsequenceQuestionsAndExistingGapForms() {
        GoldenAiDataset.ResponseEvaluationCase golden = response("P7-09-PARTIAL-001");
        for (String feedback : List.of(
                "What happens tothe wrist position as a result of losing extension?",
                "What happens to the wrist position as a result of losing extension?",
                "What happens to the wrist position as a result of loss of wrist extension?",
                "What happens to the wrist posture as a consequence of losing wrist extension?",
                "What happens to the wrist because of losing extension?",
                "What happens to the movement of the wrist when these extensors are denervated?",
                "loss of wrist extension",
                "cannot extend the wrist",
                "wrist drop",
                "dropped flexed posture")) {
            assertThat(evaluator.evaluate(golden, partialFeedbackResult(feedback)).failedRules())
                    .as(feedback).isEmpty();
        }
    }

    @Test
    void partialFeedbackRejectsGenericDisconnectedNegatedAndFabricatedConsequences() {
        GoldenAiDataset.ResponseEvaluationCase golden = response("P7-09-PARTIAL-001");
        for (String feedback : List.of(
                "What happens to the wrist position?",
                "wrist position", "wrist", "extension",
                "The wrist has a position. Extension is discussed elsewhere.",
                "What happens to the wrist position? As a result of losing extension?",
                "What happens to the wrist position as a result of not losing extension?",
                "What happens to the wrist position as a result of a brain tumor?",
                "What happens to the wrist position as a result of losing extension because of a brain tumor?",
                "What happens to the wrist position as a result of loss of wrist extension because of a brain tumor?",
                "What happens to the wrist position as a result of losing extension due to a tendon rupture?")) {
            assertThat(evaluator.evaluate(golden, partialFeedbackResult(feedback)).failedRules())
                    .as(feedback).anyMatch(rule -> rule.startsWith("feedback-does-not-identify-gap"));
        }
    }

    @Test
    void partialFeedbackRejectsReversedCausalityAndPreservedFunctionDespiteGapMentions() {
        GoldenAiDataset.ResponseEvaluationCase golden = response("P7-09-PARTIAL-001");
        for (String feedback : List.of(
                "Loss of wrist extension causes radial nerve injury.",
                "The wrist extensors remain functional and wrist extension is preserved.",
                "Normal wrist extension after denervation produces wrist drop.",
                "Denervation preserves wrist extension. What happens to the wrist position as a result of losing extension?",
                "Wrist extension is preserved. Loss of wrist extension is discussed elsewhere.")) {
            assertThat(evaluator.evaluate(golden, partialFeedbackResult(feedback)).failedRules())
                    .as(feedback).anyMatch(rule -> rule.startsWith("feedback-does-not-identify-gap"));
        }
    }

    private static ResponseEvaluationResult partialFeedbackResult(String feedback) {
        return new ResponseEvaluationResult(Evaluation.PARTIAL,
                List.of("The radial nerve supplies wrist extensors"),
                List.of("Loss of wrist extension produces wrist drop"), List.of(), feedback,
                EvaluationCertainty.SUFFICIENT, RecommendedAction.GUIDED_REASONING,
                List.of("00000000-0000-0000-0000-000000000303"), List.of());
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
    void acceptsObservedPartialCanonicalizationsWithoutAcceptingBareStemReference() {
        GoldenAiDataset.ResponseEvaluationCase partial = response("P7-09-PARTIAL-001");
        ResponseEvaluationResult firstRun = new ResponseEvaluationResult(
                Evaluation.PARTIAL,
                List.of("The radial nerve innervates the wrist extensors"),
                List.of("Loss of wrist extension produces wrist drop"),
                List.of(),
                "You correctly identified that a radial nerve injury denervates the wrist extensors. "
                        + "To fully answer the question, what happens to the movement of the wrist "
                        + "when these extensors are denervated?",
                EvaluationCertainty.SUFFICIENT,
                RecommendedAction.GUIDED_REASONING,
                List.of(partial.sourceEvidence().getFirst().sourceId()),
                List.of());
        ResponseEvaluationResult repeatRun = new ResponseEvaluationResult(
                Evaluation.PARTIAL,
                List.of("The radial nerve supplies the wrist extensors"),
                List.of("Loss of wrist extension produces wrist drop"),
                List.of(),
                "You correctly identified that the radial nerve innervates the wrist extensors. "
                        + "To complete the explanation, explicitly mention the mechanical consequence "
                        + "of this denervation (loss of wrist extension) that results in wrist drop.",
                EvaluationCertainty.SUFFICIENT,
                RecommendedAction.CONNECTION_SUPPORT,
                List.of(partial.sourceEvidence().getFirst().sourceId()),
                List.of());
        ResponseEvaluationResult bareStemReference = new ResponseEvaluationResult(
                Evaluation.PARTIAL,
                List.of("radial nerve"),
                List.of("wrist drop"),
                List.of(),
                "The missing consequence is loss of wrist extension and wrist drop.",
                EvaluationCertainty.SUFFICIENT,
                RecommendedAction.RETRY,
                List.of(partial.sourceEvidence().getFirst().sourceId()),
                List.of());

        assertThat(evaluator.evaluate(partial, firstRun).passed()).isTrue();
        assertThat(evaluator.evaluate(partial, repeatRun).passed()).isTrue();
        assertThat(evaluator.evaluate(partial, bareStemReference).failedRules())
                .anyMatch(rule -> rule.startsWith("required-correct-concept"));
    }

    @Test
    void acceptsConfirmedUncertainWristExtensorWordingButRejectsNegatedAndReversedClaims() {
        GoldenAiDataset.ResponseEvaluationCase uncertain = response("P7-09-UNCERTAIN-001");
        ResponseEvaluationResult acceptable = uncertainResult(
                uncertain, "A nerve supplies the muscles that extend or lift the wrist");
        ResponseEvaluationResult parenthetical = uncertainResult(
                uncertain, "A nerve supplies the muscles that lift (extend) the wrist");
        ResponseEvaluationResult negated = uncertainResult(
                uncertain, "A nerve does not supply the muscles that extend or lift the wrist");
        ResponseEvaluationResult reversed = uncertainResult(
                uncertain, "The wrist supplies the nerve that lifts the muscles");

        assertThat(evaluator.evaluate(uncertain, acceptable).passed()).isTrue();
        assertThat(evaluator.evaluate(uncertain, parenthetical).passed()).isTrue();
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

    @Test
    void v5MechanismEvidenceBelongsOnlyInMissingConcepts() {
        GoldenAiDataset.ResponseEvaluationCase golden = new GoldenAiDatasetLoader().loadAll().responseEvaluations()
                .stream().filter(value -> value.caseId().equals("P7-09-WRONG-REASONING-001")).findFirst().orElseThrow();
        String feedback = "You identified radial nerve injury and wrist drop. Radial nerve injury causes loss of wrist extension.";
        for (String mechanism : List.of(
                "Radial nerve injury eliminates wrist extension",
                "Radial nerve injury causes loss of wrist extensor function",
                "Injury to the radial nerve leads to loss of wrist extension",
                "Loss of radial nerve function abolishes wrist extension")) {
            assertThat(evaluator.evaluate(golden, v5Result(List.of("radial nerve injury", "wrist drop"),
                    List.of(mechanism, "loss of wrist extension"), feedback)).failedRules()).as(mechanism).isEmpty();
        }
        for (String invalid : List.of(
                "Loss of wrist extension causes radial nerve injury",
                "Radial nerve injury does not cause loss of wrist extension",
                "Radial nerve injury preserves wrist extensor function",
                "Wrist extensors remain normal after radial nerve injury",
                "A brain tumor causes loss of wrist extension",
                "A tendon rupture eliminates wrist extension",
                "Radial nerve injury causes loss of wrist extension because of a brain tumor",
                "radial nerve", "wrist extensors", "Radial nerve injury. Loss of wrist extension")) {
            assertThat(evaluator.evaluate(golden, v5Result(List.of("radial nerve injury", "wrist drop"),
                    List.of(invalid, "loss of wrist extension"), feedback)).failedRules()).as(invalid)
                    .anyMatch(rule -> rule.startsWith("required-missing-concept:radial nerve injury"));
        }
        assertThat(evaluator.evaluate(golden, v5Result(List.of("radial nerve injury", "wrist drop"),
                List.of("The radial nerve supplies wrist extensors", "loss of wrist extension"), feedback)).failedRules())
                .anyMatch(rule -> rule.startsWith("required-missing-concept:radial nerve injury"));
        assertThat(evaluator.evaluate(golden, v5Result(
                List.of("radial nerve injury", "wrist drop", "Radial nerve injury eliminates wrist extension"),
                List.of("loss of wrist extension"), feedback)).failedRules())
                .anyMatch(rule -> rule.startsWith("required-missing-concept:radial nerve injury"));
    }

    @Test
    void latestV5Gemini31ProLiveOutputPassesWithinItsOwnedFields() {
        ResponseEvaluationResult actual = new tools.jackson.databind.ObjectMapper().readValue("""
                {
                  "evaluation": "PARTIAL",
                  "correctConcepts": ["Radial nerve injury causes wrist drop"],
                  "missingConcepts": ["The radial nerve supplies wrist extensors", "Radial nerve injury eliminates wrist extension", "Loss of wrist extension produces wrist drop"],
                  "misconceptions": [
                    "The wrist extensors remain normal despite a radial nerve injury",
                    "Radial nerve injury activates wrist flexors rather than eliminating extension",
                    "Wrist drop is caused by activated flexors overpowering extensors rather than a loss of wrist extension"
                  ],
                  "feedback": "You correctly identified that a radial nerve injury results in wrist drop. However, the mechanism you described is incorrect. The radial nerve supplies the wrist extensors. An injury to this nerve eliminates wrist extension, resulting in wrist drop due to the loss of extensor function, not because the injury activates wrist flexors to overpower normal extensors.",
                  "certainty": "SUFFICIENT", "recommendedAction": "TARGETED_EXPLANATION",
                  "sourceReferences": ["00000000-0000-0000-0000-000000000304"], "limitations": []
                }
                """, ResponseEvaluationResult.class);
        assertThat(evaluator.evaluate(v5WrongReasoningCase(), actual).failedRules()).isEmpty();
        for (String misconception : actual.misconceptions()) {
            assertThat(evaluator.evaluate(v5WrongReasoningCase(), withMisconception(actual, misconception)).failedRules())
                    .as(misconception).isEmpty();
        }
    }

    @Test
    void latestV5FeedbackWithExtensorParalysisPassesThroughLiveEvaluatorPath() {
        ResponseEvaluationResult actual = new tools.jackson.databind.ObjectMapper().readValue("""
                {
                  "evaluation": "PARTIAL",
                  "correctConcepts": ["Radial nerve injury causes wrist drop"],
                  "missingConcepts": ["The radial nerve supplies wrist extensors", "Radial nerve injury eliminates wrist extension", "Loss of wrist extension causes wrist drop"],
                  "misconceptions": [
                    "Radial nerve injury activates wrist flexors, which overpower otherwise normal extensors",
                    "Wrist drop is caused by activated flexors overpowering normal extensors"
                  ],
                  "feedback": "You correctly identified that a radial nerve injury causes wrist drop. However, the mechanism you described is incorrect. The radial nerve supplies the wrist extensors. An injury to this nerve eliminates wrist extension by paralyzing the extensors, rather than by activating the flexors.",
                  "certainty": "SUFFICIENT", "recommendedAction": "TARGETED_EXPLANATION",
                  "sourceReferences": ["00000000-0000-0000-0000-000000000304"], "limitations": []
                }
                """, ResponseEvaluationResult.class);
        var golden = v5WrongReasoningCase();
        assertThat(evaluator.evaluate(golden, actual).failedRules()).isEmpty();
        for (String feedback : List.of(
                "The radial nerve supplies the wrist extensors. An injury to this nerve preserves wrist extension.",
                "The radial nerve supplies the wrist extensors. An injury to this nerve does not eliminate wrist extension by paralyzing the extensors.",
                "The radial nerve supplies the wrist extensors. Loss of wrist extension causes radial nerve injury.",
                "A different nerve supplies the wrist extensors. An injury to this nerve eliminates wrist extension by paralyzing the extensors.",
                "The radial nerve supplies the wrist extensors. A different nerve is injured. An injury to this nerve eliminates wrist extension by paralyzing the extensors.",
                "The radial nerve supplies the wrist extensors. An injury to this nerve eliminates wrist extension by activating unrelated muscles.",
                "The radial nerve supplies the wrist extensors. An injury to this nerve eliminates wrist extension by paralyzing the extensors because of an invented process.")) {
            ResponseEvaluationResult negative = new ResponseEvaluationResult(actual.evaluation(), actual.correctConcepts(),
                    actual.missingConcepts(), actual.misconceptions(), feedback, actual.certainty(), actual.recommendedAction(),
                    actual.sourceReferences(), actual.limitations());
            assertThat(evaluator.evaluate(golden, negative).failedRules()).as(feedback)
                    .anyMatch(rule -> rule.startsWith("feedback-does-not-identify-gap:loss of wrist extension"));
        }
    }

    @Test
    void v5FeedbackAcceptsBoundedFunctionalLossAndRejectsFalseRelationships() {
        var golden = v5WrongReasoningCase();
        for (String feedback : List.of(
                "Radial nerve injury eliminates wrist extension. This causes wrist drop.",
                "Radial nerve injury causes loss of wrist extensor function. This causes wrist drop.",
                "Radial nerve injury causes loss of extensor function in the wrist. This causes wrist drop.",
                "The radial nerve supplies wrist extensors. An injury to this nerve eliminates wrist extension, resulting in wrist drop.")) {
            assertThat(evaluator.evaluate(golden, v5Result(List.of("radial nerve injury", "wrist drop"),
                    golden.expectedConcepts(), feedback)).failedRules()).as(feedback).isEmpty();
        }
        for (String feedback : List.of(
                "Radial nerve injury preserves wrist extension and causes wrist drop.",
                "Wrist extensors remain functional after radial nerve injury. Wrist drop is identified.",
                "Loss of wrist extension causes radial nerve injury and wrist drop.",
                "Radial nerve injury does not eliminate wrist extension. Wrist drop is identified.",
                "A brain tumor causes loss of wrist extension. Radial nerve injury causes wrist drop.",
                "Radial nerve injury. Eliminates wrist extension. Wrist drop.",
                "Radial nerve injury causes loss of extensor function. Wrist drop.",
                "The radial nerve supplies wrist extensors. A different nerve is injured. An injury to this nerve eliminates wrist extension, resulting in wrist drop.",
                "Radial nerve injury causes loss of wrist extension because of an invented process. Wrist drop.")) {
            assertThat(evaluator.evaluate(golden, v5Result(List.of("radial nerve injury", "wrist drop"),
                    golden.expectedConcepts(), feedback)).failedRules()).as(feedback)
                    .anyMatch(rule -> rule.startsWith("feedback-does-not-identify-gap"));
        }
    }

    @Test
    void v5MisconceptionContrastsCannotNegateOrFabricateLearnerClaims() {
        var golden = v5WrongReasoningCase();
        ResponseEvaluationResult actual = v5Result(List.of("radial nerve injury", "wrist drop"), golden.expectedConcepts(),
                "Radial nerve injury causes loss of wrist extension and wrist drop.");
        for (String invalid : List.of(
                "Radial nerve injury does not activate wrist flexors",
                "Wrist extensors do not remain normal despite a radial nerve injury",
                "Activated flexors do not overpower extensors",
                "Wrist extensors remain normal during a brain tumor causing wrist drop",
                "A tendon rupture activates wrist flexors rather than eliminating extension",
                "Radial nerve injury activates wrist flexors because of an invented process",
                "Wrist drop is caused by activated flexors overpowering extensors rather than a brain tumor",
                "Radial nerve injury activates wrist flexors rather than eliminating extension because of an invented process")) {
            assertThat(evaluator.evaluate(golden, withMisconception(actual, invalid)).failedRules()).as(invalid)
                    .contains("misconception-not-demonstrated-by-response");
        }
    }

    @Test
    void v5SupplyLinkedFunctionalLossPassesThePublicFeedbackGroupPath() {
        var golden = v5WrongReasoningCase();
        String latestFeedback = "You correctly identified that a radial nerve injury results in wrist drop. "
                + "However, your explanation of the mechanism is incorrect. "
                + "Radial nerve injury does not activate wrist flexors to overpower normal extensors. "
                + "Instead, the radial nerve supplies the wrist extensors, so an injury eliminates wrist extension, "
                + "which directly produces the wrist drop.";
        ResponseEvaluationResult actual = v5Result(List.of("Radial nerve injury causes wrist drop"),
                golden.expectedConcepts(), latestFeedback);
        assertThat(evaluator.evaluate(golden, actual).failedRules()).isEmpty();
        for (String feedback : List.of(
                "The radial nerve supplies the wrist extensors, so an injury eliminates wrist extension, which directly produces the wrist drop.",
                "The radial nerve supplies wrist extensors, therefore an injury causes loss of wrist extension, which causes wrist drop.",
                "Radial nerve injury causes loss of wrist extension, resulting in wrist drop.",
                "After radial nerve injury the patient cannot extend the wrist, which produces wrist drop.",
                "After radial nerve injury the patient loses wrist extension, which directly produces wrist drop.",
                "Loss of wrist extension. Radial nerve injury results in wrist drop.")) {
            assertThat(evaluator.evaluate(golden, withFeedback(actual, feedback)).failedRules()).as(feedback).isEmpty();
        }
        for (String feedback : List.of(
                "The radial nerve supplies the wrist extensors, but wrist extension remains preserved after the injury.",
                "The radial nerve supplies wrist extensors, so the injury does not eliminate wrist extension, which produces wrist drop.",
                "The radial nerve supplies wrist extensors, so loss of wrist extension causes radial nerve injury and wrist drop.",
                "The radial nerve supplies wrist extensors, but wrist extension remains normal after the injury. Wrist drop.",
                "The radial nerve supplies wrist extensors, so a tendon rupture eliminates wrist extension, which produces wrist drop.",
                "Instead the radial nerve supplies wrist extensors. So an injury eliminates wrist extension. Wrist drop.",
                "The radial nerve does not supply wrist extensors, so an injury eliminates wrist extension, which produces wrist drop.",
                "The radial nerve supplies wrist extensors, so an injury eliminates wrist extension because of an invented process, which produces wrist drop.")) {
            assertThat(evaluator.evaluate(golden, withFeedback(actual, feedback)).failedRules()).as(feedback)
                    .anyMatch(rule -> rule.startsWith("feedback-does-not-identify-gap:loss of wrist extension"));
        }
    }

    @Test
    void literalFeedbackAlternativeGetsOrdinaryPublicPathMatchingBeforeFallback() {
        var golden = v5WrongReasoningCase();
        String latestFeedback = "You correctly identified that a radial nerve injury causes wrist drop. "
                + "However, your mechanism is incorrect. The radial nerve supplies the wrist extensors; "
                + "an injury to this nerve eliminates wrist extension (paralyzing the extensors), "
                + "rather than activating the flexors to overpower normal extensors. "
                + "The loss of wrist extension is what produces the wrist drop.";
        var actual = v5Result(List.of("Radial nerve injury causes wrist drop"), golden.expectedConcepts(), latestFeedback);
        assertThat(evaluator.evaluate(golden, actual).failedRules()).isEmpty();
        for (String feedback : List.of(
                "The loss of wrist extension is what produces the wrist drop.",
                "The injury does not activate wrist flexors. The loss of wrist extension produces wrist drop.",
                "Rather than activating the flexors. The loss of wrist extension is what produces wrist drop.")) {
            assertThat(evaluator.evaluate(golden, withFeedback(actual,
                    "Radial nerve injury is correctly identified. " + feedback)).failedRules()).as(feedback).isEmpty();
        }
        for (String feedback : List.of(
                "There is no loss of wrist extension.",
                "The injury does not cause loss of wrist extension.",
                "Wrist extension remains preserved after injury.")) {
            assertThat(evaluator.evaluate(golden, withFeedback(actual,
                    "Radial nerve injury and wrist drop are correctly identified. " + feedback)).failedRules()).as(feedback)
                    .contains("feedback-does-not-identify-gap:loss of wrist extension|cannot extend the wrist");
        }
    }

    @Test
    void latestV5MisconceptionsAreCheckedIndividuallyThroughPublicEvaluator() {
        var golden = v5WrongReasoningCase();
        var baseline = v5Result(List.of("Radial nerve injury causes wrist drop"), golden.expectedConcepts(),
                "The radial nerve supplies wrist extensors. Radial nerve injury eliminates wrist extension, producing wrist drop.");
        List<String> misconceptions = List.of(
                "The wrist extensors remain normal during a radial nerve injury",
                "Radial nerve injury activates wrist flexors rather than eliminating wrist extension",
                "Wrist drop is caused by flexors overpowering normal extensors");
        for (int index = 0; index < misconceptions.size(); index++) {
            var result = evaluator.evaluate(golden, withMisconception(baseline, misconceptions.get(index)));
            assertThat(result.failedRules()).as("misconception index %s", index).isEmpty();
        }
        var complete = new ResponseEvaluationResult(baseline.evaluation(), baseline.correctConcepts(), baseline.missingConcepts(),
                misconceptions, baseline.feedback(), baseline.certainty(), baseline.recommendedAction(),
                baseline.sourceReferences(), baseline.limitations());
        assertThat(evaluator.evaluate(golden, complete).failedRules()).isEmpty();
        for (String invalid : List.of(
                "Radial nerve injury does not activate wrist flexors",
                "Flexors do not overpower the extensors",
                "Wrist drop is caused by flexors not overpowering normal extensors",
                "A brain tumor activates wrist flexors",
                "The wrist extensors remain normal because of an unrelated mechanism",
                "Wrist extensors remain normal during a brain tumor causing wrist drop",
                "Wrist drop is caused by flexors overpowering normal extensors because of an unrelated mechanism",
                "Radial nerve injury activates wrist flexors rather than eliminating wrist extension because of an unrelated mechanism")) {
            assertThat(evaluator.evaluate(golden, withMisconception(complete, invalid)).failedRules()).as(invalid)
                    .contains("misconception-not-demonstrated-by-response");
        }
    }

    @Test
    void consolidatedRelationsAcceptExactLatestV5LiveResult() {
        assertThat(evaluator.evaluate(v5WrongReasoningCase(), latestRelationResult()).failedRules()).isEmpty();
    }

    @Test
    void consolidatedRelationsUsePublicPathPositiveMatrix() {
        var golden = v5WrongReasoningCase();
        var actual = latestRelationResult();
        for (String misconception : List.of(
                "Radial nerve injury activates wrist flexors",
                "Radial nerve injury causes wrist drop by activating wrist flexors which overpower normal extensors",
                "Wrist drop is caused by activated flexors overpowering normal extensors",
                "Activated flexors overpowering normal extensors",
                "Flexors overpower normal extensors",
                "Active wrist flexors overpower otherwise normal wrist extensors",
                "Radial nerve injury leaves wrist extensors normal",
                "The wrist extensors remain normal during a radial nerve injury")) {
            assertThat(evaluator.evaluate(golden, withMisconception(actual, misconception)).failedRules())
                    .as(misconception).isEmpty();
        }
        for (String feedback : List.of(
                "Radial nerve injury eliminates wrist extension, not by activating wrist flexors to overpower normal extensors.",
                "Radial nerve injury does not activate wrist flexors, but eliminates wrist extension.",
                "Radial nerve injury causes wrist drop by eliminating wrist extension.",
                "Radial nerve injury causes loss of wrist extension.",
                "Radial nerve injury removes wrist extension.",
                "Radial nerve injury causes paralysis of wrist extensor function.",
                "Loss of wrist extension. Radial nerve injury causes wrist drop.",
                "After radial nerve injury the patient cannot extend the wrist.",
                "The radial nerve supplies wrist extensors. Radial nerve injury causes loss of extensor function.",
                "Elimination of wrist extension produces wrist drop.")) {
            assertThat(evaluator.evaluate(golden, withFeedback(actual,
                    "The radial nerve supplies wrist extensors. " + feedback + " Wrist drop is identified.")).failedRules())
                    .as(feedback).isEmpty();
        }
        for (String supply : List.of("The radial nerve supplies wrist extensors", "The radial nerve innervates wrist extensors")) {
            var result = new ResponseEvaluationResult(actual.evaluation(), actual.correctConcepts(),
                    List.of(supply, "Radial nerve injury removes wrist extension", "Elimination of wrist extension results in wrist drop"),
                    actual.misconceptions(), actual.feedback(), actual.certainty(), actual.recommendedAction(),
                    actual.sourceReferences(), actual.limitations());
            assertThat(evaluator.evaluate(golden, result).failedRules()).as(supply).isEmpty();
            var partial = partialFeedbackResult("Loss of wrist extension produces wrist drop.");
            var supplyResult = new ResponseEvaluationResult(partial.evaluation(), List.of(supply), partial.missingConcepts(),
                    partial.misconceptions(), partial.feedback(), partial.certainty(), partial.recommendedAction(),
                    partial.sourceReferences(), partial.limitations());
            assertThat(evaluator.evaluate(response("P7-09-PARTIAL-001"), supplyResult).failedRules()).as(supply).isEmpty();
        }
    }

    @Test
    void consolidatedRelationsUsePublicPathNegativeMatrix() {
        var golden = v5WrongReasoningCase();
        var actual = latestRelationResult();
        for (String misconception : List.of(
                "Radial nerve injury does not activate wrist flexors",
                "Flexors do not overpower extensors",
                "Wrist drop is caused by flexors not overpowering normal extensors",
                "A brain tumor activates wrist flexors",
                "Median nerve injury activates wrist flexors",
                "Radial nerve injury activates wrist flexors because of an unrelated mechanism",
                "The wrist extensors remain normal because of an unrelated mechanism",
                "Normal extensors overpower activated flexors",
                "Radial nerve injury and wrist flexors and normal extensors",
                "Radial nerve",
                "Wrist extensors remain normal")) {
            assertThat(evaluator.evaluate(golden, withMisconception(actual, misconception)).failedRules()).as(misconception)
                    .contains("misconception-not-demonstrated-by-response");
        }
        for (String feedback : List.of(
                "There is no loss of wrist extension.",
                "Wrist extension is preserved.",
                "Radial nerve injury does not eliminate wrist extension.",
                "Loss of wrist extension causes radial nerve injury.",
                "A brain tumor eliminates wrist extension.",
                "Radial nerve injury eliminates wrist extension by activating unrelated muscles.",
                "Radial nerve injury eliminates wrist extension because of an unrelated mechanism.",
                "Radial nerve injury. Eliminating wrist extension. Wrist drop.",
                "Radial nerve. Loss of wrist extension.")) {
            assertThat(evaluator.evaluate(golden, withFeedback(actual,
                    (feedback.equals("Radial nerve. Loss of wrist extension.") ? "" : "The radial nerve supplies wrist extensors. ")
                            + feedback)).failedRules()).as(feedback)
                    .anyMatch(rule -> rule.startsWith("feedback-does-not-identify-gap:loss of wrist extension"));
        }
        for (String supply : List.of("Wrist extensors innervate the radial nerve", "The radial nerve does not supply wrist extensors",
                "A brain tumor innervates wrist extensors", "Radial nerve. Wrist extensors.")) {
            var partial = partialFeedbackResult("Loss of wrist extension produces wrist drop.");
            var result = new ResponseEvaluationResult(partial.evaluation(), List.of(supply), partial.missingConcepts(),
                    partial.misconceptions(), partial.feedback(), partial.certainty(), partial.recommendedAction(),
                    partial.sourceReferences(), partial.limitations());
            assertThat(evaluator.evaluate(response("P7-09-PARTIAL-001"), result).failedRules()).as(supply)
                    .anyMatch(rule -> rule.startsWith("required-correct-concept"));
        }
    }

    @Test
    void modifiedSupplyAndParentheticalFeedbackAreTracedThroughPublicPath() {
        String supply = "The radial nerve directly supplies the wrist extensors.";
        String injury = "An injury to this nerve eliminates wrist extension (causing the hand to drop), "
                + "rather than activating wrist flexors to overpower normal extensors.";
        String feedback = "You correctly identified that a radial nerve injury causes wrist drop. "
                + "However, your explanation of the mechanism is incorrect. " + supply + " " + injury;
        assertThat(evaluator.evaluate(v5WrongReasoningCase(), withFeedback(latestRelationResult(), feedback)).failedRules())
                .isEmpty();
        var supplied = WristDropRelations.parse(supply, false, false);
        assertThat(supplied.complete()).isTrue();
        assertThat(supplied.affirmative()).contains(new WristDropRelations.Relation(
                WristDropRelations.Entity.NERVE, WristDropRelations.Predicate.SUPPLIES, WristDropRelations.Entity.EXTENSORS));
        assertThat(WristDropRelations.parse("The radial nerve supplies the wrist extensors.", false, false).complete()).isTrue();
        var referential = WristDropRelations.parse(injury, true, false);
        assertThat(referential.affirmative()).contains(new WristDropRelations.Relation(
                WristDropRelations.Entity.INJURY, WristDropRelations.Predicate.LOSES, WristDropRelations.Entity.EXTENSION));
        assertThat(referential.complete()).isTrue();
        assertThat(referential.affirmative()).contains(new WristDropRelations.Relation(
                WristDropRelations.Entity.LOSS, WristDropRelations.Predicate.CAUSES, WristDropRelations.Entity.DROP));
        assertThat(referential.rejected()).contains(new WristDropRelations.Relation(
                WristDropRelations.Entity.INJURY, WristDropRelations.Predicate.ACTIVATES, WristDropRelations.Entity.FLEXORS));
    }

    @Test
    void modifiedSupplyAndParentheticalCompositionRetainBoundedRelations() {
        var golden = v5WrongReasoningCase();
        var baseline = latestRelationResult();
        for (String feedback : List.of(
                "The radial nerve supplies wrist extensors. An injury to this nerve eliminates wrist extension.",
                "The radial nerve directly supplies the wrist extensors. An injury to this nerve eliminates wrist extension.",
                "The radial nerve normally supplies the wrist extensors. An injury to this nerve eliminates wrist extension.",
                "The radial nerve supplies directly the wrist extensors. An injury to this nerve eliminates wrist extension.",
                "The radial nerve directly supplies the wrist extensors. An injury to this nerve eliminates wrist extension (causing the hand to drop).",
                "The radial nerve directly supplies the wrist extensors. An injury to this nerve eliminates wrist extension (paralyzing the extensors).",
                "The radial nerve directly supplies the wrist extensors. An injury to this nerve eliminates wrist extension, rather than activating wrist flexors.",
                "The radial nerve directly supplies the wrist extensors. An injury to this nerve eliminates wrist extension (causing the hand to drop), rather than activating wrist flexors.",
                "Radial nerve injury eliminates wrist extension.")) {
            assertThat(WristDropRelations.functionalLoss(feedback, true)).as(feedback).isTrue();
            assertThat(evaluator.evaluate(golden, withFeedback(baseline, feedback + " Wrist drop is identified.")).failedRules())
                    .as(feedback).isEmpty();
        }
        for (String feedback : List.of(
                "The radial nerve directly supplies the wrist extensors. An injury to this nerve preserves wrist extension.",
                "The radial nerve directly supplies the wrist extensors. An injury to this nerve does not eliminate wrist extension.",
                "An injury to this nerve does not eliminate wrist extension.",
                "A different nerve directly supplies the wrist extensors. An injury to this nerve eliminates wrist extension.",
                "Wrist extensors supply the radial nerve. An injury to this nerve eliminates wrist extension.",
                "The radial nerve directly supplies wrist extensors. A different nerve is injured. An injury to this nerve eliminates wrist extension.",
                "The radial nerve normally does not supply wrist extensors. An injury to this nerve eliminates wrist extension.",
                "The radial nerve does not directly supply wrist extensors. An injury to this nerve eliminates wrist extension.",
                "Loss of wrist extension causes radial nerve injury.",
                "Radial nerve injury. Wrist extension. Hand to drop.",
                "The radial nerve directly supplies wrist extensors. An injury to this nerve eliminates wrist extension (because of an unrelated mechanism).",
                "The radial nerve directly supplies wrist extensors. An injury to this nerve does not eliminate wrist extension (causing the hand to drop).",
                "The radial nerve directly supplies wrist extensors. An injury to this nerve eliminates wrist extension (not eliminating wrist extension).")) {
            assertThat(WristDropRelations.functionalLoss(feedback, true)).as(feedback).isFalse();
            assertThat(evaluator.evaluate(golden, withFeedback(baseline, feedback + " Wrist drop is identified.")).failedRules())
                    .as(feedback).contains("feedback-does-not-identify-gap:loss of wrist extension|cannot extend the wrist");
        }
    }

    @Test
    void latestAdverbFeedbackCanonicalRelationsAndPublicPathPass() {
        String cause = "You correctly identified that a radial nerve injury results in wrist drop.";
        String rejected = "A radial nerve injury does not activate wrist flexors to overpower the extensors.";
        String chain = "Instead, the radial nerve supplies the wrist extensors, so an injury directly eliminates wrist extension, "
                + "which results in the wrist drop.";
        var causeClaim = WristDropRelations.parse(cause, false, false);
        assertThat(causeClaim.affirmative()).containsExactly(new WristDropRelations.Relation(
                WristDropRelations.Entity.INJURY, WristDropRelations.Predicate.CAUSES, WristDropRelations.Entity.DROP));
        var rejectedClaim = WristDropRelations.parse(rejected, false, false);
        assertThat(rejectedClaim.complete()).isTrue();
        assertThat(rejectedClaim.rejected()).containsExactlyInAnyOrder(
                new WristDropRelations.Relation(WristDropRelations.Entity.INJURY, WristDropRelations.Predicate.ACTIVATES, WristDropRelations.Entity.FLEXORS),
                new WristDropRelations.Relation(WristDropRelations.Entity.FLEXORS, WristDropRelations.Predicate.OVERPOWERS, WristDropRelations.Entity.EXTENSORS));
        var chainClaim = WristDropRelations.parse(chain, false, false);
        assertThat(chainClaim.complete()).isTrue();
        assertThat(chainClaim.affirmative()).containsExactlyInAnyOrder(
                new WristDropRelations.Relation(WristDropRelations.Entity.NERVE, WristDropRelations.Predicate.SUPPLIES, WristDropRelations.Entity.EXTENSORS),
                new WristDropRelations.Relation(WristDropRelations.Entity.INJURY, WristDropRelations.Predicate.LOSES, WristDropRelations.Entity.EXTENSION),
                new WristDropRelations.Relation(WristDropRelations.Entity.LOSS, WristDropRelations.Predicate.CAUSES, WristDropRelations.Entity.DROP));
        assertThat(WristDropRelations.parse("The radial nerve supplies wrist extensors, so an injury eliminates wrist extension.",
                false, false).complete()).isTrue();
        String feedback = cause + " However, your explanation of the mechanism is incorrect. " + rejected + " " + chain;
        assertThat(evaluator.evaluate(v5WrongReasoningCase(), withFeedback(latestRelationResult(), feedback)).failedRules())
                .isEmpty();
        var evidence = WristDropRelations.propositions(feedback);
        assertThat(evidence.affirmative()).containsAll(causeClaim.affirmative()).containsAll(chainClaim.affirmative());
        assertThat(evidence.rejected()).containsAll(rejectedClaim.rejected());
    }

    @Test
    void functionalLossRubricUsesBoundedPropositionsAndImmediateReferents() {
        var golden = v5WrongReasoningCase();
        var baseline = latestRelationResult();
        for (String feedback : List.of(
                "The radial nerve supplies wrist extensors, so an injury eliminates wrist extension.",
                "The radial nerve supplies wrist extensors, so an injury directly eliminates wrist extension.",
                "The radial nerve supplies wrist extensors. An injury to this nerve eliminates wrist extension.",
                "The radial nerve supplies wrist extensors. An injury eliminates wrist extension, which produces wrist drop.",
                "Radial nerve injury directly eliminates wrist extension.",
                "Radial nerve injury does not directly eliminate wrist extension. Instead, the injury eliminates wrist extension.",
                "Radial nerve injury does not activate the flexors. Instead, it eliminates wrist extension.",
                "Radial nerve injury eliminates wrist extension, which explains the clinical finding.",
                "The radial nerve supplies wrist extensors, so an injury eliminates wrist extension, which explains the clinical finding.")) {
            assertThat(evaluator.evaluate(golden, withFeedback(baseline, feedback + " Wrist drop is identified.")).failedRules())
                    .as(feedback).isEmpty();
        }
        for (String feedback : List.of(
                "The median nerve supplies another structure, so an injury eliminates wrist extension.",
                "An injury eliminates wrist extension.",
                "Radial nerve injury does not directly eliminate wrist extension.",
                "There is no loss of wrist extension.",
                "Wrist extension remains preserved.",
                "Radial nerve injury. It eliminates wrist extension.",
                "Radial nerve injury does not activate wrist flexors. A different nerve is injured. Instead, it eliminates wrist extension.",
                "The radial nerve supplies wrist extensors. A brain tumor is discussed. An injury eliminates wrist extension.",
                "Radial nerve injury directly eliminates wrist extension because of an invented process.",
                "Radial nerve injury eliminates wrist extension, which causes radial nerve injury.",
                "Radial nerve injury eliminates wrist extension, which explains the finding because of an invented process.",
                "Radial nerve injury eliminates wrist extension, which explains why wrist extension is preserved.")) {
            assertThat(evaluator.evaluate(golden, withFeedback(baseline, feedback + " Wrist drop is identified.")).failedRules())
                    .as(feedback).contains("feedback-does-not-identify-gap:loss of wrist extension|cannot extend the wrist");
        }
    }

    private static ResponseEvaluationResult latestRelationResult() {
        // Preserve all supplied live fields verbatim; auxiliary contract fields use the fixture defaults.
        return new ResponseEvaluationResult(Evaluation.PARTIAL,
                List.of("Radial nerve injury causes wrist drop"),
                List.of("The radial nerve supplies wrist extensors", "Radial nerve injury eliminates wrist extension",
                        "Loss of wrist extension produces wrist drop"),
                List.of("Radial nerve injury leaves wrist extensors normal",
                        "Radial nerve injury causes wrist drop by activating wrist flexors which overpower normal extensors",
                        "Wrist drop is caused by activated flexors overpowering normal extensors"),
                "You correctly identified that a radial nerve injury causes wrist drop. However, your mechanism is incorrect. "
                        + "The radial nerve supplies the wrist extensors. Therefore, a radial nerve injury causes wrist drop "
                        + "by eliminating wrist extension (paralyzing the extensors),not by activating wrist flexors to overpower normal extensors.",
                EvaluationCertainty.SUFFICIENT, RecommendedAction.TARGETED_EXPLANATION,
                List.of("00000000-0000-0000-0000-000000000304"), List.of());
    }

    private static ResponseEvaluationResult withFeedback(ResponseEvaluationResult actual, String feedback) {
        return new ResponseEvaluationResult(actual.evaluation(), actual.correctConcepts(), actual.missingConcepts(),
                actual.misconceptions(), feedback, actual.certainty(), actual.recommendedAction(),
                actual.sourceReferences(), actual.limitations());
    }

    private static GoldenAiDataset.ResponseEvaluationCase v5WrongReasoningCase() {
        return new GoldenAiDatasetLoader().loadAll().responseEvaluations().stream()
                .filter(value -> value.caseId().equals("P7-09-WRONG-REASONING-001")).findFirst().orElseThrow();
    }

    private static ResponseEvaluationResult withMisconception(ResponseEvaluationResult actual, String misconception) {
        return new ResponseEvaluationResult(actual.evaluation(), actual.correctConcepts(), actual.missingConcepts(),
                List.of(misconception), actual.feedback(), actual.certainty(), actual.recommendedAction(),
                actual.sourceReferences(), actual.limitations());
    }

    private static ResponseEvaluationResult v5Result(List<String> correct, List<String> missing, String feedback) {
        return new ResponseEvaluationResult(Evaluation.PARTIAL, correct, missing,
                List.of("Radial nerve injury activates wrist flexors"), feedback,
                EvaluationCertainty.SUFFICIENT, RecommendedAction.TARGETED_EXPLANATION,
                List.of("00000000-0000-0000-0000-000000000304"), List.of());
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
