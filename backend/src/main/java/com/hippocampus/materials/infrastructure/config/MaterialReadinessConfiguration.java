package com.hippocampus.materials.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.jdbc.core.simple.JdbcClient;
import com.hippocampus.materials.application.DeriveMaterialReadiness;
import com.hippocampus.materials.infrastructure.persistence.JdbcMaterialReadinessRepository;
import com.hippocampus.materials.port.MaterialReadinessRepository;

@AutoConfiguration
public class MaterialReadinessConfiguration {
    @Bean MaterialReadinessRepository materialReadinessRepository(JdbcClient jdbc) {
        return new JdbcMaterialReadinessRepository(jdbc);
    }
    @Bean DeriveMaterialReadiness deriveMaterialReadiness(MaterialReadinessRepository materials) {
        return new DeriveMaterialReadiness(materials);
    }
}
