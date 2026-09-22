package com.hippocampus.ai.application.diagnostics;

public interface AiDiagnosticsPersistence {

    void record(AiRequestDiagnostic request, ProviderUsageDiagnostic usage);
}
