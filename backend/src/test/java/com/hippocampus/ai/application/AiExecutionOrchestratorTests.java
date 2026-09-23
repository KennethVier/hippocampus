package com.hippocampus.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Duration;
import java.time.Instant;
import java.util.AbstractList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.hippocampus.ai.port.AiDiagnosticsPersistence;
import com.hippocampus.ai.port.AiRequestDiagnostic;
import com.hippocampus.ai.port.ProviderUsageDiagnostic;
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
import com.hippocampus.ai.domain.StructuredOutputRepairInput;
import com.hippocampus.ai.domain.ValidatedAiResult;
import com.hippocampus.ai.infrastructure.validation.JacksonAiStructuredOutputDecoder;
import com.hippocampus.identity.domain.AuthenticatedUser;
import com.hippocampus.identity.infrastructure.security.HippocampusPrincipal;
import com.hippocampus.identity.infrastructure.security.SpringSecurityCurrentUser;
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

class AiExecutionOrchestratorTests {

    private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID MATERIAL_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID VERSION_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID NODE_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID CHUNK_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_CHUNK_ID = UUID.fromString("50000000-0000-0000-0000-000000000002");
    private static final UUID FABRICATED_CHUNK_ID = UUID.fromString("50000000-0000-0000-0000-000000000099");
    private static final PromptTokenBudget TOKEN_BUDGET = new PromptTokenBudget(200_000, 1_000);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validInitialResponseDoesNotRepair() {
        try (Harness harness = harness(sequence(validExplanation(List.of())))) {
            ValidatedAiResult<?> result = join(harness.execute(request(List.of())));

            assertThat(result.result()).isInstanceOf(ExplanationResult.class);
            assertThat(harness.adapter.requests).hasSize(1);
            assertThat(harness.adapter.requests.getFirst().taskType()).isEqualTo(AiTaskType.EXPLANATION);
        }
    }

    @Test
    void recordsSuccessfulPrimaryRequestWithProviderUsageMetadata() {
        try (Harness harness = harness(sequence(validExplanation(List.of())))) {
            join(harness.execute(request(List.of())));

            assertThat(harness.diagnostics.records).hasSize(1);
            RecordedDiagnostics recorded = harness.diagnostics.records.getFirst();
            assertThat(recorded.request())
                    .extracting(
                            AiRequestDiagnostic::userId,
                            AiRequestDiagnostic::taskType,
                            AiRequestDiagnostic::promptId,
                            AiRequestDiagnostic::promptVersion,
                            AiRequestDiagnostic::provider,
                            AiRequestDiagnostic::model,
                            AiRequestDiagnostic::status,
                            AiRequestDiagnostic::groundingMode,
                            AiRequestDiagnostic::inputTokenCount,
                            AiRequestDiagnostic::outputTokenCount,
                            AiRequestDiagnostic::retryCount,
                            AiRequestDiagnostic::errorCode)
                    .containsExactly(
                            USER_ID, "EXPLANATION", "EXPLANATION_V1", "1",
                            "GEMINI", "gemini-primary", "SUCCESS", "GENERAL_KNOWLEDGE",
                            40, 20, 0, null);
            assertThat(recorded.request().latencyMs()).isEqualTo(25L);
            assertThat(recorded.request().createdAt()).isNotNull();
            assertThat(recorded.usage().requestCount()).isEqualTo(1);
            assertThat(recorded.usage().inputTokens()).isEqualTo(40L);
            assertThat(recorded.usage().outputTokens()).isEqualTo(20L);
            assertThat(recorded.usage().estimatedCost()).isNull();
            assertThat(recorded.usage().occurredAt()).isEqualTo(recorded.request().createdAt());
        }
    }

    @Test
    void recordsMissingProviderUsageAsNullWithoutEstimatingTokensOrCost() {
        try (Harness harness = harness(request -> new ProviderExecutionResult(
                request.target().providerId(),
                request.target().modelId(),
                validExplanation(List.of()),
                ProviderUsage.NONE,
                Duration.ofMillis(7)))) {
            join(harness.execute(request(List.of())));

            RecordedDiagnostics recorded = harness.diagnostics.records.getFirst();
            assertThat(recorded.request().inputTokenCount()).isNull();
            assertThat(recorded.request().outputTokenCount()).isNull();
            assertThat(recorded.usage().inputTokens()).isNull();
            assertThat(recorded.usage().outputTokens()).isNull();
            assertThat(recorded.usage().estimatedCost()).isNull();
        }
    }

