package com.hippocampus.learning.port;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.hippocampus.learning.domain.LearningObjective;
import com.hippocampus.learning.domain.MissionMaterial;
import com.hippocampus.learning.domain.NextLearningAction;
import com.hippocampus.learning.domain.StudyMissionGroundingMode;

public interface ActivityEvidencePort {

    Evidence retrieve(Request request);

    record Request(
            UUID userId,
            UUID missionId,
            UUID topicId,
            StudyMissionGroundingMode groundingMode,
            List<MissionMaterial> materialScopes,
            LearningObjective objective,
            NextLearningAction action) {

        public Request {
            Objects.requireNonNull(userId, "userId must not be null");
            Objects.requireNonNull(missionId, "missionId must not be null");
            Objects.requireNonNull(topicId, "topicId must not be null");
            Objects.requireNonNull(groundingMode, "groundingMode must not be null");
            materialScopes = List.copyOf(Objects.requireNonNull(
                    materialScopes, "materialScopes must not be null"));
            Objects.requireNonNull(objective, "objective must not be null");
            Objects.requireNonNull(action, "action must not be null");
        }
    }

    record Evidence(Set<UUID> sourceReferenceIds) {
        public Evidence {
            sourceReferenceIds = Set.copyOf(Objects.requireNonNull(
                    sourceReferenceIds, "sourceReferenceIds must not be null"));
        }
    }
}
