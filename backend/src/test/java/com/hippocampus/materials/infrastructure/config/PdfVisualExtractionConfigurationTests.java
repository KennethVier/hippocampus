package com.hippocampus.materials.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;

import com.hippocampus.materials.application.AssociateVisualContext;
import com.hippocampus.materials.application.ExtractPdfVisuals;
import com.hippocampus.materials.application.ExtractPdfTables;
import com.hippocampus.materials.application.FinalizeTableTextExtraction;
import com.hippocampus.materials.application.PersistVisualAssets;
import com.hippocampus.materials.application.PersistVisualContext;
import com.hippocampus.materials.application.PersistTableText;
import com.hippocampus.materials.application.ProcessingStageHandler;
import com.hippocampus.materials.application.VisualExtractStageHandler;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.materials.port.DocumentStructureRepository;
import com.hippocampus.materials.port.MaterialContentInspector;
import com.hippocampus.materials.port.PdfExtractionSourceRepository;
import com.hippocampus.materials.port.PdfVisualExtractor;
import com.hippocampus.materials.port.PdfTableExtractor;
import com.hippocampus.materials.port.TableTextPersistence;
import com.hippocampus.materials.port.VisualAssetPersistence;
import com.hippocampus.materials.port.VisualContextRepository;

class PdfVisualExtractionConfigurationTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PdfVisualExtractionConfiguration.class))
            .withPropertyValues(
                    "hippocampus.materials.processing.pdf.visual.max-images-per-page=2",
                    "hippocampus.materials.processing.pdf.visual.max-images-per-document=4",
                    "hippocampus.materials.processing.pdf.visual.max-source-image-dimension=10",
                    "hippocampus.materials.processing.pdf.visual.max-source-image-pixels=100",
                    "hippocampus.materials.processing.pdf.visual.max-page-source-image-pixels=200",
                    "hippocampus.materials.processing.pdf.visual.max-document-source-image-pixels=400",
                    "hippocampus.materials.processing.pdf.visual.max-encoded-image-bytes=100",
                    "hippocampus.materials.processing.pdf.visual.max-page-encoded-image-bytes=200",
                    "hippocampus.materials.processing.pdf.visual.max-document-encoded-image-bytes=400",
                    "hippocampus.materials.processing.pdf.table.max-tables-per-page=2",
                    "hippocampus.materials.processing.pdf.table.max-tables-per-document=4",
                    "hippocampus.materials.processing.pdf.table.max-rows-per-table=10",
                    "hippocampus.materials.processing.pdf.table.max-columns-per-table=5",
                    "hippocampus.materials.processing.pdf.table.max-table-text-chars=1000");

    @Test
    void registersTheRealVisualExtractHandlerWhenAllBoundariesExist() {
        runner.withUserConfiguration(RequiredBeans.class).run(context -> {
            assertThat(context).hasNotFailed()
                    .hasSingleBean(PdfVisualExtractor.class)
                    .hasSingleBean(PdfTableExtractor.class)
                    .hasSingleBean(TableTextPersistence.class)
                    .hasSingleBean(VisualAssetPersistence.class)
                    .hasSingleBean(VisualContextRepository.class)
                    .hasSingleBean(PersistVisualAssets.class)
                    .hasSingleBean(PersistVisualContext.class)
                    .hasSingleBean(PersistTableText.class)
                    .hasSingleBean(FinalizeTableTextExtraction.class)
                    .hasSingleBean(ExtractPdfVisuals.class)
                    .hasSingleBean(ExtractPdfTables.class)
                    .hasSingleBean(AssociateVisualContext.class)
                    .hasSingleBean(ProcessingStageHandler.class);
            assertThat(context.getBean(ProcessingStageHandler.class)).isInstanceOf(VisualExtractStageHandler.class);
        });
    }

    @Test
    void doesNotRegisterPartialCapabilityWithoutObjectStorage() {
        runner.withUserConfiguration(MissingStorageBeans.class).run(context -> assertThat(context)
                .hasNotFailed()
                .doesNotHaveBean(ProcessingStageHandler.class)
                .doesNotHaveBean(PdfVisualExtractor.class)
                .doesNotHaveBean(VisualAssetPersistence.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class MissingStorageBeans {
        @Bean JdbcClient jdbcClient() { return mock(JdbcClient.class); }
        @Bean PlatformTransactionManager transactionManager() { return mock(PlatformTransactionManager.class); }
        @Bean MaterialContentInspector inspector() { return mock(MaterialContentInspector.class); }
        @Bean PdfExtractionSourceRepository sources() { return mock(PdfExtractionSourceRepository.class); }
        @Bean DocumentStructureRepository structures() { return mock(DocumentStructureRepository.class); }
        @Bean PdfExtractionProperties pdfProperties() {
            PdfExtractionProperties properties = mock(PdfExtractionProperties.class);
            org.mockito.Mockito.when(properties.maxPages()).thenReturn(10);
            org.mockito.Mockito.when(properties.maxNativeTextCharsPerPage()).thenReturn(1000);
            return properties;
        }
        @Bean PdfStructureInspectionProperties structureProperties() {
            PdfStructureInspectionProperties properties = mock(PdfStructureInspectionProperties.class);
            org.mockito.Mockito.when(properties.maxTextPositionsPerPage()).thenReturn(1000);
            org.mockito.Mockito.when(properties.maxLayoutLinesPerPage()).thenReturn(100);
            return properties;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class RequiredBeans extends MissingStorageBeans {
        @Bean BinaryObjectStore objectStore() { return mock(BinaryObjectStore.class); }
    }
}
