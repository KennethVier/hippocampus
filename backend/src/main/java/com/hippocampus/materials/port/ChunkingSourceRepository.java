package com.hippocampus.materials.port;
import java.util.List; import java.util.UUID; import com.hippocampus.materials.domain.DocumentNode; import com.hippocampus.materials.domain.TextBlock;
public interface ChunkingSourceRepository {
    int requirePageCount(UUID version); List<DocumentNode> findHierarchy(UUID version);
    List<TextBlock> findByPhysicalPage(UUID version, int firstPage, int lastPage);
    List<VisualSource> findVisualsByPhysicalPage(UUID version, int firstPage, int lastPage);
    record VisualSource(UUID id, UUID materialVersionId, UUID documentNodeId, int pageNumber) {}
}
