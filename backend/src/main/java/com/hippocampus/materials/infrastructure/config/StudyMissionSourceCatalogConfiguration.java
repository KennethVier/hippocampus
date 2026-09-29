package com.hippocampus.materials.infrastructure.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.hippocampus.learning.port.StudyMissionSourceCatalog;
import com.hippocampus.materials.infrastructure.persistence.JdbcStudyMissionSourceCatalog;

@AutoConfiguration(
        after = JdbcClientAutoConfiguration.class,
        beforeName = "com.hippocampus.learning.infrastructure.config.StudyMissionApplicationConfiguration")
@ConditionalOnBean(JdbcClient.class)
public class StudyMissionSourceCatalogConfiguration {

    @Bean
    @ConditionalOnMissingBean(StudyMissionSourceCatalog.class)
    StudyMissionSourceCatalog studyMissionSourceCatalog(JdbcClient jdbcClient) {
        return new JdbcStudyMissionSourceCatalog(jdbcClient);
    }
}
