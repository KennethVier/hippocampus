package com.hippocampus.ai.infrastructure.persistence;

import java.sql.Types;
import java.util.Objects;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.hippocampus.ai.application.diagnostics.AiDiagnosticsPersistence;
import com.hippocampus.ai.application.diagnostics.AiRequestDiagnostic;
import com.hippocampus.ai.application.diagnostics.ProviderUsageDiagnostic;

public final class JdbcAiDiagnosticsPersistence implements AiDiagnosticsPersistence {

    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;

    public JdbcAiDiagnosticsPersistence(
            JdbcClient jdbc,
            PlatformTransactionManager transactionManager) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
        this.transactions = new TransactionTemplate(Objects.requireNonNull(
                transactionManager, "transactionManager must not be null"));
    }

    @Override
    public void record(AiRequestDiagnostic request, ProviderUsageDiagnostic usage) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(usage, "usage must not be null");
        transactions.executeWithoutResult(ignored -> {
            insertRequest(request);
            insertUsage(usage);
        });
    }

    private void insertRequest(AiRequestDiagnostic diagnostic) {
        jdbc.sql("""
                INSERT INTO ai_request_records (
                    id, user_id, task_type, prompt_id, prompt_version, provider, model,
                    status, grounding_mode, input_token_count, output_token_count,
                    latency_ms, retry_count, error_code, created_at
                ) VALUES (
                    :id, :userId, :taskType, :promptId, :promptVersion, :provider, :model,
                    :status, :groundingMode, :inputTokens, :outputTokens,
                    :latencyMs, :retryCount, :errorCode, :createdAt
                )
                """)
                .param("id", diagnostic.id())
                .param("userId", diagnostic.userId(), Types.OTHER)
                .param("taskType", diagnostic.taskType())
                .param("promptId", diagnostic.promptId())
                .param("promptVersion", diagnostic.promptVersion())
                .param("provider", diagnostic.provider())
                .param("model", diagnostic.model())
                .param("status", diagnostic.status())
                .param("groundingMode", diagnostic.groundingMode(), Types.VARCHAR)
                .param("inputTokens", diagnostic.inputTokenCount(), Types.INTEGER)
                .param("outputTokens", diagnostic.outputTokenCount(), Types.INTEGER)
                .param("latencyMs", diagnostic.latencyMs(), Types.BIGINT)
                .param("retryCount", diagnostic.retryCount())
                .param("errorCode", diagnostic.errorCode(), Types.VARCHAR)
                .param("createdAt", diagnostic.createdAt())
                .update();
    }

    private void insertUsage(ProviderUsageDiagnostic diagnostic) {
        jdbc.sql("""
                INSERT INTO provider_usage_records (
                    id, provider, model, user_id, task_type, request_count,
                    input_tokens, output_tokens, estimated_cost, occurred_at
                ) VALUES (
                    :id, :provider, :model, :userId, :taskType, :requestCount,
                    :inputTokens, :outputTokens, :estimatedCost, :occurredAt
                )
                """)
                .param("id", diagnostic.id())
                .param("provider", diagnostic.provider())
                .param("model", diagnostic.model())
                .param("userId", diagnostic.userId(), Types.OTHER)
                .param("taskType", diagnostic.taskType())
                .param("requestCount", diagnostic.requestCount())
                .param("inputTokens", diagnostic.inputTokens(), Types.BIGINT)
                .param("outputTokens", diagnostic.outputTokens(), Types.BIGINT)
                .param("estimatedCost", diagnostic.estimatedCost(), Types.NUMERIC)
                .param("occurredAt", diagnostic.occurredAt())
                .update();
    }
}
