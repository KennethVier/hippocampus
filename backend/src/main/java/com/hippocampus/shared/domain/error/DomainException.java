package com.hippocampus.shared.domain.error;

import java.util.Objects;

/**
 * Base type for domain failures that may be translated into a stable API
 * error without exposing implementation details.
 */
public abstract class DomainException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String clientMessage;

    protected DomainException(ErrorCode errorCode, String clientMessage) {
        super(requireClientMessage(clientMessage));
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
        this.clientMessage = clientMessage;
    }

    public final ErrorCode errorCode() {
        return errorCode;
    }

    public final String clientMessage() {
        return clientMessage;
    }

    private static String requireClientMessage(String clientMessage) {
        Objects.requireNonNull(clientMessage, "clientMessage must not be null");
        if (clientMessage.isBlank()) {
            throw new IllegalArgumentException("clientMessage must not be blank");
        }
        return clientMessage;
    }
}
