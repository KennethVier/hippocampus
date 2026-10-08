package com.hippocampus.ai.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.hippocampus.ai.evaluation.GoldenAiQualification.Status.*;

import com.hippocampus.ai.application.prompt.PromptId;
import com.hippocampus.ai.application.provider.AiProviderAdapter;
import com.hippocampus.ai.application.provider.ProviderEventStream;
import com.hippocampus.ai.application.provider.ProviderExecutionException;
import com.hippocampus.ai.application.provider.ProviderExecutionRequest;
import com.hippocampus.ai.application.provider.ProviderExecutionResult;
import com.hippocampus.ai.application.provider.ProviderFailureType;
import com.hippocampus.ai.application.provider.ProviderUsage;
import com.hippocampus.ai.application.routing.ProviderId;
import com.hippocampus.ai.application.validation.AiSchemaValidationException;
import com.hippocampus.ai.application.validation.ResponseEvaluationAggregationFailureReason;
import com.hippocampus.ai.domain.ActivityType;
import com.hippocampus.ai.domain.AiOutputContract;
import com.hippocampus.ai.domain.AiTaskRequest;
import com.hippocampus.ai.domain.AiTaskType;
import com.hippocampus.ai.domain.ExplanationInput;
import com.hippocampus.ai.domain.ExplanationMode;
import com.hippocampus.ai.domain.QuestionDifficulty;
import com.hippocampus.ai.domain.QuestionGenerationInput;
import com.hippocampus.ai.domain.ResponseEvaluationInput;
import com.hippocampus.rag.domain.GroundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class GoldenAiLiveEvaluationRunnerTests {

    @Test
    void contractValidEvidenceSucceedsIndependentlyOfMatcherWithoutGrantingReviewOrRoutingApproval() {
        for (boolean matcherPass : List.of(true, false)) {
            var report = qualificationEntry(matcherPass, validResponseEvaluation());
            assertThat(report.passed()).isEqualTo(matcherPass);
            assertThat(report.evidenceCollectionStatus()).isEqualTo(PASS);
            assertThat(report.contractStatus()).isEqualTo(PASS);
            assertThat(report.semanticMatcherStatus()).isEqualTo(matcherPass ? PASS : FAIL);
            assertThat(report.semanticReviewStatus()).isEqualTo(PENDING);
            assertThat(report.qualificationStatus()).isEqualTo(PENDING);
            assertThat(report.failedRules()).isEqualTo(matcherPass ? List.of() : List.of("semantic-rule"));
            GoldenAiLiveEvaluationRunner.assertEvidenceCollection(List.of(report), java.nio.file.Path.of("report.json"));
            var json = new tools.jackson.databind.ObjectMapper().valueToTree(report);
            assertThat(json.has("evaluationApprovedTasks")).isFalse();
            assertThat(json.path("passed").asBoolean()).isEqualTo(matcherPass);
            assertThat(json.path("semanticReviewStatus").asText()).isEqualTo("PENDING");
        }
    }

    @Test
    void contractFailureStillFailsAutomatedAssertionAndCannotAcceptHumanApproval() {
        var failed = qualificationEntry(true, "{}", "{}");
        assertThat(failed.evidenceCollectionStatus()).isEqualTo(FAIL);
        assertThat(failed.contractStatus()).isEqualTo(FAIL);
        assertThat(failed.semanticMatcherStatus()).isEqualTo(NOT_RUN);
        assertThatThrownBy(() -> GoldenAiLiveEvaluationRunner.assertEvidenceCollection(
                List.of(failed), java.nio.file.Path.of("report.json"))).isInstanceOf(AssertionError.class);
        var identity = GoldenAiQualification.identity(Map.of("provider", "GEMINI"));
        var reviews = new GoldenAiQualification.ReviewFile(List.of(review(identity, "old-output", PASS)), null);
        var current = GoldenAiLiveEvaluationRunner.withReview(failed, identity, reviews);
        assertThat(current.semanticReviewStatus()).isEqualTo(PENDING);
        assertThat(current.qualificationStatus()).isEqualTo(FAIL);
    }

    @Test
    void humanReviewIsBoundToExactOutputAndMaterialConfigurationAndRetainedInReport() throws Exception {
        var base = qualificationEntry(false, validResponseEvaluation());
        var identity = GoldenAiQualification.currentIdentity("GEMINI", "qualification-model");
        String output = GoldenAiQualification.outputIdentity(base.validatedStructuredOutput());
        for (var status : List.of(PASS, FAIL)) {
            var evidence = review(identity, output, status);
            var file = new GoldenAiQualification.ReviewFile(List.of(evidence), null);
            GoldenAiQualification.validate(file, java.util.Set.of(base.caseId()));
            var current = GoldenAiLiveEvaluationRunner.withReview(base, identity, file);
            assertThat(current.semanticReviewStatus()).isEqualTo(status);
            assertThat(current.qualificationStatus()).isEqualTo(status);
            assertThat(current.reviewEvidence()).isEqualTo(evidence);
            assertThat(current.semanticMatcherStatus()).isEqualTo(FAIL);
            GoldenAiLiveEvaluationRunner.assertEvidenceCollection(List.of(current), java.nio.file.Path.of("report"));
            assertThat(GoldenAiQualification.overall(List.of(current), java.util.Set.of(base.caseId()), identity, file))
                    .isEqualTo(status == FAIL ? FAIL : PENDING);
        }
        var evidence = review(identity, output, PASS);
        var file = new GoldenAiQualification.ReviewFile(List.of(evidence), null);
        // Every explicit material input (including source/code/resource hashes) invalidates approval.
        for (String key : identity.inputs().keySet()) {
            var changed = new java.util.TreeMap<>(identity.inputs());
            changed.put(key, changed.get(key) + ":changed");
            var current = GoldenAiLiveEvaluationRunner.withReview(base, GoldenAiQualification.identity(changed), file);
            assertThat(current.semanticReviewStatus()).as(key).isEqualTo(PENDING);
            assertThat(current.reviewEvidence()).isEqualTo(evidence);
        }
        var staleOutput = new GoldenAiQualification.ReviewFile(List.of(review(identity, "different-output", PASS)), null);
        assertThat(GoldenAiLiveEvaluationRunner.withReview(base, identity, staleOutput).semanticReviewStatus()).isEqualTo(PENDING);
    }

    @Test
    void fullQualificationRequiresAllNineCurrentReviewsAndSeparateCuratedAndReleaseEvidence() {
        var identity = GoldenAiQualification.identity(Map.of("dataset", "v5", "provider", "GEMINI"));
        var required = new GoldenAiDatasetLoader().loadAll().responseEvaluations().stream()
                .map(GoldenAiDataset.ResponseEvaluationCase::caseId).collect(java.util.stream.Collectors.toSet());
        var base = qualificationEntry(true, validResponseEvaluation());
        String output = GoldenAiQualification.outputIdentity(base.validatedStructuredOutput());
        var reviews = required.stream().map(id -> new GoldenAiQualification.Review(id, identity, output, PASS,
                PASS, "independent-human", "2026-10-08", "Reviewed against rubric", "retained/run.json",
                List.<String>of(), null)).toList();
        var acceptance = new GoldenAiQualification.Acceptance(identity, PASS, "retained/curated-tests.txt", PASS,
                "independent-owner", "2026-10-08", "Applicable release gates accepted; sampling unresolved but not material to this claim",
                "retained/acceptance.md");
        var file = new GoldenAiQualification.ReviewFile(reviews, acceptance);
        GoldenAiQualification.validate(file, required);
        var entries = required.stream().map(id -> new GoldenAiLiveEvaluationRunner.ReportEntry(base.task(), id,
                base.model(), base.passed(), base.failedRules(), base.validatedStructuredOutput(), base.reviewerNotes(),
                base.diagnosticMetadata(), base.provider(), PASS, PASS, PASS, PENDING, "Pending", null, null, null, PENDING))
                .map(entry -> GoldenAiLiveEvaluationRunner.withReview(entry, identity, file)).toList();
        assertThat(GoldenAiQualification.overall(entries, required, identity, file)).isEqualTo(PASS);
        assertThat(GoldenAiQualification.overall(entries.subList(0, 1), required, identity, file)).isEqualTo(PENDING);
        assertThat(GoldenAiQualification.overall(entries, required, identity,
                new GoldenAiQualification.ReviewFile(reviews, null))).isEqualTo(PENDING);
        var stale = GoldenAiQualification.identity(Map.of("dataset", "v5", "provider", "changed"));
        assertThat(GoldenAiQualification.overall(entries, required, stale, file)).isEqualTo(PENDING);
    }

    @Test
    void malformedDuplicateUnknownAndUnattributedReviewsFailClosedAndFileRoundTrips(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var identity = GoldenAiQualification.identity(Map.of("dataset", "v5"));
        var evidence = review(identity, "output", PASS);
        var file = new GoldenAiQualification.ReviewFile(List.of(evidence), null);
        var path = directory.resolve("reviews.json");
        new tools.jackson.databind.ObjectMapper().writeValue(path.toFile(), file);
        assertThat(GoldenAiQualification.read(path, java.util.Set.of(evidence.caseId()))).isEqualTo(file);
        assertThatThrownBy(() -> GoldenAiQualification.validate(file, java.util.Set.of("unknown"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GoldenAiQualification.validate(new GoldenAiQualification.ReviewFile(List.of(evidence, evidence), null),
                java.util.Set.of(evidence.caseId()))).isInstanceOf(IllegalArgumentException.class);
        var anonymous = new GoldenAiQualification.Review(evidence.caseId(), identity, "output", PASS, PASS,
                "", "2026-10-08", "reason", "artifact", List.of(), null);
        assertThatThrownBy(() -> GoldenAiQualification.validate(new GoldenAiQualification.ReviewFile(List.of(anonymous), null),
                java.util.Set.of(evidence.caseId()))).isInstanceOf(IllegalArgumentException.class);
    }

    private GoldenAiLiveEvaluationRunner.ReportEntry qualificationEntry(boolean matcherPass, String... outputs) {
        return new GoldenAiLiveEvaluationRunner().execute(liveProvider(new RecordingAdapter(sequence(outputs))),
                "P7-09-WRONG-REASONING-001", "review", responseEvaluationRequest(),
                new GoldenAiLiveEvaluationRunner.RequestPacer(0, ignored -> {}),
                ignored -> new GoldenAiSemanticEvaluator.Result(matcherPass, matcherPass ? List.of() : List.of("semantic-rule")));
    }

    @Test
    void retainedReportCanBeReviewedWithoutProviderExecutionAndRejectsStaleOrAlteredEvidence() {
        var identity = GoldenAiQualification.identity(Map.of("dataset", "v5", "provider", "GEMINI"));
        var emptyReviews = new GoldenAiQualification.ReviewFile(List.of(), null);
        var base = GoldenAiLiveEvaluationRunner.withReview(qualificationEntry(false, validResponseEvaluation()), identity, emptyReviews);
        var report = new GoldenAiLiveEvaluationRunner.EvaluationReport("run-709", "v5", "GEMINI", base.model(),
                PASS, PENDING, identity, null, List.of(base));
        // Deserialize Object-valued output just as the offline command does.
        var mapper = new tools.jackson.databind.ObjectMapper();
        var retained = mapper.readValue(mapper.writeValueAsBytes(report), GoldenAiLiveEvaluationRunner.EvaluationReport.class);
        var reviews = new GoldenAiQualification.ReviewFile(List.of(review(identity, base.outputIdentity(), PASS)), null);
        var required = java.util.Set.of(base.caseId());
        var result = GoldenAiLiveEvaluationRunner.reviewRetainedReport(retained, identity, reviews, required);
        assertThat(result.runId()).isEqualTo("run-709");
        assertThat(result.evidenceCollectionStatus()).isEqualTo(PASS);
        assertThat(result.qualificationStatus()).isEqualTo(PENDING);
        assertThat(result.cases().getFirst().semanticReviewStatus()).isEqualTo(PASS);
        assertThat(result.cases().getFirst().semanticMatcherStatus()).isEqualTo(FAIL);
        assertThat(result.cases().getFirst().passed()).isFalse();
        assertThatThrownBy(() -> GoldenAiLiveEvaluationRunner.reviewRetainedReport(retained,
                GoldenAiQualification.identity(Map.of("dataset", "changed")), reviews, required))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("stale");
        var altered = mapper.readTree(mapper.writeValueAsBytes(report));
        ((tools.jackson.databind.node.ObjectNode) altered.path("cases").get(0).path("validatedStructuredOutput"))
                .put("feedback", "altered feedback");
        var tampered = mapper.treeToValue(altered, GoldenAiLiveEvaluationRunner.EvaluationReport.class);
        assertThatThrownBy(() -> GoldenAiLiveEvaluationRunner.reviewRetainedReport(tampered, identity, reviews, required))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("inconsistent");
    }

    @Test
    void matcherExecutionErrorFailsEvidenceButRetainsSuccessfulProductionContract() {
        var entry = new GoldenAiLiveEvaluationRunner().execute(liveProvider(new RecordingAdapter(sequence(validResponseEvaluation()))),
                "P7-09-WRONG-REASONING-001", "review", responseEvaluationRequest(),
                new GoldenAiLiveEvaluationRunner.RequestPacer(0, ignored -> {}),
                ignored -> { throw new IllegalStateException("private diagnostic text"); });
        assertThat(entry.evidenceCollectionStatus()).isEqualTo(FAIL);
        assertThat(entry.contractStatus()).isEqualTo(PASS);
        assertThat(entry.validatedStructuredOutput()).isNotNull();
        assertThat(entry.failedRules()).containsExactly("evaluation-runner:IllegalStateException");
        assertThatThrownBy(() -> GoldenAiLiveEvaluationRunner.assertEvidenceCollection(List.of(entry), java.nio.file.Path.of("report")))
                .isInstanceOf(AssertionError.class);
    }

    private static GoldenAiQualification.Review review(GoldenAiQualification.Identity identity, String output,
                                                      GoldenAiQualification.Status status) {
        return new GoldenAiQualification.Review("P7-09-WRONG-REASONING-001", identity, output, PASS, status,
                "independent-human", "2026-10-08", "Reviewed classification, medical reasoning, fields and learner grounding",
                "retained/live-run.json", List.of(), null);
    }

    private final GoldenAiDataset.Learner learner = new GoldenAiDataset.Learner(
            "BUILDING_MECHANISM", "RECENT_EXPOSURE", "STANDARD", Map.of(), List.of());

    @Test
    void caseSelectionIsOptionalAndConfinedToSelectedTask() {
        GoldenAiDataset.All all = new GoldenAiDatasetLoader().loadAll();
        assertThat(GoldenAiLiveEvaluationRunner.selectedDataset(all, "all", null)).isSameAs(all);
        assertThat(GoldenAiLiveEvaluationRunner.selectedDataset(all, "all", " ")).isSameAs(all);
        assertThat(GoldenAiLiveEvaluationRunner.selectedDataset(all, "response-evaluation", " "))
                .isEqualTo(GoldenAiLiveEvaluationRunner.selectedDataset(all, "response-evaluation"));
        GoldenAiDataset.All selected = GoldenAiLiveEvaluationRunner.selectedDataset(
                all, "response-evaluation", "P7-09-WRONG-REASONING-001");
        assertThat(selected.responseEvaluations()).extracting(GoldenAiDataset.ResponseEvaluationCase::caseId)
                .containsExactly("P7-09-WRONG-REASONING-001");
        assertThat(selected.explanations()).isEmpty();
        assertThat(selected.questions()).isEmpty();
        assertThat(selected.version()).isEqualTo(all.version());
        for (String id : List.of("unknown", all.explanations().getFirst().caseId())) {
            assertThatThrownBy(() -> GoldenAiLiveEvaluationRunner.selectedDataset(all, "response-evaluation", id))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("HIPPOCAMPUS_LIVE_AI_GOLDEN_CASE");
        }
        assertThat(GoldenAiLiveEvaluationRunner.selectedDataset(all, "explanation",
                all.explanations().getFirst().caseId()).explanations()).hasSize(1);
        assertThat(GoldenAiLiveEvaluationRunner.selectedDataset(all, "question-generation",
                all.questions().getFirst().caseId()).questions()).hasSize(1);
    }

    @Test
    void diagnosticsExposeOnlyCountsAndAllowListedFinishReasons() {
        ProviderExecutionResult result = new ProviderExecutionResult(ProviderId.GEMINI, "test",
                """
                {"judgments":[{"expectedConceptIndex":0,"studentClaims":["secret-medical-text"]},
                 {"expectedConceptIndex":"secret-index"},{"expectedConceptIndex":900},
                 {"expectedConceptIndex":0},{"expectedConceptIndex":1}]}
                """, ProviderUsage.of(11, 2048, 2059), Duration.ZERO, 0, 0,
                java.util.Optional.of("secret-provider-reason"));
        Map<String, String> metadata = GoldenAiLiveEvaluationRunner.providerDiagnosticMetadata("repair", result);
        assertThat(metadata).containsEntry("repairFinishReason", "UNKNOWN")
                .containsEntry("repairInputTokens", "11").containsEntry("repairOutputTokens", "2048")
                .containsEntry("repairResponseCharacters", Integer.toString(result.rawContent().length()));
        Map<String, String> coverage = GoldenAiLiveEvaluationRunner.judgmentDiagnosticMetadata(result, 2);
        assertThat(coverage).containsExactlyInAnyOrderEntriesOf(Map.of(
                "repairJudgmentCount", "5", "repairObservedExpectedConceptIndexes", "[0, 1]"));
        assertThat(metadata.toString() + coverage).doesNotContain("secret", "studentClaims", "judgments");
    }

    @Test
    void incompleteRepairCoverageRemainsTerminalAndReportsAttemptMetadata() {
        AtomicInteger attempts = new AtomicInteger();
        RecordingAdapter adapter = new RecordingAdapter(request -> new ProviderExecutionResult(
                ProviderId.GEMINI, "qualification-model", attempts.getAndIncrement() == 0
                        ? "{\"secret-raw-content\":" : validResponseEvaluation().replace(
                                "\"expectedConceptIndex\": 0", "\"expectedConceptIndex\": 1"),
                ProviderUsage.of(11, 2048, 2059), Duration.ZERO, 0, 0,
                java.util.Optional.of(request.taskType() == AiTaskType.STRUCTURED_OUTPUT_REPAIR ? "STOP" : "MAX_TOKENS")));
        AiTaskRequest<ResponseEvaluationInput> request = GoldenAiLiveEvaluationRunner.request(
                AiTaskType.RESPONSE_EVALUATION,
                new ResponseEvaluationInput("question", List.of("first concept", "second concept"), "answer", "response"),
                learner, List.of(), GroundingMode.GENERAL_KNOWLEDGE, AiOutputContract.RESPONSE_EVALUATION);
        GoldenAiLiveEvaluationRunner.ReportEntry report = new GoldenAiLiveEvaluationRunner().execute(
                liveProvider(adapter), "coverage", "review", request,
                new GoldenAiLiveEvaluationRunner.RequestPacer(0L, ignored -> {}),
                value -> new GoldenAiSemanticEvaluator.Result(true, List.of()));
        assertThat(report.passed()).isFalse();
        assertThat(report.validatedStructuredOutput()).isNull();
        assertThat(report.diagnosticMetadata()).containsEntry("initialFinishReason", "MAX_TOKENS")
                .containsEntry("repairFinishReason", "STOP")
                .containsEntry("failureStage", "REPAIR")
                .containsEntry("initialSchemaFailure", "MALFORMED_JSON")
                .containsEntry("responseEvaluationAggregationReason", "INCOMPLETE_COVERAGE")
                .containsEntry("repairJudgmentCount", "1")
                .containsEntry("repairObservedExpectedConceptIndexes", "[1]");
        assertThat(report.diagnosticMetadata().toString()).doesNotContain(
                "secret-raw-content", "AV node", "studentClaims", "first concept", "second concept");
        assertThat(adapter.requests).hasSize(2);
    }

    @Test
    void goldenRunnerSelectsCurrentPromptsForP7EvaluationTasks() {
        AiTaskRequest<ExplanationInput> explanationRequest = GoldenAiLiveEvaluationRunner.request(
                AiTaskType.EXPLANATION,
                new ExplanationInput("Explain conduction", "AV node", ExplanationMode.STEP_BY_STEP),
                learner,
                List.of(),
                GroundingMode.STRICT_SOURCE,
                AiOutputContract.EXPLANATION);
        assertThat(explanationRequest.promptVersion()).isEqualTo(PromptId.EXPLANATION_V2.name());

        AiTaskRequest<QuestionGenerationInput> questionRequest = GoldenAiLiveEvaluationRunner.request(
                AiTaskType.QUESTION_GENERATION,
                new QuestionGenerationInput(
                        "Recall conduction", "AV node", ActivityType.SHORT_ANSWER,
                        QuestionDifficulty.FOUNDATIONAL, List.of(), null),
                learner,
                List.of(),
                GroundingMode.STRICT_SOURCE,
                AiOutputContract.QUESTION_GENERATION);
        assertThat(questionRequest.promptVersion()).isEqualTo(PromptId.QUESTION_GENERATION_V2.name());

        AiTaskRequest<ResponseEvaluationInput> responseRequest = GoldenAiLiveEvaluationRunner.request(
                AiTaskType.RESPONSE_EVALUATION,
                new ResponseEvaluationInput("Question", List.of("AV node"), "AV node", "AV node"),
                learner,
                List.of(),
                GroundingMode.STRICT_SOURCE,
                AiOutputContract.RESPONSE_EVALUATION);
        assertThat(responseRequest.promptVersion()).isEqualTo(PromptId.RESPONSE_EVALUATION_V6.name());
    }

    @Test
    void v5IndexedMechanismReachesV6PromptUnchanged() {
        GoldenAiDataset.ResponseEvaluationCase golden = new GoldenAiDatasetLoader().loadAll().responseEvaluations()
                .stream().filter(value -> value.caseId().equals("P7-09-WRONG-REASONING-001")).findFirst().orElseThrow();
        AiTaskRequest<ResponseEvaluationInput> request = GoldenAiLiveEvaluationRunner.request(
                AiTaskType.RESPONSE_EVALUATION,
                new ResponseEvaluationInput(golden.question(), golden.expectedConcepts(), golden.expectedAnswer(), golden.studentResponse()),
                golden.learner(), golden.sourceEvidence(), golden.groundingMode(), AiOutputContract.RESPONSE_EVALUATION);
        var context = new com.hippocampus.ai.application.prompt.PromptContextBuilder(
                new com.hippocampus.ai.application.prompt.PromptTemplateRegistry(), String::length)
                .build(request, new com.hippocampus.ai.application.prompt.PromptTokenBudget(200_000, 2048));
        assertThat(context.taskPromptId()).isEqualTo(PromptId.RESPONSE_EVALUATION_V6);
        assertThat(context.taskPrompt()).contains("EXPECTED_CONCEPTS:\n[\"The radial nerve supplies wrist extensors\","
                + "\"Radial nerve injury eliminates wrist extension\",\"Loss of wrist extension produces wrist drop\"]");
        assertThat(context.taskPrompt()).contains(golden.expectedAnswer());
        assertThat(context.includedSources()).hasSize(1);
    }

    @Test
    void explanationTaskExecutesOnlyExplanationCases() {
        GoldenAiDataset.All all = new GoldenAiDatasetLoader().loadAll();

        GoldenAiDataset.All selected = GoldenAiLiveEvaluationRunner.selectedDataset(all, "explanation");

        assertThat(selected.explanations()).containsExactlyElementsOf(all.explanations());
        assertThat(selected.questions()).isEmpty();
        assertThat(selected.responseEvaluations()).isEmpty();
    }

    @Test
    void questionGenerationTaskExecutesOnlyQuestionCases() {
        GoldenAiDataset.All all = new GoldenAiDatasetLoader().loadAll();

        GoldenAiDataset.All selected = GoldenAiLiveEvaluationRunner.selectedDataset(
                all, "question-generation");

        assertThat(selected.explanations()).isEmpty();
        assertThat(selected.questions()).containsExactlyElementsOf(all.questions());
        assertThat(selected.responseEvaluations()).isEmpty();
    }

    @Test
    void responseEvaluationTaskExecutesOnlyResponseEvaluationCases() {
        GoldenAiDataset.All all = new GoldenAiDatasetLoader().loadAll();

        GoldenAiDataset.All selected = GoldenAiLiveEvaluationRunner.selectedDataset(
                all, "response-evaluation");

        assertThat(selected.explanations()).isEmpty();
        assertThat(selected.questions()).isEmpty();
        assertThat(selected.responseEvaluations())
                .containsExactlyElementsOf(all.responseEvaluations());
    }

    @Test
    void allTaskPreservesEveryCase() {
        GoldenAiDataset.All all = new GoldenAiDatasetLoader().loadAll();

        assertThat(all.version()).isEqualTo("v5");
        assertThat(GoldenAiLiveEvaluationRunner.selectedDataset(all, "all")).isSameAs(all);
        assertThat(GoldenAiLiveEvaluationRunner.selectedDataset(all, null)).isSameAs(all);
        assertThat(GoldenAiLiveEvaluationRunner.selectedDataset(all, "explanation").version())
                .isEqualTo("v5");
        assertThat(GoldenAiLiveEvaluationRunner.selectedDataset(all, "question-generation").version())
                .isEqualTo("v5");
        assertThat(GoldenAiLiveEvaluationRunner.selectedDataset(all, "response-evaluation").version())
                .isEqualTo("v5");
    }

    @Test
    void invalidTaskFailsClosed() {
        GoldenAiDataset.All all = new GoldenAiDatasetLoader().loadAll();

        assertThatThrownBy(() -> GoldenAiLiveEvaluationRunner.selectedDataset(all, "questions"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported live Golden AI task");
    }

    @Test
    void delayDefaultsToZeroAndRejectsInvalidValues() {
        assertThat(GoldenAiLiveEvaluationRunner.parseDelayMillis(null)).isZero();
        assertThat(GoldenAiLiveEvaluationRunner.parseDelayMillis(" ")).isZero();
        assertThat(GoldenAiLiveEvaluationRunner.parseDelayMillis("0")).isZero();
        assertThat(GoldenAiLiveEvaluationRunner.parseDelayMillis("5000")).isEqualTo(5_000L);
        assertThatThrownBy(() -> GoldenAiLiveEvaluationRunner.parseDelayMillis("-1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GoldenAiLiveEvaluationRunner.parseDelayMillis("soon"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requestPacerDelaysOnlyBetweenRequests() {
        AtomicLong sleptMillis = new AtomicLong();
        GoldenAiLiveEvaluationRunner.RequestPacer pacer =
                new GoldenAiLiveEvaluationRunner.RequestPacer(5_000L, sleptMillis::addAndGet);

        pacer.beforeRequest();
        assertThat(sleptMillis).hasValue(0L);

        pacer.beforeRequest();
        assertThat(sleptMillis).hasValue(5_000L);
    }

    @Test
    void goldenReportUsesOnlySanitizedAggregationFailureMetadata() {
        AiSchemaValidationException failure = new AiSchemaValidationException(
                AiOutputContract.RESPONSE_EVALUATION,
                AiSchemaValidationException.Reason.BUSINESS_RULE_VIOLATION,
                ResponseEvaluationAggregationFailureReason.MISSING_WITH_SUPPORTED_COMPONENTS);

        Map<String, String> metadata =
                GoldenAiLiveEvaluationRunner.schemaValidationDiagnosticMetadata(failure);

        assertThat(metadata).containsExactlyEntriesOf(Map.of(
                "responseEvaluationAggregationReason", "MISSING_WITH_SUPPORTED_COMPONENTS"));
        assertThat(metadata.toString()).doesNotContain(
                "student response", "source text", "provider raw output", "medical content", "prompt");
    }

    @Test
    void schemaFailureUsesOneSameTargetRepairAndReportsIt() {
        RecordingAdapter adapter = new RecordingAdapter(sequence(
                "{\"broken\":",
                validResponseEvaluation()));
        GoldenAiLiveEvaluationRunner runner = new GoldenAiLiveEvaluationRunner();

        GoldenAiLiveEvaluationRunner.ReportEntry report = runner.execute(
                liveProvider(adapter),
                "repair-case",
                "review",
                responseEvaluationRequest(),
                new GoldenAiLiveEvaluationRunner.RequestPacer(0L, ignored -> {}),
                value -> new GoldenAiSemanticEvaluator.Result(true, List.of()));

        assertThat(report.passed()).isTrue();
        assertThat(report.diagnosticMetadata()).containsAllEntriesOf(Map.of(
                "structuredRepairUsed", "true",
                "initialSchemaFailure", "MALFORMED_JSON"));
        assertThat(adapter.requests).hasSize(2);
        ProviderExecutionRequest initial = adapter.requests.get(0);
        ProviderExecutionRequest repair = adapter.requests.get(1);
        assertThat(initial.taskType()).isEqualTo(AiTaskType.RESPONSE_EVALUATION);
        assertThat(repair.taskType()).isEqualTo(AiTaskType.STRUCTURED_OUTPUT_REPAIR);
        assertThat(repair.promptContext().taskPromptId())
                .isEqualTo(PromptId.STRUCTURED_OUTPUT_REPAIR_V1);
        assertThat(repair.target()).isEqualTo(initial.target());
        assertThat(repair.outputContract()).isSameAs(initial.outputContract());
    }

    @Test
    void invalidContradictedShapeEntersOneRepairAndRevalidatesOriginalContract() {
        String invalid = validResponseEvaluation().replace("\"status\": \"PARTIAL\"", "\"status\": \"CONTRADICTED\"");
        for (boolean repairValid : List.of(true, false)) {
            RecordingAdapter adapter = new RecordingAdapter(sequence(invalid, repairValid ? validResponseEvaluation() : invalid));
            GoldenAiLiveEvaluationRunner.ReportEntry report = new GoldenAiLiveEvaluationRunner().execute(
                    liveProvider(adapter), "contradicted-shape", "review", responseEvaluationRequest(),
                    new GoldenAiLiveEvaluationRunner.RequestPacer(0L, ignored -> {}),
                    value -> new GoldenAiSemanticEvaluator.Result(true, List.of()));
            assertThat(report.passed()).isEqualTo(repairValid);
            assertThat(report.diagnosticMetadata()).containsEntry("structuredRepairUsed", "true")
                    .containsEntry("initialSchemaFailure", "BUSINESS_RULE_VIOLATION")
                    .containsEntry("responseEvaluationAggregationReason", "INVALID_CONTRADICTED_SHAPE");
            assertThat(adapter.requests).hasSize(2);
            var original = adapter.requests.getFirst();
            var repair = adapter.requests.get(1);
            assertThat(repair.taskType()).isEqualTo(AiTaskType.STRUCTURED_OUTPUT_REPAIR);
            assertThat(repair.promptContext().taskPromptId()).isEqualTo(PromptId.STRUCTURED_OUTPUT_REPAIR_V1);
            assertThat(repair.outputContract()).isEqualTo(AiOutputContract.RESPONSE_EVALUATION);
            assertThat(repair.promptContext().reservedOutputTokens()).isEqualTo(original.promptContext().reservedOutputTokens())
                    .isEqualTo(2048);
            if (!repairValid) {
                assertThat(report.failedRules()).containsExactly("schema-validation:BUSINESS_RULE_VIOLATION");
                assertThat(report.validatedStructuredOutput()).isNull();
            } else {
                assertThat(report.validatedStructuredOutput()).isInstanceOf(com.hippocampus.ai.domain.ResponseEvaluationResult.class);
            }
        }
    }

    @Test
    void missingMisconceptionRepairPreservesValidationAndReportsOnlyBoundedShapeMetadata() {
        String invalid = validResponseEvaluation()
                .replace("\"status\": \"PARTIAL\"", "\"status\": \"MISSING\"")
                .replace("\"supportedComponents\": [\"The AV node delays conduction\"]", "\"supportedComponents\": []")
                .replace("\"demonstratedMisconceptions\": []", "\"demonstratedMisconceptions\": [\"private-learner-claim\"]");
        for (boolean repairValid : List.of(true, false)) {
            RecordingAdapter adapter = new RecordingAdapter(sequence(invalid, repairValid ? validResponseEvaluation() : invalid));
            var report = new GoldenAiLiveEvaluationRunner().execute(liveProvider(adapter), "missing-shape", "review",
                    responseEvaluationRequest(), new GoldenAiLiveEvaluationRunner.RequestPacer(0L, ignored -> {}),
                    value -> new GoldenAiSemanticEvaluator.Result(true, List.of()));
            assertThat(report.passed()).isEqualTo(repairValid);
            assertThat(report.diagnosticMetadata()).containsEntry("initialSchemaFailure", "BUSINESS_RULE_VIOLATION")
                    .containsEntry("responseEvaluationAggregationReason", "MISSING_WITH_DEMONSTRATED_MISCONCEPTION");
            assertThat(adapter.requests).hasSize(2);
            var repair = adapter.requests.get(1);
            assertThat(repair.promptContext().reservedOutputTokens()).isEqualTo(2048);
            assertThat(repair.promptContext().taskPrompt()).contains(
                    "MISSING: supportedComponents and demonstratedMisconceptions must be empty",
                    "SUPPORTED: studentClaims and supportedComponents must be nonempty",
                    "PARTIAL: studentClaims and supportedComponents must be nonempty",
                    "CONTRADICTED: studentClaims and demonstratedMisconceptions must be nonempty",
                    "Do not omit, duplicate or invent expectedConceptIndex values",
                    "change status solely to pass validation");
            if (!repairValid) {
                assertThat(report.failedRules()).containsExactly("schema-validation:BUSINESS_RULE_VIOLATION");
                assertThat(report.diagnosticMetadata()).containsEntry("repairInvalidExpectedConceptIndex", "0")
                        .containsEntry("repairInvalidJudgmentStatus", "MISSING")
                        .containsEntry("repairInvalidStudentClaimCount", "1")
                        .containsEntry("repairInvalidSupportedComponentCount", "0")
                        .containsEntry("repairInvalidMissingComponentCount", "1")
                        .containsEntry("repairInvalidMisconceptionCount", "1");
                assertThat(report.validatedStructuredOutput()).isNull();
            } else {
                assertThat(report.diagnosticMetadata()).doesNotContainKey("repairInvalidExpectedConceptIndex");
            }
            assertThat(report.diagnosticMetadata().toString()).doesNotContain("private-learner-claim", "AV node", "studentClaims");
        }
    }

    @Test
    void secondSchemaFailureIsTerminalAndCannotRecursivelyRepair() {
        RecordingAdapter adapter = new RecordingAdapter(sequence("not json", "still not json"));
        GoldenAiLiveEvaluationRunner runner = new GoldenAiLiveEvaluationRunner();

        GoldenAiLiveEvaluationRunner.ReportEntry report = runner.execute(
                liveProvider(adapter),
                "failed-repair-case",
                "review",
                responseEvaluationRequest(),
                new GoldenAiLiveEvaluationRunner.RequestPacer(0L, ignored -> {}),
                value -> new GoldenAiSemanticEvaluator.Result(true, List.of()));

        assertThat(report.passed()).isFalse();
        assertThat(report.failedRules()).containsExactly("schema-validation:MALFORMED_JSON");
        assertThat(report.diagnosticMetadata())
                .containsEntry("structuredRepairUsed", "true")
                .containsEntry("initialSchemaFailure", "MALFORMED_JSON");
        assertThat(adapter.requests).hasSize(2);
    }

    @Test
    void validSchemaSemanticFailureDoesNotTriggerRepair() {
        RecordingAdapter adapter = new RecordingAdapter(sequence(validResponseEvaluation()));
        GoldenAiLiveEvaluationRunner runner = new GoldenAiLiveEvaluationRunner();

        GoldenAiLiveEvaluationRunner.ReportEntry report = runner.execute(
                liveProvider(adapter),
                "semantic-failure-case",
                "review",
                responseEvaluationRequest(),
                new GoldenAiLiveEvaluationRunner.RequestPacer(0L, ignored -> {}),
                value -> new GoldenAiSemanticEvaluator.Result(false, List.of("semantic-rule")));

        assertThat(report.passed()).isFalse();
        assertThat(report.failedRules()).containsExactly("semantic-rule");
        assertThat(report.diagnosticMetadata()).containsEntry("initialResponseCharacters",
                Integer.toString(validResponseEvaluation().length())).doesNotContainKey("structuredRepairUsed");
        assertThat(adapter.requests).hasSize(1);
    }

    @Test
    void providerFailureDoesNotMasqueradeAsRepairSuccess() {
        RecordingAdapter adapter = new RecordingAdapter(request -> {
            throw new ProviderExecutionException(ProviderId.GEMINI, ProviderFailureType.AUTHENTICATION_FAILURE);
        });
        GoldenAiLiveEvaluationRunner runner = new GoldenAiLiveEvaluationRunner();

        GoldenAiLiveEvaluationRunner.ReportEntry report = runner.execute(
                liveProvider(adapter),
                "provider-failure-case",
                "review",
                responseEvaluationRequest(),
                new GoldenAiLiveEvaluationRunner.RequestPacer(0L, ignored -> {}),
                value -> new GoldenAiSemanticEvaluator.Result(true, List.of()));

        assertThat(report.passed()).isFalse();
        assertThat(report.failedRules())
                .containsExactly("provider-execution:AUTHENTICATION_FAILURE");
        assertThat(adapter.requests).hasSize(1);
    }

    private AiTaskRequest<ResponseEvaluationInput> responseEvaluationRequest() {
        return GoldenAiLiveEvaluationRunner.request(
                AiTaskType.RESPONSE_EVALUATION,
                new ResponseEvaluationInput(
                        "Why does AV nodal delay support filling?",
                        List.of("AV nodal delay permits ventricular filling"),
                        "It delays ventricular activation so filling can complete.",
                        "The AV node delays conduction."),
                learner,
                List.of(),
                GroundingMode.GENERAL_KNOWLEDGE,
                AiOutputContract.RESPONSE_EVALUATION);
    }

    private static GoldenAiLiveEvaluationRunner.LiveProvider liveProvider(RecordingAdapter adapter) {
        return new GoldenAiLiveEvaluationRunner.LiveProvider(
                ProviderId.GEMINI, "qualification-model", adapter, "test");
    }

    private static Function<ProviderExecutionRequest, ProviderExecutionResult> sequence(
            String... outputs) {
        AtomicInteger index = new AtomicInteger();
        return request -> new ProviderExecutionResult(
                request.target().providerId(),
                request.target().modelId(),
                outputs[index.getAndIncrement()],
                ProviderUsage.NONE,
                Duration.ofMillis(1));
    }

    private static String validResponseEvaluation() {
        return """
                {
                  "judgments": [
                    {
                      "expectedConceptIndex": 0,
                      "expectedConcept": "AV nodal delay permits ventricular filling",
                      "studentClaims": ["The AV node delays conduction"],
                      "status": "PARTIAL",
                      "supportedComponents": ["The AV node delays conduction"],
                      "missingComponents": ["The link to ventricular filling is missing"],
                      "demonstratedMisconceptions": []
                    }
                  ],
                  "assessability": "EVALUABLE",
                  "feedback": "Connect the delay to ventricular filling.",
                  "recommendedAction": "RETRY",
                  "sourceReferences": [],
                  "limitations": []
                }
                """;
    }

    private static final class RecordingAdapter implements AiProviderAdapter {
        private final Function<ProviderExecutionRequest, ProviderExecutionResult> behavior;
        private final List<ProviderExecutionRequest> requests = new ArrayList<>();

        private RecordingAdapter(
                Function<ProviderExecutionRequest, ProviderExecutionResult> behavior) {
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
            throw new UnsupportedOperationException();
        }
    }
}
