package com.hippocampus.materials.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hippocampus.materials.domain.ChunkDraft;
import com.hippocampus.materials.domain.ChunkingExecutionSummary;
import com.hippocampus.materials.domain.DeterministicChunkTokenCounter;
import com.hippocampus.materials.domain.DocumentNode;
import com.hippocampus.materials.domain.DocumentNodeDetectionOrigin;
import com.hippocampus.materials.domain.DocumentNodeType;
import com.hippocampus.materials.domain.HierarchyAwareChunkingPolicy;
import com.hippocampus.materials.domain.TextBlock;
import com.hippocampus.materials.domain.TextBlockExtractionMethod;
import com.hippocampus.materials.domain.TextBlockType;
import com.hippocampus.materials.port.ChunkPersistence;
import com.hippocampus.materials.port.ChunkingSourceRepository;
import com.hippocampus.materials.port.ChunkingSourceRepository.VisualSource;

class ChunkMaterialTextTests {
    @Test
    void processes601PagesWithBoundedSourceAndPersistenceBatches() {
        FakeSource source = new FakeSource(601);
        RecordingPersistence sink = new RecordingPersistence();
        useCase(source, sink, 20, 7).execute(source.version);
        assertThat(source.maxRequestedPages).isEqualTo(20);
        assertThat(sink.maxBatch).isLessThanOrEqualTo(7);
        assertThat(sink.summary.pageCount()).isEqualTo(601);
        assertThat(sink.summary.expectedChunkCount()).isEqualTo(sink.totalChunks);
    }

    @Test
    void usesExactPrimaryPagesForVisualsAcrossPageBatchBoundary() {
        FakeSource source = new FakeSource(6);
        java.util.Collections.fill(source.contents, "");
        source.contents.set(3, "page four");
        source.contents.set(5, "page six");
        UUID page4 = UUID.randomUUID(); UUID page5 = UUID.randomUUID(); UUID page6 = UUID.randomUUID();
        source.visuals = List.of(new VisualSource(page4, source.version, source.root, 4),
                new VisualSource(page5, source.version, source.root, 5),
                new VisualSource(page6, source.version, source.root, 6));
        RecordingPersistence sink = new RecordingPersistence();
        useCase(source, sink, 2, 10).execute(source.version);
        ChunkDraft chunk = sink.chunks.stream().filter(value -> value.primaryPages().contains(4)).findFirst().orElseThrow();
        assertThat(chunk.primaryPages()).containsExactlyInAnyOrder(4, 6);
        assertThat(chunk.visualAssetIds()).containsExactlyInAnyOrder(page4, page6).doesNotContain(page5);
    }

    @Test
    void rejectsMissingDuplicateAndWrongOrdinalPageText() {
        FakeSource missing = new FakeSource(2); missing.omitPage = 2;
        assertThatThrownBy(() -> useCase(missing, new RecordingPersistence(), 2, 2).execute(missing.version))
                .isInstanceOf(IllegalStateException.class);
        FakeSource duplicate = new FakeSource(2); duplicate.duplicatePage = 1;
        assertThatThrownBy(() -> useCase(duplicate, new RecordingPersistence(), 2, 2).execute(duplicate.version))
                .isInstanceOf(IllegalStateException.class);
        FakeSource wrong = new FakeSource(2); wrong.wrongOrdinalPage = 1;
        assertThatThrownBy(() -> useCase(wrong, new RecordingPersistence(), 2, 2).execute(wrong.version))
                .isInstanceOf(IllegalStateException.class);
    }

    private static ChunkMaterialText useCase(FakeSource source, RecordingPersistence sink, int pages, int chunks) {
        return new ChunkMaterialText(source,
                new HierarchyAwareChunkingPolicy(new DeterministicChunkTokenCounter(), 800, 1000, 100),
                new PersistChunkBatch(sink), new FinalizeChunking(sink), pages, chunks, 1000, 10);
    }

    private static final class RecordingPersistence implements ChunkPersistence {
        int maxBatch; int totalChunks; ChunkingExecutionSummary summary; final List<ChunkDraft> chunks = new ArrayList<>();
        @Override public void persistOrVerify(UUID version, List<ChunkDraft> values) {
            maxBatch = Math.max(maxBatch, values.size()); totalChunks += values.size(); chunks.addAll(values);
        }
        @Override public void finalizeChunking(UUID version, ChunkingExecutionSummary value) { summary = value; }
    }

    private static final class FakeSource implements ChunkingSourceRepository {
        final UUID version = UUID.randomUUID(); final UUID root = UUID.randomUUID(); final int pages;
        final List<String> contents; int maxRequestedPages; int omitPage; int duplicatePage; int wrongOrdinalPage;
        List<VisualSource> visuals = List.of();
        FakeSource(int pages) { this.pages = pages; contents = new ArrayList<>(); for (int i=1;i<=pages;i++) contents.add("text "+i); }
        @Override public int requirePageCount(UUID ignored) { return pages; }
        @Override public List<DocumentNode> findHierarchy(UUID ignored) { return List.of(new DocumentNode(root,version,null,
                DocumentNodeType.DOCUMENT,"Document",1,1,pages,null,null,DocumentNodeDetectionOrigin.NATIVE,null,Instant.EPOCH)); }
        @Override public List<TextBlock> findByPhysicalPage(UUID ignored,int first,int last) {
            maxRequestedPages=Math.max(maxRequestedPages,last-first+1); List<TextBlock> result=new ArrayList<>();
            for(int page=first;page<=last;page++){if(page==omitPage)continue;result.add(block(page,page==wrongOrdinalPage?page+1:page));if(page==duplicatePage)result.add(block(page,page));}return result;
        }
        private TextBlock block(int page,int ordinal){String content=contents.get(page-1);return new TextBlock(UUID.randomUUID(),version,root,page,
                TextBlockType.PAGE_TEXT,ordinal,content,TextBlockExtractionMethod.NATIVE,null,Instant.EPOCH,content);}
        @Override public List<VisualSource> findVisualsByPhysicalPage(UUID ignored,int first,int last){return visuals.stream().filter(v->v.pageNumber()>=first&&v.pageNumber()<=last).toList();}
    }
}
