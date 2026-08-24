---
Audience: Backend, architecture, AI/RAG, QA, security, DevOps, and
  frontend contributors.
Authors: Project Hippocampus Team
Created: 2026-08-24
Document ID: 21
Last Updated: 2026-08-24
Owner: Project Hippocampus Team
Prerequisites:
- 07 - Feature Specifications
- 08 - Non-Functional Requirements
- 09 - MVP Scope & Roadmap
- 13 - RAG Architecture
- 14 - Knowledge Base Design
- 16 - System Architecture v1.1+
- 17 - Technology Stack & ADR Baseline
- 18 - Domain Model & Database Design
- 19 - Backend Architecture
- 20 - Frontend Architecture
Purpose: Define how Hippocampus validates, stores, extracts, structures,
  chunks, embeds, indexes, retries, and activates uploaded learning
  materials for safe use in RAG and Study Missions.
Related Documents:
- 22 - Security & Privacy Architecture
- 23 - Deployment & Infrastructure
- 24 - Observability & Operations
- 25 - Testing Strategy
- 26 - Development Roadmap & Implementation Phases
- 27 - Decision Log / ADR Index
Scope: Upload validation, file limits, storage, PDF parsing, scanned-PDF
  handling, OCR boundary, image extraction, table handling, transcript
  processing, limited video support, structure detection, large-file
  batching, chunking orchestration, embedding/indexing, durable jobs,
  retry/idempotency, progress, activation, failure states, deletion, and
  processing security.
Status: Final
Title: File Processing & Ingestion Architecture
Version: 1.0.0
---

# 21 - File Processing & Ingestion Architecture

## 1. Purpose

This document defines how Hippocampus converts student-provided files
into structured, searchable, source-traceable evidence.

It answers:

> **How should Hippocampus safely process large, mixed-format medical
> learning materials without losing structure, visuals, provenance, or
> reliability?**

The ingestion pipeline must support the RAG contract established in
Document 13.

------------------------------------------------------------------------

# 2. Locked Ingestion Principle

> **Ingestion is not text extraction. Ingestion is source normalization
> with preserved structure, provenance, and processing quality.**

The anti-pattern is:

``` text
Upload PDF
↓
Extract all text
↓
Split every N characters
↓
Embed
```

The intended flow is:

``` text
Upload
↓
Validate
↓
Store Original
↓
Inspect
↓
Extract
↓
Preserve Structure
↓
Associate Visuals / Tables
↓
Normalize
↓
Segment
↓
Chunk
↓
Embed
↓
Index
↓
Validate
↓
Activate
```

------------------------------------------------------------------------

# 3. Supported v1 Input Types

## Full v1 Support

-   PDF
-   Image
-   Plain text
-   Pasted notes
-   Transcript text

## Limited v1 Support

-   Video only through transcript-derived content where available

## Deferred

-   Full arbitrary video understanding
-   Continuous frame/scene interpretation
-   Automatic advanced medical-image interpretation
-   Full audio transcription pipeline unless explicitly added later

------------------------------------------------------------------------

# 4. Processing Boundaries

Ingestion has four major boundaries:

``` text
1. Intake
2. Extraction
3. Normalization / Structure
4. Retrieval Preparation
```

These correspond to:

``` text
Upload
↓
Store
↓
Parse
↓
Structure
↓
Chunk
↓
Embed
↓
Index
```

Each stage should be observable and retryable.

------------------------------------------------------------------------

# 5. Upload Acceptance Flow

``` mermaid
flowchart TD

A[Student Selects File]
--> B[Client Basic Validation]

B --> C[Upload to Spring Boot]
C --> D[Backend Transport Validation]

D --> E{Supported?}
E -->|No| F[Reject]
E -->|Yes| G[Store Original]

G --> H[Create Material]
H --> I[Create MaterialVersion]
I --> J[Create Processing Job]
J --> K[Return PROCESSING]
```

The upload HTTP request must not wait for full processing.

------------------------------------------------------------------------

# 6. Original File Preservation

The original user upload should be preserved until deletion/retention
policy requires removal.

Why?

-   Provenance
-   Reprocessing
-   Source display
-   Parser upgrades
-   OCR improvements
-   Audit/debugging
-   Re-indexing

Derived content must not replace the original file.

------------------------------------------------------------------------

# 7. Object Storage Boundary

Original binary files and extracted visuals live in the binary storage
layer.

Conceptually:

``` text
materials/{userId}/{materialId}/{versionId}/original.pdf
materials/{userId}/{materialId}/{versionId}/visuals/...
```

Exact provider and path naming are deployment concerns.

Storage keys are backend-managed.

Clients never choose authoritative storage paths.

------------------------------------------------------------------------

# 8. File Validation

Backend validation should include:

-   Declared MIME type
-   Detected MIME type
-   File signature where practical
-   File size
-   Empty file
-   Corruption
-   Encryption/password protection
-   Supported extension
-   Basic content readability
-   Image dimensions
-   Page count where determinable

Client-side checks are convenience only.

------------------------------------------------------------------------

# 9. MIME Trust Rule

