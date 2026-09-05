package com.hippocampus.materials.infrastructure.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;

import com.hippocampus.materials.application.ExtractMaterialStageHandler;
import com.hippocampus.materials.application.ExtractPdfNativeText;
import com.hippocampus.materials.application.FinalizePdfExtraction;
import com.hippocampus.materials.application.PersistPdfPageBatch;
import com.hippocampus.materials.application.ProcessingStageHandler;
import com.hippocampus.materials.infrastructure.persistence.JdbcPdfExtractionPersistence;
import com.hippocampus.materials.infrastructure.persistence.JpaDocumentStructureRepository;
import com.hippocampus.materials.infrastructure.persistence.SpringDataDocumentNodeRepository;
import com.hippocampus.materials.infrastructure.persistence.SpringDataTextBlockRepository;
import com.hippocampus.materials.port.DocumentStructureRepository;
import com.hippocampus.materials.port.PdfExtractionPersistence;

@AutoConfiguration(
        after = PdfExtractionConfiguration.class,
        afterName = "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration")
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
    @ConditionalOnBean(ExtractPdfNativeText.class)
    ProcessingStageHandler extractMaterialStageHandler(
            ExtractPdfNativeText extraction,
            PersistPdfPageBatch batches,
            FinalizePdfExtraction finalization) {
        return new ExtractMaterialStageHandler(extraction, batches, finalization);
    }
}
