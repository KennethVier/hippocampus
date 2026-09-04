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
import com.hippocampus.materials.domain.PdfPageBatch;
import com.hippocampus.materials.domain.ProcessingJobType;
import com.hippocampus.materials.port.PdfExtractionPersistence;
import com.hippocampus.materials.port.PdfExtractionSource;
import com.hippocampus.materials.port.PdfExtractionSourceRepository;
import com.hippocampus.materials.port.PdfNativeTextExtractor;

class ExtractMaterialStageHandlerTests {
    @Test
    void persistsEveryBatchBeforeFinalizingAndReturning() {
        UUID versionId = UUID.randomUUID();
        List<String> calls = new ArrayList<>();
        PdfExtractionPersistence persistence = recordingPersistence(calls);
        ExtractPdfNativeText extraction = extractionThatEmits(calls, page(1, "one"), page(2, ""));
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
            public void persistNativePageBatch(UUID ignored, PdfPageBatch batch) {
                calls.add("batch-" + batch.firstPage());
                throw new IllegalStateException("persistence failed");
            }

            @Override
            public void finalizeNativePdfExtraction(UUID ignored, int pageCount) {
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

    private static ExtractPdfNativeText extractionThatEmits(List<String> calls, PdfNativePage... pages) {
        PdfExtractionSourceRepository sources = materialVersionId ->
                new PdfExtractionSource(materialVersionId,
                        new com.hippocampus.materials.port.BinaryObjectKey("object"), 42);
        PdfNativeTextExtractor extractor = (source, sink) -> {
            calls.add("extract-start");
            for (PdfNativePage page : pages) {
                sink.accept(new PdfPageBatch(page.pageNumber(), page.pageNumber(), List.of(page)));
            }
            calls.add("extract-end");
            return new PdfDocumentMetadata(pages.length, "1.7");
        };
        return new ExtractPdfNativeText(sources, extractor);
    }

    private static PdfExtractionPersistence recordingPersistence(List<String> calls) {
        return new PdfExtractionPersistence() {
            @Override
            public void persistNativePageBatch(UUID ignored, PdfPageBatch batch) {
                calls.add("batch-" + batch.firstPage());
            }

            @Override
            public void finalizeNativePdfExtraction(UUID ignored, int pageCount) {
                calls.add("finalize-" + pageCount);
            }
        };
    }

    private static PdfNativePage page(int pageNumber, String content) {
        return new PdfNativePage(pageNumber, 612, 792, content);
    }

    private static ClaimedProcessingJob job(UUID materialVersionId) {
        return new ClaimedProcessingJob(
                UUID.randomUUID(), ProcessingJobType.MATERIAL_EXTRACT, materialVersionId, "v1");
    }
}
