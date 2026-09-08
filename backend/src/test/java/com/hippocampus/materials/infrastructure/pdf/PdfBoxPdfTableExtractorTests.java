package com.hippocampus.materials.infrastructure.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.util.Matrix;
import org.junit.jupiter.api.Test;

import com.hippocampus.materials.domain.PdfTablePage;
import com.hippocampus.materials.domain.TextBlockQuality;
import com.hippocampus.materials.port.BinaryObjectKey;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.materials.port.MaterialContentInspector;
import com.hippocampus.materials.port.PdfExtractionSource;
import com.hippocampus.materials.port.PdfTableExtractionException;

class PdfBoxPdfTableExtractorTests {
    private static final float[] COLUMNS = {50, 220, 400};

    @Test
    void extractsStableMedicalTableAsStrongWithExactTabLfSerialization() throws Exception {
        byte[] pdf = pdf(page -> {
            row(page, 720, "Drug", "Mechanism", "Effect");
            row(page, 700, "Atropine", "Muscarinic antagonist", "Increased HR");
            row(page, 680, "Propranolol", "beta1 blocker", "Decreased HR");
            row(page, 660, "Electrolytes", "Na+ / Ca2+", "C5-T1");
        });

        List<PdfTablePage> pages = extract(pdf, 32, 100, 256, 32, 100_000);

        assertThat(pages).singleElement().satisfies(page -> assertThat(page.tables()).singleElement()
                .satisfies(table -> {
                    assertThat(table.quality()).isEqualTo(TextBlockQuality.STRONG);
                    assertThat(table.content()).isEqualTo(
                            "Drug\tMechanism\tEffect\n"
                            + "Atropine\tMuscarinic antagonist\tIncreased HR\n"
                            + "Propranolol\tbeta1 blocker\tDecreased HR\n"
                            + "Electrolytes\tNa+ / Ca2+\tC5-T1");
                }));
    }

    @Test
    void keepsIrregularMergedLikeRegionLimitedWithoutInventedTabs() throws Exception {
        byte[] pdf = pdf(page -> {
            row(page, 720, "Drug", "Mechanism", "Effect");
            positioned(page, 50, 700, "Merged heading");
            positioned(page, 400, 700, "Outcome");
            row(page, 680, "Atropine", "Antagonist", "Increased HR");
        });

        List<PdfTablePage> pages = extract(pdf, 32, 100, 256, 32, 100_000);

        assertThat(pages.getFirst().tables()).singleElement().satisfies(table -> {
            assertThat(table.quality()).isEqualTo(TextBlockQuality.LIMITED);
            assertThat(table.content()).doesNotContain("\t").isEqualTo(
                    "Drug Mechanism Effect\nMerged heading Outcome\nAtropine Antagonist Increased HR");
        });
    }

    @Test
    void treatsAdjacentSpanningHeaderAsComplexRatherThanFabricatingAGrid() throws Exception {
        byte[] pdf = pdf(page -> {
            positioned(page, 50, 720, "Therapeutics by receptor and response");
            row(page, 700, "Drug", "Receptor", "Response");
            row(page, 680, "Atropine", "M1", "Tachycardia");
            row(page, 660, "Propranolol", "beta1", "Bradycardia");
        });

        assertThat(extract(pdf, 32, 100, 256, 32, 100_000).getFirst().tables())
                .singleElement().satisfies(table -> {
                    assertThat(table.quality()).isEqualTo(TextBlockQuality.LIMITED);
                    assertThat(table.content()).doesNotContain("\t")
                            .startsWith("Therapeutics by receptor and response\n");
                });
    }

    @Test
    void rejectsTwoColumnTextbookProseAndNoTablePage() throws Exception {
        byte[] pdf = pdf(page -> {
            positioned(page, 50, 720, "Left-column prose sentence");
            positioned(page, 320, 720, "Right-column prose sentence");
            positioned(page, 50, 700, "More explanatory prose");
            positioned(page, 320, 700, "More explanatory prose");
            positioned(page, 50, 650, "Ordinary paragraph without a table");
        });

        assertThat(extract(pdf, 32, 100, 256, 32, 100_000).getFirst().tables()).isEmpty();
    }

    @Test
    void extractsAlignedTwoColumnMedicalTableAsStrong() throws Exception {
        byte[] pdf = pdf(page -> {
            positioned(page, 50, 720, "Nerve"); positioned(page, 300, 720, "Function");
            positioned(page, 50, 700, "Vagus"); positioned(page, 300, 700, "Parasympathetic");
            positioned(page, 50, 680, "Radial"); positioned(page, 300, 680, "Wrist extension");
        });

        assertThat(extract(pdf, 32, 100, 256, 32, 100_000).getFirst().tables())
                .singleElement().satisfies(table -> {
                    assertThat(table.quality()).isEqualTo(TextBlockQuality.STRONG);
                    assertThat(table.content()).isEqualTo(
                            "Nerve\tFunction\nVagus\tParasympathetic\nRadial\tWrist extension");
                });
    }

