package com.hippocampus.learning.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.hippocampus.learning.application.SubmitActivityResponseUseCase;

public record ActivitySubmissionResponse(
        UUID missionId,
        UUID activityId,
        String outcome,
        List<String> correctConcepts,
        List<String> missingConcepts,
        List<String> misconceptions,
        String feedback,
        String missionStatus,
        String stage,
        Instant updatedAt,
        boolean continuationAvailable) {

    static ActivitySubmissionResponse from(SubmitActivityResponseUseCase.Result result) {
        return new ActivitySubmissionResponse(
                result.missionId(),
                result.activityId(),
                result.outcome(),
                result.correctConcepts(),
                result.missingConcepts(),
                result.misconceptions(),
                result.feedback(),
                result.missionStatus(),
                result.stage(),
                result.updatedAt(),
                result.continuationAvailable());
    }
}
