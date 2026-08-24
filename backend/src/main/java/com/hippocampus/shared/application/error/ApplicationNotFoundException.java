package com.hippocampus.shared.application.error;

import com.hippocampus.shared.domain.error.ErrorCode;

/**
 * An application use case could not find the requested authoritative state.
 */
public final class ApplicationNotFoundException extends ApplicationException {

    public ApplicationNotFoundException(ErrorCode errorCode, String clientMessage) {
        super(errorCode, clientMessage);
    }
}
