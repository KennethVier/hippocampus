package com.hippocampus.materials.application;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import com.hippocampus.materials.domain.ProcessingFailure;
import com.hippocampus.materials.port.OcrException;
import com.hippocampus.materials.port.PdfExtractionException;

class ProcessingFailureClassifierTests {
    private final ProcessingFailureClassifier classifier = new ProcessingFailureClassifier();
    @Test void retriesOnlyTypedTransientFailures() {
        assertThat(classifier.classify(new QueryTimeoutException("synthetic-secret" )).kind())
                .isEqualTo(ProcessingFailure.Kind.TRANSIENT);
        assertThat(classifier.classify(new OcrException(OcrException.Kind.TIMEOUT)).kind())
                .isEqualTo(ProcessingFailure.Kind.TRANSIENT);
    }
    @Test void treatsMalformedAndUnknownFailuresAsFatalWithSafeCodes() {
        assertThat(classifier.classify(new PdfExtractionException(PdfExtractionException.Kind.MALFORMED_PDF)))
                .isEqualTo(new ProcessingFailure(ProcessingFailure.Kind.FATAL, "EXTRACTION_FAILED"));
        ProcessingFailure unknown = classifier.classify(new RuntimeException("synthetic-secret-source"));
        assertThat(unknown.kind()).isEqualTo(ProcessingFailure.Kind.FATAL);
        assertThat(unknown.errorCode()).isEqualTo("PROCESSING_INTERNAL_ERROR").doesNotContain("secret", "source");
    }
}
