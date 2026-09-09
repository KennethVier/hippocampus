package com.hippocampus.materials.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.hippocampus.materials.domain.ChunkContentType;
import com.hippocampus.materials.domain.ChunkDraft;
import com.hippocampus.materials.domain.ChunkIdentity;
import com.hippocampus.materials.domain.ChunkingExecutionSummary;
import com.hippocampus.materials.domain.SourceTextBlockSnapshot;
import com.hippocampus.materials.domain.TextBlockExtractionMethod;
import com.hippocampus.materials.domain.TextBlockQuality;
import com.hippocampus.materials.domain.TextBlockType;

import tools.jackson.databind.ObjectMapper;

class ChunkPersistenceIntegrationTests extends ChunkPersistenceTestFixture {
    @Test
    void initialPersistenceAndExactReplayPreserveIdentityContentLinksAndCreatedAt() {
        persistence.execute(version, List.of(draft()));
        String first = chunkSnapshot();
        String created = jdbc.sql("SELECT created_at::text FROM chunks WHERE material_version_id=?")
                .param(version).query(String.class).single();
        persistence.execute(version, List.of(draft()));
        assertThat(chunkSnapshot()).isEqualTo(first);
        assertThat(jdbc.sql("SELECT created_at::text FROM chunks WHERE material_version_id=?")
                .param(version).query(String.class).single()).isEqualTo(created);
        assertThat(jdbc.sql("SELECT count(*) FROM chunks WHERE id=?")
                .param(ChunkIdentity.forChunk(version, 1)).query(Integer.class).single()).isOne();
        assertThat(jdbc.sql("SELECT count(*) FROM chunk_text_block_links WHERE chunk_id=?")
                .param(ChunkIdentity.forChunk(version, 1)).query(Integer.class).single()).isOne();
    }

