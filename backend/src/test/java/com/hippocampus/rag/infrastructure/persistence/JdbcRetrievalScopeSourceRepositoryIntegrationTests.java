package com.hippocampus.rag.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.hippocampus.rag.port.RetrievalScopeSource;
import com.hippocampus.testing.PostgresIntegrationTestSupport;

class JdbcRetrievalScopeSourceRepositoryIntegrationTests extends PostgresIntegrationTestSupport {
    @BeforeEach
    void resetDatabase() throws SQLException {
        resetPostgresSchema();
    }

    @Test
    void resolvesOnlyOwnedActiveTopicLinksToCurrentActiveVersions() {
        try (ConfigurableApplicationContext context = startApplication()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            UUID userA = insertUser(jdbc, "user-a");
            UUID userB = insertUser(jdbc, "user-b");
            UUID activeTopic = insertTopic(jdbc, userA, "ACTIVE", "ACTIVE");
            UUID archivedSubjectTopic = insertTopic(jdbc, userA, "ARCHIVED", "ACTIVE");
            UUID archivedTopic = insertTopic(jdbc, userA, "ACTIVE", "ARCHIVED");
            UUID foreignTopic = insertTopic(jdbc, userB, "ACTIVE", "ACTIVE");
            Material implicit = insertMaterial(jdbc, userA, "ACTIVE", true, 2);
            Material explicit = insertMaterial(jdbc, userA, "ACTIVE", true, 1);
            Material inactiveLink = insertMaterial(jdbc, userA, "ACTIVE", true, 1);
            Material deleted = insertMaterial(jdbc, userA, "DELETED", true, 1);
            Material noActive = insertMaterial(jdbc, userA, "ACTIVE", false, 1);
            Material foreign = insertMaterial(jdbc, userB, "ACTIVE", true, 1);

            insertLink(jdbc, activeTopic, implicit.id(), null, null, "ACTIVE");
            insertLink(jdbc, activeTopic, explicit.id(), explicit.activeVersion(), null, "ACTIVE");
            insertLink(jdbc, activeTopic, implicit.id(), implicit.historicalVersion(), null, "ACTIVE");
            insertLink(jdbc, activeTopic, inactiveLink.id(), null, null, "ARCHIVED");
            insertLink(jdbc, activeTopic, deleted.id(), null, null, "ACTIVE");
            insertLink(jdbc, activeTopic, noActive.id(), null, null, "ACTIVE");
            insertLink(jdbc, activeTopic, foreign.id(), null, null, "ACTIVE");
            insertLink(jdbc, archivedSubjectTopic, implicit.id(), null, null, "ACTIVE");
            insertLink(jdbc, archivedTopic, implicit.id(), null, null, "ACTIVE");
            insertLink(jdbc, foreignTopic, foreign.id(), null, null, "ACTIVE");
            JdbcRetrievalScopeSourceRepository repository = new JdbcRetrievalScopeSourceRepository(jdbc);

            List<RetrievalScopeSource> rows = repository.findActiveAuthorizedTargets(userA, activeTopic);

            assertThat(rows).extracting(RetrievalScopeSource::materialVersionId)
                    .containsExactlyInAnyOrder(implicit.activeVersion(), explicit.activeVersion());
            assertThat(repository.findActiveAuthorizedTargets(userB, activeTopic)).isEmpty();
            assertThat(repository.findActiveAuthorizedTargets(userA, foreignTopic)).isEmpty();
            assertThat(repository.findActiveAuthorizedTargets(userA, archivedSubjectTopic)).isEmpty();
            assertThat(repository.findActiveAuthorizedTargets(userA, archivedTopic)).isEmpty();
            assertThat(repository.findActiveAuthorizedTargets(userA, UUID.randomUUID())).isEmpty();
        }
    }

