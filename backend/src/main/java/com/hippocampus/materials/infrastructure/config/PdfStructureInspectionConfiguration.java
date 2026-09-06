package com.hippocampus.materials.infrastructure.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.hippocampus.materials.infrastructure.pdf.PdfBoxPdfStructureInspector;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.materials.port.MaterialContentInspector;
import com.hippocampus.materials.port.PdfStructureInspector;

@AutoConfiguration(after = PdfExtractionConfiguration.class)
@EnableConfigurationProperties(PdfStructureInspectionProperties.class)
public class PdfStructureInspectionConfiguration {
    @Bean
    @ConditionalOnBean({BinaryObjectStore.class, MaterialContentInspector.class})
    PdfStructureInspector pdfStructureInspector(
            BinaryObjectStore objectStore,
            MaterialContentInspector contentInspector,
            PdfExtractionProperties extractionProperties,
            PdfStructureInspectionProperties structureProperties) {
        return new PdfBoxPdfStructureInspector(
                objectStore,
                contentInspector,
                extractionProperties.pageBatchSize(),
                extractionProperties.maxPages(),
                extractionProperties.maxNativeTextCharsPerPage(),
                structureProperties.maxOutlineItems(),
                structureProperties.maxOutlineDepth(),
                structureProperties.maxTextPositionsPerPage(),
                structureProperties.maxLayoutLinesPerPage());
    }
}
