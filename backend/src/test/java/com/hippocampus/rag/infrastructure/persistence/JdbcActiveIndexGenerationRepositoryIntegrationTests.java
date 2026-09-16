package com.hippocampus.rag.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.hippocampus.testing.PostgresIntegrationTestSupport;

class JdbcActiveIndexGenerationRepositoryIntegrationTests extends PostgresIntegrationTestSupport {
    @BeforeEach void reset() throws SQLException { resetPostgresSchema(); }

    @Test
    void returnsEmptyForNoActiveAndDoesNotFallBackToBuilding() {
        try (var context = startApplicationWithFlyway()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            insert(jdbc, "BUILDING");
            assertThat(new JdbcActiveIndexGenerationRepository(jdbc).findActiveGeneration()).isEmpty();
        }
    }

    @Test
    void returnsSingleActiveGenerationWithModelMetadata() {
        try (var context = startApplicationWithFlyway()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            UUID id = insert(jdbc, "ACTIVE");
            assertThat(new JdbcActiveIndexGenerationRepository(jdbc).findActiveGeneration()).get().satisfies(g -> {
                assertThat(g.id()).isEqualTo(id);
                assertThat(g.model().provider()).isEqualTo("fake");
                assertThat(g.model().dimension()).isEqualTo(2);
            });
        }
    }

    @Test
    void failsClosedForMultipleActiveGenerations() {
        try (var context = startApplicationWithFlyway()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            insert(jdbc, "ACTIVE");
            insert(jdbc, "ACTIVE");
            assertThatThrownBy(() -> new JdbcActiveIndexGenerationRepository(jdbc).findActiveGeneration())
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("ambiguous");
        }
    }

    private static UUID insert(JdbcClient jdbc, String status) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO index_generations(id, embedding_provider, embedding_model, embedding_model_version,
                    embedding_dimension, chunking_version, status, created_at)
                VALUES (?, 'fake', 'medical', 'v1', 2, 'CHUNKER_V1', ?, CURRENT_TIMESTAMP)
                """).params(id, status).update();
        return id;
    }
}
