package com.hippocampus.ai.infrastructure.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;

import com.hippocampus.ai.application.diagnostics.AiDiagnosticsPersistence;
import com.hippocampus.ai.infrastructure.persistence.JdbcAiDiagnosticsPersistence;

@AutoConfiguration(afterName = {
        "org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration",
        "org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration"
})
@ConditionalOnBean({JdbcClient.class, PlatformTransactionManager.class})
public class AiDiagnosticsPersistenceConfiguration {

    @Bean
    AiDiagnosticsPersistence aiDiagnosticsPersistence(
            JdbcClient jdbc,
            PlatformTransactionManager transactionManager) {
        return new JdbcAiDiagnosticsPersistence(jdbc, transactionManager);
    }
}
