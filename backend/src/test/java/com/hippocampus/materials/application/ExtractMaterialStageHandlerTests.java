package com.hippocampus.materials.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.PdfDocumentMetadata;
import com.hippocampus.materials.domain.PdfNativePage;
import com.hippocampus.materials.domain.PdfExtractedPage;
import com.hippocampus.materials.domain.PdfPageExtractionType;
import com.hippocampus.materials.domain.PdfPageBatch;
import com.hippocampus.materials.domain.ProcessingJobType;
import com.hippocampus.materials.port.PdfExtractionPersistence;
import com.hippocampus.materials.port.PdfExtractionSource;
import com.hippocampus.materials.port.PdfExtractionSourceRepository;
import com.hippocampus.materials.port.PdfPageExtractor;

class ExtractMaterialStageHandlerTests {
    @Test
    void persistsEveryBatchBeforeFinalizingAndReturning() {
        UUID versionId = UUID.randomUUID();
        List<String> calls = new ArrayList<>();
        PdfExtractionPersistence persistence = recordingPersistence(calls);
        ExtractPdfPages extraction = extractionThatEmits(calls, page(1, "one"), page(2, ""));
        ExtractMaterialStageHandler handler = new ExtractMaterialStageHandler(
                extraction, new PersistPdfPageBatch(persistence), new FinalizePdfExtraction(persistence));

        handler.handle(job(versionId));

        assertThat(handler.jobType()).isEqualTo(ProcessingJobType.MATERIAL_EXTRACT);
        assertThat(calls).containsExactly("extract-start", "batch-1", "batch-2", "extract-end", "finalize-2");
    }

    @Test
    void persistenceFailurePreventsFinalizationAndPropagates() {
        UUID versionId = UUID.randomUUID();
        List<String> calls = new ArrayList<>();
        PdfExtractionPersistence persistence = new PdfExtractionPersistence() {
            @Override
            public void persistPageBatch(UUID ignored, PdfPageBatch batch) {
                calls.add("batch-" + batch.firstPage());
                throw new IllegalStateException("persistence failed");
            }

            @Override
            public void finalizePdfExtraction(UUID ignored, int pageCount) {
                calls.add("finalize");
            }
        };
        ExtractMaterialStageHandler handler = new ExtractMaterialStageHandler(
                extractionThatEmits(calls, page(1, "one")),
                new PersistPdfPageBatch(persistence), new FinalizePdfExtraction(persistence));

        assertThatThrownBy(() -> handler.handle(job(versionId)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("persistence failed");
        assertThat(calls).containsExactly("extract-start", "batch-1");
    }

    private static ExtractPdfPages extractionThatEmits(List<String> calls, PdfNativePage... pages) {
        PdfExtractionSourceRepository sources = materialVersionId ->
                new PdfExtractionSource(materialVersionId,
                        new com.hippocampus.materials.port.BinaryObjectKey("object"), 42);
        PdfPageExtractor extractor = (source, sink) -> {
            calls.add("extract-start");
            for (PdfNativePage page : pages) {
                sink.accept(new PdfPageBatch(
                        page.pageNumber(), page.pageNumber(), List.of(PdfExtractedPage.nativePage(page))));
            }
            calls.add("extract-end");
            return new PdfDocumentMetadata(pages.length, "1.7");
        };
        return new ExtractPdfPages(sources, extractor);
    }

    private static PdfExtractionPersistence recordingPersistence(List<String> calls) {
        return new PdfExtractionPersistence() {
            @Override
            public void persistPageBatch(UUID ignored, PdfPageBatch batch) {
                calls.add("batch-" + batch.firstPage());
            }

            @Override
            public void finalizePdfExtraction(UUID ignored, int pageCount) {
                calls.add("finalize-" + pageCount);
            }
        };
    }

    private static PdfNativePage page(int pageNumber, String content) {
        return new PdfNativePage(pageNumber, 612, 792, content, PdfPageExtractionType.NATIVE_TEXT);
    }

    private static ClaimedProcessingJob job(UUID materialVersionId) {
        return new ClaimedProcessingJob(
                UUID.randomUUID(), ProcessingJobType.MATERIAL_EXTRACT, materialVersionId, "v1");
    }
}
