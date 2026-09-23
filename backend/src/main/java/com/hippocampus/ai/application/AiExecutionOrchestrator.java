package com.hippocampus.ai.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

import com.hippocampus.ai.port.AiDiagnosticsPersistence;
import com.hippocampus.ai.port.AiRequestDiagnostic;
import com.hippocampus.ai.port.ProviderUsageDiagnostic;
import com.hippocampus.ai.application.prompt.PromptContext;
import com.hippocampus.ai.application.prompt.PromptContextBuilder;
import com.hippocampus.ai.application.prompt.PromptId;
import com.hippocampus.ai.application.prompt.PromptTokenBudget;
import com.hippocampus.ai.application.provider.ProviderExecutionCancellationException;
import com.hippocampus.ai.application.provider.ProviderExecutionException;
import com.hippocampus.ai.application.provider.ProviderExecutionFailure;
import com.hippocampus.ai.application.provider.ProviderExecutionRequest;
import com.hippocampus.ai.application.provider.ProviderExecutionResult;
import com.hippocampus.ai.application.provider.ProviderFailureType;
import com.hippocampus.ai.application.request.AiRequestManager;
import com.hippocampus.ai.application.request.AiRequestPriority;
import com.hippocampus.ai.application.request.AiRequestSubmission;
import com.hippocampus.ai.application.request.AiRequestTelemetry;
import com.hippocampus.ai.application.routing.ProviderRoute;
import com.hippocampus.ai.application.routing.ProviderRouter;
import com.hippocampus.ai.application.routing.ProviderRoutingCandidate;
import com.hippocampus.ai.application.routing.ProviderRoutingPreference;
import com.hippocampus.ai.application.validation.AiOutputValidator;
import com.hippocampus.ai.application.validation.AiSchemaValidationException;
import com.hippocampus.ai.application.validation.AiSourceReferenceValidator;
import com.hippocampus.shared.application.error.ApplicationException;
import com.hippocampus.ai.domain.AiTaskRequest;
import com.hippocampus.ai.domain.AiTaskType;
import com.hippocampus.ai.domain.StructuredOutputRepairInput;
import com.hippocampus.ai.domain.ValidatedAiResult;
import com.hippocampus.identity.port.CurrentUser;

/**
 * Provider-independent execution flow for complete, machine-consumed AI responses.
 * Provider transport retry remains owned by {@link AiRequestManager}.
 */
public final class AiExecutionOrchestrator {

    private final PromptContextBuilder promptContextBuilder;
    private final CurrentUser currentUser;
    private final ProviderRouter providerRouter;
    private final AiRequestManager requestManager;
    private final AiOutputValidator outputValidator;
    private final AiSourceReferenceValidator sourceReferenceValidator;
    private final AiDiagnosticsPersistence diagnosticsPersistence;
    private final AiRequestTelemetry telemetry;
    private final Clock clock;

    public AiExecutionOrchestrator(
            PromptContextBuilder promptContextBuilder,
            CurrentUser currentUser,
            ProviderRouter providerRouter,
            AiRequestManager requestManager,
            AiOutputValidator outputValidator,
            AiSourceReferenceValidator sourceReferenceValidator,
            AiDiagnosticsPersistence diagnosticsPersistence,
            AiRequestTelemetry telemetry) {
        this(promptContextBuilder, currentUser, providerRouter, requestManager, outputValidator,
                sourceReferenceValidator, diagnosticsPersistence, telemetry, Clock.systemUTC());
    }

