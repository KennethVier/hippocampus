package com.hippocampus.materials.port;

import java.util.Optional;
import java.util.UUID;

public interface MaterialRepository {

    MaterialPage findVisibleByOwner(UUID ownerId, MaterialPageRequest pageRequest);

    Optional<MaterialMetadata> findVisibleOwnedById(UUID materialId, UUID ownerId);

    MaterialDeletionOutcome markDeletedOwned(UUID materialId, UUID ownerId);
}
