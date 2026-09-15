package com.hippocampus.materials.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class SourceReferenceTests {
    @Test
    void rejectsMissingAmbiguousAndInvalidTargets() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        assertThatThrownBy(() -> new SourceReference(
                id, id, id, null, null, null, null, null, null, "Source", now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SourceReference(
                id, id, id, null, id, id, 1, null, null, "Source", now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SourceReference(
                id, id, id, null, null, null, 1, null, 2L, "Source", now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PageSourceTarget(id, id, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
