package com.hippocampus.ai.infrastructure.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;

import com.hippocampus.ai.application.AiExecutionOrchestrator;
import com.hippocampus.ai.application.prompt.PromptId;
import com.hippocampus.ai.application.prompt.PromptTokenBudget;
import com.hippocampus.ai.application.routing.ProviderId;
import com.hippocampus.ai.application.routing.ProviderRoutingCandidate;
import com.hippocampus.ai.application.routing.ProviderRoutingPreference;
import com.hippocampus.ai.domain.AiTaskType;
import com.hippocampus.ai.domain.ResponseAttributionDiscovery;
import com.hippocampus.ai.domain.ResponseAttributionVerification;
import com.hippocampus.ai.domain.ValidatedAiResult;
import com.hippocampus.learning.domain.StudyMissionGroundingMode;
import com.hippocampus.learning.port.AuthorizedResponseEvidence;
import com.hippocampus.learning.port.ResponseEvaluationPort;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiResponseEvaluationAdapterTests {

    private AiExecutionOrchestrator orchestrator;
    private AiTaskExecutionPolicy executionPolicy;
    private AiResponseEvaluationAdapter adapter;

    @BeforeEach
    void setUp() {
        orchestrator = mock(AiExecutionOrchestrator.class);
        executionPolicy = mock(AiTaskExecutionPolicy.class);
        ProviderRoutingCandidate candidate = new ProviderRoutingCandidate(
                ProviderId.GEMINI,
                "test-model",
                Set.of(AiTaskType.RESPONSE_EVALUATION),
                Set.of(AiTaskType.RESPONSE_EVALUATION),
                true,
                true,
                true,
                0,
                0,
                0);
        AiTaskExecutionOptions options = new AiTaskExecutionOptions(
                new PromptTokenBudget(1000, 100),
                List.of(candidate),
                ProviderRoutingPreference.COST_THEN_LATENCY);
        when(executionPolicy.optionsFor(AiTaskType.RESPONSE_EVALUATION)).thenReturn(options);
        adapter = new AiResponseEvaluationAdapter(orchestrator, executionPolicy);
    }

    @Test
    void emptyEvidenceRemainsUngradedWithoutCallingProvider() {
        assertThat(new com.hippocampus.ai.application.prompt.PromptTemplateRegistry()
                .resolveTask(AiTaskType.RESPONSE_EVALUATION, PromptId.RESPONSE_EVALUATION_V8.name()).promptId())
                .isEqualTo(PromptId.RESPONSE_EVALUATION_V8);
        assertThat(new com.hippocampus.ai.application.prompt.PromptTemplateRegistry()
                .resolveTask(AiTaskType.RESPONSE_EVALUATION, PromptId.RESPONSE_EVALUATION_V9.name()).promptId())
                .isEqualTo(PromptId.RESPONSE_EVALUATION_V9);
        ResponseEvaluationPort.Request request = new ResponseEvaluationPort.Request(
                "Why is AV node delay beneficial?",
                List.of("Allows ventricular filling"),
                "It allows ventricular filling before systole.",
                "It delays conduction so the ventricles fill.");

        ResponseEvaluationPort.Result result = adapter.evaluate(request);

        assertThat(result.outcome()).isEqualTo(ResponseEvaluationPort.Outcome.UNCERTAIN);
        org.mockito.Mockito.verifyNoInteractions(orchestrator);
    }

    @Test
    void nonChunkReferenceCannotGradeFromRemainingChunk() {
        var evidence = new AuthorizedResponseEvidence(StudyMissionGroundingMode.STRICT_SOURCE,
                List.of(new AuthorizedResponseEvidence.Source(UUID.randomUUID(),
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        null, 1, "Radial nerve injury paralyzes wrist extensors.",
                        null, null, "TEXT", "NATIVE", "STRONG")),
                AuthorizedResponseEvidence.Limitation.NON_CHUNK_REFERENCE_UNSUPPORTED);

        var result = adapter.evaluate(new ResponseEvaluationPort.Request(
                "Explain wrist drop", List.of("Radial nerve injury causes wrist drop"),
                "Radial nerve injury causes wrist drop", "The wrist extensors are paralyzed.",
                evidence));

        assertThat(result.outcome()).isEqualTo(ResponseEvaluationPort.Outcome.UNCERTAIN);
        org.mockito.Mockito.verifyNoInteractions(orchestrator);
    }

    @Test
    void zeroCreditInitialPassCanRecoverVerifiedIndependentConstituent() {
        UUID reference = UUID.randomUUID();
        UUID version = UUID.randomUUID();
        String source = "Radial nerve injury can paralyze wrist extensors.";
        String response = "The wrist extensors are paralyzed, but the median nerve is responsible.";
        int sourceStart = source.indexOf("paralyze");
        int wrongStart = response.indexOf("the median nerve");
        var discovery = new ResponseAttributionDiscovery(
                List.of(new ResponseAttributionDiscovery.Assertion("a", 0, response.indexOf(','),
                                response.substring(0, response.indexOf(',')),
                                "wrist extensor paralysis", "AFFIRMED", List.of(), List.of(0)),
                        new ResponseAttributionDiscovery.Assertion("b", wrongStart,
                                response.length() - 1,
                                response.substring(wrongStart, response.length() - 1),
                                "median nerve attribution", "AFFIRMED", List.of(), List.of(0))),
                List.of(new ResponseAttributionDiscovery.Candidate("c", List.of("a"), 0,
                        "wrist extensor paralysis", "CONSTITUENT",
                        List.of(new ResponseAttributionDiscovery.SourceSpan(
                                reference.toString(), version.toString(), sourceStart,
                                sourceStart + "paralyze".length(), "paralyze")))),
                List.of(0), true, List.of());
        var verification = new ResponseAttributionVerification(
                List.of(new ResponseAttributionVerification.AssertionDisposition("a",
                                ResponseAttributionVerification.Disposition.SUPPORTED, "stated", ResponseAttributionVerification.Fidelity.FAITHFUL),
                        new ResponseAttributionVerification.AssertionDisposition("b",
                                ResponseAttributionVerification.Disposition.UNSUPPORTED,
                                "wrong nerve attribution", ResponseAttributionVerification.Fidelity.FAITHFUL)),
                List.of(new ResponseAttributionVerification.CandidateDisposition("c",
                        ResponseAttributionVerification.Disposition.SUPPORTED, "source backed")),
                List.of(new ResponseAttributionVerification.ConceptCoverage(0,
                        ResponseAttributionVerification.Coverage.MISSING,
                        "correct nerve attribution absent")),
                List.of(new ResponseAttributionVerification.Misconception("b",
                        "median nerve attribution")), true, List.of(),
                new ResponseAttributionDiscovery.SourceSpan(reference.toString(),
                        version.toString(), 0, source.length(), source));
        when(orchestrator.execute(any(), any(), any(), anyList(), any()))
                .thenReturn(CompletableFuture.completedFuture(validated(discovery)))
                .thenReturn(CompletableFuture.completedFuture(validated(verification)));
        var evidence = new AuthorizedResponseEvidence(StudyMissionGroundingMode.STRICT_SOURCE,
                List.of(new AuthorizedResponseEvidence.Source(reference, UUID.randomUUID(),
                        UUID.randomUUID(), version, null, 1, source, null, null,
                        "TEXT", "NATIVE", "STRONG")));

        var result = adapter.evaluate(new ResponseEvaluationPort.Request(
                "Explain wrist drop", List.of("Radial nerve injury causes wrist extensor paralysis"),
                "Radial nerve injury causes wrist extensor paralysis", response, evidence));

        assertThat(result.outcome()).isEqualTo(ResponseEvaluationPort.Outcome.PARTIAL);
        assertThat(result.correctConcepts()).containsExactly("wrist extensor paralysis");
        assertThat(result.missingConcepts())
                .containsExactly("Radial nerve injury causes wrist extensor paralysis");
        assertThat(result.feedback()).contains("wrist extensor paralysis")
                .contains("Radial nerve injury causes wrist extensor paralysis")
                .contains("median nerve attribution is incorrect")
                .contains(source);
        org.mockito.Mockito.verify(orchestrator, org.mockito.Mockito.times(2))
                .execute(any(), any(), any(), anyList(), any());
    }

    @Test
    void omittedContradictoryResponseContentResolvesToUncertaintyBeforeVerification() {
        UUID reference = UUID.randomUUID();
        UUID version = UUID.randomUUID();
        String source = "Radial nerve injury can paralyze wrist extensors.";
        String response = "Radial nerve injury paralyzes wrist extensors, but another nerve causes it.";
        int firstEnd = response.indexOf(',');
        var incomplete = new ResponseAttributionDiscovery(
                List.of(new ResponseAttributionDiscovery.Assertion("a", 0, firstEnd,
                        response.substring(0, firstEnd), "wrist extensor paralysis",
                        "AFFIRMED", List.of(), List.of(0))),
                List.of(), List.of(0), true, List.of());
        when(orchestrator.execute(any(), any(), any(), anyList(), any()))
                .thenReturn(CompletableFuture.completedFuture(validated(incomplete)));
        var evidence = new AuthorizedResponseEvidence(StudyMissionGroundingMode.STRICT_SOURCE,
                List.of(new AuthorizedResponseEvidence.Source(reference, UUID.randomUUID(),
                        UUID.randomUUID(), version, null, 1, source, null, null,
                        "TEXT", "NATIVE", "STRONG")));

        var result = adapter.evaluate(new ResponseEvaluationPort.Request(
                "Explain wrist drop", List.of("Radial nerve injury causes wrist extensor paralysis"),
                "Radial nerve injury causes wrist extensor paralysis", response, evidence));

        assertThat(result.outcome()).isEqualTo(ResponseEvaluationPort.Outcome.UNCERTAIN);
        assertThat(result.correctConcepts()).isEmpty();
        org.mockito.Mockito.verify(orchestrator, org.mockito.Mockito.times(1))
                .execute(any(), any(), any(), anyList(), any());
    }

    @Test
    void overbroadFullResponseAssertionCannotBecomeCorrect() {
        UUID reference = UUID.randomUUID();
        UUID version = UUID.randomUUID();
        String source = "Radial nerve injury can paralyze wrist extensors.";
        String response = "Radial nerve injury paralyzes wrist extensors, and the median nerve is responsible.";
        var discovery = new ResponseAttributionDiscovery(
                List.of(new ResponseAttributionDiscovery.Assertion("a", 0, response.length(),
                        response, "radial nerve injury causes wrist extensor paralysis",
                        "AFFIRMED", List.of(), List.of(0))),
                List.of(new ResponseAttributionDiscovery.Candidate("c", List.of("a"), 0,
                        "radial nerve injury causes wrist extensor paralysis", "COMPLETE",
                        List.of(new ResponseAttributionDiscovery.SourceSpan(
                                reference.toString(), version.toString(), 0,
                                source.length(), source)))),
                List.of(0), true, List.of());
        var falselyCompleteVerification = new ResponseAttributionVerification(
                List.of(new ResponseAttributionVerification.AssertionDisposition("a",
                        ResponseAttributionVerification.Disposition.SUPPORTED,
                        "incorrectly treats both clauses as one", ResponseAttributionVerification.Fidelity.FAITHFUL)),
                List.of(new ResponseAttributionVerification.CandidateDisposition("c",
                        ResponseAttributionVerification.Disposition.SUPPORTED, "source backed")),
                List.of(new ResponseAttributionVerification.ConceptCoverage(0,
                        ResponseAttributionVerification.Coverage.COMPLETE, "full relation")),
                List.of(), true, List.of(),
                new ResponseAttributionDiscovery.SourceSpan(reference.toString(),
                        version.toString(), 0, source.length(), source));
        when(orchestrator.execute(any(), any(), any(), anyList(), any()))
                .thenReturn(CompletableFuture.completedFuture(validated(discovery)))
                .thenReturn(CompletableFuture.completedFuture(validated(falselyCompleteVerification)));
        var evidence = new AuthorizedResponseEvidence(StudyMissionGroundingMode.STRICT_SOURCE,
                List.of(new AuthorizedResponseEvidence.Source(reference, UUID.randomUUID(),
                        UUID.randomUUID(), version, null, 1, source, null, null,
                        "TEXT", "NATIVE", "STRONG")));

        var result = adapter.evaluate(new ResponseEvaluationPort.Request(
                "Explain wrist drop", List.of("Radial nerve injury causes wrist extensor paralysis"),
                "Radial nerve injury causes wrist extensor paralysis", response, evidence));

        assertThat(result.outcome()).isEqualTo(ResponseEvaluationPort.Outcome.UNCERTAIN);
        assertThat(result.correctConcepts()).isEmpty();
        org.mockito.Mockito.verify(orchestrator, org.mockito.Mockito.times(1))
                .execute(any(), any(), any(), anyList(), any());
    }

    @Test
    void discoveryProviderFailureResolvesToSafeUncertainty() {
        when(orchestrator.execute(any(), any(), any(), anyList(), any()))
                .thenReturn(CompletableFuture.failedFuture(
                        new IllegalStateException("private provider failure")));
        var evidence = new AuthorizedResponseEvidence(StudyMissionGroundingMode.STRICT_SOURCE,
                List.of(new AuthorizedResponseEvidence.Source(UUID.randomUUID(),
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        null, 1, "Radial nerve injury paralyzes wrist extensors.",
                        null, null, "TEXT", "NATIVE", "STRONG")));

        var result = adapter.evaluate(new ResponseEvaluationPort.Request(
                "Explain wrist drop", List.of("Radial nerve injury causes wrist drop"),
                "Radial nerve injury causes wrist drop", "The wrist extensors are paralyzed.",
                evidence));

        assertThat(result.outcome()).isEqualTo(ResponseEvaluationPort.Outcome.UNCERTAIN);
        assertThat(result.correctConcepts()).isEmpty();
        assertThat(result.feedback()).doesNotContain("private provider failure");
        org.mockito.Mockito.verify(orchestrator, org.mockito.Mockito.times(1))
                .execute(any(), any(), any(), anyList(), any());
    }

    private static ValidatedAiResult<?> validated(Object result) {
        return new ValidatedAiResult<>(result,
                new ValidatedAiResult.ExecutionMetadata(
                        "GEMINI", "test-model", "test-version", "test-prompt", "1"));
    }
}
