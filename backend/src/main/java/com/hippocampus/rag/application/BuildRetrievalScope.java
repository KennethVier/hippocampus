package com.hippocampus.rag.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.rag.domain.GroundingMode;
import com.hippocampus.rag.domain.RetrievalScope;
import com.hippocampus.rag.domain.RetrievalScopeTarget;
import com.hippocampus.rag.port.RetrievalScopeSource;
import com.hippocampus.rag.port.RetrievalScopeSourceRepository;

public class BuildRetrievalScope {
    private final CurrentUser currentUser;
    private final RetrievalScopeSourceRepository sources;

    public BuildRetrievalScope(CurrentUser currentUser, RetrievalScopeSourceRepository sources) {
        this.currentUser = Objects.requireNonNull(currentUser);
        this.sources = Objects.requireNonNull(sources);
    }

    @Transactional(readOnly = true)
    public RetrievalScope execute(Query query) {
        Objects.requireNonNull(query, "query must not be null");
        UUID userId = currentUser.authenticatedUser().userId();
        List<RetrievalScopeSource> authorized = Objects.requireNonNull(
                sources.findActiveAuthorizedTargets(userId, query.topicId()),
                "scope source repository must not return null");

        Map<UUID, Set<UUID>> nodesByVersion = new LinkedHashMap<>();
        Set<UUID> wholeVersions = new LinkedHashSet<>();
        for (RetrievalScopeSource source : authorized) {
            Objects.requireNonNull(source, "scope source rows must not contain null");
            UUID versionId = source.materialVersionId();
            if (source.documentNodeId() == null) {
                wholeVersions.add(versionId);
                nodesByVersion.remove(versionId);
            } else if (!wholeVersions.contains(versionId)) {
                nodesByVersion.computeIfAbsent(versionId, ignored -> new LinkedHashSet<>())
                        .add(source.documentNodeId());
            }
        }

        List<RetrievalScopeTarget> targets = new ArrayList<>();
        wholeVersions.forEach(version -> targets.add(new RetrievalScopeTarget(version, Set.of())));
        nodesByVersion.forEach((version, nodes) -> targets.add(new RetrievalScopeTarget(version, nodes)));
        return new RetrievalScope(userId, query.topicId(), query.groundingMode(), targets);
    }

    public record Query(UUID topicId, GroundingMode groundingMode) {
        public Query {
            Objects.requireNonNull(topicId, "topicId must not be null");
            Objects.requireNonNull(groundingMode, "groundingMode must not be null");
        }
    }
}