> **Do not trust filename extension or browser-declared MIME type
> alone.**

Use content inspection through the backend parsing stack.

Apache Tika is the baseline content-type/metadata inspection layer.

------------------------------------------------------------------------

# 10. File/Page Limits

Exact production limits are not hard-coded in this architecture
document.

v1 must define configurable limits for:

``` text
max_file_size
max_pdf_pages
max_image_dimensions
max_transcript_length
max_materials_per_user
max_concurrent_processing_jobs_per_user
```

Limits are benchmark-derived and can differ by deployment tier.

The architecture must support large medical PDFs, including hundreds of
pages, when within configured limits.

------------------------------------------------------------------------

# 11. Large PDF Principle

A 600+ page PDF is a supported architectural case.

It must not be processed:

-   in one blocking web request;
-   as one in-memory string;
-   as one topic;
-   as one embedding unit.

It should be processed incrementally.

------------------------------------------------------------------------

# 12. Large PDF Processing Model

``` text
600+ Page PDF
      ↓
Read Metadata
      ↓
Determine Page Count
      ↓
Create Batches
      ↓
Extract Per Batch
      ↓
Persist Intermediate Blocks
      ↓
Detect Hierarchy
      ↓
Build Chunks
      ↓
Embed in Batches
      ↓
Index
```

------------------------------------------------------------------------

# 13. Page Batch Strategy

Large PDFs should process bounded ranges.

Conceptual example:

``` text
pages 1–25
pages 26–50
pages 51–75
...
```

Exact batch size is configurable and benchmarked.

Benefits:

-   Memory control
-   Retry isolation
-   Progress reporting
-   Reduced transaction size
-   Partial processing recovery

------------------------------------------------------------------------

# 14. PDF Parsing Stack

Baseline Java stack:

``` text
Apache Tika
+
Apache PDFBox
```

Tika responsibilities:

-   MIME detection
-   Metadata
-   General parsing support

PDFBox responsibilities:

-   Page access
-   Native text extraction
-   Embedded image extraction
-   Page dimensions
-   PDF structure inspection where feasible

------------------------------------------------------------------------

# 15. Native Text Detection

Each PDF page should be classified conceptually as:

``` text
NATIVE_TEXT
MIXED
IMAGE_ONLY
UNREADABLE
```

This drives extraction behavior.

------------------------------------------------------------------------

# 16. Scanned PDF Handling

For image-only/scanned pages:

``` text
Page
↓
No useful native text
↓
OCR Adapter
↓
OCR Result
↓
Quality Classification
```

OCR-derived output must carry:

``` text
extractionMethod = OCR
quality = STRONG | LIMITED | POOR
```

or equivalent.

------------------------------------------------------------------------

# 17. OCR Adapter Boundary

The backend should expose a provider-neutral OCR port.

Conceptually:

``` text
OcrPort
  ↓
OcrResult
```

Exact OCR engine is intentionally deferred.

Possible future implementations may be:

-   Local OCR
-   Cloud OCR
-   Specialized document OCR

The rest of the ingestion pipeline must not depend on
OCR-vendor-specific DTOs.

------------------------------------------------------------------------

# 18. OCR Safety Rule

> **OCR output is evidence with uncertainty, not unquestioned truth.**

If OCR quality is poor:

-   mark source quality;
-   avoid confident source-grounded generation;
-   preserve original image;
-   surface limitation in EvidencePackage when relevant.

------------------------------------------------------------------------

# 19. Text Block Extraction

Before chunking, persist normalized blocks.

Conceptual block types:

``` text
HEADING
PARAGRAPH
LIST
CAPTION
TABLE_TEXT
TRANSCRIPT
```

Each block preserves:

-   page/timestamp
-   source order
-   extraction method
-   quality
-   parent DocumentNode

------------------------------------------------------------------------

# 20. Structure Detection

Structure detection should use layered signals.

## Priority 1 --- Native Structure

-   PDF outline/bookmarks
-   TOC
-   native heading metadata

## Priority 2 --- Layout/Heuristics

-   font size
-   font weight
-   numbering
-   spacing
-   capitalization
-   repeated headers/footers
-   section patterns

## Priority 3 --- AI Assistance

Only when hierarchy is ambiguous.

AI-assisted structure output must be:

-   structured;
-   bounded;
-   versioned;
-   labeled as AI-assisted;
-   not treated as infallible.

------------------------------------------------------------------------

# 21. Document Hierarchy Output

Conceptually:

``` text
DOCUMENT
└── CHAPTER
    └── SECTION
        └── SUBSECTION
```

Each node preserves:

-   title
-   source order
-   page range
-   detection origin
-   confidence/quality

------------------------------------------------------------------------

# 22. Table of Contents Handling

When a TOC is available:

-   use it as a structural signal;
-   map headings to likely page regions;
-   verify against actual page content where possible.

Do not assume the TOC is perfect.

Printed page numbers may differ from PDF page indexes.

------------------------------------------------------------------------

# 23. Header/Footer Removal

Repeated page elements such as:

``` text
book title
chapter title
page number
copyright footer
```

