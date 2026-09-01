package com.hippocampus.materials.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hippocampus.identity.infrastructure.persistence.UserRepository;
import com.hippocampus.learning.infrastructure.persistence.LearningOrganizationStatus;
import com.hippocampus.learning.infrastructure.persistence.SpringDataSubjectRepository;
import com.hippocampus.learning.infrastructure.persistence.SpringDataTopicRepository;
import com.hippocampus.learning.infrastructure.persistence.SubjectEntity;
import com.hippocampus.learning.infrastructure.persistence.TopicEntity;
import com.hippocampus.materials.port.CreateMaterialTopicLinkResult.Outcome;
import com.hippocampus.materials.port.MaterialTopicLinkRepository;
import com.hippocampus.testing.PostgresIntegrationTestSupport;
import com.hippocampus.testing.security.OwnershipTestUsers;

class MaterialTopicLinkRepositoryIntegrationTests extends PostgresIntegrationTestSupport {

    @BeforeEach
    void resetDatabase() throws SQLException {
        resetPostgresSchema();
    }

    @Test
    void createsSameUserLinksInBothManyToManyDirectionsWithServerOwnedProvenance() throws Exception {
        try (var context = startApplicationWithFlyway()) {
            Fixture fixture = fixture(context, "link-cardinality");
            MaterialTopicLinkRepository links = context.getBean(MaterialTopicLinkRepository.class);

            var first = links.createUserSelectedActive(
                    fixture.userA(), fixture.topicA1(), fixture.materialA1(), null);
            var sameMaterialDifferentTopic = links.createUserSelectedActive(
                    fixture.userA(), fixture.topicA2(), fixture.materialA1(), null);
            var sameTopicDifferentMaterial = links.createUserSelectedActive(
                    fixture.userA(), fixture.topicA1(), fixture.materialA2(), null);

            assertThat(first.outcome()).isEqualTo(Outcome.CREATED);
            assertThat(first.link().origin().name()).isEqualTo("USER_SELECTED");
            assertThat(first.link().status().name()).isEqualTo("ACTIVE");
            assertThat(first.link().createdAt()).isNotNull().isEqualTo(first.link().updatedAt());
            assertThat(sameMaterialDifferentTopic.outcome()).isEqualTo(Outcome.CREATED);
            assertThat(sameTopicDifferentMaterial.outcome()).isEqualTo(Outcome.CREATED);
            assertThat(countLinks()).isEqualTo(3);
        }
    }

    @Test
    void rejectsEveryCrossOwnerAndInvalidVersionCombinationWithoutMutation() throws Exception {
        try (var context = startApplicationWithFlyway()) {
            Fixture fixture = fixture(context, "link-isolation");
            MaterialTopicLinkRepository links = context.getBean(MaterialTopicLinkRepository.class);

            assertThat(links.createUserSelectedActive(
                    fixture.userA(), fixture.topicA1(), fixture.materialB(), null).outcome())
                    .isEqualTo(Outcome.INELIGIBLE);
            assertThat(links.createUserSelectedActive(
                    fixture.userA(), fixture.topicB(), fixture.materialA1(), null).outcome())
                    .isEqualTo(Outcome.INELIGIBLE);
            assertThat(links.createUserSelectedActive(
                    fixture.userA(), fixture.topicA1(), fixture.materialA1(), fixture.versionB()).outcome())
                    .isEqualTo(Outcome.INELIGIBLE);
            assertThat(links.createUserSelectedActive(
                    fixture.userA(), fixture.topicA1(), fixture.materialA1(), fixture.versionA2()).outcome())
                    .isEqualTo(Outcome.INELIGIBLE);
            assertThat(links.createUserSelectedActive(
                    fixture.userA(), fixture.topicA1(), fixture.materialB(), fixture.versionA1()).outcome())
                    .isEqualTo(Outcome.INELIGIBLE);
            assertThat(links.createUserSelectedActive(
                    fixture.userA(), fixture.topicA1(), fixture.materialA1(), UUID.randomUUID()).outcome())
                    .isEqualTo(Outcome.INELIGIBLE);

            assertThat(countLinks()).isZero();
        }
    }

    @Test
    void allowsArchivedLearningParentsButRejectsDeletedMaterial() throws Exception {
        try (var context = startApplicationWithFlyway()) {
            Fixture fixture = fixture(context, "link-lifecycle");
            MaterialTopicLinkRepository links = context.getBean(MaterialTopicLinkRepository.class);
            execute("UPDATE subjects SET status = 'ARCHIVED' WHERE id = '" + fixture.subjectA() + "'");
            execute("UPDATE topics SET status = 'ARCHIVED' WHERE id = '" + fixture.topicA1() + "'");

            assertThat(links.createUserSelectedActive(
                    fixture.userA(), fixture.topicA1(), fixture.materialA1(), null).outcome())
                    .isEqualTo(Outcome.CREATED);

            execute("UPDATE materials SET status = 'DELETED' WHERE id = '" + fixture.materialA2() + "'");
            assertThat(links.createUserSelectedActive(
                    fixture.userA(), fixture.topicA2(), fixture.materialA2(), null).outcome())
                    .isEqualTo(Outcome.INELIGIBLE);
            assertThat(countLinks()).isEqualTo(1);
        }
    }

