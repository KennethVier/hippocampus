# P3-07 OCR Runtime

P3-07 uses a provider-neutral `OcrPort` backed initially by the local
Tesseract command-line engine. The application does not use an OCR SaaS,
credentials, JNI, or JNA.

## Validated runtime

- Tesseract executable: `/usr/bin/tesseract`
- Tesseract: `5.3.4-1build5` (`tesseract 5.3.4`)
- `libtesseract5`: `5.3.4-1build5`
- English trained data package: `tesseract-ocr-eng 1:4.1.0-2`
- Supported OCR language: `eng` only; OCR never translates source text.

These are Ubuntu 24.04 packages. The GitHub Actions quality job installs the
exact packages, checks the engine version and `eng` availability, and runs the
non-skipped real-engine fixtures as part of Maven verification. PILOT packaging
must supply the same runtime prerequisites when the separately owned backend
container is implemented; P3-07 does not add a Dockerfile.

## Process contract and isolation

The adapter starts this fixed argument vector with `ProcessBuilder` (not a
shell):

```text
/usr/bin/tesseract stdin stdout -l eng tsv
```

The current page is encoded as a bounded grayscale PNG and written to standard
input. Tesseract TSV is read from standard output for deterministic reading
order and word confidence. Standard output and standard error are drained
concurrently into separate bounded collectors. The child environment is
cleared and receives only controlled `LANG=C.UTF-8` and `LC_ALL=C.UTF-8`
values, so application/database/object-storage/AI credentials are not inherited.

The executable path is trusted operator configuration. Material metadata never
controls it, the language, a tessdata path, or any process argument.

## Resource and failure behavior

Defaults are configured under `hippocampus.materials.processing.pdf`:

- 300 DPI;
- maximum 10,000-pixel width and height;
- maximum 40,000,000 pixels;
- maximum 8,000 pixels on either source-image dimension;
- maximum 40,000,000 pixels per unique painted source image, including image masks;
- maximum 80,000,000 pixels across unique painted source images on one OCR page;
- maximum 25,000,000 encoded PNG bytes;
- maximum 8,000,000 stdout bytes and 65,536 stderr bytes;
- maximum 200,000 TSV rows and 100,000 characters per field;
- maximum 1,000,000 reconstructed text characters;
- 30-second OCR timeout and 2-second termination grace.

The pre-allocation canvas budget conservatively follows PDFBox 3.0.8 allocation
semantics: crop-box width and height are scaled by DPI / 72, rounded upward, and
swapped for normalized 90/270-degree rotation. `UserUnit` can make this guard
stricter when greater than 1, but can never reduce it below PDFBox's allocation
when less than 1. Painted source-image dimensions and checked pixel totals are
also inspected before OCR rendering, and PDFBox image subsampling is enabled.
Only `IMAGE_ONLY` pages are rendered, only one page raster is held at a time,
and the page resource cache is cleared after successful or failed OCR page
processing. Raster bytes are not persisted.

Unavailable engines, timeouts, non-zero exits, malformed TSV, input/output
limits, process I/O, and termination failures are distinct sanitized internal
failure kinds. Raw stderr, TSV, OCR text, images, paths, command details, storage
keys, and environment values are not logged or exposed. OCR occurs before the
existing short page-batch persistence transaction. Durable retry and recovery
remain P3-15 responsibilities.

## Quality heuristic

The adapter averages confidence across accepted Tesseract word-level TSV rows:

- `STRONG`: mean confidence at least `85.0`;
- `LIMITED`: mean confidence at least `60.0` and below `85.0`;
- `POOR`: mean confidence below `60.0`.

These named boundaries are calibrated implementation heuristics exercised by
clear, degraded, blank, and boundary fixtures. They are not medical-correctness
thresholds. Raw Tesseract confidence is never persisted. A successful engine
run with no usable word returns an explicit no-text outcome and persists the
page as blank `OCR / POOR`.

## Validation

After installing the exact packages above:

```bash
tesseract --version
tesseract --list-langs
cd backend
./mvnw -B -ntp -Dtest=TesseractRealEngineTests test
```

The real-engine suite must execute three tests (strong, degraded/poor, and
no-text) with zero skips. A skipped suite is not accepted as validation.
