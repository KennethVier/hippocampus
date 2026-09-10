package com.hippocampus.materials.application;

import java.util.UUID;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.hippocampus.materials.domain.MaterialReadiness;
import com.hippocampus.materials.port.MaterialReadinessRepository;

public class DeriveMaterialReadiness {
    private final MaterialReadinessRepository materials;
    public DeriveMaterialReadiness(MaterialReadinessRepository materials) { this.materials = materials; }

    @Transactional(propagation = Propagation.MANDATORY)
    public void execute(UUID transitionedJobId) {
        materials.lockAndRead(transitionedJobId).ifPresent(snapshot -> {
            var state = MaterialReadiness.derive(snapshot.facts());
            boolean affectsParent = snapshot.latestVersion()
                    || snapshot.versionId().equals(snapshot.activeVersionId());
            String parent = affectsParent
                    ? MaterialReadiness.parent(state, snapshot.parentStatus(), snapshot.activeVersionId(),
                            snapshot.versionId(), snapshot.activeVersionStatus())
                    : snapshot.parentStatus();
            materials.update(snapshot, state, parent);
        });
    }
}
