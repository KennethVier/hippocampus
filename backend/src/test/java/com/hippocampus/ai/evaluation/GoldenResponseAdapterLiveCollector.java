package com.hippocampus.ai.evaluation;

import static com.hippocampus.ai.evaluation.GoldenResponseAdapterLiveCollector.Status.*;

import com.google.genai.Client;
import com.hippocampus.ai.application.AiExecutionOrchestrator;
import com.hippocampus.ai.application.prompt.PromptContextBuilder;
import com.hippocampus.ai.application.prompt.PromptId;
import com.hippocampus.ai.application.prompt.PromptTemplateRegistry;
import com.hippocampus.ai.application.prompt.PromptTokenBudget;
import com.hippocampus.ai.application.provider.AiProviderAdapter;
import com.hippocampus.ai.application.provider.ProviderEventStream;
import com.hippocampus.ai.application.provider.ProviderExecutionRequest;
import com.hippocampus.ai.application.provider.ProviderExecutionResult;
import com.hippocampus.ai.application.request.AiRequestManager;
import com.hippocampus.ai.application.request.AiRequestManagerPolicy;
import com.hippocampus.ai.application.request.AiRequestPriority;
import com.hippocampus.ai.application.request.AiRequestTelemetry;
import com.hippocampus.ai.application.routing.ProviderId;
import com.hippocampus.ai.application.routing.ProviderRouter;
import com.hippocampus.ai.application.routing.ProviderRoutingCandidate;
import com.hippocampus.ai.application.routing.ProviderRoutingPreference;
import com.hippocampus.ai.application.validation.AiOutputValidator;
import com.hippocampus.ai.application.validation.AiSourceReferenceValidator;
import com.hippocampus.ai.application.validation.ResponseAttributionValidator;
import com.hippocampus.ai.domain.AiOutputContract;
import com.hippocampus.ai.domain.AiTaskContext;
import com.hippocampus.ai.domain.AiTaskType;
import com.hippocampus.ai.domain.Evaluation;
import com.hippocampus.ai.domain.EvaluationCertainty;
import com.hippocampus.ai.domain.RecommendedAction;
import com.hippocampus.ai.domain.ResponseAttributionDiscovery;
import com.hippocampus.ai.domain.ResponseAttributionVerification;
import com.hippocampus.ai.domain.ResponseEvaluationResult;
import com.hippocampus.ai.domain.ValidatedAiResult;
import com.hippocampus.ai.infrastructure.learning.AiResponseEvaluationAdapter;
import com.hippocampus.ai.infrastructure.learning.AiTaskExecutionOptions;
import com.hippocampus.ai.infrastructure.learning.AiTaskExecutionPolicy;
import com.hippocampus.ai.infrastructure.prompt.Utf8ByteLengthPromptTokenCounter;
import com.hippocampus.ai.infrastructure.provider.gemini.GeminiProviderAdapter;
import com.hippocampus.ai.infrastructure.provider.ollama.OllamaCloudProviderAdapter;
import com.hippocampus.ai.infrastructure.validation.JacksonAiStructuredOutputDecoder;
import com.hippocampus.ai.port.AiDiagnosticsPersistence;
import com.hippocampus.ai.port.AiRequestDiagnostic;
import com.hippocampus.ai.port.ProviderUsageDiagnostic;
import com.hippocampus.identity.domain.AuthenticatedUser;
import com.hippocampus.learning.domain.StudyMissionGroundingMode;
import com.hippocampus.learning.port.AuthorizedResponseEvidence;
import com.hippocampus.learning.port.ResponseEvaluationPort;
import com.hippocampus.materials.domain.ChunkSourceTarget;
import com.hippocampus.materials.domain.SourceReference;
import com.hippocampus.materials.domain.SourceReferenceTarget;
import com.hippocampus.materials.port.SourceReferenceRepository;
import com.hippocampus.materials.port.SourceReferenceSeed;
import com.hippocampus.rag.domain.EvidenceChunk;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
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

/**
 * Test-only live Golden evaluation collector for the production two-pass
 * {@link AiResponseEvaluationAdapter}, using Golden dataset v7.
 *
 * <p>Never executes automatically. Requires explicit environment authorization:
 * {@code HIPPOCAMPUS_LIVE_AI_GOLDEN_ADAPTER=true}.
 */
public class GoldenResponseAdapterLiveCollector {

    public static final String AUTHORIZATION_ENVIRONMENT = "HIPPOCAMPUS_LIVE_AI_GOLDEN_ADAPTER";
    public static final String PROVIDER_ENVIRONMENT = "HIPPOCAMPUS_LIVE_AI_GOLDEN_PROVIDER";
    public static final String CASE_ENVIRONMENT = "HIPPOCAMPUS_LIVE_AI_GOLDEN_CASE";
    public static final String DELAY_ENVIRONMENT = "HIPPOCAMPUS_LIVE_AI_GOLDEN_DELAY_MS";
    public static final String MAX_REQUESTS_ENVIRONMENT = "HIPPOCAMPUS_LIVE_AI_GOLDEN_MAX_REQUESTS";
    public static final String MAX_TOKENS_ENVIRONMENT = "HIPPOCAMPUS_LIVE_AI_GOLDEN_MAX_TOKENS";
    public static final String TIMEOUT_SECONDS_ENVIRONMENT = "HIPPOCAMPUS_LIVE_AI_GOLDEN_TIMEOUT_SECONDS";
    public static final String MAX_COST_ENVIRONMENT = "HIPPOCAMPUS_LIVE_AI_GOLDEN_MAX_COST_USD";
    public static final String INPUT_COST_PER_MILLION_ENVIRONMENT = "HIPPOCAMPUS_LIVE_AI_COST_PER_MILLION_INPUT";
    public static final String OUTPUT_COST_PER_MILLION_ENVIRONMENT = "HIPPOCAMPUS_LIVE_AI_COST_PER_MILLION_OUTPUT";

