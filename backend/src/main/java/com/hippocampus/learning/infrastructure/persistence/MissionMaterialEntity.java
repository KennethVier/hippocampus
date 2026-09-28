package com.hippocampus.learning.infrastructure.persistence;

import java.util.UUID;

import com.hippocampus.learning.domain.MissionMaterial;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "mission_materials")
public class MissionMaterialEntity {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "study_mission_id", nullable = false, updatable = false)
    private UUID studyMissionId;

    @Column(name = "material_id", nullable = false, updatable = false)
    private UUID materialId;

    @Column(name = "material_version_id", nullable = false, updatable = false)
    private UUID materialVersionId;

    @Column(name = "document_node_id", updatable = false)
    private UUID documentNodeId;

    protected MissionMaterialEntity() {}

    MissionMaterialEntity(UUID studyMissionId, MissionMaterial material) {
        id = material.id();
        this.studyMissionId = studyMissionId;
        materialId = material.materialId();
        materialVersionId = material.materialVersionId();
        documentNodeId = material.documentNodeId();
    }

    MissionMaterial toDomain() {
        return new MissionMaterial(id, materialId, materialVersionId, documentNodeId);
    }

    boolean hasFrozenIdentity(UUID missionId, MissionMaterial material) {
        return studyMissionId.equals(missionId)
                && materialId.equals(material.materialId())
                && materialVersionId.equals(material.materialVersionId())
                && java.util.Objects.equals(documentNodeId, material.documentNodeId());
    }

    public UUID getId() { return id; }
    public UUID getStudyMissionId() { return studyMissionId; }
}
