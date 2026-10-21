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
            ActivityEvidenceRetrievalOptions options,com.hippocampus.identity.port.CurrentUser currentUser,
            com.hippocampus.learning.port.StudyMissionRepository missions,
            com.hippocampus.learning.port.StudyMissionStartReceiptRepository receipts,
            com.hippocampus.learning.port.InitialStudyMissionSourceAuthority sources) {
        return new LearningActivityEvidenceAdapter(
                buildScope,
                lexicalSearch,
                vectorSearch,
                generations,
                java.util.Optional.ofNullable(embeddingPort.getIfAvailable()),
                buildEvidencePackage,
                materializeSourceReferences,
                options,currentUser,missions,receipts,sources);
    }

    @Bean
    @org.springframework.context.annotation.Lazy
    @ConditionalOnMissingBean
    com.hippocampus.learning.application.StartStudyMissionExperienceUseCase startStudyMissionExperienceUseCase(
            com.hippocampus.identity.port.CurrentUser currentUser,
            com.hippocampus.learning.application.StudyMissionStartTransactions transactions,
            com.hippocampus.learning.application.MaterializeLearningActivityUseCase materialize,
            com.hippocampus.ai.infrastructure.config.AiRequestManagerProperties manager) {
        return new com.hippocampus.learning.application.StartStudyMissionExperienceUseCase(
                currentUser,transactions,materialize,()->withinInitialEnvelope(manager));
    }

    private static boolean withinInitialEnvelope(
            com.hippocampus.ai.infrastructure.config.AiRequestManagerProperties manager) {
        return manager.maximumOutstandingRequestsPerUser()>=1
                && manager.maximumOutstandingRequestsPerUser()<=4
                && withinInitialEnvelope(manager.gemini()) && withinInitialEnvelope(manager.ollamaCloud());
    }

    private static boolean withinInitialEnvelope(
            com.hippocampus.ai.infrastructure.config.AiRequestManagerProperties.ProviderPolicy policy) {
        if(policy==null || policy.requestTimeout()==null || policy.initialRetryDelay()==null
                || policy.maximumRetryDelay()==null) return false;
        java.time.Duration zero=java.time.Duration.ZERO;
        java.time.Duration maximumDelay=java.time.Duration.ofSeconds(2);
        return policy.maximumConcurrency()>=1 && policy.maximumConcurrency()<=2
                && policy.maximumQueuedRequests()>=0 && policy.maximumQueuedRequests()<=32
                && policy.requestTimeout().compareTo(zero)>0
                && policy.requestTimeout().compareTo(java.time.Duration.ofSeconds(45))<=0
                && policy.maximumAttempts()>=1 && policy.maximumAttempts()<=3
                && policy.initialRetryDelay().compareTo(zero)>=0
                && policy.initialRetryDelay().compareTo(policy.maximumRetryDelay())<=0
                && policy.maximumRetryDelay().compareTo(maximumDelay)<=0;
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
