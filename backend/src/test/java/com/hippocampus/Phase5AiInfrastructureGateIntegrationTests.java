package com.hippocampus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.hippocampus.ai.application.AiExecutionOrchestrator;
import com.hippocampus.ai.application.diagnostics.AiDiagnosticsPersistence;
import com.hippocampus.ai.application.prompt.PromptContext;
import com.hippocampus.ai.application.prompt.PromptContextBuilder;
import com.hippocampus.ai.application.prompt.PromptId;
import com.hippocampus.ai.application.prompt.PromptTemplateRegistry;
import com.hippocampus.ai.application.prompt.PromptTokenBudget;
import com.hippocampus.ai.application.provider.AiProviderAdapter;
import com.hippocampus.ai.application.provider.ProviderEventStream;
import com.hippocampus.ai.application.provider.ProviderExecutionException;
import com.hippocampus.ai.application.provider.ProviderExecutionRequest;
import com.hippocampus.ai.application.provider.ProviderExecutionResult;
import com.hippocampus.ai.application.provider.ProviderFailureType;
import com.hippocampus.ai.application.provider.ProviderUsage;
import com.hippocampus.ai.application.request.AiRequestManager;
import com.hippocampus.ai.application.request.AiRequestManagerPolicy;
import com.hippocampus.ai.application.request.AiRequestPriority;
import com.hippocampus.ai.application.request.AiRequestTelemetry;
import com.hippocampus.ai.application.routing.ProviderId;
import com.hippocampus.ai.application.routing.ProviderRouter;
import com.hippocampus.ai.application.routing.ProviderRoutingCandidate;
import com.hippocampus.ai.application.routing.ProviderRoutingPreference;
import com.hippocampus.ai.application.validation.AiGroundingValidationException;
import com.hippocampus.ai.application.validation.AiOutputValidator;
import com.hippocampus.ai.application.validation.AiSchemaValidationException;
import com.hippocampus.ai.application.validation.AiSourceReferenceValidator;
import com.hippocampus.ai.domain.AiOutputContract;
import com.hippocampus.ai.domain.AiTaskRequest;
import com.hippocampus.ai.domain.AiTaskType;
import com.hippocampus.ai.domain.ExplanationInput;
import com.hippocampus.ai.domain.ExplanationMode;
import com.hippocampus.ai.domain.ExplanationResult;
import com.hippocampus.ai.domain.LearnerContext;
import com.hippocampus.ai.domain.ResponseEvaluationInput;
import com.hippocampus.ai.domain.ValidatedAiResult;
import com.hippocampus.ai.infrastructure.validation.JacksonAiStructuredOutputDecoder;
import com.hippocampus.identity.domain.AuthenticatedUser;
import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.materials.domain.ChunkSourceTarget;
import com.hippocampus.materials.domain.SourceReference;
import com.hippocampus.materials.domain.SourceReferenceTarget;
import com.hippocampus.materials.port.SourceReferenceRepository;
import com.hippocampus.materials.port.SourceReferenceSeed;
import com.hippocampus.rag.domain.EvidenceChunk;
import com.hippocampus.rag.domain.EvidencePackage;
import com.hippocampus.rag.domain.EvidenceReferenceKind;
import com.hippocampus.rag.domain.EvidenceSourceReference;
import com.hippocampus.rag.domain.GroundingMode;
import com.hippocampus.rag.domain.RetrievalDiagnostics;
import com.hippocampus.rag.domain.RetrievalQuality;
import com.hippocampus.testing.PostgresIntegrationTestSupport;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Phase5AiInfrastructureGateIntegrationTests extends PostgresIntegrationTestSupport {

    private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000005");
    private static final UUID MATERIAL_ID = UUID.fromString("20000000-0000-0000-0000-000000000005");
    private static final UUID VERSION_ID = UUID.fromString("30000000-0000-0000-0000-000000000005");
    private static final UUID NODE_ID = UUID.fromString("40000000-0000-0000-0000-000000000005");
    private static final UUID INCLUDED_CHUNK_ID = UUID.fromString("50000000-0000-0000-0000-000000000005");
    private static final UUID TRIMMED_CHUNK_ID = UUID.fromString("50000000-0000-0000-0000-000000000006");
    private static final UUID FABRICATED_CHUNK_ID = UUID.fromString("50000000-0000-0000-0000-000000000099");
    private static final PromptTokenBudget LARGE_BUDGET = new PromptTokenBudget(200_000, 1_000);

    private static final String PRIVATE_SOURCE_TEXT = "PRIVATE_SOURCE_TEXT_SENTINEL";
    private static final String STUDENT_RESPONSE = "PRIVATE_STUDENT_RESPONSE_SENTINEL";
    private static final String MALFORMED_OUTPUT = "PRIVATE_MALFORMED_OUTPUT_SENTINEL";
    private static final String PROVIDER_ERROR_BODY = "PRIVATE_PROVIDER_ERROR_BODY_SENTINEL";
    private static final String PROVIDER_CREDENTIAL = "PRIVATE_PROVIDER_CREDENTIAL_SENTINEL";
    private static final String SESSION_COOKIE = "PRIVATE_SESSION_COOKIE_SENTINEL";
    private static final String EMAIL = "phase5-private-email@example.test";
    private static final String DISPLAY_NAME = "PRIVATE_DISPLAY_NAME_SENTINEL";
    private static final String MATERIAL_FILENAME = "private-material-filename.pdf";

    private final PromptTemplateRegistry promptRegistry = new PromptTemplateRegistry();
    private final PromptContextBuilder promptBuilder = new PromptContextBuilder(promptRegistry, String::length);
    private final AiOutputValidator outputValidator =
            new AiOutputValidator(new JacksonAiStructuredOutputDecoder());
    private final StubSourceReferenceRepository sourceReferences = new StubSourceReferenceRepository();

    private ConfigurableApplicationContext application;
    private JdbcClient jdbc;
    private AiDiagnosticsPersistence diagnostics;

    @BeforeAll
    void startDatabaseBackedApplication() throws SQLException {
        resetPostgresSchema();
        application = startApplicationWithFlyway();
        jdbc = application.getBean(JdbcClient.class);
        diagnostics = application.getBean(AiDiagnosticsPersistence.class);
        insertUser();
    }

    @AfterAll
    void stopDatabaseBackedApplication() {
        if (application != null) {
            application.close();
        }
    }

    @Test
    void completePhase5AiInfrastructureExecutesThroughProviderIndependentContracts() {
        EvidenceChunk included = chunk(1, INCLUDED_CHUNK_ID, PRIVATE_SOURCE_TEXT + " " + MATERIAL_FILENAME);
        EvidenceChunk trimmed = chunk(2, TRIMMED_CHUNK_ID, "Distinct evidence excluded by the token budget.");
        sourceReferences.authorize(included);
        sourceReferences.authorize(trimmed);
        AiTaskRequest<ExplanationInput> canonicalRequest = explanationRequest(List.of(included));

        ValidatedAiResult<?> geminiResult;
        PromptContext geminiPrompt;
        try (GateHarness harness = harness(
                "gemini-primary-gate",
                request -> successfulResult(request, validExplanation(INCLUDED_CHUNK_ID)),
                "ollama-unused-gate",
                request -> successfulResult(request, validExplanation(INCLUDED_CHUNK_ID)))) {
            geminiResult = join(harness.execute(canonicalRequest, LARGE_BUDGET, harness.geminiOnly(canonicalRequest)));

            assertThat(canonicalRequest.taskType()).isEqualTo(AiTaskType.EXPLANATION);
            assertThat(canonicalRequest.outputContract()).isEqualTo(AiOutputContract.EXPLANATION);
            assertThat(geminiResult.result()).isInstanceOf(ExplanationResult.class);
            assertThat(harness.gemini.requests).hasSize(1);
            assertThat(harness.ollama.requests).isEmpty();
            ProviderExecutionRequest received = harness.gemini.requests.getFirst();
            geminiPrompt = received.promptContext();
            assertCanonicalProviderRequest(received, canonicalRequest, ProviderId.GEMINI, "gemini-primary-gate");
            assertThat(geminiPrompt.systemPromptId()).isEqualTo(PromptId.HIPPOCAMPUS_SYSTEM_V1);
            assertThat(geminiPrompt.taskPromptId()).isEqualTo(PromptId.EXPLANATION_V1);
            assertThat(geminiPrompt.includedSources())
                    .extracting(PromptContext.IncludedSource::chunkId)
                    .containsExactly(INCLUDED_CHUNK_ID);
        }

        ValidatedAiResult<?> ollamaResult;
        try (GateHarness harness = harness(
                "gemini-unused-gate",
                request -> successfulResult(request, validExplanation(INCLUDED_CHUNK_ID)),
                "ollama-primary-gate",
                request -> successfulResult(request, validExplanation(INCLUDED_CHUNK_ID)))) {
            ollamaResult = join(harness.execute(canonicalRequest, LARGE_BUDGET, harness.ollamaOnly(canonicalRequest)));

            assertThat(harness.gemini.requests).isEmpty();
            assertThat(harness.ollama.requests).hasSize(1);
            assertCanonicalProviderRequest(
                    harness.ollama.requests.getFirst(), canonicalRequest,
                    ProviderId.OLLAMA_CLOUD, "ollama-primary-gate");
        }
        assertThat(ollamaResult).isEqualTo(geminiResult);
        assertThat(ollamaResult.getClass()).isEqualTo(ValidatedAiResult.class);

        verifyFallbackRetainsCanonicalBoundary(canonicalRequest);
        verifyOneBoundedRepair(canonicalRequest);
        verifyGroundingFailureIsTerminal(canonicalRequest);
        verifyTokenTrimmedSourcesFailForPrimaryAndFallback(included, trimmed);
        verifyFiniteFallbackRepairAndTerminalSchemaFailure(canonicalRequest);
        verifyStudentAndProviderFailurePrivacy();
        verifyPersistedDiagnosticsAndPrivacy(geminiPrompt);
    }

    private void verifyFallbackRetainsCanonicalBoundary(AiTaskRequest<ExplanationInput> request) {
        try (GateHarness harness = harness(
                "gemini-fallback-primary",
                ignored -> {
                    assertNoProviderTransaction();
                    throw new ProviderExecutionException(
                            ProviderId.GEMINI, ProviderFailureType.PROVIDER_UNAVAILABLE);
                },
                "ollama-fallback-target",
                providerRequest -> successfulResult(providerRequest, validExplanation(INCLUDED_CHUNK_ID)))) {
            CountingCandidateList candidates = harness.geminiThenOllama(request);
            ValidatedAiResult<?> result = join(harness.execute(request, LARGE_BUDGET, candidates));

            assertThat(result.result()).isInstanceOf(ExplanationResult.class);
            assertThat(candidates.streamCalls()).isEqualTo(2);
            assertThat(harness.gemini.requests).hasSize(1);
            assertThat(harness.ollama.requests).hasSize(1);
            ProviderExecutionRequest primary = harness.gemini.requests.getFirst();
            ProviderExecutionRequest fallback = harness.ollama.requests.getFirst();
            assertThat(fallback.promptContext()).isSameAs(primary.promptContext());
            assertThat(fallback.promptContext().includedSources()).isEqualTo(primary.promptContext().includedSources());
            assertThat(fallback.taskType()).isEqualTo(primary.taskType());
            assertThat(fallback.outputContract()).isSameAs(primary.outputContract());
            assertThat(fallback.target().providerId()).isEqualTo(ProviderId.OLLAMA_CLOUD);
            assertThat(request.groundingMode()).isEqualTo(GroundingMode.STRICT_SOURCE);
            assertDiagnosticSequence(
                    "gemini-fallback-primary", "ollama-fallback-target",
                    List.of("FAILED", "SUCCESS"),
                    Arrays.asList("PROVIDER_UNAVAILABLE", null));
        }
    }

    private void verifyOneBoundedRepair(AiTaskRequest<ExplanationInput> request) {
        AtomicInteger generation = new AtomicInteger();
        try (GateHarness harness = harness(
                "gemini-repair-gate",
                providerRequest -> {
                    assertNoProviderTransaction();
                    if (generation.getAndIncrement() == 0) {
                        return successfulResult(providerRequest, MALFORMED_OUTPUT);
                    }
                    return successfulResult(providerRequest, validExplanation(INCLUDED_CHUNK_ID));
                },
                "ollama-repair-unused",
                providerRequest -> successfulResult(providerRequest, validExplanation(INCLUDED_CHUNK_ID)))) {
            ValidatedAiResult<?> result = join(harness.execute(
                    request, LARGE_BUDGET, harness.geminiThenOllama(request)));

            assertThat(result.result()).isInstanceOf(ExplanationResult.class);
            assertThat(generation).hasValue(2);
            assertThat(harness.gemini.requests).hasSize(2);
            assertThat(harness.ollama.requests).isEmpty();
            ProviderExecutionRequest initial = harness.gemini.requests.get(0);
            ProviderExecutionRequest repair = harness.gemini.requests.get(1);
            assertThat(repair.taskType()).isEqualTo(AiTaskType.STRUCTURED_OUTPUT_REPAIR);
            assertThat(repair.target()).isEqualTo(initial.target());
            assertThat(repair.outputContract()).isSameAs(initial.outputContract());
            assertThat(repair.promptContext().taskPromptId()).isEqualTo(PromptId.STRUCTURED_OUTPUT_REPAIR_V1);
            assertThat(initial.promptContext().includedSources())
                    .extracting(PromptContext.IncludedSource::chunkId)
                    .containsExactly(INCLUDED_CHUNK_ID);
            assertDiagnosticSequence(
                    "gemini-repair-gate", "gemini-repair-gate",
                    List.of("FAILED", "SUCCESS"),
                    Arrays.asList("AI_SCHEMA_FAILURE", null));
        }
    }

    private void verifyGroundingFailureIsTerminal(AiTaskRequest<ExplanationInput> request) {
        AtomicInteger providerCalls = new AtomicInteger();
        ProviderExecutionResult structurallyValid = new ProviderExecutionResult(
                ProviderId.GEMINI,
                "gemini-grounding-gate",
                validExplanation(FABRICATED_CHUNK_ID),
                ProviderUsage.of(41, 21, 62),
                Duration.ofMillis(11));
        assertThat(outputValidator.validate(structurallyValid, request.outputContract()).result())
                .isInstanceOf(ExplanationResult.class);

        try (GateHarness harness = harness(
                "gemini-grounding-gate",
                ignored -> {
                    assertNoProviderTransaction();
                    providerCalls.incrementAndGet();
                    return structurallyValid;
                },
                "ollama-grounding-unused",
                providerRequest -> successfulResult(providerRequest, validExplanation(INCLUDED_CHUNK_ID)))) {
            assertGroundingFailure(harness.execute(
                    request, LARGE_BUDGET, harness.geminiThenOllama(request)));

            assertThat(providerCalls).hasValue(1);
            assertThat(harness.gemini.requests).hasSize(1);
            assertThat(harness.ollama.requests).isEmpty();
            assertSingleFailure("gemini-grounding-gate", "AI_GROUNDING_FAILURE");
        }
    }

    private void verifyTokenTrimmedSourcesFailForPrimaryAndFallback(
            EvidenceChunk included, EvidenceChunk trimmed) {
        AiTaskRequest<ExplanationInput> oneSourceRequest = explanationRequest(List.of(included));
        int oneSourceTokens = promptBuilder.build(oneSourceRequest, LARGE_BUDGET).inputTokenCount();
        PromptTokenBudget trimmedBudget = new PromptTokenBudget(oneSourceTokens + 1_000, 1_000);
        AiTaskRequest<ExplanationInput> twoSourceRequest = explanationRequest(List.of(included, trimmed));

        try (GateHarness primaryHarness = harness(
                "gemini-trim-primary",
                providerRequest -> successfulResult(providerRequest, validExplanation(TRIMMED_CHUNK_ID)),
                "ollama-trim-unused",
                providerRequest -> successfulResult(providerRequest, validExplanation(TRIMMED_CHUNK_ID)))) {
            assertGroundingFailure(primaryHarness.execute(
                    twoSourceRequest, trimmedBudget, primaryHarness.geminiThenOllama(twoSourceRequest)));
            assertTrimmedBoundary(primaryHarness.gemini.requests.getFirst().promptContext());
            assertThat(primaryHarness.gemini.requests).hasSize(1);
            assertThat(primaryHarness.ollama.requests).isEmpty();
        }

        try (GateHarness fallbackHarness = harness(
                "gemini-trim-fallback-primary",
                ignored -> {
                    assertNoProviderTransaction();
                    throw new ProviderExecutionException(
                            ProviderId.GEMINI, ProviderFailureType.PROVIDER_UNAVAILABLE);
                },
                "ollama-trim-fallback",
                providerRequest -> successfulResult(providerRequest, validExplanation(TRIMMED_CHUNK_ID)))) {
            assertGroundingFailure(fallbackHarness.execute(
                    twoSourceRequest, trimmedBudget, fallbackHarness.geminiThenOllama(twoSourceRequest)));

            assertThat(fallbackHarness.gemini.requests).hasSize(1);
            assertThat(fallbackHarness.ollama.requests).hasSize(1);
            PromptContext primaryPrompt = fallbackHarness.gemini.requests.getFirst().promptContext();
            PromptContext fallbackPrompt = fallbackHarness.ollama.requests.getFirst().promptContext();
            assertThat(fallbackPrompt).isSameAs(primaryPrompt);
            assertTrimmedBoundary(fallbackPrompt);
        }
    }

    private void verifyFiniteFallbackRepairAndTerminalSchemaFailure(
            AiTaskRequest<ExplanationInput> request) {
        AtomicInteger fallbackCalls = new AtomicInteger();
        try (GateHarness harness = harness(
                "gemini-finite-primary",
                ignored -> {
                    assertNoProviderTransaction();
                    throw new ProviderExecutionException(
                            ProviderId.GEMINI, ProviderFailureType.PROVIDER_UNAVAILABLE);
                },
                "ollama-finite-fallback",
                providerRequest -> {
                    assertNoProviderTransaction();
                    return fallbackCalls.getAndIncrement() == 0
                            ? successfulResult(providerRequest, "not-json-on-fallback")
                            : successfulResult(providerRequest, validExplanation(INCLUDED_CHUNK_ID));
                })) {
            join(harness.execute(request, LARGE_BUDGET, harness.geminiThenOllama(request)));

            assertThat(harness.gemini.requests).hasSize(1);
            assertThat(harness.ollama.requests).hasSize(2);
            assertThat(fallbackCalls).hasValue(2);
            assertThat(harness.ollama.requests.get(1).taskType())
                    .isEqualTo(AiTaskType.STRUCTURED_OUTPUT_REPAIR);
            assertThat(harness.ollama.requests.get(1).target())
                    .isEqualTo(harness.ollama.requests.get(0).target());
        }

        AtomicInteger primaryCalls = new AtomicInteger();
        try (GateHarness harness = harness(
                "gemini-terminal-schema",
                providerRequest -> {
                    assertNoProviderTransaction();
                    primaryCalls.incrementAndGet();
                    return successfulResult(providerRequest, "still-not-json");
                },
                "ollama-schema-bypass-forbidden",
                providerRequest -> successfulResult(providerRequest, validExplanation(INCLUDED_CHUNK_ID)))) {
            assertThatThrownBy(() -> join(harness.execute(
                    request, LARGE_BUDGET, harness.geminiThenOllama(request))))
                    .isInstanceOf(AiSchemaValidationException.class);

            assertThat(primaryCalls).hasValue(2);
            assertThat(harness.gemini.requests).hasSize(2);
            assertThat(harness.ollama.requests).isEmpty();
        }
    }

    private void verifyStudentAndProviderFailurePrivacy() {
        AiTaskRequest<ResponseEvaluationInput> evaluationRequest = new AiTaskRequest<>(
                AiTaskType.RESPONSE_EVALUATION,
                PromptId.RESPONSE_EVALUATION_V1.name(),
                new LearnerContext("LEARNING", "FIRST", "STEADY", Map.of(), List.of()),
                new ResponseEvaluationInput(
                        "Which structure delays conduction?",
                        List.of("AV node"),
                        "The AV node",
                        STUDENT_RESPONSE + " " + SESSION_COOKIE + " " + PROVIDER_CREDENTIAL),
                evidence(List.of()),
                GroundingMode.GENERAL_KNOWLEDGE,
                AiOutputContract.RESPONSE_EVALUATION);
        try (GateHarness harness = harness(
                "gemini-private-student-response",
                providerRequest -> successfulResult(providerRequest, validResponseEvaluation()),
                "ollama-private-unused",
                providerRequest -> successfulResult(providerRequest, validResponseEvaluation()))) {
            join(harness.execute(evaluationRequest, LARGE_BUDGET, harness.geminiOnly(evaluationRequest)));
            assertThat(harness.gemini.requests.getFirst().promptContext().taskPrompt())
                    .contains(STUDENT_RESPONSE, SESSION_COOKIE, PROVIDER_CREDENTIAL);
        }

        try (GateHarness harness = harness(
                "gemini-private-error",
                ignored -> {
                    assertNoProviderTransaction();
                    throw new IllegalStateException(PROVIDER_ERROR_BODY);
                },
                "ollama-private-error-unused",
                providerRequest -> successfulResult(providerRequest, validResponseEvaluation()))) {
            assertThatThrownBy(() -> join(harness.execute(
                    evaluationRequest, LARGE_BUDGET, harness.geminiOnly(evaluationRequest))))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(PROVIDER_ERROR_BODY);
        }
    }

    private void verifyPersistedDiagnosticsAndPrivacy(PromptContext generatedPrompt) {
        assertThat(jdbc.sql("SELECT count(*) FROM ai_request_records").query(Long.class).single())
                .isGreaterThanOrEqualTo(16L);
        assertThat(jdbc.sql("SELECT count(*) FROM provider_usage_records").query(Long.class).single())
                .isEqualTo(jdbc.sql("SELECT count(*) FROM ai_request_records").query(Long.class).single());

        DiagnosticRow gemini = diagnostic("gemini-primary-gate");
        assertThat(gemini).isEqualTo(new DiagnosticRow(
                USER_ID,
                "EXPLANATION",
                "EXPLANATION_V1",
                "1",
                "GEMINI",
                "gemini-primary-gate",
                "SUCCESS",
                "STRICT_SOURCE",
                41,
                21,
                0,
                null));
        DiagnosticRow ollama = diagnostic("ollama-primary-gate");
        assertThat(ollama.provider()).isEqualTo("OLLAMA_CLOUD");
        assertThat(ollama.taskType()).isEqualTo(gemini.taskType());
        assertThat(ollama.promptId()).isEqualTo(gemini.promptId());
        assertThat(ollama.promptVersion()).isEqualTo(gemini.promptVersion());
        assertThat(ollama.groundingMode()).isEqualTo(gemini.groundingMode());
        assertThat(ollama.userId()).isEqualTo(gemini.userId());

        List<String> requestColumns = jdbc.sql("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'ai_request_records'
                ORDER BY ordinal_position
                """).query(String.class).list();
        assertThat(requestColumns).containsExactly(
                "id", "user_id", "task_type", "prompt_id", "prompt_version", "provider", "model",
                "status", "grounding_mode", "input_token_count", "output_token_count", "latency_ms",
                "retry_count", "error_code", "created_at");
        List<String> usageColumns = jdbc.sql("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'provider_usage_records'
                ORDER BY ordinal_position
                """).query(String.class).list();
        assertThat(usageColumns).containsExactly(
                "id", "provider", "model", "user_id", "task_type", "request_count",
                "input_tokens", "output_tokens", "estimated_cost", "occurred_at");

        String persistedDiagnostics = String.join("\n", jdbc.sql("""
                SELECT to_jsonb(diagnostic_row)::text
                FROM (
                    SELECT * FROM ai_request_records
                    UNION ALL
                    SELECT id, user_id, task_type, NULL AS prompt_id, NULL AS prompt_version,
                           provider, model, NULL AS status, NULL AS grounding_mode,
                           input_tokens::int AS input_token_count,
                           output_tokens::int AS output_token_count,
                           NULL AS latency_ms, request_count AS retry_count,
                           estimated_cost::text AS error_code, occurred_at AS created_at
                    FROM provider_usage_records
                ) diagnostic_row
                """).query(String.class).list());
        assertThat(persistedDiagnostics)
                .doesNotContain(
                        generatedPrompt.systemPrompt(),
                        generatedPrompt.taskPrompt(),
                        PRIVATE_SOURCE_TEXT,
                        STUDENT_RESPONSE,
                        MALFORMED_OUTPUT,
                        PROVIDER_ERROR_BODY,
                        PROVIDER_CREDENTIAL,
                        SESSION_COOKIE,
                        EMAIL,
                        DISPLAY_NAME,
                        MATERIAL_FILENAME);
    }

    private GateHarness harness(
            String geminiModel,
            Function<ProviderExecutionRequest, ProviderExecutionResult> geminiBehavior,
            String ollamaModel,
            Function<ProviderExecutionRequest, ProviderExecutionResult> ollamaBehavior) {
        CurrentUser currentUser = () -> new AuthenticatedUser(USER_ID);
        return new GateHarness(
                geminiModel,
                geminiBehavior,
                ollamaModel,
                ollamaBehavior,
                new AiSourceReferenceValidator(currentUser, sourceReferences));
    }

    private static void assertCanonicalProviderRequest(
            ProviderExecutionRequest providerRequest,
            AiTaskRequest<?> taskRequest,
            ProviderId providerId,
            String modelId) {
        assertThat(providerRequest.taskType()).isEqualTo(taskRequest.taskType());
        assertThat(providerRequest.outputContract()).isSameAs(taskRequest.outputContract());
        assertThat(providerRequest.target().providerId()).isEqualTo(providerId);
        assertThat(providerRequest.target().modelId()).isEqualTo(modelId);
        assertNoProviderTransaction();
    }

    private static void assertTrimmedBoundary(PromptContext prompt) {
        assertThat(prompt.includedSources())
                .extracting(PromptContext.IncludedSource::chunkId)
                .containsExactly(INCLUDED_CHUNK_ID)
                .doesNotContain(TRIMMED_CHUNK_ID);
    }

    private void assertDiagnosticSequence(
            String firstModel,
            String secondModel,
            List<String> statuses,
            List<String> errorCodes) {
        List<StatusRow> rows = jdbc.sql("""
                SELECT status, error_code
                FROM ai_request_records
                WHERE model IN (:firstModel, :secondModel)
                ORDER BY created_at, id
                """)
                .param("firstModel", firstModel)
                .param("secondModel", secondModel)
                .query((result, rowNumber) -> new StatusRow(
                        result.getString("status"), result.getString("error_code")))
                .list();
        assertThat(rows).extracting(StatusRow::status).containsExactlyInAnyOrderElementsOf(statuses);
        assertThat(rows).extracting(StatusRow::errorCode).containsExactlyInAnyOrderElementsOf(errorCodes);
    }

    private void assertSingleFailure(String model, String errorCode) {
        StatusRow row = jdbc.sql("""
                SELECT status, error_code
                FROM ai_request_records
                WHERE model = :model
                """)
                .param("model", model)
                .query((result, rowNumber) -> new StatusRow(
                        result.getString("status"), result.getString("error_code")))
                .single();
        assertThat(row).isEqualTo(new StatusRow("FAILED", errorCode));
    }

    private DiagnosticRow diagnostic(String model) {
        return jdbc.sql("""
                SELECT user_id, task_type, prompt_id, prompt_version, provider, model,
                       status, grounding_mode, input_token_count, output_token_count,
                       retry_count, error_code
                FROM ai_request_records
                WHERE model = :model
                """)
                .param("model", model)
                .query((result, rowNumber) -> new DiagnosticRow(
                        result.getObject("user_id", UUID.class),
                        result.getString("task_type"),
                        result.getString("prompt_id"),
                        result.getString("prompt_version"),
                        result.getString("provider"),
                        result.getString("model"),
                        result.getString("status"),
                        result.getString("grounding_mode"),
                        result.getObject("input_token_count", Integer.class),
                        result.getObject("output_token_count", Integer.class),
                        result.getInt("retry_count"),
                        result.getString("error_code")))
                .single();
    }

    private void insertUser() {
        jdbc.sql("""
                INSERT INTO users (id, email, display_name, status, created_at, updated_at)
                VALUES (:id, :email, :displayName, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """)
                .param("id", USER_ID)
                .param("email", EMAIL)
                .param("displayName", DISPLAY_NAME)
                .update();
    }

    private static void assertGroundingFailure(CompletableFuture<ValidatedAiResult<?>> result) {
        assertThatThrownBy(() -> join(result))
                .isInstanceOfSatisfying(AiGroundingValidationException.class,
                        failure -> assertThat(failure.errorCode().value())
                                .isEqualTo("AI_GROUNDING_FAILURE"));
    }

    private static void assertNoProviderTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    private static ValidatedAiResult<?> join(CompletableFuture<ValidatedAiResult<?>> result) {
        try {
            return result.join();
        } catch (java.util.concurrent.CompletionException failure) {
            if (failure.getCause() instanceof RuntimeException runtimeFailure) {
                throw runtimeFailure;
            }
            throw failure;
        }
    }

    private static AiTaskRequest<ExplanationInput> explanationRequest(List<EvidenceChunk> chunks) {
        return new AiTaskRequest<>(
                AiTaskType.EXPLANATION,
                PromptId.EXPLANATION_V1.name(),
                new LearnerContext("LEARNING", "FIRST", "STEADY", Map.of(), List.of()),
                new ExplanationInput(
                        "Explain membrane depolarization.",
                        "action potential",
                        ExplanationMode.STANDARD),
                evidence(chunks),
                chunks.isEmpty() ? GroundingMode.GENERAL_KNOWLEDGE : GroundingMode.STRICT_SOURCE,
                AiOutputContract.EXPLANATION);
    }

    private static EvidencePackage evidence(List<EvidenceChunk> chunks) {
        GroundingMode groundingMode = chunks.isEmpty()
                ? GroundingMode.GENERAL_KNOWLEDGE
                : GroundingMode.STRICT_SOURCE;
        RetrievalQuality quality = chunks.isEmpty() ? RetrievalQuality.FAILED : RetrievalQuality.STRONG;
        List<EvidenceSourceReference> references = chunks.stream()
                .map(chunk -> new EvidenceSourceReference(
                        EvidenceReferenceKind.CHUNK,
                        chunk.materialId(),
                        chunk.materialVersionId(),
                        chunk.documentNodeId(),
                        chunk.chunkId(),
                        null,
                        chunk.pageStart()))
                .toList();
        return new EvidencePackage(
                quality,
                groundingMode,
                chunks,
                List.of(),
                references,
                List.of(),
                new RetrievalDiagnostics(
                        chunks.size(),
                        chunks.size(),
                        chunks.stream().map(EvidenceChunk::chunkId).toList(),
                        List.of(),
                        chunks.isEmpty() ? Set.of() : Set.of(MATERIAL_ID),
                        Set.of(),
                        quality));
    }

    private static EvidenceChunk chunk(int rank, UUID chunkId, String content) {
        return new EvidenceChunk(
                rank,
                chunkId,
                MATERIAL_ID,
                VERSION_ID,
                NODE_ID,
                rank,
                content,
                7,
                7,
                List.of("Membrane physiology"),
                "TEXT",
                "NATIVE",
                "GOOD");
    }

    private static String validExplanation(UUID reference) {
        return """
                {
                  "concept": "action potential",
                  "explanation": "Sodium influx rapidly depolarizes the membrane.",
                  "keyPoints": ["Voltage-gated sodium channels open rapidly"],
                  "prerequisitesUsed": ["membrane potential"],
                  "sourceReferences": ["%s"],
                  "supplementalKnowledgeUsed": false,
                  "limitations": []
                }
                """.formatted(reference);
    }

    private static String validResponseEvaluation() {
        return """
                {
                  "evaluation": "PARTIAL",
                  "correctConcepts": ["AV node delay"],
                  "missingConcepts": ["ventricular filling"],
                  "misconceptions": [],
                  "feedback": "Connect the delay to ventricular filling.",
                  "certainty": "SUFFICIENT",
                  "recommendedAction": "RETRY",
                  "sourceReferences": [],
                  "limitations": []
                }
                """;
    }

    private static ProviderExecutionResult successfulResult(
            ProviderExecutionRequest request, String rawContent) {
        assertNoProviderTransaction();
        return new ProviderExecutionResult(
                request.target().providerId(),
                request.target().modelId(),
                rawContent,
                ProviderUsage.of(41, 21, 62),
                Duration.ofMillis(11));
    }

    private record DiagnosticRow(
            UUID userId,
            String taskType,
            String promptId,
            String promptVersion,
            String provider,
            String model,
            String status,
            String groundingMode,
            Integer inputTokens,
            Integer outputTokens,
            int retryCount,
            String errorCode) {}

    private record StatusRow(String status, String errorCode) {}

    private final class GateHarness implements AutoCloseable {
        private final String geminiModel;
        private final String ollamaModel;
        private final RecordingAdapter gemini;
        private final RecordingAdapter ollama;
        private final AiRequestManager manager;
        private final AiExecutionOrchestrator orchestrator;

        private GateHarness(
                String geminiModel,
                Function<ProviderExecutionRequest, ProviderExecutionResult> geminiBehavior,
                String ollamaModel,
                Function<ProviderExecutionRequest, ProviderExecutionResult> ollamaBehavior,
                AiSourceReferenceValidator sourceValidator) {
            this.geminiModel = geminiModel;
            this.ollamaModel = ollamaModel;
            gemini = new RecordingAdapter(ProviderId.GEMINI, geminiBehavior);
            ollama = new RecordingAdapter(ProviderId.OLLAMA_CLOUD, ollamaBehavior);
            AiRequestManagerPolicy policy = new AiRequestManagerPolicy(
                    1,
                    10,
                    Duration.ofSeconds(5),
                    1,
                    Duration.ofMillis(1),
                    Duration.ofMillis(1),
                    10,
                    Duration.ofSeconds(1));
            Map<ProviderId, AiRequestManagerPolicy> policies = new EnumMap<>(ProviderId.class);
            policies.put(ProviderId.GEMINI, policy);
            policies.put(ProviderId.OLLAMA_CLOUD, policy);
            manager = new AiRequestManager(
                    List.of(gemini, ollama), policies, 10, AiRequestTelemetry.NONE);
            orchestrator = new AiExecutionOrchestrator(
                    promptBuilder,
                    new ProviderRouter(),
                    manager,
                    outputValidator,
                    sourceValidator,
                    diagnostics);
        }

        private CompletableFuture<ValidatedAiResult<?>> execute(
                AiTaskRequest<?> request,
                PromptTokenBudget budget,
                List<ProviderRoutingCandidate> candidates) {
            return orchestrator.execute(
                    request,
                    USER_ID,
                    AiRequestPriority.INTERACTIVE_EXPLANATION,
                    budget,
                    candidates,
                    ProviderRoutingPreference.LATENCY_THEN_COST);
        }

        private List<ProviderRoutingCandidate> geminiOnly(AiTaskRequest<?> request) {
            return List.of(candidate(request, ProviderId.GEMINI, geminiModel, 1));
        }

        private List<ProviderRoutingCandidate> ollamaOnly(AiTaskRequest<?> request) {
            return List.of(candidate(request, ProviderId.OLLAMA_CLOUD, ollamaModel, 1));
        }

        private CountingCandidateList geminiThenOllama(AiTaskRequest<?> request) {
            return new CountingCandidateList(List.of(
                    candidate(request, ProviderId.GEMINI, geminiModel, 1),
                    candidate(request, ProviderId.OLLAMA_CLOUD, ollamaModel, 2)));
        }

        @Override
        public void close() {
            manager.close();
        }
    }

    private static ProviderRoutingCandidate candidate(
            AiTaskRequest<?> request,
            ProviderId providerId,
            String modelId,
            int rank) {
        Set<AiTaskType> supported = request.taskType() == AiTaskType.STRUCTURED_OUTPUT_REPAIR
                ? Set.of(AiTaskType.STRUCTURED_OUTPUT_REPAIR)
                : Set.of(request.taskType(), AiTaskType.STRUCTURED_OUTPUT_REPAIR);
        return new ProviderRoutingCandidate(
                providerId,
                modelId,
                supported,
                supported,
                true,
                true,
                true,
                rank,
                rank,
                rank);
    }

    private static final class RecordingAdapter implements AiProviderAdapter {
        private final ProviderId providerId;
        private final Function<ProviderExecutionRequest, ProviderExecutionResult> behavior;
        private final List<ProviderExecutionRequest> requests = new CopyOnWriteArrayList<>();

        private RecordingAdapter(
                ProviderId providerId,
                Function<ProviderExecutionRequest, ProviderExecutionResult> behavior) {
            this.providerId = providerId;
            this.behavior = behavior;
        }

        @Override
        public ProviderId providerId() {
            return providerId;
        }

        @Override
        public boolean supports(AiTaskType taskType) {
            return true;
        }

        @Override
        public ProviderExecutionResult execute(ProviderExecutionRequest request) {
            requests.add(request);
            return behavior.apply(request);
        }

        @Override
        public ProviderEventStream stream(ProviderExecutionRequest request) {
            throw new UnsupportedOperationException("streaming is outside the Phase 5 gate");
        }
    }

    private static final class CountingCandidateList extends AbstractList<ProviderRoutingCandidate> {
        private final List<ProviderRoutingCandidate> delegate;
        private final AtomicInteger streamCalls = new AtomicInteger();

        private CountingCandidateList(List<ProviderRoutingCandidate> delegate) {
            this.delegate = List.copyOf(delegate);
        }

        @Override
        public ProviderRoutingCandidate get(int index) {
            return delegate.get(index);
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public Stream<ProviderRoutingCandidate> stream() {
            streamCalls.incrementAndGet();
            return delegate.stream();
        }

        private int streamCalls() {
            return streamCalls.get();
        }
    }

    private static final class StubSourceReferenceRepository implements SourceReferenceRepository {
        private final Map<UUID, SourceReferenceSeed> sources = new java.util.concurrent.ConcurrentHashMap<>();

        private void authorize(EvidenceChunk chunk) {
            sources.put(chunk.chunkId(), new SourceReferenceSeed(
                    chunk.materialId(),
                    chunk.materialVersionId(),
                    chunk.documentNodeId(),
                    chunk.chunkId(),
                    null,
                    chunk.pageStart(),
                    MATERIAL_FILENAME,
                    "Membrane physiology"));
        }

        @Override
        public Optional<SourceReferenceSeed> findAuthorizedTarget(
                UUID userId, SourceReferenceTarget target) {
            if (!USER_ID.equals(userId) || !(target instanceof ChunkSourceTarget chunkTarget)) {
                return Optional.empty();
            }
            return Optional.ofNullable(sources.get(chunkTarget.chunkId()));
        }

        @Override
        public SourceReference upsert(SourceReferenceSeed seed, String displayLabel) {
            return new SourceReference(
                    UUID.randomUUID(),
                    seed.materialId(),
                    seed.materialVersionId(),
                    seed.documentNodeId(),
                    seed.chunkId(),
                    seed.visualAssetId(),
                    seed.pageNumber(),
                    null,
                    null,
                    displayLabel,
                    Instant.EPOCH);
        }

        @Override
        public Optional<SourceReference> resolveAuthorized(UUID userId, UUID sourceReferenceId) {
            return Optional.empty();
        }
    }
}
