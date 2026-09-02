package com.hippocampus.materials.application;

import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.materials.port.MaterialDeletionOutcome;
import com.hippocampus.materials.port.MaterialLifecycleTelemetry;
import com.hippocampus.materials.port.MaterialRepository;

public class DeleteMaterial {
    private final CurrentUser currentUser;
    private final MaterialRepository materials;
    private final MaterialLifecycleTelemetry telemetry;

    public DeleteMaterial(
            CurrentUser currentUser,
            MaterialRepository materials,
            MaterialLifecycleTelemetry telemetry) {
        this.currentUser = currentUser;
        this.materials = materials;
        this.telemetry = telemetry;
    }

    @Transactional
    public void execute(UUID materialId) {
        UUID ownerId = currentUser.authenticatedUser().userId();
        MaterialDeletionOutcome outcome = materials.markDeletedOwned(materialId, ownerId);
        switch (outcome) {
            case DELETED -> publishDeletedSafely(materialId);
            case ALREADY_DELETED -> {
                // Idempotent HTTP success without a second lifecycle transition.
            }
            case NOT_FOUND -> throw MaterialFailures.notFound();
        }
    }

    private void publishDeletedSafely(UUID materialId) {
        try {
            telemetry.materialDeleted(materialId);
        } catch (RuntimeException ignored) {
            // The telemetry port contract is non-disruptive; preserve the business transaction.
        }
    }
}
