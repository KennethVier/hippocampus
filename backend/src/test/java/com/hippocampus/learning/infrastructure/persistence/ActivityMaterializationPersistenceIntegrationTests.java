package com.hippocampus.learning.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.hippocampus.identity.domain.AuthenticatedUser;
import com.hippocampus.learning.application.ActivityMaterializationException;
import com.hippocampus.learning.application.MaterializeLearningActivityUseCase;
import com.hippocampus.learning.application.PersistMaterializedActivity;
import com.hippocampus.learning.domain.LearningActionConstraints;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningActivity;
import com.hippocampus.learning.domain.LearningActivityIntent;
import com.hippocampus.learning.domain.LearningActivityType;
import com.hippocampus.learning.domain.LearningDifficulty;
import com.hippocampus.learning.domain.LearningObjective;
import com.hippocampus.learning.domain.LearningObjectiveStatus;
import com.hippocampus.learning.domain.LearningStage;
import com.hippocampus.learning.domain.MissionMaterial;
import com.hippocampus.learning.domain.NextLearningAction;
import com.hippocampus.learning.domain.SourceRequirement;
import com.hippocampus.learning.domain.StudyMission;
import com.hippocampus.learning.domain.StudyMissionGroundingMode;
import com.hippocampus.learning.domain.StudyMissionStatus;
import com.hippocampus.learning.port.ActivityAiTaskPort;
import com.hippocampus.learning.port.ActivityEvidencePort;
import com.hippocampus.learning.port.ActivitySourceReferenceAuthorization;
import com.hippocampus.learning.port.GeneratedArtifactRepository;
import com.hippocampus.learning.port.StudyMissionRepository;
import com.hippocampus.testing.PostgresIntegrationTestSupport;

