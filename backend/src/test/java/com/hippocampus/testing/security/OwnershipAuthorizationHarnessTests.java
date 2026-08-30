package com.hippocampus.testing.security;

import static com.hippocampus.testing.security.OwnershipAssertions.collectionContainsOwnedAndExcludesForeign;
import static com.hippocampus.testing.security.OwnershipAssertions.forbiddenWithoutForeignData;
import static com.hippocampus.testing.security.OwnershipAssertions.notFoundWithoutForeignData;
import static com.hippocampus.testing.security.OwnershipTestRequests.authenticatedAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.WebApplicationContext;

import com.hippocampus.identity.infrastructure.persistence.UserRepository;
import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.testing.PostgresIntegrationTestSupport;

class OwnershipAuthorizationHarnessTests extends PostgresIntegrationTestSupport {
    private static final String RESOURCE_A_MARKER = "OWNERSHIP_USER_A_RESOURCE_74192";
    private static final String RESOURCE_B_MARKER = "OWNERSHIP_USER_B_RESOURCE_58316";
    private static final String REPLACEMENT_MARKER = "UNAUTHORIZED_REPLACEMENT_91435";

    @BeforeEach
    void resetDatabase() throws Exception {
        resetPostgresSchema();
    }

    @Test
    void ownerAccessReturnsResourceAndIdentityResolvedThroughCurrentUser() throws Exception {
        try (Fixture fixture = fixture("owner-success")) {
            fixture.mvc().perform(get("/api/test/ownership/forbidden/{id}", fixture.resourceA().resourceId())
                            .with(authenticatedAs(fixture.users().userA())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resourceId").value(fixture.resourceA().resourceId().toString()))
                    .andExpect(jsonPath("$.marker").value(RESOURCE_A_MARKER))
                    .andExpect(jsonPath("$.resolvedUserId").value(fixture.users().userA().userId().toString()));
        }
    }

    @Test
    void forbiddenPolicyIsExactAndLeaksNoForeignData() throws Exception {
        try (Fixture fixture = fixture("strict-forbidden")) {
            fixture.mvc().perform(get("/api/test/ownership/forbidden/{id}", fixture.resourceA().resourceId())
                            .with(authenticatedAs(fixture.users().userB())))
                    .andExpect(forbiddenWithoutForeignData(
                            fixture.resourceA().resourceId().toString(), RESOURCE_A_MARKER))
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.code").value("TEST_OWNERSHIP_FORBIDDEN"));

            MvcResult hidden = fixture.mvc().perform(
                            get("/api/test/ownership/hidden/{id}", fixture.resourceA().resourceId())
                                    .with(authenticatedAs(fixture.users().userB())))
                    .andReturn();
            ResultMatcher matcher = forbiddenWithoutForeignData(
                    fixture.resourceA().resourceId().toString(), RESOURCE_A_MARKER);
            assertThatThrownBy(() -> matcher.match(hidden)).isInstanceOf(AssertionError.class);
        }
    }

    @Test
    void hiddenPolicyIsExactAndLeaksNoForeignData() throws Exception {
        try (Fixture fixture = fixture("strict-hidden")) {
            fixture.mvc().perform(get("/api/test/ownership/hidden/{id}", fixture.resourceA().resourceId())
                            .with(authenticatedAs(fixture.users().userB())))
                    .andExpect(notFoundWithoutForeignData(
                            fixture.resourceA().resourceId().toString(), RESOURCE_A_MARKER))
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.code").value("TEST_OWNERSHIP_NOT_FOUND"));

            MvcResult forbidden = fixture.mvc().perform(
                            get("/api/test/ownership/forbidden/{id}", fixture.resourceA().resourceId())
                                    .with(authenticatedAs(fixture.users().userB())))
                    .andReturn();
            ResultMatcher matcher = notFoundWithoutForeignData(
                    fixture.resourceA().resourceId().toString(), RESOURCE_A_MARKER);
            assertThatThrownBy(() -> matcher.match(forbidden)).isInstanceOf(AssertionError.class);
        }
    }

    @Test
    void collectionReturnsCallerDataAndExcludesForeignData() throws Exception {
        try (Fixture fixture = fixture("collection-isolation")) {
            fixture.mvc().perform(get("/api/test/ownership/resources")
                            .with(authenticatedAs(fixture.users().userB())))
                    .andExpect(status().isOk())
                    .andExpect(collectionContainsOwnedAndExcludesForeign(
                            RESOURCE_B_MARKER, fixture.resourceA().resourceId().toString(), RESOURCE_A_MARKER))
                    .andExpect(jsonPath("$[0].resourceId").value(fixture.resourceB().resourceId().toString()))
                    .andExpect(jsonPath("$[0].marker").value(RESOURCE_B_MARKER));
        }
    }

    @Test
    void forbiddenMutationPreservesAuthoritativeStateAndOwnerCanMutate() throws Exception {
        try (Fixture fixture = fixture("mutation-protection")) {
            SyntheticOwnedResource before = fixture.store().find(fixture.resourceA().resourceId()).orElseThrow();
            MvcResult denied = fixture.mvc().perform(
                            patch("/api/test/ownership/forbidden/{id}", fixture.resourceA().resourceId())
                                    .with(authenticatedAs(fixture.users().userB())).with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"marker\":\"" + REPLACEMENT_MARKER + "\"}"))
                    .andExpect(forbiddenWithoutForeignData(
                            fixture.resourceA().resourceId().toString(), RESOURCE_A_MARKER))
                    .andReturn();
            SyntheticOwnedResource after = fixture.store().find(fixture.resourceA().resourceId()).orElseThrow();
            assertThat(after).isEqualTo(before);
            assertThat(denied.getResponse().getContentAsString()).doesNotContain(REPLACEMENT_MARKER);

            fixture.mvc().perform(patch("/api/test/ownership/forbidden/{id}", fixture.resourceA().resourceId())
                            .with(authenticatedAs(fixture.users().userA())).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"marker\":\"AUTHORIZED_REPLACEMENT_28164\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.marker").value("AUTHORIZED_REPLACEMENT_28164"));
        }
    }

