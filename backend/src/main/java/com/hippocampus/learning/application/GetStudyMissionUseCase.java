package com.hippocampus.learning.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.learning.domain.LearningActivity;
import com.hippocampus.learning.domain.LearningActivityType;
import com.hippocampus.learning.domain.StudyMission;
import com.hippocampus.learning.port.GeneratedActivityContentDecoder;
import com.hippocampus.learning.port.GeneratedArtifactRepository;
import com.hippocampus.learning.port.GeneratedArtifactRepository.GeneratedArtifact;
import com.hippocampus.learning.port.StudyMissionRepository;
import com.hippocampus.learning.port.StudyMissionSourcePresentationRepository;
import com.hippocampus.shared.application.error.ApplicationNotFoundException;
import com.hippocampus.shared.domain.error.ErrorCode;

public class GetStudyMissionUseCase {

    private static final ErrorCode MISSION_NOT_FOUND = new ErrorCode("STUDY_MISSION_NOT_FOUND");
    private static final Set<String> SAFE_CLASSIFICATIONS = Set.of(
            "SOURCE_DERIVED",
            "SOURCE_GROUNDED_GENERATED",
            "SUPPLEMENTAL_GENERATED",
            "GENERAL_GENERATED");

    private final CurrentUser currentUser;
    private final StudyMissionRepository missions;
    private final GeneratedArtifactRepository artifacts;
    private final GeneratedActivityContentDecoder contentDecoder;
    private final StudyMissionSourcePresentationRepository sourcePresentations;

    public GetStudyMissionUseCase(
            CurrentUser currentUser,
            StudyMissionRepository missions,
            GeneratedArtifactRepository artifacts,
            GeneratedActivityContentDecoder contentDecoder,
            StudyMissionSourcePresentationRepository sourcePresentations) {
        this.currentUser = Objects.requireNonNull(currentUser, "currentUser must not be null");
        this.missions = Objects.requireNonNull(missions, "missions must not be null");
        this.artifacts = Objects.requireNonNull(artifacts, "artifacts must not be null");
        this.contentDecoder = Objects.requireNonNull(contentDecoder, "contentDecoder must not be null");
        this.sourcePresentations = Objects.requireNonNull(
                sourcePresentations, "sourcePresentations must not be null");
    }

    @Transactional(readOnly = true)
    public Result execute(UUID missionId) {
        Objects.requireNonNull(missionId, "missionId must not be null");
        UUID ownerId = currentUser.authenticatedUser().userId();
        StudyMission mission = missions.findOwnedById(missionId, ownerId)
                .orElseThrow(GetStudyMissionUseCase::notFound);
        CurrentActivity currentActivity = currentActivity(mission, ownerId);
        return new Result(
                mission.id(), mission.status().name(),
                mission.learningState() == null ? null : mission.learningState().name(),
                currentActivity, mission.availableTimeMinutes(), mission.startedAt(),
                mission.completedAt(), mission.stoppedAt(), mission.updatedAt());
    }

