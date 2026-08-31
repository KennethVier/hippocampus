package com.hippocampus.materials.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.servlet.autoconfigure.MultipartProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties("hippocampus.materials.upload")
public record MaterialUploadProperties(DataSize maxFileSize) {

    public MaterialUploadProperties {
        if (maxFileSize == null || maxFileSize.toBytes() <= 0) {
            throw new IllegalArgumentException("max-file-size must be positive");
        }
    }

    public void validateTransport(MultipartProperties multipart) {
        if (!multipart.isEnabled()) {
            throw new IllegalArgumentException("Spring multipart support must be enabled");
        }
        if (multipart.getMaxFileSize().toBytes() < maxFileSize.toBytes()) {
            throw new IllegalArgumentException("Spring multipart max-file-size must not be below the application limit");
        }
        if (multipart.getMaxRequestSize().toBytes() <= maxFileSize.toBytes()) {
            throw new IllegalArgumentException("Spring multipart max-request-size must be greater than the application limit");
        }
    }
}
