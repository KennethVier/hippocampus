package com.hippocampus.learning.api;

import java.time.Instant;
import java.util.UUID;
import com.hippocampus.learning.application.GetStudyMissionUseCase;

record StudyMissionWithContinuationResponse(UUID id, String status, String stage,
        StudyMissionResponse.CurrentActivityResponse currentActivity, Integer availableTimeMinutes,
        Instant startedAt, Instant completedAt, Instant stoppedAt, Instant updatedAt,
        ActivitySubmissionResponse latestSubmission, GetStudyMissionUseCase.Continuation continuation) {
    static StudyMissionWithContinuationResponse from(GetStudyMissionUseCase.Result result) {
        var legacy = StudyMissionResponse.from(result);
        return new StudyMissionWithContinuationResponse(legacy.id(), legacy.status(), legacy.stage(),
                legacy.currentActivity(), legacy.availableTimeMinutes(), legacy.startedAt(), legacy.completedAt(),
                legacy.stoppedAt(), legacy.updatedAt(), legacy.latestSubmission(), result.continuation());
    }
}