    @Test
    void recordsRequestManagerRetryCountWithoutCreatingGuessedUsage() {
        AtomicInteger attempts = new AtomicInteger();
        try (Harness harness = harnessWithMaximumAttempts(2, request -> {
            if (attempts.getAndIncrement() == 0) {
                throw new ProviderExecutionException(ProviderId.GEMINI, ProviderFailureType.TIMEOUT);
            }
            return providerResult(request, validExplanation(List.of()));
        })) {
            join(harness.execute(request(List.of())));

            assertThat(attempts).hasValue(2);
            assertThat(harness.diagnostics.records).hasSize(1);
            assertThat(harness.diagnostics.records.getFirst().request().retryCount()).isEqualTo(1);
            assertThat(harness.diagnostics.records.getFirst().usage().requestCount()).isEqualTo(2);
        }
    }

    @Test
    void callerThreadSecurityIdentityAuthorizesSourcesAfterAsyncProviderCompletion() {
        EvidenceChunk source = chunk(1, CHUNK_ID, "source one");
        Thread callerThread = Thread.currentThread();
        AtomicReference<Thread> providerThread = new AtomicReference<>();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        new HippocampusPrincipal(USER_ID, "student@example.test"),
                        null,
                        List.of()));

        try (Harness harness = harness(
                new SpringSecurityCurrentUser(),
                AiRequestTelemetry.NONE,
                request -> {
                    providerThread.set(Thread.currentThread());
                    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
                    return providerResult(
                            request, validExplanation(List.of(CHUNK_ID.toString())));
                },
                null)) {
            harness.repository.authorize(source);

            join(harness.execute(request(List.of(source))));

            assertThat(providerThread.get()).isNotSameAs(callerThread);
            assertThat(harness.repository.lastAuthorizedUserId).isEqualTo(USER_ID);
            assertThat(harness.diagnostics.records.getFirst().request().userId()).isEqualTo(USER_ID);
        }
    }

    @Test
    void circuitRejectionPersistsLogicalRequestWithoutProviderUsage() {
        try (Harness harness = harness(providerFailure(
                ProviderId.GEMINI, ProviderFailureType.AUTHENTICATION_FAILURE))) {
            assertThatThrownBy(() -> join(harness.execute(request(List.of()))))
                    .isInstanceOf(ProviderExecutionException.class);
            assertThatThrownBy(() -> join(harness.execute(request(List.of()))))
                    .isInstanceOf(ProviderExecutionException.class);

            assertThat(harness.adapter.requests).hasSize(1);
            assertThat(harness.diagnostics.records).hasSize(2);
            assertThat(harness.diagnostics.records.get(0).usage().requestCount()).isEqualTo(1);
            assertThat(harness.diagnostics.records.get(1).usage()).isNull();
        }
    }

    @Test
    void unsupportedProviderRejectionPersistsLogicalRequestWithoutProviderUsage() {
        AiTaskRequest<ExplanationInput> task = request(List.of());
        try (Harness harness = harness(sequence(validExplanation(List.of())))) {
            assertThatThrownBy(() -> join(harness.execute(task, List.of(Harness.candidate(
                    task, ProviderId.OLLAMA_CLOUD, "ollama-not-configured", 1)))))
                    .isInstanceOf(ProviderExecutionException.class);

            assertThat(harness.adapter.requests).isEmpty();
            assertThat(harness.diagnostics.records).singleElement()
                    .extracting(RecordedDiagnostics::usage)
                    .isNull();
        }
    }

    @Test
    void cancellationWhileQueuedPersistsNoProviderUsage() throws Exception {
        CountDownLatch providerStarted = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        try (Harness harness = harness(request -> {
            providerStarted.countDown();
            try {
                assertThat(releaseProvider.await(2, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new CancellationException("cancelled");
            }
            return providerResult(request, validExplanation(List.of()));
        })) {
            CompletableFuture<ValidatedAiResult<?>> running = harness.execute(request(List.of()));
            assertThat(providerStarted.await(2, TimeUnit.SECONDS)).isTrue();
            CompletableFuture<ValidatedAiResult<?>> queued = harness.execute(request(List.of()));

            assertThat(queued.cancel(true)).isTrue();

            assertThat(queued).isCancelled();
            assertThat(harness.adapter.requests).hasSize(1);
            assertThat(harness.diagnostics.records)
                    .filteredOn(record -> "CANCELLED".equals(record.request().status()))
                    .singleElement()
                    .extracting(RecordedDiagnostics::usage)
                    .isNull();
            releaseProvider.countDown();
            join(running);
        } finally {
            releaseProvider.countDown();
        }
    }

    @Test
    void fallbackEmitsExactlyOneBoundedTelemetrySignal() {
        RecordingTelemetry telemetry = new RecordingTelemetry();
        try (Harness harness = harness(
                () -> new AuthenticatedUser(USER_ID),
                telemetry,
                providerFailure(ProviderId.GEMINI, ProviderFailureType.TIMEOUT),
                sequence(validExplanation(List.of())))) {
            join(harness.executeWithFallback(request(List.of())));

            assertThat(telemetry.fallbackSignals)
                    .containsExactly(new FallbackSignal(
                            ProviderId.GEMINI,
                            ProviderId.OLLAMA_CLOUD,
                            AiTaskType.EXPLANATION));
            assertThat(telemetry.fallbackSignals.toString())
                    .doesNotContain(
                            USER_ID.toString(), MATERIAL_ID.toString(), CHUNK_ID.toString(),
                            "source", "prompt", "response");
        }
    }

    @ParameterizedTest(name = "{0} primary failure invokes approved fallback")
    @MethodSource("fallbackEligibleProviderFailures")
    void eligiblePrimaryFailureInvokesFallback(ProviderFailureType failureType) {
        try (Harness harness = harness(
                providerFailure(ProviderId.GEMINI, failureType),
                sequence(validExplanation(List.of())))) {
            ValidatedAiResult<?> result = join(harness.executeWithFallback(request(List.of())));

            assertThat(result.result()).isInstanceOf(ExplanationResult.class);
            assertThat(harness.adapter.requests).hasSize(1);
            assertThat(harness.fallbackAdapter.requests).hasSize(1);
            assertThat(harness.fallbackAdapter.requests.getFirst().target())
                    .isEqualTo(new com.hippocampus.ai.application.routing.ProviderRoute.Target(
                            ProviderId.OLLAMA_CLOUD, "ollama-fallback"));
            assertThat(harness.diagnostics.records)
                    .extracting(record -> record.request().provider())
                    .containsExactly("GEMINI", "OLLAMA_CLOUD");
            assertThat(harness.diagnostics.records)
                    .extracting(record -> record.request().status())
                    .containsExactly("FAILED", "SUCCESS");
        }
    }

    @Test
    void providerInvocationRunsWithoutDatabaseTransactionBeforeDiagnosticsAreRecorded() {
        try (Harness harness = harness(request -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return providerResult(request, validExplanation(List.of()));
        })) {
            join(harness.execute(request(List.of())));

            assertThat(harness.diagnostics.records).hasSize(1);
        }
    }

    @Test
    void eligibleFailureWithoutFallbackReturnsOriginalNormalizedFailure() {
        ProviderExecutionException primaryFailure = new ProviderExecutionException(
                ProviderId.GEMINI, ProviderFailureType.PROVIDER_UNAVAILABLE);
        try (Harness harness = harness(request -> {
            throw primaryFailure;
        })) {
            Throwable failure = catchThrowable(() -> join(harness.execute(request(List.of()))));

            assertThat(failure)
                    .isInstanceOfSatisfying(ProviderExecutionException.class, providerFailure -> {
                        assertThat(providerFailure.providerId()).isEqualTo(primaryFailure.providerId());
                        assertThat(providerFailure.failureType()).isEqualTo(primaryFailure.failureType());
                        assertThat(providerFailure.retryCount()).isZero();
                    });
            assertThat(harness.adapter.requests).hasSize(1);
        }
    }

    @ParameterizedTest(name = "{0} primary failure does not invoke fallback")
    @MethodSource("fallbackIneligibleProviderFailures")
    void ineligibleProviderFailureDoesNotInvokeFallback(ProviderFailureType failureType) {
        try (Harness harness = harness(
                providerFailure(ProviderId.GEMINI, failureType),
                sequence(validExplanation(List.of())))) {
            assertThatThrownBy(() -> join(harness.executeWithFallback(request(List.of()))))
                    .isInstanceOfSatisfying(ProviderExecutionException.class,
                            failure -> assertThat(failure.failureType()).isEqualTo(failureType));
            assertThat(harness.adapter.requests).hasSize(1);
            assertThat(harness.fallbackAdapter.requests).isEmpty();
        }
    }

    @Test
    void fallbackReusesExactCanonicalPromptAndTrimmedSourceBoundary() {
        EvidenceChunk included = chunk(1, CHUNK_ID, "duplicate content");
        EvidenceChunk trimmed = chunk(2, SECOND_CHUNK_ID, "duplicate content");
        AiTaskRequest<ExplanationInput> originalRequest = request(List.of(included, trimmed));
        try (Harness harness = harness(
                providerFailure(ProviderId.GEMINI, ProviderFailureType.TIMEOUT),
                sequence(validExplanation(List.of(CHUNK_ID.toString()))))) {
            harness.repository.authorize(included);

            join(harness.executeWithFallback(originalRequest));

            ProviderExecutionRequest primary = harness.adapter.requests.getFirst();
            ProviderExecutionRequest fallback = harness.fallbackAdapter.requests.getFirst();
            assertThat(fallback.taskType()).isEqualTo(originalRequest.taskType());
            assertThat(fallback.outputContract()).isSameAs(originalRequest.outputContract());
            assertThat(fallback.promptContext()).isSameAs(primary.promptContext());
            assertThat(fallback.promptContext().includedSources())
                    .extracting(source -> source.chunkId())
                    .containsExactly(CHUNK_ID)
                    .doesNotContain(SECOND_CHUNK_ID);
        }
    }

    @Test
    void fallbackFabricatedReferenceFailsGroundingWithoutRepairOrFurtherFallback() {
        EvidenceChunk source = chunk(1, CHUNK_ID, "source one");
        try (Harness harness = harness(
                providerFailure(ProviderId.GEMINI, ProviderFailureType.RATE_LIMITED),
                sequence(validExplanation(List.of(FABRICATED_CHUNK_ID.toString()))))) {
            assertGroundingFailure(harness.executeWithFallback(request(List.of(source))));

            assertThat(harness.adapter.requests).hasSize(1);
            assertThat(harness.fallbackAdapter.requests).hasSize(1);
        }
    }

    @Test
    void malformedFallbackResponseRepairsOnceOnFallbackProviderAndModel() {
        try (Harness harness = harness(
                providerFailure(ProviderId.GEMINI, ProviderFailureType.QUOTA_EXHAUSTED),
                sequence("not json", validExplanation(List.of())))) {
            ValidatedAiResult<?> result = join(harness.executeWithFallback(request(List.of())));

            assertThat(result.result()).isInstanceOf(ExplanationResult.class);
            assertThat(harness.adapter.requests).hasSize(1);
            assertThat(harness.fallbackAdapter.requests).hasSize(2);
            ProviderExecutionRequest fallback = harness.fallbackAdapter.requests.get(0);
            ProviderExecutionRequest repair = harness.fallbackAdapter.requests.get(1);
            assertThat(repair.taskType()).isEqualTo(AiTaskType.STRUCTURED_OUTPUT_REPAIR);
            assertThat(repair.target()).isEqualTo(fallback.target());
            assertThat(repair.outputContract()).isSameAs(fallback.outputContract());
            assertThat(harness.diagnostics.records)
                    .extracting(record -> List.of(
                            record.request().provider(),
                            record.request().taskType(),
                            record.request().status(),
                            String.valueOf(record.request().errorCode())))
                    .containsExactly(
                            List.of("GEMINI", "EXPLANATION", "FAILED", "QUOTA_EXHAUSTED"),
                            List.of("OLLAMA_CLOUD", "EXPLANATION", "FAILED", "AI_SCHEMA_FAILURE"),
                            List.of("OLLAMA_CLOUD", "STRUCTURED_OUTPUT_REPAIR", "SUCCESS", "null"));
            assertThat(harness.diagnostics.records.get(1).request().promptId())
                    .isEqualTo("EXPLANATION_V1");
            assertThat(harness.diagnostics.records.get(2).request().promptId())
                    .isEqualTo("STRUCTURED_OUTPUT_REPAIR_V1");
        }
    }

    @Test
    void diagnosticCarriersNeverReceivePromptSourceResponseOrProviderErrorContent() {
        String privateSource = "private-source-content";
        String malformedResponse = "private-malformed-response";
        EvidenceChunk source = chunk(1, CHUNK_ID, privateSource);
        try (Harness harness = harness(sequence(
                malformedResponse,
                validExplanation(List.of(CHUNK_ID.toString()))))) {
            harness.repository.authorize(source);

            join(harness.execute(request(List.of(source))));

            assertThat(harness.diagnostics.records).hasSize(2);
            assertThat(harness.diagnostics.records.toString())
                    .doesNotContain(privateSource, malformedResponse, "secret", "api-key");
        }
    }

    @Test
    void fallbackRepairFailureIsTerminalWithoutReturningToPrimary() {
        try (Harness harness = harness(
                providerFailure(ProviderId.GEMINI, ProviderFailureType.PROVIDER_UNAVAILABLE),
                sequence("not json", "still not json"))) {
            assertThatThrownBy(() -> join(harness.executeWithFallback(request(List.of()))))
                    .isInstanceOf(AiSchemaValidationException.class);

            assertThat(harness.adapter.requests).hasSize(1);
            assertThat(harness.fallbackAdapter.requests).hasSize(2);
        }
    }

    @Test
    void providerRouteIsResolvedOnceForPrimaryAndFallbackExecution() {
        try (Harness harness = harness(
                providerFailure(ProviderId.GEMINI, ProviderFailureType.TIMEOUT),
                sequence(validExplanation(List.of())))) {
            CountingCandidateList candidates = harness.fallbackCandidates(request(List.of()));

            join(harness.execute(request(List.of()), candidates));

            // ProviderRouter streams once for null validation and once for eligibility/ranking.
            // A second route invocation would therefore raise this count to four.
            assertThat(candidates.streamCalls()).isEqualTo(2);
            assertThat(harness.adapter.requests).hasSize(1);
            assertThat(harness.fallbackAdapter.requests).hasSize(1);
        }
    }

    @ParameterizedTest(name = "{0} provider failure does not trigger schema repair")
    @MethodSource("nonRepairProviderFailures")
    void providerFailuresDoNotRepair(ProviderFailureType failureType) {
        try (Harness harness = harness(request -> {
            throw new ProviderExecutionException(ProviderId.GEMINI, failureType);
        })) {
            assertThatThrownBy(() -> join(harness.execute(request(List.of()))))
                    .isInstanceOfSatisfying(ProviderExecutionException.class,
                            failure -> assertThat(failure.failureType()).isEqualTo(failureType));
            assertThat(harness.adapter.requests).hasSize(1);
        }
    }

    @Test
    void groundingFailureDoesNotRepair() {
        EvidenceChunk source = chunk(1, CHUNK_ID, "source one");
        try (Harness harness = harness(sequence(validExplanation(List.of(FABRICATED_CHUNK_ID.toString()))))) {
            assertGroundingFailure(harness.execute(request(List.of(source))));
            assertThat(harness.adapter.requests).hasSize(1);
        }
    }

    @Test
    void schemaAndGroundingFailuresDoNotInvokeConfiguredFallback() {
        EvidenceChunk source = chunk(1, CHUNK_ID, "source one");
        try (Harness schemaHarness = harness(sequence("not json", "still invalid"),
                sequence(validExplanation(List.of())));
                Harness groundingHarness = harness(
                        sequence(validExplanation(List.of(FABRICATED_CHUNK_ID.toString()))),
                        sequence(validExplanation(List.of())))) {
            assertThatThrownBy(() -> join(schemaHarness.executeWithFallback(request(List.of()))))
                    .isInstanceOf(AiSchemaValidationException.class);
            assertGroundingFailure(groundingHarness.executeWithFallback(request(List.of(source))));

            assertThat(schemaHarness.adapter.requests).hasSize(2);
            assertThat(schemaHarness.fallbackAdapter.requests).isEmpty();
            assertThat(groundingHarness.adapter.requests).hasSize(1);
            assertThat(groundingHarness.fallbackAdapter.requests).isEmpty();
        }
    }

    @Test
    void malformedInitialResponseRepairsOnceOnSameProviderModelAndContract() {
        EvidenceChunk source = chunk(1, CHUNK_ID, "source one");
        try (Harness harness = harness(sequence(
                "{\"privateMalformed\":",
                validExplanation(List.of(CHUNK_ID.toString()))))) {
            harness.repository.authorize(source);

            ValidatedAiResult<?> result = join(harness.execute(request(List.of(source))));

            assertThat(result.result()).isInstanceOf(ExplanationResult.class);
            assertThat(harness.adapter.requests).hasSize(2);
            ProviderExecutionRequest original = harness.adapter.requests.get(0);
            ProviderExecutionRequest repair = harness.adapter.requests.get(1);
            assertThat(repair.taskType()).isEqualTo(AiTaskType.STRUCTURED_OUTPUT_REPAIR);
            assertThat(repair.target()).isEqualTo(original.target());
            assertThat(repair.target().providerId()).isEqualTo(ProviderId.GEMINI);
            assertThat(repair.target().modelId()).isEqualTo("gemini-primary");
            assertThat(repair.outputContract()).isSameAs(original.outputContract());
            assertThat(repair.promptContext().includedSources()).isEmpty();
            assertThat(harness.diagnostics.records)
                    .extracting(record -> List.of(
                            record.request().taskType(),
                            record.request().promptId(),
                            record.request().status()))
                    .containsExactly(
                            List.of("EXPLANATION", "EXPLANATION_V1", "FAILED"),
                            List.of("STRUCTURED_OUTPUT_REPAIR", "STRUCTURED_OUTPUT_REPAIR_V1", "SUCCESS"));
        }
    }

    @Test
    void secondSchemaFailureIsTerminalAndDoesNotExposeMalformedOutput() {
        String sensitiveMalformedOutput = "{\"private-note\":\"secret-medical-note\"";
        try (Harness harness = harness(sequence(sensitiveMalformedOutput, "still invalid"))) {
            assertThatThrownBy(() -> join(harness.execute(request(List.of()))))
                    .isInstanceOfSatisfying(AiSchemaValidationException.class, failure -> {
                        assertThat(failure.errorCode().value()).isEqualTo("AI_SCHEMA_FAILURE");
                        assertThat(failure.getMessage())
                                .doesNotContain(sensitiveMalformedOutput, "secret-medical-note", "still invalid");
                        assertThat(failure.getCause()).isNull();
                    });
            assertThat(harness.adapter.requests).hasSize(2);
        }
    }

    @Test
    void fabricatedReferenceInRepairFailsGroundingWithoutAnotherRepair() {
        EvidenceChunk source = chunk(1, CHUNK_ID, "source one");
        try (Harness harness = harness(sequence(
                "not json",
                validExplanation(List.of(FABRICATED_CHUNK_ID.toString()))))) {
            assertGroundingFailure(harness.execute(request(List.of(source))));
            assertThat(harness.adapter.requests).hasSize(2);
        }
    }

    @Test
    void repairCannotReferenceEvidenceTrimmedFromOriginalPrompt() {
        EvidenceChunk included = chunk(1, CHUNK_ID, "duplicate content");
        EvidenceChunk deduplicated = chunk(2, SECOND_CHUNK_ID, "duplicate content");
        try (Harness harness = harness(sequence(
                "not json",
                validExplanation(List.of(SECOND_CHUNK_ID.toString()))))) {
            harness.repository.authorize(deduplicated);

            assertGroundingFailure(harness.execute(request(List.of(included, deduplicated))));

            assertThat(harness.adapter.requests).hasSize(2);
            assertThat(harness.adapter.requests.getFirst().promptContext().includedSources())
                    .extracting(source -> source.chunkId())
                    .containsExactly(CHUNK_ID);
        }
    }

    @Test
    void repairTaskCannotRecursivelyTriggerRepair() {
        try (Harness harness = harness(sequence("not json"))) {
            AiTaskRequest<StructuredOutputRepairInput> repairRequest = new AiTaskRequest<>(
                    AiTaskType.STRUCTURED_OUTPUT_REPAIR,
                    PromptId.STRUCTURED_OUTPUT_REPAIR_V1.name(),
                    learnerContext(),
                    new StructuredOutputRepairInput("previous invalid output"),
                    evidence(List.of()),
                    GroundingMode.GENERAL_KNOWLEDGE,
                    AiOutputContract.EXPLANATION);

            assertThatThrownBy(() -> join(harness.execute(repairRequest)))
                    .isInstanceOf(AiSchemaValidationException.class);
            assertThat(harness.adapter.requests).hasSize(1);
        }
    }

    @Test
    void cancellationBeforeRepairStartsCancelsLogicalRequestWithoutRepair() throws Exception {
        CountDownLatch providerStarted = new CountDownLatch(1);
        try (Harness harness = harness(request -> {
            providerStarted.countDown();
            try {
                new CountDownLatch(1).await();
                throw new AssertionError("provider wait unexpectedly completed");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new CancellationException("cancelled");
            }
        })) {
            CompletableFuture<ValidatedAiResult<?>> result = harness.execute(request(List.of()));
            assertThat(providerStarted.await(2, TimeUnit.SECONDS)).isTrue();

            assertThat(result.cancel(true)).isTrue();

            assertThat(result).isCancelled();
            assertThat(harness.adapter.requests).hasSize(1);
            assertThat(harness.diagnostics.records)
                    .filteredOn(record -> "CANCELLED".equals(record.request().status()))
                    .singleElement()
                    .extracting(record -> record.usage().requestCount())
                    .isEqualTo(1);
        }
    }

    @Test
    void cancellationBeforeFallbackPreventsFallbackExecution() throws Exception {
        CountDownLatch providerStarted = new CountDownLatch(1);
        try (Harness harness = harness(request -> {
            providerStarted.countDown();
            try {
                new CountDownLatch(1).await();
                throw new AssertionError("provider wait unexpectedly completed");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ProviderExecutionException(
                        ProviderId.GEMINI, ProviderFailureType.PROVIDER_UNAVAILABLE);
            }
        }, sequence(validExplanation(List.of())))) {
            CompletableFuture<ValidatedAiResult<?>> result = harness.executeWithFallback(request(List.of()));
            assertThat(providerStarted.await(2, TimeUnit.SECONDS)).isTrue();

            assertThat(result.cancel(true)).isTrue();

            assertThat(result).isCancelled();
            assertThat(harness.adapter.requests).hasSize(1);
            assertThat(harness.fallbackAdapter.requests).isEmpty();
        }
    }

    private static Stream<Arguments> nonRepairProviderFailures() {
        return Stream.of(ProviderFailureType.values()).map(Arguments::of);
    }

    private static Stream<Arguments> fallbackEligibleProviderFailures() {
        return Stream.of(
                        ProviderFailureType.PROVIDER_UNAVAILABLE,
                        ProviderFailureType.RATE_LIMITED,
                        ProviderFailureType.QUOTA_EXHAUSTED,
                        ProviderFailureType.TIMEOUT)
                .map(Arguments::of);
    }

    private static Stream<Arguments> fallbackIneligibleProviderFailures() {
        return Stream.of(
                        ProviderFailureType.AUTHENTICATION_FAILURE,
                        ProviderFailureType.UNSUPPORTED_TASK,
                        ProviderFailureType.INVALID_RESPONSE)
                .map(Arguments::of);
    }

    private static void assertGroundingFailure(CompletableFuture<ValidatedAiResult<?>> result) {
        assertThatThrownBy(() -> join(result))
                .isInstanceOfSatisfying(AiGroundingValidationException.class,
                        failure -> assertThat(failure.errorCode().value()).isEqualTo("AI_GROUNDING_FAILURE"));
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

    private static Harness harness(Function<ProviderExecutionRequest, ProviderExecutionResult> behavior) {
        return new Harness(behavior, null);
    }

    private static Harness harness(
            Function<ProviderExecutionRequest, ProviderExecutionResult> primaryBehavior,
            Function<ProviderExecutionRequest, ProviderExecutionResult> fallbackBehavior) {
        return new Harness(primaryBehavior, fallbackBehavior);
    }

    private static Harness harness(
            CurrentUser currentUser,
            AiRequestTelemetry telemetry,
            Function<ProviderExecutionRequest, ProviderExecutionResult> primaryBehavior,
            Function<ProviderExecutionRequest, ProviderExecutionResult> fallbackBehavior) {
        return new Harness(primaryBehavior, fallbackBehavior, 1, currentUser, telemetry);
    }

    private static Harness harnessWithMaximumAttempts(
            int maximumAttempts,
            Function<ProviderExecutionRequest, ProviderExecutionResult> primaryBehavior) {
        return new Harness(primaryBehavior, null, maximumAttempts);
    }

    private static Function<ProviderExecutionRequest, ProviderExecutionResult> sequence(String... outputs) {
        AtomicInteger index = new AtomicInteger();
        return request -> providerResult(request, outputs[index.getAndIncrement()]);
    }

    private static Function<ProviderExecutionRequest, ProviderExecutionResult> providerFailure(
            ProviderId providerId, ProviderFailureType failureType) {
        return request -> {
            throw new ProviderExecutionException(providerId, failureType);
        };
    }

    private static AiTaskRequest<ExplanationInput> request(List<EvidenceChunk> chunks) {
        GroundingMode groundingMode = chunks.isEmpty()
                ? GroundingMode.GENERAL_KNOWLEDGE
                : GroundingMode.STRICT_SOURCE;
        return new AiTaskRequest<>(
                AiTaskType.EXPLANATION,
                PromptId.EXPLANATION_V1.name(),
                learnerContext(),
                new ExplanationInput("objective", "concept", ExplanationMode.STANDARD),
                evidence(chunks),
                groundingMode,
                AiOutputContract.EXPLANATION);
    }

    private static LearnerContext learnerContext() {
        return new LearnerContext("LEARNING", "FIRST", "STEADY", Map.of(), List.of());
    }

    private static EvidencePackage evidence(List<EvidenceChunk> chunks) {
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
        RetrievalQuality quality = chunks.isEmpty() ? RetrievalQuality.FAILED : RetrievalQuality.STRONG;
        GroundingMode groundingMode = chunks.isEmpty()
                ? GroundingMode.GENERAL_KNOWLEDGE
                : GroundingMode.STRICT_SOURCE;
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
                List.of("Section"),
                "TEXT",
                "NATIVE",
                "GOOD");
    }

    private static String validExplanation(List<String> references) {
        String serializedReferences = references.stream()
                .map(reference -> "\"" + reference + "\"")
                .collect(java.util.stream.Collectors.joining(", "));
        return """
                {
                  "concept": "action potential",
                  "explanation": "Sodium influx rapidly depolarizes the membrane.",
                  "keyPoints": ["Voltage-gated sodium channels open rapidly"],
                  "prerequisitesUsed": ["membrane potential"],
                  "sourceReferences": [%s],
                  "supplementalKnowledgeUsed": false,
                  "limitations": []
                }
                """.formatted(serializedReferences);
    }

    private static ProviderExecutionResult providerResult(
            ProviderExecutionRequest request, String rawContent) {
        return new ProviderExecutionResult(
                request.target().providerId(),
                request.target().modelId(),
                rawContent,
                ProviderUsage.of(40, 20, 60),
                Duration.ofMillis(25));
    }

    private static final class Harness implements AutoCloseable {
        private final StubProviderAdapter adapter;
        private final StubProviderAdapter fallbackAdapter;
        private final StubSourceReferenceRepository repository = new StubSourceReferenceRepository();
        private final RecordingDiagnostics diagnostics = new RecordingDiagnostics();
        private final AiRequestManager manager;
        private final AiExecutionOrchestrator orchestrator;

        private Harness(
                Function<ProviderExecutionRequest, ProviderExecutionResult> primaryBehavior,
                Function<ProviderExecutionRequest, ProviderExecutionResult> fallbackBehavior) {
            this(primaryBehavior, fallbackBehavior, 1);
        }

        private Harness(
                Function<ProviderExecutionRequest, ProviderExecutionResult> primaryBehavior,
                Function<ProviderExecutionRequest, ProviderExecutionResult> fallbackBehavior,
                int maximumAttempts) {
            this(
                    primaryBehavior,
                    fallbackBehavior,
                    maximumAttempts,
                    () -> new AuthenticatedUser(USER_ID),
                    AiRequestTelemetry.NONE);
        }

        private Harness(
                Function<ProviderExecutionRequest, ProviderExecutionResult> primaryBehavior,
                Function<ProviderExecutionRequest, ProviderExecutionResult> fallbackBehavior,
                int maximumAttempts,
                CurrentUser currentUser,
                AiRequestTelemetry telemetry) {
            adapter = new StubProviderAdapter(ProviderId.GEMINI, primaryBehavior);
            fallbackAdapter = fallbackBehavior == null
                    ? null
                    : new StubProviderAdapter(ProviderId.OLLAMA_CLOUD, fallbackBehavior);
            AiRequestManagerPolicy policy = new AiRequestManagerPolicy(
                    1,
                    10,
                    Duration.ofSeconds(5),
                    maximumAttempts,
                    Duration.ofMillis(1),
                    Duration.ofMillis(1),
                    10,
                    Duration.ofSeconds(1));
            Map<ProviderId, AiRequestManagerPolicy> policies = new EnumMap<>(ProviderId.class);
            policies.put(ProviderId.GEMINI, policy);
            List<AiProviderAdapter> adapters;
            if (fallbackAdapter == null) {
                adapters = List.of(adapter);
            } else {
                adapters = List.of(adapter, fallbackAdapter);
                policies.put(ProviderId.OLLAMA_CLOUD, policy);
            }
            manager = new AiRequestManager(adapters, policies, 10, telemetry);
            orchestrator = new AiExecutionOrchestrator(
                    new PromptContextBuilder(new PromptTemplateRegistry(), String::length),
                    currentUser,
                    new ProviderRouter(),
                    manager,
                    new AiOutputValidator(new JacksonAiStructuredOutputDecoder()),
                    new AiSourceReferenceValidator(repository),
                    diagnostics,
                    telemetry);
        }

        private CompletableFuture<ValidatedAiResult<?>> execute(AiTaskRequest<?> request) {
            return execute(request, List.of(candidate(
                    request, ProviderId.GEMINI, "gemini-primary", 1)));
        }

        private CompletableFuture<ValidatedAiResult<?>> executeWithFallback(AiTaskRequest<?> request) {
            return execute(request, fallbackCandidates(request));
        }

        private CountingCandidateList fallbackCandidates(AiTaskRequest<?> request) {
            return new CountingCandidateList(List.of(
                    candidate(request, ProviderId.GEMINI, "gemini-primary", 1),
                    candidate(request, ProviderId.OLLAMA_CLOUD, "ollama-fallback", 2)));
        }

        private CompletableFuture<ValidatedAiResult<?>> execute(
                AiTaskRequest<?> request, List<ProviderRoutingCandidate> candidates) {
            return orchestrator.execute(
                    request,
                    AiRequestPriority.INTERACTIVE_EXPLANATION,
                    TOKEN_BUDGET,
                    candidates,
                    ProviderRoutingPreference.LATENCY_THEN_COST);
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

        @Override
        public void close() {
            manager.close();
        }
    }

    private static final class RecordingDiagnostics
            implements AiDiagnosticsPersistence {
        private final List<RecordedDiagnostics> records = new CopyOnWriteArrayList<>();

        @Override
        public void record(AiRequestDiagnostic request) {
            records.add(new RecordedDiagnostics(request, null));
        }

        @Override
        public void record(AiRequestDiagnostic request, ProviderUsageDiagnostic usage) {
            records.add(new RecordedDiagnostics(request, usage));
        }
    }

    private record RecordedDiagnostics(
            AiRequestDiagnostic request,
            ProviderUsageDiagnostic usage) {}

    private static final class RecordingTelemetry implements AiRequestTelemetry {
        private final List<FallbackSignal> fallbackSignals = new CopyOnWriteArrayList<>();

        @Override
        public void fallback(
                ProviderId primaryProviderId,
                ProviderId fallbackProviderId,
                AiTaskType taskType) {
            fallbackSignals.add(new FallbackSignal(
                    primaryProviderId, fallbackProviderId, taskType));
        }
    }

    private record FallbackSignal(
            ProviderId primaryProviderId,
            ProviderId fallbackProviderId,
            AiTaskType taskType) {}

    private static final class StubProviderAdapter implements AiProviderAdapter {
        private final ProviderId providerId;
        private final Function<ProviderExecutionRequest, ProviderExecutionResult> behavior;
        private final List<ProviderExecutionRequest> requests = new CopyOnWriteArrayList<>();

        private StubProviderAdapter(
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
            throw new UnsupportedOperationException("streaming is outside P5-10");
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
        private final Map<UUID, SourceReferenceSeed> sources = new java.util.HashMap<>();
        private volatile UUID lastAuthorizedUserId;

        private void authorize(EvidenceChunk chunk) {
            sources.put(chunk.chunkId(), new SourceReferenceSeed(
                    chunk.materialId(),
                    chunk.materialVersionId(),
                    chunk.documentNodeId(),
                    chunk.chunkId(),
                    null,
                    chunk.pageStart(),
                    "Material",
                    "Section"));
        }

        @Override
        public Optional<SourceReferenceSeed> findAuthorizedTarget(
                UUID userId, SourceReferenceTarget target) {
            lastAuthorizedUserId = userId;
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
