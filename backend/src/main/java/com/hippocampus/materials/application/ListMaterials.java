package com.hippocampus.materials.application;

import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.materials.port.MaterialPageRequest;
import com.hippocampus.materials.port.MaterialRepository;

public final class ListMaterials {
    private final CurrentUser currentUser;
    private final MaterialRepository materials;

    public ListMaterials(CurrentUser currentUser, MaterialRepository materials) {
        this.currentUser = currentUser;
        this.materials = materials;
    }

    @Transactional(readOnly = true)
    public MaterialPageResult execute(int page, int size) {
        UUID ownerId = currentUser.authenticatedUser().userId();
        return MaterialPageResult.from(materials.findVisibleByOwner(ownerId, new MaterialPageRequest(page, size)));
    }
}
