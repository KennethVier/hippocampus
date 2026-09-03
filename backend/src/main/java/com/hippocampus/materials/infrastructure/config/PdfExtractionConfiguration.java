package com.hippocampus.materials.infrastructure.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.hippocampus.materials.application.ExtractPdfNativeText;
import com.hippocampus.materials.infrastructure.pdf.PdfBoxNativeTextExtractor;
import com.hippocampus.materials.infrastructure.persistence.JdbcPdfExtractionSourceRepository;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.materials.port.MaterialContentInspector;
import com.hippocampus.materials.port.PdfExtractionSourceRepository;
import com.hippocampus.materials.port.PdfNativeTextExtractor;

@AutoConfiguration(after = MaterialContentInspectionConfiguration.class)
@ConditionalOnBean({BinaryObjectStore.class, MaterialContentInspector.class, JdbcClient.class})
@EnableConfigurationProperties(PdfExtractionProperties.class)
public class PdfExtractionConfiguration {
    @Bean
    PdfExtractionSourceRepository pdfExtractionSourceRepository(JdbcClient jdbcClient) {
        return new JdbcPdfExtractionSourceRepository(jdbcClient);
    }

    @Bean
    PdfNativeTextExtractor pdfNativeTextExtractor(
            BinaryObjectStore objectStore,
            MaterialContentInspector contentInspector,
            PdfExtractionProperties properties) {
        return new PdfBoxNativeTextExtractor(
                objectStore,
                contentInspector,
                properties.pageBatchSize(),
                properties.maxPages(),
                properties.maxNativeTextCharsPerPage());
    }

    @Bean
    ExtractPdfNativeText extractPdfNativeText(
            PdfExtractionSourceRepository sources,
            PdfNativeTextExtractor extractor) {
        return new ExtractPdfNativeText(sources, extractor);
    }
}