    @Test
    void distinctPersistedUsersResolveAsDistinctAuthoritativeIdentities() throws Exception {
        try (Fixture fixture = fixture("authoritative-identities")) {
            assertThat(fixture.users().userA().userId()).isNotEqualTo(fixture.users().userB().userId());
            assertThat(fixture.userRepository().existsById(fixture.users().userA().userId())).isTrue();
            assertThat(fixture.userRepository().existsById(fixture.users().userB().userId())).isTrue();
            fixture.mvc().perform(get("/api/test/ownership/identity")
                            .with(authenticatedAs(fixture.users().userA())))
                    .andExpect(content().string(fixture.users().userA().userId().toString()));
            fixture.mvc().perform(get("/api/test/ownership/identity")
                            .with(authenticatedAs(fixture.users().userB())))
                    .andExpect(content().string(fixture.users().userB().userId().toString()));
        }
    }

    @Test
    void ownershipAssertionsRejectMissingForeignMarkers() {
        assertThatThrownBy(OwnershipAssertions::forbiddenWithoutForeignData).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(OwnershipAssertions::notFoundWithoutForeignData).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> collectionContainsOwnedAndExcludesForeign("OWNED"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ownershipAssertionsRejectBlankForeignMarkers() {
        assertThatThrownBy(() -> forbiddenWithoutForeignData(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> notFoundWithoutForeignData("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> collectionContainsOwnedAndExcludesForeign("OWNED", "\t"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ownershipAssertionsRejectNullForeignMarkersAndBlankOwnedMarker() {
        assertThatThrownBy(() -> forbiddenWithoutForeignData(new String[] {null}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> notFoundWithoutForeignData((String[]) null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> collectionContainsOwnedAndExcludesForeign(" ", "FOREIGN"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> collectionContainsOwnedAndExcludesForeign("OWNED", new String[] {null}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Fixture fixture(String scenario) {
        ConfigurableApplicationContext context = startApplicationWithFlyway(TestOwnershipConfiguration.class);
        UserRepository repository = context.getBean(UserRepository.class);
        OwnershipTestUsers users = OwnershipTestUsers.persistWith(repository, scenario);
        SyntheticOwnedResourceStore store = context.getBean(SyntheticOwnedResourceStore.class);
        SyntheticOwnedResource resourceA = new SyntheticOwnedResource(UUID.randomUUID(), users.userA().userId(), RESOURCE_A_MARKER);
        SyntheticOwnedResource resourceB = new SyntheticOwnedResource(UUID.randomUUID(), users.userB().userId(), RESOURCE_B_MARKER);
        store.replaceAll(resourceA, resourceB);
        MockMvc mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context).apply(springSecurity()).build();
        return new Fixture(context, mvc, repository, users, store, resourceA, resourceB);
    }

    private record Fixture(ConfigurableApplicationContext context, MockMvc mvc, UserRepository userRepository,
            OwnershipTestUsers users, SyntheticOwnedResourceStore store, SyntheticOwnedResource resourceA,
            SyntheticOwnedResource resourceB) implements AutoCloseable {
        @Override public void close() { context.close(); }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestOwnershipConfiguration {
        @Bean SyntheticOwnedResourceStore store() { return new SyntheticOwnedResourceStore(); }
        @Bean SyntheticOwnershipApplication application(CurrentUser currentUser, SyntheticOwnedResourceStore store) {
            return new SyntheticOwnershipApplication(currentUser, store);
        }
        @Bean SyntheticOwnershipController controller(SyntheticOwnershipApplication application) {
            return new SyntheticOwnershipController(application);
        }
        @Bean SyntheticOwnershipProblemHandler problems() { return new SyntheticOwnershipProblemHandler(); }
    }

    record SyntheticOwnedResource(UUID resourceId, UUID ownerId, String marker) {}
    record SyntheticResourceResponse(UUID resourceId, String marker, UUID resolvedUserId) {
        static SyntheticResourceResponse from(SyntheticOwnedResource resource, UUID userId) {
            return new SyntheticResourceResponse(resource.resourceId(), resource.marker(), userId);
        }
    }
    record RenameRequest(String marker) {}

    static final class SyntheticOwnedResourceStore {
        private final Map<UUID, SyntheticOwnedResource> resources = new ConcurrentHashMap<>();
        void replaceAll(SyntheticOwnedResource... replacements) {
            resources.clear();
            for (SyntheticOwnedResource resource : replacements) resources.put(resource.resourceId(), resource);
        }
        Optional<SyntheticOwnedResource> find(UUID id) { return Optional.ofNullable(resources.get(id)); }
        Optional<SyntheticOwnedResource> findOwned(UUID id, UUID ownerId) {
            return find(id).filter(resource -> resource.ownerId().equals(ownerId));
        }
        List<SyntheticOwnedResource> findAllOwned(UUID ownerId) {
            return resources.values().stream().filter(resource -> resource.ownerId().equals(ownerId))
                    .sorted(Comparator.comparing(SyntheticOwnedResource::resourceId)).toList();
        }
        SyntheticOwnedResource rename(SyntheticOwnedResource resource, String marker) {
            SyntheticOwnedResource renamed = new SyntheticOwnedResource(resource.resourceId(), resource.ownerId(), marker);
            resources.put(renamed.resourceId(), renamed);
            return renamed;
        }
    }

    static final class SyntheticOwnershipApplication {
        private final CurrentUser currentUser;
        private final SyntheticOwnedResourceStore store;
        SyntheticOwnershipApplication(CurrentUser currentUser, SyntheticOwnedResourceStore store) {
            this.currentUser = currentUser; this.store = store;
        }
        SyntheticResourceResponse getForbidden(UUID id) {
            UUID userId = currentUser.authenticatedUser().userId();
            SyntheticOwnedResource resource = store.find(id).orElseThrow(SyntheticNotFound::new);
            requireOwner(resource, userId);
            return SyntheticResourceResponse.from(resource, userId);
        }
        SyntheticResourceResponse getHidden(UUID id) {
            UUID userId = currentUser.authenticatedUser().userId();
            return SyntheticResourceResponse.from(store.findOwned(id, userId).orElseThrow(SyntheticNotFound::new), userId);
        }
        List<SyntheticResourceResponse> list() {
            UUID userId = currentUser.authenticatedUser().userId();
            return store.findAllOwned(userId).stream().map(resource -> SyntheticResourceResponse.from(resource, userId)).toList();
        }
        SyntheticResourceResponse rename(UUID id, String marker) {
            UUID userId = currentUser.authenticatedUser().userId();
            SyntheticOwnedResource resource = store.find(id).orElseThrow(SyntheticNotFound::new);
            requireOwner(resource, userId);
            return SyntheticResourceResponse.from(store.rename(resource, marker), userId);
        }
        UUID currentUserId() { return currentUser.authenticatedUser().userId(); }
        private static void requireOwner(SyntheticOwnedResource resource, UUID userId) {
            if (!resource.ownerId().equals(userId)) throw new SyntheticForbidden();
        }
    }

    @RestController
    @RequestMapping("/api/test/ownership")
    static final class SyntheticOwnershipController {
        private final SyntheticOwnershipApplication application;
        SyntheticOwnershipController(SyntheticOwnershipApplication application) { this.application = application; }
        @GetMapping("/forbidden/{id}") SyntheticResourceResponse forbidden(@PathVariable UUID id) { return application.getForbidden(id); }
        @GetMapping("/hidden/{id}") SyntheticResourceResponse hidden(@PathVariable UUID id) { return application.getHidden(id); }
        @GetMapping("/resources") List<SyntheticResourceResponse> list() { return application.list(); }
        @PatchMapping("/forbidden/{id}") SyntheticResourceResponse rename(@PathVariable UUID id, @RequestBody RenameRequest request) {
            return application.rename(id, request.marker());
        }
        @GetMapping("/identity") String identity() { return application.currentUserId().toString(); }
    }

    @RestControllerAdvice(assignableTypes = SyntheticOwnershipController.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    static final class SyntheticOwnershipProblemHandler {
        @ExceptionHandler(SyntheticForbidden.class) ResponseEntity<ProblemDetail> forbidden() {
            return problem(HttpStatus.FORBIDDEN, "TEST_OWNERSHIP_FORBIDDEN", "Synthetic resource access is forbidden.");
        }
        @ExceptionHandler(SyntheticNotFound.class) ResponseEntity<ProblemDetail> notFound() {
            return problem(HttpStatus.NOT_FOUND, "TEST_OWNERSHIP_NOT_FOUND", "Synthetic resource was not found.");
        }
        private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
            problem.setType(URI.create("about:blank")); problem.setTitle(status.getReasonPhrase()); problem.setProperty("code", code);
            return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
        }
    }
    static final class SyntheticForbidden extends RuntimeException {}
    static final class SyntheticNotFound extends RuntimeException {}
}
