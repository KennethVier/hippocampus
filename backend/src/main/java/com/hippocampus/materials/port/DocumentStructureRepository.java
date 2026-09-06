package com.hippocampus.materials.port;

import java.util.List;
import java.util.UUID;

import com.hippocampus.materials.domain.DocumentNode;
import com.hippocampus.materials.domain.TextBlock;

public interface DocumentStructureRepository {
    List<DocumentNode> findNodesByMaterialVersion(UUID materialVersionId);

    List<DocumentNode> findChildren(UUID materialVersionId, UUID parentId);

    List<TextBlock> findTextBlocksByOrdinalRange(UUID materialVersionId, int firstOrdinal, int lastOrdinal);
}
