package com.hippocampus.ai.application.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.hippocampus.ai.application.prompt.PromptContext;
import com.hippocampus.ai.application.prompt.PromptId;
import com.hippocampus.ai.domain.ActivityType;
import com.hippocampus.ai.domain.AiOutputContract;
import com.hippocampus.ai.domain.AiTaskRequest;
import com.hippocampus.ai.domain.AiTaskType;
import com.hippocampus.ai.domain.ApplicationDifficulty;
import com.hippocampus.ai.domain.ConceptConnectionResult;
import com.hippocampus.ai.domain.ContextualApplicationResult;
import com.hippocampus.ai.domain.Evaluation;
import com.hippocampus.ai.domain.EvaluationCertainty;
import com.hippocampus.ai.domain.ExplanationInput;
import com.hippocampus.ai.domain.ExplanationMode;
import com.hippocampus.ai.domain.ExplanationResult;
import com.hippocampus.ai.domain.LearnerContext;
import com.hippocampus.ai.domain.QuestionDifficulty;
import com.hippocampus.ai.domain.QuestionGenerationResult;
import com.hippocampus.ai.domain.QuestionOption;
import com.hippocampus.ai.domain.RecommendedAction;
import com.hippocampus.ai.domain.ResponseEvaluationResult;
import com.hippocampus.ai.domain.ValidatedAiResult;
import com.hippocampus.identity.domain.AuthenticatedUser;
import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.materials.domain.SourceReference;
import com.hippocampus.materials.domain.SourceReferenceTarget;
import com.hippocampus.materials.domain.ChunkSourceTarget;
import com.hippocampus.materials.port.SourceReferenceRepository;
import com.hippocampus.materials.port.SourceReferenceSeed;
import com.hippocampus.rag.domain.EvidenceChunk;
import com.hippocampus.rag.domain.EvidencePackage;
import com.hippocampus.rag.domain.EvidenceReferenceKind;
import com.hippocampus.rag.domain.EvidenceSourceReference;
import com.hippocampus.rag.domain.GroundingMode;
import com.hippocampus.rag.domain.RetrievalDiagnostics;
import com.hippocampus.rag.domain.RetrievalQuality;

class AiSourceReferenceValidatorTests {

    private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID MATERIAL_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID VERSION_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID NODE_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID CHUNK_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_CHUNK_ID = UUID.fromString("50000000-0000-0000-0000-000000000002");

    private final StubSourceReferenceRepository repository = new StubSourceReferenceRepository();
    private final CurrentUser currentUser = () -> new AuthenticatedUser(USER_ID);
    private final AiSourceReferenceValidator validator =
            new AiSourceReferenceValidator(currentUser, repository);

    @Test
    void acceptsOneReferenceActuallyIncludedInPrompt() {
        EvidenceChunk chunk = chunk(1, CHUNK_ID);
        repository.authorize(chunk);
        ValidatedAiResult<ExplanationResult> result = explanation(List.of(CHUNK_ID.toString()));

        assertThat(validator.validate(result, request(GroundingMode.STRICT_SOURCE, List.of(chunk)),
                prompt(List.of(included(chunk))))).isSameAs(result);
    }

    @Test
    void acceptsMultipleValidReferences() {
        EvidenceChunk first = chunk(1, CHUNK_ID);
        EvidenceChunk second = chunk(2, SECOND_CHUNK_ID);
        repository.authorize(first);
        repository.authorize(second);

        assertThat(validator.validate(
                explanation(List.of(CHUNK_ID.toString(), SECOND_CHUNK_ID.toString())),
                request(GroundingMode.SOURCE_FIRST, List.of(first, second)),
                prompt(List.of(included(first), included(second)))).result().sourceReferences())
                .containsExactly(CHUNK_ID.toString(), SECOND_CHUNK_ID.toString());
    }

