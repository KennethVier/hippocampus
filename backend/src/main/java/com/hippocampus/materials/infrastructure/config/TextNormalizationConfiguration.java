package com.hippocampus.materials.infrastructure.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;

import com.hippocampus.materials.application.NormalizeMaterialText;
import com.hippocampus.materials.application.NormalizeMaterialStageHandler;
import com.hippocampus.materials.application.PersistNormalizedText;
import com.hippocampus.materials.application.FinalizeTextNormalization;
import com.hippocampus.materials.application.ProcessingStageHandler;
import com.hippocampus.materials.domain.ExtractionNormalizationPolicy;
import com.hippocampus.materials.infrastructure.persistence.JdbcTextNormalizationRepository;

@AutoConfiguration(after = {PdfExtractionConfiguration.class, DocumentStructurePersistenceConfiguration.class})
@ConditionalOnBean({JdbcClient.class, PlatformTransactionManager.class, PdfExtractionProperties.class})
@EnableConfigurationProperties(PdfTableExtractionProperties.class)
public class TextNormalizationConfiguration {
    @Bean
    JdbcTextNormalizationRepository textNormalizationRepository(
            JdbcClient jdbcClient, PdfExtractionProperties pdfProperties, PdfTableExtractionProperties tableProperties) {
        return new JdbcTextNormalizationRepository(jdbcClient, pdfProperties.maxNativeTextCharsPerPage(),
                pdfProperties.ocrMaxTextChars(), tableProperties.maxTableTextChars());
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
}
