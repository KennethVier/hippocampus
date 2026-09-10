package com.hippocampus.materials.infrastructure.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;

import com.hippocampus.materials.application.ChunkMaterialStageHandler;
import com.hippocampus.materials.application.ChunkMaterialText;
import com.hippocampus.materials.application.FinalizeChunking;
import com.hippocampus.materials.application.PersistChunkBatch;
import com.hippocampus.materials.application.ProcessingStageHandler;
import com.hippocampus.materials.application.ReportProcessingJobProgress;
import com.hippocampus.materials.domain.DeterministicChunkTokenCounter;
import com.hippocampus.materials.domain.HierarchyAwareChunkingPolicy;
import com.hippocampus.materials.infrastructure.persistence.JdbcChunkRepository;

import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;

@AutoConfiguration(after = {
        PdfExtractionConfiguration.class,
        PdfStructureInspectionConfiguration.class,
        TextNormalizationConfiguration.class
})
@ConditionalOnBean({JdbcClient.class, PlatformTransactionManager.class, PdfExtractionProperties.class})
@EnableConfigurationProperties({ChunkingProperties.class, PdfStructureInspectionProperties.class})
public class ChunkingConfiguration {
    @Bean
    JdbcChunkRepository chunkRepository(
            JdbcClient jdbc, ObjectMapper mapper, ChunkingProperties properties) {
        return new JdbcChunkRepository(jdbc, mapper, properties.hardTokenCount());
    }

    @Bean
    PersistChunkBatch persistChunkBatch(JdbcChunkRepository repository) {
        return new PersistChunkBatch(repository);
    }

    @Bean
    FinalizeChunking finalizeChunking(JdbcChunkRepository repository) {
        return new FinalizeChunking(repository);
    }

    @Bean
    ChunkMaterialText chunkMaterialText(
            JdbcChunkRepository repository,
            PersistChunkBatch persistence,
            FinalizeChunking finalization,
            PdfExtractionProperties pdf,
            PdfStructureInspectionProperties structure,
            ChunkingProperties chunking) {
        HierarchyAwareChunkingPolicy policy = new HierarchyAwareChunkingPolicy(
                new DeterministicChunkTokenCounter(), chunking.targetTokenCount(),
                chunking.hardTokenCount(), chunking.overlapTokenCount());
        return new ChunkMaterialText(repository, policy, persistence, finalization,
                pdf.pageBatchSize(), chunking.persistenceBatchSize(),
                structure.maxNodesPerDocument(), structure.maxOutlineDepth());
    }

    @Bean
    ProcessingStageHandler chunkMaterialStageHandler(ChunkMaterialText chunking,
            ObjectProvider<ReportProcessingJobProgress> progress) {
        ReportProcessingJobProgress reporter = progress.getIfAvailable();
        return reporter == null ? new ChunkMaterialStageHandler(chunking)
                : new ChunkMaterialStageHandler(chunking, reporter);
    }
}
