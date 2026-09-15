package com.hippocampus.materials.domain;

import java.util.UUID;

public sealed interface SourceReferenceTarget permits ChunkSourceTarget, VisualSourceTarget,
        DocumentNodeSourceTarget, PageSourceTarget {

    UUID materialId();

    UUID materialVersionId();
}
