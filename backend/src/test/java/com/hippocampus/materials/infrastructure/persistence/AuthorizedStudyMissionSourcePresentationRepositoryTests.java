package com.hippocampus.materials.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hippocampus.learning.domain.MissionMaterial;
import com.hippocampus.materials.domain.SourceReference;
import com.hippocampus.materials.port.MaterialMetadata;
import com.hippocampus.materials.port.MaterialRepository;
import com.hippocampus.materials.port.SourceReferenceRepository;

class AuthorizedStudyMissionSourcePresentationRepositoryTests {

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID SOURCE_ID = UUID.randomUUID();
    private static final UUID MATERIAL_ID = UUID.randomUUID();
    private static final UUID VERSION_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-01T09:00:00Z");

    @Test
    void mapsOnlyCurrentlyAuthorizedSourceWithinFrozenMissionScope() {
        SourceReferenceRepository references = mock(SourceReferenceRepository.class);
        MaterialRepository materials = mock(MaterialRepository.class);
        when(references.resolveAuthorized(OWNER_ID, SOURCE_ID)).thenReturn(Optional.of(reference()));
        when(materials.findVisibleOwnedById(MATERIAL_ID, OWNER_ID)).thenReturn(Optional.of(material(VERSION_ID)));
        var repository = new AuthorizedStudyMissionSourcePresentationRepository(references, materials);

        var result = repository.resolveAuthorized(
                OWNER_ID,
                List.of(new MissionMaterial(UUID.randomUUID(), MATERIAL_ID, VERSION_ID, null)),
                Set.of(SOURCE_ID));

        assertThat(result).isPresent();
        assertThat(result.orElseThrow()).singleElement().satisfies(source -> {
            assertThat(source.sourceReferenceId()).isEqualTo(SOURCE_ID);
            assertThat(source.materialTitle()).isEqualTo("Upper Limb Lecture");
            assertThat(source.pageNumber()).isEqualTo(14);
            assertThat(source.displayLabel()).isEqualTo("Posterior Cord");
        });
    }

    @Test
    void rejectsOutOfScopeDeletedOrStaleMaterialVersion() {
        SourceReferenceRepository references = mock(SourceReferenceRepository.class);
        MaterialRepository materials = mock(MaterialRepository.class);
        when(references.resolveAuthorized(OWNER_ID, SOURCE_ID)).thenReturn(Optional.of(reference()));
        when(materials.findVisibleOwnedById(MATERIAL_ID, OWNER_ID))
                .thenReturn(Optional.of(material(UUID.randomUUID())));
        var repository = new AuthorizedStudyMissionSourcePresentationRepository(references, materials);

        assertThat(repository.resolveAuthorized(
                OWNER_ID,
                List.of(new MissionMaterial(UUID.randomUUID(), MATERIAL_ID, VERSION_ID, UUID.randomUUID())),
                Set.of(SOURCE_ID))).isEmpty();

        assertThat(repository.resolveAuthorized(
                OWNER_ID,
                List.of(new MissionMaterial(UUID.randomUUID(), MATERIAL_ID, VERSION_ID, null)),
                Set.of(SOURCE_ID))).isEmpty();

        when(references.resolveAuthorized(OWNER_ID, SOURCE_ID)).thenReturn(Optional.empty());
        assertThat(repository.resolveAuthorized(
                OWNER_ID,
                List.of(new MissionMaterial(UUID.randomUUID(), MATERIAL_ID, VERSION_ID, null)),
                Set.of(SOURCE_ID))).isEmpty();
    }

    private static SourceReference reference() {
        return new SourceReference(
                SOURCE_ID, MATERIAL_ID, VERSION_ID, null, UUID.randomUUID(), null,
                14, null, null, "Posterior Cord", NOW);
    }

    private static MaterialMetadata material(UUID activeVersionId) {
        return new MaterialMetadata(
                MATERIAL_ID, "Upper Limb Lecture", "PDF", "upper-limb.pdf",
                "application/pdf", "READY", activeVersionId, NOW, NOW);
    }
}
