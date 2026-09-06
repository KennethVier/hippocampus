package com.hippocampus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hippocampus.testing.PostgresIntegrationTestSupport;

class FlywayMigrationApplicationTests extends PostgresIntegrationTestSupport {

    @BeforeEach
    void resetDatabase() throws SQLException {
        resetPostgresSchema();
    }

    @Test
    void flywayMigratesFreshDatabaseFromZeroAndSecondStartupIsIdempotent() throws Exception {
        assertThat(isPostgresRunning()).isTrue();
        assertThat(postgresMappedPort()).isBetween(1, 65_535);
        assertThat(postgresJdbcUrl())
                .contains(postgresHost())
                .contains(":" + postgresMappedPort() + "/");
        assertDatabaseIsEmpty();

        try (var firstContext = startApplicationWithFlyway()) {
            assertThat(firstContext.isActive()).isTrue();
        }

        assertPostgresMajorVersion(18);
        assertExtensionExists("vector", "0.8.6");
        assertExtensionExists("pg_trgm", "1.6");
        assertSuccessfulFlywayVersion("1");
        assertSuccessfulFlywayVersion("2");
        assertSuccessfulFlywayVersion("3");
        assertSuccessfulFlywayVersion("4");
        assertSuccessfulFlywayVersion("5");
        assertSuccessfulFlywayVersion("6");
        assertSuccessfulFlywayVersion("7");
        assertSuccessfulFlywayVersion("8");
        assertSuccessfulFlywayVersion("9");
        assertSuccessfulFlywayVersion("10");
        assertNoFailedFlywayMigration();
        assertDomainTablesExist();
        assertSpringSessionSchema();
        assertUsersColumnsMatchContract();
        assertUsersPrimaryKey();
        assertUsersEmailUniqueConstraint();
        assertUsersHasNoCheckConstraint();
        assertPasswordCredentialContract();
        assertPasswordCredentialPrimaryKey();
        assertPasswordCredentialForeignKey();
        assertLearningOrganizationSchema();
        assertMaterialFoundationSchema();
        assertMaterialTopicLinkSchema();
        assertProcessingJobSchema();
        assertDocumentStructureSchema();

        try (var secondContext = startApplicationWithFlyway()) {
            assertThat(secondContext.isActive()).isTrue();
        }

        assertSuccessfulFlywayVersion("1");
        assertSuccessfulFlywayVersion("2");
        assertSuccessfulFlywayVersion("3");
        assertSuccessfulFlywayVersion("4");
        assertSuccessfulFlywayVersion("5");
        assertSuccessfulFlywayVersion("6");
        assertSuccessfulFlywayVersion("7");
        assertSuccessfulFlywayVersion("8");
        assertSuccessfulFlywayVersion("9");
        assertSuccessfulFlywayVersion("10");
        assertNoFailedFlywayMigration();
        assertDomainTablesExist();
        assertSpringSessionSchema();
        assertLearningOrganizationSchema();
        assertMaterialFoundationSchema();
        assertMaterialTopicLinkSchema();
        assertProcessingJobSchema();
        assertDocumentStructureSchema();
    }

