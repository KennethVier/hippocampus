package com.hippocampus.learning.infrastructure.persistence;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ActivitySourceReferenceId implements Serializable {
    @Column(name = "learning_activity_id")
    private UUID learningActivityId;

    @Column(name = "source_reference_id")
    private UUID sourceReferenceId;

    protected ActivitySourceReferenceId() {}

    public ActivitySourceReferenceId(UUID learningActivityId, UUID sourceReferenceId) {
        this.learningActivityId = Objects.requireNonNull(
                learningActivityId, "learningActivityId must not be null");
        this.sourceReferenceId = Objects.requireNonNull(
                sourceReferenceId, "sourceReferenceId must not be null");
    }

    public UUID learningActivityId() { return learningActivityId; }
    public UUID sourceReferenceId() { return sourceReferenceId; }
    public UUID getLearningActivityId() { return learningActivityId; }
    public UUID getSourceReferenceId() { return sourceReferenceId; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ActivitySourceReferenceId that)) return false;
        return learningActivityId.equals(that.learningActivityId)
                && sourceReferenceId.equals(that.sourceReferenceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(learningActivityId, sourceReferenceId);
    }
}