should be detected and excluded from semantic chunks where safe.

But page number metadata itself must be preserved.

------------------------------------------------------------------------

# 24. Hyphenation / Line-Wrap Normalization

PDF extraction may produce:

``` text
cardio-
vascular
```

Normalization may reconstruct:

``` text
cardiovascular
```

only where confidence is high.

Do not aggressively rewrite medical terms.

------------------------------------------------------------------------

# 25. Medical Symbol Preservation

Preserve terms such as:

``` text
Na+
K+
Ca2+
β1
C5-T1
CN VII
IL-6
pH
```

Normalization must not destroy retrieval-relevant symbols.

------------------------------------------------------------------------

# 26. Image Extraction

Embedded source visuals should be extracted when technically feasible.

For each visual:

-   preserve original image bytes;
-   store page;
-   associate caption;
-   associate nearby text;
-   associate DocumentNode;
-   assign processing status.

------------------------------------------------------------------------

# 27. Visual Types

Initial categories may include:

``` text
ANATOMY_DIAGRAM
HISTOLOGY
PATHOLOGY
RADIOLOGY
FLOW_DIAGRAM
TABLE
CHART
SLIDE_FIGURE
OTHER
```

Classification may be:

-   heuristic;
-   metadata-based;
-   AI-assisted.

It must not be used to make unvalidated clinical claims.

------------------------------------------------------------------------

# 28. Caption Association

Caption detection should use:

-   text proximity;
-   figure numbering;
-   page layout;
-   structural context.

Example:

``` text
Figure 10-2
SA Nodal Action Potential
```

should remain linked to the visual.

------------------------------------------------------------------------

# 29. Nearby Text Association

A visual may also link to surrounding explanatory paragraphs.

Conceptually:

``` text
Visual
↔ Caption
↔ Nearby TextBlocks
↔ DocumentNode
```

This relationship later supports visual-aware retrieval.

------------------------------------------------------------------------

# 30. Image-Only Uploads

Standalone images follow:

``` text
Validate
↓
Store Original
↓
Metadata
↓
Optional OCR
↓
Optional Visual Classification
↓
Create VisualAsset
↓
Link to Topic/Material
```

Do not require an AI description for the image to be usable.

------------------------------------------------------------------------

# 31. Table Handling

Medical sources frequently contain tables.

Preferred behavior:

1.  detect table-like structure;
2.  preserve row/column semantics where feasible;
3.  store table text with source order;
4.  avoid flattening into meaningless text.

Example:

``` text
Drug | Mechanism | Effect
```

should remain semantically recoverable.

------------------------------------------------------------------------

# 32. Table Fallback

If structure cannot be reliably parsed:

-   preserve page image/visual;
-   preserve nearby extracted text;
-   mark table quality as limited;
-   do not fabricate cell relationships.

------------------------------------------------------------------------

# 33. Transcript Processing

Transcript ingestion should preserve:

-   text
-   timestamps when available
-   speaker markers when useful
-   section/topic grouping
-   source order

------------------------------------------------------------------------

# 34. Transcript Normalization

Normalize:

-   filler where safe;
-   duplicate false starts;
-   timestamp markers;
-   obvious transcription artifacts.

Do not rewrite lecture meaning.

Original transcript should remain recoverable where appropriate.

------------------------------------------------------------------------

# 35. Video Boundary

For v1:

``` text
Video
↓
Transcript Available?
   ├── Yes → Transcript Processing
   └── No → Unsupported / Limited
```

Full video/frame understanding is deferred.

------------------------------------------------------------------------

# 36. Educational Segmentation

After extraction:

``` text
Document Hierarchy
+
TextBlocks
+
Visuals
↓
EducationalSegmenter
```

This stage identifies coherent source units before chunking.

------------------------------------------------------------------------

# 37. Chunking Pipeline

``` text
DocumentNode
↓
Ordered TextBlocks
↓
Semantic Boundary Detection
↓
Token Limit Enforcement
↓
Conservative Overlap
↓
Chunk
```

Semantic/document structure takes precedence over arbitrary size.

------------------------------------------------------------------------

# 38. Chunk Quality Metadata

Each chunk should inherit/derive:

-   extraction method
-   source quality
-   page range
-   heading path
-   visual links
-   content type

Poor upstream quality must not disappear during chunking.

------------------------------------------------------------------------

# 39. Chunk Versioning

Chunk generation should have an explicit processing/chunking version.

Example:

``` text
chunking_version = CHUNKER_V1
```

If chunking changes materially:

``` text
New IndexGeneration
+
Reprocessing
```

should occur rather than silently mixing outputs.

------------------------------------------------------------------------

# 40. Embedding Stage

Chunks eligible for retrieval are embedded in batches.

Conceptually:

``` text
Active Candidate Chunks
↓
Embedding Provider
↓
Embedding Batch
↓
Persist ChunkEmbedding
```

The embedding provider is selected through configuration/evaluation.

------------------------------------------------------------------------

# 41. Embedding Idempotency

Embedding job should uniquely target:

``` text
chunkId
+
indexGenerationId
```