    AiExecutionOrchestrator(
            PromptContextBuilder promptContextBuilder,
            CurrentUser currentUser,
            ProviderRouter providerRouter,
            AiRequestManager requestManager,
            AiOutputValidator outputValidator,
            AiSourceReferenceValidator sourceReferenceValidator,
            AiDiagnosticsPersistence diagnosticsPersistence,
            AiRequestTelemetry telemetry,
            Clock clock) {
        this.promptContextBuilder = Objects.requireNonNull(
                promptContextBuilder, "promptContextBuilder must not be null");
        this.currentUser = Objects.requireNonNull(currentUser, "currentUser must not be null");
        this.providerRouter = Objects.requireNonNull(providerRouter, "providerRouter must not be null");
        this.requestManager = Objects.requireNonNull(requestManager, "requestManager must not be null");
        this.outputValidator = Objects.requireNonNull(outputValidator, "outputValidator must not be null");
        this.sourceReferenceValidator = Objects.requireNonNull(
                sourceReferenceValidator, "sourceReferenceValidator must not be null");
        this.diagnosticsPersistence = Objects.requireNonNull(
                diagnosticsPersistence, "diagnosticsPersistence must not be null");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public CompletableFuture<ValidatedAiResult<?>> execute(
            AiTaskRequest<?> request,
            AiRequestPriority priority,
            PromptTokenBudget tokenBudget,
            List<ProviderRoutingCandidate> routingCandidates,
            ProviderRoutingPreference routingPreference) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(priority, "priority must not be null");
        Objects.requireNonNull(tokenBudget, "tokenBudget must not be null");
        Objects.requireNonNull(routingCandidates, "routingCandidates must not be null");
        Objects.requireNonNull(routingPreference, "routingPreference must not be null");

        UUID authenticatedUserId = currentUser.authenticatedUser().userId();
        PromptContext originalPrompt = promptContextBuilder.build(request, tokenBudget);
        ProviderRoute route = providerRouter.route(request, routingCandidates, routingPreference);

        CompletableFuture<ValidatedAiResult<?>> outcome = new CompletableFuture<>();
        AtomicReference<CompletableFuture<?>> activeExecution = new AtomicReference<>();
        outcome.whenComplete((ignored, failure) -> {
            if (outcome.isCancelled()) {
                CompletableFuture<?> active = activeExecution.get();
                if (active != null) {
                    active.cancel(true);
                }
            }
        });

        startGeneration(
                request,
                originalPrompt,
                route.primary(),
                route.fallback(),
                authenticatedUserId,
                priority,
                tokenBudget,
                activeExecution,
                outcome);
        return outcome;
    }

    private void startGeneration(
            AiTaskRequest<?> request,
            PromptContext originalPrompt,
            ProviderRoute.Target target,
            Optional<ProviderRoute.Target> fallback,
            UUID authenticatedUserId,
            AiRequestPriority priority,
            PromptTokenBudget tokenBudget,
            AtomicReference<CompletableFuture<?>> activeExecution,
            CompletableFuture<ValidatedAiResult<?>> outcome) {
        if (outcome.isCancelled()) {
            return;
        }

        ProviderExecutionRequest providerRequest = providerRequest(request, originalPrompt, target);
        long startedNanos = System.nanoTime();
        CompletableFuture<ProviderExecutionResult> generation = requestManager.execute(
                new AiRequestSubmission(authenticatedUserId, providerRequest), priority);
        track(activeExecution, outcome, generation);
        generation.whenComplete((providerResult, failure) -> {
            if (failure != null) {
                Throwable normalized = normalize(failure);
                try {
                    recordFailure(
                            authenticatedUserId, request, originalPrompt, target,
                            null, failure, elapsedSince(startedNanos));
                } catch (RuntimeException diagnosticsFailure) {
                    completeFailure(outcome, diagnosticsFailure);
                    return;
                }
                if (!outcome.isCancelled()
                        && fallback.isPresent()
                        && isFallbackEligible(normalized)) {
                    ProviderRoute.Target fallbackTarget = fallback.orElseThrow();
                    telemetry.fallback(
                            target.providerId(), fallbackTarget.providerId(), request.taskType());
                    startGeneration(
                            request,
                            originalPrompt,
                            fallbackTarget,
                            Optional.empty(),
                            authenticatedUserId,
                            priority,
                            tokenBudget,
                            activeExecution,
                            outcome);
                } else {
                    completeFailure(outcome, normalized);
                }
                return;
            }
            if (outcome.isCancelled()) {
                return;
            }
            ValidatedAiResult<?> validated;
            try {
                validated = validate(authenticatedUserId, providerResult, request, originalPrompt);
            } catch (AiSchemaValidationException schemaFailure) {
                try {
                    recordFailure(
                            authenticatedUserId, request, originalPrompt, target,
                            providerResult, schemaFailure, providerResult.latency());
                } catch (RuntimeException diagnosticsFailure) {
                    outcome.completeExceptionally(diagnosticsFailure);
                    return;
                }
                if (request.taskType() == AiTaskType.STRUCTURED_OUTPUT_REPAIR) {
                    outcome.completeExceptionally(schemaFailure);
                    return;
                }
                try {
                    startRepair(
                            request,
                            originalPrompt,
                            target,
                            providerResult.rawContent(),
                            authenticatedUserId,
                            priority,
                            tokenBudget,
                            activeExecution,
                            outcome);
                } catch (RuntimeException repairStartFailure) {
                    outcome.completeExceptionally(repairStartFailure);
                }
                return;
            } catch (RuntimeException validationFailure) {
                try {
                    recordFailure(
                            authenticatedUserId, request, originalPrompt, target,
                            providerResult, validationFailure, providerResult.latency());
                } catch (RuntimeException diagnosticsFailure) {
                    outcome.completeExceptionally(diagnosticsFailure);
                    return;
                }
                outcome.completeExceptionally(validationFailure);
                return;
            }
            try {
                recordSuccess(authenticatedUserId, request, originalPrompt, providerResult);
                outcome.complete(validated);
            } catch (RuntimeException diagnosticsFailure) {
                outcome.completeExceptionally(diagnosticsFailure);
            }
        });
    }

