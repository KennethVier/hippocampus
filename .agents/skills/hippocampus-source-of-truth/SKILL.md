---
name: hippocampus-source-of-truth
description: Use when planning, implementing, reviewing, or debugging Project Hippocampus work that must align with the frozen v1 documentation. Resolves which docs to read, prevents scope drift, applies the approved visual design authority for UI work, and identifies when ADR review is required.
---

# Hippocampus Source-of-Truth Workflow

## Goal

Use the minimum authoritative context needed for the current task while preventing undocumented product, architecture, or visual-design changes.

## Workflow

1. Identify the exact tracker task ID from `docs/IMPLEMENTATION-TRACKER.md`.

2. Read the task's goal, build requirement, dependencies, tests/validation, expected result, Definition of Done, and authority-document references.

3. Read Document 26 for the current phase boundary.

4. Read only the authority documents needed for the task.

5. If the task changes frontend UI, visual styling, layout, responsive behavior, or reusable UI components:
   - read `docs/design/README.md`;
   - read `docs/design/DESIGN.md`;
   - inspect only the relevant files under `docs/design/references/` when visual intent is needed.

6. If the task touches a major architectural boundary, also check Document 27.

7. Extract requirements into:
   - MUST
   - MUST NOT
   - DEFERRED
   - unresolved decisions

8. If documents conflict, use root `AGENTS.md` authority and accepted ADRs.

9. If a significant choice is genuinely unresolved, stop and flag reviewer/ADR review.

## Context-Efficiency Rule

Do not read all numbered documents by default.

Start with tracker authority references and expand only when a concrete dependency or conflict requires it.

For UI tasks, do not inspect all design reference images by default. Read `docs/design/DESIGN.md` first, then inspect only the reference screens relevant to the current task.

Do not repeatedly re-derive visual tokens, palette, typography, spacing, or component styling when they are already defined by the approved design authority.

## Visual Authority Rule

For frontend UI work:

- Numbered Source-of-Truth documents and the Implementation Tracker define **WHAT the product does**.
- Frontend architecture defines **HOW frontend responsibilities are structured**.
- `docs/design/DESIGN.md` defines **HOW approved UI should look**.
- `docs/design/references/` clarifies visual intent, layout, information density, hierarchy, and overall product character.
- Reference screenshots are not pixel-perfect implementation contracts.
- Mock text, metrics, controls, medical content, or behavior visible in a reference image do not become product requirements unless supported by the numbered Source-of-Truth documents or tracker.
- If a reference screenshot conflicts with product behavior, educational rules, security, domain rules, accessibility requirements, or architecture, the higher product/technical authority wins.
- `DESIGN.md` remains authoritative for visual treatment where higher authorities do not define appearance.
- Do not independently redesign Hippocampus unless the owning tracker task explicitly requires a design change.
- Preserve the approved Hippocampus visual direction rather than substituting generic SaaS, dashboard, LMS, EHR, or chatbot styling.

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

## Hard Visual Rules

When implementing approved frontend UI:

- Preserve the frozen Hippocampus visual direction defined in `docs/design/DESIGN.md`.
- Favor learning clarity, readability, cognitive simplicity, and medical-content visibility over decorative polish.
- Do not invent new palettes, typography systems, spacing systems, or component aesthetics when the design authority already defines them.
- Do not infer product behavior from visual mock content.
- Do not introduce unnecessary card density, excessive shadows, gradients, glassmorphism, gamification, or generic dashboard styling.
- Medical imagery should retain visual priority where the learning activity is image-first.
- Learning evidence must remain qualitative where required by the product Source of Truth; visual design must not introduce fake percentages or unsupported precision.
- Color must not be the sole carrier of meaning.
- Accessibility requirements remain mandatory even when a reference image does not explicitly demonstrate every accessibility state.

## Output for Planning/Review

Always state:

1. task ID;
2. authoritative docs read;
3. whether visual authority was required and, if so, which design files were consulted;
4. requirements extracted;
5. scope exclusions;
6. unresolved decisions;
7. whether ADR review is required.