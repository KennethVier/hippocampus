package com.hippocampus.materials.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hippocampus.materials.domain.ChunkDraft;

class ChunkVisualLinkPersistenceIntegrationTests extends ChunkPersistenceTestFixture {
    @Test
    void validVisualsPersistAndExactRetryIsNoOp() {
        UUID first = insertVisual(1, node, version);
        UUID second = insertVisual(1, node, version);
        ChunkDraft expected = withVisuals(draft(), List.of(first, second));
        persistence.execute(version, List.of(expected));
        jdbc.sql("DELETE FROM chunk_visual_links WHERE visual_asset_id=?").param(second).update();
        persistence.execute(version, List.of(expected));
        assertThat(jdbc.sql("SELECT count(*) FROM chunk_visual_links WHERE relationship_type='NEARBY'")
                .query(Integer.class).single()).isEqualTo(2);
    }

    @Test
    void oneVisualMayLinkToMultipleIndependentlyValidChunks() {
        UUID visual = insertVisual(1, node, version);
        ChunkDraft first = withVisuals(draft(), List.of(visual));
        ChunkDraft second = withVisuals(draft(2, "second", List.of(
                new ChunkDraft.SourceLink(snapshot(), 1, false)), List.of(), Set.of(1)), List.of(visual));
        persistence.execute(version, List.of(first, second));
        assertThat(jdbc.sql("SELECT count(*) FROM chunk_visual_links WHERE visual_asset_id=?")
                .param(visual).query(Integer.class).single()).isEqualTo(2);
    }

