package com.hippocampus.ai.evaluation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.genai.Client;
import com.hippocampus.ai.application.prompt.PromptContext;
import com.hippocampus.ai.application.prompt.PromptContextBuilder;
import com.hippocampus.ai.application.prompt.PromptId;
import com.hippocampus.ai.application.prompt.PromptTemplateRegistry;
import com.hippocampus.ai.application.prompt.PromptTokenBudget;
import com.hippocampus.ai.application.provider.AiProviderAdapter;
import com.hippocampus.ai.application.provider.ProviderExecutionException;
import com.hippocampus.ai.application.provider.ProviderExecutionRequest;
import com.hippocampus.ai.application.provider.ProviderExecutionResult;
import com.hippocampus.ai.application.routing.ProviderId;
import com.hippocampus.ai.application.routing.ProviderRoute;
import com.hippocampus.ai.application.validation.AiOutputValidator;
import com.hippocampus.ai.application.validation.AiSchemaValidationException;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

class GoldenAiLiveEvaluationRunner {

    private static final PromptTokenBudget PROMPT_BUDGET = new PromptTokenBudget(131_072, 2_048);
    private static final String TASK_ENVIRONMENT = "HIPPOCAMPUS_LIVE_AI_GOLDEN_TASK";
    private static final String DELAY_ENVIRONMENT = "HIPPOCAMPUS_LIVE_AI_GOLDEN_DELAY_MS";

    private final GoldenAiSemanticEvaluator semanticEvaluator = new GoldenAiSemanticEvaluator();
    private final PromptContextBuilder promptBuilder = new PromptContextBuilder(
            new PromptTemplateRegistry(), new Utf8ByteLengthPromptTokenCounter());
    private final AiOutputValidator outputValidator =
            new AiOutputValidator(new JacksonAiStructuredOutputDecoder());

    @Test
    void runsP7GoldenEvaluationWhenExplicitlyEnabled() throws Exception {
        Assumptions.assumeTrue(
                "true".equalsIgnoreCase(System.getenv("HIPPOCAMPUS_LIVE_AI_GOLDEN")));

        GoldenTask task = GoldenTask.parse(System.getenv(TASK_ENVIRONMENT));
        long delayMillis = parseDelayMillis(System.getenv(DELAY_ENVIRONMENT));
        LiveProvider liveProvider = liveProvider();
        GoldenAiDataset.All dataset = selectedDataset(new GoldenAiDatasetLoader().loadAll(), task);
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
                    AiOutputContract.RESPONSE_EVALUATION);
            entries.add(execute(
                    liveProvider,
                    golden.caseId(),
                    golden.reviewerNotes(),
                    request,
                    requestPacer,
                    value -> semanticEvaluator.evaluate(golden, (ResponseEvaluationResult) value)));
        }

        Path reportDirectory = Path.of("target", "ai-golden-evaluation");
        Files.createDirectories(reportDirectory);
        Path reportPath = reportDirectory.resolve(liveProvider.reportName() + ".json");
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(
                reportPath.toFile(),
                new EvaluationReport(
                        dataset.version(),
                        liveProvider.providerId().name(),
                        liveProvider.configuredModel(),
                        entries));

        List<ReportEntry> failures = entries.stream().filter(entry -> !entry.passed()).toList();
        assertTrue(
                failures.isEmpty(),
                () -> failures.size() + " Golden AI cases failed; inspect " + reportPath);
    }

    private ReportEntry execute(
            LiveProvider liveProvider,
            String caseId,
            String reviewerNotes,
            AiTaskRequest<?> request,
            RequestPacer requestPacer,
            Function<Object, GoldenAiSemanticEvaluator.Result> evaluate) {
        requestPacer.beforeRequest();
        PromptContext prompt = promptBuilder.build(request, PROMPT_BUDGET);
        ProviderExecutionRequest providerRequest = new ProviderExecutionRequest(
                request.taskType(),
                request.outputContract(),
                prompt,
                new ProviderRoute.Target(liveProvider.providerId(), liveProvider.configuredModel()));
        try {
            ProviderExecutionResult providerResult = liveProvider.adapter().execute(providerRequest);
            ValidatedAiResult<?> validated = outputValidator.validate(
                    providerResult,
                    request.outputContract(),
                    request.taskContext(),
                    prompt.taskPromptId());
            GoldenAiSemanticEvaluator.Result semantic = evaluate.apply(validated.result());
            return new ReportEntry(
                    request.taskType().name(),
                    caseId,
                    providerResult.modelId(),
                    semantic.passed(),
                    semantic.failedRules(),
                    validated.result(),
                    reviewerNotes);
        } catch (AiSchemaValidationException exception) {
            return failedEntry(
                    request, caseId, liveProvider.configuredModel(),
                    "schema-validation:" + exception.reason(), reviewerNotes);
        } catch (ProviderExecutionException exception) {
            return failedEntry(
                    request, caseId, liveProvider.configuredModel(),
                    "provider-execution:" + exception.failureType(), reviewerNotes);
        } catch (RuntimeException exception) {
            return failedEntry(
                    request, caseId, liveProvider.configuredModel(),
                    "evaluation-runner:" + exception.getClass().getSimpleName(), reviewerNotes);
        }
    }

    private static ReportEntry failedEntry(
            AiTaskRequest<?> request,
            String caseId,
            String model,
            String rule,
            String reviewerNotes) {
        return new ReportEntry(
                request.taskType().name(), caseId, model, false, List.of(rule), null, reviewerNotes);
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
        String promptVersion = switch (taskType) {
            case EXPLANATION -> PromptId.EXPLANATION_V2.name();
            case QUESTION_GENERATION -> PromptId.QUESTION_GENERATION_V2.name();
            case RESPONSE_EVALUATION -> PromptId.RESPONSE_EVALUATION_V4.name();
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

    private record LiveProvider(
            ProviderId providerId,
            String configuredModel,
            AiProviderAdapter adapter,
            String reportName) {}

    private record EvaluationReport(
            String datasetVersion,
            String provider,
            String configuredModel,
            List<ReportEntry> cases) {}

    private record ReportEntry(
            String task,
            String caseId,
            String model,
            boolean passed,
            List<String> failedRules,
            Object validatedStructuredOutput,
            String reviewerNotes) {}
}