    private CurrentActivity currentActivity(StudyMission mission, UUID ownerId) {
        if (mission.currentActivityId() == null) {
            return null;
        }
        LearningActivity activity = mission.activities().stream()
                .filter(candidate -> candidate.id().equals(mission.currentActivityId()))
                .findFirst()
                .orElseThrow(GetStudyMissionUseCase::notFound);

        List<SourcePresentation> sources = sourcePresentations.resolveAuthorized(
                        ownerId, mission.materials(), activity.sourceReferenceIds())
                .orElseThrow(GetStudyMissionUseCase::notFound)
                .stream()
                .map(source -> new SourcePresentation(
                        source.sourceReferenceId(), source.materialTitle(),
                        source.pageNumber(), source.displayLabel()))
                .toList();

        if (activity.generatedArtifactId() == null) {
            return new CurrentActivity(
                    activity.id(), discriminator(activity.activityType()), activity.status(),
                    activity.difficulty() == null ? null : activity.difficulty().name(),
                    null, null, sources);
        }

        GeneratedArtifact artifact = artifacts.findOwnedById(activity.generatedArtifactId(), ownerId)
                .filter(candidate -> "VALIDATED".equals(candidate.validationStatus()))
                .filter(candidate -> candidate.userId().equals(mission.userId()))
                .orElseThrow(GetStudyMissionUseCase::notFound);
        if (!artifacts.findSourceReferenceIds(artifact.id()).equals(activity.sourceReferenceIds())
                || !SAFE_CLASSIFICATIONS.contains(artifact.classification())) {
            throw notFound();
        }

        if (activity.activityType() == LearningActivityType.VISUAL) {
            return new CurrentActivity(
                    activity.id(), discriminator(activity.activityType()), activity.status(),
                    activity.difficulty() == null ? null : activity.difficulty().name(),
                    artifact.classification(), null, sources);
        }

        GeneratedActivityContentDecoder.DecodedContent decoded;
        try {
            decoded = contentDecoder.decode(
                    activity.activityType(), artifact.artifactType(), artifact.taskType(),
                    artifact.contentPayload());
        } catch (IllegalArgumentException invalidStoredArtifact) {
            throw notFound();
        }
        return new CurrentActivity(
                activity.id(), discriminator(activity.activityType()), activity.status(),
                activity.difficulty() == null ? null : activity.difficulty().name(),
                artifact.classification(), content(decoded), sources);
    }

    private static Content content(GeneratedActivityContentDecoder.DecodedContent decoded) {
        return switch (decoded) {
            case GeneratedActivityContentDecoder.Explanation value -> new ExplanationContent(
                    value.concept(), value.explanation(), value.keyPoints(), value.limitations());
            case GeneratedActivityContentDecoder.Retrieval value -> new RetrievalContent(
                    value.subtype(), value.concept(), value.question(),
                    value.options().stream().map(option -> new Option(option.id(), option.text())).toList(),
                    value.difficulty(), value.limitations());
            case GeneratedActivityContentDecoder.Connection value -> new ConnectionContent(
                    value.fromConcept(), value.toConcept(), value.relationshipType(),
                    value.relationship(), value.whyItMatters(), value.limitations());
            case GeneratedActivityContentDecoder.Application value -> new ApplicationContent(
                    value.scenario(), value.question(), value.targetConcept(),
                    value.difficulty(), value.limitations());
        };
    }

    private static String discriminator(LearningActivityType activityType) {
        return switch (activityType) {
            case UNDERSTAND -> "EXPLANATION";
            case RETRIEVE -> "RETRIEVAL";
            case CONNECT -> "CONNECTION";
            case APPLY -> "APPLICATION";
            case VISUAL -> "VISUAL";
            case FEEDBACK -> "FEEDBACK";
            case REFLECT -> "REFLECTION";
        };
    }

    private static ApplicationNotFoundException notFound() {
        return new ApplicationNotFoundException(MISSION_NOT_FOUND, "Study mission was not found.");
    }

    public record Result(
            UUID id,
            String status,
            String stage,
            CurrentActivity currentActivity,
            Integer availableTimeMinutes,
            Instant startedAt,
            Instant completedAt,
            Instant stoppedAt,
            Instant updatedAt) {}

    public record CurrentActivity(
            UUID id,
            String type,
            String status,
            String difficulty,
            String classification,
            Content content,
            List<SourcePresentation> sources) {}

    public sealed interface Content permits
            ExplanationContent, RetrievalContent, ConnectionContent, ApplicationContent {}

    public record ExplanationContent(
            String concept,
            String explanation,
            List<String> keyPoints,
            List<String> limitations) implements Content {}

    public record RetrievalContent(
            String subtype,
            String concept,
            String question,
            List<Option> options,
            String difficulty,
            List<String> limitations) implements Content {}

    public record Option(String id, String text) {}

    public record ConnectionContent(
            String fromConcept,
            String toConcept,
            String relationshipType,
            String relationship,
            String whyItMatters,
            List<String> limitations) implements Content {}

    public record ApplicationContent(
            String scenario,
            String question,
            String targetConcept,
            String difficulty,
            List<String> limitations) implements Content {}

    public record SourcePresentation(
            UUID sourceReferenceId,
            String materialTitle,
            Integer pageNumber,
            String displayLabel) {}
}
