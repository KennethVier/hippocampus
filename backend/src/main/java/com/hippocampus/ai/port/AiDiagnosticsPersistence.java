package com.hippocampus.ai.port;

public interface AiDiagnosticsPersistence {

    void record(AiRequestDiagnostic request);

    void record(AiRequestDiagnostic request, ProviderUsageDiagnostic usage);
}
