package com.hippocampus.materials.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class MaterialTopicLinkTests {

    @Test
    void documentNodeRequiresMaterialVersion() {
        assertThatThrownBy(() -> new MaterialTopicLink(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID(),
                MaterialTopicLinkOrigin.STRUCTURE_DETECTED, MaterialTopicLinkStatus.ACTIVE,
                Instant.now(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("documentNodeId requires materialVersionId");
    }

    @Test
    void requiredProvenanceCannotBeNull() {
        assertThatThrownBy(() -> new MaterialTopicLink(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, null,
                null, MaterialTopicLinkStatus.ACTIVE, Instant.now(), Instant.now()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("origin must not be null");
    }
}
