package com.hippocampus.materials.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.hippocampus.materials.port.MaterialLifecycleTelemetry.UploadFailureReason;
import com.hippocampus.materials.port.MaterialLifecycleTelemetry.UploadRejectionReason;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class MicrometerMaterialLifecycleTelemetryTests {

    private static final UUID MATERIAL_ID = UUID.fromString("dc17ef4c-ab80-40bb-a02f-05fc17a5f6c4");
    private static final UUID VERSION_ID = UUID.fromString("ed44e98a-c638-43f2-92ce-d2869d01c29f");

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void acceptedPublishesSafeIdsAndCurrentUploadedStatusExactlyOnce() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerMaterialLifecycleTelemetry telemetry = new MicrometerMaterialLifecycleTelemetry(registry);
        ListAppender<ILoggingEvent> logs = captureLogs();

        telemetry.uploadAccepted(MATERIAL_ID, VERSION_ID);

        assertThat(counter(registry, MicrometerMaterialLifecycleTelemetry.UPLOAD_ACCEPTED_METRIC)).isEqualTo(1.0);
        assertThat(statusCounter(registry, "MATERIAL", "UPLOADED")).isEqualTo(1.0);
        assertThat(statusCounter(registry, "MATERIAL_VERSION", "UPLOADED")).isEqualTo(1.0);
        Map<String, Object> fields = fields(event(logs, "material_upload_accepted"));
        assertThat(fields).containsAllEntriesOf(Map.of(
                "event", "material_upload_accepted",
                "domain", "materials",
                "operation", "upload",
                "materialId", MATERIAL_ID,
                "materialVersionId", VERSION_ID,
                "materialStatus", "UPLOADED",
                "processingStatus", "UPLOADED"));
        assertThat(fields).doesNotContainKeys(
                "filename", "originalFilename", "title", "storageKey", "userId", "sessionId");
    }

    @Test
    void rejectionAndOperationalFailureUseSeparateFiniteMetricsWithoutResourceIds() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerMaterialLifecycleTelemetry telemetry = new MicrometerMaterialLifecycleTelemetry(registry);
        ListAppender<ILoggingEvent> logs = captureLogs();

        telemetry.uploadRejected(UploadRejectionReason.UPLOAD_TYPE_UNSUPPORTED);
        telemetry.uploadFailed(UploadFailureReason.UPLOAD_STORAGE_UNAVAILABLE);

        assertThat(registry.get(MicrometerMaterialLifecycleTelemetry.UPLOAD_REJECTED_METRIC)
                .tag("reason", "UPLOAD_TYPE_UNSUPPORTED").counter().count()).isEqualTo(1.0);
        assertThat(registry.get(MicrometerMaterialLifecycleTelemetry.UPLOAD_FAILED_METRIC)
                .tag("reason", "UPLOAD_STORAGE_UNAVAILABLE").counter().count()).isEqualTo(1.0);
        assertThat(fields(event(logs, "material_upload_rejected")))
                .containsEntry("errorCode", "UPLOAD_TYPE_UNSUPPORTED")
                .doesNotContainKeys("materialId", "materialVersionId", "userId");
        assertThat(fields(event(logs, "material_upload_failed")))
                .containsEntry("errorCode", "UPLOAD_STORAGE_UNAVAILABLE")
                .doesNotContainKeys("materialId", "materialVersionId", "userId");
    }

    @Test
    void deletionPublishesOnlyAfterCommitAndRollbackPublishesNothing() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerMaterialLifecycleTelemetry telemetry = new MicrometerMaterialLifecycleTelemetry(registry);

        beginTransactionSynchronization();
        telemetry.materialDeleted(MATERIAL_ID);
        assertThat(registry.find(MicrometerMaterialLifecycleTelemetry.MATERIAL_DELETED_METRIC).counter()).isNull();
        completeCommit();

        assertThat(counter(registry, MicrometerMaterialLifecycleTelemetry.MATERIAL_DELETED_METRIC)).isEqualTo(1.0);
        assertThat(statusCounter(registry, "MATERIAL", "DELETED")).isEqualTo(1.0);

        beginTransactionSynchronization();
        telemetry.materialDeleted(UUID.randomUUID());
        completeRollback();

        assertThat(counter(registry, MicrometerMaterialLifecycleTelemetry.MATERIAL_DELETED_METRIC)).isEqualTo(1.0);
        assertThat(statusCounter(registry, "MATERIAL", "DELETED")).isEqualTo(1.0);
    }

    @Test
    void postCommitMetricFailureDoesNotEscapeTheCallback() {
        MeterRegistry registry = mock(MeterRegistry.class);
        when(registry.counter(anyString(), org.mockito.ArgumentMatchers.<Iterable<Tag>>any()))
                .thenThrow(new IllegalStateException("PRIVATE_EXCEPTION_SENTINEL"));
        MicrometerMaterialLifecycleTelemetry telemetry = new MicrometerMaterialLifecycleTelemetry(registry);
        ListAppender<ILoggingEvent> logs = captureLogs();

        beginTransactionSynchronization();
        telemetry.materialDeleted(MATERIAL_ID);
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();

        assertThatCode(() -> synchronizations.forEach(TransactionSynchronization::afterCommit))
                .doesNotThrowAnyException();
        assertThat(logs.list).allSatisfy(log -> {
            assertThat(log.getFormattedMessage()).doesNotContain("PRIVATE_EXCEPTION_SENTINEL");
            assertThat(fields(log).values()).doesNotContain("PRIVATE_EXCEPTION_SENTINEL");
        });
    }

    @Test
    void materialMetricsUseOnlyBoundedLowCardinalityTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerMaterialLifecycleTelemetry telemetry = new MicrometerMaterialLifecycleTelemetry(registry);
        telemetry.uploadAccepted(MATERIAL_ID, VERSION_ID);
        for (UploadRejectionReason reason : UploadRejectionReason.values()) {
            telemetry.uploadRejected(reason);
        }
        for (UploadFailureReason reason : UploadFailureReason.values()) {
            telemetry.uploadFailed(reason);
        }
        telemetry.materialDeleted(MATERIAL_ID);

        List<Meter> meters = registry.getMeters().stream()
                .filter(meter -> meter.getId().getName().startsWith("hippocampus.materials."))
                .toList();
        assertThat(meters).isNotEmpty();
        assertThat(meters).allSatisfy(meter -> {
            Map<String, String> tags = meter.getId().getTags().stream()
                    .collect(Collectors.toMap(Tag::getKey, Tag::getValue));
            assertThat(tags.keySet()).doesNotContain(
                    "materialId", "materialVersionId", "userId", "correlationId", "filename");
            assertThat(tags.keySet()).allMatch(key -> key.equals("reason") || key.equals("scope") || key.equals("status"));
            if (tags.containsKey("reason")) {
                assertThat(java.util.stream.Stream.concat(
                                java.util.Arrays.stream(UploadRejectionReason.values()).map(Enum::name),
                                java.util.Arrays.stream(UploadFailureReason.values()).map(Enum::name))
                        .anyMatch(tags.get("reason")::equals)).isTrue();
            }
            if (tags.containsKey("scope")) {
                assertThat(tags.get("scope")).isIn("MATERIAL", "MATERIAL_VERSION");
                assertThat(tags.get("status")).isIn("UPLOADED", "DELETED");
            }
        });
    }

    private static void beginTransactionSynchronization() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    private static void completeCommit() {
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(synchronization -> synchronization.beforeCommit(false));
        synchronizations.forEach(TransactionSynchronization::beforeCompletion);
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        synchronizations.forEach(synchronization ->
                synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    private static void completeRollback() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::beforeCompletion);
        TransactionSynchronizationManager.getSynchronizations().forEach(synchronization ->
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    private static double counter(SimpleMeterRegistry registry, String name) {
        return registry.get(name).counter().count();
    }

    private static double statusCounter(SimpleMeterRegistry registry, String scope, String status) {
        return registry.get(MicrometerMaterialLifecycleTelemetry.STATUS_TRANSITIONS_METRIC)
                .tags("scope", scope, "status", status)
                .counter()
                .count();
    }

    private static ListAppender<ILoggingEvent> captureLogs() {
        Logger logger = (Logger) LoggerFactory.getLogger(MicrometerMaterialLifecycleTelemetry.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private static ILoggingEvent event(ListAppender<ILoggingEvent> logs, String eventName) {
        return logs.list.stream()
                .filter(log -> eventName.equals(fields(log).get("event")))
                .findFirst()
                .orElseThrow();
    }

    private static Map<String, Object> fields(ILoggingEvent event) {
        return event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value, (first, second) -> second));
    }
}