If that pair already has a valid embedding, retry should not create a
duplicate.

------------------------------------------------------------------------

# 42. Embedding Failure

If a subset fails:

-   record failed chunk IDs;
-   retry boundedly;
-   allow PARTIALLY_READY only if remaining evidence is still
    safe/useful;
-   do not silently mark READY.

------------------------------------------------------------------------

# 43. Indexing Stage

After embeddings and search fields are ready:

-   build/refresh retrieval indexes;
-   verify expected records;
-   validate source references;
-   validate active-version eligibility.

Only then can activation proceed.

------------------------------------------------------------------------

# 44. Material Activation

A MaterialVersion becomes `READY` only when required v1 capabilities are
satisfied.

Example minimum:

``` text
source stored
extraction completed
structure persisted
chunks persisted
search/index ready
source references valid
```

If visual extraction partially fails but text is reliable:

``` text
PARTIALLY_READY
```

may be appropriate.

------------------------------------------------------------------------

# 45. READY vs PARTIALLY_READY

## READY

Required ingestion stages succeeded.

## PARTIALLY_READY

Core learning can proceed, but known limitations exist.

Examples:

-   some OCR pages poor;
-   some visuals failed;
-   some table extraction failed.

The backend must expose capability/limitation metadata.

------------------------------------------------------------------------

# 46. FAILED

Use `FAILED` when the material cannot safely support expected learning
behavior.

Examples:

-   unreadable source;
-   corrupted file;
-   complete extraction failure;
-   index activation failure after retries.

------------------------------------------------------------------------

# 47. UNSUPPORTED

Use `UNSUPPORTED` when the input type or condition is intentionally
outside v1 capability.

Examples:

-   encrypted PDF with no password flow;
-   unsupported archive format;
-   video without transcript where no transcription service exists.

------------------------------------------------------------------------

# 48. Processing Job Pipeline

Recommended stages:

``` text
MATERIAL_VALIDATE
↓
MATERIAL_EXTRACT
↓
STRUCTURE_DETECT
↓
VISUAL_EXTRACT
↓
NORMALIZE
↓
CHUNK
↓
EMBED
↓
INDEX
↓
ACTIVATE
```

Cleanup may run independently.

------------------------------------------------------------------------

# 49. Processing Job State

Each job:

``` text
PENDING
RUNNING
RETRY
COMPLETED
FAILED
CANCELLED
```

Material overall state is derived from required stage state.

------------------------------------------------------------------------

# 50. Job Progress

Progress should be meaningful, not fake.

Examples:

``` text
pages extracted / total pages
chunks embedded / total chunks
```

Avoid progress that increments only by arbitrary timers.

------------------------------------------------------------------------

# 51. Frontend Progress Mapping

Technical stages map to user-friendly messages.

Example:

``` text
MATERIAL_EXTRACT
→ Reading your material

STRUCTURE_DETECT
→ Detecting chapters and sections

VISUAL_EXTRACT
→ Preparing figures and images

CHUNK / EMBED / INDEX
→ Preparing this material for study
```

The frontend does not need internal implementation terminology.

------------------------------------------------------------------------

# 52. Durable Job Claiming

Processing jobs are stored in PostgreSQL.

Worker claims use atomic locking.

Conceptually:

``` text
FOR UPDATE SKIP LOCKED
```

or equivalent.

This enables future multiple workers without duplicate processing.

------------------------------------------------------------------------

# 53. Job Retry Policy

Retries should be bounded and failure-specific.

Retry candidates:

-   temporary object-storage error;
-   provider timeout;
-   temporary embedding API rate limit;
-   transient DB issue.

Do not retry indefinitely:

-   corrupted PDF;
-   unsupported encryption;
-   invalid file type.

------------------------------------------------------------------------

# 54. Retry Backoff

Use configured backoff for transient errors.

Examples:

``` text
short delay
↓
longer delay
↓
final failure
```

Respect provider `Retry-After` when applicable.

------------------------------------------------------------------------

# 55. Idempotency

Every processing stage should be safe to retry.

Examples:

-   extraction replaces/upserts same processing-version output;
-   visual assets deduplicate using stable source identity/content hash;
-   chunk generation uses versioned chunk IDs/indexes;
-   embedding enforces unique `(chunk, generation)`;
-   activation is transactionally repeatable.

------------------------------------------------------------------------

# 56. Cancellation

If student deletes a Material during processing:

``` text
Material marked deletion-pending/inactive
↓
future jobs stop
↓
running job detects cancellation
↓
derived retrieval state removed
↓
binary cleanup
```

Do not continue indexing deleted material.

------------------------------------------------------------------------

# 57. Reprocessing

Reprocessing may occur when:

-   parser improves;
-   OCR strategy changes;
-   chunking changes;
-   embedding model changes;
-   source version changes.

Reprocessing must create explicit processing/index versions.

------------------------------------------------------------------------

# 58. Material Replacement

Replacing a PDF creates:

``` text
Material
└── MaterialVersion v2
```

Do not overwrite v1 in place.

v2 processes independently.

