package com.hippocampus.learning.infrastructure.persistence;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "activity_source_references")
public class ActivitySourceReferenceEntity {
    @EmbeddedId
    private ActivitySourceReferenceId id;

    protected ActivitySourceReferenceEntity() {}

    ActivitySourceReferenceEntity(ActivitySourceReferenceId id) {
        this.id = id;
    }

    public ActivitySourceReferenceId getId() { return id; }
}
