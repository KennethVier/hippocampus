package com.hippocampus.materials.infrastructure.storage.filesystem;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hippocampus.storage.filesystem")
public record FileSystemStorageProperties(Path root) {}
