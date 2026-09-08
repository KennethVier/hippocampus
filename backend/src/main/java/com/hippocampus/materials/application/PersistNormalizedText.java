package com.hippocampus.materials.application;

import java.util.Objects;
import java.util.UUID;
import java.util.List;

import com.hippocampus.materials.domain.TextBlock;

import org.springframework.transaction.annotation.Transactional;

import com.hippocampus.materials.port.NormalizedTextPersistence;

public class PersistNormalizedText {
    private final NormalizedTextPersistence persistence;

    public PersistNormalizedText(NormalizedTextPersistence persistence) {
        this.persistence = Objects.requireNonNull(persistence);
    }

    @Transactional
    public void execute(UUID version, List<TextBlock> blocks) {
        persistence.persistOrVerify(version, blocks);
    }
}