    @Test
    void returnsValidNodeTargetsAndDatabaseRejectsMismatchedNodeVersions() {
        try (ConfigurableApplicationContext context = startApplication()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            UUID user = insertUser(jdbc, "nodes");
            UUID topic = insertTopic(jdbc, user, "ACTIVE", "ACTIVE");
            Material first = insertMaterial(jdbc, user, "ACTIVE", true, 1);
            Material second = insertMaterial(jdbc, user, "ACTIVE", true, 1);
            UUID node = insertNode(jdbc, first.activeVersion());
            UUID otherVersionNode = insertNode(jdbc, second.activeVersion());
            insertLink(jdbc, topic, first.id(), first.activeVersion(), node, "ACTIVE");

            assertThat(new JdbcRetrievalScopeSourceRepository(jdbc)
                    .findActiveAuthorizedTargets(user, topic))
                    .containsExactly(new RetrievalScopeSource(first.activeVersion(), node));
            assertThatThrownBy(() -> insertLink(jdbc, topic, first.id(),
                    first.activeVersion(), otherVersionNode, "ACTIVE"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    private static ConfigurableApplicationContext startApplication() {
        return startApplicationWithFlywayAndArguments(new Class<?>[0],
                "--hippocampus.materials.processing.recovery.enabled=false");
    }

    private static UUID insertUser(JdbcClient jdbc, String name) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO users(id,email,status,created_at,updated_at) VALUES (?,?,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")
                .params(id, name + "-" + id + "@example.test").update();
        return id;
    }

    private static UUID insertTopic(
            JdbcClient jdbc, UUID userId, String subjectStatus, String topicStatus) {
        UUID subject = UUID.randomUUID();
        UUID topic = UUID.randomUUID();
        jdbc.sql("INSERT INTO subjects(id,user_id,name,status,created_at,updated_at) VALUES (?,?,? ,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")
                .params(subject, userId, "Subject " + subject, subjectStatus).update();
        jdbc.sql("INSERT INTO topics(id,subject_id,name,status,created_at,updated_at) VALUES (?,?,?, ?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")
                .params(topic, subject, "Topic " + topic, topicStatus).update();
        return topic;
    }

    private static Material insertMaterial(
            JdbcClient jdbc, UUID userId, String status, boolean activate, int versionCount) {
        UUID material = UUID.randomUUID();
        jdbc.sql("INSERT INTO materials(id,user_id,title,material_type,status,created_at,updated_at) VALUES (?,?,'Source','PDF',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")
                .params(material, userId, status).update();
        UUID historical = null;
        UUID active = null;
        for (int versionNumber = 1; versionNumber <= versionCount; versionNumber++) {
            UUID version = UUID.randomUUID();
            jdbc.sql("INSERT INTO material_versions(id,material_id,version_number,processing_status,created_at) VALUES (?,?,?,'READY',CURRENT_TIMESTAMP)")
                    .params(version, material, versionNumber).update();
            if (versionNumber == 1 && versionCount > 1) historical = version;
            active = version;
        }
        if (activate) {
            jdbc.sql("UPDATE materials SET active_version_id=? WHERE id=?")
                    .params(active, material).update();
        }
        return new Material(material, active, historical);
    }

    private static UUID insertNode(JdbcClient jdbc, UUID version) {
        UUID node = UUID.randomUUID();
        jdbc.sql("INSERT INTO document_nodes(id,material_version_id,node_type,ordinal,detection_origin,created_at) VALUES (?,?,'SECTION',1,'NATIVE',CURRENT_TIMESTAMP)")
                .params(node, version).update();
        return node;
    }

    private static void insertLink(JdbcClient jdbc, UUID topic, UUID material,
            UUID version, UUID node, String status) {
        jdbc.sql("INSERT INTO material_topic_links(id,topic_id,material_id,material_version_id,document_node_id,link_origin,status,created_at,updated_at) VALUES (?,?,?,?,?,'USER_SELECTED',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")
                .params(UUID.randomUUID(), topic, material, version, node, status).update();
    }

    private record Material(UUID id, UUID activeVersion, UUID historicalVersion) {}
}
