package com.hippocampus.rag.infrastructure.config;

import java.util.Optional;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;

import com.hippocampus.materials.application.ProcessingStageHandler;
import com.hippocampus.materials.application.ReportProcessingJobProgress;
import com.hippocampus.materials.domain.ProcessingFailure;
import com.hippocampus.materials.port.ProcessingFailureMapping;
import com.hippocampus.rag.application.EmbedMaterialVersion;
import com.hippocampus.rag.application.EmbeddingMaterialStageHandler;
import com.hippocampus.rag.application.PersistEmbeddingBatch;
import com.hippocampus.rag.infrastructure.persistence.JdbcEmbeddingJobRepository;
import com.hippocampus.rag.port.EmbeddingFailureException;
import com.hippocampus.rag.port.EmbeddingJobRepository;
import com.hippocampus.rag.port.EmbeddingPort;

@AutoConfiguration(afterName = {
        "com.hippocampus.materials.infrastructure.config.ProcessingJobDispatchConfiguration",
        "com.hippocampus.materials.infrastructure.config.ProcessingRecoveryConfiguration"
})
@ConditionalOnBean({EmbeddingPort.class, JdbcClient.class, PlatformTransactionManager.class})
@EnableConfigurationProperties(EmbeddingProcessingProperties.class)
public class EmbeddingProcessingConfiguration {
    @Bean
    EmbeddingJobRepository embeddingJobRepository(JdbcClient jdbc) {
        return new JdbcEmbeddingJobRepository(jdbc);
    }

    @Bean
    PersistEmbeddingBatch persistEmbeddingBatch(EmbeddingJobRepository repository) {
        return new PersistEmbeddingBatch(repository);
    }

    @Bean
    EmbedMaterialVersion embedMaterialVersion(
            EmbeddingJobRepository repository,
            EmbeddingPort provider,
            PersistEmbeddingBatch persistence,
            EmbeddingProcessingProperties properties) {
        return new EmbedMaterialVersion(repository, provider, persistence, properties.batchSize());
    }

    @Bean
    @ConditionalOnBean(ReportProcessingJobProgress.class)
    ProcessingStageHandler embeddingMaterialStageHandler(
            EmbedMaterialVersion embedding,
            ReportProcessingJobProgress progress) {
        return new EmbeddingMaterialStageHandler(embedding, progress);
    }

    @Bean
    ProcessingFailureMapping embeddingProcessingFailureMapping() {
        return failure -> {
            if (!(failure instanceof EmbeddingFailureException embedding)) {
                return Optional.empty();
            }
            return Optional.of(switch (embedding.reason()) {
                case PROVIDER_FAILURE -> new ProcessingFailure(
                        ProcessingFailure.Kind.TRANSIENT, "EMBEDDING_PROVIDER_UNAVAILABLE");
                case INVALID_RESPONSE -> new ProcessingFailure(
                        ProcessingFailure.Kind.FATAL, "EMBEDDING_RESPONSE_INVALID");
            });
        };
    }
}
