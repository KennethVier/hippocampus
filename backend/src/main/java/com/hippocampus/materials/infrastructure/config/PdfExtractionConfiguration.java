package com.hippocampus.materials.infrastructure.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.hippocampus.materials.application.ExtractPdfPages;
import com.hippocampus.materials.infrastructure.pdf.PdfBoxPdfPageExtractor;
import com.hippocampus.materials.infrastructure.pdf.PdfBoxPdfSourceInspector;
import com.hippocampus.materials.infrastructure.ocr.TesseractCliOcrAdapter;
import com.hippocampus.materials.infrastructure.persistence.JdbcPdfExtractionSourceRepository;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.materials.port.MaterialContentInspector;
import com.hippocampus.materials.port.OcrPort;
import com.hippocampus.materials.port.PdfExtractionSourceRepository;
import com.hippocampus.materials.port.PdfPageExtractor;
import com.hippocampus.materials.port.PdfSourceInspector;

@AutoConfiguration(
        after = MaterialContentInspectionConfiguration.class,
        afterName = "org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration")
@ConditionalOnBean(JdbcClient.class)
@EnableConfigurationProperties(PdfExtractionProperties.class)
public class PdfExtractionConfiguration {
    @Bean
    PdfExtractionSourceRepository pdfExtractionSourceRepository(JdbcClient jdbcClient) {
        return new JdbcPdfExtractionSourceRepository(jdbcClient);
    }

    @Bean
    @ConditionalOnBean({BinaryObjectStore.class, MaterialContentInspector.class})
    PdfSourceInspector pdfSourceInspector(
            BinaryObjectStore objectStore,
            MaterialContentInspector contentInspector,
            PdfExtractionProperties properties) {
        return new PdfBoxPdfSourceInspector(objectStore, contentInspector, properties.maxPages());
    }

    @Bean
    OcrPort ocrPort(PdfExtractionProperties properties) {
        return new TesseractCliOcrAdapter(
                properties.ocrExecutable(), properties.ocrMaxInputBytes(), properties.ocrMaxStdoutBytes(),
                properties.ocrMaxStderrBytes(), properties.ocrMaxTsvRows(), properties.ocrMaxTsvFieldChars(),
                properties.ocrMaxTextChars(), properties.ocrTimeout(), properties.ocrTerminationGrace());
    }

    @Bean
    @ConditionalOnBean({BinaryObjectStore.class, MaterialContentInspector.class})
    PdfPageExtractor pdfPageExtractor(
            BinaryObjectStore objectStore,
            MaterialContentInspector contentInspector,
            OcrPort ocrPort,
            PdfExtractionProperties properties) {
        return new PdfBoxPdfPageExtractor(
                objectStore,
                contentInspector,
                ocrPort,
                properties.pageBatchSize(),
                properties.maxPages(),
                properties.maxNativeTextCharsPerPage(),
                properties.ocrRenderDpi(),
                properties.ocrMaxWidthPixels(),
                properties.ocrMaxHeightPixels(),
                properties.ocrMaxPixels(),
                properties.ocrMaxSourceImageDimension(),
                properties.ocrMaxSourceImagePixels(),
                properties.ocrMaxPageSourceImagePixels(),
                properties.ocrMaxInputBytes());
    }

    @Bean
    @ConditionalOnBean({BinaryObjectStore.class, MaterialContentInspector.class})
    ExtractPdfPages extractPdfPages(
            PdfExtractionSourceRepository sources,
            PdfPageExtractor extractor) {
        return new ExtractPdfPages(sources, extractor);
    }
}
