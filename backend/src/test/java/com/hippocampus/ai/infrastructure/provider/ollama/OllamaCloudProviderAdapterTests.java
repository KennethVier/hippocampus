package com.hippocampus.ai.infrastructure.provider.ollama;

import static com.hippocampus.ai.infrastructure.provider.ProviderTestFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withRawStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.hippocampus.ai.application.provider.ProviderExecutionException;
import com.hippocampus.ai.application.provider.ProviderExecutionResult;
import com.hippocampus.ai.application.provider.ProviderFailureType;
import com.hippocampus.ai.application.provider.ProviderStreamCompleted;
import com.hippocampus.ai.application.provider.ProviderStreamEvent;
import com.hippocampus.ai.application.provider.ProviderTextDelta;
import com.hippocampus.ai.application.routing.ProviderId;

class OllamaCloudProviderAdapterTests {
    private static final String SECRET = "ollama-test-secret";

    @Test
    void postsCanonicalPromptAndRouteToAuthenticatedCloudChatEndpoint() {
        Fixture fixture = fixture();
        fixture.server().expect(requestTo("https://ollama.com/api/chat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + SECRET))
                .andExpect(content().json("""
                        {
                          "model":"cloud-selected",
                          "messages":[
                            {"role":"system","content":"system-policy-secret-marker"},
                            {"role":"user","content":"student-task-secret-marker"}
                          ],
                          "stream":false,
                          "format":"json",
                          "options":{"num_predict":64}
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "model":"cloud-actual",
                          "message":{"role":"assistant","content":"{\\\"answer\\\":\\\"untrusted\\\"}"},
                          "prompt_eval_count":12,
                          "eval_count":5
                        }
                        """, MediaType.APPLICATION_JSON));

        ProviderExecutionResult result = fixture.adapter()
                .execute(request(ProviderId.OLLAMA_CLOUD, "cloud-selected"));

        assertThat(fixture.adapter().providerId()).isEqualTo(ProviderId.OLLAMA_CLOUD);
        assertThat(result.providerId()).isEqualTo(ProviderId.OLLAMA_CLOUD);
        assertThat(result.modelId()).isEqualTo("cloud-actual");
        assertThat(result.rawContent()).isEqualTo("{\"answer\":\"untrusted\"}");
        assertThat(result.usage().inputTokens()).contains(12);
        assertThat(result.usage().outputTokens()).contains(5);
        assertThat(result.usage().totalTokens()).isEmpty();
        assertThat(result.latency().isNegative()).isFalse();
        fixture.server().verify();
    }

    @Test
    void keepsAbsentUsageAbsent() {
        Fixture fixture = fixture();
        fixture.server().expect(requestTo("https://ollama.com/api/chat"))
                .andRespond(withSuccess(
                        "{\"message\":{\"role\":\"assistant\",\"content\":\"{}\"}}",
                        MediaType.APPLICATION_JSON));

        ProviderExecutionResult result = fixture.adapter()
                .execute(request(ProviderId.OLLAMA_CLOUD, "cloud-selected"));

        assertThat(result.modelId()).isEqualTo("cloud-selected");
        assertThat(result.usage().inputTokens()).isEmpty();
        assertThat(result.usage().outputTokens()).isEmpty();
        assertThat(result.usage().totalTokens()).isEmpty();
        fixture.server().verify();
    }

    @Test
    void rejectsAnotherProviderRoute() {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> fixture.adapter().execute(request(ProviderId.GEMINI, "wrong")))
                .isInstanceOfSatisfying(ProviderExecutionException.class, failure -> {
                    assertThat(failure.failureType()).isEqualTo(ProviderFailureType.UNSUPPORTED_TASK);
                    assertThat(failure.providerId()).isEqualTo(ProviderId.OLLAMA_CLOUD);
                });
    }

    @Test
    void malformedOrEmptyResponseFailsSafely() {
        Fixture fixture = fixture();
        fixture.server().expect(requestTo("https://ollama.com/api/chat"))
                .andRespond(withSuccess("{not-json", MediaType.APPLICATION_JSON));

        assertFailure(fixture.adapter(), ProviderFailureType.INVALID_RESPONSE);
        fixture.server().verify();
    }

    @Test
    void normalizesHttpFailuresWithoutLeakingSecretOrRawBody() {
        assertHttpFailure(401, ProviderFailureType.AUTHENTICATION_FAILURE);
        assertHttpFailure(403, ProviderFailureType.AUTHENTICATION_FAILURE);
        assertHttpFailure(429, ProviderFailureType.RATE_LIMITED);
        assertHttpFailure(503, ProviderFailureType.PROVIDER_UNAVAILABLE);
    }

    @Test
    void preservesNormalizedRetryAfterWithoutLeakingProviderDetails() {
        Fixture fixture = fixture();
        fixture.server().expect(requestTo("https://ollama.com/api/chat"))
                .andRespond(withRawStatus(429)
                        .header(HttpHeaders.RETRY_AFTER, "9")
                        .body("raw-provider-body " + SECRET)
                        .contentType(MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> fixture.adapter().execute(request(ProviderId.OLLAMA_CLOUD, "cloud-selected")))
                .isInstanceOfSatisfying(ProviderExecutionException.class, failure -> {
                    assertThat(failure.failureType()).isEqualTo(ProviderFailureType.RATE_LIMITED);
                    assertThat(failure.retryAfter()).contains(Duration.ofSeconds(9));
                    assertThat(failure).hasNoCause();
                    assertThat(failure.getMessage()).doesNotContain(SECRET, "raw-provider-body");
                });
        fixture.server().verify();
    }

    @Test
    void saturatesExtremelyLargeRetryAfterDeltaWithoutLeakingProviderDetails() {
        Fixture fixture = fixture();
        fixture.server().expect(requestTo("https://ollama.com/api/chat"))
                .andRespond(withRawStatus(429)
                        .header(HttpHeaders.RETRY_AFTER, "999999999999999999999999999999999999999")
                        .body("raw-provider-body " + SECRET)
                        .contentType(MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> fixture.adapter().execute(request(ProviderId.OLLAMA_CLOUD, "cloud-selected")))
                .isInstanceOfSatisfying(ProviderExecutionException.class, failure -> {
                    assertThat(failure.failureType()).isEqualTo(ProviderFailureType.RATE_LIMITED);
                    assertThat(failure.retryAfter()).contains(Duration.ofSeconds(Long.MAX_VALUE));
                    assertThat(failure).hasNoCause();
                    assertThat(failure.getMessage()).doesNotContain(SECRET, "raw-provider-body");
                });
        fixture.server().verify();
    }

    @Test
    void normalizesFarFutureRetryAfterDateWithoutLeakingProviderDetails() {
        Fixture fixture = fixture();
        fixture.server().expect(requestTo("https://ollama.com/api/chat"))
                .andRespond(withRawStatus(429)
                        .header(HttpHeaders.RETRY_AFTER, "Fri, 31 Dec 9999 23:59:59 GMT")
                        .body("raw-provider-body " + SECRET)
                        .contentType(MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> fixture.adapter().execute(request(ProviderId.OLLAMA_CLOUD, "cloud-selected")))
                .isInstanceOfSatisfying(ProviderExecutionException.class, failure -> {
                    assertThat(failure.failureType()).isEqualTo(ProviderFailureType.RATE_LIMITED);
                    assertThat(failure.retryAfter()).hasValueSatisfying(
                            duration -> assertThat(duration).isGreaterThan(Duration.ofDays(365_000)));
                    assertThat(failure).hasNoCause();
                    assertThat(failure.getMessage()).doesNotContain(SECRET, "raw-provider-body");
                });
        fixture.server().verify();
    }

    @Test
    void normalizesTimeoutWithoutLeakingUnderlyingFailure() {
        ClientHttpRequestFactory requestFactory = (uri, method) -> {
            throw new ResourceAccessException("secret timeout body", new SocketTimeoutException("timed out"));
        };
        RestClient restClient = RestClient.builder()
                .baseUrl("https://ollama.com/api")
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + SECRET)
                .build();

        assertThatThrownBy(() -> new OllamaCloudProviderAdapter(restClient)
                        .execute(request(ProviderId.OLLAMA_CLOUD, "cloud-selected")))
                .isInstanceOfSatisfying(ProviderExecutionException.class, failure -> {
                    assertThat(failure.failureType()).isEqualTo(ProviderFailureType.TIMEOUT);
                    assertThat(failure).hasNoCause();
                    assertThat(failure.getMessage()).doesNotContain(SECRET, "secret timeout body");
                });
    }

    @Test
    void classifiesNonTimeoutResourceAccessFailureAsProviderUnavailable() {
        ClientHttpRequestFactory requestFactory = (uri, method) -> {
            throw new ResourceAccessException(
                    "secret transport body",
                    new ConnectException("api-key-secret connection refused"));
        };
        RestClient restClient = RestClient.builder()
                .baseUrl("https://ollama.com/api")
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + SECRET)
                .build();

        assertThatThrownBy(() -> new OllamaCloudProviderAdapter(restClient)
                        .execute(request(ProviderId.OLLAMA_CLOUD, "cloud-selected")))
                .isInstanceOfSatisfying(ProviderExecutionException.class, failure -> {
                    assertThat(failure.failureType()).isEqualTo(ProviderFailureType.PROVIDER_UNAVAILABLE);
                    assertThat(failure).hasNoCause();
                    assertThat(failure.getMessage()).doesNotContain(
                            SECRET, "secret transport body", "api-key-secret", "connection refused");
                });
    }

    @Test
    void requestsAndConsumesRealStreamingDeltasWithTerminalMetadata() {
        Fixture fixture = fixture();
        fixture.server().expect(requestTo("https://ollama.com/api/chat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "model":"cloud-selected",
                          "messages":[
                            {"role":"system","content":"system-policy-secret-marker"},
                            {"role":"user","content":"student-task-secret-marker"}
                          ],
                          "stream":true,
                          "format":"json",
                          "options":{"num_predict":64}
                        }
                        """))
                .andRespond(withSuccess("""
                        {"model":"cloud-actual","created_at":"2026-09-22T10:15:30.000Z","message":{"role":"assistant","content":"{\\\"answer\\\":","thinking":"provider-only reasoning"},"done":false}
                        {"model":"cloud-actual","created_at":"2026-09-22T10:15:30.100Z","message":{"role":"assistant","content":"\\\"untrusted\\\"}","tool_calls":[]},"done":false}
                        {"model":"cloud-actual","created_at":"2026-09-22T10:15:30.200Z","message":{"role":"assistant","content":""},"done":true,"done_reason":"stop","total_duration":1800000000,"load_duration":120000000,"prompt_eval_count":12,"prompt_eval_duration":480000000,"eval_count":5,"eval_duration":1200000000}
                        """, MediaType.APPLICATION_NDJSON));
        List<ProviderStreamEvent> events = new ArrayList<>();

        fixture.adapter().stream(request(ProviderId.OLLAMA_CLOUD, "cloud-selected")).consume(events::add);

        assertThat(events.subList(0, 2)).containsExactly(
                new ProviderTextDelta("{\"answer\":"),
                new ProviderTextDelta("\"untrusted\"}"));
        assertThat(events.get(2)).isInstanceOfSatisfying(ProviderStreamCompleted.class, completed -> {
            assertThat(completed.providerId()).isEqualTo(ProviderId.OLLAMA_CLOUD);
            assertThat(completed.modelId()).isEqualTo("cloud-actual");
            assertThat(completed.usage().inputTokens()).contains(12);
            assertThat(completed.usage().outputTokens()).contains(5);
            assertThat(completed.finishReason()).contains("stop");
        });
        fixture.server().verify();
    }

    @Test
    void malformedRequiredStreamingContentFailsClosedDespiteProviderMetadata() {
        Fixture fixture = fixture();
        fixture.server().expect(requestTo("https://ollama.com/api/chat"))
                .andRespond(withSuccess("""
                        {"model":"cloud-actual","created_at":"2026-09-22T10:15:30.000Z","message":{"role":"assistant","content":{"unexpected":"object"},"thinking":"provider-only reasoning"},"done":false}
                        """, MediaType.APPLICATION_NDJSON));

        assertThatThrownBy(() -> fixture.adapter()
                        .stream(request(ProviderId.OLLAMA_CLOUD, "cloud-selected"))
                        .consume(ignored -> {}))
                .isInstanceOfSatisfying(ProviderExecutionException.class, failure -> {
                    assertThat(failure.failureType()).isEqualTo(ProviderFailureType.INVALID_RESPONSE);
                    assertThat(failure).hasNoCause();
                });
        fixture.server().verify();
    }

    @Test
    void preservesApplicationFailureThrownByStreamConsumer() {
        Fixture fixture = fixture();
        fixture.server().expect(requestTo("https://ollama.com/api/chat"))
                .andRespond(withSuccess("""
                        {"model":"cloud-actual","message":{"role":"assistant","content":"valid text delta"},"done":false}
                        {"model":"cloud-actual","message":{"role":"assistant","content":""},"done":true,"done_reason":"stop"}
                        """, MediaType.APPLICATION_NDJSON));
        RuntimeException marker = new RuntimeException("consumer-marker");

        assertThatThrownBy(() -> fixture.adapter()
                        .stream(request(ProviderId.OLLAMA_CLOUD, "cloud-selected"))
                        .consume(ignored -> {
                            throw marker;
                        }))
                .isSameAs(marker)
                .isNotInstanceOf(ProviderExecutionException.class);
        fixture.server().verify();
    }

    @Test
    void normalizesStreamingHttpFailureWithoutLeakingSecretOrRawBody() {
        Fixture fixture = fixture();
        fixture.server().expect(requestTo("https://ollama.com/api/chat"))
                .andRespond(withRawStatus(503)
                        .body("raw-provider-body " + SECRET)
                        .contentType(MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> fixture.adapter()
                        .stream(request(ProviderId.OLLAMA_CLOUD, "cloud-selected"))
                        .consume(ignored -> {}))
                .isInstanceOfSatisfying(ProviderExecutionException.class, failure -> {
                    assertThat(failure.failureType()).isEqualTo(ProviderFailureType.PROVIDER_UNAVAILABLE);
                    assertThat(failure).hasNoCause();
                    assertThat(failure.getMessage()).doesNotContain(SECRET, "raw-provider-body");
                });
        fixture.server().verify();
    }

    private static void assertHttpFailure(int status, ProviderFailureType expected) {
        Fixture fixture = fixture();
        fixture.server().expect(requestTo("https://ollama.com/api/chat"))
                .andRespond(withRawStatus(status)
                        .body("raw-provider-body " + SECRET)
                        .contentType(MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> fixture.adapter().execute(request(ProviderId.OLLAMA_CLOUD, "cloud-selected")))
                .isInstanceOfSatisfying(ProviderExecutionException.class, failure -> {
                    assertThat(failure.failureType()).isEqualTo(expected);
                    assertThat(failure).hasNoCause();
                    assertThat(failure.getMessage()).doesNotContain(SECRET, "raw-provider-body");
                });
        fixture.server().verify();
    }

    private static void assertFailure(OllamaCloudProviderAdapter adapter, ProviderFailureType expected) {
        assertThatThrownBy(() -> adapter.execute(request(ProviderId.OLLAMA_CLOUD, "cloud-selected")))
                .isInstanceOfSatisfying(ProviderExecutionException.class, failure -> {
                    assertThat(failure.failureType()).isEqualTo(expected);
                    assertThat(failure).hasNoCause();
                });
    }

    private static Fixture fixture() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://ollama.com/api")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + SECRET);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Fixture(new OllamaCloudProviderAdapter(builder.build()), server);
    }

    private record Fixture(OllamaCloudProviderAdapter adapter, MockRestServiceServer server) {}
}
