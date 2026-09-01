package com.hippocampus.materials.port;

import java.util.Optional;
import java.util.UUID;

public interface MaterialRepository {

    MaterialPage findVisibleByOwner(UUID ownerId, MaterialPageRequest pageRequest);

    Optional<MaterialMetadata> findVisibleOwnedById(UUID materialId, UUID ownerId);

    /**
     * Marks the owner's material deleted. Returns true when the material belongs to
     * the owner, including when it was already deleted, so deletion is idempotent.
     */
    boolean markDeletedOwned(UUID materialId, UUID ownerId);
}
