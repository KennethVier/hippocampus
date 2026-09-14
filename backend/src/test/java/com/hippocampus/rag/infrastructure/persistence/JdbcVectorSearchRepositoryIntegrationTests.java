package com.hippocampus.rag.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.hippocampus.rag.port.EmbeddingVector;
import com.hippocampus.rag.port.VectorSearchHit;
import com.hippocampus.rag.port.VectorSearchRepository;
import com.hippocampus.rag.port.VectorSearchRequest;
import com.hippocampus.rag.port.VectorSearchScope;
import com.hippocampus.testing.PostgresIntegrationTestSupport;

class JdbcVectorSearchRepositoryIntegrationTests extends PostgresIntegrationTestSupport {

    @BeforeEach
    void resetDatabase() throws SQLException {
        resetPostgresSchema();
    }

    @Test
    void ranksByExactCosineSimilarityEnforcesLimitAndReturnsExactProvenance() {
        try (ConfigurableApplicationContext context = startApplication()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            UUID generation = insertGeneration(jdbc, "ACTIVE", 3);
            UUID user = insertUser(jdbc, "ranking");
            Material material = insertMaterial(jdbc, user, "ACTIVE", 1);
            UUID node = insertNode(jdbc, material.activeVersion(), "Motor pathways");
            UUID best = insertChunk(jdbc, material.activeVersion(), node, 1,
                    "Canonical corticospinal content", true, "[\"Motor\",\"Corticospinal\"]");
            UUID second = insertChunk(jdbc, material.activeVersion(), null, 2, "Second candidate", true, null);
            insertChunk(jdbc, material.activeVersion(), null, 3, "Third candidate", true, null);
            insertEmbedding(jdbc, best, generation, List.of(1.0F, 0.0F, 0.0F));
            insertEmbedding(jdbc, second, generation, List.of(1.0F, 1.0F, 0.0F));
            insertEmbedding(jdbc, jdbc.sql("SELECT id FROM chunks WHERE chunk_index=3 AND material_version_id=?")
                    .param(material.activeVersion()).query(UUID.class).single(), generation, List.of(0.0F, 1.0F, 0.0F));

            List<VectorSearchHit> hits = search(context, user, Set.of(material.activeVersion()), Set.of(),
                    generation, List.of(1.0F, 0.0F, 0.0F), 2);

            assertThat(hits).extracting(VectorSearchHit::chunkId).containsExactly(best, second);
            assertThat(hits).hasSize(2);
            assertThat(hits.getFirst().cosineSimilarity()).isEqualTo(1.0);
            assertThat(hits.get(1).cosineSimilarity()).isCloseTo(1.0 / Math.sqrt(2.0),
                    within(0.000001));
            assertThat(hits.getFirst()).satisfies(hit -> {
                assertThat(hit.materialId()).isEqualTo(material.id());
                assertThat(hit.materialVersionId()).isEqualTo(material.activeVersion());
                assertThat(hit.documentNodeId()).isEqualTo(node);
                assertThat(hit.indexGenerationId()).isEqualTo(generation);
                assertThat(hit.chunkIndex()).isEqualTo(1);
                assertThat(hit.content()).isEqualTo("Canonical corticospinal content");
                assertThat(hit.pageStart()).isEqualTo(1);
                assertThat(hit.pageEnd()).isEqualTo(2);
                assertThat(hit.headingPath()).containsExactly("Motor", "Corticospinal");
                assertThat(hit.contentType()).isEqualTo("TEXT");
                assertThat(hit.extractionMethod()).isEqualTo("NATIVE");
                assertThat(hit.quality()).isEqualTo("STRONG");
            });
        }
    }

    @Test
    void filtersOwnershipBeforeRankingEvenWhenForeignCandidateIsPerfect() {
        try (ConfigurableApplicationContext context = startApplication()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            UUID generation = insertGeneration(jdbc, "ACTIVE", 2);
            UUID userA = insertUser(jdbc, "user-a");
            UUID userB = insertUser(jdbc, "user-b");
            Material owned = insertMaterial(jdbc, userA, "ACTIVE", 1);
            Material foreign = insertMaterial(jdbc, userB, "ACTIVE", 1);
            UUID ownedChunk = insertChunk(jdbc, owned.activeVersion(), null, 1, "Owned semantic evidence", true, null);
            UUID foreignPerfect = insertChunk(jdbc, foreign.activeVersion(), null, 1,
                    "Foreign strongest possible evidence", true, null);
            UUID foreignIdentical = insertChunk(jdbc, foreign.activeVersion(), null, 2,
                    "Foreign semantically identical evidence", true, null);
            insertEmbedding(jdbc, ownedChunk, generation, List.of(0.8F, 0.2F));
            insertEmbedding(jdbc, foreignPerfect, generation, List.of(1.0F, 0.0F));
            insertEmbedding(jdbc, foreignIdentical, generation, List.of(0.8F, 0.2F));

            List<VectorSearchHit> mixed = search(context, userA,
                    Set.of(owned.activeVersion(), foreign.activeVersion()), Set.of(),
                    generation, List.of(1.0F, 0.0F), 10);
            List<VectorSearchHit> forgedForeignOnly = search(context, userA,
                    Set.of(foreign.activeVersion()), Set.of(), generation, List.of(1.0F, 0.0F), 10);

            assertThat(mixed).extracting(VectorSearchHit::chunkId).containsExactly(ownedChunk);
            assertThat(mixed).noneMatch(hit -> Set.of(foreignPerfect, foreignIdentical).contains(hit.chunkId()));
            assertThat(forgedForeignOnly).isEmpty();
        }
    }

