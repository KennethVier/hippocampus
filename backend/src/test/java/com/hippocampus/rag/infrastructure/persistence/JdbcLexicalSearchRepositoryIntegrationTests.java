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

import com.hippocampus.rag.port.LexicalSearchHit;
import com.hippocampus.rag.port.LexicalSearchRepository;
import com.hippocampus.rag.port.LexicalSearchRequest;
import com.hippocampus.rag.port.LexicalSearchScope;
import com.hippocampus.testing.PostgresIntegrationTestSupport;

class JdbcLexicalSearchRepositoryIntegrationTests extends PostgresIntegrationTestSupport {

    @BeforeEach
    void resetDatabase() throws SQLException {
        resetPostgresSchema();
    }

    @ParameterizedTest
    @ValueSource(strings = {"C5-T1", "CN VII", "β1", "Na+", "IL-6", "posterior cord"})
    void retrievesGoldenMedicalLexicalTerms(String query) {
        try (ConfigurableApplicationContext context = startApplication()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            UUID user = insertUser(jdbc, "golden");
            Material material = insertMaterial(jdbc, user, "ACTIVE", 1);
            UUID expected = insertChunk(jdbc, material.activeVersion(), null, 1,
                    "The anatomy reference includes " + query + " in canonical source text.", true, null);

            List<LexicalSearchHit> hits = search(context, user, Set.of(material.activeVersion()), Set.of(), query, 10);

            assertThat(hits).extracting(LexicalSearchHit::chunkId).contains(expected);
            assertThat(hits.getFirst().content()).contains(query);
        }
    }

    @Test
    void combinesFtsCaseInsensitiveLiteralAndTrigramSignalsWithDeterministicRanking() {
        try (ConfigurableApplicationContext context = startApplication()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            UUID user = insertUser(jdbc, "signals");
            Material material = insertMaterial(jdbc, user, "ACTIVE", 1);
            UUID exact = insertChunk(jdbc, material.activeVersion(), null, 1,
                    "The posterior cord carries sensory pathways.", true, null);
            UUID fuzzy = insertChunk(jdbc, material.activeVersion(), null, 2,
                    "Posterior spinal cord pathway overview.", true, null);
            insertChunk(jdbc, material.activeVersion(), null, 3,
                    "Cardiac output determines organ perfusion.", true, null);

            List<LexicalSearchHit> ranked = search(
                    context, user, Set.of(material.activeVersion()), Set.of(), "POSTERIOR CORD", 10);
            List<LexicalSearchHit> fts = search(
                    context, user, Set.of(material.activeVersion()), Set.of(), "cardiac perfusion", 10);
            List<LexicalSearchHit> trigram = search(
                    context, user, Set.of(material.activeVersion()), Set.of(), "postrior cord", 10);

            assertThat(ranked).extracting(LexicalSearchHit::chunkId).startsWith(exact, fuzzy);
            assertThat(ranked.getFirst().exactMatch()).isTrue();
            assertThat(ranked.get(1).exactMatch()).isFalse();
            assertThat(fts.getFirst().fullTextRank()).isPositive();
            assertThat(fts.getFirst().exactMatch()).isFalse();
            assertThat(trigram).extracting(LexicalSearchHit::chunkId).contains(exact);
            assertThat(trigram.stream().filter(hit -> hit.chunkId().equals(exact)).findFirst().orElseThrow().trigramScore())
                    .isPositive();
        }
    }

    @Test
    void enforcesOwnershipActiveVersionChunkAndMaterialStatusInsideCandidateQuery() {
        try (ConfigurableApplicationContext context = startApplication()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            UUID userA = insertUser(jdbc, "user-a");
            UUID userB = insertUser(jdbc, "user-b");
            Material owned = insertMaterial(jdbc, userA, "ACTIVE", 2);
            Material foreign = insertMaterial(jdbc, userB, "ACTIVE", 1);
            Material deleted = insertMaterial(jdbc, userA, "DELETED", 1);
            UUID visible = insertChunk(jdbc, owned.activeVersion(), null, 1, "isolation-marker", true, null);
            insertChunk(jdbc, owned.historicalVersion(), null, 1, "isolation-marker", true, null);
            insertChunk(jdbc, owned.activeVersion(), null, 2, "isolation-marker", false, null);
            insertChunk(jdbc, foreign.activeVersion(), null, 1, "isolation-marker", true, null);
            insertChunk(jdbc, deleted.activeVersion(), null, 1, "isolation-marker", true, null);

            Set<UUID> deliberatelyWideScope = Set.of(
                    owned.activeVersion(), owned.historicalVersion(),
                    foreign.activeVersion(), deleted.activeVersion());
            List<LexicalSearchHit> hits = search(
                    context, userA, deliberatelyWideScope, Set.of(), "isolation-marker", 20);

            assertThat(hits).extracting(LexicalSearchHit::chunkId).containsExactly(visible);
            assertThat(hits).allMatch(hit -> hit.materialVersionId().equals(owned.activeVersion()));
            assertThat(search(context, userA, Set.of(foreign.activeVersion()), Set.of(), "isolation-marker", 20))
                    .isEmpty();
        }
    }

