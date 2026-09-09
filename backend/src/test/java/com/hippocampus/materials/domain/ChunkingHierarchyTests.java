package com.hippocampus.materials.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ChunkingHierarchyTests {
    private static final UUID VERSION = UUID.randomUUID();

    @Test
    void validatesRootAndBuildsDeterministicHeadingPaths() {
        DocumentNode root = node(UUID.randomUUID(), null, DocumentNodeType.DOCUMENT, "Book", 1, 3);
        DocumentNode section = node(UUID.randomUUID(), root.id(), DocumentNodeType.SECTION, "Heart", 2, 3);
        ChunkingHierarchy hierarchy = new ChunkingHierarchy(VERSION, 3, List.of(root, section), 10, 5);
        assertThat(hierarchy.headingPath(section.id())).containsExactly("Book", "Heart");
        hierarchy.validate(section.id(), 2);
        assertThatThrownBy(() -> hierarchy.validate(section.id(), 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingRootParentCycleVersionBoundsAndLimits() {
        DocumentNode root = node(UUID.randomUUID(), null, DocumentNodeType.DOCUMENT, null, 1, 2);
        assertThatThrownBy(() -> new ChunkingHierarchy(VERSION, 2, List.of(), 10, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChunkingHierarchy(VERSION, 2,
                List.of(root, node(UUID.randomUUID(), UUID.randomUUID(), DocumentNodeType.SECTION, "x", 1, 2)), 10, 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChunkingHierarchy(VERSION, 2, List.of(root), 0, 5)).isInstanceOf(IllegalArgumentException.class);
        DocumentNode foreign = new DocumentNode(UUID.randomUUID(), UUID.randomUUID(), null, DocumentNodeType.SECTION,
                null, 2, 1, 2, null, null, DocumentNodeDetectionOrigin.NATIVE, null, Instant.EPOCH);
        assertThatThrownBy(() -> new ChunkingHierarchy(VERSION, 2, List.of(root, foreign), 10, 5)).isInstanceOf(IllegalArgumentException.class);
    }

    private static DocumentNode node(UUID id, UUID parent, DocumentNodeType type, String title, int start, int end) {
        return new DocumentNode(id, VERSION, parent, type, title, 1, start, end, null, null,
                DocumentNodeDetectionOrigin.NATIVE, null, Instant.EPOCH);
    }
}
