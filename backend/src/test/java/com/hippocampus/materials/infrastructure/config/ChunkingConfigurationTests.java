package com.hippocampus.materials.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;

import com.hippocampus.materials.application.ChunkMaterialStageHandler;
import com.hippocampus.materials.application.ChunkMaterialText;
import com.hippocampus.materials.application.FinalizeChunking;
import com.hippocampus.materials.application.PersistChunkBatch;
import com.hippocampus.materials.application.ProcessingStageHandler;

import tools.jackson.databind.ObjectMapper;

class ChunkingConfigurationTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ChunkingConfiguration.class))
            .withUserConfiguration(Prerequisites.class)
            .withPropertyValues(
                    "hippocampus.materials.processing.chunk.chunking-version=CHUNKER_V1",
                    "hippocampus.materials.processing.chunk.target-token-count=800",
                    "hippocampus.materials.processing.chunk.hard-token-count=1000",
                    "hippocampus.materials.processing.chunk.overlap-token-count=100",
                    "hippocampus.materials.processing.chunk.persistence-batch-size=100",
                    "hippocampus.materials.processing.pdf.structure.max-outline-items=100",
                    "hippocampus.materials.processing.pdf.structure.max-outline-depth=16",
                    "hippocampus.materials.processing.pdf.structure.max-text-positions-per-page=100",
                    "hippocampus.materials.processing.pdf.structure.max-layout-lines-per-page=100",
                    "hippocampus.materials.processing.pdf.structure.max-candidates-per-document=100",
                    "hippocampus.materials.processing.pdf.structure.max-nodes-per-document=100");

    @Test
    void registersTransactionalBoundariesAndExactlyOneChunkHandler() {
        runner.run(context -> {
            assertThat(context).hasNotFailed()
                    .hasSingleBean(PersistChunkBatch.class)
                    .hasSingleBean(FinalizeChunking.class)
                    .hasSingleBean(ChunkMaterialText.class)
                    .hasSingleBean(ProcessingStageHandler.class);
            assertThat(context.getBean(ProcessingStageHandler.class)).isInstanceOf(ChunkMaterialStageHandler.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class Prerequisites {
        @Bean JdbcClient jdbcClient() { return mock(JdbcClient.class); }
        @Bean PlatformTransactionManager transactionManager() { return mock(PlatformTransactionManager.class); }
        @Bean ObjectMapper objectMapper() { return mock(ObjectMapper.class); }
        @Bean PdfExtractionProperties pdfExtractionProperties() {
            PdfExtractionProperties properties = mock(PdfExtractionProperties.class);
            when(properties.pageBatchSize()).thenReturn(20);
            return properties;
        }

    }
}
