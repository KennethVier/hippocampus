---
Audience: Backend, architecture, database, AI, QA, security, and DevOps
  contributors.
Authors: Project Hippocampus Team
Created: 2026-08-24
Document ID: 18
Last Updated: 2026-08-30
Owner: Project Hippocampus Team
Prerequisites:
- 00 - Project Vision
- 01 - Guiding Principles
- 02 - Problem Statement
- 03 - Educational Foundation
- 04 - Product Requirements
- 05 - User Personas
- 06 - User Journey & Learning Flow
- 07 - Feature Specifications
- 08 - Non-Functional Requirements
- 09 - MVP Scope & Roadmap
- 10 - AI Architecture v1.1+
- 11 - AI Learning Engine
- 12 - Prompt Engineering Strategy v1.0.1+
- 13 - RAG Architecture
- 14 - Knowledge Base Design
- 15 - AI Evaluation Strategy v1.0.1+
- 16 - System Architecture v1.1+
- 17 - Technology Stack & ADR Baseline
Purpose: Define the concrete v1 domain model and PostgreSQL relational
  design for Hippocampus, including ownership, aggregates, tables,
  relationships, indexes, vector persistence, lifecycle behavior, and
  integrity rules.
Related Documents:
- 19 - Backend Architecture
- 20 - Frontend Architecture
- 21 - File Processing & Ingestion Architecture
- 22 - Security & Privacy Architecture
- 23 - Deployment & Infrastructure
- 24 - Observability & Operations
- 25 - Testing Strategy
- 26 - Development Roadmap & Implementation Phases
- 27 - Decision Log / ADR Index
Scope: Core entities, aggregate boundaries, PostgreSQL schema design,
  keys, foreign keys, ownership, material hierarchy, topic-material
  mapping, chunks, visuals, embeddings, Study Missions, activities,
  attempts, learning evidence, review, generated artifacts, background
  jobs, authentication/session persistence, indexes, deletion,
  versioning, and migration rules.
Status: Final
Title: Domain Model & Database Design
Version: 1.0.1
---

# 18 - Domain Model & Database Design

## 1. Purpose

This document defines the concrete v1 domain and PostgreSQL database
structure for Hippocampus.

It answers:

> **How should Hippocampus persist the student's learning structure,
> source material structure, retrievable evidence, Study Missions,
> learner activity, learning evidence, and review state while preserving
> provenance and user isolation?**

The database is the authoritative source of persistent application
state.

The vector index is a retrieval structure, not the source of truth.

------------------------------------------------------------------------

# 2. Core Domain Principles

The following principles are non-negotiable.

## 2.1 Material ≠ Topic

A Material is a source.

A Topic is a learner-facing unit of study.

They are linked many-to-many.

## 2.2 Source Versioning Is Explicit

Chunks, visuals, embeddings, and source references belong to a specific
MaterialVersion.

Different material versions must never silently mix.

## 2.3 Learning Evidence Must Be Traceable

Learning evidence must derive from meaningful student attempts, review
attempts, or reflection events.

It must not exist only because an LLM generated a label.

## 2.4 PostgreSQL Is Authoritative

PostgreSQL owns durable application relationships and state.

Vector records may be rebuilt.

## 2.5 User Isolation Is Structural

User ownership is represented in schema relationships and enforced at
service/query boundaries.

## 2.6 Generated Content Is Provenanced

Persisted generated artifacts retain provider/model/prompt/source
metadata.

------------------------------------------------------------------------

# 3. Aggregate Boundaries

The domain should be organized around the following aggregate roots.

  -----------------------------------------------------------------------
  Aggregate               Root Entity             Owns
  ----------------------- ----------------------- -----------------------
  Identity                User                    profile/account
                                                  metadata

  Learning Organization   Subject                 Topics, Subtopics

  Material                Material                MaterialVersions,
                                                  source lifecycle

  Source Structure        MaterialVersion         DocumentNodes, Chunks,
                                                  VisualAssets

  Study Mission           StudyMission            LearningObjectives,
                                                  LearningActivities

  Learning Evidence       LearningEvidence        evidence summaries /
                                                  misconception links

  Review                  ReviewRecord            review state and review
                                                  outcome

  AI Artifact             GeneratedArtifact       generated educational
                                                  content metadata

  Processing              ProcessingJob           background
                                                  ingestion/indexing
                                                  lifecycle
  -----------------------------------------------------------------------

Cross-aggregate references should normally use identifiers rather than
object graphs spanning the full model.

------------------------------------------------------------------------

# 4. Primary Key Strategy

Use UUID primary keys for application-domain entities.

Recommended:

``` text
UUID
```

Reasons:

-   Safe generation across async jobs
-   Non-sequential public identifiers
-   Easy merging/migration
-   Suitable for distributed future evolution

PostgreSQL-native UUID support should be used.

------------------------------------------------------------------------

# 5. Common Column Conventions

Most domain tables should use:

``` text
id
created_at
updated_at
```

Where appropriate:

``` text
created_by
updated_by
deleted_at
status
version
```

Timestamps should use:

``` text
TIMESTAMPTZ
```

All persisted timestamps should be stored timezone-aware.

------------------------------------------------------------------------

# 6. User

## Table: `users`

``` text
id UUID PK
email VARCHAR UNIQUE NOT NULL
display_name VARCHAR NULL
status VARCHAR NOT NULL
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
```

Possible status:

``` text
ACTIVE
DISABLED
DELETED
```

Authentication-provider-specific fields should remain minimal in v1.
The provider-neutral `users.id` remains the identity and ownership root.

Under accepted ADR-0002, direct password credentials for the controlled
v1 pilot are persisted separately from `users`; neither `password` nor
`password_hash` belongs on the `users` table. Credential persistence has
a one-to-one relationship with `users.id`, enforces at most one direct-
password credential per user, and stores only an adaptive encoded hash.
Plaintext passwords are never durable data. Credential data must not
outlive a physically deleted user.

The exact credential-table name and DDL remain P1-02 implementation work.
During soft/account lifecycle handling, retained credential data does not
override account eligibility: `DISABLED` and `DELETED` users cannot
authenticate.

The existing `email VARCHAR UNIQUE NOT NULL` semantics remain unchanged.
No `lower(email)`, `citext`, canonicalization, or case-insensitive unique
index is introduced. Any future normalization or case-insensitivity
policy requires an explicit data-model change and migration.

------------------------------------------------------------------------

# 7. Subject

## Table: `subjects`

``` text
id UUID PK
user_id UUID FK -> users.id
name VARCHAR NOT NULL
description TEXT NULL
sort_order INT NULL
status VARCHAR NOT NULL
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
```

Constraint:

``` text
UNIQUE(user_id, lower(name))
```

