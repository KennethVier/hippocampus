package com.hippocampus.materials.infrastructure.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;

import com.hippocampus.materials.application.ClaimNextProcessingJob;
import com.hippocampus.materials.infrastructure.persistence.JdbcProcessingJobClaimRepository;
import com.hippocampus.materials.port.ProcessingJobClaimRepository;

@AutoConfiguration(afterName = {
        "org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration",
        "org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration"
})
@ConditionalOnBean({JdbcClient.class, PlatformTransactionManager.class})
public class ProcessingJobClaimConfiguration {

    @Bean
    ProcessingJobClaimRepository processingJobClaimRepository(JdbcClient jdbcClient) {
        return new JdbcProcessingJobClaimRepository(jdbcClient);
    }

    @Bean
    ClaimNextProcessingJob claimNextProcessingJob(ProcessingJobClaimRepository jobs) {
        return new ClaimNextProcessingJob(jobs);
    }
}
