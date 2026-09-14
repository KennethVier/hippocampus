package com.hippocampus.rag.port;

import java.util.Objects;

public final class EmbeddingFailureException extends RuntimeException {
    private final Reason reason;

    public EmbeddingFailureException(Reason reason) {
        super(messageFor(reason));
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        PROVIDER_FAILURE,
        INVALID_RESPONSE
    }

    private static String messageFor(Reason reason) {
        return switch (Objects.requireNonNull(reason, "reason must not be null")) {
            case PROVIDER_FAILURE -> "Embedding provider request failed";
            case INVALID_RESPONSE -> "Embedding provider response was invalid";
        };
    }
}
