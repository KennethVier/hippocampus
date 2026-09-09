---
ADR: ADR-0004
Title: Durable Text Normalization Contract
Status: ACCEPTED
Date: 2026-09-08
Decision Owners: Project Hippocampus Team
Categories: DATA, INGESTION, DOMAIN
Affected Documents: 18, 21, 27
---

# Context

P3-04 and P3-12 persist immutable extraction evidence in `text_blocks.content`, while P3-13 needs durable normalized text before chunking.

# Decision

Add nullable `text_blocks.normalized_content`. Raw `content` remains immutable. NORMALIZE alone may fill or verify the derived field for PAGE_TEXT and TABLE_TEXT; downstream chunking consumes it after successful normalization.

# Rationale

The additive field preserves replay, source order, and provenance without duplicate TextBlock identities or a second ordering model.

# Alternatives Considered

Separate normalized rows were rejected because they duplicate provenance and ordering. Replacing `content` was rejected because it breaks extraction evidence and replay.

# Consequences

Normalization is idempotent: null is written, an exact value is retained, and conflict fails closed. Future reprocessing/version replacement remains out of scope.

# Security & Privacy Impact

Untrusted extracted content remains inert data; no source text is logged or executed.

# Migration Impact

Additive nullable column only; no backfill.
