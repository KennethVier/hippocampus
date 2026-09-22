package com.hippocampus.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
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
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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

    @Test
    void validInitialResponseDoesNotRepair() {
        try (Harness harness = harness(sequence(validExplanation(List.of())))) {
            ValidatedAiResult<?> result = join(harness.execute(request(List.of())));

            assertThat(result.result()).isInstanceOf(ExplanationResult.class);
            assertThat(harness.adapter.requests).hasSize(1);
            assertThat(harness.adapter.requests.getFirst().taskType()).isEqualTo(AiTaskType.EXPLANATION);
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
        }
    }

    private static Stream<Arguments> nonRepairProviderFailures() {
        return Stream.of(ProviderFailureType.values()).map(Arguments::of);
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
        return new Harness(behavior);
    }

    private static Function<ProviderExecutionRequest, ProviderExecutionResult> sequence(String... outputs) {
        AtomicInteger index = new AtomicInteger();
        return request -> providerResult(request, outputs[index.getAndIncrement()]);
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
        private final StubSourceReferenceRepository repository = new StubSourceReferenceRepository();
        private final AiRequestManager manager;
        private final AiExecutionOrchestrator orchestrator;

        private Harness(Function<ProviderExecutionRequest, ProviderExecutionResult> behavior) {
            adapter = new StubProviderAdapter(behavior);
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
            manager = new AiRequestManager(List.of(adapter), policies, 10, AiRequestTelemetry.NONE);
            CurrentUser currentUser = () -> new AuthenticatedUser(USER_ID);
            orchestrator = new AiExecutionOrchestrator(
                    new PromptContextBuilder(new PromptTemplateRegistry(), String::length),
                    new ProviderRouter(),
                    manager,
                    new AiOutputValidator(new JacksonAiStructuredOutputDecoder()),
                    new AiSourceReferenceValidator(currentUser, repository));
        }

        private CompletableFuture<ValidatedAiResult<?>> execute(AiTaskRequest<?> request) {
            Set<AiTaskType> supported = request.taskType() == AiTaskType.STRUCTURED_OUTPUT_REPAIR
                    ? Set.of(AiTaskType.STRUCTURED_OUTPUT_REPAIR)
                    : Set.of(request.taskType(), AiTaskType.STRUCTURED_OUTPUT_REPAIR);
            ProviderRoutingCandidate candidate = new ProviderRoutingCandidate(
                    ProviderId.GEMINI,
                    "gemini-primary",
                    supported,
                    supported,
                    true,
                    true,
                    true,
                    1,
                    1,
                    1);
            return orchestrator.execute(
                    request,
                    USER_ID,
                    AiRequestPriority.INTERACTIVE_EXPLANATION,
                    TOKEN_BUDGET,
                    List.of(candidate),
                    ProviderRoutingPreference.LATENCY_THEN_COST);
        }

        @Override
        public void close() {
            manager.close();
        }
    }

    private static final class StubProviderAdapter implements AiProviderAdapter {
        private final Function<ProviderExecutionRequest, ProviderExecutionResult> behavior;
        private final List<ProviderExecutionRequest> requests = new CopyOnWriteArrayList<>();

        private StubProviderAdapter(Function<ProviderExecutionRequest, ProviderExecutionResult> behavior) {
            this.behavior = behavior;
        }

        @Override
        public ProviderId providerId() {
            return ProviderId.GEMINI;
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

    private static final class StubSourceReferenceRepository implements SourceReferenceRepository {
        private final Map<UUID, SourceReferenceSeed> sources = new java.util.HashMap<>();

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
