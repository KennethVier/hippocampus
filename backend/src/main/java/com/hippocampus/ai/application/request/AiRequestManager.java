package com.hippocampus.ai.application.request;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

import com.hippocampus.ai.application.provider.AiProviderAdapter;
import com.hippocampus.ai.application.provider.ProviderExecutionCancellationException;
import com.hippocampus.ai.application.provider.ProviderExecutionException;
import com.hippocampus.ai.application.provider.ProviderExecutionFailure;
import com.hippocampus.ai.application.provider.ProviderExecutionRequest;
import com.hippocampus.ai.application.provider.ProviderExecutionResult;
import com.hippocampus.ai.application.provider.ProviderFailureType;
import com.hippocampus.ai.application.provider.ProviderStreamEvent;
import com.hippocampus.ai.application.routing.ProviderId;

/**
 * Application-owned pressure boundary for all external AI provider work.
 * Capacity is released only after the physical provider invocation returns.
 */
public final class AiRequestManager implements AutoCloseable {
    private final Object lock = new Object();
    private final Clock clock;
    private final AiRequestTelemetry telemetry;
    private final ExecutorService workers;
    private final ScheduledExecutorService scheduler;
    private final int maximumOutstandingRequestsPerUser;
    private final EnumMap<ProviderId, ProviderState> providers = new EnumMap<>(ProviderId.class);
    private final Map<UUID, Integer> outstandingRequestsByUser = new java.util.HashMap<>();
    private long sequence;
    private boolean closed;

