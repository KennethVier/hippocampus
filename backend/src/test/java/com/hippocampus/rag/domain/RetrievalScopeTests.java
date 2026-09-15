package com.hippocampus.rag.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class RetrievalScopeTests {
    @Test
    void isImmutableAndProvidesDerivedViews() {
        UUID wholeVersion = UUID.randomUUID();
        UUID nodeVersion = UUID.randomUUID();
        UUID node = UUID.randomUUID();
        Set<UUID> mutableNodes = new HashSet<>(Set.of(node));
        RetrievalScopeTarget nodeTarget = new RetrievalScopeTarget(nodeVersion, mutableNodes);
        List<RetrievalScopeTarget> mutableTargets = new ArrayList<>(List.of(
                new RetrievalScopeTarget(wholeVersion, Set.of()), nodeTarget));
        RetrievalScope scope = new RetrievalScope(UUID.randomUUID(), UUID.randomUUID(),
                GroundingMode.SOURCE_FIRST, mutableTargets);
        mutableNodes.clear();
        mutableTargets.clear();

        assertThat(scope.allowedMaterialVersionIds()).containsExactlyInAnyOrder(wholeVersion, nodeVersion);
        assertThat(scope.wholeMaterialVersionIds()).containsExactly(wholeVersion);
        assertThat(scope.allowedDocumentNodeIds()).containsExactly(node);
        assertThat(scope.isEmpty()).isFalse();
        assertThatThrownBy(() -> scope.targets().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> nodeTarget.documentNodeIds().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsDuplicateMaterialVersionTargets() {
        UUID version = UUID.randomUUID();
        assertThatThrownBy(() -> new RetrievalScope(UUID.randomUUID(), UUID.randomUUID(),
                GroundingMode.STRICT_SOURCE, List.of(
                        new RetrievalScopeTarget(version, Set.of()),
                        new RetrievalScopeTarget(version, Set.of(UUID.randomUUID())))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate material versions");
    }

    @Test
    void supportsAValidEmptyScope() {
        RetrievalScope scope = new RetrievalScope(UUID.randomUUID(), UUID.randomUUID(),
                GroundingMode.GENERAL_KNOWLEDGE, Set.of());
        assertThat(scope.isEmpty()).isTrue();
        assertThat(scope.allowedMaterialVersionIds()).isEmpty();
    }
}
