package com.hippocampus.learning.application;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.learning.domain.LearningActionConstraints;
import com.hippocampus.learning.domain.LearningActivityIntent;
import com.hippocampus.learning.domain.LearningEngine;
import com.hippocampus.learning.domain.LearningEvidenceSnapshot;
import com.hippocampus.learning.domain.LearningObjective;
import com.hippocampus.learning.domain.LearningObjectiveStatus;
import com.hippocampus.learning.domain.LearningStage;
import com.hippocampus.learning.domain.LearningState;
import com.hippocampus.learning.domain.LearningTimeContext;
import com.hippocampus.learning.domain.MissionLifecycleState;
import com.hippocampus.learning.domain.MissionMaterial;
import com.hippocampus.learning.domain.NextLearningAction;
import com.hippocampus.learning.domain.SourceCapability;
import com.hippocampus.learning.domain.SourceReadiness;
import com.hippocampus.learning.domain.SourceRequirement;
import com.hippocampus.learning.domain.StudyMission;
import com.hippocampus.learning.domain.StudyMissionGroundingMode;
import com.hippocampus.learning.domain.StudyMissionStatus;
import com.hippocampus.learning.domain.Subtopic;
import com.hippocampus.learning.domain.SubtopicStatus;
import com.hippocampus.learning.domain.Topic;
import com.hippocampus.learning.domain.TopicStatus;
import com.hippocampus.learning.port.StudyMissionRepository;
import com.hippocampus.learning.port.StudyMissionSourceCatalog;
import com.hippocampus.learning.port.SubtopicRepository;
import com.hippocampus.learning.port.TopicRepository;
import com.hippocampus.shared.application.error.ApplicationNotFoundException;
import com.hippocampus.shared.domain.error.ErrorCode;

public final class StartStudyMissionUseCase {

    private static final ErrorCode SOURCE_NOT_FOUND =
            new ErrorCode("STUDY_MISSION_SOURCE_NOT_FOUND");

    private final CurrentUser currentUser;
    private final TopicRepository topics;
    private final SubtopicRepository subtopics;
    private final StudyMissionSourceCatalog sourceCatalog;
    private final StudyMissionRepository missions;
    private final LearningEngine learningEngine;
    private final Clock clock;

