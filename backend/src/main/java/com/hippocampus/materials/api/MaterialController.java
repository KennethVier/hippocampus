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

    public MaterialController(ListMaterials listMaterials, GetMaterial getMaterial, DeleteMaterial deleteMaterial) {
        this.listMaterials = listMaterials;
        this.getMaterial = getMaterial;
        this.deleteMaterial = deleteMaterial;
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

    @DeleteMapping("/{materialId}")
    ResponseEntity<Void> delete(@PathVariable UUID materialId) {
        deleteMaterial.execute(materialId);
        return ResponseEntity.noContent().build();
    }
}
