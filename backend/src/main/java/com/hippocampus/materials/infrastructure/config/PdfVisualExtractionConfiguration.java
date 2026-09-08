package com.hippocampus.materials.infrastructure.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
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
import com.hippocampus.materials.domain.VisualContextAssociationPolicy;
import com.hippocampus.materials.infrastructure.pdf.PdfBoxPdfVisualExtractor;
import com.hippocampus.materials.infrastructure.pdf.PdfBoxPdfTableExtractor;
import com.hippocampus.materials.infrastructure.persistence.JdbcTableTextPersistence;
import com.hippocampus.materials.infrastructure.persistence.JdbcVisualAssetPersistence;
import com.hippocampus.materials.infrastructure.persistence.JdbcVisualContextRepository;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.materials.port.DocumentStructureRepository;
import com.hippocampus.materials.port.MaterialContentInspector;
import com.hippocampus.materials.port.PdfExtractionSourceRepository;
import com.hippocampus.materials.port.PdfVisualExtractor;
import com.hippocampus.materials.port.PdfTableExtractor;
import com.hippocampus.materials.port.TableTextPersistence;
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
@EnableConfigurationProperties({PdfVisualExtractionProperties.class, PdfTableExtractionProperties.class})
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
    PdfTableExtractor pdfTableExtractor(
            BinaryObjectStore objectStore,
            MaterialContentInspector contentInspector,
            PdfExtractionProperties pdfProperties,
            PdfStructureInspectionProperties structureProperties,
            PdfTableExtractionProperties tableProperties) {
        return new PdfBoxPdfTableExtractor(
                objectStore, contentInspector, pdfProperties.maxPages(),
                pdfProperties.maxNativeTextCharsPerPage(), structureProperties.maxTextPositionsPerPage(),
                tableProperties.maxTablesPerPage(), tableProperties.maxTablesPerDocument(),
                tableProperties.maxRowsPerTable(), tableProperties.maxColumnsPerTable(),
                tableProperties.maxTableTextChars());
    }

    @Bean
    TableTextPersistence tableTextPersistence(
            JdbcClient jdbcClient, PdfTableExtractionProperties properties) {
        return new JdbcTableTextPersistence(
                jdbcClient, properties.maxTablesPerDocument(), properties.maxTableTextChars());
    }

    @Bean
    PersistTableText persistTableText(TableTextPersistence persistence) {
        return new PersistTableText(persistence);
    }

    @Bean
    FinalizeTableTextExtraction finalizeTableTextExtraction(TableTextPersistence persistence) {
        return new FinalizeTableTextExtraction(persistence);
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
    ExtractPdfTables extractPdfTables(
            PdfExtractionSourceRepository sources,
            DocumentStructureRepository structures,
            PdfTableExtractor extractor,
            PersistTableText persistence,
            FinalizeTableTextExtraction finalization,
            PdfTableExtractionProperties properties) {
        return new ExtractPdfTables(
                sources, structures, extractor, persistence, finalization,
                properties.maxTablesPerPage(), properties.maxTablesPerDocument(), properties.maxRowsPerTable(),
                properties.maxColumnsPerTable(), properties.maxTableTextChars());
    }

    @Bean
    ProcessingStageHandler visualExtractStageHandler(
            ExtractPdfVisuals extraction, AssociateVisualContext association, ExtractPdfTables tables) {
        return new VisualExtractStageHandler(extraction, association, tables);
    }
}