    public AiRequestManager(
            List<AiProviderAdapter> adapters,
            Map<ProviderId, AiRequestManagerPolicy> policies,
            int maximumOutstandingRequestsPerUser,
            AiRequestTelemetry telemetry) {
        this(adapters, policies, maximumOutstandingRequestsPerUser, telemetry, Clock.systemUTC(),
                Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("ai-provider-", 0).factory()),
                Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("ai-request-scheduler").factory()));
    }

    AiRequestManager(
            List<AiProviderAdapter> adapters,
            Map<ProviderId, AiRequestManagerPolicy> policies,
            int maximumOutstandingRequestsPerUser,
            AiRequestTelemetry telemetry,
            Clock clock,
            ExecutorService workers,
            ScheduledExecutorService scheduler) {
        Objects.requireNonNull(adapters, "adapters must not be null");
        Objects.requireNonNull(policies, "policies must not be null");
        if (maximumOutstandingRequestsPerUser < 1) {
            throw new IllegalArgumentException("maximumOutstandingRequestsPerUser must be positive");
        }
        this.maximumOutstandingRequestsPerUser = maximumOutstandingRequestsPerUser;
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.workers = Objects.requireNonNull(workers, "workers must not be null");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
        for (AiProviderAdapter adapter : adapters) {
            Objects.requireNonNull(adapter, "adapters must not contain null");
            AiRequestManagerPolicy policy = Objects.requireNonNull(
                    policies.get(adapter.providerId()), "policy missing for " + adapter.providerId());
            if (providers.put(adapter.providerId(), new ProviderState(adapter, policy)) != null) {
                throw new IllegalArgumentException("duplicate adapter for " + adapter.providerId());
            }
        }
    }

    public CompletableFuture<ProviderExecutionResult> execute(
            AiRequestSubmission submission,
            AiRequestPriority priority) {
        Objects.requireNonNull(submission, "submission must not be null");
        ProviderExecutionRequest request = submission.providerRequest();
        return submit(submission.userId(), request, priority, adapter -> adapter.execute(request), () -> true);
    }

    public CompletableFuture<Void> stream(
            AiRequestSubmission submission,
            AiRequestPriority priority,
            Consumer<? super ProviderStreamEvent> consumer) {
        Objects.requireNonNull(submission, "submission must not be null");
        ProviderExecutionRequest request = submission.providerRequest();
        Objects.requireNonNull(consumer, "consumer must not be null");
        StreamAttempt streamAttempt = new StreamAttempt();
        return submit(submission.userId(), request, priority, adapter -> {
            adapter.stream(request).consume(event -> {
                streamAttempt.eventDelivered = true;
                consumer.accept(event);
            });
            return null;
        }, () -> !streamAttempt.eventDelivered);
    }

    public AiRequestManagerDiagnostics diagnostics() {
        synchronized (lock) {
            EnumMap<ProviderId, AiRequestManagerDiagnostics.ProviderDiagnostics> snapshots =
                    new EnumMap<>(ProviderId.class);
            Instant now = clock.instant();
            providers.forEach((providerId, state) -> snapshots.put(providerId,
                    new AiRequestManagerDiagnostics.ProviderDiagnostics(
                            state.running,
                            state.queue.size(),
                            state.circuitState.name(),
                            state.cooldownUntil.isAfter(now) ? Optional.of(state.cooldownUntil) : Optional.empty())));
            return new AiRequestManagerDiagnostics(snapshots);
        }
    }

    private <T> CompletableFuture<T> submit(
            UUID userId,
            ProviderExecutionRequest request,
            AiRequestPriority priority,
            ProviderInvocation<T> invocation,
            RetryPermission retryPermission) {
        Objects.requireNonNull(priority, "priority must not be null");
        ProviderState state;
        ManagedRequest<T> managed;
        synchronized (lock) {
            ensureOpen();
            state = providers.get(request.target().providerId());
            if (state == null) {
                return CompletableFuture.failedFuture(failure(request.target().providerId(), ProviderFailureType.UNSUPPORTED_TASK));
            }
            Instant now = clock.instant();
            updateCircuitForTime(state, now);
            if (state.circuitState == CircuitState.OPEN) {
                telemetry.rejected(state.adapter.providerId(), request.taskType(), "circuit_open");
                return CompletableFuture.failedFuture(failure(state.adapter.providerId(), state.openFailureType));
            }
            if (state.cooldownUntil.isAfter(now)) {
                telemetry.rejected(state.adapter.providerId(), request.taskType(), "provider_cooldown");
                return CompletableFuture.failedFuture(new ProviderExecutionException(
                        state.adapter.providerId(),
                        ProviderFailureType.RATE_LIMITED,
                        Optional.of(Duration.between(now, state.cooldownUntil))));
            }
            int userOutstanding = outstandingRequestsByUser.getOrDefault(userId, 0);
            if (userOutstanding >= maximumOutstandingRequestsPerUser) {
                telemetry.rejected(state.adapter.providerId(), request.taskType(), "user_limit");
                return CompletableFuture.failedFuture(
                        failure(state.adapter.providerId(), ProviderFailureType.RATE_LIMITED));
            }
            long providerCapacity = (long) state.policy.maximumConcurrency()
                    + state.policy.maximumQueuedRequests();
            if (state.logicalOutstanding >= providerCapacity) {
                telemetry.rejected(state.adapter.providerId(), request.taskType(), "queue_full");
                return CompletableFuture.failedFuture(
                        failure(state.adapter.providerId(), ProviderFailureType.PROVIDER_UNAVAILABLE));
            }
            boolean canStartImmediately = canStartImmediately(state, now);
            if (state.queue.size() >= state.policy.maximumQueuedRequests() && !canStartImmediately) {
                telemetry.rejected(state.adapter.providerId(), request.taskType(), "queue_full");
                return CompletableFuture.failedFuture(failure(state.adapter.providerId(), ProviderFailureType.PROVIDER_UNAVAILABLE));
            }
            outstandingRequestsByUser.put(userId, userOutstanding + 1);
            state.logicalOutstanding++;
            long deadlineNanos = System.nanoTime() + state.policy.requestTimeout().toNanos();
            managed = new ManagedRequest<>(
                    state,
                    userId,
                    request,
                    priority,
                    sequence++,
                    deadlineNanos,
                    invocation,
                    retryPermission);
            state.logicalRequests.add(managed);
            state.queue.add(managed);
            telemetry.queued(state.adapter.providerId(), request.taskType());
            managed.timeoutTask = scheduler.schedule(
                    () -> timeout(managed), schedulerDelayNanos(state.policy.requestTimeout()), TimeUnit.NANOSECONDS);
            managed.result.whenComplete((ignored, failure) -> terminalCompletion(managed));
            dispatch(state);
        }
        return managed.result;
    }

    private void dispatch(ProviderState state) {
        Instant now = clock.instant();
        updateCircuitForTime(state, now);
        if (state.circuitState == CircuitState.OPEN) {
            scheduleDispatch(state, Duration.between(now, state.circuitOpenUntil));
            return;
        }
        if (state.cooldownUntil.isAfter(now)) {
            scheduleDispatch(state, Duration.between(now, state.cooldownUntil));
            return;
        }
        while (state.running < state.policy.maximumConcurrency() && !state.queue.isEmpty()) {
            if (state.circuitState == CircuitState.HALF_OPEN && state.recoveryProbe != null) {
                return;
            }
            ManagedRequest<?> request = state.queue.poll();
            if (request.result.isDone()) continue;
            if (deadlineExpired(request)) {
                completeTimeout(request);
                continue;
            }
            state.running++;
            request.physicallyRunning = true;
            if (state.circuitState == CircuitState.HALF_OPEN) state.recoveryProbe = request;
            workers.submit(() -> runAttempt(request));
        }
    }

    private <T> void runAttempt(ManagedRequest<T> request) {
        synchronized (lock) {
            request.physicalThread = Thread.currentThread();
            if (request.result.isDone()) {
                request.physicallyRunning = false;
                request.physicalThread = null;
                request.state.running--;
                releaseAdmissions(request);
                releaseProbe(request.state, request);
                dispatch(request.state);
                return;
            }
            request.providerInvocationStarted = true;
            request.providerInvocationCount++;
        }
        T value = null;
        Throwable failure = null;
        try {
            value = request.invocation.invoke(request.state.adapter);
        } catch (Throwable caught) {
            failure = caught;
        } finally {
            physicalAttemptFinished(request, value, failure);
        }
    }

    private <T> void physicalAttemptFinished(ManagedRequest<T> request, T value, Throwable caught) {
        synchronized (lock) {
            ProviderState state = request.state;
            request.physicallyRunning = false;
            request.providerInvocationStarted = false;
            request.physicalThread = null;
            state.running--;

            if (request.result.isDone()) {
                releaseAdmissions(request);
                releaseProbe(state, request);
                dispatch(state);
                return;
            }
            if (caught == null) {
                providerSucceeded(state, request);
                complete(request, value);
                dispatch(state);
                return;
            }
            if (!(caught instanceof ProviderExecutionException providerFailure)) {
                releaseProbe(state, request);
                completeExceptionally(request, caught, "application_failure");
                dispatch(state);
                return;
            }

            boolean retryable = isRetryable(providerFailure.failureType()) && request.retryPermission.allowed();
            providerFailed(state, request, providerFailure);
            if (retryable && request.attempts < state.policy.maximumAttempts() && canRetryWithinDeadline(request, providerFailure)) {
                request.attempts++;
                Duration delay = retryDelay(request, providerFailure);
                telemetry.retrying(state.adapter.providerId(), request.request.taskType(), providerFailure.failureType());
                scheduler.schedule(() -> requeue(request), schedulerDelayNanos(delay), TimeUnit.NANOSECONDS);
            } else {
                completeExceptionally(request, providerFailure, providerFailure.failureType().name().toLowerCase());
            }
            dispatch(state);
        }
    }

    private void providerSucceeded(ProviderState state, ManagedRequest<?> request) {
        if (state.circuitState == CircuitState.OPEN) return;
        if (state.circuitState == CircuitState.HALF_OPEN && state.recoveryProbe != request) return;
        state.consecutiveFailures = 0;
        state.openFailureType = ProviderFailureType.PROVIDER_UNAVAILABLE;
        if (state.circuitState == CircuitState.HALF_OPEN) {
            state.circuitState = CircuitState.CLOSED;
            state.recoveryProbe = null;
            telemetry.circuitChanged(state.adapter.providerId(), "closed");
        }
    }

    private void providerFailed(
            ProviderState state,
            ManagedRequest<?> request,
            ProviderExecutionException failure) {
        if (state.circuitState == CircuitState.HALF_OPEN && state.recoveryProbe != request) {
            if (failure.failureType() == ProviderFailureType.RATE_LIMITED) {
                applyRateLimitCooldown(state, failure);
            }
            return;
        }
        switch (failure.failureType()) {
            case PROVIDER_UNAVAILABLE, TIMEOUT -> availabilityFailed(state);
            case RATE_LIMITED -> {
                applyRateLimitCooldown(state, failure);
                if (state.circuitState == CircuitState.HALF_OPEN) {
                    openCircuit(state, ProviderFailureType.RATE_LIMITED);
                }
            }
            case AUTHENTICATION_FAILURE, QUOTA_EXHAUSTED -> openCircuit(state, failure.failureType());
            case INVALID_RESPONSE, UNSUPPORTED_TASK -> releaseProbe(state, request);
        }
    }

    private void applyRateLimitCooldown(ProviderState state, ProviderExecutionException failure) {
        Duration delay = failure.retryAfter().orElse(state.policy.initialRetryDelay());
        state.cooldownUntil = later(state.cooldownUntil, saturatingAdd(clock.instant(), delay));
    }

    private void availabilityFailed(ProviderState state) {
        state.consecutiveFailures++;
        if (state.circuitState == CircuitState.HALF_OPEN
                || state.consecutiveFailures >= state.policy.circuitFailureThreshold()) {
            openCircuit(state, ProviderFailureType.PROVIDER_UNAVAILABLE);
        }
    }

    private static void releaseProbe(ProviderState state, ManagedRequest<?> request) {
        if (state.recoveryProbe == request) state.recoveryProbe = null;
    }

    private void openCircuit(ProviderState state, ProviderFailureType openFailureType) {
        state.circuitState = CircuitState.OPEN;
        state.openFailureType = openFailureType;
        state.recoveryProbe = null;
        state.circuitOpenUntil = saturatingAdd(clock.instant(), state.policy.circuitOpenDuration());
        telemetry.circuitChanged(state.adapter.providerId(), "open");
        List<ManagedRequest<?>> rejected = new ArrayList<>(state.queue);
        state.queue.clear();
        rejected.forEach(request -> completeExceptionally(
                request,
                failure(state.adapter.providerId(), openFailureType),
                "circuit_open"));
        scheduleDispatch(state, state.policy.circuitOpenDuration());
    }

    private void requeue(ManagedRequest<?> request) {
        synchronized (lock) {
            if (closed || request.result.isDone()) return;
            ProviderState state = request.state;
            updateCircuitForTime(state, clock.instant());
            if (state.circuitState == CircuitState.OPEN) {
                completeExceptionally(request,
                        failure(state.adapter.providerId(), state.openFailureType), "circuit_open");
                return;
            }
            state.queue.add(request);
            dispatch(state);
        }
    }

    private void timeout(ManagedRequest<?> request) {
        synchronized (lock) {
            if (request.result.isDone()) return;
            request.state.queue.remove(request);
            if (request.providerInvocationStarted) {
                providerFailed(
                        request.state,
                        request,
                        failure(request.state.adapter.providerId(), ProviderFailureType.TIMEOUT));
            }
            completeTimeout(request);
            Thread physicalThread = request.physicalThread;
            if (physicalThread != null) physicalThread.interrupt();
            dispatch(request.state);
        }
    }

    private void terminalCompletion(ManagedRequest<?> request) {
        synchronized (lock) {
            if (!request.providerInvocationStarted) releaseAdmissions(request);
            if (!request.result.isCancelled()) return;
            request.state.queue.remove(request);
            Thread physicalThread = request.physicalThread;
            if (physicalThread != null) physicalThread.interrupt();
            cancelTimeout(request);
            telemetry.completed(
                    request.state.adapter.providerId(), request.request.taskType(), "cancelled",
                    elapsed(request), request.attempts - 1);
            dispatch(request.state);
        }
    }

    private void releaseAdmissions(ManagedRequest<?> request) {
        if (request.admissionReleased) return;
        request.admissionReleased = true;
        request.state.logicalRequests.remove(request);
        request.state.logicalOutstanding--;
        outstandingRequestsByUser.compute(request.userId, (ignored, outstanding) -> {
            if (outstanding == null || outstanding <= 1) return null;
            return outstanding - 1;
        });
    }

    private void completeTimeout(ManagedRequest<?> request) {
        completeExceptionally(
                request,
                failure(request.state.adapter.providerId(), ProviderFailureType.TIMEOUT),
                "timeout");
    }

    private <T> void complete(ManagedRequest<T> request, T value) {
        cancelTimeout(request);
        T completedValue = withExecutionMetadata(
                value, request.attempts - 1, request.providerInvocationCount);
        if (request.result.complete(completedValue)) {
            telemetry.completed(request.state.adapter.providerId(), request.request.taskType(), "success",
                    elapsed(request), request.attempts - 1);
        }
    }

    private void completeExceptionally(ManagedRequest<?> request, Throwable failure, String outcome) {
        cancelTimeout(request);
        Throwable completedFailure = failure instanceof ProviderExecutionException providerFailure
                ? providerFailure.withExecutionMetadata(
                        request.attempts - 1, request.providerInvocationCount)
                : new ProviderExecutionFailure(
                        failure, request.attempts - 1, request.providerInvocationCount);
        if (request.result.completeExceptionally(completedFailure)) {
            telemetry.completed(request.state.adapter.providerId(), request.request.taskType(), outcome,
                    elapsed(request), request.attempts - 1);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T withExecutionMetadata(
            T value, int retryCount, int providerInvocationCount) {
        if (value instanceof ProviderExecutionResult providerResult) {
            return (T) providerResult.withExecutionMetadata(retryCount, providerInvocationCount);
        }
        return value;
    }

    private static void cancelTimeout(ManagedRequest<?> request) {
        ScheduledFuture<?> timeoutTask = request.timeoutTask;
        if (timeoutTask != null) timeoutTask.cancel(false);
    }

    private void scheduleDispatch(ProviderState state, Duration delay) {
        if (delay.isNegative() || delay.isZero()) {
            scheduler.execute(() -> {
                synchronized (lock) { if (!closed) dispatch(state); }
            });
            return;
        }
        if (state.dispatchTask != null && !state.dispatchTask.isDone()) return;
        state.dispatchTask = scheduler.schedule(() -> {
            synchronized (lock) {
                state.dispatchTask = null;
                if (!closed) dispatch(state);
            }
        }, schedulerDelayNanos(delay), TimeUnit.NANOSECONDS);
    }

    private void updateCircuitForTime(ProviderState state, Instant now) {
        if (state.circuitState == CircuitState.OPEN && !now.isBefore(state.circuitOpenUntil)) {
            state.circuitState = CircuitState.HALF_OPEN;
            telemetry.circuitChanged(state.adapter.providerId(), "half_open");
        }
    }

    private static boolean canStartImmediately(ProviderState state, Instant now) {
        return state.running < state.policy.maximumConcurrency()
                && state.queue.isEmpty()
                && !state.cooldownUntil.isAfter(now)
                && (state.circuitState == CircuitState.CLOSED
                        || state.circuitState == CircuitState.HALF_OPEN && state.recoveryProbe == null);
    }

    private static boolean isRetryable(ProviderFailureType failureType) {
        return failureType == ProviderFailureType.PROVIDER_UNAVAILABLE
                || failureType == ProviderFailureType.RATE_LIMITED
                || failureType == ProviderFailureType.TIMEOUT;
    }

    private static Duration retryDelay(ManagedRequest<?> request, ProviderExecutionException failure) {
        AiRequestManagerPolicy policy = request.state.policy;
        long multiplier = 1L << Math.min(Math.max(0, request.attempts - 2), 30);
        Duration exponential;
        try {
            exponential = policy.initialRetryDelay().multipliedBy(multiplier);
        } catch (ArithmeticException ignored) {
            exponential = policy.maximumRetryDelay();
        }
        Duration bounded = exponential.compareTo(policy.maximumRetryDelay()) > 0
                ? policy.maximumRetryDelay() : exponential;
        return failure.retryAfter().filter(serverDelay -> serverDelay.compareTo(bounded) > 0).orElse(bounded);
    }

    private static boolean canRetryWithinDeadline(
            ManagedRequest<?> request,
            ProviderExecutionException failure) {
        Duration delay = retryDelay(request, failure);
        long remaining = request.deadlineNanos - System.nanoTime();
        return remaining > 0 && delay.compareTo(Duration.ofNanos(remaining)) < 0;
    }

    private static boolean deadlineExpired(ManagedRequest<?> request) {
        return System.nanoTime() >= request.deadlineNanos;
    }

    private static ProviderExecutionException failure(ProviderId providerId, ProviderFailureType failureType) {
        return new ProviderExecutionException(providerId, failureType);
    }

    private static Instant later(Instant left, Instant right) {
        return left.isAfter(right) ? left : right;
    }

    private static Instant saturatingAdd(Instant instant, Duration duration) {
        try {
            return instant.plus(duration);
        } catch (DateTimeException | ArithmeticException overflow) {
            return Instant.MAX;
        }
    }

    private static long schedulerDelayNanos(Duration delay) {
        try {
            return Math.max(0, delay.toNanos());
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private static Duration elapsed(ManagedRequest<?> request) {
        return Duration.ofNanos(Math.max(0, System.nanoTime() - request.startedNanos));
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("AI request manager is closed");
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (closed) return;
            closed = true;
            providers.values().forEach(state -> {
                List<ManagedRequest<?>> admitted = new ArrayList<>(state.logicalRequests);
                state.queue.clear();
                state.recoveryProbe = null;
                admitted.forEach(request -> request.result.cancel(false));
            });
        }
        scheduler.shutdownNow();
        workers.shutdownNow();
    }

    @FunctionalInterface
    private interface ProviderInvocation<T> {
        T invoke(AiProviderAdapter adapter);
    }

    @FunctionalInterface
    private interface RetryPermission {
        boolean allowed();
    }

    private enum CircuitState { CLOSED, OPEN, HALF_OPEN }

    private static final class StreamAttempt {
        private volatile boolean eventDelivered;
    }

    private static final class ProviderState {
        private static final Comparator<ManagedRequest<?>> ORDERING = Comparator
                .comparing((ManagedRequest<?> request) -> request.priority)
                .thenComparingLong(request -> request.sequence);

        private final AiProviderAdapter adapter;
        private final AiRequestManagerPolicy policy;
        private final PriorityQueue<ManagedRequest<?>> queue = new PriorityQueue<>(ORDERING);
        private final HashSet<ManagedRequest<?>> logicalRequests = new HashSet<>();
        private int running;
        private int logicalOutstanding;
        private int consecutiveFailures;
        private CircuitState circuitState = CircuitState.CLOSED;
        private Instant circuitOpenUntil = Instant.EPOCH;
        private Instant cooldownUntil = Instant.EPOCH;
        private ProviderFailureType openFailureType = ProviderFailureType.PROVIDER_UNAVAILABLE;
        private ManagedRequest<?> recoveryProbe;
        private ScheduledFuture<?> dispatchTask;

        private ProviderState(AiProviderAdapter adapter, AiRequestManagerPolicy policy) {
            this.adapter = adapter;
            this.policy = policy;
        }
    }

    private static final class ManagedRequest<T> {
        private final ProviderState state;
        private final UUID userId;
        private final ProviderExecutionRequest request;
        private final AiRequestPriority priority;
        private final long sequence;
        private final long deadlineNanos;
        private final ProviderInvocation<T> invocation;
        private final RetryPermission retryPermission;
        private final CompletableFuture<T> result;
        private final long startedNanos = System.nanoTime();
        private int attempts = 1;
        private volatile int providerInvocationCount;
        private boolean physicallyRunning;
        private boolean providerInvocationStarted;
        private Thread physicalThread;
        private ScheduledFuture<?> timeoutTask;
        private boolean admissionReleased;

        private ManagedRequest(
                ProviderState state,
                UUID userId,
                ProviderExecutionRequest request,
                AiRequestPriority priority,
                long sequence,
                long deadlineNanos,
                ProviderInvocation<T> invocation,
                RetryPermission retryPermission) {
            this.state = state;
            this.userId = userId;
            this.request = request;
            this.priority = priority;
            this.sequence = sequence;
            this.deadlineNanos = deadlineNanos;
            this.invocation = invocation;
            this.retryPermission = retryPermission;
            this.result = new ManagedExecutionFuture<>(() -> providerInvocationCount);
        }
    }

    private static final class ManagedExecutionFuture<T> extends CompletableFuture<T> {
        private final IntSupplier providerInvocationCount;

        private ManagedExecutionFuture(IntSupplier providerInvocationCount) {
            this.providerInvocationCount = providerInvocationCount;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return completeExceptionally(new ProviderExecutionCancellationException(
                    providerInvocationCount.getAsInt()));
        }
    }
}
