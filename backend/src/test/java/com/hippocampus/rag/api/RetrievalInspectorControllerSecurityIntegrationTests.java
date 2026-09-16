package com.hippocampus.rag.api;

import static com.hippocampus.testing.security.OwnershipTestRequests.authenticatedAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.hippocampus.rag.application.InspectRetrieval;
import com.hippocampus.testing.PostgresIntegrationTestSupport;
import com.hippocampus.testing.security.OwnershipTestUser;

class RetrievalInspectorControllerSecurityIntegrationTests extends PostgresIntegrationTestSupport {
    private static final String BODY = """
            {"topicId":"%s","query":"posterior cord","groundingMode":"STRICT_SOURCE"}
            """;

    @BeforeEach void reset() throws SQLException { resetPostgresSchema(); }

    @Test
    void localEnabledEndpointRequiresAuthenticationAndCsrfThenReturnsPrivacySafeInspection() throws Exception {
        try (var context = start("local,test", true)) {
            MockMvc mvc = mvc(context);
            UUID topic = UUID.randomUUID();
            String body = BODY.formatted(topic);
            OwnershipTestUser user = new OwnershipTestUser(UUID.randomUUID(), "inspector@example.test");

            mvc.perform(post("/api/dev/rag/inspect").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

            mvc.perform(post("/api/dev/rag/inspect").with(authenticatedAs(user))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("CSRF_VALIDATION_FAILED"));

            mvc.perform(post("/api/dev/rag/inspect").with(authenticatedAs(user)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"topicId":"%s","query":"posterior cord","groundingMode":"STRICT_SOURCE",
                                     "userId":"%s","materialVersionIds":["%s"],"fusionWeights":{"vector":999}}
                                    """.formatted(topic, UUID.randomUUID(), UUID.randomUUID())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.query").value("posterior cord"))
                    .andExpect(jsonPath("$.scope.topicId").value(topic.toString()))
                    .andExpect(jsonPath("$.scope.targets").isEmpty())
                    .andExpect(jsonPath("$.vector.status").value("UNAVAILABLE_EMPTY_SCOPE"))
                    .andExpect(jsonPath("$.userId").doesNotExist())
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("PRIVATE SOURCE CHUNK SENTINEL"))));
        }
    }

    @Test
    void validatesOnlyTheNarrowRequestContract() throws Exception {
        try (var context = start("local,test", true)) {
            MockMvc mvc = mvc(context);
            OwnershipTestUser user = new OwnershipTestUser(UUID.randomUUID(), "validation@example.test");
            for (String invalid : new String[] {
                    "{}",
                    "{\"topicId\":\"%s\",\"query\":\"   \",\"groundingMode\":\"STRICT_SOURCE\"}"
                            .formatted(UUID.randomUUID()),
                    "{\"topicId\":\"%s\",\"query\":\"q\"}"
                            .formatted(UUID.randomUUID())}) {
                mvc.perform(post("/api/dev/rag/inspect").with(authenticatedAs(user)).with(csrf())
                                .contentType(MediaType.APPLICATION_JSON).content(invalid))
                        .andExpect(status().isBadRequest());
            }
        }
    }

    @Test
    void componentIsAbsentWhenDisabledOrOutsideLocalProfile() {
        try (var disabled = start("local,test", false)) {
            assertThat(disabled.getBeansOfType(InspectRetrieval.class)).isEmpty();
            assertThat(disabled.getBeansOfType(RetrievalInspectorController.class)).isEmpty();
        }
        try (var defaults = start("test", true)) {
            assertThat(defaults.getBeansOfType(InspectRetrieval.class)).isEmpty();
            assertThat(defaults.getBeansOfType(RetrievalInspectorController.class)).isEmpty();
        }
        try (var pilot = start("test,pilot", true)) {
            assertThat(pilot.getBeansOfType(InspectRetrieval.class)).isEmpty();
            assertThat(pilot.getBeansOfType(RetrievalInspectorController.class)).isEmpty();
        }
    }

    private static ConfigurableApplicationContext start(String profiles, boolean enabled) {
        return startApplicationWithFlywayAndArguments(new Class<?>[0],
                "--spring.profiles.active=" + profiles,
                "--hippocampus.rag.inspector.enabled=" + enabled,
                "--hippocampus.security.cors.allowed-origins=http://localhost:5173",
                "--hippocampus.materials.processing.recovery.enabled=false");
    }

    private static MockMvc mvc(ConfigurableApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup((WebApplicationContext) context)
                .apply(springSecurity()).build();
    }
}
