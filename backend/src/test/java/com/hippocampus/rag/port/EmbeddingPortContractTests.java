package com.hippocampus.rag.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class EmbeddingPortContractTests {
    private static final UUID FIRST_REFERENCE = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_REFERENCE = UUID.fromString("10000000-0000-0000-0000-000000000002");

    @Test
    void embedsMultipleInputsAndPreservesCallerControlledReferences() {
        EmbeddingBatchRequest request = request();

        EmbeddingBatchResult result = new CountingFakeEmbeddingAdapter().embed(request);

        assertThat(result.vectors()).extracting(EmbeddingVectorResult::referenceId)
                .containsExactly(FIRST_REFERENCE, SECOND_REFERENCE);
        assertThat(result.vectors()).extracting(vector -> vector.vector().values())
                .containsExactly(List.of(5.0F, 1.0F), List.of(5.0F, 1.0F));
    }

    @Test
    void differentProvidersSatisfyTheSamePortWithoutCallerChanges() {
        EmbeddingBatchRequest request = request();

        EmbeddingBatchResult counting = embed(new CountingFakeEmbeddingAdapter(), request);
        EmbeddingBatchResult constant = embed(new ConstantFakeEmbeddingAdapter(), request);

        assertThat(counting.model())
                .isEqualTo(new EmbeddingModelMetadata("counting-fake", "word-shape", "1", 2));
        assertThat(constant.model())
                .isEqualTo(new EmbeddingModelMetadata("constant-fake", "constant-shape", "test", 2));
        assertThat(counting.usage().inputTokenCount()).isEqualTo(6);
        assertThat(constant.usage().inputTokenCount()).isZero();
        assertThat(counting.vectors()).allSatisfy(vector -> assertThat(vector.vector().dimension()).isEqualTo(2));
        assertThat(constant.vectors()).allSatisfy(vector -> assertThat(vector.vector().dimension()).isEqualTo(2));
    }

    @Test
    void fakeProviderTypesRemainInsideTheAdapter() throws NoSuchMethodException {
        assertThat(EmbeddingPort.class.getMethod("embed", EmbeddingBatchRequest.class).getReturnType())
                .isEqualTo(EmbeddingBatchResult.class);
        assertThat(embed(new ProviderPayloadFakeEmbeddingAdapter(), request()).vectors())
                .extracting(EmbeddingVectorResult::vector)
                .allSatisfy(vector -> assertThat(vector).isInstanceOf(EmbeddingVector.class));
    }

    @Test
    void requestAndResultCollectionsAreDefensivelyImmutable() {
        List<EmbeddingInput> mutableInputs = new ArrayList<>();
        mutableInputs.add(new EmbeddingInput(FIRST_REFERENCE, "radial nerve"));
        EmbeddingBatchRequest request = new EmbeddingBatchRequest(mutableInputs);
        mutableInputs.clear();

        List<Float> mutableValues = new ArrayList<>(List.of(1.0F, 2.0F));
        EmbeddingVector vector = new EmbeddingVector(mutableValues);
        mutableValues.set(0, 99.0F);
        List<EmbeddingVectorResult> mutableResults = new ArrayList<>();
        mutableResults.add(new EmbeddingVectorResult(FIRST_REFERENCE, vector));
        EmbeddingBatchResult result = new EmbeddingBatchResult(metadata(), usage(), mutableResults);
        mutableResults.clear();

        assertThat(request.inputs()).hasSize(1);
        assertThat(vector.values()).containsExactly(1.0F, 2.0F);
        assertThat(result.vectors()).hasSize(1);
        assertThatThrownBy(() -> request.inputs().add(new EmbeddingInput(SECOND_REFERENCE, "wrist drop")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> vector.values().add(3.0F)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.vectors().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsInvalidRequestsAndInputs() {
        EmbeddingInput valid = new EmbeddingInput(FIRST_REFERENCE, "radial nerve");

        assertThatThrownBy(() -> new EmbeddingBatchRequest(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EmbeddingBatchRequest(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmbeddingBatchRequest(Arrays.asList(valid, null)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EmbeddingInput(null, "text")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EmbeddingInput(FIRST_REFERENCE, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EmbeddingInput(FIRST_REFERENCE, " \t"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmbeddingBatchRequest(List.of(valid, valid)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidVectors() {
        assertThatThrownBy(() -> new EmbeddingVector(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EmbeddingVector(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmbeddingVector(Arrays.asList(1.0F, null)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EmbeddingVector(List.of(Float.NaN)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmbeddingVector(List.of(Float.POSITIVE_INFINITY)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmbeddingVector(List.of(Float.NEGATIVE_INFINITY)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidMetadataAndUsage() {
        assertThatThrownBy(() -> new EmbeddingModelMetadata(null, "model", "1", 2))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EmbeddingModelMetadata(" ", "model", "1", 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmbeddingModelMetadata("provider", "", "1", 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmbeddingModelMetadata("provider", "model", "\n", 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmbeddingModelMetadata("provider", "model", "1", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmbeddingUsageMetadata(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidBatchResults() {
        EmbeddingVectorResult valid = result(FIRST_REFERENCE, 1.0F, 2.0F);

        assertThatThrownBy(() -> new EmbeddingBatchResult(null, usage(), List.of(valid)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EmbeddingBatchResult(metadata(), null, List.of(valid)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EmbeddingBatchResult(metadata(), usage(), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EmbeddingBatchResult(metadata(), usage(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmbeddingBatchResult(metadata(), usage(), Arrays.asList(valid, null)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EmbeddingVectorResult(null, valid.vector()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EmbeddingVectorResult(FIRST_REFERENCE, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EmbeddingBatchResult(metadata(), usage(), List.of(valid, valid)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmbeddingBatchResult(metadata(), usage(),
                List.of(result(FIRST_REFERENCE, 1.0F))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static EmbeddingBatchResult embed(EmbeddingPort port, EmbeddingBatchRequest request) {
        return port.embed(request);
    }

    private static EmbeddingBatchRequest request() {
        return new EmbeddingBatchRequest(List.of(
                new EmbeddingInput(FIRST_REFERENCE, "radial nerve injury"),
                new EmbeddingInput(SECOND_REFERENCE, "wrist drop mechanism")));
    }

    private static EmbeddingVectorResult result(UUID reference, Float... values) {
        return new EmbeddingVectorResult(reference, new EmbeddingVector(List.of(values)));
    }

    private static EmbeddingModelMetadata metadata() {
        return new EmbeddingModelMetadata("fake-provider", "fake-model", "1", 2);
    }

    private static EmbeddingUsageMetadata usage() {
        return new EmbeddingUsageMetadata(0);
    }

    private static final class CountingFakeEmbeddingAdapter implements EmbeddingPort {
        @Override
        public EmbeddingBatchResult embed(EmbeddingBatchRequest request) {
            List<EmbeddingVectorResult> vectors = request.inputs().stream()
                    .map(input -> result(input.referenceId(),
                            (float) input.text().split(" ").length + 2.0F,
                            (float) input.text().split(" ").length - 2.0F))
                    .toList();
            return new EmbeddingBatchResult(
                    new EmbeddingModelMetadata("counting-fake", "word-shape", "1", 2),
                    new EmbeddingUsageMetadata(request.inputs().stream()
                            .mapToLong(input -> input.text().split(" ").length)
                            .sum()),
                    vectors);
        }
    }

    private static final class ConstantFakeEmbeddingAdapter implements EmbeddingPort {
        @Override
        public EmbeddingBatchResult embed(EmbeddingBatchRequest request) {
            List<EmbeddingVectorResult> vectors = request.inputs().stream()
                    .map(input -> result(input.referenceId(), 0.25F, 0.75F))
                    .toList();
            return new EmbeddingBatchResult(
                    new EmbeddingModelMetadata("constant-fake", "constant-shape", "test", 2),
                    new EmbeddingUsageMetadata(0),
                    vectors);
        }
    }

    private static final class ProviderPayloadFakeEmbeddingAdapter implements EmbeddingPort {
        @Override
        public EmbeddingBatchResult embed(EmbeddingBatchRequest request) {
            List<FakeProviderPayload> providerPayloads = request.inputs().stream()
                    .map(input -> new FakeProviderPayload(input.referenceId(), new float[] {0.5F, 0.5F}))
                    .toList();
            List<EmbeddingVectorResult> vectors = providerPayloads.stream()
                    .map(payload -> result(payload.reference(), payload.providerVector()[0], payload.providerVector()[1]))
                    .toList();
            return new EmbeddingBatchResult(metadata(), usage(), vectors);
        }

        private record FakeProviderPayload(UUID reference, float[] providerVector) {}
    }
}
