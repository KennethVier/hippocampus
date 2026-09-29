package com.hippocampus.learning.application;

import com.hippocampus.shared.application.error.ApplicationException;
import com.hippocampus.shared.domain.error.ErrorCode;

public final class ActivityMaterializationException extends ApplicationException {

    private final Reason reason;

    ActivityMaterializationException(Reason reason, String message) {
        super(new ErrorCode("ACTIVITY_MATERIALIZATION_" + reason.name()), message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        MISSION_NOT_ACTIVE,
        OBJECTIVE_NOT_IN_MISSION,
        NOT_MATERIALIZABLE,
        COMPATIBLE_ACTIVITY_NOT_FOUND,
        UNFINISHED_CURRENT_ACTIVITY,
        STALE_MISSION,
        INVALID_SOURCE_REFERENCES,
        INVALID_ARTIFACT,
        INVALID_AI_CONTENT
    }
}
