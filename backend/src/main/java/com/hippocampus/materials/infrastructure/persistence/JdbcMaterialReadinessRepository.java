package com.hippocampus.materials.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.hippocampus.materials.domain.MaterialReadiness;
import com.hippocampus.materials.port.MaterialReadinessRepository;

/** Reads bounded aggregate facts; lifecycle decisions remain in the domain policy. */
public final class JdbcMaterialReadinessRepository implements MaterialReadinessRepository {
    private final JdbcClient jdbc;
    public JdbcMaterialReadinessRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override public Optional<Snapshot> lockAndRead(UUID jobId) {
        requireTransaction();
        // Follow artifact persistence lock order: version before parent. The triggering job is already locked.
        var context = jdbc.sql("""
                SELECT mv.id AS version_id, mv.material_id, pj.processing_version
                FROM processing_jobs pj JOIN material_versions mv ON mv.id=pj.material_version_id
                JOIN materials m ON m.id=mv.material_id
                WHERE pj.id=:job AND pj.user_id=m.user_id
                  AND pj.job_type IN ('MATERIAL_VALIDATE','MATERIAL_EXTRACT','STRUCTURE_DETECT','VISUAL_EXTRACT','NORMALIZE','CHUNK')
                FOR UPDATE OF mv
                """).param("job", jobId).query((r, n) -> new Context(r.getObject("version_id", UUID.class),
                        r.getObject("material_id", UUID.class), r.getString("processing_version"))).optional();
        if (context.isEmpty()) return Optional.empty(); // Non-material jobs and invalid ownership cannot affect lifecycle.
        Context c = context.get();
        String parent = jdbc.sql("SELECT status FROM materials WHERE id=:id FOR UPDATE")
                .param("id", c.materialId()).query(String.class).single();
        if ("DELETED".equals(parent)) return Optional.empty();
        var parentFacts = jdbc.sql("""
                SELECT active.processing_status AS active_status,
                       NOT EXISTS (SELECT 1 FROM material_versions newer
                           WHERE newer.material_id=m.id AND newer.version_number>mv.version_number) AS latest
                FROM materials m JOIN material_versions mv ON mv.id=:version
                LEFT JOIN material_versions active ON active.id=m.active_version_id AND active.material_id=m.id
                WHERE m.id=:material
                """).param("version", c.versionId()).param("material", c.materialId())
                .query((r,n) -> new Parent(r.getString("active_status"), r.getBoolean("latest"))).single();
        var stages = jdbc.sql("""
                SELECT count(*) AS total, count(DISTINCT job_type) AS types,
                       count(*) FILTER (WHERE status='COMPLETED') AS completed,
                       count(*) FILTER (WHERE status='FAILED') AS failed
                FROM processing_jobs WHERE material_version_id=:version AND processing_version=:processing
                  AND job_type IN ('MATERIAL_VALIDATE','MATERIAL_EXTRACT','STRUCTURE_DETECT','VISUAL_EXTRACT','NORMALIZE','CHUNK')
                """).param("version", c.versionId()).param("processing", c.processingVersion())
                .query((r,n) -> new Stages(r.getLong("total"), r.getLong("types"), r.getLong("completed"), r.getLong("failed"))).single();
        boolean completed = stages.total()==6 && stages.types()==6 && stages.completed()==6;
        // Only completed required processing can establish final evidence validity. Do not scan artifacts on heartbeats.
        boolean valid = false;
        boolean usable = false;
        boolean limited = false;
        if (completed) {
            valid = provenanceValid(c.versionId());
            var qualities = jdbc.sql("""
                    SELECT extraction_method, quality, count(*) AS amount FROM chunks
                    WHERE material_version_id=:version AND is_active AND btrim(content)<>''
                    GROUP BY extraction_method, quality
                    """).param("version", c.versionId()).query((r,n) -> new Quality(r.getString(1), r.getString(2))).list();
            usable = qualities.stream().anyMatch(q -> MaterialReadiness.usable(q.method(), q.quality()));
            limited = qualities.stream().anyMatch(q -> MaterialReadiness.limited(q.quality()));
            var textQualities = jdbc.sql("SELECT DISTINCT quality FROM text_blocks WHERE material_version_id=:version")
                    .param("version", c.versionId()).query((r,n) -> r.getString(1)).list();
            limited |= textQualities.stream().anyMatch(MaterialReadiness::limited);
            var visualStatuses = jdbc.sql("SELECT DISTINCT interpretation_status FROM visual_assets WHERE material_version_id=:version")
                    .param("version", c.versionId()).query(String.class).list();
            limited |= visualStatuses.stream().anyMatch(MaterialReadiness::visualLimitation);
        }
        return Optional.of(new Snapshot(c.materialId(), c.versionId(), parent, parentFacts.activeStatus(),
                parentFacts.latest(), new MaterialReadiness.Facts(stages.total()>0, stages.failed()>0,
                    completed, valid, usable, limited, MaterialReadiness.IndexPrerequisite.ABSENT)));
    }

