package com.hippocampus.materials.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hippocampus.learning.domain.SourceReadiness;
import com.hippocampus.learning.port.InitialStudyMissionSourceAuthority.SourceUnavailableException;
import com.hippocampus.learning.port.StudyMissionSourceCatalog;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class JdbcStudyMissionSourceCatalogFailureTests {
    @AfterEach
    void clearTransactionContext() {
        TransactionSynchronizationManager.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-locked-row", "changed-link", "changed-resolution"})
    void sourceChangeAtEachFreezeCheckpointUsesExistingPortFailure(String scenario) {
        UUID owner = UUID.randomUUID(), topic = UUID.randomUUID(), material = UUID.randomUUID();
        UUID version = UUID.randomUUID(), link = UUID.randomUUID();
        var jdbc = mock(JdbcClient.class);
        var statement = mock(JdbcClient.StatementSpec.class, RETURNS_SELF);
        @SuppressWarnings("unchecked")
        JdbcClient.MappedQuerySpec<UUID> ids = mock(JdbcClient.MappedQuerySpec.class);
        @SuppressWarnings("unchecked")
        JdbcClient.MappedQuerySpec<Boolean> valid = mock(JdbcClient.MappedQuerySpec.class);
        when(jdbc.sql(anyString())).thenReturn(statement);
        when(statement.query(UUID.class)).thenReturn(ids);
        when(statement.query(Boolean.class)).thenReturn(valid);
        when(ids.optional()).thenReturn(Optional.of(link));
        when(ids.list()).thenReturn(List.of());
        when(valid.single()).thenReturn(!scenario.equals("changed-link"));
        if (scenario.equals("missing-locked-row")) {
            when(ids.optional()).thenReturn(Optional.of(link), Optional.empty());
        }
        var initial = new StudyMissionSourceCatalog.Resolution(List.of(
                new StudyMissionSourceCatalog.ResolvedSource(material, version, null,
                        SourceReadiness.READY, true, false, false)));
        var catalog = new JdbcStudyMissionSourceCatalog(jdbc) {
            private int reads;
            @Override
            public Optional<Resolution> resolve(UUID requestedOwner, UUID requestedTopic,
                                                List<SourceSelection> selections) {
                return scenario.equals("changed-resolution") && reads++ > 0
                        ? Optional.empty() : Optional.of(initial);
            }
        };
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(Connection.TRANSACTION_READ_COMMITTED);

        assertThrows(SourceUnavailableException.class, () -> catalog.resolveLocked(owner, topic,
                List.of(new StudyMissionSourceCatalog.SourceSelection(material, null))));
    }

    @Test
    void catalogAdapterHasNoApplicationLayerDependencies() {
        ArchRuleDefinition.noClasses().should().dependOnClassesThat()
                .resideInAPackage("com.hippocampus..application..")
                .check(new ClassFileImporter().importClasses(JdbcStudyMissionSourceCatalog.class));
    }
}
