package com.hippocampus.ai.port;

import java.util.Objects;

public final class AiOutputDecodingException extends RuntimeException {

    private final Kind kind;

    public AiOutputDecodingException(Kind kind) {
        super("AI output decoding failed: " + Objects.requireNonNull(kind, "kind must not be null"));
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public enum Kind {
        MALFORMED_JSON,
        CONTRACT_MISMATCH
    }
}
