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
    void rejectsMissingWrongNodeAndNonPrimaryPageVisuals() {
        assertThatThrownBy(() -> persistence.execute(version,
                List.of(withVisuals(draft(), List.of(UUID.randomUUID()))))).isInstanceOf(IllegalStateException.class);
        UUID otherNode = UUID.randomUUID();
        jdbc.sql("INSERT INTO document_nodes(id,material_version_id,parent_id,node_type,ordinal,start_page,end_page,detection_origin,created_at) VALUES (?,?,?,'SECTION',2,1,1,'NATIVE',CURRENT_TIMESTAMP)")
                .params(otherNode, version, node).update();
        UUID wrongNode = insertVisual(1, otherNode, version);
        assertThatThrownBy(() -> persistence.execute(version,
                List.of(withVisuals(draft(), List.of(wrongNode))))).isInstanceOf(IllegalStateException.class);
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
}