where practical.

Possible status:

``` text
ACTIVE
ARCHIVED
```

------------------------------------------------------------------------

# 8. Topic

## Table: `topics`

``` text
id UUID PK
subject_id UUID FK -> subjects.id
name VARCHAR NOT NULL
description TEXT NULL
status VARCHAR NOT NULL
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
```

Possible status:

``` text
ACTIVE
ARCHIVED
```

A Topic is always reached through Subject ownership.

------------------------------------------------------------------------

# 9. Subtopic

## Table: `subtopics`

``` text
id UUID PK
topic_id UUID FK -> topics.id
name VARCHAR NOT NULL
description TEXT NULL
sort_order INT NULL
status VARCHAR NOT NULL
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
```

v1 should support only one explicit subtopic level.

Arbitrary recursive topic trees are deferred.

Possible status:

``` text
ACTIVE
ARCHIVED
```

------------------------------------------------------------------------

# 10. Material

## Table: `materials`

``` text
id UUID PK
user_id UUID FK -> users.id
title VARCHAR NOT NULL
material_type VARCHAR NOT NULL
original_filename VARCHAR NULL
mime_type VARCHAR NULL
storage_key VARCHAR NULL
status VARCHAR NOT NULL
active_version_id UUID NULL
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
```

Material type examples:

``` text
PDF
IMAGE
TEXT
TRANSCRIPT
VIDEO_TRANSCRIPT
```

Material status examples:

``` text
UPLOADED
PROCESSING
READY
PARTIALLY_READY
FAILED
UNSUPPORTED
DELETED
```

------------------------------------------------------------------------

# 11. MaterialVersion

## Table: `material_versions`

``` text
id UUID PK
material_id UUID FK -> materials.id
version_number INT NOT NULL
storage_key VARCHAR NULL
file_size_bytes BIGINT NULL
page_count INT NULL
content_hash VARCHAR NULL
processing_status VARCHAR NOT NULL
processing_progress NUMERIC(5,2) NULL
extraction_method VARCHAR NULL
extraction_quality VARCHAR NULL
activated_at TIMESTAMPTZ NULL
created_at TIMESTAMPTZ NOT NULL
```

Constraint:

``` text
UNIQUE(material_id, version_number)
```

A Material has at most one active version at a time.

------------------------------------------------------------------------

# 12. Material Active Version

`materials.active_version_id` references the currently active
MaterialVersion.

Activation should be transactional.

Conceptually:

``` text
New version indexed successfully
        ↓
Activate new version
        ↓
Deactivate old retrieval eligibility
```

Do not switch active version before successful processing/index
readiness.

------------------------------------------------------------------------

# 13. DocumentNode

## Table: `document_nodes`

``` text
id UUID PK
material_version_id UUID FK -> material_versions.id
parent_id UUID NULL FK -> document_nodes.id
node_type VARCHAR NOT NULL
title VARCHAR NULL
ordinal INT NULL
start_page INT NULL
end_page INT NULL
start_offset BIGINT NULL
end_offset BIGINT NULL
detection_origin VARCHAR NOT NULL
detection_confidence VARCHAR NULL
created_at TIMESTAMPTZ NOT NULL
```

Node types may include:

``` text
DOCUMENT
CHAPTER
SECTION
SUBSECTION
HEADING
PAGE_GROUP
TRANSCRIPT_SEGMENT_GROUP
```

Detection origins:

``` text
NATIVE
HEURISTIC
AI_ASSISTED
USER_CONFIRMED
```

------------------------------------------------------------------------

# 14. Document Node Hierarchy Integrity

Rules:

-   Parent and child must belong to the same MaterialVersion.
-   No circular parent references.
-   `start_page <= end_page` when both exist.
-   Ordinal should preserve source order within the parent.

Deep arbitrary recursion may exist in source metadata, but
learner-facing topic structure remains simpler.

------------------------------------------------------------------------

# 15. MaterialTopicLink

## Table: `material_topic_links`

``` text
id UUID PK
topic_id UUID FK -> topics.id
material_id UUID FK -> materials.id
material_version_id UUID NULL FK -> material_versions.id
document_node_id UUID NULL FK -> document_nodes.id
link_origin VARCHAR NOT NULL
status VARCHAR NOT NULL
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
```

Origin:

``` text
USER_SELECTED
STRUCTURE_DETECTED
SYSTEM_SUGGESTED
AI_ASSISTED
```

Status:

``` text
ACTIVE
DISMISSED
ARCHIVED
```

------------------------------------------------------------------------

# 16. MaterialTopicLink Constraints

At minimum:

-   Topic and Material must resolve to the same User.
-   If `material_version_id` exists, it must belong to `material_id`.
-   If `document_node_id` exists, it must belong to
    `material_version_id`.
-   Duplicate active links for the same exact target should be
    prevented.

------------------------------------------------------------------------

# 17. TextBlock

v1 may persist normalized extraction blocks separately from retrieval
chunks.

## Table: `text_blocks`

``` text
id UUID PK
material_version_id UUID FK -> material_versions.id
document_node_id UUID NULL FK -> document_nodes.id
page_number INT NULL
block_type VARCHAR NOT NULL
ordinal INT NOT NULL
content TEXT NOT NULL
extraction_method VARCHAR NOT NULL
quality VARCHAR NULL
created_at TIMESTAMPTZ NOT NULL
```

Possible block types:

``` text
HEADING
PARAGRAPH
LIST
CAPTION
TABLE_TEXT
TRANSCRIPT
```

TextBlocks preserve normalized source structure.

Chunks are derived retrieval units.

------------------------------------------------------------------------

# 18. Chunk

## Table: `chunks`

``` text
id UUID PK
material_version_id UUID FK -> material_versions.id
document_node_id UUID NULL FK -> document_nodes.id
chunk_index INT NOT NULL
content TEXT NOT NULL
token_count INT NULL
page_start INT NULL
page_end INT NULL
timestamp_start_ms BIGINT NULL
timestamp_end_ms BIGINT NULL
heading_path JSONB NULL
content_type VARCHAR NOT NULL
extraction_method VARCHAR NOT NULL
quality VARCHAR NULL
source_order BIGINT NULL
is_active BOOLEAN NOT NULL DEFAULT TRUE
created_at TIMESTAMPTZ NOT NULL
```

Constraint:

``` text
UNIQUE(material_version_id, chunk_index)
```

------------------------------------------------------------------------

# 19. Chunk Content Types

Examples:

``` text
TEXT
TABLE
CAPTION
TRANSCRIPT
MIXED
```

Exact types should remain small and meaningful.

------------------------------------------------------------------------

# 20. VisualAsset

## Table: `visual_assets`

