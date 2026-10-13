package com.hippocampus.ai.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hippocampus.ai.application.AiExecutionOrchestrator;
import com.hippocampus.ai.application.prompt.PromptTokenBudget;
import com.hippocampus.ai.application.routing.ProviderId;
import com.hippocampus.ai.application.routing.ProviderRoutingCandidate;
import com.hippocampus.ai.application.routing.ProviderRoutingPreference;
import com.hippocampus.ai.application.validation.ResponseAttributionValidator;
import com.hippocampus.ai.application.validation.VerifiedResponseEvaluationAggregator;
import com.hippocampus.ai.domain.AiTaskType;
import com.hippocampus.ai.domain.ResponseAttributionDiscovery;
import com.hippocampus.ai.domain.ResponseAttributionVerification;
import com.hippocampus.ai.domain.ResponseEvaluationInput;
import com.hippocampus.ai.domain.ValidatedAiResult;
import com.hippocampus.ai.infrastructure.learning.AiResponseEvaluationAdapter;
import com.hippocampus.ai.infrastructure.learning.AiTaskExecutionOptions;
import com.hippocampus.ai.infrastructure.learning.AiTaskExecutionPolicy;
import com.hippocampus.learning.domain.StudyMissionGroundingMode;
import com.hippocampus.learning.port.AuthorizedResponseEvidence;
import com.hippocampus.learning.port.ResponseEvaluationPort;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CapturedCase3AttributionReplayTests {
    private static final UUID MATERIAL = UUID.fromString("00000000-0000-0000-0000-000000000710");
    private static final UUID VERSION = UUID.fromString("00000000-0000-0000-0000-000000000709");

    record Replay(ResponseAttributionDiscovery discovery,
            ResponseAttributionVerification verification) {}

    @Test
    void capturedFaithfulSupportedOvercreditIsRejectedBeforeVerification() throws Exception {
        var golden = caseThree();
        Replay captured = captured();
        var input = input(golden);
        var evidence = evidence(golden);
        var validator = new ResponseAttributionValidator(new ResponseAttributionValidator.Limits(
                8_000, 8, 32_000, 32, 64, 128, 60_000));

        assertThat(captured.verification().assertions().getFirst().fidelity())
                .isEqualTo(ResponseAttributionVerification.Fidelity.FAITHFUL);
        assertThat(captured.verification().candidates().getFirst().disposition())
                .isEqualTo(ResponseAttributionVerification.Disposition.SUPPORTED);
        assertThat(new VerifiedResponseEvaluationAggregator().aggregate(
                input, captured.discovery(), captured.verification()).correctConcepts())
                .anyMatch(concept -> concept.contains("radial nerve"));
        assertThatThrownBy(() -> validator.validateDiscovery(input, evidence, captured.discovery()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("assertion imports context not independently stated by student");

        AiExecutionOrchestrator orchestrator = mock(AiExecutionOrchestrator.class);
        when(orchestrator.execute(any(), any(), any(), anyList(), any()))
                .thenReturn(completed(captured.discovery()));
        var result = adapter(orchestrator).evaluate(request(golden, evidence));
        assertThat(result.outcome()).isEqualTo(ResponseEvaluationPort.Outcome.UNCERTAIN);
        assertThat(result.correctConcepts()).isEmpty();
        verify(orchestrator, times(1)).execute(any(), any(), any(), anyList(), any());
    }

    @Test
    void referentialObjectAndMiddleReferenceCannotBorrowQuestionIdentity() throws Exception {
        var golden = caseThree();
        Replay captured = captured();
        assertThat(captured.verification().assertions().getFirst().fidelity())
                .isEqualTo(ResponseAttributionVerification.Fidelity.FAITHFUL);
        assertThat(captured.verification().candidates().getFirst().disposition())
                .isEqualTo(ResponseAttributionVerification.Disposition.SUPPORTED);

        for (String response : List.of(
                "Wrist extensors are denervated by it.",
                "Wrist extensors, because of it, are denervated.")) {
            var original = captured.discovery().assertions().getFirst();
            var assertion = new ResponseAttributionDiscovery.Assertion(original.id(), 0,
                    response.length(), response, original.proposition(), original.polarity(),
                    original.qualifiers(), original.relatedExpectedConceptIndexes());
            var discovery = new ResponseAttributionDiscovery(List.of(assertion),
                    captured.discovery().candidates(), List.of(0, 1), true, List.of());
            var input = new ResponseEvaluationInput(golden.question(), golden.expectedConcepts(),
                    golden.expectedAnswer(), response);
            assertThatThrownBy(() -> new ResponseAttributionValidator(
                    new ResponseAttributionValidator.Limits(8_000, 8, 32_000, 32, 64, 128, 60_000))
                            .validateDiscovery(input, evidence(golden), discovery))
                    .hasMessage("assertion imports context not independently stated by student");

            AiExecutionOrchestrator orchestrator = mock(AiExecutionOrchestrator.class);
            when(orchestrator.execute(any(), any(), any(), anyList(), any()))
                    .thenReturn(completed(discovery))
                    .thenReturn(completed(captured.verification()));
            var result = adapter(orchestrator).evaluate(new ResponseEvaluationPort.Request(
                    golden.question(), golden.expectedConcepts(), golden.expectedAnswer(),
                    response, evidence(golden)));
            assertThat(result.outcome()).isEqualTo(ResponseEvaluationPort.Outcome.UNCERTAIN);
            assertThat(result.correctConcepts()).isEmpty();
            verify(orchestrator, times(1)).execute(any(), any(), any(), anyList(), any());
        }
    }

    @Test
    void referentialObjectCannotSupportCompleteRelationEvenWithoutEnrichedMeaning() throws Exception {
        var golden = caseThree();
        Replay captured = captured();
        String response = "Wrist extensors are denervated by it.";
        var original = captured.discovery().assertions().getFirst();
        var assertion = new ResponseAttributionDiscovery.Assertion(original.id(), 0,
                response.length(), response, "Wrist extensors are denervated",
                original.polarity(), original.qualifiers(), original.relatedExpectedConceptIndexes());
        var candidate = captured.discovery().candidates().getFirst();
        var unexpanded = new ResponseAttributionDiscovery.Candidate(candidate.id(),
                candidate.assertionIds(), candidate.expectedConceptIndex(),
                "Wrist extensors are denervated", "COMPLETE", candidate.sourceEvidence());
        var discovery = new ResponseAttributionDiscovery(List.of(assertion),
                List.of(unexpanded), List.of(0, 1), true, List.of());
        var input = new ResponseEvaluationInput(golden.question(), golden.expectedConcepts(),
                golden.expectedAnswer(), response);

        assertThatThrownBy(() -> new ResponseAttributionValidator(
                new ResponseAttributionValidator.Limits(8_000, 8, 32_000, 32, 64, 128, 60_000))
                        .validateDiscovery(input, evidence(golden), discovery))
                .hasMessage("complete relation depends on a referential student assertion");
    }

    @Test
    void independentlyStatedConstituentCanStillReceivePartialCredit() throws Exception {
        var golden = caseThree();
        Replay captured = captured();
        var original = captured.discovery().assertions().getFirst();
        var assertion = new ResponseAttributionDiscovery.Assertion(
                original.id(), original.studentStart(), original.studentEnd(), original.studentText(),
                "wrist extensors are denervated", original.polarity(), original.qualifiers(),
                original.relatedExpectedConceptIndexes());
        var originalCandidate = captured.discovery().candidates().get(1);
        var candidate = new ResponseAttributionDiscovery.Candidate(
                originalCandidate.id(), originalCandidate.assertionIds(), 0,
                originalCandidate.constituentMeaning(), "CONSTITUENT",
                captured.discovery().candidates().getFirst().sourceEvidence());
        var discovery = new ResponseAttributionDiscovery(
                List.of(assertion), List.of(candidate), List.of(0, 1), true, List.of());
        var verification = new ResponseAttributionVerification(
                captured.verification().assertions(),
                List.of(captured.verification().candidates().get(1)),
                List.of(new ResponseAttributionVerification.ConceptCoverage(0,
                                ResponseAttributionVerification.Coverage.MISSING,
                                "radial nerve relationship not stated"),
                        captured.verification().expectedConceptCoverage().get(1)),
                List.of(), true, List.of(), captured.verification().mechanismEvidence());

        AiExecutionOrchestrator orchestrator = mock(AiExecutionOrchestrator.class);
        when(orchestrator.execute(any(), any(), any(), anyList(), any()))
                .thenReturn(completed(discovery))
                .thenReturn(completed(verification));
        var result = adapter(orchestrator).evaluate(request(golden, evidence(golden)));
        assertThat(result.outcome()).isEqualTo(ResponseEvaluationPort.Outcome.PARTIAL);
        assertThat(result.correctConcepts()).containsExactly("Wrist extensors are denervated");
        assertThat(result.missingConcepts()).containsExactlyElementsOf(golden.expectedConcepts());
        verify(orchestrator, times(2)).execute(any(), any(), any(), anyList(), any());
    }

    @Test
    void explicitlyNamedAnatomyIsNotRejectedSolelyForMatchingContext() throws Exception {
        var golden = caseThree();
        Replay captured = captured();
        String response = "Radial nerve injury denervates the wrist extensors.";
        var prior = captured.discovery().assertions().getFirst();
        var assertion = new ResponseAttributionDiscovery.Assertion(prior.id(), 0,
                response.length(), response, prior.proposition(), prior.polarity(),
                prior.qualifiers(), prior.relatedExpectedConceptIndexes());
        var discovery = new ResponseAttributionDiscovery(List.of(assertion),
                List.of(captured.discovery().candidates().getFirst()), List.of(0, 1), true, List.of());
        var verification = new ResponseAttributionVerification(
                captured.verification().assertions(),
                List.of(captured.verification().candidates().getFirst()),
                captured.verification().expectedConceptCoverage(), List.of(), true, List.of(),
                captured.verification().mechanismEvidence());
        AiExecutionOrchestrator orchestrator = mock(AiExecutionOrchestrator.class);
        when(orchestrator.execute(any(), any(), any(), anyList(), any()))
                .thenReturn(completed(discovery)).thenReturn(completed(verification));

        var result = adapter(orchestrator).evaluate(new ResponseEvaluationPort.Request(
                golden.question(), golden.expectedConcepts(), golden.expectedAnswer(),
                response, evidence(golden)));
        assertThat(result.outcome()).isEqualTo(ResponseEvaluationPort.Outcome.PARTIAL);
        assertThat(result.correctConcepts()).anyMatch(concept -> concept.contains("radial nerve"));
        verify(orchestrator, times(2)).execute(any(), any(), any(), anyList(), any());
    }

    @Test
    void singleWordContextImportAndWrongAnatomyRemainUngraded() throws Exception {
        var golden = caseThree();
        Replay captured = captured();
        var prior = captured.discovery().assertions().getFirst();
        var imported = new ResponseAttributionDiscovery.Assertion(prior.id(),
                prior.studentStart(), prior.studentEnd(), prior.studentText(),
                "Radial denervates the wrist extensors", prior.polarity(),
                prior.qualifiers(), prior.relatedExpectedConceptIndexes());
        var importedDiscovery = new ResponseAttributionDiscovery(List.of(imported),
                List.of(captured.discovery().candidates().get(1)), List.of(0, 1), true, List.of());
        assertThatThrownBy(() -> new ResponseAttributionValidator(
                new ResponseAttributionValidator.Limits(8_000, 8, 32_000, 32, 64, 128, 60_000))
                        .validateDiscovery(input(golden), evidence(golden), importedDiscovery))
                .hasMessage("assertion imports context not independently stated by student");

        var wrong = new ResponseAttributionDiscovery.Assertion(prior.id(),
                prior.studentStart(), prior.studentEnd(), prior.studentText(),
                "The ulnar nerve denervates the wrist extensors", prior.polarity(),
                prior.qualifiers(), prior.relatedExpectedConceptIndexes());
        var wrongDiscovery = new ResponseAttributionDiscovery(List.of(wrong),
                List.of(captured.discovery().candidates().getFirst()), List.of(0, 1), true, List.of());
        AiExecutionOrchestrator orchestrator = mock(AiExecutionOrchestrator.class);
        when(orchestrator.execute(any(), any(), any(), anyList(), any()))
                .thenReturn(completed(wrongDiscovery));
        var result = adapter(orchestrator).evaluate(request(golden, evidence(golden)));
        assertThat(result.outcome()).isEqualTo(ResponseEvaluationPort.Outcome.UNCERTAIN);
        assertThat(result.correctConcepts()).isEmpty();
        verify(orchestrator, times(1)).execute(any(), any(), any(), anyList(), any());
    }

    private static GoldenAiDataset.ResponseEvaluationCase caseThree() {
        return new GoldenAiDatasetLoader().loadAll("v7").responseEvaluations().stream()
                .filter(c -> c.caseId().equals("P7-09-PARTIAL-001")).findFirst().orElseThrow();
    }

    private static Replay captured() throws Exception {
        try (var stream = CapturedCase3AttributionReplayTests.class.getClassLoader()
                .getResourceAsStream("ai/golden/replays/p7-09-case3-f76b4c9f.json")) {
            if (stream == null) throw new IllegalStateException("captured Case 3 replay is missing");
            return new ObjectMapper().readValue(stream, Replay.class);
        }
    }

    private static ResponseEvaluationInput input(GoldenAiDataset.ResponseEvaluationCase golden) {
        return new ResponseEvaluationInput(golden.question(), golden.expectedConcepts(),
                golden.expectedAnswer(), golden.studentResponse());
    }

    private static AuthorizedResponseEvidence evidence(GoldenAiDataset.ResponseEvaluationCase golden) {
        return new AuthorizedResponseEvidence(StudyMissionGroundingMode.STRICT_SOURCE,
                golden.sourceEvidence().stream().map(source -> {
                    UUID id = UUID.fromString(source.sourceId());
                    return new AuthorizedResponseEvidence.Source(id, id, MATERIAL, VERSION,
                            null, 1, source.content(), null, null, "TEXT", "NATIVE", "STRONG");
                }).toList());
    }

    private static ResponseEvaluationPort.Request request(
            GoldenAiDataset.ResponseEvaluationCase golden, AuthorizedResponseEvidence evidence) {
        return new ResponseEvaluationPort.Request(golden.question(), golden.expectedConcepts(),
                golden.expectedAnswer(), golden.studentResponse(), evidence);
    }

    private static AiResponseEvaluationAdapter adapter(AiExecutionOrchestrator orchestrator) {
        AiTaskExecutionPolicy policy = mock(AiTaskExecutionPolicy.class);
        var candidate = new ProviderRoutingCandidate(ProviderId.GEMINI, "offline-replay",
                Set.of(AiTaskType.RESPONSE_EVALUATION), Set.of(AiTaskType.RESPONSE_EVALUATION),
                true, true, true, 0, 0, 0);
        when(policy.optionsFor(AiTaskType.RESPONSE_EVALUATION)).thenReturn(
                new AiTaskExecutionOptions(new PromptTokenBudget(131_072, 2_048),
                        List.of(candidate), ProviderRoutingPreference.COST_THEN_LATENCY));
        return new AiResponseEvaluationAdapter(orchestrator, policy);
    }

    private static CompletableFuture<ValidatedAiResult<?>> completed(Object value) {
        return CompletableFuture.completedFuture(new ValidatedAiResult<>(value));
    }
}
