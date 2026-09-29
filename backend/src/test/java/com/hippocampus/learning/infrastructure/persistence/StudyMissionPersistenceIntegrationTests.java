package com.hippocampus.learning.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hippocampus.learning.domain.LearningActivity;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningActivityType;
import com.hippocampus.learning.domain.LearningDifficulty;
import com.hippocampus.learning.domain.LearningObjective;
import com.hippocampus.learning.domain.LearningObjectiveStatus;
import com.hippocampus.learning.domain.LearningStage;
import com.hippocampus.learning.domain.MissionMaterial;
import com.hippocampus.learning.domain.StudyMission;
import com.hippocampus.learning.domain.StudyMissionGroundingMode;
import com.hippocampus.learning.domain.StudyMissionStatus;
import com.hippocampus.learning.port.StudyMissionRepository;
import com.hippocampus.testing.PostgresIntegrationTestSupport;

class StudyMissionPersistenceIntegrationTests extends PostgresIntegrationTestSupport {

    @BeforeEach
    void resetDatabase() throws SQLException {
        resetPostgresSchema();
    }

    @Test
    void migrationCreatesStudyMissionFoundationWithRequiredConstraints() throws Exception {
        try (var context = startApplicationWithFlyway()) {
            assertThat(context.isActive()).isTrue();
        }

        try (var connection = openPostgresConnection()) {
            assertThat(successfulMigration(connection, "20")).isTrue();
            assertThat(existingTables(connection)).contains(
                    "generated_artifacts",
                    "study_missions",
                    "mission_materials",
                    "learning_objectives",
                    "learning_activities",
                    "activity_source_references");
            assertThat(columnNames(connection, "study_missions")).contains(
                    "id", "user_id", "topic_id", "subtopic_id", "status", "learning_state",
                    "grounding_mode", "available_time_minutes", "current_activity_id");
            assertThat(columnNames(connection, "mission_materials")).contains(
                    "id", "study_mission_id", "material_id", "material_version_id",
                    "document_node_id");
            assertThat(columnNames(connection, "learning_objectives")).contains(
                    "id", "study_mission_id", "objective_text", "status", "created_at");
            assertThat(columnNames(connection, "learning_activities")).contains(
                    "id", "study_mission_id", "learning_objective_id", "activity_type",
                    "status", "sequence_number", "generated_artifact_id", "source_required");
            assertThat(columnNullable(connection, "mission_materials", "document_node_id")).isTrue();
            assertThat(constraintDefinition(connection, "mission_materials", "uq_mission_materials_scope"))
                    .containsIgnoringCase("UNIQUE NULLS NOT DISTINCT")
                    .contains("study_mission_id", "material_version_id", "document_node_id");
            assertThat(constraintNames(connection, "study_missions")).contains(
                    "fk_study_missions_subtopic_same_topic",
                    "fk_study_missions_current_activity_same_mission",
                    "chk_study_missions_status");
            assertThat(constraintNames(connection, "learning_activities")).contains(
                    "fk_learning_activities_objective_same_mission",
                    "fk_learning_activities_generated_artifact",
                    "uq_learning_activities_mission_sequence",
                    "chk_learning_activities_sequence");
        }
    }

    @Test
    void repositoryRoundTripsCompleteOwnedMissionAndKeepsFrozenVersion() throws Exception {
        try (var context = startApplicationWithFlyway()) {
            Fixture fixture = insertFixture();
            StudyMissionRepository repository = context.getBean(StudyMissionRepository.class);
            StudyMission mission = representativeMission(fixture);

            StudyMission saved = repository.save(mission);
            activateMaterialVersion(fixture.materialId(), fixture.versionTwoId());

            StudyMission reloaded = repository.findOwnedById(mission.id(), fixture.ownerId()).orElseThrow();
            assertThat(saved.id()).isEqualTo(mission.id());
            assertThat(reloaded.userId()).isEqualTo(fixture.ownerId());
            assertThat(reloaded.topicId()).isEqualTo(fixture.topicId());
            assertThat(reloaded.subtopicId()).isEqualTo(fixture.subtopicId());
            assertThat(reloaded.status()).isEqualTo(StudyMissionStatus.ACTIVE);
            assertThat(reloaded.learningState()).isEqualTo(LearningStage.RETRIEVAL);
            assertThat(reloaded.groundingMode()).isEqualTo(StudyMissionGroundingMode.STRICT_SOURCE);
            assertThat(reloaded.availableTimeMinutes()).isEqualTo(30);
            assertThat(reloaded.currentActivityId()).isEqualTo(mission.currentActivityId());
            assertThat(reloaded.materials()).containsExactlyInAnyOrderElementsOf(mission.materials());
            assertThat(reloaded.objectives()).containsExactlyInAnyOrderElementsOf(mission.objectives());
            assertThat(reloaded.materials()).allMatch(material ->
                    material.materialVersionId().equals(fixture.versionOneId()));
            assertThat(reloaded.activities()).extracting(LearningActivity::sequenceNumber)
                    .containsExactly(1, 2);
            assertThat(reloaded.activities().getFirst().sourceReferenceIds())
                    .containsExactly(fixture.sourceReferenceId());
            assertThat(reloaded.activities().getLast().generatedArtifactId())
                    .isEqualTo(fixture.generatedArtifactId());
            assertThat(reloaded.createdAt()).isEqualTo(mission.createdAt());
            assertThat(reloaded.updatedAt()).isEqualTo(mission.updatedAt());

            assertThat(repository.findOwnedById(mission.id(), fixture.foreignOwnerId())).isEmpty();
            assertThat(repository.findOwnedById(UUID.randomUUID(), fixture.ownerId())).isEmpty();
        }
    }

