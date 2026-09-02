package com.hippocampus;

import static com.hippocampus.testing.security.OwnershipAssertions.notFoundWithoutForeignData;
import static com.hippocampus.testing.security.OwnershipTestRequests.authenticatedAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.hippocampus.identity.infrastructure.persistence.UserRepository;
import com.hippocampus.identity.infrastructure.security.HippocampusPrincipal;
import com.hippocampus.materials.MaterialUploadFixtures;
import com.hippocampus.materials.application.CreateUserSelectedMaterialTopicLink;
import com.hippocampus.materials.infrastructure.persistence.SpringDataMaterialRepository;
import com.hippocampus.materials.infrastructure.storage.filesystem.FileSystemBinaryObjectStore;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.shared.application.error.ApplicationNotFoundException;
import com.hippocampus.testing.PostgresIntegrationTestSupport;
import com.hippocampus.testing.security.OwnershipTestUser;
import com.hippocampus.testing.security.OwnershipTestUsers;

import tools.jackson.databind.ObjectMapper;

class Phase2EndToEndGateIntegrationTests extends PostgresIntegrationTestSupport {

    private static final String[] UPLOAD_ARGUMENTS = {
            "--hippocampus.materials.upload.max-file-size=256B",
            "--spring.servlet.multipart.max-file-size=256B",
            "--spring.servlet.multipart.max-request-size=512B"
    };

    @BeforeEach
    void resetDatabase() throws Exception {
        resetPostgresSchema();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void privateOrganizationUploadLinkDeleteAndCrossUserIsolationHoldTogether() throws Exception {
        try (ConfigurableApplicationContext context = startApplicationWithFlywayAndArguments(
                new Class<?>[] {StorageTestConfiguration.class}, UPLOAD_ARGUMENTS)) {
            OwnershipTestUsers users = OwnershipTestUsers.persistWith(
                    context.getBean(UserRepository.class), "phase2-end-to-end-gate");
            MockMvc mvc = mvc(context);
            ObjectMapper objectMapper = context.getBean(ObjectMapper.class);

            UUID subjectId = responseId(objectMapper, mvc.perform(post("/api/subjects")
                            .with(authenticatedAs(users.userA())).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Phase 2 Anatomy\",\"description\":\"Gate subject\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andReturn(), "id");

            UUID topicId = responseId(objectMapper, mvc.perform(post("/api/subjects/{subjectId}/topics", subjectId)
                            .with(authenticatedAs(users.userA())).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Upper Limb\",\"description\":\"Gate topic\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.subjectId").value(subjectId.toString()))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andReturn(), "id");

            MvcResult upload = mvc.perform(multipart("/api/materials")
                            .file(new MockMultipartFile(
                                    "file", "phase2-gate-notes.txt", "text/plain", MaterialUploadFixtures.text()))
                            .with(authenticatedAs(users.userA())).with(csrf()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.materialStatus").value("UPLOADED"))
                    .andExpect(jsonPath("$.processingStatus").value("UPLOADED"))
                    .andReturn();
            UUID materialId = responseId(objectMapper, upload, "materialId");
            UUID versionId = responseId(objectMapper, upload, "versionId");

            CreateUserSelectedMaterialTopicLink createLink = context.getBean(CreateUserSelectedMaterialTopicLink.class);
            authenticate(users.userA());
            var link = createLink.execute(new CreateUserSelectedMaterialTopicLink.Command(
                    topicId, materialId, versionId));
            assertThat(link.topicId()).isEqualTo(topicId);
            assertThat(link.materialId()).isEqualTo(materialId);
            assertThat(link.materialVersionId()).isEqualTo(versionId);
            assertThat(link.origin().name()).isEqualTo("USER_SELECTED");
            assertThat(link.status().name()).isEqualTo("ACTIVE");
            SecurityContextHolder.clearContext();

            mvc.perform(get("/api/subjects/{id}", subjectId).with(authenticatedAs(users.userA())))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/materials/{id}", materialId).with(authenticatedAs(users.userA())))
                    .andExpect(status().isOk());

            mvc.perform(get("/api/subjects/{id}", subjectId).with(authenticatedAs(users.userB())))
                    .andExpect(notFoundWithoutForeignData("Phase 2 Anatomy", "Gate subject"))
                    .andExpect(jsonPath("$.code").value("SUBJECT_NOT_FOUND"));
            mvc.perform(get("/api/materials/{id}", materialId).with(authenticatedAs(users.userB())))
                    .andExpect(notFoundWithoutForeignData("phase2-gate-notes.txt"))
                    .andExpect(jsonPath("$.code").value("MATERIAL_NOT_FOUND"));

            authenticate(users.userB());
            assertThatThrownBy(() -> createLink.execute(new CreateUserSelectedMaterialTopicLink.Command(
                    topicId, materialId, versionId)))
                    .isInstanceOf(ApplicationNotFoundException.class)
                    .hasMessage("Material was not found.");
            SecurityContextHolder.clearContext();

            mvc.perform(delete("/api/materials/{id}", materialId)
                            .with(authenticatedAs(users.userA())).with(csrf()))
                    .andExpect(status().isNoContent());
            mvc.perform(get("/api/materials/{id}", materialId).with(authenticatedAs(users.userA())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("MATERIAL_NOT_FOUND"));
            assertThat(context.getBean(SpringDataMaterialRepository.class).findById(materialId).orElseThrow().getStatus())
                    .isEqualTo("DELETED");

            authenticate(users.userA());
            assertThatThrownBy(() -> createLink.execute(new CreateUserSelectedMaterialTopicLink.Command(
                    topicId, materialId, null)))
                    .isInstanceOf(ApplicationNotFoundException.class)
                    .hasMessage("Material was not found.");
        }
    }

    private static UUID responseId(ObjectMapper objectMapper, MvcResult result, String field) throws Exception {
        String value = objectMapper.readTree(result.getResponse().getContentAsByteArray()).get(field).asText();
        return UUID.fromString(value);
    }

    private static void authenticate(OwnershipTestUser user) {
        var securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new HippocampusPrincipal(user.userId(), user.email()), null, List.of()));
        SecurityContextHolder.setContext(securityContext);
    }

    private static MockMvc mvc(ConfigurableApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup((WebApplicationContext) context)
                .apply(springSecurity()).build();
    }

    @Configuration(proxyBeanMethods = false)
    static class StorageTestConfiguration {
        @Bean("phase2GateStorageRoot")
        Path phase2GateStorageRoot() throws Exception {
            return Files.createTempDirectory("hippocampus-phase2-gate-");
        }

        @Bean
        BinaryObjectStore binaryObjectStore(@Qualifier("phase2GateStorageRoot") Path root) {
            return new FileSystemBinaryObjectStore(root);
        }
    }
}
