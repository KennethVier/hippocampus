package com.hippocampus.materials.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.hippocampus.materials.domain.DocumentNode;
import com.hippocampus.materials.domain.TextBlock;

public interface DocumentStructureRepository {
    boolean hasDocumentRoot(UUID materialVersionId);

    Optional<DocumentNode> findDocumentRoot(UUID materialVersionId);

    List<DocumentNode> findNodesByMaterialVersion(UUID materialVersionId);

    List<DocumentNode> findChildren(UUID materialVersionId, UUID parentId);

    List<TextBlock> findTextBlocksByOrdinalRange(UUID materialVersionId, int firstOrdinal, int lastOrdinal);
}
