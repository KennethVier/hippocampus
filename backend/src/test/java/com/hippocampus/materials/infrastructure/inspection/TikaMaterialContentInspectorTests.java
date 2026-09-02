package com.hippocampus.materials.infrastructure.inspection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Stream;

import org.apache.tika.Tika;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.hippocampus.materials.MaterialUploadFixtures;
import com.hippocampus.materials.port.MaterialContentInspectionException;

class TikaMaterialContentInspectorTests {

    private final TikaMaterialContentInspector inspector = new TikaMaterialContentInspector(new Tika());

    @ParameterizedTest
    @MethodSource("supportedContent")
    void detectsSupportedContentFromBytes(String expectedMimeType, byte[] bytes) {
        assertThat(inspector.inspect(new ByteArrayInputStream(bytes), bytes.length).mimeType())
                .isEqualTo(expectedMimeType);
    }

    @Test
    void detectsUnsupportedContentWithoutFilenameAuthority() {
        byte[] bytes = MaterialUploadFixtures.zipLikeUnsupported();

        assertThat(inspector.inspect(new ByteArrayInputStream(bytes), bytes.length).mimeType())
                .isNotIn("application/pdf", "image/jpeg", "image/png", "text/plain");
    }

    @Test
    void rejectsBasicInvalidPdfThatHasPdfMimeButNoEofMarker() {
        byte[] bytes = MaterialUploadFixtures.corruptPdf();

        assertThatThrownBy(() -> inspector.inspect(new ByteArrayInputStream(bytes), bytes.length))
                .isInstanceOf(MaterialContentInspectionException.class)
                .hasMessage("PDF is missing required intake markers");
    }

    @Test
    void rejectsLengthMismatchAndReadFailuresAsInvalidContent() {
        byte[] bytes = MaterialUploadFixtures.text();
        assertThatThrownBy(() -> inspector.inspect(new ByteArrayInputStream(bytes), bytes.length + 1))
                .isInstanceOf(MaterialContentInspectionException.class)
                .hasMessage("Content length mismatch");

        InputStream failing = new InputStream() {
            @Override public int read() throws IOException {
                throw new IOException("synthetic read failure");
            }
        };
        assertThatThrownBy(() -> inspector.inspect(failing, 1))
                .isInstanceOf(MaterialContentInspectionException.class)
                .hasMessage("Content inspection failed")
                .hasCauseInstanceOf(IOException.class);
    }

    private static Stream<Object[]> supportedContent() {
        return Stream.of(
                new Object[] {"application/pdf", MaterialUploadFixtures.pdf()},
                new Object[] {"image/jpeg", MaterialUploadFixtures.jpeg()},
                new Object[] {"image/png", MaterialUploadFixtures.png()},
                new Object[] {"text/plain", MaterialUploadFixtures.text()});
    }
}