``` text
id UUID PK
material_version_id UUID FK -> material_versions.id
document_node_id UUID NULL FK -> document_nodes.id
page_number INT NULL
storage_key VARCHAR NOT NULL
visual_type VARCHAR NOT NULL
caption TEXT NULL
nearby_text TEXT NULL
interpretation_status VARCHAR NOT NULL
width_px INT NULL
height_px INT NULL
content_hash VARCHAR NULL
created_at TIMESTAMPTZ NOT NULL
```

Interpretation status:

``` text
UNASSESSED
SUPPORTED
LIMITED
UNSUPPORTED
FAILED
```

------------------------------------------------------------------------

# 21. ChunkVisualLink

## Table: `chunk_visual_links`

``` text
chunk_id UUID FK -> chunks.id
visual_asset_id UUID FK -> visual_assets.id
relationship_type VARCHAR NOT NULL
PRIMARY KEY(chunk_id, visual_asset_id)
```

Relationship examples:

``` text
NEARBY
CAPTION_FOR
REFERENCES
EXPLAINS
SAME_SECTION
```

------------------------------------------------------------------------

# 22. SourceReference

## Table: `source_references`

``` text
id UUID PK
material_id UUID FK -> materials.id
material_version_id UUID FK -> material_versions.id
document_node_id UUID NULL FK -> document_nodes.id
chunk_id UUID NULL FK -> chunks.id
visual_asset_id UUID NULL FK -> visual_assets.id
page_number INT NULL
timestamp_start_ms BIGINT NULL
timestamp_end_ms BIGINT NULL
display_label VARCHAR NOT NULL
created_at TIMESTAMPTZ NOT NULL
```

At least one of:

``` text
document_node_id
chunk_id
visual_asset_id
page_number
timestamp_start_ms
```

should provide a meaningful source target.

------------------------------------------------------------------------

# 23. IndexGeneration

## Table: `index_generations`

``` text
id UUID PK
embedding_provider VARCHAR NOT NULL
embedding_model VARCHAR NOT NULL
embedding_model_version VARCHAR NULL
embedding_dimension INT NOT NULL
chunking_version VARCHAR NOT NULL
status VARCHAR NOT NULL
created_at TIMESTAMPTZ NOT NULL
activated_at TIMESTAMPTZ NULL
```

Status:

``` text
BUILDING
ACTIVE
INACTIVE
FAILED
```

------------------------------------------------------------------------

# 24. ChunkEmbedding

## Table: `chunk_embeddings`

``` text
id UUID PK
chunk_id UUID FK -> chunks.id
index_generation_id UUID FK -> index_generations.id
embedding VECTOR NOT NULL
created_at TIMESTAMPTZ NOT NULL
```

Constraint:

``` text
UNIQUE(chunk_id, index_generation_id)
```

The actual vector dimension must match the associated IndexGeneration.

------------------------------------------------------------------------

# 25. Vector Indexing

Use pgvector indexes appropriate to actual corpus size and selected
distance metric.

Exact index strategy is benchmark-driven.

Possible later choices include:

``` text
HNSW
IVFFlat
```

Do not choose solely from convention.

For the approximately 40-user v1, correctness and maintainability are
more important than premature indexing complexity.

------------------------------------------------------------------------

# 26. Lexical Search Columns

Chunks should support PostgreSQL lexical retrieval.

Recommended derived/search support:

``` text
tsvector
trigram similarity
normalized search text
```

This may be implemented through generated columns, triggers, or query
expressions.

Exact strategy belongs to Backend/RAG implementation design.

------------------------------------------------------------------------

# 27. StudyMission

## Table: `study_missions`

``` text
id UUID PK
user_id UUID FK -> users.id
topic_id UUID FK -> topics.id
subtopic_id UUID NULL FK -> subtopics.id
status VARCHAR NOT NULL
learning_state VARCHAR NULL
grounding_mode VARCHAR NOT NULL
available_time_minutes INT NULL
started_at TIMESTAMPTZ NULL
completed_at TIMESTAMPTZ NULL
stopped_at TIMESTAMPTZ NULL
current_activity_id UUID NULL
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
```

Status examples:

``` text
PLANNED
ACTIVE
PAUSED
COMPLETED
STOPPED
FAILED
```

Completion does not imply mastery.

------------------------------------------------------------------------

# 28. MissionMaterial

A StudyMission may use multiple Materials/versions.

## Table: `mission_materials`

``` text
study_mission_id UUID FK -> study_missions.id
material_id UUID FK -> materials.id
material_version_id UUID FK -> material_versions.id
document_node_id UUID NULL FK -> document_nodes.id
PRIMARY KEY(study_mission_id, material_version_id, document_node_id)
```

This freezes source versions for reproducible mission behavior.

------------------------------------------------------------------------

# 29. LearningObjective

## Table: `learning_objectives`

``` text
id UUID PK
study_mission_id UUID FK -> study_missions.id
objective_text TEXT NOT NULL
concept_key VARCHAR NULL
display_name VARCHAR NULL
priority INT NULL
status VARCHAR NOT NULL
created_at TIMESTAMPTZ NOT NULL
```

Status:

``` text
PENDING
ACTIVE
COMPLETED
DEFERRED
```

------------------------------------------------------------------------

# 30. LearningActivity

## Table: `learning_activities`

``` text
id UUID PK
study_mission_id UUID FK -> study_missions.id
learning_objective_id UUID NULL FK -> learning_objectives.id
activity_type VARCHAR NOT NULL
status VARCHAR NOT NULL
difficulty VARCHAR NULL
sequence_number INT NOT NULL
generated_artifact_id UUID NULL
source_required BOOLEAN NOT NULL DEFAULT FALSE
started_at TIMESTAMPTZ NULL
completed_at TIMESTAMPTZ NULL
created_at TIMESTAMPTZ NOT NULL
```

Activity types:

``` text
UNDERSTAND
RETRIEVE
CONNECT
APPLY
VISUAL
FEEDBACK
REFLECT
```

------------------------------------------------------------------------

# 31. ActivitySourceReference

## Table: `activity_source_references`

``` text
learning_activity_id UUID FK -> learning_activities.id
source_reference_id UUID FK -> source_references.id
PRIMARY KEY(learning_activity_id, source_reference_id)
```

This preserves source provenance independently of generated text.

------------------------------------------------------------------------

# 32. StudentAttempt

## Table: `student_attempts`

``` text
id UUID PK
user_id UUID FK -> users.id
learning_activity_id UUID FK -> learning_activities.id
attempt_number INT NOT NULL
response_text TEXT NULL
response_payload JSONB NULL
submitted_at TIMESTAMPTZ NOT NULL
evaluation_status VARCHAR NOT NULL
evaluation_artifact_id UUID NULL
deterministic_result VARCHAR NULL
created_at TIMESTAMPTZ NOT NULL
```

