package com.hippocampus.materials.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties("hippocampus.materials.upload")
public record MaterialUploadProperties(DataSize maxFileSize, DataSize maxRequestSize) {

    public MaterialUploadProperties {
        if (maxFileSize == null || maxFileSize.toBytes() <= 0) {
            throw new IllegalArgumentException("max-file-size must be positive");
        }
        if (maxRequestSize == null || maxRequestSize.toBytes() <= maxFileSize.toBytes()) {
            throw new IllegalArgumentException("max-request-size must be greater than max-file-size");
        }
    }
}