    @Test
    void nullsNotDistinctPreventsDuplicateActiveWholeMaterialAndVersionTargets() throws Exception {
        try (var context = startApplicationWithFlyway()) {
            Fixture fixture = fixture(context, "link-duplicates");
            MaterialTopicLinkRepository links = context.getBean(MaterialTopicLinkRepository.class);

            assertThat(links.createUserSelectedActive(
                    fixture.userA(), fixture.topicA1(), fixture.materialA1(), null).outcome())
                    .isEqualTo(Outcome.CREATED);
            assertThat(links.createUserSelectedActive(
                    fixture.userA(), fixture.topicA1(), fixture.materialA1(), null).outcome())
                    .isEqualTo(Outcome.DUPLICATE_ACTIVE);
            assertThat(links.createUserSelectedActive(
                    fixture.userA(), fixture.topicA2(), fixture.materialA1(), fixture.versionA1()).outcome())
                    .isEqualTo(Outcome.CREATED);
            assertThat(links.createUserSelectedActive(
                    fixture.userA(), fixture.topicA2(), fixture.materialA1(), fixture.versionA1()).outcome())
                    .isEqualTo(Outcome.DUPLICATE_ACTIVE);
            assertThat(countLinks()).isEqualTo(2);
        }
    }

    @Test
    void concurrentDuplicateCreationCommitsExactlyOneActiveLink() throws Exception {
        try (var context = startApplicationWithFlyway()) {
            Fixture fixture = fixture(context, "link-concurrent");
            MaterialTopicLinkRepository links = context.getBean(MaterialTopicLinkRepository.class);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            try (var executor = Executors.newFixedThreadPool(2)) {
                Future<Outcome> first = executor.submit(() -> createWhenReleased(links, fixture, ready, start));
                Future<Outcome> second = executor.submit(() -> createWhenReleased(links, fixture, ready, start));
                ready.await();
                start.countDown();

                assertThat(first.get()).isIn(Outcome.CREATED, Outcome.DUPLICATE_ACTIVE);
                assertThat(second.get()).isIn(Outcome.CREATED, Outcome.DUPLICATE_ACTIVE);
                assertThat(first.get()).isNotEqualTo(second.get());
            }
            assertThat(countLinks()).isEqualTo(1);
        }
    }

