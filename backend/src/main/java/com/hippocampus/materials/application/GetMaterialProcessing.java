package com.hippocampus.materials.application;

import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.materials.port.MaterialMetadata;
import com.hippocampus.materials.port.MaterialRepository;
import com.hippocampus.materials.port.MaterialVersionReadRepository;

public class GetMaterialProcessing {
    private final CurrentUser currentUser;
    private final MaterialRepository materials;
    private final MaterialVersionReadRepository versions;

    public GetMaterialProcessing(
            CurrentUser currentUser,
            MaterialRepository materials,
            MaterialVersionReadRepository versions) {
        this.currentUser = currentUser;
        this.materials = materials;
        this.versions = versions;
    }

    @Transactional(readOnly = true)
    public MaterialProcessingResult execute(UUID materialId) {
        UUID ownerId = currentUser.authenticatedUser().userId();
        MaterialMetadata material = materials.findVisibleOwnedById(materialId, ownerId)
                .orElseThrow(MaterialFailures::notFound);

        MaterialVersionReadRepository.MaterialVersionSnapshot version =
                versions.findActiveOrLatestByMaterialId(materialId).orElse(null);

        String readiness = material.status();
        Double progress = version != null && version.progress() != null ? version.progress().doubleValue() : null;
        String limitation = version != null && "LIMITED".equalsIgnoreCase(version.extractionQuality())
                ? "Some pages or images could not be processed."
                : null;

        return new MaterialProcessingResult(
                material.id(),
                version != null ? version.versionId() : null,
                readiness,
                null,
                progress,
                limitation);
    }

    public record MaterialProcessingResult(
            UUID materialId,
            UUID versionId,
            String readiness,
            String stage,
            Double progress,
            String limitation) {}
}
