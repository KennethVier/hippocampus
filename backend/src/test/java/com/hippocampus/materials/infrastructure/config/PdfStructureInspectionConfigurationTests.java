package com.hippocampus.materials.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.materials.port.MaterialContentInspector;
import com.hippocampus.materials.port.PdfStructureInspector;

class PdfStructureInspectionConfigurationTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PdfStructureInspectionConfiguration.class))
            .withPropertyValues(
                    "hippocampus.materials.processing.pdf.structure.max-outline-items=10",
                    "hippocampus.materials.processing.pdf.structure.max-outline-depth=3",
                    "hippocampus.materials.processing.pdf.structure.max-text-positions-per-page=100",
                    "hippocampus.materials.processing.pdf.structure.max-layout-lines-per-page=20",
                    "hippocampus.materials.processing.pdf.structure.max-candidates-per-document=50",
                    "hippocampus.materials.processing.pdf.structure.max-nodes-per-document=25");

    @Test
    void createsInspectorOnlyWhenPrivateStorageAndContentInspectionAreAvailable() {
        runner.withUserConfiguration(RequiredBeans.class).run(context -> assertThat(context)
                .hasNotFailed()
                .hasSingleBean(PdfStructureInspectionProperties.class)
                .hasSingleBean(PdfStructureInspector.class));

        runner.withBean(PdfExtractionProperties.class, () -> extractionProperties())
                .run(context -> assertThat(context).doesNotHaveBean(PdfStructureInspector.class));
    }

    @Test
    void backsOffWhenExtractionPropertiesAreUnavailable() {
        runner.withBean(BinaryObjectStore.class, () -> mock(BinaryObjectStore.class))
                .withBean(MaterialContentInspector.class, () -> mock(MaterialContentInspector.class))
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .doesNotHaveBean(PdfStructureInspector.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class RequiredBeans {
        @Bean
        BinaryObjectStore binaryObjectStore() {
            return mock(BinaryObjectStore.class);
        }

        @Bean
        MaterialContentInspector materialContentInspector() {
            return mock(MaterialContentInspector.class);
        }

        @Bean
        PdfExtractionProperties pdfExtractionProperties() {
            return extractionProperties();
        }
    }

    private static PdfExtractionProperties extractionProperties() {
        PdfExtractionProperties properties = mock(PdfExtractionProperties.class);
        when(properties.pageBatchSize()).thenReturn(20);
        when(properties.maxPages()).thenReturn(2_000);
        when(properties.maxNativeTextCharsPerPage()).thenReturn(200_000);
        return properties;
    }
}
