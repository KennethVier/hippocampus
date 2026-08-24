package com.hippocampus.shared.application.error;

import java.util.Objects;

import com.hippocampus.shared.domain.error.ErrorCode;

/**
 * Base type for application-use-case failures that have a stable, client-safe
 * representation.
 */
public abstract class ApplicationException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String clientMessage;

    protected ApplicationException(ErrorCode errorCode, String clientMessage) {
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
