package com.hippocampus.materials.infrastructure.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.materials.application.CreateUserSelectedMaterialTopicLink;
import com.hippocampus.materials.infrastructure.persistence.JdbcMaterialTopicLinkRepository;
import com.hippocampus.materials.port.MaterialTopicLinkRepository;

@AutoConfiguration
@ConditionalOnBean({CurrentUser.class, JdbcClient.class})
public class MaterialTopicLinkConfiguration {

    @Bean
    MaterialTopicLinkRepository materialTopicLinkRepository(JdbcClient jdbcClient) {
        return new JdbcMaterialTopicLinkRepository(jdbcClient);
    }

    @Bean
    CreateUserSelectedMaterialTopicLink createUserSelectedMaterialTopicLink(
            CurrentUser currentUser, MaterialTopicLinkRepository links) {
        return new CreateUserSelectedMaterialTopicLink(currentUser, links);
    }
}
