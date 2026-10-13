package com.hippocampus.ai.infrastructure.learning;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.hippocampus.ai.application.AiExecutionOrchestrator;
import com.hippocampus.ai.application.validation.ResponseAttributionValidator;
import com.hippocampus.ai.application.validation.VerifiedResponseEvaluationAggregator;
import com.hippocampus.ai.application.prompt.PromptId;
import com.hippocampus.ai.application.request.AiRequestPriority;
import com.hippocampus.ai.domain.AiOutputContract;
import com.hippocampus.ai.domain.AiTaskRequest;
import com.hippocampus.ai.domain.AiTaskType;
import com.hippocampus.ai.domain.LearnerContext;
import com.hippocampus.ai.domain.ResponseAttributionDiscovery;
import com.hippocampus.ai.domain.ResponseAttributionVerification;
import com.hippocampus.ai.domain.ResponseEvaluationInput;
import com.hippocampus.ai.domain.ValidatedAiResult;
import com.hippocampus.learning.port.ResponseEvaluationPort;
import com.hippocampus.learning.port.AuthorizedResponseEvidence;
import com.hippocampus.rag.domain.EvidenceChunk;
import com.hippocampus.rag.domain.EvidenceReferenceKind;
import com.hippocampus.rag.domain.EvidenceSourceReference;
import com.hippocampus.rag.domain.EvidenceLimitation;
import com.hippocampus.rag.domain.EvidenceLimitationCode;
import com.hippocampus.rag.domain.EvidencePackage;
import com.hippocampus.rag.domain.GroundingMode;
import com.hippocampus.rag.domain.RetrievalDiagnostics;
import com.hippocampus.rag.domain.RetrievalQuality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AiResponseEvaluationAdapter implements ResponseEvaluationPort {
    private static final Logger LOG = LoggerFactory.getLogger(AiResponseEvaluationAdapter.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private final AiExecutionOrchestrator orchestrator;
    private final AiTaskExecutionPolicy executionPolicy;
    private final ResponseAttributionValidator.Limits limits;
    private final ResponseAttributionValidator attributionValidator;
    private final VerifiedResponseEvaluationAggregator aggregator =
            new VerifiedResponseEvaluationAggregator();

    public AiResponseEvaluationAdapter(
            AiExecutionOrchestrator orchestrator,
            AiTaskExecutionPolicy executionPolicy) {
        this(orchestrator, executionPolicy,
                new ResponseAttributionValidator.Limits(8_000, 8, 32_000, 32, 64, 128, 60_000));
    }

    public AiResponseEvaluationAdapter(
            AiExecutionOrchestrator orchestrator,
            AiTaskExecutionPolicy executionPolicy,
            ResponseAttributionValidator.Limits limits) {
        this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator must not be null");
        this.executionPolicy = Objects.requireNonNull(
                executionPolicy, "executionPolicy must not be null");
        this.limits = Objects.requireNonNull(limits, "limits must not be null");
        this.attributionValidator = new ResponseAttributionValidator(limits);
    }

    @Override
    public Result evaluate(Request request) {
        Objects.requireNonNull(request, "request must not be null");
        AuthorizedResponseEvidence authorized = request.evidence();
        if (authorized.groundingMode()
                == com.hippocampus.learning.domain.StudyMissionGroundingMode.GENERAL_KNOWLEDGE
                || authorized.limitation() != AuthorizedResponseEvidence.Limitation.NONE
                || authorized.sources().isEmpty()
                || request.studentResponse().length() > limits.maxResponseCharacters()
                || authorized.sources().size() > limits.maxSources()
                || authorized.sources().stream().mapToInt(source -> source.content().length()).sum()
                        > limits.maxSourceCharacters()) {
            return VerifiedResponseEvaluationAggregator.uncertain();
        }
        EvidencePackage evidence = evidencePackage(authorized);
        long deadline = System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(limits.maxEndToEndMillis());
        try {
            String referenceMap = referenceMap(authorized);
            ResponseEvaluationInput discoveryInput = new ResponseEvaluationInput(
                    request.question(), request.expectedConcepts(), request.expectedAnswer(),
                    request.studentResponse(), referenceMap, null);
            Object discovered = executePass(PromptId.RESPONSE_EVALUATION_DISCOVERY_V1,
                    discoveryInput, evidence, deadline);
            if (!(discovered instanceof ResponseAttributionDiscovery rawDiscovery)) {
                return VerifiedResponseEvaluationAggregator.uncertain();
            }
            var normalizedDiscovery = SourceSpanOffsetNormalizer.discovery(rawDiscovery, authorized);
            auditCorrections(normalizedDiscovery.corrections());
            ResponseAttributionDiscovery discovery = normalizedDiscovery.value();
            attributionValidator.validateDiscovery(discoveryInput, authorized, discovery);
            if (!discovery.complete()) {
                return VerifiedResponseEvaluationAggregator.uncertain();
            }
            ResponseEvaluationInput verificationInput = new ResponseEvaluationInput(
                    request.question(), request.expectedConcepts(), request.expectedAnswer(),
                    request.studentResponse(), referenceMap, toJson(discovery));
            Object verified = executePass(PromptId.RESPONSE_EVALUATION_VERIFICATION_V1,
                    verificationInput, evidence, deadline);
            if (!(verified instanceof ResponseAttributionVerification rawVerification)) {
                return VerifiedResponseEvaluationAggregator.uncertain();
            }
            var normalizedVerification = SourceSpanOffsetNormalizer.verification(rawVerification, authorized);
            auditCorrections(normalizedVerification.corrections());
            ResponseAttributionVerification verification = normalizedVerification.value();
            attributionValidator.validateVerification(
                    verificationInput, authorized, discovery, verification);
            return aggregator.aggregate(verificationInput, discovery, verification);
        } catch (RuntimeException unresolved) {
            return VerifiedResponseEvaluationAggregator.uncertain();
        }
    }

    private static void auditCorrections(List<SourceSpanOffsetNormalizer.Correction> corrections) {
        for (var correction : corrections) {
            LOG.info("Response evaluation source offset corrected: reference={}, version={}, original=[{},{}), corrected=[{},{})",
                    correction.sourceReferenceId(), correction.sourceVersion(),
                    correction.originalStart(), correction.originalEnd(),
                    correction.correctedStart(), correction.correctedEnd());
        }
    }

    private Object executePass(PromptId promptId, ResponseEvaluationInput input,
            EvidencePackage evidence, long deadline) {
        long remainingNanos = deadline - System.nanoTime();
        if (remainingNanos <= 0) {
            throw new IllegalStateException("response evaluation time limit exceeded");
        }
        AiTaskRequest<ResponseEvaluationInput> aiRequest = new AiTaskRequest<>(
                AiTaskType.RESPONSE_EVALUATION, promptId.name(),
                new LearnerContext("RESPONSE_EVALUATION", "CURRENT_ACTIVITY",
                        "MAINTAIN", Map.of(), List.of()),
                input, evidence, evidence.groundingMode(),
                AiOutputContract.RESPONSE_EVALUATION);
        AiTaskExecutionOptions options = executionPolicy.optionsFor(AiTaskType.RESPONSE_EVALUATION);
        CompletableFuture<ValidatedAiResult<?>> future = orchestrator.execute(
                aiRequest, AiRequestPriority.INTERACTIVE_EVALUATION,
                options.tokenBudget(), options.routingCandidates(),
                options.routingPreference());
        try {
            return future.get(remainingNanos, TimeUnit.NANOSECONDS).result();
        } catch (TimeoutException timedOut) {
            future.cancel(true);
            throw new IllegalStateException("response evaluation time limit exceeded", timedOut);
        } catch (InterruptedException interrupted) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("response evaluation interrupted", interrupted);
        } catch (java.util.concurrent.ExecutionException failed) {
            throw new IllegalStateException("response evaluation pass failed", failed.getCause());
        }
    }

    private static String referenceMap(AuthorizedResponseEvidence authorized) {
        return toJson(authorized.sources().stream().map(source -> Map.of(
                "sourceReferenceId", source.sourceReferenceId().toString(),
                "chunkId", source.chunkId().toString(),
                "sourceVersion", source.materialVersionId().toString()))
                .toList());
    }

    private static String toJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("attribution context could not be encoded", failure);
        }
    }

    private static EvidencePackage evidencePackage(AuthorizedResponseEvidence authorized) {
        GroundingMode mode = GroundingMode.valueOf(authorized.groundingMode().name());
        List<AuthorizedResponseEvidence.Source> sources = authorized.sources();
        RetrievalQuality quality = sources.isEmpty()
                ? RetrievalQuality.INSUFFICIENT
                : sources.stream().anyMatch(source -> !"STRONG".equals(source.quality()))
                        ? RetrievalQuality.LIMITED : RetrievalQuality.STRONG;
        List<EvidenceChunk> chunks = IntStream.range(0, sources.size())
                .mapToObj(index -> {
                    var source = sources.get(index);
                    return new EvidenceChunk(index + 1, source.chunkId(), source.materialId(),
                            source.materialVersionId(), source.documentNodeId(),
                            source.chunkIndex(), source.content(), source.pageStart(),
                            source.pageEnd(), List.of(), source.contentType(),
                            source.extractionMethod(), source.quality());
                }).toList();
        List<EvidenceSourceReference> references = sources.stream()
                .map(source -> new EvidenceSourceReference(
                        EvidenceReferenceKind.CHUNK, source.materialId(),
                        source.materialVersionId(), source.documentNodeId(),
                        source.chunkId(), null,
                        Objects.equals(source.pageStart(), source.pageEnd())
                                ? source.pageStart() : null))
                .toList();
        return new EvidencePackage(quality, mode, chunks, List.of(), references,
                sources.isEmpty()
                        ? List.of(new EvidenceLimitation(EvidenceLimitationCode.INSUFFICIENT_EVIDENCE))
                        : List.of(),
                new RetrievalDiagnostics(sources.size(), sources.size(),
                        chunks.stream().map(EvidenceChunk::chunkId).toList(), List.of(),
                        sources.stream().map(AuthorizedResponseEvidence.Source::materialId)
                                .collect(java.util.stream.Collectors.toSet()),
                        Set.of(), quality));
    }
}
