package com.hippocampus.rag.infrastructure.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.hippocampus.rag.infrastructure.persistence.JdbcVectorSearchRepository;
import com.hippocampus.rag.port.VectorSearchRepository;

import tools.jackson.databind.ObjectMapper;

@AutoConfiguration(after = JdbcClientAutoConfiguration.class)
@ConditionalOnBean(JdbcClient.class)
public class VectorSearchConfiguration {

    @Bean
    VectorSearchRepository vectorSearchRepository(JdbcClient jdbc, ObjectMapper objectMapper) {
        return new JdbcVectorSearchRepository(jdbc, objectMapper);
    }
}
