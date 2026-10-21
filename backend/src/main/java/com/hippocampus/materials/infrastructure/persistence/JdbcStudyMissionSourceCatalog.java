package com.hippocampus.materials.infrastructure.persistence;

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

import com.hippocampus.learning.domain.SourceReadiness;
import com.hippocampus.learning.port.StudyMissionSourceCatalog;

public class JdbcStudyMissionSourceCatalog implements StudyMissionSourceCatalog {

    private static final String RESOLVE_SOURCE = """
            SELECT m.id AS material_id,
                   mv.id AS material_version_id,
                   CASE
                       WHEN m.status = 'READY' AND mv.processing_status = 'READY' THEN 'READY'
                       ELSE 'LIMITED'
                   END AS source_readiness,
                   EXISTS (
                       SELECT 1
                       FROM chunks c
                       WHERE c.material_version_id = mv.id
                         AND c.is_active = true
                         AND btrim(c.content) <> ''
                         AND (CAST(:documentNodeId AS UUID) IS NULL
                              OR c.document_node_id = CAST(:documentNodeId AS UUID))
                   ) AS grounded_text_available,
                   EXISTS (
                       SELECT 1
                       FROM visual_assets va
                       WHERE va.material_version_id = mv.id
                         AND (CAST(:documentNodeId AS UUID) IS NULL
                              OR va.document_node_id = CAST(:documentNodeId AS UUID))
                   ) AS visual_available,
                   EXISTS (
                       SELECT 1
                       FROM visual_assets va
                       WHERE va.material_version_id = mv.id
                         AND va.interpretation_status = 'SUPPORTED'
                         AND (CAST(:documentNodeId AS UUID) IS NULL
                              OR va.document_node_id = CAST(:documentNodeId AS UUID))
                   ) AS visual_reliable
            FROM materials m
            JOIN material_versions mv
              ON mv.id = m.active_version_id
             AND mv.material_id = m.id
            WHERE m.id = :materialId
              AND m.user_id = :ownerId
              AND m.status IN ('READY', 'PARTIALLY_READY')
              AND mv.processing_status IN ('READY', 'PARTIALLY_READY')
              AND EXISTS (
                  SELECT 1
                  FROM material_topic_links link
                  WHERE link.topic_id = :topicId
                    AND link.material_id = m.id
                    AND link.status = 'ACTIVE'
                    AND (link.material_version_id IS NULL OR link.material_version_id = mv.id)
                    AND (link.document_node_id IS NULL
                         OR link.document_node_id = CAST(:documentNodeId AS UUID))
              )
              AND (CAST(:documentNodeId AS UUID) IS NULL OR EXISTS (
                  SELECT 1
                  FROM document_nodes node
                  WHERE node.id = CAST(:documentNodeId AS UUID)
                    AND node.material_version_id = mv.id
              ))
              AND EXISTS (
                  SELECT 1
                  FROM chunks c
                  WHERE c.material_version_id = mv.id
                    AND c.is_active = true
                    AND btrim(c.content) <> ''
                    AND (CAST(:documentNodeId AS UUID) IS NULL
                         OR c.document_node_id = CAST(:documentNodeId AS UUID))
              )
            """;

    private final JdbcClient jdbcClient;

