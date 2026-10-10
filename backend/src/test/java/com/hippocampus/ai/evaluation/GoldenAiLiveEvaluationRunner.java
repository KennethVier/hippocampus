package com.hippocampus.ai.evaluation;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.hippocampus.ai.evaluation.GoldenAiQualification.Status.*;

import com.google.genai.Client;
import com.hippocampus.ai.application.AiExecutionOrchestrator;
import com.hippocampus.ai.application.prompt.PromptContextBuilder;
import com.hippocampus.ai.application.prompt.PromptId;
import com.hippocampus.ai.application.prompt.PromptTemplateRegistry;
import com.hippocampus.ai.application.prompt.PromptTokenBudget;
import com.hippocampus.ai.application.provider.AiProviderAdapter;
import com.hippocampus.ai.application.provider.ProviderEventStream;
import com.hippocampus.ai.application.provider.ProviderExecutionException;
import com.hippocampus.ai.application.provider.ProviderExecutionRequest;
import com.hippocampus.ai.application.provider.ProviderExecutionResult;
import com.hippocampus.ai.application.provider.ProviderExecutionFailure;
import com.hippocampus.ai.application.request.AiRequestManager;
import com.hippocampus.ai.application.request.AiRequestManagerPolicy;
import com.hippocampus.ai.application.request.AiRequestPriority;
import com.hippocampus.ai.application.request.AiRequestTelemetry;
import com.hippocampus.ai.application.routing.ProviderId;
import com.hippocampus.ai.application.routing.ProviderRouter;
import com.hippocampus.ai.application.routing.ProviderRoutingCandidate;
import com.hippocampus.ai.application.routing.ProviderRoutingPreference;
import com.hippocampus.ai.application.validation.AiOutputValidator;
import com.hippocampus.ai.application.validation.AiSchemaValidationException;
import com.hippocampus.ai.application.validation.AiSourceReferenceValidator;
import com.hippocampus.ai.domain.AiOutputContract;
import com.hippocampus.ai.domain.AiTaskContext;
import com.hippocampus.ai.domain.AiTaskRequest;
import com.hippocampus.ai.domain.AiTaskType;
import com.hippocampus.ai.domain.ExplanationInput;
import com.hippocampus.ai.domain.ExplanationResult;
import com.hippocampus.ai.domain.LearnerContext;
import com.hippocampus.ai.domain.QuestionGenerationInput;
import com.hippocampus.ai.domain.QuestionGenerationResult;
import com.hippocampus.ai.domain.ResponseEvaluationInput;
import com.hippocampus.ai.domain.ResponseEvaluationResult;
import com.hippocampus.ai.domain.ValidatedAiResult;
import com.hippocampus.ai.infrastructure.prompt.Utf8ByteLengthPromptTokenCounter;
import com.hippocampus.ai.infrastructure.provider.gemini.GeminiProviderAdapter;
import com.hippocampus.ai.infrastructure.provider.ollama.OllamaCloudProviderAdapter;
import com.hippocampus.ai.infrastructure.validation.JacksonAiStructuredOutputDecoder;
import com.hippocampus.ai.port.AiDiagnosticsPersistence;
import com.hippocampus.ai.port.AiRequestDiagnostic;
import com.hippocampus.ai.port.ProviderUsageDiagnostic;
import com.hippocampus.identity.domain.AuthenticatedUser;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.LongConsumer;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

class GoldenAiLiveEvaluationRunner {

    private static final PromptTokenBudget PROMPT_BUDGET = new PromptTokenBudget(131_072, 2_048);
    private static final String TASK_ENVIRONMENT = "HIPPOCAMPUS_LIVE_AI_GOLDEN_TASK";
    private static final String CASE_ENVIRONMENT = "HIPPOCAMPUS_LIVE_AI_GOLDEN_CASE";
    private static final String DELAY_ENVIRONMENT = "HIPPOCAMPUS_LIVE_AI_GOLDEN_DELAY_MS";
    private static final String RESPONSE_PROMPT_ENVIRONMENT = "HIPPOCAMPUS_LIVE_AI_GOLDEN_RESPONSE_PROMPT";
    private static final UUID QUALIFICATION_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000709");

    private final GoldenAiSemanticEvaluator semanticEvaluator = new GoldenAiSemanticEvaluator();