    public static final String DATASET_VERSION = "v7";
    public static final String MEDICAL_REVIEWER_REF = "MED-REV-NEURO-2026-088";
    public static final String MEDICAL_ATTESTATION_SIG_REF = "AUTH-SIG-SHA256-7B93E10C-20261014";

    private static final UUID QUALIFICATION_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000709");
    private static final UUID MATERIAL_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000710");
    private static final UUID MATERIAL_VERSION_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000709");

    public enum Status { PASS, FAIL, PENDING, NOT_RUN }

    public record CollectorLimits(
            int maxRequests,
            long maxTokens,
            long timeoutSeconds,
            BigDecimal maxCostUsd,
            long delayMillis) {

        public CollectorLimits {
            if (maxRequests <= 0 || maxTokens <= 0 || timeoutSeconds <= 0
                    || maxCostUsd == null || maxCostUsd.signum() <= 0 || delayMillis < 0
                    || timeoutSeconds > Long.MAX_VALUE / 1000) {
                throw new IllegalArgumentException("invalid live collector limits");
            }
        }

        public static final int DEFAULT_MAX_REQUESTS = 25;
        public static final long DEFAULT_MAX_TOKENS = 100_000L;
        public static final long DEFAULT_TIMEOUT_SECONDS = 120L;
        public static final BigDecimal DEFAULT_MAX_COST_USD = new BigDecimal("1.00");
        public static final long DEFAULT_DELAY_MILLIS = 0L;

        public static CollectorLimits defaultLimits() {
            return new CollectorLimits(
                    DEFAULT_MAX_REQUESTS,
                    DEFAULT_MAX_TOKENS,
                    DEFAULT_TIMEOUT_SECONDS,
                    DEFAULT_MAX_COST_USD,
                    DEFAULT_DELAY_MILLIS);
        }
    }

    public record CostRates(BigDecimal perMillionInputTokens, BigDecimal perMillionOutputTokens) {
        public CostRates {
            if (perMillionInputTokens == null || perMillionOutputTokens == null
                    || perMillionInputTokens.signum() <= 0 || perMillionOutputTokens.signum() <= 0) {
                throw new IllegalArgumentException("positive model-specific input and output prices are required");
            }
        }
        public static final BigDecimal DEFAULT_INPUT_RATE = new BigDecimal("0.10");
        public static final BigDecimal DEFAULT_OUTPUT_RATE = new BigDecimal("0.40");

        public static CostRates defaults() {
            return new CostRates(DEFAULT_INPUT_RATE, DEFAULT_OUTPUT_RATE);
        }

        public BigDecimal estimate(long inputTokens, long outputTokens) {
            BigDecimal inputCost = BigDecimal.valueOf(inputTokens)
                    .multiply(perMillionInputTokens)
                    .divide(BigDecimal.valueOf(1_000_000), 12, RoundingMode.UP);
            BigDecimal outputCost = BigDecimal.valueOf(outputTokens)
                    .multiply(perMillionOutputTokens)
                    .divide(BigDecimal.valueOf(1_000_000), 12, RoundingMode.UP);
            return inputCost.add(outputCost);
        }
    }

    public record QualificationIdentity(
            String version,
            Map<String, String> inputs,
            String fingerprint) {}

    public record LiveProvider(
            ProviderId providerId,
            String configuredModel,
            AiProviderAdapter adapter,
            String reportName) {}

    public record DiscoveryRecord(
            String promptId,
            String promptVersion,
            boolean executed,
            boolean valid,
            ResponseAttributionDiscovery output,
            Integer inputTokens,
            Integer outputTokens,
            Long latencyMs,
            String finishReason,
            String error) {}

    public record VerificationRecord(
            String promptId,
            String promptVersion,
            boolean executed,
            boolean valid,
            ResponseAttributionVerification output,
            Integer inputTokens,
            Integer outputTokens,
            Long latencyMs,
            String finishReason,
            String error) {}

    public record FinalEvaluationRecord(
            String outcome,
            List<String> correctConcepts,
            List<String> missingConcepts,
            List<String> misconceptions,
            String feedback,
            String certainty,
            String recommendedAction,
            List<String> sourceReferences,
            boolean validated) {}

    public record SemanticMatcherRecord(
            boolean passed,
            List<String> failedRules) {}

    public record CaseReportEntry(
            String caseId,
            String subject,
            String topic,
            String question,
            String studentResponse,
            String reviewerNotes,
            String model,
            String provider,
            DiscoveryRecord discovery,
            VerificationRecord verification,
            FinalEvaluationRecord finalEvaluation,
            SemanticMatcherRecord semanticMatcher,
            Map<String, String> diagnosticMetadata,
            Status evidenceCollectionStatus,
            Status contractStatus,
            Status semanticMatcherStatus,
            Status semanticReviewStatus,
            Status qualificationStatus) {}

    public record CollectorTotals(
            int totalRequests,
            Long totalInputTokens,
            Long totalOutputTokens,
            Long totalTokens,
            long totalLatencyMs,
            BigDecimal totalEstimatedCostUsd) {}

    public record EvaluationReport(
            String runId,
            String datasetVersion,
            String provider,
            String configuredModel,
            String medicalAttestationStatus,
            String medicalAttestationReferences,
            String medicalAttestationNote,
            CollectorLimits limits,
            CollectorTotals totals,
            Status evidenceCollectionStatus,
            Status qualificationStatus,
            QualificationIdentity qualificationIdentity,
            List<CaseReportEntry> cases) {}