    @Test
    void partialCompatibleStateConvergesWithoutReplacingChunk() {
        persistence.execute(version, List.of(draft()));
        String before = chunkSnapshot();
        jdbc.sql("DELETE FROM chunk_text_block_links").update();
        persistence.execute(version, List.of(draft()));
        assertThat(chunkSnapshot()).isEqualTo(before);
        assertThat(jdbc.sql("SELECT count(*) FROM chunk_text_block_links").query(Integer.class).single()).isOne();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UPDATE chunks SET content='changed'",
            "UPDATE chunks SET token_count=3",
            "UPDATE chunks SET page_start=2,page_end=2",
            "UPDATE chunks SET heading_path='[\"Changed\"]'::jsonb",
            "UPDATE chunks SET content_type='TABLE'",
            "UPDATE chunks SET extraction_method='OCR'",
            "UPDATE chunks SET quality='LIMITED'",
            "UPDATE chunks SET source_order=2",
            "UPDATE chunks SET is_active=false"
    })
    void chunkMetadataConflictFailsClosed(String mutation) {
        persistence.execute(version, List.of(draft()));
        jdbc.sql(mutation).update();
        String before = chunkSnapshot();
        assertThatThrownBy(() -> persistence.execute(version, List.of(draft())))
                .isInstanceOf(IllegalStateException.class);
        assertThat(chunkSnapshot()).isEqualTo(before);
    }

    @Test
    void wrongUuidForExistingChunkIndexFailsClosed() {
        ChunkDraft expected = draft();
        jdbc.sql("""
                INSERT INTO chunks(id,material_version_id,document_node_id,chunk_index,content,token_count,
                  page_start,page_end,heading_path,content_type,extraction_method,source_order,is_active,created_at)
                VALUES (?,?,?,1,'cardiac output',2,1,1,'["Document"]','TEXT','NATIVE',1,true,CURRENT_TIMESTAMP)
                """).params(UUID.randomUUID(), version, node).update();
        assertThatThrownBy(() -> persistence.execute(version, List.of(expected)))
                .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UPDATE text_blocks SET content='changed'",
            "UPDATE text_blocks SET normalized_content='changed'",
            "UPDATE text_blocks SET page_number=2",
            "UPDATE text_blocks SET block_type='PARAGRAPH'",
            "UPDATE text_blocks SET ordinal=2",
            "UPDATE text_blocks SET extraction_method='OCR',quality='LIMITED'",
            "UPDATE text_blocks SET quality='STRONG'"
    })
    void completeSourceSnapshotMismatchFailsWithoutWriting(String mutation) {
        ChunkDraft expected = draft();
        jdbc.sql(mutation).update();
        assertThatThrownBy(() -> persistence.execute(version, List.of(expected)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.sql("SELECT count(*) FROM chunks").query(Integer.class).single()).isZero();
    }

    @Test
    void sourceNodeMismatchAndPageOutsideNodeFailWithoutWriting() {
        ChunkDraft expected = draft();
        UUID otherNode = UUID.randomUUID();
        jdbc.sql("INSERT INTO document_nodes(id,material_version_id,parent_id,node_type,ordinal,start_page,end_page,detection_origin,created_at) VALUES (?,?,?,'SECTION',2,1,1,'NATIVE',CURRENT_TIMESTAMP)")
                .params(otherNode, version, node).update();
        jdbc.sql("UPDATE text_blocks SET document_node_id=?").param(otherNode).update();
        assertThatThrownBy(() -> persistence.execute(version, List.of(expected))).isInstanceOf(IllegalStateException.class);
        jdbc.sql("UPDATE text_blocks SET document_node_id=?").param(node).update();
        jdbc.sql("UPDATE document_nodes SET end_page=2,start_page=2 WHERE id=?").param(node).update();
        assertThatThrownBy(() -> persistence.execute(version, List.of(expected))).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.sql("SELECT count(*) FROM chunks").query(Integer.class).single()).isZero();
    }

    @Test
    void repeatedTextBlockOccurrencesAndMultipleChunksAreAllowed() {
        SourceTextBlockSnapshot source = snapshot();
        ChunkDraft first = draft(1, "cardiac output\n\ncardiac output", List.of(
                new ChunkDraft.SourceLink(source, 1, false), new ChunkDraft.SourceLink(source, 2, false)),
                List.of(), Set.of(1));
        ChunkDraft second = draft(2, "cardiac output", List.of(
                new ChunkDraft.SourceLink(source, 1, false)), List.of(), Set.of(1));
        persistence.execute(version, List.of(first, second));
        assertThat(jdbc.sql("SELECT count(*) FROM chunk_text_block_links WHERE text_block_id=?")
                .param(block).query(Integer.class).single()).isEqualTo(3);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UPDATE chunk_text_block_links SET text_block_id=gen_random_uuid()",
            "UPDATE chunk_text_block_links SET is_overlap=true",
            "UPDATE chunk_text_block_links SET source_position=2"
    })
    void sourceLinkConflictFailsClosed(String mutation) {
        persistence.execute(version, List.of(draft()));
        if (mutation.contains("gen_random_uuid")) {
            UUID replacement = UUID.randomUUID();
            jdbc.sql("""
                    INSERT INTO text_blocks (
                        id, material_version_id, document_node_id, page_number, block_type, ordinal,
                        content, extraction_method, quality, created_at, normalized_content)
                    SELECT ?, material_version_id, document_node_id, page_number, block_type, 2,
                        content, extraction_method, quality, created_at, normalized_content
                    FROM text_blocks WHERE id=?
                    """)
                    .params(replacement, block).update();
            jdbc.sql("UPDATE chunk_text_block_links SET text_block_id=?").param(replacement).update();
        } else {
            jdbc.sql(mutation).update();
        }
        assertThatThrownBy(() -> persistence.execute(version, List.of(draft())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void conflictInBatchRollsBackEveryEarlierWrite() {
        ChunkDraft second = draft(2, "second", List.of(new ChunkDraft.SourceLink(snapshot(), 1, false)), List.of(), Set.of(1));
        persistence.execute(version, List.of(second));
        jdbc.sql("UPDATE chunks SET content='conflict' WHERE chunk_index=2").update();
        assertThatThrownBy(() -> persistence.execute(version, List.of(draft(), second)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.sql("SELECT count(*) FROM chunks WHERE chunk_index=1").query(Integer.class).single()).isZero();
    }

    @Test
    void finalizationAcceptsValidAndBlankZeroChunkStateAndRejectsMalformedState() {
        persistence.execute(version, List.of(draft()));
        finalizeOne();
        jdbc.sql("DELETE FROM chunks").update();
        jdbc.sql("UPDATE text_blocks SET content='',normalized_content=''").update();
        finalization.execute(version, new ChunkingExecutionSummary(1, 0, 0));
        jdbc.sql("UPDATE text_blocks SET content='x',normalized_content='x'").update();
        assertThatThrownBy(() -> finalization.execute(version, new ChunkingExecutionSummary(1, 0, 0)))
                .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "DELETE FROM text_blocks",
            "UPDATE text_blocks SET normalized_content=NULL",
            "UPDATE text_blocks SET ordinal=2",
            "UPDATE text_blocks SET page_number=2"
    })
    void finalizationRejectsInvalidPageTextSource(String mutation) {
        persistence.execute(version, List.of(draft()));
        jdbc.sql(mutation).update();
        assertThatThrownBy(this::finalizeOne).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void injectedHardTokenLimitIsAuthoritative() {
        ObjectMapper mapper = context.getBean(ObjectMapper.class);
        TransactionTemplate tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        ChunkDraft at900 = withTokens(draft(), 900);
        tx.executeWithoutResult(status -> new JdbcChunkRepository(jdbc, mapper, 900).persistOrVerify(version, List.of(at900)));
        jdbc.sql("DELETE FROM chunks").update();
        assertThatThrownBy(() -> tx.executeWithoutResult(status ->
                new JdbcChunkRepository(jdbc, mapper, 900).persistOrVerify(version, List.of(withTokens(draft(), 901)))))
                .isInstanceOf(IllegalStateException.class);
        tx.executeWithoutResult(status -> new JdbcChunkRepository(jdbc, mapper, 1200)
                .persistOrVerify(version, List.of(withTokens(draft(), 1001))));
        jdbc.sql("DELETE FROM chunks").update();
        assertThatThrownBy(() -> tx.executeWithoutResult(status ->
                new JdbcChunkRepository(jdbc, mapper, 1200).persistOrVerify(version,
                        List.of(withTokens(draft(), 1201)))))
                .isInstanceOf(IllegalStateException.class);
    }


    @ParameterizedTest
    @ValueSource(strings = {
            "UPDATE chunks SET is_active=false",
            "UPDATE chunks SET token_count=1001",
            "UPDATE chunks SET source_order=NULL"
    })
    void finalizationRejectsInvalidDurableChunkState(String mutation) {
        persistence.execute(version, List.of(draft()));
        jdbc.sql(mutation).update();
        assertThatThrownBy(this::finalizeOne).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void finalizationRejectsMissingExtraGapAndWrongUuidChunks() {
        assertThatThrownBy(this::finalizeOne).isInstanceOf(IllegalStateException.class);
        persistence.execute(version, List.of(draft()));
        ChunkDraft second = draft(2, "second", List.of(new ChunkDraft.SourceLink(snapshot(), 1, false)),
                List.of(), Set.of(1));
        persistence.execute(version, List.of(second));
        assertThatThrownBy(this::finalizeOne).isInstanceOf(IllegalStateException.class);
        jdbc.sql("DELETE FROM chunks WHERE chunk_index=2").update();
        jdbc.sql("DELETE FROM chunk_text_block_links").update();
        jdbc.sql("UPDATE chunks SET id=?").param(UUID.randomUUID()).update();
        assertThatThrownBy(this::finalizeOne).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UPDATE text_blocks SET normalized_content='changed' WHERE block_type='TABLE_TEXT'",
            "UPDATE text_blocks SET extraction_method='OCR',quality='STRONG' WHERE block_type='TABLE_TEXT'"
    })
    void finalizationRejectsMalformedTableState(String mutation) {
        persistence.execute(version, List.of(draft()));
        jdbc.sql("INSERT INTO text_blocks(id,material_version_id,document_node_id,page_number,block_type,ordinal,content,normalized_content,extraction_method,quality,created_at) VALUES (?,?,?,1,'TABLE_TEXT',2,'A\tB','A\tB','NATIVE','STRONG',CURRENT_TIMESTAMP)")
                .params(UUID.randomUUID(), version, node).update();
        jdbc.sql(mutation).update();
        assertThatThrownBy(this::finalizeOne).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void schemaRejectsTableOrdinalInsidePageTextNamespace() {
        jdbc.sql("""
                INSERT INTO text_blocks(
                    id,material_version_id,document_node_id,page_number,block_type,ordinal,
                    content,normalized_content,extraction_method,quality,created_at)
                VALUES (?,?,?,1,'TABLE_TEXT',2,'A\tB','A\tB','NATIVE','STRONG',CURRENT_TIMESTAMP)
                """).params(UUID.randomUUID(), version, node).update();

        assertThatThrownBy(() -> jdbc.sql(
                "UPDATE text_blocks SET ordinal=1 WHERE block_type='TABLE_TEXT'").update())
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    @Test
    void unexpectedExistingSourceLinkFailsClosed() {
        persistence.execute(version, List.of(draft()));
        jdbc.sql("INSERT INTO chunk_text_block_links(chunk_id,text_block_id,material_version_id,source_position,is_overlap) VALUES (?,?,?,?,false)")
                .params(ChunkIdentity.forChunk(version, 1), block, version, 2).update();
        assertThatThrownBy(() -> persistence.execute(version, List.of(draft())))
                .isInstanceOf(IllegalStateException.class);
    }

    private ChunkDraft withTokens(ChunkDraft value, int tokens) {
        return new ChunkDraft(value.id(), value.materialVersionId(), value.documentNodeId(), value.chunkIndex(),
                value.content(), tokens, value.pageStart(), value.pageEnd(), value.primaryPages(), value.headingPath(),
                value.contentType(), value.extractionMethod(), value.quality(), value.sourceOrder(),
                value.sourceLinks(), value.visualAssetIds());
    }
}
