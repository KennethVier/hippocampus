package com.hippocampus.rag.infrastructure.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.hippocampus.rag.infrastructure.persistence.JdbcLexicalSearchRepository;
import com.hippocampus.rag.port.LexicalSearchRepository;

import tools.jackson.databind.ObjectMapper;

@AutoConfiguration(after = JdbcClientAutoConfiguration.class)
@ConditionalOnBean(JdbcClient.class)
public class LexicalSearchConfiguration {

    @Bean
    LexicalSearchRepository lexicalSearchRepository(JdbcClient jdbc, ObjectMapper objectMapper) {
        return new JdbcLexicalSearchRepository(jdbc, objectMapper);
    }
}