Constraint:

``` text
UNIQUE(learning_activity_id, attempt_number)
```

------------------------------------------------------------------------

# 33. Attempt Evaluation Status

Examples:

``` text
PENDING
VALIDATED
FAILED
MANUAL_REVIEW
NOT_REQUIRED
```

An invalid AI evaluation must not update LearningEvidence.

------------------------------------------------------------------------

# 34. GeneratedArtifact

## Table: `generated_artifacts`

``` text
id UUID PK
user_id UUID FK -> users.id
artifact_type VARCHAR NOT NULL
task_type VARCHAR NOT NULL
content_text TEXT NULL
content_payload JSONB NULL
grounding_mode VARCHAR NOT NULL
classification VARCHAR NOT NULL
prompt_id VARCHAR NOT NULL
prompt_version VARCHAR NOT NULL
provider VARCHAR NOT NULL
model VARCHAR NOT NULL
model_version VARCHAR NULL
validation_status VARCHAR NOT NULL
reusable BOOLEAN NOT NULL DEFAULT FALSE
created_at TIMESTAMPTZ NOT NULL
```

Classification:

``` text
SOURCE_DERIVED
SOURCE_GROUNDED_GENERATED
SUPPLEMENTAL_GENERATED
GENERAL_GENERATED
```

------------------------------------------------------------------------

# 35. GeneratedArtifactSource

## Table: `generated_artifact_sources`

``` text
generated_artifact_id UUID FK -> generated_artifacts.id
source_reference_id UUID FK -> source_references.id
PRIMARY KEY(generated_artifact_id, source_reference_id)
```

Grounded reusable artifacts must preserve sources.

------------------------------------------------------------------------

# 36. AI Request Audit Metadata

Do not store full prompt text by default.

Persist compact diagnostics where justified.

## Table: `ai_request_records`

``` text
id UUID PK
user_id UUID NULL FK -> users.id
task_type VARCHAR NOT NULL
prompt_id VARCHAR NOT NULL
prompt_version VARCHAR NOT NULL
provider VARCHAR NOT NULL
model VARCHAR NOT NULL
status VARCHAR NOT NULL
grounding_mode VARCHAR NULL
input_token_count INT NULL
output_token_count INT NULL
latency_ms BIGINT NULL
retry_count INT NOT NULL DEFAULT 0
error_code VARCHAR NULL
created_at TIMESTAMPTZ NOT NULL
```

Sensitive source/student content should not be duplicated into this
table.

------------------------------------------------------------------------

# 37. LearningEvidence

Use concept/activity-dimension summaries rather than fake mastery
percentages.

## Table: `learning_evidence`

``` text
id UUID PK
user_id UUID FK -> users.id
topic_id UUID FK -> topics.id
subtopic_id UUID NULL FK -> subtopics.id
concept_key VARCHAR NULL
evidence_dimension VARCHAR NOT NULL
state VARCHAR NOT NULL
supporting_event_count INT NOT NULL DEFAULT 0
last_observed_at TIMESTAMPTZ NULL
updated_at TIMESTAMPTZ NOT NULL
```

Dimensions:

``` text
RETRIEVAL
UNDERSTANDING
CONNECTION
APPLICATION
VISUAL_IDENTIFICATION
REVIEW_RETENTION
```

States:

``` text
STRONG
DEVELOPING
WEAK
INSUFFICIENT_EVIDENCE
```

------------------------------------------------------------------------

# 38. EvidenceEvent

LearningEvidence summaries should derive from event-level records.

## Table: `evidence_events`

``` text
id UUID PK
user_id UUID FK -> users.id
topic_id UUID FK -> topics.id
subtopic_id UUID NULL FK -> subtopics.id
concept_key VARCHAR NULL
student_attempt_id UUID NULL FK -> student_attempts.id
learning_activity_id UUID FK -> learning_activities.id
event_type VARCHAR NOT NULL
outcome VARCHAR NOT NULL
difficulty VARCHAR NULL
confidence VARCHAR NULL
occurred_at TIMESTAMPTZ NOT NULL
created_at TIMESTAMPTZ NOT NULL
```

LearningEvidence can be recomputed from EvidenceEvents if needed.

------------------------------------------------------------------------

# 39. Evidence Event Types

Examples:

``` text
RETRIEVAL_ATTEMPT
APPLICATION_ATTEMPT
VISUAL_IDENTIFICATION
CORRECTIVE_RETRY
REVIEW_ATTEMPT
REFLECTION
```

------------------------------------------------------------------------

# 40. MisconceptionEvidence

## Table: `misconception_evidence`

``` text
id UUID PK
user_id UUID FK -> users.id
topic_id UUID FK -> topics.id
concept_key VARCHAR NULL
description TEXT NOT NULL
status VARCHAR NOT NULL
first_observed_event_id UUID FK -> evidence_events.id
last_observed_event_id UUID FK -> evidence_events.id
first_observed_at TIMESTAMPTZ NOT NULL
last_observed_at TIMESTAMPTZ NOT NULL
resolved_at TIMESTAMPTZ NULL
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
```

Status:

``` text
POSSIBLE
ACTIVE
RESOLVED
```

One ambiguous answer should normally create `POSSIBLE`, not permanent
`ACTIVE`.

------------------------------------------------------------------------

# 41. ReflectionEvidence

## Table: `reflection_evidence`

``` text
id UUID PK
user_id UUID FK -> users.id
study_mission_id UUID FK -> study_missions.id
learning_activity_id UUID NULL FK -> learning_activities.id
confidence VARCHAR NULL
remaining_confusion TEXT NULL
interpreted_concepts JSONB NULL
created_at TIMESTAMPTZ NOT NULL
```

Reflection is secondary evidence.

------------------------------------------------------------------------

# 42. ReviewRecord

## Table: `review_records`

``` text
id UUID PK
user_id UUID FK -> users.id
topic_id UUID FK -> topics.id
subtopic_id UUID NULL FK -> subtopics.id
concept_key VARCHAR NULL
status VARCHAR NOT NULL
priority VARCHAR NOT NULL
reason_code VARCHAR NOT NULL
eligible_at TIMESTAMPTZ NOT NULL
scheduled_at TIMESTAMPTZ NULL
completed_at TIMESTAMPTZ NULL
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
```

Status:

``` text
PENDING
AVAILABLE
IN_PROGRESS
COMPLETED
CANCELLED
```

Priority:

``` text
HIGH
MEDIUM
LOW
```

------------------------------------------------------------------------

# 43. ReviewEvidenceLink

## Table: `review_evidence_links`

