---
name: hippocampus-source-of-truth
description: Resolve the minimum authoritative Hippocampus documentation needed for a tracker task. Use when scope, authority, phase, visual design, or ADR governance must be established; do not load it automatically when an approved execution packet already establishes those facts.
---

# Hippocampus Source-of-Truth Resolver

## Goal

Find the smallest authoritative context that safely governs the current task. Do not read the entire documentation set by default.

## Resolve

1. Identify the exact tracker task in `docs/IMPLEMENTATION-TRACKER.md`.
2. Read its goal, dependencies, required behavior, tests/validation, Definition of Done, and authority references.
3. Check Document 26 only as needed to confirm the current phase boundary.
4. Read only the authority documents referenced by or materially required for the task.
5. For approved frontend visual work, read `docs/design/DESIGN.md`; inspect only relevant files under `docs/design/references/` when visual intent is needed.
6. Check Document 27 / accepted ADRs only when a significant architectural decision or conflict is actually in scope.
7. Extract only what execution needs: MUST, MUST NOT, DEFERRED, and unresolved decisions.

If documents conflict, follow root `AGENTS.md` authority order. If a significant choice is genuinely unresolved, stop and require reviewer/ADR resolution.

## Context Efficiency

- Do not read all numbered documents.
- Do not scan all design references.
- Do not reread authority already captured in an approved execution packet unless implementation exposes a contradiction.
- Do not inspect repository history or unrelated code to resolve documentation authority.
- Expand context only when a concrete dependency, conflict, or blocker requires it.

## Hard Boundaries

- Medical students are the primary persona.
- Study Missions are core.
- Learning Engine owns pedagogical sequencing.
- AI output is bounded and untrusted.
- Evidence is application-owned and traceable.
- Material != Topic.
- Ownership scope precedes RAG ranking; cross-user leakage tolerance is zero.
- v1 AI providers remain remote Ollama API + Gemini API behind abstraction.
- Visual mock content never creates product behavior absent higher authority.

## Output

When this skill is explicitly used for planning/review, report only:

- task ID;
- authority read;
- MUST / MUST NOT / DEFERRED;
- unresolved decisions / ADR requirement.

Do not restate large source passages.
