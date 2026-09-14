package com.hippocampus.rag.infrastructure.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;

import com.hippocampus.rag.port.EmbeddingBatchRequest;
import com.hippocampus.rag.port.EmbeddingBatchResult;
import com.hippocampus.rag.port.EmbeddingFailureException;
import com.hippocampus.rag.port.EmbeddingFailureException.Reason;
import com.hippocampus.rag.port.EmbeddingInput;
import com.hippocampus.rag.port.EmbeddingVector;

class GeminiEmbeddingAdapterTests {
    private static final UUID FIRST = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID SECOND = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final int DIMENSION = 3;

    @Test
    void embedsOneUnchangedBatchAndCorrelatesShuffledResultsByIndex() {
        CapturingModel model = new CapturingModel(response(
                List.of(embedding(1, 4, 5, 6), embedding(0, 1, 2, 3)),
                metadata("models/gemini-embedding-2", 11)));
        GeminiEmbeddingAdapter adapter = adapter(model);

        EmbeddingBatchResult result = adapter.embed(request());

        assertThat(model.request.getInstructions()).containsExactly("radial nerve", "wrist drop");
        assertThat(result.vectors()).extracting(vector -> vector.referenceId()).containsExactly(FIRST, SECOND);
        assertThat(result.vectors()).extracting(vector -> vector.vector().values())
                .containsExactly(List.of(1F, 2F, 3F), List.of(4F, 5F, 6F));
        assertThat(result.model().provider()).isEqualTo("google-genai");
        assertThat(result.model().model()).isEqualTo("models/gemini-embedding-2");
        assertThat(result.model().version()).isNull();
        assertThat(result.model().dimension()).isEqualTo(DIMENSION);
        assertThat(result.usage().inputTokenCount()).hasValue(11);
        assertThat(result.vectors()).allSatisfy(vector -> assertThat(vector.vector()).isInstanceOf(EmbeddingVector.class));
    }

    @Test
    void fallsBackToConfiguredModelAndMarksMissingUsageUnavailable() {
        EmbeddingResponseMetadata metadata = new EmbeddingResponseMetadata();
        metadata.setModel(" ");
        metadata.setUsage(null);
        EmbeddingBatchResult result = adapter(new CapturingModel(response(validEmbeddings(), metadata))).embed(request());

        assertThat(result.model().model()).isEqualTo("gemini-embedding-2");
        assertThat(result.usage().inputTokenCount()).isEmpty();
    }

    @Test
    void preservesReportedZeroUsageAsAvailable() {
        EmbeddingBatchResult result = adapter(new CapturingModel(response(
                validEmbeddings(), metadata("gemini-embedding-2", 0)))).embed(request());

        assertThat(result.usage().inputTokenCount()).hasValue(0);
    }

    @Test
    void rejectsNegativeUsage() {
        assertInvalidResponse(response(validEmbeddings(), metadata("gemini-embedding-2", -1)));
    }

    @Test
    void rejectsMissingDuplicateNegativeAndOutOfRangeIndices() {
        assertInvalidResponse(response(List.of(new Embedding(vector(1, 2, 3), null), embedding(1, 4, 5, 6)), metadata()));
        assertInvalidResponse(response(List.of(embedding(0, 1, 2, 3), embedding(0, 4, 5, 6)), metadata()));
        assertInvalidResponse(response(List.of(embedding(-1, 1, 2, 3), embedding(1, 4, 5, 6)), metadata()));
        assertInvalidResponse(response(List.of(embedding(0, 1, 2, 3), embedding(2, 4, 5, 6)), metadata()));
    }

    @Test
    void rejectsTooFewTooManyAndNullResults() {
        assertInvalidResponse(response(List.of(embedding(0, 1, 2, 3)), metadata()));
        assertInvalidResponse(response(
                List.of(embedding(0, 1, 2, 3), embedding(1, 4, 5, 6), embedding(2, 7, 8, 9)), metadata()));
        assertInvalidResponse(null);
    }

