package com.hippocampus.materials.port;

import java.util.Optional;
import java.util.UUID;
import com.hippocampus.materials.domain.MaterialReadiness;

public interface MaterialReadinessRepository {
    /** Called only inside the transaction that owns the triggering job transition. */
    Optional<Snapshot> lockAndRead(UUID jobId);
    void update(Snapshot snapshot, MaterialReadiness.State versionStatus, String parentStatus);

    record Snapshot(UUID materialId, UUID versionId, String parentStatus, String activeVersionStatus,
            boolean latestVersion, MaterialReadiness.Facts facts) {}
}
