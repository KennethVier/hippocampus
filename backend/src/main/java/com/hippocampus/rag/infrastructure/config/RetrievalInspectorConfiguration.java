package com.hippocampus.rag.infrastructure.config;

import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.hippocampus.rag.application.BuildRetrievalScope;
import com.hippocampus.rag.application.HybridCandidateMerger;
import com.hippocampus.rag.application.InspectRetrieval;
import com.hippocampus.rag.application.RetrievalInspectorLimits;
import com.hippocampus.rag.infrastructure.persistence.JdbcActiveIndexGenerationRepository;
import com.hippocampus.rag.port.ActiveIndexGenerationRepository;
import com.hippocampus.rag.port.EmbeddingPort;
import com.hippocampus.rag.port.LexicalSearchRepository;
import com.hippocampus.rag.port.VectorSearchRepository;

@AutoConfiguration(after = {RetrievalScopeConfiguration.class, LexicalSearchConfiguration.class,
        VectorSearchConfiguration.class})
@Profile("local")
@ConditionalOnProperty(prefix = "hippocampus.rag.inspector", name = "enabled", havingValue = "true")
@ConditionalOnBean({JdbcClient.class, BuildRetrievalScope.class, LexicalSearchRepository.class,
        VectorSearchRepository.class})
@EnableConfigurationProperties(RetrievalInspectorProperties.class)
public class RetrievalInspectorConfiguration {

    @Bean
    ActiveIndexGenerationRepository activeIndexGenerationRepository(JdbcClient jdbc) {
        return new JdbcActiveIndexGenerationRepository(jdbc);
    }

    @Bean
    InspectRetrieval inspectRetrieval(BuildRetrievalScope buildScope, LexicalSearchRepository lexicalSearch,
            VectorSearchRepository vectorSearch, ActiveIndexGenerationRepository generations,
            ObjectProvider<EmbeddingPort> embeddingPort, RetrievalInspectorProperties properties) {
        var limits = new RetrievalInspectorLimits(properties.getLexicalLimit(), properties.getVectorLimit(),
                properties.getHybridLimit(), properties.getMaxQueryLength());
        return new InspectRetrieval(buildScope, lexicalSearch, vectorSearch, new HybridCandidateMerger(), generations,
                Optional.ofNullable(embeddingPort.getIfAvailable()), limits);
    }
}