    @Test
    void databaseEnforcesMissionMaterialCanonicalityAndExactVersionRelationships() throws Exception {
        try (var context = startApplicationWithFlyway()) {
            Fixture fixture = insertFixture();
            UUID missionId = insertMission(fixture.ownerId(), fixture.topicId(), fixture.subtopicId());

            insertMissionMaterial(UUID.randomUUID(), missionId, fixture.materialId(),
                    fixture.versionOneId(), null);
            assertSqlRejected(() -> insertMissionMaterial(UUID.randomUUID(), missionId,
                    fixture.materialId(), fixture.versionOneId(), null));

            insertMissionMaterial(UUID.randomUUID(), missionId, fixture.materialId(),
                    fixture.versionOneId(), fixture.nodeOneId());
            assertSqlRejected(() -> insertMissionMaterial(UUID.randomUUID(), missionId,
                    fixture.materialId(), fixture.versionOneId(), fixture.nodeOneId()));

            assertSqlRejected(() -> insertMissionMaterial(UUID.randomUUID(), missionId,
                    fixture.otherMaterialId(), fixture.versionOneId(), null));
            assertSqlRejected(() -> insertMissionMaterial(UUID.randomUUID(), missionId,
                    fixture.materialId(), fixture.versionTwoId(), fixture.nodeOneId()));
        }
    }

    @Test
    void databaseEnforcesObjectiveCurrentActivityAndSequenceIntegrity() throws Exception {
        try (var context = startApplicationWithFlyway()) {
            Fixture fixture = insertFixture();
            UUID firstMission = insertMission(fixture.ownerId(), fixture.topicId(), fixture.subtopicId());
            UUID secondMission = insertMission(fixture.ownerId(), fixture.topicId(), fixture.subtopicId());
            UUID firstObjective = insertObjective(firstMission);
            UUID secondObjective = insertObjective(secondMission);
            UUID firstActivity = insertActivity(firstMission, firstObjective, 1, null);
            UUID secondActivity = insertActivity(secondMission, secondObjective, 1, null);

            updateCurrentActivity(firstMission, firstActivity);
            assertSqlRejected(() -> updateCurrentActivity(firstMission, secondActivity));
            assertSqlRejected(() -> insertActivity(secondMission, firstObjective, 2, null));
            assertSqlRejected(() -> insertActivity(firstMission, firstObjective, 1, null));
            assertSqlRejected(() -> insertActivity(firstMission, firstObjective, 0, null));
            assertSqlRejected(() -> insertActivity(firstMission, firstObjective, -1, null));
        }
    }