    @Test
    void runsP7GoldenEvaluationWhenExplicitlyEnabled() throws Exception {
        Assumptions.assumeTrue(
                "true".equalsIgnoreCase(System.getenv("HIPPOCAMPUS_LIVE_AI_GOLDEN")));

        GoldenTask task = GoldenTask.parse(System.getenv(TASK_ENVIRONMENT));
        long delayMillis = parseDelayMillis(System.getenv(DELAY_ENVIRONMENT));
        GoldenAiDataset.All dataset = selectedDataset(new GoldenAiDatasetLoader().loadAll(),
                task.environmentValue, System.getenv(CASE_ENVIRONMENT));
        PromptId responsePrompt = responseEvaluationPrompt(System.getenv(RESPONSE_PROMPT_ENVIRONMENT));
        LiveProvider liveProvider = liveProvider();
        GoldenAiQualification.Identity identity = GoldenAiQualification.currentIdentity(
                liveProvider.providerId().name(), liveProvider.configuredModel(), responsePrompt);
        Set<String> requiredCases = new GoldenAiDatasetLoader().loadAll().responseEvaluations().stream()
                .map(GoldenAiDataset.ResponseEvaluationCase::caseId).collect(java.util.stream.Collectors.toSet());
        String reviewPath = System.getenv("HIPPOCAMPUS_LIVE_AI_GOLDEN_REVIEW_FILE");
        GoldenAiQualification.ReviewFile reviewFile = GoldenAiQualification.read(
                reviewPath == null || reviewPath.isBlank() ? null : Path.of(reviewPath), requiredCases);
        RequestPacer requestPacer = new RequestPacer(delayMillis, GoldenAiLiveEvaluationRunner::sleep);
        List<ReportEntry> entries = new ArrayList<>();

        for (GoldenAiDataset.ExplanationCase golden : dataset.explanations()) {
            AiTaskRequest<ExplanationInput> request = request(
                    AiTaskType.EXPLANATION,
                    new ExplanationInput(
                            golden.learningObjective(), golden.targetConcept(), golden.explanationMode()),
                    golden.learner(),
                    golden.sourceEvidence(),
                    golden.groundingMode(),
                    AiOutputContract.EXPLANATION);
            entries.add(execute(
                    liveProvider,
                    golden.caseId(),
                    golden.reviewerNotes(),
                    request,
                    requestPacer,
                    value -> semanticEvaluator.evaluate(golden, (ExplanationResult) value)));
        }
        for (GoldenAiDataset.QuestionCase golden : dataset.questions()) {
            AiTaskRequest<QuestionGenerationInput> request = request(
                    AiTaskType.QUESTION_GENERATION,
                    new QuestionGenerationInput(
                            golden.learningObjective(),
                            golden.targetConcept(),
                            golden.activityType(),
                            golden.difficulty(),
                            golden.recentQuestionIntents(),
                            golden.repetitionPurpose()),
                    golden.learner(),
                    golden.sourceEvidence(),
                    golden.groundingMode(),
                    AiOutputContract.QUESTION_GENERATION);
            entries.add(execute(
                    liveProvider,
                    golden.caseId(),
                    golden.reviewerNotes(),
                    request,
                    requestPacer,
                    value -> semanticEvaluator.evaluate(golden, (QuestionGenerationResult) value)));
        }
        for (GoldenAiDataset.ResponseEvaluationCase golden : dataset.responseEvaluations()) {
            AiTaskRequest<ResponseEvaluationInput> request = request(
                    AiTaskType.RESPONSE_EVALUATION,
                    new ResponseEvaluationInput(
                            golden.question(),
                            golden.expectedConcepts(),
                            golden.expectedAnswer(),
                            golden.studentResponse()),
                    golden.learner(),
                    golden.sourceEvidence(),
                    golden.groundingMode(),
                    AiOutputContract.RESPONSE_EVALUATION,
                    responsePrompt);
            entries.add(execute(
                    liveProvider,
                    golden.caseId(),
                    golden.reviewerNotes(),
                    request,
                    requestPacer,
                    value -> semanticEvaluator.evaluate(golden, (ResponseEvaluationResult) value)));
        }

        entries = entries.stream().map(entry -> withReview(entry, identity, reviewFile)).toList();
        Path reportDirectory = Path.of("target", "ai-golden-evaluation");
        Files.createDirectories(reportDirectory);
        Path reportPath = reportDirectory.resolve(liveProvider.reportName() + ".json");
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(
                reportPath.toFile(),
                new EvaluationReport(
                        UUID.randomUUID().toString(),
                        dataset.version(),
                        liveProvider.providerId().name(),
                        liveProvider.configuredModel(),
                        entries.stream().allMatch(GoldenAiLiveEvaluationRunner::automatedSuccess) ? PASS : FAIL,
                        GoldenAiQualification.overall(entries, requiredCases, identity, reviewFile),
                        identity,
                        reviewFile.acceptance(),
                        entries));
        // Retain each run separately as well as the compatible latest-provider filename.
        Files.copy(reportPath, reportDirectory.resolve(liveProvider.reportName() + "-" + UUID.randomUUID() + ".json"));

        assertEvidenceCollection(entries, reportPath);
    }

