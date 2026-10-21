package com.hippocampus.learning.infrastructure.persistence;

import com.hippocampus.learning.domain.*;
import com.hippocampus.learning.port.StudyMissionStartReceiptRepository.*;
import com.hippocampus.testing.PostgresIntegrationTestSupport;
import java.sql.Connection;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

/** Operator/CI only: real PostgreSQL constraints, JSON round trips and rollback, never an agent-run target. */
public class StudyMissionStartPersistenceIntegrationTests extends PostgresIntegrationTestSupport {
    protected DriverManagerDataSource dataSource;
    protected JdbcClient jdbc;
    protected JdbcStudyMissionStartReceiptRepository receipts;
    protected TransactionTemplate write;
    protected Seed seed;

    @BeforeEach void database() throws Exception {
        resetPostgresSchema();
        Flyway.configure().dataSource(postgresJdbcUrl(),"hippocampus","hippocampus").load().migrate();
        dataSource=new DriverManagerDataSource(postgresJdbcUrl(),"hippocampus","hippocampus");
        jdbc=JdbcClient.create(dataSource);receipts=new JdbcStudyMissionStartReceiptRepository(jdbc,new ObjectMapper());
        write=new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        write.setIsolationLevel(Connection.TRANSACTION_READ_COMMITTED);write.setTimeout(5);
        seed=seed(jdbc);
    }

