package com.hippocampus.learning.application;

import com.hippocampus.shared.application.error.ApplicationException;
import com.hippocampus.shared.domain.error.ErrorCode;

public final class StartStudyMissionValidationException extends ApplicationException {

    private static final ErrorCode INVALID_COMMAND =
            new ErrorCode("START_STUDY_MISSION_INVALID_COMMAND");

    StartStudyMissionValidationException(String clientMessage) {
        super(INVALID_COMMAND, clientMessage);
    }
}
