package com.hippocampus.materials.api;

import static com.hippocampus.testing.security.OwnershipTestRequests.authenticatedAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.hippocampus.identity.infrastructure.persistence.UserRepository;
import com.hippocampus.materials.infrastructure.persistence.SpringDataMaterialRepository;
import com.hippocampus.materials.infrastructure.persistence.SpringDataMaterialVersionRepository;
import com.hippocampus.materials.infrastructure.storage.filesystem.FileSystemBinaryObjectStore;
import com.hippocampus.materials.port.BinaryObjectKey;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.testing.PostgresIntegrationTestSupport;
import com.hippocampus.testing.security.OwnershipTestUsers;

class MaterialUploadControllerIntegrationTests extends PostgresIntegrationTestSupport {

    private static final String[] UPLOAD_ARGUMENTS = {
            "--hippocampus.materials.upload.max-file-size=8B",
            "--hippocampus.materials.upload.max-request-size=32B"
    };

    @BeforeEach
    void resetDatabase() throws Exception {
        resetPostgresSchema();
    }

    @Test
    void validTypesCreateUploadedMetadataAndStoreRetrievableOriginal() throws Exception {
        try (ConfigurableApplicationContext context = context()) {
            OwnershipTestUsers users = OwnershipTestUsers.persistWith(context.getBean(UserRepository.class), "material-upload-valid");
            MockMvc mvc = mvc(context);
            for (String[] type : new String[][] {
                    {"application/pdf", "PDF"}, {"image/jpeg", "IMAGE"},
                    {"image/png", "IMAGE"}, {"text/plain", "TEXT"}}) {
                byte[] source = new byte[] {1, 2, 3};
                mvc.perform(multipart("/api/materials")
                                .file(new MockMultipartFile("file", "source.bin", type[0], source))
                                .with(authenticatedAs(users.userA())).with(csrf()))
                        .andExpect(status().isCreated())
                        .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                        .andExpect(jsonPath("$.materialType").value(type[1]))
                        .andExpect(jsonPath("$.materialStatus").value("UPLOADED"))
                        .andExpect(jsonPath("$.processingStatus").value("UPLOADED"))
                        .andExpect(jsonPath("$.storageKey").doesNotExist())
                        .andExpect(jsonPath("$.userId").doesNotExist());
            }

            SpringDataMaterialRepository materials = context.getBean(SpringDataMaterialRepository.class);
            SpringDataMaterialVersionRepository versions = context.getBean(SpringDataMaterialVersionRepository.class);
            assertThat(materials.findAll()).hasSize(4).allSatisfy(material -> {
                assertThat(material.getUserId()).isEqualTo(users.userA().userId());
                assertThat(material.getStorageKey()).isNull();
                assertThat(material.getActiveVersionId()).isNull();
            });
            assertThat(versions.findAll()).hasSize(4).allSatisfy(version -> {
                assertThat(version.getVersionNumber()).isOne();
                assertThat(version.getStorageKey()).matches("materials/[0-9a-f-]{36}/original");
                assertThat(version.getProcessingProgress()).isNull();
                assertThat(version.getActivatedAt()).isNull();
                ByteArrayOutputStream retrieved = new ByteArrayOutputStream();
                context.getBean(BinaryObjectStore.class).get(new BinaryObjectKey(version.getStorageKey()), retrieved);
                assertThat(retrieved.toByteArray()).containsExactly(1, 2, 3);
            });
        }
    }

    @Test
    void rejectsInvalidRequestsBeforeCreatingRows() throws Exception {
        try (ConfigurableApplicationContext context = context()) {
            OwnershipTestUsers users = OwnershipTestUsers.persistWith(context.getBean(UserRepository.class), "material-upload-invalid");
            MockMvc mvc = mvc(context);
            mvc.perform(multipart("/api/materials").with(authenticatedAs(users.userA())).with(csrf()))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UPLOAD_FILE_REQUIRED"));
            mvc.perform(multipart("/api/materials")
                            .file(file("a.pdf", "application/pdf", new byte[] {1}))
                            .file(file("b.pdf", "application/pdf", new byte[] {2}))
                            .with(authenticatedAs(users.userA())).with(csrf()))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UPLOAD_SINGLE_FILE_REQUIRED"));
            mvc.perform(multipart("/api/materials").file(file("empty.pdf", "application/pdf", new byte[0]))
                            .with(authenticatedAs(users.userA())).with(csrf()))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UPLOAD_EMPTY"));
            mvc.perform(multipart("/api/materials").file(file("bad.zip", "application/zip", new byte[] {1}))
                            .with(authenticatedAs(users.userA())).with(csrf()))
                    .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("UPLOAD_TYPE_UNSUPPORTED"));
            mvc.perform(multipart("/api/materials").file(file("large.pdf", "application/pdf", new byte[9]))
                            .with(authenticatedAs(users.userA())).with(csrf()))
                    .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("UPLOAD_TOO_LARGE"));
            assertThat(context.getBean(SpringDataMaterialRepository.class).count()).isZero();
            assertThat(context.getBean(SpringDataMaterialVersionRepository.class).count()).isZero();
        }
    }

    @Test
    void enforcesAuthenticationCsrfAndSeparatesSameFilenameBetweenUsers() throws Exception {
        try (ConfigurableApplicationContext context = context()) {
            OwnershipTestUsers users = OwnershipTestUsers.persistWith(context.getBean(UserRepository.class), "material-upload-security");
            MockMvc mvc = mvc(context);
            MockMultipartFile file = file("same.pdf", "application/pdf", new byte[] {1});
            mvc.perform(multipart("/api/materials").file(file).with(csrf())).andExpect(status().isUnauthorized());
            mvc.perform(multipart("/api/materials").file(file).with(authenticatedAs(users.userA())))
                    .andExpect(status().isForbidden());
            mvc.perform(multipart("/api/materials").file(file("same.pdf", "application/pdf", new byte[] {1}))
                            .with(authenticatedAs(users.userA())).with(csrf()))
                    .andExpect(status().isCreated());
            mvc.perform(multipart("/api/materials").file(file("same.pdf", "application/pdf", new byte[] {1}))
                            .with(authenticatedAs(users.userB())).with(csrf()))
                    .andExpect(status().isCreated());
            assertThat(context.getBean(SpringDataMaterialRepository.class).findAll())
                    .extracting(material -> material.getUserId()).containsExactlyInAnyOrder(users.userA().userId(), users.userB().userId());
            assertThat(context.getBean(SpringDataMaterialVersionRepository.class).findAll())
                    .extracting(version -> version.getStorageKey()).doesNotHaveDuplicates();
        }
    }

    private static MockMultipartFile file(String name, String type, byte[] content) {
        return new MockMultipartFile("file", name, type, content);
    }

    private static ConfigurableApplicationContext context() {
        return startApplicationWithFlywayAndArguments(new Class<?>[] {StorageTestConfiguration.class}, UPLOAD_ARGUMENTS);
    }

    private static MockMvc mvc(ConfigurableApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup((WebApplicationContext) context).apply(springSecurity()).build();
    }

    @Configuration(proxyBeanMethods = false)
    static class StorageTestConfiguration {
        @Bean BinaryObjectStore binaryObjectStore() throws Exception {
            return new FileSystemBinaryObjectStore(Files.createTempDirectory("hippocampus-upload-test-"));
        }
    }
}
