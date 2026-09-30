package com.hippocampus.ai.domain;

import java.util.Objects;

public record ValidatedAiResult<T>(T result, ExecutionMetadata executionMetadata) {

    public ValidatedAiResult(T result) {
        this(result, null);
    }

    public ValidatedAiResult {
        Objects.requireNonNull(result, "result must not be null");
    }

    public ValidatedAiResult<T> withExecutionMetadata(ExecutionMetadata metadata) {
        return new ValidatedAiResult<>(result, Objects.requireNonNull(metadata, "metadata must not be null"));
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof ValidatedAiResult<?> that && result.equals(that.result);
    }

    @Override
    public int hashCode() {
        return result.hashCode();
    }

    public record ExecutionMetadata(
            String provider,
            String model,
            String modelVersion,
            String promptId,
            String promptVersion) {

        public ExecutionMetadata {
            provider = required(provider, "provider");
            model = required(model, "model");
            if (modelVersion != null && modelVersion.isBlank()) {
                throw new IllegalArgumentException("modelVersion must not be blank");
            }
            promptId = required(promptId, "promptId");
            promptVersion = required(promptVersion, "promptVersion");
        }

        private static String required(String value, String name) {
            Objects.requireNonNull(value, name + " must not be null");
            if (value.isBlank()) {
                throw new IllegalArgumentException(name + " must not be blank");
            }
            return value;
        }
    }
}