    @Test
    void sourceAndGeneratedArtifactForeignKeysPreserveExternalRecords() throws Exception {
        try (var context = startApplicationWithFlyway()) {
            Fixture fixture = insertFixture();
            UUID missionId = insertMission(fixture.ownerId(), fixture.topicId(), fixture.subtopicId());
            UUID objectiveId = insertObjective(missionId);
            UUID activityWithoutArtifact = insertActivity(missionId, objectiveId, 1, null);
            UUID activityWithArtifact = insertActivity(
                    missionId, objectiveId, 2, fixture.generatedArtifactId());
            updateCurrentActivity(missionId, activityWithArtifact);

            insertActivitySource(activityWithoutArtifact, fixture.sourceReferenceId());
            assertSqlRejected(() -> insertActivitySource(
                    activityWithoutArtifact, fixture.sourceReferenceId()));
            assertSqlRejected(() -> insertActivitySource(
                    activityWithoutArtifact, UUID.randomUUID()));
            assertSqlRejected(() -> insertActivity(
                    missionId, objectiveId, 3, UUID.randomUUID()));

            insertMissionMaterial(UUID.randomUUID(), missionId, fixture.materialId(),
                    fixture.versionOneId(), fixture.nodeOneId());
            deleteMission(missionId);

            assertThat(rowExists("material_versions", fixture.versionOneId())).isTrue();
            assertThat(rowExists("document_nodes", fixture.nodeOneId())).isTrue();
            assertThat(rowExists("source_references", fixture.sourceReferenceId())).isTrue();
            assertThat(rowExists("generated_artifacts", fixture.generatedArtifactId())).isTrue();
            assertThat(rowExists("learning_activities", activityWithoutArtifact)).isFalse();
        }
    }

    private static StudyMission representativeMission(Fixture fixture) {
        Instant createdAt = Instant.parse("2026-09-28T01:00:00Z");
        UUID objectiveOneId = UUID.randomUUID();
        UUID objectiveTwoId = UUID.randomUUID();
        UUID activityOneId = UUID.randomUUID();
        UUID activityTwoId = UUID.randomUUID();
        return new StudyMission(
                UUID.randomUUID(), fixture.ownerId(), fixture.topicId(), fixture.subtopicId(),
                StudyMissionStatus.ACTIVE, LearningStage.RETRIEVAL,
                StudyMissionGroundingMode.STRICT_SOURCE, 30,
                createdAt.plusSeconds(60), null, null, activityTwoId,
                List.of(
                        new MissionMaterial(UUID.randomUUID(), fixture.materialId(),
                                fixture.versionOneId(), null),
                        new MissionMaterial(UUID.randomUUID(), fixture.materialId(),
                                fixture.versionOneId(), fixture.nodeOneId())),
                List.of(
                        new LearningObjective(objectiveOneId, "Explain the concept", "concept-1",
                                "Concept 1", 1, LearningObjectiveStatus.ACTIVE, createdAt),
                        new LearningObjective(objectiveTwoId, "Apply the concept", "concept-2",
                                "Concept 2", 2, LearningObjectiveStatus.PENDING, createdAt)),
                List.of(
                        new LearningActivity(activityOneId, objectiveOneId,
                                LearningActivityType.UNDERSTAND, LearningActionType.UNDERSTAND,
                                null, null, "COMPLETED",
                                LearningDifficulty.FOUNDATIONAL, 1, null, true,
                                createdAt.plusSeconds(60), createdAt.plusSeconds(120), createdAt,
                                Set.of(fixture.sourceReferenceId())),
                        new LearningActivity(activityTwoId, objectiveTwoId,
                                LearningActivityType.APPLY, LearningActionType.APPLY,
                                null, null, "ACTIVE",
                                LearningDifficulty.APPLIED, 2, fixture.generatedArtifactId(), true,
                                createdAt.plusSeconds(180), null, createdAt, Set.of())),
                createdAt, createdAt.plusSeconds(180));
    }

