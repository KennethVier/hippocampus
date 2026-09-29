package com.hippocampus.materials.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hippocampus.learning.domain.SourceReadiness;
import com.hippocampus.learning.port.StudyMissionSourceCatalog;
import com.hippocampus.testing.PostgresIntegrationTestSupport;

class StudyMissionSourceCatalogIntegrationTests extends PostgresIntegrationTestSupport {

    @BeforeEach
    void resetDatabase() throws SQLException {
        resetPostgresSchema();
    }

    @Test
    void resolvesOnlyOwnedTopicEligibleUsableActiveVersionAndExactNode() throws Exception {
        try (var context = startApplicationWithFlyway()) {
            Fixture fixture = insertFixture();
            StudyMissionSourceCatalog catalog = context.getBean(StudyMissionSourceCatalog.class);

            StudyMissionSourceCatalog.Resolution first = catalog.resolve(
                            fixture.ownerId(),
                            fixture.topicId(),
                            List.of(new StudyMissionSourceCatalog.SourceSelection(
                                    fixture.materialId(), fixture.firstNodeId())))
                    .orElseThrow();

            assertThat(first.sources()).singleElement().satisfies(source -> {
                assertThat(source.materialId()).isEqualTo(fixture.materialId());
                assertThat(source.materialVersionId()).isEqualTo(fixture.firstVersionId());
                assertThat(source.documentNodeId()).isEqualTo(fixture.firstNodeId());
                assertThat(source.readiness()).isEqualTo(SourceReadiness.READY);
                assertThat(source.groundedTextAvailable()).isTrue();
                assertThat(source.visualAvailable()).isTrue();
                assertThat(source.visualReliable()).isTrue();
            });

            assertThat(catalog.resolve(
                    fixture.foreignOwnerId(),
                    fixture.topicId(),
                    List.of(new StudyMissionSourceCatalog.SourceSelection(
                            fixture.materialId(), fixture.firstNodeId())))).isEmpty();
            assertThat(catalog.resolve(
                    fixture.ownerId(),
                    fixture.topicId(),
                    List.of(new StudyMissionSourceCatalog.SourceSelection(
                            fixture.ineligibleMaterialId(), null)))).isEmpty();
            assertThat(catalog.resolve(
                    fixture.ownerId(),
                    fixture.topicId(),
                    List.of(new StudyMissionSourceCatalog.SourceSelection(
                            fixture.failedMaterialId(), null)))).isEmpty();
            assertThat(catalog.resolve(
                    fixture.ownerId(),
                    fixture.topicId(),
                    List.of(new StudyMissionSourceCatalog.SourceSelection(
                            fixture.foreignMaterialId(), null)))).isEmpty();

            activateSecondVersion(fixture);

            StudyMissionSourceCatalog.Resolution second = catalog.resolve(
                            fixture.ownerId(),
                            fixture.topicId(),
                            List.of(new StudyMissionSourceCatalog.SourceSelection(
                                    fixture.materialId(), fixture.secondNodeId())))
                    .orElseThrow();
            assertThat(second.sources().getFirst().materialVersionId())
                    .isEqualTo(fixture.secondVersionId());
            assertThat(first.sources().getFirst().materialVersionId())
                    .isEqualTo(fixture.firstVersionId());
            assertThat(catalog.resolve(
                    fixture.ownerId(),
                    fixture.topicId(),
                    List.of(new StudyMissionSourceCatalog.SourceSelection(
                            fixture.materialId(), fixture.firstNodeId())))).isEmpty();

            markSecondVersionLimited(fixture);
            assertThat(catalog.resolve(
                            fixture.ownerId(),
                            fixture.topicId(),
                            List.of(new StudyMissionSourceCatalog.SourceSelection(
                                    fixture.materialId(), fixture.secondNodeId())))
                    .orElseThrow().sources().getFirst().readiness()).isEqualTo(SourceReadiness.LIMITED);

            markMaterialDeleted(fixture.materialId());
            assertThat(catalog.resolve(
                    fixture.ownerId(),
                    fixture.topicId(),
                    List.of(new StudyMissionSourceCatalog.SourceSelection(
                            fixture.materialId(), fixture.secondNodeId())))).isEmpty();
        }
    }

