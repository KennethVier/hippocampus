package com.hippocampus.materials.application;

import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.materials.port.MaterialRepository;

public final class GetMaterial {
    private final CurrentUser currentUser;
    private final MaterialRepository materials;

    public GetMaterial(CurrentUser currentUser, MaterialRepository materials) {
        this.currentUser = currentUser;
        this.materials = materials;
    }

    @Transactional(readOnly = true)
    public MaterialResult execute(UUID materialId) {
        UUID ownerId = currentUser.authenticatedUser().userId();
        return materials.findVisibleOwnedById(materialId, ownerId)
                .map(MaterialResult::from)
                .orElseThrow(MaterialFailures::notFound);
    }
}
