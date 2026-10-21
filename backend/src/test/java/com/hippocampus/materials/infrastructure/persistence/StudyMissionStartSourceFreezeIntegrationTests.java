package com.hippocampus.materials.infrastructure.persistence;

import com.hippocampus.learning.domain.MissionMaterial;
import com.hippocampus.learning.infrastructure.persistence.JdbcActivitySourceReferenceAuthorization;
import com.hippocampus.learning.infrastructure.persistence.StudyMissionStartPersistenceIntegrationTests;
import com.hippocampus.learning.port.StudyMissionSourceCatalog.SourceSelection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Operator/CI only: actual catalog predicate and frozen-version authority, not mocked SQL. */
class StudyMissionStartSourceFreezeIntegrationTests extends StudyMissionStartPersistenceIntegrationTests {
    @Test void frozenV1SurvivesV2ActivationAcrossSearchVisualsAndAtomicProvenance() {
        var claim=claimWithFrozenScope();
        var binding=new com.hippocampus.rag.port.FrozenMissionSourceBinding(seed.mission(),claim.generation(),claim.claimToken());
        UUID generation=UUID.randomUUID(),visual=UUID.randomUUID(),next=UUID.randomUUID();
        jdbc.sql("INSERT INTO index_generations(id,embedding_provider,embedding_model,embedding_dimension,chunking_version,status,created_at) VALUES (:id,'TEST','synthetic',2,'CHUNKER_V1','ACTIVE',now())").param("id",generation).update();
        jdbc.sql("INSERT INTO chunk_embeddings(id,chunk_id,index_generation_id,embedding,created_at) VALUES (:id,:chunk,:generation,'[1,0]'::vector,now())")
            .param("id",UUID.randomUUID()).param("chunk",seed.chunk()).param("generation",generation).update();
        jdbc.sql("INSERT INTO visual_assets(id,material_version_id,page_number,storage_key,visual_type,caption,interpretation_status,content_hash,created_at) VALUES (:id,:version,1,:key,'FLOW_DIAGRAM','Synthetic','SUPPORTED',:hash,now())")
            .param("id",visual).param("version",seed.version()).param("key","private/"+visual).param("hash",visual.toString()).update();
        jdbc.sql("INSERT INTO chunk_visual_links(chunk_id,visual_asset_id,material_version_id,relationship_type) VALUES (:chunk,:visual,:version,'NEARBY')")
            .param("chunk",seed.chunk()).param("visual",visual).param("version",seed.version()).update();
        jdbc.sql("INSERT INTO material_versions(id,material_id,version_number,processing_status,created_at) VALUES (:id,:material,2,'READY',now())")
            .param("id",next).param("material",seed.material()).update();
        jdbc.sql("UPDATE materials SET active_version_id=:next WHERE id=:material").param("next",next).param("material",seed.material()).update();
        jdbc.sql("UPDATE material_topic_links SET status='ARCHIVED' WHERE material_id=:material").param("material",seed.material()).update();
        var scope=frozenScope(seed.owner(),seed.version());
        var lexical=new com.hippocampus.rag.infrastructure.persistence.JdbcLexicalSearchRepository(jdbc,new tools.jackson.databind.ObjectMapper());
        var vector=new com.hippocampus.rag.infrastructure.persistence.JdbcVectorSearchRepository(jdbc,new tools.jackson.databind.ObjectMapper());
        var visuals=new com.hippocampus.rag.infrastructure.persistence.JdbcEvidenceVisualRepository(jdbc);
        var hits=lexical.search(new com.hippocampus.rag.port.LexicalSearchRequest(scope,"Synthetic",10,binding));
        assertEquals(List.of(seed.chunk()),hits.stream().map(com.hippocampus.rag.port.LexicalSearchHit::chunkId).toList());
        assertEquals(List.of(seed.chunk()),vector.search(new com.hippocampus.rag.port.VectorSearchRequest(scope,generation,
            new com.hippocampus.rag.port.EmbeddingVector(List.of(1F,0F)),10,binding)).stream().map(com.hippocampus.rag.port.VectorSearchHit::chunkId).toList());
        assertEquals(List.of(visual),visuals.findLinkedVisuals(scope,java.util.Set.of(seed.chunk()),binding).stream().map(com.hippocampus.rag.port.EvidenceVisualSource::visualId).toList());
        assertTrue(lexical.search(new com.hippocampus.rag.port.LexicalSearchRequest(scope,"Synthetic",10)).isEmpty());
        assertTrue(vector.search(new com.hippocampus.rag.port.VectorSearchRequest(scope,generation,new com.hippocampus.rag.port.EmbeddingVector(List.of(1F,0F)),10)).isEmpty());
        assertTrue(visuals.findLinkedVisuals(scope,java.util.Set.of(seed.chunk())).isEmpty());
        var repository=new JdbcSourceReferenceRepository(jdbc);
        var materializer=new com.hippocampus.rag.application.MaterializeEvidenceSourceReferences(new com.hippocampus.materials.application.CreateSourceReferences(
            ()->new com.hippocampus.identity.domain.AuthenticatedUser(seed.owner()),repository));
        var candidates=new com.hippocampus.rag.application.HybridCandidateMerger().merge(hits,List.of(),10);
        var evidence=new com.hippocampus.rag.application.BuildEvidencePackage(visuals).execute(new com.hippocampus.rag.application.BuildEvidencePackage.Command(
            scope,candidates,com.hippocampus.rag.domain.RetrievalQuality.STRONG,new com.hippocampus.rag.domain.EvidencePackageBudget(3,3),binding));
        var refs=write.execute(s->materializer.execute(evidence,binding));
        assertEquals(2,refs.size());assertTrue(refs.stream().allMatch(r->r.materialVersionId().equals(seed.version())));
        assertEquals(refs,write.execute(s->materializer.execute(evidence,binding)));
        assertEquals(0,jdbc.sql("SELECT count(*) FROM learning_activities").query(Integer.class).single());
        assertEquals(com.hippocampus.learning.port.StudyMissionStartReceiptRepository.State.CLAIMED,receipts.findByMission(seed.owner(),seed.mission()).orElseThrow().state());
    }

