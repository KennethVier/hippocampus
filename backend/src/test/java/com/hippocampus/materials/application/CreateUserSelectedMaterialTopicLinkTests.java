package com.hippocampus.materials.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hippocampus.identity.domain.AuthenticatedUser;
import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.materials.domain.MaterialTopicLink;
import com.hippocampus.materials.domain.MaterialTopicLinkOrigin;
import com.hippocampus.materials.domain.MaterialTopicLinkStatus;
import com.hippocampus.materials.port.CreateMaterialTopicLinkResult;
import com.hippocampus.materials.port.MaterialTopicLinkRepository;
import com.hippocampus.shared.application.error.ApplicationNotFoundException;

class CreateUserSelectedMaterialTopicLinkTests {

    @Test
    void derivesOwnerAndOwnsOriginAndStatus() {
        UUID ownerId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        UUID materialId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        RecordingRepository repository = new RecordingRepository();
        CreateUserSelectedMaterialTopicLink useCase = new CreateUserSelectedMaterialTopicLink(
                currentUser(ownerId), repository);

        MaterialTopicLink created = useCase.execute(
                new CreateUserSelectedMaterialTopicLink.Command(topicId, materialId, versionId));

        assertThat(repository.ownerId).isEqualTo(ownerId);
        assertThat(repository.topicId).isEqualTo(topicId);
        assertThat(repository.materialId).isEqualTo(materialId);
        assertThat(repository.materialVersionId).isEqualTo(versionId);
        assertThat(created.origin()).isEqualTo(MaterialTopicLinkOrigin.USER_SELECTED);
        assertThat(created.status()).isEqualTo(MaterialTopicLinkStatus.ACTIVE);
    }

    @Test
    void hidesMissingAndForeignEligibilityBehindSameApplicationFailure() {
        MaterialTopicLinkRepository repository = (owner, topic, material, version) ->
                CreateMaterialTopicLinkResult.ineligible();
        CreateUserSelectedMaterialTopicLink useCase = new CreateUserSelectedMaterialTopicLink(
                currentUser(UUID.randomUUID()), repository);

        assertThatThrownBy(() -> useCase.execute(new CreateUserSelectedMaterialTopicLink.Command(
                UUID.randomUUID(), UUID.randomUUID(), null)))
                .isInstanceOf(ApplicationNotFoundException.class)
                .hasMessage("Material was not found.");
    }

    @Test
    void translatesDuplicateActiveOutcome() {
        MaterialTopicLinkRepository repository = (owner, topic, material, version) ->
                CreateMaterialTopicLinkResult.duplicateActive();
        CreateUserSelectedMaterialTopicLink useCase = new CreateUserSelectedMaterialTopicLink(
                currentUser(UUID.randomUUID()), repository);

        assertThatThrownBy(() -> useCase.execute(new CreateUserSelectedMaterialTopicLink.Command(
                UUID.randomUUID(), UUID.randomUUID(), null)))
                .isInstanceOf(MaterialTopicLinkAlreadyActiveException.class)
                .hasMessage("An active link already exists for this target.");
    }

    private static CurrentUser currentUser(UUID ownerId) {
        return () -> new AuthenticatedUser(ownerId);
    }

    private static final class RecordingRepository implements MaterialTopicLinkRepository {
        private UUID ownerId;
        private UUID topicId;
        private UUID materialId;
        private UUID materialVersionId;

        @Override
        public CreateMaterialTopicLinkResult createUserSelectedActive(
                UUID ownerId, UUID topicId, UUID materialId, UUID materialVersionId) {
            this.ownerId = ownerId;
            this.topicId = topicId;
            this.materialId = materialId;
            this.materialVersionId = materialVersionId;
            Instant now = Instant.now();
            return CreateMaterialTopicLinkResult.created(new MaterialTopicLink(
                    UUID.randomUUID(), topicId, materialId, materialVersionId, null,
                    MaterialTopicLinkOrigin.USER_SELECTED, MaterialTopicLinkStatus.ACTIVE, now, now));
        }
    }
}
