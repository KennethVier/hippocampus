package com.hippocampus.materials.infrastructure.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataMaterialRepository extends JpaRepository<MaterialEntity, UUID> {}
