package com.hippocampus.materials.port;

import java.util.List;
import java.util.UUID;
import com.hippocampus.materials.domain.TextBlock;

public interface TextNormalizationSourceRepository {
    int requirePageCount(UUID materialVersionId);
    List<TextBlock> findPageText(UUID materialVersionId, int firstPage, int lastPage);
    List<TextBlock> findTableText(UUID materialVersionId, int firstPage, int lastPage);
}
