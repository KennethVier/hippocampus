package com.hippocampus.learning.api;

import com.hippocampus.learning.application.GetStudyMissionUseCase;
import java.time.Instant;
import java.util.UUID;

record StudyMissionWithInitialActivityResponse(UUID id,String status,String stage,
        StudyMissionResponse.CurrentActivityResponse currentActivity,Integer availableTimeMinutes,
        Instant startedAt,Instant completedAt,Instant stoppedAt,Instant updatedAt,
        ActivitySubmissionResponse latestSubmission,GetStudyMissionUseCase.InitialActivity initialActivity) {
    static Object from(GetStudyMissionUseCase.Result result,boolean continuationOptIn) {
        var legacy=StudyMissionResponse.from(result);
        if(continuationOptIn) return new WithContinuation(legacy.id(),legacy.status(),legacy.stage(),
                legacy.currentActivity(),legacy.availableTimeMinutes(),legacy.startedAt(),legacy.completedAt(),
                legacy.stoppedAt(),legacy.updatedAt(),legacy.latestSubmission(),result.continuation(),result.initialActivity());
        return new StudyMissionWithInitialActivityResponse(legacy.id(),legacy.status(),legacy.stage(),
                legacy.currentActivity(),legacy.availableTimeMinutes(),legacy.startedAt(),legacy.completedAt(),
                legacy.stoppedAt(),legacy.updatedAt(),legacy.latestSubmission(),result.initialActivity());
    }
    record WithContinuation(UUID id,String status,String stage,
            StudyMissionResponse.CurrentActivityResponse currentActivity,Integer availableTimeMinutes,
            Instant startedAt,Instant completedAt,Instant stoppedAt,Instant updatedAt,
            ActivitySubmissionResponse latestSubmission,GetStudyMissionUseCase.Continuation continuation,
            GetStudyMissionUseCase.InitialActivity initialActivity) {}
}
