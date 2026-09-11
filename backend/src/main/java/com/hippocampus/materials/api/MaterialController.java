package com.hippocampus.materials.api;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hippocampus.materials.application.DeleteMaterial;
import com.hippocampus.materials.application.GetMaterial;
import com.hippocampus.materials.application.GetMaterialProcessing;
import com.hippocampus.materials.application.GetMaterialStructure;
import com.hippocampus.materials.application.ListMaterials;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/materials")
@ConditionalOnBean({ListMaterials.class, GetMaterial.class, DeleteMaterial.class})
public class MaterialController {
    private final ListMaterials listMaterials;
    private final GetMaterial getMaterial;
    private final DeleteMaterial deleteMaterial;
    private final GetMaterialProcessing getMaterialProcessing;
    private final GetMaterialStructure getMaterialStructure;

    public MaterialController(
            ListMaterials listMaterials,
            GetMaterial getMaterial,
            DeleteMaterial deleteMaterial,
            GetMaterialProcessing getMaterialProcessing,
            GetMaterialStructure getMaterialStructure) {
        this.listMaterials = listMaterials;
        this.getMaterial = getMaterial;
        this.deleteMaterial = deleteMaterial;
        this.getMaterialProcessing = getMaterialProcessing;
        this.getMaterialStructure = getMaterialStructure;
    }

    @GetMapping
    MaterialPageResponse list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return MaterialPageResponse.from(listMaterials.execute(page, size));
    }

    @GetMapping("/{materialId}")
    MaterialResponse get(@PathVariable UUID materialId) {
        return MaterialResponse.from(getMaterial.execute(materialId));
    }

    @GetMapping("/{materialId}/processing")
    MaterialProcessingResponse processing(@PathVariable UUID materialId) {
        GetMaterialProcessing.MaterialProcessingResult result = getMaterialProcessing.execute(materialId);
        return new MaterialProcessingResponse(
                result.materialId(),
                result.versionId(),
                result.readiness(),
                result.stage(),
                result.progress(),
                result.limitation(),
                result.structureAvailable());
    }

    @GetMapping("/{materialId}/structure")
    MaterialStructureResponse structure(@PathVariable UUID materialId) {
        GetMaterialStructure.MaterialStructureResult result = getMaterialStructure.execute(materialId);
        return new MaterialStructureResponse(
                result.available(),
                toNode(result.root()));
    }

    private MaterialStructureResponse.MaterialNode toNode(GetMaterialStructure.MaterialNode node) {
        if (node == null) return null;
        return new MaterialStructureResponse.MaterialNode(
                node.id(),
                node.nodeType(),
                node.title(),
                node.startPage(),
                node.endPage(),
                node.children() == null ? java.util.List.of() : node.children().stream().map(this::toNode).toList());
    }

    @DeleteMapping("/{materialId}")
    ResponseEntity<Void> delete(@PathVariable UUID materialId) {
        deleteMaterial.execute(materialId);
        return ResponseEntity.noContent().build();
    }
}
