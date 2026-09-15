package com.hippocampus.materials.infrastructure.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.materials.application.CreateSourceReferences;
import com.hippocampus.materials.application.ResolveSourceReference;
import com.hippocampus.materials.infrastructure.persistence.JdbcSourceReferenceRepository;
import com.hippocampus.materials.port.SourceReferenceRepository;

@AutoConfiguration(after = JdbcClientAutoConfiguration.class)
@ConditionalOnBean({JdbcClient.class, CurrentUser.class})
public class SourceReferenceConfiguration {
    @Bean
    SourceReferenceRepository sourceReferenceRepository(JdbcClient jdbc) {
        return new JdbcSourceReferenceRepository(jdbc);
    }

    @Bean
    CreateSourceReferences createSourceReferences(CurrentUser currentUser, SourceReferenceRepository references) {
        return new CreateSourceReferences(currentUser, references);
    }

    @Bean
    ResolveSourceReference resolveSourceReference(CurrentUser currentUser, SourceReferenceRepository references) {
        return new ResolveSourceReference(currentUser, references);
    }
}
