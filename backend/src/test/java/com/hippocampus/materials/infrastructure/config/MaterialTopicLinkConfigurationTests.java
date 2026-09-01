package com.hippocampus.materials.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.hippocampus.identity.domain.AuthenticatedUser;
import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.materials.application.CreateUserSelectedMaterialTopicLink;
import com.hippocampus.materials.port.MaterialTopicLinkRepository;

class MaterialTopicLinkConfigurationTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AopAutoConfiguration.class,
                    JdbcTemplateAutoConfiguration.class,
                    JdbcClientAutoConfiguration.class,
                    TransactionAutoConfiguration.class,
                    MaterialTopicLinkConfiguration.class))
            .withUserConfiguration(RequiredBeansConfiguration.class);

    @Test
    void createsRepositoryAndTransactionProxiedUseCaseAfterJdbcClientAutoConfiguration() {
        runner.run(context -> {
            assertThat(context).hasNotFailed()
                    .hasSingleBean(MaterialTopicLinkRepository.class)
                    .hasSingleBean(CreateUserSelectedMaterialTopicLink.class);
            CreateUserSelectedMaterialTopicLink useCase =
                    context.getBean(CreateUserSelectedMaterialTopicLink.class);
            assertThat(AopUtils.isAopProxy(useCase)).isTrue();
            assertThat(AopUtils.isCglibProxy(useCase)).isTrue();
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class RequiredBeansConfiguration {
        @Bean
        DataSource dataSource() {
            return mock(DataSource.class);
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return mock(PlatformTransactionManager.class);
        }

        @Bean
        CurrentUser currentUser() {
            return () -> new AuthenticatedUser(UUID.randomUUID());
        }
    }
}
