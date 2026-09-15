package com.hippocampus.rag.domain;

import java.util.Objects;

public record EvidenceLimitation(EvidenceLimitationCode code) {
    public EvidenceLimitation {
        Objects.requireNonNull(code, "code must not be null");
    }
}
