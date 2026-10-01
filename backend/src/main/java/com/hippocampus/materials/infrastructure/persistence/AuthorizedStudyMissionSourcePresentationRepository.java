package com.hippocampus.materials.infrastructure.persistence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.hippocampus.learning.domain.MissionMaterial;
import com.hippocampus.learning.port.StudyMissionSourcePresentationRepository;
import com.hippocampus.materials.domain.SourceReference;
import com.hippocampus.materials.port.MaterialRepository;
import com.hippocampus.materials.port.SourceReferenceRepository;

public final class AuthorizedStudyMissionSourcePresentationRepository
        implements StudyMissionSourcePresentationRepository {

    private final SourceReferenceRepository sourceReferences;
    private final MaterialRepository materials;

    public AuthorizedStudyMissionSourcePresentationRepository(
            SourceReferenceRepository sourceReferences,
            MaterialRepository materials) {
        this.sourceReferences = Objects.requireNonNull(
                sourceReferences, "sourceReferences must not be null");
        this.materials = Objects.requireNonNull(materials, "materials must not be null");
    }

    @Override
    public Optional<List<SourcePresentation>> resolveAuthorized(
            UUID ownerId,
            List<MissionMaterial> missionMaterials,
            Set<UUID> sourceReferenceIds) {
        Objects.requireNonNull(ownerId, "ownerId must not be null");
        Objects.requireNonNull(missionMaterials, "missionMaterials must not be null");
        Objects.requireNonNull(sourceReferenceIds, "sourceReferenceIds must not be null");

        List<SourcePresentation> presentations = new ArrayList<>();
        for (UUID sourceReferenceId : sourceReferenceIds.stream().sorted().toList()) {
            SourceReference reference = sourceReferences.resolveAuthorized(ownerId, sourceReferenceId)
                    .orElse(null);
            if (reference == null || !withinMissionScope(reference, missionMaterials)) {
                return Optional.empty();
            }
            var material = materials.findVisibleOwnedById(reference.materialId(), ownerId)
                    .orElse(null);
            if (material == null) {
                return Optional.empty();
            }
            presentations.add(new SourcePresentation(
                    reference.sourceReferenceId(), material.title(), reference.pageNumber(),
                    reference.displayLabel()));
        }
        presentations.sort(Comparator.comparing(SourcePresentation::sourceReferenceId));
        return Optional.of(List.copyOf(presentations));
    }

    private static boolean withinMissionScope(
            SourceReference reference, List<MissionMaterial> missionMaterials) {
        return missionMaterials.stream().anyMatch(scope ->
                scope.materialId().equals(reference.materialId())
                        && scope.materialVersionId().equals(reference.materialVersionId())
                        && (scope.documentNodeId() == null
                                || scope.documentNodeId().equals(reference.documentNodeId())));
    }
}
