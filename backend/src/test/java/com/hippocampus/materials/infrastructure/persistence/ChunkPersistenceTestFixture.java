package com.hippocampus.materials.infrastructure.persistence;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.hippocampus.materials.application.FinalizeChunking;
import com.hippocampus.materials.application.PersistChunkBatch;
import com.hippocampus.materials.domain.ChunkContentType;
import com.hippocampus.materials.domain.ChunkDraft;
import com.hippocampus.materials.domain.ChunkIdentity;
import com.hippocampus.materials.domain.ChunkingExecutionSummary;
import com.hippocampus.materials.domain.SourceTextBlockSnapshot;
import com.hippocampus.materials.domain.TextBlockExtractionMethod;
import com.hippocampus.materials.domain.TextBlockType;
import com.hippocampus.testing.PostgresIntegrationTestSupport;

abstract class ChunkPersistenceTestFixture extends PostgresIntegrationTestSupport {
    ConfigurableApplicationContext context;
    JdbcClient jdbc;
    PersistChunkBatch persistence;
    FinalizeChunking finalization;
    UUID version;
    UUID node;
    UUID block;

    @BeforeEach
    void prepareChunkDatabase() throws SQLException {
        resetPostgresSchema();
        context = startApplicationWithFlyway();
        jdbc = context.getBean(JdbcClient.class);
        persistence = context.getBean(PersistChunkBatch.class);
        finalization = context.getBean(FinalizeChunking.class);
        version = UUID.randomUUID();
        node = UUID.randomUUID();
        block = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        UUID material = UUID.randomUUID();
        jdbc.sql("INSERT INTO users(id,email,status,created_at,updated_at) VALUES (?,?,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")
                .params(user, user + "@example.test").update();
        jdbc.sql("INSERT INTO materials(id,user_id,title,material_type,mime_type,status,created_at,updated_at) VALUES (?,?,'PDF','PDF','application/pdf','PROCESSING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")
                .params(material, user).update();
        jdbc.sql("INSERT INTO material_versions(id,material_id,version_number,storage_key,file_size_bytes,page_count,processing_status,created_at) VALUES (?,?,1,'objects/test.pdf',42,1,'PROCESSING',CURRENT_TIMESTAMP)")
                .params(version, material).update();
        jdbc.sql("INSERT INTO document_nodes(id,material_version_id,node_type,title,ordinal,start_page,end_page,detection_origin,created_at) VALUES (?,?,'DOCUMENT','Document',1,1,1,'NATIVE',CURRENT_TIMESTAMP)")
                .params(node, version).update();
        jdbc.sql("INSERT INTO text_blocks(id,material_version_id,document_node_id,page_number,block_type,ordinal,content,normalized_content,extraction_method,quality,created_at) VALUES (?,?,?,1,'PAGE_TEXT',1,'cardiac output','cardiac output','NATIVE',NULL,CURRENT_TIMESTAMP)")
                .params(block, version, node).update();
    }

    @AfterEach
    void closeChunkDatabase() {
        if (context != null) context.close();
    }

    ChunkDraft draft() {
        return draft(1, "cardiac output", List.of(
                new ChunkDraft.SourceLink(snapshot(), 1, false)), List.of(), Set.of(1));
    }

    ChunkDraft draft(int index, String content, List<ChunkDraft.SourceLink> links,
            List<UUID> visuals, Set<Integer> primaryPages) {
        return new ChunkDraft(ChunkIdentity.forChunk(version, index), version, node, index, content,
                2, 1, 1, primaryPages, List.of("Document"), ChunkContentType.TEXT,
                TextBlockExtractionMethod.NATIVE, null, index, links, visuals);
    }

    SourceTextBlockSnapshot snapshot() {
        return new SourceTextBlockSnapshot(block, version, node, 1, TextBlockType.PAGE_TEXT, 1,
                "cardiac output", "cardiac output", TextBlockExtractionMethod.NATIVE, null);
    }

    UUID insertVisual(int page, UUID visualNode, UUID visualVersion) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO visual_assets(id,material_version_id,document_node_id,page_number,storage_key,visual_type,interpretation_status,content_hash,created_at) VALUES (?,?,?,?,?,'OTHER','UNASSESSED',?,CURRENT_TIMESTAMP)")
                .params(id, visualVersion, visualNode, page, "objects/" + id + ".png", id.toString().replace("-", "")).update();
        return id;
    }

    void finalizeOne() {
        finalization.execute(version, new ChunkingExecutionSummary(1, 1, 1));
    }

    String chunkSnapshot() {
        return jdbc.sql("SELECT row_to_json(c)::text FROM chunks c WHERE material_version_id=?")
                .param(version).query(String.class).optional().orElse("");
    }
}