    @Test
    void excludesZeroNormCandidateBeforeRankingAlongsideValidCandidate() {
        try (ConfigurableApplicationContext context = startApplication()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            UUID generation = insertGeneration(jdbc, "ACTIVE", 2);
            UUID user = insertUser(jdbc, "mixed-zero-norm");
            Material material = insertMaterial(jdbc, user, "ACTIVE", 1);
            UUID valid = insertChunk(jdbc, material.activeVersion(), null, 1, "Valid candidate", true, null);
            UUID zeroNorm = insertChunk(jdbc, material.activeVersion(), null, 2, "Zero-norm candidate", true, null);
            insertEmbedding(jdbc, valid, generation, List.of(1.0F, 0.0F));
            insertEmbedding(jdbc, zeroNorm, generation, List.of(0.0F, 0.0F));

            List<VectorSearchHit> hits = search(context, user, Set.of(material.activeVersion()), Set.of(),
                    generation, List.of(1.0F, 0.0F), 10);

            assertThat(hits).extracting(VectorSearchHit::chunkId).containsExactly(valid);
            assertThat(hits).allMatch(hit -> Double.isFinite(hit.cosineSimilarity()));
        }
    }

    @Test
    void returnsNoHitsWhenAuthorizedCandidatesAreOnlyZeroNorm() {
        try (ConfigurableApplicationContext context = startApplication()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            UUID generation = insertGeneration(jdbc, "ACTIVE", 2);
            UUID user = insertUser(jdbc, "only-zero-norm");
            Material material = insertMaterial(jdbc, user, "ACTIVE", 1);
            UUID zeroNorm = insertChunk(jdbc, material.activeVersion(), null, 1, "Zero-norm candidate", true, null);
            insertEmbedding(jdbc, zeroNorm, generation, List.of(0.0F, 0.0F));

            assertThat(search(context, user, Set.of(material.activeVersion()), Set.of(),
                    generation, List.of(1.0F, 0.0F), 10)).isEmpty();
        }
    }

    @Test
    void excludesHistoricalDeletedAndInactiveCandidatesAndSupportsNodeNarrowing() {
        try (ConfigurableApplicationContext context = startApplication()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            UUID generation = insertGeneration(jdbc, "ACTIVE", 2);
            UUID user = insertUser(jdbc, "eligibility");
            Material owned = insertMaterial(jdbc, user, "ACTIVE", 2);
            Material deleted = insertMaterial(jdbc, user, "DELETED", 1);
            UUID firstNode = insertNode(jdbc, owned.activeVersion(), "First");
            UUID secondNode = insertNode(jdbc, owned.activeVersion(), "Second");
            UUID visible = insertChunk(jdbc, owned.activeVersion(), secondNode, 1, "Visible", true, null);
            UUID otherNode = insertChunk(jdbc, owned.activeVersion(), firstNode, 2, "Other node", true, null);
            UUID historical = insertChunk(jdbc, owned.historicalVersion(), null, 1, "Historical perfect", true, null);
            UUID inactive = insertChunk(jdbc, owned.activeVersion(), null, 3, "Inactive perfect", false, null);
            UUID deletedChunk = insertChunk(jdbc, deleted.activeVersion(), null, 1, "Deleted perfect", true, null);
            insertEmbedding(jdbc, visible, generation, List.of(0.9F, 0.1F));
            insertEmbedding(jdbc, otherNode, generation, List.of(0.7F, 0.3F));
            insertEmbedding(jdbc, historical, generation, List.of(1.0F, 0.0F));
            insertEmbedding(jdbc, inactive, generation, List.of(1.0F, 0.0F));
            insertEmbedding(jdbc, deletedChunk, generation, List.of(1.0F, 0.0F));

            Set<UUID> wideVersions = Set.of(
                    owned.activeVersion(), owned.historicalVersion(), deleted.activeVersion());
            List<VectorSearchHit> allEligible = search(context, user, wideVersions, Set.of(),
                    generation, List.of(1.0F, 0.0F), 10);
            List<VectorSearchHit> narrowed = search(context, user, wideVersions, Set.of(secondNode),
                    generation, List.of(1.0F, 0.0F), 10);

            assertThat(allEligible).extracting(VectorSearchHit::chunkId).containsExactly(visible, otherNode);
            assertThat(narrowed).extracting(VectorSearchHit::chunkId).containsExactly(visible);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"BUILDING", "INACTIVE", "FAILED"})
    void excludesNonActiveGenerations(String status) {
        try (ConfigurableApplicationContext context = startApplication()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            UUID generation = insertGeneration(jdbc, status, 2);
            UUID user = insertUser(jdbc, "generation-" + status.toLowerCase());
            Material material = insertMaterial(jdbc, user, "ACTIVE", 1);
            UUID chunk = insertChunk(jdbc, material.activeVersion(), null, 1, "Generation candidate", true, null);
            insertEmbedding(jdbc, chunk, generation, List.of(1.0F, 0.0F));

            assertThat(search(context, user, Set.of(material.activeVersion()), Set.of(),
                    generation, List.of(1.0F, 0.0F), 10)).isEmpty();
        }
    }

