package com.hippocampus.materials.application;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.materials.domain.DocumentNode;
import com.hippocampus.materials.domain.DocumentNodeType;
import com.hippocampus.materials.infrastructure.persistence.DocumentNodeEntity;
import com.hippocampus.materials.infrastructure.persistence.SpringDataDocumentNodeRepository;
import com.hippocampus.materials.port.MaterialMetadata;
import com.hippocampus.materials.port.MaterialRepository;

public class GetMaterialStructure {
    private final CurrentUser currentUser;
    private final MaterialRepository materials;
    private final SpringDataDocumentNodeRepository nodes;

    public GetMaterialStructure(
            CurrentUser currentUser,
            MaterialRepository materials,
            SpringDataDocumentNodeRepository nodes) {
        this.currentUser = currentUser;
        this.materials = materials;
        this.nodes = nodes;
    }

    @Transactional(readOnly = true)
    public MaterialStructureResult execute(UUID materialId) {
        UUID ownerId = currentUser.authenticatedUser().userId();
        MaterialMetadata material = materials.findVisibleOwnedById(materialId, ownerId)
                .orElseThrow(MaterialFailures::notFound);
        UUID versionId = material.activeVersionId();
        if (versionId == null) {
            return new MaterialStructureResult(null, "UNAVAILABLE", null, null, null, List.of());
        }

        DocumentNodeEntity root = nodes.findByMaterialVersionIdAndNodeTypeAndParentIdIsNull(
                        versionId, DocumentNodeType.DOCUMENT)
                .orElse(null);
        return root == null
                ? new MaterialStructureResult(null, "UNAVAILABLE", null, null, null, List.of())
                : MaterialStructureResult.from(root, nodes.findByMaterialVersionIdAndParentIdOrderByOrdinalAsc(
                        versionId, root.getId()));
    }

    public record MaterialStructureResult(
            UUID id,
            String nodeType,
            String title,
            Integer startPage,
            Integer endPage,
            List<MaterialStructureResult> children) {
        static MaterialStructureResult from(DocumentNodeEntity root, List<DocumentNodeEntity> children) {
            return new MaterialStructureResult(
                    root.getId(),
                    root.getNodeType().name(),
                    root.getTitle(),
                    root.getStartPage(),
                    root.getEndPage(),
                    children.stream().map(GetMaterialStructure.MaterialStructureResult::fromEntity).toList());
        }

        static MaterialStructureResult fromEntity(DocumentNodeEntity node) {
            List<MaterialStructureResult> nested = List.of();
            return new MaterialStructureResult(
                    node.getId(),
                    node.getNodeType().name(),
                    node.getTitle(),
                    node.getStartPage(),
                    node.getEndPage(),
                    nested);
        }

        static MaterialStructureResult from(DocumentNode child, List<MaterialStructureResult> nested) {
            return new MaterialStructureResult(
                    child.id(),
                    child.nodeType().name(),
                    child.title(),
                    child.startPage(),
                    child.endPage(),
                    nested);
        }
    }
}