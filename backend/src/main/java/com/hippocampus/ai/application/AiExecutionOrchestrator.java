package com.hippocampus.ai.application;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

import com.hippocampus.ai.application.prompt.PromptContext;
import com.hippocampus.ai.application.prompt.PromptContextBuilder;
import com.hippocampus.ai.application.prompt.PromptId;
import com.hippocampus.ai.application.prompt.PromptTokenBudget;
import com.hippocampus.ai.application.provider.ProviderExecutionRequest;
import com.hippocampus.ai.application.provider.ProviderExecutionResult;
import com.hippocampus.ai.application.request.AiRequestManager;
import com.hippocampus.ai.application.request.AiRequestPriority;
import com.hippocampus.ai.application.request.AiRequestSubmission;
import com.hippocampus.ai.application.routing.ProviderRoute;
import com.hippocampus.ai.application.routing.ProviderRouter;
import com.hippocampus.ai.application.routing.ProviderRoutingCandidate;
import com.hippocampus.ai.application.routing.ProviderRoutingPreference;
import com.hippocampus.ai.application.validation.AiOutputValidator;
import com.hippocampus.ai.application.validation.AiSchemaValidationException;
import com.hippocampus.ai.application.validation.AiSourceReferenceValidator;
import com.hippocampus.ai.domain.AiTaskRequest;
import com.hippocampus.ai.domain.AiTaskType;
import com.hippocampus.ai.domain.StructuredOutputRepairInput;
import com.hippocampus.ai.domain.ValidatedAiResult;

/**
 * Provider-independent execution flow for complete, machine-consumed AI responses.
 * Provider transport retry remains owned by {@link AiRequestManager}.
 */
public final class AiExecutionOrchestrator {

    private final PromptContextBuilder promptContextBuilder;
    private final ProviderRouter providerRouter;
    private final AiRequestManager requestManager;
    private final AiOutputValidator outputValidator;
    private final AiSourceReferenceValidator sourceReferenceValidator;

    public AiExecutionOrchestrator(
            PromptContextBuilder promptContextBuilder,
            ProviderRouter providerRouter,
            AiRequestManager requestManager,
            AiOutputValidator outputValidator,
            AiSourceReferenceValidator sourceReferenceValidator) {
        this.promptContextBuilder = Objects.requireNonNull(
                promptContextBuilder, "promptContextBuilder must not be null");
        this.providerRouter = Objects.requireNonNull(providerRouter, "providerRouter must not be null");
        this.requestManager = Objects.requireNonNull(requestManager, "requestManager must not be null");
        this.outputValidator = Objects.requireNonNull(outputValidator, "outputValidator must not be null");
        this.sourceReferenceValidator = Objects.requireNonNull(
                sourceReferenceValidator, "sourceReferenceValidator must not be null");
    }

    public CompletableFuture<ValidatedAiResult<?>> execute(
            AiTaskRequest<?> request,
            UUID authenticatedUserId,
            AiRequestPriority priority,
            PromptTokenBudget tokenBudget,
            List<ProviderRoutingCandidate> routingCandidates,
            ProviderRoutingPreference routingPreference) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(authenticatedUserId, "authenticatedUserId must not be null");
        Objects.requireNonNull(priority, "priority must not be null");
        Objects.requireNonNull(tokenBudget, "tokenBudget must not be null");
        Objects.requireNonNull(routingCandidates, "routingCandidates must not be null");
        Objects.requireNonNull(routingPreference, "routingPreference must not be null");

        PromptContext originalPrompt = promptContextBuilder.build(request, tokenBudget);
        ProviderRoute.Target primary = providerRouter
                .route(request, routingCandidates, routingPreference)
                .primary();
        ProviderExecutionRequest providerRequest = providerRequest(request, originalPrompt, primary);

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

        CompletableFuture<ProviderExecutionResult> initial = requestManager.execute(
                new AiRequestSubmission(authenticatedUserId, providerRequest), priority);
        track(activeExecution, outcome, initial);
        initial.whenComplete((providerResult, failure) -> {
            if (failure != null) {
                completeFailure(outcome, failure);
                return;
            }
            if (outcome.isCancelled()) {
                return;
            }
            try {
                outcome.complete(validate(providerResult, request, originalPrompt));
            } catch (AiSchemaValidationException schemaFailure) {
                if (request.taskType() == AiTaskType.STRUCTURED_OUTPUT_REPAIR) {
                    outcome.completeExceptionally(schemaFailure);
                    return;
                }
                try {
                    startRepair(
                            request,
                            originalPrompt,
                            primary,
                            providerResult.rawContent(),
                            authenticatedUserId,
                            priority,
                            tokenBudget,
                            activeExecution,
                            outcome);
                } catch (RuntimeException repairStartFailure) {
                    outcome.completeExceptionally(repairStartFailure);
                }
            } catch (RuntimeException validationFailure) {
                outcome.completeExceptionally(validationFailure);
            }
        });
        return outcome;
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
        CompletableFuture<ProviderExecutionResult> repair = requestManager.execute(
                new AiRequestSubmission(authenticatedUserId, providerRequest), priority);
        track(activeExecution, outcome, repair);
        repair.whenComplete((providerResult, failure) -> {
            if (failure != null) {
                completeFailure(outcome, failure);
                return;
            }
            if (outcome.isCancelled()) {
                return;
            }
            try {
                // Ground repaired output against the original request and the sources actually
                // included in the original generation prompt, never the repair prompt.
                outcome.complete(validate(providerResult, originalRequest, originalPrompt));
            } catch (RuntimeException validationFailure) {
                outcome.completeExceptionally(validationFailure);
            }
        });
    }

    private ValidatedAiResult<?> validate(
            ProviderExecutionResult providerResult,
            AiTaskRequest<?> originalRequest,
            PromptContext originalPrompt) {
        ValidatedAiResult<?> schemaValidated = outputValidator.validate(
                providerResult, originalRequest.outputContract());
        return sourceReferenceValidator.validate(schemaValidated, originalRequest, originalPrompt);
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
        Throwable normalized = failure instanceof CompletionException completion
                ? completion.getCause()
                : failure;
        if (normalized instanceof CancellationException) {
            outcome.cancel(false);
        } else {
            outcome.completeExceptionally(normalized);
        }
    }
}