    @Test void ownerIdentityRoundTripsAndDatabaseRejectsMutationOrDeletion() {
        Receipt ready=ready(seed,UUID.randomUUID());
        write.executeWithoutResult(s->{lock(seed.mission());receipts.insert(ready);});
        assertEquals(ready,receipts.findByRequest(seed.owner(),ready.startRequestId()).orElseThrow());
        assertTrue(receipts.findByRequest(UUID.randomUUID(),ready.startRequestId()).isEmpty());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.sql(
                "UPDATE study_mission_start_receipts SET request_fingerprint=:changed WHERE mission_id=:mission")
                .param("changed","d".repeat(64)).param("mission",seed.mission()).update());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.sql(
                "DELETE FROM study_mission_start_receipts WHERE mission_id=:mission").param("mission",seed.mission()).update());
    }

    @Test void claimJsonAndClosedHistoryRoundTripWithoutPermittingAuditRewrites() {
        Receipt initial=ready(seed,UUID.randomUUID());
        Receipt claimed=write.execute(s->{lock(seed.mission());receipts.insert(initial);
            Receipt c=initial.claim(UUID.randomUUID(),snapshot(seed),action(seed),initial.createdAt());
            receipts.replace(initial,c);return c;});
        assertEquals(claimed,receipts.findByMission(seed.owner(),seed.mission()).orElseThrow());
        Receipt closed=claimed.close(claimed.claimedAt().plusSeconds(1),Outcome.FAILED,Reason.DEPENDENCY_UNAVAILABLE,null);
        write.executeWithoutResult(s->{lock(seed.mission());receipts.replace(claimed,closed);});
        assertEquals(closed,receipts.findByMission(seed.owner(),seed.mission()).orElseThrow());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.sql("""
                UPDATE study_mission_start_receipts
                SET attempt_history=jsonb_set(attempt_history,'{0,outcome}','"ACCEPTED"'::jsonb)
                WHERE mission_id=:mission
                """).param("mission",seed.mission()).update());
    }

    @Test void activityAndAcceptedReceiptRollbackTogetherAndThenBecomeImmutable() {
        Receipt ready=ready(seed,UUID.randomUUID());
        Receipt claimed=write.execute(s->{lock(seed.mission());receipts.insert(ready);
            Receipt c=ready.claim(UUID.randomUUID(),snapshot(seed),action(seed),ready.createdAt());receipts.replace(ready,c);return c;});
        UUID activity=UUID.randomUUID();
        assertThrows(IllegalStateException.class,()->write.executeWithoutResult(s->{lock(seed.mission());
            insertReflection(jdbc,seed,activity);
            receipts.replace(claimed,claimed.close(claimed.claimedAt().plusSeconds(1),Outcome.ACCEPTED,null,activity));
            throw new IllegalStateException("Synthetic rollback after both writes");}));
        assertEquals(0,jdbc.sql("SELECT count(*) FROM learning_activities WHERE study_mission_id=:id").param("id",seed.mission()).query(Integer.class).single());
        assertEquals(State.CLAIMED,receipts.findByMission(seed.owner(),seed.mission()).orElseThrow().state());
        write.executeWithoutResult(s->{lock(seed.mission());insertReflection(jdbc,seed,activity);
            receipts.replace(claimed,claimed.close(claimed.claimedAt().plusSeconds(1),Outcome.ACCEPTED,null,activity));});
        assertEquals(activity,receipts.findByMission(seed.owner(),seed.mission()).orElseThrow().acceptedActivityId());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.sql(
                "UPDATE study_mission_start_receipts SET updated_at=clock_timestamp()+interval '1 hour' WHERE mission_id=:id")
                .param("id",seed.mission()).update());
    }

    protected void lock(UUID mission) {
        receipts.configureTransactionLimits();
        jdbc.sql("SELECT id FROM study_missions WHERE id=:id FOR UPDATE").param("id",mission).query(UUID.class).single();
    }
    protected Receipt ready(Seed fixture,UUID request) {
        return Receipt.ready(fixture.owner(),request,"a".repeat(64),fixture.mission(),fixture.objective(),
                "b".repeat(64),"c".repeat(64),receipts.databaseTime());
    }
    protected Snapshot snapshot(Seed fixture) {
        Instant updated=jdbc.sql("SELECT updated_at FROM study_missions WHERE id=:id").param("id",fixture.mission())
                .query((r,n)->r.getObject(1,OffsetDateTime.class).toInstant()).single();
        return new Snapshot(updated,null,0,0,fixture.objective(),"b".repeat(64));
    }
    protected static NextLearningAction action(Seed fixture) {
        return new NextLearningAction(LearningActionType.REFLECT,fixture.objective(),"topic:"+fixture.topic(),null,
                "TIME_LIMIT",false,LearningActionConstraints.unconstrained());
    }
    public static void insertReflection(JdbcClient jdbc,Seed f,UUID activity) {
        jdbc.sql("""
            INSERT INTO learning_activities(id,study_mission_id,learning_objective_id,activity_type,
                represented_action_type,status,sequence_number,source_required,created_at)
            VALUES (:id,:mission,:objective,'REFLECT','REFLECT','PENDING',1,false,clock_timestamp())
            """).param("id",activity).param("mission",f.mission()).param("objective",f.objective()).update();
    }
    public static Seed seed(JdbcClient jdbc) {
        var f=new Seed(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),
                UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        jdbc.sql("INSERT INTO users(id,email,status,created_at,updated_at) VALUES (:id,:email,'ACTIVE',now(),now())")
                .param("id",f.owner()).param("email",f.owner()+"@example.test").update();
        jdbc.sql("INSERT INTO subjects(id,user_id,name,status,created_at,updated_at) VALUES (:id,:owner,'Synthetic','ACTIVE',now(),now())")
                .param("id",f.subject()).param("owner",f.owner()).update();
        jdbc.sql("INSERT INTO topics(id,subject_id,name,status,created_at,updated_at) VALUES (:id,:subject,'Synthetic topic','ACTIVE',now(),now())")
                .param("id",f.topic()).param("subject",f.subject()).update();
        jdbc.sql("INSERT INTO materials(id,user_id,title,material_type,status,created_at,updated_at) VALUES (:id,:owner,'Synthetic material','PDF','READY',now(),now())")
                .param("id",f.material()).param("owner",f.owner()).update();
        jdbc.sql("INSERT INTO material_versions(id,material_id,version_number,processing_status,created_at) VALUES (:id,:material,1,'READY',now())")
                .param("id",f.version()).param("material",f.material()).update();
        jdbc.sql("UPDATE materials SET active_version_id=:version WHERE id=:material").param("version",f.version()).param("material",f.material()).update();
        jdbc.sql("INSERT INTO chunks(id,material_version_id,chunk_index,content,content_type,extraction_method,quality,created_at) VALUES (:id,:version,1,'Synthetic source grounding.','TEXT','NATIVE','STRONG',now())")
                .param("id",f.chunk()).param("version",f.version()).update();
        jdbc.sql("INSERT INTO material_topic_links(id,topic_id,material_id,link_origin,status,created_at,updated_at) VALUES (:id,:topic,:material,'USER_SELECTED','ACTIVE',now(),now())")
                .param("id",UUID.randomUUID()).param("topic",f.topic()).param("material",f.material()).update();
        jdbc.sql("""
            INSERT INTO study_missions(id,user_id,topic_id,status,learning_state,grounding_mode,available_time_minutes,started_at,created_at,updated_at)
            VALUES (:id,:owner,:topic,'ACTIVE','UNDERSTANDING','STRICT_SOURCE',20,now(),now(),now())
            """).param("id",f.mission()).param("owner",f.owner()).param("topic",f.topic()).update();
        jdbc.sql("INSERT INTO learning_objectives(id,study_mission_id,objective_text,concept_key,display_name,status,created_at) VALUES (:id,:mission,'Synthetic objective',:concept,'Synthetic topic','ACTIVE',now())")
                .param("id",f.objective()).param("mission",f.mission()).param("concept","topic:"+f.topic()).update();
        return f;
    }
    public record Seed(UUID owner,UUID subject,UUID topic,UUID material,UUID version,UUID chunk,UUID mission,UUID objective) {}
}
