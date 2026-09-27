---
name: hippocampus-source-of-truth
description: Resolve the minimum authoritative Hippocampus documentation needed for a tracker task. Use when scope, authority, phase, visual design, runtime AI/provider behavior, or ADR governance must be established; do not load it automatically when an approved execution packet already establishes those facts.
---

# Hippocampus Source-of-Truth Resolver

## Goal

Find the smallest authoritative context that safely governs the current task. Do not read the entire documentation set by default.

This skill resolves project authority, not development-tool preference. Gemini/Antigravity/Jules usage for software development does not change runtime product/AI architecture.

## Resolve

1. Identify the exact tracker task in `docs/IMPLEMENTATION-TRACKER.md`.
2. Read its goal, dependencies, required behavior, tests/validation, Definition of Done, authority references, and current status.
3. Check Document 26 only as needed to confirm the current phase boundary.
4. Read only the authority documents referenced by or materially required for the task.
5. For approved frontend visual work, read `docs/design/DESIGN.md`; inspect only relevant files under `docs/design/references/` when visual intent is needed.
6. Check Document 27 / accepted ADRs only when a significant architectural decision or conflict is actually in scope.
7. For runtime AI/provider/RAG work, resolve Documents 10–15 plus the exact owning tracker task as applicable; never infer runtime authority from the development agent/tool being used.
8. Extract only what execution needs: MUST, MUST NOT, DEFERRED, and unresolved decisions.

If documents conflict, follow root `AGENTS.md` authority order. If a significant choice is genuinely unresolved, stop and require human/ADR resolution.

## Context Efficiency

- Do not read all numbered documents.
- Do not scan all design references.
- Do not reread authority already captured in an approved execution packet unless implementation exposes a contradiction.
- Do not inspect repository history or unrelated code to resolve documentation authority.
- Do not load every engineering skill merely because the task is broad.
- Expand context only when a concrete dependency, conflict, blocker, architecture issue, or security requirement requires it.

## Hard Boundaries

- Medical students are the primary persona.
- Study Missions are core.
- Learning Engine owns pedagogical sequencing.
- AI output is bounded and untrusted.
- Evidence is application-owned and traceable.
- Material != Topic.
- Ownership scope precedes RAG ranking; cross-user leakage tolerance is zero.
- v1 runtime AI providers remain remote Ollama API + Gemini API behind the Provider Router/approved abstraction.
- Development use of Gemini, Antigravity, or Jules does not authorize direct runtime coupling to those tools/providers.
- Provider-specific DTOs/SDK concerns stay inside provider adapters.
- Visual mock content never creates product behavior absent higher authority.

## Output

When this skill is explicitly used for planning/review, report only:

- task ID;
- authority read;
- MUST / MUST NOT / DEFERRED;
- unresolved decisions / ADR requirement.

Do not restate large source passages.