    private boolean provenanceValid(UUID version) {
        return Boolean.TRUE.equals(jdbc.sql("""
                SELECT mv.storage_key IS NOT NULL AND btrim(mv.storage_key)<>'' AND mv.file_size_bytes>0
                  AND mv.page_count>0
                  AND (SELECT count(*) FROM document_nodes d WHERE d.material_version_id=mv.id
                         AND d.node_type='DOCUMENT' AND d.parent_id IS NULL)=1
                  AND (SELECT count(*) FROM text_blocks t WHERE t.material_version_id=mv.id AND t.block_type='PAGE_TEXT')=mv.page_count
                  AND (SELECT count(DISTINCT t.page_number) FROM text_blocks t
                         WHERE t.material_version_id=mv.id AND t.block_type='PAGE_TEXT')=mv.page_count
                  AND NOT EXISTS (SELECT 1 FROM text_blocks t WHERE t.material_version_id=mv.id
                      AND (t.normalized_content IS NULL OR t.document_node_id IS NULL
                        OR t.page_number IS NULL OR t.page_number<1 OR t.page_number>mv.page_count
                        OR (t.block_type='PAGE_TEXT' AND t.ordinal<>t.page_number)
                        OR (t.extraction_method='OCR' AND t.quality IS NULL)
                        OR NOT EXISTS (SELECT 1 FROM document_nodes d WHERE d.id=t.document_node_id AND d.material_version_id=mv.id)))
                  AND NOT EXISTS (SELECT 1 FROM chunks c WHERE c.material_version_id=mv.id
                      AND (c.document_node_id IS NULL OR c.page_start IS NULL OR c.page_end IS NULL
                        OR c.page_start<1 OR c.page_end>mv.page_count
                        OR (c.extraction_method='OCR' AND c.quality IS NULL)
                        OR NOT EXISTS (SELECT 1 FROM document_nodes d WHERE d.id=c.document_node_id AND d.material_version_id=mv.id)
                        OR NOT EXISTS (SELECT 1 FROM chunk_text_block_links l WHERE l.chunk_id=c.id)
                        OR (SELECT max(l.source_position) FROM chunk_text_block_links l WHERE l.chunk_id=c.id)
                           <> (SELECT count(*) FROM chunk_text_block_links l WHERE l.chunk_id=c.id)
                        OR EXISTS (SELECT 1 FROM chunk_text_block_links l
                            JOIN text_blocks t ON t.id=l.text_block_id WHERE l.chunk_id=c.id
                            AND (l.material_version_id<>mv.id OR t.material_version_id<>mv.id
                                OR t.normalized_content IS NULL OR btrim(t.normalized_content)=''
                                OR t.extraction_method<>c.extraction_method
                                OR t.page_number<c.page_start OR t.page_number>c.page_end))))
                FROM material_versions mv WHERE mv.id=:version
                """).param("version", version).query(Boolean.class).single());
    }

    @Override public void update(Snapshot snapshot, MaterialReadiness.State versionStatus, String parentStatus) {
        requireTransaction();
        jdbc.sql("""
                UPDATE material_versions SET processing_status=:status
                WHERE id=:id AND processing_status IS DISTINCT FROM :status
                """).param("id", snapshot.versionId()).param("status", versionStatus.name()).update();
        jdbc.sql("""
                UPDATE materials SET status=:status, updated_at=CURRENT_TIMESTAMP
                WHERE id=:id AND status<>'DELETED' AND status IS DISTINCT FROM :status
                """).param("id", snapshot.materialId()).param("status", parentStatus).update();
    }
    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Readiness derivation requires the job transition transaction");
    }
    private record Context(UUID versionId, UUID materialId, String processingVersion) {}
    private record Parent(String activeStatus, boolean latest) {}
    private record Stages(long total, long types, long completed, long failed) {}
    private record Quality(String method, String quality) {}
}