    @Test
    void rejectsWrongSizedEmptyNullAndNonFiniteVectors() {
        assertInvalidResponse(response(List.of(embedding(0, 1, 2), embedding(1, 4, 5, 6)), metadata()));
        assertInvalidResponse(response(List.of(embedding(0), embedding(1, 4, 5, 6)), metadata()));
        assertInvalidResponse(response(
                List.of(new Embedding(null, 0), embedding(1, 4, 5, 6)), metadata()));
        assertInvalidResponse(response(List.of(embedding(0, Float.NaN, 2, 3), embedding(1, 4, 5, 6)), metadata()));
        assertInvalidResponse(response(
                List.of(embedding(0, Float.POSITIVE_INFINITY, 2, 3), embedding(1, 4, 5, 6)), metadata()));
        assertInvalidResponse(response(
                List.of(embedding(0, Float.NEGATIVE_INFINITY, 2, 3), embedding(1, 4, 5, 6)), metadata()));
    }

    @Test
    void rejectsInvalidAdapterConfiguration() {
        CapturingModel model = new CapturingModel(response(validEmbeddings(), metadata()));

        assertThatThrownBy(() -> new GeminiEmbeddingAdapter(null, "model", DIMENSION))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new GeminiEmbeddingAdapter(model, " ", DIMENSION))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeminiEmbeddingAdapter(model, "model", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizesProviderFailuresWithoutLeakingProviderExceptionOrSensitiveMessage() {
        String sensitiveMarker = testCredential();
        EmbeddingModel failing = new EmbeddingModel() {
            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                throw new ProviderClientFailure(sensitiveMarker + " and raw input radial nerve");
            }

            @Override
            public float[] embed(Document document) {
                throw new UnsupportedOperationException();
            }
        };

        assertThatThrownBy(() -> adapter(failing).embed(request()))
                .isInstanceOfSatisfying(EmbeddingFailureException.class, failure -> {
                    assertThat(failure.reason()).isEqualTo(Reason.PROVIDER_FAILURE);
                    assertThat(failure.getMessage()).isEqualTo("Embedding provider request failed");
                    assertThat(failure.getMessage()).doesNotContain(sensitiveMarker, "radial nerve");
                    assertThat(failure.getCause()).isNull();
                });
    }

    private static void assertInvalidResponse(EmbeddingResponse response) {
        assertThatThrownBy(() -> adapter(new CapturingModel(response)).embed(request()))
                .isInstanceOfSatisfying(EmbeddingFailureException.class,
                        failure -> assertThat(failure.reason()).isEqualTo(Reason.INVALID_RESPONSE));
    }

    private static GeminiEmbeddingAdapter adapter(EmbeddingModel model) {
        return new GeminiEmbeddingAdapter(model, "gemini-embedding-2", DIMENSION);
    }

    private static EmbeddingBatchRequest request() {
        return new EmbeddingBatchRequest(List.of(
                new EmbeddingInput(FIRST, "radial nerve"),
                new EmbeddingInput(SECOND, "wrist drop")));
    }

    private static List<Embedding> validEmbeddings() {
        return List.of(embedding(0, 1, 2, 3), embedding(1, 4, 5, 6));
    }

    private static Embedding embedding(int index, float... values) {
        return new Embedding(values, index);
    }

    private static float[] vector(float... values) {
        return values;
    }

    private static String testCredential() {
        return "test-" + UUID.randomUUID();
    }

    private static EmbeddingResponse response(List<Embedding> embeddings, EmbeddingResponseMetadata metadata) {
        return new EmbeddingResponse(embeddings, metadata);
    }

    private static EmbeddingResponseMetadata metadata() {
        return metadata("gemini-embedding-2", 5);
    }

    private static EmbeddingResponseMetadata metadata(String model, Integer totalTokens) {
        return new EmbeddingResponseMetadata(model, new DefaultUsage(0, 0, totalTokens));
    }

    private static final class CapturingModel implements EmbeddingModel {
        private final EmbeddingResponse response;
        private EmbeddingRequest request;

        private CapturingModel(EmbeddingResponse response) {
            this.response = response;
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            this.request = request;
            return response;
        }

        @Override
        public float[] embed(Document document) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class ProviderClientFailure extends RuntimeException {
        private ProviderClientFailure(String message) {
            super(message);
        }
    }
}
