package com.hippocampus.rag.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EmbeddingProcessingPropertiesTests {
    @Test
    void rejectsNonPositiveBatchSize() {
        assertThatThrownBy(() -> new EmbeddingProcessingProperties(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmbeddingProcessingProperties(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
