package com.hippocampus.learning.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.hippocampus.learning.application.SubmitActivityResponseUseCase;
import com.hippocampus.learning.application.SubmissionReceipt;

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
        boolean continuationAvailable,
        UUID attemptId,
        String limitationCode) {

    public ActivitySubmissionResponse(
            UUID missionId, UUID activityId, String outcome,
            List<String> correctConcepts, List<String> missingConcepts,
            List<String> misconceptions, String feedback, String missionStatus,
            String stage, Instant updatedAt, boolean continuationAvailable) {
        this(missionId, activityId, outcome, correctConcepts, missingConcepts,
                misconceptions, feedback, missionStatus, stage, updatedAt,
                continuationAvailable, null, null);
    }

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
                result.continuationAvailable(),
                result.attemptId(),
                result.limitationCode());
    }

    static ActivitySubmissionResponse from(SubmissionReceipt receipt) {
        return new ActivitySubmissionResponse(
                receipt.missionId(), receipt.activityId(), receipt.outcome(),
                receipt.correctConcepts(), receipt.missingConcepts(),
                receipt.misconceptions(), receipt.feedback(), receipt.missionStatus(),
                receipt.stage(), receipt.updatedInstant(), receipt.continuationAvailable(),
                receipt.attemptId(), receipt.limitationCode());
    }
}
