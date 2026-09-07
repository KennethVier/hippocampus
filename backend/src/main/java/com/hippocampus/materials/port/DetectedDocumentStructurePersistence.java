package com.hippocampus.materials.port;

import com.hippocampus.materials.domain.DetectedDocumentStructure;

public interface DetectedDocumentStructurePersistence {
    void persistOrVerify(DetectedDocumentStructure structure);
}
