package com.hippocampus.learning.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.hippocampus.learning.application.GetStudyMissionUseCase;

public record StudyMissionResponse(
        UUID id,
        String status,
        String stage,
        CurrentActivityResponse currentActivity,
        Integer availableTimeMinutes,
        Instant startedAt,
        Instant completedAt,
        Instant stoppedAt,
        Instant updatedAt) {

    static StudyMissionResponse from(GetStudyMissionUseCase.Result result) {
        return new StudyMissionResponse(
                result.id(), result.status(), result.stage(),
                result.currentActivity() == null ? null : CurrentActivityResponse.from(result.currentActivity()),
                result.availableTimeMinutes(), result.startedAt(), result.completedAt(),
                result.stoppedAt(), result.updatedAt());
    }

    public record CurrentActivityResponse(
            UUID id,
            String type,
            String status,
            String difficulty,
            String classification,
            Object content,
            List<SourcePresentationResponse> sources) {

        static CurrentActivityResponse from(GetStudyMissionUseCase.CurrentActivity activity) {
            return new CurrentActivityResponse(
                    activity.id(), activity.type(), activity.status(), activity.difficulty(),
                    activity.classification(), content(activity.content()),
                    activity.sources().stream().map(SourcePresentationResponse::from).toList());
        }

        private static Object content(GetStudyMissionUseCase.Content content) {
            if (content == null) {
                return null;
            }
            return switch (content) {
                case GetStudyMissionUseCase.ExplanationContent value -> value;
                case GetStudyMissionUseCase.RetrievalContent value -> value;
                case GetStudyMissionUseCase.ConnectionContent value -> value;
                case GetStudyMissionUseCase.ApplicationContent value -> value;
            };
        }
    }

    public record SourcePresentationResponse(
            UUID sourceReferenceId,
            String materialTitle,
            Integer pageNumber,
            String displayLabel) {

        static SourcePresentationResponse from(GetStudyMissionUseCase.SourcePresentation source) {
            return new SourcePresentationResponse(
                    source.sourceReferenceId(), source.materialTitle(),
                    source.pageNumber(), source.displayLabel());
        }
    }
}
