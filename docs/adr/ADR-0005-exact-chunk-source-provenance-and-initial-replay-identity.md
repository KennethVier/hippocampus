---
ADR: ADR-0005
Title: Exact Chunk Source Provenance and Initial Replay Identity
Status: ACCEPTED
Date: 2026-09-09
Decision Owners: Project Hippocampus Team
Categories: DATA, INGESTION, RAG, DOMAIN
Affected Documents: 18, 21, 27
---

# Context

Documents 13 and 21 require each retrieval Chunk to preserve stable source
provenance from ordered TextBlocks. Document 18 stores MaterialVersion,
DocumentNode, page range, source order, and derived metadata on `chunks`, but
does not define an exact relation from a Chunk to every contributing TextBlock.

Those fields are insufficient when one Chunk uses multiple TextBlocks, one
TextBlock contributes to multiple Chunks, TABLE_TEXT shares a physical page
with PAGE_TEXT, an oversized source is split, or conservative overlap repeats
source content in the next Chunk.

Document 21 also requires versioned and retry-safe Chunk generation. It gives
`CHUNKER_V1` as an example and says Chunk IDs/indexes are versioned. Document
18 defines `UNIQUE(material_version_id, chunk_index)` and places
`chunking_version` on future `index_generations`, not on `chunks`. P3-14 needs
an exact initial replay rule without prematurely introducing general
multi-generation Chunk persistence.

# Decision

Add `chunk_text_block_links` as the exact ordered relational provenance between
Chunks and TextBlocks.

The table has:

``` text
chunk_id UUID NOT NULL
text_block_id UUID NOT NULL
material_version_id UUID NOT NULL
source_position INT NOT NULL
is_overlap BOOLEAN NOT NULL DEFAULT FALSE
```

Its primary key and position constraint are:

``` text
PRIMARY KEY (chunk_id, source_position)
CHECK (source_position >= 1)
```

`source_position` is one-based, contiguous within a finalized Chunk, and
represents the deterministic occurrence order of the contributing source in
the Chunk. It is not a page number, TextBlock ordinal, character offset, or
MaterialVersion-global ordinal.

The same TextBlock may appear at more than one source position in a Chunk and
may contribute to multiple Chunks. No uniqueness constraint is placed on
`(chunk_id, text_block_id)`.

`is_overlap = false` identifies primary newly consumed source for the Chunk.
`is_overlap = true` identifies a source occurrence intentionally repeated from
the immediately preceding compatible Chunk by the conservative-overlap policy.
Overlap does not create or mutate a TextBlock.

Same-MaterialVersion integrity is enforced with one stored
`material_version_id`, candidate keys on `(id, material_version_id)` for both
parent tables, and composite foreign keys:

``` text
(chunk_id, material_version_id)
    -> chunks(id, material_version_id)

(text_block_id, material_version_id)
    -> text_blocks(id, material_version_id)
```

The association rows use `ON DELETE CASCADE` because they are derived from both
parents. Application provenance validation remains defense in depth.

P3-14 uses one initial durable Chunk generation named exactly `CHUNKER_V1`.
`CHUNKER_V1` is a deterministic replay-identity label, not a new Chunk column
and not an IndexGeneration.

Chunk IDs use UUIDv5/name-based semantics with one fixed application-owned UUID
namespace constant. The implementation must define and permanently retain that
constant for CHUNKER_V1. This ADR does not select an arbitrary namespace UUID.

The canonical UTF-8 name is:

``` text
"CHUNKER_V1\n" +
canonical lowercase MaterialVersion UUID +
"\n" +
base-10 chunkIndex
```

`chunkIndex` is positive and one-based. No third-party UUID library is required
by this decision.

The existing constraint remains:

``` text
UNIQUE(material_version_id, chunk_index)
```

It represents one initial durable Chunk generation per MaterialVersion. P3-14
does not add `chunks.chunking_version` and does not introduce concurrent
multi-generation Chunk persistence.

An extraction-method change is a hard Chunk boundary. NATIVE and OCR source
content do not coexist in one P3-14 Chunk, and P3-14 does not introduce a
`MIXED` extraction method.

