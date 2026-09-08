package com.hippocampus.materials.infrastructure.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;

import com.hippocampus.materials.application.ExtractMaterialStageHandler;
import com.hippocampus.materials.application.ExtractPdfPages;
import com.hippocampus.materials.application.FinalizePdfExtraction;
import com.hippocampus.materials.application.PersistPdfPageBatch;
import com.hippocampus.materials.application.ProcessingStageHandler;
import com.hippocampus.materials.application.NormalizeMaterialText;
import com.hippocampus.materials.application.NormalizeMaterialStageHandler;
import com.hippocampus.materials.application.PersistNormalizedText;
import com.hippocampus.materials.application.FinalizeTextNormalization;
import com.hippocampus.materials.domain.ExtractionNormalizationPolicy;
import com.hippocampus.materials.infrastructure.persistence.JdbcPdfExtractionPersistence;
import com.hippocampus.materials.infrastructure.persistence.JpaDocumentStructureRepository;
import com.hippocampus.materials.infrastructure.persistence.SpringDataDocumentNodeRepository;
import com.hippocampus.materials.infrastructure.persistence.SpringDataTextBlockRepository;
import com.hippocampus.materials.port.DocumentStructureRepository;
import com.hippocampus.materials.port.PdfExtractionPersistence;
import com.hippocampus.materials.infrastructure.persistence.JdbcTextNormalizationRepository;

@AutoConfiguration(
        after = PdfExtractionConfiguration.class,
        afterName = {
                "org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration",
                "org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration",
                "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"
        })
@ConditionalOnBean({JdbcClient.class, PlatformTransactionManager.class})
public class DocumentStructurePersistenceConfiguration {
    @Bean
    PdfExtractionPersistence pdfExtractionPersistence(JdbcClient jdbcClient) {
        return new JdbcPdfExtractionPersistence(jdbcClient);
    }

    @Bean
    @ConditionalOnBean({SpringDataDocumentNodeRepository.class, SpringDataTextBlockRepository.class})
    DocumentStructureRepository documentStructureRepository(
            SpringDataDocumentNodeRepository nodes,
            SpringDataTextBlockRepository blocks) {
        return new JpaDocumentStructureRepository(nodes, blocks);
    }

    @Bean
    PersistPdfPageBatch persistPdfPageBatch(PdfExtractionPersistence persistence) {
        return new PersistPdfPageBatch(persistence);
    }

    @Bean
    FinalizePdfExtraction finalizePdfExtraction(PdfExtractionPersistence persistence) {
        return new FinalizePdfExtraction(persistence);
    }

    @Bean
    JdbcTextNormalizationRepository textNormalizationRepository(JdbcClient jdbcClient) {
        return new JdbcTextNormalizationRepository(jdbcClient);
    }

    @Bean
    PersistNormalizedText persistNormalizedText(JdbcTextNormalizationRepository repository) {
        return new PersistNormalizedText(repository);
    }

    @Bean
    FinalizeTextNormalization finalizeTextNormalization(JdbcTextNormalizationRepository repository) {
        return new FinalizeTextNormalization(repository);
    }

    @Bean
    NormalizeMaterialText normalizeMaterialText(
            JdbcTextNormalizationRepository repository, PersistNormalizedText persistence,
            FinalizeTextNormalization finalization, PdfExtractionProperties properties) {
        return new NormalizeMaterialText(repository, new ExtractionNormalizationPolicy(), persistence, finalization,
                properties.pageBatchSize());
    }

    @Bean
    ProcessingStageHandler normalizeMaterialStageHandler(NormalizeMaterialText normalization) {
        return new NormalizeMaterialStageHandler(normalization);
    }

    @Bean
    @ConditionalOnBean(ExtractPdfPages.class)
    ProcessingStageHandler extractMaterialStageHandler(
            ExtractPdfPages extraction,
            PersistPdfPageBatch batches,
            FinalizePdfExtraction finalization) {
        return new ExtractMaterialStageHandler(extraction, batches, finalization);
    }
}
