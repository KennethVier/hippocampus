package com.hippocampus.ai.domain;

import java.util.Objects;

public record ValidatedAiResult<T>(T result) {

    public ValidatedAiResult {
        Objects.requireNonNull(result, "result must not be null");
    }
}
