package com.hippocampus.rag.port;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record EmbeddingBatchRequest(List<EmbeddingInput> inputs) {
    public EmbeddingBatchRequest {
        inputs = List.copyOf(Objects.requireNonNull(inputs, "inputs must not be null"));
        if (inputs.isEmpty()) {
            throw new IllegalArgumentException("embedding batch must not be empty");
        }

        Set<UUID> references = new HashSet<>();
        for (EmbeddingInput input : inputs) {
            if (!references.add(input.referenceId())) {
                throw new IllegalArgumentException("embedding input references must be unique");
            }
        }
    }
}