    private static Fixture insertFixture() throws SQLException {
        UUID ownerId = UUID.randomUUID();
        UUID foreignOwnerId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        UUID subtopicId = UUID.randomUUID();
        UUID materialId = UUID.randomUUID();
        UUID otherMaterialId = UUID.randomUUID();
        UUID versionOneId = UUID.randomUUID();
        UUID versionTwoId = UUID.randomUUID();
        UUID nodeOneId = UUID.randomUUID();
        UUID sourceReferenceId = UUID.randomUUID();
        UUID generatedArtifactId = UUID.randomUUID();

        try (var connection = openPostgresConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate(userInsert(ownerId, "owner"));
            statement.executeUpdate(userInsert(foreignOwnerId, "foreign"));
            statement.executeUpdate("""
                    INSERT INTO subjects (id, user_id, name, status, created_at, updated_at)
                    VALUES ('%s', '%s', 'Anatomy', 'ACTIVE', now(), now())
                    """.formatted(subjectId, ownerId));
            statement.executeUpdate("""
                    INSERT INTO topics (id, subject_id, name, status, created_at, updated_at)
                    VALUES ('%s', '%s', 'Thorax', 'ACTIVE', now(), now())
                    """.formatted(topicId, subjectId));
            statement.executeUpdate("""
                    INSERT INTO subtopics (id, topic_id, name, status, created_at, updated_at)
                    VALUES ('%s', '%s', 'Mediastinum', 'ACTIVE', now(), now())
                    """.formatted(subtopicId, topicId));
            statement.executeUpdate(materialInsert(materialId, ownerId, "Primary"));
            statement.executeUpdate(materialInsert(otherMaterialId, ownerId, "Other"));
            statement.executeUpdate(materialVersionInsert(versionOneId, materialId, 1));
            statement.executeUpdate(materialVersionInsert(versionTwoId, materialId, 2));
            statement.executeUpdate("""
                    UPDATE materials SET active_version_id = '%s' WHERE id = '%s'
                    """.formatted(versionOneId, materialId));
            statement.executeUpdate("""
                    INSERT INTO document_nodes (
                        id, material_version_id, node_type, title, ordinal,
                        detection_origin, created_at)
                    VALUES ('%s', '%s', 'CHAPTER', 'Chapter 1', 1, 'NATIVE', now())
                    """.formatted(nodeOneId, versionOneId));
            statement.executeUpdate("""
                    INSERT INTO source_references (
                        id, material_id, material_version_id, document_node_id,
                        display_label, created_at)
                    VALUES ('%s', '%s', '%s', '%s', 'Primary - Chapter 1', now())
                    """.formatted(sourceReferenceId, materialId, versionOneId, nodeOneId));
            statement.executeUpdate("""
                    INSERT INTO generated_artifacts (
                        id, user_id, artifact_type, task_type, grounding_mode, classification,
                        prompt_id, prompt_version, provider, model, validation_status, created_at)
                    VALUES (
                        '%s', '%s', 'EXPLANATION', 'MISSION_ACTIVITY', 'STRICT_SOURCE',
                        'SOURCE_GROUNDED_GENERATED', 'mission-activity', '1', 'GEMINI',
                        'fixture-model', 'VALID', now())
                    """.formatted(generatedArtifactId, ownerId));
        }
        return new Fixture(ownerId, foreignOwnerId, topicId, subtopicId, materialId,
                otherMaterialId, versionOneId, versionTwoId, nodeOneId,
                sourceReferenceId, generatedArtifactId);
    }

    private static String userInsert(UUID userId, String label) {
        return """
                INSERT INTO users (id, email, status, created_at, updated_at)
                VALUES ('%s', '%s-%s@example.test', 'ACTIVE', now(), now())
                """.formatted(userId, label, userId);
    }

    private static String materialInsert(UUID materialId, UUID userId, String title) {
        return """
                INSERT INTO materials (
                    id, user_id, title, material_type, status, created_at, updated_at)
                VALUES ('%s', '%s', '%s', 'PDF', 'READY', now(), now())
                """.formatted(materialId, userId, title);
    }

    private static String materialVersionInsert(UUID versionId, UUID materialId, int number) {
        return """
                INSERT INTO material_versions (
                    id, material_id, version_number, processing_status, created_at)
                VALUES ('%s', '%s', %d, 'READY', now())
                """.formatted(versionId, materialId, number);
    }

