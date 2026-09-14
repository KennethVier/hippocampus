package com.hippocampus.rag.port;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VectorSearchHitTests {

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsNonFiniteCosineSimilarity(double cosineSimilarity) {
        assertThatThrownBy(() -> new VectorSearchHit(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                UUID.randomUUID(), 1, "Content", null, null, List.of(),
                "TEXT", "NATIVE", "STRONG", cosineSimilarity))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cosineSimilarity must be finite");
    }
}