After successful activation:

``` text
active_version_id → v2
```

Historical provenance may still point to v1.

------------------------------------------------------------------------

# 59. Processing Transactions

Long work happens outside long DB transactions.

Use short transactions for:

-   claim;
-   persist batch;
-   update progress;
-   activate;
-   fail.

Do not hold row locks during PDF parsing or API calls.

------------------------------------------------------------------------

# 60. Intermediate Persistence

For large files, persist intermediate extraction results.

Benefits:

-   restart from last stage;
-   no need to reparse 600 pages after embedding failure;
-   easier diagnostics.

TextBlocks provide one such durable intermediate representation.

------------------------------------------------------------------------

# 61. Memory Safety

Processing must avoid:

-   loading full 600-page PDF text into one huge String unnecessarily;
-   retaining all page images simultaneously;
-   embedding all chunks in one request.

Use streams/batches.

------------------------------------------------------------------------

# 62. Temporary Files

If parsers require local temp files:

-   use controlled temp directory;
-   random/non-user-controlled names;
-   size limits;
-   cleanup;
-   no public exposure.

Exact deployment temp-volume strategy belongs to Document 23.

------------------------------------------------------------------------

# 63. File Decompression Bombs

If compressed/embedded content can expand dramatically, processing
should enforce resource limits.

The ingestion system should detect/abort suspicious resource
amplification.

Detailed security thresholds belong to Document 22.

------------------------------------------------------------------------

# 64. Parser Isolation

File parsers process untrusted input.

Design should allow:

-   bounded memory;
-   bounded processing time;
-   timeout;
-   controlled filesystem access;
-   future worker isolation if needed.

v1 may run workers in the same application process initially, but
architecture should permit later process separation.

------------------------------------------------------------------------

# 65. Processing Timeout

Each stage should have a configurable timeout.

Examples:

``` text
page extraction timeout
OCR timeout
embedding request timeout
index job timeout
```

Timeout must produce an explicit failure/retry state.

------------------------------------------------------------------------

# 66. Source Quality Classification

Persist source quality signals such as:

``` text
STRONG
LIMITED
POOR
```

or equivalent.

Possible inputs:

-   extraction method;
-   OCR confidence;
-   missing pages;
-   corrupted regions;
-   table/visual failures.

RAG later receives these signals.

------------------------------------------------------------------------

# 67. Retrieval Eligibility

Only chunks satisfying:

``` text
active material version
+
active chunk
+
authorized user
+
acceptable source quality
```

should be considered for normal retrieval.

Exact quality threshold may depend on grounding mode/task.

------------------------------------------------------------------------

# 68. STRICT_SOURCE and Poor OCR

If STRICT_SOURCE task depends on poor OCR evidence:

``` text
RAG quality = LIMITED / INSUFFICIENT
```

rather than confident generation.

The ingestion layer enables this by preserving extraction quality.

------------------------------------------------------------------------

# 69. Source Reference Generation

Source references should be created from stable source positions.

Examples:

``` text
PDF page 121
Section: SA Node
Chunk: internal id
Visual: figure id
Transcript: 13:20–15:10
```

Learner-facing display label should remain understandable.

------------------------------------------------------------------------

# 70. Page Number Distinction

A PDF may have:

``` text
PDF index page = 130
Printed book page = 121
```

Where detectable, preserve both.

Do not assume they are identical.

------------------------------------------------------------------------

# 71. Duplicate Upload Detection

Content hashes may identify exact duplicate files.

Possible behavior:

-   warn student;
-   reuse processing where safe;
-   create separate logical Material only if user intends.

Do not automatically merge sources without product policy.

------------------------------------------------------------------------

# 72. Duplicate Page/Chunk Handling

Repeated headers/footers and duplicated pages should be detected where
practical.

Chunk deduplication must not remove intentional repeated educational
content blindly.

------------------------------------------------------------------------

# 73. Language Boundary

v1 extraction may support multilingual source text if parsers/AI
providers handle it.

However, OCR quality and prompt evaluation may differ by language.

Language detection should be captured as metadata where useful.

Do not silently translate source material during ingestion.

------------------------------------------------------------------------

# 74. Encoding Normalization

Plain text/transcripts should normalize encoding to UTF-8.

Invalid byte sequences should produce explicit processing diagnostics.

------------------------------------------------------------------------

# 75. Text Input

Pasted text follows a simplified path:

``` text
Validate Length
↓
Normalize UTF-8
↓
Detect Headings
↓
Create TextBlocks
↓
Chunk
↓
Embed
↓
Index
```

No binary extraction stage is needed.

------------------------------------------------------------------------

# 76. Image Input

Standalone image path:

``` text
Validate
↓
Store
↓
Extract Metadata
↓
Optional OCR
↓
Create VisualAsset
↓
Create TextBlock if reliable OCR exists
↓
Embed/index associated text
↓
READY / PARTIAL
```

------------------------------------------------------------------------

# 77. Transcript Input

Transcript path:

``` text
Validate
↓
Normalize
↓
Parse timestamps
↓
Segment by topic/window
↓
Create DocumentNodes/TextBlocks
↓
Chunk
↓
Embed
↓
Index
```

