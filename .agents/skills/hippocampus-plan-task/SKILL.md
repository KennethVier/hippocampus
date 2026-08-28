---
name: hippocampus-plan-task
description: Use when the user asks Codex to plan a Hippocampus implementation-tracker task before coding. Produces a source-grounded, scope-safe plan with SRP/pattern/security reasoning and must not modify files.
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
7. Apply `hippocampus-java-spring-engineering` for Java/Spring work.
8. Apply `hippocampus-react-typescript-engineering` for React/TypeScript work.
9. Apply `hippocampus-architecture-patterns` for non-trivial responsibility/pattern decisions.
10. Identify security-sensitive boundaries that the implementation and final vulnerability review must verify.
11. Produce the required plan.
12. Stop.

## Required Plan Output

### 1. Task
### 2. Authoritative Sources Read
### 3. Current Repository Assessment
### 4. Requirements Extracted
Separate MUST / MUST NOT / DEFERRED.
### 5. Responsibility and Boundary Design
Identify owning module, SRP boundaries, dependency direction, domain invariants, and framework/external boundaries.
### 6. Pattern Decisions
For each significant pattern: problem/design pressure, selected pattern, why it fits, and complexity cost. Also identify important patterns deliberately not introduced when that prevents speculative abstraction.
### 7. Security Risk Assessment
Identify relevant trust boundaries, authorization/ownership risks, untrusted inputs, sensitive data/secrets, file/network/provider concerns, and required negative security tests. This is design-time risk identification, not the final security verdict.
### 8. File Change Projection
### 9. Implementation Steps
### 10. Tests / Validation
For each: type, behavior proven, expected result. Include negative/security tests when risk warrants them.
### 11. Definition of Done Mapping
For each DoD item: implementation, verification, evidence. Include the independent `hippocampus-security-vulnerability-review` gate after tests and general implementation review.
### 12. Scope Exclusions
### 13. Risks / Decisions
Classify: already decided / normal implementation choice / reviewer-ADR required.
### 14. Expected End State

## Self-Reject the Plan If It

- steals later-task scope;
- adds undocumented dependencies/services;
- adds speculative abstractions or patterns without real design pressure;
- changes architecture without governance;
- mixes unrelated responsibilities without justification;
- omits tracker tests/DoD;
- omits relevant authorization/ownership/security-negative tests;
- outsources deterministic application decisions to AI;
- treats a final security scan as a substitute for secure design.