    @Test
    void isolatesGenerationsAndFailsClosedOnQueryDimensionMismatch() {
        try (ConfigurableApplicationContext context = startApplication()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            UUID activeTwoDimensions = insertGeneration(jdbc, "ACTIVE", 2);
            UUID activeThreeDimensions = insertGeneration(jdbc, "ACTIVE", 3);
            UUID user = insertUser(jdbc, "dimensions");
            Material material = insertMaterial(jdbc, user, "ACTIVE", 1);
            UUID chunk = insertChunk(jdbc, material.activeVersion(), null, 1, "Same chunk", true, null);
            insertEmbedding(jdbc, chunk, activeTwoDimensions, List.of(1.0F, 0.0F));
            insertEmbedding(jdbc, chunk, activeThreeDimensions, List.of(0.0F, 1.0F, 0.0F));

            List<VectorSearchHit> twoDimensions = search(context, user, Set.of(material.activeVersion()), Set.of(),
                    activeTwoDimensions, List.of(1.0F, 0.0F), 10);
            List<VectorSearchHit> threeDimensions = search(context, user, Set.of(material.activeVersion()), Set.of(),
                    activeThreeDimensions, List.of(0.0F, 1.0F, 0.0F), 10);
            List<VectorSearchHit> mismatch = search(context, user, Set.of(material.activeVersion()), Set.of(),
                    activeTwoDimensions, List.of(1.0F, 0.0F, 0.0F), 10);

            assertThat(twoDimensions).singleElement()
                    .extracting(VectorSearchHit::indexGenerationId).isEqualTo(activeTwoDimensions);
            assertThat(threeDimensions).singleElement()
                    .extracting(VectorSearchHit::indexGenerationId).isEqualTo(activeThreeDimensions);
            assertThat(mismatch).isEmpty();
            assertThat(search(context, user, Set.of(material.activeVersion()), Set.of(),
                    UUID.randomUUID(), List.of(1.0F, 0.0F), 10)).isEmpty();
        }
    }

    @Test
    void emptyAllowedVersionScopeFailsClosedAndTiesAreDeterministic() {
        try (ConfigurableApplicationContext context = startApplication()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            UUID generation = insertGeneration(jdbc, "ACTIVE", 2);
            UUID user = insertUser(jdbc, "ties");
            Material firstMaterial = insertMaterial(jdbc, user, "ACTIVE", 1);
            Material secondMaterial = insertMaterial(jdbc, user, "ACTIVE", 1);
            UUID higher = new UUID(0, 20);
            UUID lower = new UUID(0, 10);
            UUID laterIndex = new UUID(0, 30);
            insertChunk(jdbc, firstMaterial.activeVersion(), null, 2, "Later", true, null, laterIndex);
            insertChunk(jdbc, firstMaterial.activeVersion(), null, 1, "Higher UUID", true, null, higher);
            insertChunk(jdbc, secondMaterial.activeVersion(), null, 1, "Lower UUID", true, null, lower);
            insertEmbedding(jdbc, laterIndex, generation, List.of(1.0F, 0.0F));
            insertEmbedding(jdbc, higher, generation, List.of(1.0F, 0.0F));
            insertEmbedding(jdbc, lower, generation, List.of(1.0F, 0.0F));

            Set<UUID> versions = Set.of(firstMaterial.activeVersion(), secondMaterial.activeVersion());
            assertThat(search(context, user, versions, Set.of(), generation, List.of(1.0F, 0.0F), 10))
                    .extracting(VectorSearchHit::chunkId).containsExactly(lower, higher, laterIndex);
            assertThat(search(context, user, Set.of(), Set.of(), generation, List.of(1.0F, 0.0F), 10))
                    .isEmpty();
        }
    }