------------------------------------------------------------------------

# 78. Processing Capability Metadata

Each MaterialVersion should expose capabilities.

Conceptually:

``` text
textSearchAvailable
visualsAvailable
ocrUsed
structureDetected
tablesDetected
partialFailures
```

This helps Learning Engine avoid unsupported activity types.

------------------------------------------------------------------------

# 79. Visual Capability

Example:

``` text
visualsAvailable = false
```

means Learning Engine should not create source-dependent visual
activities from that material.

------------------------------------------------------------------------

# 80. Structure Capability

If hierarchy detection fails:

-   material may still be searchable;
-   retrieval scope becomes broader;
-   user-visible detected structure may be unavailable;
-   material may be PARTIALLY_READY.

------------------------------------------------------------------------

# 81. User Confirmation

Detected hierarchy may be shown to student for selection/confirmation.

User confirmation should update mapping metadata.

It should not rewrite original source structure destructively.

------------------------------------------------------------------------

# 82. Material-to-Topic Mapping

Ingestion may suggest mappings.

Examples:

``` text
Chapter: ECG
→ Suggested Topic: ECG

Section: QRS Complex
→ Suggested Subtopic: QRS Complex
```

Student/app may accept.

AI suggestion remains explicit as `AI_ASSISTED`.

------------------------------------------------------------------------

# 83. AI Usage in Ingestion

AI may assist only where value justifies it.

Potential tasks:

-   ambiguous heading hierarchy;
-   section labeling;
-   visual classification;
-   topic suggestion.

Avoid using AI for:

-   basic MIME validation;
-   page counting;
-   obvious structural metadata;
-   deterministic file checks.

------------------------------------------------------------------------

# 84. AI Cost Control

For large PDFs, do not send every page to Gemini/Ollama merely to infer
structure.

Use:

``` text
native metadata
+
heuristics
+
sampled/ambiguous segments
```

and reserve AI assistance for uncertainty.

------------------------------------------------------------------------

# 85. External Provider Failure During Ingestion

If embedding provider is unavailable:

``` text
extraction remains persisted
↓
job retries later
```

Do not throw away already-completed extraction work.

------------------------------------------------------------------------

# 86. Embedding Provider Switch

If embedding provider/model changes:

``` text
new IndexGeneration
↓
reuse existing chunks
↓
re-embed
↓
evaluate
↓
activate
```

No need to re-extract the source unless chunking/source processing also
changed.

------------------------------------------------------------------------

# 87. Ingestion Observability

Capture:

``` text
materialId
versionId
jobId
stage
pagesProcessed
chunksProduced
visualsProduced
ocrPages
failedPages
embeddingCount
duration
retryCount
errorCode
```

Avoid logging source content unnecessarily.

------------------------------------------------------------------------

# 88. Processing Metrics

Useful metrics:

``` text
material_processing_duration
material_processing_failure_rate
pages_processed_total
ocr_pages_total
chunks_generated_total
visuals_extracted_total
embedding_requests_total
embedding_failures_total
processing_queue_depth
```

------------------------------------------------------------------------

# 89. Failure Taxonomy

Recommended codes:

``` text
UNSUPPORTED_FILE_TYPE
FILE_TOO_LARGE
TOO_MANY_PAGES
ENCRYPTED_PDF
CORRUPT_FILE
EXTRACTION_FAILED
OCR_FAILED
STRUCTURE_DETECTION_FAILED
VISUAL_EXTRACTION_FAILED
CHUNKING_FAILED
EMBEDDING_FAILED
INDEXING_FAILED
ACTIVATION_FAILED
PROCESSING_TIMEOUT
STORAGE_FAILURE
```

------------------------------------------------------------------------

# 90. Failure Severity

## Fatal

Material cannot be safely used.

Examples:

-   corrupt file
-   unsupported encryption
-   complete extraction failure

## Partial

Material may still support limited learning.

Examples:

-   some visuals failed
-   some OCR pages poor
-   table parsing limited

## Transient

Retry may succeed.

Examples:

-   provider timeout
-   DB/network issue
-   rate limit

------------------------------------------------------------------------

# 91. Processing Recovery

When job restarts:

``` text
Inspect completed stages
↓
Resume from first incomplete valid stage
```

Do not always restart from upload.

------------------------------------------------------------------------

# 92. Activation Consistency

Activation should be one short transaction.

Conceptually:

``` text
verify processing state
verify index generation
verify source references
set MaterialVersion READY
set Material.active_version_id
deactivate old version retrieval
commit
```

------------------------------------------------------------------------

# 93. Index Rebuildability

All vector/lexical retrieval artifacts should be reproducible from:

``` text
Original Material
+
MaterialVersion
+
TextBlocks
+
Chunks
+
Processing Configuration
```

Search state must not become irreplaceable.

------------------------------------------------------------------------

# 94. Deletion Flow

