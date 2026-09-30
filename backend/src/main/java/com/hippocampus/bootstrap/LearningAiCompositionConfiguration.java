package com.hippocampus.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.hippocampus.ai.application.AiExecutionOrchestrator;
import com.hippocampus.ai.infrastructure.config.AiExecutionOrchestratorConfiguration;
import com.hippocampus.ai.infrastructure.learning.AiTaskExecutionPolicy;
import com.hippocampus.learning.port.ActivityAiTaskPort;
import com.hippocampus.learning.port.ActivityEvidencePort;
import com.hippocampus.rag.application.BuildEvidencePackage;
import com.hippocampus.rag.application.BuildRetrievalScope;
import com.hippocampus.rag.application.MaterializeEvidenceSourceReferences;
import com.hippocampus.rag.port.ActiveIndexGenerationRepository;
import com.hippocampus.rag.port.EmbeddingPort;
import com.hippocampus.rag.port.LexicalSearchRepository;
import com.hippocampus.rag.port.VectorSearchRepository;

@AutoConfiguration(
        after = AiExecutionOrchestratorConfiguration.class,
        afterName = {
                "com.hippocampus.rag.infrastructure.config.RetrievalScopeConfiguration",
                "com.hippocampus.rag.infrastructure.config.LexicalSearchConfiguration",
                "com.hippocampus.rag.infrastructure.config.VectorSearchConfiguration",
                "com.hippocampus.rag.infrastructure.config.EvidencePackageConfiguration"
        },
        beforeName = "com.hippocampus.learning.infrastructure.config.StudyMissionApplicationConfiguration")
@ConditionalOnBean({AiExecutionOrchestrator.class, AiTaskExecutionPolicy.class})
@EnableConfigurationProperties(ActivityEvidenceRetrievalProperties.class)
public class LearningAiCompositionConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ActivityEvidenceRetrievalOptions activityEvidenceRetrievalOptions(
            ActivityEvidenceRetrievalProperties properties) {
        return properties.toOptions();
    }

    @Bean
    @ConditionalOnMissingBean(ActivityEvidencePort.class)
    @ConditionalOnBean({
            BuildRetrievalScope.class,
            LexicalSearchRepository.class,
            VectorSearchRepository.class,
            ActiveIndexGenerationRepository.class,
            BuildEvidencePackage.class,
            MaterializeEvidenceSourceReferences.class,
            ActivityEvidenceRetrievalOptions.class
    })
    ActivityEvidencePort activityEvidencePort(
            BuildRetrievalScope buildScope,
            LexicalSearchRepository lexicalSearch,
            VectorSearchRepository vectorSearch,
            ActiveIndexGenerationRepository generations,
            ObjectProvider<EmbeddingPort> embeddingPort,
            BuildEvidencePackage buildEvidencePackage,
            MaterializeEvidenceSourceReferences materializeSourceReferences,
            ActivityEvidenceRetrievalOptions options) {
        return new LearningActivityEvidenceAdapter(
                buildScope,
                lexicalSearch,
                vectorSearch,
                generations,
                java.util.Optional.ofNullable(embeddingPort.getIfAvailable()),
                buildEvidencePackage,
                materializeSourceReferences,
                options);
    }

    @Bean
    @ConditionalOnMissingBean(ActivityAiTaskPort.class)
    ActivityAiTaskPort activityAiTaskPort(
            AiExecutionOrchestrator orchestrator,
            AiTaskExecutionPolicy executionPolicy,
            ObjectMapper objectMapper) {
        return new LearningActivityAiTaskAdapter(orchestrator, executionPolicy, objectMapper);
    }
}
