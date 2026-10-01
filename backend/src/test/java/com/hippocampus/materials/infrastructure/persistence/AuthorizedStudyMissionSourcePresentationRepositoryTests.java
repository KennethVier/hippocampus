package com.hippocampus.materials.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.hippocampus.learning.domain.MissionMaterial;
import com.hippocampus.learning.port.StudyMissionSourcePresentationRepository;
import com.hippocampus.learning.port.StudyMissionSourcePresentationRepository.SourcePresentation;
import com.hippocampus.testing.PostgresIntegrationTestSupport;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthorizedStudyMissionSourcePresentationRepositoryTests extends PostgresIntegrationTestSupport {

    private ConfigurableApplicationContext context;
    private JdbcClient jdbc;
    private StudyMissionSourcePresentationRepository repository;

    @BeforeAll
    void startApplication() throws SQLException {
        resetPostgresSchema();
        context = startApplicationWithFlywayAndArguments(new Class<?>[0],
                "--hippocampus.materials.processing.recovery.enabled=false");
        jdbc = context.getBean(JdbcClient.class);
        repository = context.getBean(StudyMissionSourcePresentationRepository.class);
    }

    @BeforeEach
    void cleanDatabase() {
        jdbc.sql("TRUNCATE TABLE users CASCADE").update();
    }

    @AfterAll
    void closeApplication() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void frozenV1RemainsReadableAfterV2BecomesActive() {
        UUID owner = insertUser("owner");
        MaterialFixture material = insertMaterial(owner, "Anatomy Lecture", "ACTIVE", 20);
        UUID node = insertNode(material.v1(), "Posterior Cord");
        UUID chunk = insertChunk(material.v1(), node, 14, 14, true);
        UUID sourceRefId = insertSourceReference(material.id(), material.v1(), node, chunk, null, 14, "Posterior Cord");

        // Advance material to V2
        UUID v2 = insertVersion(material.id(), 2, 20);
        jdbc.sql("UPDATE materials SET active_version_id = ? WHERE id = ?").params(v2, material.id()).update();

        // Mission freezes V1
        var result = repository.resolveAuthorized(
                owner,
                List.of(new MissionMaterial(UUID.randomUUID(), material.id(), material.v1(), null)),
                Set.of(sourceRefId));

        assertThat(result).isPresent();
        assertThat(result.orElseThrow()).singleElement().satisfies(source -> {
            assertThat(source.sourceReferenceId()).isEqualTo(sourceRefId);
            assertThat(source.materialTitle()).isEqualTo("Anatomy Lecture");
            assertThat(source.pageNumber()).isEqualTo(14);
            assertThat(source.displayLabel()).isEqualTo("Posterior Cord");
        });
    }

    @Test
    void crossUserFailsClosed() {
        UUID ownerA = insertUser("ownerA");
        UUID ownerB = insertUser("ownerB");
        MaterialFixture material = insertMaterial(ownerA, "Private", "ACTIVE", 5);
        UUID sourceRefId = insertSourceReference(material.id(), material.v1(), null, null, null, 1, "Page 1");

        var result = repository.resolveAuthorized(
                ownerB,
                List.of(new MissionMaterial(UUID.randomUUID(), material.id(), material.v1(), null)),
                Set.of(sourceRefId));

        assertThat(result).isEmpty();
    }

    @Test
    void deletedMaterialFailsClosed() {
        UUID owner = insertUser("owner");
        MaterialFixture material = insertMaterial(owner, "Delete Me", "ACTIVE", 5);
        UUID sourceRefId = insertSourceReference(material.id(), material.v1(), null, null, null, 1, "Page 1");

        jdbc.sql("UPDATE materials SET status = 'DELETED' WHERE id = ?").param(material.id()).update();

        var result = repository.resolveAuthorized(
                owner,
                List.of(new MissionMaterial(UUID.randomUUID(), material.id(), material.v1(), null)),
                Set.of(sourceRefId));

        assertThat(result).isEmpty();
    }

    @Test
    void frozenVersionMismatchFailsClosed() {
        UUID owner = insertUser("owner");
        MaterialFixture material = insertMaterial(owner, "Versions", "ACTIVE", 5);
        UUID sourceRefId = insertSourceReference(material.id(), material.v1(), null, null, null, 1, "Page 1");

        // Mission freezes a different version than the source reference's version
        UUID v2 = insertVersion(material.id(), 2, 5);

        var result = repository.resolveAuthorized(
                owner,
                List.of(new MissionMaterial(UUID.randomUUID(), material.id(), v2, null)),
                Set.of(sourceRefId));

        assertThat(result).isEmpty();
    }

    @Test
    void frozenDocumentNodeMismatchFailsClosed() {
        UUID owner = insertUser("owner");
        MaterialFixture material = insertMaterial(owner, "Nodes", "ACTIVE", 10);
        UUID nodeA = insertNode(material.v1(), "Node A");
        UUID nodeB = insertNode(material.v1(), "Node B");
        UUID chunk = insertChunk(material.v1(), nodeB, 3, 3, true);
        UUID sourceRefId = insertSourceReference(material.id(), material.v1(), nodeB, chunk, null, 3, "Node B ref");

        // Mission freezes node A, but source reference belongs to node B
        var result = repository.resolveAuthorized(
                owner,
                List.of(new MissionMaterial(UUID.randomUUID(), material.id(), material.v1(), nodeA)),
                Set.of(sourceRefId));

        assertThat(result).isEmpty();

        // Also verify: source reference has no documentNodeId but mission freezes a specific node
        UUID pageRefId = insertSourceReference(material.id(), material.v1(), null, null, null, 1, "Page ref");

        var result2 = repository.resolveAuthorized(
                owner,
                List.of(new MissionMaterial(UUID.randomUUID(), material.id(), material.v1(), nodeA)),
                Set.of(pageRefId));

        assertThat(result2).isEmpty();
    }

    // --- fixture helpers ---

    private UUID insertUser(String name) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO users(id,email,status,created_at,updated_at) VALUES (?,?,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")
                .params(id, name + "-" + id + "@example.test").update();
        return id;
    }

    private MaterialFixture insertMaterial(UUID user, String title, String status, Integer pageCount) {
        UUID materialId = UUID.randomUUID();
        jdbc.sql("INSERT INTO materials(id,user_id,title,material_type,status,created_at,updated_at) VALUES (?,?,?,'PDF',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")
                .params(materialId, user, title, status).update();
        UUID v1 = insertVersion(materialId, 1, pageCount);
        jdbc.sql("UPDATE materials SET active_version_id = ? WHERE id = ?").params(v1, materialId).update();
        return new MaterialFixture(materialId, v1);
    }

    private UUID insertVersion(UUID materialId, int versionNumber, Integer pageCount) {
        UUID version = UUID.randomUUID();
        jdbc.sql("INSERT INTO material_versions(id,material_id,version_number,page_count,processing_status,created_at) VALUES (?,?,?,?,'READY',CURRENT_TIMESTAMP)")
                .params(version, materialId, versionNumber, pageCount).update();
        return version;
    }

    private UUID insertNode(UUID versionId, String title) {
        UUID id = UUID.randomUUID();
        int ordinal = jdbc.sql("SELECT count(*) + 1 FROM document_nodes WHERE material_version_id = ?")
                .param(versionId).query(Integer.class).single();
        jdbc.sql("INSERT INTO document_nodes(id,material_version_id,node_type,title,ordinal,start_page,end_page,detection_origin,created_at) VALUES (?,?,'SECTION',?,?,1,20,'NATIVE',CURRENT_TIMESTAMP)")
                .params(id, versionId, title, ordinal).update();
        return id;
    }

    private UUID insertChunk(UUID versionId, UUID nodeId, int pageStart, int pageEnd, boolean active) {
        UUID id = UUID.randomUUID();
        int index = jdbc.sql("SELECT count(*) + 1 FROM chunks WHERE material_version_id = ?")
                .param(versionId).query(Integer.class).single();
        jdbc.sql("INSERT INTO chunks(id,material_version_id,document_node_id,chunk_index,content,page_start,page_end,content_type,extraction_method,quality,is_active,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)")
                .params(id, versionId, nodeId, index, "chunk-" + id, pageStart, pageEnd,
                        "TEXT", "NATIVE", "STRONG", active).update();
        return id;
    }

    private UUID insertVisual(UUID versionId, UUID nodeId, int page) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO visual_assets(id,material_version_id,document_node_id,page_number,storage_key,visual_type,interpretation_status,content_hash,created_at) VALUES (?,?,?,?,?,'OTHER','SUPPORTED',?,CURRENT_TIMESTAMP)")
                .params(id, versionId, nodeId, page, "private/" + id, id.toString()).update();
        return id;
    }

    private UUID insertSourceReference(
            UUID materialId, UUID versionId, UUID nodeId, UUID chunkId, UUID visualId,
            Integer pageNumber, String displayLabel) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO source_references(
                    id, material_id, material_version_id, document_node_id, chunk_id,
                    visual_asset_id, page_number, timestamp_start_ms, timestamp_end_ms,
                    display_label, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, NULL, NULL, ?, CURRENT_TIMESTAMP)
                """)
                .params(id, materialId, versionId, nodeId, chunkId, visualId, pageNumber, displayLabel)
                .update();
        return id;
    }

    private record MaterialFixture(UUID id, UUID v1) {}
}
