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

    @Override
    public Optional<SourceReferenceSeed> findOwnedFrozenChunk(
            UUID userId, ChunkSourceTarget target) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(target, "target must not be null");
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
                WHERE m.id = :materialId AND mv.id = :versionId AND c.id = :chunkId
                  AND m.user_id = :userId AND m.status <> 'DELETED'
                  AND c.is_active = true AND btrim(m.title) <> ''
                """)
                .param("materialId", target.materialId())
                .param("versionId", target.materialVersionId())
                .param("chunkId", target.chunkId())
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
    public java.util.List<SourceReference> createInitialMissionBatch(UUID owner,FrozenMissionBinding binding,
            java.util.List<SourceReferenceTarget> targets) {
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
                || org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !Integer.valueOf(java.sql.Connection.TRANSACTION_READ_COMMITTED).equals(
                    org.springframework.transaction.support.TransactionSynchronizationManager.getCurrentTransactionIsolationLevel()))
            throw new IllegalStateException("Initial provenance requires READ_COMMITTED write transaction");
        Objects.requireNonNull(owner);Objects.requireNonNull(binding);targets=java.util.List.copyOf(targets);
        jdbc.sql("SET LOCAL statement_timeout='5s'").update();
        jdbc.sql("SET LOCAL lock_timeout='1s'").update();
        jdbc.sql("SET LOCAL transaction_timeout='5s'").update();
        if(jdbc.sql("SELECT id FROM study_missions WHERE id=:mission AND user_id=:owner FOR UPDATE")
                .param("mission",binding.missionId()).param("owner",owner).query(UUID.class).optional().isEmpty())
            throw initialUnavailable();
        requireInitialClaim(owner,binding,true);
        var rows=new java.util.LinkedHashMap<String,java.util.Set<UUID>>();
        for(String table:java.util.List.of("material_versions","materials","source_references","document_nodes","chunks","visual_assets"))
            rows.put(table,new java.util.HashSet<>());
        jdbc.sql("SELECT material_id,material_version_id,document_node_id FROM mission_materials WHERE study_mission_id=:mission")
            .param("mission",binding.missionId()).query((r,n)->{
                rows.get("material_versions").add(r.getObject(2,UUID.class));rows.get("materials").add(r.getObject(1,UUID.class));
                UUID node=r.getObject(3,UUID.class);if(node!=null)rows.get("document_nodes").add(node);return true;
            }).list();
        var seeds=new java.util.ArrayList<SourceReferenceSeed>();
        var existing=new java.util.ArrayList<Optional<SourceReference>>();
        for(SourceReferenceTarget target:targets) {
            SourceReferenceSeed seed=initialSeed(owner,binding,target).orElseThrow(JdbcSourceReferenceRepository::initialUnavailable);
            seeds.add(seed);Optional<SourceReference> reference=initialExisting(seed);existing.add(reference);
            reference.ifPresent(r->rows.get("source_references").add(r.sourceReferenceId()));
            rows.get("material_versions").add(seed.materialVersionId());rows.get("materials").add(seed.materialId());
            if(seed.documentNodeId()!=null)rows.get("document_nodes").add(seed.documentNodeId());
            if(seed.chunkId()!=null)rows.get("chunks").add(seed.chunkId());
            if(seed.visualAssetId()!=null)rows.get("visual_assets").add(seed.visualAssetId());
        }
        for(var table:rows.entrySet()) for(UUID id:table.getValue().stream().sorted(java.util.Comparator.comparing(UUID::toString)).toList()) {
            if(jdbc.sql("SELECT id FROM "+table.getKey()+" WHERE id=:id FOR SHARE NOWAIT")
                    .param("id",id).query(UUID.class).optional().isEmpty()) throw initialUnavailable();
        }
        requireInitialClaim(owner,binding,false);
        for(int i=0;i<targets.size();i++) {
            if(!initialSeed(owner,binding,targets.get(i)).equals(Optional.of(seeds.get(i)))
                    || !initialExisting(seeds.get(i)).equals(existing.get(i))) throw initialUnavailable();
        }
        var result=new java.util.ArrayList<SourceReference>();
        for(int i=0;i<seeds.size();i++) {
            if(existing.get(i).isPresent()) { result.add(existing.get(i).get());continue; }
            var seed=seeds.get(i);
            var label=new java.util.ArrayList<String>();label.add(seed.materialTitle().strip());
            if(seed.pageNumber()!=null)label.add("Page "+seed.pageNumber());
            if(seed.documentNodeTitle()!=null && !seed.documentNodeTitle().isBlank())label.add(seed.documentNodeTitle().strip());
            // Existing references were locked in the one canonical pass. A new concurrent identity is a retry,
            // not permission to acquire an unplanned earlier reference lock or update historical labels.
            SourceReference inserted=jdbc.sql("""
                INSERT INTO source_references(id,material_id,material_version_id,document_node_id,chunk_id,
                    visual_asset_id,page_number,timestamp_start_ms,timestamp_end_ms,display_label,created_at)
                VALUES (:id,:material,:version,:node,:chunk,:visual,:page,NULL,NULL,:label,clock_timestamp())
                ON CONFLICT (material_id,material_version_id,document_node_id,chunk_id,visual_asset_id,
                    page_number,timestamp_start_ms,timestamp_end_ms) DO NOTHING
                RETURNING *
                """).param("id",UUID.randomUUID()).param("material",seed.materialId()).param("version",seed.materialVersionId())
                .param("node",seed.documentNodeId()).param("chunk",seed.chunkId()).param("visual",seed.visualAssetId())
                .param("page",seed.pageNumber()).param("label",String.join(" · ",label))
                .query(REFERENCE_MAPPER).optional().orElseThrow(JdbcSourceReferenceRepository::initialUnavailable);
            result.add(inserted);
        }
        requireInitialClaim(owner,binding,false);
        return java.util.List.copyOf(result);
    }

    private void requireInitialClaim(UUID owner,FrozenMissionBinding binding,boolean lock) {
        if(jdbc.sql("""
            SELECT r.mission_id FROM study_mission_start_receipts r
            JOIN study_missions sm ON sm.id=r.mission_id AND sm.user_id=r.owner_id
            JOIN learning_objectives lo ON lo.id=r.objective_id AND lo.study_mission_id=sm.id
            WHERE sm.id=:mission AND sm.user_id=:owner AND sm.status='ACTIVE' AND sm.current_activity_id IS NULL
              AND NOT EXISTS(SELECT 1 FROM learning_activities a WHERE a.study_mission_id=sm.id)
              AND r.state='CLAIMED' AND r.generation=:generation AND r.claim_token=:token
              AND r.work_deadline>clock_timestamp() AND r.lease_expires_at>clock_timestamp()
              AND sm.updated_at=(r.claim_snapshot->>'updatedAt')::timestamptz
              AND r.objective_id=(r.claim_snapshot->>'objectiveId')::uuid
              AND r.frozen_binding_fingerprint=(r.claim_snapshot->>'frozenBindingFingerprint')
              AND NOT EXISTS(SELECT 1 FROM mission_materials mm
                LEFT JOIN materials m ON m.id=mm.material_id
                LEFT JOIN material_versions mv ON mv.id=mm.material_version_id AND mv.material_id=m.id
                WHERE mm.study_mission_id=sm.id AND (m.id IS NULL OR m.user_id<>:owner
                  OR m.status NOT IN ('READY','PARTIALLY_READY') OR mv.id IS NULL
                  OR mv.processing_status NOT IN ('READY','PARTIALLY_READY')
                  OR (mm.document_node_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM document_nodes d
                    WHERE d.id=mm.document_node_id AND d.material_version_id=mv.id))))
            """+(lock?" FOR UPDATE OF r":"")).param("mission",binding.missionId()).param("owner",owner)
            .param("generation",binding.generation()).param("token",binding.claimToken()).query(UUID.class).optional().isEmpty())
            throw initialUnavailable();
    }

    private Optional<SourceReferenceSeed> initialSeed(UUID owner,FrozenMissionBinding binding,SourceReferenceTarget target) {
        UUID material,version,id;boolean chunk;
        switch(target) {
            case ChunkSourceTarget t -> {material=t.materialId();version=t.materialVersionId();id=t.chunkId();chunk=true;}
            case VisualSourceTarget t -> {material=t.materialId();version=t.materialVersionId();id=t.visualAssetId();chunk=false;}
            default -> throw initialUnavailable();
        }
        String table=chunk?"chunks":"visual_assets";
        String fields=chunk ? "x.id AS chunk_id,NULL::uuid AS visual_asset_id,CASE WHEN x.page_start=x.page_end THEN x.page_start END AS page_number"
                : "NULL::uuid AS chunk_id,x.id AS visual_asset_id,x.page_number";
        return jdbc.sql("SELECT m.id AS material_id,mv.id AS material_version_id,x.document_node_id,"+fields+"""
                ,m.title AS material_title,dn.title AS document_node_title
                FROM materials m JOIN material_versions mv ON mv.material_id=m.id
                """+" JOIN "+table+" x ON x.material_version_id=mv.id "+"""
                LEFT JOIN document_nodes dn ON dn.id=x.document_node_id AND dn.material_version_id=mv.id
                WHERE m.id=:material AND mv.id=:version AND x.id=:target AND m.user_id=:owner
                  AND m.status IN ('READY','PARTIALLY_READY') AND mv.processing_status IN ('READY','PARTIALLY_READY')
                  AND btrim(m.title)<>'' AND (x.document_node_id IS NULL OR dn.id IS NOT NULL)
                  AND ("""+(chunk?"x.is_active AND btrim(x.content)<>''":"x.interpretation_status IN ('SUPPORTED','LIMITED')")+") "+"""
                  AND EXISTS(SELECT 1 FROM mission_materials mm JOIN study_missions sm ON sm.id=mm.study_mission_id
                    JOIN study_mission_start_receipts r ON r.mission_id=sm.id AND r.owner_id=sm.user_id
                    WHERE sm.id=:mission AND sm.user_id=:owner AND sm.status='ACTIVE' AND sm.current_activity_id IS NULL
                      AND NOT EXISTS(SELECT 1 FROM learning_activities a WHERE a.study_mission_id=sm.id)
                      AND r.state='CLAIMED' AND r.generation=:generation AND r.claim_token=:token
                      AND r.work_deadline>clock_timestamp() AND r.lease_expires_at>clock_timestamp()
                      AND mm.material_id=m.id AND mm.material_version_id=mv.id
                      AND (mm.document_node_id IS NULL OR mm.document_node_id=x.document_node_id))
                """).param("material",material).param("version",version).param("target",id).param("owner",owner)
                .param("mission",binding.missionId()).param("generation",binding.generation()).param("token",binding.claimToken())
                .query(SEED_MAPPER).optional();
    }

    private Optional<SourceReference> initialExisting(SourceReferenceSeed seed) {
        return jdbc.sql("""
            SELECT * FROM source_references WHERE material_id=:material AND material_version_id=:version
              AND document_node_id IS NOT DISTINCT FROM CAST(:node AS uuid)
              AND chunk_id IS NOT DISTINCT FROM CAST(:chunk AS uuid)
              AND visual_asset_id IS NOT DISTINCT FROM CAST(:visual AS uuid)
              AND page_number IS NOT DISTINCT FROM CAST(:page AS integer)
              AND timestamp_start_ms IS NULL AND timestamp_end_ms IS NULL
            """).param("material",seed.materialId()).param("version",seed.materialVersionId()).param("node",seed.documentNodeId())
            .param("chunk",seed.chunkId()).param("visual",seed.visualAssetId()).param("page",seed.pageNumber())
            .query(REFERENCE_MAPPER).optional();
    }
    private static IllegalStateException initialUnavailable() {
        return new IllegalStateException("Initial source authorization changed");
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
