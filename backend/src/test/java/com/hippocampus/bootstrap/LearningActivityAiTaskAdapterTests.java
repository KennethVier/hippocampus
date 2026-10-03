package com.hippocampus.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hippocampus.ai.application.AiExecutionOrchestrator;
import com.hippocampus.ai.application.prompt.PromptTokenBudget;
import com.hippocampus.ai.application.request.AiRequestPriority;
import com.hippocampus.ai.application.routing.ProviderId;
import com.hippocampus.ai.application.routing.ProviderRoutingCandidate;
import com.hippocampus.ai.application.routing.ProviderRoutingPreference;
import com.hippocampus.ai.application.validation.AiSchemaValidationException;
import com.hippocampus.ai.domain.AiOutputContract;
import com.hippocampus.ai.domain.AiTaskRequest;
import com.hippocampus.ai.domain.AiTaskType;
import com.hippocampus.ai.domain.ActivityType;
import com.hippocampus.ai.domain.ApplicationDifficulty;
import com.hippocampus.ai.domain.ApplicationLevel;
import com.hippocampus.ai.domain.ConceptConnectionInput;
import com.hippocampus.ai.domain.ConceptConnectionResult;
import com.hippocampus.ai.domain.ContextualApplicationInput;
import com.hippocampus.ai.domain.ContextualApplicationResult;
import com.hippocampus.ai.domain.ExplanationResult;
import com.hippocampus.ai.domain.QuestionDifficulty;
import com.hippocampus.ai.domain.QuestionGenerationInput;
import com.hippocampus.ai.domain.QuestionGenerationResult;
import com.hippocampus.ai.domain.ValidatedAiResult;
import com.hippocampus.ai.infrastructure.learning.AiTaskExecutionOptions;
import com.hippocampus.ai.infrastructure.learning.AiTaskExecutionPolicy;
import com.hippocampus.learning.domain.ApplicationActivityLevel;
import com.hippocampus.learning.domain.LearningActionConstraints;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningActivityIntent;
import com.hippocampus.learning.domain.LearningDifficulty;
import com.hippocampus.learning.domain.SourceRequirement;
import com.hippocampus.learning.domain.StudyMissionGroundingMode;
import com.hippocampus.learning.port.ActivityAiTaskPort;
import com.hippocampus.learning.port.ActivityEvidencePort;
import com.hippocampus.rag.domain.EvidencePackage;
import com.hippocampus.rag.domain.GroundingMode;

class LearningActivityAiTaskAdapterTests {

    private AiExecutionOrchestrator orchestrator;
    private LearningActivityAiTaskAdapter adapter;

    @BeforeEach
    void setUp() {
        orchestrator = mock(AiExecutionOrchestrator.class);
        AiTaskExecutionPolicy executionPolicy = mock(AiTaskExecutionPolicy.class);
        AiTaskExecutionOptions options = executionOptions();
        when(executionPolicy.optionsFor(AiTaskType.EXPLANATION)).thenReturn(options);
        when(executionPolicy.optionsFor(AiTaskType.QUESTION_GENERATION)).thenReturn(options);
        when(executionPolicy.optionsFor(AiTaskType.CONCEPT_CONNECTION)).thenReturn(options);
        when(executionPolicy.optionsFor(AiTaskType.CONTEXTUAL_APPLICATION)).thenReturn(options);
        adapter = new LearningActivityAiTaskAdapter(
                orchestrator, executionPolicy, new ObjectMapper());
    }

    @Test
    void rejectsSupplementalExplanationWhenLearningEngineDisallowsIt() {
        stubSupplementalExplanation();

        assertThatThrownBy(() -> adapter.execute(request(
                StudyMissionGroundingMode.STRICT_SOURCE, false)))
                .isInstanceOf(AiSchemaValidationException.class)
                .satisfies(failure -> {
                    AiSchemaValidationException validationFailure =
                            (AiSchemaValidationException) failure;
                    assertThat(validationFailure.outputContract())
                            .isEqualTo(AiOutputContract.EXPLANATION);
                    assertThat(validationFailure.reason())
                            .isEqualTo(AiSchemaValidationException.Reason.BUSINESS_RULE_VIOLATION);
                });
    }