    private void startRepair(
            AiTaskRequest<?> originalRequest,
            PromptContext originalPrompt,
            ProviderRoute.Target originalTarget,
            String malformedOutput,
            UUID authenticatedUserId,
            AiRequestPriority priority,
            PromptTokenBudget tokenBudget,
            AtomicReference<CompletableFuture<?>> activeExecution,
            CompletableFuture<ValidatedAiResult<?>> outcome) {
        if (outcome.isCancelled()) {
            return;
        }

        AiTaskRequest<StructuredOutputRepairInput> repairRequest = new AiTaskRequest<>(
                AiTaskType.STRUCTURED_OUTPUT_REPAIR,
                PromptId.STRUCTURED_OUTPUT_REPAIR_V1.name(),
                originalRequest.learnerContext(),
                new StructuredOutputRepairInput(malformedOutput),
                originalRequest.evidencePackage(),
                originalRequest.groundingMode(),
                originalRequest.outputContract());
        PromptContext repairPrompt = promptContextBuilder.build(repairRequest, tokenBudget);
        ProviderExecutionRequest providerRequest = providerRequest(repairRequest, repairPrompt, originalTarget);

        if (outcome.isCancelled()) {
            return;
        }
        long startedNanos = System.nanoTime();
        CompletableFuture<ProviderExecutionResult> repair = requestManager.execute(
                new AiRequestSubmission(authenticatedUserId, providerRequest), priority);
        track(activeExecution, outcome, repair);
        repair.whenComplete((providerResult, failure) -> {
            if (failure != null) {
                Throwable normalized = normalize(failure);
                try {
                    recordFailure(
                            authenticatedUserId, repairRequest, repairPrompt, originalTarget,
                            null, failure, elapsedSince(startedNanos));
                    completeFailure(outcome, normalized);
                } catch (RuntimeException diagnosticsFailure) {
                    completeFailure(outcome, diagnosticsFailure);
                }
                return;
            }
            if (outcome.isCancelled()) {
                return;
            }
            ValidatedAiResult<?> validated;
            try {
                // Ground repaired output against the original request and the sources actually
                // included in the original generation prompt, never the repair prompt.
                validated = validate(
                        authenticatedUserId, providerResult, originalRequest, originalPrompt);
            } catch (RuntimeException validationFailure) {
                try {
                    recordFailure(
                            authenticatedUserId, repairRequest, repairPrompt, originalTarget,
                            providerResult, validationFailure, providerResult.latency());
                } catch (RuntimeException diagnosticsFailure) {
                    outcome.completeExceptionally(diagnosticsFailure);
                    return;
                }
                outcome.completeExceptionally(validationFailure);
                return;
            }
            try {
                recordSuccess(authenticatedUserId, repairRequest, repairPrompt, providerResult);
                outcome.complete(validated);
            } catch (RuntimeException diagnosticsFailure) {
                outcome.completeExceptionally(diagnosticsFailure);
            }
        });
    }

    private void recordSuccess(
            UUID userId,
            AiTaskRequest<?> request,
            PromptContext prompt,
            ProviderExecutionResult result) {
        record(userId, request, prompt, result.providerId().name(), result.modelId(),
                "SUCCESS", null, result, result.latency(), result.retryCount(),
                result.providerInvocationCount());
    }

    private void recordFailure(
            UUID userId,
            AiTaskRequest<?> request,
            PromptContext prompt,
            ProviderRoute.Target target,
            ProviderExecutionResult result,
            Throwable failure,
            Duration latency) {
        String status = failure instanceof CancellationException ? "CANCELLED" : "FAILED";
        record(userId, request, prompt, target.providerId().name(), target.modelId(),
                status, errorCode(failure), result, latency,
                result == null ? retryCount(failure) : result.retryCount(),
                result == null ? providerInvocationCount(failure) : result.providerInvocationCount());
    }

