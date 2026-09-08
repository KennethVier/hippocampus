package com.hippocampus.materials.infrastructure.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;

import com.hippocampus.materials.application.AssociateVisualContext;
import com.hippocampus.materials.application.ExtractPdfVisuals;
import com.hippocampus.materials.application.PersistVisualAssets;
import com.hippocampus.materials.application.PersistVisualContext;
import com.hippocampus.materials.application.ProcessingStageHandler;
import com.hippocampus.materials.application.VisualExtractStageHandler;
import com.hippocampus.materials.domain.VisualContextAssociationPolicy;
import com.hippocampus.materials.infrastructure.pdf.PdfBoxPdfVisualExtractor;
import com.hippocampus.materials.infrastructure.persistence.JdbcVisualAssetPersistence;
import com.hippocampus.materials.infrastructure.persistence.JdbcVisualContextRepository;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.materials.port.DocumentStructureRepository;
import com.hippocampus.materials.port.MaterialContentInspector;
import com.hippocampus.materials.port.PdfExtractionSourceRepository;
import com.hippocampus.materials.port.PdfVisualExtractor;
import com.hippocampus.materials.port.VisualAssetPersistence;
import com.hippocampus.materials.port.VisualContextRepository;

@AutoConfiguration(after = {
        PdfExtractionConfiguration.class,
        DocumentStructurePersistenceConfiguration.class
})
@ConditionalOnBean({
        JdbcClient.class,
        PlatformTransactionManager.class,
        BinaryObjectStore.class,
        MaterialContentInspector.class,
        PdfExtractionSourceRepository.class,
        DocumentStructureRepository.class
})
@EnableConfigurationProperties(PdfVisualExtractionProperties.class)
public class PdfVisualExtractionConfiguration {
    @Bean
    PdfVisualExtractor pdfVisualExtractor(
            BinaryObjectStore objectStore,
            MaterialContentInspector contentInspector,
            PdfExtractionProperties pdfProperties,
            PdfVisualExtractionProperties visualProperties) {
        return new PdfBoxPdfVisualExtractor(
                objectStore, contentInspector, pdfProperties.maxPages(),
                visualProperties.maxImagesPerPage(), visualProperties.maxImagesPerDocument(),
                visualProperties.maxSourceImageDimension(), visualProperties.maxSourceImagePixels(),
                visualProperties.maxPageSourceImagePixels(), visualProperties.maxDocumentSourceImagePixels(),
                visualProperties.maxEncodedImageBytes(), visualProperties.maxPageEncodedImageBytes(),
                visualProperties.maxDocumentEncodedImageBytes());
    }

    @Bean
    VisualAssetPersistence visualAssetPersistence(
            JdbcClient jdbcClient, PdfVisualExtractionProperties properties) {
        return new JdbcVisualAssetPersistence(jdbcClient, properties.maxImagesPerDocument());
    }

    @Bean
    PersistVisualAssets persistVisualAssets(VisualAssetPersistence persistence) {
        return new PersistVisualAssets(persistence);
    }

    @Bean
    PersistVisualContext persistVisualContext(VisualContextRepository repository) {
        return new PersistVisualContext(repository);
    }

    @Bean
    VisualContextRepository visualContextRepository(JdbcClient jdbcClient) {
        return new JdbcVisualContextRepository(jdbcClient);
    }

    @Bean
    AssociateVisualContext associateVisualContext(
            VisualContextRepository visuals, DocumentStructureRepository structures,
            PersistVisualContext persistence) {
        return new AssociateVisualContext(
                visuals, structures, new VisualContextAssociationPolicy(), persistence);
    }

    @Bean
    ExtractPdfVisuals extractPdfVisuals(
            PdfExtractionSourceRepository sources,
            DocumentStructureRepository structures,
            PdfVisualExtractor extractor,
            BinaryObjectStore objectStore,
            PersistVisualAssets persistence) {
        return new ExtractPdfVisuals(sources, structures, extractor, objectStore, persistence);
    }

    @Bean
    ProcessingStageHandler visualExtractStageHandler(
            ExtractPdfVisuals extraction, AssociateVisualContext association) {
        return new VisualExtractStageHandler(extraction, association);
    }
}
