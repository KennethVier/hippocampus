package com.hippocampus.rag.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.hippocampus.identity.domain.AuthenticatedUser;
import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.rag.application.BuildRetrievalScope;
import com.hippocampus.rag.application.HybridCandidate;
import com.hippocampus.rag.application.HybridCandidateMerger;
import com.hippocampus.rag.domain.GroundingMode;
import com.hippocampus.rag.domain.RetrievalScope;
import com.hippocampus.rag.domain.RetrievalScopeTarget;
import com.hippocampus.rag.port.EmbeddingVector;
import com.hippocampus.rag.port.LexicalSearchHit;
import com.hippocampus.rag.port.LexicalSearchRepository;
import com.hippocampus.rag.port.LexicalSearchRequest;
import com.hippocampus.rag.port.RetrievalScopeSourceRepository;
import com.hippocampus.rag.port.VectorSearchHit;
import com.hippocampus.rag.port.VectorSearchRepository;
import com.hippocampus.rag.port.VectorSearchRequest;
import com.hippocampus.testing.PostgresIntegrationTestSupport;

class RagIsolationReleaseIntegrationTests extends PostgresIntegrationTestSupport {
    private static final String SENTINEL = "posterior cord radial nerve wrist extension";
    private static final List<Float> QUERY_VECTOR = List.of(1.0F, 0.0F);
    private static final int LIMIT = 20;

    @BeforeEach
    void resetDatabase() throws SQLException {
        resetPostgresSchema();
    }

    @Test
    void semanticIdenticalForeignCandidateCannotEnterLexicalVectorOrHybridResults() {
        try (Fixture fixture = fixture()) {
            UUID userA = fixture.insertUser("semantic-user-a");
            UUID userB = fixture.insertUser("semantic-user-b");
            UUID topicA = fixture.insertTopic(userA, "ACTIVE", "ACTIVE", "semantic-topic-a");
            Material materialA = fixture.insertMaterial(userA, "ACTIVE", 1, "authorized-material");
            Material materialB = fixture.insertMaterial(userB, "ACTIVE", 1, "foreign-material");
            fixture.insertLink(topicA, materialA, materialA.activeVersion(), null, "ACTIVE");
            UUID chunkA = fixture.insertChunk(materialA.activeVersion(), null, 1,
                    SENTINEL + " authorized clinical note", true, "authorized-chunk");
            UUID chunkB = fixture.insertChunk(materialB.activeVersion(), null, 1,
                    SENTINEL, true, "foreign-perfect-chunk");
            fixture.insertEmbedding(chunkA, List.of(0.8F, 0.2F), "authorized-embedding");
            fixture.insertEmbedding(chunkB, QUERY_VECTOR, "foreign-perfect-embedding");

            RetrievalScope scope = fixture.buildScope(userA, topicA);
            List<LexicalSearchHit> lexical = fixture.lexical(scope, SENTINEL);
            List<VectorSearchHit> vector = fixture.vector(scope);
            List<HybridCandidate> hybrid = fixture.hybrid(lexical, vector);

            assertThat(scope.allowedMaterialVersionIds())
                    .containsExactly(materialA.activeVersion())
                    .doesNotContain(materialB.activeVersion());
            assertLexicalIsolation(lexical, chunkA, materialA, null, chunkB, materialB);
            assertVectorIsolation(vector, chunkA, materialA, null, chunkB, materialB);
            assertHybridIsolation(hybrid, chunkA, materialA, null, chunkB, materialB);
        }
    }

