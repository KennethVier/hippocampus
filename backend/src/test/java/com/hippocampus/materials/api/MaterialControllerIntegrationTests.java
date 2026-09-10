package com.hippocampus.materials.api;

import static com.hippocampus.testing.security.OwnershipAssertions.collectionContainsOwnedAndExcludesForeign;
import static com.hippocampus.testing.security.OwnershipAssertions.notFoundWithoutForeignData;
import static com.hippocampus.testing.security.OwnershipTestRequests.authenticatedAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.nullValue;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.hippocampus.identity.infrastructure.persistence.UserRepository;
import com.hippocampus.materials.infrastructure.persistence.MaterialEntity;
import com.hippocampus.materials.infrastructure.persistence.MaterialVersionEntity;
import com.hippocampus.materials.infrastructure.persistence.DocumentNodeEntity;
import com.hippocampus.materials.infrastructure.persistence.SpringDataMaterialRepository;
import com.hippocampus.materials.infrastructure.persistence.SpringDataMaterialVersionRepository;
import com.hippocampus.materials.infrastructure.persistence.SpringDataDocumentNodeRepository;
import com.hippocampus.testing.PostgresIntegrationTestSupport;
import com.hippocampus.testing.security.OwnershipTestUser;
import com.hippocampus.testing.security.OwnershipTestUsers;
import com.hippocampus.shared.infrastructure.web.CorrelationIdFilter;
import org.springframework.test.util.ReflectionTestUtils;

import io.micrometer.core.instrument.MeterRegistry;

@ExtendWith(OutputCaptureExtension.class)
class MaterialControllerIntegrationTests extends PostgresIntegrationTestSupport {

    private static final String MATERIAL_DELETED_METRIC = "hippocampus.materials.deleted";
    private static final String STATUS_TRANSITIONS_METRIC = "hippocampus.materials.status.transitions";
    private static final String DELETE_CORRELATION_ID = "fa23fa40-e03c-46d5-909f-f42cba83c829";

    @BeforeEach
    void resetDatabase() throws Exception {
        resetPostgresSchema();
    }

