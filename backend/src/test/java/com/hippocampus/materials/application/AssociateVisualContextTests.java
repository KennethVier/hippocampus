package com.hippocampus.materials.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.DocumentNode;
import com.hippocampus.materials.domain.DocumentNodeDetectionOrigin;
import com.hippocampus.materials.domain.DocumentNodeType;
import com.hippocampus.materials.domain.ProcessingJobType;
import com.hippocampus.materials.domain.TextBlock;
import com.hippocampus.materials.domain.TextBlockExtractionMethod;
import com.hippocampus.materials.domain.TextBlockQuality;
import com.hippocampus.materials.domain.TextBlockType;
import com.hippocampus.materials.domain.VisualContextAsset;
import com.hippocampus.materials.domain.VisualContextAssociation;
import com.hippocampus.materials.domain.VisualContextAssociationPolicy;
import com.hippocampus.materials.port.DocumentStructureRepository;
import com.hippocampus.materials.port.VisualContextRepository;

class AssociateVisualContextTests {
    private static final UUID VERSION = UUID.randomUUID();
    private static final UUID ROOT = UUID.randomUUID();

    @Test
    void persistsDetectedContextAndTreatsNoCaptionAsSuccessfulProcessing() {
        RecordingVisuals captioned = new RecordingVisuals(List.of(asset(VERSION)));
        AssociateVisualContext useCase = useCase(captioned, "Figure 4. Cardiac conduction\n\nLocal explanation.");

        assertThat(useCase.execute(job())).singleElement().satisfies(association -> {
            assertThat(association.caption()).isEqualTo("Figure 4. Cardiac conduction");
            assertThat(association.nearbyText()).isEqualTo("Local explanation.");
        });
        assertThat(captioned.persisted).hasSize(1);

        RecordingVisuals unresolved = new RecordingVisuals(List.of(asset(VERSION)));
        assertThat(useCase(unresolved, "No explicit caption").execute(job())).isEmpty();
        assertThat(unresolved.persisted).isNull();
    }

    @Test
    void validatesNodesOnceAndLoadsAndPersistsOneVisualPageAtATime() {
        List<String> events = new ArrayList<>();
        RecordingVisuals visuals = new RecordingVisuals(
                List.of(asset(VERSION, 2), asset(VERSION, 1)), events);
        RecordingStructures structures = new RecordingStructures(events);
        AssociateVisualContext useCase = new AssociateVisualContext(
                visuals,
                structures,
                new VisualContextAssociationPolicy(),
                new PersistVisualContext(visuals));

        assertThat(useCase.execute(job())).hasSize(2);
        assertThat(structures.nodeLoads).isEqualTo(1);
        assertThat(structures.pageRanges).containsExactly("1-1", "2-2");
        assertThat(visuals.persistedBatches)
                .hasSize(2)
                .allSatisfy(batch -> assertThat(batch).hasSize(1));
        assertThat(events).containsExactly("nodes", "text-1", "persist-1", "text-2", "persist-2");
    }

    @Test
    void failsClosedForCrossMaterialVersionState() {
        RecordingVisuals visuals = new RecordingVisuals(List.of(asset(UUID.randomUUID())));

        assertThatThrownBy(() -> useCase(visuals, "Figure 4. Caption").execute(job()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cross-material-version");
        assertThat(visuals.persisted).isNull();
    }

    private static AssociateVisualContext useCase(RecordingVisuals visuals, String pageContent) {
        return new AssociateVisualContext(
                visuals,
                structures(pageContent),
                new VisualContextAssociationPolicy(),
                new PersistVisualContext(visuals));
    }

    private static DocumentStructureRepository structures(String pageContent) {
        DocumentNode root = new DocumentNode(
                ROOT, VERSION, null, DocumentNodeType.DOCUMENT, null, null, 1, 1,
                null, null, DocumentNodeDetectionOrigin.NATIVE, null, Instant.EPOCH);
        TextBlock block = new TextBlock(
                UUID.randomUUID(), VERSION, ROOT, 1, TextBlockType.PAGE_TEXT, 1, pageContent,
                TextBlockExtractionMethod.NATIVE, TextBlockQuality.STRONG, Instant.EPOCH);
        return new DocumentStructureRepository() {
            @Override public boolean hasDocumentRoot(UUID id) { return true; }
            @Override public Optional<DocumentNode> findDocumentRoot(UUID id) { return Optional.of(root); }
            @Override public List<DocumentNode> findNodesByMaterialVersion(UUID id) { return List.of(root); }
            @Override public List<DocumentNode> findChildren(UUID id, UUID parentId) { return List.of(); }
            @Override public List<TextBlock> findTextBlocksByOrdinalRange(UUID id, int first, int last) {
                return List.of(block);
            }
        };
    }

    private static VisualContextAsset asset(UUID version) {
        return asset(version, 1);
    }

    private static VisualContextAsset asset(UUID version, int page) {
        return new VisualContextAsset(UUID.randomUUID(), version, ROOT, page, null, null);
    }

    private static ClaimedProcessingJob job() {
        return new ClaimedProcessingJob(UUID.randomUUID(), ProcessingJobType.VISUAL_EXTRACT, VERSION, "worker");
    }

    private static final class RecordingVisuals implements VisualContextRepository {
        private final List<VisualContextAsset> assets;
        private final List<String> events;
        private final List<List<VisualContextAssociation>> persistedBatches = new ArrayList<>();
        private List<VisualContextAssociation> persisted;

        private RecordingVisuals(List<VisualContextAsset> assets) {
            this(assets, new ArrayList<>());
        }

        private RecordingVisuals(List<VisualContextAsset> assets, List<String> events) {
            this.assets = new ArrayList<>(assets);
            this.events = events;
        }

        @Override public List<VisualContextAsset> findByMaterialVersion(UUID id) { return List.copyOf(assets); }
        @Override public void persist(UUID id, List<VisualContextAssociation> associations) {
            persisted = List.copyOf(associations);
            persistedBatches.add(persisted);
            if (!persisted.isEmpty()) {
                events.add("persist-" + persisted.getFirst().pageNumber());
            }
        }
    }

    private static final class RecordingStructures implements DocumentStructureRepository {
        private final List<String> events;
        private final List<String> pageRanges = new ArrayList<>();
        private int nodeLoads;

        private RecordingStructures(List<String> events) {
            this.events = events;
        }

        @Override public boolean hasDocumentRoot(UUID id) { return false; }

        @Override public Optional<DocumentNode> findDocumentRoot(UUID id) { return Optional.empty(); }

        @Override
        public List<DocumentNode> findNodesByMaterialVersion(UUID id) {
            nodeLoads++;
            events.add("nodes");
            return List.of(new DocumentNode(
                    ROOT, VERSION, null, DocumentNodeType.DOCUMENT, null, null, 1, 2,
                    null, null, DocumentNodeDetectionOrigin.NATIVE, null, Instant.EPOCH));
        }

        @Override public List<DocumentNode> findChildren(UUID id, UUID parentId) { return List.of(); }

        @Override
        public List<TextBlock> findTextBlocksByOrdinalRange(UUID id, int first, int last) {
            pageRanges.add(first + "-" + last);
            events.add("text-" + first);
            return List.of(new TextBlock(
                    UUID.randomUUID(), VERSION, ROOT, first, TextBlockType.PAGE_TEXT, first,
                    "Figure " + first + ". Caption", TextBlockExtractionMethod.NATIVE,
                    TextBlockQuality.STRONG, Instant.EPOCH));
        }
    }
}
