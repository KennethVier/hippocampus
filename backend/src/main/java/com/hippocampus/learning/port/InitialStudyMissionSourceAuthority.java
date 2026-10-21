package com.hippocampus.learning.port;

import com.hippocampus.learning.domain.MissionMaterial;
import com.hippocampus.learning.domain.SourceCapability;
import com.hippocampus.learning.domain.SourceReadiness;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Actual frozen-version facts; active-version advancement and topic links are not revocations. */
public interface InitialStudyMissionSourceAuthority {
    List<ScopeFacts> inspect(UUID owner, List<MissionMaterial> scopes);
    /** Read-only initial provider-exposure check of the exact persisted evidence targets. */
    default List<ScopeFacts> inspectForExposure(UUID owner,List<MissionMaterial> scopes,Set<UUID> references) {
        throw new IllegalStateException("Initial evidence exposure verification is not configured");
    }
    List<ScopeFacts> lockAndInspect(UUID owner,List<MissionMaterial> scopes,
                                    Set<UUID> generatedReferences, boolean sourceRequired);

    /** Frozen source or evidence target is unavailable; busy dependencies remain distinct. */
    final class SourceUnavailableException extends RuntimeException {
        public SourceUnavailableException() { super("Initial source authorization failed"); }
    }

    record ScopeFacts(UUID materialId,UUID frozenVersionId,UUID documentNodeId,
                      String materialStatus,String versionStatus,SourceReadiness readiness,
                      boolean groundedTextAvailable,boolean visualAvailable,boolean visualReliable,
                      Set<UUID> chunkWitnesses,Set<UUID> visualWitnesses) {
        public ScopeFacts {
            chunkWitnesses=Set.copyOf(chunkWitnesses); visualWitnesses=Set.copyOf(visualWitnesses);
        }
    }

    static SourceCapability capability(List<ScopeFacts> facts) {
        SourceReadiness readiness = facts.isEmpty() ? SourceReadiness.INSUFFICIENT
                : facts.stream().allMatch(f -> f.readiness()==SourceReadiness.READY)
                ? SourceReadiness.READY : SourceReadiness.LIMITED;
        return new SourceCapability(readiness,facts.stream().anyMatch(ScopeFacts::groundedTextAvailable),
                facts.stream().anyMatch(ScopeFacts::visualAvailable),facts.stream().anyMatch(ScopeFacts::visualReliable));
    }
}