    @Test
    void listIsOwnerScopedExcludesDeletedAndPaginates() throws Exception {
        try (ConfigurableApplicationContext context = startApplicationWithFlyway()) {
            OwnershipTestUsers users = OwnershipTestUsers.persistWith(
                    context.getBean(UserRepository.class), "material-management-list");
            SpringDataMaterialRepository materials = context.getBean(SpringDataMaterialRepository.class);

            createMaterial(materials, users.userA().userId(), "first.pdf", "UPLOADED");
            createMaterial(materials, users.userA().userId(), "second.pdf", "FAILED");
            createMaterial(materials, users.userA().userId(), "third.pdf", "UNSUPPORTED");
            createMaterial(materials, users.userA().userId(), "deleted.pdf", "DELETED");
            createMaterial(materials, users.userB().userId(), "foreign.pdf", "UPLOADED");

            MockMvc mvc = mvc(context);
            MvcResult visible = mvc.perform(get("/api/materials")
                            .param("size", "100")
                            .with(authenticatedAs(users.userA())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(100))
                    .andExpect(jsonPath("$.totalElements").value(3))
                    .andExpect(jsonPath("$.totalPages").value(1))
                    .andExpect(jsonPath("$.items.length()").value(3))
                    .andExpect(collectionContainsOwnedAndExcludesForeign("first.pdf", "deleted.pdf", "foreign.pdf"))
                    .andReturn();
            assertThat(visible.getResponse().getContentAsString())
                    .contains("first.pdf", "second.pdf", "third.pdf")
                    .doesNotContain("storageKey", "userId", "activeVersionId");

            mvc.perform(get("/api/materials").param("page", "0").param("size", "2")
                            .with(authenticatedAs(users.userA())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(2))
                    .andExpect(jsonPath("$.totalElements").value(3))
                    .andExpect(jsonPath("$.totalPages").value(2));
            mvc.perform(get("/api/materials").param("page", "1").param("size", "2")
                            .with(authenticatedAs(users.userA())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.totalElements").value(3));
            mvc.perform(get("/api/materials").param("page", "-1")
                            .with(authenticatedAs(users.userA())))
                    .andExpect(status().isBadRequest());
            mvc.perform(get("/api/materials").param("size", "101")
                            .with(authenticatedAs(users.userA())))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void detailConcealsForeignDeletedAndMissingMaterials() throws Exception {
        try (ConfigurableApplicationContext context = startApplicationWithFlyway()) {
            OwnershipTestUsers users = OwnershipTestUsers.persistWith(
                    context.getBean(UserRepository.class), "material-management-detail");
            SpringDataMaterialRepository materials = context.getBean(SpringDataMaterialRepository.class);
            MaterialEntity own = createMaterial(materials, users.userA().userId(), "own.pdf", "UPLOADED");
            MaterialEntity foreign = createMaterial(materials, users.userB().userId(), "foreign.pdf", "UPLOADED");
            MaterialEntity deleted = createMaterial(materials, users.userA().userId(), "deleted.pdf", "DELETED");

            MockMvc mvc = mvc(context);
            MvcResult ownResult = mvc.perform(get("/api/materials/{id}", own.getId())
                            .with(authenticatedAs(users.userA())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(own.getId().toString()))
                    .andExpect(jsonPath("$.title").value("own.pdf"))
                    .andExpect(jsonPath("$.status").value("UPLOADED"))
                    .andReturn();
            assertThat(ownResult.getResponse().getContentAsString())
                    .doesNotContain("storageKey", "userId", "activeVersionId");

            assertNotFound(mvc, foreign.getId(), users.userA(), "foreign.pdf");
            assertNotFound(mvc, deleted.getId(), users.userA(), "deleted.pdf");
            assertNotFound(mvc, UUID.randomUUID(), users.userA(), "foreign-marker-not-present");

            mvc.perform(get("/api/materials/{id}", own.getId()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void processingAndStructureAreOwnerScopedAndExposeAllowedProjection() throws Exception {
        try (ConfigurableApplicationContext context = startApplicationWithFlyway()) {
            OwnershipTestUsers users = OwnershipTestUsers.persistWith(
                    context.getBean(UserRepository.class), "material-management-processing");
            SpringDataMaterialRepository materials = context.getBean(SpringDataMaterialRepository.class);
            SpringDataMaterialVersionRepository versions = context.getBean(SpringDataMaterialVersionRepository.class);
            SpringDataDocumentNodeRepository nodes = context.getBean(SpringDataDocumentNodeRepository.class);

            MaterialEntity own = createMaterial(materials, users.userA().userId(), "structural.pdf", "PARTIALLY_READY");
            MaterialVersionEntity active = versions.saveAndFlush(new MaterialVersionEntity(own.getId(), 1, "PARTIALLY_READY"));
            active.setExtractionMethod("OCR");
            active.setExtractionQuality("LIMITED");
            versions.saveAndFlush(active);
            own.setActiveVersionId(active.getId());
            materials.saveAndFlush(own);

            DocumentNodeEntity root = insertNode(nodes, active.getId(), null,
                    com.hippocampus.materials.domain.DocumentNodeType.DOCUMENT, "Document", 1, 1, 8);
            DocumentNodeEntity second = insertNode(nodes, active.getId(), root.getId(),
                    com.hippocampus.materials.domain.DocumentNodeType.CHAPTER, "2 Second", 2, 5, 8);
            DocumentNodeEntity first = insertNode(nodes, active.getId(), root.getId(),
                    com.hippocampus.materials.domain.DocumentNodeType.CHAPTER, "1 First", 1, 1, 4);
            DocumentNodeEntity section = insertNode(nodes, active.getId(), first.getId(),
                    com.hippocampus.materials.domain.DocumentNodeType.SECTION, "1.1 Cells", 1, 2, 4);
            assertThat(second.getId()).isNotNull();

            MaterialEntity foreign = createMaterial(materials, users.userB().userId(), "foreign.pdf", "UPLOADED");

            MockMvc mvc = mvc(context);

            MvcResult processing = mvc.perform(get("/api/materials/{id}/processing", own.getId())
                            .with(authenticatedAs(users.userA())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.readiness").value("PARTIALLY_READY"))
                    .andExpect(jsonPath("$.stage").value(nullValue()))
                    .andExpect(jsonPath("$.progress").value(nullValue()))
                    .andExpect(jsonPath("$.limitation").value("Some parts of this material could not be fully processed."))
                    .andExpect(jsonPath("$.structureAvailable").value(true))
                    .andExpect(jsonPath("$.updatedAt").doesNotExist())
                    .andReturn();
            assertThat(processing.getResponse().getContentAsString())
                    .doesNotContain("OCR", "NATIVE", "updatedAt");

            mvc.perform(get("/api/materials/{id}/processing", foreign.getId())
                            .with(authenticatedAs(users.userA())))
                    .andExpect(notFoundWithoutForeignData("foreign.pdf"))
                    .andExpect(jsonPath("$.code").value("MATERIAL_NOT_FOUND"));

            mvc.perform(get("/api/materials/{id}/structure", own.getId())
                            .with(authenticatedAs(users.userA())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.available").value(true))
                    .andExpect(jsonPath("$.root.id").value(root.getId().toString()))
                    .andExpect(jsonPath("$.root.title").value("Document"))
                    .andExpect(jsonPath("$.root.nodeType").value("DOCUMENT"))
                    .andExpect(jsonPath("$.root.children.length()").value(2))
                    .andExpect(jsonPath("$.root.children[0].id").value(first.getId().toString()))
                    .andExpect(jsonPath("$.root.children[0].nodeType").value("CHAPTER"))
                    .andExpect(jsonPath("$.root.children[0].children.length()").value(1))
                    .andExpect(jsonPath("$.root.children[0].children[0].id").value(section.getId().toString()))
                    .andExpect(jsonPath("$.root.children[0].children[0].nodeType").value("SECTION"))
                    .andExpect(jsonPath("$.root.children[1].id").value(second.getId().toString()));

            mvc.perform(get("/api/materials/{id}/structure", foreign.getId())
                            .with(authenticatedAs(users.userA())))
                    .andExpect(notFoundWithoutForeignData("foreign.pdf"))
                    .andExpect(jsonPath("$.code").value("MATERIAL_NOT_FOUND"));
        }
    }

    @Test
    void processingReadsDurableJobProgressAndExposesOnlySafeStage() throws Exception {
        try (ConfigurableApplicationContext context = startApplicationWithFlyway()) {
            OwnershipTestUsers users = OwnershipTestUsers.persistWith(
                    context.getBean(UserRepository.class), "material-management-durable-progress");
            SpringDataMaterialRepository materials = context.getBean(SpringDataMaterialRepository.class);
            SpringDataMaterialVersionRepository versions = context.getBean(SpringDataMaterialVersionRepository.class);
            JdbcClient jdbc = context.getBean(JdbcClient.class);

            MaterialEntity own = createMaterial(materials, users.userA().userId(), "durable-progress.pdf", "PROCESSING");
            MaterialVersionEntity active = versions.saveAndFlush(new MaterialVersionEntity(own.getId(), 1, "PROCESSING"));
            own.setActiveVersionId(active.getId());
            materials.saveAndFlush(own);

            assertThat(active.getProcessingProgress()).isNull();
            jdbc.sql("""
                    INSERT INTO processing_jobs(
                        id,user_id,material_version_id,job_type,status,priority,progress,progress_current,progress_total,
                        attempt_count,max_attempts,locked_by,locked_at,processing_version,error_code,error_message,
                        created_at,updated_at)
                    VALUES (?,?,?,?,?,0,72.50,72,100,1,3,'worker-secret',CURRENT_TIMESTAMP,'v1',
                        'PRIVATE_ERROR','private retry detail',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                    """)
                    .param(UUID.randomUUID())
                    .param(users.userA().userId())
                    .param(active.getId())
                    .param("STRUCTURE_DETECT")
                    .param("RUNNING")
                    .update();

            MockMvc mvc = mvc(context);
            MvcResult processing = mvc.perform(get("/api/materials/{id}/processing", own.getId())
                            .with(authenticatedAs(users.userA())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.readiness").value("PROCESSING"))
                    .andExpect(jsonPath("$.stage").value("STRUCTURE_DETECTION"))
                    .andExpect(jsonPath("$.progress").value(72.5))
                    .andExpect(jsonPath("$.structureAvailable").value(false))
                    .andExpect(jsonPath("$.updatedAt").doesNotExist())
                    .andExpect(jsonPath("$.jobType").doesNotExist())
                    .andExpect(jsonPath("$.workerId").doesNotExist())
                    .andExpect(jsonPath("$.attemptCount").doesNotExist())
                    .andExpect(jsonPath("$.maxAttempts").doesNotExist())
                    .andExpect(jsonPath("$.errorCode").doesNotExist())
                    .andExpect(jsonPath("$.errorMessage").doesNotExist())
                    .andReturn();

            assertThat(processing.getResponse().getContentAsString())
                    .doesNotContain("STRUCTURE_DETECT", "worker-secret", "PRIVATE_ERROR", "private retry detail");
            assertThat(versions.findById(active.getId()).orElseThrow().getProcessingProgress()).isNull();
        }
    }

    @Test
    void structureUsesLatestVersionWhenNoActiveVersionAndReturnsNoStructureContract() throws Exception {
        try (ConfigurableApplicationContext context = startApplicationWithFlyway()) {
            OwnershipTestUsers users = OwnershipTestUsers.persistWith(
                    context.getBean(UserRepository.class), "material-management-latest-structure");
            SpringDataMaterialRepository materials = context.getBean(SpringDataMaterialRepository.class);
            SpringDataMaterialVersionRepository versions = context.getBean(SpringDataMaterialVersionRepository.class);
            SpringDataDocumentNodeRepository nodes = context.getBean(SpringDataDocumentNodeRepository.class);

            MaterialEntity own = createMaterial(materials, users.userA().userId(), "latest.pdf", "PROCESSING");
            MaterialVersionEntity historical = versions.saveAndFlush(new MaterialVersionEntity(own.getId(), 1, "FAILED"));
            MaterialVersionEntity latest = versions.saveAndFlush(new MaterialVersionEntity(own.getId(), 2, "PROCESSING"));
            DocumentNodeEntity root = insertNode(nodes, latest.getId(), null,
                    com.hippocampus.materials.domain.DocumentNodeType.DOCUMENT, "Latest document", 1, 1, 3);
            insertNode(nodes, historical.getId(), null,
                    com.hippocampus.materials.domain.DocumentNodeType.DOCUMENT, "Historical document", 1, 1, 1);

            MaterialEntity noStructure = createMaterial(materials, users.userA().userId(), "no-structure.pdf", "PROCESSING");
            versions.saveAndFlush(new MaterialVersionEntity(noStructure.getId(), 1, "PROCESSING"));

            MockMvc mvc = mvc(context);
            mvc.perform(get("/api/materials/{id}/structure", own.getId())
                            .with(authenticatedAs(users.userA())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.available").value(true))
                    .andExpect(jsonPath("$.root.id").value(root.getId().toString()))
                    .andExpect(jsonPath("$.root.title").value("Latest document"));

            mvc.perform(get("/api/materials/{id}/structure", noStructure.getId())
                            .with(authenticatedAs(users.userA())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.available").value(false))
                    .andExpect(jsonPath("$.root").value(nullValue()));
        }
    }

    @Test
    void deleteIsOwnerScopedCsrfProtectedIdempotentAndImmediatelyHidesMaterial(CapturedOutput output)
            throws Exception {
        try (ConfigurableApplicationContext context = startApplicationWithFlyway()) {
            OwnershipTestUsers users = OwnershipTestUsers.persistWith(
                    context.getBean(UserRepository.class), "material-management-delete");
            SpringDataMaterialRepository materials = context.getBean(SpringDataMaterialRepository.class);
            SpringDataMaterialVersionRepository versions = context.getBean(SpringDataMaterialVersionRepository.class);
            MaterialEntity own = createActiveMaterial(materials, versions, users.userA().userId(), "delete-me.pdf");
            var originalUpdatedAt = own.getUpdatedAt();
            MaterialEntity csrfProtected = createMaterial(materials, users.userA().userId(), "csrf.pdf", "UPLOADED");
            MaterialEntity foreign = createMaterial(materials, users.userB().userId(), "foreign.pdf", "UPLOADED");
            MeterRegistry meterRegistry = context.getBean(MeterRegistry.class);

            MockMvc mvc = mvc(context);
            mvc.perform(delete("/api/materials/{id}", csrfProtected.getId())
                            .with(authenticatedAs(users.userA())))
                    .andExpect(status().isForbidden());
            assertThat(materials.findById(csrfProtected.getId()).orElseThrow().getStatus()).isEqualTo("UPLOADED");

            mvc.perform(delete("/api/materials/{id}", foreign.getId())
                            .with(authenticatedAs(users.userA())).with(csrf()))
                    .andExpect(notFoundWithoutForeignData("foreign.pdf"))
                    .andExpect(jsonPath("$.code").value("MATERIAL_NOT_FOUND"));
            assertThat(materials.findById(foreign.getId()).orElseThrow().getStatus()).isEqualTo("UPLOADED");

            mvc.perform(delete("/api/materials/{id}", own.getId())
                            .header("X-Correlation-ID", DELETE_CORRELATION_ID)
                            .with(authenticatedAs(users.userA())).with(csrf()))
                    .andExpect(status().isNoContent())
                    .andExpect(header().string("X-Correlation-ID", DELETE_CORRELATION_ID));
            MaterialEntity deleted = materials.findById(own.getId()).orElseThrow();
            assertThat(deleted.getStatus()).isEqualTo("DELETED");
            assertThat(deleted.getActiveVersionId()).isNull();
            assertThat(deleted.getUpdatedAt()).isAfter(originalUpdatedAt);
            var deletedAt = deleted.getUpdatedAt();
            assertThat(versions.findAll()).hasSize(1);

            assertNotFound(mvc, own.getId(), users.userA(), "delete-me.pdf");
            mvc.perform(get("/api/materials").with(authenticatedAs(users.userA())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1));

            mvc.perform(delete("/api/materials/{id}", own.getId())
                            .with(authenticatedAs(users.userA())).with(csrf()))
                    .andExpect(status().isNoContent());
            assertThat(materials.findById(own.getId()).orElseThrow().getUpdatedAt()).isEqualTo(deletedAt);
            mvc.perform(delete("/api/materials/{id}", UUID.randomUUID())
                            .with(authenticatedAs(users.userA())).with(csrf()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("MATERIAL_NOT_FOUND"));
            mvc.perform(delete("/api/materials/{id}", csrfProtected.getId()).with(csrf()))
                    .andExpect(status().isUnauthorized());

            assertThat(counterValue(meterRegistry, MATERIAL_DELETED_METRIC)).isEqualTo(1);
            assertThat(counterValue(
                    meterRegistry, STATUS_TRANSITIONS_METRIC, "scope", "MATERIAL", "status", "DELETED"))
                    .isEqualTo(1);
            assertThat(occurrences(output.getOut(), "\"event\":\"material_deleted\"")).isEqualTo(1);
            assertThat(output.getOut())
                    .contains("\"correlationId\":\"" + DELETE_CORRELATION_ID + "\"")
                    .doesNotContain("delete-me.pdf", users.userA().userId().toString(), users.userA().email());
        }
    }

    private static double counterValue(MeterRegistry meterRegistry, String name, String... tags) {
        var counter = meterRegistry.find(name).tags(tags).counter();
        return counter == null ? 0 : counter.count();
    }

    private static int occurrences(String value, String needle) {
        return (value.length() - value.replace(needle, "").length()) / needle.length();
    }

    private static void assertNotFound(MockMvc mvc, UUID materialId, OwnershipTestUser user, String protectedMarker)
            throws Exception {
        mvc.perform(get("/api/materials/{id}", materialId).with(authenticatedAs(user)))
                .andExpect(notFoundWithoutForeignData(protectedMarker))
                .andExpect(jsonPath("$.code").value("MATERIAL_NOT_FOUND"));
    }

    private static MaterialEntity createMaterial(
            SpringDataMaterialRepository materials, UUID ownerId, String filename, String status) {
        MaterialEntity material = new MaterialEntity(ownerId, filename, "PDF", status);
        material.setOriginalFilename(filename);
        material.setMimeType("application/pdf");
        return materials.saveAndFlush(material);
    }

    private static MaterialEntity createActiveMaterial(
            SpringDataMaterialRepository materials,
            SpringDataMaterialVersionRepository versions,
            UUID ownerId,
            String filename) {
        MaterialEntity material = createMaterial(materials, ownerId, filename, "READY");
        MaterialVersionEntity version = new MaterialVersionEntity(material.getId(), 1, "READY");
        versions.saveAndFlush(version);
        material.setActiveVersionId(version.getId());
        return materials.saveAndFlush(material);
    }

    private static DocumentNodeEntity insertNode(
            SpringDataDocumentNodeRepository nodes,
            UUID versionId,
            UUID parentId,
            com.hippocampus.materials.domain.DocumentNodeType nodeType,
            String title,
            int ordinal,
            int startPage,
            int endPage) throws Exception {
        var ctor = DocumentNodeEntity.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        DocumentNodeEntity node = (DocumentNodeEntity) ctor.newInstance();
        ReflectionTestUtils.setField(node, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(node, "materialVersionId", versionId);
        ReflectionTestUtils.setField(node, "parentId", parentId);
        ReflectionTestUtils.setField(node, "nodeType", nodeType);
        ReflectionTestUtils.setField(node, "title", title);
        ReflectionTestUtils.setField(node, "ordinal", ordinal);
        ReflectionTestUtils.setField(node, "startPage", startPage);
        ReflectionTestUtils.setField(node, "endPage", endPage);
        ReflectionTestUtils.setField(node, "startOffset", 0L);
        ReflectionTestUtils.setField(node, "endOffset", 100L);
        ReflectionTestUtils.setField(node, "detectionOrigin",
                com.hippocampus.materials.domain.DocumentNodeDetectionOrigin.NATIVE);
        ReflectionTestUtils.setField(node, "detectionConfidence", "HIGH");
        ReflectionTestUtils.setField(node, "createdAt", java.time.Instant.now());
        return nodes.saveAndFlush(node);
    }

    private static MockMvc mvc(ConfigurableApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup((WebApplicationContext) context)
                .addFilters(context.getBean(CorrelationIdFilter.class))
                .apply(springSecurity())
                .build();
    }
}