    @Test
    void acceptsVisualInAContainedDescendantNode() {
        UUID section = UUID.randomUUID();
        jdbc.sql("INSERT INTO document_nodes(id,material_version_id,parent_id,node_type,ordinal,start_page,end_page,detection_origin,created_at) VALUES (?,?,?,'SECTION',2,1,1,'NATIVE',CURRENT_TIMESTAMP)")
                .params(section, version, node).update();
        UUID visual = insertVisual(1, section, version);

        persistence.execute(version, List.of(withVisuals(draft(), List.of(visual))));
        finalizeOne();

        assertThat(jdbc.sql("SELECT count(*) FROM chunk_visual_links WHERE visual_asset_id=?")
                .param(visual).query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void finalizationRejectsVisualOutsideChunkHierarchy() {
        UUID chunkSection = UUID.randomUUID();
        UUID visualSection = UUID.randomUUID();
        jdbc.sql("INSERT INTO document_nodes(id,material_version_id,parent_id,node_type,ordinal,start_page,end_page,detection_origin,created_at) VALUES (?,?,?,'SECTION',2,1,1,'NATIVE',CURRENT_TIMESTAMP)")
                .params(chunkSection, version, node).update();
        jdbc.sql("INSERT INTO document_nodes(id,material_version_id,parent_id,node_type,ordinal,start_page,end_page,detection_origin,created_at) VALUES (?,?,?,'SECTION',3,1,1,'NATIVE',CURRENT_TIMESTAMP)")
                .params(visualSection, version, node).update();
        UUID visual = insertVisual(1, visualSection, version);
        persistence.execute(version, List.of(withNode(draft(), chunkSection)));
        insertNearbyLink(visual);

        assertThatThrownBy(this::finalizeOne).isInstanceOf(IllegalStateException.class)
                .hasMessage("Durable visual links are invalid");
    }

    @Test
    void finalizationRejectsVisualWithoutPrimarySourcePage() {
        UUID visual = insertVisual(1, node, version);
        persistence.execute(version, List.of(withVisuals(draft(), List.of(visual))));
        jdbc.sql("UPDATE chunk_text_block_links SET is_overlap=true").update();

        assertThatThrownBy(this::finalizeOne).isInstanceOf(IllegalStateException.class)
                .hasMessage("Durable visual links are invalid");
    }

    @Test
    void finalizationRejectsUnexpectedVisualRelationship() {
        UUID visual = insertVisual(1, node, version);
        persistence.execute(version, List.of(withVisuals(draft(), List.of(visual))));
        jdbc.sql("UPDATE chunk_visual_links SET relationship_type='REFERENCES'").update();

        assertThatThrownBy(this::finalizeOne).isInstanceOf(IllegalStateException.class)
                .hasMessage("Durable visual links are invalid");
    }

    @Test
    void finalizationRejectsCrossVersionVisualProvenanceEvenIfDatabaseConstraintIsUnavailable() {
        UUID otherVersion = insertOtherVersion();
        UUID otherNode = UUID.randomUUID();
        jdbc.sql("INSERT INTO document_nodes(id,material_version_id,node_type,title,ordinal,start_page,end_page,detection_origin,created_at) VALUES (?,?,'DOCUMENT','Other',1,1,1,'NATIVE',CURRENT_TIMESTAMP)")
                .params(otherNode, otherVersion).update();
        UUID visual = insertVisual(1, otherNode, otherVersion);
        persistence.execute(version, List.of(draft()));
        jdbc.sql("ALTER TABLE chunk_visual_links DROP CONSTRAINT fk_chunk_visual_links_visual").update();
        insertNearbyLink(visual);

        assertThatThrownBy(this::finalizeOne).isInstanceOf(IllegalStateException.class)
                .hasMessage("Durable visual links are invalid");
    }

    @Test
    void rejectsMissingUnrelatedNodeAndNonPrimaryPageVisuals() {
        assertThatThrownBy(() -> persistence.execute(version,
                List.of(withVisuals(draft(), List.of(UUID.randomUUID()))))).isInstanceOf(IllegalStateException.class);
        UUID chunkNode = UUID.randomUUID();
        UUID otherNode = UUID.randomUUID();
        jdbc.sql("INSERT INTO document_nodes(id,material_version_id,parent_id,node_type,ordinal,start_page,end_page,detection_origin,created_at) VALUES (?,?,?,'SECTION',2,1,1,'NATIVE',CURRENT_TIMESTAMP)")
                .params(chunkNode, version, node).update();
        jdbc.sql("INSERT INTO document_nodes(id,material_version_id,parent_id,node_type,ordinal,start_page,end_page,detection_origin,created_at) VALUES (?,?,?,'SECTION',3,1,1,'NATIVE',CURRENT_TIMESTAMP)")
                .params(otherNode, version, node).update();
        UUID wrongNode = insertVisual(1, otherNode, version);
        assertThatThrownBy(() -> persistence.execute(version,
                List.of(withVisuals(withNode(draft(), chunkNode), List.of(wrongNode)))))
                .isInstanceOf(IllegalStateException.class);
        UUID nonPrimary = insertVisual(2, node, version);
        assertThatThrownBy(() -> persistence.execute(version,
                List.of(withVisuals(draft(), List.of(nonPrimary))))).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.sql("SELECT count(*) FROM chunks").query(Integer.class).single()).isZero();
    }

    @Test
    void unexpectedOrConflictingVisualLinkFailsClosed() {
        UUID visual = insertVisual(1, node, version);
        ChunkDraft expected = withVisuals(draft(), List.of(visual));
        persistence.execute(version, List.of(expected));
        jdbc.sql("UPDATE chunk_visual_links SET relationship_type='REFERENCES'").update();
        assertThatThrownBy(() -> persistence.execute(version, List.of(expected)))
                .isInstanceOf(IllegalStateException.class);
        jdbc.sql("DELETE FROM chunk_visual_links").update();
        persistence.execute(version, List.of(draft()));
        jdbc.sql("INSERT INTO chunk_visual_links(chunk_id,visual_asset_id,material_version_id,relationship_type) VALUES (?,?,?,'NEARBY')")
                .params(expected.id(), visual, version).update();
        assertThatThrownBy(() -> persistence.execute(version, List.of(draft())))
                .isInstanceOf(IllegalStateException.class);
    }

    private ChunkDraft withVisuals(ChunkDraft value, List<UUID> visuals) {
        return new ChunkDraft(value.id(), value.materialVersionId(), value.documentNodeId(), value.chunkIndex(),
                value.content(), value.tokenCount(), value.pageStart(), value.pageEnd(), value.primaryPages(),
                value.headingPath(), value.contentType(), value.extractionMethod(), value.quality(), value.sourceOrder(),
                value.sourceLinks(), visuals);
    }

    private ChunkDraft withNode(ChunkDraft value, UUID documentNodeId) {
        return new ChunkDraft(value.id(), value.materialVersionId(), documentNodeId, value.chunkIndex(),
                value.content(), value.tokenCount(), value.pageStart(), value.pageEnd(), value.primaryPages(),
                value.headingPath(), value.contentType(), value.extractionMethod(), value.quality(), value.sourceOrder(),
                value.sourceLinks(), value.visualAssetIds());
    }

    private void insertNearbyLink(UUID visual) {
        jdbc.sql("INSERT INTO chunk_visual_links(chunk_id,visual_asset_id,material_version_id,relationship_type) VALUES (?,?,?,'NEARBY')")
                .params(draft().id(), visual, version).update();
    }

    private UUID insertOtherVersion() {
        UUID otherVersion = UUID.randomUUID();
        UUID material = jdbc.sql("SELECT material_id FROM material_versions WHERE id=?")
                .param(version).query(UUID.class).single();
        jdbc.sql("INSERT INTO material_versions(id,material_id,version_number,storage_key,file_size_bytes,page_count,processing_status,created_at) VALUES (?,?,2,?,42,1,'PROCESSING',CURRENT_TIMESTAMP)")
                .params(otherVersion, material, "objects/" + otherVersion + ".pdf").update();
        return otherVersion;
    }
}
