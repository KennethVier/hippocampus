package com.hippocampus.materials.api;

import static com.hippocampus.testing.security.OwnershipTestRequests.authenticatedAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.hippocampus.materials.MaterialUploadFixtures;
import com.hippocampus.materials.port.BinaryObjectKey;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.materials.port.MaterialUploadPersistence;
import com.hippocampus.testing.security.OwnershipTestUser;

@SpringBootTest(properties = {
        "hippocampus.materials.upload.max-file-size=256B",
        "spring.servlet.multipart.max-file-size=256B",
        "spring.servlet.multipart.max-request-size=512B"
})
@AutoConfigureMockMvc
@Import(MaterialUploadControllerWebTests.TestInfrastructure.class)
class MaterialUploadControllerWebTests {

    private static final OwnershipTestUser USER = new OwnershipTestUser(UUID.randomUUID(), "upload@example.test");

    @Autowired MockMvc mvc;
    @Autowired RecordingStore store;
    @Autowired RecordingPersistence persistence;

    @BeforeEach
    void reset() {
        store.calls = 0;
        persistence.calls = 0;
    }

    @Test
    void validUploadReturnsCreatedWithoutLocationOrPrivateMetadata() throws Exception {
        mvc.perform(multipart("/api/materials")
                        .file(file("source.pdf", "application/pdf", MaterialUploadFixtures.pdf()))
                        .with(authenticatedAs(USER)).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.materialType").value("PDF"))
                .andExpect(jsonPath("$.mimeType").value("application/pdf"))
                .andExpect(jsonPath("$.materialStatus").value("UPLOADED"))
                .andExpect(jsonPath("$.processingStatus").value("UPLOADED"))
                .andExpect(jsonPath("$.storageKey").doesNotExist())
                .andExpect(jsonPath("$.userId").doesNotExist());
        assertThat(store.calls).isOne();
        assertThat(persistence.upload.ownerId()).isEqualTo(USER.userId());
    }

    @Test
    void rejectsMissingRepeatedEmptyUnsupportedAndOversized() throws Exception {
        mvc.perform(multipart("/api/materials").with(authenticatedAs(USER)).with(csrf()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UPLOAD_FILE_REQUIRED"));
        mvc.perform(multipart("/api/materials")
                        .file(file("a.pdf", "application/pdf", MaterialUploadFixtures.pdf()))
                        .file(file("b.pdf", "application/pdf", MaterialUploadFixtures.pdf()))
                        .with(authenticatedAs(USER)).with(csrf()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UPLOAD_SINGLE_FILE_REQUIRED"));
        mvc.perform(multipart("/api/materials").file(file("empty.pdf", "application/pdf", new byte[0]))
                        .with(authenticatedAs(USER)).with(csrf()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UPLOAD_EMPTY"));
        mvc.perform(multipart("/api/materials").file(file("bad.zip", "application/pdf", MaterialUploadFixtures.zipLikeUnsupported()))
                        .with(authenticatedAs(USER)).with(csrf()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UPLOAD_TYPE_UNSUPPORTED"));
        mvc.perform(multipart("/api/materials").file(file("large.pdf", "application/pdf", new byte[257]))
                        .with(authenticatedAs(USER)).with(csrf()))
                .andExpect(status().isPayloadTooLarge());
        assertThat(store.calls).isZero();
        assertThat(persistence.calls).isZero();
    }

    @Test
    void rejectsDisguisedAndBasicInvalidContentWithSanitizedErrors() throws Exception {
        mvc.perform(multipart("/api/materials").file(file("notes.pdf", "application/pdf", MaterialUploadFixtures.text()))
                        .with(authenticatedAs(USER)).with(csrf()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UPLOAD_TYPE_MISMATCH"))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.message").value("The upload content type could not be verified."));

        mvc.perform(multipart("/api/materials").file(file("corrupt.pdf", "application/pdf", MaterialUploadFixtures.corruptPdf()))
                        .with(authenticatedAs(USER)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UPLOAD_CONTENT_INVALID"))
                .andExpect(jsonPath("$.message").value("The upload content could not be verified."));

        assertThat(store.calls).isZero();
        assertThat(persistence.calls).isZero();
    }

    @Test
    void acceptsSupportedContentWithGenericOrMissingDeclaration() throws Exception {
        mvc.perform(multipart("/api/materials")
                        .file(file("notes.bin", "application/octet-stream", MaterialUploadFixtures.text()))
                        .with(authenticatedAs(USER)).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.materialType").value("TEXT"))
                .andExpect(jsonPath("$.mimeType").value("text/plain"));

        mvc.perform(multipart("/api/materials")
                        .file(file("source.bin", null, MaterialUploadFixtures.pdf()))
                        .with(authenticatedAs(USER)).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.materialType").value("PDF"))
                .andExpect(jsonPath("$.mimeType").value("application/pdf"));
    }

    @Test
    void requiresAuthenticationAndCsrf() throws Exception {
        mvc.perform(multipart("/api/materials").file(file("source.pdf", "application/pdf", MaterialUploadFixtures.pdf())).with(csrf()))
                .andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/materials").file(file("source.pdf", "application/pdf", MaterialUploadFixtures.pdf()))
                        .with(authenticatedAs(USER)))
                .andExpect(status().isForbidden());
    }

    private static MockMultipartFile file(String name, String type, byte[] content) {
        return new MockMultipartFile("file", name, type, content);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestInfrastructure {
        @Bean RecordingStore binaryObjectStore() { return new RecordingStore(); }
        @Bean RecordingPersistence materialUploadPersistence() { return new RecordingPersistence(); }
    }

    static final class RecordingStore implements BinaryObjectStore {
        int calls;
        @Override public void put(BinaryObjectKey key, java.io.InputStream source, long contentLength) { calls++; }
        @Override public void get(BinaryObjectKey key, java.io.OutputStream destination) {}
        @Override public void delete(BinaryObjectKey key) {}
    }

    static final class RecordingPersistence implements MaterialUploadPersistence {
        int calls;
        InitialMaterial upload;
        @Override public CreatedMaterial createInitialMaterial(InitialMaterial upload) {
            calls++;
            this.upload = upload;
            return new CreatedMaterial(UUID.randomUUID(), UUID.randomUUID(), Instant.parse("2026-08-31T00:00:00Z"));
        }
    }
}
