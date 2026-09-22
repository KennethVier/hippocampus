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
import com.hippocampus.ai.application.routing.ProviderId;
import com.hippocampus.ai.domain.AiOutputContract;
import com.hippocampus.ai.domain.ConceptConnectionResult;
import com.hippocampus.ai.domain.ContextualApplicationResult;
import com.hippocampus.ai.domain.ExplanationResult;
import com.hippocampus.ai.domain.QuestionGenerationResult;
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
                Arguments.of(AiOutputContract.CONCEPT_CONNECTION, """
                        {
                          "fromConcept": "alveolar ventilation",
                          "toConcept": "arterial carbon dioxide",
                          "relationshipType": "inverse physiological relationship",
                          "relationship": "Increasing alveolar ventilation lowers arterial carbon dioxide.",
                          "whyItMatters": "It explains respiratory compensation and ventilatory disorders.",
                          "sourceReferences": [],
                          "limitations": []
                        }
                        """, ConceptConnectionResult.class),
                Arguments.of(AiOutputContract.CONTEXTUAL_APPLICATION, """
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
                        """, ContextualApplicationResult.class));
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
}
