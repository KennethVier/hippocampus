package com.hippocampus.learning.infrastructure.persistence;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.sql.Connection;
import java.sql.SQLException;

import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.hippocampus.learning.domain.MissionMaterial;
import com.hippocampus.learning.port.ActivitySourceReferenceAuthorization;
import com.hippocampus.learning.port.SourceDependencyBusyException;

@Repository
@Lazy
public class JdbcActivitySourceReferenceAuthorization
        implements ActivitySourceReferenceAuthorization, com.hippocampus.learning.port.InitialStudyMissionSourceAuthority {

    private final JdbcClient jdbc;

    public JdbcActivitySourceReferenceAuthorization(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
    }

    @Override
    public boolean availableForRead(UUID userId, List<MissionMaterial> missionMaterials,
            Set<UUID> sourceReferenceIds) {
        return frozenSourcesAvailable(userId, missionMaterials, false)
                && ownedAndWithinMissionScope(userId, missionMaterials, sourceReferenceIds, false);
    }

    @Override
    public boolean allOwnedAndWithinMissionScope(
            UUID userId,
            List<MissionMaterial> missionMaterials,
            Set<UUID> sourceReferenceIds) {
        return ownedAndWithinMissionScope(userId, missionMaterials, sourceReferenceIds, true);
    }

    private boolean ownedAndWithinMissionScope(UUID userId, List<MissionMaterial> missionMaterials,
            Set<UUID> sourceReferenceIds, boolean lock) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(missionMaterials, "missionMaterials must not be null");
        Objects.requireNonNull(sourceReferenceIds, "sourceReferenceIds must not be null");
        for (UUID id : sourceReferenceIds.stream().sorted().toList()) {
            if (jdbc.sql("SELECT id FROM source_references WHERE id=:id" + (lock ? " FOR SHARE" : ""))
                    .param("id",id).query(UUID.class).list().isEmpty()) return false;
        }
        return sourceReferenceIds.stream().allMatch(sourceReferenceId ->
                isAuthorized(userId, missionMaterials, sourceReferenceId));
    }

    @Override
    public boolean allFrozenSourcesAvailable(UUID userId, List<MissionMaterial> materials) {
        return frozenSourcesAvailable(userId, materials, true);
    }

    private boolean frozenSourcesAvailable(UUID userId, List<MissionMaterial> materials, boolean lock) {
        // The owned mission is locked first by the caller. Deterministic source order avoids
        // lock-order inversions. No active-version substitution: old frozen versions stay valid.
        List<MissionMaterial> ordered=materials.stream()
                .sorted(java.util.Comparator.comparing(s -> s.materialId().toString())).toList();
        for (MissionMaterial scope : ordered) {
            boolean available=!jdbc.sql("""
                    SELECT m.id FROM materials m
                    JOIN material_versions mv ON mv.material_id=m.id AND mv.id=:version
                    WHERE m.id=:material AND m.user_id=:owner
                      AND m.status IN ('READY','PARTIALLY_READY')
                      AND mv.processing_status IN ('READY','PARTIALLY_READY')
                    """ + (lock ? " FOR SHARE OF m,mv" : ""))
                    .param("version",scope.materialVersionId()).param("material",scope.materialId())
                    .param("owner",userId).query(UUID.class).list().isEmpty();
            if (!available) return false;
            if (scope.documentNodeId()!=null && jdbc.sql("""
                    SELECT id FROM document_nodes WHERE id=:id AND material_version_id=:version
                    """ + (lock ? " FOR SHARE" : ""))
                    .param("id",scope.documentNodeId()).param("version",scope.materialVersionId())
                    .query(UUID.class).list().isEmpty()) return false;
        }
        return true;
    }

    private boolean isAuthorized(
            UUID userId, List<MissionMaterial> scopes, UUID sourceReferenceId) {
        return scopes.stream().anyMatch(scope -> Boolean.TRUE.equals(jdbc.sql("""
                SELECT EXISTS (
                    SELECT 1
                    FROM source_references sr
                    JOIN materials material ON material.id = sr.material_id
                    WHERE sr.id = :sourceReferenceId
                      AND material.user_id = :userId
                      AND material.status <> 'DELETED'
                      AND sr.material_id = :materialId
                      AND sr.material_version_id = :materialVersionId
                      AND (CAST(:documentNodeId AS uuid) IS NULL
                           OR sr.document_node_id = CAST(:documentNodeId AS uuid))
                )
                """)
                .param("sourceReferenceId", sourceReferenceId)
                .param("userId", userId)
                .param("materialId", scope.materialId())
                .param("materialVersionId", scope.materialVersionId())
                .param("documentNodeId", scope.documentNodeId())
                .query(Boolean.class).single()));
    }

    @Override
    public boolean lockAndValidateForCompletion(UUID userId, List<MissionMaterial> scopes,
            Set<UUID> references, boolean sourceRequired) {
        return lockAndValidateForCompletion(userId,scopes,references,sourceRequired,List.of(),Set.of());
    }

    @Override
    public boolean lockAndValidateForCompletion(UUID userId, List<MissionMaterial> scopes,
            Set<UUID> references, boolean sourceRequired,
            List<MissionMaterial> consultedScopes, Set<UUID> consultedReferences) {
        return lockAndValidate(userId,scopes,references,sourceRequired,consultedScopes,consultedReferences,Map.of());
    }

    private boolean lockAndValidate(UUID userId,List<MissionMaterial> scopes,Set<UUID> references,
            boolean sourceRequired,List<MissionMaterial> consultedScopes,Set<UUID> consultedReferences,
            Map<String,List<UUID>> witnesses) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !Integer.valueOf(Connection.TRANSACTION_READ_COMMITTED).equals(
                        TransactionSynchronizationManager.getCurrentTransactionIsolationLevel()))
            throw new IllegalStateException("Completion requires an effective READ COMMITTED write transaction");
        if (sourceRequired && references.isEmpty()) return false;
        Map<UUID, Binding> bindings = new LinkedHashMap<>();
        Map<String, List<UUID>> rows = new LinkedHashMap<>();
        for (String table : List.of("material_versions", "materials", "source_references",
                "document_nodes", "chunks", "visual_assets")) rows.put(table, new ArrayList<>());
        witnesses.forEach((table,ids) -> rows.get(table).addAll(ids));
        Set<UUID> allReferences=new java.util.HashSet<>(references);
        allReferences.addAll(consultedReferences);
        for (MissionMaterial scope:consultedScopes) {
            if (!scopes.contains(scope) || !ownedScope(userId,scope)) return false;
            rows.get("material_versions").add(scope.materialVersionId());
            rows.get("materials").add(scope.materialId());
            if (scope.documentNodeId()!=null) rows.get("document_nodes").add(scope.documentNodeId());
        }
        for (UUID id : ordered(allReferences)) {
            // Consulted sources may already be unavailable; the policy observes that under locks.
            Binding binding = binding(userId, id, !references.contains(id));
            if (binding == null) return false;
            MissionMaterial scope = scopes.stream().filter(s -> s.materialId().equals(binding.material())
                    && s.materialVersionId().equals(binding.version())
                    && (s.documentNodeId() == null || s.documentNodeId().equals(binding.node())))
                    .findFirst().orElse(null);
            if (scope == null) return false;
            bindings.put(id, binding);
            rows.get("material_versions").add(binding.version());
            rows.get("materials").add(binding.material());
            rows.get("source_references").add(id);
            if (scope.documentNodeId() != null) rows.get("document_nodes").add(scope.documentNodeId());
            if (binding.node() != null) rows.get("document_nodes").add(binding.node());
            if (binding.chunk() != null) rows.get("chunks").add(binding.chunk());
            if (binding.visual() != null) rows.get("visual_assets").add(binding.visual());
        }
        for (var table : rows.entrySet()) {
            for (UUID id : ordered(table.getValue())) {
                try {
                    // table is selected only from the fixed internal list above, never from request data.
                    if (jdbc.sql("SELECT id FROM " + table.getKey() + " WHERE id=:id FOR SHARE NOWAIT")
                            .param("id", id).query(UUID.class).optional().isEmpty()) return false;
                } catch (DataAccessException failure) {
                    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                        if (cause instanceof SQLException sql && "55P03".equals(sql.getSQLState()))
                            throw new SourceDependencyBusyException(failure);
                    }
                    throw failure;
                }
            }
        }
        for (var entry : bindings.entrySet()) {
            Binding locked = binding(userId, entry.getKey(), !references.contains(entry.getKey()));
            if (locked == null) return false;
            if (!entry.getValue().equals(locked)) throw new SourceDependencyBusyException(null);
            if (references.contains(entry.getKey()) && !validTarget(userId, entry.getKey())) return false;
        }
        for (MissionMaterial scope:consultedScopes) if (!ownedScope(userId,scope))
            throw new SourceDependencyBusyException(null);
        return true;
    }


    @Override
    public List<ScopeFacts> inspect(UUID owner,List<MissionMaterial> scopes) {
        List<ScopeFacts> facts=new ArrayList<>();
        for (MissionMaterial scope:scopes) {
            ScopeFacts fact=jdbc.sql("""
                SELECT m.status,mv.processing_status,
                  (SELECT c.id FROM chunks c WHERE c.material_version_id=mv.id AND c.is_active
                    AND btrim(c.content)<>'' AND (CAST(:node AS uuid) IS NULL OR c.document_node_id=CAST(:node AS uuid))
                    ORDER BY c.id LIMIT 1) AS chunk_witness,
                  (SELECT v.id FROM visual_assets v WHERE v.material_version_id=mv.id
                    AND (CAST(:node AS uuid) IS NULL OR v.document_node_id=CAST(:node AS uuid))
                    ORDER BY v.id LIMIT 1) AS visual_witness,
                  (SELECT v.id FROM visual_assets v WHERE v.material_version_id=mv.id
                    AND v.interpretation_status='SUPPORTED'
                    AND (CAST(:node AS uuid) IS NULL OR v.document_node_id=CAST(:node AS uuid))
                    ORDER BY v.id LIMIT 1) AS reliable_witness
                FROM materials m JOIN material_versions mv ON mv.material_id=m.id AND mv.id=:version
                WHERE m.id=:material AND m.user_id=:owner AND m.status IN ('READY','PARTIALLY_READY')
                  AND mv.processing_status IN ('READY','PARTIALLY_READY')
                  AND (CAST(:node AS uuid) IS NULL OR EXISTS(SELECT 1 FROM document_nodes d
                    WHERE d.id=CAST(:node AS uuid) AND d.material_version_id=mv.id))
                """).param("material",scope.materialId()).param("version",scope.materialVersionId())
                .param("owner",owner).param("node",scope.documentNodeId()).query((r,n)->{
                    UUID chunk=r.getObject("chunk_witness",UUID.class);
                    UUID visual=r.getObject("visual_witness",UUID.class);
                    UUID reliable=r.getObject("reliable_witness",UUID.class);
                    Set<UUID> visuals=new java.util.HashSet<>();
                    if(visual!=null) visuals.add(visual); if(reliable!=null) visuals.add(reliable);
                    String status=r.getString("status"),version=r.getString("processing_status");
                    return new ScopeFacts(scope.materialId(),scope.materialVersionId(),scope.documentNodeId(),
                        status,version,"READY".equals(status)&&"READY".equals(version)
                            ? com.hippocampus.learning.domain.SourceReadiness.READY
                            : com.hippocampus.learning.domain.SourceReadiness.LIMITED,
                        chunk!=null,visual!=null,reliable!=null,chunk==null?Set.of():Set.of(chunk),visuals);
                }).optional().orElseThrow(()->new com.hippocampus.learning.port.InitialStudyMissionSourceAuthority.SourceUnavailableException());
            facts.add(fact);
        }
        return List.copyOf(facts);
    }

    @Override
    public List<ScopeFacts> inspectForExposure(UUID owner,List<MissionMaterial> scopes,Set<UUID> references) {
        var facts=inspect(owner,scopes);
        for(UUID reference:references) {
            Binding target=binding(owner,reference,false);
            if(target==null || (target.chunk()==null && target.visual()==null)
                    || scopes.stream().noneMatch(scope->scope.materialId().equals(target.material())
                        && scope.materialVersionId().equals(target.version())
                        && (scope.documentNodeId()==null || scope.documentNodeId().equals(target.node())))
                    || !validInitialTarget(owner,reference))
                throw new com.hippocampus.learning.port.InitialStudyMissionSourceAuthority.SourceUnavailableException();
        }
        return facts;
    }

    @Override
    public List<ScopeFacts> lockAndInspect(UUID owner,List<MissionMaterial> scopes,
            Set<UUID> references,boolean sourceRequired) {
        List<ScopeFacts> before=inspect(owner,scopes);
        Map<String,List<UUID>> witnesses=Map.of("chunks",new ArrayList<>(),"visual_assets",new ArrayList<>());
        before.forEach(f->{witnesses.get("chunks").addAll(f.chunkWitnesses());
            witnesses.get("visual_assets").addAll(f.visualWitnesses());});
        if(!lockAndValidate(owner,scopes,references,sourceRequired,scopes,Set.of(),witnesses))
            throw new com.hippocampus.learning.port.InitialStudyMissionSourceAuthority.SourceUnavailableException();
        for(UUID reference:references) if(!validInitialTarget(owner,reference))
            throw new com.hippocampus.learning.port.InitialStudyMissionSourceAuthority.SourceUnavailableException();
        List<ScopeFacts> after=inspect(owner,scopes);
        if(!before.equals(after)) throw new SourceDependencyBusyException(null);
        return after;
    }

    private boolean ownedScope(UUID owner, MissionMaterial scope) {
        return jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM materials m
                    JOIN material_versions mv ON mv.material_id=m.id AND mv.id=:version
                    WHERE m.id=:material AND m.user_id=:owner
                      AND (CAST(:node AS uuid) IS NULL OR EXISTS (
                          SELECT 1 FROM document_nodes dn WHERE dn.id=CAST(:node AS uuid)
                              AND dn.material_version_id=mv.id)))
                """).param("material",scope.materialId()).param("version",scope.materialVersionId())
                .param("owner",owner).param("node",scope.documentNodeId()).query(Boolean.class).single();
    }

    private Binding binding(UUID owner, UUID reference, boolean includeUnavailable) {
        return jdbc.sql("""
                SELECT sr.material_id, sr.material_version_id, sr.document_node_id,
                       sr.chunk_id, sr.visual_asset_id, sr.page_number
                FROM source_references sr JOIN materials m ON m.id=sr.material_id
                WHERE sr.id=:id AND m.user_id=:owner
                """ + (includeUnavailable ? "" : " AND m.status<>'DELETED'"))
                .param("id", reference).param("owner", owner)
                .query((r, n) -> new Binding(r.getObject(1, UUID.class), r.getObject(2, UUID.class),
                        r.getObject(3, UUID.class), r.getObject(4, UUID.class), r.getObject(5, UUID.class),
                        r.getObject(6, Integer.class))).optional().orElse(null);
    }

    /** Initial evidence keeps the exact node/page association admitted by initial provenance creation. */
    private boolean validInitialTarget(UUID owner,UUID reference) {
        return validTarget(owner,reference) && jdbc.sql("""
            SELECT EXISTS(SELECT 1 FROM source_references sr
              LEFT JOIN chunks c ON c.id=sr.chunk_id AND c.material_version_id=sr.material_version_id
              LEFT JOIN visual_assets va ON va.id=sr.visual_asset_id AND va.material_version_id=sr.material_version_id
              WHERE sr.id=:id AND (
                (sr.chunk_id IS NOT NULL AND sr.visual_asset_id IS NULL AND c.is_active AND btrim(c.content)<>''
                  AND c.document_node_id IS NOT DISTINCT FROM sr.document_node_id
                  AND sr.page_number IS NOT DISTINCT FROM CASE WHEN c.page_start=c.page_end THEN c.page_start END)
                OR (sr.visual_asset_id IS NOT NULL AND sr.chunk_id IS NULL
                  AND va.interpretation_status IN ('SUPPORTED','LIMITED')
                  AND va.document_node_id IS NOT DISTINCT FROM sr.document_node_id
                  AND sr.page_number=va.page_number)))
            """).param("id",reference).query(Boolean.class).single();
    }

    private boolean validTarget(UUID owner, UUID reference) {
        return jdbc.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM source_references sr
                    JOIN material_versions mv ON mv.id=sr.material_version_id AND mv.material_id=sr.material_id
                    JOIN materials m ON m.id=sr.material_id
                    LEFT JOIN chunks c ON c.id=sr.chunk_id AND c.material_version_id=sr.material_version_id
                    LEFT JOIN document_nodes dn ON dn.id=sr.document_node_id AND dn.material_version_id=sr.material_version_id
                    LEFT JOIN visual_assets va ON va.id=sr.visual_asset_id AND va.material_version_id=sr.material_version_id
                    WHERE sr.id=:id AND m.user_id=:owner AND m.status<>'DELETED'
                      AND (sr.chunk_id IS NULL OR (c.id IS NOT NULL AND c.is_active))
                      AND (sr.document_node_id IS NULL OR dn.id IS NOT NULL)
                      AND (sr.visual_asset_id IS NULL OR va.id IS NOT NULL)
                      AND (sr.page_number IS NULL OR mv.page_count IS NULL OR sr.page_number<=mv.page_count)
                )
                """).param("id", reference).param("owner", owner).query(Boolean.class).single();
    }

    private static List<UUID> ordered(java.util.Collection<UUID> ids) {
        // Canonical UUID text order agrees with PostgreSQL's unsigned UUID byte order.
        return ids.stream().distinct().sorted(Comparator.comparing(UUID::toString)).toList();
    }

    private record Binding(UUID material, UUID version, UUID node, UUID chunk, UUID visual, Integer page) {}
}