class ActivityMaterializationPersistenceIntegrationTests extends PostgresIntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");

    @BeforeEach
    void resetDatabase() throws SQLException {
        resetPostgresSchema();
    }

    @Test
    void persistsArtifactProvenanceActivityAndCurrentMissionStateAtomically() throws Exception {
        try (var context = startApplicationWithFlyway(MaterializationTestConfiguration.class)) {
            Fixture fixture = insertFixture();
            JpaStudyMissionRepository repository = context.getBean(JpaStudyMissionRepository.class);
            StudyMission mission = initialMission(fixture);
            repository.save(mission);

            var useCase = useCase(
                    fixture,
                    context.getBean(StudyMissionRepository.class),
                    context.getBean(PersistMaterializedActivity.class),
                    Set.of(fixture.sourceReferenceId()));
            var result = useCase.execute(new MaterializeLearningActivityUseCase.Command(
                    mission.id(), action(mission.objectives().getFirst().id())));

            StudyMission reloaded = repository.findOwnedById(mission.id(), fixture.ownerId()).orElseThrow();
            assertThat(reloaded.activities()).hasSize(2);
            assertThat(reloaded.activities()).extracting(LearningActivity::sequenceNumber)
                    .containsExactly(1, 2);
            assertThat(reloaded.activities().getFirst().status()).isEqualTo("COMPLETED");
            assertThat(reloaded.currentActivityId()).isEqualTo(result.activity().id());
            assertThat(result.activity().generatedArtifactId()).isNotNull();
            assertThat(result.activity().sourceReferenceIds()).containsExactly(fixture.sourceReferenceId());
            assertThat(result.activity().representedActionType()).isEqualTo(LearningActionType.UNDERSTAND);
            assertThat(result.activity().questionIntent()).isEqualTo("mechanism");
            assertThat(result.activity().templateSignature()).isEqualTo("understand-v1");

            GeneratedArtifactRepository artifacts = context.getBean(GeneratedArtifactRepository.class);
            var artifact = artifacts.findById(result.activity().generatedArtifactId()).orElseThrow();
            assertThat(artifact.userId()).isEqualTo(fixture.ownerId());
            assertThat(artifact.artifactType()).isEqualTo("EXPLANATION");
            assertThat(artifact.taskType()).isEqualTo("EXPLANATION");
            assertThat(artifact.contentText()).isEqualTo("Validated explanation");
            assertThat(artifact.contentPayload()).contains("\"kind\": \"explanation\"");
            assertThat(artifact.groundingMode()).isEqualTo("STRICT_SOURCE");
            assertThat(artifact.classification()).isEqualTo("SOURCE_GROUNDED_GENERATED");
            assertThat(artifact.promptId()).isEqualTo("EXPLANATION_V1");
            assertThat(artifact.promptVersion()).isEqualTo("1");
            assertThat(artifact.provider()).isEqualTo("GEMINI");
            assertThat(artifact.model()).isEqualTo("gemini-test");
            assertThat(artifact.modelVersion()).isEqualTo("2026-09");
            assertThat(artifact.validationStatus()).isEqualTo("VALIDATED");
            assertThat(artifact.reusable()).isTrue();
            assertThat(linkExists(
                    "generated_artifact_sources", "generated_artifact_id",
                    artifact.id(), fixture.sourceReferenceId())).isTrue();
            assertThat(linkExists(
                    "activity_source_references", "learning_activity_id",
                    result.activity().id(), fixture.sourceReferenceId())).isTrue();
        }
    }

    @Test
    void representedActionsSurviveMaterializationPersistenceAndReload() throws Exception {
        try (var context = startApplicationWithFlyway(MaterializationTestConfiguration.class)) {
            Fixture fixture = insertFixture();
            JpaStudyMissionRepository repository = context.getBean(JpaStudyMissionRepository.class);
            PersistMaterializedActivity persistence = context.getBean(PersistMaterializedActivity.class);

            assertMaterializedRoundTrip(
                    fixture, repository, persistence, LearningActionType.HINT, false,
                    LearningActivityType.UNDERSTAND);
            assertMaterializedRoundTrip(
                    fixture, repository, persistence, LearningActionType.PREREQUISITE_SUPPORT, false,
                    LearningActivityType.UNDERSTAND);
            assertMaterializedRoundTrip(
                    fixture, repository, persistence, LearningActionType.RETRIEVE, true,
                    LearningActivityType.VISUAL);
        }
    }

    @Test
    void reuseRoundTripPreservesExactSelectedPriorRepresentedAction() throws Exception {
        try (var context = startApplicationWithFlyway(MaterializationTestConfiguration.class)) {
            Fixture fixture = insertFixture();
            JpaStudyMissionRepository repository = context.getBean(JpaStudyMissionRepository.class);
            StudyMission mission = reuseMission(fixture);
            LearningActivity selected = mission.activities().getFirst();
            repository.save(mission);
            insertArtifactSource(fixture.existingArtifactId(), fixture.sourceReferenceId());

            var useCase = useCase(
                    fixture,
                    context.getBean(StudyMissionRepository.class),
                    context.getBean(PersistMaterializedActivity.class),
                    Set.of(fixture.sourceReferenceId()));
            var result = useCase.execute(new MaterializeLearningActivityUseCase.Command(
                    mission.id(), reuseAction(selected.learningObjectiveId(), selected.id())));

            StudyMission reloaded = repository.findOwnedById(mission.id(), fixture.ownerId()).orElseThrow();
            LearningActivity reloadedSelected = activity(reloaded, selected.id());
            LearningActivity reloadedReuse = activity(reloaded, result.activity().id());
            assertThat(reloadedSelected.representedActionType()).isEqualTo(LearningActionType.HINT);
            assertThat(reloadedReuse.activityType()).isEqualTo(LearningActivityType.UNDERSTAND);
            assertThat(reloadedReuse.representedActionType()).isEqualTo(LearningActionType.HINT);
            assertThat(reloadedReuse.representedActionType())
                    .isNotEqualTo(LearningActionType.REUSE_VALIDATED_CONTENT);
            assertThat(reloadedReuse.generatedArtifactId()).isEqualTo(selected.generatedArtifactId());
        }
    }

    @Test
    void foreignUserSourceReferenceFailsClosedWithoutPartialWrites() throws Exception {
        try (var context = startApplicationWithFlyway(MaterializationTestConfiguration.class)) {
            Fixture fixture = insertFixture();
            JpaStudyMissionRepository repository = context.getBean(JpaStudyMissionRepository.class);
            StudyMission mission = initialMission(fixture);
            repository.save(mission);
            int artifactCount = rowCount("generated_artifacts");

            var useCase = useCase(
                    fixture,
                    context.getBean(StudyMissionRepository.class),
                    context.getBean(PersistMaterializedActivity.class),
                    Set.of(fixture.foreignSourceReferenceId()));

            assertThatThrownBy(() -> useCase.execute(new MaterializeLearningActivityUseCase.Command(
                    mission.id(), action(mission.objectives().getFirst().id()))))
                    .isInstanceOf(ActivityMaterializationException.class)
                    .extracting(failure -> ((ActivityMaterializationException) failure).reason())
                    .isEqualTo(ActivityMaterializationException.Reason.INVALID_SOURCE_REFERENCES);
            assertThat(rowCount("generated_artifacts")).isEqualTo(artifactCount);
            assertThat(repository.findOwnedById(mission.id(), fixture.ownerId()).orElseThrow().activities())
                    .hasSize(1);
        }
    }

    @Test
    void deletedMaterialSourceFailsClosedWithoutPartialWrites() throws Exception {
        try (var context = startApplicationWithFlyway(MaterializationTestConfiguration.class)) {
            Fixture fixture = insertFixture();
            JpaStudyMissionRepository repository = context.getBean(JpaStudyMissionRepository.class);
            StudyMission mission = initialMission(fixture);
            repository.save(mission);
            int artifactCount = rowCount("generated_artifacts");
            int artifactSourceCount = rowCount("generated_artifact_sources");
            int activityCount = rowCount("learning_activities");
            UUID originalCurrentActivityId = mission.currentActivityId();
            markMaterialDeleted(fixture.materialId());

            var useCase = useCase(
                    fixture,
                    context.getBean(StudyMissionRepository.class),
                    context.getBean(PersistMaterializedActivity.class),
                    Set.of(fixture.sourceReferenceId()));

            assertThatThrownBy(() -> useCase.execute(new MaterializeLearningActivityUseCase.Command(
                    mission.id(), action(mission.objectives().getFirst().id()))))
                    .isInstanceOf(ActivityMaterializationException.class)
                    .extracting(failure -> ((ActivityMaterializationException) failure).reason())
                    .isEqualTo(ActivityMaterializationException.Reason.INVALID_SOURCE_REFERENCES);
            assertThat(rowCount("generated_artifacts")).isEqualTo(artifactCount);
            assertThat(rowCount("generated_artifact_sources")).isEqualTo(artifactSourceCount);
            assertThat(rowCount("learning_activities")).isEqualTo(activityCount);
            StudyMission reloaded = repository.findOwnedById(mission.id(), fixture.ownerId()).orElseThrow();
            assertThat(reloaded.currentActivityId()).isEqualTo(originalCurrentActivityId);
            assertThat(reloaded.activities()).extracting(LearningActivity::id)
                    .containsExactly(mission.activities().getFirst().id());
        }
    }

    @Test
    void writeFailureRollsBackArtifactSourcesAndActivity() throws Exception {
        try (var context = startApplicationWithFlyway(MaterializationTestConfiguration.class)) {
            Fixture fixture = insertFixture();
            JpaStudyMissionRepository repository = context.getBean(JpaStudyMissionRepository.class);
            StudyMission mission = initialMission(fixture);
            repository.save(mission);
            int artifactCount = rowCount("generated_artifacts");
            FailingMissionRepository failing = context.getBean(FailingMissionRepository.class);
            failing.failSaves = true;

            var useCase = useCase(
                    fixture, failing, context.getBean(PersistMaterializedActivity.class),
                    Set.of(fixture.sourceReferenceId()));
            assertThatThrownBy(() -> useCase.execute(new MaterializeLearningActivityUseCase.Command(
                    mission.id(), action(mission.objectives().getFirst().id()))))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("simulated mission write failure");

            assertThat(rowCount("generated_artifacts")).isEqualTo(artifactCount);
            assertThat(rowCount("generated_artifact_sources")).isZero();
            assertThat(repository.findOwnedById(mission.id(), fixture.ownerId()).orElseThrow().activities())
                    .hasSize(1);
        }
    }

    @Test
    void generatedArtifactSourcePrimaryAndForeignKeysAreEnforced() throws Exception {
        try (var context = startApplicationWithFlyway()) {
            Fixture fixture = insertFixture();
            UUID artifactId = fixture.existingArtifactId();
            insertArtifactSource(artifactId, fixture.sourceReferenceId());
            assertSqlRejected(() -> insertArtifactSource(artifactId, fixture.sourceReferenceId()));
            assertSqlRejected(() -> insertArtifactSource(UUID.randomUUID(), fixture.sourceReferenceId()));
            assertSqlRejected(() -> insertArtifactSource(artifactId, UUID.randomUUID()));
        }
    }

    private static MaterializeLearningActivityUseCase useCase(
            Fixture fixture,
            StudyMissionRepository missions,
            PersistMaterializedActivity persistence,
            Set<UUID> sourceReferenceIds) {
        ActivityEvidencePort evidence = request -> new ActivityEvidencePort.Evidence(sourceReferenceIds);
        ActivityAiTaskPort ai = request -> new ActivityAiTaskPort.ValidatedContent(
                "EXPLANATION", "EXPLANATION", "Validated explanation",
                "{\"kind\":\"explanation\"}", request.groundingMode().name(),
                "SOURCE_GROUNDED_GENERATED", "EXPLANATION_V1", "1", "GEMINI",
                "gemini-test", "2026-09", ActivityAiTaskPort.ValidationStatus.VALIDATED, true);
        return new MaterializeLearningActivityUseCase(
                () -> new AuthenticatedUser(fixture.ownerId()), missions, evidence, ai,
                persistence, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static StudyMission initialMission(Fixture fixture) {
        UUID objectiveId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();
        var objective = new LearningObjective(
                objectiveId, "Explain preload", "preload", "Preload", 1,
                LearningObjectiveStatus.ACTIVE, NOW.minusSeconds(300));
        var prior = new LearningActivity(
                activityId, objectiveId, LearningActivityType.UNDERSTAND,
                LearningActionType.UNDERSTAND, null, null, "COMPLETED",
                LearningDifficulty.FOUNDATIONAL, 1, fixture.existingArtifactId(), true,
                NOW.minusSeconds(240), NOW.minusSeconds(180), NOW.minusSeconds(250),
                Set.of(fixture.sourceReferenceId()));
        return new StudyMission(
                UUID.randomUUID(), fixture.ownerId(), fixture.topicId(), null,
                StudyMissionStatus.ACTIVE, LearningStage.RETRIEVAL,
                StudyMissionGroundingMode.STRICT_SOURCE, 30, NOW.minusSeconds(300),
                null, null, prior.id(),
                List.of(new MissionMaterial(
                        UUID.randomUUID(), fixture.materialId(), fixture.materialVersionId(), null)),
                List.of(objective), List.of(prior), NOW.minusSeconds(300), NOW.minusSeconds(60));
    }

    private static NextLearningAction action(UUID objectiveId) {
        return new NextLearningAction(
                LearningActionType.UNDERSTAND, objectiveId, "preload",
                LearningDifficulty.INTERMEDIATE, "test", true,
                new LearningActionConstraints(
                        SourceRequirement.REQUIRED, false, false,
                        "mechanism", "understand-v1", LearningActivityIntent.STANDARD));
    }

    private static NextLearningAction action(
            UUID objectiveId, LearningActionType actionType, boolean visualRequired) {
        return new NextLearningAction(
                actionType, objectiveId, "preload",
                LearningDifficulty.INTERMEDIATE, "test", true,
                new LearningActionConstraints(
                        SourceRequirement.REQUIRED, visualRequired, false,
                        "mechanism", "activity-v1",
                        LearningActivityIntent.STANDARD));
    }

    private static NextLearningAction reuseAction(UUID objectiveId, UUID activityId) {
        return new NextLearningAction(
                LearningActionType.REUSE_VALIDATED_CONTENT, objectiveId, "preload",
                LearningDifficulty.INTERMEDIATE, "test", false,
                new LearningActionConstraints(
                        SourceRequirement.REQUIRED, false, false,
                        "mechanism", "activity-v1", LearningActivityIntent.STANDARD),
                activityId);
    }

    private static void assertMaterializedRoundTrip(
            Fixture fixture,
            JpaStudyMissionRepository repository,
            PersistMaterializedActivity persistence,
            LearningActionType representedActionType,
            boolean visualRequired,
            LearningActivityType expectedActivityType) {
        StudyMission mission = initialMission(fixture);
        repository.save(mission);
        var materializer = useCase(
                fixture, repository, persistence, Set.of(fixture.sourceReferenceId()));
        var result = materializer.execute(new MaterializeLearningActivityUseCase.Command(
                mission.id(), action(
                        mission.objectives().getFirst().id(), representedActionType, visualRequired)));

        LearningActivity reloaded = activity(
                repository.findOwnedById(mission.id(), fixture.ownerId()).orElseThrow(),
                result.activity().id());
        assertThat(reloaded.activityType()).isEqualTo(expectedActivityType);
        assertThat(reloaded.representedActionType()).isEqualTo(representedActionType);
    }

    private static StudyMission reuseMission(Fixture fixture) {
        StudyMission base = initialMission(fixture);
        LearningActivity original = base.activities().getFirst();
        LearningActivity selected = new LearningActivity(
                original.id(), original.learningObjectiveId(), LearningActivityType.UNDERSTAND,
                LearningActionType.HINT, "mechanism", "hint-v1", "COMPLETED",
                original.difficulty(), original.sequenceNumber(), original.generatedArtifactId(),
                original.sourceRequired(), original.startedAt(), original.completedAt(),
                original.createdAt(), original.sourceReferenceIds());
        LearningActivity newer = new LearningActivity(
                UUID.randomUUID(), original.learningObjectiveId(), LearningActivityType.APPLY,
                LearningActionType.APPLY, "application", "apply-v1", "COMPLETED",
                LearningDifficulty.APPLIED, 2, null, false,
                NOW.minusSeconds(120), NOW.minusSeconds(90), NOW.minusSeconds(130), Set.of());
        return new StudyMission(
                base.id(), base.userId(), base.topicId(), base.subtopicId(), base.status(),
                base.learningState(), base.groundingMode(), base.availableTimeMinutes(),
                base.startedAt(), base.completedAt(), base.stoppedAt(), newer.id(),
                base.materials(), base.objectives(), List.of(selected, newer),
                base.createdAt(), base.updatedAt());
    }

    private static LearningActivity activity(StudyMission mission, UUID activityId) {
        return mission.activities().stream()
                .filter(candidate -> candidate.id().equals(activityId))
                .findFirst()
                .orElseThrow();
    }

    private static Fixture insertFixture() throws SQLException {
        UUID ownerId = UUID.randomUUID();
        UUID foreignOwnerId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        UUID materialId = UUID.randomUUID();
        UUID foreignMaterialId = UUID.randomUUID();
        UUID materialVersionId = UUID.randomUUID();
        UUID foreignVersionId = UUID.randomUUID();
        UUID sourceReferenceId = UUID.randomUUID();
        UUID foreignSourceReferenceId = UUID.randomUUID();
        UUID existingArtifactId = UUID.randomUUID();
        try (var connection = openPostgresConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate(userInsert(ownerId, "owner"));
            statement.executeUpdate(userInsert(foreignOwnerId, "foreign"));
            statement.executeUpdate("""
                    INSERT INTO subjects (id, user_id, name, status, created_at, updated_at)
                    VALUES ('%s', '%s', 'Physiology', 'ACTIVE', now(), now())
                    """.formatted(subjectId, ownerId));
            statement.executeUpdate("""
                    INSERT INTO topics (id, subject_id, name, status, created_at, updated_at)
                    VALUES ('%s', '%s', 'Hemodynamics', 'ACTIVE', now(), now())
                    """.formatted(topicId, subjectId));
            statement.executeUpdate(materialInsert(materialId, ownerId, "Owner source"));
            statement.executeUpdate(materialInsert(foreignMaterialId, foreignOwnerId, "Foreign source"));
            statement.executeUpdate(versionInsert(materialVersionId, materialId));
            statement.executeUpdate(versionInsert(foreignVersionId, foreignMaterialId));
            statement.executeUpdate(sourceInsert(
                    sourceReferenceId, materialId, materialVersionId, "Owner p. 1"));
            statement.executeUpdate(sourceInsert(
                    foreignSourceReferenceId, foreignMaterialId, foreignVersionId, "Foreign p. 1"));
            statement.executeUpdate("""
                    INSERT INTO generated_artifacts (
                        id, user_id, artifact_type, task_type, content_text, grounding_mode,
                        classification, prompt_id, prompt_version, provider, model,
                        validation_status, reusable, created_at)
                    VALUES ('%s', '%s', 'EXPLANATION', 'EXPLANATION', 'Prior',
                        'STRICT_SOURCE', 'SOURCE_GROUNDED_GENERATED', 'EXPLANATION_V1',
                        '1', 'GEMINI', 'fixture', 'VALIDATED', true, now())
                    """.formatted(existingArtifactId, ownerId));
        }
        return new Fixture(
                ownerId, topicId, materialId, materialVersionId, sourceReferenceId,
                foreignSourceReferenceId, existingArtifactId);
    }

    private static String userInsert(UUID id, String label) {
        return """
                INSERT INTO users (id, email, status, created_at, updated_at)
                VALUES ('%s', '%s-%s@example.test', 'ACTIVE', now(), now())
                """.formatted(id, label, id);
    }

    private static String materialInsert(UUID id, UUID userId, String title) {
        return """
                INSERT INTO materials (
                    id, user_id, title, material_type, status, created_at, updated_at)
                VALUES ('%s', '%s', '%s', 'PDF', 'READY', now(), now())
                """.formatted(id, userId, title);
    }

    private static String versionInsert(UUID id, UUID materialId) {
        return """
                INSERT INTO material_versions (
                    id, material_id, version_number, processing_status, page_count, created_at)
                VALUES ('%s', '%s', 1, 'READY', 1, now())
                """.formatted(id, materialId);
    }

    private static String sourceInsert(UUID id, UUID materialId, UUID versionId, String label) {
        return """
                INSERT INTO source_references (
                    id, material_id, material_version_id, page_number, display_label, created_at)
                VALUES ('%s', '%s', '%s', 1, '%s', now())
                """.formatted(id, materialId, versionId, label);
    }

    private static int rowCount(String table) throws SQLException {
        try (var connection = openPostgresConnection(); var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT count(*) FROM " + table)) {
            assertThat(result.next()).isTrue();
            return result.getInt(1);
        }
    }

    private static void markMaterialDeleted(UUID materialId) throws SQLException {
        try (var connection = openPostgresConnection();
                var statement = connection.prepareStatement(
                        "UPDATE materials SET status = 'DELETED' WHERE id = ?")) {
            statement.setObject(1, materialId);
            assertThat(statement.executeUpdate()).isOne();
        }
    }

    private static boolean linkExists(
            String table, String ownerColumn, UUID ownerId, UUID sourceId) throws SQLException {
        try (var connection = openPostgresConnection();
                var statement = connection.prepareStatement("SELECT EXISTS (SELECT 1 FROM "
                        + table + " WHERE " + ownerColumn + " = ? AND source_reference_id = ?)")) {
            statement.setObject(1, ownerId);
            statement.setObject(2, sourceId);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getBoolean(1);
            }
        }
    }

    private static void insertArtifactSource(UUID artifactId, UUID sourceId) throws SQLException {
        try (var connection = openPostgresConnection();
                var statement = connection.prepareStatement("""
                        INSERT INTO generated_artifact_sources (
                            generated_artifact_id, source_reference_id) VALUES (?, ?)
                        """)) {
            statement.setObject(1, artifactId);
            statement.setObject(2, sourceId);
            statement.executeUpdate();
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
            UUID topicId,
            UUID materialId,
            UUID materialVersionId,
            UUID sourceReferenceId,
            UUID foreignSourceReferenceId,
            UUID existingArtifactId) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class MaterializationTestConfiguration {
        @Bean
        @Primary
        FailingMissionRepository failingMissionRepository(JpaStudyMissionRepository delegate) {
            return new FailingMissionRepository(delegate);
        }

        @Bean
        PersistMaterializedActivity persistMaterializedActivity(
                FailingMissionRepository missions,
                GeneratedArtifactRepository artifacts,
                ActivitySourceReferenceAuthorization sourceAuthorization) {
            return new PersistMaterializedActivity(missions, artifacts, sourceAuthorization);
        }
    }

    static final class FailingMissionRepository implements StudyMissionRepository {
        private final JpaStudyMissionRepository delegate;
        private boolean failSaves;

        FailingMissionRepository(JpaStudyMissionRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public StudyMission save(StudyMission mission) {
            if (failSaves) {
                throw new IllegalStateException("simulated mission write failure");
            }
            return delegate.save(mission);
        }

        @Override
        public Optional<StudyMission> findOwnedById(UUID missionId, UUID ownerId) {
            return delegate.findOwnedById(missionId, ownerId);
        }

        @Override
        public Optional<StudyMission> findOwnedByIdForUpdate(UUID missionId, UUID ownerId) {
            return delegate.findOwnedByIdForUpdate(missionId, ownerId);
        }
    }
}
