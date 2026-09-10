package com.hippocampus.materials.infrastructure.persistence;

import static org.assertj.core.api.Assertions.*;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.hippocampus.testing.PostgresIntegrationTestSupport;
import com.hippocampus.materials.application.*;
import com.hippocampus.materials.domain.*;
import com.hippocampus.materials.port.MaterialReadinessRepository;

class MaterialReadinessIntegrationTests extends PostgresIntegrationTestSupport {
    private ConfigurableApplicationContext context;
    private JdbcClient jdbc;
    private TransactionTemplate tx;
    @BeforeEach void start() throws Exception {
        resetPostgresSchema(); context=startApplicationWithFlyway(); jdbc=context.getBean(JdbcClient.class);
        tx=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    }
    @AfterEach void close() { if(context!=null) context.close(); }

    @Test void firstClaimRetryAndTerminalFailureAreAtomicLifecycleTransitions() {
        Fixture f=fixture(null); ClaimedProcessingJob job=claim();
        assertState(f,"PROCESSING","PROCESSING");
        fail(job,ProcessingFailure.Kind.TRANSIENT);
        assertState(f,"PROCESSING","PROCESSING"); assertThat(jobStatus(job.jobId())).isEqualTo("RETRY");
        jdbc.sql("UPDATE processing_jobs SET next_attempt_at=CURRENT_TIMESTAMP WHERE id=?").param(job.jobId()).update();
        job=claim(); fail(job,ProcessingFailure.Kind.FATAL);
        assertState(f,"FAILED","FAILED"); assertUnactivated(f);
    }
    @ParameterizedTest @ValueSource(strings={"READY","PARTIALLY_READY"})
    void failedCandidateRestoresUsableActiveStatus(String active) {
        Fixture f=fixture(active); ClaimedProcessingJob job=claim();
        assertState(f,"PROCESSING","PROCESSING");
        fail(job,ProcessingFailure.Kind.FATAL); assertState(f,"FAILED",active);
        assertThat(jdbc.sql("SELECT active_version_id FROM materials WHERE id=?").param(f.material()).query(UUID.class).single()).isEqualTo(f.active());
        assertThat(jdbc.sql("SELECT activated_at FROM material_versions WHERE id=?").param(f.version()).query((r,n)->r.getObject(1)).list().getFirst()).isNull();
    }
    @Test void wrongWorkerStaleAttemptAndForgedVersionCannotMutateLifecycle() {
        Fixture f=fixture(null); ClaimedProcessingJob old=claim();
        ClaimedProcessingJob wrong=new ClaimedProcessingJob(old.jobId(),old.jobType(),old.materialVersionId(),"v1","wrong",1,3);
        assertThatThrownBy(()->fail(wrong,ProcessingFailure.Kind.FATAL)).isInstanceOf(ProcessingJobOwnershipLostException.class);
        jdbc.sql("UPDATE processing_jobs SET last_heartbeat_at=CURRENT_TIMESTAMP-interval '2 minutes' WHERE id=?").param(old.jobId()).update();
        ClaimedProcessingJob current=claim(); // same worker name, new attempt
        assertThat(current.attemptNumber()).isEqualTo(2);
        assertThatThrownBy(()->fail(old,ProcessingFailure.Kind.FATAL)).isInstanceOf(ProcessingJobOwnershipLostException.class);
        assertThatThrownBy(()->complete(old)).isInstanceOf(ProcessingStageCompletionException.class);
        Fixture foreign=fixture(null);
        ClaimedProcessingJob forged=new ClaimedProcessingJob(current.jobId(),current.jobType(),foreign.version(),"v1",current.workerId(),2,3);
        assertThatThrownBy(()->fail(forged,ProcessingFailure.Kind.FATAL)).isInstanceOf(ProcessingJobOwnershipLostException.class);
        assertThatThrownBy(()->complete(forged)).isInstanceOf(ProcessingStageCompletionException.class);
        assertState(f,"PROCESSING","PROCESSING"); assertState(foreign,"UPLOADED","UPLOADED");
    }
    @Test void exhaustedCleanupDerivesEvenWithoutReplacementClaim() {
        Fixture f=fixture(null); ClaimedProcessingJob job=claim();
        exhaust(job);
        assertThat(context.getBean(ClaimNextProcessingJob.class).execute("worker")).isEmpty();
        assertState(f,"FAILED","FAILED"); assertThat(jobStatus(job.jobId())).isEqualTo("FAILED");
    }
    @Test void lockedExhaustedJobDoesNotBlockAnotherClaim() throws Exception {
        Fixture first=fixture(null); ClaimedProcessingJob exhausted=claim(); exhaust(exhausted);
        Fixture second=fixture(null);
        try(var connection=openPostgresConnection(); var executor=Executors.newSingleThreadExecutor()) {
            connection.setAutoCommit(false);
            try(var lock=connection.prepareStatement("SELECT id FROM processing_jobs WHERE id=? FOR UPDATE")) {
                lock.setObject(1,exhausted.jobId()); lock.executeQuery();
                var result=executor.submit(()->claim()).get(10,TimeUnit.SECONDS);
                assertThat(result.materialVersionId()).isEqualTo(second.version());
                assertState(first,"PROCESSING","PROCESSING");
            } finally { connection.rollback(); }
        }
        assertThat(context.getBean(ClaimNextProcessingJob.class).execute("worker")).isEmpty();
        assertState(first,"FAILED","FAILED");
    }
    @Test void lifecycleWriteFailureRollsBackJobTransition() {
        Fixture f=fixture(null); ClaimedProcessingJob job=claim();
        jdbc.sql("CREATE FUNCTION reject_readiness() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test rollback'; END $$").update();
        jdbc.sql("CREATE TRIGGER reject_readiness BEFORE UPDATE ON material_versions FOR EACH ROW EXECUTE FUNCTION reject_readiness()").update();
        assertThatThrownBy(()->fail(job,ProcessingFailure.Kind.FATAL)).isInstanceOf(RuntimeException.class);
        assertThat(jobStatus(job.jobId())).isEqualTo("RUNNING"); assertState(f,"PROCESSING","PROCESSING");
    }
    @Test void deletionRacingLateFailureCannotResurrectMaterial() throws Exception {
        Fixture f=fixture("READY"); ClaimedProcessingJob job=claim();
        try(var connection=openPostgresConnection(); var executor=Executors.newSingleThreadExecutor()) {
            connection.setAutoCommit(false);
            try(var deletion=connection.prepareStatement("UPDATE materials SET status='DELETED',active_version_id=NULL WHERE id=?")) {
                deletion.setObject(1,f.material()); deletion.executeUpdate();
                var entered=new java.util.concurrent.CountDownLatch(1);
                var late=executor.submit(()->{entered.countDown();fail(job,ProcessingFailure.Kind.FATAL);});
                assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
                connection.commit(); late.get(10,TimeUnit.SECONDS);
            } finally {connection.rollback();}
        }
        assertState(f,"PROCESSING","DELETED"); assertUnactivated(f);
    }
    @Test void duplicateCompletionAndRepeatedDerivationAreNoOps() {
        Fixture f=fixture(null); ClaimedProcessingJob job=claim(); complete(job);
        Object updated=jdbc.sql("SELECT updated_at FROM materials WHERE id=?").param(f.material()).query((r,n)->r.getObject(1)).list().getFirst();
        assertThatThrownBy(()->complete(job)).isInstanceOf(ProcessingStageCompletionException.class);
        tx.executeWithoutResult(s->context.getBean(DeriveMaterialReadiness.class).execute(job.jobId()));
        assertThat(jdbc.sql("SELECT updated_at FROM materials WHERE id=?").param(f.material()).query((r,n)->r.getObject(1)).list().getFirst()).isEqualTo(updated);
        assertThat(jdbc.sql("SELECT count(*) FROM processing_jobs WHERE material_version_id=?").param(f.version()).query(Integer.class).single()).isEqualTo(2);
    }
    @Test void crossOwnerJobCannotChangeLifecycleThroughRecovery() {
        Fixture f=fixture(null); Fixture foreign=fixture(null);
        jdbc.sql("UPDATE processing_jobs SET user_id=?,status='RUNNING',attempt_count=3,locked_at=CURRENT_TIMESTAMP-interval '2 minutes' WHERE id=?")
            .param(foreign.user()).param(f.job()).update();
        context.getBean(ClaimNextProcessingJob.class).execute("worker");
        assertState(f,"UPLOADED","UPLOADED");
    }
    @ParameterizedTest @ValueSource(strings={"NATIVE","STRONG","LIMITED","POOR","MISSING_LINK","NO_CHUNK"})
    void completedP3UsesDurableEvidenceWithoutInventingIndex(String quality) {
        Fixture f=fixture(null);
        jdbc.sql("UPDATE processing_jobs SET status='COMPLETED' WHERE id=?").param(f.job()).update();
        for(String stage:new String[]{"MATERIAL_EXTRACT","STRUCTURE_DETECT","VISUAL_EXTRACT","NORMALIZE"}) job(f,stage,"COMPLETED");
        UUID chunkJob=job(f,"CHUNK","PENDING");
        evidence(f,quality);
        complete(claim());
        boolean failure=quality.equals("POOR")||quality.equals("MISSING_LINK")||quality.equals("NO_CHUNK");
        assertState(f,failure?"FAILED":"PROCESSING",failure?"FAILED":"PROCESSING"); assertUnactivated(f);
        assertThat(jdbc.sql("SELECT count(*) FROM processing_jobs WHERE job_type='EMBED'").query(Integer.class).single()).isZero();
        var snapshot=tx.execute(s->context.getBean(MaterialReadinessRepository.class).lockAndRead(chunkJob).orElseThrow());
        assertThat(snapshot.facts().index()).isEqualTo(MaterialReadiness.IndexPrerequisite.ABSENT);
        if(quality.equals("LIMITED")) assertThat(snapshot.facts().limitedEvidence()).isTrue();
        if(quality.equals("NATIVE")) assertThat(snapshot.facts().limitedEvidence()).isFalse();
        assertThat(jdbc.sql("SELECT processing_progress FROM material_versions WHERE id=?").param(f.version()).query((r,n)->r.getObject(1)).list().getFirst()).isNull();
    }
    @ParameterizedTest @ValueSource(strings={"UNASSESSED","LIMITED","FAILED","UNSUPPORTED","TABLE","MIXED_POOR"})
    void preservesDurableOptionalLimitationsWithoutInferringFromAbsence(String kind) {
        Fixture f=fixture(null);
        jdbc.sql("UPDATE processing_jobs SET status='COMPLETED' WHERE id=?").param(f.job()).update();
        for(String stage:new String[]{"MATERIAL_EXTRACT","STRUCTURE_DETECT","VISUAL_EXTRACT","NORMALIZE"}) job(f,stage,"COMPLETED");
        UUID chunkJob=job(f,"CHUNK","PENDING"); evidence(f,"NATIVE");
        UUID root=jdbc.sql("SELECT id FROM document_nodes WHERE material_version_id=?").param(f.version()).query(UUID.class).single();
        if(kind.equals("TABLE")||kind.equals("MIXED_POOR")) {
            jdbc.sql("INSERT INTO text_blocks(id,material_version_id,document_node_id,page_number,block_type,ordinal,content,normalized_content,extraction_method,quality,created_at) VALUES (?,?,?,1,'TABLE_TEXT',2,'Limited table','Limited table',?,?,CURRENT_TIMESTAMP)")
                .param(UUID.randomUUID()).param(f.version()).param(root).param(kind.equals("TABLE")?"NATIVE":"OCR")
                .param(kind.equals("TABLE")?"LIMITED":"POOR").update();
        } else {
            jdbc.sql("INSERT INTO visual_assets(id,material_version_id,document_node_id,page_number,storage_key,visual_type,interpretation_status,content_hash,created_at) VALUES (?,?,?,1,?,'OTHER',?,'hash',CURRENT_TIMESTAMP)")
                .param(UUID.randomUUID()).param(f.version()).param(root).param(UUID.randomUUID().toString()).param(kind).update();
        }
        complete(claim()); assertState(f,"PROCESSING","PROCESSING");
        var snapshot=tx.execute(t->context.getBean(MaterialReadinessRepository.class).lockAndRead(chunkJob).orElseThrow());
        boolean limited=!kind.equals("UNASSESSED");
        assertThat(snapshot.facts().limitedEvidence()).isEqualTo(limited);
        var facts=snapshot.facts();
        // Satisfied index exists only in the pure policy test, never in production persistence.
        var indexed=new MaterialReadiness.Facts(facts.started(),facts.requiredStageFailed(),facts.requiredStagesCompleted(),
            facts.provenanceValid(),facts.usableEvidence(),facts.limitedEvidence(),MaterialReadiness.IndexPrerequisite.SATISFIED);
        assertThat(MaterialReadiness.derive(indexed)).isEqualTo(limited?MaterialReadiness.State.PARTIALLY_READY:MaterialReadiness.State.READY);
        if(kind.equals("TABLE")||kind.equals("MIXED_POOR")) {
            assertThat(jdbc.sql("SELECT quality FROM text_blocks WHERE material_version_id=? AND ordinal=2").param(f.version()).query(String.class).single())
                .isEqualTo(kind.equals("TABLE")?"LIMITED":"POOR");
        } else assertThat(jdbc.sql("SELECT interpretation_status FROM visual_assets WHERE material_version_id=?").param(f.version()).query(String.class).single()).isEqualTo(kind);
    }

