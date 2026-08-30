package com.hippocampus.learning.api;

import static com.hippocampus.testing.security.OwnershipAssertions.collectionContainsOwnedAndExcludesForeign;
import static com.hippocampus.testing.security.OwnershipAssertions.notFoundWithoutForeignData;
import static com.hippocampus.testing.security.OwnershipTestRequests.authenticatedAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

import com.hippocampus.identity.infrastructure.persistence.UserRepository;
import com.hippocampus.learning.infrastructure.persistence.LearningOrganizationStatus;
import com.hippocampus.learning.infrastructure.persistence.SpringDataSubjectRepository;
import com.hippocampus.learning.infrastructure.persistence.SubjectEntity;
import com.hippocampus.testing.PostgresIntegrationTestSupport;
import com.hippocampus.testing.security.OwnershipTestUsers;

class SubjectControllerIntegrationTests extends PostgresIntegrationTestSupport {

    @BeforeEach
    void resetDatabase() throws SQLException {
        resetPostgresSchema();
    }

    @Test
    void createUsesAuthenticatedOwnerPreservesNameAndEnforcesValidationCsrfAndConflicts() throws Exception {
        try (var context = startApplicationWithFlyway()) {
            var users = users(context, "subject-api-create");
            MockMvc mvc = mvc(context);

            mvc.perform(post("/api/subjects").with(authenticatedAs(users.userA())).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"  Anatomy  ","description":"Body","sortOrder":1,
                                     "userId":"%s"}
                                    """.formatted(users.userB().userId())))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern(
                            "/api/subjects/[0-9a-f-]+")))
                    .andExpect(jsonPath("$.name").value("  Anatomy  "))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.userId").doesNotExist());

            var repository = context.getBean(SpringDataSubjectRepository.class);
            assertThat(repository.findAll()).singleElement().satisfies(subject ->
                    assertThat(subject.getUserId()).isEqualTo(users.userA().userId()));

            for (String invalid : new String[] {"{}", "{\"name\":null}", "{\"name\":\"   \"}"}) {
                mvc.perform(post("/api/subjects").with(authenticatedAs(users.userA())).with(csrf())
                                .contentType(MediaType.APPLICATION_JSON).content(invalid))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
            }

            mvc.perform(post("/api/subjects").with(authenticatedAs(users.userA())).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  anatomy  \"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SUBJECT_NAME_CONFLICT"))
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("uq_subjects_user_lower_name"))));

            mvc.perform(post("/api/subjects").with(authenticatedAs(users.userB())).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  anatomy  \"}"))
                    .andExpect(status().isCreated());

            mvc.perform(post("/api/subjects").with(authenticatedAs(users.userA()))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"No CSRF\"}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void listIsOwnerAndActiveScopedBoundedAndDeterministicallyOrdered() throws Exception {
        try (var context = startApplicationWithFlyway()) {
            var users = users(context, "subject-api-list");
            var repository = context.getBean(SpringDataSubjectRepository.class);
            persist(repository, users.userA().userId(), "Zulu owned", null, 2, LearningOrganizationStatus.ACTIVE);
            persist(repository, users.userA().userId(), "alpha owned", null, 2, LearningOrganizationStatus.ACTIVE);
            persist(repository, users.userA().userId(), "Null owned", null, null, LearningOrganizationStatus.ACTIVE);
            persist(repository, users.userA().userId(), "Archived secret", null, 1, LearningOrganizationStatus.ARCHIVED);
            persist(repository, users.userB().userId(), "Foreign secret", null, 1, LearningOrganizationStatus.ACTIVE);
            MockMvc mvc = mvc(context);

            mvc.perform(get("/api/subjects?page=0&size=2").with(authenticatedAs(users.userA())))
                    .andExpect(collectionContainsOwnedAndExcludesForeign("alpha owned", "Foreign secret"))
                    .andExpect(jsonPath("$.items[0].name").value("alpha owned"))
                    .andExpect(jsonPath("$.items[1].name").value("Zulu owned"))
                    .andExpect(jsonPath("$.totalElements").value(3))
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("Archived secret"))));

            mvc.perform(get("/api/subjects?page=1&size=2").with(authenticatedAs(users.userA())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].name").value("Null owned"));
            mvc.perform(get("/api/subjects?size=101").with(authenticatedAs(users.userA())))
                    .andExpect(status().isBadRequest());
            mvc.perform(get("/api/subjects?page=-1").with(authenticatedAs(users.userA())))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void getConcealsForeignExistenceAndAllowsOwnerToReadArchived() throws Exception {
        try (var context = startApplicationWithFlyway()) {
            var users = users(context, "subject-api-get");
            var repository = context.getBean(SpringDataSubjectRepository.class);
            SubjectEntity archived = persist(repository, users.userA().userId(), "Archived protected",
                    "Private description", null, LearningOrganizationStatus.ARCHIVED);
            MockMvc mvc = mvc(context);

            mvc.perform(get("/api/subjects/{id}", archived.getId()).with(authenticatedAs(users.userA())))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"));
            mvc.perform(get("/api/subjects/{id}", archived.getId()).with(authenticatedAs(users.userB())))
                    .andExpect(notFoundWithoutForeignData("Archived protected", "Private description"))
                    .andExpect(jsonPath("$.code").value("SUBJECT_NOT_FOUND"));
            mvc.perform(get("/api/subjects/{id}", UUID.randomUUID()).with(authenticatedAs(users.userB())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SUBJECT_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("Subject was not found."));
            mvc.perform(get("/api/subjects/not-a-uuid").with(authenticatedAs(users.userA())))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void updateAllowsArchivedOwnerButForeignMutationAndOwnershipInjectionCannotChangeAuthority() throws Exception {
        try (var context = startApplicationWithFlyway()) {
            var users = users(context, "subject-api-update");
            var repository = context.getBean(SpringDataSubjectRepository.class);
            SubjectEntity target = persist(repository, users.userA().userId(), "Protected original",
                    "Original secret", 1, LearningOrganizationStatus.ARCHIVED);
            persist(repository, users.userA().userId(), "Duplicate", null, null, LearningOrganizationStatus.ACTIVE);
            MockMvc mvc = mvc(context);

            mvc.perform(put("/api/subjects/{id}", target.getId()).with(authenticatedAs(users.userB())).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Attacker changed\",\"description\":\"leak\"}"))
                    .andExpect(notFoundWithoutForeignData("Protected original", "Original secret"));
            SubjectEntity unchanged = repository.findById(target.getId()).orElseThrow();
            assertThat(unchanged.getName()).isEqualTo("Protected original");
            assertThat(unchanged.getStatus()).isEqualTo(LearningOrganizationStatus.ARCHIVED);

            mvc.perform(put("/api/subjects/{id}", target.getId()).with(authenticatedAs(users.userA())).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"Archived renamed","description":"Updated","sortOrder":7,
                                     "userId":"%s","status":"ACTIVE"}
                                    """.formatted(users.userB().userId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Archived renamed"))
                    .andExpect(jsonPath("$.status").value("ARCHIVED"))
                    .andExpect(jsonPath("$.userId").doesNotExist());
            SubjectEntity updated = repository.findById(target.getId()).orElseThrow();
            assertThat(updated.getUserId()).isEqualTo(users.userA().userId());
            assertThat(updated.getStatus()).isEqualTo(LearningOrganizationStatus.ARCHIVED);

            mvc.perform(put("/api/subjects/{id}", target.getId()).with(authenticatedAs(users.userA())).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"duplicate\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SUBJECT_NAME_CONFLICT"));
            mvc.perform(put("/api/subjects/{id}", target.getId()).with(authenticatedAs(users.userA()))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"No CSRF\"}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void archiveIsOwnedIdempotentAndUnauthenticatedRequestsAreRejected() throws Exception {
        try (var context = startApplicationWithFlyway()) {
            var users = users(context, "subject-api-archive");
            var repository = context.getBean(SpringDataSubjectRepository.class);
            SubjectEntity target = persist(repository, users.userA().userId(), "Archive protected",
                    "Archive secret", null, LearningOrganizationStatus.ACTIVE);
            MockMvc mvc = mvc(context);

            mvc.perform(post("/api/subjects/{id}/archive", target.getId())
                            .with(authenticatedAs(users.userB())).with(csrf()))
                    .andExpect(notFoundWithoutForeignData("Archive protected", "Archive secret"));
            assertThat(repository.findById(target.getId()).orElseThrow().getStatus())
                    .isEqualTo(LearningOrganizationStatus.ACTIVE);

            for (int attempt = 0; attempt < 2; attempt++) {
                mvc.perform(post("/api/subjects/{id}/archive", target.getId())
                                .with(authenticatedAs(users.userA())).with(csrf()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("ARCHIVED"));
            }
            mvc.perform(post("/api/subjects/{id}/archive", target.getId()).with(authenticatedAs(users.userA())))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/subjects")).andExpect(status().isUnauthorized());
            mvc.perform(post("/api/subjects").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Anonymous\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    private static OwnershipTestUsers users(ConfigurableApplicationContext context, String scenario) {
        return OwnershipTestUsers.persistWith(context.getBean(UserRepository.class), scenario);
    }

    private static MockMvc mvc(ConfigurableApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup((WebApplicationContext) context)
                .apply(springSecurity()).build();
    }

    private static SubjectEntity persist(
            SpringDataSubjectRepository repository,
            UUID ownerId,
            String name,
            String description,
            Integer sortOrder,
            LearningOrganizationStatus status) {
        SubjectEntity subject = new SubjectEntity(ownerId, name, status);
        subject.setDescription(description);
        subject.setSortOrder(sortOrder);
        return repository.saveAndFlush(subject);
    }
}
