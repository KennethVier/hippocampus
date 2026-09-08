package com.hippocampus.materials.application;

import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.hippocampus.materials.port.NormalizedTextPersistence;

public class FinalizeTextNormalization {
    private final NormalizedTextPersistence persistence;

    public FinalizeTextNormalization(NormalizedTextPersistence persistence) {
        this.persistence = Objects.requireNonNull(persistence);
    }

    @Transactional
    public void execute(UUID version, int pages) {
        persistence.finalizeNormalization(version, pages);
    }
}
