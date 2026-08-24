package com.hippocampus.shared.domain.error;

/**
 * A domain operation cannot proceed because the requested state conflicts with
 * current authoritative state.
 */
public final class DomainConflictException extends DomainException {

    public DomainConflictException(ErrorCode errorCode, String clientMessage) {
        super(errorCode, clientMessage);
    }
}
