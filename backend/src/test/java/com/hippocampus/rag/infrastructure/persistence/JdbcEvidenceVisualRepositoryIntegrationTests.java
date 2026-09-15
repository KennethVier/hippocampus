package com.hippocampus.rag.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.hippocampus.rag.application.BuildEvidencePackage;
import com.hippocampus.rag.application.HybridCandidate;
import com.hippocampus.rag.application.HybridLexicalSignal;
import com.hippocampus.rag.domain.EvidenceLimitationCode;
import com.hippocampus.rag.domain.EvidencePackage;
import com.hippocampus.rag.domain.EvidencePackageBudget;
import com.hippocampus.rag.domain.GroundingMode;
import com.hippocampus.rag.domain.RetrievalQuality;
import com.hippocampus.rag.domain.RetrievalScope;
import com.hippocampus.rag.domain.RetrievalScopeTarget;
import com.hippocampus.rag.port.EvidenceVisualRepository;
import com.hippocampus.rag.port.EvidenceVisualSource;
import com.hippocampus.testing.PostgresIntegrationTestSupport;

class JdbcEvidenceVisualRepositoryIntegrationTests extends PostgresIntegrationTestSupport {

    @BeforeEach
    void resetDatabase() throws SQLException {
        resetPostgresSchema();
    }

    @Test
    void buildsSupportedAndLimitedVisualEvidenceWithMetadataAndMergedLinks() {
        try (ConfigurableApplicationContext context = startApplication()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            UUID user = insertUser(jdbc, "visual-package");
            Material material = insertMaterial(jdbc, user, "ACTIVE", 1);
            UUID node = insertNode(jdbc, material.activeVersion(), "Authorized");
            UUID firstChunk = insertChunk(jdbc, material.activeVersion(), node, 1, true);
            UUID secondChunk = insertChunk(jdbc, material.activeVersion(), node, 2, true);
            UUID supported = insertVisual(jdbc, material.activeVersion(), node, 8,
                    "ANATOMY_DIAGRAM", "Brachial plexus caption", "Roots and trunks", "SUPPORTED");
            UUID limited = insertVisual(jdbc, material.activeVersion(), node, 9,
                    "CHART", "Limited caption", null, "LIMITED");
            UUID unlinked = insertVisual(jdbc, material.activeVersion(), node, 10,
                    "OTHER", "Must not appear", null, "SUPPORTED");
            link(jdbc, firstChunk, supported, material.activeVersion(), "NEARBY");
            link(jdbc, secondChunk, supported, material.activeVersion(), "REFERENCES");
            link(jdbc, firstChunk, limited, material.activeVersion(), "CAPTION_FOR");

            EvidencePackage evidence = build(context, scope(user,
                    new RetrievalScopeTarget(material.activeVersion(), Set.of(node))),
                    List.of(candidate(firstChunk, material, node, 1), candidate(secondChunk, material, node, 2)), 5);

            assertThat(evidence.visuals()).extracting(visual -> visual.visualId())
                    .containsExactly(supported, limited).doesNotContain(unlinked);
            assertThat(evidence.visuals().getFirst()).satisfies(visual -> {
                assertThat(visual.caption()).isEqualTo("Brachial plexus caption");
                assertThat(visual.nearbyText()).isEqualTo("Roots and trunks");
                assertThat(visual.pageNumber()).isEqualTo(8);
                assertThat(visual.visualType()).isEqualTo("ANATOMY_DIAGRAM");
                assertThat(visual.linkedChunkIds()).containsExactlyInAnyOrder(firstChunk, secondChunk);
                assertThat(visual.relationshipTypes()).containsExactlyInAnyOrder("NEARBY", "REFERENCES");
            });
            assertThat(codes(evidence)).containsExactly(EvidenceLimitationCode.LIMITED_VISUAL_INTERPRETATION);
        }
    }