    @Test
    void forgedForeignAndMixedScopesRemainOwnershipFilteredBeforeRanking() {
        try (Fixture fixture = fixture()) {
            UUID userA = fixture.insertUser("forged-user-a");
            UUID userB = fixture.insertUser("forged-user-b");
            Material materialA = fixture.insertMaterial(userA, "ACTIVE", 1, "forged-authorized");
            Material materialB = fixture.insertMaterial(userB, "ACTIVE", 1, "forged-foreign");
            UUID chunkA = fixture.insertChunk(materialA.activeVersion(), null, 1,
                    SENTINEL + " partial authorized wording", true, "forged-authorized-chunk");
            UUID chunkB = fixture.insertChunk(materialB.activeVersion(), null, 1,
                    SENTINEL, true, "forged-foreign-chunk");
            fixture.insertEmbedding(chunkA, List.of(0.6F, 0.4F), "forged-authorized-embedding");
            fixture.insertEmbedding(chunkB, QUERY_VECTOR, "forged-foreign-embedding");
            UUID topic = id("forged-topic");
            RetrievalScope foreignOnly = scope(userA, topic,
                    new RetrievalScopeTarget(materialB.activeVersion(), Set.of()));
            RetrievalScope mixed = scope(userA, topic,
                    new RetrievalScopeTarget(materialA.activeVersion(), Set.of()),
                    new RetrievalScopeTarget(materialB.activeVersion(), Set.of()));

            List<LexicalSearchHit> foreignLexical = fixture.lexical(foreignOnly, SENTINEL);
            List<VectorSearchHit> foreignVector = fixture.vector(foreignOnly);
            List<LexicalSearchHit> mixedLexical = fixture.lexical(mixed, SENTINEL);
            List<VectorSearchHit> mixedVector = fixture.vector(mixed);

            assertThat(foreignLexical).isEmpty();
            assertThat(foreignVector).isEmpty();
            assertLexicalIsolation(mixedLexical, chunkA, materialA, null, chunkB, materialB);
            assertVectorIsolation(mixedVector, chunkA, materialA, null, chunkB, materialB);
        }
    }

    @Test
    void foreignTopicInactiveTopicAndInactiveLinkProduceNoAuthorizedScope() {
        try (Fixture fixture = fixture()) {
            UUID userA = fixture.insertUser("scope-user-a");
            UUID userB = fixture.insertUser("scope-user-b");
            UUID foreignTopic = fixture.insertTopic(userB, "ACTIVE", "ACTIVE", "foreign-topic");
            UUID inactiveTopic = fixture.insertTopic(userA, "ACTIVE", "ARCHIVED", "inactive-topic");
            UUID inactiveLinkTopic = fixture.insertTopic(userA, "ACTIVE", "ACTIVE", "inactive-link-topic");
            Material foreign = fixture.insertMaterial(userB, "ACTIVE", 1, "foreign-topic-material");
            Material owned = fixture.insertMaterial(userA, "ACTIVE", 1, "inactive-scope-material");
            fixture.insertLink(foreignTopic, foreign, foreign.activeVersion(), null, "ACTIVE");
            fixture.insertLink(inactiveTopic, owned, owned.activeVersion(), null, "ACTIVE");
            fixture.insertLink(inactiveLinkTopic, owned, owned.activeVersion(), null, "ARCHIVED");

            RetrievalScope foreignScope = fixture.buildScope(userA, foreignTopic);
            RetrievalScope inactiveTopicScope = fixture.buildScope(userA, inactiveTopic);
            RetrievalScope inactiveLinkScope = fixture.buildScope(userA, inactiveLinkTopic);

            assertEmptyScope(fixture, foreignScope, foreign.activeVersion());
            assertEmptyScope(fixture, inactiveTopicScope, owned.activeVersion());
            assertEmptyScope(fixture, inactiveLinkScope, owned.activeVersion());
        }
    }

