package com.hippocampus.materials.infrastructure.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;

import com.hippocampus.materials.application.DetectDocumentStructure;
import com.hippocampus.materials.application.PersistDetectedDocumentStructure;
import com.hippocampus.materials.application.ProcessingStageHandler;
import com.hippocampus.materials.application.StructureDetectStageHandler;
import com.hippocampus.materials.domain.DeterministicDocumentStructureDetector;
import com.hippocampus.materials.infrastructure.persistence.JdbcDetectedDocumentStructurePersistence;
import com.hippocampus.materials.port.DetectedDocumentStructurePersistence;
import com.hippocampus.materials.port.DocumentStructureRepository;
import com.hippocampus.materials.port.PdfExtractionSourceRepository;
import com.hippocampus.materials.port.PdfStructureInspector;

@AutoConfiguration(after = {
        PdfStructureInspectionConfiguration.class,
        DocumentStructurePersistenceConfiguration.class
})
@ConditionalOnBean({
        JdbcClient.class,
        PlatformTransactionManager.class,
        PdfExtractionSourceRepository.class,
        PdfStructureInspector.class,
        DocumentStructureRepository.class
})
public class DetectedDocumentStructureConfiguration {
    @Bean
    DetectedDocumentStructurePersistence detectedDocumentStructurePersistence(
            JdbcClient jdbcClient, PdfStructureInspectionProperties properties) {
        return new JdbcDetectedDocumentStructurePersistence(jdbcClient, properties.maxNodesPerDocument());
    }

    @Bean
    PersistDetectedDocumentStructure persistDetectedDocumentStructure(
            DetectedDocumentStructurePersistence persistence) {
        return new PersistDetectedDocumentStructure(persistence);
    }

    @Bean
    DeterministicDocumentStructureDetector deterministicDocumentStructureDetector(
            PdfStructureInspectionProperties properties) {
        return new DeterministicDocumentStructureDetector(
                properties.maxCandidatesPerDocument(), properties.maxNodesPerDocument());
    }

    @Bean
    DetectDocumentStructure detectDocumentStructure(
            PdfExtractionSourceRepository sources,
            DocumentStructureRepository structures,
            PdfStructureInspector inspector,
            DeterministicDocumentStructureDetector detector,
            PersistDetectedDocumentStructure persistence) {
        return new DetectDocumentStructure(sources, structures, inspector, detector, persistence);
    }

    @Bean
    ProcessingStageHandler structureDetectStageHandler(DetectDocumentStructure detection) {
        return new StructureDetectStageHandler(detection);
    }
}
