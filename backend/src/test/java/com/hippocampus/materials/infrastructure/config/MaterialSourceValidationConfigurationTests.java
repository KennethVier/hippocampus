package com.hippocampus.materials.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.hippocampus.materials.application.MaterialValidateStageHandler;
import com.hippocampus.materials.application.ProcessingDispatcher;
import com.hippocampus.materials.application.ProcessingStageHandler;
import com.hippocampus.materials.infrastructure.persistence.SpringDataMaterialRepository;
import com.hippocampus.materials.infrastructure.persistence.SpringDataMaterialVersionRepository;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.materials.port.MaterialSourceValidator;
import com.hippocampus.materials.port.PdfSourceInspector;

class MaterialSourceValidationConfigurationTests {

    @Test
    void remainsAbsentInBaseContextWithoutPersistenceOrStorage() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MaterialSourceValidationConfiguration.class))
                .run(context -> assertThat(context).hasNotFailed()
                        .doesNotHaveBean(MaterialSourceValidator.class)
                        .doesNotHaveBean(MaterialValidateStageHandler.class));
    }

    @Test
    void registersValidatorAndDispatcherHandlerOnlyWhenAllBoundariesExist() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        AopAutoConfiguration.class,
                        JdbcTemplateAutoConfiguration.class,
                        JdbcClientAutoConfiguration.class,
                        TransactionAutoConfiguration.class,
                        MaterialSourceValidationConfiguration.class,
                        ProcessingJobDispatchConfiguration.class))
                .withUserConfiguration(RequiredBeans.class)
                .run(context -> assertThat(context).hasNotFailed()
                        .hasSingleBean(MaterialSourceValidator.class)
                        .hasSingleBean(MaterialValidateStageHandler.class)
                        .hasSingleBean(ProcessingStageHandler.class)
                        .hasSingleBean(ProcessingDispatcher.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class RequiredBeans {
        @Bean
        DataSource dataSource() {
            return mock(DataSource.class);
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return mock(PlatformTransactionManager.class);
        }

        @Bean
        SpringDataMaterialRepository materials() {
            return mock(SpringDataMaterialRepository.class);
        }

        @Bean
        SpringDataMaterialVersionRepository versions() {
            return mock(SpringDataMaterialVersionRepository.class);
        }

        @Bean
        BinaryObjectStore objectStore() {
            return mock(BinaryObjectStore.class);
        }

        @Bean
        PdfSourceInspector pdfSourceInspector() {
            return mock(PdfSourceInspector.class);
        }
    }
}
