package com.hippocampus.rag.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hippocampus.identity.domain.AuthenticatedUser;
import com.hippocampus.rag.domain.GroundingMode;
import com.hippocampus.rag.port.EmbeddingBatchResult;
import com.hippocampus.rag.port.EmbeddingFailureException;
import com.hippocampus.rag.port.EmbeddingModelMetadata;
import com.hippocampus.rag.port.EmbeddingUsageMetadata;
import com.hippocampus.rag.port.EmbeddingVector;
import com.hippocampus.rag.port.EmbeddingVectorResult;
import com.hippocampus.rag.port.IndexGeneration;
import com.hippocampus.rag.port.LexicalSearchHit;
import com.hippocampus.rag.port.RetrievalScopeSource;
import com.hippocampus.rag.port.VectorSearchHit;

class InspectRetrievalTests {
    private static final String PRIVATE = "PRIVATE SOURCE CHUNK SENTINEL";
    private static final EmbeddingModelMetadata MODEL = new EmbeddingModelMetadata("fake", "medical", "v1", 2);
    private final UUID userId = UUID.randomUUID();
    private final UUID topicId = UUID.randomUUID();
    private final UUID versionId = UUID.randomUUID();
    private final UUID generationId = UUID.randomUUID();

    @Test
    void emptyAuthorizedScopeShortCircuitsAllRetrievalAndReturnsImmutableEmptyInspection() {
        Fixture fixture = fixture(List.of());

        RetrievalInspection result = fixture.inspector().execute(query("posterior cord"));

        assertThat(fixture.lexicalCalls).isZero();
        assertThat(fixture.generationCalls).isZero();
        assertThat(fixture.embeddingCalls).isZero();
        assertThat(fixture.vectorCalls).isZero();
        assertThat(result.scope().targets()).isEmpty();
        assertThat(result.vector().status()).isEqualTo(RetrievalInspection.VectorStatus.UNAVAILABLE_EMPTY_SCOPE);
        assertThatThrownBy(() -> result.lexical().candidates().add(null)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void noEmbeddingProviderReturnsOrderedPrivacySafeLexicalAndHybridDiagnostics() {
        Fixture fixture = fixture(scopeRows());
        fixture.embeddingAvailable = false;
        fixture.lexicalHits = List.of(lexical(2), lexical(1));

        RetrievalInspection result = fixture.inspector().execute(query("  Na+ posterior cord  "));

        assertThat(fixture.seenQuery).isEqualTo("  Na+ posterior cord  ");
        assertThat(result.query()).isEqualTo("  Na+ posterior cord  ");
        assertThat(result.lexical().candidates()).extracting(RetrievalInspection.LexicalCandidate::chunkIndex)
                .containsExactly(2, 1);
        assertThat(result.vector().status()).isEqualTo(RetrievalInspection.VectorStatus.UNAVAILABLE_NO_EMBEDDING_PROVIDER);
        assertThat(result.hybrid().candidates()).hasSize(2);
        assertThat(result.toString()).doesNotContain(PRIVATE);
        assertThat(result.scope().targets()).singleElement().satisfies(target ->
                assertThat(target.materialVersionId()).isEqualTo(versionId));
    }

    @Test
    void noActiveGenerationSkipsProviderAndVectorSearch() {
        Fixture fixture = fixture(scopeRows());
        fixture.activeGeneration = Optional.empty();

        RetrievalInspection result = fixture.inspector().execute(query("posterior cord"));

        assertThat(result.vector().status())
                .isEqualTo(RetrievalInspection.VectorStatus.UNAVAILABLE_NO_ACTIVE_INDEX_GENERATION);
        assertThat(fixture.embeddingCalls).isZero();
        assertThat(fixture.vectorCalls).isZero();
    }

    @Test
    void compatibleEmbeddingUsesExactlyOneUnchangedInputAndReturnsOrderedVectorProjection() {
        Fixture fixture = fixture(scopeRows());
        fixture.lexicalHits = List.of(lexical(1));
        fixture.vectorHits = List.of(vector(2), vector(1));

        RetrievalInspection result = fixture.inspector().execute(query("Î²1 receptor"));

        assertThat(fixture.embeddingCalls).isEqualTo(1);
        assertThat(fixture.embeddingInputCount).isEqualTo(1);
        assertThat(fixture.seenEmbeddingText).isEqualTo("Î²1 receptor");
        assertThat(fixture.vectorCalls).isEqualTo(1);
        assertThat(result.vector().status()).isEqualTo(RetrievalInspection.VectorStatus.AVAILABLE);
        assertThat(result.vector().candidates()).extracting(RetrievalInspection.VectorCandidate::chunkIndex)
                .containsExactly(2, 1);
        assertThat(result.toString()).doesNotContain(PRIVATE);
    }

    @Test
    void providerModelMismatchFailsClosedBeforeVectorSearch() {
        Fixture fixture = fixture(scopeRows());
        fixture.returnedModel = new EmbeddingModelMetadata("fake", "other", "v1", 2);

        RetrievalInspection result = fixture.inspector().execute(query("posterior cord"));

        assertThat(result.vector().status()).isEqualTo(RetrievalInspection.VectorStatus.INCOMPATIBLE_INDEX_GENERATION);
        assertThat(fixture.vectorCalls).isZero();
    }

    @Test
    void wrongCorrelationAndZeroVectorFailClosedBeforeVectorSearch() {
        Fixture wrongCorrelation = fixture(scopeRows());
        wrongCorrelation.returnWrongCorrelation = true;
        assertThat(wrongCorrelation.inspector().execute(query("posterior cord")).vector().status())
                .isEqualTo(RetrievalInspection.VectorStatus.INVALID_QUERY_EMBEDDING);
        assertThat(wrongCorrelation.vectorCalls).isZero();

        Fixture zero = fixture(scopeRows());
        zero.embeddingValues = List.of(0.0F, 0.0F);
        assertThat(zero.inspector().execute(query("posterior cord")).vector().status())
                .isEqualTo(RetrievalInspection.VectorStatus.INVALID_QUERY_EMBEDDING);
        assertThat(zero.vectorCalls).isZero();
    }

    @Test
    void knownProviderFailureReturnsFixedStatusWithoutRawMessage() {
        Fixture fixture = fixture(scopeRows());
        fixture.providerFails = true;

        RetrievalInspection result = fixture.inspector().execute(query("posterior cord"));

        assertThat(result.vector().status()).isEqualTo(RetrievalInspection.VectorStatus.EMBEDDING_PROVIDER_FAILED);
        assertThat(result.toString()).doesNotContain("secret provider payload");
        assertThat(fixture.vectorCalls).isZero();
    }

    @Test
    void configuredLimitsAreUsedAndQueryLengthIsBounded() {
        Fixture fixture = fixture(scopeRows());
        fixture.limits = new RetrievalInspectorLimits(3, 4, 5, 6);
        fixture.inspector().execute(query("cord"));
        assertThat(fixture.lexicalLimit).isEqualTo(3);
        assertThat(fixture.vectorLimit).isEqualTo(4);

        assertThatThrownBy(() -> fixture.inspector().execute(query("1234567")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private InspectRetrieval.Query query(String text) {
        return new InspectRetrieval.Query(topicId, text, GroundingMode.STRICT_SOURCE);
    }

    private List<RetrievalScopeSource> scopeRows() {
        return List.of(new RetrievalScopeSource(versionId, null));
    }

    private LexicalSearchHit lexical(int index) {
        return new LexicalSearchHit(new UUID(0, index), UUID.randomUUID(), versionId, null, index, PRIVATE,
                7, 8, List.of("Posterior Cord"), "TEXT", "NATIVE", "STRONG", true, 0.8, 0.7);
    }

    private VectorSearchHit vector(int index) {
        return new VectorSearchHit(new UUID(1, index), UUID.randomUUID(), versionId, null, generationId, index,
                PRIVATE, 7, 8, List.of("Posterior Cord"), "TEXT", "NATIVE", "STRONG", 0.9);
    }

    private Fixture fixture(List<RetrievalScopeSource> rows) {
        return new Fixture(rows);
    }

    private final class Fixture {
        private final List<RetrievalScopeSource> rows;
        private List<LexicalSearchHit> lexicalHits = new ArrayList<>();
        private List<VectorSearchHit> vectorHits = new ArrayList<>();
        private Optional<IndexGeneration> activeGeneration = Optional.of(
                new IndexGeneration(generationId, MODEL, "CHUNKER_V1", IndexGeneration.Status.ACTIVE));
        private boolean embeddingAvailable = true;
        private boolean providerFails;
        private boolean returnWrongCorrelation;
        private EmbeddingModelMetadata returnedModel = MODEL;
        private List<Float> embeddingValues = List.of(0.2F, 0.8F);
        private RetrievalInspectorLimits limits = new RetrievalInspectorLimits(20, 20, 20, 1000);
        private int lexicalCalls;
        private int generationCalls;
        private int embeddingCalls;
        private int vectorCalls;
        private int embeddingInputCount;
        private int lexicalLimit;
        private int vectorLimit;
        private String seenQuery;
        private String seenEmbeddingText;

        Fixture(List<RetrievalScopeSource> rows) { this.rows = rows; }

        InspectRetrieval inspector() {
            BuildRetrievalScope build = new BuildRetrievalScope(() -> new AuthenticatedUser(userId),
                    (authorizedUser, requestedTopic) -> {
                        assertThat(authorizedUser).isEqualTo(userId);
                        assertThat(requestedTopic).isEqualTo(topicId);
                        return rows;
                    });
            Optional<com.hippocampus.rag.port.EmbeddingPort> port = embeddingAvailable ? Optional.of(request -> {
                embeddingCalls++;
                embeddingInputCount = request.inputs().size();
                seenEmbeddingText = request.inputs().getFirst().text();
                if (providerFails) throw new EmbeddingFailureException(
                        EmbeddingFailureException.Reason.PROVIDER_FAILURE);
                UUID reference = returnWrongCorrelation ? UUID.randomUUID() : request.inputs().getFirst().referenceId();
                return new EmbeddingBatchResult(returnedModel, EmbeddingUsageMetadata.unavailable(),
                        List.of(new EmbeddingVectorResult(reference, new EmbeddingVector(embeddingValues))));
            }) : Optional.empty();
            return new InspectRetrieval(build, request -> {
                lexicalCalls++;
                seenQuery = request.query();
                lexicalLimit = request.limit();
                return lexicalHits;
            }, request -> {
                vectorCalls++;
                vectorLimit = request.limit();
                return vectorHits;
            }, new HybridCandidateMerger(), () -> {
                generationCalls++;
                return activeGeneration;
            }, port, limits);
        }
    }
}