    private void record(
            UUID userId,
            AiTaskRequest<?> request,
            PromptContext prompt,
            String provider,
            String model,
            String status,
            String errorCode,
            ProviderExecutionResult result,
            Duration latency,
            int retryCount,
            int providerInvocationCount) {
        Instant occurredAt = clock.instant();
        Integer inputTokens = result == null
                ? null
                : result.usage().inputTokens().orElse(null);
        Integer outputTokens = result == null
                ? null
                : result.usage().outputTokens().orElse(null);
        AiRequestDiagnostic requestDiagnostic = new AiRequestDiagnostic(
                UUID.randomUUID(),
                userId,
                request.taskType().name(),
                prompt.taskPromptId().name(),
                Integer.toString(prompt.taskPromptId().version()),
                provider,
                result == null ? model : result.modelId(),
                status,
                request.groundingMode().name(),
                inputTokens,
                outputTokens,
                Math.max(0L, latency.toMillis()),
                retryCount,
                errorCode,
                occurredAt);
        if (providerInvocationCount > 0) {
            ProviderUsageDiagnostic usageDiagnostic = new ProviderUsageDiagnostic(
                    UUID.randomUUID(), provider, result == null ? model : result.modelId(), userId,
                    request.taskType().name(), providerInvocationCount,
                    inputTokens == null ? null : inputTokens.longValue(),
                    outputTokens == null ? null : outputTokens.longValue(), null, occurredAt);
            diagnosticsPersistence.record(requestDiagnostic, usageDiagnostic);
        } else {
            diagnosticsPersistence.record(requestDiagnostic);
        }
    }

    private static String errorCode(Throwable failure) {
        Throwable normalized = normalize(failure);
        if (normalized instanceof CancellationException) {
            return null;
        }
        if (normalized instanceof ProviderExecutionException providerFailure) {
            return providerFailure.failureType().name();
        }
        if (normalized instanceof ApplicationException applicationFailure) {
            return applicationFailure.errorCode().value();
        }
        return "AI_EXECUTION_FAILURE";
    }

    private static int retryCount(Throwable failure) {
        Throwable normalized = normalize(failure);
        if (failure instanceof ProviderExecutionFailure executionFailure) {
            return executionFailure.retryCount();
        }
        return normalized instanceof ProviderExecutionException providerFailure
                ? providerFailure.retryCount() : 0;
    }

    private static int providerInvocationCount(Throwable failure) {
        if (failure instanceof ProviderExecutionCancellationException cancellation) {
            return cancellation.providerInvocationCount();
        }
        if (failure instanceof ProviderExecutionFailure executionFailure) {
            return executionFailure.providerInvocationCount();
        }
        Throwable normalized = normalize(failure);
        return normalized instanceof ProviderExecutionException providerFailure
                ? providerFailure.providerInvocationCount() : 0;
    }

    private static Duration elapsedSince(long startedNanos) {
        return Duration.ofNanos(Math.max(0L, System.nanoTime() - startedNanos));
    }

    private ValidatedAiResult<?> validate(
            UUID authenticatedUserId,
            ProviderExecutionResult providerResult,
            AiTaskRequest<?> originalRequest,
            PromptContext originalPrompt) {
        ValidatedAiResult<?> schemaValidated = outputValidator.validate(
                providerResult, originalRequest.outputContract());
        return sourceReferenceValidator.validate(
                authenticatedUserId, schemaValidated, originalRequest, originalPrompt);
    }

    private static ProviderExecutionRequest providerRequest(
            AiTaskRequest<?> request,
            PromptContext promptContext,
            ProviderRoute.Target target) {
        return new ProviderExecutionRequest(
                request.taskType(), request.outputContract(), promptContext, target);
    }

    private static void track(
            AtomicReference<CompletableFuture<?>> activeExecution,
            CompletableFuture<ValidatedAiResult<?>> outcome,
            CompletableFuture<?> execution) {
        activeExecution.set(execution);
        if (outcome.isCancelled()) {
            execution.cancel(true);
        }
    }

    private static void completeFailure(
            CompletableFuture<ValidatedAiResult<?>> outcome, Throwable failure) {
        Throwable normalized = normalize(failure);
        if (normalized instanceof CancellationException) {
            outcome.cancel(false);
        } else {
            outcome.completeExceptionally(normalized);
        }
    }

    private static boolean isFallbackEligible(Throwable failure) {
        if (!(failure instanceof ProviderExecutionException providerFailure)) {
            return false;
        }
        return switch (providerFailure.failureType()) {
            case PROVIDER_UNAVAILABLE, RATE_LIMITED, QUOTA_EXHAUSTED, TIMEOUT -> true;
            case INVALID_RESPONSE, AUTHENTICATION_FAILURE, UNSUPPORTED_TASK -> false;
        };
    }

    private static Throwable normalize(Throwable failure) {
        if (failure instanceof ProviderExecutionFailure executionFailure) {
            return executionFailure.getCause();
        }
        return failure instanceof CompletionException completion && completion.getCause() != null
                ? completion.getCause()
                : failure;
    }
}
