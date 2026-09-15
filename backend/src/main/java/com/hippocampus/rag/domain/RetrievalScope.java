package com.hippocampus.rag.domain;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record RetrievalScope(
        UUID userId,
        UUID topicId,
        GroundingMode groundingMode,
        Set<RetrievalScopeTarget> targets) {

    public RetrievalScope(UUID userId, UUID topicId, GroundingMode groundingMode,
            Collection<RetrievalScopeTarget> targets) {
        this(userId, topicId, groundingMode, immutableUniqueTargets(targets));
    }

    public RetrievalScope {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(topicId, "topicId must not be null");
        Objects.requireNonNull(groundingMode, "groundingMode must not be null");
        targets = immutableUniqueTargets(targets);
    }

    public Set<UUID> allowedMaterialVersionIds() {
        return targets.stream()
                .map(RetrievalScopeTarget::materialVersionId)
                .collect(Collectors.toUnmodifiableSet());
    }

    public Set<UUID> wholeMaterialVersionIds() {
        return targets.stream()
                .filter(RetrievalScopeTarget::allowsWholeMaterialVersion)
                .map(RetrievalScopeTarget::materialVersionId)
                .collect(Collectors.toUnmodifiableSet());
    }

    public Set<UUID> allowedDocumentNodeIds() {
        return targets.stream()
                .flatMap(target -> target.documentNodeIds().stream())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isEmpty() {
        return targets.isEmpty();
    }

    private static Set<RetrievalScopeTarget> immutableUniqueTargets(
            Collection<RetrievalScopeTarget> targets) {
        Objects.requireNonNull(targets, "targets must not be null");
        LinkedHashSet<RetrievalScopeTarget> copied = new LinkedHashSet<>();
        Set<UUID> versions = new LinkedHashSet<>();
        for (RetrievalScopeTarget target : targets) {
            Objects.requireNonNull(target, "targets must not contain null");
            if (!versions.add(target.materialVersionId())) {
                throw new IllegalArgumentException("targets must not contain duplicate material versions");
            }
            copied.add(target);
        }
        return Set.copyOf(copied);
    }
}
