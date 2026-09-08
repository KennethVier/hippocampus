package com.hippocampus.materials.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class DocumentNodePageLocatorTests {
    private static final UUID VERSION = UUID.randomUUID();
    private static final UUID ROOT = UUID.randomUUID();

    @Test
    void selectsDeepestContainingNodeAndFallsBackToRoot() {
        UUID chapter = UUID.randomUUID();
        UUID section = UUID.randomUUID();
        DocumentNodePageLocator locator = new DocumentNodePageLocator(VERSION, List.of(
                node(ROOT, null, DocumentNodeType.DOCUMENT, 1, 10, null),
                node(chapter, ROOT, DocumentNodeType.CHAPTER, 2, 8, 1),
                node(section, chapter, DocumentNodeType.SECTION, 4, 5, 1)));

        assertThat(locator.locate(4)).isEqualTo(section);
        assertThat(locator.locate(7)).isEqualTo(chapter);
        assertThat(locator.locate(10)).isEqualTo(ROOT);
        assertThat(locator.locate(11)).isEqualTo(ROOT);
    }

    @Test
    void rejectsForeignDuplicateMissingParentCycleAndMissingRoot() {
        assertThatThrownBy(() -> new DocumentNodePageLocator(VERSION, List.of(
                node(ROOT, null, DocumentNodeType.DOCUMENT, 1, 1, null),
                new DocumentNode(UUID.randomUUID(), UUID.randomUUID(), ROOT, DocumentNodeType.SECTION,
                        null, 1, 1, 1, null, null, DocumentNodeDetectionOrigin.NATIVE, null, Instant.EPOCH))))
                .isInstanceOf(IllegalStateException.class);
        DocumentNode duplicate = node(ROOT, null, DocumentNodeType.DOCUMENT, 1, 1, null);
        assertThatThrownBy(() -> new DocumentNodePageLocator(VERSION, List.of(duplicate, duplicate)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new DocumentNodePageLocator(VERSION, List.of(
                node(ROOT, null, DocumentNodeType.DOCUMENT, 1, 1, null),
                node(UUID.randomUUID(), UUID.randomUUID(), DocumentNodeType.SECTION, 1, 1, 1))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("missing parent");
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertThatThrownBy(() -> new DocumentNodePageLocator(VERSION, List.of(
                node(ROOT, null, DocumentNodeType.DOCUMENT, 1, 1, null),
                node(a, b, DocumentNodeType.SECTION, 1, 1, 1),
                node(b, a, DocumentNodeType.SECTION, 1, 1, 2))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("acyclic");
        assertThatThrownBy(() -> new DocumentNodePageLocator(VERSION, List.of()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Exactly one");
    }

    private static DocumentNode node(
            UUID id, UUID parent, DocumentNodeType type, int start, int end, Integer ordinal) {
        return new DocumentNode(id, VERSION, parent, type, null, ordinal, start, end,
                null, null, DocumentNodeDetectionOrigin.NATIVE, null, Instant.EPOCH);
    }
}
