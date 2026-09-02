package com.hippocampus.materials.api;

import static com.hippocampus.testing.security.OwnershipTestRequests.authenticatedAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.hippocampus.identity.infrastructure.persistence.UserRepository;
import com.hippocampus.materials.MaterialUploadFixtures;
import com.hippocampus.materials.infrastructure.persistence.SpringDataMaterialRepository;
import com.hippocampus.materials.infrastructure.persistence.SpringDataMaterialVersionRepository;
import com.hippocampus.materials.infrastructure.persistence.JpaMaterialUploadPersistence;
import com.hippocampus.materials.infrastructure.persistence.MaterialVersionEntity;
import com.hippocampus.materials.infrastructure.storage.filesystem.FileSystemBinaryObjectStore;
import com.hippocampus.materials.port.BinaryObjectKey;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.materials.port.MaterialUploadPersistence;
import com.hippocampus.testing.PostgresIntegrationTestSupport;
import com.hippocampus.testing.security.OwnershipTestUsers;

import io.micrometer.core.instrument.MeterRegistry;

class MaterialUploadControllerIntegrationTests extends PostgresIntegrationTestSupport {

    private static final String UPLOAD_ACCEPTED_METRIC = "hippocampus.materials.upload.accepted";
    private static final String UPLOAD_FAILED_METRIC = "hippocampus.materials.upload.failed";
    private static final String STATUS_TRANSITIONS_METRIC = "hippocampus.materials.status.transitions";