    @Test
    void deletedHistoricalAndInactiveEvidenceCannotReappear() {
        try (Fixture fixture = fixture()) {
            UUID userA = fixture.insertUser("lifecycle-user-a");
            UUID deletedTopic = fixture.insertTopic(userA, "ACTIVE", "ACTIVE", "deleted-topic");
            Material current = fixture.insertMaterial(userA, "ACTIVE", 2, "versioned-material");
            Material deleted = fixture.insertMaterial(userA, "ACTIVE", 1, "deleted-material");
            fixture.insertLink(deletedTopic, deleted, deleted.activeVersion(), null, "ACTIVE");
            UUID currentChunk = fixture.insertChunk(current.activeVersion(), null, 1,
                    SENTINEL + " current evidence", true, "current-chunk");
            UUID historicalChunk = fixture.insertChunk(current.historicalVersion(), null, 1,
                    SENTINEL, true, "historical-chunk");
            UUID inactiveChunk = fixture.insertChunk(current.activeVersion(), null, 2,
                    SENTINEL, false, "inactive-chunk");
            UUID deletedChunk = fixture.insertChunk(deleted.activeVersion(), null, 1,
                    SENTINEL, true, "deleted-chunk");
            fixture.insertEmbedding(currentChunk, List.of(0.7F, 0.3F), "current-embedding");
            fixture.insertEmbedding(historicalChunk, QUERY_VECTOR, "historical-embedding");
            fixture.insertEmbedding(inactiveChunk, QUERY_VECTOR, "inactive-embedding");
            fixture.insertEmbedding(deletedChunk, QUERY_VECTOR, "deleted-embedding");
            fixture.markMaterialDeleted(deleted.id());

            RetrievalScope deletedScope = fixture.buildScope(userA, deletedTopic);
            assertEmptyScope(fixture, deletedScope, deleted.activeVersion());

            RetrievalScope forgedLifecycleScope = scope(userA, id("lifecycle-topic"),
                    new RetrievalScopeTarget(current.activeVersion(), Set.of()),
                    new RetrievalScopeTarget(current.historicalVersion(), Set.of()),
                    new RetrievalScopeTarget(deleted.activeVersion(), Set.of()));
            List<LexicalSearchHit> lexical = fixture.lexical(forgedLifecycleScope, SENTINEL);
            List<VectorSearchHit> vector = fixture.vector(forgedLifecycleScope);

            assertThat(lexical).extracting(LexicalSearchHit::chunkId)
                    .containsExactly(currentChunk)
                    .doesNotContain(historicalChunk, inactiveChunk, deletedChunk);
            assertThat(vector).extracting(VectorSearchHit::chunkId)
                    .containsExactly(currentChunk)
                    .doesNotContain(historicalChunk, inactiveChunk, deletedChunk);
            assertThat(lexical).extracting(LexicalSearchHit::materialVersionId)
                    .doesNotContain(current.historicalVersion(), deleted.activeVersion());
            assertThat(vector).extracting(VectorSearchHit::materialVersionId)
                    .doesNotContain(current.historicalVersion(), deleted.activeVersion());
        }
    }

    @Test
    void documentNodeScopeExcludesBetterSiblingAndForeignNodes() {
        try (Fixture fixture = fixture()) {
            UUID userA = fixture.insertUser("node-user-a");
            UUID userB = fixture.insertUser("node-user-b");
            UUID topicA = fixture.insertTopic(userA, "ACTIVE", "ACTIVE", "node-topic-a");
            Material materialA = fixture.insertMaterial(userA, "ACTIVE", 1, "node-material-a");
            Material materialB = fixture.insertMaterial(userB, "ACTIVE", 1, "node-material-b");
            UUID allowedNode = fixture.insertNode(materialA.activeVersion(), "allowed-node", 1);
            UUID siblingNode = fixture.insertNode(materialA.activeVersion(), "sibling-node", 2);
            UUID foreignNode = fixture.insertNode(materialB.activeVersion(), "foreign-node", 1);
            fixture.insertLink(topicA, materialA, materialA.activeVersion(), allowedNode, "ACTIVE");
            UUID allowedChunk = fixture.insertChunk(materialA.activeVersion(), allowedNode, 1,
                    SENTINEL + " permitted section", true, "allowed-node-chunk");
            UUID siblingChunk = fixture.insertChunk(materialA.activeVersion(), siblingNode, 2,
                    SENTINEL, true, "sibling-node-chunk");
            UUID foreignChunk = fixture.insertChunk(materialB.activeVersion(), foreignNode, 1,
                    SENTINEL, true, "foreign-node-chunk");
            fixture.insertEmbedding(allowedChunk, List.of(0.6F, 0.4F), "allowed-node-embedding");
            fixture.insertEmbedding(siblingChunk, QUERY_VECTOR, "sibling-node-embedding");
            fixture.insertEmbedding(foreignChunk, QUERY_VECTOR, "foreign-node-embedding");

            RetrievalScope authorizedNodeScope = fixture.buildScope(userA, topicA);
            List<LexicalSearchHit> lexical = fixture.lexical(authorizedNodeScope, SENTINEL);
            List<VectorSearchHit> vector = fixture.vector(authorizedNodeScope);

            assertThat(authorizedNodeScope.targets())
                    .containsExactly(new RetrievalScopeTarget(materialA.activeVersion(), Set.of(allowedNode)));
            assertLexicalIsolation(lexical, allowedChunk, materialA, allowedNode,
                    siblingChunk, materialA);
            assertThat(lexical).extracting(LexicalSearchHit::chunkId).doesNotContain(foreignChunk);
            assertVectorIsolation(vector, allowedChunk, materialA, allowedNode,
                    siblingChunk, materialA);
            assertThat(vector).extracting(VectorSearchHit::chunkId).doesNotContain(foreignChunk);

            RetrievalScope forgedForeignNode = scope(userA, topicA,
                    new RetrievalScopeTarget(materialB.activeVersion(), Set.of(foreignNode)));
            assertThat(fixture.lexical(forgedForeignNode, SENTINEL))
                    .extracting(LexicalSearchHit::chunkId).doesNotContain(foreignChunk).isEmpty();
            assertThat(fixture.vector(forgedForeignNode))
                    .extracting(VectorSearchHit::chunkId).doesNotContain(foreignChunk).isEmpty();
        }
    }

