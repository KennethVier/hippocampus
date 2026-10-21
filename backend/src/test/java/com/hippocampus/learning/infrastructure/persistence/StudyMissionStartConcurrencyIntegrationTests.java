package com.hippocampus.learning.infrastructure.persistence;

import com.hippocampus.learning.port.StudyMissionStartReceiptRepository.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Operator/CI only. Inherited constraint cases also run against this independently reset fixture. */
class StudyMissionStartConcurrencyIntegrationTests extends StudyMissionStartPersistenceIntegrationTests {
    @Test void staleWorkerCannotCommitAnActivityAfterLeaseTakeover() {
        Receipt initial=ready(seed,UUID.randomUUID());
        Receipt old=write.execute(s->{lock(seed.mission());receipts.insert(initial);
            Receipt c=initial.claim(UUID.randomUUID(),snapshot(seed),action(seed),initial.createdAt());receipts.replace(initial,c);return c;});
        Receipt replacement=write.execute(s->{lock(seed.mission());
            Receipt expired=old.close(old.leaseExpiresAt(),Outcome.LEASE_EXPIRED,Reason.PREPARATION_TIMEOUT,null);
            Receipt next=expired.claim(UUID.randomUUID(),snapshot(seed),action(seed),old.leaseExpiresAt());
            receipts.replace(old,next);return next;});
        assertThrows(IllegalStateException.class,()->write.executeWithoutResult(s->{lock(seed.mission());
            UUID activity=UUID.randomUUID();insertReflection(jdbc,seed,activity);
            receipts.replace(old,old.close(old.claimedAt().plusSeconds(1),Outcome.ACCEPTED,null,activity));}));
        assertEquals(replacement,receipts.findByMission(seed.owner(),seed.mission()).orElseThrow());
        assertEquals(0,jdbc.sql("SELECT count(*) FROM learning_activities WHERE study_mission_id=:id")
                .param("id",seed.mission()).query(Integer.class).single());
    }

    @Test void competingReceiptWritesRespectTheMissionLockAndOneSecondWait() throws Exception {
        Receipt initial=ready(seed,UUID.randomUUID());
        write.executeWithoutResult(s->{lock(seed.mission());receipts.insert(initial);});
        try(var held=dataSource.getConnection()) {
            held.setAutoCommit(false);
            try(var query=held.prepareStatement("SELECT id FROM study_missions WHERE id=? FOR UPDATE")) {
                query.setObject(1,seed.mission());query.executeQuery().close();
            }
            assertThrows(org.springframework.dao.DataAccessException.class,()->write.executeWithoutResult(s->lock(seed.mission())));
            held.rollback();
        }
        assertEquals(initial,receipts.findByMission(seed.owner(),seed.mission()).orElseThrow());
    }
}