    @Test
    void inactiveHistoryDoesNotConflictButDocumentNodeIsDisabledInPhaseTwo() throws Exception {
        try (var context = startApplicationWithFlyway()) {
            Fixture fixture = fixture(context, "link-schema-guards");
            UUID first = UUID.randomUUID();
            insertRaw(first, fixture.topicA1(), fixture.materialA1(), null, null, "AI_ASSISTED", "DISMISSED");
            insertRaw(UUID.randomUUID(), fixture.topicA1(), fixture.materialA1(), null, null,
                    "SYSTEM_SUGGESTED", "ARCHIVED");
            insertRaw(UUID.randomUUID(), fixture.topicA1(), fixture.materialA1(), null, null,
                    "STRUCTURE_DETECTED", "ACTIVE");
            assertThat(countLinks()).isEqualTo(3);

            assertThatThrownBy(() -> insertRaw(
                    UUID.randomUUID(), fixture.topicA2(), fixture.materialA1(), fixture.versionA1(),
                    UUID.randomUUID(), "STRUCTURE_DETECTED", "ACTIVE"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("chk_material_topic_links_document_node_phase2_disabled");
            assertThat(linkOrigin(first)).isEqualTo("AI_ASSISTED");
        }
    }

    @Test
    void databaseRejectsMismatchedVersionInvalidVocabularyAndPhysicalParentDeletion() throws Exception {
        try (var context = startApplicationWithFlyway()) {
            Fixture fixture = fixture(context, "link-db-integrity");
            assertThatThrownBy(() -> insertRaw(
                    UUID.randomUUID(), fixture.topicA1(), fixture.materialA1(), fixture.versionA2(), null,
                    "USER_SELECTED", "ACTIVE"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("fk_material_topic_links_material_version");
            assertThatThrownBy(() -> insertRaw(
                    UUID.randomUUID(), fixture.topicA1(), fixture.materialA1(), null, null,
                    "SPOOFED", "ACTIVE"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("chk_material_topic_links_origin");

            MaterialTopicLinkRepository links = context.getBean(MaterialTopicLinkRepository.class);
            assertThat(links.createUserSelectedActive(
                    fixture.userA(), fixture.topicA1(), fixture.materialA1(), null).outcome())
                    .isEqualTo(Outcome.CREATED);
            assertThatThrownBy(() -> execute("DELETE FROM materials WHERE id = '" + fixture.materialA1() + "'"))
                    .isInstanceOf(SQLException.class);
            assertThat(countLinks()).isEqualTo(1);
        }
    }

    private static Outcome createWhenReleased(MaterialTopicLinkRepository links, Fixture fixture,
            CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        return links.createUserSelectedActive(
                fixture.userA(), fixture.topicA1(), fixture.materialA1(), null).outcome();
    }

    private static Fixture fixture(org.springframework.context.ConfigurableApplicationContext context, String scenario) {
        OwnershipTestUsers users = OwnershipTestUsers.persistWith(context.getBean(UserRepository.class), scenario);
        SpringDataSubjectRepository subjects = context.getBean(SpringDataSubjectRepository.class);
        SpringDataTopicRepository topics = context.getBean(SpringDataTopicRepository.class);
        SpringDataMaterialRepository materials = context.getBean(SpringDataMaterialRepository.class);
        SpringDataMaterialVersionRepository versions = context.getBean(SpringDataMaterialVersionRepository.class);

        SubjectEntity subjectA = subjects.saveAndFlush(
                new SubjectEntity(users.userA().userId(), "Subject A " + scenario, LearningOrganizationStatus.ACTIVE));
        SubjectEntity subjectB = subjects.saveAndFlush(
                new SubjectEntity(users.userB().userId(), "Subject B " + scenario, LearningOrganizationStatus.ACTIVE));
        TopicEntity topicA1 = topics.saveAndFlush(new TopicEntity(subjectA, "Topic A1", LearningOrganizationStatus.ACTIVE));
        TopicEntity topicA2 = topics.saveAndFlush(new TopicEntity(subjectA, "Topic A2", LearningOrganizationStatus.ACTIVE));
        TopicEntity topicB = topics.saveAndFlush(new TopicEntity(subjectB, "Topic B", LearningOrganizationStatus.ACTIVE));
        MaterialEntity materialA1 = materials.saveAndFlush(
                new MaterialEntity(users.userA().userId(), "Material A1", "PDF", "READY"));
        MaterialEntity materialA2 = materials.saveAndFlush(
                new MaterialEntity(users.userA().userId(), "Material A2", "PDF", "READY"));
        MaterialEntity materialB = materials.saveAndFlush(
                new MaterialEntity(users.userB().userId(), "Material B", "PDF", "READY"));
        MaterialVersionEntity versionA1 = versions.saveAndFlush(
                new MaterialVersionEntity(materialA1.getId(), 1, "READY"));
        MaterialVersionEntity versionA2 = versions.saveAndFlush(
                new MaterialVersionEntity(materialA2.getId(), 1, "READY"));
        MaterialVersionEntity versionB = versions.saveAndFlush(
                new MaterialVersionEntity(materialB.getId(), 1, "READY"));
        return new Fixture(users.userA().userId(), subjectA.getId(), topicA1.getId(), topicA2.getId(), topicB.getId(),
                materialA1.getId(), materialA2.getId(), materialB.getId(),
                versionA1.getId(), versionA2.getId(), versionB.getId());
    }

    private static void insertRaw(UUID id, UUID topicId, UUID materialId, UUID versionId, UUID nodeId,
            String origin, String status) throws SQLException {
        try (Connection connection = openPostgresConnection();
                var statement = connection.prepareStatement("""
                        INSERT INTO material_topic_links (
                            id, topic_id, material_id, material_version_id, document_node_id,
                            link_origin, status, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
            statement.setObject(1, id);
            statement.setObject(2, topicId);
            statement.setObject(3, materialId);
            statement.setObject(4, versionId);
            statement.setObject(5, nodeId);
            statement.setString(6, origin);
            statement.setString(7, status);
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            statement.setObject(8, now);
            statement.setObject(9, now);
            statement.executeUpdate();
        }
    }

    private static long countLinks() throws SQLException {
        try (Connection connection = openPostgresConnection();
                var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT count(*) FROM material_topic_links")) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }

    private static String linkOrigin(UUID id) throws SQLException {
        try (Connection connection = openPostgresConnection();
                var statement = connection.prepareStatement(
                        "SELECT link_origin FROM material_topic_links WHERE id = ?")) {
            statement.setObject(1, id);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getString(1);
            }
        }
    }

    private static void execute(String sql) throws SQLException {
        try (Connection connection = openPostgresConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private record Fixture(
            UUID userA, UUID subjectA, UUID topicA1, UUID topicA2, UUID topicB,
            UUID materialA1, UUID materialA2, UUID materialB,
            UUID versionA1, UUID versionA2, UUID versionB) {}
}
