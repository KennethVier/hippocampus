package com.hippocampus.materials.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class ChunkIdentityTests {
    private static final UUID MATERIAL_VERSION = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    @Test
    void freezesChunkerV1GoldenReplayIdentity() {
        UUID expected = UUID.fromString("d103fe0a-9ed8-5899-83e2-fbf9304a2b8f");
        assertThat(ChunkIdentity.forChunk(MATERIAL_VERSION, 1)).isEqualTo(expected);
        assertThat(ChunkIdentity.forChunk(MATERIAL_VERSION, 1)).isEqualTo(expected);
        assertThat(ChunkIdentity.forChunk(MATERIAL_VERSION, 2))
                .isEqualTo(UUID.fromString("040b2645-e5f3-5800-9ef9-112eb15b3e91"))
                .isNotEqualTo(expected);
    }

    @Test
    void rejectsNonPositiveIndexes() {
        assertThatThrownBy(() -> ChunkIdentity.forChunk(MATERIAL_VERSION, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChunkIdentity.forChunk(MATERIAL_VERSION, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
