package com.hippocampus.ai.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hippocampus.ai.application.provider.AiProviderAdapter;
import com.hippocampus.ai.application.provider.ProviderEventStream;
import com.hippocampus.ai.application.provider.ProviderExecutionRequest;
import com.hippocampus.ai.application.provider.ProviderExecutionResult;
import com.hippocampus.ai.application.provider.ProviderUsage;
import com.hippocampus.ai.application.routing.ProviderId;
import com.hippocampus.ai.domain.AiTaskType;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

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
    void qualificationIdentityHasCompleteCryptographicInputs() throws IOException {
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
    void enforcesMaxRequestsLimit() {
        SyntheticRecordingAdapter mockAdapter = new SyntheticRecordingAdapter(sequence("{}", "{}"));
        var liveProvider = new GoldenResponseAdapterLiveCollector.LiveProvider(
                ProviderId.GEMINI, "offline-test-model", mockAdapter, "test");

        var limits = new GoldenResponseAdapterLiveCollector.CollectorLimits(
                1, 100_000L, 120L, new BigDecimal("1.00"), 0L);
        var rates = GoldenResponseAdapterLiveCollector.CostRates.defaults();

        assertThatThrownBy(() -> GoldenResponseAdapterLiveCollector.runCollection(
                liveProvider, limits, rates, "P7-09-PARTIAL-001"))
                .isInstanceOf(Exception.class);
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
