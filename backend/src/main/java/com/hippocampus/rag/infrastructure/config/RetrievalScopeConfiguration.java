package com.hippocampus.rag.infrastructure.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.rag.application.BuildRetrievalScope;
import com.hippocampus.rag.infrastructure.persistence.JdbcRetrievalScopeSourceRepository;
import com.hippocampus.rag.port.RetrievalScopeSourceRepository;

@AutoConfiguration(after = JdbcClientAutoConfiguration.class)
@ConditionalOnBean({JdbcClient.class, CurrentUser.class})
public class RetrievalScopeConfiguration {
    @Bean
    RetrievalScopeSourceRepository retrievalScopeSourceRepository(JdbcClient jdbc) {
        return new JdbcRetrievalScopeSourceRepository(jdbc);
    }

    @Bean
    BuildRetrievalScope buildRetrievalScope(
            CurrentUser currentUser, RetrievalScopeSourceRepository repository) {
        return new BuildRetrievalScope(currentUser, repository);
    }
}