    private static UUID insertMission(UUID userId, UUID topicId, UUID subtopicId) throws SQLException {
        UUID missionId = UUID.randomUUID();
        try (var connection = openPostgresConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO study_missions (
                        id, user_id, topic_id, subtopic_id, status, grounding_mode,
                        created_at, updated_at)
                    VALUES ('%s', '%s', '%s', '%s', 'PLANNED', 'SOURCE_FIRST', now(), now())
                    """.formatted(missionId, userId, topicId, subtopicId));
        }
        return missionId;
    }

    private static UUID insertObjective(UUID missionId) throws SQLException {
        UUID objectiveId = UUID.randomUUID();
        try (var connection = openPostgresConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO learning_objectives (
                        id, study_mission_id, objective_text, status, created_at)
                    VALUES ('%s', '%s', 'Objective', 'PENDING', now())
                    """.formatted(objectiveId, missionId));
        }
        return objectiveId;
    }

    private static UUID insertActivity(
            UUID missionId, UUID objectiveId, int sequence, UUID generatedArtifactId) throws SQLException {
        UUID activityId = UUID.randomUUID();
        String artifact = generatedArtifactId == null ? "NULL" : "'%s'".formatted(generatedArtifactId);
        try (var connection = openPostgresConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO learning_activities (
                        id, study_mission_id, learning_objective_id, activity_type, status,
                        sequence_number, generated_artifact_id, source_required, created_at)
                    VALUES ('%s', '%s', '%s', 'RETRIEVE', 'PENDING', %d, %s, false, now())
                    """.formatted(activityId, missionId, objectiveId, sequence, artifact));
        }
        return activityId;
    }

    private static void insertMissionMaterial(
            UUID id, UUID missionId, UUID materialId, UUID versionId, UUID nodeId) throws SQLException {
        String node = nodeId == null ? "NULL" : "'%s'".formatted(nodeId);
        try (var connection = openPostgresConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO mission_materials (
                        id, study_mission_id, material_id, material_version_id, document_node_id)
                    VALUES ('%s', '%s', '%s', '%s', %s)
                    """.formatted(id, missionId, materialId, versionId, node));
        }
    }

    private static void updateCurrentActivity(UUID missionId, UUID activityId) throws SQLException {
        try (var connection = openPostgresConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    UPDATE study_missions SET current_activity_id = '%s' WHERE id = '%s'
                    """.formatted(activityId, missionId));
        }
    }

    private static void insertActivitySource(UUID activityId, UUID sourceReferenceId) throws SQLException {
        try (var connection = openPostgresConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO activity_source_references (learning_activity_id, source_reference_id)
                    VALUES ('%s', '%s')
                    """.formatted(activityId, sourceReferenceId));
        }
    }

    private static void activateMaterialVersion(UUID materialId, UUID versionId) throws SQLException {
        try (var connection = openPostgresConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    UPDATE materials SET active_version_id = '%s' WHERE id = '%s'
                    """.formatted(versionId, materialId));
        }
    }

    private static void deleteMission(UUID missionId) throws SQLException {
        try (var connection = openPostgresConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM study_missions WHERE id = '%s'".formatted(missionId));
        }
    }

    private static boolean rowExists(String tableName, UUID id) throws SQLException {
        try (var connection = openPostgresConnection();
                var statement = connection.prepareStatement(
                        "SELECT EXISTS (SELECT 1 FROM " + tableName + " WHERE id = ?)") ) {
            statement.setObject(1, id);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getBoolean(1);
            }
        }
    }

    private static boolean successfulMigration(Connection connection, String version) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT success FROM flyway_schema_history WHERE version = ?
                """)) {
            statement.setString(1, version);
            try (var result = statement.executeQuery()) {
                return result.next() && result.getBoolean(1);
            }
        }
    }

    private static Set<String> existingTables(Connection connection) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery("""
                SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'
                """)) {
            var tables = new java.util.HashSet<String>();
            while (result.next()) tables.add(result.getString(1));
            return tables;
        }
    }

    private static boolean columnNullable(Connection connection, String table, String column)
            throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                """)) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return "YES".equals(result.getString(1));
            }
        }
    }

    private static Set<String> columnNames(Connection connection, String table) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ?
                """)) {
            statement.setString(1, table);
            try (var result = statement.executeQuery()) {
                var names = new java.util.HashSet<String>();
                while (result.next()) names.add(result.getString(1));
                return names;
            }
        }
    }

    private static String constraintDefinition(Connection connection, String table, String constraint)
            throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT pg_get_constraintdef(oid) FROM pg_constraint
                WHERE conrelid = ('public.' || ?)::regclass AND conname = ?
                """)) {
            statement.setString(1, table);
            statement.setString(2, constraint);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getString(1);
            }
        }
    }

    private static Set<String> constraintNames(Connection connection, String table) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT conname FROM pg_constraint WHERE conrelid = ('public.' || ?)::regclass
                """)) {
            statement.setString(1, table);
            try (var result = statement.executeQuery()) {
                var names = new java.util.HashSet<String>();
                while (result.next()) names.add(result.getString(1));
                return names;
            }
        }
    }

    private static void assertSqlRejected(SqlOperation operation) {
        assertThatThrownBy(operation::run).isInstanceOf(SQLException.class);
    }

    @FunctionalInterface
    private interface SqlOperation {
        void run() throws SQLException;
    }

    private record Fixture(
            UUID ownerId,
            UUID foreignOwnerId,
            UUID topicId,
            UUID subtopicId,
            UUID materialId,
            UUID otherMaterialId,
            UUID versionOneId,
            UUID versionTwoId,
            UUID nodeOneId,
            UUID sourceReferenceId,
            UUID generatedArtifactId) {}
}