    @Test
    void collectsLiveGoldenV7EvidenceWhenExplicitlyAuthorized() throws Exception {
        Assumptions.assumeTrue(isAuthorized(),
                "Live Golden adapter evaluation requires deliberate authorization via "
                        + AUTHORIZATION_ENVIRONMENT + "=true");

        LiveProvider liveProvider = liveProvider();
        CollectorLimits limits = parseLimits();
        CostRates costRates = parseCostRates();
        String caseFilter = System.getenv(CASE_ENVIRONMENT);

        Path reportDirectory = Path.of("target", "ai-golden-evaluation");
        Files.createDirectories(reportDirectory);

        EvaluationReport report = runCollection(liveProvider, limits, costRates, caseFilter);

        Path reportFile = reportDirectory.resolve("adapter-v7-" + liveProvider.reportName()
                + "-" + UUID.randomUUID() + ".json");
        Path latestFile = reportDirectory.resolve("adapter-v7-" + liveProvider.reportName() + ".json");

        ObjectMapper mapper = new ObjectMapper();
        mapper.writerWithDefaultPrettyPrinter().writeValue(reportFile.toFile(), report);
        Files.copy(reportFile, latestFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        assertCollectionSuccess(report, reportFile);
    }

    public static boolean isAuthorized() {
        return "true".equalsIgnoreCase(System.getenv(AUTHORIZATION_ENVIRONMENT));
    }

    public static CollectorLimits parseLimits() {
        int maxRequests = parsePositiveInt(
                System.getenv(MAX_REQUESTS_ENVIRONMENT), CollectorLimits.DEFAULT_MAX_REQUESTS);
        long maxTokens = parsePositiveLong(
                System.getenv(MAX_TOKENS_ENVIRONMENT), CollectorLimits.DEFAULT_MAX_TOKENS);
        long timeoutSeconds = parsePositiveLong(
                System.getenv(TIMEOUT_SECONDS_ENVIRONMENT), CollectorLimits.DEFAULT_TIMEOUT_SECONDS);
        BigDecimal maxCost = parseBigDecimal(
                System.getenv(MAX_COST_ENVIRONMENT), CollectorLimits.DEFAULT_MAX_COST_USD);
        long delayMillis = parseNonNegativeLong(
                System.getenv(DELAY_ENVIRONMENT), CollectorLimits.DEFAULT_DELAY_MILLIS);
        return new CollectorLimits(maxRequests, maxTokens, timeoutSeconds, maxCost, delayMillis);
    }

    public static CostRates parseCostRates() {
        BigDecimal inputRate = parseBigDecimal(requiredEnvironment(INPUT_COST_PER_MILLION_ENVIRONMENT), null);
        BigDecimal outputRate = parseBigDecimal(requiredEnvironment(OUTPUT_COST_PER_MILLION_ENVIRONMENT), null);
        return new CostRates(inputRate, outputRate);
    }

    public static EvaluationReport runCollection(
            LiveProvider liveProvider,
            CollectorLimits limits,
            CostRates costRates,
            String caseFilter) throws IOException {

        Objects.requireNonNull(liveProvider, "liveProvider");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(costRates, "costRates");
        if (liveProvider.configuredModel() == null || liveProvider.configuredModel().isBlank()
                || liveProvider.adapter() == null || liveProvider.adapter().providerId() != liveProvider.providerId()) {
            throw new IllegalArgumentException("explicit model and matching provider adapter are required");
        }

        GoldenAiDataset.All dataset = new GoldenAiDatasetLoader().loadAll(DATASET_VERSION);
        List<GoldenAiDataset.ResponseEvaluationCase> allCases = dataset.responseEvaluations();
        List<GoldenAiDataset.ResponseEvaluationCase> targetCases;

        if (caseFilter != null && !caseFilter.isBlank()) {
            String filter = caseFilter.trim();
            targetCases = allCases.stream().filter(c -> c.caseId().equals(filter)).toList();
            if (targetCases.isEmpty()) {
                throw new IllegalArgumentException(CASE_ENVIRONMENT + " matched no case: " + filter);
            }
        } else {
            targetCases = allCases;
        }

        if (targetCases.size() != 9 && (caseFilter == null || caseFilter.isBlank())) {
            throw new IllegalStateException("Golden v7 response evaluations must contain exactly 9 cases");
        }

        QualificationIdentity identity = currentIdentity(
                liveProvider.providerId().name(), liveProvider.configuredModel(), limits, costRates);
        RequestPacer pacer = new RequestPacer(limits.delayMillis(), GoldenResponseAdapterLiveCollector::sleep);
        GoldenAiSemanticEvaluator semanticEvaluator = new GoldenAiSemanticEvaluator();

        List<CaseReportEntry> entries = new ArrayList<>();
        AtomicInteger totalRequests = new AtomicInteger();
        AtomicLong totalInputTokens = new AtomicLong();
        AtomicLong totalOutputTokens = new AtomicLong();
        AtomicLong totalLatencyMs = new AtomicLong();

        for (GoldenAiDataset.ResponseEvaluationCase golden : targetCases) {
            CaseReportEntry entry = executeCase(
                    golden, liveProvider, limits, costRates, pacer, semanticEvaluator,
                    totalRequests, totalInputTokens, totalOutputTokens, totalLatencyMs);
            entries.add(entry);
            if (entry.diagnosticMetadata().containsKey("limitViolation")) {
                break;
            }
        }

        boolean usageUnknown = entries.stream().anyMatch(entry ->
                "true".equals(entry.diagnosticMetadata().get("usageUnknown")));
        long allTokens = totalInputTokens.get() + totalOutputTokens.get();
        BigDecimal totalCost = costRates.estimate(totalInputTokens.get(), totalOutputTokens.get());

        CollectorTotals totals = new CollectorTotals(
                totalRequests.get(),
                usageUnknown ? null : totalInputTokens.get(),
                usageUnknown ? null : totalOutputTokens.get(),
                usageUnknown ? null : allTokens,
                totalLatencyMs.get(),
                usageUnknown ? null : totalCost);

        boolean allCollectionPass = entries.size() == targetCases.size() && entries.stream()
                .allMatch(e -> e.evidenceCollectionStatus() == PASS);

        return new EvaluationReport(
                UUID.randomUUID().toString(),
                DATASET_VERSION,
                liveProvider.providerId().name(),
                liveProvider.configuredModel(),
                "PENDING",
                MEDICAL_REVIEWER_REF + ", " + MEDICAL_ATTESTATION_SIG_REF,
                "Supplied medical reviewer and attestation signature references recorded; "
                        + "authentic record provenance verification remains PENDING.",
                limits,
                totals,
                allCollectionPass ? PASS : FAIL,
                PENDING,
                identity,
                entries);
    }

    private static CaseReportEntry executeCase(
            GoldenAiDataset.ResponseEvaluationCase golden,
            LiveProvider liveProvider,
            CollectorLimits limits,
            CostRates costRates,
            RequestPacer pacer,
            GoldenAiSemanticEvaluator semanticEvaluator,
            AtomicInteger totalRequests,
            AtomicLong totalInputTokens,
            AtomicLong totalOutputTokens,
            AtomicLong totalLatencyMs) {

        String caseId = golden.caseId();
        String studentResponse = golden.studentResponse();

        List<AuthorizedResponseEvidence.Source> sources = new ArrayList<>();
        for (int i = 0; i < golden.sourceEvidence().size(); i++) {
            GoldenAiDataset.Source s = golden.sourceEvidence().get(i);
            UUID sourceId = UUID.fromString(s.sourceId());
            sources.add(new AuthorizedResponseEvidence.Source(
                    sourceId, sourceId, MATERIAL_ID, MATERIAL_VERSION_ID, null,
                    i + 1, s.content(), null, null, "TEXT", "NATIVE", "STRONG"));
        }
        var authorizedEvidence = new AuthorizedResponseEvidence(
                StudyMissionGroundingMode.valueOf(golden.groundingMode().name()),
                sources);

        TrackingProviderAdapter trackingAdapter = new TrackingProviderAdapter(
                liveProvider.adapter(), pacer, liveProvider.configuredModel(),
                limits, costRates, totalRequests, totalInputTokens, totalOutputTokens);
        TrackingStructuredOutputDecoder trackingDecoder = new TrackingStructuredOutputDecoder(
                new JacksonAiStructuredOutputDecoder());
        AiOutputValidator outputValidator = new AiOutputValidator(trackingDecoder);

        PromptContextBuilder promptBuilder = new PromptContextBuilder(
                new PromptTemplateRegistry(), new Utf8ByteLengthPromptTokenCounter());

        AiRequestManagerPolicy policy = new AiRequestManagerPolicy(
                1, 1, Duration.ofSeconds(limits.timeoutSeconds()), 1,
                Duration.ofMillis(1), Duration.ofMillis(1), 2, Duration.ofSeconds(1));
        Map<ProviderId, AiRequestManagerPolicy> policies = new EnumMap<>(ProviderId.class);
        policies.put(liveProvider.providerId(), policy);

        AiRequestManager requestManager = new AiRequestManager(
                List.of(trackingAdapter), policies, 2, AiRequestTelemetry.NONE);

        SourceReferenceRepository sourceRepo = new GoldenSourceReferenceRepository(golden);

        AiExecutionOrchestrator orchestrator = new AiExecutionOrchestrator(
                promptBuilder,
                () -> new AuthenticatedUser(QUALIFICATION_USER_ID),
                new ProviderRouter(),
                requestManager,
                outputValidator,
                new AiSourceReferenceValidator(sourceRepo),
                NoOpDiagnostics.INSTANCE,
                AiRequestTelemetry.NONE);

        ProviderRoutingCandidate candidate = new ProviderRoutingCandidate(
                liveProvider.providerId(), liveProvider.configuredModel(),
                Set.of(AiTaskType.RESPONSE_EVALUATION, AiTaskType.STRUCTURED_OUTPUT_REPAIR),
                Set.of(AiTaskType.RESPONSE_EVALUATION, AiTaskType.STRUCTURED_OUTPUT_REPAIR),
                true, true, true, 1, 1, 1);

        AiTaskExecutionPolicy executionPolicy = new AiTaskExecutionPolicy(Map.of(
                AiTaskType.RESPONSE_EVALUATION,
                new AiTaskExecutionOptions(
                        new PromptTokenBudget(131_072, 2_048),
                        List.of(candidate),
                        ProviderRoutingPreference.LATENCY_THEN_COST)));

        ResponseAttributionValidator.Limits validatorLimits =
                new ResponseAttributionValidator.Limits(
                        8_000, 8, 32_000, 32, 64, 128, limits.timeoutSeconds() * 1000L);

        AiResponseEvaluationAdapter adapter = new AiResponseEvaluationAdapter(
                orchestrator, executionPolicy, validatorLimits);

        long startNs = System.nanoTime();
        ResponseEvaluationPort.Result portResult = null;
        Throwable executionError = null;

        try {
            portResult = adapter.evaluate(new ResponseEvaluationPort.Request(
                    golden.question(), golden.expectedConcepts(),
                    golden.expectedAnswer(), studentResponse, authorizedEvidence));
        } catch (Throwable t) {
            executionError = t;
        } finally {
            requestManager.close();
        }

        long caseLatencyMs = (System.nanoTime() - startNs) / 1_000_000L;
        totalLatencyMs.addAndGet(caseLatencyMs);

        DiscoveryRecord discoveryRecord = extractDiscoveryRecord(trackingDecoder, trackingAdapter);
        VerificationRecord verificationRecord = extractVerificationRecord(trackingDecoder, trackingAdapter);

        FinalEvaluationRecord finalEvaluationRecord;
        if (portResult != null) {
            List<String> refStrings = golden.sourceEvidence().stream()
                    .map(GoldenAiDataset.Source::sourceId).toList();
            finalEvaluationRecord = new FinalEvaluationRecord(
                    portResult.outcome().name(),
                    portResult.correctConcepts(),
                    portResult.missingConcepts(),
                    portResult.misconceptions(),
                    portResult.feedback(),
                    portResult.certainty().name(),
                    portResult.recommendedAction().name(),
                    refStrings,
                    portResult.validated());
        } else {
            finalEvaluationRecord = null;
        }

        SemanticMatcherRecord semanticMatcherRecord;
        if (portResult != null && !portResult.feedback().isBlank()) {
            ResponseEvaluationResult evaluationResult = new ResponseEvaluationResult(
                    Evaluation.valueOf(portResult.outcome().name()),
                    portResult.correctConcepts(),
                    portResult.missingConcepts(),
                    portResult.misconceptions(),
                    portResult.feedback(),
                    EvaluationCertainty.valueOf(portResult.certainty().name()),
                    RecommendedAction.valueOf(portResult.recommendedAction().name()),
                    golden.sourceEvidence().stream().map(GoldenAiDataset.Source::sourceId).toList(),
                    List.of());
            GoldenAiSemanticEvaluator.Result evalResult = semanticEvaluator.evaluate(golden, evaluationResult);
            semanticMatcherRecord = new SemanticMatcherRecord(evalResult.passed(), evalResult.failedRules());
        } else {
            semanticMatcherRecord = null;
        }

        Map<String, String> diagnostics = buildDiagnostics(
                trackingAdapter, caseLatencyMs, executionError);

        boolean contractPassed = portResult != null
                && portResult.validated()
                && executionError == null
                && discoveryRecord.valid()
                && verificationRecord.valid();
        Status evidenceStatus = contractPassed ? PASS : FAIL;
        Status contractStatus = contractPassed ? PASS : FAIL;
        Status semanticStatus = semanticMatcherRecord == null ? NOT_RUN : semanticMatcherRecord.passed() ? PASS : FAIL;

        return new CaseReportEntry(
                caseId,
                golden.subject(),
                golden.topic(),
                golden.question(),
                studentResponse,
                golden.reviewerNotes(),
                liveProvider.configuredModel(),
                liveProvider.providerId().name(),
                discoveryRecord,
                verificationRecord,
                finalEvaluationRecord,
                semanticMatcherRecord,
                diagnostics,
                evidenceStatus,
                contractStatus,
                semanticStatus,
                PENDING,
                PENDING);
    }

    private static DiscoveryRecord extractDiscoveryRecord(
            TrackingStructuredOutputDecoder decoder, TrackingProviderAdapter trackingAdapter) {
        var optResult = decoder.findLatest(ResponseAttributionDiscovery.class);
        var attempt = trackingAdapter.findAttempt(PromptId.RESPONSE_EVALUATION_DISCOVERY_V1);
        var optProviderResult = attempt.flatMap(RequestAttempt::result);

        boolean executed = attempt.isPresent();
        boolean valid = optResult.isPresent();
        ResponseAttributionDiscovery output = optResult.orElse(null);

        Integer inTokens = optProviderResult.flatMap(r -> r.usage().inputTokens()).orElse(null);
        Integer outTokens = optProviderResult.flatMap(r -> r.usage().outputTokens()).orElse(null);
        Long latency = optProviderResult.map(r -> r.latency().toMillis()).orElse(null);
        String finish = optProviderResult.flatMap(ProviderExecutionResult::finishReason).orElse(null);
        String error = attempt.flatMap(RequestAttempt::failure).map(Throwable::toString)
                .orElseGet(() -> decoder.findError(ResponseAttributionDiscovery.class)
                        .map(Throwable::toString).orElse(null));

        return new DiscoveryRecord(
                PromptId.RESPONSE_EVALUATION_DISCOVERY_V1.name(), "1",
                executed, valid, output, inTokens, outTokens, latency, finish, error);
    }

    private static VerificationRecord extractVerificationRecord(
            TrackingStructuredOutputDecoder decoder, TrackingProviderAdapter trackingAdapter) {
        var optResult = decoder.findLatest(ResponseAttributionVerification.class);
        var attempt = trackingAdapter.findAttempt(PromptId.RESPONSE_EVALUATION_VERIFICATION_V1);
        var optProviderResult = attempt.flatMap(RequestAttempt::result);

        boolean executed = attempt.isPresent();
        boolean valid = optResult.isPresent();
        ResponseAttributionVerification output = optResult.orElse(null);

        Integer inTokens = optProviderResult.flatMap(r -> r.usage().inputTokens()).orElse(null);
        Integer outTokens = optProviderResult.flatMap(r -> r.usage().outputTokens()).orElse(null);
        Long latency = optProviderResult.map(r -> r.latency().toMillis()).orElse(null);
        String finish = optProviderResult.flatMap(ProviderExecutionResult::finishReason).orElse(null);
        String error = attempt.flatMap(RequestAttempt::failure).map(Throwable::toString)
                .orElseGet(() -> decoder.findError(ResponseAttributionVerification.class)
                        .map(Throwable::toString).orElse(null));

        return new VerificationRecord(
                PromptId.RESPONSE_EVALUATION_VERIFICATION_V1.name(), "1",
                executed, valid, output, inTokens, outTokens, latency, finish, error);
    }

    private static Map<String, String> buildDiagnostics(
            TrackingProviderAdapter adapter, long totalLatencyMs, Throwable error) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("totalCaseLatencyMs", Long.toString(totalLatencyMs));
        metadata.put("providerRequests", Integer.toString(adapter.requests().size()));
        metadata.put("structuredRepairUsed", Boolean.toString(adapter.structuredRepairUsed()));
        if (adapter.limitViolation() != null) {
            metadata.put("limitViolation", adapter.limitViolation().getMessage());
        }
        if (adapter.usageUnknown()) {
            metadata.put("usageUnknown", "true");
        }
        for (int i = 0; i < adapter.attempts().size(); i++) {
            RequestAttempt attempt = adapter.attempts().get(i);
            String requestKey = "request." + i;
            metadata.put(requestKey + ".prompt", attempt.request().promptContext().taskPromptId().name());
            attempt.failure().ifPresent(f -> metadata.put(requestKey + ".failure", f.toString()));
        }
        if (error != null) {
            metadata.put("executionError", error.getClass().getSimpleName() + ": " + error.getMessage());
        }
        return Map.copyOf(metadata);
    }