``` text
review_record_id UUID FK -> review_records.id
evidence_event_id UUID FK -> evidence_events.id
PRIMARY KEY(review_record_id, evidence_event_id)
```

Review reasons must be explainable from evidence.

------------------------------------------------------------------------

# 44. ReviewMission Link

A review session may create a normal StudyMission with review-specific
state.

Optional relation:

``` text
review_record_id
```

may be added to `study_missions` or represented via a bridge table.

Recommended v1:

``` text
study_missions.review_record_id UUID NULL
```

to keep one review → one review mission simple.

------------------------------------------------------------------------

# 45. ProcessingJob

## Table: `processing_jobs`

``` text
id UUID PK
user_id UUID FK -> users.id
material_version_id UUID NULL FK -> material_versions.id
job_type VARCHAR NOT NULL
status VARCHAR NOT NULL
priority INT NOT NULL
progress NUMERIC(5,2) NULL
attempt_count INT NOT NULL DEFAULT 0
max_attempts INT NOT NULL
locked_at TIMESTAMPTZ NULL
locked_by VARCHAR NULL
next_attempt_at TIMESTAMPTZ NULL
error_code VARCHAR NULL
error_message TEXT NULL
created_at TIMESTAMPTZ NOT NULL
started_at TIMESTAMPTZ NULL
completed_at TIMESTAMPTZ NULL
updated_at TIMESTAMPTZ NOT NULL
```

Job types:

``` text
MATERIAL_EXTRACT
STRUCTURE_DETECT
VISUAL_EXTRACT
CHUNK
EMBED
INDEX
REINDEX
CLEANUP
```

------------------------------------------------------------------------

# 46. Processing Job Idempotency

Recommended idempotency key:

``` text
material_version_id
+
job_type
+
processing_version
```

Prevent multiple active duplicate jobs for the same processing stage.

------------------------------------------------------------------------

# 47. ProviderUsageRecord

To manage free-tier / low-cost provider usage:

## Table: `provider_usage_records`

``` text
id UUID PK
provider VARCHAR NOT NULL
model VARCHAR NOT NULL
user_id UUID NULL FK -> users.id
task_type VARCHAR NOT NULL
request_count INT NOT NULL DEFAULT 1
input_tokens BIGINT NULL
output_tokens BIGINT NULL
estimated_cost NUMERIC NULL
occurred_at TIMESTAMPTZ NOT NULL
```

This supports:

-   Quota diagnostics
-   Cost visibility
-   Provider routing evaluation

Exact cost calculation may remain operational rather than billing-grade.

------------------------------------------------------------------------

# 48. User Settings

## Table: `user_settings`

``` text
user_id UUID PK FK -> users.id
default_study_minutes INT NULL
preferred_grounding_mode VARCHAR NULL
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
```

Avoid storing unsupported "learning style" labels.

------------------------------------------------------------------------

# 49. Session Persistence

Spring Session JDBC should use its supported schema.

Do not re-invent application session tables unless necessary.

Session data remains infrastructure state, not learning evidence.

------------------------------------------------------------------------

# 50. Database Relationship Diagram

``` mermaid
erDiagram

    USERS ||--o{ SUBJECTS : owns
    SUBJECTS ||--o{ TOPICS : contains
    TOPICS ||--o{ SUBTOPICS : contains

    USERS ||--o{ MATERIALS : owns
    MATERIALS ||--o{ MATERIAL_VERSIONS : versions

    MATERIAL_VERSIONS ||--o{ DOCUMENT_NODES : structures
    DOCUMENT_NODES ||--o{ DOCUMENT_NODES : parent_of

    MATERIAL_VERSIONS ||--o{ TEXT_BLOCKS : extracts
    MATERIAL_VERSIONS ||--o{ CHUNKS : chunks
    MATERIAL_VERSIONS ||--o{ VISUAL_ASSETS : visuals

    CHUNKS }o--o{ VISUAL_ASSETS : linked_by

    TOPICS ||--o{ MATERIAL_TOPIC_LINKS : maps
    MATERIALS ||--o{ MATERIAL_TOPIC_LINKS : maps

    INDEX_GENERATIONS ||--o{ CHUNK_EMBEDDINGS : owns
    CHUNKS ||--o{ CHUNK_EMBEDDINGS : embeds

    USERS ||--o{ STUDY_MISSIONS : starts
    TOPICS ||--o{ STUDY_MISSIONS : studies
    STUDY_MISSIONS ||--o{ LEARNING_OBJECTIVES : contains
    STUDY_MISSIONS ||--o{ LEARNING_ACTIVITIES : contains

    LEARNING_ACTIVITIES ||--o{ STUDENT_ATTEMPTS : receives
    LEARNING_ACTIVITIES ||--o{ EVIDENCE_EVENTS : produces
    STUDENT_ATTEMPTS ||--o{ EVIDENCE_EVENTS : contributes

    USERS ||--o{ LEARNING_EVIDENCE : owns
    TOPICS ||--o{ LEARNING_EVIDENCE : summarizes

    EVIDENCE_EVENTS ||--o{ MISCONCEPTION_EVIDENCE : supports
    TOPICS ||--o{ REVIEW_RECORDS : reviews
    REVIEW_RECORDS }o--o{ EVIDENCE_EVENTS : justified_by

    USERS ||--o{ GENERATED_ARTIFACTS : owns
    GENERATED_ARTIFACTS }o--o{ SOURCE_REFERENCES : grounded_by

    MATERIAL_VERSIONS ||--o{ PROCESSING_JOBS : processed_by
```

------------------------------------------------------------------------

# 51. User Ownership Traversal

Ownership should be resolvable without trusting request-supplied user
IDs.

Examples:

``` text
Topic
→ Subject
→ User
```

``` text
Chunk
→ MaterialVersion
→ Material
→ User
```

``` text
Attempt
→ Activity
→ StudyMission
→ User
```

Queries must validate ownership through authoritative relationships.

------------------------------------------------------------------------

# 52. Deletion Strategy

v1 should prefer controlled soft/archive behavior for user-facing
learning structure and hard cleanup for derived search/index records
where safe. Under ADR-0003, archive is a non-destructive lifecycle state
for Subject, Topic, and Subtopic; it is not physical deletion and does
not automatically rewrite descendant statuses or physically delete the
learning hierarchy, Material, learning history, evidence, or review
history.

## Learning Organization Deletion

Archive is the user-facing lifecycle mechanism for Subject, Topic, and
Subtopic. Archiving a parent does not cascade status changes to its
descendants. Physical deletion is not equivalent to archive and should
not delete Material or retained learning history.

Recommended:

``` text
Subject → ARCHIVED
Topic → ARCHIVED
Subtopic → ARCHIVED
```

## Material Deletion

Should:

