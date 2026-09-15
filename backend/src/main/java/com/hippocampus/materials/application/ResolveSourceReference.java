package com.hippocampus.materials.application;

import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.materials.domain.SourceReference;
import com.hippocampus.materials.port.SourceReferenceRepository;

public class ResolveSourceReference {
    private final CurrentUser currentUser;
    private final SourceReferenceRepository references;

    public ResolveSourceReference(CurrentUser currentUser, SourceReferenceRepository references) {
        this.currentUser = Objects.requireNonNull(currentUser);
        this.references = Objects.requireNonNull(references);
    }

    @Transactional(readOnly = true)
    public SourceReference execute(Query query) {
        Objects.requireNonNull(query, "query must not be null");
        UUID userId = currentUser.authenticatedUser().userId();
        return references.resolveAuthorized(userId, query.sourceReferenceId())
                .orElseThrow(SourceReferenceFailures::notFound);
    }

    public record Query(UUID sourceReferenceId) {
        public Query {
            Objects.requireNonNull(sourceReferenceId, "sourceReferenceId must not be null");
        }
    }
}