    @Test
    void rejectsAlignedThreeColumnProse() throws Exception {
        byte[] pdf = pdf(page -> {
            row(page, 720, "This prose sentence", "continues across columns", "without table semantics");
            row(page, 700, "Another prose sentence", "continues across columns", "without tabular labels");
            row(page, 680, "Ordinary explanatory text", "continues across columns", "as flowing prose");
        });

        assertThat(extract(pdf, 32, 100, 256, 32, 100_000).getFirst().tables()).isEmpty();
    }

    @Test
    void doesNotMergeSideBySideTablesIntoOneStrongGrid() throws Exception {
        byte[] pdf = pdf(page -> {
            fourColumns(page, 720, "Drug", "Dose", "Nerve", "Action");
            fourColumns(page, 700, "A", "1mg", "Vagus", "Slow HR");
            fourColumns(page, 680, "B", "2mg", "Radial", "Extend");
        });

        assertThat(extract(pdf, 32, 100, 256, 32, 100_000).getFirst().tables())
                .noneMatch(table -> table.quality() == TextBlockQuality.STRONG);
    }

    @Test
    void rotatedAndOverlappingLayoutCannotEstablishStrongConfidence() throws Exception {
        byte[] rotated = pdf(page -> {
            page.transform(Matrix.getRotateInstance(Math.PI / 2, 500, 100));
            row(page, 720, "A", "B", "C"); row(page, 700, "1", "2", "3"); row(page, 680, "4", "5", "6");
        });
        assertThat(extract(rotated, 32, 100, 256, 32, 100_000).getFirst().tables())
                .noneMatch(table -> table.quality() == TextBlockQuality.STRONG);

        byte[] overlap = pdf(page -> {
            positioned(page, 50, 720, "Drug"); positioned(page, 70, 720, "Dose"); positioned(page, 300, 720, "Effect");
            positioned(page, 50, 700, "Aspirin"); positioned(page, 70, 700, "5mg"); positioned(page, 300, 700, "Relief");
            positioned(page, 50, 680, "Statin"); positioned(page, 70, 680, "10mg"); positioned(page, 300, 680, "Lower LDL");
        });
        assertThat(extract(overlap, 32, 100, 256, 32, 100_000).getFirst().tables())
                .noneMatch(table -> table.quality() == TextBlockQuality.STRONG);
    }

    @Test
    void emitsTwoSeparatedTablesOnOnePageAndDoesNotDeduplicateAcrossPages() throws Exception {
        PageContent twoTables = page -> {
            row(page, 720, "A", "B", "C");
            row(page, 700, "1", "2", "3");
            row(page, 680, "4", "5", "6");
            positioned(page, 50, 630, "Paragraph separating independent regions");
            row(page, 580, "D", "E", "F");
            row(page, 560, "7", "8", "9");
            row(page, 540, "10", "11", "12");
        };
        byte[] pdf = pdf(twoTables, page -> {
            row(page, 720, "A", "B", "C");
            row(page, 700, "1", "2", "3");
            row(page, 680, "4", "5", "6");
        });

        List<PdfTablePage> pages = extract(pdf, 32, 100, 256, 32, 100_000);

        assertThat(pages).hasSize(2);
        assertThat(pages.getFirst().tables()).hasSize(2);
        assertThat(pages.get(1).tables()).singleElement()
                .extracting(table -> table.content()).isEqualTo("A\tB\tC\n1\t2\t3\n4\t5\t6");
        assertThat(pages).extracting(PdfTablePage::pageNumber).containsExactly(1, 2);
    }

    @Test
    void enforcesRowTextAndDocumentTableLimitsWithControlledFailure() throws Exception {
        byte[] oneTable = pdf(page -> {
            row(page, 720, "A", "B", "C");
            row(page, 700, "1", "2", "3");
            row(page, 680, "4", "5", "6");
        });
        assertResourceLimit(() -> extract(oneTable, 32, 100, 2, 32, 100_000));
        assertResourceLimit(() -> extract(oneTable, 32, 100, 256, 32, 5));

        byte[] twoPages = pdf(page -> {
            row(page, 720, "A", "B", "C"); row(page, 700, "1", "2", "3"); row(page, 680, "4", "5", "6");
        }, page -> {
            row(page, 720, "D", "E", "F"); row(page, 700, "7", "8", "9"); row(page, 680, "10", "11", "12");
        });
        assertResourceLimit(() -> extract(twoPages, 1, 1, 256, 32, 100_000));
    }

