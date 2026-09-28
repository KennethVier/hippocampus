package com.hippocampus.learning.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import com.hippocampus.learning.domain.LearningObjective;
import com.hippocampus.learning.domain.LearningObjectiveStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "learning_objectives")
public class LearningObjectiveEntity {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "study_mission_id", nullable = false, updatable = false)
    private UUID studyMissionId;

    @Column(name = "objective_text", nullable = false)
    private String objectiveText;

    @Column(name = "concept_key")
    private String conceptKey;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "priority")
    private Integer priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private LearningObjectiveStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LearningObjectiveEntity() {}

    LearningObjectiveEntity(UUID studyMissionId, LearningObjective objective) {
        id = objective.id();
        this.studyMissionId = studyMissionId;
        createdAt = objective.createdAt();
        apply(objective);
    }

    void apply(LearningObjective objective) {
        objectiveText = objective.objectiveText();
        conceptKey = objective.conceptKey();
        displayName = objective.displayName();
        priority = objective.priority();
        status = objective.status();
    }

    LearningObjective toDomain() {
        return new LearningObjective(id, objectiveText, conceptKey, displayName, priority, status, createdAt);
    }

    public UUID getId() { return id; }
    public UUID getStudyMissionId() { return studyMissionId; }
}
