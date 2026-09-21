package com.hippocampus.ai.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hippocampus.rag.domain.EvidencePackage;
import com.hippocampus.rag.domain.GroundingMode;
import com.hippocampus.rag.domain.RetrievalDiagnostics;
import com.hippocampus.rag.domain.RetrievalQuality;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AiTaskContractsTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void definesExactCanonicalTaskAndOutputContractCoverage() {
        assertThat(EnumSet.allOf(AiTaskType.class)).containsExactly(
                AiTaskType.EXPLANATION,
                AiTaskType.QUESTION_GENERATION,
                AiTaskType.RESPONSE_EVALUATION,
                AiTaskType.CONCEPT_CONNECTION,
                AiTaskType.CONTEXTUAL_APPLICATION,
                AiTaskType.STRUCTURED_OUTPUT_REPAIR);
        assertThat(EnumSet.allOf(AiOutputContract.class)).containsExactly(
                AiOutputContract.EXPLANATION,
                AiOutputContract.QUESTION_GENERATION,
                AiOutputContract.RESPONSE_EVALUATION,
                AiOutputContract.CONCEPT_CONNECTION,
                AiOutputContract.CONTEXTUAL_APPLICATION);
    }

    @Test
    void enforcesNormalTaskContextAndOutputCompatibility() {
        assertThatThrownBy(() -> request(
                        AiTaskType.EXPLANATION,
                        new ConceptConnectionInput("cord", "connect", List.of()),
                        AiOutputContract.EXPLANATION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("taskContext");
        assertThatThrownBy(() -> request(
                        AiTaskType.EXPLANATION,
                        new ExplanationInput("explain", "cord", ExplanationMode.STANDARD),
                        AiOutputContract.CONCEPT_CONNECTION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outputContract");
    }

    @Test
    void repairTargetsAnyExistingPrimaryOutputContract() {
        for (AiOutputContract outputContract : AiOutputContract.values()) {
            AiTaskRequest<StructuredOutputRepairInput> request = request(
                    AiTaskType.STRUCTURED_OUTPUT_REPAIR,
                    new StructuredOutputRepairInput("{malformed"),
                    outputContract);

            assertThat(request.outputContract()).isEqualTo(outputContract);
        }
        assertThatThrownBy(() -> request(
                        AiTaskType.STRUCTURED_OUTPUT_REPAIR,
                        new ExplanationInput("explain", "cord", ExplanationMode.STANDARD),
                        AiOutputContract.EXPLANATION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("StructuredOutputRepairInput");
    }

    @Test
    void rejectsMissingFieldsAndGroundingThatDisagreesWithEvidence() {
        assertThatThrownBy(() -> new AiTaskRequest<>(
                        AiTaskType.EXPLANATION,
                        " ",
                        learnerContext(),
                        new ExplanationInput("explain", "cord", ExplanationMode.STANDARD),
                        evidence(GroundingMode.STRICT_SOURCE),
                        GroundingMode.STRICT_SOURCE,
                        AiOutputContract.EXPLANATION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("promptVersion");

        assertThatThrownBy(() -> new AiTaskRequest<>(
                        AiTaskType.EXPLANATION,
                        "EXPLANATION_V1",
                        learnerContext(),
                        new ExplanationInput("explain", "cord", ExplanationMode.STANDARD),
                        evidence(GroundingMode.STRICT_SOURCE),
                        GroundingMode.GENERAL_KNOWLEDGE,
                        AiOutputContract.EXPLANATION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("groundingMode");
    }

    @Test
    void defensivelyCopiesMutableCollections() {
        ArrayList<String> misconceptions = new ArrayList<>(List.of("cord relationship"));
        LinkedHashMap<String, String> relevantEvidence = new LinkedHashMap<>(Map.of("retrieval", "WEAK"));
        LearnerContext learner = new LearnerContext(
                "CONCEPT_STRUGGLING", "FIRST_EXPOSURE", "MORE_SCAFFOLDING",
                relevantEvidence, misconceptions);
        ArrayList<String> keyPoints = new ArrayList<>(List.of("point"));
        ExplanationResult result = new ExplanationResult(
                "cord", "explanation", keyPoints, List.of(), List.of("chunk-1"), false, List.of());

        relevantEvidence.clear();
        misconceptions.clear();
        keyPoints.clear();

        assertThat(learner.relevantEvidence()).containsEntry("retrieval", "WEAK");
        assertThat(learner.relevantMisconceptions()).containsExactly("cord relationship");
        assertThat(result.keyPoints()).containsExactly("point");
        assertThatThrownBy(() -> learner.relevantEvidence().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.keyPoints().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void serializesEveryTaskInputAndResultContract() throws Exception {
        List<Object> contracts = List.of(
                new ExplanationInput("explain", "cord", ExplanationMode.STEP_BY_STEP),
                new QuestionGenerationInput(
                        "recall", "cord", ActivityType.MCQ, QuestionDifficulty.FOUNDATIONAL,
                        List.of("identify origin"), null),
                new ResponseEvaluationInput(
                        "Which nerve?", List.of("radial nerve"), "Radial nerve", "The radial nerve"),
                new ConceptConnectionInput("cord", "connect", List.of("cord-radial nerve")),
                new ContextualApplicationInput(
                        "cord", "apply", ApplicationLevel.MECHANISM_TO_FINDING),
                new StructuredOutputRepairInput("{malformed"),
                new ExplanationResult(
                        "cord", "explanation", List.of("point"), List.of(),
                        List.of("chunk-1"), false, List.of()),
                new QuestionGenerationResult(
                        ActivityType.MCQ, "cord", "recall", "Which nerve?",
                        List.of(new QuestionOption("A", "Radial nerve")), "A", "Radial nerve",
                        "It arises from the posterior cord.", QuestionDifficulty.FOUNDATIONAL,
                        List.of("chunk-1"), List.of()),
                new ResponseEvaluationResult(
                        Evaluation.CORRECT, List.of("radial nerve"), List.of(), List.of(),
                        "Correct.", EvaluationCertainty.SUFFICIENT, RecommendedAction.CONTINUE,
                        List.of("chunk-1"), List.of()),
                new ConceptConnectionResult(
                        "posterior cord", "radial nerve", "anatomy", "gives rise to",
                        "localizes injury", List.of("chunk-1"), List.of()),
                new ContextualApplicationResult(
                        "A patient has wrist drop.", "Which structure is involved?", "posterior cord",
                        List.of("connect deficit to nerve"), "Radial nerve pathway",
                        List.of("identify wrist extensors"), ApplicationDifficulty.FOUNDATIONAL_APPLIED,
                        List.of("chunk-1"), List.of()));

        for (Object contract : contracts) {
            assertThat(objectMapper.writeValueAsString(contract)).startsWith("{").endsWith("}");
        }
    }

    @Test
    void serializesRepresentativeRequestAndValidatedResult() throws Exception {
        AiTaskRequest<ExplanationInput> request = request(
                AiTaskType.EXPLANATION,
                new ExplanationInput("explain", "posterior cord", ExplanationMode.ANALOGY),
                AiOutputContract.EXPLANATION);
        ValidatedAiResult<ExplanationResult> result = new ValidatedAiResult<>(new ExplanationResult(
                "posterior cord", "A concise explanation", List.of("key point"), List.of(),
                List.of(), true, List.of("supplemental knowledge used")));

        assertThat(objectMapper.readTree(objectMapper.writeValueAsString(request)).get("taskType").asText())
                .isEqualTo("EXPLANATION");
        assertThat(objectMapper.readTree(objectMapper.writeValueAsString(result)).get("result").get("concept").asText())
                .isEqualTo("posterior cord");
    }

    @Test
    void canonicalContractsContainNoProviderSpecificTypes() {
        List<Class<?>> contracts = List.of(
                AiTaskRequest.class, ValidatedAiResult.class, LearnerContext.class,
                ExplanationInput.class, ExplanationResult.class,
                QuestionGenerationInput.class, QuestionGenerationResult.class,
                ResponseEvaluationInput.class, ResponseEvaluationResult.class,
                ConceptConnectionInput.class, ConceptConnectionResult.class,
                ContextualApplicationInput.class, ContextualApplicationResult.class,
                StructuredOutputRepairInput.class);

        assertThat(contracts.stream()
                        .flatMap(type -> List.of(type.getRecordComponents()).stream())
                        .map(RecordComponent::getGenericType)
                        .map(type -> type.getTypeName().toLowerCase())
                        .filter(type -> type.contains("gemini") || type.contains("ollama") || type.contains("spring.ai")))
                .isEmpty();
    }

    private static LearnerContext learnerContext() {
        return new LearnerContext(
                "CONCEPT_STRUGGLING", "FIRST_EXPOSURE", "MORE_SCAFFOLDING",
                Map.of("retrieval", "WEAK"), List.of("cord relationship"));
    }

    private static EvidencePackage evidence(GroundingMode groundingMode) {
        return new EvidencePackage(
                RetrievalQuality.FAILED,
                groundingMode,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new RetrievalDiagnostics(0, 0, List.of(), List.of(), Set.of(), Set.of(), RetrievalQuality.FAILED));
    }

    private static <C extends AiTaskContext> AiTaskRequest<C> request(
            AiTaskType type, C context, AiOutputContract outputContract) {
        return new AiTaskRequest<>(
                type,
                type.name() + "_V1",
                learnerContext(),
                context,
                evidence(GroundingMode.STRICT_SOURCE),
                GroundingMode.STRICT_SOURCE,
                outputContract);
    }
}
