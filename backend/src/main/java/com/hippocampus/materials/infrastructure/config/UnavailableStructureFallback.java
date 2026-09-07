package com.hippocampus.materials.infrastructure.config;

import java.util.Optional;

import com.hippocampus.materials.port.StructureFallback;
import com.hippocampus.materials.port.StructureFallbackRequest;
import com.hippocampus.materials.port.StructureFallbackResponse;

/** Phase 3 deliberately has no live provider capability. */
public final class UnavailableStructureFallback implements StructureFallback {
    @Override
    public Optional<StructureFallbackResponse> detect(StructureFallbackRequest request) {
        return Optional.empty();
    }
}
