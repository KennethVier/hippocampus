package com.hippocampus.materials.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.hippocampus.materials.domain.ChunkSourceTarget;
import com.hippocampus.materials.domain.DocumentNodeSourceTarget;
import com.hippocampus.materials.domain.PageSourceTarget;
import com.hippocampus.materials.domain.SourceReference;
import com.hippocampus.materials.domain.SourceReferenceTarget;
import com.hippocampus.materials.domain.VisualSourceTarget;
import com.hippocampus.materials.port.SourceReferenceRepository;
import com.hippocampus.materials.port.SourceReferenceSeed;

public final class JdbcSourceReferenceRepository implements SourceReferenceRepository {
    private static final RowMapper<SourceReferenceSeed> SEED_MAPPER = (row, rowNumber) ->
            new SourceReferenceSeed(
                    row.getObject("material_id", UUID.class),
                    row.getObject("material_version_id", UUID.class),
                    row.getObject("document_node_id", UUID.class),
                    row.getObject("chunk_id", UUID.class),
                    row.getObject("visual_asset_id", UUID.class),
                    row.getObject("page_number", Integer.class),
                    row.getString("material_title"),
                    row.getString("document_node_title"));

    private static final RowMapper<SourceReference> REFERENCE_MAPPER = (row, rowNumber) ->
            new SourceReference(
                    row.getObject("id", UUID.class),
                    row.getObject("material_id", UUID.class),
                    row.getObject("material_version_id", UUID.class),
                    row.getObject("document_node_id", UUID.class),
                    row.getObject("chunk_id", UUID.class),
                    row.getObject("visual_asset_id", UUID.class),
                    row.getObject("page_number", Integer.class),
                    row.getObject("timestamp_start_ms", Long.class),
                    row.getObject("timestamp_end_ms", Long.class),
                    row.getString("display_label"),
                    row.getObject("created_at", OffsetDateTime.class).toInstant());

    private final JdbcClient jdbc;