    @Test void unrelatedJobDoesNotChangeMaterialLifecycle() {
        Fixture f=fixture(null);
        jdbc.sql("UPDATE processing_jobs SET job_type='CLEANUP' WHERE id=?").param(f.job()).update();
        claim(); assertState(f,"UPLOADED","UPLOADED");
    }

    private Fixture fixture(String activeStatus) {
        UUID user=UUID.randomUUID(), material=UUID.randomUUID(), version=UUID.randomUUID(), active=activeStatus==null?null:UUID.randomUUID();
        jdbc.sql("INSERT INTO users(id,email,status,created_at,updated_at) VALUES (?,?,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)").param(user).param(user+"@example.test").update();
        jdbc.sql("INSERT INTO materials(id,user_id,title,material_type,status,created_at,updated_at) VALUES (?,?,'Readiness','PDF',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)").param(material).param(user).param(activeStatus==null?"UPLOADED":activeStatus).update();
        if(active!=null) {
            jdbc.sql("INSERT INTO material_versions(id,material_id,version_number,processing_status,created_at) VALUES (?,?,1,?,CURRENT_TIMESTAMP)").param(active).param(material).param(activeStatus).update();
            jdbc.sql("UPDATE materials SET active_version_id=? WHERE id=?").param(active).param(material).update();
        }
        jdbc.sql("INSERT INTO material_versions(id,material_id,version_number,processing_status,storage_key,file_size_bytes,page_count,created_at) VALUES (?,?,?,'UPLOADED','source',100,1,CURRENT_TIMESTAMP)").param(version).param(material).param(active==null?1:2).update();
        Fixture f=new Fixture(user,material,version,active,null);
        return new Fixture(user,material,version,active,job(f,"MATERIAL_VALIDATE","PENDING"));
    }
    private UUID job(Fixture f,String type,String status) {
        UUID id=UUID.randomUUID();
        jdbc.sql("INSERT INTO processing_jobs(id,user_id,material_version_id,job_type,status,priority,attempt_count,max_attempts,processing_version,created_at,updated_at) VALUES (?,?,?,?,?,0,0,3,'v1',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")
            .param(id).param(f.user()).param(f.version()).param(type).param(status).update();return id;
    }
    private void evidence(Fixture f,String kind) {
        UUID root=UUID.randomUUID(),text=UUID.randomUUID(),chunk=UUID.randomUUID();
        String method=kind.equals("NATIVE")?"NATIVE":"OCR";
        String quality=kind.equals("NATIVE")?null:kind.equals("LIMITED")?"LIMITED":kind.equals("POOR")?"POOR":"STRONG";
        jdbc.sql("INSERT INTO document_nodes(id,material_version_id,node_type,title,start_page,end_page,detection_origin,created_at) VALUES (?,?,'DOCUMENT','Source',1,1,'NATIVE',CURRENT_TIMESTAMP)").param(root).param(f.version()).update();
        jdbc.sql("INSERT INTO text_blocks(id,material_version_id,document_node_id,page_number,block_type,ordinal,content,normalized_content,extraction_method,quality,created_at) VALUES (?,?,?,1,'PAGE_TEXT',1,'Evidence','Evidence',?,?,CURRENT_TIMESTAMP)")
            .param(text).param(f.version()).param(root).param(method).param(5,quality,java.sql.Types.VARCHAR).update();
        if(kind.equals("NO_CHUNK"))return;
        jdbc.sql("INSERT INTO chunks(id,material_version_id,document_node_id,chunk_index,content,page_start,page_end,content_type,extraction_method,quality,created_at) VALUES (?,?,?,1,'Evidence',1,1,'TEXT',?,?,CURRENT_TIMESTAMP)")
            .param(chunk).param(f.version()).param(root).param(method).param(5,quality,java.sql.Types.VARCHAR).update();
        if(!kind.equals("MISSING_LINK")) jdbc.sql("INSERT INTO chunk_text_block_links(chunk_id,text_block_id,material_version_id,source_position) VALUES (?,?,?,1)").param(chunk).param(text).param(f.version()).update();
    }
    private ClaimedProcessingJob claim(){return context.getBean(ClaimNextProcessingJob.class).execute("worker").orElseThrow();}
    private void fail(ClaimedProcessingJob job,ProcessingFailure.Kind kind){context.getBean(FinalizeProcessingFailure.class).execute(job,new ProcessingFailure(kind,"EXTRACTION_FAILED"));}
    private void complete(ClaimedProcessingJob job){context.getBean(CompleteProcessingStage.class).execute(job,new ProcessingStageResult(job.jobType(),null));}
    private void exhaust(ClaimedProcessingJob job){jdbc.sql("UPDATE processing_jobs SET attempt_count=3,last_heartbeat_at=CURRENT_TIMESTAMP-interval '2 minutes' WHERE id=?").param(job.jobId()).update();}
    private String jobStatus(UUID id){return jdbc.sql("SELECT status FROM processing_jobs WHERE id=?").param(id).query(String.class).single();}
    private void assertState(Fixture f,String version,String material){
        assertThat(jdbc.sql("SELECT processing_status FROM material_versions WHERE id=?").param(f.version()).query(String.class).single()).isEqualTo(version);
        assertThat(jdbc.sql("SELECT status FROM materials WHERE id=?").param(f.material()).query(String.class).single()).isEqualTo(material);
    }
    private void assertUnactivated(Fixture f){
        assertThat(jdbc.sql("SELECT active_version_id FROM materials WHERE id=?").param(f.material()).query((r,n)->r.getObject(1)).list().getFirst()).isNull();
        assertThat(jdbc.sql("SELECT activated_at FROM material_versions WHERE id=?").param(f.version()).query((r,n)->r.getObject(1)).list().getFirst()).isNull();
    }
    private record Fixture(UUID user,UUID material,UUID version,UUID active,UUID job){}
}
