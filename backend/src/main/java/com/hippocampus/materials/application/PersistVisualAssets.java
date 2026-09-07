package com.hippocampus.materials.application;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.hippocampus.materials.domain.VisualAssetDraft;
import com.hippocampus.materials.port.VisualAssetPersistence;

public class PersistVisualAssets {
    private final VisualAssetPersistence persistence;

    public PersistVisualAssets(VisualAssetPersistence persistence) {
        this.persistence = Objects.requireNonNull(persistence);
    }

    @Transactional
    public void execute(UUID materialVersionId, List<VisualAssetDraft> visuals) {
        persistence.persistOrVerify(materialVersionId, visuals);
    }
}