    @Test
    void acceptsSupplementalExplanationWhenLearningEngineAllowsIt() {
        stubSupplementalExplanation();

        ActivityAiTaskPort.ValidatedContent content = adapter.execute(request(
                StudyMissionGroundingMode.SOURCE_FIRST, true));

        assertThat(content.groundingMode()).isEqualTo("SOURCE_FIRST");
        assertThat(content.classification()).isEqualTo("SUPPLEMENTAL_GENERATED");
        assertThat(content.validationStatus())
                .isEqualTo(ActivityAiTaskPort.ValidationStatus.VALIDATED);
    }

    @Test
    void executesConnectAsSourceGroundedReusableConceptConnection() throws Exception {
        ConceptConnectionResult connection = new ConceptConnectionResult(
                "Alveolar ventilation",
                "Arterial carbon dioxide",
                "inverse physiological relationship",
                "Increasing alveolar ventilation lowers arterial carbon dioxide.",
                "This relationship explains respiratory compensation.",
                List.of("source-1"),
                List.of());
        ValidatedAiResult<?> validated = new ValidatedAiResult<>(
                connection,
                new ValidatedAiResult.ExecutionMetadata(
                        "GEMINI", "test-model", "test-version", "CONCEPT_CONNECTION_V1", "1"));
        when(orchestrator.execute(any(), any(), any(), anyList(), any()))
                .thenReturn(CompletableFuture.completedFuture(validated));

        ActivityAiTaskPort.ValidatedContent content = adapter.execute(connectionRequest());

        ArgumentCaptor<AiTaskRequest<?>> requestCaptor = ArgumentCaptor.forClass(AiTaskRequest.class);
        verify(orchestrator).execute(
                requestCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(AiRequestPriority.INTERACTIVE_GENERATION),
                any(), anyList(), any());
        AiTaskRequest<?> aiRequest = requestCaptor.getValue();
        assertThat(aiRequest.taskType()).isEqualTo(AiTaskType.CONCEPT_CONNECTION);
        assertThat(aiRequest.promptVersion()).isEqualTo("CONCEPT_CONNECTION_V1");
        assertThat(aiRequest.outputContract()).isEqualTo(AiOutputContract.CONCEPT_CONNECTION);
        assertThat(aiRequest.groundingMode()).isEqualTo(GroundingMode.STRICT_SOURCE);
        assertThat(aiRequest.taskContext()).isEqualTo(new ConceptConnectionInput(
                "Alveolar ventilation", "Connect ventilation to gas exchange", List.of()));
        assertThat(content.artifactType()).isEqualTo("CONCEPT_CONNECTION");
        assertThat(content.taskType()).isEqualTo("CONCEPT_CONNECTION");
        assertThat(content.contentText()).isEqualTo(connection.relationship());
        assertThat(content.contentPayload()).isEqualTo(new ObjectMapper().writeValueAsString(connection));
        assertThat(content.groundingMode()).isEqualTo("STRICT_SOURCE");
        assertThat(content.classification()).isEqualTo("SOURCE_GROUNDED_GENERATED");
        assertThat(content.validationStatus()).isEqualTo(ActivityAiTaskPort.ValidationStatus.VALIDATED);
        assertThat(content.reusable()).isTrue();
    }

