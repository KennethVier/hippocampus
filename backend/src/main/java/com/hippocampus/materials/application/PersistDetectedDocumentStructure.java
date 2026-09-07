package com.hippocampus.materials.application;

import java.util.Objects;

import org.springframework.transaction.annotation.Transactional;

import com.hippocampus.materials.domain.DetectedDocumentStructure;
import com.hippocampus.materials.port.DetectedDocumentStructurePersistence;

public class PersistDetectedDocumentStructure {
    private final DetectedDocumentStructurePersistence persistence;

    public PersistDetectedDocumentStructure(DetectedDocumentStructurePersistence persistence) {
        this.persistence = Objects.requireNonNull(persistence);
    }

    @Transactional
    public void execute(DetectedDocumentStructure structure) {
        persistence.persistOrVerify(structure);
    }
}
