package com.hippocampus.materials.infrastructure.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataMaterialVersionRepository extends JpaRepository<MaterialVersionEntity, UUID> {}
