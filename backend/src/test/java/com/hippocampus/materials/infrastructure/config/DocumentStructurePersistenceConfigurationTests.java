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

import com.hippocampus.materials.application.ExtractMaterialStageHandler;
import com.hippocampus.materials.application.ExtractPdfPages;
import com.hippocampus.materials.application.FinalizePdfExtraction;
import com.hippocampus.materials.application.PersistPdfPageBatch;
import com.hippocampus.materials.application.ProcessingStageHandler;
import com.hippocampus.materials.infrastructure.persistence.SpringDataDocumentNodeRepository;
import com.hippocampus.materials.infrastructure.persistence.SpringDataTextBlockRepository;
import com.hippocampus.materials.port.DocumentStructureRepository;
import com.hippocampus.materials.port.PdfExtractionPersistence;

class DocumentStructurePersistenceConfigurationTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DocumentStructurePersistenceConfiguration.class, TextNormalizationConfiguration.class));

    @Test
    void databaseOnlyContextCreatesPersistenceWithoutExtractionHandler() {
        runner.withUserConfiguration(DatabaseBeans.class).run(context -> assertThat(context)
                .hasNotFailed()
                .hasSingleBean(PdfExtractionPersistence.class)
                .hasSingleBean(PersistPdfPageBatch.class)
                .hasSingleBean(FinalizePdfExtraction.class)
                .doesNotHaveBean(DocumentStructureRepository.class)
                .doesNotHaveBean(ProcessingStageHandler.class));
    }

    @Test
    void createsDocumentStructureRepositoryOnlyWithItsJpaRepositories() {
        runner.withUserConfiguration(DatabaseAndJpaBeans.class).run(context -> assertThat(context)
                .hasNotFailed()
                .hasSingleBean(DocumentStructureRepository.class)
                .doesNotHaveBean(ProcessingStageHandler.class));
    }

    @Test
    void fullExtractionContextRegistersRealMaterialExtractHandler() {
        runner.withUserConfiguration(FullExtractionBeans.class).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(ProcessingStageHandler.class);
            assertThat(context.getBean(ProcessingStageHandler.class))
                    .isInstanceOf(ExtractMaterialStageHandler.class);
        });
    }

    @Test
    void normalizationRegistersWithTypedLimitsWithoutDependingOnLaterVisualConfiguration() {
        runner.withUserConfiguration(NormalizationBeans.class).withPropertyValues(
                "hippocampus.materials.processing.pdf.table.max-tables-per-page=2",
                "hippocampus.materials.processing.pdf.table.max-tables-per-document=10",
                "hippocampus.materials.processing.pdf.table.max-rows-per-table=10",
                "hippocampus.materials.processing.pdf.table.max-columns-per-table=4",
                "hippocampus.materials.processing.pdf.table.max-table-text-chars=40").run(context -> {
            assertThat(context).hasNotFailed()
                    .hasSingleBean(com.hippocampus.materials.application.NormalizeMaterialText.class)
                    .hasSingleBean(com.hippocampus.materials.application.PersistNormalizedText.class)
                    .hasSingleBean(com.hippocampus.materials.application.FinalizeTextNormalization.class);
            assertThat(context.getBean(ProcessingStageHandler.class))
                    .isInstanceOf(com.hippocampus.materials.application.NormalizeMaterialStageHandler.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class NormalizationBeans extends DatabaseBeans {
        @Bean
        PdfExtractionProperties pdfExtractionProperties() {
            var properties = mock(PdfExtractionProperties.class);
            org.mockito.Mockito.when(properties.pageBatchSize()).thenReturn(2);
            org.mockito.Mockito.when(properties.maxNativeTextCharsPerPage()).thenReturn(20);
            org.mockito.Mockito.when(properties.ocrMaxTextChars()).thenReturn(30);
            return properties;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DatabaseBeans {
        @Bean
        JdbcClient jdbcClient() {
            return mock(JdbcClient.class);
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return mock(PlatformTransactionManager.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DatabaseAndJpaBeans extends DatabaseBeans {
        @Bean
        SpringDataDocumentNodeRepository documentNodes() {
            return mock(SpringDataDocumentNodeRepository.class);
        }

        @Bean
        SpringDataTextBlockRepository textBlocks() {
            return mock(SpringDataTextBlockRepository.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class FullExtractionBeans extends DatabaseBeans {
        @Bean
        ExtractPdfPages extractPdfNativeText() {
            return mock(ExtractPdfPages.class);
        }
    }
}