    @Test
    void emptyScopeReturnsNoLexicalOrVectorCandidates() {
        try (Fixture fixture = fixture()) {
            UUID user = fixture.insertUser("empty-scope-user");
            RetrievalScope empty = new RetrievalScope(
                    user, id("empty-scope-topic"), GroundingMode.STRICT_SOURCE, List.of());

            assertThat(fixture.lexical(empty, SENTINEL)).isEmpty();
            assertThat(fixture.vector(empty)).isEmpty();
        }
    }

    private static void assertEmptyScope(Fixture fixture, RetrievalScope scope, UUID forbiddenVersion) {
        assertThat(scope.isEmpty()).isTrue();
        assertThat(scope.allowedMaterialVersionIds()).doesNotContain(forbiddenVersion).isEmpty();
        assertThat(fixture.lexical(scope, SENTINEL)).isEmpty();
        assertThat(fixture.vector(scope)).isEmpty();
    }

    private static void assertLexicalIsolation(
            List<LexicalSearchHit> hits, UUID allowedChunk, Material allowedMaterial, UUID allowedNode,
            UUID forbiddenChunk, Material forbiddenMaterial) {
        assertThat(hits).extracting(LexicalSearchHit::chunkId)
                .containsExactly(allowedChunk)
                .doesNotContain(forbiddenChunk);
        assertThat(hits).extracting(LexicalSearchHit::materialId)
                .containsOnly(allowedMaterial.id());
        assertThat(hits).extracting(LexicalSearchHit::materialVersionId)
                .containsOnly(allowedMaterial.activeVersion());
        if (!allowedMaterial.id().equals(forbiddenMaterial.id())) {
            assertThat(hits).extracting(LexicalSearchHit::materialId)
                    .doesNotContain(forbiddenMaterial.id());
        }
        if (!allowedMaterial.activeVersion().equals(forbiddenMaterial.activeVersion())) {
            assertThat(hits).extracting(LexicalSearchHit::materialVersionId)
                    .doesNotContain(forbiddenMaterial.activeVersion());
        }
        if (allowedNode != null) {
            assertThat(hits).extracting(LexicalSearchHit::documentNodeId).containsOnly(allowedNode);
        }
    }

    private static void assertVectorIsolation(
            List<VectorSearchHit> hits, UUID allowedChunk, Material allowedMaterial, UUID allowedNode,
            UUID forbiddenChunk, Material forbiddenMaterial) {
        assertThat(hits).extracting(VectorSearchHit::chunkId)
                .containsExactly(allowedChunk)
                .doesNotContain(forbiddenChunk);
        assertThat(hits).extracting(VectorSearchHit::materialId)
                .containsOnly(allowedMaterial.id());
        assertThat(hits).extracting(VectorSearchHit::materialVersionId)
                .containsOnly(allowedMaterial.activeVersion());
        if (!allowedMaterial.id().equals(forbiddenMaterial.id())) {
            assertThat(hits).extracting(VectorSearchHit::materialId)
                    .doesNotContain(forbiddenMaterial.id());
        }
        if (!allowedMaterial.activeVersion().equals(forbiddenMaterial.activeVersion())) {
            assertThat(hits).extracting(VectorSearchHit::materialVersionId)
                    .doesNotContain(forbiddenMaterial.activeVersion());
        }
        if (allowedNode != null) {
            assertThat(hits).extracting(VectorSearchHit::documentNodeId).containsOnly(allowedNode);
        }
    }