    @Test
    void executesUnderstandingCheckThroughShortAnswerQuestionGeneration() throws Exception {
        QuestionGenerationResult question = new QuestionGenerationResult(
                ActivityType.SHORT_ANSWER,
                "Cardiac output",
                "Explain cardiac output",
                "Why can cardiac output rise?",
                List.of(),
                null,
                "Heart rate or stroke volume rises.",
                "Cardiac output is heart rate multiplied by stroke volume.",
                QuestionDifficulty.FOUNDATIONAL,
                List.of("source-1"),
                List.of());
        ValidatedAiResult<?> validated = new ValidatedAiResult<>(
                question,
                new ValidatedAiResult.ExecutionMetadata(
                        "GEMINI", "test-model", "test-version", "QUESTION_GENERATION_V1", "1"));
        when(orchestrator.execute(any(), any(), any(), anyList(), any()))
                .thenReturn(CompletableFuture.completedFuture(validated));

        ActivityAiTaskPort.ValidatedContent content = adapter.execute(understandingCheckRequest());

        ArgumentCaptor<AiTaskRequest<?>> requestCaptor = ArgumentCaptor.forClass(AiTaskRequest.class);
        verify(orchestrator).execute(
                requestCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(AiRequestPriority.INTERACTIVE_GENERATION),
                any(), anyList(), any());
        AiTaskRequest<?> aiRequest = requestCaptor.getValue();
        assertThat(aiRequest.taskType()).isEqualTo(AiTaskType.QUESTION_GENERATION);
        assertThat(aiRequest.promptVersion()).isEqualTo("QUESTION_GENERATION_V1");
        assertThat(aiRequest.outputContract()).isEqualTo(AiOutputContract.QUESTION_GENERATION);
        assertThat(aiRequest.taskContext()).isEqualTo(new QuestionGenerationInput(
                "Explain cardiac output", "Cardiac output", ActivityType.SHORT_ANSWER,
                QuestionDifficulty.FOUNDATIONAL, List.of(), null));
        assertThat(aiRequest.learnerContext().learningState())
                .isEqualTo(LearningActionType.UNDERSTANDING_CHECK.name());
        assertThat(content.artifactType()).isEqualTo("QUESTION");
        assertThat(content.taskType()).isEqualTo(AiTaskType.QUESTION_GENERATION.name());
        assertThat(content.contentPayload()).contains(
                "\"question\":\"Why can cardiac output rise?\"",
                "\"expectedAnswer\":\"Heart rate or stroke volume rises.\"");
    }

    @Test
    void executesApplyAsValidatedReusableContextualApplication() throws Exception {
        ContextualApplicationResult application = new ContextualApplicationResult(
                "A learner links posterior cord injury to impaired wrist extension.",
                "What mechanism explains the wrist drop?",
                "Radial nerve",
                List.of("The radial nerve arises from the posterior cord", "It supplies wrist extensors"),
                "Posterior cord injury can impair the radial nerve and wrist extension.",
                List.of("Connect the nerve origin to its motor function"),
                ApplicationDifficulty.FOUNDATIONAL_APPLIED,
                List.of("source-1"),
                List.of("Educational scenario only"));
        ValidatedAiResult<?> validated = new ValidatedAiResult<>(
                application,
                new ValidatedAiResult.ExecutionMetadata(
                        "GEMINI", "test-model", "test-version", "CONTEXTUAL_APPLICATION_V1", "1"));
        when(orchestrator.execute(any(), any(), any(), anyList(), any()))
                .thenReturn(CompletableFuture.completedFuture(validated));

        ActivityAiTaskPort.ValidatedContent content = adapter.execute(applicationRequest());

        ArgumentCaptor<AiTaskRequest<?>> requestCaptor = ArgumentCaptor.forClass(AiTaskRequest.class);
        verify(orchestrator).execute(
                requestCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(AiRequestPriority.INTERACTIVE_GENERATION),
                any(), anyList(), any());
        AiTaskRequest<?> aiRequest = requestCaptor.getValue();
        assertThat(aiRequest.taskType()).isEqualTo(AiTaskType.CONTEXTUAL_APPLICATION);
        assertThat(aiRequest.promptVersion()).isEqualTo("CONTEXTUAL_APPLICATION_V1");
        assertThat(aiRequest.outputContract()).isEqualTo(AiOutputContract.CONTEXTUAL_APPLICATION);
        assertThat(aiRequest.taskContext()).isEqualTo(new ContextualApplicationInput(
                "Radial nerve", "Apply posterior cord anatomy", ApplicationLevel.DIRECT));
        assertThat(content.artifactType()).isEqualTo("CONTEXTUAL_APPLICATION");
        assertThat(content.taskType()).isEqualTo("CONTEXTUAL_APPLICATION");
        assertThat(content.contentText()).isEqualTo(application.scenario());
        assertThat(content.contentPayload()).isEqualTo(new ObjectMapper().writeValueAsString(application));
        assertThat(content.groundingMode()).isEqualTo("STRICT_SOURCE");
        assertThat(content.classification()).isEqualTo("SOURCE_GROUNDED_GENERATED");
        assertThat(content.validationStatus()).isEqualTo(ActivityAiTaskPort.ValidationStatus.VALIDATED);
        assertThat(content.reusable()).isTrue();
    }