    private static Fixture insertFixture() throws SQLException {
        UUID ownerId = UUID.randomUUID();
        UUID foreignOwnerId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        UUID materialId = UUID.randomUUID();
        UUID ineligibleMaterialId = UUID.randomUUID();
        UUID failedMaterialId = UUID.randomUUID();
        UUID foreignMaterialId = UUID.randomUUID();
        UUID firstVersionId = UUID.randomUUID();
        UUID secondVersionId = UUID.randomUUID();
        UUID ineligibleVersionId = UUID.randomUUID();
        UUID failedVersionId = UUID.randomUUID();
        UUID foreignVersionId = UUID.randomUUID();
        UUID firstNodeId = UUID.randomUUID();
        UUID secondNodeId = UUID.randomUUID();

        try (var connection = openPostgresConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate(userInsert(ownerId, "owner"));
            statement.executeUpdate(userInsert(foreignOwnerId, "foreign"));
            statement.executeUpdate("""
                    INSERT INTO subjects (id, user_id, name, status, created_at, updated_at)
                    VALUES ('%s', '%s', 'Cardiology', 'ACTIVE', now(), now())
                    """.formatted(subjectId, ownerId));
            statement.executeUpdate("""
                    INSERT INTO topics (id, subject_id, name, status, created_at, updated_at)
                    VALUES ('%s', '%s', 'Hemodynamics', 'ACTIVE', now(), now())
                    """.formatted(topicId, subjectId));

            statement.executeUpdate(materialInsert(materialId, ownerId, "READY"));
            statement.executeUpdate(materialInsert(ineligibleMaterialId, ownerId, "READY"));
            statement.executeUpdate(materialInsert(failedMaterialId, ownerId, "FAILED"));
            statement.executeUpdate(materialInsert(foreignMaterialId, foreignOwnerId, "READY"));
            statement.executeUpdate(versionInsert(firstVersionId, materialId, 1, "READY"));
            statement.executeUpdate(versionInsert(secondVersionId, materialId, 2, "READY"));
            statement.executeUpdate(versionInsert(ineligibleVersionId, ineligibleMaterialId, 1, "READY"));
            statement.executeUpdate(versionInsert(failedVersionId, failedMaterialId, 1, "FAILED"));
            statement.executeUpdate(versionInsert(foreignVersionId, foreignMaterialId, 1, "READY"));
            statement.executeUpdate(activeVersionUpdate(materialId, firstVersionId));
            statement.executeUpdate(activeVersionUpdate(ineligibleMaterialId, ineligibleVersionId));
            statement.executeUpdate(activeVersionUpdate(failedMaterialId, failedVersionId));
            statement.executeUpdate(activeVersionUpdate(foreignMaterialId, foreignVersionId));
            statement.executeUpdate(nodeInsert(firstNodeId, firstVersionId, "First node"));
            statement.executeUpdate(nodeInsert(secondNodeId, secondVersionId, "Second node"));
            statement.executeUpdate(chunkInsert(firstVersionId, firstNodeId, 1, "First version evidence"));
            statement.executeUpdate(chunkInsert(secondVersionId, secondNodeId, 1, "Second version evidence"));
            statement.executeUpdate(chunkInsert(ineligibleVersionId, null, 1, "Ineligible evidence"));
            statement.executeUpdate(chunkInsert(foreignVersionId, null, 1, "Foreign evidence"));
            statement.executeUpdate("""
                    INSERT INTO visual_assets (
                        id, material_version_id, document_node_id, page_number,
                        storage_key, visual_type, interpretation_status, content_hash, created_at)
                    VALUES ('%s', '%s', '%s', 1, 'visual/%s', 'CHART', 'SUPPORTED', '%s', now())
                    """.formatted(
                            UUID.randomUUID(), firstVersionId, firstNodeId,
                            UUID.randomUUID(), UUID.randomUUID()));
            statement.executeUpdate("""
                    INSERT INTO material_topic_links (
                        id, topic_id, material_id, material_version_id, document_node_id,
                        link_origin, status, created_at, updated_at)
                    VALUES ('%s', '%s', '%s', NULL, NULL,
                            'USER_SELECTED', 'ACTIVE', now(), now())
                    """.formatted(UUID.randomUUID(), topicId, materialId));
            statement.executeUpdate("""
                    INSERT INTO material_topic_links (
                        id, topic_id, material_id, material_version_id, document_node_id,
                        link_origin, status, created_at, updated_at)
                    VALUES ('%s', '%s', '%s', NULL, NULL,
                            'USER_SELECTED', 'ACTIVE', now(), now())
                    """.formatted(UUID.randomUUID(), topicId, failedMaterialId));
        }
        return new Fixture(
                ownerId,
                foreignOwnerId,
                topicId,
                materialId,
                ineligibleMaterialId,
                failedMaterialId,
                foreignMaterialId,
                firstVersionId,
                secondVersionId,
                firstNodeId,
                secondNodeId);
    }