    @Test
    void excludesUnassessedUnsupportedAndFailedVisuals() {
        try (ConfigurableApplicationContext context = startApplication()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            UUID user = insertUser(jdbc, "statuses");
            Material material = insertMaterial(jdbc, user, "ACTIVE", 1);
            UUID chunk = insertChunk(jdbc, material.activeVersion(), null, 1, true);
            for (String status : List.of("UNASSESSED", "UNSUPPORTED", "FAILED")) {
                UUID visual = insertVisual(jdbc, material.activeVersion(), null, 1, "OTHER", status, null, status);
                link(jdbc, chunk, visual, material.activeVersion(), "NEARBY");
            }

            EvidencePackage evidence = build(context, scope(user,
                    new RetrievalScopeTarget(material.activeVersion(), Set.of())),
                    List.of(candidate(chunk, material, null, 1)), 5);

            assertThat(evidence.visuals()).isEmpty();
            assertThat(codes(evidence)).containsExactly(
                    EvidenceLimitationCode.VISUAL_EXCLUDED_BY_INTERPRETATION_STATUS);
        }
    }

    @Test
    void foreignDeletedHistoricalAndInactiveChunkSourcesCannotProduceVisualEvidence() {
        try (ConfigurableApplicationContext context = startApplication()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            UUID user = insertUser(jdbc, "owner");
            UUID foreignUser = insertUser(jdbc, "foreign");
            Material foreign = insertMaterial(jdbc, foreignUser, "ACTIVE", 1);
            Material deleted = insertMaterial(jdbc, user, "DELETED", 1);
            Material versioned = insertMaterial(jdbc, user, "ACTIVE", 2);
            Material inactive = insertMaterial(jdbc, user, "ACTIVE", 1);
            UUID foreignChunk = insertChunk(jdbc, foreign.activeVersion(), null, 1, true);
            UUID deletedChunk = insertChunk(jdbc, deleted.activeVersion(), null, 1, true);
            UUID historicalChunk = insertChunk(jdbc, versioned.historicalVersion(), null, 1, true);
            UUID inactiveChunk = insertChunk(jdbc, inactive.activeVersion(), null, 1, false);
            for (ChunkMaterial source : List.of(
                    new ChunkMaterial(foreignChunk, foreign), new ChunkMaterial(deletedChunk, deleted),
                    new ChunkMaterial(historicalChunk, new Material(versioned.id(), versioned.historicalVersion(), null)),
                    new ChunkMaterial(inactiveChunk, inactive))) {
                UUID visual = insertVisual(jdbc, source.material().activeVersion(), null, 1,
                        "OTHER", "Excluded", null, "SUPPORTED");
                link(jdbc, source.chunkId(), visual, source.material().activeVersion(), "NEARBY");
            }
            RetrievalScope deliberatelyWide = new RetrievalScope(user, UUID.randomUUID(), GroundingMode.STRICT_SOURCE,
                    List.of(
                            new RetrievalScopeTarget(foreign.activeVersion(), Set.of()),
                            new RetrievalScopeTarget(deleted.activeVersion(), Set.of()),
                            new RetrievalScopeTarget(versioned.historicalVersion(), Set.of()),
                            new RetrievalScopeTarget(inactive.activeVersion(), Set.of())));

            List<EvidenceVisualSource> rows = context.getBean(EvidenceVisualRepository.class)
                    .findLinkedVisuals(deliberatelyWide,
                            Set.of(foreignChunk, deletedChunk, historicalChunk, inactiveChunk));

            assertThat(rows).isEmpty();
        }
    }

    @Test
    void nodeOnlyScopeRequiresTheVisualItselfToHaveTheAuthorizedNode() {
        try (ConfigurableApplicationContext context = startApplication()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            UUID user = insertUser(jdbc, "node-scope");
            Material material = insertMaterial(jdbc, user, "ACTIVE", 1);
            UUID nodeA = insertNode(jdbc, material.activeVersion(), "A");
            UUID nodeB = insertNode(jdbc, material.activeVersion(), "B");
            UUID chunk = insertChunk(jdbc, material.activeVersion(), nodeA, 1, true);
            UUID sameNode = insertVisual(jdbc, material.activeVersion(), nodeA, 1,
                    "OTHER", "Same node", null, "SUPPORTED");
            UUID otherNode = insertVisual(jdbc, material.activeVersion(), nodeB, 2,
                    "OTHER", "Other node", null, "SUPPORTED");
            UUID nullNode = insertVisual(jdbc, material.activeVersion(), null, 3,
                    "OTHER", "Null node", null, "SUPPORTED");
            link(jdbc, chunk, sameNode, material.activeVersion(), "NEARBY");
            link(jdbc, chunk, otherNode, material.activeVersion(), "NEARBY");
            link(jdbc, chunk, nullNode, material.activeVersion(), "NEARBY");

            EvidencePackage evidence = build(context, scope(user,
                    new RetrievalScopeTarget(material.activeVersion(), Set.of(nodeA))),
                    List.of(candidate(chunk, material, nodeA, 1)), 5);

            assertThat(evidence.visuals()).extracting(visual -> visual.visualId()).containsExactly(sameNode);
        }
    }

