package com.hippocampus.ai.application.validation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.hippocampus.ai.application.prompt.PromptContext;
import com.hippocampus.ai.domain.AiTaskRequest;
import com.hippocampus.ai.domain.ConceptConnectionResult;
import com.hippocampus.ai.domain.ContextualApplicationResult;
import com.hippocampus.ai.domain.ExplanationResult;
import com.hippocampus.ai.domain.QuestionGenerationResult;
import com.hippocampus.ai.domain.ResponseEvaluationResult;
import com.hippocampus.ai.domain.ValidatedAiResult;
import com.hippocampus.materials.domain.ChunkSourceTarget;
import com.hippocampus.materials.port.SourceReferenceRepository;
import com.hippocampus.materials.port.SourceReferenceSeed;
import com.hippocampus.rag.domain.EvidenceChunk;
import com.hippocampus.rag.domain.EvidenceReferenceKind;
import com.hippocampus.rag.domain.EvidenceSourceReference;

public class AiSourceReferenceValidator {

    private final SourceReferenceRepository sourceReferences;

    public AiSourceReferenceValidator(SourceReferenceRepository sourceReferences) {
        this.sourceReferences = Objects.requireNonNull(sourceReferences, "sourceReferences must not be null");
    }

    @Transactional(readOnly = true)
    public <T> ValidatedAiResult<T> validate(
            UUID authenticatedUserId,
            ValidatedAiResult<T> result,
            AiTaskRequest<?> request,
            PromptContext promptContext) {
        Objects.requireNonNull(authenticatedUserId, "authenticatedUserId must not be null");
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(promptContext, "promptContext must not be null");

        List<String> returnedReferences = extractSourceReferences(result.result());
        if (returnedReferences.isEmpty()) {
            return result;
        }

        Map<UUID, PromptContext.IncludedSource> promptedSources = promptedSources(promptContext);
        Map<UUID, EvidenceChunk> evidenceChunks = evidenceChunks(request);
        Map<UUID, EvidenceSourceReference> evidenceReferences = evidenceReferences(request);
        Set<UUID> uniqueReferences = new HashSet<>();
        List<UUID> parsedReferences = new ArrayList<>(returnedReferences.size());

        for (String returnedReference : returnedReferences) {
            UUID chunkId = parseCanonicalUuid(returnedReference);
            if (!uniqueReferences.add(chunkId)) {
                fail(AiGroundingValidationException.Reason.DUPLICATE_REFERENCE);
            }
            parsedReferences.add(chunkId);
        }

        for (UUID chunkId : parsedReferences) {
            PromptContext.IncludedSource prompted = promptedSources.get(chunkId);
            if (prompted == null) {
                fail(AiGroundingValidationException.Reason.REFERENCE_NOT_IN_PROMPT);
            }

            EvidenceChunk evidenceChunk = evidenceChunks.get(chunkId);
            EvidenceSourceReference evidenceReference = evidenceReferences.get(chunkId);
            if (!matchesEvidence(prompted, evidenceChunk, evidenceReference)) {
                fail(AiGroundingValidationException.Reason.EVIDENCE_PROVENANCE_MISMATCH);
            }

            SourceReferenceSeed current = resolveCurrent(authenticatedUserId, prompted);
            if (!matchesCurrent(prompted, current)) {
                fail(AiGroundingValidationException.Reason.CURRENT_PROVENANCE_MISMATCH);
            }
        }
        return result;
    }

    private static List<String> extractSourceReferences(Object result) {
        return switch (result) {
            case ExplanationResult value -> value.sourceReferences();
            case QuestionGenerationResult value -> value.sourceReferences();
            case ResponseEvaluationResult value -> value.sourceReferences();
            case ConceptConnectionResult value -> value.sourceReferences();
            case ContextualApplicationResult value -> value.sourceReferences();
            default -> throw new AiGroundingValidationException(
                    AiGroundingValidationException.Reason.UNSUPPORTED_RESULT_TYPE);
        };
    }

