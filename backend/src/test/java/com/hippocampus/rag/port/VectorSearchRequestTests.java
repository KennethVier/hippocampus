package com.hippocampus.rag.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hippocampus.rag.domain.GroundingMode;
import com.hippocampus.rag.domain.RetrievalScope;

class VectorSearchRequestTests {
    @Test
    void preservesEmbeddingValuesAndScope() {
        RetrievalScope scope = new RetrievalScope(
                UUID.randomUUID(), UUID.randomUUID(), GroundingMode.SOURCE_FIRST, Set.of());
        EmbeddingVector embedding = new EmbeddingVector(List.of(3.0F, 4.0F));
        VectorSearchRequest request = new VectorSearchRequest(scope, UUID.randomUUID(), embedding, 5);
        assertThat(request.scope()).isSameAs(scope);
        assertThat(request.queryEmbedding()).isSameAs(embedding);
        assertThat(request.queryEmbedding().values()).containsExactly(3.0F, 4.0F);
    }

    @Test
    void rejectsMissingAndNonPositiveRequestValues() {
        RetrievalScope scope = new RetrievalScope(
                UUID.randomUUID(), UUID.randomUUID(), GroundingMode.STRICT_SOURCE, Set.of());
        UUID generation = UUID.randomUUID();
        EmbeddingVector embedding = new EmbeddingVector(List.of(1.0F));
        assertThatThrownBy(() -> new VectorSearchRequest(null, generation, embedding, 1))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new VectorSearchRequest(scope, null, embedding, 1))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new VectorSearchRequest(scope, generation, null, 1))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new VectorSearchRequest(scope, generation, embedding, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAllZeroEmbeddingIncludingNegativeZero() {
        RetrievalScope scope = new RetrievalScope(
                UUID.randomUUID(), UUID.randomUUID(), GroundingMode.STRICT_SOURCE, Set.of());
        assertThatThrownBy(() -> new VectorSearchRequest(scope, UUID.randomUUID(),
                new EmbeddingVector(List.of(0.0F, -0.0F)), 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("all-zero");
    }
}