    @Test void forgedClaimsOwnersMissionsVersionsAndLostSourcesFailBeforeRankingOrProvenance() {
        var claim=claimWithFrozenScope();
        var valid=new com.hippocampus.rag.port.FrozenMissionSourceBinding(seed.mission(),claim.generation(),claim.claimToken());
        var lexical=new com.hippocampus.rag.infrastructure.persistence.JdbcLexicalSearchRepository(jdbc,new tools.jackson.databind.ObjectMapper());
        for(var forged:List.of(new com.hippocampus.rag.port.FrozenMissionSourceBinding(seed.mission(),claim.generation(),UUID.randomUUID()),
                new com.hippocampus.rag.port.FrozenMissionSourceBinding(seed.mission(),claim.generation()+1,claim.claimToken()),
                new com.hippocampus.rag.port.FrozenMissionSourceBinding(UUID.randomUUID(),claim.generation(),claim.claimToken()))) {
            assertTrue(lexical.search(new com.hippocampus.rag.port.LexicalSearchRequest(frozenScope(seed.owner(),seed.version()),"Synthetic",10,forged)).isEmpty());
            assertThrows(RuntimeException.class,()->write.execute(s->new JdbcSourceReferenceRepository(jdbc).createInitialMissionBatch(seed.owner(),
                new com.hippocampus.materials.port.SourceReferenceRepository.FrozenMissionBinding(forged.missionId(),forged.generation(),forged.claimToken()),
                List.of(new com.hippocampus.materials.domain.ChunkSourceTarget(seed.material(),seed.version(),seed.chunk())))));
        }
        assertTrue(lexical.search(new com.hippocampus.rag.port.LexicalSearchRequest(frozenScope(UUID.randomUUID(),seed.version()),"Synthetic",10,valid)).isEmpty());
        assertTrue(lexical.search(new com.hippocampus.rag.port.LexicalSearchRequest(frozenScope(seed.owner(),UUID.randomUUID()),"Synthetic",10,valid)).isEmpty());
        jdbc.sql("UPDATE chunks SET is_active=false WHERE id=:id").param("id",seed.chunk()).update();
        assertTrue(lexical.search(new com.hippocampus.rag.port.LexicalSearchRequest(frozenScope(seed.owner(),seed.version()),"Synthetic",10,valid)).isEmpty());
        assertThrows(RuntimeException.class,()->write.execute(s->new JdbcSourceReferenceRepository(jdbc).createInitialMissionBatch(seed.owner(),
            new com.hippocampus.materials.port.SourceReferenceRepository.FrozenMissionBinding(valid.missionId(),valid.generation(),valid.claimToken()),
            List.of(new com.hippocampus.materials.domain.ChunkSourceTarget(seed.material(),seed.version(),seed.chunk())))));
        assertEquals(0,jdbc.sql("SELECT count(*) FROM source_references").query(Integer.class).single());
    }

