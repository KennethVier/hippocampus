package com.hippocampus.learning.port;

import java.util.Objects;

import com.hippocampus.learning.domain.LearningActionConstraints;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningDifficulty;
import com.hippocampus.learning.domain.StudyMissionGroundingMode;

public interface ActivityAiTaskPort {

    ValidatedContent execute(Request request);

    record Request(
            String objective,
            String targetDisplayName,
            LearningActionType actionType,
            LearningDifficulty difficulty,
            StudyMissionGroundingMode groundingMode,
            ActivityEvidencePort.Evidence evidence,
            LearningActionConstraints constraints) {

        public Request {
            objective = required(objective, "objective");
            targetDisplayName = required(targetDisplayName, "targetDisplayName");
            Objects.requireNonNull(actionType, "actionType must not be null");
            Objects.requireNonNull(groundingMode, "groundingMode must not be null");
            Objects.requireNonNull(evidence, "evidence must not be null");
            Objects.requireNonNull(constraints, "constraints must not be null");
        }
    }

    record ValidatedContent(
            String artifactType,
            String taskType,
            String contentText,
            String contentPayload,
            String groundingMode,
            String classification,
            String promptId,
            String promptVersion,
            String provider,
            String model,
            String modelVersion,
            ValidationStatus validationStatus,
            boolean reusable) {

        public ValidatedContent {
            artifactType = required(artifactType, "artifactType");
            taskType = required(taskType, "taskType");
            if ((contentText == null || contentText.isBlank())
                    && (contentPayload == null || contentPayload.isBlank())) {
                throw new IllegalArgumentException("validated content must contain text or payload");
            }
            groundingMode = required(groundingMode, "groundingMode");
            classification = required(classification, "classification");
            promptId = required(promptId, "promptId");
            promptVersion = required(promptVersion, "promptVersion");
            provider = required(provider, "provider");
            model = required(model, "model");
            if (modelVersion != null && modelVersion.isBlank()) {
                throw new IllegalArgumentException("modelVersion must not be blank");
            }
            Objects.requireNonNull(validationStatus, "validationStatus must not be null");
        }
    }

    enum ValidationStatus {
        VALIDATED
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
