package com.hippocampus.materials.port;

import java.util.List;
import java.util.UUID;
import com.hippocampus.materials.domain.TextBlock;

public interface NormalizedTextPersistence {
    void persistOrVerify(UUID materialVersionId, List<TextBlock> blocks);
    void finalizeNormalization(UUID materialVersionId, int pageCount);
}
