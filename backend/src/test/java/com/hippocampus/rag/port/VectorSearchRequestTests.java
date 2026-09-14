package com.hippocampus.rag.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class VectorSearchRequestTests {

    @Test
    void preservesEmbeddingValuesAndDefensivelyCopiesScope() {
        UUID user = UUID.randomUUID();
        UUID version = UUID.randomUUID();
        UUID node = UUID.randomUUID();
        Set<UUID> versions = new HashSet<>(Set.of(version));
        Set<UUID> nodes = new HashSet<>(Set.of(node));
        EmbeddingVector embedding = new EmbeddingVector(List.of(3.0F, 4.0F));

        VectorSearchRequest request = new VectorSearchRequest(
                new VectorSearchScope(user, versions, nodes), UUID.randomUUID(), embedding, 5);
        versions.clear();
        nodes.clear();

        assertThat(request.queryEmbedding()).isSameAs(embedding);
        assertThat(request.queryEmbedding().values()).containsExactly(3.0F, 4.0F);
        assertThat(request.scope().allowedMaterialVersionIds()).containsExactly(version);
        assertThat(request.scope().allowedDocumentNodeIds()).containsExactly(node);
    }

    @Test
    void rejectsMissingAndNonPositiveRequestValues() {
        VectorSearchScope scope = new VectorSearchScope(UUID.randomUUID(), Set.of(), Set.of());
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
        VectorSearchScope scope = new VectorSearchScope(UUID.randomUUID(), Set.of(), Set.of());

        assertThatThrownBy(() -> new VectorSearchRequest(scope, UUID.randomUUID(),
                new EmbeddingVector(List.of(0.0F, -0.0F)), 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("all-zero");
    }

    @Test
    void rejectsMissingScopeValues() {
        UUID user = UUID.randomUUID();

        assertThatThrownBy(() -> new VectorSearchScope(null, Set.of(), Set.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new VectorSearchScope(user, null, Set.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new VectorSearchScope(user, Set.of(), null))
                .isInstanceOf(NullPointerException.class);
    }
}
