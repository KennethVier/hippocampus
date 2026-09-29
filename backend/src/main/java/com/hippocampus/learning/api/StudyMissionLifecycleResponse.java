package com.hippocampus.learning.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.hippocampus.learning.application.ChangeStudyMissionStatusUseCase;

public record StudyMissionLifecycleResponse(
        UUID id,
        String status,
        UUID currentActivityId,
        List<SourceScope> sourceScopes,
        Instant startedAt,
        Instant completedAt,
        Instant stoppedAt,
        Instant updatedAt) {

    static StudyMissionLifecycleResponse from(ChangeStudyMissionStatusUseCase.Result mission) {
        return new StudyMissionLifecycleResponse(
                mission.id(), mission.status(), mission.currentActivityId(),
                mission.sourceScopes().stream().map(SourceScope::from).toList(),
                mission.startedAt(), mission.completedAt(), mission.stoppedAt(), mission.updatedAt());
    }

    public record SourceScope(UUID materialId, UUID materialVersionId, UUID documentNodeId) {
        static SourceScope from(ChangeStudyMissionStatusUseCase.SourceScope material) {
            return new SourceScope(
                    material.materialId(), material.materialVersionId(), material.documentNodeId());
        }
    }
}