    @Test
    void narrowsByDocumentNodeAndReturnsCanonicalProvenance() {
        try (ConfigurableApplicationContext context = startApplication()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            UUID user = insertUser(jdbc, "nodes");
            Material material = insertMaterial(jdbc, user, "ACTIVE", 1);
            UUID firstNode = insertNode(jdbc, material.activeVersion(), "First");
            UUID secondNode = insertNode(jdbc, material.activeVersion(), "Second");
            insertChunk(jdbc, material.activeVersion(), firstNode, 1, "node-marker first", true, null);
            UUID expected = insertChunk(jdbc, material.activeVersion(), secondNode, 2,
                    "node-marker canonical", true, "[\"Brachial plexus\",\"Roots\"]");

            List<LexicalSearchHit> hits = search(
                    context, user, Set.of(material.activeVersion()), Set.of(secondNode), "node-marker", 10);

            assertThat(hits).singleElement().satisfies(hit -> {
                assertThat(hit.chunkId()).isEqualTo(expected);
                assertThat(hit.documentNodeId()).isEqualTo(secondNode);
                assertThat(hit.content()).isEqualTo("node-marker canonical");
                assertThat(hit.headingPath()).containsExactly("Brachial plexus", "Roots");
                assertThat(hit.pageStart()).isEqualTo(1);
                assertThat(hit.pageEnd()).isEqualTo(2);
                assertThat(hit.contentType()).isEqualTo("TEXT");
                assertThat(hit.extractionMethod()).isEqualTo("NATIVE");
                assertThat(hit.quality()).isEqualTo("STRONG");
            });
        }
    }

    @Test
    void failsClosedForEmptyScopeAndTreatsLikeMetacharactersAndInjectionShapeAsData() {
        try (ConfigurableApplicationContext context = startApplication()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            UUID user = insertUser(jdbc, "patterns");
            Material material = insertMaterial(jdbc, user, "ACTIVE", 1);
            UUID percent = insertChunk(jdbc, material.activeVersion(), null, 1, "dose is 100% complete", true, null);
            UUID underscore = insertChunk(jdbc, material.activeVersion(), null, 2, "literal_under score", true, null);
            UUID backslash = insertChunk(jdbc, material.activeVersion(), null, 3, "route C:\\nerve", true, null);
            UUID injection = insertChunk(jdbc, material.activeVersion(), null, 4, "x' OR 1=1 --", true, null);
            insertChunk(jdbc, material.activeVersion(), null, 5, "unrelated ordinary content", true, null);

            assertThat(search(context, user, Set.of(), Set.of(), "ordinary", 10)).isEmpty();
            assertThat(search(context, user, Set.of(material.activeVersion()), Set.of(), "%", 10))
                    .extracting(LexicalSearchHit::chunkId).containsExactly(percent);
            assertThat(search(context, user, Set.of(material.activeVersion()), Set.of(), "_", 10))
                    .extracting(LexicalSearchHit::chunkId).containsExactly(underscore);
            assertThat(search(context, user, Set.of(material.activeVersion()), Set.of(), "\\", 10))
                    .extracting(LexicalSearchHit::chunkId).containsExactly(backslash);
            assertThat(search(context, user, Set.of(material.activeVersion()), Set.of(), "x' OR 1=1 --", 10))
                    .extracting(LexicalSearchHit::chunkId).containsExactly(injection);
        }
    }

    @Test
    void enforcesCallerLimitAndOrdersTiesByChunkIndexThenUuid() {
        try (ConfigurableApplicationContext context = startApplication()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            UUID user = insertUser(jdbc, "ordering");
            Material firstMaterial = insertMaterial(jdbc, user, "ACTIVE", 1);
            Material secondMaterial = insertMaterial(jdbc, user, "ACTIVE", 1);
            UUID higher = new UUID(0, 20);
            UUID lower = new UUID(0, 10);
            insertChunk(jdbc, firstMaterial.activeVersion(), null, 2, "tie-marker", true, null, new UUID(0, 30));
            insertChunk(jdbc, firstMaterial.activeVersion(), null, 1, "tie-marker", true, null, higher);
            insertChunk(jdbc, secondMaterial.activeVersion(), null, 1, "tie-marker", true, null, lower);

            List<LexicalSearchHit> hits = search(context, user,
                    Set.of(firstMaterial.activeVersion(), secondMaterial.activeVersion()), Set.of(), "tie-marker", 2);

            assertThat(hits).extracting(LexicalSearchHit::chunkId).containsExactly(lower, higher);
        }
    }

    @Test
    void installsPartialFtsAndTrigramIndexes() {
        try (ConfigurableApplicationContext context = startApplication()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            List<String> definitions = jdbc.sql("""
                    SELECT indexdef FROM pg_indexes
                    WHERE schemaname='public'
                      AND indexname IN ('idx_chunks_active_content_fts', 'idx_chunks_active_content_trgm')
                    ORDER BY indexname
                    """).query(String.class).list();

            assertThat(definitions).hasSize(2)
                    .allMatch(definition -> definition.contains("WHERE (is_active = true)"));
            assertThat(definitions).anyMatch(definition -> definition.contains("to_tsvector('simple'::regconfig, content)"));
            assertThat(definitions).anyMatch(definition -> definition.contains("gin_trgm_ops"));
            assertThat(jdbc.sql("SELECT extname FROM pg_extension WHERE extname='pg_trgm'")
                    .query(String.class).single()).isEqualTo("pg_trgm");
        }
    }

    private static ConfigurableApplicationContext startApplication() {
        return startApplicationWithFlywayAndArguments(new Class<?>[0],
                "--hippocampus.materials.processing.recovery.enabled=false");
    }

    private static List<LexicalSearchHit> search(
            ConfigurableApplicationContext context, UUID user, Set<UUID> versions,
            Set<UUID> nodes, String query, int limit) {
        return context.getBean(LexicalSearchRepository.class).search(
                new LexicalSearchRequest(new LexicalSearchScope(user, versions, nodes), query, limit));
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

    private record Material(UUID id, UUID activeVersion, UUID historicalVersion) {}
}
