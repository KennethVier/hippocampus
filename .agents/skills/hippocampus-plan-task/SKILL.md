---
name: hippocampus-plan-task
description: Use only when a detailed Hippocampus tracker-task plan is requested before coding. Planning is read-only and should produce a compact execution packet so the implementation agent can start immediately without re-planning.
---

# Plan a Hippocampus Tracker Task

## Non-Negotiable

PLAN ONLY. Do not modify files, install dependencies, implement, commit, push, or create a PR.

## Workflow

1. Identify the exact tracker task.
2. Resolve only the required Source-of-Truth context.
3. Inspect only repository files needed to understand the current implementation boundary.
4. Determine goal, dependencies, scope exclusions, required behavior, expected files, validation, DoD, and security-sensitive boundaries.
5. Make architecture/framework decisions only where the task creates real design pressure.
6. Flag unresolved significant decisions for reviewer/ADR handling.
7. Produce the detailed plan and a short implementation execution packet.
8. Stop.

Do not automatically load Java, Spring Boot, architecture, testing, or security skills. Load a detailed skill only when the plan contains a concrete question that requires its guidance.

## Required Plan

Cover:

- task and authority;
- current repository assessment;
- MUST / MUST NOT / DEFERRED;
- responsibility/module boundaries;
- significant architecture/framework decisions and why;
- security risks and required negative cases;
- projected file changes;
- ordered implementation steps;
- focused and tracker-required validation;
- Definition of Done mapping;
- scope exclusions;
- unresolved decisions / ADR requirement;
- expected end state.

Be detailed enough that implementation does not need to rediscover the plan, but do not copy large source passages.

## Execution Packet

End with a compact handoff containing only:

1. task ID;
2. required behavior;
3. controlling decisions/adjustments not obvious from the tracker;
4. expected scope/files when useful;
5. validation requirements;
6. stop conditions;
7. publication restriction if applicable.

The implementation agent should use `hippocampus-implement-task` and implement this packet directly.

## Reject the Plan If It

- pulls later tracker work forward;
- changes architecture without governance;
- adds undocumented services/dependencies/features;
- introduces speculative abstraction;
- omits required tests/DoD/security-sensitive negative behavior;
- leaves a significant unresolved decision hidden inside implementation;
- delegates deterministic application decisions to AI.