    @Test
    void mapsEveryLearningOwnedApplicationLevelExactly() {
        assertThat(LearningActivityAiTaskAdapter.map(ApplicationActivityLevel.DIRECT))
                .isEqualTo(ApplicationLevel.DIRECT);
        assertThat(LearningActivityAiTaskAdapter.map(ApplicationActivityLevel.GUIDED))
                .isEqualTo(ApplicationLevel.GUIDED);
        assertThat(LearningActivityAiTaskAdapter.map(ApplicationActivityLevel.MECHANISM_TO_FINDING))
                .isEqualTo(ApplicationLevel.MECHANISM_TO_FINDING);
        assertThat(LearningActivityAiTaskAdapter.map(ApplicationActivityLevel.SHORT_CASE))
                .isEqualTo(ApplicationLevel.SHORT_CASE);
    }

    @Test
    void mapsLearningDifficultyToExpectedApplicationDifficultyExactly() {
        assertThat(LearningActivityAiTaskAdapter.mapApplicationDifficulty(LearningDifficulty.FOUNDATIONAL))
                .isEqualTo(ApplicationDifficulty.FOUNDATIONAL_APPLIED);
        assertThat(LearningActivityAiTaskAdapter.mapApplicationDifficulty(LearningDifficulty.INTERMEDIATE))
                .isEqualTo(ApplicationDifficulty.INTERMEDIATE_APPLIED);
        assertThat(LearningActivityAiTaskAdapter.mapApplicationDifficulty(LearningDifficulty.APPLIED))
                .isEqualTo(ApplicationDifficulty.INTERMEDIATE_APPLIED);
    }

    @Test
    void rejectsContextualApplicationWithDifficultyDifferentFromLearningEngineRequest() {
        ContextualApplicationResult application = new ContextualApplicationResult(
                "Scenario", "Question", "Radial nerve", List.of("Reasoning"),
                "Expected answer", List.of("Feedback"), ApplicationDifficulty.INTERMEDIATE_APPLIED,
                List.of(), List.of());
        ValidatedAiResult<?> validated = new ValidatedAiResult<>(
                application,
                new ValidatedAiResult.ExecutionMetadata(
                        "GEMINI", "test-model", null, "CONTEXTUAL_APPLICATION_V1", "1"));
        when(orchestrator.execute(any(), any(), any(), anyList(), any()))
                .thenReturn(CompletableFuture.completedFuture(validated));

        assertThatThrownBy(() -> adapter.execute(applicationRequest()))
                .isInstanceOf(AiSchemaValidationException.class)
                .satisfies(failure -> assertThat(((AiSchemaValidationException) failure).reason())
                        .isEqualTo(AiSchemaValidationException.Reason.BUSINESS_RULE_VIOLATION));
    }

    private void stubSupplementalExplanation() {
        ExplanationResult explanation = new ExplanationResult(
                "Cardiac output", "Supplemental explanation", List.of(), List.of(),
                List.of(), true, List.of());
        ValidatedAiResult<?> validated = new ValidatedAiResult<>(
                explanation,
                new ValidatedAiResult.ExecutionMetadata(
                        "GEMINI", "test-model", null, "EXPLANATION_V1", "1"));
        when(orchestrator.execute(any(), any(), any(), anyList(), any()))
                .thenReturn(CompletableFuture.completedFuture(validated));
    }

    private static ActivityAiTaskPort.Request request(
            StudyMissionGroundingMode groundingMode,
            boolean supplementalKnowledgeAllowed) {
        LearningActionConstraints constraints = new LearningActionConstraints(
                SourceRequirement.REQUIRED, false, supplementalKnowledgeAllowed,
                null, null, LearningActivityIntent.STANDARD);
        EvidencePackage evidencePackage = mock(EvidencePackage.class);
        when(evidencePackage.groundingMode()).thenReturn(GroundingMode.valueOf(groundingMode.name()));
        ActivityEvidencePort.Evidence evidence = new ActivityEvidencePort.Evidence(
                Set.of(), new RagActivityEvidencePayload(evidencePackage));
        return new ActivityAiTaskPort.Request(
                "Explain cardiac output", "Cardiac output", LearningActionType.UNDERSTAND,
                LearningDifficulty.FOUNDATIONAL, groundingMode, evidence, List.of(), constraints);
    }

