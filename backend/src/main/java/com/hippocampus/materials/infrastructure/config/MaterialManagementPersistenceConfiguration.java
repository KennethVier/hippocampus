package com.hippocampus.materials.infrastructure.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

import com.hippocampus.materials.infrastructure.persistence.JpaMaterialRepository;
import com.hippocampus.materials.infrastructure.persistence.JpaMaterialVersionReadRepository;
import com.hippocampus.materials.infrastructure.persistence.SpringDataMaterialRepository;
import com.hippocampus.materials.infrastructure.persistence.SpringDataMaterialVersionRepository;
import com.hippocampus.materials.port.MaterialRepository;
import com.hippocampus.materials.port.MaterialVersionReadRepository;

@AutoConfiguration(afterName = "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration")
@ConditionalOnBean(SpringDataMaterialRepository.class)
public class MaterialManagementPersistenceConfiguration {

    @Bean
    MaterialRepository materialRepository(SpringDataMaterialRepository materials) {
        return new JpaMaterialRepository(materials);
    }

    @Bean
    @ConditionalOnBean(SpringDataMaterialVersionRepository.class)
    MaterialVersionReadRepository materialVersionReadRepository(
            SpringDataMaterialRepository materials,
            SpringDataMaterialVersionRepository versions) {
        return new JpaMaterialVersionReadRepository(materials, versions);
    }
}