``` mermaid
flowchart TD

A[Student Deletes Material]
--> B[Authorize]
B --> C[Disable Retrieval Immediately]
C --> D[Cancel Pending Jobs]
D --> E[Mark Deletion State]
E --> F[Delete Derived Index Records]
F --> G[Delete Visual/Derived Assets]
G --> H[Delete Original Binary Per Policy]
H --> I[Finalize]
```

Exact retention rules belong to Document 22.

------------------------------------------------------------------------

# 95. Large PDF End-to-End Sequence

``` mermaid
sequenceDiagram
    actor Student
    participant UI as React
    participant API as Spring Boot
    participant Storage as Object Storage
    participant Jobs as Processing Worker
    participant DB as PostgreSQL
    participant Embed as Embedding Provider

    Student->>UI: Upload 600-page PDF
    UI->>API: multipart upload
    API->>Storage: Store original
    API->>DB: Create Material + Version + Job
    API-->>UI: PROCESSING

    loop Page batches
        Jobs->>Storage: Read source
        Jobs->>Jobs: Extract batch
        Jobs->>DB: Persist TextBlocks / progress
    end

    Jobs->>Jobs: Detect hierarchy
    Jobs->>DB: Persist DocumentNodes

    Jobs->>Jobs: Extract visuals
    Jobs->>DB: Persist VisualAssets

    Jobs->>Jobs: Build chunks
    Jobs->>DB: Persist Chunks

    loop Embedding batches
        Jobs->>Embed: Embed chunks
        Embed-->>Jobs: vectors
        Jobs->>DB: Persist embeddings
    end

    Jobs->>DB: Validate index + activate
    DB-->>Jobs: READY

    UI->>API: Poll material state
    API-->>UI: READY + detected structure
```

------------------------------------------------------------------------

# 96. Mixed PDF Example

Example:

``` text
Page 1–10   TOC
Page 11–80  Anatomy
Page 81–160 Physiology
Page 161    Diagram-heavy summary
Page 162–250 Pathology
...
```

Hippocampus should produce:

``` text
MaterialVersion
├── DocumentNodes by chapter/section
├── TextBlocks
├── Chunks
├── VisualAssets
└── Topic mappings
```

not:

``` text
one topic
one text blob
```

------------------------------------------------------------------------

# 97. Ingestion Security Boundary

All uploaded content is untrusted.

Never allow source content to:

-   modify prompt policy;
-   execute code;
-   choose backend file paths;
-   access arbitrary filesystem locations;
-   bypass user ownership;
-   influence authorization.

Detailed controls belong to Document 22.

------------------------------------------------------------------------

# 98. Parser Versioning

Persist processing versions where useful:

``` text
extractor_version
structure_detector_version
chunking_version
ocr_version
```

This allows targeted reprocessing after parser upgrades.

------------------------------------------------------------------------

# 99. Reprocessing Trigger Matrix

  Change                              Re-extract?   Re-chunk?                  Re-embed?
  ------------------------------ ---------------- ----------- --------------------------
  New source version                          Yes         Yes                        Yes
  OCR engine change                Affected pages     Usually                        Yes
  Structure detector change                 Maybe     Usually                        Yes
  Chunking algorithm change                    No         Yes                        Yes
  Embedding model change                       No          No                        Yes
  Vector index parameters only                 No          No   Maybe rebuild index only

------------------------------------------------------------------------

# 100. Ingestion Testing Requirements

Test fixtures should include:

-   native-text PDF;
-   scanned PDF;
-   mixed text/image PDF;
-   600+ page PDF;
-   password-protected PDF;
-   corrupt PDF;
-   image-only upload;
-   table-heavy PDF;
-   lecture slides;
-   transcript with timestamps;
-   malformed text encoding;
-   duplicate upload;
-   partial OCR failure.

------------------------------------------------------------------------

# 101. Golden Ingestion Fixtures

Maintain small known fixtures with expected:

``` text
page count
headings
sections
chunks
visual count
captions
source references
processing state
```

Large fixture tests may run separately from fast CI.

------------------------------------------------------------------------

# 102. Performance Evaluation

Measure:

``` text
pages/minute
peak memory
storage growth
chunks/page
embedding throughput
total processing time
retry rate
queue delay
```

Benchmark with realistic medical materials.

------------------------------------------------------------------------

# 103. Concurrency

Bound:

-   simultaneous large-PDF jobs;
-   OCR jobs;
-   embedding batches;
-   visual extraction workers.

Interactive study remains higher priority.

------------------------------------------------------------------------

# 104. User-Level Fairness

One user uploading several huge files should not monopolize all workers.

Processing scheduler should support:

-   per-user concurrency;
-   global concurrency;
-   priority.

Exact limits are configuration.

------------------------------------------------------------------------

# 105. MVP Exit Criteria

The ingestion system is v1-ready when:

