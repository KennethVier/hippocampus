package com.hippocampus.ai.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.hippocampus.ai.application.diagnostics.AiDiagnosticsPersistence;
import com.hippocampus.ai.application.diagnostics.AiRequestDiagnostic;
import com.hippocampus.ai.application.diagnostics.ProviderUsageDiagnostic;
import com.hippocampus.testing.PostgresIntegrationTestSupport;

class AiDiagnosticsPersistenceIntegrationTests extends PostgresIntegrationTestSupport {

    private static final Instant OCCURRED_AT = Instant.parse("2026-09-23T08:15:30Z");

    @BeforeEach
    void resetDatabase() throws SQLException {
        resetPostgresSchema();
    }

    @Test
    void persistsRequestAndUsageMetadataAtomicallyWithoutPrivateContentColumns() {
        try (var context = startApplicationWithFlyway()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            UUID userId = insertUser(jdbc, "ai-diagnostics@example.test");
            AiDiagnosticsPersistence persistence = context.getBean(AiDiagnosticsPersistence.class);

            persistence.record(
                    request(userId, "SUCCESS", null, 37, 19),
                    usage(userId, 37L, 19L));

            assertThat(jdbc.sql("SELECT count(*) FROM ai_request_records").query(Long.class).single())
                    .isEqualTo(1L);
            assertThat(jdbc.sql("SELECT count(*) FROM provider_usage_records").query(Long.class).single())
                    .isEqualTo(1L);
            RequestRow row = jdbc.sql("""
                    SELECT user_id, task_type, prompt_id, prompt_version, provider, model,
                           status, grounding_mode, input_token_count, output_token_count,
                           latency_ms, retry_count, error_code, created_at
                    FROM ai_request_records
                    """).query((result, rowNumber) -> new RequestRow(
                            result.getObject("user_id", UUID.class),
                            result.getString("task_type"),
                            result.getString("prompt_id"),
                            result.getString("prompt_version"),
                            result.getString("provider"),
                            result.getString("model"),
                            result.getString("status"),
                            result.getString("grounding_mode"),
                            result.getObject("input_token_count", Integer.class),
                            result.getObject("output_token_count", Integer.class),
                            result.getObject("latency_ms", Long.class),
                            result.getInt("retry_count"),
                            result.getString("error_code"),
                            result.getObject("created_at", java.time.OffsetDateTime.class).toInstant()))
                    .single();
            assertThat(row).isEqualTo(new RequestRow(
                    userId, "EXPLANATION", "EXPLANATION_V1", "1", "GEMINI", "gemini-2.5-flash",
                    "SUCCESS", "STRICT_SOURCE", 37, 19, 125L, 0, null, OCCURRED_AT));
            UsageRow usage = jdbc.sql("""
                    SELECT provider, model, user_id, task_type, request_count,
                           input_tokens, output_tokens, estimated_cost, occurred_at
                    FROM provider_usage_records
                    """).query((result, rowNumber) -> new UsageRow(
                            result.getString("provider"),
                            result.getString("model"),
                            result.getObject("user_id", UUID.class),
                            result.getString("task_type"),
                            result.getInt("request_count"),
                            result.getObject("input_tokens", Long.class),
                            result.getObject("output_tokens", Long.class),
                            result.getBigDecimal("estimated_cost"),
                            result.getObject("occurred_at", java.time.OffsetDateTime.class).toInstant()))
                    .single();
            assertThat(usage).isEqualTo(new UsageRow(
                    "GEMINI", "gemini-2.5-flash", userId, "EXPLANATION", 1,
                    37L, 19L, null, OCCURRED_AT));
        }
    }

    @Test
    void acceptsNullableUserAndMissingProviderUsage() {
        try (var context = startApplicationWithFlyway()) {
            AiDiagnosticsPersistence persistence = context.getBean(AiDiagnosticsPersistence.class);
            JdbcClient jdbc = context.getBean(JdbcClient.class);

            persistence.record(
                    request(null, "FAILED", "TIMEOUT", null, null),
                    usage(null, null, null));

            assertThat(jdbc.sql("SELECT user_id FROM ai_request_records")
                    .query(UUID.class).optional()).isEmpty();
            assertThat(jdbc.sql("SELECT input_tokens FROM provider_usage_records")
                    .query(Long.class).optional()).isEmpty();
        }
    }

    @Test
    void rejectsUnknownUserAndRollsBackBothDiagnosticRows() {
        try (var context = startApplicationWithFlyway()) {
            AiDiagnosticsPersistence persistence = context.getBean(AiDiagnosticsPersistence.class);
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            UUID existingUser = insertUser(jdbc, "ai-diagnostics-rollback@example.test");

            assertThatThrownBy(() -> persistence.record(
                    request(UUID.randomUUID(), "SUCCESS", null, 1, 1),
                    usage(existingUser, 1L, 1L)))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> persistence.record(
                    request(existingUser, "SUCCESS", null, 1, 1),
                    usage(UUID.randomUUID(), 1L, 1L)))
                    .isInstanceOf(DataIntegrityViolationException.class);

            assertThat(jdbc.sql("SELECT count(*) FROM ai_request_records").query(Long.class).single())
                    .isZero();
            assertThat(jdbc.sql("SELECT count(*) FROM provider_usage_records").query(Long.class).single())
                    .isZero();
        }
    }

    private static AiRequestDiagnostic request(
            UUID userId, String status, String errorCode, Integer inputTokens, Integer outputTokens) {
        return new AiRequestDiagnostic(
                UUID.randomUUID(), userId, "EXPLANATION", "EXPLANATION_V1", "1",
                "GEMINI", "gemini-2.5-flash", status, "STRICT_SOURCE",
                inputTokens, outputTokens, 125L, 0, errorCode, OCCURRED_AT);
    }

    private static ProviderUsageDiagnostic usage(
            UUID userId, Long inputTokens, Long outputTokens) {
        return new ProviderUsageDiagnostic(
                UUID.randomUUID(), "GEMINI", "gemini-2.5-flash", userId,
                "EXPLANATION", 1, inputTokens, outputTokens, null, OCCURRED_AT);
    }

    private static UUID insertUser(JdbcClient jdbc, String email) {
        UUID userId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO users (id, email, status, created_at, updated_at)
                VALUES (:id, :email, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """).param("id", userId).param("email", email).update();
        return userId;
    }

    private record RequestRow(
            UUID userId,
            String taskType,
            String promptId,
            String promptVersion,
            String provider,
            String model,
            String status,
            String groundingMode,
            Integer inputTokens,
            Integer outputTokens,
            Long latencyMs,
            int retryCount,
            String errorCode,
            Instant createdAt) {}

    private record UsageRow(
            String provider,
            String model,
            UUID userId,
            String taskType,
            int requestCount,
            Long inputTokens,
            Long outputTokens,
            BigDecimal estimatedCost,
            Instant occurredAt) {}
}
