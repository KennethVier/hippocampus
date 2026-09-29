package com.hippocampus.materials.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.hippocampus.learning.port.StudyMissionSourceCatalog;
import com.hippocampus.materials.infrastructure.persistence.JdbcStudyMissionSourceCatalog;

class StudyMissionSourceCatalogConfigurationTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(StudyMissionSourceCatalogConfiguration.class));

    @Test
    void remainsAbsentWithoutJdbcClient() {
        runner.run(context -> assertThat(context).hasNotFailed()
                .doesNotHaveBean(StudyMissionSourceCatalog.class));
    }

    @Test
    void createsJdbcSourceCatalogWhenJdbcClientExists() {
        runner.withUserConfiguration(JdbcClientConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(StudyMissionSourceCatalog.class);
            assertThat(context.getBean(StudyMissionSourceCatalog.class))
                    .isInstanceOf(JdbcStudyMissionSourceCatalog.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class JdbcClientConfiguration {
        @Bean
        JdbcClient jdbcClient() {
            return mock(JdbcClient.class);
        }
    }
}
