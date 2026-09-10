package com.hippocampus.materials.domain;

import java.util.Objects;

public record ProcessingFailure(Kind kind, String errorCode) {
    public ProcessingFailure {
        Objects.requireNonNull(kind);
        if (errorCode == null || errorCode.isBlank()) throw new IllegalArgumentException("Error code is required");
    }

    public enum Kind { TRANSIENT, FATAL }
}