    private static ActivityAiTaskPort.Request connectionRequest() {
        LearningActionConstraints constraints = new LearningActionConstraints(
                SourceRequirement.REQUIRED, false, false,
                null, null, LearningActivityIntent.STANDARD);
        EvidencePackage evidencePackage = mock(EvidencePackage.class);
        when(evidencePackage.groundingMode()).thenReturn(GroundingMode.STRICT_SOURCE);
        ActivityEvidencePort.Evidence evidence = new ActivityEvidencePort.Evidence(
                Set.of(), new RagActivityEvidencePayload(evidencePackage));
        return new ActivityAiTaskPort.Request(
                "Connect ventilation to gas exchange", "Alveolar ventilation", LearningActionType.CONNECT,
                LearningDifficulty.FOUNDATIONAL, StudyMissionGroundingMode.STRICT_SOURCE,
                evidence, List.of(), constraints);
    }

    private static ActivityAiTaskPort.Request understandingCheckRequest() {
        LearningActionConstraints constraints = new LearningActionConstraints(
                SourceRequirement.REQUIRED, false, false,
                null, null, LearningActivityIntent.STANDARD)
                .withRetrievalActivityType(com.hippocampus.learning.domain.RetrievalActivityType.SHORT_ANSWER);
        EvidencePackage evidencePackage = mock(EvidencePackage.class);
        when(evidencePackage.groundingMode()).thenReturn(GroundingMode.STRICT_SOURCE);
        ActivityEvidencePort.Evidence evidence = new ActivityEvidencePort.Evidence(
                Set.of(), new RagActivityEvidencePayload(evidencePackage));
        return new ActivityAiTaskPort.Request(
                "Explain cardiac output", "Cardiac output", LearningActionType.UNDERSTANDING_CHECK,
                LearningDifficulty.FOUNDATIONAL, StudyMissionGroundingMode.STRICT_SOURCE,
                evidence, List.of(), constraints);
    }

    private static ActivityAiTaskPort.Request applicationRequest() {
        LearningActionConstraints constraints = new LearningActionConstraints(
                SourceRequirement.REQUIRED, false, false,
                null, null, LearningActivityIntent.STANDARD)
                .withApplicationActivityLevel(ApplicationActivityLevel.DIRECT);
        EvidencePackage evidencePackage = mock(EvidencePackage.class);
        when(evidencePackage.groundingMode()).thenReturn(GroundingMode.STRICT_SOURCE);
        ActivityEvidencePort.Evidence evidence = new ActivityEvidencePort.Evidence(
                Set.of(), new RagActivityEvidencePayload(evidencePackage));
        return new ActivityAiTaskPort.Request(
                "Apply posterior cord anatomy", "Radial nerve", LearningActionType.APPLY,
                LearningDifficulty.FOUNDATIONAL, StudyMissionGroundingMode.STRICT_SOURCE,
                evidence, List.of(), constraints);
    }

    private static AiTaskExecutionOptions executionOptions() {
        ProviderRoutingCandidate candidate = new ProviderRoutingCandidate(
                ProviderId.GEMINI, "test-model",
                Set.of(AiTaskType.EXPLANATION, AiTaskType.QUESTION_GENERATION, AiTaskType.CONCEPT_CONNECTION,
                        AiTaskType.CONTEXTUAL_APPLICATION),
                Set.of(AiTaskType.EXPLANATION, AiTaskType.QUESTION_GENERATION, AiTaskType.CONCEPT_CONNECTION,
                        AiTaskType.CONTEXTUAL_APPLICATION),
                true, true, true, 0, 0, 0);
        return new AiTaskExecutionOptions(
                new PromptTokenBudget(1_000, 100), List.of(candidate),
                ProviderRoutingPreference.COST_THEN_LATENCY);
    }
}
