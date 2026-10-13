package com.hippocampus.ai.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hippocampus.ai.application.provider.AiProviderAdapter;
import com.hippocampus.ai.application.provider.ProviderEventStream;
import com.hippocampus.ai.application.provider.ProviderExecutionRequest;
import com.hippocampus.ai.application.provider.ProviderExecutionResult;
import com.hippocampus.ai.application.provider.ProviderUsage;
import com.hippocampus.ai.application.prompt.PromptContext;
import com.hippocampus.ai.application.prompt.PromptId;
import com.hippocampus.ai.application.routing.ProviderId;
import com.hippocampus.ai.application.routing.ProviderRoute;
import com.hippocampus.ai.domain.AiOutputContract;
import com.hippocampus.ai.domain.AiTaskType;
import com.hippocampus.materials.domain.ChunkSourceTarget;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class GoldenResponseAdapterLiveCollectorTests {

    @Test
    void isNotAuthorizedByDefault() {
        // Deliberate execution authorization must be required; never runs automatically
        assertThat(GoldenResponseAdapterLiveCollector.isAuthorized()).isFalse();
    }

    @Test
    void parsesConfigurableLimitsAndCostRatesWithSafeDefaults() {
        var limits = GoldenResponseAdapterLiveCollector.CollectorLimits.defaultLimits();
        assertThat(limits.maxRequests()).isEqualTo(25);
        assertThat(limits.maxTokens()).isEqualTo(100_000L);
        assertThat(limits.timeoutSeconds()).isEqualTo(120L);
        assertThat(limits.maxCostUsd()).isEqualByComparingTo(new BigDecimal("1.00"));
        assertThat(limits.delayMillis()).isEqualTo(0L);

        var rates = GoldenResponseAdapterLiveCollector.CostRates.defaults();
        assertThat(rates.perMillionInputTokens()).isEqualByComparingTo(new BigDecimal("0.10"));
        assertThat(rates.perMillionOutputTokens()).isEqualByComparingTo(new BigDecimal("0.40"));
        // 10,000 input tokens @ 0.10/M = 0.0010; 2,000 output tokens @ 0.40/M = 0.0008 -> 0.0018
        assertThat(rates.estimate(10_000L, 2_000L)).isEqualByComparingTo(new BigDecimal("0.001800"));
    }

    @Test
    void qualificationIdentityHasCompleteCryptographicInputs() throws Exception {
        var identity = GoldenResponseAdapterLiveCollector.currentIdentity("GEMINI", "gemini-2.5-flash");

        assertThat(identity.version()).isEqualTo("P7-09-two-pass-adapter-v1");
        assertThat(identity.inputs())
                .containsEntry("dataset", "v7")
                .containsEntry("provider", "GEMINI")
                .containsEntry("model", "gemini-2.5-flash")
                .containsEntry("discoveryPrompt", "RESPONSE_EVALUATION_DISCOVERY_V1")
                .containsEntry("verificationPrompt", "RESPONSE_EVALUATION_VERIFICATION_V1")
                .containsEntry("adapter", "AiResponseEvaluationAdapter (two-pass verified attribution)")
                .containsKey("src/test/resources/ai/golden/v7/response-evaluation-rubric-overrides.json");

        assertThat(identity.fingerprint()).hasSize(64);
        var changedLimits = new GoldenResponseAdapterLiveCollector.CollectorLimits(
                24, 100_000, 120, new BigDecimal("1.00"), 0);
        var changed = GoldenResponseAdapterLiveCollector.currentIdentity(
                "GEMINI", "gemini-2.5-flash", changedLimits,
                GoldenResponseAdapterLiveCollector.CostRates.defaults());
        assertThat(changed.fingerprint()).isNotEqualTo(identity.fingerprint());
        assertThat(identity.inputs()).containsKey(
                "src/main/java/com/hippocampus/ai/application/prompt/PromptTemplateRegistry.java");
        String geminiAdapter = "src/main/java/com/hippocampus/ai/infrastructure/provider/gemini/GeminiProviderAdapter.java";
        assertThat(identity.inputs()).containsKey(geminiAdapter);
        assertThat(identity.inputs().get(geminiAdapter)).hasSize(64);
        assertThat(identity.inputs()).containsKey(
                "src/main/java/com/hippocampus/ai/infrastructure/learning/SourceSpanOffsetNormalizer.java");
        for (String file : List.of(
                "src/main/java/com/hippocampus/ai/infrastructure/provider/ProviderStructuredOutputSchema.java",
                "src/main/java/com/hippocampus/ai/application/validation/AiOutputValidator.java")) {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(Path.of(file))));
            assertThat(identity.inputs()).containsEntry(file, hash);
            var modifiedInputs = new TreeMap<>(identity.inputs());
            modifiedInputs.put(file, "changed implementation hash");
            String changedFingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(new ObjectMapper().writeValueAsBytes(modifiedInputs)));
            assertThat(changedFingerprint).isNotEqualTo(identity.fingerprint());
        }
    }

    @Test
    void offlineSyntheticRunExercisesTwoPassDiscoveryVerificationAndAggregator() throws IOException {
        String discoveryJson = """
                {
                  "assertions": [
                    {
                      "id": "a0",
                      "studentStart": 3,
                      "studentEnd": 33,
                      "studentText": "denervates the wrist extensors",
                      "proposition": "denervates the wrist extensors",
                      "polarity": "AFFIRMED",
                      "qualifiers": [],
                      "relatedExpectedConceptIndexes": [0, 1]
                    }
                  ],
                  "candidates": [
                    {
                      "id": "c0",
                      "assertionIds": ["a0"],
                      "expectedConceptIndex": 0,
                      "constituentMeaning": "wrist-extensor denervation",
                      "relationToExpectedConcept": "CONSTITUENT",
                      "sourceEvidence": [
                        {
                          "sourceReferenceId": "00000000-0000-0000-0000-000000000303",
                          "sourceVersion": "00000000-0000-0000-0000-000000000709",
                          "sourceStart": 0,
                          "sourceEnd": 42,
                          "sourceText": "The radial nerve supplies wrist extensors."
                        }
                      ]
                    }
                  ],
                  "coveredExpectedConceptIndexes": [0, 1],
                  "complete": true,
                  "limitations": []
                }
                """;

        String verificationJson = """
                {
                  "assertions": [
                    {
                      "assertionId": "a0",
                      "disposition": "SUPPORTED",
                      "reason": "verified against student response and source",
                      "fidelity": "FAITHFUL"
                    }
                  ],
                  "candidates": [
                    {
                      "candidateId": "c0",
                      "disposition": "SUPPORTED",
                      "reason": "student and source support constituent"
                    }
                  ],
                  "expectedConceptCoverage": [
                    {
                      "expectedConceptIndex": 0,
                      "coverage": "MISSING",
                      "reason": "full relation not independently stated"
                    },
                    {
                      "expectedConceptIndex": 1,
                      "coverage": "MISSING",
                      "reason": "full relation not independently stated"
                    }
                  ],
                  "misconceptions": [],
                  "complete": true,
                  "limitations": [],
                  "mechanismEvidence": {
                    "sourceReferenceId": "00000000-0000-0000-0000-000000000303",
                    "sourceVersion": "00000000-0000-0000-0000-000000000709",
                    "sourceStart": 0,
                    "sourceEnd": 42,
                    "sourceText": "The radial nerve supplies wrist extensors."
                  }
                }
                """;

        SyntheticRecordingAdapter mockAdapter = new SyntheticRecordingAdapter(
                sequence(discoveryJson, verificationJson));

        var liveProvider = new GoldenResponseAdapterLiveCollector.LiveProvider(
                ProviderId.GEMINI, "offline-test-model", mockAdapter, "test");
        var limits = GoldenResponseAdapterLiveCollector.CollectorLimits.defaultLimits();
        var rates = GoldenResponseAdapterLiveCollector.CostRates.defaults();

        var report = GoldenResponseAdapterLiveCollector.runCollection(
                liveProvider, limits, rates, "P7-09-PARTIAL-001");

        assertThat(report.datasetVersion()).isEqualTo("v7");
        assertThat(report.medicalAttestationStatus()).isEqualTo("PENDING");
        assertThat(report.medicalAttestationReferences())
                .contains(GoldenResponseAdapterLiveCollector.MEDICAL_REVIEWER_REF)
                .contains(GoldenResponseAdapterLiveCollector.MEDICAL_ATTESTATION_SIG_REF);
        assertThat(report.qualificationStatus())
                .isEqualTo(GoldenResponseAdapterLiveCollector.Status.PENDING);
        assertThat(report.cases()).hasSize(1);

        var c = report.cases().getFirst();
        assertThat(c.caseId()).isEqualTo("P7-09-PARTIAL-001");
        assertThat(c.discovery().executed()).isTrue();
        assertThat(c.discovery().valid()).isTrue();
        assertThat(c.discovery().output().assertions()).hasSize(1);

        assertThat(c.verification().executed()).isTrue();
        assertThat(c.verification().valid()).isTrue();
        assertThat(c.verification().output().expectedConceptCoverage()).hasSize(2);

        assertThat(c.finalEvaluation().outcome()).isEqualTo("PARTIAL");
        assertThat(c.finalEvaluation().validated()).isTrue();
        assertThat(c.finalEvaluation().correctConcepts()).containsExactly("wrist-extensor denervation");

        assertThat(report.totals().totalRequests()).isEqualTo(2);
        assertThat(mockAdapter.requests).hasSize(2);
    }

    @Test
    void enforcesMaxRequestsLimit() throws IOException {
        SyntheticRecordingAdapter mockAdapter = new SyntheticRecordingAdapter(sequence("{}", "{}"));
        var liveProvider = new GoldenResponseAdapterLiveCollector.LiveProvider(
                ProviderId.GEMINI, "offline-test-model", mockAdapter, "test");

        var limits = new GoldenResponseAdapterLiveCollector.CollectorLimits(
                1, 100_000L, 120L, new BigDecimal("1.00"), 0L);
        var rates = GoldenResponseAdapterLiveCollector.CostRates.defaults();

        var report = GoldenResponseAdapterLiveCollector.runCollection(
                liveProvider, limits, rates, "P7-09-PARTIAL-001");
        assertThat(report).isNotNull();
        assertThat(report.totals().totalRequests()).isEqualTo(1);
        assertThat(report.cases().getFirst().diagnosticMetadata()).containsKey("limitViolation");
        assertThat(mockAdapter.requests).hasSize(1);
    }

    @Test
    void rejectsInvalidConfigurationAndNormalizesProvider() {
        assertThat(GoldenResponseAdapterLiveCollector.normalizeProvider(" GEMINI ")).isEqualTo("gemini");
        assertThat(GoldenResponseAdapterLiveCollector.normalizeProvider("OLLAMA_CLOUD")).isEqualTo("ollama-cloud");
        assertThatThrownBy(() -> GoldenResponseAdapterLiveCollector.normalizeProvider("other"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GoldenResponseAdapterLiveCollector.CollectorLimits(
                0, 100, 10, BigDecimal.ONE, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GoldenResponseAdapterLiveCollector.CostRates(
                BigDecimal.ZERO, BigDecimal.ONE)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingUsageStopsBeforeAnotherDispatch() {
        SyntheticRecordingAdapter delegate = new SyntheticRecordingAdapter(request -> new ProviderExecutionResult(
                ProviderId.GEMINI, "offline-test-model", "{}", ProviderUsage.NONE, Duration.ZERO));
        var tracker = tracker(delegate, GoldenResponseAdapterLiveCollector.CollectorLimits.defaultLimits());
        var request = request(PromptId.RESPONSE_EVALUATION_DISCOVERY_V1);
        assertThatThrownBy(() -> tracker.execute(request)).hasMessageContaining("usage unavailable");
        assertThatThrownBy(() -> tracker.execute(request)).hasMessageContaining("collector stopped");
        assertThat(delegate.requests).hasSize(1);
    }

    @Test
    void preRequestBudgetRejectsWithoutDispatch() {
        SyntheticRecordingAdapter delegate = new SyntheticRecordingAdapter(sequence("{}"));
        var limits = new GoldenResponseAdapterLiveCollector.CollectorLimits(
                5, 50, 120, BigDecimal.ONE, 0);
        var tracker = tracker(delegate, limits);
        assertThatThrownBy(() -> tracker.execute(request(PromptId.RESPONSE_EVALUATION_DISCOVERY_V1)))
                .hasMessageContaining("pre-request token allowance");
        assertThat(delegate.requests).isEmpty();
    }

    @Test
    void failedRequestWithUnknownUsageStopsFurtherDispatch() {
        AtomicInteger count = new AtomicInteger();
        SyntheticRecordingAdapter delegate = new SyntheticRecordingAdapter(request -> {
            if (count.getAndIncrement() == 0) throw new IllegalStateException("first failed");
            return new ProviderExecutionResult(ProviderId.GEMINI, "offline-test-model", "{}",
                    ProviderUsage.of(10, 10, 20), Duration.ofMillis(2));
        });
        var tracker = tracker(delegate, GoldenResponseAdapterLiveCollector.CollectorLimits.defaultLimits());
        assertThatThrownBy(() -> tracker.execute(request(PromptId.RESPONSE_EVALUATION_DISCOVERY_V1)))
                .hasMessageContaining("first failed");
        assertThatThrownBy(() -> tracker.execute(request(PromptId.RESPONSE_EVALUATION_VERIFICATION_V1)))
                .hasMessageContaining("collector stopped");
        assertThat(tracker.usageUnknown()).isTrue();
        assertThat(tracker.findAttempt(PromptId.RESPONSE_EVALUATION_DISCOVERY_V1).orElseThrow().result()).isEmpty();
        assertThat(tracker.findAttempt(PromptId.RESPONSE_EVALUATION_DISCOVERY_V1).orElseThrow().failure()).isPresent();
        assertThat(tracker.findAttempt(PromptId.RESPONSE_EVALUATION_VERIFICATION_V1)).isEmpty();
        assertThat(delegate.requests).hasSize(1);
    }

    @Test
    void unknownUsageStopsCollectionAndLeavesTotalsUnknown() throws IOException {
        SyntheticRecordingAdapter delegate = new SyntheticRecordingAdapter(request -> {
            throw new IllegalStateException("provider failed without usage");
        });
        var liveProvider = new GoldenResponseAdapterLiveCollector.LiveProvider(
                ProviderId.GEMINI, "offline-test-model", delegate, "test");

        var report = GoldenResponseAdapterLiveCollector.runCollection(liveProvider,
                GoldenResponseAdapterLiveCollector.CollectorLimits.defaultLimits(),
                GoldenResponseAdapterLiveCollector.CostRates.defaults(), null);

        assertThat(report.cases()).hasSize(1);
        assertThat(delegate.requests).hasSize(1);
        assertThat(report.cases().getFirst().diagnosticMetadata())
                .containsEntry("usageUnknown", "true")
                .containsKey("limitViolation")
                .containsKey("request.0.failure");
        assertThat(report.totals().totalRequests()).isEqualTo(1);
        assertThat(report.totals().totalInputTokens()).isNull();
        assertThat(report.totals().totalOutputTokens()).isNull();
        assertThat(report.totals().totalTokens()).isNull();
        assertThat(report.totals().totalEstimatedCostUsd()).isNull();
        assertThat(report.evidenceCollectionStatus())
                .isEqualTo(GoldenResponseAdapterLiveCollector.Status.FAIL);
    }

    @Test
    void syntheticSourcesAreLimitedToCurrentCaseAndMaterial() throws IOException {
        var cases = new GoldenAiDatasetLoader().loadAll("v7").responseEvaluations();
        var current = cases.getFirst();
        var repository = new GoldenResponseAdapterLiveCollector.GoldenSourceReferenceRepository(current);
        UUID user = UUID.fromString("00000000-0000-0000-0000-000000000709");
        UUID material = UUID.fromString("00000000-0000-0000-0000-000000000710");
        UUID version = user;
        UUID chunk = UUID.fromString(current.sourceEvidence().getFirst().sourceId());
        assertThat(repository.findAuthorizedTarget(user, new ChunkSourceTarget(material, version, chunk))).isPresent();
        assertThat(repository.findAuthorizedTarget(UUID.randomUUID(), new ChunkSourceTarget(material, version, chunk))).isEmpty();
        assertThat(repository.findAuthorizedTarget(user, new ChunkSourceTarget(UUID.randomUUID(), version, chunk))).isEmpty();
        assertThat(repository.findAuthorizedTarget(user, new ChunkSourceTarget(material, version, UUID.randomUUID()))).isEmpty();
    }

    private static GoldenResponseAdapterLiveCollector.TrackingProviderAdapter tracker(
            AiProviderAdapter delegate, GoldenResponseAdapterLiveCollector.CollectorLimits limits) {
        return new GoldenResponseAdapterLiveCollector.TrackingProviderAdapter(
                delegate, new GoldenResponseAdapterLiveCollector.RequestPacer(0, ignored -> {}),
                "offline-test-model", limits, GoldenResponseAdapterLiveCollector.CostRates.defaults(),
                new AtomicInteger(), new java.util.concurrent.atomic.AtomicLong(),
                new java.util.concurrent.atomic.AtomicLong());
    }

    private static ProviderExecutionRequest request(PromptId promptId) {
        return new ProviderExecutionRequest(AiTaskType.RESPONSE_EVALUATION,
                AiOutputContract.RESPONSE_EVALUATION,
                new PromptContext(PromptId.HIPPOCAMPUS_SYSTEM_V1, promptId,
                        "system", "task", 10, 64, List.of()),
                new ProviderRoute.Target(ProviderId.GEMINI, "offline-test-model"));
    }

    private static Function<ProviderExecutionRequest, ProviderExecutionResult> sequence(
            String... outputs) {
        AtomicInteger index = new AtomicInteger();
        return request -> new ProviderExecutionResult(
                request.target().providerId(),
                request.target().modelId(),
                outputs[index.getAndIncrement() % outputs.length],
                ProviderUsage.of(500, 200, 700),
                Duration.ofMillis(10));
    }

    private static final class SyntheticRecordingAdapter implements AiProviderAdapter {
        private final Function<ProviderExecutionRequest, ProviderExecutionResult> responder;
        private final List<ProviderExecutionRequest> requests = new CopyOnWriteArrayList<>();

        SyntheticRecordingAdapter(Function<ProviderExecutionRequest, ProviderExecutionResult> responder) {
            this.responder = responder;
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
            return responder.apply(request);
        }

        @Override
        public ProviderEventStream stream(ProviderExecutionRequest request) {
            throw new UnsupportedOperationException();
        }
    }
}