    @Test
    void reviewsRetainedEvidenceWhenExplicitlyRequested() throws Exception {
        String retainedPath = System.getenv("HIPPOCAMPUS_LIVE_AI_GOLDEN_REVIEW_REPORT");
        Assumptions.assumeTrue(retainedPath != null && !retainedPath.isBlank());
        Path input = Path.of(retainedPath);
        EvaluationReport report = new ObjectMapper().readValue(input.toFile(), EvaluationReport.class);
        var responsePrompt = responseEvaluationPrompt(report.qualificationIdentity().inputs().get("prompt"));
        var currentIdentity = GoldenAiQualification.currentIdentity(report.provider(), report.configuredModel(), responsePrompt);
        Set<String> required = new GoldenAiDatasetLoader().loadAll().responseEvaluations().stream()
                .map(GoldenAiDataset.ResponseEvaluationCase::caseId).collect(java.util.stream.Collectors.toSet());
        var reviews = GoldenAiQualification.read(Path.of(requiredEnvironment("HIPPOCAMPUS_LIVE_AI_GOLDEN_REVIEW_FILE")), required);
        EvaluationReport reviewed = reviewRetainedReport(report, currentIdentity, reviews, required);
        Path output = input.resolveSibling(input.getFileName() + ".reviewed-" + UUID.randomUUID() + ".json");
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.toFile(), reviewed);
        assertEvidenceCollection(reviewed.cases(), output);
    }

    static EvaluationReport reviewRetainedReport(EvaluationReport report, GoldenAiQualification.Identity currentIdentity,
            GoldenAiQualification.ReviewFile reviews, Set<String> required) {
        if (!currentIdentity.equals(report.qualificationIdentity())) {
            throw new IllegalArgumentException("Retained run has stale configuration; recollect current evidence");
        }
        GoldenAiQualification.validate(reviews, required);
        if (!"v5".equals(report.datasetVersion()) || report.runId() == null || report.runId().isBlank()
                || report.cases() == null || report.cases().isEmpty()) {
            throw new IllegalArgumentException("Incomplete retained qualification report");
        }
        Set<String> seen = new java.util.HashSet<>();
        for (ReportEntry entry : report.cases()) {
            if (!entry.task().equals("RESPONSE_EVALUATION") || !required.contains(entry.caseId())
                    || !seen.add(entry.caseId()) || !currentIdentity.equals(entry.qualificationIdentity())
                    || !report.provider().equals(entry.provider()) || !report.configuredModel().equals(entry.model())
                    || entry.evidenceCollectionStatus() == null || entry.contractStatus() == null
                    || entry.semanticMatcherStatus() == null || entry.failedRules() == null || entry.diagnosticMetadata() == null
                    || (entry.contractStatus() == PASS && (entry.validatedStructuredOutput() == null
                    || !GoldenAiQualification.outputIdentity(entry.validatedStructuredOutput()).equals(entry.outputIdentity())))
                    || (entry.evidenceCollectionStatus() == PASS && (entry.contractStatus() != PASS
                    || (entry.semanticMatcherStatus() != PASS && entry.semanticMatcherStatus() != FAIL)))) {
                throw new IllegalArgumentException("Incomplete or inconsistent retained case evidence");
            }
        }
        var entries = report.cases().stream().map(entry -> withReview(entry, currentIdentity, reviews)).toList();
        return new EvaluationReport(report.runId(), report.datasetVersion(), report.provider(), report.configuredModel(),
                entries.stream().allMatch(GoldenAiLiveEvaluationRunner::automatedSuccess) ? PASS : FAIL,
                GoldenAiQualification.overall(entries, required, currentIdentity, reviews), currentIdentity, reviews.acceptance(), entries);
    }

    static boolean automatedSuccess(ReportEntry entry) {
        // Layered live qualification applies only to response evaluation; preserve other task gates.
        return entry.task().equals(AiTaskType.RESPONSE_EVALUATION.name())
                ? entry.evidenceCollectionStatus() == PASS : entry.passed();
    }

    static void assertEvidenceCollection(List<ReportEntry> entries, Path reportPath) {
        List<ReportEntry> failures = entries.stream().filter(entry -> !automatedSuccess(entry)).toList();
        assertTrue(
                failures.isEmpty(),
                () -> failures.size() + " Golden AI evidence-collection cases failed; inspect " + reportPath);
    }

    static ReportEntry withReview(ReportEntry entry, GoldenAiQualification.Identity identity,
                                 GoldenAiQualification.ReviewFile reviews) {
        String outputIdentity = entry.validatedStructuredOutput() == null ? null
                : GoldenAiQualification.outputIdentity(entry.validatedStructuredOutput());
        var review = GoldenAiQualification.review(entry.caseId(), identity, outputIdentity, entry.contractStatus(), reviews);
        return new ReportEntry(entry.task(), entry.caseId(), entry.model(), entry.passed(), entry.failedRules(),
                entry.validatedStructuredOutput(), entry.reviewerNotes(), entry.diagnosticMetadata(),
                entry.provider(), entry.evidenceCollectionStatus(), entry.contractStatus(), entry.semanticMatcherStatus(),
                review.semanticReviewStatus(), review.semanticReviewRationale(), review.reviewEvidence(),
                identity, outputIdentity, entry.evidenceCollectionStatus() == FAIL ? FAIL : review.qualificationStatus());
    }

    ReportEntry execute(
            LiveProvider liveProvider,
            String caseId,
            String reviewerNotes,
            AiTaskRequest<?> request,
            RequestPacer requestPacer,
            Function<Object, GoldenAiSemanticEvaluator.Result> evaluate) {
        QualificationExecution execution = new QualificationExecution(
                liveProvider, requestPacer, request);
        ValidatedAiResult<?> validated = null;
        try {
            validated = execution.execute(request);
            GoldenAiSemanticEvaluator.Result semantic = evaluate.apply(validated.result());
            return new ReportEntry(
                    request.taskType().name(),
                    caseId,
                    validated.executionMetadata().model(),
                    semantic.passed(),
                    semantic.failedRules(),
                    validated.result(),
                    reviewerNotes,
                    execution.diagnosticMetadata(request), liveProvider.providerId().name(),
                    PASS, PASS, semantic.passed() ? PASS : FAIL, PENDING,
                    "No human review supplied", null, null, null, PENDING);
        } catch (RuntimeException exception) {
            Throwable failure = normalizedFailure(exception);
            Map<String, String> executionMetadata = execution.diagnosticMetadata(request);
            if (validated != null) {
                // A matcher/report failure is an objective evidence failure, not contract rejection.
                return new ReportEntry(request.taskType().name(), caseId, validated.executionMetadata().model(),
                        false, List.of("evaluation-runner:" + failure.getClass().getSimpleName()), validated.result(),
                        reviewerNotes, executionMetadata, liveProvider.providerId().name(), FAIL, PASS, NOT_RUN,
                        PENDING, "Evidence generation failed after production validation", null, null, null, FAIL);
            }
            if (failure instanceof AiSchemaValidationException schemaFailure) {
                Map<String, String> metadata = new LinkedHashMap<>(executionMetadata);
                metadata.put("failureStage", execution.adapter.structuredRepairUsed() ? "REPAIR" : "INITIAL");
                metadata.put("schemaFailure", schemaFailure.reason().name());
                metadata.putAll(schemaValidationDiagnosticMetadata(schemaFailure));
                return failedEntry(
                        request, caseId, liveProvider.configuredModel(),
                        "schema-validation:" + schemaFailure.reason(), reviewerNotes,
                        Map.copyOf(metadata), liveProvider.providerId().name());
            }
            if (failure instanceof ProviderExecutionException providerFailure) {
                Map<String, String> metadata = new LinkedHashMap<>(executionMetadata);
                metadata.put("failureStage", execution.adapter.structuredRepairUsed() ? "REPAIR" : "INITIAL");
                return failedEntry(
                        request, caseId, liveProvider.configuredModel(),
                        "provider-execution:" + providerFailure.failureType(), reviewerNotes,
                        Map.copyOf(metadata), liveProvider.providerId().name());
            }
            return failedEntry(
                    request, caseId, liveProvider.configuredModel(),
                    "evaluation-runner:" + failure.getClass().getSimpleName(), reviewerNotes,
                    executionMetadata, liveProvider.providerId().name());
        } finally {
            execution.close();
        }
    }

    private static Throwable normalizedFailure(Throwable failure) {
        Throwable normalized = failure;
        while ((normalized instanceof CompletionException
                        || normalized instanceof ProviderExecutionFailure)
                && normalized.getCause() != null) {
            normalized = normalized.getCause();
        }
        return normalized;
    }

    static Map<String, String> schemaValidationDiagnosticMetadata(
            AiSchemaValidationException exception) {
        return exception.aggregationFailureReason()
                .map(reason -> Map.of("responseEvaluationAggregationReason", reason.name()))
                .orElseGet(Map::of);
    }

    private static ReportEntry failedEntry(
            AiTaskRequest<?> request,
            String caseId,
            String model,
            String rule,
            String reviewerNotes,
            Map<String, String> diagnosticMetadata, String provider) {
        return new ReportEntry(
                request.taskType().name(), caseId, model, false, List.of(rule), null, reviewerNotes,
                diagnosticMetadata, provider, FAIL, FAIL, NOT_RUN, PENDING,
                "No contract-valid output", null, null, null, FAIL);
    }

    private static LiveProvider liveProvider() {
        String provider = requiredEnvironment("HIPPOCAMPUS_LIVE_AI_GOLDEN_PROVIDER");
        return switch (provider) {
            case "gemini" -> geminiProvider();
            case "ollama-cloud" -> ollamaProvider();
            default -> throw new IllegalArgumentException("unsupported live Golden AI provider");
        };
    }

    private static LiveProvider geminiProvider() {
        String apiKey = requiredEnvironment("GEMINI_API_KEY");
        String model = requiredEnvironment("HIPPOCAMPUS_GEMINI_SMOKE_MODEL");
        Client client = Client.builder().apiKey(apiKey).build();
        ChatModel chatModel = GoogleGenAiChatModel.builder()
                .genAiClient(client)
                .options(GoogleGenAiChatOptions.builder().model(model).build())
                .retryTemplate(new RetryTemplate(RetryPolicy.withMaxRetries(0)))
                .build();
        return new LiveProvider(
                ProviderId.GEMINI, model, new GeminiProviderAdapter(chatModel), "gemini");
    }

    private static LiveProvider ollamaProvider() {
        String apiKey = requiredEnvironment("OLLAMA_API_KEY");
        String model = requiredEnvironment("HIPPOCAMPUS_OLLAMA_CLOUD_SMOKE_MODEL");
        RestClient client = RestClient.builder()
                .baseUrl("https://ollama.com/api")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
        return new LiveProvider(
                ProviderId.OLLAMA_CLOUD,
                model,
                new OllamaCloudProviderAdapter(client),
                "ollama-cloud");
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for live Golden AI evaluation");
        }
        return value;
    }

    static GoldenAiDataset.All selectedDataset(GoldenAiDataset.All dataset, String task) {
        return selectedDataset(dataset, GoldenTask.parse(task));
    }

    static GoldenAiDataset.All selectedDataset(GoldenAiDataset.All dataset, String task, String caseId) {
        GoldenAiDataset.All selected = selectedDataset(dataset, task);
        if (caseId == null || caseId.isBlank()) {
            return selected;
        }
        String id = caseId.trim();
        GoldenAiDataset.All filtered = new GoldenAiDataset.All(selected.version(),
                selected.explanations().stream().filter(value -> value.caseId().equals(id)).toList(),
                selected.questions().stream().filter(value -> value.caseId().equals(id)).toList(),
                selected.responseEvaluations().stream().filter(value -> value.caseId().equals(id)).toList());
        if (filtered.explanations().size() + filtered.questions().size()
                + filtered.responseEvaluations().size() != 1) {
            throw new IllegalArgumentException(CASE_ENVIRONMENT + " must identify exactly one case in the selected task");
        }
        return filtered;
    }

    static Map<String, String> providerDiagnosticMetadata(String stage, ProviderExecutionResult result) {
        Map<String, String> metadata = new LinkedHashMap<>();
        // Allow-list provider enum values: arbitrary provider strings must never enter the report.
        result.finishReason().ifPresent(reason -> metadata.put(stage + "FinishReason",
                Set.of("STOP", "MAX_TOKENS", "SAFETY", "RECITATION", "OTHER", "BLOCKLIST",
                        "PROHIBITED_CONTENT", "SPII", "MALFORMED_FUNCTION_CALL", "FINISH_REASON_UNSPECIFIED",
                        "UNEXPECTED_TOOL_CALL", "TOO_MANY_TOOL_CALLS", "IMAGE_SAFETY", "IMAGE_PROHIBITED_CONTENT",
                        "IMAGE_RECITATION", "IMAGE_OTHER", "NO_IMAGE").contains(reason) ? reason : "UNKNOWN"));
        result.usage().inputTokens().ifPresent(value -> metadata.put(stage + "InputTokens", value.toString()));
        result.usage().outputTokens().ifPresent(value -> metadata.put(stage + "OutputTokens", value.toString()));
        metadata.put(stage + "ResponseCharacters", Integer.toString(result.rawContent().length()));
        metadata.put(stage + "LatencyMillis", Long.toString(result.latency().toMillis()));
        return Map.copyOf(metadata);
    }

    static Map<String, String> judgmentDiagnosticMetadata(ProviderExecutionResult result, int expectedCount) {
        Map<String, String> metadata = new LinkedHashMap<>();
        try {
            JsonNode judgments = new ObjectMapper().readTree(result.rawContent()).path("judgments");
            if (judgments.isArray()) {
                metadata.put("repairJudgmentCount", Integer.toString(judgments.size()));
                Set<Integer> indexes = new java.util.TreeSet<>();
                for (JsonNode judgment : judgments) {
                    JsonNode index = judgment.path("expectedConceptIndex");
                    if (index.isIntegralNumber() && index.canConvertToInt()
                            && index.intValue() >= 0 && index.intValue() < expectedCount) {
                        indexes.add(index.intValue());
                        JsonNode misconceptions = judgment.path("demonstratedMisconceptions");
                        if (!metadata.containsKey("repairInvalidExpectedConceptIndex")
                                && "MISSING".equals(judgment.path("status").asText())
                                && misconceptions.isArray() && !misconceptions.isEmpty()) {
                            // Observed invariant violation only: expose bounded counts, never text.
                            metadata.put("repairInvalidExpectedConceptIndex", Integer.toString(index.intValue()));
                            metadata.put("repairInvalidJudgmentStatus", "MISSING");
                            for (String field : List.of("studentClaims", "supportedComponents", "missingComponents", "demonstratedMisconceptions")) {
                                JsonNode components = judgment.path(field);
                                if (components.isArray()) {
                                    String label = switch (field) {
                                        case "studentClaims" -> "StudentClaim";
                                        case "supportedComponents" -> "SupportedComponent";
                                        case "missingComponents" -> "MissingComponent";
                                        default -> "Misconception";
                                    };
                                    metadata.put("repairInvalid" + label + "Count", Integer.toString(Math.min(components.size(), 10_000)));
                                }
                            }
                        }
                    }
                }
                // Observed indexes only, not a claim that these untrusted judgments are valid.
                metadata.put("repairObservedExpectedConceptIndexes", indexes.toString());
            }
        } catch (tools.jackson.core.JacksonException ignored) {
            // Malformed output has no parseable coverage; never include parser messages/content.
        }
        return Map.copyOf(metadata);
    }

    private static GoldenAiDataset.All selectedDataset(
            GoldenAiDataset.All dataset, GoldenTask task) {
        return switch (task) {
            case ALL -> dataset;
            case EXPLANATION -> new GoldenAiDataset.All(
                    dataset.version(), dataset.explanations(), List.of(), List.of());
            case QUESTION_GENERATION -> new GoldenAiDataset.All(
                    dataset.version(), List.of(), dataset.questions(), List.of());
            case RESPONSE_EVALUATION -> new GoldenAiDataset.All(
                    dataset.version(), List.of(), List.of(), dataset.responseEvaluations());
        };
    }

    static long parseDelayMillis(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            long delayMillis = Long.parseLong(value);
            if (delayMillis < 0L) {
                throw new IllegalArgumentException(DELAY_ENVIRONMENT + " must be zero or positive");
            }
            return delayMillis;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    DELAY_ENVIRONMENT + " must be a whole number of milliseconds", exception);
        }
    }

    private static void sleep(long delayMillis) {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("live Golden AI request pacing interrupted", exception);
        }
    }

    static <C extends AiTaskContext> AiTaskRequest<C> request(
            AiTaskType taskType,
            C taskContext,
            GoldenAiDataset.Learner learner,
            List<GoldenAiDataset.Source> sources,
            GroundingMode groundingMode,
            AiOutputContract outputContract) {
        return request(taskType, taskContext, learner, sources, groundingMode, outputContract,
                PromptId.RESPONSE_EVALUATION_V6);
    }

    static PromptId responseEvaluationPrompt(String value) {
        if (value == null || value.isBlank()) return PromptId.RESPONSE_EVALUATION_V6;
        if (value.equals(PromptId.RESPONSE_EVALUATION_V6.name())) return PromptId.RESPONSE_EVALUATION_V6;
        if (value.equals(PromptId.RESPONSE_EVALUATION_V7.name())) return PromptId.RESPONSE_EVALUATION_V7;
        throw new IllegalArgumentException(RESPONSE_PROMPT_ENVIRONMENT + " must be RESPONSE_EVALUATION_V6 or RESPONSE_EVALUATION_V7");
    }

    static <C extends AiTaskContext> AiTaskRequest<C> request(
            AiTaskType taskType,
            C taskContext,
            GoldenAiDataset.Learner learner,
            List<GoldenAiDataset.Source> sources,
            GroundingMode groundingMode,
            AiOutputContract outputContract,
            PromptId responsePrompt) {
        responseEvaluationPrompt(responsePrompt.name());
        String promptVersion = switch (taskType) {
            case EXPLANATION -> PromptId.EXPLANATION_V2.name();
            case QUESTION_GENERATION -> PromptId.QUESTION_GENERATION_V2.name();
            case RESPONSE_EVALUATION -> responsePrompt.name();
            default -> taskType.name() + "_V1";
        };
        return new AiTaskRequest<>(
                taskType,
                promptVersion,
                new LearnerContext(
                        learner.learningState(),
                        learner.topicExposure(),
                        learner.difficultyDirection(),
                        learner.relevantEvidence(),
                        learner.relevantMisconceptions()),
                taskContext,
                evidencePackage(sources, groundingMode),
                groundingMode,
                outputContract);
    }

    private static EvidencePackage evidencePackage(
            List<GoldenAiDataset.Source> sources, GroundingMode groundingMode) {
        List<EvidenceChunk> chunks = new ArrayList<>();
        List<EvidenceSourceReference> references = new ArrayList<>();
        for (int index = 0; index < sources.size(); index++) {
            int rank = index + 1;
            GoldenAiDataset.Source source = sources.get(index);
            UUID chunkId = UUID.fromString(source.sourceId());
            UUID materialId = stableId(source.sourceId() + ":material");
            UUID versionId = stableId(source.sourceId() + ":version");
            UUID nodeId = stableId(source.sourceId() + ":node");
            chunks.add(new EvidenceChunk(
                    rank,
                    chunkId,
                    materialId,
                    versionId,
                    nodeId,
                    rank,
                    source.content(),
                    rank,
                    rank,
                    List.of("Synthetic Golden Evidence"),
                    "TEXT",
                    "GOLDEN_DATASET",
                    "HIGH"));
            references.add(new EvidenceSourceReference(
                    EvidenceReferenceKind.CHUNK,
                    materialId,
                    versionId,
                    nodeId,
                    chunkId,
                    null,
                    rank));
        }
        RetrievalQuality quality = chunks.isEmpty() ? RetrievalQuality.FAILED : RetrievalQuality.STRONG;
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
                        chunks.stream().map(EvidenceChunk::materialId).collect(java.util.stream.Collectors.toSet()),
                        Set.of(),
                        quality));
    }

    private static UUID stableId(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private enum GoldenTask {
        ALL("all"),
        EXPLANATION("explanation"),
        QUESTION_GENERATION("question-generation"),
        RESPONSE_EVALUATION("response-evaluation");

        private final String environmentValue;

        GoldenTask(String environmentValue) {
            this.environmentValue = environmentValue;
        }

        private static GoldenTask parse(String value) {
            String normalized = value == null || value.isBlank() ? "all" : value.trim();
            for (GoldenTask task : values()) {
                if (task.environmentValue.equals(normalized)) {
                    return task;
                }
            }
            throw new IllegalArgumentException("unsupported live Golden AI task: " + value);
        }
    }

    static final class RequestPacer {
        private final long delayMillis;
        private final LongConsumer sleeper;
        private boolean requestStarted;

        RequestPacer(long delayMillis, LongConsumer sleeper) {
            if (delayMillis < 0L) {
                throw new IllegalArgumentException("delayMillis must be zero or positive");
            }
            this.delayMillis = delayMillis;
            this.sleeper = sleeper;
        }

        void beforeRequest() {
            if (requestStarted && delayMillis > 0L) {
                sleeper.accept(delayMillis);
            }
            requestStarted = true;
        }
    }

    record LiveProvider(
            ProviderId providerId,
            String configuredModel,
            AiProviderAdapter adapter,
            String reportName) {}

    record EvaluationReport(
            String runId,
            String datasetVersion,
            String provider,
            String configuredModel,
            GoldenAiQualification.Status evidenceCollectionStatus,
            GoldenAiQualification.Status qualificationStatus,
            GoldenAiQualification.Identity qualificationIdentity,
            GoldenAiQualification.Acceptance acceptanceEvidence,
            List<ReportEntry> cases) {}

    record ReportEntry(
            String task,
            String caseId,
            String model,
            boolean passed,
            List<String> failedRules,
            Object validatedStructuredOutput,
            String reviewerNotes,
            Map<String, String> diagnosticMetadata,
            String provider,
            GoldenAiQualification.Status evidenceCollectionStatus,
            GoldenAiQualification.Status contractStatus,
            GoldenAiQualification.Status semanticMatcherStatus,
            GoldenAiQualification.Status semanticReviewStatus,
            String semanticReviewRationale,
            GoldenAiQualification.Review reviewEvidence,
            GoldenAiQualification.Identity qualificationIdentity,
            String outputIdentity,
            GoldenAiQualification.Status qualificationStatus) {}

    private static final class QualificationExecution implements AutoCloseable {
        private final PromptContextBuilder promptBuilder = new PromptContextBuilder(
                new PromptTemplateRegistry(), new Utf8ByteLengthPromptTokenCounter());
        private final AiOutputValidator outputValidator =
                new AiOutputValidator(new JacksonAiStructuredOutputDecoder());
        private final TrackingProviderAdapter adapter;
        private final AiRequestManager requestManager;
        private final AiExecutionOrchestrator orchestrator;

        private QualificationExecution(
                LiveProvider liveProvider,
                RequestPacer requestPacer,
                AiTaskRequest<?> request) {
            adapter = new TrackingProviderAdapter(
                    liveProvider.adapter(), requestPacer, liveProvider.configuredModel());
            AiRequestManagerPolicy policy = new AiRequestManagerPolicy(
                    1,
                    1,
                    Duration.ofMinutes(3),
                    1,
                    Duration.ofMillis(1),
                    Duration.ofMillis(1),
                    2,
                    Duration.ofSeconds(1));
            Map<ProviderId, AiRequestManagerPolicy> policies = new EnumMap<>(ProviderId.class);
            policies.put(liveProvider.providerId(), policy);
            requestManager = new AiRequestManager(
                    List.of(adapter), policies, 2, AiRequestTelemetry.NONE);
            orchestrator = new AiExecutionOrchestrator(
                    promptBuilder,
                    () -> new AuthenticatedUser(QUALIFICATION_USER_ID),
                    new ProviderRouter(),
                    requestManager,
                    outputValidator,
                    new AiSourceReferenceValidator(new GoldenSourceReferenceRepository(request)),
                    NoOpDiagnostics.INSTANCE,
                    AiRequestTelemetry.NONE);
        }

        private ValidatedAiResult<?> execute(AiTaskRequest<?> request) {
            // Test-harness eligibility permits collecting evidence for an unqualified candidate.
            // This does not populate deployment evaluation-approved-tasks or authorize runtime routing.
            Set<AiTaskType> approvedTasks = Set.of(
                    request.taskType(), AiTaskType.STRUCTURED_OUTPUT_REPAIR);
            ProviderRoutingCandidate candidate = new ProviderRoutingCandidate(
                    adapter.providerId(),
                    adapter.modelId,
                    approvedTasks,
                    approvedTasks,
                    true,
                    true,
                    true,
                    1,
                    1,
                    1);
            return orchestrator.execute(
                            request,
                            AiRequestPriority.INTERACTIVE_EVALUATION,
                            PROMPT_BUDGET,
                            List.of(candidate),
                            ProviderRoutingPreference.LATENCY_THEN_COST)
                    .join();
        }

        private Map<String, String> diagnosticMetadata(AiTaskRequest<?> originalRequest) {
            Map<String, String> metadata = new LinkedHashMap<>();
            adapter.initialResult().ifPresent(initial ->
                    metadata.putAll(providerDiagnosticMetadata("initial", initial)));
            if (!adapter.structuredRepairUsed()) {
                return Map.copyOf(metadata);
            }
            metadata.put("structuredRepairUsed", "true");
            adapter.results.stream().skip(1).findFirst().ifPresent(repair -> {
                metadata.putAll(providerDiagnosticMetadata("repair", repair));
                if (originalRequest.taskContext() instanceof ResponseEvaluationInput input) {
                    metadata.putAll(judgmentDiagnosticMetadata(repair, input.expectedConcepts().size()));
                }
            });
            adapter.initialResult().ifPresent(initial -> {
                try {
                    outputValidator.validate(
                            initial,
                            originalRequest.outputContract(),
                            originalRequest.taskContext(),
                            promptBuilder.build(originalRequest, PROMPT_BUDGET).taskPromptId());
                } catch (AiSchemaValidationException exception) {
                    metadata.put("initialSchemaFailure", exception.reason().name());
                    metadata.putAll(schemaValidationDiagnosticMetadata(exception));
                }
            });
            return Map.copyOf(metadata);
        }

        @Override
        public void close() {
            requestManager.close();
        }
    }

    private static final class TrackingProviderAdapter implements AiProviderAdapter {
        private final AiProviderAdapter delegate;
        private final RequestPacer requestPacer;
        private final String modelId;
        private final List<ProviderExecutionRequest> requests = new CopyOnWriteArrayList<>();
        private final List<ProviderExecutionResult> results = new CopyOnWriteArrayList<>();

        private TrackingProviderAdapter(
                AiProviderAdapter delegate, RequestPacer requestPacer, String modelId) {
            this.delegate = delegate;
            this.requestPacer = requestPacer;
            this.modelId = modelId;
        }

        @Override
        public ProviderId providerId() {
            return delegate.providerId();
        }

        @Override
        public boolean supports(AiTaskType taskType) {
            return delegate.supports(taskType);
        }

        @Override
        public ProviderExecutionResult execute(ProviderExecutionRequest request) {
            requestPacer.beforeRequest();
            requests.add(request);
            ProviderExecutionResult result = delegate.execute(request);
            results.add(result);
            return result;
        }

        @Override
        public ProviderEventStream stream(ProviderExecutionRequest request) {
            return delegate.stream(request);
        }

        private boolean structuredRepairUsed() {
            return requests.stream().anyMatch(
                    request -> request.taskType() == AiTaskType.STRUCTURED_OUTPUT_REPAIR);
        }

        private Optional<ProviderExecutionResult> initialResult() {
            return results.stream().findFirst();
        }
    }

    private static final class GoldenSourceReferenceRepository implements SourceReferenceRepository {
        private final Map<UUID, EvidenceChunk> chunks;

        private GoldenSourceReferenceRepository(AiTaskRequest<?> request) {
            chunks = request.evidencePackage().chunks().stream()
                    .collect(java.util.stream.Collectors.toMap(EvidenceChunk::chunkId, value -> value));
        }

        @Override
        public Optional<SourceReferenceSeed> findAuthorizedTarget(
                UUID userId, SourceReferenceTarget target) {
            if (!QUALIFICATION_USER_ID.equals(userId)
                    || !(target instanceof ChunkSourceTarget chunkTarget)) {
                return Optional.empty();
            }
            EvidenceChunk chunk = chunks.get(chunkTarget.chunkId());
            if (chunk == null
                    || !chunk.materialId().equals(chunkTarget.materialId())
                    || !chunk.materialVersionId().equals(chunkTarget.materialVersionId())) {
                return Optional.empty();
            }
            return Optional.of(new SourceReferenceSeed(
                    chunk.materialId(),
                    chunk.materialVersionId(),
                    chunk.documentNodeId(),
                    chunk.chunkId(),
                    null,
                    chunk.pageStart(),
                    "Synthetic Golden Evidence",
                    "Synthetic Golden Evidence"));
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

    private enum NoOpDiagnostics implements AiDiagnosticsPersistence {
        INSTANCE;

        @Override
        public void record(AiRequestDiagnostic request) {}

        @Override
        public void record(AiRequestDiagnostic request, ProviderUsageDiagnostic usage) {}
    }
}