1.  Supported files validate correctly.
2.  Large PDFs process asynchronously.
3.  Processing survives retries.
4.  Original sources remain preserved.
5.  Structure is retained where detectable.
6.  Mixed text/image PDFs preserve linked visuals.
7.  OCR quality is explicit.
8.  Tables fail safely when structure is uncertain.
9.  Transcript timestamps survive ingestion.
10. Chunking respects hierarchy.
11. Embeddings are idempotent.
12. Index activation is transactional.
13. READY/PARTIALLY_READY/FAILED states are meaningful.
14. Processing progress is observable.
15. Deleted materials become immediately retrieval-ineligible.
16. Large-file memory remains bounded.
17. AI assistance is not used unnecessarily during ingestion.
18. Retrieval can trace evidence to source page/section/visual.
19. Cross-user isolation remains enforced.
20. Golden ingestion fixtures pass.

------------------------------------------------------------------------

# 106. Locked v1 Ingestion Decisions

The following are approved:

1.  Upload requests do not block for full processing.
2.  Original files are preserved.
3.  Object storage holds binaries; PostgreSQL holds metadata/state.
4.  Backend validates content type independently of filename extension.
5.  Large PDFs are first-class expected inputs.
6.  Large PDFs are processed in bounded batches.
7.  Full source text is not held unnecessarily in one memory object.
8.  Apache Tika + PDFBox are the baseline PDF/document stack.
9.  Pages are classified by extraction type/quality.
10. OCR is accessed through an adapter.
11. OCR output retains quality metadata.
12. OCR uncertainty propagates into retrieval quality.
13. TextBlocks are normalized source units before chunking.
14. Structure detection uses native metadata first, heuristics second,
    AI assistance last.
15. AI-assisted hierarchy detection is explicit and not unquestionable
    truth.
16. Repeated headers/footers should be excluded from chunks where safe.
17. Medical symbols/terminology must survive normalization.
18. Source images are preserved as VisualAssets.
19. Visuals retain captions, nearby text, page, and hierarchy.
20. Standalone images do not require AI descriptions to remain usable.
21. Tables preserve structure where feasible and fail safely otherwise.
22. Transcript timestamps are preserved.
23. Full arbitrary video understanding is deferred.
24. Semantic/document boundaries precede token-size chunking.
25. Chunking version is explicit.
26. Embedding is batched and idempotent.
27. Embedding provider changes create new IndexGenerations.
28. Only processed/index-valid versions become retrieval-active.
29. PARTIALLY_READY is a first-class state with explicit limitations.
30. Processing uses durable PostgreSQL jobs.
31. Processing stages are separately observable/retryable.
32. Job retries are bounded and failure-specific.
33. `SKIP LOCKED`-style atomic claiming is the baseline worker strategy.
34. Long processing occurs outside long DB transactions.
35. Intermediate extraction results are persisted for recoverability.
36. Processing time/memory is bounded.
37. Material deletion immediately removes retrieval eligibility.
38. Reprocessing is versioned.
39. Topic remapping does not require reprocessing source files.
40. AI is used during ingestion only when it adds measurable value.
41. One user's large uploads must not starve all processing capacity.
42. Source references remain resolvable to original material.
43. Search/index artifacts remain rebuildable.
44. Ingestion must remain simple enough for the approximately 40-user
    MVP.
45. The ingestion architecture must preserve Documents 13, 18, 19, and
    20.

------------------------------------------------------------------------

# 107. Out of Scope

This document does not lock:

-   Exact OCR provider/engine
-   Exact file-size limit
-   Exact max page count
-   Exact page batch size
-   Exact worker count
-   Exact embedding batch size
-   Exact parser timeout
-   Exact S3 provider
-   Exact visual-classification model
-   Exact table parser
-   Full video understanding
-   malware-scanning vendor
-   production retention duration

These are resolved in Documents 22/23, implementation benchmarks, or
later ADRs.

------------------------------------------------------------------------

# 108. Next Document

**22 - Security & Privacy Architecture**

The next document should define:

-   authentication
-   authorization
-   session security
-   API-key protection
-   upload security
-   parser isolation
-   cross-user data isolation
-   RAG ownership enforcement
-   object-storage access
-   prompt-injection defenses
-   AI-provider privacy boundary
-   retention/deletion
-   logs/telemetry minimization
-   secrets management
-   rate limiting
-   abuse controls
-   CSRF/CORS
-   privacy defaults

It must preserve:

> **Student source material and learning evidence are private by
> default.**

------------------------------------------------------------------------

# 109. Revision History

  -----------------------------------------------------------------------------
  Version           Date              Author            Changes
  ----------------- ----------------- ----------------- -----------------------
  1.0.0             2026-08-24        Project           Initial finalized File
                                      Hippocampus Team  Processing & Ingestion
                                                        Architecture defining
                                                        large-PDF batching,
                                                        mixed text/image
                                                        extraction, OCR
                                                        boundary, structure
                                                        detection, visual/table
                                                        handling, transcripts,
                                                        chunk/embedding/index
                                                        orchestration, durable
                                                        jobs,
                                                        retry/idempotency,
                                                        progress, activation,
                                                        deletion, and
                                                        processing security
                                                        boundaries

  -----------------------------------------------------------------------------

------------------------------------------------------------------------

# 110. Approval

**Status:** Final

**Reviewed By:** Project Hippocampus Team

**Approved By:** Project Hippocampus Team