    private static void assertHybridIsolation(
            List<HybridCandidate> hits, UUID allowedChunk, Material allowedMaterial, UUID allowedNode,
            UUID forbiddenChunk, Material forbiddenMaterial) {
        assertThat(hits).extracting(HybridCandidate::chunkId)
                .containsExactly(allowedChunk)
                .doesNotContain(forbiddenChunk);
        assertThat(hits).extracting(HybridCandidate::materialId)
                .containsOnly(allowedMaterial.id())
                .doesNotContain(forbiddenMaterial.id());
        assertThat(hits).extracting(HybridCandidate::materialVersionId)
                .containsOnly(allowedMaterial.activeVersion())
                .doesNotContain(forbiddenMaterial.activeVersion());
        if (allowedNode != null) {
            assertThat(hits).extracting(HybridCandidate::documentNodeId).containsOnly(allowedNode);
        }
    }

    private static RetrievalScope scope(UUID userId, UUID topicId, RetrievalScopeTarget... targets) {
        return new RetrievalScope(userId, topicId, GroundingMode.STRICT_SOURCE, List.of(targets));
    }

    private static UUID id(String value) {
        return UUID.nameUUIDFromBytes(("p4-13:" + value).getBytes(StandardCharsets.UTF_8));
    }

    private static Fixture fixture() {
        return new Fixture(startApplicationWithFlywayAndArguments(new Class<?>[0],
                "--hippocampus.materials.processing.recovery.enabled=false"));
    }

    private static final class Fixture implements AutoCloseable {
        private final ConfigurableApplicationContext context;
        private final JdbcClient jdbc;
        private final RetrievalScopeSourceRepository scopeSources;
        private final LexicalSearchRepository lexicalSearch;
        private final VectorSearchRepository vectorSearch;
        private final HybridCandidateMerger merger = new HybridCandidateMerger();
        private final UUID generationId = id("active-index-generation");

        private Fixture(ConfigurableApplicationContext context) {
            this.context = context;
            this.jdbc = context.getBean(JdbcClient.class);
            this.scopeSources = context.getBean(RetrievalScopeSourceRepository.class);
            this.lexicalSearch = context.getBean(LexicalSearchRepository.class);
            this.vectorSearch = context.getBean(VectorSearchRepository.class);
            jdbc.sql("""
                    INSERT INTO index_generations(
                        id,embedding_provider,embedding_model,embedding_dimension,
                        chunking_version,status,created_at)
                    VALUES (?,'TEST','p4-13-synthetic',2,'CHUNKER_V1','ACTIVE',CURRENT_TIMESTAMP)
                    """).param(generationId).update();
        }

        private UUID insertUser(String key) {
            UUID userId = id(key);
            jdbc.sql("""
                    INSERT INTO users(id,email,status,created_at,updated_at)
                    VALUES (?,?,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                    """).params(userId, key + "@example.test").update();
            return userId;
        }

        private UUID insertTopic(
                UUID userId, String subjectStatus, String topicStatus, String key) {
            UUID subjectId = id(key + "-subject");
            UUID topicId = id(key);
            jdbc.sql("""
                    INSERT INTO subjects(id,user_id,name,status,created_at,updated_at)
                    VALUES (?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                    """).params(subjectId, userId, "Subject " + key, subjectStatus).update();
            jdbc.sql("""
                    INSERT INTO topics(id,subject_id,name,status,created_at,updated_at)
                    VALUES (?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                    """).params(topicId, subjectId, "Topic " + key, topicStatus).update();
            return topicId;
        }

