package com.hippocampus.ai.evaluation;

import static org.assertj.core.api.Assertions.*;
import com.hippocampus.ai.application.provider.ProviderUsage;
import com.hippocampus.ai.application.validation.VerifiedResponseEvaluationAggregator;
import com.hippocampus.ai.domain.*;
import com.hippocampus.learning.port.ResponseEvaluationPort;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;

class P709ObservationCollectorTests {
    static final P709ObservationCollector.Budget BUDGET = new P709ObservationCollector.Budget(4, 1_000_000, 30_000, 1_000_000, 1);
    static P709ObservationIdentity.Snapshot identity;
    static Path root, evidence;
    @BeforeAll static void identityBefore() throws Exception {
        root = Path.of("").toAbsolutePath().getParent();
        evidence = Files.createDirectories(Path.of("target/p709-offline-evidence"));
        identity = P709ObservationIdentity.capture(root, Map.of("mode", "OFFLINE", "provider", "SCRIPTED_FAKE", "model", P709ObservationCollector.MODEL,
                "routing", "NO_FALLBACK_ONE_ATTEMPT", "budget", BUDGET.toString(), "cases", "EXACT_NINE_V2"));
        Files.write(evidence.resolve("identity-before-" + UUID.randomUUID() + ".json"), P709ObservationCollector.JSON.writeValueAsBytes(identity), StandardOpenOption.CREATE_NEW);
    }
    @AfterAll static void identityAfter() throws Exception {
        var after = P709ObservationIdentity.capture(root, Map.of("mode", "OFFLINE", "provider", "SCRIPTED_FAKE", "model", P709ObservationCollector.MODEL,
                "routing", "NO_FALLBACK_ONE_ATTEMPT", "budget", BUDGET.toString(), "cases", "EXACT_NINE_V2"));
        Files.write(evidence.resolve("identity-after-" + UUID.randomUUID() + ".json"), P709ObservationCollector.JSON.writeValueAsBytes(after), StandardOpenOption.CREATE_NEW);
        P709ObservationIdentity.requireSame(identity, after);
    }
    static List<P709ObservationCollector.Emission> script(P709ObservationCollector.Input input) throws Exception {
        var indexes = java.util.stream.IntStream.range(0, input.expectedConcepts().size()).boxed().toList();
        var assertions = new ArrayList<ResponseAttributionDiscovery.Assertion>();
        var words = java.util.regex.Pattern.compile("[\\p{L}\\p{N}]+").matcher(input.studentResponse());
        while (words.find()) assertions.add(new ResponseAttributionDiscovery.Assertion("a" + assertions.size(), words.start(),
                words.end(), words.group(), words.group(), "UNCERTAIN", List.of(), indexes));
        var d = new ResponseAttributionDiscovery(assertions, List.of(), indexes, true, List.of("synthetic structural observation only"));
        var s = P709ObservationCollector.syntheticEvidence(input).sources().getFirst();
        var v = new ResponseAttributionVerification(assertions.stream().map(a -> new ResponseAttributionVerification.AssertionDisposition(
                a.id(), ResponseAttributionVerification.Disposition.AMBIGUOUS, "synthetic observation", ResponseAttributionVerification.Fidelity.FAITHFUL)).toList(),
                List.of(), indexes.stream().map(i -> new ResponseAttributionVerification.ConceptCoverage(i,
                        ResponseAttributionVerification.Coverage.AMBIGUOUS, "requires independent medical review")).toList(),
                List.of(), true, List.of("not a medical classification"), new ResponseAttributionDiscovery.SourceSpan(s.sourceReferenceId().toString(),
                        s.materialVersionId().toString(), 1, s.content().length() + 1, s.content()));
        return List.of(emission(P709ObservationCollector.JSON.writeValueAsString(d)), emission(P709ObservationCollector.JSON.writeValueAsString(v)));
    }
    static P709ObservationCollector.Emission emission(String raw) {
        return new P709ObservationCollector.Emission(raw, "actually-emitted-fake-model", ProviderUsage.of(10, 10, 20), false);
    }
    static P709ObservationCollector.Report collect(P709ObservationCollector.Input input, List<P709ObservationCollector.Emission> script) throws Exception {
        return P709ObservationCollector.offline(evidence, UUID.randomUUID(), input, BUDGET, script, identity);
    }
    @Test void collectsExactNineThroughRealContainedGraphWithSeparatePendingMedicalStatuses() throws Exception {
        var inputs = P709ObservationCollector.inputs();
        assertThat(inputs).hasSize(9);
        assertThat(inputs.get(7).studentResponse()).isEmpty();
        for (var input : inputs) {
            var report = collect(input, script(input));
            assertThat(report.evidenceCollectionStatus()).as(input.caseId()).isEqualTo("PASS");
            assertThat(report.contractStatus()).isEqualTo("PASS");
            assertThat(report.structuralStatus()).isEqualTo("PASS");
            assertThat(report.semanticReviewStatus()).isEqualTo("PENDING");
            assertThat(report.qualificationStatus()).isEqualTo("PENDING");
            assertThat(report.containedApplicationOutcome()).isEqualTo(VerifiedResponseEvaluationAggregator.uncertain());
            assertThat(report.dispatches()).hasSize(2);
            assertThat(report.dispatches().getFirst().configuredModel()).isEqualTo(P709ObservationCollector.MODEL);
            assertThat(report.dispatches().getFirst().providerEmittedModel()).isEqualTo("actually-emitted-fake-model");
            assertThat(report.normalizationReplay()).isNotNull();
        }
    }
    @Test void countsRepairInsideSameEnvelopeAndRetainsMalformedOriginal() throws Exception {
        var input = P709ObservationCollector.inputs().getFirst();
        var scripts = new ArrayList<>(script(input)); scripts.addFirst(emission("malformed JSON"));
        var report = collect(input, scripts);
        assertThat(report.evidenceCollectionStatus()).isEqualTo("PASS");
        assertThat(report.dispatches()).hasSize(3);
        assertThat(report.dispatches().get(1).task()).isEqualTo("STRUCTURED_OUTPUT_REPAIR");
        assertThat(report.dispatches().getFirst().rawContent()).isEqualTo("malformed JSON");
        var capped = P709ObservationCollector.offline(evidence, UUID.randomUUID(), input,
                new P709ObservationCollector.Budget(2, 1_000_000, 30_000, 1_000_000, 1), scripts, identity);
        assertThat(capped.evidenceCollectionStatus()).isEqualTo("FAIL");
        assertThat(capped.failureCode()).isEqualTo("BUDGET_EXHAUSTED");
    }
    @Test void providerFailureUnknownUsageMissingTraceAndUnsuccessfulRepairCannotBecomePass() throws Exception {
        var input = P709ObservationCollector.inputs().getFirst();
        for (var scripts : List.of(
                List.of(new P709ObservationCollector.Emission(null, null, null, true)),
                List.of(new P709ObservationCollector.Emission("{}", "fake", ProviderUsage.NONE, false)),
                List.of(script(input).getFirst()),
                List.of(emission("broken"), emission("still broken")))) {
            var report = collect(input, scripts);
            assertThat(report.evidenceCollectionStatus()).isEqualTo("FAIL");
            assertThat(report.qualificationStatus()).isEqualTo("PENDING");
            assertThat(report.containedApplicationOutcome()).isEqualTo(VerifiedResponseEvaluationAggregator.uncertain());
        }
    }
    @Test void rejectsForgedSourceClaimsAndPreservesActualEmission() throws Exception {
        var input = P709ObservationCollector.inputs().getFirst();
        var scripts = new ArrayList<>(script(input));
        var actualId = input.sources().getFirst().fixtureId().toString();
        var forged = UUID.randomUUID().toString();
        scripts.set(1, emission(scripts.get(1).rawContent().replace(actualId, forged)));
        var report = collect(input, scripts);
        assertThat(report.evidenceCollectionStatus()).isEqualTo("FAIL");
        assertThat(report.dispatches().getLast().rawContent()).contains(forged);
        assertThat(report.expectedFixtureReferences()).contains(input.sources().getFirst().fixtureId());
    }
    @Test void secretPayloadAndProviderExceptionPayloadAreNeverPersisted() throws Exception {
        var input = P709ObservationCollector.inputs().getFirst();
        var report = collect(input, List.of(emission("{\"authorization\":\"Bearer sentinel-secret-709\"}")));
        String retained = P709ObservationCollector.JSON.writeValueAsString(report);
        assertThat(retained).doesNotContain("sentinel-secret-709");
        assertThat(report.evidenceCollectionStatus()).isEqualTo("FAIL");
        assertThat(report.dispatches().getFirst().rawContent()).isNull();
    }
    @Test void rejectsUnapprovedInputsMissingDuplicatesAndOutputOverwrite() throws Exception {
        var inputs = P709ObservationCollector.inputs(); var ids = inputs.stream().map(P709ObservationCollector.Input::caseId).toList();
        assertThatThrownBy(() -> P709ObservationCollector.validateInputs(inputs.subList(0,8), ids)).isInstanceOf(IllegalArgumentException.class);
        var duplicates = new ArrayList<>(inputs); duplicates.set(8, inputs.getFirst());
        assertThatThrownBy(() -> P709ObservationCollector.validateInputs(duplicates, ids)).isInstanceOf(IllegalArgumentException.class);
        var unknown = new P709ObservationCollector.Input("unapproved", "question", List.of("concept"), "answer", "", inputs.getFirst().sources());
        assertThatThrownBy(() -> collect(unknown, List.of())).isInstanceOf(IllegalArgumentException.class);
        UUID run = UUID.randomUUID();
        P709ObservationCollector.offline(evidence, run, inputs.getFirst(), BUDGET, script(inputs.getFirst()), identity);
        assertThatThrownBy(() -> P709ObservationCollector.offline(evidence, run, inputs.getFirst(), BUDGET, List.of(), identity))
                .isInstanceOf(FileAlreadyExistsException.class);
    }
    @Test void unauthorizedLiveDispatchIsImpossibleEvenWithEnvironmentOptInAndForgedApproval() {
        assertThatThrownBy(() -> P709ObservationLiveRunner.requireAuthorizedProfile(Map.of(), null)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> P709ObservationLiveRunner.requireAuthorizedProfile(Map.of("HIPPOCAMPUS_LIVE_AI_GOLDEN_ADAPTER", "true"), "forged-owner-approval"))
                .isInstanceOf(SecurityException.class).hasMessageContaining("no owner-approved");
    }
    @Test void rejectsAccidentalScoringAuthorityAndInvalidBudgets() {
        var score = new ResponseEvaluationPort.Result(ResponseEvaluationPort.Outcome.CORRECT, List.of("claim"), List.of(), List.of(), "feedback",
                ResponseEvaluationPort.Certainty.SUFFICIENT, ResponseEvaluationPort.RecommendedAction.CONTINUE, true);
        assertThatThrownBy(() -> P709ObservationCollector.assertContained(score)).isInstanceOf(P709ObservationCollector.CollectionFailure.class);
        assertThatThrownBy(() -> new P709ObservationCollector.Budget(0, 1, 1, 1, 1)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void missingNullAndNonTextResponsesAreNotCanonicalEmpty() throws Exception {
        var ids = P709ObservationCollector.inputs().stream().map(P709ObservationCollector.Input::caseId).toList();
        var tree = P709ObservationCollector.JSON.readTree(P709ObservationCollector.resource(P709ObservationCollector.RESOURCE));
        var item = (com.fasterxml.jackson.databind.node.ObjectNode) tree.path("cases").get(7);
        item.remove("studentResponse");
        assertThatThrownBy(() -> P709ObservationCollector.parseInputs(P709ObservationCollector.JSON.writeValueAsBytes(tree), ids)).isInstanceOf(IllegalArgumentException.class);
        item.putNull("studentResponse");
        assertThatThrownBy(() -> P709ObservationCollector.parseInputs(P709ObservationCollector.JSON.writeValueAsBytes(tree), ids)).isInstanceOf(IllegalArgumentException.class);
        item.put("studentResponse", 123);
        assertThatThrownBy(() -> P709ObservationCollector.parseInputs(P709ObservationCollector.JSON.writeValueAsBytes(tree), ids)).isInstanceOf(IllegalArgumentException.class);
        item.put("studentResponse", "");
        assertThat(P709ObservationCollector.parseInputs(P709ObservationCollector.JSON.writeValueAsBytes(tree), ids).get(7).studentResponse()).isEmpty();
    }
    @Test void incompletePassesAndFailedRepairHaveDistinctStates() throws Exception {
        var input = P709ObservationCollector.inputs().getFirst();
        var incomplete = new ArrayList<>(script(input));
        incomplete.set(0, emission(incomplete.getFirst().rawContent().replace("\"complete\":true", "\"complete\":false")));
        assertThat(collect(input, incomplete).failureCode()).isEqualTo("INCOMPLETE_DISCOVERY");
        incomplete = new ArrayList<>(script(input));
        incomplete.set(1, emission(incomplete.get(1).rawContent().replace("\"complete\":true", "\"complete\":false")));
        assertThat(collect(input, incomplete).failureCode()).isEqualTo("INCOMPLETE_VERIFICATION");
        var failedRepair = collect(input, List.of(emission("broken"), emission("still broken")));
        assertThat(failedRepair.failureCode()).isEqualTo("REPAIR_FAILED");
        assertThat(failedRepair.executionDiagnostics()).anyMatch(d -> d.status().equals("FAILED"));
        assertThat(failedRepair.passObservations()).anyMatch(d -> d.status().equals("SCHEMA_FAIL"));
    }
    @Test void adapterNoDispatchSourceAndLimitPathsStayContained() {
        var orchestrator = org.mockito.Mockito.mock(com.hippocampus.ai.application.AiExecutionOrchestrator.class);
        var adapter = new com.hippocampus.ai.infrastructure.learning.AiResponseEvaluationAdapter(orchestrator,
                new com.hippocampus.ai.infrastructure.learning.AiTaskExecutionPolicy(Map.of()));
        assertThat(adapter.evaluate(new ResponseEvaluationPort.Request("q", List.of("c"), "a", "r")))
                .isEqualTo(VerifiedResponseEvaluationAggregator.uncertain());
        var source = new P709ObservationCollector.Source(UUID.randomUUID(), "synthetic source");
        var input = new P709ObservationCollector.Input("test-no-dispatch", "q", List.of("c"), "a", "x".repeat(8001), List.of(source));
        assertThat(adapter.evaluate(new ResponseEvaluationPort.Request(input.question(), input.expectedConcepts(), input.expectedAnswer(),
                input.studentResponse(), P709ObservationCollector.syntheticEvidence(input)))).isEqualTo(VerifiedResponseEvaluationAggregator.uncertain());
        org.mockito.Mockito.verifyNoInteractions(orchestrator);
    }
    @Test void tokenCostAndTimeBudgetsRejectBeforeAnotherDispatch() {
        assertThatThrownBy(() -> P709ObservationCollector.requireBudget(new P709ObservationCollector.Budget(2, 10, 100, 100, 1), 0, 0, 11, 0))
                .isInstanceOf(P709ObservationCollector.CollectionFailure.class);
        assertThatThrownBy(() -> P709ObservationCollector.requireBudget(new P709ObservationCollector.Budget(2, 100, 100, 10, 2), 0, 0, 6, 0))
                .isInstanceOf(P709ObservationCollector.CollectionFailure.class);
        assertThatThrownBy(() -> P709ObservationCollector.requireBudget(new P709ObservationCollector.Budget(2, 100, 100, 100, 1), 0, 0, 1, 100))
                .isInstanceOf(P709ObservationCollector.CollectionFailure.class);
    }
    @Test void recordedIdentityBindsActualBudgetAndRejectsStaleOrPartialBase() throws Exception {
        var input = P709ObservationCollector.inputs().getFirst();
        var first = collect(input, script(input));
        var second = P709ObservationCollector.offline(evidence, UUID.randomUUID(), input,
                new P709ObservationCollector.Budget(3, 1_000_000, 30_000, 1_000_000, 1), script(input), identity);
        assertThat(second.executionIdentity()).isNotEqualTo(first.executionIdentity());
        assertThatThrownBy(() -> P709ObservationIdentity.bindRun(P709ObservationIdentity.snapshot(Map.of("invented", "a".repeat(64))), Map.of()))
                .isInstanceOf(IllegalStateException.class);
    }
}
