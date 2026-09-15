package com.hippocampus.materials.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.hippocampus.identity.infrastructure.security.HippocampusPrincipal;
import com.hippocampus.materials.application.CreateSourceReferences;
import com.hippocampus.materials.application.ResolveSourceReference;
import com.hippocampus.materials.domain.ChunkSourceTarget;
import com.hippocampus.materials.domain.DocumentNodeSourceTarget;
import com.hippocampus.materials.domain.PageSourceTarget;
import com.hippocampus.materials.domain.SourceReference;
import com.hippocampus.materials.domain.VisualSourceTarget;
import com.hippocampus.rag.application.MaterializeEvidenceSourceReferences;
import com.hippocampus.rag.domain.EvidenceChunk;
import com.hippocampus.rag.domain.EvidencePackage;
import com.hippocampus.rag.domain.EvidenceReferenceKind;
import com.hippocampus.rag.domain.EvidenceSourceReference;
import com.hippocampus.rag.domain.EvidenceVisual;
import com.hippocampus.rag.domain.GroundingMode;
import com.hippocampus.rag.domain.RetrievalDiagnostics;
import com.hippocampus.rag.domain.RetrievalQuality;
import com.hippocampus.shared.application.error.ApplicationNotFoundException;
import com.hippocampus.testing.PostgresIntegrationTestSupport;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SourceReferenceIntegrationTests extends PostgresIntegrationTestSupport {
    private ConfigurableApplicationContext context;
    private JdbcClient jdbc;
    private CreateSourceReferences create;
    private ResolveSourceReference resolve;

    @BeforeAll
    void startApplication() throws SQLException {
        resetPostgresSchema();
        context = startApplicationWithFlywayAndArguments(new Class<?>[0],
                "--hippocampus.materials.processing.recovery.enabled=false");
        jdbc = context.getBean(JdbcClient.class);
        create = context.getBean(CreateSourceReferences.class);
        resolve = context.getBean(ResolveSourceReference.class);
    }

    @BeforeEach
    void cleanDatabase() {
        jdbc.sql("TRUNCATE TABLE users CASCADE").update();
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @AfterAll
    void closeApplication() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void createsAndResolvesChunkVisualNodeAndPageWithCanonicalLabels() {
        UUID user = insertUser("owner");
        Material material = insertMaterial(user, "Upper Limb Lecture", "ACTIVE", 1, 20);
        UUID node = insertNode(material.activeVersion(), " Posterior Cord ");
        UUID chunk = insertChunk(material.activeVersion(), node, 14, 14, true);
        UUID visual = insertVisual(material.activeVersion(), node, 15);
        authenticate(user);

        List<SourceReference> references = create.execute(new CreateSourceReferences.Command(List.of(
                new ChunkSourceTarget(material.id(), material.activeVersion(), chunk),
                new VisualSourceTarget(material.id(), material.activeVersion(), visual),
                new DocumentNodeSourceTarget(material.id(), material.activeVersion(), node),
                new PageSourceTarget(material.id(), material.activeVersion(), 16))));

        assertThat(references).extracting(SourceReference::displayLabel).containsExactly(
                "Upper Limb Lecture · Page 14 · Posterior Cord",
                "Upper Limb Lecture · Page 15 · Posterior Cord",
                "Upper Limb Lecture · Posterior Cord",
                "Upper Limb Lecture · Page 16");
        assertThat(references.stream().map(reference -> resolve.execute(
                new ResolveSourceReference.Query(reference.sourceReferenceId()))).toList())
                .isEqualTo(references);
    }

    @Test
    void repeatedCanonicalTargetReturnsSameIdAndRefreshesAuthoritativeLabel() {
        UUID user = insertUser("stable");
        Material material = insertMaterial(user, "First Title", "ACTIVE", 1, 2);
        authenticate(user);
        PageSourceTarget target = new PageSourceTarget(material.id(), material.activeVersion(), 2);

        SourceReference first = create.execute(new CreateSourceReferences.Command(List.of(target))).getFirst();
        jdbc.sql("UPDATE materials SET title='Updated Title' WHERE id=?").param(material.id()).update();
        SourceReference second = create.execute(new CreateSourceReferences.Command(List.of(target))).getFirst();

        assertThat(second.sourceReferenceId()).isEqualTo(first.sourceReferenceId());
        assertThat(second.createdAt()).isEqualTo(first.createdAt());
        assertThat(second.displayLabel()).isEqualTo("Updated Title · Page 2");
        assertThat(jdbc.sql("SELECT count(*) FROM source_references").query(Long.class).single()).isOne();
    }

    @Test
    void crossUserCreationFailsAndForeignAndRandomIdsHaveIdenticalErrors() {
        UUID owner = insertUser("owner");
        UUID foreign = insertUser("foreign");
        Material material = insertMaterial(owner, "Private", "ACTIVE", 1, 1);
        authenticate(foreign);
        assertNotFound(() -> create.execute(new CreateSourceReferences.Command(List.of(
                new PageSourceTarget(material.id(), material.activeVersion(), 1)))));

        authenticate(owner);
        SourceReference reference = create.execute(new CreateSourceReferences.Command(List.of(
                new PageSourceTarget(material.id(), material.activeVersion(), 1)))).getFirst();
        authenticate(foreign);
        ApplicationNotFoundException foreignError = notFound(() -> resolve.execute(
                new ResolveSourceReference.Query(reference.sourceReferenceId())));
        ApplicationNotFoundException missingError = notFound(() -> resolve.execute(
                new ResolveSourceReference.Query(UUID.randomUUID())));

        assertThat(foreignError.errorCode()).isEqualTo(missingError.errorCode());
        assertThat(foreignError.clientMessage()).isEqualTo(missingError.clientMessage());
    }

    @Test
    void deletedMaterialCannotCreateAndInvalidatesExistingReference() {
        UUID user = insertUser("deleted");
        Material material = insertMaterial(user, "Delete Me", "ACTIVE", 1, 1);
        authenticate(user);
        PageSourceTarget target = new PageSourceTarget(material.id(), material.activeVersion(), 1);
        SourceReference reference = create.execute(new CreateSourceReferences.Command(List.of(target))).getFirst();
        jdbc.sql("UPDATE materials SET status='DELETED' WHERE id=?").param(material.id()).update();

        assertNotFound(() -> create.execute(new CreateSourceReferences.Command(List.of(target))));
        assertNotFound(() -> resolve.execute(new ResolveSourceReference.Query(reference.sourceReferenceId())));
    }

    @Test
    void historicalVersionCannotCreateAndActivationOfNewVersionInvalidatesOldReference() {
        UUID user = insertUser("versions");
        Material material = insertMaterial(user, "Versions", "ACTIVE", 1, 1);
        authenticate(user);
        PageSourceTarget oldTarget = new PageSourceTarget(material.id(), material.activeVersion(), 1);
        SourceReference oldReference = create.execute(new CreateSourceReferences.Command(List.of(oldTarget))).getFirst();
        UUID newer = insertVersion(material.id(), 2, 1);
        jdbc.sql("UPDATE materials SET active_version_id=? WHERE id=?").params(newer, material.id()).update();

        assertNotFound(() -> create.execute(new CreateSourceReferences.Command(List.of(oldTarget))));
        assertNotFound(() -> resolve.execute(new ResolveSourceReference.Query(oldReference.sourceReferenceId())));
    }

    @Test
    void inactiveChunkCannotCreateAndLaterInactivationInvalidatesResolution() {
        UUID user = insertUser("inactive");
        Material material = insertMaterial(user, "Chunks", "ACTIVE", 1, 3);
        UUID node = insertNode(material.activeVersion(), "Node");
        UUID activeChunk = insertChunk(material.activeVersion(), node, 1, 1, true);
        UUID inactiveChunk = insertChunk(material.activeVersion(), node, 2, 2, false);
        authenticate(user);

        assertNotFound(() -> create.execute(new CreateSourceReferences.Command(List.of(
                new ChunkSourceTarget(material.id(), material.activeVersion(), inactiveChunk)))));
        SourceReference reference = create.execute(new CreateSourceReferences.Command(List.of(
                new ChunkSourceTarget(material.id(), material.activeVersion(), activeChunk)))).getFirst();
        jdbc.sql("UPDATE chunks SET is_active=false WHERE id=?").param(activeChunk).update();
        assertNotFound(() -> resolve.execute(new ResolveSourceReference.Query(reference.sourceReferenceId())));
    }

    @Test
    void mismatchedTargetsAndOutOfRangePagesFailClosed() {
        UUID user = insertUser("mismatch");
        Material first = insertMaterial(user, "First", "ACTIVE", 1, 2);
        Material second = insertMaterial(user, "Second", "ACTIVE", 1, 2);
        UUID secondNode = insertNode(second.activeVersion(), "Second node");
        UUID secondChunk = insertChunk(second.activeVersion(), secondNode, 1, 1, true);
        UUID secondVisual = insertVisual(second.activeVersion(), secondNode, 1);
        authenticate(user);

        assertNotFound(() -> create.execute(new CreateSourceReferences.Command(List.of(
                new ChunkSourceTarget(first.id(), first.activeVersion(), secondChunk)))));
        assertNotFound(() -> create.execute(new CreateSourceReferences.Command(List.of(
                new VisualSourceTarget(first.id(), first.activeVersion(), secondVisual)))));
        assertNotFound(() -> create.execute(new CreateSourceReferences.Command(List.of(
                new DocumentNodeSourceTarget(first.id(), first.activeVersion(), secondNode)))));
        assertNotFound(() -> create.execute(new CreateSourceReferences.Command(List.of(
                new PageSourceTarget(first.id(), first.activeVersion(), 3)))));
    }

    @Test
    void multiPageChunkDoesNotFabricatePageAndInvalidBatchRollsBack() {
        UUID user = insertUser("batch");
        Material material = insertMaterial(user, "Batch", "ACTIVE", 1, 5);
        UUID node = insertNode(material.activeVersion(), "Range");
        UUID chunk = insertChunk(material.activeVersion(), node, 2, 3, true);
        authenticate(user);

        SourceReference multiPage = create.execute(new CreateSourceReferences.Command(List.of(
                new ChunkSourceTarget(material.id(), material.activeVersion(), chunk)))).getFirst();
        assertThat(multiPage.pageNumber()).isNull();
        jdbc.sql("DELETE FROM source_references").update();

        assertNotFound(() -> create.execute(new CreateSourceReferences.Command(List.of(
                new PageSourceTarget(material.id(), material.activeVersion(), 1),
                new PageSourceTarget(material.id(), material.activeVersion(), 99)))));
        assertThat(jdbc.sql("SELECT count(*) FROM source_references").query(Long.class).single()).isZero();
    }

    @Test
    void evidencePackageChunkAndVisualReferencesMaterializeInOrderWithStableIds() {
        UUID user = insertUser("bridge");
        Material material = insertMaterial(user, "Bridge", "ACTIVE", 1, 4);
        UUID node = insertNode(material.activeVersion(), "Evidence");
        UUID chunkId = insertChunk(material.activeVersion(), node, 2, 2, true);
        UUID visualId = insertVisual(material.activeVersion(), node, 3);
        authenticate(user);
        EvidencePackage evidence = evidencePackage(material, node, chunkId, visualId);
        MaterializeEvidenceSourceReferences bridge = context.getBean(MaterializeEvidenceSourceReferences.class);

        List<SourceReference> first = bridge.execute(evidence);
        List<SourceReference> second = bridge.execute(evidence);

        assertThat(first).extracting(SourceReference::chunkId).containsExactly(chunkId, null);
        assertThat(first).extracting(SourceReference::visualAssetId).containsExactly(null, visualId);
        assertThat(second).extracting(SourceReference::sourceReferenceId)
                .containsExactlyElementsOf(first.stream().map(SourceReference::sourceReferenceId).toList());
        assertThat(evidence.sourceReferences()).hasSize(2);
    }

    @Test
    void migrationCreatesColumnsForeignKeysChecksAndCanonicalNullAwareUniqueness() {
        Set<String> columns = Set.copyOf(jdbc.sql("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema='public' AND table_name='source_references'
                """).query(String.class).list());
        assertThat(columns).containsExactlyInAnyOrder(
                "id", "material_id", "material_version_id", "document_node_id", "chunk_id",
                "visual_asset_id", "page_number", "timestamp_start_ms", "timestamp_end_ms",
                "display_label", "created_at");
        Set<String> foreignKeys = Set.copyOf(jdbc.sql("""
                SELECT constraint_name FROM information_schema.table_constraints
                WHERE table_schema='public' AND table_name='source_references'
                  AND constraint_type='FOREIGN KEY'
                """).query(String.class).list());
        assertThat(foreignKeys).contains(
                "fk_source_references_material_version", "fk_source_references_node_same_version",
                "fk_source_references_chunk_same_version", "fk_source_references_visual_same_version");

        UUID user = insertUser("schema");
        Material first = insertMaterial(user, "First", "ACTIVE", 1, 2);
        Material second = insertMaterial(user, "Second", "ACTIVE", 1, 2);
        UUID node = insertNode(second.activeVersion(), "Node");
        UUID chunk = insertChunk(second.activeVersion(), node, 1, 1, true);
        UUID visual = insertVisual(second.activeVersion(), node, 1);
        assertInvalidReference(first, node, null, null, 1, null, null);
        assertInvalidReference(first, null, chunk, null, 1, null, null);
        assertInvalidReference(first, null, null, visual, 1, null, null);
        assertInvalidReference(first, null, null, null, null, null, null);
        assertInvalidReference(first, null, null, null, 0, null, null);
        assertInvalidReference(first, null, null, null, 1, -1L, null);
        assertInvalidReference(first, null, null, null, 1, 0L, -1L);
        assertInvalidReference(first, null, null, null, 1, null, 2L);
        assertInvalidReference(first, null, null, null, 1, 5L, 4L);
        assertInvalidReference(second, node, chunk, visual, 1, null, null);

        insertRawReference(first, null, null, null, 1, null, null);
        assertThatThrownBy(() -> insertRawReference(first, null, null, null, 1, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private EvidencePackage evidencePackage(Material material, UUID node, UUID chunkId, UUID visualId) {
        EvidenceChunk chunk = new EvidenceChunk(
                1, chunkId, material.id(), material.activeVersion(), node, 1, "canonical", 2, 2,
                List.of("Evidence"), "TEXT", "NATIVE", "STRONG");
        EvidenceVisual visual = new EvidenceVisual(
                visualId, material.id(), material.activeVersion(), node, 3, "OTHER", "Caption", null,
                "SUPPORTED", Set.of(chunkId), Set.of("NEARBY"));
        List<EvidenceSourceReference> references = List.of(
                new EvidenceSourceReference(EvidenceReferenceKind.CHUNK, material.id(),
                        material.activeVersion(), node, chunkId, null, 2),
                new EvidenceSourceReference(EvidenceReferenceKind.VISUAL, material.id(),
                        material.activeVersion(), node, null, visualId, 3));
        RetrievalDiagnostics diagnostics = new RetrievalDiagnostics(
                1, 1, List.of(chunkId), List.of(visualId), Set.of(material.id()), Set.of(), RetrievalQuality.STRONG);
        return new EvidencePackage(RetrievalQuality.STRONG, GroundingMode.STRICT_SOURCE,
                List.of(chunk), List.of(visual), references, List.of(), diagnostics);
    }

    private void assertInvalidReference(
            Material material, UUID node, UUID chunk, UUID visual, Integer page, Long start, Long end) {
        assertThatThrownBy(() -> insertRawReference(material, node, chunk, visual, page, start, end))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertRawReference(
            Material material, UUID node, UUID chunk, UUID visual, Integer page, Long start, Long end) {
        jdbc.sql("""
                INSERT INTO source_references(
                    id,material_id,material_version_id,document_node_id,chunk_id,visual_asset_id,
                    page_number,timestamp_start_ms,timestamp_end_ms,display_label,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,'Label',CURRENT_TIMESTAMP)
                """).params(UUID.randomUUID(), material.id(), material.activeVersion(), node, chunk, visual,
                        page, start, end).update();
    }

    private ApplicationNotFoundException notFound(Runnable action) {
        try {
            action.run();
        } catch (ApplicationNotFoundException exception) {
            assertThat(exception.errorCode().value()).isEqualTo("SOURCE_REFERENCE_NOT_FOUND");
            return exception;
        }
        throw new AssertionError("Expected ApplicationNotFoundException");
    }

    private void assertNotFound(Runnable action) {
        notFound(action);
    }

    private void authenticate(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new HippocampusPrincipal(userId, userId + "@example.test"), null, List.of()));
    }

    private UUID insertUser(String name) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO users(id,email,status,created_at,updated_at) VALUES (?,?,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")
                .params(id, name + "-" + id + "@example.test").update();
        return id;
    }

    private Material insertMaterial(UUID user, String title, String status, int versions, Integer pageCount) {
        UUID material = UUID.randomUUID();
        jdbc.sql("INSERT INTO materials(id,user_id,title,material_type,status,created_at,updated_at) VALUES (?,?,?,'PDF',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")
                .params(material, user, title, status).update();
        UUID historical = null;
        UUID active = null;
        for (int number = 1; number <= versions; number++) {
            UUID version = insertVersion(material, number, pageCount);
            if (number == 1 && versions > 1) {
                historical = version;
            }
            active = version;
        }
        jdbc.sql("UPDATE materials SET active_version_id=? WHERE id=?").params(active, material).update();
        return new Material(material, active, historical);
    }

    private UUID insertVersion(UUID material, int number, Integer pageCount) {
        UUID version = UUID.randomUUID();
        jdbc.sql("INSERT INTO material_versions(id,material_id,version_number,page_count,processing_status,created_at) VALUES (?,?,?,?, 'READY',CURRENT_TIMESTAMP)")
                .params(version, material, number, pageCount).update();
        return version;
    }

    private UUID insertNode(UUID version, String title) {
        UUID id = UUID.randomUUID();
        int ordinal = jdbc.sql("SELECT count(*) + 1 FROM document_nodes WHERE material_version_id=?")
                .param(version).query(Integer.class).single();
        jdbc.sql("INSERT INTO document_nodes(id,material_version_id,node_type,title,ordinal,start_page,end_page,detection_origin,created_at) VALUES (?,?,'SECTION',?,?,1,20,'NATIVE',CURRENT_TIMESTAMP)")
                .params(id, version, title, ordinal).update();
        return id;
    }

    private UUID insertChunk(UUID version, UUID node, int pageStart, int pageEnd, boolean active) {
        UUID id = UUID.randomUUID();
        int index = jdbc.sql("SELECT count(*) + 1 FROM chunks WHERE material_version_id=?")
                .param(version).query(Integer.class).single();
        jdbc.sql("INSERT INTO chunks(id,material_version_id,document_node_id,chunk_index,content,page_start,page_end,content_type,extraction_method,quality,is_active,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)")
                .params(id, version, node, index, "chunk-" + id, pageStart, pageEnd,
                        "TEXT", "NATIVE", "STRONG", active).update();
        return id;
    }

    private UUID insertVisual(UUID version, UUID node, int page) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO visual_assets(id,material_version_id,document_node_id,page_number,storage_key,visual_type,interpretation_status,content_hash,created_at) VALUES (?,?,?,?,?,'OTHER','SUPPORTED',?,CURRENT_TIMESTAMP)")
                .params(id, version, node, page, "private/" + id, id.toString()).update();
        return id;
    }

    private record Material(UUID id, UUID activeVersion, UUID historicalVersion) {}
}
