package com.hippocampus.rag.port;

import java.util.Objects;
import java.util.UUID;

public record EmbeddingInput(UUID referenceId, String text) {
    public EmbeddingInput {
        Objects.requireNonNull(referenceId, "referenceId must not be null");
        Objects.requireNonNull(text, "text must not be null");
        if (text.isBlank()) {
            throw new IllegalArgumentException("embedding input text must not be blank");
        }
    }
}