    @Test
    void wholeVersionAndMixedScopesRemainBroadAndNarrowPerVersion() {
        try (ConfigurableApplicationContext context = startApplication()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            UUID user = insertUser(jdbc, "mixed-scope");
            Material whole = insertMaterial(jdbc, user, "ACTIVE", 1);
            Material narrowed = insertMaterial(jdbc, user, "ACTIVE", 1);
            UUID wholeChunkNode = insertNode(jdbc, whole.activeVersion(), "Whole chunk");
            UUID wholeVisualNode = insertNode(jdbc, whole.activeVersion(), "Elsewhere");
            UUID allowedNode = insertNode(jdbc, narrowed.activeVersion(), "Allowed");
            UUID blockedNode = insertNode(jdbc, narrowed.activeVersion(), "Blocked");
            UUID wholeChunk = insertChunk(jdbc, whole.activeVersion(), wholeChunkNode, 1, true);
            UUID narrowedChunk = insertChunk(jdbc, narrowed.activeVersion(), allowedNode, 1, true);
            UUID broadVisual = insertVisual(jdbc, whole.activeVersion(), wholeVisualNode, 2,
                    "OTHER", "Broad", null, "SUPPORTED");
            UUID narrowVisual = insertVisual(jdbc, narrowed.activeVersion(), allowedNode, 3,
                    "OTHER", "Narrow", null, "SUPPORTED");
            UUID blockedVisual = insertVisual(jdbc, narrowed.activeVersion(), blockedNode, 4,
                    "OTHER", "Blocked", null, "SUPPORTED");
            link(jdbc, wholeChunk, broadVisual, whole.activeVersion(), "NEARBY");
            link(jdbc, narrowedChunk, narrowVisual, narrowed.activeVersion(), "NEARBY");
            link(jdbc, narrowedChunk, blockedVisual, narrowed.activeVersion(), "NEARBY");
            RetrievalScope mixed = new RetrievalScope(user, UUID.randomUUID(), GroundingMode.SOURCE_FIRST,
                    List.of(new RetrievalScopeTarget(whole.activeVersion(), Set.of()),
                            new RetrievalScopeTarget(narrowed.activeVersion(), Set.of(allowedNode))));

            EvidencePackage evidence = build(context, mixed,
                    List.of(candidate(wholeChunk, whole, wholeChunkNode, 1),
                            candidate(narrowedChunk, narrowed, allowedNode, 1)), 5);

            assertThat(evidence.visuals()).extracting(visual -> visual.visualId())
                    .containsExactlyInAnyOrder(broadVisual, narrowVisual).doesNotContain(blockedVisual);
        }
    }

    @Test
    void visualBudgetUsesSelectedChunkRankThenPageThenVisualIdAndEmptySetSkipsSql() {
        try (ConfigurableApplicationContext context = startApplication()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            UUID user = insertUser(jdbc, "order");
            Material material = insertMaterial(jdbc, user, "ACTIVE", 1);
            UUID firstChunk = insertChunk(jdbc, material.activeVersion(), null, 1, true);
            UUID secondChunk = insertChunk(jdbc, material.activeVersion(), null, 2, true);
            UUID firstById = new UUID(0, 100);
            UUID secondById = new UUID(0, 200);
            insertVisual(jdbc, material.activeVersion(), null, 5, "OTHER", "ID first", null,
                    "SUPPORTED", firstById);
            insertVisual(jdbc, material.activeVersion(), null, 5, "OTHER", "ID second", null,
                    "SUPPORTED", secondById);
            UUID earlierPageLaterRank = insertVisual(jdbc, material.activeVersion(), null, 1,
                    "OTHER", "Later rank", null, "SUPPORTED");
            link(jdbc, firstChunk, secondById, material.activeVersion(), "NEARBY");
            link(jdbc, firstChunk, firstById, material.activeVersion(), "NEARBY");
            link(jdbc, secondChunk, earlierPageLaterRank, material.activeVersion(), "NEARBY");
            RetrievalScope scope = scope(user, new RetrievalScopeTarget(material.activeVersion(), Set.of()));

            EvidencePackage evidence = build(context, scope,
                    List.of(candidate(firstChunk, material, null, 1), candidate(secondChunk, material, null, 2)), 2);

            assertThat(evidence.visuals()).extracting(visual -> visual.visualId())
                    .containsExactly(firstById, secondById);
            assertThat(codes(evidence)).contains(EvidenceLimitationCode.VISUAL_BUDGET_APPLIED);
            assertThat(context.getBean(EvidenceVisualRepository.class).findLinkedVisuals(scope, Set.of())).isEmpty();
        }
    }

