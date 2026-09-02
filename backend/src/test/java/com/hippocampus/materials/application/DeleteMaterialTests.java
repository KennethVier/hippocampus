package com.hippocampus.materials.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hippocampus.identity.domain.AuthenticatedUser;
import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.materials.port.MaterialDeletionOutcome;
import com.hippocampus.materials.port.MaterialLifecycleTelemetry;
import com.hippocampus.materials.port.MaterialMetadata;
import com.hippocampus.materials.port.MaterialPage;
import com.hippocampus.materials.port.MaterialPageRequest;
import com.hippocampus.materials.port.MaterialRepository;
import com.hippocampus.shared.application.error.ApplicationNotFoundException;

class DeleteMaterialTests {

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID MATERIAL_ID = UUID.randomUUID();

    @Test
    void actualTransitionPublishesDeletionOnce() {
        RecordingTelemetry telemetry = new RecordingTelemetry();
        DeleteMaterial deleteMaterial = delete(MaterialDeletionOutcome.DELETED, telemetry);

        deleteMaterial.execute(MATERIAL_ID);

        assertThat(telemetry.deleted).containsExactly(MATERIAL_ID);
    }

    @Test
    void alreadyDeletedRemainsIdempotentWithoutAnotherLifecycleSignal() {
        RecordingTelemetry telemetry = new RecordingTelemetry();
        DeleteMaterial deleteMaterial = delete(MaterialDeletionOutcome.ALREADY_DELETED, telemetry);

        assertThatCode(() -> deleteMaterial.execute(MATERIAL_ID)).doesNotThrowAnyException();

        assertThat(telemetry.deleted).isEmpty();
    }

    @Test
    void foreignOrMissingMaterialPreservesNotFoundWithoutDeletionSignal() {
        RecordingTelemetry telemetry = new RecordingTelemetry();
        DeleteMaterial deleteMaterial = delete(MaterialDeletionOutcome.NOT_FOUND, telemetry);

        assertThatThrownBy(() -> deleteMaterial.execute(MATERIAL_ID))
                .isInstanceOf(ApplicationNotFoundException.class);
        assertThat(telemetry.deleted).isEmpty();
    }

    @Test
    void telemetryFailureDoesNotChangeSuccessfulDeletionResult() {
        MaterialLifecycleTelemetry telemetry = new RecordingTelemetry() {
            @Override public void materialDeleted(UUID materialId) {
                throw new IllegalStateException("PRIVATE_EXCEPTION_SENTINEL");
            }
        };
        DeleteMaterial deleteMaterial = delete(MaterialDeletionOutcome.DELETED, telemetry);

        assertThatCode(() -> deleteMaterial.execute(MATERIAL_ID)).doesNotThrowAnyException();
    }

    private static DeleteMaterial delete(
            MaterialDeletionOutcome outcome,
            MaterialLifecycleTelemetry telemetry) {
        CurrentUser currentUser = () -> new AuthenticatedUser(OWNER_ID);
        MaterialRepository materials = new StubMaterialRepository(outcome);
        return new DeleteMaterial(currentUser, materials, telemetry);
    }

    private static final class StubMaterialRepository implements MaterialRepository {
        private final MaterialDeletionOutcome outcome;

        private StubMaterialRepository(MaterialDeletionOutcome outcome) {
            this.outcome = outcome;
        }

        @Override public MaterialPage findVisibleByOwner(UUID ownerId, MaterialPageRequest pageRequest) {
            throw new UnsupportedOperationException();
        }

        @Override public Optional<MaterialMetadata> findVisibleOwnedById(UUID materialId, UUID ownerId) {
            throw new UnsupportedOperationException();
        }

        @Override public MaterialDeletionOutcome markDeletedOwned(UUID materialId, UUID ownerId) {
            assertThat(materialId).isEqualTo(MATERIAL_ID);
            assertThat(ownerId).isEqualTo(OWNER_ID);
            return outcome;
        }
    }

    private static class RecordingTelemetry implements MaterialLifecycleTelemetry {
        final java.util.List<UUID> deleted = new java.util.ArrayList<>();

        @Override public void uploadAccepted(UUID materialId, UUID materialVersionId) {}
        @Override public void uploadRejected(UploadRejectionReason reason) {}
        @Override public void uploadFailed(UploadFailureReason reason) {}
        @Override public void materialDeleted(UUID materialId) { deleted.add(materialId); }
    }
}
