package com.hippocampus.rag.infrastructure.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.hippocampus.rag.application.BuildEvidencePackage;
import com.hippocampus.rag.application.MaterializeEvidenceSourceReferences;
import com.hippocampus.materials.application.CreateSourceReferences;
import com.hippocampus.rag.infrastructure.persistence.JdbcEvidenceVisualRepository;
import com.hippocampus.rag.port.EvidenceVisualRepository;

@AutoConfiguration(after = JdbcClientAutoConfiguration.class)
@ConditionalOnBean(JdbcClient.class)
public class EvidencePackageConfiguration {

    @Bean
    EvidenceVisualRepository evidenceVisualRepository(JdbcClient jdbc) {
        return new JdbcEvidenceVisualRepository(jdbc);
    }

    @Bean
    BuildEvidencePackage buildEvidencePackage(EvidenceVisualRepository visuals) {
        return new BuildEvidencePackage(visuals);
    }

    @Bean
    MaterializeEvidenceSourceReferences materializeEvidenceSourceReferences(
            CreateSourceReferences createSourceReferences) {
        return new MaterializeEvidenceSourceReferences(createSourceReferences);
    }
}