    public static QualificationIdentity currentIdentity(String provider, String model) throws IOException {
        return currentIdentity(provider, model, CollectorLimits.defaultLimits(), CostRates.defaults());
    }

    public static QualificationIdentity currentIdentity(
            String provider, String model, CollectorLimits limits, CostRates rates) throws IOException {
        Path backend = Files.isDirectory(Path.of("src/main/java")) ? Path.of(".") : Path.of("backend");
        Map<String, String> inputs = new TreeMap<>();
        inputs.put("dataset", DATASET_VERSION);
        inputs.put("datasetRubric", "v7 (v2 base + v4 rubric + v5 input contract + v6 rubric override + v7 rubric override)");
        inputs.put("provider", provider);
        inputs.put("model", model);
        inputs.put("maxRequests", Integer.toString(limits.maxRequests()));
        inputs.put("maxTokens", Long.toString(limits.maxTokens()));
        inputs.put("timeoutSeconds", Long.toString(limits.timeoutSeconds()));
        inputs.put("maxCostUsd", limits.maxCostUsd().stripTrailingZeros().toPlainString());
        inputs.put("delayMillis", Long.toString(limits.delayMillis()));
        inputs.put("inputCostPerMillion", rates.perMillionInputTokens().stripTrailingZeros().toPlainString());
        inputs.put("outputCostPerMillion", rates.perMillionOutputTokens().stripTrailingZeros().toPlainString());
        inputs.put("discoveryPrompt", PromptId.RESPONSE_EVALUATION_DISCOVERY_V1.name());
        inputs.put("verificationPrompt", PromptId.RESPONSE_EVALUATION_VERIFICATION_V1.name());
        inputs.put("repairPrompt", PromptId.STRUCTURED_OUTPUT_REPAIR_V1.name());
        inputs.put("adapter", "AiResponseEvaluationAdapter (two-pass verified attribution)");
        inputs.put("validator", "ResponseAttributionValidator");
        inputs.put("aggregator", "VerifiedResponseEvaluationAggregator");
        inputs.put("budgets", "context=131072; output=2048");
        inputs.put("grounding", "STRICT_SOURCE; authorized synthetic v7 sources");

        for (String file : List.of(
                "pom.xml",
                "src/main/java/com/hippocampus/ai/infrastructure/learning/AiResponseEvaluationAdapter.java",
                "src/main/java/com/hippocampus/ai/infrastructure/learning/SourceSpanOffsetNormalizer.java",
                "src/main/java/com/hippocampus/ai/infrastructure/provider/gemini/GeminiProviderAdapter.java",
                "src/main/java/com/hippocampus/ai/infrastructure/provider/ProviderStructuredOutputSchema.java",
                "src/main/java/com/hippocampus/ai/application/prompt/PromptTemplateRegistry.java",
                "src/main/java/com/hippocampus/ai/application/prompt/PromptContextBuilder.java",
                "src/main/java/com/hippocampus/ai/application/validation/AiOutputValidator.java",
                "src/main/java/com/hippocampus/ai/application/validation/ResponseAttributionValidator.java",
                "src/main/java/com/hippocampus/ai/application/validation/VerifiedResponseEvaluationAggregator.java",
                "src/test/java/com/hippocampus/ai/evaluation/GoldenResponseAdapterLiveCollector.java",
                "src/test/resources/ai/golden/v2/response-evaluation-cases.json",
                "src/test/resources/ai/golden/v4/response-evaluation-rubric-overrides.json",
                "src/test/resources/ai/golden/v5/response-evaluation-input-contract.json",
                "src/test/resources/ai/golden/v6/response-evaluation-rubric-overrides.json",
                "src/test/resources/ai/golden/v7/response-evaluation-rubric-overrides.json")) {
            Path target = backend.resolve(file);
            if (!Files.isRegularFile(target)) throw new IOException("missing qualification input: " + target);
            inputs.put(file, sha256Hex(Files.readAllBytes(target)));
        }
        return new QualificationIdentity(
                "P7-09-two-pass-adapter-v1",
                Map.copyOf(inputs),
                sha256Hex(new ObjectMapper().writeValueAsBytes(inputs)));
    }

