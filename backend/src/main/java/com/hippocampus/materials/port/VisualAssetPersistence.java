package com.hippocampus.materials.port;

import java.util.List;
import java.util.UUID;

import com.hippocampus.materials.domain.VisualAssetDraft;

public interface VisualAssetPersistence {
    void persistOrVerify(UUID materialVersionId, List<VisualAssetDraft> visuals);
}
