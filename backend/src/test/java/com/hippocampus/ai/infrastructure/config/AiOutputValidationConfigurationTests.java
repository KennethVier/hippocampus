package com.hippocampus.ai.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.hippocampus.ai.application.AiExecutionOrchestrator;
import com.hippocampus.ai.application.validation.AiOutputValidator;
import com.hippocampus.ai.application.validation.AiSourceReferenceValidator;
import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.materials.infrastructure.config.SourceReferenceConfiguration;
import com.hippocampus.materials.port.SourceReferenceRepository;

class AiOutputValidationConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AiOutputValidationConfiguration.class));

    @Test
    void keepsSchemaValidationAvailableWithoutSourceReferenceRepository() {
        contextRunner.run(context -> assertThat(context).hasNotFailed()
                .hasSingleBean(AiOutputValidator.class)
                .doesNotHaveBean(AiSourceReferenceValidator.class)
                .doesNotHaveBean(AiExecutionOrchestrator.class));
    }

    @Test
    void registersGroundingValidationWhenAuthoritativeRepositoryExists() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        AiOutputValidationConfiguration.class,
                        SourceReferenceConfiguration.class))
                .withUserConfiguration(GroundingDependencies.class)
                .run(context -> assertThat(context).hasNotFailed()
                        .hasSingleBean(AiOutputValidator.class)
                        .hasSingleBean(SourceReferenceRepository.class)
                        .hasSingleBean(AiSourceReferenceValidator.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class GroundingDependencies {

        @Bean
        CurrentUser currentUser() {
            return mock(CurrentUser.class);
        }

        @Bean
        JdbcClient jdbcClient() {
            return mock(JdbcClient.class);
        }
    }
}
