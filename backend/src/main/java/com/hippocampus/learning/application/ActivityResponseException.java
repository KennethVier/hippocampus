package com.hippocampus.learning.application;

import com.hippocampus.shared.application.error.ApplicationException;
import com.hippocampus.shared.domain.error.ErrorCode;

public final class ActivityResponseException extends ApplicationException {

    public enum Reason {
        MISSION_NOT_ACTIVE,
        ACTIVITY_NOT_CURRENT,
        ACTIVITY_ALREADY_COMPLETED,
        INVALID_RESPONSE,
        EVALUATION_CONTRACT_NOT_FOUND,
        EXTERNAL_EVALUATION_FAILED,
        INVALID_EVALUATION,
        STALE_SUBMISSION
    }

    private final Reason reason;

    ActivityResponseException(Reason reason, String clientMessage) {
        super(new ErrorCode("ACTIVITY_RESPONSE_" + reason.name()), clientMessage);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
