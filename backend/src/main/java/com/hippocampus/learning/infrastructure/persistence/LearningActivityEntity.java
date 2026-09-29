package com.hippocampus.learning.infrastructure.persistence;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.hippocampus.learning.domain.LearningActivity;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningActivityType;
import com.hippocampus.learning.domain.LearningDifficulty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "learning_activities")
public class LearningActivityEntity {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "study_mission_id", nullable = false, updatable = false)
    private UUID studyMissionId;

    @Column(name = "learning_objective_id")
    private UUID learningObjectiveId;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false)
    private LearningActivityType activityType;

    @Enumerated(EnumType.STRING)
    @Column(name = "represented_action_type", nullable = false)
    private LearningActionType representedActionType;

    @Column(name = "question_intent")
    private String questionIntent;

    @Column(name = "template_signature")
    private String templateSignature;

    @Column(name = "status", nullable = false)
    private String status;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty")
    private LearningDifficulty difficulty;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Column(name = "generated_artifact_id")
    private UUID generatedArtifactId;

    @Column(name = "source_required", nullable = false)
    private boolean sourceRequired;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LearningActivityEntity() {}

    LearningActivityEntity(UUID studyMissionId, LearningActivity activity) {
        id = activity.id();
        this.studyMissionId = studyMissionId;
        createdAt = activity.createdAt();
        apply(activity);
    }

    void apply(LearningActivity activity) {
        learningObjectiveId = activity.learningObjectiveId();
        activityType = activity.activityType();
        representedActionType = activity.representedActionType();
        questionIntent = activity.questionIntent();
        templateSignature = activity.templateSignature();
        status = activity.status();
        difficulty = activity.difficulty();
        sequenceNumber = activity.sequenceNumber();
        generatedArtifactId = activity.generatedArtifactId();
        sourceRequired = activity.sourceRequired();
        startedAt = activity.startedAt();
        completedAt = activity.completedAt();
    }

    LearningActivity toDomain(Set<UUID> sourceReferenceIds) {
        return new LearningActivity(id, learningObjectiveId, activityType, representedActionType,
                questionIntent, templateSignature, status, difficulty,
                sequenceNumber, generatedArtifactId, sourceRequired, startedAt, completedAt,
                createdAt, sourceReferenceIds);
    }

    public UUID getId() { return id; }
    public UUID getStudyMissionId() { return studyMissionId; }
    public int getSequenceNumber() { return sequenceNumber; }
}