Future re-chunking, replacement, historical generation retention, and the
relationship between Chunk generations and IndexGeneration remain owned by
future IndexGeneration/reprocessing design.

# Rationale

Relational links provide exact source identity, deterministic ordering, and
database-enforced MaterialVersion isolation. They allow one TextBlock to
contribute to multiple Chunks and allow overlap to be represented without
duplicating or mutating extraction evidence.

JSON source identifiers were rejected because PostgreSQL could not enforce
source ownership or foreign-key integrity over them. Page range, DocumentNode,
and first/last ordinal metadata remain useful summaries but cannot represent an
exact contributing source set.

A deterministic CHUNKER_V1 UUID makes exact retry reproducible while retaining
Document 18's initial per-MaterialVersion Chunk-index uniqueness. Adding a
Chunk version column or multiple durable generations now would preempt the
future IndexGeneration/reprocessing lifecycle.

# Alternatives Considered

## JSON source identifiers

Rejected because relational ownership, ordering, and same-version integrity
would not be enforceable through normal foreign keys.

## Page, node, or first/last ordinal only

Rejected because these values cannot identify exact contributors, TABLE_TEXT
participation, repeated source occurrences, oversized splits, or overlap.

## Unique `(chunk_id, text_block_id)`

Rejected because a TextBlock may legitimately occur more than once in a Chunk
and may occur in multiple Chunks.

## Random Chunk UUIDs

Rejected because exact replay should reproduce and verify Chunk identity.

## Add `chunks.chunking_version`

Rejected for P3-14 because Document 18 assigns chunking version to future
IndexGeneration and this decision does not approve multi-generation Chunk
storage.

## Change uniqueness to include chunking version

Rejected because it would introduce general multi-generation persistence
before replacement, activation, retention, and retrieval behavior are
designed.

## Mixed extraction-method Chunks

Rejected. An extraction-method change is a deterministic Chunk boundary, so
P3-14 can preserve one exact extraction method per Chunk.

# Consequences

P3-14 persistence must write and verify ordered Chunk-to-TextBlock source
occurrences alongside each Chunk.

Exact retry preserves Chunk UUID, Chunk index, source positions, source
TextBlock identities, and overlap flags. Partial compatible state may converge.
Any conflicting durable identity or provenance fails closed.

Under ADR-0004, `text_blocks.content` remains immutable extraction evidence and
`text_blocks.normalized_content` remains the NORMALIZE-owned derived
representation. CHUNK consumes `normalized_content`, never modifies either
field, and references the original TextBlock identity through
`chunk_text_block_links`.

Deleting a Chunk, TextBlock, or owning MaterialVersion removes its derived
association rows through cascade behavior. Normal P3-14 retry does not delete
conflicting source or Chunk state.

P3-14 remains limited to one CHUNKER_V1 generation per MaterialVersion.
Materially changed chunking behavior cannot silently overwrite or coexist with
that generation. Future reprocessing must define the new lifecycle before
another generation is persisted.

Exact token budgets, overlap limits, persistence batch size, paragraph/table
splitting, visual-link selection, SourceReference creation, embeddings, and
retrieval remain outside this ADR.

# Security & Privacy Impact

Composite foreign keys prevent cross-MaterialVersion Chunk-to-TextBlock links.
Application persistence must independently revalidate MaterialVersion,
DocumentNode, page, and TextBlock provenance before writing.

Source content remains inert untrusted data. No raw or normalized source text,
heading, table content, caption, or overlap content may be written to logs. No
network, shell, AI provider, or provider-specific tokenizer behavior is
introduced.

# Migration Impact

P3-14's eventual V13 migration creates `chunks`,
`chunk_text_block_links`, and the separately documented Chunk-to-visual
relationship.

The migration adds candidate keys on `(id, material_version_id)` where needed,
the composite same-version foreign keys, a positive source-position check, and
an index supporting reverse TextBlock-to-Chunk resolution.

The migration does not add `chunks.chunking_version`, an IndexGeneration,
SourceReference, embedding, vector, or retrieval table.
