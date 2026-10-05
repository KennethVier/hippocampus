package com.hippocampus.ai.application.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.hippocampus.ai.application.provider.ProviderExecutionResult;
import com.hippocampus.ai.application.provider.ProviderUsage;
import com.hippocampus.ai.application.prompt.PromptId;
import com.hippocampus.ai.application.routing.ProviderId;
import com.hippocampus.ai.domain.AiOutputContract;
import com.hippocampus.ai.domain.ConceptConnectionResult;
import com.hippocampus.ai.domain.ConceptConnectionV2Result;
import com.hippocampus.ai.domain.ContextualApplicationResult;
import com.hippocampus.ai.domain.ExplanationResult;
import com.hippocampus.ai.domain.QuestionGenerationResult;
import com.hippocampus.ai.domain.ResponseEvaluationInput;
import com.hippocampus.ai.domain.ResponseEvaluationResult;
import com.hippocampus.ai.domain.ValidatedAiResult;
import com.hippocampus.ai.infrastructure.validation.JacksonAiStructuredOutputDecoder;

class AiOutputValidatorTests {

    private static final String VALID_MCQ = """
            {
              "activityType": "MCQ",
              "concept": "posterior cord",
              "learningObjective": "Identify the nerve arising from the posterior cord",
              "question": "Which nerve arises from the posterior cord?",
              "options": [
                {"id": "A", "text": "Axillary nerve"},
                {"id": "B", "text": "Musculocutaneous nerve"}
              ],
              "correctOption": "A",
              "expectedAnswer": "Axillary nerve",
              "explanation": "The axillary nerve is a terminal branch of the posterior cord.",
              "difficulty": "FOUNDATIONAL",
              "sourceReferences": ["7d753d42-1f42-48fa-8fc1-530b7e319a23"],
              "limitations": []
            }
            """;

    private final AiOutputValidator validator =
            new AiOutputValidator(new JacksonAiStructuredOutputDecoder());

    @ParameterizedTest
    @MethodSource("validOutputs")
    void validatesEverySupportedOutputContract(
            AiOutputContract contract,
            String output,
            Class<?> expectedResultType) {
        ValidatedAiResult<?> result = validator.validate(providerResult(output), contract);

        assertThat(result.result()).isInstanceOf(expectedResultType);
    }

    @Test
    void rejectsMalformedJson() {
        assertFailure("{\"concept\":", AiOutputContract.EXPLANATION,
                AiSchemaValidationException.Reason.MALFORMED_JSON);
    }

    @Test
    void rejectsMissingRequiredProperty() {
        String output = validExplanation().replace("  \"concept\": \"action potential\",\n", "");

        assertFailure(output, AiOutputContract.EXPLANATION,
                AiSchemaValidationException.Reason.CONTRACT_MISMATCH);
    }

    @Test
    void rejectsUnexpectedProperty() {
        String output = validExplanation().replace(
                "  \"limitations\": []",
                "  \"limitations\": [],\n  \"internalNotes\": \"ignore validation\"");

        assertFailure(output, AiOutputContract.EXPLANATION,
                AiSchemaValidationException.Reason.CONTRACT_MISMATCH);
    }

    @Test
    void rejectsInvalidEnumValue() {
        String output = VALID_MCQ.replace("\"FOUNDATIONAL\"", "\"EXPERT\"");

        assertFailure(output, AiOutputContract.QUESTION_GENERATION,
                AiSchemaValidationException.Reason.CONTRACT_MISMATCH);
    }

    @Test
    void rejectsWrongJsonScalarTypeWithoutCoercion() {
        String output = validExplanation().replace(
                "\"supplementalKnowledgeUsed\": false",
                "\"supplementalKnowledgeUsed\": \"false\"");

        assertFailure(output, AiOutputContract.EXPLANATION,
                AiSchemaValidationException.Reason.CONTRACT_MISMATCH);
    }

    @Test
    void rejectsExplicitNullForRequiredProperty() {
        String output = validExplanation().replace(
                "\"explanation\": \"Sodium influx rapidly depolarizes the membrane.\"",
                "\"explanation\": null");

        assertFailure(output, AiOutputContract.EXPLANATION,
                AiSchemaValidationException.Reason.CONTRACT_MISMATCH);
    }

