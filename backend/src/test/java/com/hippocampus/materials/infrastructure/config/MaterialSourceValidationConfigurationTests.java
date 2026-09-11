package com.hippocampus.materials.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.nio.file.Path;

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
import com.hippocampus.materials.infrastructure.pdf.PdfBoxPdfSourceInspector;
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
                        MaterialContentInspectionConfiguration.class,
                        PdfExtractionConfiguration.class,
                        MaterialSourceValidationConfiguration.class,
                        ProcessingJobDispatchConfiguration.class))
                .withUserConfiguration(RequiredBeans.class)
                .withPropertyValues(pdfProperties())
                .run(context -> {
                    assertThat(context).hasNotFailed()
                            .hasSingleBean(MaterialSourceValidator.class)
                            .hasSingleBean(MaterialValidateStageHandler.class)
                            .hasSingleBean(ProcessingStageHandler.class)
                            .hasSingleBean(ProcessingDispatcher.class);
                    assertThat(context.getBeansOfType(PdfSourceInspector.class)).hasSize(1);
                    assertThat(context.getBean(PdfSourceInspector.class))
                            .isInstanceOf(PdfBoxPdfSourceInspector.class);
                });
    }

    private static String[] pdfProperties() {
        return new String[] {
            "hippocampus.materials.processing.pdf.page-batch-size=20",
            "hippocampus.materials.processing.pdf.max-pages=2000",
            "hippocampus.materials.processing.pdf.max-native-text-chars-per-page=1000000",
            "hippocampus.materials.processing.pdf.ocr-executable=" + absoluteExecutable(),
            "hippocampus.materials.processing.pdf.ocr-render-dpi=300",
            "hippocampus.materials.processing.pdf.ocr-max-width-pixels=10000",
            "hippocampus.materials.processing.pdf.ocr-max-height-pixels=10000",
            "hippocampus.materials.processing.pdf.ocr-max-pixels=40000000",
            "hippocampus.materials.processing.pdf.ocr-max-source-image-dimension=8000",
            "hippocampus.materials.processing.pdf.ocr-max-source-image-pixels=40000000",
            "hippocampus.materials.processing.pdf.ocr-max-page-source-image-pixels=80000000",
            "hippocampus.materials.processing.pdf.ocr-max-input-bytes=25000000",
            "hippocampus.materials.processing.pdf.ocr-max-stdout-bytes=8000000",
            "hippocampus.materials.processing.pdf.ocr-max-stderr-bytes=65536",
            "hippocampus.materials.processing.pdf.ocr-max-tsv-rows=200000",
            "hippocampus.materials.processing.pdf.ocr-max-tsv-field-chars=100000",
            "hippocampus.materials.processing.pdf.ocr-max-text-chars=1000000",
            "hippocampus.materials.processing.pdf.ocr-timeout=PT30S",
            "hippocampus.materials.processing.pdf.ocr-termination-grace=PT2S"
        };
    }

    private static String absoluteExecutable() {
        return Path.of(
                System.getProperty("java.home"), "bin",
                System.getProperty("os.name", "").startsWith("Windows") ? "java.exe" : "java")
                .toAbsolutePath().normalize().toString();
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

    }
}
