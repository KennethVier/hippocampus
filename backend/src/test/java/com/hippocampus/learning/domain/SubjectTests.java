package com.hippocampus.learning.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class SubjectTests {

    @Test
    void createsActiveSubjectWithoutNormalizingSubmittedDetails() {
        UUID ownerId = UUID.randomUUID();
        Subject subject = Subject.create(ownerId, "  Gross  Anatomy  ", " description ", -1);

        assertThat(subject.ownerId()).isEqualTo(ownerId);
        assertThat(subject.name()).isEqualTo("  Gross  Anatomy  ");
        assertThat(subject.description()).isEqualTo(" description ");
        assertThat(subject.sortOrder()).isEqualTo(-1);
        assertThat(subject.status()).isEqualTo(SubjectStatus.ACTIVE);
    }

    @Test
    void changesOnlyMutableDetailsAndArchivesIdempotently() {
        UUID id = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-08-30T10:00:00Z");
        Subject original = new Subject(id, ownerId, "Anatomy", null, null,
                SubjectStatus.ACTIVE, createdAt, createdAt);

        Subject changed = original.changeDetails("Physiology", "Details", 2);
        Subject archived = changed.archive();

        assertThat(changed.id()).isEqualTo(id);
        assertThat(changed.ownerId()).isEqualTo(ownerId);
        assertThat(changed.status()).isEqualTo(SubjectStatus.ACTIVE);
        assertThat(archived.status()).isEqualTo(SubjectStatus.ARCHIVED);
        assertThat(archived.archive()).isSameAs(archived);
    }
}
