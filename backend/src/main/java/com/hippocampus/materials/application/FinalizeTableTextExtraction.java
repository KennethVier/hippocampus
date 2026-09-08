package com.hippocampus.materials.application;

import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.hippocampus.materials.port.TableTextPersistence;

public class FinalizeTableTextExtraction {
    private final TableTextPersistence persistence;

    public FinalizeTableTextExtraction(TableTextPersistence persistence) {
        this.persistence = Objects.requireNonNull(persistence);
    }

    @Transactional
    public void execute(UUID materialVersionId, int pageCount, int tableCount) {
        persistence.finalizeExtraction(materialVersionId, pageCount, tableCount);
    }
}
