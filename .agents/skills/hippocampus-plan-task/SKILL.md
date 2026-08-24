---
name: hippocampus-plan-task
description: Use when the user asks Codex to plan a Hippocampus implementation-tracker task before coding. Produces a source-grounded, scope-safe plan and must not modify files.
---

# Plan a Hippocampus Tracker Task

## Non-Negotiable

PLAN ONLY. Do not create/modify files, install dependencies, run generators, implement, or commit.

## Workflow

1. Identify exact task ID.
2. Apply the Source-of-Truth workflow.
3. Inspect current repository state.
4. Extract goal, dependencies, build requirements, expected behavior, tests, DoD, and scope exclusions.
5. Check current phase in Document 26.
6. Read only relevant authority docs.
7. For Java/Spring work, apply `hippocampus-java-spring-engineering`.
8. Produce the required plan.
9. Stop.

## Required Plan Output

### 1. Task
### 2. Authoritative Sources Read
### 3. Current Repository Assessment
### 4. Requirements Extracted
Separate MUST / MUST NOT / DEFERRED.
### 5. Proposed Design
### 6. File Change Projection
### 7. Implementation Steps
### 8. Tests / Validation
For each: type, behavior proven, expected result.
### 9. Definition of Done Mapping
For each DoD item: implementation, verification, evidence.
### 10. Scope Exclusions
### 11. Risks / Decisions
Classify: already decided / normal implementation choice / reviewer-ADR required.
### 12. Expected End State

## Self-Reject the Plan If It

- steals later-task scope;
- adds undocumented dependencies/services;
- adds speculative abstractions;
- changes architecture without governance;
- omits tracker tests/DoD;
- outsources deterministic application decisions to AI.
