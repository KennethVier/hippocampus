package com.hippocampus.materials.application;

import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.hippocampus.materials.domain.TableTextDraft;
import com.hippocampus.materials.port.TableTextPersistence;

public class PersistTableText {
    private final TableTextPersistence persistence;

    public PersistTableText(TableTextPersistence persistence) {
        this.persistence = Objects.requireNonNull(persistence);
    }

    @Transactional
    public void execute(UUID materialVersionId, int pageCount, TableTextDraft table) {
        persistence.persistOrVerify(materialVersionId, pageCount, table);
    }
}
