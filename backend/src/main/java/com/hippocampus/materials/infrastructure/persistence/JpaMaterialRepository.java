package com.hippocampus.materials.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.hippocampus.materials.port.MaterialMetadata;
import com.hippocampus.materials.port.MaterialPage;
import com.hippocampus.materials.port.MaterialPageRequest;
import com.hippocampus.materials.port.MaterialRepository;

public final class JpaMaterialRepository implements MaterialRepository {
    private static final String DELETED = "DELETED";

    private final SpringDataMaterialRepository materials;

    public JpaMaterialRepository(SpringDataMaterialRepository materials) {
        this.materials = materials;
    }

    @Override
    public MaterialPage findVisibleByOwner(UUID ownerId, MaterialPageRequest pageRequest) {
        Pageable pageable = PageRequest.of(
                pageRequest.page(),
                pageRequest.size(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<MaterialEntity> page = materials.findByUserIdAndStatusNot(ownerId, DELETED, pageable);
        return new MaterialPage(
                page.getContent().stream().map(JpaMaterialRepository::metadata).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Override
    public Optional<MaterialMetadata> findVisibleOwnedById(UUID materialId, UUID ownerId) {
        return materials.findByIdAndUserIdAndStatusNot(materialId, ownerId, DELETED)
                .map(JpaMaterialRepository::metadata);
    }

    @Override
    public boolean markDeletedOwned(UUID materialId, UUID ownerId) {
        Optional<MaterialEntity> owned = materials.findByIdAndUserId(materialId, ownerId);
        if (owned.isEmpty()) {
            return false;
        }

        MaterialEntity material = owned.get();
        if (!DELETED.equals(material.getStatus()) || material.getActiveVersionId() != null) {
            material.setStatus(DELETED);
            material.setActiveVersionId(null);
            materials.saveAndFlush(material);
        }
        return true;
    }

    private static MaterialMetadata metadata(MaterialEntity entity) {
        return new MaterialMetadata(
                entity.getId(), entity.getTitle(), entity.getMaterialType(), entity.getOriginalFilename(),
                entity.getMimeType(), entity.getStatus(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