    private static org.assertj.core.data.Offset<Double> within(double value) {
        return org.assertj.core.data.Offset.offset(value);
    }

    private static ConfigurableApplicationContext startApplication() {
        return startApplicationWithFlywayAndArguments(new Class<?>[0],
                "--hippocampus.materials.processing.recovery.enabled=false");
    }

    private static List<VectorSearchHit> search(
            ConfigurableApplicationContext context, UUID user, Set<UUID> versions, Set<UUID> nodes,
            UUID generation, List<Float> query, int limit) {
        return context.getBean(VectorSearchRepository.class).search(new VectorSearchRequest(
                new VectorSearchScope(user, versions, nodes), generation, new EmbeddingVector(query), limit));
    }

    private static UUID insertUser(JdbcClient jdbc, String name) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO users(id,email,status,created_at,updated_at) VALUES (?,?,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")
                .params(id, name + "-" + id + "@example.test").update();
        return id;
    }

    private static Material insertMaterial(JdbcClient jdbc, UUID user, String status, int versionCount) {
        UUID material = UUID.randomUUID();
        jdbc.sql("INSERT INTO materials(id,user_id,title,material_type,status,created_at,updated_at) VALUES (?,?,'Source','PDF',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")
                .params(material, user, status).update();
        UUID historical = null;
        UUID active = null;
        for (int versionNumber = 1; versionNumber <= versionCount; versionNumber++) {
            UUID version = UUID.randomUUID();
            jdbc.sql("INSERT INTO material_versions(id,material_id,version_number,processing_status,created_at) VALUES (?,?,?,'READY',CURRENT_TIMESTAMP)")
                    .params(version, material, versionNumber).update();
            if (versionNumber == 1 && versionCount > 1) {
                historical = version;
            }
            active = version;
        }
        jdbc.sql("UPDATE materials SET active_version_id=? WHERE id=?").params(active, material).update();
        return new Material(material, active, historical);
    }

    private static UUID insertNode(JdbcClient jdbc, UUID version, String title) {
        UUID id = UUID.randomUUID();
        int ordinal = jdbc.sql("SELECT count(*) + 1 FROM document_nodes WHERE material_version_id=?")
                .param(version).query(Integer.class).single();
        jdbc.sql("""
                INSERT INTO document_nodes(
                    id,material_version_id,node_type,title,ordinal,start_page,end_page,detection_origin,created_at)
                VALUES (?,?,'SECTION',?,?,1,2,'NATIVE',CURRENT_TIMESTAMP)
                """).params(id, version, title, ordinal).update();
        return id;
    }

    private static UUID insertGeneration(JdbcClient jdbc, String status, int dimension) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO index_generations(
                    id,embedding_provider,embedding_model,embedding_dimension,chunking_version,status,created_at)
                VALUES (?,'TEST','synthetic',?,'CHUNKER_V1',?,CURRENT_TIMESTAMP)
                """).params(id, dimension, status).update();
        return id;
    }

    private static UUID insertChunk(
            JdbcClient jdbc, UUID version, UUID node, int index, String content,
            boolean active, String headingPath) {
        return insertChunk(jdbc, version, node, index, content, active, headingPath, UUID.randomUUID());
    }

    private static UUID insertChunk(
            JdbcClient jdbc, UUID version, UUID node, int index, String content,
            boolean active, String headingPath, UUID id) {
        jdbc.sql("""
                INSERT INTO chunks(
                    id,material_version_id,document_node_id,chunk_index,content,page_start,page_end,
                    heading_path,content_type,extraction_method,quality,is_active,created_at)
                VALUES (?,?,?,?,?,1,2,CAST(? AS jsonb),'TEXT','NATIVE','STRONG',?,CURRENT_TIMESTAMP)
                """).params(id, version, node, index, content, headingPath, active).update();
        return id;
    }

    private static void insertEmbedding(
            JdbcClient jdbc, UUID chunk, UUID generation, List<Float> values) {
        jdbc.sql("""
                INSERT INTO chunk_embeddings(id,chunk_id,index_generation_id,embedding,created_at)
                VALUES (?,?,?,CAST(? AS vector),CURRENT_TIMESTAMP)
                """).params(UUID.randomUUID(), chunk, generation, vectorLiteral(values)).update();
    }

    private static String vectorLiteral(List<Float> values) {
        return values.toString().replace(" ", "");
    }

    private record Material(UUID id, UUID activeVersion, UUID historicalVersion) {}
}