    public StartStudyMissionUseCase(
            CurrentUser currentUser,
            TopicRepository topics,
            SubtopicRepository subtopics,
            StudyMissionSourceCatalog sourceCatalog,
            StudyMissionRepository missions,
            LearningEngine learningEngine,
            Clock clock) {
        this.currentUser = Objects.requireNonNull(currentUser, "currentUser must not be null");
        this.topics = Objects.requireNonNull(topics, "topics must not be null");
        this.subtopics = Objects.requireNonNull(subtopics, "subtopics must not be null");
        this.sourceCatalog = Objects.requireNonNull(sourceCatalog, "sourceCatalog must not be null");
        this.missions = Objects.requireNonNull(missions, "missions must not be null");
        this.learningEngine = Objects.requireNonNull(learningEngine, "learningEngine must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional
    public Result execute(Command command) {
        Objects.requireNonNull(command, "command must not be null");
        UUID ownerId = currentUser.authenticatedUser().userId();
        Topic topic = topics.findOwnedByIdWithActiveSubject(command.topicId(), ownerId)
                .filter(candidate -> candidate.status() == TopicStatus.ACTIVE)
                .orElseThrow(TopicFailures::notFound);
        Subtopic subtopic = resolveSubtopic(command.subtopicId(), command.topicId(), ownerId);

        List<StudyMissionSourceCatalog.SourceSelection> selections = command.sourceScopes().stream()
                .map(scope -> new StudyMissionSourceCatalog.SourceSelection(
                        scope.materialId(), scope.documentNodeId()))
                .toList();
        StudyMissionSourceCatalog.Resolution sourceResolution = sourceCatalog
                .resolve(ownerId, command.topicId(), selections)
                .orElseThrow(StartStudyMissionUseCase::sourceNotFound);

        Instant now = clock.instant();
        UUID missionId = UUID.randomUUID();
        UUID objectiveId = UUID.randomUUID();
        String conceptKey = subtopic == null
                ? "topic:" + topic.id()
                : "subtopic:" + subtopic.id();
        String displayName = subtopic == null ? topic.name() : subtopic.name();
        LearningObjective objective = new LearningObjective(
                objectiveId,
                command.learningObjectiveText(),
                conceptKey,
                displayName,
                1,
                LearningObjectiveStatus.ACTIVE,
                now);
        List<MissionMaterial> frozenMaterials = sourceResolution.sources().stream()
                .map(source -> new MissionMaterial(
                        UUID.randomUUID(),
                        source.materialId(),
                        source.materialVersionId(),
                        source.documentNodeId()))
                .toList();
        SourceCapability sourceCapability = sourceCapability(sourceResolution.sources());
        LearningState initialState = new LearningState(
                missionId,
                objectiveId,
                conceptKey,
                MissionLifecycleState.ACTIVE,
                LearningStage.UNDERSTANDING,
                new LearningEvidenceSnapshot(Map.of()),
                sourceCapability,
                new LearningTimeContext(
                        command.availableTimeMinutes(),
                        command.availableTimeMinutes(),
                        0),
                List.of(),
                false,
                constraintsFor(command.groundingMode()));
        NextLearningAction firstAction = learningEngine.decide(initialState);
        StudyMission mission = new StudyMission(
                missionId,
                ownerId,
                command.topicId(),
                command.subtopicId(),
                StudyMissionStatus.ACTIVE,
                LearningStage.UNDERSTANDING,
                command.groundingMode(),
                command.availableTimeMinutes(),
                now,
                null,
                null,
                null,
                frozenMaterials,
                List.of(objective),
                List.of(),
                now,
                now);

        return new Result(missions.save(mission), firstAction);
    }

    private Subtopic resolveSubtopic(UUID subtopicId, UUID topicId, UUID ownerId) {
        if (subtopicId == null) {
            return null;
        }
        return subtopics.findOwnedByIdWithActiveAncestors(subtopicId, ownerId)
                .filter(candidate -> candidate.status() == SubtopicStatus.ACTIVE)
                .filter(candidate -> candidate.topicId().equals(topicId))
                .orElseThrow(SubtopicFailures::notFound);
    }

    private static SourceCapability sourceCapability(
            List<StudyMissionSourceCatalog.ResolvedSource> sources) {
        if (sources.isEmpty()) {
            return new SourceCapability(SourceReadiness.INSUFFICIENT, false, false, false);
        }
        SourceReadiness readiness;
        if (sources.stream().anyMatch(source -> source.readiness() == SourceReadiness.FAILED)) {
            readiness = SourceReadiness.FAILED;
        } else if (sources.stream().anyMatch(source -> source.readiness() == SourceReadiness.INSUFFICIENT)) {
            readiness = SourceReadiness.INSUFFICIENT;
        } else if (sources.stream().allMatch(source -> source.readiness() == SourceReadiness.READY)) {
            readiness = SourceReadiness.READY;
        } else {
            readiness = SourceReadiness.LIMITED;
        }
        return new SourceCapability(
                readiness,
                sources.stream().anyMatch(StudyMissionSourceCatalog.ResolvedSource::groundedTextAvailable),
                sources.stream().anyMatch(StudyMissionSourceCatalog.ResolvedSource::visualAvailable),
                sources.stream().anyMatch(StudyMissionSourceCatalog.ResolvedSource::visualReliable));
    }

    private static LearningActionConstraints constraintsFor(StudyMissionGroundingMode groundingMode) {
        return switch (groundingMode) {
            case STRICT_SOURCE -> new LearningActionConstraints(
                    SourceRequirement.REQUIRED,
                    false,
                    false,
                    null,
                    null,
                    LearningActivityIntent.STANDARD);
            case SOURCE_FIRST -> new LearningActionConstraints(
                    SourceRequirement.REQUIRED,
                    false,
                    true,
                    null,
                    null,
                    LearningActivityIntent.STANDARD);
            case GENERAL_KNOWLEDGE -> LearningActionConstraints.unconstrained();
        };
    }

    private static ApplicationNotFoundException sourceNotFound() {
        return new ApplicationNotFoundException(
                SOURCE_NOT_FOUND,
                "A selected mission source was not found or is not available.");
    }

    public record Command(
            UUID topicId,
            UUID subtopicId,
            String learningObjectiveText,
            int availableTimeMinutes,
            StudyMissionGroundingMode groundingMode,
            List<SelectedSourceScope> sourceScopes) {

        public Command {
            if (topicId == null) {
                throw invalid("topicId must not be null");
            }
            if (learningObjectiveText == null || learningObjectiveText.isBlank()) {
                throw invalid("learningObjectiveText must not be blank");
            }
            if (availableTimeMinutes <= 0) {
                throw invalid("availableTimeMinutes must be positive");
            }
            if (groundingMode == null) {
                throw invalid("groundingMode must not be null");
            }
            if (sourceScopes == null) {
                throw invalid("sourceScopes must not be null");
            }
            for (SelectedSourceScope scope : sourceScopes) {
                if (scope == null) {
                    throw invalid("sourceScopes must not contain null");
                }
            }
            sourceScopes = List.copyOf(sourceScopes);
            Set<SelectedSourceScope> uniqueScopes = new HashSet<>(sourceScopes);
            if (uniqueScopes.size() != sourceScopes.size()) {
                throw invalid("sourceScopes must not contain duplicates");
            }
        }

        private static StartStudyMissionValidationException invalid(String message) {
            return new StartStudyMissionValidationException(message);
        }
    }

    public record SelectedSourceScope(UUID materialId, UUID documentNodeId) {
        public SelectedSourceScope {
            if (materialId == null) {
                throw new StartStudyMissionValidationException("materialId must not be null");
            }
        }
    }

    public record Result(StudyMission mission, NextLearningAction firstAction) {
        public Result {
            Objects.requireNonNull(mission, "mission must not be null");
            Objects.requireNonNull(firstAction, "firstAction must not be null");
        }
    }
}
