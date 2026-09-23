---
name: hippocampus-plan-task
description: Optional in-repository planner for Hippocampus tracker work. Normal planning is Human + Gemini. Produce a compact executor-ready implementation packet with minimum authority, exact scope, permitted changed-test execution, user/CI validation commands, publication restrictions, and stop conditions.
---

# Plan a Hippocampus Tracker Task

## Role

Normal Hippocampus planning happens with the human + Gemini planning role. Use this skill when planning is intentionally performed inside a coding environment or when an approved packet must be reconstructed from repository authority.

PLAN ONLY. Do not modify implementation files, run validation commands, install dependencies, implement, commit, push, create a PR, or change tracker status while planning unless the user explicitly authorizes a documentation-only planning change.

The output is executor-neutral. The normal executor is Antigravity locally; Jules may be selected for cloud execution.

## Workflow

1. Identify the exact tracker task in `docs/IMPLEMENTATION-TRACKER.md`.
2. Resolve only the Source-of-Truth/ADR context required by that task.
3. Confirm dependencies, current phase boundary, and explicit exclusions.
4. Inspect only repository files needed to understand the current implementation boundary.
5. Determine required behavior, affected responsibilities, tests to create/modify, user/CI validation commands, Definition of Done, publication constraints, and stop conditions.
6. Make architecture/framework decisions only when current scope creates real design pressure.
7. Flag significant unresolved decisions for human/Document-27/ADR handling.
8. Produce the implementation packet and stop.

Do not automatically load Java, Spring Boot, architecture, testing, or security skills. Load detailed guidance only for a concrete unresolved question.

## Implementation Packet

Keep the handoff compact and implementation-ready:

```text
TASK
<TASK-ID>

GOAL
<required behavior>

AUTHORITY
<only controlling docs/ADR/tracker facts needed by execution>

IMPLEMENT
- <required change>

DO NOT
- <scope exclusions / later work / prohibited changes>

EXPECTED FILES
- <only when useful>

RELEVANT SKILLS
- `hippocampus-implement-task`
- <at most one or two additional skills only when materially needed>

AGENT TEST AUTHORIZATION
- The executor may run only tests/test methods it creates or modifies.
- Prefer exact changed test method; fall back to changed class/file only when needed.
- If the changed test requires Docker, Testcontainers, external infrastructure, application/browser startup, or expensive environment setup: do not run it.

USER VALIDATION
- <exact command user/CI should run after implementation>
- <exact command user/CI should run after implementation>

STOP CONDITIONS
- <contradiction / ADR / security-sensitive ambiguity / scope conflict>

PUBLICATION
- <normally no commit/push/PR unless explicitly authorized>
```

Do not require the executor to rediscover or restate this plan.

## Antigravity Handoff

Antigravity natively discovers `.agents/skills/`. The implementation prompt should be short:

```text
Implement the approved Hippocampus packet below.
Use /hippocampus-implement-task.
Do not re-plan unless a concrete contradiction or blocker appears.
<packet>
```

Do not make Antigravity repeat repository orientation if the current session already has it.

## Jules Handoff

Jules reads root `AGENTS.md`, but task-critical skill instructions should be named explicitly in the task prompt:

```text
Before executing, follow:
- AGENTS.md
- .agents/skills/hippocampus-implement-task/SKILL.md
- <additional task-specific skill only if needed>

The approved packet below is controlling. Your generated Jules plan may restate it for execution but must not expand or redesign it.
<packet>
```

If the Jules-generated plan materially deviates from the approved packet, the human must not approve it; return to planning for resolution.

## Planning Rules

- Keep authority reads minimal.
- Do not scan all docs or repository history.
- Do not pull later tracker work forward.
- Do not add undocumented services, dependencies, infrastructure, or features.
- Do not introduce speculative abstractions.
- Do not delegate deterministic application decisions to AI.
- Do not make broad command execution part of implementation.
- Put broad/integration/build/lint/container/E2E validation under `USER VALIDATION` instead.
- Runtime Gemini/Ollama choices remain governed by Documents 10–15 and their owning tracker tasks; development-tool migration does not alter runtime AI architecture.

## Planning Verdict

Return one:

- `PLAN APPROVED`
- `APPROVED WITH CONTROLLING ADJUSTMENTS`
- `REPLAN REQUIRED`

A significant unresolved architecture/product/security decision requires re-planning/Document-27 handling rather than silent invention.