        private Material insertMaterial(UUID userId, String status, int versionCount, String key) {
            UUID materialId = id(key);
            jdbc.sql("""
                    INSERT INTO materials(
                        id,user_id,title,material_type,status,created_at,updated_at)
                    VALUES (?,?,'Synthetic P4-13 source','PDF',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                    """).params(materialId, userId, status).update();
            UUID historicalVersion = null;
            UUID activeVersion = null;
            for (int versionNumber = 1; versionNumber <= versionCount; versionNumber++) {
                UUID versionId = id(key + "-version-" + versionNumber);
                jdbc.sql("""
                        INSERT INTO material_versions(
                            id,material_id,version_number,processing_status,created_at)
                        VALUES (?,?,?,'READY',CURRENT_TIMESTAMP)
                        """).params(versionId, materialId, versionNumber).update();
                if (versionNumber == 1 && versionCount > 1) {
                    historicalVersion = versionId;
                }
                activeVersion = versionId;
            }
            jdbc.sql("UPDATE materials SET active_version_id=? WHERE id=?")
                    .params(activeVersion, materialId).update();
            return new Material(materialId, activeVersion, historicalVersion);
        }

        private UUID insertNode(UUID versionId, String key, int ordinal) {
            UUID nodeId = id(key);
            jdbc.sql("""
                    INSERT INTO document_nodes(
                        id,material_version_id,node_type,title,ordinal,start_page,end_page,
                        detection_origin,created_at)
                    VALUES (?,?,'SECTION',?,?,1,1,'NATIVE',CURRENT_TIMESTAMP)
                    """).params(nodeId, versionId, "Section " + key, ordinal).update();
            return nodeId;
        }

        private void insertLink(
                UUID topicId, Material material, UUID versionId, UUID nodeId, String status) {
            jdbc.sql("""
                    INSERT INTO material_topic_links(
                        id,topic_id,material_id,material_version_id,document_node_id,
                        link_origin,status,created_at,updated_at)
                    VALUES (?,?,?,?,?,'USER_SELECTED',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                    """).params(id("link-" + topicId + "-" + material.id() + "-" + nodeId),
                            topicId, material.id(), versionId, nodeId, status).update();
        }

        private UUID insertChunk(
                UUID versionId, UUID nodeId, int chunkIndex, String content, boolean active, String key) {
            UUID chunkId = id(key);
            jdbc.sql("""
                    INSERT INTO chunks(
                        id,material_version_id,document_node_id,chunk_index,content,
                        page_start,page_end,heading_path,content_type,extraction_method,
                        quality,is_active,created_at)
                    VALUES (?,?,?,?,?,1,1,'["P4-13"]'::jsonb,'TEXT','NATIVE','STRONG',?,CURRENT_TIMESTAMP)
                    """).params(chunkId, versionId, nodeId, chunkIndex, content, active).update();
            return chunkId;
        }

        private void insertEmbedding(UUID chunkId, List<Float> vector, String key) {
            jdbc.sql("""
                    INSERT INTO chunk_embeddings(
                        id,chunk_id,index_generation_id,embedding,created_at)
                    VALUES (?,?,?,CAST(? AS vector),CURRENT_TIMESTAMP)
                    """).params(id(key), chunkId, generationId, vectorLiteral(vector)).update();
        }

        private void markMaterialDeleted(UUID materialId) {
            jdbc.sql("UPDATE materials SET status='DELETED', updated_at=CURRENT_TIMESTAMP WHERE id=?")
                    .param(materialId).update();
        }

        private RetrievalScope buildScope(UUID userId, UUID topicId) {
            CurrentUser currentUser = () -> new AuthenticatedUser(userId);
            return new BuildRetrievalScope(currentUser, scopeSources).execute(
                    new BuildRetrievalScope.Query(topicId, GroundingMode.STRICT_SOURCE));
        }

        private List<LexicalSearchHit> lexical(RetrievalScope scope, String query) {
            return lexicalSearch.search(new LexicalSearchRequest(scope, query, LIMIT));
        }

        private List<VectorSearchHit> vector(RetrievalScope scope) {
            return vectorSearch.search(new VectorSearchRequest(
                    scope, generationId, new EmbeddingVector(QUERY_VECTOR), LIMIT));
        }

        private List<HybridCandidate> hybrid(
                List<LexicalSearchHit> lexical, List<VectorSearchHit> vector) {
            return merger.merge(lexical, vector, LIMIT);
        }

        @Override
        public void close() {
            context.close();
        }

        private static String vectorLiteral(List<Float> vector) {
            return vector.toString().replace(" ", "");
        }
    }

    private record Material(UUID id, UUID activeVersion, UUID historicalVersion) {}
}