    @Test
    void acceptsEmptyReferencesForGeneralKnowledgeWithoutAuthorizationLookup() {
        repository.failure = new IllegalStateException("repository must not be called");
        ValidatedAiResult<ExplanationResult> result = explanation(List.of());

        assertThat(validator.validate(
                result, request(GroundingMode.GENERAL_KNOWLEDGE, List.of()), prompt(List.of())))
                .isSameAs(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-a-uuid", "1-1-1-1-1", "50000000-0000-0000-0000-000000000001 "})
    void rejectsBlankMalformedAndNonCanonicalReferences(String reference) {
        assertFailure(explanation(List.of(reference)), request(GroundingMode.STRICT_SOURCE, List.of()),
                prompt(List.of()), AiGroundingValidationException.Reason.INVALID_REFERENCE);
    }

    @Test
    void rejectsFabricatedReference() {
        EvidenceChunk chunk = chunk(1, CHUNK_ID);

        assertFailure(explanation(List.of(SECOND_CHUNK_ID.toString())),
                request(GroundingMode.STRICT_SOURCE, List.of(chunk)), prompt(List.of(included(chunk))),
                AiGroundingValidationException.Reason.REFERENCE_NOT_IN_PROMPT);
    }

    @Test
    void rejectsDuplicateProviderReferences() {
        EvidenceChunk chunk = chunk(1, CHUNK_ID);

        assertFailure(explanation(List.of(CHUNK_ID.toString(), CHUNK_ID.toString())),
                request(GroundingMode.STRICT_SOURCE, List.of(chunk)), prompt(List.of(included(chunk))),
                AiGroundingValidationException.Reason.DUPLICATE_REFERENCE);
    }

    @Test
    void rejectsEvidenceThatWasTrimmedOutOfActualPrompt() {
        EvidenceChunk chunk = chunk(1, CHUNK_ID);

        assertFailure(explanation(List.of(CHUNK_ID.toString())),
                request(GroundingMode.STRICT_SOURCE, List.of(chunk)), prompt(List.of()),
                AiGroundingValidationException.Reason.REFERENCE_NOT_IN_PROMPT);
    }

    @Test
    void rejectsSourceOwnedByAnotherUser() {
        EvidenceChunk chunk = chunk(1, CHUNK_ID);
        repository.authorizedUserId = OTHER_USER_ID;
        repository.authorize(chunk);

        assertFailure(explanation(List.of(CHUNK_ID.toString())),
                request(GroundingMode.STRICT_SOURCE, List.of(chunk)), prompt(List.of(included(chunk))),
                AiGroundingValidationException.Reason.AUTHORIZATION_NOT_CONFIRMED);
    }

    @Test
    void failsClosedWhenAuthoritativeRepositoryCannotConfirmAccess() {
        EvidenceChunk chunk = chunk(1, CHUNK_ID);
        repository.failure = new IllegalStateException("database details: private-source");

        assertFailure(explanation(List.of(CHUNK_ID.toString())),
                request(GroundingMode.STRICT_SOURCE, List.of(chunk)), prompt(List.of(included(chunk))),
                AiGroundingValidationException.Reason.AUTHORIZATION_NOT_CONFIRMED);
    }

    @ParameterizedTest(name = "rejects source when authoritative state is {0}")
    @ValueSource(strings = {"deleted material", "inactive material version", "inactive chunk", "non-current version"})
    void rejectsInactiveOrNonCurrentSource(String authoritativeState) {
        EvidenceChunk chunk = chunk(1, CHUNK_ID);

        assertFailure(explanation(List.of(CHUNK_ID.toString())),
                request(GroundingMode.STRICT_SOURCE, List.of(chunk)), prompt(List.of(included(chunk))),
                AiGroundingValidationException.Reason.AUTHORIZATION_NOT_CONFIRMED);
        assertThat(authoritativeState).isNotBlank();
    }

    @ParameterizedTest(name = "rejects prompt/evidence mismatch in {0}")
    @MethodSource("mismatchedPromptSources")
    void rejectsMismatchedApplicationOwnedProvenance(
            String field, PromptContext.IncludedSource mismatchedSource) {
        EvidenceChunk chunk = chunk(1, CHUNK_ID);

        assertFailure(explanation(List.of(CHUNK_ID.toString())),
                request(GroundingMode.STRICT_SOURCE, List.of(chunk)), prompt(List.of(mismatchedSource)),
                AiGroundingValidationException.Reason.EVIDENCE_PROVENANCE_MISMATCH);
        assertThat(field).isNotBlank();
    }

    @Test
    void rejectsProvenanceThatChangedInAuthoritativeCurrentState() {
        EvidenceChunk chunk = chunk(1, CHUNK_ID);
        repository.authorize(chunk);
        repository.sources.put(CHUNK_ID, new SourceReferenceSeed(
                MATERIAL_ID, VERSION_ID, UUID.randomUUID(), CHUNK_ID, null, 7,
                "Private material", "Changed node"));

        assertFailure(explanation(List.of(CHUNK_ID.toString())),
                request(GroundingMode.STRICT_SOURCE, List.of(chunk)), prompt(List.of(included(chunk))),
                AiGroundingValidationException.Reason.CURRENT_PROVENANCE_MISMATCH);
    }

    @ParameterizedTest
    @MethodSource("resultsWithSourceReference")
    void extractsReferencesFromEveryCurrentResultType(Object output) {
        EvidenceChunk chunk = chunk(1, CHUNK_ID);
        repository.authorize(chunk);

        assertThat(validator.validate(new ValidatedAiResult<>(output),
                request(GroundingMode.STRICT_SOURCE, List.of(chunk)), prompt(List.of(included(chunk)))))
                .isNotNull();
    }

    @Test
    void groundingFailureIsTypedGenericAndDoesNotLeakPrivateContent() {
        EvidenceChunk chunk = chunk(1, CHUNK_ID);
        repository.failure = new IllegalStateException("secret provider output and private-source");

        assertThatThrownBy(() -> validator.validate(
                explanation(List.of(CHUNK_ID.toString())),
                request(GroundingMode.STRICT_SOURCE, List.of(chunk)), prompt(List.of(included(chunk)))))
                .isInstanceOfSatisfying(AiGroundingValidationException.class, failure -> {
                    assertThat(failure.errorCode().value()).isEqualTo("AI_GROUNDING_FAILURE");
                    assertThat(failure.getMessage())
                            .doesNotContain("secret", "provider", "private-source", CHUNK_ID.toString());
                    assertThat(failure.getCause()).isNull();
                });
    }

    private void assertFailure(
            ValidatedAiResult<?> result,
            AiTaskRequest<?> request,
            PromptContext context,
            AiGroundingValidationException.Reason reason) {
        assertThatThrownBy(() -> validator.validate(result, request, context))
                .isInstanceOfSatisfying(AiGroundingValidationException.class, failure -> {
                    assertThat(failure.errorCode().value()).isEqualTo("AI_GROUNDING_FAILURE");
                    assertThat(failure.reason()).isEqualTo(reason);
                });
    }

    private static Stream<Arguments> mismatchedPromptSources() {
        return Stream.of(
                Arguments.of("material", source(UUID.randomUUID(), VERSION_ID, NODE_ID, 7, 7)),
                Arguments.of("material version", source(MATERIAL_ID, UUID.randomUUID(), NODE_ID, 7, 7)),
                Arguments.of("document node", source(MATERIAL_ID, VERSION_ID, UUID.randomUUID(), 7, 7)),
                Arguments.of("page start", source(MATERIAL_ID, VERSION_ID, NODE_ID, 6, 7)),
                Arguments.of("page end", source(MATERIAL_ID, VERSION_ID, NODE_ID, 7, 8)));
    }

    private static Stream<Object> resultsWithSourceReference() {
        List<String> references = List.of(CHUNK_ID.toString());
        return Stream.of(
                new ExplanationResult("concept", "explanation", List.of(), List.of(), references, false, List.of()),
                new QuestionGenerationResult(ActivityType.MCQ, "concept", "objective", "question",
                        List.of(new QuestionOption("A", "answer")), "A", "answer", "explanation",
                        QuestionDifficulty.FOUNDATIONAL, references, List.of()),
                new ResponseEvaluationResult(Evaluation.CORRECT, List.of(), List.of(), List.of(), "feedback",
                        EvaluationCertainty.SUFFICIENT, RecommendedAction.CONTINUE, references, List.of()),
                new ConceptConnectionResult("from", "to", "type", "relationship", "why", references, List.of()),
                new ContextualApplicationResult("scenario", "question", "concept", List.of("reason"),
                        "answer", List.of("feedback"), ApplicationDifficulty.FOUNDATIONAL_APPLIED,
                        references, List.of()));
    }

    private static ValidatedAiResult<ExplanationResult> explanation(List<String> references) {
        return new ValidatedAiResult<>(new ExplanationResult(
                "concept", "private source content", List.of(), List.of(), references, false, List.of()));
    }

    private static AiTaskRequest<ExplanationInput> request(
            GroundingMode groundingMode, List<EvidenceChunk> chunks) {
        EvidencePackage evidence = evidence(groundingMode, chunks);
        return new AiTaskRequest<>(AiTaskType.EXPLANATION, "EXPLANATION_V1",
                new LearnerContext("LEARNING", "FIRST", "STEADY", Map.of(), List.of()),
                new ExplanationInput("objective", "concept", ExplanationMode.STANDARD),
                evidence, groundingMode, AiOutputContract.EXPLANATION);
    }

    private static EvidencePackage evidence(GroundingMode groundingMode, List<EvidenceChunk> chunks) {
        List<EvidenceSourceReference> references = chunks.stream().map(chunk -> new EvidenceSourceReference(
                EvidenceReferenceKind.CHUNK, chunk.materialId(), chunk.materialVersionId(),
                chunk.documentNodeId(), chunk.chunkId(), null,
                chunk.pageStart().equals(chunk.pageEnd()) ? chunk.pageStart() : null)).toList();
        RetrievalQuality quality = chunks.isEmpty() ? RetrievalQuality.FAILED : RetrievalQuality.STRONG;
        return new EvidencePackage(quality, groundingMode, chunks, List.of(), references, List.of(),
                new RetrievalDiagnostics(chunks.size(), chunks.size(),
                        chunks.stream().map(EvidenceChunk::chunkId).toList(), List.of(),
                        chunks.isEmpty() ? Set.of() : Set.of(MATERIAL_ID), Set.of(), quality));
    }

    private static EvidenceChunk chunk(int rank, UUID chunkId) {
        return new EvidenceChunk(rank, chunkId, MATERIAL_ID, VERSION_ID, NODE_ID, rank,
                "private source content", 7, 7, List.of("Section"), "TEXT", "NATIVE", "GOOD");
    }

    private static PromptContext prompt(List<PromptContext.IncludedSource> sources) {
        return new PromptContext(PromptId.HIPPOCAMPUS_SYSTEM_V1, PromptId.EXPLANATION_V1,
                "system", "task", 10, 100, sources);
    }

    private static PromptContext.IncludedSource included(EvidenceChunk chunk) {
        return new PromptContext.IncludedSource(chunk.rank(), chunk.chunkId(), chunk.materialId(),
                chunk.materialVersionId(), chunk.documentNodeId(), chunk.pageStart(), chunk.pageEnd());
    }

    private static PromptContext.IncludedSource source(
            UUID materialId, UUID versionId, UUID nodeId, Integer pageStart, Integer pageEnd) {
        return new PromptContext.IncludedSource(1, CHUNK_ID, materialId, versionId, nodeId, pageStart, pageEnd);
    }

    private static final class StubSourceReferenceRepository implements SourceReferenceRepository {
        private final Map<UUID, SourceReferenceSeed> sources = new HashMap<>();
        private UUID authorizedUserId = USER_ID;
        private RuntimeException failure;

        private void authorize(EvidenceChunk chunk) {
            sources.put(chunk.chunkId(), new SourceReferenceSeed(
                    chunk.materialId(), chunk.materialVersionId(), chunk.documentNodeId(), chunk.chunkId(),
                    null, chunk.pageStart().equals(chunk.pageEnd()) ? chunk.pageStart() : null,
                    "Material", "Section"));
        }

        @Override
        public Optional<SourceReferenceSeed> findAuthorizedTarget(UUID userId, SourceReferenceTarget target) {
            if (failure != null) {
                throw failure;
            }
            if (!authorizedUserId.equals(userId)) {
                return Optional.empty();
            }
            if (!(target instanceof ChunkSourceTarget chunkTarget)) {
                return Optional.empty();
            }
            return Optional.ofNullable(sources.get(chunkTarget.chunkId()));
        }

        @Override
        public SourceReference upsert(SourceReferenceSeed seed, String displayLabel) {
            return new SourceReference(UUID.randomUUID(), seed.materialId(), seed.materialVersionId(),
                    seed.documentNodeId(), seed.chunkId(), seed.visualAssetId(), seed.pageNumber(), null, null,
                    displayLabel, Instant.EPOCH);
        }

        @Override
        public Optional<SourceReference> resolveAuthorized(UUID userId, UUID sourceReferenceId) {
            return Optional.empty();
        }
    }
}
