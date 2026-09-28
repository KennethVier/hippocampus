package com.hippocampus.learning.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import com.hippocampus.learning.domain.LearningStage;
import com.hippocampus.learning.domain.StudyMission;
import com.hippocampus.learning.domain.StudyMissionGroundingMode;
import com.hippocampus.learning.domain.StudyMissionStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "study_missions")
public class StudyMissionEntity {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "topic_id", nullable = false)
    private UUID topicId;

    @Column(name = "subtopic_id")
    private UUID subtopicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StudyMissionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "learning_state")
    private LearningStage learningState;

    @Enumerated(EnumType.STRING)
    @Column(name = "grounding_mode", nullable = false)
    private StudyMissionGroundingMode groundingMode;

    @Column(name = "available_time_minutes")
    private Integer availableTimeMinutes;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "stopped_at")
    private Instant stoppedAt;

    @Column(name = "current_activity_id")
    private UUID currentActivityId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StudyMissionEntity() {}

    StudyMissionEntity(StudyMission mission, UUID currentActivityId) {
        id = mission.id();
        userId = mission.userId();
        apply(mission, currentActivityId);
        createdAt = mission.createdAt();
    }

    void apply(StudyMission mission, UUID newCurrentActivityId) {
        topicId = mission.topicId();
        subtopicId = mission.subtopicId();
        status = mission.status();
        learningState = mission.learningState();
        groundingMode = mission.groundingMode();
        availableTimeMinutes = mission.availableTimeMinutes();
        startedAt = mission.startedAt();
        completedAt = mission.completedAt();
        stoppedAt = mission.stoppedAt();
        currentActivityId = newCurrentActivityId;
        updatedAt = mission.updatedAt();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getTopicId() { return topicId; }
    public UUID getSubtopicId() { return subtopicId; }
    public StudyMissionStatus getStatus() { return status; }
    public LearningStage getLearningState() { return learningState; }
    public StudyMissionGroundingMode getGroundingMode() { return groundingMode; }
    public Integer getAvailableTimeMinutes() { return availableTimeMinutes; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getStoppedAt() { return stoppedAt; }
    public UUID getCurrentActivityId() { return currentActivityId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
