package com.hippocampus.materials.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.PdfDocumentMetadata;
import com.hippocampus.materials.domain.ProcessingJobType;
import com.hippocampus.materials.port.BinaryObjectKey;
import com.hippocampus.materials.port.PdfPageBatchSink;
import com.hippocampus.materials.port.PdfExtractionSource;

class ExtractPdfNativeTextTests {
    @Test
    void resolvesAuthoritativeSourceAndDelegatesTheCallerOwnedSink() {
        UUID versionId = UUID.randomUUID();
        PdfExtractionSource source = new PdfExtractionSource(
                versionId, new BinaryObjectKey("materials/test/original"), 123);
        Object[] received = new Object[2];
        ExtractPdfNativeText useCase = new ExtractPdfNativeText(
                requestedId -> {
                    assertThat(requestedId).isEqualTo(versionId);
                    return source;
                },
                (resolvedSource, sink) -> {
                    received[0] = resolvedSource;
                    received[1] = sink;
                    return new PdfDocumentMetadata(3, "1.6");
                });
        PdfPageBatchSink sink = batch -> {};

        PdfDocumentMetadata result = useCase.execute(new ClaimedProcessingJob(
                UUID.randomUUID(), ProcessingJobType.MATERIAL_EXTRACT, versionId, "v1"), sink);

        assertThat(result).isEqualTo(new PdfDocumentMetadata(3, "1.6"));
        assertThat(received).containsExactly(source, sink);
    }

    @Test
    void rejectsAnyJobOtherThanMaterialExtractWithVersionIdentity() {
        ExtractPdfNativeText useCase = new ExtractPdfNativeText(
                ignored -> { throw new AssertionError("source must not be resolved"); },
                (source, sink) -> { throw new AssertionError("extractor must not run"); });

        assertThatThrownBy(() -> useCase.execute(new ClaimedProcessingJob(
                UUID.randomUUID(), ProcessingJobType.MATERIAL_VALIDATE, UUID.randomUUID(), "v1"), batch -> {}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.execute(new ClaimedProcessingJob(
                UUID.randomUUID(), ProcessingJobType.MATERIAL_EXTRACT, null, "v1"), batch -> {}))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
