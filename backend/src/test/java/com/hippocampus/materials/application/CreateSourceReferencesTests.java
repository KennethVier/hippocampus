package com.hippocampus.materials.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hippocampus.identity.domain.AuthenticatedUser;
import com.hippocampus.materials.domain.ChunkSourceTarget;
import com.hippocampus.materials.domain.PageSourceTarget;
import com.hippocampus.materials.domain.SourceReference;
import com.hippocampus.materials.domain.SourceReferenceTarget;
import com.hippocampus.materials.port.SourceReferenceRepository;
import com.hippocampus.materials.port.SourceReferenceSeed;

class CreateSourceReferencesTests {
    @Test
    void derivesCurrentUserPreservesOrderAndBuildsAuthoritativeLabels() {
        UUID owner = UUID.randomUUID();
        UUID material = UUID.randomUUID();
        UUID version = UUID.randomUUID();
        UUID chunk = UUID.randomUUID();
        UUID node = UUID.randomUUID();
        CapturingRepository repository = new CapturingRepository(owner, material, version, chunk, node);
        CreateSourceReferences useCase = new CreateSourceReferences(
                () -> new AuthenticatedUser(owner), repository);

        List<SourceReference> result = useCase.execute(new CreateSourceReferences.Command(List.of(
                new ChunkSourceTarget(material, version, chunk),
                new PageSourceTarget(material, version, 9))));

        assertThat(repository.seenUsers).containsExactly(owner, owner);
        assertThat(result).extracting(SourceReference::displayLabel)
                .containsExactly("Upper Limb Lecture · Page 14 · Posterior Cord", "Upper Limb Lecture · Page 9");
        assertThat(result).extracting(SourceReference::pageNumber).containsExactly(14, 9);
    }

    @Test
    void rejectsDuplicatePrimaryTargetsBeforePersistence() {
        UUID id = UUID.randomUUID();
        ChunkSourceTarget target = new ChunkSourceTarget(id, id, id);
        CapturingRepository repository = new CapturingRepository(id, id, id, id, id);
        CreateSourceReferences useCase = new CreateSourceReferences(
                () -> new AuthenticatedUser(id), repository);

        assertThatThrownBy(() -> useCase.execute(
                new CreateSourceReferences.Command(List.of(target, target))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate");
        assertThat(repository.seenUsers).isEmpty();
    }

    private static final class CapturingRepository implements SourceReferenceRepository {
        private final UUID expectedUser;
        private final UUID material;
        private final UUID version;
        private final UUID chunk;
        private final UUID node;
        private final List<UUID> seenUsers = new ArrayList<>();

        private CapturingRepository(UUID expectedUser, UUID material, UUID version, UUID chunk, UUID node) {
            this.expectedUser = expectedUser;
            this.material = material;
            this.version = version;
            this.chunk = chunk;
            this.node = node;
        }

        @Override
        public Optional<SourceReferenceSeed> findAuthorizedTarget(UUID userId, SourceReferenceTarget target) {
            assertThat(userId).isEqualTo(expectedUser);
            seenUsers.add(userId);
            if (target instanceof ChunkSourceTarget) {
                return Optional.of(new SourceReferenceSeed(
                        material, version, node, chunk, null, 14,
                        " Upper Limb Lecture ", " Posterior Cord "));
            }
            return Optional.of(new SourceReferenceSeed(
                    material, version, null, null, null, 9, "Upper Limb Lecture", null));
        }

        @Override
        public SourceReference upsert(SourceReferenceSeed seed, String displayLabel) {
            return new SourceReference(
                    UUID.randomUUID(), seed.materialId(), seed.materialVersionId(), seed.documentNodeId(),
                    seed.chunkId(), seed.visualAssetId(), seed.pageNumber(), null, null,
                    displayLabel, Instant.EPOCH);
        }

        @Override
        public Optional<SourceReference> resolveAuthorized(UUID userId, UUID sourceReferenceId) {
            return Optional.empty();
        }
    }
}
