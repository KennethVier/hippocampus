package com.hippocampus.materials.application;

import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.materials.port.MaterialRepository;

public class DeleteMaterial {
    private final CurrentUser currentUser;
    private final MaterialRepository materials;

    public DeleteMaterial(CurrentUser currentUser, MaterialRepository materials) {
        this.currentUser = currentUser;
        this.materials = materials;
    }

    @Transactional
    public void execute(UUID materialId) {
        UUID ownerId = currentUser.authenticatedUser().userId();
        if (!materials.markDeletedOwned(materialId, ownerId)) {
            throw MaterialFailures.notFound();
        }
    }
}
