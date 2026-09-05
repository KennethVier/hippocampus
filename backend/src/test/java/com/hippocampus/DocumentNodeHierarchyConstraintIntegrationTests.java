package com.hippocampus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hippocampus.testing.PostgresIntegrationTestSupport;

class DocumentNodeHierarchyConstraintIntegrationTests extends PostgresIntegrationTestSupport {

    @BeforeEach
    void resetDatabase() throws SQLException {
        resetPostgresSchema();
    }

    @Test
    void documentNodeHierarchyAcceptsTreesAndRejectsIndirectCyclesAtCommit() throws Exception {
        try (var context = startApplicationWithFlyway()) {
            assertThat(context.isActive()).isTrue();
        }

        var userId = UUID.randomUUID();
        var materialId = UUID.randomUUID();
        var materialVersionId = UUID.randomUUID();

        try (var connection = openPostgresConnection()) {
            seedMaterialVersion(connection, userId, materialId, materialVersionId);

            var documentId = UUID.randomUUID();
            var chapterId = UUID.randomUUID();
            var sectionId = UUID.randomUUID();

            connection.setAutoCommit(false);
            insertDocumentNode(connection, documentId, materialVersionId, null, "DOCUMENT");
            insertDocumentNode(connection, chapterId, materialVersionId, documentId, "CHAPTER");
            insertDocumentNode(connection, sectionId, materialVersionId, chapterId, "SECTION");

            assertThatCode(connection::commit).doesNotThrowAnyException();

            var firstCycleNodeId = UUID.randomUUID();
            var secondCycleNodeId = UUID.randomUUID();
            var thirdCycleNodeId = UUID.randomUUID();

            insertDocumentNode(connection, firstCycleNodeId, materialVersionId, secondCycleNodeId, "CHAPTER");
            insertDocumentNode(connection, secondCycleNodeId, materialVersionId, thirdCycleNodeId, "SECTION");
            insertDocumentNode(connection, thirdCycleNodeId, materialVersionId, firstCycleNodeId, "SUBSECTION");

            assertThatThrownBy(connection::commit)
                    .isInstanceOf(SQLException.class)
                    .satisfies(throwable -> {
                        var sqlException = (SQLException) throwable;
                        assertThat(sqlException.getSQLState()).isEqualTo("23514");
                        assertThat(sqlException.getMessage())
                                .contains("document_nodes hierarchy must be acyclic");
                    });

            connection.rollback();
        }
    }

    private static void seedMaterialVersion(
            Connection connection, UUID userId, UUID materialId, UUID materialVersionId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO users (id, email, status, created_at, updated_at)
                VALUES (?, ?, 'ACTIVE', NOW(), NOW())
                """)) {
            statement.setObject(1, userId);
            statement.setString(2, userId + "@example.test");
            statement.executeUpdate();
        }

        try (var statement = connection.prepareStatement("""
                INSERT INTO materials (
                    id, user_id, title, material_type, status, created_at, updated_at
                )
                VALUES (?, ?, 'Hierarchy constraint test', 'PDF', 'READY', NOW(), NOW())
                """)) {
            statement.setObject(1, materialId);
            statement.setObject(2, userId);
            statement.executeUpdate();
        }

        try (var statement = connection.prepareStatement("""
                INSERT INTO material_versions (
                    id, material_id, version_number, processing_status, created_at
                )
                VALUES (?, ?, 1, 'READY', NOW())
                """)) {
            statement.setObject(1, materialVersionId);
            statement.setObject(2, materialId);
            statement.executeUpdate();
        }
    }

    private static void insertDocumentNode(
            Connection connection,
            UUID nodeId,
            UUID materialVersionId,
            UUID parentId,
            String nodeType) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO document_nodes (
                    id, material_version_id, parent_id, node_type, detection_origin, created_at
                )
                VALUES (?, ?, ?, ?, 'NATIVE', NOW())
                """)) {
            statement.setObject(1, nodeId);
            statement.setObject(2, materialVersionId);
            if (parentId == null) {
                statement.setNull(3, Types.OTHER);
            } else {
                statement.setObject(3, parentId);
            }
            statement.setString(4, nodeType);
            statement.executeUpdate();
        }
    }
}
