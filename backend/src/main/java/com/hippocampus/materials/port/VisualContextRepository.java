package com.hippocampus.materials.port;

import java.util.List;
import java.util.UUID;

import com.hippocampus.materials.domain.VisualContextAsset;
import com.hippocampus.materials.domain.VisualContextAssociation;

public interface VisualContextRepository {
    List<VisualContextAsset> findByMaterialVersion(UUID materialVersionId);

    void persist(UUID materialVersionId, List<VisualContextAssociation> associations);
}
