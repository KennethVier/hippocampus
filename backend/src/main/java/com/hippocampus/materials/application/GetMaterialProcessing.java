package com.hippocampus.materials.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.materials.infrastructure.persistence.MaterialVersionEntity;
import com.hippocampus.materials.infrastructure.persistence.SpringDataMaterialVersionRepository;
import com.hippocampus.materials.port.MaterialMetadata;
import com.hippocampus.materials.port.MaterialRepository;

public class GetMaterialProcessing {
    private final CurrentUser currentUser;
    private final MaterialRepository materials;
    private final SpringDataMaterialVersionRepository versions;

    public GetMaterialProcessing(
            CurrentUser currentUser,
            MaterialRepository materials,
            SpringDataMaterialVersionRepository versions) {
        this.currentUser = currentUser;
        this.materials = materials;
        this.versions = versions;
    }

    @Transactional(readOnly = true)
    public MaterialProcessingResult execute(UUID materialId) {
        UUID ownerId = currentUser.authenticatedUser().userId();
        MaterialMetadata material = materials.findVisibleOwnedById(materialId, ownerId)
                .orElseThrow(MaterialFailures::notFound);
        MaterialVersionEntity version = Optional.ofNullable(material.activeVersionId())
                .flatMap(versions::findById)
                .orElse(null);
        String status = version != null ? version.getProcessingStatus() : material.status();
        BigDecimal progress = version != null && version.getProcessingProgress() != null
                ? version.getProcessingProgress()
                : BigDecimal.ZERO;
        return new MaterialProcessingResult(
                material.id(),
                materialId,
                status,
                progress.doubleValue(),
                limitationFor(status),
                version != null ? version.getCreatedAt() : material.createdAt());
    }

    private static String limitationFor(String status) {
        if ("PARTIALLY_READY".equals(status) || "FAILED".equals(status)) {
            return "Some pages or images could not be processed.";
        }
        if ("READY".equals(status)) {
            return "Ready to study.";
        }
        if ("PROCESSING".equals(status)) {
            return "Still processing this material.";
        }
        return "Processing status is being updated.";
    }

    public record MaterialProcessingResult(
            UUID materialId,
            UUID versionId,
            String status,
            double progress,
            String limitation,
            Instant updatedAt) {}
}