-   Disable active retrieval immediately
-   Remove/disable MaterialTopicLinks
-   Remove active chunks/embeddings from retrieval
-   Remove binary object according to retention policy
-   Preserve only required historical provenance where policy permits

## User Deletion

Requires cascading/privacy cleanup designed in Document 22.

------------------------------------------------------------------------

# 53. Foreign Key Delete Policy

Recommended general approach:

## `ON DELETE CASCADE`

Use for strictly owned derived records:

-   MaterialVersion → TextBlock
-   MaterialVersion → Chunk
-   MaterialVersion → VisualAsset
-   Chunk → ChunkEmbedding
-   StudyMission → LearningObjective
-   StudyMission → LearningActivity

## `ON DELETE RESTRICT`

Use where deletion would destroy important provenance unexpectedly:

-   Material referenced by historical source records
-   Attempt referenced by evidence

## Soft/Archive

Use for primary user learning entities where history matters.

P2-01 should use fail-closed, non-cascading parent foreign keys for the
primary learning hierarchy. Later privacy/account deletion work may
perform explicit controlled cleanup where required; it must not be
conflated with the user-facing archive lifecycle.

Other physical-deletion policies should be finalized with
privacy/retention requirements.

------------------------------------------------------------------------

# 54. Optimistic Locking

Use optimistic locking where concurrent updates could conflict.

Candidate entities:

``` text
Material
StudyMission
LearningEvidence
ReviewRecord
ProcessingJob
```

JPA `@Version` may be used where appropriate.

------------------------------------------------------------------------

# 55. Study Mission Consistency

A StudyMission should maintain these invariants:

-   Topic belongs to same User.
-   Selected material belongs to same User.
-   MaterialVersion remains fixed once mission starts unless explicitly
    migrated.
-   Current activity belongs to same mission.
-   Completed mission cannot silently return to ACTIVE.
-   Completion does not update mastery directly.

------------------------------------------------------------------------

# 56. Attempt Consistency

Rules:

-   Attempt user must equal mission user.
-   Attempt belongs to one LearningActivity.
-   Attempt number increments monotonically per activity.
-   Evaluation failure does not produce LearningEvidence.
-   Retried responses create new attempts rather than overwriting
    history.

------------------------------------------------------------------------

# 57. Learning Evidence Aggregation

`learning_evidence` is a summary/projection over `evidence_events`.

Conceptually:

``` text
EvidenceEvents
      ↓
LearningEvidenceProjector
      ↓
Current Summary
```

This means LearningEvidence can be repaired/recomputed if aggregation
rules change.

Do not rely solely on destructive counters.

------------------------------------------------------------------------

# 58. Review Derivation

Review state should be derived from:

``` text
LearningEvidence
+
EvidenceEvents
+
ReviewHistory
+
Deterministic Review Rules
```

The review record stores resulting operational state and rationale.

The LLM does not directly create review dates.

------------------------------------------------------------------------

# 59. Generated Artifact Reuse Constraints

A reusable generated artifact should match at minimum:

``` text
task_type
prompt_version
provider/model evaluation status
grounding_mode
source version(s)
learning objective / concept
```

Personalized feedback should default to `reusable = false`.

------------------------------------------------------------------------

# 60. Source Reference Integrity

A SourceReference must never point to:

-   An inactive/deleted chunk without historical retention support
-   A MaterialVersion that never existed
-   Another user's source
-   A fabricated page/visual

Citation validation should resolve references before presenting them to
the student.

------------------------------------------------------------------------

# 61. Indexing Strategy

High-value relational indexes should include:

``` text
subjects(user_id)
topics(subject_id)
materials(user_id, status)
material_versions(material_id, processing_status)
document_nodes(material_version_id, parent_id)
material_topic_links(topic_id, status)
chunks(material_version_id, is_active)
learning_activities(study_mission_id, sequence_number)
student_attempts(learning_activity_id, attempt_number)
evidence_events(user_id, topic_id, occurred_at)
learning_evidence(user_id, topic_id, evidence_dimension)
review_records(user_id, status, eligible_at)
processing_jobs(status, priority, next_attempt_at)
ai_request_records(provider, created_at)
```

Exact indexes should be validated with real query plans.

------------------------------------------------------------------------

# 62. Partial Indexes

PostgreSQL partial indexes may be useful for operational queries.

Examples:

``` text
processing_jobs WHERE status IN ('PENDING','RETRY')
review_records WHERE status IN ('PENDING','AVAILABLE')
chunks WHERE is_active = true
materials WHERE status IN ('READY','PARTIALLY_READY')
```

------------------------------------------------------------------------

# 63. JSONB Usage Policy

JSONB is appropriate for flexible bounded payloads such as:

-   Heading path
-   Structured student response
-   Generated artifact payload
-   Reflection interpreted concepts
-   Provider metadata

Do not use JSONB to avoid modeling core relational relationships.

Rule:

> **If the application frequently joins, filters, owns, or constrains
> it, model it relationally.**

------------------------------------------------------------------------

# 64. Database Naming Conventions

Recommended:

``` text
snake_case
plural table names
singular semantic column names
```

Examples:

``` text
study_missions
material_versions
learning_evidence
created_at
```

Foreign keys:

``` text
user_id
topic_id
material_version_id
```

------------------------------------------------------------------------

# 65. Enum Storage Strategy

Prefer PostgreSQL-compatible string columns with application enums for
v1 rather than database-native ENUM types.

Rationale:

-   Easier schema evolution
-   Easier Flyway migration
-   Less coupling to DB enum changes

Use CHECK constraints for important bounded values where useful.

------------------------------------------------------------------------

# 66. Monetary / Cost Precision

Provider estimated cost should use:

``` text
NUMERIC
```

not floating-point types.

Even though v1 cost tracking is approximate, representation should
remain deterministic.

------------------------------------------------------------------------

# 67. Search / Embedding Deletion

When a MaterialVersion becomes inactive or deleted:

``` text
chunks.is_active = false
```

immediately removes it from retrieval eligibility.

Embeddings may be physically deleted asynchronously.

This avoids a race where deleted material remains searchable.

------------------------------------------------------------------------

# 68. Reindex Strategy

Changing:

-   Embedding model
-   Embedding dimensions
-   Chunking algorithm
-   Search normalization

may create a new IndexGeneration.

Flow:

``` text
Create Generation
↓
Re-index eligible chunks
↓
Run retrieval evaluation
↓
Activate generation
↓
Deactivate prior generation
```

Activation should be explicit.

------------------------------------------------------------------------

# 69. Database Migration Policy

All structural changes use Flyway.

Rules:

1.  Never edit an already-applied production migration.
2.  Add a new migration.
3.  Schema migration must be backward-compatible where deployment
    sequencing requires it.