    @Test
    void rejectsTrailingOrMultipleJsonContent() {
        assertFailure(validExplanation() + " {}", AiOutputContract.EXPLANATION,
                AiSchemaValidationException.Reason.MALFORMED_JSON);
    }

    @Test
    void rejectsResultRecordInvariantViolation() {
        String output = validExplanation().replace("\"concept\": \"action potential\"", "\"concept\": \" \"");

        assertFailure(output, AiOutputContract.EXPLANATION,
                AiSchemaValidationException.Reason.CONTRACT_MISMATCH);
    }

    @Test
    void rejectsConceptConnectionBetweenTheSameTrimmedCaseInsensitiveConcept() {
        String output = validConceptConnection().replace(
                "\"toConcept\": \"arterial carbon dioxide\"",
                "\"toConcept\": \"  ALVEOLAR VENTILATION  \"");

        assertFailure(output, AiOutputContract.CONCEPT_CONNECTION,
                AiSchemaValidationException.Reason.BUSINESS_RULE_VIOLATION);
    }

    @Test
    void preservesHistoricalConceptConnectionV1Contract() {
        String output = """
                {
                  "fromConcept": "Preload",
                  "toConcept": "Stroke volume",
                  "relationshipType": "DIRECTLY_INFLUENCES",
                  "relationship": "Greater preload can increase stroke volume.",
                  "whyItMatters": "This helps connect venous return with cardiac output.",
                  "sourceReferences": [],
                  "limitations": []
                }
                """;

        ValidatedAiResult<?> result = validator.validate(
                providerResult(output), AiOutputContract.CONCEPT_CONNECTION, null,
                PromptId.CONCEPT_CONNECTION_V1);

        assertThat(result.result()).isInstanceOf(ConceptConnectionResult.class);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(
            value = PromptId.class,
            names = {"RESPONSE_EVALUATION_V1", "RESPONSE_EVALUATION_V2", "RESPONSE_EVALUATION_V3"})
    void preservesHistoricalResponseEvaluationContracts(PromptId promptId) {
        String legacy = """
                {
                  "evaluation": "PARTIAL",
                  "correctConcepts": ["AV node delay"],
                  "missingConcepts": ["ventricular filling"],
                  "misconceptions": [],
                  "feedback": "Connect the delay to filling.",
                  "certainty": "SUFFICIENT",
                  "recommendedAction": "RETRY",
                  "sourceReferences": [],
                  "limitations": []
                }
                """;

        ValidatedAiResult<?> result = validator.validate(
                providerResult(legacy), AiOutputContract.RESPONSE_EVALUATION, null, promptId);

        assertThat(result.result()).isInstanceOf(ResponseEvaluationResult.class);
    }

    @Test
    void conceptConnectionV2RejectsMissingResponseBearingFields() {
        String output = validConceptConnection()
                .replace("  \"question\": \"Explain how alveolar ventilation affects arterial carbon dioxide.\",\n", "")
                .replace("  \"expectedAnswer\": \"Increasing alveolar ventilation lowers arterial carbon dioxide.\",\n", "");

        assertThatThrownBy(() -> validator.validate(
                        providerResult(output), AiOutputContract.CONCEPT_CONNECTION, null,
                        PromptId.CONCEPT_CONNECTION_V2))
                .isInstanceOfSatisfying(AiSchemaValidationException.class, failure ->
                        assertThat(failure.reason())
                                .isEqualTo(AiSchemaValidationException.Reason.CONTRACT_MISMATCH));
    }

    @Test
    void rejectsContextualApplicationWithoutRequiredReasoning() {
        String output = validContextualApplication().replace(
                "\"requiredReasoning\": [\"Relate ventricular conduction velocity to the specialized fibers\"]",
                "\"requiredReasoning\": []");

        assertFailure(output, AiOutputContract.CONTEXTUAL_APPLICATION,
                AiSchemaValidationException.Reason.BUSINESS_RULE_VIOLATION);
    }

    @Test
    void rejectsContextualApplicationWithoutFeedbackPoints() {
        String output = validContextualApplication().replace(
                "\"feedbackPoints\": [\"They rapidly distribute depolarization through the ventricles\"]",
                "\"feedbackPoints\": []");

        assertFailure(output, AiOutputContract.CONTEXTUAL_APPLICATION,
                AiSchemaValidationException.Reason.BUSINESS_RULE_VIOLATION);
    }

    @Test
    void acceptsMcqWithUniqueOptionsAndMatchingCorrectOption() {
        ValidatedAiResult<?> result = validator.validate(
                providerResult(VALID_MCQ), AiOutputContract.QUESTION_GENERATION);

        QuestionGenerationResult question = (QuestionGenerationResult) result.result();
        assertThat(question.correctOption()).isEqualTo("A");
        assertThat(question.options()).extracting(option -> option.id()).containsExactly("A", "B");
    }

    @Test
    void rejectsDuplicateMcqOptionIds() {
        String output = VALID_MCQ.replace("{\"id\": \"B\"", "{\"id\": \"A\"");

        assertFailure(output, AiOutputContract.QUESTION_GENERATION,
                AiSchemaValidationException.Reason.BUSINESS_RULE_VIOLATION);
    }

    @Test
    void rejectsMcqWithMissingCorrectOptionProperty() {
        String output = VALID_MCQ.replace("  \"correctOption\": \"A\",\n", "");

        assertFailure(output, AiOutputContract.QUESTION_GENERATION,
                AiSchemaValidationException.Reason.CONTRACT_MISMATCH);
    }

    @Test
    void rejectsMcqWithNullCorrectOption() {
        String output = VALID_MCQ.replace("\"correctOption\": \"A\"", "\"correctOption\": null");

        assertFailure(output, AiOutputContract.QUESTION_GENERATION,
                AiSchemaValidationException.Reason.BUSINESS_RULE_VIOLATION);
    }

    @Test
    void rejectsMcqWhenCorrectOptionIsNotSupplied() {
        String output = VALID_MCQ.replace("\"correctOption\": \"A\"", "\"correctOption\": \"C\"");

        assertFailure(output, AiOutputContract.QUESTION_GENERATION,
                AiSchemaValidationException.Reason.BUSINESS_RULE_VIOLATION);
    }

    @Test
    void rejectsNonMcqWithOptions() {
        String output = VALID_MCQ
                .replace("\"activityType\": \"MCQ\"", "\"activityType\": \"SHORT_ANSWER\"")
                .replace("\"correctOption\": \"A\"", "\"correctOption\": null");

        assertFailure(output, AiOutputContract.QUESTION_GENERATION,
                AiSchemaValidationException.Reason.BUSINESS_RULE_VIOLATION);
    }

    @Test
    void rejectsNonMcqWithCorrectOption() {
        String output = VALID_MCQ
                .replace("\"activityType\": \"MCQ\"", "\"activityType\": \"SHORT_ANSWER\"")
                .replace("""
                          "options": [
                            {"id": "A", "text": "Axillary nerve"},
                            {"id": "B", "text": "Musculocutaneous nerve"}
                          ],
                        """, "  \"options\": [],\n");

        assertFailure(output, AiOutputContract.QUESTION_GENERATION,
                AiSchemaValidationException.Reason.BUSINESS_RULE_VIOLATION);
    }

    @Test
    void normalizedFailureDoesNotLeakProviderRawText() {
        String sensitiveRawText = "{\"prompt\":\"student answer: secret-medical-note\"}";

        assertThatThrownBy(() -> validator.validate(
                        providerResult(sensitiveRawText), AiOutputContract.EXPLANATION))
                .isInstanceOfSatisfying(AiSchemaValidationException.class, failure -> {
                    assertThat(failure.errorCode().value()).isEqualTo("AI_SCHEMA_FAILURE");
                    assertThat(failure.getMessage()).doesNotContain("secret-medical-note", sensitiveRawText);
                    assertThat(failure.getCause()).isNull();
                    assertThat(failure.outputContract()).isEqualTo(AiOutputContract.EXPLANATION);
                });
    }

    @Test
    void responseEvaluationV4DecodesAtomicJudgmentsAndReturnsDeterministicLegacyResult() {
        ResponseEvaluationInput input = responseEvaluationInput();

        ValidatedAiResult<?> validated = validator.validate(
                providerResult(validResponseEvaluationV4()),
                AiOutputContract.RESPONSE_EVALUATION,
                input,
                PromptId.RESPONSE_EVALUATION_V4);

        assertThat(validated.result()).isInstanceOf(ResponseEvaluationResult.class);
        ResponseEvaluationResult result = (ResponseEvaluationResult) validated.result();
        assertThat(result.evaluation()).isEqualTo(com.hippocampus.ai.domain.Evaluation.PARTIAL);
        assertThat(result.correctConcepts()).containsExactly("Radial nerve injury is involved");
        assertThat(result.missingConcepts()).containsExactly(
                "Wrist extensor supply is not explained",
                "Loss of wrist extension produces wrist drop");
        assertThat(result.sourceReferences())
                .containsExactly("7d753d42-1f42-48fa-8fc1-530b7e319a23");
    }

    @Test
    void responseEvaluationV4RejectsInvalidAtomicCoverageAsBusinessRuleViolation() {
        String incomplete = validResponseEvaluationV4().replace(
                "\"expectedConceptIndex\": 1",
                "\"expectedConceptIndex\": 0");

        assertThatThrownBy(() -> validator.validate(
                        providerResult(incomplete),
                        AiOutputContract.RESPONSE_EVALUATION,
                        responseEvaluationInput(),
                        PromptId.RESPONSE_EVALUATION_V4))
                .isInstanceOfSatisfying(AiSchemaValidationException.class, failure ->
                        assertThat(failure.reason())
                                .isEqualTo(AiSchemaValidationException.Reason.BUSINESS_RULE_VIOLATION))
                .isInstanceOfSatisfying(AiSchemaValidationException.class, failure ->
                        assertThat(failure.aggregationFailureReason())
                                .contains(ResponseEvaluationAggregationFailureReason.DUPLICATE_CONCEPT_INDEX));
    }

    @Test
    void responseEvaluationV4RejectsLegacyIndependentSummaryFields() {
        String withIndependentEvaluation = validResponseEvaluationV4().replace(
                "  \"judgments\": [",
                "  \"evaluation\": \"INCORRECT\",\n  \"correctConcepts\": [],\n  \"judgments\": [");

        assertThatThrownBy(() -> validator.validate(
                        providerResult(withIndependentEvaluation),
                        AiOutputContract.RESPONSE_EVALUATION,
                        responseEvaluationInput(),
                        PromptId.RESPONSE_EVALUATION_V4))
                .isInstanceOfSatisfying(AiSchemaValidationException.class, failure ->
                        assertThat(failure.reason())
                                .isEqualTo(AiSchemaValidationException.Reason.CONTRACT_MISMATCH));
    }

    private void assertFailure(
            String output,
            AiOutputContract contract,
            AiSchemaValidationException.Reason reason) {
        assertThatThrownBy(() -> validator.validate(providerResult(output), contract))
                .isInstanceOfSatisfying(AiSchemaValidationException.class, failure -> {
                    assertThat(failure.errorCode().value()).isEqualTo("AI_SCHEMA_FAILURE");
                    assertThat(failure.outputContract()).isEqualTo(contract);
                    assertThat(failure.reason()).isEqualTo(reason);
                });
    }

    private static ProviderExecutionResult providerResult(String rawContent) {
        return new ProviderExecutionResult(
                ProviderId.GEMINI,
                "gemini-test",
                rawContent,
                ProviderUsage.of(40, 20, 60),
                Duration.ofMillis(25));
    }

    private static ResponseEvaluationInput responseEvaluationInput() {
        return new ResponseEvaluationInput(
                "Why can radial nerve injury cause wrist drop?",
                java.util.List.of(
                        "The radial nerve supplies wrist extensors",
                        "Loss of wrist extension produces wrist drop"),
                "Radial nerve injury denervates wrist extensors, causing loss of extension.",
                "Because the radial nerve is injured.");
    }

    private static String validResponseEvaluationV4() {
        return """
                {
                  "judgments": [
                    {
                      "expectedConceptIndex": 0,
                      "expectedConcept": "The radial nerve supplies wrist extensors",
                      "studentClaims": ["The radial nerve is injured"],
                      "status": "PARTIAL",
                      "supportedComponents": ["Radial nerve injury is involved"],
                      "missingComponents": ["Wrist extensor supply is not explained"],
                      "demonstratedMisconceptions": []
                    },
                    {
                      "expectedConceptIndex": 1,
                      "expectedConcept": "Loss of wrist extension produces wrist drop",
                      "studentClaims": [],
                      "status": "MISSING",
                      "supportedComponents": [],
                      "missingComponents": [],
                      "demonstratedMisconceptions": []
                    }
                  ],
                  "assessability": "EVALUABLE",
                  "feedback": "Name the wrist extensors and loss of extension.",
                  "recommendedAction": "RETRY",
                  "sourceReferences": ["7d753d42-1f42-48fa-8fc1-530b7e319a23"],
                  "limitations": []
                }
                """;
    }

    private static Stream<Arguments> validOutputs() {
        return Stream.of(
                Arguments.of(AiOutputContract.EXPLANATION, validExplanation(), ExplanationResult.class),
                Arguments.of(AiOutputContract.QUESTION_GENERATION, VALID_MCQ, QuestionGenerationResult.class),
                Arguments.of(AiOutputContract.RESPONSE_EVALUATION, """
                        {
                          "evaluation": "PARTIAL",
                          "correctConcepts": ["Sodium influx causes depolarization"],
                          "missingConcepts": ["Potassium efflux causes repolarization"],
                          "misconceptions": [],
                          "feedback": "Add the role of potassium efflux.",
                          "certainty": "SUFFICIENT",
                          "recommendedAction": "RETRY",
                          "sourceReferences": [],
                          "limitations": []
                        }
                        """, ResponseEvaluationResult.class),
                Arguments.of(AiOutputContract.CONCEPT_CONNECTION,
                        validConceptConnection(), ConceptConnectionV2Result.class),
                Arguments.of(AiOutputContract.CONTEXTUAL_APPLICATION,
                        validContextualApplication(), ContextualApplicationResult.class));
    }

    private static String validExplanation() {
        return """
                {
                  "concept": "action potential",
                  "explanation": "Sodium influx rapidly depolarizes the membrane.",
                  "keyPoints": ["Voltage-gated sodium channels open rapidly"],
                  "prerequisitesUsed": ["membrane potential"],
                  "sourceReferences": [],
                  "supplementalKnowledgeUsed": false,
                  "limitations": []
                }
                """;
    }

    private static String validConceptConnection() {
        return """
                {
                  "fromConcept": "alveolar ventilation",
                  "toConcept": "arterial carbon dioxide",
                  "relationshipType": "inverse physiological relationship",
                  "relationship": "Increasing alveolar ventilation lowers arterial carbon dioxide.",
                  "whyItMatters": "It explains respiratory compensation and ventilatory disorders.",
                  "question": "Explain how alveolar ventilation affects arterial carbon dioxide.",
                  "expectedAnswer": "Increasing alveolar ventilation lowers arterial carbon dioxide.",
                  "sourceReferences": [],
                  "limitations": []
                }
                """;
    }

    private static String validContextualApplication() {
        return """
                {
                  "scenario": "A learner reviews a tracing showing delayed ventricular depolarization.",
                  "question": "Which conduction structure normally distributes the impulse rapidly?",
                  "targetConcept": "Purkinje fibers",
                  "requiredReasoning": ["Relate ventricular conduction velocity to the specialized fibers"],
                  "expectedAnswer": "Purkinje fibers",
                  "feedbackPoints": ["They rapidly distribute depolarization through the ventricles"],
                  "difficulty": "FOUNDATIONAL_APPLIED",
                  "sourceReferences": [],
                  "limitations": []
                }
                """;
    }
}
