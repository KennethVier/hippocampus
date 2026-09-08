package com.hippocampus.materials.application;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.hippocampus.materials.domain.VisualContextAssociation;
import com.hippocampus.materials.port.VisualContextRepository;

public class PersistVisualContext {
    private final VisualContextRepository repository;

    public PersistVisualContext(VisualContextRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Transactional
    public void execute(UUID materialVersionId, List<VisualContextAssociation> associations) {
        repository.persist(materialVersionId, associations);
    }
}