    private static final String[] UPLOAD_ARGUMENTS = {
            "--hippocampus.materials.upload.max-file-size=256B",
            "--spring.servlet.multipart.max-file-size=256B",
            "--spring.servlet.multipart.max-request-size=512B"
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
            for (Fixture fixture : supportedFixtures()) {
                mvc.perform(multipart("/api/materials")
                                .file(new MockMultipartFile("file", "source.bin", fixture.declaredMimeType(), fixture.bytes()))
                                .with(authenticatedAs(users.userA())).with(csrf()))
                        .andExpect(status().isCreated())
                        .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                        .andExpect(jsonPath("$.materialType").value(fixture.materialType()))
                        .andExpect(jsonPath("$.mimeType").value(fixture.detectedMimeType()))
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
                assertThat(material.getMimeType()).isIn("application/pdf", "image/jpeg", "image/png", "text/plain");
            });
            List<String> expectedObjects = supportedFixtures().stream()
                    .map(fixture -> Base64.getEncoder().encodeToString(fixture.bytes()))
                    .toList();
            assertThat(versions.findAll()).hasSize(4).allSatisfy(version -> {
                assertThat(version.getVersionNumber()).isOne();
                assertThat(version.getStorageKey()).matches("materials/[0-9a-f-]{36}/original");
                assertThat(version.getProcessingProgress()).isNull();
                assertThat(version.getActivatedAt()).isNull();
                ByteArrayOutputStream retrieved = new ByteArrayOutputStream();
                context.getBean(BinaryObjectStore.class).get(new BinaryObjectKey(version.getStorageKey()), retrieved);
                assertThat(Base64.getEncoder().encodeToString(retrieved.toByteArray())).isIn(expectedObjects);
            });
            MeterRegistry meterRegistry = context.getBean(MeterRegistry.class);
            assertThat(counterValue(meterRegistry, UPLOAD_ACCEPTED_METRIC)).isEqualTo(4);
            assertThat(counterValue(
                    meterRegistry, STATUS_TRANSITIONS_METRIC, "scope", "MATERIAL", "status", "UPLOADED"))
                    .isEqualTo(4);
            assertThat(counterValue(
                    meterRegistry, STATUS_TRANSITIONS_METRIC, "scope", "MATERIAL_VERSION", "status", "UPLOADED"))
                    .isEqualTo(4);
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
                            .file(file("a.pdf", "application/pdf", MaterialUploadFixtures.pdf()))
                            .file(file("b.pdf", "application/pdf", MaterialUploadFixtures.pdf()))
                            .with(authenticatedAs(users.userA())).with(csrf()))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UPLOAD_SINGLE_FILE_REQUIRED"));
            mvc.perform(multipart("/api/materials").file(file("empty.pdf", "application/pdf", new byte[0]))
                            .with(authenticatedAs(users.userA())).with(csrf()))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UPLOAD_EMPTY"));
            mvc.perform(multipart("/api/materials").file(file("bad.zip", "application/pdf", MaterialUploadFixtures.zipLikeUnsupported()))
                            .with(authenticatedAs(users.userA())).with(csrf()))
                    .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("UPLOAD_TYPE_UNSUPPORTED"));
            mvc.perform(multipart("/api/materials").file(file("large.pdf", "application/pdf", new byte[257]))
                            .with(authenticatedAs(users.userA())).with(csrf()))
                    .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("UPLOAD_TOO_LARGE"));
            assertThat(context.getBean(SpringDataMaterialRepository.class).count()).isZero();
            assertThat(context.getBean(SpringDataMaterialVersionRepository.class).count()).isZero();
            assertNoStoredObjects(context.getBean("uploadTestStorageRoot", Path.class));
            assertThat(counterValue(context.getBean(MeterRegistry.class), UPLOAD_ACCEPTED_METRIC)).isZero();
        }
    }

    @Test
    void rejectsDisguisedMimeCorruptContentAndPathLikeFilenameWithoutUnsafeSideEffects() throws Exception {
        try (ConfigurableApplicationContext context = context()) {
            OwnershipTestUsers users = OwnershipTestUsers.persistWith(context.getBean(UserRepository.class), "material-upload-fixtures");
            MockMvc mvc = mvc(context);

            mvc.perform(multipart("/api/materials").file(file("notes.pdf", "application/pdf", MaterialUploadFixtures.text()))
                            .with(authenticatedAs(users.userA())).with(csrf()))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(jsonPath("$.code").value("UPLOAD_TYPE_MISMATCH"));
            mvc.perform(multipart("/api/materials").file(file("corrupt.pdf", "application/pdf", MaterialUploadFixtures.corruptPdf()))
                            .with(authenticatedAs(users.userA())).with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("UPLOAD_CONTENT_INVALID"));

            assertThat(context.getBean(SpringDataMaterialRepository.class).count()).isZero();
            assertThat(context.getBean(SpringDataMaterialVersionRepository.class).count()).isZero();
            assertNoStoredObjects(context.getBean("uploadTestStorageRoot", Path.class));

            mvc.perform(multipart("/api/materials")
                            .file(file("../notes.pdf", "application/pdf", MaterialUploadFixtures.pdf()))
                            .with(authenticatedAs(users.userA())).with(csrf()))
                    .andExpect(status().isCreated());
            assertThat(context.getBean(SpringDataMaterialRepository.class).findAll()).singleElement()
                    .satisfies(material -> assertThat(material.getOriginalFilename()).isEqualTo("../notes.pdf"));
            assertThat(context.getBean(SpringDataMaterialVersionRepository.class).findAll()).singleElement()
                    .satisfies(version -> assertThat(version.getStorageKey())
                            .matches("materials/[0-9a-f-]{36}/original")
                            .doesNotContain("notes", "..", users.userA().userId().toString()));
        }
    }

    @Test
    void enforcesAuthenticationCsrfAndSeparatesSameFilenameBetweenUsers() throws Exception {
        try (ConfigurableApplicationContext context = context()) {
            OwnershipTestUsers users = OwnershipTestUsers.persistWith(context.getBean(UserRepository.class), "material-upload-security");
            MockMvc mvc = mvc(context);
            MockMultipartFile file = file("same.pdf", "application/pdf", MaterialUploadFixtures.pdf());
            mvc.perform(multipart("/api/materials").file(file).with(csrf())).andExpect(status().isUnauthorized());
            mvc.perform(multipart("/api/materials").file(file).with(authenticatedAs(users.userA())))
                    .andExpect(status().isForbidden());
            mvc.perform(multipart("/api/materials").file(file("same.pdf", "application/pdf", MaterialUploadFixtures.pdf()))
                            .with(authenticatedAs(users.userA())).with(csrf()))
                    .andExpect(status().isCreated());
            mvc.perform(multipart("/api/materials").file(file("same.pdf", "application/pdf", MaterialUploadFixtures.pdf()))
                            .with(authenticatedAs(users.userB())).with(csrf()))
                    .andExpect(status().isCreated());
            assertThat(context.getBean(SpringDataMaterialRepository.class).findAll())
                    .extracting(material -> material.getUserId()).containsExactlyInAnyOrder(users.userA().userId(), users.userB().userId());
            assertThat(context.getBean(SpringDataMaterialVersionRepository.class).findAll())
                    .extracting(version -> version.getStorageKey()).doesNotHaveDuplicates();
            assertThat(counterValue(context.getBean(MeterRegistry.class), UPLOAD_ACCEPTED_METRIC)).isEqualTo(2);
        }
    }

    @Test
    void persistenceFailureRollsBackRowsCleansStorageAndDoesNotEmitAcceptedTelemetry() throws Exception {
        try (ConfigurableApplicationContext context = startApplicationWithFlywayAndArguments(
                new Class<?>[] {StorageTestConfiguration.class, RollbackTestConfiguration.class}, UPLOAD_ARGUMENTS)) {
            OwnershipTestUsers users = OwnershipTestUsers.persistWith(
                    context.getBean(UserRepository.class), "material-upload-rollback");
            MockMvc mvc = mvc(context);

            mvc.perform(multipart("/api/materials")
                            .file(file("rollback.pdf", "application/pdf", MaterialUploadFixtures.pdf()))
                            .with(authenticatedAs(users.userA())).with(csrf()))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("UPLOAD_PERSISTENCE_FAILED"));

            assertThat(context.getBean(SpringDataMaterialRepository.class).count()).isZero();
            SpringDataMaterialVersionRepository actualVersions = context.getBean(
                    "springDataMaterialVersionRepository", SpringDataMaterialVersionRepository.class);
            assertThat(actualVersions.count()).isZero();
            assertNoStoredObjects(context.getBean("uploadTestStorageRoot", Path.class));
            MeterRegistry meterRegistry = context.getBean(MeterRegistry.class);
            assertThat(counterValue(meterRegistry, UPLOAD_ACCEPTED_METRIC)).isZero();
            assertThat(counterValue(
                    meterRegistry, UPLOAD_FAILED_METRIC, "reason", "UPLOAD_PERSISTENCE_FAILED"))
                    .isEqualTo(1);
        }
    }

    private static MockMultipartFile file(String name, String type, byte[] content) {
        return new MockMultipartFile("file", name, type, content);
    }

    private static double counterValue(MeterRegistry meterRegistry, String name, String... tags) {
        var counter = meterRegistry.find(name).tags(tags).counter();
        return counter == null ? 0 : counter.count();
    }

    private static ConfigurableApplicationContext context() {
        return startApplicationWithFlywayAndArguments(new Class<?>[] {StorageTestConfiguration.class}, UPLOAD_ARGUMENTS);
    }

    private static MockMvc mvc(ConfigurableApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup((WebApplicationContext) context).apply(springSecurity()).build();
    }

    @Configuration(proxyBeanMethods = false)
    static class StorageTestConfiguration {
        @Bean("uploadTestStorageRoot")
        Path uploadTestStorageRoot() throws Exception {
            return Files.createTempDirectory("hippocampus-upload-test-");
        }

        @Bean BinaryObjectStore binaryObjectStore(@Qualifier("uploadTestStorageRoot") Path root) {
            return new FileSystemBinaryObjectStore(root);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class RollbackTestConfiguration {
        @Bean("rollbackMaterialUploadPersistence")
        @Primary
        MaterialUploadPersistence rollbackMaterialUploadPersistence(
                SpringDataMaterialRepository materials,
                @Qualifier("springDataMaterialVersionRepository") SpringDataMaterialVersionRepository versions) {
            SpringDataMaterialVersionRepository failingVersions = mock(
                    SpringDataMaterialVersionRepository.class, delegatesTo(versions));
            doThrow(new DataIntegrityViolationException("forced version failure"))
                    .when(failingVersions).saveAndFlush(any(MaterialVersionEntity.class));
            return new JpaMaterialUploadPersistence(materials, failingVersions);
        }
    }

    private static List<Fixture> supportedFixtures() {
        return List.of(
                new Fixture("application/pdf", "application/pdf", "PDF", MaterialUploadFixtures.pdf()),
                new Fixture("image/jpeg", "image/jpeg", "IMAGE", MaterialUploadFixtures.jpeg()),
                new Fixture("image/png", "image/png", "IMAGE", MaterialUploadFixtures.png()),
                new Fixture("text/plain", "text/plain", "TEXT", MaterialUploadFixtures.text()));
    }

    private static void assertNoStoredObjects(Path root) throws IOException {
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            assertThat(paths.filter(Files::isRegularFile)).isEmpty();
        }
    }

    private record Fixture(String declaredMimeType, String detectedMimeType, String materialType, byte[] bytes) {}
}