    @Test
    void enforcesConstructionAndPageLimitsBeforeStructuresGrowPastThem() throws Exception {
        byte[] rows = pdf(page -> {
            positioned(page, 50, 720, "one"); positioned(page, 50, 700, "two");
            positioned(page, 50, 680, "three"); positioned(page, 50, 660, "four");
        });
        assertResourceLimit(() -> extract(rows, 32, 100, 256, 32, 100_000, 3));

        byte[] columns = pdf(page -> fourColumns(page, 720, "A", "B", "C", "D"));
        assertResourceLimit(() -> extract(columns, 32, 100, 256, 3, 100_000));

        byte[] twoTables = pdf(page -> {
            row(page, 720, "A", "B", "C"); row(page, 700, "1", "2", "3"); row(page, 680, "4", "5", "6");
            positioned(page, 50, 630, "separator paragraph");
            row(page, 580, "D", "E", "F"); row(page, 560, "7", "8", "9"); row(page, 540, "10", "11", "12");
        });
        assertResourceLimit(() -> extract(twoTables, 1, 100, 256, 32, 100_000));
    }

    @Test
    void safelyDisregardsGeometryOutsideTheCropBox() throws Exception {
        byte[] pdf = pdf(page -> {
            positioned(page, 2000, 720, "A"); positioned(page, 2200, 720, "B");
            positioned(page, 2000, 700, "1"); positioned(page, 2200, 700, "2");
            positioned(page, 2000, 680, "3"); positioned(page, 2200, 680, "4");
        });
        assertThat(extract(pdf, 32, 100, 256, 32, 100_000).getFirst().tables()).isEmpty();
    }

    private static void assertResourceLimit(ThrowingOperation operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(PdfTableExtractionException.class)
                .extracting("kind").isEqualTo(PdfTableExtractionException.Kind.RESOURCE_LIMIT_EXCEEDED);
    }

    private static List<PdfTablePage> extract(
            byte[] pdf, int pageTables, int documentTables, int rows, int columns, int chars) {
        List<PdfTablePage> output = new ArrayList<>();
        BinaryObjectStore store = new ByteStore(pdf);
        PdfBoxPdfTableExtractor extractor = new PdfBoxPdfTableExtractor(
                store,
                (input, length) -> new MaterialContentInspector.Inspection("application/pdf"),
                100, 100_000, 100_000, 10_000, pageTables, documentTables, rows, columns, chars);
        int pageCount = extractor.extract(
                new PdfExtractionSource(UUID.randomUUID(), new BinaryObjectKey("objects/pdf"), pdf.length),
                output::add);
        assertThat(pageCount).isEqualTo(output.size());
        return output;
    }

    private static List<PdfTablePage> extract(
            byte[] pdf, int pageTables, int documentTables, int rows, int columns, int chars, int layoutRows) {
        List<PdfTablePage> output = new ArrayList<>();
        PdfBoxPdfTableExtractor extractor = new PdfBoxPdfTableExtractor(
                new ByteStore(pdf),
                (input, length) -> new MaterialContentInspector.Inspection("application/pdf"),
                100, 100_000, 100_000, layoutRows,
                pageTables, documentTables, rows, columns, chars);
        int pageCount = extractor.extract(
                new PdfExtractionSource(UUID.randomUUID(), new BinaryObjectKey("objects/pdf"), pdf.length),
                output::add);
        assertThat(pageCount).isEqualTo(output.size());
        return output;
    }

    private static byte[] pdf(PageContent... pages) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            for (PageContent writer : pages) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    writer.write(content);
                }
            }
            document.save(bytes);
            return bytes.toByteArray();
        }
    }

    private static void row(PDPageContentStream page, float y, String... values) throws IOException {
        for (int index = 0; index < values.length; index++) {
            positioned(page, COLUMNS[index], y, values[index]);
        }
    }

    private static void positioned(PDPageContentStream page, float x, float y, String value) throws IOException {
        page.beginText();
        page.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        page.newLineAtOffset(x, y);
        page.showText(value);
        page.endText();
    }

    private static void fourColumns(PDPageContentStream page, float y, String... values) throws IOException {
        float[] columns = {50, 150, 360, 470};
        for (int index = 0; index < values.length; index++) {
            positioned(page, columns[index], y, values[index]);
        }
    }

    @FunctionalInterface private interface PageContent { void write(PDPageContentStream page) throws Exception; }
    @FunctionalInterface private interface ThrowingOperation { void run() throws Exception; }

    private static final class ByteStore implements BinaryObjectStore {
        private final byte[] bytes;
        private ByteStore(byte[] bytes) { this.bytes = bytes; }
        @Override public void put(BinaryObjectKey key, InputStream source, long length) { throw new UnsupportedOperationException(); }
        @Override public void get(BinaryObjectKey key, OutputStream destination) {
            try { destination.write(bytes); } catch (IOException exception) { throw new AssertionError(exception); }
        }
        @Override public void delete(BinaryObjectKey key) { throw new UnsupportedOperationException(); }
    }
}