    @Test void provenanceRollsBackWholeBatchAndHonorsNowaitBeforeAnyReferenceInsert() throws Exception {
        var claim=claimWithFrozenScope();
        var binding=new com.hippocampus.materials.port.SourceReferenceRepository.FrozenMissionBinding(seed.mission(),claim.generation(),claim.claimToken());
        var repository=new JdbcSourceReferenceRepository(jdbc);
        var target=new com.hippocampus.materials.domain.ChunkSourceTarget(seed.material(),seed.version(),seed.chunk());
        assertThrows(IllegalStateException.class,()->write.executeWithoutResult(s->{repository.createInitialMissionBatch(seed.owner(),binding,List.of(target));throw new IllegalStateException("rollback");}));
        assertEquals(0,jdbc.sql("SELECT count(*) FROM source_references").query(Integer.class).single());
        try(var held=dataSource.getConnection()) {
            held.setAutoCommit(false);
            try(var q=held.prepareStatement("SELECT id FROM chunks WHERE id=? FOR UPDATE")){q.setObject(1,seed.chunk());q.executeQuery().close();}
            var failure=assertThrows(org.springframework.dao.DataAccessException.class,()->write.execute(s->repository.createInitialMissionBatch(seed.owner(),binding,List.of(target))));
            Throwable cause=failure;while(cause.getCause()!=null)cause=cause.getCause();
            assertEquals("55P03",((java.sql.SQLException)cause).getSQLState());held.rollback();
        }
        assertEquals(0,jdbc.sql("SELECT count(*) FROM source_references").query(Integer.class).single());
    }

