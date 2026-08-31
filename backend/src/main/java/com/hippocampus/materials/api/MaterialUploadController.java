package com.hippocampus.materials.api;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.multipart.MultipartFile;

import com.hippocampus.materials.application.MaterialUploadException;
import com.hippocampus.materials.application.MaterialUploadResult;
import com.hippocampus.materials.application.UploadMaterial;

@RequestMapping("/api/materials")
@RestController
@ConditionalOnBean(UploadMaterial.class)
public final class MaterialUploadController {

    private final UploadMaterial uploadMaterial;

    public MaterialUploadController(UploadMaterial uploadMaterial) {
        this.uploadMaterial = uploadMaterial;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MaterialUploadResponse> upload(
            @RequestPart(name = "file", required = false) List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new MaterialUploadException(MaterialUploadException.Kind.FILE_REQUIRED);
        }
        if (files.size() != 1) {
            throw new MaterialUploadException(MaterialUploadException.Kind.SINGLE_FILE_REQUIRED);
        }
        MultipartFile file = files.getFirst();
        MaterialUploadResult result = uploadMaterial.execute(new UploadMaterial.Command(
                file.getOriginalFilename(), file.getContentType(), file.getSize(), file::getInputStream));
        return ResponseEntity.status(201).body(MaterialUploadResponse.from(result));
    }
}