    private static LiveProvider liveProvider() {
        return switch (normalizeProvider(requiredEnvironment(PROVIDER_ENVIRONMENT))) {
            case "gemini" -> geminiProvider();
            case "ollama-cloud" -> ollamaProvider();
            default -> throw new IllegalStateException("unreachable provider");
        };
    }

    static String normalizeProvider(String provider) {
        if (provider == null) throw new IllegalArgumentException("provider is required");
        String normalized = provider.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        if (!normalized.equals("gemini") && !normalized.equals("ollama-cloud")) {
            throw new IllegalArgumentException("unsupported live Golden AI provider: " + provider);
        }
        return normalized;
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
                ProviderId.OLLAMA_CLOUD, model, new OllamaCloudProviderAdapter(client), "ollama-cloud");
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for live Golden AI evaluation");
        }
        return value.trim();
    }

    private static void assertCollectionSuccess(EvaluationReport report, Path reportPath) {
        long failures = report.cases().stream()
                .filter(e -> e.evidenceCollectionStatus() != PASS).count();
        if (failures > 0) {
            throw new AssertionError(failures + " Golden AI evidence collection cases failed; see " + reportPath);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("live collector pacing interrupted", e);
        }
    }

    private static int parsePositiveInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed <= 0) throw new IllegalArgumentException("must be positive: " + value);
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid integer: " + value, e);
        }
    }

    private static long parsePositiveLong(String value, long defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            long parsed = Long.parseLong(value.trim());
            if (parsed <= 0) throw new IllegalArgumentException("must be positive: " + value);
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid long: " + value, e);
        }
    }

    private static long parseNonNegativeLong(String value, long defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            long parsed = Long.parseLong(value.trim());
            if (parsed < 0) throw new IllegalArgumentException("must not be negative: " + value);
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid long: " + value, e);
        }
    }

    private static BigDecimal parseBigDecimal(String value, BigDecimal defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            BigDecimal parsed = new BigDecimal(value.trim());
            if (parsed.signum() <= 0) throw new IllegalArgumentException("must be positive: " + value);
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid decimal: " + value, e);
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static final class RequestPacer {
        private final long delayMillis;
        private final LongConsumer sleeper;
        private boolean requestStarted;

        public RequestPacer(long delayMillis, LongConsumer sleeper) {
            if (delayMillis < 0L) throw new IllegalArgumentException("delayMillis must be non-negative");
            this.delayMillis = delayMillis;
            this.sleeper = sleeper;
        }

        public void beforeRequest() {
            if (requestStarted && delayMillis > 0L) {
                sleeper.accept(delayMillis);
            }
            requestStarted = true;
        }
    }

    public record RequestAttempt(ProviderExecutionRequest request,
            Optional<ProviderExecutionResult> result, Optional<Throwable> failure) {}

    public static final class TrackingProviderAdapter implements AiProviderAdapter {
        private final AiProviderAdapter delegate;
        private final RequestPacer pacer;
        private final String modelId;
        private final CollectorLimits limits;
        private final CostRates costRates;
        private final AtomicInteger globalRequests;
        private final AtomicLong globalInputTokens;
        private final AtomicLong globalOutputTokens;

        private final List<RequestAttempt> attempts = new CopyOnWriteArrayList<>();
        private volatile Throwable limitViolation;
        private volatile boolean usageUnknown;

        public TrackingProviderAdapter(
                AiProviderAdapter delegate,
                RequestPacer pacer,
                String modelId,
                CollectorLimits limits,
                CostRates costRates,
                AtomicInteger globalRequests,
                AtomicLong globalInputTokens,
                AtomicLong globalOutputTokens) {
            this.delegate = Objects.requireNonNull(delegate);
            this.pacer = Objects.requireNonNull(pacer);
            this.modelId = Objects.requireNonNull(modelId);
            this.limits = Objects.requireNonNull(limits);
            this.costRates = Objects.requireNonNull(costRates);
            this.globalRequests = Objects.requireNonNull(globalRequests);
            this.globalInputTokens = Objects.requireNonNull(globalInputTokens);
            this.globalOutputTokens = Objects.requireNonNull(globalOutputTokens);
        }

        public Throwable limitViolation() {
            return limitViolation;
        }

        public boolean usageUnknown() {
            return usageUnknown;
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
            if (limitViolation != null) throw new IllegalStateException("collector stopped", limitViolation);
            if (globalRequests.get() >= limits.maxRequests()) {
                IllegalStateException ex = new IllegalStateException(
                        "Execution limit exceeded: max requests " + limits.maxRequests());
                limitViolation = ex;
                throw ex;
            }

            long proposedInput = (long) globalInputTokens.get() + request.promptContext().inputTokenCount();
            long proposedOutput = (long) globalOutputTokens.get() + request.promptContext().reservedOutputTokens();
            if (proposedInput + proposedOutput > limits.maxTokens()) {
                IllegalStateException ex = new IllegalStateException(
                        "Execution limit exceeded: pre-request token allowance " + limits.maxTokens());
                limitViolation = ex;
                throw ex;
            }
            BigDecimal currentCost = costRates.estimate(proposedInput, proposedOutput);
            if (currentCost.compareTo(limits.maxCostUsd()) > 0) {
                IllegalStateException ex = new IllegalStateException(
                        "Execution limit exceeded: pre-request estimated cost USD " + limits.maxCostUsd());
                limitViolation = ex;
                throw ex;
            }

            pacer.beforeRequest();
            globalRequests.incrementAndGet();
            ProviderExecutionResult result;
            try {
                result = delegate.execute(request);
            } catch (RuntimeException | Error ex) {
                attempts.add(new RequestAttempt(request, Optional.empty(), Optional.of(ex)));
                usageUnknown = true;
                limitViolation = new IllegalStateException(
                        "Execution limit exceeded: provider usage unavailable after failed request");
                throw ex;
            }
            attempts.add(new RequestAttempt(request, Optional.of(result), Optional.empty()));

            if (result.usage().inputTokens().isEmpty() || result.usage().outputTokens().isEmpty()) {
                IllegalStateException ex = new IllegalStateException("Execution limit exceeded: provider usage unavailable");
                usageUnknown = true;
                limitViolation = ex;
                throw ex;
            }
            long inTokens = result.usage().inputTokens().get();
            long outTokens = result.usage().outputTokens().get();
            long newTotalTokens = globalInputTokens.addAndGet(inTokens) + globalOutputTokens.addAndGet(outTokens);

            if (newTotalTokens > limits.maxTokens()) {
                IllegalStateException ex = new IllegalStateException(
                        "Execution limit exceeded: max tokens " + limits.maxTokens());
                limitViolation = ex;
                throw ex;
            }

            BigDecimal newCost = costRates.estimate(globalInputTokens.get(), globalOutputTokens.get());
            if (newCost.compareTo(limits.maxCostUsd()) > 0) {
                IllegalStateException ex = new IllegalStateException(
                        "Execution limit exceeded: max cost USD " + limits.maxCostUsd());
                limitViolation = ex;
                throw ex;
            }

            return result;
        }

        @Override
        public ProviderEventStream stream(ProviderExecutionRequest request) {
            throw new UnsupportedOperationException(
                    "Streaming is not supported by the Golden collector");
        }

        public List<ProviderExecutionRequest> requests() {
            return attempts.stream().map(RequestAttempt::request).toList();
        }

        public List<ProviderExecutionResult> results() {
            return attempts.stream().flatMap(a -> a.result().stream()).toList();
        }

        public List<RequestAttempt> attempts() { return List.copyOf(attempts); }

        public boolean structuredRepairUsed() {
            return attempts.stream()
                    .anyMatch(a -> a.request().taskType() == AiTaskType.STRUCTURED_OUTPUT_REPAIR);
        }

        public Optional<RequestAttempt> findAttempt(PromptId promptId) {
            return attempts.stream()
                    .filter(a -> a.request().promptContext().taskPromptId() == promptId)
                    .findFirst();
        }
    }

    public static final class TrackingStructuredOutputDecoder implements com.hippocampus.ai.port.AiStructuredOutputDecoder {
        private final com.hippocampus.ai.port.AiStructuredOutputDecoder delegate;
        private final Map<Class<?>, Object> latestDecoded = new java.util.concurrent.ConcurrentHashMap<>();
        private final Map<Class<?>, Throwable> latestErrors = new java.util.concurrent.ConcurrentHashMap<>();

        public TrackingStructuredOutputDecoder(com.hippocampus.ai.port.AiStructuredOutputDecoder delegate) {
            this.delegate = Objects.requireNonNull(delegate);
        }

        @Override
        public <T> T decode(String rawOutput, Class<T> resultType) {
            try {
                T decoded = delegate.decode(rawOutput, resultType);
                latestDecoded.put(resultType, decoded);
                return decoded;
            } catch (Throwable t) {
                latestErrors.put(resultType, t);
                throw t;
            }
        }

        @SuppressWarnings("unchecked")
        public <T> Optional<T> findLatest(Class<T> type) {
            return Optional.ofNullable((T) latestDecoded.get(type));
        }

        public Optional<Throwable> findError(Class<?> type) {
            return Optional.ofNullable(latestErrors.get(type));
        }
    }

    static final class GoldenSourceReferenceRepository implements SourceReferenceRepository {
        private final GoldenAiDataset.ResponseEvaluationCase golden;

        GoldenSourceReferenceRepository(GoldenAiDataset.ResponseEvaluationCase golden) {
            this.golden = golden;
        }

        @Override
        public Optional<SourceReferenceSeed> findAuthorizedTarget(UUID userId, SourceReferenceTarget target) {
            if (!QUALIFICATION_USER_ID.equals(userId) || !(target instanceof ChunkSourceTarget chunkTarget)
                    || !MATERIAL_ID.equals(chunkTarget.materialId())
                    || !MATERIAL_VERSION_ID.equals(chunkTarget.materialVersionId())) {
                return Optional.empty();
            }
            int sourceIndex = -1;
            for (int i = 0; i < golden.sourceEvidence().size(); i++) {
                if (UUID.fromString(golden.sourceEvidence().get(i).sourceId()).equals(chunkTarget.chunkId())) {
                    sourceIndex = i;
                    break;
                }
            }
            if (sourceIndex < 0) return Optional.empty();
            return Optional.of(new SourceReferenceSeed(
                    MATERIAL_ID,
                    MATERIAL_VERSION_ID,
                    null,
                    chunkTarget.chunkId(),
                    null,
                    sourceIndex + 1,
                    "Synthetic Golden Evidence",
                    "Synthetic Golden Evidence"));
        }

        @Override
        public SourceReference upsert(SourceReferenceSeed seed, String displayLabel) {
            if (findAuthorizedTarget(QUALIFICATION_USER_ID,
                    new ChunkSourceTarget(seed.materialId(), seed.materialVersionId(), seed.chunkId())).isEmpty()) {
                throw new IllegalArgumentException("unauthorized synthetic Golden source");
            }
            return new SourceReference(
                    UUID.randomUUID(), seed.materialId(), seed.materialVersionId(),
                    seed.documentNodeId(), seed.chunkId(), seed.visualAssetId(),
                    seed.pageNumber(), null, null, displayLabel, Instant.EPOCH);
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
