package com.hippocampus.materials.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

import com.hippocampus.materials.infrastructure.persistence.JpaMaterialUploadPersistence;
import com.hippocampus.materials.infrastructure.persistence.SpringDataMaterialRepository;
import com.hippocampus.materials.infrastructure.persistence.SpringDataMaterialVersionRepository;
import com.hippocampus.materials.port.MaterialUploadPersistence;

@AutoConfiguration(afterName = "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration")
@ConditionalOnBean({SpringDataMaterialRepository.class, SpringDataMaterialVersionRepository.class})
public class MaterialUploadPersistenceConfiguration {

    @Bean
    MaterialUploadPersistence materialUploadPersistence(
            SpringDataMaterialRepository materials,
            SpringDataMaterialVersionRepository versions) {
        return new JpaMaterialUploadPersistence(materials, versions);
    }
}
