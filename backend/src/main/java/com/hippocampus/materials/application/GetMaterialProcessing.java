package com.hippocampus.materials.application;

import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.materials.port.MaterialMetadata;
import com.hippocampus.materials.port.MaterialProcessingStateRepository;
import com.hippocampus.materials.port.MaterialRepository;
import com.hippocampus.materials.port.MaterialVersionReadRepository;

public class GetMaterialProcessing {
    private final CurrentUser currentUser;
    private final MaterialRepository materials;
    private final MaterialVersionReadRepository versions;
    private final MaterialProcessingStateRepository processingStates;

    public GetMaterialProcessing(
            CurrentUser currentUser,
            MaterialRepository materials,
            MaterialVersionReadRepository versions,
            MaterialProcessingStateRepository processingStates) {
        this.currentUser = currentUser;
        this.materials = materials;
        this.versions = versions;
        this.processingStates = processingStates;
    }

    @Transactional(readOnly = true)
    public MaterialProcessingResult execute(UUID materialId) {
        UUID ownerId = currentUser.authenticatedUser().userId();
        MaterialMetadata material = materials.findVisibleOwnedById(materialId, ownerId)
                .orElseThrow(MaterialFailures::notFound);

        MaterialVersionReadRepository.MaterialVersionSnapshot version =
                versions.findActiveOrLatestByMaterialId(materialId).orElse(null);

        String readiness = material.status();
        MaterialProcessingStateRepository.DurableProcessingState state = version == null
                ? null
                : processingStates.findCurrentPhaseThreeState(version.versionId()).orElse(null);
        Double progress = meaningfulProgress(state);
        String limitation = limitation(readiness);
        boolean structureAvailable = version != null
                && processingStates.isStructureDetectionComplete(version.versionId());

        return new MaterialProcessingResult(
                material.id(),
                version != null ? version.versionId() : null,
                readiness,
                state != null ? state.stage() : null,
                progress,
                limitation,
                structureAvailable);
    }

    private static Double meaningfulProgress(
            MaterialProcessingStateRepository.DurableProcessingState state) {
        if (state == null || state.progress() == null
                || state.progressCurrent() == null || state.progressTotal() == null) {
            return null;
        }
        return state.progress().doubleValue();
    }

    private static String limitation(String readiness) {
        if ("PARTIALLY_READY".equals(readiness)) {
            return "Some parts of this material could not be fully processed.";
        }
        if ("FAILED".equals(readiness)) {
            return "This material could not be processed.";
        }
        return null;
    }

    public record MaterialProcessingResult(
            UUID materialId,
            UUID versionId,
            String readiness,
            String stage,
            Double progress,
            String limitation,
            boolean structureAvailable) {}
}
