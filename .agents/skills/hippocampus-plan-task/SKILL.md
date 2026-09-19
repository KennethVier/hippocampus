---
name: hippocampus-plan-task
description: Optional fallback planner for Hippocampus tracker work. Normal planning is Human + ChatGPT. Produce a compact Codex-ready implementation packet with minimum authority, exact scope, permitted changed-test execution, user-run validation commands, and stop conditions.
---

# Plan a Hippocampus Tracker Task

## Role

Normal Hippocampus planning happens with Human + ChatGPT. Use this skill only when planning is intentionally performed inside the coding environment.

PLAN ONLY. Do not modify files, run validation commands, install dependencies, implement, commit, push, or create a PR.

## Workflow

1. Identify the exact tracker task.
2. Resolve only the Source-of-Truth/ADR context required by that task.
3. Inspect only repository files needed to understand the implementation boundary.
4. Determine required behavior, exclusions, affected responsibilities, tests to create/modify, user-run validation commands, Definition of Done, and stop conditions.
5. Make architecture/framework decisions only when current scope creates real design pressure.
6. Flag significant unresolved decisions for human/ADR handling.
7. Produce the implementation packet and stop.

Do not automatically load Java, Spring Boot, architecture, testing, or security skills. Load detailed guidance only for a concrete unresolved question.

## Implementation Packet

Keep the handoff compact and implementation-ready:

```text
TASK
<TASK-ID>

GOAL
<required behavior>

AUTHORITY
<only controlling docs/ADR/tracker facts needed by implementation>

IMPLEMENT
- <required change>

DO NOT
- <scope exclusions / later work / prohibited changes>

EXPECTED FILES
- <only when useful>

AGENT TEST AUTHORIZATION
- Codex may run only tests/test methods it creates or modifies.
- Prefer exact changed test method; fall back to changed class/file only when needed.
- If the changed test requires Docker, Testcontainers, external infrastructure, application/browser startup, or expensive environment setup: do not run it.

USER VALIDATION
- <exact command user should run after implementation>
- <exact command user should run after implementation>

STOP CONDITIONS
- <contradiction / ADR / security-sensitive ambiguity / scope conflict>

PUBLICATION
- <normally no commit/push/PR unless explicitly authorized>
```

Do not require Codex to rediscover or restate this plan.

## Planning Rules

- Keep authority reads minimal.
- Do not scan all docs/repository history.
- Do not pull later tracker work forward.
- Do not add undocumented services/dependencies/features.
- Do not introduce speculative abstractions.
- Do not delegate deterministic application decisions to AI.
- Do not make broad command execution part of Codex implementation.
- Put broad/integration/build/lint/container/E2E validation under `USER VALIDATION` instead.

Reject/replan only when a material scope, authority, architecture, security, or dependency conflict prevents a safe implementation packet.