    private static ConfigurableApplicationContext startApplication() {
        return startApplicationWithFlywayAndArguments(new Class<?>[0],
                "--hippocampus.materials.processing.recovery.enabled=false");
    }

    private static EvidencePackage build(
            ConfigurableApplicationContext context, RetrievalScope scope,
            List<HybridCandidate> candidates, int maxVisuals) {
        return context.getBean(BuildEvidencePackage.class).execute(new BuildEvidencePackage.Command(
                scope, candidates, RetrievalQuality.STRONG, new EvidencePackageBudget(candidates.size(), maxVisuals)));
    }

    private static RetrievalScope scope(UUID user, RetrievalScopeTarget... targets) {
        return new RetrievalScope(user, UUID.randomUUID(), GroundingMode.STRICT_SOURCE, List.of(targets));
    }

    private static HybridCandidate candidate(
            UUID chunkId, Material material, UUID node, int chunkIndex) {
        return new HybridCandidate(
                chunkId, material.id(), material.activeVersion(), node, chunkIndex, "canonical", 1, 1,
                List.of(), "TEXT", "NATIVE", "STRONG",
                Optional.of(new HybridLexicalSignal(1, true, 1, 1)), Optional.empty(), 0.1);
    }

    private static List<EvidenceLimitationCode> codes(EvidencePackage evidence) {
        return evidence.limitations().stream().map(limitation -> limitation.code()).toList();
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
                VALUES (?,?,'SECTION',?,?,1,10,'NATIVE',CURRENT_TIMESTAMP)
                """).params(id, version, title, ordinal).update();
        return id;
    }

    private static UUID insertChunk(JdbcClient jdbc, UUID version, UUID node, int index, boolean active) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO chunks(
                    id,material_version_id,document_node_id,chunk_index,content,page_start,page_end,
                    content_type,extraction_method,quality,is_active,created_at)
                VALUES (?,?,?,?,?,1,1,'TEXT','NATIVE','STRONG',?,CURRENT_TIMESTAMP)
                """).params(id, version, node, index, "chunk-" + id, active).update();
        return id;
    }

    private static UUID insertVisual(
            JdbcClient jdbc, UUID version, UUID node, int page, String type,
            String caption, String nearby, String status) {
        return insertVisual(jdbc, version, node, page, type, caption, nearby, status, UUID.randomUUID());
    }

    private static UUID insertVisual(
            JdbcClient jdbc, UUID version, UUID node, int page, String type,
            String caption, String nearby, String status, UUID id) {
        jdbc.sql("""
                INSERT INTO visual_assets(
                    id,material_version_id,document_node_id,page_number,storage_key,visual_type,
                    caption,nearby_text,interpretation_status,content_hash,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)
                """).params(id, version, node, page, "private/" + id, type, caption, nearby, status, id.toString()).update();
        return id;
    }

    private static void link(
            JdbcClient jdbc, UUID chunk, UUID visual, UUID version, String relationship) {
        jdbc.sql("""
                INSERT INTO chunk_visual_links(chunk_id,visual_asset_id,material_version_id,relationship_type)
                VALUES (?,?,?,?)
                """).params(chunk, visual, version, relationship).update();
    }

    private record Material(UUID id, UUID activeVersion, UUID historicalVersion) {}

    private record ChunkMaterial(UUID chunkId, Material material) {}
}