    public JdbcStudyMissionSourceCatalog(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<Resolution> resolve(
            UUID ownerId,
            UUID topicId,
            List<SourceSelection> selections) {
        List<ResolvedSource> resolved = new ArrayList<>(selections.size());
        for (SourceSelection selection : selections) {
            Optional<ResolvedSource> source = jdbcClient.sql(RESOLVE_SOURCE)
                    .param("ownerId", ownerId)
                    .param("topicId", topicId)
                    .param("materialId", selection.materialId())
                    .param("documentNodeId", selection.documentNodeId(), Types.OTHER)
                    .query((result, rowNumber) -> new ResolvedSource(
                            result.getObject("material_id", UUID.class),
                            result.getObject("material_version_id", UUID.class),
                            selection.documentNodeId(),
                            SourceReadiness.valueOf(result.getString("source_readiness")),
                            result.getBoolean("grounded_text_available"),
                            result.getBoolean("visual_available"),
                            result.getBoolean("visual_reliable")))
                    .optional();
            if (source.isEmpty()) {
                return Optional.empty();
            }
            resolved.add(source.get());
        }
        return Optional.of(new Resolution(resolved));
    }
    @Override
    public Optional<Resolution> resolveLocked(UUID owner,UUID topic,List<SourceSelection> selections) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
                || org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !Integer.valueOf(java.sql.Connection.TRANSACTION_READ_COMMITTED).equals(
                    org.springframework.transaction.support.TransactionSynchronizationManager.getCurrentTransactionIsolationLevel()))
            throw new IllegalStateException("Source freeze requires READ_COMMITTED write transaction");
        Optional<Resolution> first=resolve(owner,topic,selections);
        if(first.isEmpty()) return Optional.empty();
        var locks=new java.util.LinkedHashMap<String,List<UUID>>();
        for(String table:List.of("material_versions","materials","material_topic_links","document_nodes","chunks","visual_assets"))
            locks.put(table,new ArrayList<>());
        var linkBindings=new java.util.LinkedHashMap<UUID,ResolvedSource>();
        for(ResolvedSource source:first.get().sources()) {
            locks.get("material_versions").add(source.materialVersionId()); locks.get("materials").add(source.materialId());
            if(source.documentNodeId()!=null) locks.get("document_nodes").add(source.documentNodeId());
            UUID link=jdbcClient.sql("""
                SELECT id FROM material_topic_links WHERE material_id=:material AND topic_id=:topic AND status='ACTIVE'
                  AND (material_version_id IS NULL OR material_version_id=:version)
                  AND (document_node_id IS NULL OR document_node_id=CAST(:node AS uuid)) ORDER BY id LIMIT 1
                """).param("material",source.materialId()).param("topic",topic).param("version",source.materialVersionId())
                .param("node",source.documentNodeId()).query(UUID.class).optional().orElse(null);
            if(link==null) return Optional.empty();
            locks.get("material_topic_links").add(link);
            linkBindings.put(link,source);
            locks.get("chunks").addAll(jdbcClient.sql("""
                SELECT id FROM chunks WHERE material_version_id=:version AND is_active AND btrim(content)<>''
                  AND (CAST(:node AS uuid) IS NULL OR document_node_id=CAST(:node AS uuid)) ORDER BY id LIMIT 1
                """).param("version",source.materialVersionId()).param("node",source.documentNodeId()).query(UUID.class).list());
            for(boolean reliable:List.of(false,true)) locks.get("visual_assets").addAll(jdbcClient.sql("""
                SELECT id FROM visual_assets WHERE material_version_id=:version
                  AND (CAST(:node AS uuid) IS NULL OR document_node_id=CAST(:node AS uuid))
                """+(reliable?" AND interpretation_status='SUPPORTED'":"")+" ORDER BY id LIMIT 1")
                .param("version",source.materialVersionId()).param("node",source.documentNodeId()).query(UUID.class).list());
        }
        for(var group:locks.entrySet()) for(UUID id:group.getValue().stream().distinct()
                .sorted(java.util.Comparator.comparing(UUID::toString)).toList()) {
            if(jdbcClient.sql("SELECT id FROM "+group.getKey()+" WHERE id=:id FOR SHARE NOWAIT")
                    .param("id",id).query(UUID.class).optional().isEmpty()) throw changed();
        }
        for(var entry:linkBindings.entrySet()) {
            var source=entry.getValue();
            boolean valid=jdbcClient.sql("""
                SELECT EXISTS(SELECT 1 FROM material_topic_links WHERE id=:id AND material_id=:material
                  AND topic_id=:topic AND status='ACTIVE'
                  AND (material_version_id IS NULL OR material_version_id=:version)
                  AND (document_node_id IS NULL OR document_node_id=CAST(:node AS uuid)))
                """).param("id",entry.getKey()).param("material",source.materialId()).param("topic",topic)
                .param("version",source.materialVersionId()).param("node",source.documentNodeId()).query(Boolean.class).single();
            if(!valid) throw changed();
        }
        Optional<Resolution> after=resolve(owner,topic,selections);
        if(!first.equals(after)) throw changed();
        return after;
    }

    @Override
    public SourcePage eligible(UUID owner,UUID topic,int limit,UUID after) {
        if(limit<1 || limit>50) throw new IllegalArgumentException("Invalid page limit");
        String sql=RESOLVE_SOURCE.replace("SELECT m.id AS material_id,",
                "SELECT m.id AS material_id,m.title,mv.processing_status,")
                .replace("WHERE m.id = :materialId","WHERE (CAST(:after AS uuid) IS NULL OR m.id>CAST(:after AS uuid))")
                +" ORDER BY m.id LIMIT :maximum";
        List<EligibleSource> rows=jdbcClient.sql(sql).param("ownerId",owner).param("topicId",topic)
                .param("documentNodeId",null,Types.OTHER).param("after",after).param("maximum",limit+1)
                .query((r,n)->new EligibleSource(r.getObject("material_id",UUID.class),r.getString("title"),
                    r.getObject("material_version_id",UUID.class),r.getString("processing_status"),
                    SourceReadiness.valueOf(r.getString("source_readiness")))).list();
        boolean more=rows.size()>limit;
        List<EligibleSource> items=more?rows.subList(0,limit):rows;
        return new SourcePage(items,more?items.getLast().materialId():null);
    }
    private static com.hippocampus.learning.port.InitialStudyMissionSourceAuthority.SourceUnavailableException changed() {
        return new com.hippocampus.learning.port.InitialStudyMissionSourceAuthority.SourceUnavailableException();
    }

}