4.  Destructive migrations require explicit review.
5.  Large backfills should be separated from DDL when appropriate.

------------------------------------------------------------------------

# 70. Seed Data

Seed only stable application defaults.

Possible:

-   Default subject suggestions
-   System configuration keys
-   Initial prompt/template metadata if persisted

Do not seed:

-   Fake learner evidence
-   Fake medical knowledge as authoritative application data

------------------------------------------------------------------------

# 71. Audit and History

Not every table needs full temporal history in v1.

History should be retained where product integrity requires it:

-   Material versions
-   Attempts
-   Evidence events
-   Review records
-   Generated artifact provenance
-   Prompt/provider/model identifiers

Simple mutable metadata such as display names need not create a full
event history.

------------------------------------------------------------------------

# 72. Privacy-Aware Persistence

Avoid persisting unnecessary copies of:

-   Full AI prompts
-   Full RAG contexts
-   Uploaded source text in logs
-   Sensitive user interaction payloads

The database may contain source chunks because retrieval requires them,
but duplication across operational/audit tables should be minimized.

------------------------------------------------------------------------

# 73. Large PDF Representation

A 600-page PDF may create:

``` text
1 Material
1 active MaterialVersion
Many DocumentNodes
Many TextBlocks
Many Chunks
Many VisualAssets
Many Embeddings
Many MaterialTopicLinks
```

This is expected.

The database design must not assume:

``` text
1 Material = 1 Topic
```

or:

``` text
1 Material = 1 Chunk
```

------------------------------------------------------------------------

# 74. Large PDF Query Pattern

Example retrieval setup:

``` text
Topic: SA Node
      ↓
MaterialTopicLink
      ↓
Relevant DocumentNode / MaterialVersion
      ↓
Active Chunks
      ↓
Hybrid Retrieval
```

The full textbook does not need to be joined into every prompt.

------------------------------------------------------------------------

# 75. Topic Reorganization

Changing a topic name or restructuring learner organization should
affect:

``` text
Subject / Topic / Subtopic
MaterialTopicLink
```

It should not require:

``` text
Re-extraction
Re-chunking
Re-embedding
```

unless the source itself changed.

------------------------------------------------------------------------

# 76. Concept Keys

v1 may use optional normalized `concept_key` strings.

Examples:

``` text
posterior-cord
ventricular-action-potential
sa-node-automaticity
```

Concept keys support:

-   Evidence aggregation
-   Review
-   Duplicate control

They do not constitute a full medical ontology.

------------------------------------------------------------------------

# 77. Concept Key Generation

Concept keys may originate from:

``` text
Application normalization
User topic/subtopic
Learning objective
AI-assisted suggestion
```

AI-assisted keys must be normalized by application rules before
persistence.

------------------------------------------------------------------------

# 78. Material Processing State Machine

``` mermaid
stateDiagram-v2

    [*] --> UPLOADED
    UPLOADED --> PROCESSING
    PROCESSING --> READY
    PROCESSING --> PARTIALLY_READY
    PROCESSING --> FAILED
    UPLOADED --> UNSUPPORTED

    READY --> PROCESSING: new version
    PARTIALLY_READY --> PROCESSING: retry/reprocess
```

Detailed processing substates remain in ProcessingJob records.

------------------------------------------------------------------------

# 79. Study Mission State Machine

``` mermaid
stateDiagram-v2

    [*] --> PLANNED
    PLANNED --> ACTIVE
    ACTIVE --> PAUSED
    PAUSED --> ACTIVE
    ACTIVE --> COMPLETED
    ACTIVE --> STOPPED
    ACTIVE --> FAILED
    PAUSED --> STOPPED
```

Educational stage is stored separately from persistence status.

------------------------------------------------------------------------

# 80. Review State Machine

``` mermaid
stateDiagram-v2

    [*] --> PENDING
    PENDING --> AVAILABLE
    AVAILABLE --> IN_PROGRESS
    IN_PROGRESS --> COMPLETED
    AVAILABLE --> CANCELLED
    PENDING --> CANCELLED
```

------------------------------------------------------------------------

# 81. Processing Job Claiming

For a database-backed worker, jobs should be claimed atomically.

Implementation may use PostgreSQL techniques such as:

``` text
SELECT ... FOR UPDATE SKIP LOCKED
```

or equivalent Spring-supported patterns.

The goal is to prevent two workers from processing the same job
concurrently.

Exact worker implementation belongs to Document 19/21.

------------------------------------------------------------------------

# 82. Processing Job Priority

Suggested relative priorities:

``` text
Interactive-support jobs
        >
Mission preparation
        >
Material ingestion
        >
Reindex
        >
Cleanup
```

Most ingestion is asynchronous and must not starve interactive learning.

------------------------------------------------------------------------

# 83. Provider Routing Data

Provider routing configuration should not be hard-coded into the core
relational domain.

Configuration may be persisted separately or externalized.

If persisted later, it should capture:

``` text
task_type
preferred_provider
preferred_model
fallback_provider
enabled
```

but such operational configuration is outside the initial domain schema
unless needed.

------------------------------------------------------------------------

# 84. Database Constraints Over Application Assumptions

Where feasible, the database should enforce:

-   Foreign keys
-   Unique constraints
-   Non-null rules
-   Basic bounded checks
-   Referential integrity

Application validation remains necessary but should not be the only
protection for critical relationships.

------------------------------------------------------------------------

# 85. High-Level Query Examples

## Load Topic Workspace

``` text
User
→ Subject
→ Topic
→ MaterialTopicLinks
→ Material status
→ Latest learning evidence
→ Review availability
```

## Resume Mission

``` text
StudyMission
→ Current activity
→ LearningObjective
→ MissionMaterial
→ Recent EvidenceEvents
```

## RAG Retrieval

``` text
Allowed MaterialVersions
→ Active Chunks
→ Metadata filters
→ pgvector + FTS
→ SourceReferences
```

## Review Dashboard

``` text
User
→ ReviewRecords WHERE AVAILABLE
→ Topic
→ Reason
→ Supporting EvidenceEvents
```

------------------------------------------------------------------------

# 86. Domain Boundary Diagram

