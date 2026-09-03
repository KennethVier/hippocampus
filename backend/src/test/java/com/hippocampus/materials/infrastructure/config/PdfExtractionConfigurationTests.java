package com.hippocampus.materials.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.hippocampus.materials.application.ExtractPdfNativeText;
import com.hippocampus.materials.application.ProcessingStageHandler;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.materials.port.MaterialContentInspector;
import com.hippocampus.materials.port.PdfExtractionSourceRepository;
import com.hippocampus.materials.port.PdfNativeTextExtractor;

class PdfExtractionConfigurationTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    MaterialContentInspectionConfiguration.class,
                    PdfExtractionConfiguration.class))
            .withUserConfiguration(RequiredBeans.class)
            .withPropertyValues(
                    "hippocampus.materials.processing.pdf.page-batch-size=20",
                    "hippocampus.materials.processing.pdf.max-pages=2000",
                    "hippocampus.materials.processing.pdf.max-native-text-chars-per-page=1000000");

    @Test
    void createsExtractionCapabilityWithoutRegisteringAProcessingHandler() {
        runner.run(context -> assertThat(context)
                .hasNotFailed()
                .hasSingleBean(MaterialContentInspector.class)
                .hasSingleBean(PdfExtractionSourceRepository.class)
                .hasSingleBean(PdfNativeTextExtractor.class)
                .hasSingleBean(ExtractPdfNativeText.class)
                .doesNotHaveBean(ProcessingStageHandler.class));
    }

    @Test
    void remainsUnavailableWithoutTheObjectStorageBoundary() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        MaterialContentInspectionConfiguration.class,
                        PdfExtractionConfiguration.class))
                .withUserConfiguration(DatabaseOnly.class)
                .withPropertyValues(
                        "hippocampus.materials.processing.pdf.page-batch-size=20",
                        "hippocampus.materials.processing.pdf.max-pages=2000",
                        "hippocampus.materials.processing.pdf.max-native-text-chars-per-page=1000000")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(ExtractPdfNativeText.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class RequiredBeans extends DatabaseOnly {
        @Bean
        BinaryObjectStore objectStore() {
            return mock(BinaryObjectStore.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DatabaseOnly {
        @Bean
        JdbcClient jdbcClient() {
            return mock(JdbcClient.class);
        }
    }
}
