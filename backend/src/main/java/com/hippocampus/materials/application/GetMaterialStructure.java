package com.hippocampus.materials.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.materials.domain.DocumentNode;
import com.hippocampus.materials.port.DocumentStructureRepository;
import com.hippocampus.materials.port.MaterialMetadata;
import com.hippocampus.materials.port.MaterialRepository;
import com.hippocampus.materials.port.MaterialVersionReadRepository;

public class GetMaterialStructure {
    private final CurrentUser currentUser;
    private final MaterialRepository materials;
    private final DocumentStructureRepository structures;
    private final MaterialVersionReadRepository versions;

    public GetMaterialStructure(
            CurrentUser currentUser,
            MaterialRepository materials,
            DocumentStructureRepository structures,
            MaterialVersionReadRepository versions) {
        this.currentUser = currentUser;
        this.materials = materials;
        this.structures = structures;
        this.versions = versions;
    }

    @Transactional(readOnly = true)
    public MaterialStructureResult execute(UUID materialId) {
        UUID ownerId = currentUser.authenticatedUser().userId();
        MaterialMetadata material = materials.findVisibleOwnedById(materialId, ownerId)
                .orElseThrow(MaterialFailures::notFound);

        UUID versionId = versions.findActiveByMaterialId(materialId)
                .map(MaterialVersionReadRepository.MaterialVersionSnapshot::versionId)
                .orElse(null);
        if (versionId == null) {
            return new MaterialStructureResult(false, null);
        }

        List<DocumentNode> nodes = structures.findNodesByMaterialVersion(versionId);
        if (nodes.isEmpty()) {
            return new MaterialStructureResult(false, null);
        }

        Map<UUID, List<DocumentNode>> childrenByParent = new HashMap<>();
        for (DocumentNode node : nodes) {
            childrenByParent.computeIfAbsent(node.parentId(), ignored -> new ArrayList<>())
                    .add(node);
        }
        for (List<DocumentNode> children : childrenByParent.values()) {
            children.sort(Comparator.comparing(DocumentNode::ordinal, Comparator.nullsLast(Integer::compareTo)));
        }

        DocumentNode root = nodes.stream()
                .filter(node -> node.parentId() == null && node.nodeType().name().equals("DOCUMENT"))
                .findFirst()
                .orElse(null);

        return new MaterialStructureResult(true, root == null ? null : MaterialStructureResult.fromNode(root, childrenByParent));
    }

    public record MaterialStructureResult(boolean available, MaterialNode root) {
        static MaterialNode fromNode(DocumentNode node, Map<UUID, List<DocumentNode>> childrenByParent) {
            List<DocumentNode> children = childrenByParent.getOrDefault(node.id(), List.of());
            return new MaterialNode(
                    node.id(),
                    node.nodeType().name(),
                    node.title(),
                    node.startPage(),
                    node.endPage(),
                    children.stream()
                            .map(child -> fromNode(child, childrenByParent))
                            .toList());
        }
    }

    public record MaterialNode(
            UUID id,
            String nodeType,
            String title,
            Integer startPage,
            Integer endPage,
            List<MaterialNode> children) {}
}
