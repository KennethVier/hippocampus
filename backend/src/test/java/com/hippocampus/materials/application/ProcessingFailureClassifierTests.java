package com.hippocampus.materials.application;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import java.sql.SQLException;
import com.hippocampus.materials.domain.ProcessingFailure;
import com.hippocampus.materials.port.OcrException;
import com.hippocampus.materials.port.PdfExtractionException;
import com.hippocampus.materials.port.BinaryObjectStoreException;
import com.hippocampus.materials.port.MaterialSourceValidationException;

class ProcessingFailureClassifierTests {
    private final ProcessingFailureClassifier classifier = new ProcessingFailureClassifier();
    @Test void retriesOnlyTypedTransientFailures() {
        assertThat(classifier.classify(new QueryTimeoutException("synthetic-secret" )).kind())
                .isEqualTo(ProcessingFailure.Kind.TRANSIENT);
        assertThat(classifier.classify(new OcrException(OcrException.Kind.TIMEOUT)).kind())
                .isEqualTo(ProcessingFailure.Kind.TRANSIENT);
        assertThat(classifier.classify(new DataAccessResourceFailureException("synthetic-secret")).errorCode())
                .isEqualTo("DB_UNAVAILABLE");
        assertThat(classifier.classify(new CannotGetJdbcConnectionException(
                "synthetic-secret", new SQLException("database unavailable"))).errorCode())
                .isEqualTo("DB_UNAVAILABLE");
    }
    @Test void treatsMalformedAndUnknownFailuresAsFatalWithSafeCodes() {
        assertThat(classifier.classify(new PdfExtractionException(PdfExtractionException.Kind.MALFORMED_PDF)))
                .isEqualTo(new ProcessingFailure(ProcessingFailure.Kind.FATAL, "EXTRACTION_FAILED"));
        ProcessingFailure unknown = classifier.classify(new RuntimeException("synthetic-secret-source"));
        assertThat(unknown.kind()).isEqualTo(ProcessingFailure.Kind.FATAL);
        assertThat(unknown.errorCode()).isEqualTo("PROCESSING_INTERNAL_ERROR").doesNotContain("secret", "source");
    }

    @Test void classifiesMaterialSourceValidationAsExplicitFatalFailure() {
        assertThat(classifier.classify(new MaterialSourceValidationException(
                MaterialSourceValidationException.Kind.SOURCE_NOT_AVAILABLE)))
                .isEqualTo(new ProcessingFailure(ProcessingFailure.Kind.FATAL, "SOURCE_VALIDATION_FAILED"));
        assertThat(classifier.classify(new MaterialSourceValidationException(
                MaterialSourceValidationException.Kind.SOURCE_NOT_PROCESSABLE)))
                .isEqualTo(new ProcessingFailure(ProcessingFailure.Kind.FATAL, "SOURCE_VALIDATION_FAILED"));
    }

    @Test void preservesStorageFailureAsTransient() {
        assertThat(classifier.classify(new BinaryObjectStoreException("synthetic-secret")))
                .isEqualTo(new ProcessingFailure(ProcessingFailure.Kind.TRANSIENT, "STORAGE_UNAVAILABLE"));
    }

    @Test void retriesOcrTimeoutWrappedByProductionPdfExtractionBoundary() {
        PdfExtractionException wrapped = new PdfExtractionException(
                PdfExtractionException.Kind.OCR_FAILED, new OcrException(OcrException.Kind.TIMEOUT));
        assertThat(classifier.classify(wrapped))
                .isEqualTo(new ProcessingFailure(ProcessingFailure.Kind.TRANSIENT, "OCR_UNAVAILABLE"));
    }
}
