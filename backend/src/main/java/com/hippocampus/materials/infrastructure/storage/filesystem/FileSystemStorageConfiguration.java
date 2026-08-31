package com.hippocampus.materials.infrastructure.storage.filesystem;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.hippocampus.materials.port.BinaryObjectStore;

@Configuration(proxyBeanMethods = false)
@Profile("local & !pilot")
@ConditionalOnProperty(prefix = "hippocampus.storage", name = "backend", havingValue = "filesystem")
@EnableConfigurationProperties(FileSystemStorageProperties.class)
public class FileSystemStorageConfiguration {

    @Bean
    BinaryObjectStore binaryObjectStore(FileSystemStorageProperties properties) {
        return new FileSystemBinaryObjectStore(properties.root());
    }
}
