package com.hippocampus.learning.application;

import com.hippocampus.shared.application.error.ApplicationException;
import com.hippocampus.shared.domain.error.ErrorCode;

public final class MissionLifecycleException extends ApplicationException {

    MissionLifecycleException(String clientMessage) {
        super(new ErrorCode("STUDY_MISSION_INVALID_TRANSITION"), clientMessage);
    }
}