    @Test void selectedReferenceLossDeniesProviderExposureAndFinalAdmissionDespiteOtherAvailableText() {
        var claim=claimWithFrozenScope();
        var binding=new com.hippocampus.materials.port.SourceReferenceRepository.FrozenMissionBinding(seed.mission(),claim.generation(),claim.claimToken());
        var reference=write.execute(s->new JdbcSourceReferenceRepository(jdbc).createInitialMissionBatch(seed.owner(),binding,
            List.of(new com.hippocampus.materials.domain.ChunkSourceTarget(seed.material(),seed.version(),seed.chunk())))).getFirst();
        var scopes=List.of(new MissionMaterial(UUID.randomUUID(),seed.material(),seed.version(),null));
        var authority=new JdbcActivitySourceReferenceAuthorization(jdbc);
        assertFalse(authority.inspectForExposure(seed.owner(),scopes,java.util.Set.of(reference.sourceReferenceId())).isEmpty());
        jdbc.sql("INSERT INTO chunks(id,material_version_id,chunk_index,content,content_type,extraction_method,quality,created_at) VALUES (:id,:version,2,'Other valid synthetic text','TEXT','NATIVE','STRONG',now())")
            .param("id",UUID.randomUUID()).param("version",seed.version()).update();
        jdbc.sql("UPDATE chunks SET is_active=false WHERE id=:id").param("id",seed.chunk()).update();
        assertTrue(authority.inspect(seed.owner(),scopes).getFirst().groundedTextAvailable());
        assertThrows(RuntimeException.class,()->authority.inspectForExposure(seed.owner(),scopes,java.util.Set.of(reference.sourceReferenceId())));
        assertThrows(RuntimeException.class,()->write.execute(s->{lock(seed.mission());
            return authority.lockAndInspect(seed.owner(),scopes,java.util.Set.of(reference.sourceReferenceId()),true);}));
        assertEquals(0,jdbc.sql("SELECT count(*) FROM learning_activities").query(Integer.class).single());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"chunk-node","visual-node","visual-status"})
    void changedInitialTargetAssociationFailsExposureAndFinalSourceGate(String scenario) {
        var claim=claimWithFrozenScope();UUID node=UUID.randomUUID(),other=UUID.randomUUID(),visual=UUID.randomUUID();
        jdbc.sql("INSERT INTO document_nodes(id,material_version_id,node_type,detection_origin,created_at) VALUES (:id,:version,'DOCUMENT','NATIVE',now())")
            .param("id",node).param("version",seed.version()).update();
        jdbc.sql("INSERT INTO document_nodes(id,material_version_id,parent_id,node_type,detection_origin,created_at) VALUES (:id,:version,:parent,'SECTION','NATIVE',now())")
            .param("id",other).param("version",seed.version()).param("parent",node).update();
        jdbc.sql("UPDATE chunks SET document_node_id=:node WHERE id=:id").param("node",node).param("id",seed.chunk()).update();
        jdbc.sql("INSERT INTO visual_assets(id,material_version_id,document_node_id,page_number,storage_key,visual_type,interpretation_status,content_hash,created_at) VALUES (:id,:version,:node,1,:key,'FLOW_DIAGRAM','SUPPORTED',:hash,now())")
            .param("id",visual).param("version",seed.version()).param("node",node).param("key","private/"+visual).param("hash",visual.toString()).update();
        var binding=new com.hippocampus.materials.port.SourceReferenceRepository.FrozenMissionBinding(seed.mission(),claim.generation(),claim.claimToken());
        com.hippocampus.materials.domain.SourceReferenceTarget target=scenario.equals("chunk-node")
            ?new com.hippocampus.materials.domain.ChunkSourceTarget(seed.material(),seed.version(),seed.chunk())
            :new com.hippocampus.materials.domain.VisualSourceTarget(seed.material(),seed.version(),visual);
        var reference=write.execute(s->new JdbcSourceReferenceRepository(jdbc).createInitialMissionBatch(seed.owner(),binding,List.of(target))).getFirst();
        var scopes=List.of(new MissionMaterial(UUID.randomUUID(),seed.material(),seed.version(),null));
        var authority=new JdbcActivitySourceReferenceAuthorization(jdbc);
        assertFalse(authority.inspectForExposure(seed.owner(),scopes,java.util.Set.of(reference.sourceReferenceId())).isEmpty());
        switch(scenario) {
            case "chunk-node" -> jdbc.sql("UPDATE chunks SET document_node_id=:node WHERE id=:id").param("node",other).param("id",seed.chunk()).update();
            case "visual-node" -> jdbc.sql("UPDATE visual_assets SET document_node_id=:node WHERE id=:id").param("node",other).param("id",visual).update();
            case "visual-status" -> jdbc.sql("UPDATE visual_assets SET interpretation_status='UNSUPPORTED' WHERE id=:id").param("id",visual).update();
            default -> throw new AssertionError(scenario);
        }
        assertThrows(RuntimeException.class,()->authority.inspectForExposure(seed.owner(),scopes,java.util.Set.of(reference.sourceReferenceId())));
        assertThrows(RuntimeException.class,()->write.execute(s->{lock(seed.mission());return authority.lockAndInspect(seed.owner(),scopes,java.util.Set.of(reference.sourceReferenceId()),true);}));
        assertEquals(0,jdbc.sql("SELECT count(*) FROM learning_activities").query(Integer.class).single());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"forged-token","forged-generation","foreign-owner","foreign-mission","expired-work","superseded","paused","stopped","deleted","inactive-chunk","invalid-node"})
    void everyRetrievalChannelAndProvenanceDenyInvalidInitialAuthority(String scenario) {
        var claim=claimWithFrozenScope(scenario.equals("expired-work")?151:0);
        UUID generation=UUID.randomUUID(),visual=UUID.randomUUID();
        jdbc.sql("INSERT INTO index_generations(id,embedding_provider,embedding_model,embedding_dimension,chunking_version,status,created_at) VALUES (:id,'TEST','synthetic',2,'CHUNKER_V1','ACTIVE',now())").param("id",generation).update();
        jdbc.sql("INSERT INTO chunk_embeddings(id,chunk_id,index_generation_id,embedding,created_at) VALUES (:id,:chunk,:generation,'[1,0]'::vector,now())")
            .param("id",UUID.randomUUID()).param("chunk",seed.chunk()).param("generation",generation).update();
        jdbc.sql("INSERT INTO visual_assets(id,material_version_id,page_number,storage_key,visual_type,interpretation_status,content_hash,created_at) VALUES (:id,:version,1,:key,'FLOW_DIAGRAM','SUPPORTED',:hash,now())")
            .param("id",visual).param("version",seed.version()).param("key","private/"+visual).param("hash",visual.toString()).update();
        jdbc.sql("INSERT INTO chunk_visual_links(chunk_id,visual_asset_id,material_version_id,relationship_type) VALUES (:chunk,:visual,:version,'NEARBY')")
            .param("chunk",seed.chunk()).param("visual",visual).param("version",seed.version()).update();
        UUID owner=seed.owner(),mission=seed.mission(),token=claim.claimToken();long claimGeneration=claim.generation();
        switch(scenario) {
            case "forged-token" -> token=UUID.randomUUID();
            case "forged-generation" -> claimGeneration++;
            case "foreign-owner" -> owner=UUID.randomUUID();
            case "foreign-mission" -> mission=UUID.randomUUID();
            case "superseded" -> write.executeWithoutResult(s->{lock(seed.mission());receipts.replace(claim,
                claim.close(claim.leaseExpiresAt(),com.hippocampus.learning.port.StudyMissionStartReceiptRepository.Outcome.LEASE_EXPIRED,
                    com.hippocampus.learning.port.StudyMissionStartReceiptRepository.Reason.PREPARATION_TIMEOUT,null)
                .claim(UUID.randomUUID(),snapshot(seed),action(seed),claim.leaseExpiresAt()));});
            case "paused","stopped" -> jdbc.sql("UPDATE study_missions SET status=:status,updated_at=clock_timestamp() WHERE id=:id")
                .param("status",scenario.equals("paused")?"PAUSED":"STOPPED").param("id",seed.mission()).update();
            case "deleted" -> jdbc.sql("UPDATE materials SET status='DELETED' WHERE id=:id").param("id",seed.material()).update();
            case "inactive-chunk" -> jdbc.sql("UPDATE chunks SET is_active=false WHERE id=:id").param("id",seed.chunk()).update();
            case "invalid-node" -> {
                UUID node=UUID.randomUUID();
                jdbc.sql("INSERT INTO document_nodes(id,material_version_id,node_type,detection_origin,created_at) VALUES (:id,:version,'DOCUMENT','NATIVE',now())")
                    .param("id",node).param("version",seed.version()).update();
                jdbc.sql("UPDATE mission_materials SET document_node_id=:node WHERE study_mission_id=:id").param("node",node).param("id",seed.mission()).update();
            }
            default -> { }
        }
        var binding=new com.hippocampus.rag.port.FrozenMissionSourceBinding(mission,claimGeneration,token);
        var scope=frozenScope(owner,seed.version());
        assertTrue(new com.hippocampus.rag.infrastructure.persistence.JdbcLexicalSearchRepository(jdbc,new tools.jackson.databind.ObjectMapper())
            .search(new com.hippocampus.rag.port.LexicalSearchRequest(scope,"Synthetic",10,binding)).isEmpty(),scenario+" lexical");
        assertTrue(new com.hippocampus.rag.infrastructure.persistence.JdbcVectorSearchRepository(jdbc,new tools.jackson.databind.ObjectMapper())
            .search(new com.hippocampus.rag.port.VectorSearchRequest(scope,generation,new com.hippocampus.rag.port.EmbeddingVector(List.of(1F,0F)),10,binding)).isEmpty(),scenario+" vector");
        assertTrue(new com.hippocampus.rag.infrastructure.persistence.JdbcEvidenceVisualRepository(jdbc)
            .findLinkedVisuals(scope,java.util.Set.of(seed.chunk()),binding).isEmpty(),scenario+" visual");
        UUID actualOwner=owner;
        var materialBinding=new com.hippocampus.materials.port.SourceReferenceRepository.FrozenMissionBinding(binding.missionId(),binding.generation(),binding.claimToken());
        assertThrows(RuntimeException.class,()->write.execute(s->new JdbcSourceReferenceRepository(jdbc).createInitialMissionBatch(actualOwner,materialBinding,
            List.of(new com.hippocampus.materials.domain.ChunkSourceTarget(seed.material(),seed.version(),seed.chunk()),
                new com.hippocampus.materials.domain.VisualSourceTarget(seed.material(),seed.version(),visual)))),scenario+" provenance");
        assertEquals(0,jdbc.sql("SELECT count(*) FROM source_references").query(Integer.class).single());
        assertEquals(0,jdbc.sql("SELECT count(*) FROM learning_activities").query(Integer.class).single());
    }

    private com.hippocampus.learning.port.StudyMissionStartReceiptRepository.Receipt claimWithFrozenScope() {
        return claimWithFrozenScope(0);
    }
    private com.hippocampus.learning.port.StudyMissionStartReceiptRepository.Receipt claimWithFrozenScope(int ageSeconds) {
        jdbc.sql("INSERT INTO mission_materials(id,study_mission_id,material_id,material_version_id) VALUES (:id,:mission,:material,:version)")
            .param("id",UUID.randomUUID()).param("mission",seed.mission()).param("material",seed.material()).param("version",seed.version()).update();
        var created=receipts.databaseTime().minusSeconds(ageSeconds);
        var ready=com.hippocampus.learning.port.StudyMissionStartReceiptRepository.Receipt.ready(seed.owner(),UUID.randomUUID(),"a".repeat(64),seed.mission(),seed.objective(),"b".repeat(64),"c".repeat(64),created);
        return write.execute(s->{lock(seed.mission());receipts.insert(ready);var claimed=ready.claim(UUID.randomUUID(),snapshot(seed),action(seed),created);receipts.replace(ready,claimed);return claimed;});
    }
    private com.hippocampus.rag.domain.RetrievalScope frozenScope(UUID owner,UUID version) {
        return new com.hippocampus.rag.domain.RetrievalScope(owner,seed.topic(),com.hippocampus.rag.domain.GroundingMode.STRICT_SOURCE,
            List.of(new com.hippocampus.rag.domain.RetrievalScopeTarget(version,java.util.Set.of())));
    }

    @Test void selectorAndAtomicFreezeUseTheSameOwnedWholeMaterialPredicate() {
        var catalog=new JdbcStudyMissionSourceCatalog(jdbc);
        assertEquals(seed.material(),catalog.eligible(seed.owner(),seed.topic(),20,null).items().getFirst().materialId());
        assertTrue(catalog.eligible(UUID.randomUUID(),seed.topic(),20,null).items().isEmpty());
        var frozen=write.execute(s->{receipts.configureTransactionLimits();
            return catalog.resolveLocked(seed.owner(),seed.topic(),List.of(new SourceSelection(seed.material(),null))).orElseThrow();});
        assertEquals(seed.version(),frozen.sources().getFirst().materialVersionId());
        UUID next=UUID.randomUUID();
        jdbc.sql("INSERT INTO material_versions(id,material_id,version_number,processing_status,created_at) VALUES (:id,:material,2,'READY',now())")
                .param("id",next).param("material",seed.material()).update();
        jdbc.sql("UPDATE materials SET active_version_id=:next WHERE id=:material").param("next",next).param("material",seed.material()).update();
        jdbc.sql("UPDATE material_topic_links SET status='ARCHIVED' WHERE material_id=:material").param("material",seed.material()).update();
        var scopes=List.of(new MissionMaterial(UUID.randomUUID(),seed.material(),seed.version(),null));
        var authority=new JdbcActivitySourceReferenceAuthorization(jdbc);
        assertEquals(seed.version(),authority.inspect(seed.owner(),scopes).getFirst().frozenVersionId());
        assertThrows(RuntimeException.class,()->authority.inspect(UUID.randomUUID(),scopes));
        jdbc.sql("UPDATE materials SET status='DELETED' WHERE id=:material").param("material",seed.material()).update();
        assertThrows(RuntimeException.class,()->authority.inspect(seed.owner(),scopes));
    }

    @Test void sourceFreezePreservesNowaitUnderACompetingWriter() throws Exception {
        var catalog=new JdbcStudyMissionSourceCatalog(jdbc);
        try(var held=dataSource.getConnection()) {
            held.setAutoCommit(false);
            try(var query=held.prepareStatement("SELECT id FROM material_versions WHERE id=? FOR UPDATE")) {
                query.setObject(1,seed.version());query.executeQuery().close();
            }
            var failure=assertThrows(org.springframework.dao.DataAccessException.class,()->write.execute(s->{
                receipts.configureTransactionLimits();return catalog.resolveLocked(seed.owner(),seed.topic(),List.of(new SourceSelection(seed.material(),null)));}));
            Throwable root=failure;while(root.getCause()!=null)root=root.getCause();
            assertInstanceOf(java.sql.SQLException.class,root);assertEquals("55P03",((java.sql.SQLException)root).getSQLState());
            held.rollback();
        }
    }
}