    private static void assertDatabaseIsEmpty() throws SQLException {
        try (var connection = openPostgresConnection();
                var statement = connection.createStatement();
                var result = statement.executeQuery("""
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_schema = 'public'
                          AND table_type = 'BASE TABLE'
                        """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isZero();
            assertThat(result.next()).isFalse();
        }
    }

    private static void assertPostgresMajorVersion(int expectedMajorVersion) throws SQLException {
        try (var connection = openPostgresConnection();
                var statement = connection.createStatement();
                var result = statement.executeQuery("SHOW server_version_num")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1) / 10_000).isEqualTo(expectedMajorVersion);
            assertThat(result.next()).isFalse();
        }
    }

    private static void assertExtensionExists(String extensionName, String expectedVersion)
            throws SQLException {
        try (var connection = openPostgresConnection();
                var statement = connection.prepareStatement("""
                        SELECT extversion
                        FROM pg_extension
                        WHERE extname = ?
                        """)) {
            statement.setString(1, extensionName);

            try (var result = statement.executeQuery()) {
                assertThat(result.next())
                        .as("Expected extension %s", extensionName)
                        .isTrue();
                assertThat(result.getString("extversion")).isEqualTo(expectedVersion);
                assertThat(result.next()).isFalse();
            }
        }
    }

    private static void assertSuccessfulFlywayVersion(String version) throws SQLException {
        try (var connection = openPostgresConnection();
                var statement = connection.prepareStatement("""
                        SELECT success
                        FROM flyway_schema_history
                        WHERE version = ?
                        """)) {
            statement.setString(1, version);

            try (var result = statement.executeQuery()) {
                assertThat(result.next())
                        .as("Expected Flyway version %s", version)
                        .isTrue();
                assertThat(result.getBoolean("success")).isTrue();
                assertThat(result.next()).isFalse();
            }
        }
    }

    private static void assertNoFailedFlywayMigration() throws SQLException {
        try (var connection = openPostgresConnection();
                var statement = connection.createStatement();
                var result = statement.executeQuery("""
                        SELECT COUNT(*) AS failed_count
                        FROM flyway_schema_history
                        WHERE success = false
                        """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt("failed_count")).isZero();
        }
    }

    private static void assertDomainTablesExist() throws SQLException {
        try (var connection = openPostgresConnection();
                var statement = connection.createStatement();
                var result = statement.executeQuery("""
                        SELECT table_name
                        FROM information_schema.tables
                        WHERE table_schema = 'public'
                          AND table_type = 'BASE TABLE'
                          AND table_name <> 'flyway_schema_history'
                        ORDER BY table_name
                        """)) {
            var actual = new ArrayList<String>();
            while (result.next()) {
                actual.add(result.getString("table_name"));
            }
            assertThat(actual).containsExactly(
                    "document_nodes", "material_topic_links", "material_versions", "materials",
                    "processing_jobs",
                    "spring_session", "spring_session_attributes",
                    "subjects", "subtopics", "text_blocks", "topics",
                    "user_password_credentials", "users");
        }
    }

    private static void assertDocumentStructureSchema() throws SQLException {
        assertColumnsMatch("document_nodes", Map.ofEntries(
                Map.entry("id", "uuid:NO"), Map.entry("material_version_id", "uuid:NO"),
                Map.entry("parent_id", "uuid:YES"), Map.entry("node_type", "character varying:NO"),
                Map.entry("title", "character varying:YES"), Map.entry("ordinal", "integer:YES"),
                Map.entry("start_page", "integer:YES"), Map.entry("end_page", "integer:YES"),
                Map.entry("start_offset", "bigint:YES"), Map.entry("end_offset", "bigint:YES"),
                Map.entry("detection_origin", "character varying:NO"),
                Map.entry("detection_confidence", "character varying:YES"),
                Map.entry("created_at", "timestamp with time zone:NO")));
        assertColumnsMatch("text_blocks", Map.ofEntries(
                Map.entry("id", "uuid:NO"), Map.entry("material_version_id", "uuid:NO"),
                Map.entry("document_node_id", "uuid:YES"), Map.entry("page_number", "integer:YES"),
                Map.entry("block_type", "character varying:NO"), Map.entry("ordinal", "integer:NO"),
                Map.entry("content", "text:NO"), Map.entry("extraction_method", "character varying:NO"),
                Map.entry("quality", "character varying:YES"),
                Map.entry("created_at", "timestamp with time zone:NO")));
        assertNamedConstraint("document_nodes", "uq_document_nodes_id_material_version",
                "UNIQUE (id, material_version_id)", null);
        assertNamedConstraint("document_nodes", "fk_document_nodes_material_version",
                "FOREIGN KEY (material_version_id) REFERENCES material_versions(id) ON DELETE CASCADE", "c");
        assertNamedConstraint("document_nodes", "fk_document_nodes_parent_same_version",
                "FOREIGN KEY (parent_id, material_version_id) REFERENCES document_nodes(id, material_version_id) DEFERRABLE INITIALLY DEFERRED", "a");
        assertNamedConstraint("text_blocks", "fk_text_blocks_material_version",
                "FOREIGN KEY (material_version_id) REFERENCES material_versions(id) ON DELETE CASCADE", "c");
        assertNamedConstraint("text_blocks", "fk_text_blocks_node_same_version",
                "FOREIGN KEY (document_node_id, material_version_id) REFERENCES document_nodes(id, material_version_id) DEFERRABLE INITIALLY DEFERRED", "a");
        assertNamedConstraint("text_blocks", "uq_text_blocks_material_version_ordinal",
                "UNIQUE (material_version_id, ordinal)", null);
        assertIndex("document_nodes", "uq_document_nodes_document_root", true,
                "material_version_id", "DOCUMENT", "parent_id", "IS NULL");
        assertIndex("document_nodes", "uq_document_nodes_sibling_ordinal", true,
                "material_version_id", "parent_id", "ordinal", "NULLS NOT DISTINCT", "ordinal IS NOT NULL");
        assertIndex("text_blocks", "idx_text_blocks_material_version_page", false,
                "material_version_id", "page_number");
        assertCheckConstraintContains("document_nodes", "chk_document_nodes_node_type",
                "DOCUMENT", "CHAPTER", "SECTION", "SUBSECTION", "HEADING", "PAGE_GROUP",
                "TRANSCRIPT_SEGMENT_GROUP");
        assertCheckConstraintContains("document_nodes", "chk_document_nodes_detection_origin",
                "NATIVE", "HEURISTIC", "AI_ASSISTED", "USER_CONFIRMED");
        assertCheckConstraintContains("text_blocks", "chk_text_blocks_block_type",
                "PAGE_TEXT", "HEADING", "PARAGRAPH", "LIST", "CAPTION", "TABLE_TEXT", "TRANSCRIPT");
        assertCheckConstraintContains("text_blocks", "chk_text_blocks_extraction_method", "NATIVE", "OCR");
        assertCheckConstraintContains("text_blocks", "chk_text_blocks_quality", "STRONG", "LIMITED", "POOR");
    }

    private static void assertLearningOrganizationSchema() throws SQLException {
        assertColumnsMatch("subjects", Map.of(
                "id", "uuid:NO", "user_id", "uuid:NO", "name", "character varying:NO",
                "description", "text:YES", "sort_order", "integer:YES",
                "status", "character varying:NO", "created_at", "timestamp with time zone:NO",
                "updated_at", "timestamp with time zone:NO"));
        assertColumnsMatch("topics", Map.of(
                "id", "uuid:NO", "subject_id", "uuid:NO", "name", "character varying:NO",
                "description", "text:YES", "status", "character varying:NO",
                "created_at", "timestamp with time zone:NO", "updated_at", "timestamp with time zone:NO"));
        assertColumnsMatch("subtopics", Map.of(
                "id", "uuid:NO", "topic_id", "uuid:NO", "name", "character varying:NO",
                "description", "text:YES", "sort_order", "integer:YES",
                "status", "character varying:NO", "created_at", "timestamp with time zone:NO",
                "updated_at", "timestamp with time zone:NO"));
        assertConstraintColumns("subjects", "PRIMARY KEY", List.of("id"));
        assertConstraintColumns("topics", "PRIMARY KEY", List.of("id"));
        assertConstraintColumns("subtopics", "PRIMARY KEY", List.of("id"));
        assertForeignKey("subjects", "user_id", "users", "id", "RESTRICT");
        assertForeignKey("topics", "subject_id", "subjects", "id", "RESTRICT");
        assertForeignKey("subtopics", "topic_id", "topics", "id", "RESTRICT");
        assertIndex("subjects", "uq_subjects_user_lower_name", true, "user_id", "lower((name)::text)");
        assertIndex("topics", "idx_topics_subject_id", false, "subject_id");
        assertIndex("subtopics", "idx_subtopics_topic_id", false, "topic_id");
        assertStatusCheck("subjects", "chk_subjects_status");
        assertStatusCheck("topics", "chk_topics_status");
        assertStatusCheck("subtopics", "chk_subtopics_status");
        assertInvalidStatusesRejected();
        assertNoRecursiveSubtopicRelationship();
    }

    private static void assertMaterialFoundationSchema() throws SQLException {
        assertColumnsMatch("materials", Map.ofEntries(
                Map.entry("id", "uuid:NO"),
                Map.entry("user_id", "uuid:NO"),
                Map.entry("title", "character varying:NO"),
                Map.entry("material_type", "character varying:NO"),
                Map.entry("original_filename", "character varying:YES"),
                Map.entry("mime_type", "character varying:YES"),
                Map.entry("storage_key", "character varying:YES"),
                Map.entry("status", "character varying:NO"),
                Map.entry("active_version_id", "uuid:YES"),
                Map.entry("created_at", "timestamp with time zone:NO"),
                Map.entry("updated_at", "timestamp with time zone:NO")));
        assertColumnsMatch("material_versions", Map.ofEntries(
                Map.entry("id", "uuid:NO"),
                Map.entry("material_id", "uuid:NO"),
                Map.entry("version_number", "integer:NO"),
                Map.entry("storage_key", "character varying:YES"),
                Map.entry("file_size_bytes", "bigint:YES"),
                Map.entry("page_count", "integer:YES"),
                Map.entry("content_hash", "character varying:YES"),
                Map.entry("processing_status", "character varying:NO"),
                Map.entry("processing_progress", "numeric:YES"),
                Map.entry("extraction_method", "character varying:YES"),
                Map.entry("extraction_quality", "character varying:YES"),
                Map.entry("activated_at", "timestamp with time zone:YES"),
                Map.entry("created_at", "timestamp with time zone:NO")));
        assertNumericPrecision("material_versions", "processing_progress", 5, 2);
        assertNamedConstraint("materials", "pk_materials", "PRIMARY KEY (id)", null);
        assertNamedConstraint("materials", "fk_materials_user", "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT", "r");
        assertNamedConstraint("material_versions", "pk_material_versions", "PRIMARY KEY (id)", null);
        assertNamedConstraint("material_versions", "fk_material_versions_material",
                "FOREIGN KEY (material_id) REFERENCES materials(id) ON DELETE RESTRICT", "r");
        assertNamedConstraint("material_versions", "uq_material_versions_material_version_number",
                "UNIQUE (material_id, version_number)", null);
        assertNamedConstraint("material_versions", "uq_material_versions_material_id_id",
                "UNIQUE (material_id, id)", null);
        assertNamedConstraint("materials", "fk_materials_active_version",
                "FOREIGN KEY (id, active_version_id) REFERENCES material_versions(material_id, id) ON DELETE RESTRICT",
                "r");
        assertNamedConstraint("material_versions", "chk_material_versions_version_number",
                "CHECK ((version_number >= 1))", null);
        assertNamedConstraint("material_versions", "chk_material_versions_file_size",
                "CHECK ((file_size_bytes >= 0))", null);
        assertNamedConstraint("material_versions", "chk_material_versions_page_count",
                "CHECK ((page_count >= 0))", null);
        assertIndex("materials", "idx_materials_user_status", false, "user_id", "status");
        assertIndex("material_versions", "idx_material_versions_material_processing_status", false,
                "material_id", "processing_status");
        assertNoMaterialVocabularyOrProgressChecks();
    }

    private static void assertMaterialTopicLinkSchema() throws SQLException {
        assertColumnsMatch("material_topic_links", Map.ofEntries(
                Map.entry("id", "uuid:NO"),
                Map.entry("topic_id", "uuid:NO"),
                Map.entry("material_id", "uuid:NO"),
                Map.entry("material_version_id", "uuid:YES"),
                Map.entry("document_node_id", "uuid:YES"),
                Map.entry("link_origin", "character varying:NO"),
                Map.entry("status", "character varying:NO"),
                Map.entry("created_at", "timestamp with time zone:NO"),
                Map.entry("updated_at", "timestamp with time zone:NO")));
        assertNamedConstraint("material_topic_links", "pk_material_topic_links", "PRIMARY KEY (id)", null);
        assertNamedConstraint("material_topic_links", "fk_material_topic_links_topic",
                "FOREIGN KEY (topic_id) REFERENCES topics(id) ON DELETE RESTRICT", "r");
        assertNamedConstraint("material_topic_links", "fk_material_topic_links_material",
                "FOREIGN KEY (material_id) REFERENCES materials(id) ON DELETE RESTRICT", "r");
        assertNamedConstraint("material_topic_links", "fk_material_topic_links_material_version",
                "FOREIGN KEY (material_id, material_version_id) REFERENCES material_versions(material_id, id) ON DELETE RESTRICT",
                "r");
        assertNamedConstraint("material_topic_links", "chk_material_topic_links_document_node_requires_version",
                "CHECK (((document_node_id IS NULL) OR (material_version_id IS NOT NULL)))", null);
        assertNamedConstraint("material_topic_links", "chk_material_topic_links_document_node_phase2_disabled",
                "CHECK ((document_node_id IS NULL))", null);
        assertIndex("material_topic_links", "idx_material_topic_links_topic_status", false, "topic_id", "status");
        assertIndex("material_topic_links", "uq_material_topic_links_active_exact_target", true,
                "topic_id", "material_id", "material_version_id", "document_node_id",
                "NULLS NOT DISTINCT", "WHERE ((status)::text = 'ACTIVE'::text)");
        assertMaterialTopicLinkVocabularyChecks();
    }

    private static void assertProcessingJobSchema() throws SQLException {
        assertColumnsMatch("processing_jobs", Map.ofEntries(
                Map.entry("id", "uuid:NO"),
                Map.entry("user_id", "uuid:NO"),
                Map.entry("material_version_id", "uuid:YES"),
                Map.entry("job_type", "character varying:NO"),
                Map.entry("status", "character varying:NO"),
                Map.entry("priority", "integer:NO"),
                Map.entry("progress", "numeric:YES"),
                Map.entry("attempt_count", "integer:NO"),
                Map.entry("max_attempts", "integer:NO"),
                Map.entry("locked_at", "timestamp with time zone:YES"),
                Map.entry("locked_by", "character varying:YES"),
                Map.entry("next_attempt_at", "timestamp with time zone:YES"),
                Map.entry("last_heartbeat_at", "timestamp with time zone:YES"),
                Map.entry("processing_version", "character varying:NO"),
                Map.entry("error_code", "character varying:YES"),
                Map.entry("error_message", "text:YES"),
                Map.entry("created_at", "timestamp with time zone:NO"),
                Map.entry("started_at", "timestamp with time zone:YES"),
                Map.entry("completed_at", "timestamp with time zone:YES"),
                Map.entry("updated_at", "timestamp with time zone:NO")));
        assertNumericPrecision("processing_jobs", "progress", 5, 2);
        assertColumnDefault("processing_jobs", "attempt_count", "0");
        assertNamedConstraint("processing_jobs", "pk_processing_jobs", "PRIMARY KEY (id)", null);
        assertNamedConstraint("processing_jobs", "fk_processing_jobs_user",
                "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT", "r");
        assertNamedConstraint("processing_jobs", "fk_processing_jobs_material_version",
                "FOREIGN KEY (material_version_id) REFERENCES material_versions(id) ON DELETE RESTRICT", "r");
        assertCheckConstraintContains("processing_jobs", "chk_processing_jobs_status",
                "PENDING", "RUNNING", "RETRY", "COMPLETED", "FAILED", "CANCELLED");
        assertCheckConstraintContains("processing_jobs", "chk_processing_jobs_job_type",
                "MATERIAL_VALIDATE", "MATERIAL_EXTRACT", "STRUCTURE_DETECT", "VISUAL_EXTRACT",
                "NORMALIZE", "CHUNK", "EMBED", "INDEX", "ACTIVATE", "REINDEX", "CLEANUP");
        assertCheckConstraintContains("processing_jobs", "chk_processing_jobs_progress", "progress");
        assertCheckConstraintContains("processing_jobs", "chk_processing_jobs_attempt_count", "attempt_count");
        assertCheckConstraintContains("processing_jobs", "chk_processing_jobs_max_attempts", "max_attempts");
        assertCheckConstraintContains("processing_jobs", "chk_processing_jobs_attempt_limit",
                "attempt_count", "max_attempts");
        assertIndex("processing_jobs", "uq_processing_jobs_active_material_version_stage", true,
                "material_version_id", "job_type", "processing_version", "PENDING", "RUNNING", "RETRY");
        assertIndex("processing_jobs", "idx_processing_jobs_pending_claim_fifo", false,
                "created_at", "id", "status", "PENDING", "attempt_count", "max_attempts");
    }

    private static void assertMaterialTopicLinkVocabularyChecks() throws SQLException {
        try (var connection = openPostgresConnection();
                var statement = connection.prepareStatement("""
                        SELECT conname, pg_get_constraintdef(oid) AS definition
                        FROM pg_constraint
                        WHERE conrelid = 'public.material_topic_links'::regclass
                          AND conname IN ('chk_material_topic_links_origin', 'chk_material_topic_links_status')
                        ORDER BY conname
                        """);
                var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString("conname")).isEqualTo("chk_material_topic_links_origin");
            assertThat(result.getString("definition")).contains(
                    "USER_SELECTED", "STRUCTURE_DETECTED", "SYSTEM_SUGGESTED", "AI_ASSISTED");
            assertThat(result.next()).isTrue();
            assertThat(result.getString("conname")).isEqualTo("chk_material_topic_links_status");
            assertThat(result.getString("definition")).contains("ACTIVE", "DISMISSED", "ARCHIVED");
            assertThat(result.next()).isFalse();
        }
    }

    private static void assertNumericPrecision(
            String tableName, String columnName, int precision, int scale) throws SQLException {
        try (var connection = openPostgresConnection();
                var statement = connection.prepareStatement("""
                        SELECT numeric_precision, numeric_scale
                        FROM information_schema.columns
                        WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                        """)) {
            statement.setString(1, tableName);
            statement.setString(2, columnName);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt("numeric_precision")).isEqualTo(precision);
                assertThat(result.getInt("numeric_scale")).isEqualTo(scale);
                assertThat(result.next()).isFalse();
            }
        }
    }

    private static void assertColumnDefault(String tableName, String columnName, String expectedFragment)
            throws SQLException {
        try (var connection = openPostgresConnection();
                var statement = connection.prepareStatement("""
                        SELECT column_default
                        FROM information_schema.columns
                        WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                        """)) {
            statement.setString(1, tableName);
            statement.setString(2, columnName);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("column_default")).contains(expectedFragment);
                assertThat(result.next()).isFalse();
            }
        }
    }

    private static void assertCheckConstraintContains(
            String tableName, String constraintName, String... definitionFragments) throws SQLException {
        try (var connection = openPostgresConnection();
                var statement = connection.prepareStatement("""
                        SELECT pg_get_constraintdef(oid) AS definition
                        FROM pg_constraint
                        WHERE conrelid = ('public.' || ?)::regclass AND conname = ? AND contype = 'c'
                        """)) {
            statement.setString(1, tableName);
            statement.setString(2, constraintName);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("definition")).contains(definitionFragments);
                assertThat(result.next()).isFalse();
            }
        }
    }

    private static void assertNamedConstraint(
            String tableName, String constraintName, String expectedDefinition, String expectedDeleteAction)
            throws SQLException {
        try (var connection = openPostgresConnection();
                var statement = connection.prepareStatement("""
                        SELECT pg_get_constraintdef(oid) AS definition, confdeltype
                        FROM pg_constraint
                        WHERE conrelid = ('public.' || ?)::regclass AND conname = ?
                        """)) {
            statement.setString(1, tableName);
            statement.setString(2, constraintName);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("definition")).isEqualTo(expectedDefinition);
                if (expectedDeleteAction != null) {
                    assertThat(result.getString("confdeltype")).isEqualTo(expectedDeleteAction);
                }
                assertThat(result.next()).isFalse();
            }
        }
    }

    private static void assertNoMaterialVocabularyOrProgressChecks() throws SQLException {
        try (var connection = openPostgresConnection();
                var statement = connection.createStatement();
                var result = statement.executeQuery("""
                        SELECT conname
                        FROM pg_constraint
                        WHERE conrelid IN ('public.materials'::regclass, 'public.material_versions'::regclass)
                          AND contype = 'c'
                        ORDER BY conname
                        """)) {
            var constraints = new ArrayList<String>();
            while (result.next()) constraints.add(result.getString("conname"));
            assertThat(constraints).containsExactly(
                    "chk_material_versions_file_size",
                    "chk_material_versions_page_count",
                    "chk_material_versions_version_number");
        }
    }

    private static void assertColumnsMatch(String tableName, Map<String, String> expected)
            throws SQLException {
        try (var connection = openPostgresConnection();
                var statement = connection.prepareStatement("""
                        SELECT column_name, data_type, is_nullable
                        FROM information_schema.columns
                        WHERE table_schema = 'public' AND table_name = ?
                        ORDER BY ordinal_position
                        """)) {
            statement.setString(1, tableName);
            try (var result = statement.executeQuery()) {
                var actual = new java.util.LinkedHashMap<String, String>();
                while (result.next()) {
                    actual.put(result.getString("column_name"),
                            result.getString("data_type") + ":" + result.getString("is_nullable"));
                }
                assertThat(actual).containsExactlyInAnyOrderEntriesOf(expected);
            }
        }
    }

    private static void assertForeignKey(String tableName, String childColumn,
            String parentTable, String parentColumn, String deleteRule) throws SQLException {
        try (var connection = openPostgresConnection();
                var statement = connection.prepareStatement("""
                        SELECT kcu.column_name, ccu.table_name, ccu.column_name, rc.delete_rule
                        FROM information_schema.table_constraints tc
                        JOIN information_schema.key_column_usage kcu
                          ON tc.constraint_schema = kcu.constraint_schema
                         AND tc.constraint_name = kcu.constraint_name
                        JOIN information_schema.constraint_column_usage ccu
                          ON tc.constraint_schema = ccu.constraint_schema
                         AND tc.constraint_name = ccu.constraint_name
                        JOIN information_schema.referential_constraints rc
                          ON tc.constraint_schema = rc.constraint_schema
                         AND tc.constraint_name = rc.constraint_name
                        WHERE tc.table_schema = 'public' AND tc.table_name = ?
                          AND tc.constraint_type = 'FOREIGN KEY'
                        """)) {
            statement.setString(1, tableName);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo(childColumn);
                assertThat(result.getString(2)).isEqualTo(parentTable);
                assertThat(result.getString(3)).isEqualTo(parentColumn);
                assertThat(result.getString(4)).isEqualTo(deleteRule);
                assertThat(result.next()).isFalse();
            }
        }
    }

    private static void assertIndex(String tableName, String indexName, boolean unique,
            String... definitionFragments) throws SQLException {
        try (var connection = openPostgresConnection();
                var statement = connection.prepareStatement("""
                        SELECT indexdef FROM pg_indexes
                        WHERE schemaname = 'public' AND tablename = ? AND indexname = ?
                        """)) {
            statement.setString(1, tableName);
            statement.setString(2, indexName);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                String definition = result.getString("indexdef");
                if (unique) assertThat(definition).containsIgnoringCase("UNIQUE INDEX");
                else assertThat(definition).doesNotContainIgnoringCase("UNIQUE INDEX");
                assertThat(definition).contains(definitionFragments);
                assertThat(result.next()).isFalse();
            }
        }
    }

    private static void assertStatusCheck(String tableName, String constraintName) throws SQLException {
        try (var connection = openPostgresConnection();
                var statement = connection.prepareStatement("""
                        SELECT pg_get_constraintdef(oid) AS definition
                        FROM pg_constraint
                        WHERE conrelid = ('public.' || ?)::regclass AND conname = ? AND contype = 'c'
                        """)) {
            statement.setString(1, tableName);
            statement.setString(2, constraintName);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("definition")).contains("ACTIVE", "ARCHIVED", "status");
                assertThat(result.next()).isFalse();
            }
        }
    }

    private static void assertInvalidStatusesRejected() throws SQLException {
        UUID userId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        UUID subtopicId = UUID.randomUUID();
        try (var connection = openPostgresConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO users (id, email, status, created_at, updated_at)
                    VALUES ('%s', '%s', 'ACTIVE', now(), now())
                    """.formatted(userId, "status-check-" + userId + "@example.test"));
            statement.executeUpdate("""
                    INSERT INTO subjects (id, user_id, name, status, created_at, updated_at)
                    VALUES ('%s', '%s', 'Subject', 'ACTIVE', now(), now())
                    """.formatted(subjectId, userId));
            statement.executeUpdate("""
                    INSERT INTO topics (id, subject_id, name, status, created_at, updated_at)
                    VALUES ('%s', '%s', 'Topic', 'ACTIVE', now(), now())
                    """.formatted(topicId, subjectId));
            statement.executeUpdate("""
                    INSERT INTO subtopics (id, topic_id, name, status, created_at, updated_at)
                    VALUES ('%s', '%s', 'Subtopic', 'ACTIVE', now(), now())
                    """.formatted(subtopicId, topicId));
            assertThatThrownBy(() -> statement.executeUpdate(
                    "UPDATE subjects SET status = 'INVALID' WHERE id = '" + subjectId + "'"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.executeUpdate(
                    "UPDATE topics SET status = 'INVALID' WHERE id = '" + topicId + "'"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.executeUpdate(
                    "UPDATE subtopics SET status = 'INVALID' WHERE id = '" + subtopicId + "'"))
                    .isInstanceOf(SQLException.class);
        }
    }

    private static void assertNoRecursiveSubtopicRelationship() throws SQLException {
        try (var connection = openPostgresConnection(); var statement = connection.createStatement();
                var result = statement.executeQuery("""
                        SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema = 'public' AND table_name = 'subtopics'
                          AND column_name = 'parent_subtopic_id'
                        """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isZero();
        }
    }

    private static void assertSpringSessionSchema() throws SQLException {
        assertConstraintColumns("spring_session", "PRIMARY KEY", List.of("primary_id"));
        assertConstraintColumns("spring_session_attributes", "PRIMARY KEY",
                List.of("session_primary_id", "attribute_name"));
        assertSpringSessionIndexes();
        assertSpringSessionAttributesForeignKey();
    }

    private static void assertSpringSessionIndexes() throws SQLException {
        try (var connection = openPostgresConnection();
                var statement = connection.prepareStatement("""
                    SELECT indexname, indexdef
                    FROM pg_indexes
                    WHERE schemaname = 'public' AND tablename = ?
                    ORDER BY indexname
                    """)) {
            statement.setString(1, "spring_session");
            try (var result = statement.executeQuery()) {
                var indexes = new java.util.LinkedHashMap<String, String>();
                while (result.next()) {
                    indexes.put(result.getString("indexname"), result.getString("indexdef"));
                }
                assertThat(indexes.keySet()).contains(
                        "spring_session_ix1", "spring_session_ix2", "spring_session_ix3");
                assertThat(indexes.get("spring_session_ix1")).containsIgnoringCase("UNIQUE");
                assertThat(indexes.get("spring_session_ix1")).containsIgnoringCase("session_id");
                assertThat(indexes.get("spring_session_ix2")).containsIgnoringCase("expiry_time");
                assertThat(indexes.get("spring_session_ix3")).containsIgnoringCase("principal_name");
            }
        }
    }

    private static void assertSpringSessionAttributesForeignKey() throws SQLException {
        try (var connection = openPostgresConnection();
                var statement = connection.createStatement();
                var result = statement.executeQuery("""
                    SELECT kcu.column_name AS child_column,
                           ccu.table_name AS parent_table,
                           ccu.column_name AS parent_column,
                           rc.delete_rule
                    FROM information_schema.table_constraints tc
                    JOIN information_schema.key_column_usage kcu
                      ON tc.constraint_catalog = kcu.constraint_catalog
                     AND tc.constraint_schema = kcu.constraint_schema
                     AND tc.constraint_name = kcu.constraint_name
                    JOIN information_schema.constraint_column_usage ccu
                      ON tc.constraint_catalog = ccu.constraint_catalog
                     AND tc.constraint_schema = ccu.constraint_schema
                     AND tc.constraint_name = ccu.constraint_name
                    JOIN information_schema.referential_constraints rc
                      ON tc.constraint_catalog = rc.constraint_catalog
                     AND tc.constraint_schema = rc.constraint_schema
                     AND tc.constraint_name = rc.constraint_name
                    WHERE tc.table_schema = 'public'
                      AND tc.table_name = 'spring_session_attributes'
                      AND tc.constraint_type = 'FOREIGN KEY'
                    """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString("child_column")).isEqualTo("session_primary_id");
            assertThat(result.getString("parent_table")).isEqualTo("spring_session");
            assertThat(result.getString("parent_column")).isEqualTo("primary_id");
            assertThat(result.getString("delete_rule")).isEqualTo("CASCADE");
            assertThat(result.next()).isFalse();
        }
    }

    private static void assertPasswordCredentialContract() throws SQLException {
        try (var connection = openPostgresConnection(); var statement = connection.createStatement();
                var result = statement.executeQuery("""
                    SELECT column_name, data_type, is_nullable
                    FROM information_schema.columns
                    WHERE table_schema='public' AND table_name='user_password_credentials'
                    ORDER BY ordinal_position
                    """)) {
            var actual = new java.util.LinkedHashMap<String, String>();
            while (result.next()) actual.put(result.getString(1), result.getString(2) + ":" + result.getString(3));
            assertThat(actual).containsExactlyInAnyOrderEntriesOf(Map.of(
                    "user_id", "uuid:NO", "password_hash", "character varying:NO",
                    "created_at", "timestamp with time zone:NO", "updated_at", "timestamp with time zone:NO"));
        }
    }

    private static void assertPasswordCredentialPrimaryKey() throws SQLException {
        assertConstraintColumns("user_password_credentials", "PRIMARY KEY", List.of("user_id"));
    }

    private static void assertPasswordCredentialForeignKey() throws SQLException {
        try (var connection = openPostgresConnection();
                var statement = connection.createStatement();
                var result = statement.executeQuery("""
                    SELECT kcu.column_name AS child_column,
                           ccu.table_name AS parent_table,
                           ccu.column_name AS parent_column,
                           rc.delete_rule
                    FROM information_schema.table_constraints tc
                    JOIN information_schema.key_column_usage kcu
                      ON tc.constraint_catalog = kcu.constraint_catalog
                     AND tc.constraint_schema = kcu.constraint_schema
                     AND tc.constraint_name = kcu.constraint_name
                    JOIN information_schema.constraint_column_usage ccu
                      ON tc.constraint_catalog = ccu.constraint_catalog
                     AND tc.constraint_schema = ccu.constraint_schema
                     AND tc.constraint_name = ccu.constraint_name
                    JOIN information_schema.referential_constraints rc
                      ON tc.constraint_catalog = rc.constraint_catalog
                     AND tc.constraint_schema = rc.constraint_schema
                     AND tc.constraint_name = rc.constraint_name
                    WHERE tc.table_schema = 'public'
                      AND tc.table_name = 'user_password_credentials'
                      AND tc.constraint_type = 'FOREIGN KEY'
                    ORDER BY kcu.ordinal_position
                    """)) {
            var actual = new ArrayList<ForeignKeyMetadata>();
            while (result.next()) {
                actual.add(new ForeignKeyMetadata(
                        result.getString("child_column"),
                        result.getString("parent_table"),
                        result.getString("parent_column"),
                        result.getString("delete_rule")));
            }
            assertThat(actual).containsExactly(
                    new ForeignKeyMetadata("user_id", "users", "id", "CASCADE"));
        }
    }

    private static void assertUsersColumnsMatchContract() throws SQLException {
        try (var connection = openPostgresConnection();
                var statement = connection.createStatement();
                var result = statement.executeQuery("""
                        SELECT column_name, data_type, is_nullable
                        FROM information_schema.columns
                        WHERE table_schema = 'public' AND table_name = 'users'
                        ORDER BY ordinal_position
                        """)) {
            var expected = Map.of(
                    "id", "uuid:NO",
                    "email", "character varying:NO",
                    "display_name", "character varying:YES",
                    "status", "character varying:NO",
                    "created_at", "timestamp with time zone:NO",
                    "updated_at", "timestamp with time zone:NO");
            var actual = new java.util.LinkedHashMap<String, String>();
            while (result.next()) {
                actual.put(result.getString("column_name"),
                        result.getString("data_type") + ":" + result.getString("is_nullable"));
            }
            assertThat(actual).containsExactlyInAnyOrderEntriesOf(expected);
        }
    }

    private static void assertUsersPrimaryKey() throws SQLException {
        assertConstraintColumns("users", "PRIMARY KEY", List.of("id"));
    }

    private static void assertUsersEmailUniqueConstraint() throws SQLException {
        assertConstraintColumns("users", "UNIQUE", List.of("email"));
    }

    private static void assertUsersHasNoCheckConstraint() throws SQLException {
        try (var connection = openPostgresConnection();
                var statement = connection.createStatement();
                var result = statement.executeQuery("""
                        SELECT COUNT(*)
                        FROM pg_constraint
                        WHERE conrelid = 'public.users'::regclass
                          AND contype = 'c'
                        """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isZero();
        }
    }

    private static void assertConstraintColumns(
            String tableName, String constraintType, List<String> expectedColumns)
            throws SQLException {
        try (var connection = openPostgresConnection();
                var statement = connection.prepareStatement("""
                        SELECT tc.constraint_name, kcu.column_name
                        FROM information_schema.table_constraints tc
                        JOIN information_schema.key_column_usage kcu
                          ON tc.constraint_catalog = kcu.constraint_catalog
                         AND tc.constraint_schema = kcu.constraint_schema
                         AND tc.constraint_name = kcu.constraint_name
                        WHERE tc.table_schema = 'public'
                          AND tc.table_name = ?
                          AND tc.constraint_type = ?
                        ORDER BY tc.constraint_name, kcu.ordinal_position
                        """)) {
            statement.setString(1, tableName);
            statement.setString(2, constraintType);
            try (var result = statement.executeQuery()) {
                var actual = new java.util.LinkedHashMap<String, List<String>>();
                while (result.next()) {
                    actual.computeIfAbsent(result.getString("constraint_name"), ignored -> new ArrayList<>())
                            .add(result.getString("column_name"));
                }
                assertThat(actual)
                        .as("Expected exactly one %s constraint on %s", constraintType, tableName)
                        .hasSize(1);
                assertThat(actual.values()).containsExactly(expectedColumns);
            }
        }
    }

    private record ForeignKeyMetadata(
            String childColumn, String parentTable, String parentColumn, String deleteRule) {}
}