    private static Map<UUID, PromptContext.IncludedSource> promptedSources(PromptContext promptContext) {
        Map<UUID, PromptContext.IncludedSource> result = new HashMap<>();
        for (PromptContext.IncludedSource source : promptContext.includedSources()) {
            if (result.put(source.chunkId(), source) != null) {
                fail(AiGroundingValidationException.Reason.EVIDENCE_PROVENANCE_MISMATCH);
            }
        }
        return result;
    }

    private static Map<UUID, EvidenceChunk> evidenceChunks(AiTaskRequest<?> request) {
        Map<UUID, EvidenceChunk> result = new HashMap<>();
        request.evidencePackage().chunks().forEach(chunk -> result.put(chunk.chunkId(), chunk));
        return result;
    }

    private static Map<UUID, EvidenceSourceReference> evidenceReferences(AiTaskRequest<?> request) {
        Map<UUID, EvidenceSourceReference> result = new HashMap<>();
        request.evidencePackage().sourceReferences().stream()
                .filter(reference -> reference.kind() == EvidenceReferenceKind.CHUNK)
                .forEach(reference -> result.put(reference.chunkId(), reference));
        return result;
    }

    private SourceReferenceSeed resolveCurrent(UUID userId, PromptContext.IncludedSource source) {
        try {
            return sourceReferences.findAuthorizedTarget(
                            userId,
                            new ChunkSourceTarget(
                                    source.materialId(), source.materialVersionId(), source.chunkId()))
                    .orElseThrow(() -> new AiGroundingValidationException(
                            AiGroundingValidationException.Reason.AUTHORIZATION_NOT_CONFIRMED));
        } catch (AiGroundingValidationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AiGroundingValidationException(
                    AiGroundingValidationException.Reason.AUTHORIZATION_NOT_CONFIRMED);
        }
    }

    private static UUID parseCanonicalUuid(String value) {
        if (value == null || value.isBlank()) {
            fail(AiGroundingValidationException.Reason.INVALID_REFERENCE);
        }
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equals(value)) {
                fail(AiGroundingValidationException.Reason.INVALID_REFERENCE);
            }
            return parsed;
        } catch (IllegalArgumentException exception) {
            throw new AiGroundingValidationException(
                    AiGroundingValidationException.Reason.INVALID_REFERENCE);
        }
    }

    private static boolean matchesEvidence(
            PromptContext.IncludedSource prompted,
            EvidenceChunk chunk,
            EvidenceSourceReference reference) {
        if (chunk == null || reference == null) {
            return false;
        }
        Integer singlePage = singlePage(prompted.pageStart(), prompted.pageEnd());
        return prompted.rank() == chunk.rank()
                && prompted.chunkId().equals(chunk.chunkId())
                && prompted.materialId().equals(chunk.materialId())
                && prompted.materialVersionId().equals(chunk.materialVersionId())
                && Objects.equals(prompted.documentNodeId(), chunk.documentNodeId())
                && Objects.equals(prompted.pageStart(), chunk.pageStart())
                && Objects.equals(prompted.pageEnd(), chunk.pageEnd())
                && reference.kind() == EvidenceReferenceKind.CHUNK
                && prompted.materialId().equals(reference.materialId())
                && prompted.materialVersionId().equals(reference.materialVersionId())
                && Objects.equals(prompted.documentNodeId(), reference.documentNodeId())
                && prompted.chunkId().equals(reference.chunkId())
                && Objects.equals(singlePage, reference.pageNumber());
    }

    private static boolean matchesCurrent(
            PromptContext.IncludedSource prompted, SourceReferenceSeed current) {
        return prompted.materialId().equals(current.materialId())
                && prompted.materialVersionId().equals(current.materialVersionId())
                && prompted.chunkId().equals(current.chunkId())
                && Objects.equals(prompted.documentNodeId(), current.documentNodeId())
                && Objects.equals(singlePage(prompted.pageStart(), prompted.pageEnd()), current.pageNumber());
    }

    private static Integer singlePage(Integer pageStart, Integer pageEnd) {
        return Objects.equals(pageStart, pageEnd) ? pageStart : null;
    }

    private static void fail(AiGroundingValidationException.Reason reason) {
        throw new AiGroundingValidationException(reason);
    }
}
