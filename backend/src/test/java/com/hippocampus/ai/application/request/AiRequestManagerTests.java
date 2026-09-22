package com.hippocampus.ai.application.request;

import static com.hippocampus.ai.infrastructure.provider.ProviderTestFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.hippocampus.ai.application.provider.AiProviderAdapter;
import com.hippocampus.ai.application.provider.ProviderEventStream;
import com.hippocampus.ai.application.provider.ProviderExecutionException;
import com.hippocampus.ai.application.provider.ProviderExecutionRequest;
import com.hippocampus.ai.application.provider.ProviderExecutionResult;
import com.hippocampus.ai.application.provider.ProviderFailureType;
import com.hippocampus.ai.application.provider.ProviderStreamEvent;
import com.hippocampus.ai.application.provider.ProviderTextDelta;
import com.hippocampus.ai.application.provider.ProviderUsage;
import com.hippocampus.ai.application.routing.ProviderId;
import com.hippocampus.ai.domain.AiTaskType;

class AiRequestManagerTests {
    private static final UUID USER_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_B = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void boundsEachProviderIndependently() throws Exception {
        BlockingAdapter gemini = new BlockingAdapter(ProviderId.GEMINI, 2);
        BlockingAdapter ollama = new BlockingAdapter(ProviderId.OLLAMA_CLOUD, 1);
        try (AiRequestManager manager = manager(List.of(gemini, ollama), policy(2, 8))) {
            List<CompletableFuture<ProviderExecutionResult>> geminiCalls = List.of(
                    submit(manager, ProviderId.GEMINI, "g1"),
                    submit(manager, ProviderId.GEMINI, "g2"),
                    submit(manager, ProviderId.GEMINI, "g3"));
            CompletableFuture<ProviderExecutionResult> ollamaCall =
                    submit(manager, ProviderId.OLLAMA_CLOUD, "o1");

            assertThat(gemini.started.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(ollama.started.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(gemini.maximumActive.get()).isEqualTo(2);
            assertThat(ollama.maximumActive.get()).isEqualTo(1);
            assertThat(manager.diagnostics().providers().get(ProviderId.GEMINI).runningRequests()).isEqualTo(2);

            gemini.release.countDown();
            ollama.release.countDown();
            CompletableFuture.allOf(geminiCalls.toArray(CompletableFuture[]::new)).get(2, TimeUnit.SECONDS);
            ollamaCall.get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void boundsQueueAndOrdersByPriorityThenFifo() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        List<String> order = java.util.Collections.synchronizedList(new ArrayList<>());
        FakeAdapter adapter = new FakeAdapter(ProviderId.GEMINI, request -> {
            if (request.target().modelId().equals("blocker")) {
                firstStarted.countDown();
                awaitUninterruptibly(releaseFirst);
            } else {
                order.add(request.target().modelId());
            }
            return result(request);
        });
        try (AiRequestManager manager = manager(List.of(adapter), policy(1, 5))) {
            CompletableFuture<?> blocker = manager.execute(submission(USER_A, ProviderId.GEMINI, "blocker"), AiRequestPriority.BACKGROUND_AI);
            assertThat(firstStarted.await(2, TimeUnit.SECONDS)).isTrue();
            CompletableFuture<?> background = manager.execute(submission(USER_A, ProviderId.GEMINI, "background"), AiRequestPriority.BACKGROUND_AI);
            CompletableFuture<?> generation = manager.execute(submission(USER_A, ProviderId.GEMINI, "generation"), AiRequestPriority.INTERACTIVE_GENERATION);
            CompletableFuture<?> explanationOne = manager.execute(submission(USER_A, ProviderId.GEMINI, "explanation-1"), AiRequestPriority.INTERACTIVE_EXPLANATION);
            CompletableFuture<?> explanationTwo = manager.execute(submission(USER_A, ProviderId.GEMINI, "explanation-2"), AiRequestPriority.INTERACTIVE_EXPLANATION);
            CompletableFuture<?> evaluation = manager.execute(submission(USER_A, ProviderId.GEMINI, "evaluation"), AiRequestPriority.INTERACTIVE_EVALUATION);

            CompletableFuture<?> rejected = manager.execute(
                    submission(USER_A, ProviderId.GEMINI, "queue-overflow"), AiRequestPriority.MISSION_PREPARATION);
            assertFailure(rejected, ProviderFailureType.PROVIDER_UNAVAILABLE);

            releaseFirst.countDown();
            CompletableFuture.allOf(blocker, background, generation, explanationOne, explanationTwo, evaluation)
                    .get(2, TimeUnit.SECONDS);
            assertThat(order).containsExactly(
                    "evaluation", "explanation-1", "explanation-2", "generation", "background");
        }
    }

    @Test
    void timeoutDoesNotReleaseCapacityUntilPhysicalCallStops() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        FakeAdapter adapter = blockingFirstAdapter(firstStarted, releaseFirst, secondStarted);
        AiRequestManagerPolicy timeoutPolicy = policy(1, 2, Duration.ofMillis(150), 1, 5, Duration.ofMillis(50));
        try (AiRequestManager manager = manager(List.of(adapter), timeoutPolicy)) {
            CompletableFuture<?> first = manager.execute(submission(USER_A, ProviderId.GEMINI, "first"), AiRequestPriority.INTERACTIVE_GENERATION);
            assertThat(firstStarted.await(2, TimeUnit.SECONDS)).isTrue();
            assertFailure(first, ProviderFailureType.TIMEOUT);

            CompletableFuture<?> second = manager.execute(submission(USER_A, ProviderId.GEMINI, "second"), AiRequestPriority.INTERACTIVE_GENERATION);
            assertThat(secondStarted.await(60, TimeUnit.MILLISECONDS)).isFalse();
            assertThat(manager.diagnostics().providers().get(ProviderId.GEMINI).runningRequests()).isEqualTo(1);

            releaseFirst.countDown();
            second.get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void cancellationDoesNotReleaseCapacityUntilPhysicalCallStops() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        FakeAdapter adapter = blockingFirstAdapter(firstStarted, releaseFirst, secondStarted);
        try (AiRequestManager manager = manager(List.of(adapter), policy(1, 2))) {
            CompletableFuture<?> first = manager.execute(submission(USER_A, ProviderId.GEMINI, "first"), AiRequestPriority.INTERACTIVE_GENERATION);
            assertThat(firstStarted.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(first.cancel(true)).isTrue();

            CompletableFuture<?> second = manager.execute(submission(USER_A, ProviderId.GEMINI, "second"), AiRequestPriority.INTERACTIVE_GENERATION);
            assertThat(secondStarted.await(120, TimeUnit.MILLISECONDS)).isFalse();
            assertThat(manager.diagnostics().providers().get(ProviderId.GEMINI).runningRequests()).isEqualTo(1);

            releaseFirst.countDown();
            second.get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void rejectsWhenSameUserOutstandingLimitIsExhaustedWithoutContactingProvider() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        FakeAdapter adapter = new FakeAdapter(ProviderId.GEMINI, request -> {
            calls.incrementAndGet();
            started.countDown();
            awaitUninterruptibly(release);
            return result(request);
        });
        try (AiRequestManager manager = manager(List.of(adapter), policy(1, 2), 1)) {
            CompletableFuture<?> first = manager.execute(
                    submission(USER_A, ProviderId.GEMINI, "first"), AiRequestPriority.INTERACTIVE_GENERATION);
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();

            assertFailure(
                    manager.execute(
                            submission(USER_A, ProviderId.GEMINI, "rejected"),
                            AiRequestPriority.INTERACTIVE_GENERATION),
                    ProviderFailureType.RATE_LIMITED);
            assertThat(calls).hasValue(1);

            release.countDown();
            first.get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void keepsDifferentUsersAdmissionIndependent() throws Exception {
        BlockingAdapter adapter = new BlockingAdapter(ProviderId.GEMINI, 2);
        try (AiRequestManager manager = manager(List.of(adapter), policy(2, 2), 1)) {
            CompletableFuture<?> first = manager.execute(
                    submission(USER_A, ProviderId.GEMINI, "user-a"), AiRequestPriority.INTERACTIVE_GENERATION);
            CompletableFuture<?> second = manager.execute(
                    submission(USER_B, ProviderId.GEMINI, "user-b"), AiRequestPriority.INTERACTIVE_GENERATION);

            assertThat(adapter.started.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(adapter.maximumActive).hasValue(2);
            adapter.release.countDown();
            CompletableFuture.allOf(first, second).get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void releasesUserAdmissionAfterSuccessAndTerminalFailure() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        FakeAdapter adapter = new FakeAdapter(ProviderId.GEMINI, request -> {
            int call = calls.incrementAndGet();
            if (call == 2) throw failure(ProviderId.GEMINI, ProviderFailureType.INVALID_RESPONSE);
            return result(request);
        });
        AiRequestManagerPolicy oneAttempt = policy(1, 2, Duration.ofSeconds(2), 1, 5, Duration.ofMillis(100));
        try (AiRequestManager manager = manager(List.of(adapter), oneAttempt, 1)) {
            manager.execute(submission(USER_A, ProviderId.GEMINI, "success"), AiRequestPriority.INTERACTIVE_GENERATION)
                    .get(2, TimeUnit.SECONDS);
            assertFailure(
                    manager.execute(
                            submission(USER_A, ProviderId.GEMINI, "failure"),
                            AiRequestPriority.INTERACTIVE_GENERATION),
                    ProviderFailureType.INVALID_RESPONSE);
            manager.execute(submission(USER_A, ProviderId.GEMINI, "after"), AiRequestPriority.INTERACTIVE_GENERATION)
                    .get(2, TimeUnit.SECONDS);
            assertThat(calls).hasValue(3);
        }
    }

    @Test
    void releasesUserAdmissionAfterTimeoutAndCancellation() throws Exception {
        assertAdmissionReleasedAfterInterruptedTerminalCompletion(false);
        assertAdmissionReleasedAfterInterruptedTerminalCompletion(true);
    }

    @Test
    void retryRetainsOneUserAdmissionSlot() throws Exception {
        CountDownLatch firstAttempt = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        FakeAdapter adapter = new FakeAdapter(ProviderId.GEMINI, request -> {
            if (calls.incrementAndGet() == 1) {
                firstAttempt.countDown();
                throw failure(ProviderId.GEMINI, ProviderFailureType.PROVIDER_UNAVAILABLE);
            }
            return result(request);
        });
        AiRequestManagerPolicy retryPolicy = new AiRequestManagerPolicy(
                1, 2, Duration.ofSeconds(2), 2,
                Duration.ofMillis(200), Duration.ofMillis(200), 5, Duration.ofMillis(100));
        try (AiRequestManager manager = manager(List.of(adapter), retryPolicy, 1)) {
            CompletableFuture<?> retrying = manager.execute(
                    submission(USER_A, ProviderId.GEMINI, "retrying"), AiRequestPriority.INTERACTIVE_GENERATION);
            assertThat(firstAttempt.await(2, TimeUnit.SECONDS)).isTrue();
            assertFailure(
                    manager.execute(
                            submission(USER_A, ProviderId.GEMINI, "second-logical-request"),
                            AiRequestPriority.INTERACTIVE_GENERATION),
                    ProviderFailureType.RATE_LIMITED);

            retrying.get(2, TimeUnit.SECONDS);
            assertThat(calls).hasValue(2);
        }
    }

    @Test
    void retriesOnlyTransientFailuresWithinConfiguredBound() throws Exception {
        AtomicInteger transientCalls = new AtomicInteger();
        FakeAdapter transientAdapter = new FakeAdapter(ProviderId.GEMINI, request -> {
            if (transientCalls.incrementAndGet() < 3) {
                throw failure(ProviderId.GEMINI, ProviderFailureType.PROVIDER_UNAVAILABLE);
            }
            return result(request);
        });
        try (AiRequestManager manager = manager(List.of(transientAdapter), policy(1, 2))) {
            manager.execute(submission(USER_A, ProviderId.GEMINI, "retry"), AiRequestPriority.INTERACTIVE_GENERATION)
                    .get(2, TimeUnit.SECONDS);
            assertThat(transientCalls).hasValue(3);
        }

        AtomicInteger authenticationCalls = new AtomicInteger();
        FakeAdapter authenticationAdapter = new FakeAdapter(ProviderId.GEMINI, request -> {
            authenticationCalls.incrementAndGet();
            throw failure(ProviderId.GEMINI, ProviderFailureType.AUTHENTICATION_FAILURE);
        });
        try (AiRequestManager manager = manager(List.of(authenticationAdapter), policy(1, 2))) {
            assertFailure(manager.execute(submission(USER_A, ProviderId.GEMINI, "no-retry"), AiRequestPriority.INTERACTIVE_GENERATION),
                    ProviderFailureType.AUTHENTICATION_FAILURE);
            assertThat(authenticationCalls).hasValue(1);
        }
    }

    @Test
    void retryAfterCreatesProviderLocalCooldown() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        List<Long> starts = java.util.Collections.synchronizedList(new ArrayList<>());
        FakeAdapter adapter = new FakeAdapter(ProviderId.GEMINI, request -> {
            starts.add(System.nanoTime());
            if (calls.incrementAndGet() == 1) {
                throw new ProviderExecutionException(
                        ProviderId.GEMINI,
                        ProviderFailureType.RATE_LIMITED,
                        Optional.of(Duration.ofMillis(150)));
            }
            return result(request);
        });
        try (AiRequestManager manager = manager(List.of(adapter), policy(1, 2))) {
            manager.execute(submission(USER_A, ProviderId.GEMINI, "rate-limited"), AiRequestPriority.INTERACTIVE_GENERATION)
                    .get(2, TimeUnit.SECONDS);
            assertThat(Duration.ofNanos(starts.get(1) - starts.get(0))).isGreaterThanOrEqualTo(Duration.ofMillis(120));
        }
    }

    @Test
    void opensCircuitFailsFastAndAllowsSingleRecoveryProbe() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        FakeAdapter adapter = new FakeAdapter(ProviderId.GEMINI, request -> {
            int call = calls.incrementAndGet();
            if (call == 1) throw failure(ProviderId.GEMINI, ProviderFailureType.PROVIDER_UNAVAILABLE);
            if (call == 2) throw failure(ProviderId.GEMINI, ProviderFailureType.TIMEOUT);
            return result(request);
        });
        AiRequestManagerPolicy policy = policy(1, 4, Duration.ofSeconds(2), 1, 2, Duration.ofMillis(100));
        try (AiRequestManager manager = manager(List.of(adapter), policy)) {
            assertFailure(submit(manager, ProviderId.GEMINI, "failure-1"), ProviderFailureType.PROVIDER_UNAVAILABLE);
            assertFailure(submit(manager, ProviderId.GEMINI, "failure-2"), ProviderFailureType.TIMEOUT);
            assertThat(manager.diagnostics().providers().get(ProviderId.GEMINI).circuitState()).isEqualTo("OPEN");

            assertFailure(submit(manager, ProviderId.GEMINI, "fast-fail"), ProviderFailureType.PROVIDER_UNAVAILABLE);
            assertThat(calls).hasValue(2);

            Thread.sleep(130);
            submit(manager, ProviderId.GEMINI, "probe").get(2, TimeUnit.SECONDS);
            assertThat(calls).hasValue(3);
            assertThat(manager.diagnostics().providers().get(ProviderId.GEMINI).circuitState()).isEqualTo("CLOSED");
        }
    }

    @Test
    void failedHalfOpenProbeReopensCircuit() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        FakeAdapter adapter = new FakeAdapter(ProviderId.GEMINI, request -> {
            calls.incrementAndGet();
            throw failure(ProviderId.GEMINI, ProviderFailureType.TIMEOUT);
        });
        AiRequestManagerPolicy policy = policy(1, 2, Duration.ofSeconds(2), 1, 1, Duration.ofMillis(80));
        try (AiRequestManager manager = manager(List.of(adapter), policy)) {
            assertFailure(submit(manager, ProviderId.GEMINI, "open"), ProviderFailureType.TIMEOUT);
            Thread.sleep(100);
            assertFailure(submit(manager, ProviderId.GEMINI, "failed-probe"), ProviderFailureType.TIMEOUT);

            assertThat(manager.diagnostics().providers().get(ProviderId.GEMINI).circuitState()).isEqualTo("OPEN");
            assertFailure(submit(manager, ProviderId.GEMINI, "fail-fast"), ProviderFailureType.PROVIDER_UNAVAILABLE);
            assertThat(calls).hasValue(2);
        }
    }

    @Test
    void repeatedRateLimitsPreserveClassificationWithoutOpeningAvailabilityCircuit() {
        AtomicInteger calls = new AtomicInteger();
        FakeAdapter adapter = new FakeAdapter(ProviderId.GEMINI, request -> {
            calls.incrementAndGet();
            throw failure(ProviderId.GEMINI, ProviderFailureType.RATE_LIMITED);
        });
        AiRequestManagerPolicy policy = policy(1, 2, Duration.ofSeconds(2), 1, 1, Duration.ofMillis(80));
        try (AiRequestManager manager = manager(List.of(adapter), policy)) {
            assertFailure(submit(manager, ProviderId.GEMINI, "rate-limit-1"), ProviderFailureType.RATE_LIMITED);
            assertFailure(submit(manager, ProviderId.GEMINI, "rate-limit-2"), ProviderFailureType.RATE_LIMITED);

            assertThat(calls).hasValue(2);
            assertThat(manager.diagnostics().providers().get(ProviderId.GEMINI).circuitState()).isEqualTo("CLOSED");
        }
    }

    @Test
    void authenticationAndQuotaFailuresEnterProtectiveFailFastState() {
        for (ProviderFailureType type : List.of(
                ProviderFailureType.AUTHENTICATION_FAILURE,
                ProviderFailureType.QUOTA_EXHAUSTED)) {
            AtomicInteger calls = new AtomicInteger();
            FakeAdapter adapter = new FakeAdapter(ProviderId.GEMINI, request -> {
                calls.incrementAndGet();
                throw failure(ProviderId.GEMINI, type);
            });
            try (AiRequestManager manager = manager(
                    List.of(adapter),
                    policy(1, 2, Duration.ofSeconds(2), 3, 5, Duration.ofMillis(100)))) {
                assertFailure(submit(manager, ProviderId.GEMINI, "protect"), type);
                assertFailure(submit(manager, ProviderId.GEMINI, "fail-fast"), type);
                assertThat(calls).hasValue(1);
                assertThat(manager.diagnostics().providers().get(ProviderId.GEMINI).circuitState())
                        .isEqualTo("OPEN");
            }
        }
    }

    @Test
    void invalidResponseAndUnsupportedTaskDoNotAffectAvailabilityCircuit() {
        for (ProviderFailureType type : List.of(
                ProviderFailureType.INVALID_RESPONSE,
                ProviderFailureType.UNSUPPORTED_TASK)) {
            AtomicInteger calls = new AtomicInteger();
            FakeAdapter adapter = new FakeAdapter(ProviderId.GEMINI, request -> {
                calls.incrementAndGet();
                throw failure(ProviderId.GEMINI, type);
            });
            try (AiRequestManager manager = manager(
                    List.of(adapter),
                    policy(1, 2, Duration.ofSeconds(2), 1, 1, Duration.ofMillis(100)))) {
                assertFailure(submit(manager, ProviderId.GEMINI, "failure-1"), type);
                assertFailure(submit(manager, ProviderId.GEMINI, "failure-2"), type);
                assertThat(calls).hasValue(2);
                assertThat(manager.diagnostics().providers().get(ProviderId.GEMINI).circuitState())
                        .isEqualTo("CLOSED");
            }
        }
    }

    @Test
    void streamingUsesTheSameProviderCapacityAndDoesNotRetryAfterDeliveringContent() throws Exception {
        CountDownLatch streamStarted = new CountDownLatch(1);
        CountDownLatch releaseStream = new CountDownLatch(1);
        CountDownLatch executeStarted = new CountDownLatch(1);
        AtomicInteger streamCalls = new AtomicInteger();
        FakeAdapter adapter = new FakeAdapter(ProviderId.GEMINI, request -> {
            executeStarted.countDown();
            return result(request);
        }, consumer -> {
            streamCalls.incrementAndGet();
            consumer.accept(new ProviderTextDelta("untrusted"));
            streamStarted.countDown();
            awaitUninterruptibly(releaseStream);
            throw failure(ProviderId.GEMINI, ProviderFailureType.PROVIDER_UNAVAILABLE);
        });
        try (AiRequestManager manager = manager(List.of(adapter), policy(1, 2))) {
            CompletableFuture<Void> stream = manager.stream(
                    submission(USER_A, ProviderId.GEMINI, "stream"), AiRequestPriority.INTERACTIVE_EXPLANATION, ignored -> {});
            assertThat(streamStarted.await(2, TimeUnit.SECONDS)).isTrue();
            CompletableFuture<?> execute = submit(manager, ProviderId.GEMINI, "execute");
            assertThat(executeStarted.await(120, TimeUnit.MILLISECONDS)).isFalse();

            releaseStream.countDown();
            assertFailure(stream, ProviderFailureType.PROVIDER_UNAVAILABLE);
            execute.get(2, TimeUnit.SECONDS);
            assertThat(streamCalls).hasValue(1);
        }
    }

    @Test
    void streamingFailureAfterContentStillUpdatesProviderHealthWithoutRetry() {
        AtomicInteger streamCalls = new AtomicInteger();
        FakeAdapter adapter = new FakeAdapter(ProviderId.GEMINI, AiRequestManagerTests::result, consumer -> {
            streamCalls.incrementAndGet();
            consumer.accept(new ProviderTextDelta("untrusted"));
            throw failure(ProviderId.GEMINI, ProviderFailureType.PROVIDER_UNAVAILABLE);
        });
        AiRequestManagerPolicy policy = policy(1, 2, Duration.ofSeconds(2), 3, 1, Duration.ofMillis(100));
        try (AiRequestManager manager = manager(List.of(adapter), policy)) {
            assertFailure(
                    manager.stream(
                            submission(USER_A, ProviderId.GEMINI, "stream"),
                            AiRequestPriority.INTERACTIVE_EXPLANATION,
                            ignored -> {}),
                    ProviderFailureType.PROVIDER_UNAVAILABLE);

            assertThat(streamCalls).hasValue(1);
            assertThat(manager.diagnostics().providers().get(ProviderId.GEMINI).circuitState()).isEqualTo("OPEN");
        }
    }

    private static CompletableFuture<ProviderExecutionResult> submit(
            AiRequestManager manager, ProviderId providerId, String modelId) {
        return manager.execute(submission(USER_A, providerId, modelId), AiRequestPriority.INTERACTIVE_GENERATION);
    }

    private static void assertAdmissionReleasedAfterInterruptedTerminalCompletion(boolean cancel) throws Exception {
        CountDownLatch blockingStarted = new CountDownLatch(1);
        CountDownLatch releaseBlocking = new CountDownLatch(1);
        FakeAdapter gemini = new FakeAdapter(ProviderId.GEMINI, request -> {
            blockingStarted.countDown();
            awaitUninterruptibly(releaseBlocking);
            return result(request);
        });
        FakeAdapter ollama = new FakeAdapter(ProviderId.OLLAMA_CLOUD, AiRequestManagerTests::result);
        AiRequestManagerPolicy policy = policy(
                1, 2, Duration.ofMillis(120), 1, 5, Duration.ofMillis(100));
        try (AiRequestManager manager = manager(List.of(gemini, ollama), policy, 1)) {
            CompletableFuture<?> terminal = manager.execute(
                    submission(USER_A, ProviderId.GEMINI, cancel ? "cancel" : "timeout"),
                    AiRequestPriority.INTERACTIVE_GENERATION);
            assertThat(blockingStarted.await(2, TimeUnit.SECONDS)).isTrue();
            if (cancel) {
                assertThat(terminal.cancel(true)).isTrue();
            } else {
                assertFailure(terminal, ProviderFailureType.TIMEOUT);
            }

            manager.execute(
                            submission(USER_A, ProviderId.OLLAMA_CLOUD, "after-terminal"),
                            AiRequestPriority.INTERACTIVE_GENERATION)
                    .get(2, TimeUnit.SECONDS);
            releaseBlocking.countDown();
        }
    }

    private static AiRequestSubmission submission(UUID userId, ProviderId providerId, String modelId) {
        return new AiRequestSubmission(userId, request(providerId, modelId));
    }

    private static AiRequestManager manager(List<AiProviderAdapter> adapters, AiRequestManagerPolicy policy) {
        Map<ProviderId, AiRequestManagerPolicy> policies = new EnumMap<>(ProviderId.class);
        adapters.forEach(adapter -> policies.put(adapter.providerId(), policy));
        return new AiRequestManager(adapters, policies, 10, AiRequestTelemetry.NONE);
    }

    private static AiRequestManager manager(
            List<AiProviderAdapter> adapters,
            AiRequestManagerPolicy policy,
            int maximumOutstandingRequestsPerUser) {
        Map<ProviderId, AiRequestManagerPolicy> policies = new EnumMap<>(ProviderId.class);
        adapters.forEach(adapter -> policies.put(adapter.providerId(), policy));
        return new AiRequestManager(
                adapters, policies, maximumOutstandingRequestsPerUser, AiRequestTelemetry.NONE);
    }

    private static AiRequestManagerPolicy policy(int concurrency, int queue) {
        return policy(concurrency, queue, Duration.ofSeconds(2), 3, 5, Duration.ofMillis(100));
    }

    private static AiRequestManagerPolicy policy(
            int concurrency,
            int queue,
            Duration timeout,
            int maximumAttempts,
            int circuitThreshold,
            Duration circuitOpenDuration) {
        return new AiRequestManagerPolicy(
                concurrency, queue, timeout, maximumAttempts,
                Duration.ofMillis(10), Duration.ofMillis(40), circuitThreshold, circuitOpenDuration);
    }

    private static ProviderExecutionResult result(ProviderExecutionRequest request) {
        return new ProviderExecutionResult(
                request.target().providerId(), request.target().modelId(), "untrusted",
                ProviderUsage.NONE, Duration.ZERO);
    }

    private static ProviderExecutionException failure(ProviderId providerId, ProviderFailureType type) {
        return new ProviderExecutionException(providerId, type);
    }

    private static void assertFailure(CompletableFuture<?> future, ProviderFailureType expected) {
        assertThatThrownBy(() -> future.get(2, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOfSatisfying(ProviderExecutionException.class,
                        failure -> assertThat(failure.failureType()).isEqualTo(expected));
    }

    private static FakeAdapter blockingFirstAdapter(
            CountDownLatch firstStarted,
            CountDownLatch releaseFirst,
            CountDownLatch secondStarted) {
        return new FakeAdapter(ProviderId.GEMINI, request -> {
            if (request.target().modelId().equals("first")) {
                firstStarted.countDown();
                awaitUninterruptibly(releaseFirst);
            } else {
                secondStarted.countDown();
            }
            return result(request);
        });
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException ignored) {
                interrupted = true;
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    private static class FakeAdapter implements AiProviderAdapter {
        private final ProviderId providerId;
        private final Function<ProviderExecutionRequest, ProviderExecutionResult> execution;
        private final Consumer<Consumer<? super ProviderStreamEvent>> streaming;

        private FakeAdapter(
                ProviderId providerId,
                Function<ProviderExecutionRequest, ProviderExecutionResult> execution) {
            this(providerId, execution, ignored -> { throw new UnsupportedOperationException(); });
        }

        private FakeAdapter(
                ProviderId providerId,
                Function<ProviderExecutionRequest, ProviderExecutionResult> execution,
                Consumer<Consumer<? super ProviderStreamEvent>> streaming) {
            this.providerId = providerId;
            this.execution = execution;
            this.streaming = streaming;
        }

        @Override public ProviderId providerId() { return providerId; }
        @Override public boolean supports(AiTaskType taskType) { return true; }
        @Override public ProviderExecutionResult execute(ProviderExecutionRequest request) { return execution.apply(request); }
        @Override public ProviderEventStream stream(ProviderExecutionRequest request) { return streaming::accept; }
    }

    private static final class BlockingAdapter extends FakeAdapter {
        private final AtomicInteger active = new AtomicInteger();
        private final AtomicInteger maximumActive = new AtomicInteger();
        private final CountDownLatch started;
        private final CountDownLatch release = new CountDownLatch(1);

        private BlockingAdapter(ProviderId providerId, int expectedStarts) {
            super(providerId, request -> { throw new AssertionError("replaced by override"); });
            this.started = new CountDownLatch(expectedStarts);
        }

        @Override
        public ProviderExecutionResult execute(ProviderExecutionRequest request) {
            int current = active.incrementAndGet();
            maximumActive.accumulateAndGet(current, Math::max);
            started.countDown();
            awaitUninterruptibly(release);
            active.decrementAndGet();
            return result(request);
        }
    }
}
