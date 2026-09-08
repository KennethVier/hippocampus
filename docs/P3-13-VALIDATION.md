# P3-13 corrective-pass evidence

PR: [#128](https://github.com/KennethVier/hippocampus/pull/128).
State: **Ready For Review**, not Done. P3-14 remains Not Started; the Phase 3 gate is unchanged.

## Local validation

Executed from repository root on 2026-09-09 (Asia/Manila), finishing at 01:18:16 +08:00:

```powershell
mvn -B '-Dhippocampus.materials.processing.pdf.ocr-executable=C:\Windows\System32\where.exe' '-Dtest=ExtractionNormalizationPolicyTests,TextNormalizationPersistenceIntegrationTests,NormalizeMaterialTextTests,PdfExtractionPersistenceIntegrationTests,TableTextPersistenceIntegrationTests,ProcessingDispatcherTests,ProcessingJobStageCompletionIntegrationTests,DocumentStructurePersistenceConfigurationTests,FlywayMigrationApplicationTests,HippocampusArchitectureTests' -f backend/pom.xml test
```

**Tests run: 116; failures: 0; errors: 0; skipped: 0; BUILD SUCCESS.**
All database suites ran with PostgreSQL Testcontainers and reached assertions. Evidence is from the current complete invocation, not old Surefire reports. `git diff --check` passed after validation.

| Suite | Tests |
| --- | ---: |
| ExtractionNormalizationPolicyTests | 7 |
| TextNormalizationPersistenceIntegrationTests | 45 |
| NormalizeMaterialTextTests | 4 |
| PdfExtractionPersistenceIntegrationTests | 11 |
| TableTextPersistenceIntegrationTests | 4 |
| ProcessingDispatcherTests | 24 |
| ProcessingJobStageCompletionIntegrationTests | 7 |
| DocumentStructurePersistenceConfigurationTests | 4 |
| FlywayMigrationApplicationTests | 1 |
| HippocampusArchitectureTests | 9 |

The 45 normalization persistence cases include concurrent connection lock-timeout assertions for both eligibility rows in write and finalization transactions. The real application regression exercises the actual Spring transactional boundaries. The orchestration regression processes 100,000 unique pages while retaining at most 32 candidate entries.

## Corrections found during the pass

Finalization now locks current eligible durable state, checks the expected page count against it, and rejects SQL NULL/unknown validity rather than allowing malformed method/quality, page or node state through. Candidate discovery caps both per-batch and cross-batch collection with exact counts and no eviction. Threshold arithmetic uses long multiplication to prevent overflow. TABLE_TEXT mismatch fails before writing.

The real-application test exposed missing NORMALIZE wiring caused by conditional property registration order. `TextNormalizationConfiguration` now explicitly runs after PDF extraction and document persistence configuration, binds the existing typed table properties and registers the complete NORMALIZE handler/use-case/transactional persistence path. Database-only contexts remain supported.

## Final self-audit

The complete P3-13 change against main was inspected, including the original implementation and this corrective pass.

- **PASS** ? ADR-0004 accepted and aligned with implementation
- **PASS** ? V12 adds nullable normalized_content only
- **PASS** ? No backfill
- **PASS** ? Raw content immutable
- **PASS** ? No duplicate normalized persistence model or second ordinal namespace
- **PASS** ? Complete source provenance verification (id/version/node/page/type/ordinal/content/method/quality)
- **PASS** ? Same-version DocumentNode and page containment
- **PASS** ? Race-safe eligibility in persistence (MaterialVersion update lock; Materials shared lock)
- **PASS** ? Race-safe eligibility in finalization (same locks, current page-count equality)
- **PASS** ? Native PAGE_TEXT bound from PdfExtractionProperties
- **PASS** ? OCR PAGE_TEXT bound from PdfExtractionProperties
- **PASS** ? TABLE_TEXT bound from PdfTableExtractionProperties; raw and normalized bounds checked
- **PASS** ? Candidate state capped at 32 exact slot signatures independently of page count, including a single batch
- **PASS** ? First/last two nonblank slots only; candidate length cap 512; three-page minimum; 90% threshold unchanged
- **PASS** ? No arbitrary medical-digit canonicalization
- **PASS** ? No whole-document source concatenation
- **PASS** ? No pages-squared comparisons
- **PASS** ? No document-wide transaction
- **PASS** ? Short persistence transaction
- **PASS** ? Short finalization transaction
- **PASS** ? Parameterized production SQL
- **PASS** ? No source text logging
- **PASS** ? No shell/network/AI behavior in normalization
- **PASS** ? Medical symbols preserved
- **PASS** ? Native IL-\n6 -> IL-6 and HLA-\nB27 -> HLA-B27
- **PASS** ? Native cardio-\nvascular -> cardiovascular
- **PASS** ? OCR has no lexical dehyphenation or metadata upgrade
- **PASS** ? TABLE_TEXT exact pass-through, including TAB/LF; mismatches rejected before mutation
- **PASS** ? Blank page supported as empty string rather than NULL
- **PASS** ? Exact retry no-op preserves identity, timestamp and every source field
- **PASS** ? Partial retry converges
- **PASS** ? Conflict fails closed and rolls back the batch
- **PASS** ? PAGE_TEXT finalization verifies exact 1..pageCount set using count, page/ordinal equality and unique ordinal constraint
- **PASS** ? TABLE_TEXT finalization verifies page/ordinal, normalization, method/quality and provenance
- **PASS** ? Unknown/NULL malformed finalization state fails closed via IS NOT TRUE
- **PASS** ? Zero tables supported
- **PASS** ? P3-04 database replay preserves normalized_content and complete source row
- **PASS** ? P3-12 database replay preserves normalized_content, TAB/LF and complete source row
- **PASS** ? NORMALIZE is the existing stage only and is registered in the real application
- **PASS** ? Successful NORMALIZE advances to pending CHUNK without executing it
- **PASS** ? No P3-14 implementation
- **PASS** ? Architecture dependency direction preserved

No unresolved P3-13 implementation finding was identified in this self-audit. External general implementation review remains pending. The independent adversarial security review was **not run** and no SECURITY PASS is claimed.

## Commit and publication evidence

The corrective commit is `fix(materials): finalize P3-13 normalization review`, containing this evidence and the tracker update. Its exact SHA is recorded in PR #128's Validation section after commit; the commit containing this file can also be resolved with `git log -1 --format=%H -- docs/P3-13-VALIDATION.md`. A commit cannot embed its own literal SHA without changing that SHA. Exact-head CI is recorded in the PR Validation section after publication.