``` mermaid
flowchart LR

subgraph LearningOrganization
    Subject
    Topic
    Subtopic
end

subgraph SourceKnowledge
    Material
    MaterialVersion
    DocumentNode
    Chunk
    VisualAsset
end

subgraph LearningExecution
    StudyMission
    LearningObjective
    LearningActivity
    StudentAttempt
end

subgraph LearningState
    EvidenceEvent
    LearningEvidence
    Misconception
    ReviewRecord
end

subgraph AIArtifacts
    GeneratedArtifact
    SourceReference
    AIRequestRecord
end

Topic <-->|MaterialTopicLink| Material

StudyMission --> Topic
StudyMission --> LearningObjective
LearningObjective --> LearningActivity
LearningActivity --> StudentAttempt

StudentAttempt --> EvidenceEvent
EvidenceEvent --> LearningEvidence
EvidenceEvent --> Misconception
LearningEvidence --> ReviewRecord

Chunk --> SourceReference
VisualAsset --> SourceReference
SourceReference --> GeneratedArtifact
GeneratedArtifact --> LearningActivity
```

------------------------------------------------------------------------

# 87. MVP Data Scope

The v1 database includes the domains necessary for the complete MVP
loop:

``` text
Organize
↓
Upload
↓
Process
↓
Retrieve
↓
Study
↓
Attempt
↓
Evidence
↓
Review
```

It does not attempt to model every possible future platform capability.

------------------------------------------------------------------------

# 88. Deferred Database Capabilities

Not required for v1:

-   Full medical knowledge graph
-   Graph database
-   Faculty/course management
-   Shared institutional libraries
-   Social study groups
-   Collaborative annotation
-   Native mobile synchronization model
-   Multi-tenant organizations
-   Billing/subscriptions
-   Fine-grained content sharing
-   Event sourcing
-   CQRS read models
-   Data warehouse

------------------------------------------------------------------------

# 89. Locked v1 Domain & Database Decisions

The following are approved:

1.  PostgreSQL 18 is the authoritative domain database.
2.  UUIDs are used for application-domain primary keys.
3.  Timestamps use `TIMESTAMPTZ`.
4.  Material and Topic remain separate.
5.  Material-to-Topic mapping is many-to-many.
6.  MaterialVersion is the source-version boundary.
7.  DocumentNode preserves detected hierarchy.
8.  TextBlocks preserve normalized extraction structure where useful.
9.  Chunks are retrieval units, not learning topics.
10. VisualAssets are first-class source evidence.
11. Chunk-to-visual links are explicit.
12. SourceReferences provide stable provenance.
13. pgvector embeddings are linked through IndexGeneration.
14. Embedding generations never silently mix.
15. PostgreSQL lexical search supports hybrid retrieval.
16. StudyMission owns bounded learning-session state.
17. Mission source versions are frozen through MissionMaterial.
18. LearningObjective represents explicit educational intent.
19. LearningActivity represents one guided activity.
20. StudentAttempt records immutable attempt history.
21. Invalid AI evaluations do not produce learning evidence.
22. GeneratedArtifact stores AI provenance when persistence is
    justified.
23. AI request diagnostics avoid duplicating sensitive prompt/source
    data.
24. EvidenceEvents are the traceable basis of learning evidence.
25. LearningEvidence is an aggregate/projection, not an LLM-owned score.
26. Broad evidence states are preferred over false precision.
27. Misconceptions require supporting evidence.
28. Reflection remains secondary evidence.
29. ReviewRecords remain explainable through supporting evidence.
30. Background processing uses durable ProcessingJob records.
31. Provider usage may be tracked for quota/cost visibility.
32. Spring Session JDBC owns browser session persistence.
33. JSONB is used only for bounded flexible payloads, not core
    relationships.
34. Flyway owns schema migrations.
35. Production schema mutation through Hibernate auto-update is
    prohibited.
36. User ownership is enforced through authoritative relationships.
37. Retrieval-active records must never survive unauthorized/deleted
    source state.
38. Search indexes remain rebuildable.
39. Topic reorganization must not require source reprocessing.
40. Large multi-topic PDFs are a first-class expected data shape.
41. The database design remains simple enough for the approximately
    40-user MVP.
42. Future ontology/knowledge-graph capability can be added without
    replacing the v1 source/evidence model.

------------------------------------------------------------------------

# 90. Out of Scope

This document does not define:

-   Exact JPA annotations
-   Exact Java package structure
-   Exact Flyway SQL
-   Exact HNSW/IVFFlat parameters
-   Exact FTS weighting
-   Exact table partitioning
-   Exact S3 provider
-   Exact retention durations
-   Exact review formula
-   Exact learning-evidence aggregation formula
-   Exact Spring Session schema
-   Exact encryption-at-rest implementation

Those belong to Documents 19, 21, 22, 23, and implementation ADRs.

------------------------------------------------------------------------

# 91. Related Documents

-   14 - Knowledge Base Design
-   15 - AI Evaluation Strategy
-   16 - System Architecture
-   17 - Technology Stack & ADR Baseline
-   19 - Backend Architecture
-   20 - Frontend Architecture
-   21 - File Processing & Ingestion Architecture
-   22 - Security & Privacy Architecture
-   23 - Deployment & Infrastructure
-   24 - Observability & Operations
-   25 - Testing Strategy
-   26 - Development Roadmap & Implementation Phases
-   27 - Decision Log / ADR Index

------------------------------------------------------------------------

# 92. Next Document

**19 - Backend Architecture**

The next document should define:

-   Spring Boot module/package boundaries
-   Application/domain/infrastructure layering
-   REST/API controllers
-   Use cases/application services
-   Domain services
-   Repository boundaries
-   Transaction boundaries
-   AI provider adapters
-   Provider Router
-   RAG services
-   Learning Engine implementation boundary
-   Background workers
-   Validation
-   Security integration
-   Error handling
-   DTO mapping
-   streaming/SSE
-   concurrency
-   idempotency
-   testing seams

It must preserve:

> **AI generates; Hippocampus decides.**

and:

> **The database stores authoritative learning state; AI providers never
> own it.**

------------------------------------------------------------------------

# 93. Revision History

  -----------------------------------------------------------------------
  Version           Date              Author            Changes
  ----------------- ----------------- ----------------- -----------------
  1.0.0             2026-08-24        Project           Initial finalized
                                      Hippocampus Team  Domain Model &
                                                        Database Design
                                                        defining
                                                        PostgreSQL
                                                        entities,
                                                        material/topic
                                                        separation,
                                                        source hierarchy,
                                                        vector/index
                                                        records, Study
                                                        Missions,
                                                        attempts,
                                                        learning
                                                        evidence, review,
                                                        generated
                                                        artifacts,
                                                        processing jobs,
                                                        indexes, and
                                                        lifecycle rules

  1.0.1             2026-08-28        Project           Aligned the separate
                                      Hippocampus Team  one-to-one password
                                                        credential persistence
                                                        boundary and existing
                                                        user/email semantics
                                                        with ADR-0002

  -----------------------------------------------------------------------

------------------------------------------------------------------------

# 94. Approval

**Status:** Final

**Reviewed By:** Project Hippocampus Team

**Approved By:** Project Hippocampus Team