    public JdbcSourceReferenceRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public Optional<SourceReferenceSeed> findAuthorizedTarget(UUID userId, SourceReferenceTarget target) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(target, "target must not be null");
        return switch (target) {
            case ChunkSourceTarget chunk -> findChunk(userId, chunk);
            case VisualSourceTarget visual -> findVisual(userId, visual);
            case DocumentNodeSourceTarget node -> findNode(userId, node);
            case PageSourceTarget page -> findPage(userId, page);
        };
    }

    private Optional<SourceReferenceSeed> findChunk(UUID userId, ChunkSourceTarget target) {
        return jdbc.sql("""
                SELECT m.id AS material_id, mv.id AS material_version_id,
                       c.document_node_id, c.id AS chunk_id, NULL::uuid AS visual_asset_id,
                       CASE WHEN c.page_start IS NOT NULL AND c.page_start = c.page_end
                            THEN c.page_start END AS page_number,
                       m.title AS material_title, dn.title AS document_node_title
                FROM materials m
                JOIN material_versions mv ON mv.material_id = m.id
                JOIN chunks c ON c.material_version_id = mv.id
                LEFT JOIN document_nodes dn
                  ON dn.id = c.document_node_id AND dn.material_version_id = mv.id
                WHERE m.id = :materialId AND mv.id = :versionId AND c.id = :targetId
                  AND m.user_id = :userId AND m.status <> 'DELETED'
                  AND m.active_version_id = mv.id AND c.is_active = true
                  AND btrim(m.title) <> ''
                FOR SHARE OF m, mv, c
                """)
                .param("materialId", target.materialId())
                .param("versionId", target.materialVersionId())
                .param("targetId", target.chunkId())
                .param("userId", userId)
                .query(SEED_MAPPER).optional();
    }

    private Optional<SourceReferenceSeed> findVisual(UUID userId, VisualSourceTarget target) {
        return jdbc.sql("""
                SELECT m.id AS material_id, mv.id AS material_version_id,
                       va.document_node_id, NULL::uuid AS chunk_id, va.id AS visual_asset_id,
                       va.page_number, m.title AS material_title, dn.title AS document_node_title
                FROM materials m
                JOIN material_versions mv ON mv.material_id = m.id
                JOIN visual_assets va ON va.material_version_id = mv.id
                LEFT JOIN document_nodes dn
                  ON dn.id = va.document_node_id AND dn.material_version_id = mv.id
                WHERE m.id = :materialId AND mv.id = :versionId AND va.id = :targetId
                  AND m.user_id = :userId AND m.status <> 'DELETED'
                  AND m.active_version_id = mv.id AND btrim(m.title) <> ''
                FOR SHARE OF m, mv, va
                """)
                .param("materialId", target.materialId())
                .param("versionId", target.materialVersionId())
                .param("targetId", target.visualAssetId())
                .param("userId", userId)
                .query(SEED_MAPPER).optional();
    }

    private Optional<SourceReferenceSeed> findNode(UUID userId, DocumentNodeSourceTarget target) {
        return jdbc.sql("""
                SELECT m.id AS material_id, mv.id AS material_version_id,
                       dn.id AS document_node_id, NULL::uuid AS chunk_id, NULL::uuid AS visual_asset_id,
                       NULL::int AS page_number, m.title AS material_title,
                       dn.title AS document_node_title
                FROM materials m
                JOIN material_versions mv ON mv.material_id = m.id
                JOIN document_nodes dn ON dn.material_version_id = mv.id
                WHERE m.id = :materialId AND mv.id = :versionId AND dn.id = :targetId
                  AND m.user_id = :userId AND m.status <> 'DELETED'
                  AND m.active_version_id = mv.id AND btrim(m.title) <> ''
                FOR SHARE OF m, mv, dn
                """)
                .param("materialId", target.materialId())
                .param("versionId", target.materialVersionId())
                .param("targetId", target.documentNodeId())
                .param("userId", userId)
                .query(SEED_MAPPER).optional();
    }

    private Optional<SourceReferenceSeed> findPage(UUID userId, PageSourceTarget target) {
        return jdbc.sql("""
                SELECT m.id AS material_id, mv.id AS material_version_id,
                       NULL::uuid AS document_node_id, NULL::uuid AS chunk_id,
                       NULL::uuid AS visual_asset_id, :pageNumber::int AS page_number,
                       m.title AS material_title, NULL::varchar AS document_node_title
                FROM materials m
                JOIN material_versions mv ON mv.material_id = m.id
                WHERE m.id = :materialId AND mv.id = :versionId
                  AND m.user_id = :userId AND m.status <> 'DELETED'
                  AND m.active_version_id = mv.id AND btrim(m.title) <> ''
                  AND (mv.page_count IS NULL OR :pageNumber <= mv.page_count)
                FOR SHARE OF m, mv
                """)
                .param("materialId", target.materialId())
                .param("versionId", target.materialVersionId())
                .param("pageNumber", target.pageNumber())
                .param("userId", userId)
                .query(SEED_MAPPER).optional();
    }

    @Override
    public SourceReference upsert(SourceReferenceSeed seed, String displayLabel) {
        Objects.requireNonNull(seed, "seed must not be null");
        Objects.requireNonNull(displayLabel, "displayLabel must not be null");
        return jdbc.sql("""
                INSERT INTO source_references(
                    id, material_id, material_version_id, document_node_id, chunk_id,
                    visual_asset_id, page_number, timestamp_start_ms, timestamp_end_ms,
                    display_label, created_at)
                VALUES (:id, :materialId, :versionId, :nodeId, :chunkId,
                        :visualId, :pageNumber, NULL, NULL, :displayLabel, CURRENT_TIMESTAMP)
                ON CONFLICT (material_id, material_version_id, document_node_id, chunk_id,
                             visual_asset_id, page_number, timestamp_start_ms, timestamp_end_ms)
                DO UPDATE SET display_label = EXCLUDED.display_label
                RETURNING id, material_id, material_version_id, document_node_id, chunk_id,
                          visual_asset_id, page_number, timestamp_start_ms, timestamp_end_ms,
                          display_label, created_at
                """)
                .param("id", UUID.randomUUID())
                .param("materialId", seed.materialId())
                .param("versionId", seed.materialVersionId())
                .param("nodeId", seed.documentNodeId())
                .param("chunkId", seed.chunkId())
                .param("visualId", seed.visualAssetId())
                .param("pageNumber", seed.pageNumber())
                .param("displayLabel", displayLabel)
                .query(REFERENCE_MAPPER).single();
    }

    @Override
    public Optional<SourceReference> resolveAuthorized(UUID userId, UUID sourceReferenceId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(sourceReferenceId, "sourceReferenceId must not be null");
        return jdbc.sql("""
                SELECT sr.id, sr.material_id, sr.material_version_id, sr.document_node_id,
                       sr.chunk_id, sr.visual_asset_id, sr.page_number,
                       sr.timestamp_start_ms, sr.timestamp_end_ms, sr.display_label, sr.created_at
                FROM source_references sr
                JOIN material_versions mv
                  ON mv.id = sr.material_version_id AND mv.material_id = sr.material_id
                JOIN materials m ON m.id = sr.material_id
                LEFT JOIN chunks c
                  ON c.id = sr.chunk_id AND c.material_version_id = sr.material_version_id
                LEFT JOIN document_nodes dn
                  ON dn.id = sr.document_node_id AND dn.material_version_id = sr.material_version_id
                LEFT JOIN visual_assets va
                  ON va.id = sr.visual_asset_id AND va.material_version_id = sr.material_version_id
                WHERE sr.id = :sourceReferenceId
                  AND m.user_id = :userId AND m.status <> 'DELETED'
                  AND m.active_version_id = sr.material_version_id
                  AND (sr.chunk_id IS NULL OR (c.id IS NOT NULL AND c.is_active = true))
                  AND (sr.document_node_id IS NULL OR dn.id IS NOT NULL)
                  AND (sr.visual_asset_id IS NULL OR va.id IS NOT NULL)
                  AND (sr.page_number IS NULL OR mv.page_count IS NULL OR sr.page_number <= mv.page_count)
                """)
                .param("sourceReferenceId", sourceReferenceId)
                .param("userId", userId)
                .query(REFERENCE_MAPPER).optional();
    }
}
