---
ADR: ADR-0003
Title: Learning Organization Archive Lifecycle
Status: ACCEPTED
Date: 2026-08-30
Decision Owners: Project Hippocampus Team
Categories:
  - DOMAIN
  - DATA
  - BACKEND
Affected Documents:
  - "18"
  - "27"
  - IMPLEMENTATION-TRACKER.md
  - docs/adr/README.md
Supersedes: None
Superseded By: None
---

# Context

Document 18 currently gives Topic an `ACTIVE` / `ARCHIVED` status but
provides no status column for Subject or Subtopic. The Implementation
Tracker nevertheless requires P2-01 to create subjects, topics, and
subtopics with archive status, P2-02 to archive Subject, and P2-03 to
archive Topic and Subtopic. Documents 04 and 07 require learner
organization but do not independently specify Subject or Subtopic archive
storage. The authorities are therefore internally inconsistent, and the
lifecycle must be aligned before P2-01 schema coding begins.

This ADR resolves only the Learning Organization lifecycle and related
persistence direction. It does not implement P2-01, P2-02, or P2-03.

# Decision

Subject, Topic, and Subtopic share the same v1 lifecycle values:

```text
ACTIVE
ARCHIVED
```

Subject is a primary learner-owned organization entity with the
conceptual persistence shape:

```text
id UUID PK
user_id UUID FK -> users.id
name VARCHAR NOT NULL
description TEXT NULL
sort_order INT NULL
status VARCHAR NOT NULL
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
```

Topic retains its already-approved `ACTIVE` / `ARCHIVED` lifecycle. This
is no semantic change beyond aligning Topic with the common Learning
Organization lifecycle.

Subtopic has the conceptual persistence shape:

```text
id UUID PK
topic_id UUID FK -> topics.id
name VARCHAR NOT NULL
description TEXT NULL
sort_order INT NULL
status VARCHAR NOT NULL
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
```

## Archive Semantics

Archive is a non-destructive lifecycle state, not physical deletion.
Archiving an entity must not automatically physically delete Subject,
Topic, Subtopic, Material, learning history, evidence, or review history.
No cascade deletion semantics are introduced.

For v1, archiving a parent does not automatically rewrite the persisted
status of its descendants. Archiving Subject does not set its Topics or
Subtopics to `ARCHIVED`, and archiving Topic does not mutate its
Subtopics. Persisted child state remains independently meaningful, mass
implicit mutation and its hidden side effects are avoided, and a future
restoration does not have to reconstruct prior child states. Queries and
application behavior can account for ancestor availability.

P2-02 and P2-03 own exact visibility, listing, mutation eligibility,
restoration, and ancestor-archive behavior. Those use cases are not
specified or implemented here.

## Storage Representation

All three status fields use `VARCHAR NOT NULL`, bounded at the
persistence/database boundary to `ACTIVE` and `ARCHIVED` with an ordinary
PostgreSQL constraint such as:

```sql
CHECK (status IN ('ACTIVE', 'ARCHIVED'))
```

P2-01 should use that constraint unless implementation discovers a
concrete incompatibility requiring review. PostgreSQL native ENUM,
`BOOLEAN archived`, `is_archived`, a deleted flag, and `deleted_at` are
not substitutes for this lifecycle field. No database default is
approved by this ADR; creation behavior can explicitly persist `ACTIVE`.

## Unchanged Ownership, Hierarchy, and Naming

Ownership remains normalized and authoritative:

```text
Subject -> User
Topic -> Subject -> User
Subtopic -> Topic -> Subject -> User
```

This ADR does not add `topics.user_id` or `subtopics.user_id`.

The v1 hierarchy remains exactly one explicit Subtopic level:

```text
Subject
  -> Topic
       -> Subtopic
```

It introduces no `parent_subtopic_id`, recursive tree, closure table,
nested set, or materialized path.

Subject retains `UNIQUE(user_id, lower(name))`. No Topic sibling-name or
Subtopic sibling-name uniqueness is added; either would require a
separate future decision if product requirements establish the need.

## Deletion and Foreign-Key Direction

Archive is the user-facing lifecycle mechanism for the Learning
Organization, and physical deletion is not equivalent to archive. P2-01
should use fail-closed, non-cascading parent foreign keys for primary
learning hierarchy rows, without destructive automatic cascade behavior.
Later privacy/account deletion work may perform explicit, controlled
cleanup where required. Broader privacy and retention policy is outside
this ADR.

# Alternatives Considered

## A. Keep status only on Topic

Rejected because it leaves the tracker archive contracts for Subject and
Subtopic without an authoritative persistence lifecycle and preserves the
confirmed inconsistency.

## B. Use a Boolean or deletion marker

Rejected because a boolean/archive flag or `deleted_at` conflates or
narrows lifecycle semantics, diverges from Topic's approved status model,
and makes future bounded states harder to introduce consistently.

## C. Use PostgreSQL native ENUM

Rejected for v1 because constrained `VARCHAR` preserves database-level
validation without binding lifecycle evolution to native ENUM migration
semantics.

## D. Cascade parent archive into descendant statuses

Rejected because it destroys independently meaningful child state,
creates hidden mass updates, and makes faithful restoration depend on
reconstructing earlier descendant states.

## E. Physically delete on archive or cascade parent deletion

Rejected because organization history, learning evidence, review history,
and linked Material must not be destroyed as a side effect of the
user-facing lifecycle action.

# Rationale

A common explicit lifecycle matches the already-required archive use
cases and gives P2-01 one bounded persistence rule. Independent child
status preserves domain meaning and makes archive effects explicit at the
application/query boundary rather than encoding hidden write cascades.
Constrained `VARCHAR` supplies fail-closed storage validation while
remaining simpler to evolve than a native database ENUM. Existing
normalized ownership, shallow hierarchy, and naming decisions remain
untouched, keeping this governance patch narrow.

# Consequences

## Positive

- Document 18 and P2-01 through P2-03 describe one consistent lifecycle.
- Every Learning Organization entity can be archived without deletion.
- Child lifecycle state survives parent archive and future restoration.
- Database constraints can reject values outside the bounded v1 set.
- Ownership, hierarchy depth, and name uniqueness remain stable.

## Negative / Tradeoffs

- Queries and use cases must explicitly account for archived ancestors.
- A child may remain persisted as `ACTIVE` while an ancestor is
  `ARCHIVED`, so persisted status alone does not express effective
  availability.
- P2-02 and P2-03 still need precise visibility, mutation, restoration,
  and ancestor-handling contracts.
- Controlled physical cleanup for privacy/account deletion remains
  separate future work.

# Documentation Changes

| Document | Alignment |
| --- | --- |
| 18 — Domain Model and Database Design | Adds Subject and Subtopic status, aligns all three lifecycle values, and records non-destructive/non-cascading archive and FK direction. |
| 27 — Decision Log / ADR Index | Registers and summarizes ADR-0003. |
| `docs/IMPLEMENTATION-TRACKER.md` | Makes P2-01's ownership hierarchy and lifecycle coverage explicit without changing P2 task statuses. |
| `docs/adr/README.md` | Records ADR-0003 as ACCEPTED. |

Documents 04 and 07 remain unchanged because their learner-organization
requirements are compatible and do not define archive storage. ADR-0001
and ADR-0002 remain unchanged.

# Approval

**Project architecture/domain/data review:** ACCEPTED — 2026-08-30
