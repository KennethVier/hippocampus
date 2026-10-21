package com.hippocampus.learning.application;

import com.hippocampus.learning.port.StudyMissionStartReceiptRepository.Reason;

/** Stable sanitized failure; no provider/database details cross the application boundary. */
public final class StudyMissionStartException extends com.hippocampus.shared.application.error.ApplicationException {
    public enum Kind { CONFLICT, STATE_CHANGED, SOURCE_BUSY, UNAVAILABLE, MALFORMED, TOO_LARGE, INVALID_INITIAL_CONTRACT }
    private final Kind kind;
    private final Reason reason;

    public StudyMissionStartException(Kind kind,Reason reason) {
        super(new com.hippocampus.shared.domain.error.ErrorCode(switch(kind) {
            case CONFLICT -> "STUDY_MISSION_START_REQUEST_CONFLICT";
            case STATE_CHANGED -> "STUDY_MISSION_INITIAL_STATE_CHANGED";
            case SOURCE_BUSY -> "STUDY_MISSION_SOURCE_BUSY";
            case UNAVAILABLE -> "STUDY_MISSION_START_UNAVAILABLE";
            case MALFORMED -> "MALFORMED_REQUEST";
            case INVALID_INITIAL_CONTRACT -> "INVALID_INITIAL_ACTIVITY_CONTRACT";
            case TOO_LARGE -> "REQUEST_TOO_LARGE";
        }),switch(kind) {
            case CONFLICT -> "This start request was already used for different mission details.";
            case STATE_CHANGED -> "The mission changed. Reload its current state.";
            case SOURCE_BUSY -> "The selected source is busy. Please retry explicitly.";
            case UNAVAILABLE -> "Initial preparation is temporarily unavailable.";
            case MALFORMED -> "The mission start request is invalid.";
            case INVALID_INITIAL_CONTRACT -> "The initial activity contract version is invalid.";
            case TOO_LARGE -> "The mission start request is too large.";
        });
        this.kind=kind; this.reason=reason;
    }
    public Kind kind() { return kind; }
    public Reason reason() { return reason; }
}