    private static void activateSecondVersion(Fixture fixture) throws SQLException {
        try (var connection = openPostgresConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate(activeVersionUpdate(fixture.materialId(), fixture.secondVersionId()));
        }
    }

    private static void markSecondVersionLimited(Fixture fixture) throws SQLException {
        try (var connection = openPostgresConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE materials SET status = 'PARTIALLY_READY' WHERE id = '%s'"
                    .formatted(fixture.materialId()));
            statement.executeUpdate("UPDATE material_versions SET processing_status = 'PARTIALLY_READY' WHERE id = '%s'"
                    .formatted(fixture.secondVersionId()));
        }
    }

    private static void markMaterialDeleted(UUID materialId) throws SQLException {
        try (var connection = openPostgresConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE materials SET status = 'DELETED', active_version_id = NULL WHERE id = '%s'"
                    .formatted(materialId));
        }
    }

    private static String userInsert(UUID userId, String label) {
        return """
                INSERT INTO users (id, email, status, created_at, updated_at)
                VALUES ('%s', '%s-%s@example.test', 'ACTIVE', now(), now())
                """.formatted(userId, label, userId);
    }

    private static String materialInsert(UUID materialId, UUID ownerId, String status) {
        return """
                INSERT INTO materials (
                    id, user_id, title, material_type, status, created_at, updated_at)
                VALUES ('%s', '%s', 'Source', 'PDF', '%s', now(), now())
                """.formatted(materialId, ownerId, status);
    }

    private static String versionInsert(
            UUID versionId, UUID materialId, int versionNumber, String status) {
        return """
                INSERT INTO material_versions (
                    id, material_id, version_number, processing_status, created_at)
                VALUES ('%s', '%s', %d, '%s', now())
                """.formatted(versionId, materialId, versionNumber, status);
    }

    private static String activeVersionUpdate(UUID materialId, UUID versionId) {
        return "UPDATE materials SET active_version_id = '%s' WHERE id = '%s'"
                .formatted(versionId, materialId);
    }

    private static String nodeInsert(UUID nodeId, UUID versionId, String title) {
        return """
                INSERT INTO document_nodes (
                    id, material_version_id, node_type, title, ordinal,
                    detection_origin, created_at)
                VALUES ('%s', '%s', 'CHAPTER', '%s', 1, 'NATIVE', now())
                """.formatted(nodeId, versionId, title);
    }

    private static String chunkInsert(UUID versionId, UUID nodeId, int index, String content) {
        String nodeValue = nodeId == null ? "NULL" : "'%s'".formatted(nodeId);
        return """
                INSERT INTO chunks (
                    id, material_version_id, document_node_id, chunk_index, content,
                    content_type, extraction_method, quality, is_active, created_at)
                VALUES ('%s', '%s', %s, %d, '%s',
                        'TEXT', 'NATIVE', 'STRONG', true, now())
                """.formatted(UUID.randomUUID(), versionId, nodeValue, index, content);
    }

    private record Fixture(
            UUID ownerId,
            UUID foreignOwnerId,
            UUID topicId,
            UUID materialId,
            UUID ineligibleMaterialId,
            UUID failedMaterialId,
            UUID foreignMaterialId,
            UUID firstVersionId,
            UUID secondVersionId,
            UUID firstNodeId,
            UUID secondNodeId) {}
}
