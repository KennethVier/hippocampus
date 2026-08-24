---
name: hippocampus-source-of-truth
description: Use when planning, implementing, reviewing, or debugging Project Hippocampus work that must align with the frozen v1 documentation. Resolves which docs to read, prevents scope drift, and identifies when ADR review is required.
---

# Hippocampus Source-of-Truth Workflow

## Goal

Use the minimum authoritative context needed for the current task while preventing undocumented product or architecture changes.

## Workflow

1. Identify the exact tracker task ID from `docs/IMPLEMENTATION-TRACKER.md`.
2. Read the task's goal, build requirement, dependencies, tests/validation, expected result, Definition of Done, and authority-document references.
3. Read Document 26 for the current phase boundary.
4. Read only the authority documents needed for the task.
5. If the task touches a major architectural boundary, also check Document 27.
6. Extract requirements into MUST, MUST NOT, DEFERRED, and unresolved decisions.
7. If documents conflict, use root `AGENTS.md` authority and accepted ADRs.
8. If a significant choice is genuinely unresolved, stop and flag reviewer/ADR review.

## Context-Efficiency Rule

Do not read all 28 documents by default. Start with tracker authority references and expand only when a concrete dependency or conflict requires it.

## Hard Product Rules

- Medical students are the primary persona.
- Study Missions are the core experience.
- Learning Engine owns pedagogical sequencing.
- AI output is bounded and untrusted.
- Evidence is application-owned and traceable.
- Material != Topic.
- Provenance must survive retrieval/generation.
- Ownership scope is applied before RAG ranking.
- Cross-user leakage tolerance is zero.
- v1 AI providers are remote Ollama API + Gemini API behind abstraction.

## Output for Planning/Review

Always state:
1. task ID;
2. authoritative docs read;
3. requirements extracted;
4. scope exclusions;
5. unresolved decisions;
6. whether ADR review is required.
