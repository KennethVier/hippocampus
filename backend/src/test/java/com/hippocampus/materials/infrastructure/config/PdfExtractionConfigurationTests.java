package com.hippocampus.materials.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.hippocampus.materials.application.ExtractPdfPages;
import com.hippocampus.materials.application.ProcessingStageHandler;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.materials.port.MaterialContentInspector;
import com.hippocampus.materials.port.PdfExtractionSourceRepository;
import com.hippocampus.materials.port.PdfPageExtractor;

class PdfExtractionConfigurationTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    MaterialContentInspectionConfiguration.class,
                    PdfExtractionConfiguration.class))
            .withUserConfiguration(RequiredBeans.class)
            .withPropertyValues(
                    "hippocampus.materials.processing.pdf.page-batch-size=20",
                    "hippocampus.materials.processing.pdf.max-pages=2000",
                    "hippocampus.materials.processing.pdf.max-native-text-chars-per-page=1000000")
            .withPropertyValues(ocrProperties());

    @Test
    void createsExtractionCapabilityWithoutRegisteringAProcessingHandler() {
        runner.run(context -> assertThat(context)
                .hasNotFailed()
                .hasSingleBean(MaterialContentInspector.class)
                .hasSingleBean(PdfExtractionSourceRepository.class)
                .hasSingleBean(PdfPageExtractor.class)
                .hasSingleBean(ExtractPdfPages.class)
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
                .withPropertyValues(ocrProperties())
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(ExtractPdfPages.class));
    }

    private static String[] ocrProperties() {
        return new String[] {
            "hippocampus.materials.processing.pdf.ocr-executable=/usr/bin/tesseract",
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
