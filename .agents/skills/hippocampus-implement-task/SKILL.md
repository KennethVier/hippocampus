---
name: hippocampus-implement-task
description: Default Hippocampus execution workflow for an approved implementation packet. Antigravity is the default local executor; Jules may be used as an optional cloud executor. Implement directly with minimal context, no re-planning, no broad command execution, changed-test-only execution when eligible, and a terse user-validation handoff.
---

# Implement a Hippocampus Tracker Task

## Goal

Implement the approved task correctly with the smallest sufficient context, change set, command usage, and response output.

If an externally approved implementation packet exists, treat it as the working plan and start implementation. Do not reproduce or re-derive that plan unless implementation exposes a real contradiction, blocker, or significant unresolved decision.

This skill is executor-neutral. It is normally used by Antigravity locally. Jules may follow the same contract when selected for cloud execution.

## Execution

1. Read root `AGENTS.md` if not already established in the current session.
2. Read the approved implementation packet and exact tracker task.
3. Read only authority/files required by that packet or a concrete implementation blocker.
4. Inspect only the affected implementation/tests needed to make the change safely.
5. Implement the smallest complete change within current tracker scope.
6. Add/update behavior-oriented tests where required.
7. Run only an eligible test created or modified by this implementation, following the command policy below.
8. Return a concise implementation report containing exact user/CI validation commands.

Do not run a separate validation skill during ordinary implementation.

## Approved Packet Is Controlling

The implementation executor must not silently change:

- tracker task scope;
- Source-of-Truth behavior;
- accepted ADR decisions;
- architecture/module ownership;
- authentication/security model;
- runtime AI/provider architecture;
- dependencies/infrastructure;
- later-task sequencing.

If repository reality contradicts the approved packet, stop and report the contradiction instead of improvising a new plan.

For Jules specifically: a Jules-generated plan is an execution restatement only. It does not override the externally approved Hippocampus packet.

## Lean Implementation Rules

Prefer, in order:

1. reuse an existing Hippocampus pattern;
2. use Java/Spring/React/platform capabilities already available;
3. use an existing project dependency;
4. write the smallest custom implementation;
5. introduce a new abstraction only when current requirements justify it;
6. introduce a new dependency only when implementation cannot correctly proceed without it and project governance allows it.

Do not:

- refactor adjacent code merely because it could be cleaner;
- generalize for hypothetical future tracker tasks;
- create extension points before current requirements need them;
- introduce interfaces without a real boundary/contract;
- perform broad repository reconnaissance;
- inspect Git HEAD/history/branches/remotes/PR metadata for ceremony;
- search for or enumerate agents/subagents;
- spawn subagents by default;
- enumerate/load multiple skills “to be safe”;
- inspect unrelated modules/docs;
- install/download tools/dependencies/agents/CLIs without explicit need and authorization;
- reread established context unnecessarily;
- re-plan an already approved task.

Single-agent execution is the default.

## Command Policy — Default Is Run Nothing

Do not run any command unless it is permitted below or the current user instruction explicitly authorizes the exact command.

### Permitted by default

Only a test/test method that was **created or modified by the current implementation** may be run.

Use the narrowest practical target:

1. exact changed test method;
2. changed test class/file only when method-level execution is unavailable or impractical.

The test must also be cheap/local. If it requires Docker, Docker Compose, Testcontainers, database/container startup, external infrastructure/service access, application startup, Playwright/browser startup, or similarly expensive environment setup, **do not run it**. Put its exact command in `USER VALIDATION` instead.

### Not permitted by default

Do not run:

- unchanged/existing tests merely because production code changed;
- full/module test suites;
- Maven `verify`, `package`, or broad lifecycle commands;
- npm test suites beyond the changed test file/method;
- build, lint, typecheck, formatting, validation scripts;
- architecture suites;
- Docker/Docker Compose/Testcontainers;
- PostgreSQL/database startup;
- Flyway/container validation;
- application/server startup;
- Playwright/E2E/browser journeys;
- Git diff/status/check/stat/history commands solely for routine validation;
- any other command not explicitly permitted.

Do not infer permission from tracker phrases such as “required validation”, “recommended”, “normally run”, or general engineering practice. Those requirements become user/CI-run commands in the final report unless the user explicitly authorizes agent execution.

## Skill Routing

Root `AGENTS.md` contains the persistent baseline. Load detailed guidance only for a concrete issue that cannot be resolved safely from the packet/current code:

- repository/task orientation → `hippocampus-onboard-agent` only for a fresh/unfamiliar session
- authority resolution → `hippocampus-source-of-truth`
- Java language/domain design → `hippocampus-java-spring-engineering`
- Spring framework behavior → `hippocampus-spring-boot-engineering`
- React/TypeScript → `hippocampus-react-typescript-engineering`
- non-trivial architecture/pattern choice → `hippocampus-architecture-patterns`
- specialized test/security-test design → `hippocampus-testing-security`

Do not automatically chain these skills. Cross-references are routing hints, not instructions to load more context. The independent `hippocampus-review-implementation` and `hippocampus-security-vulnerability-review` belong to the later Review role, not implementation.

## Scope / Safety

- Do not pull later tracker work forward.
- Do not introduce undocumented architecture, dependencies, infrastructure, or product capability.
- Preserve module/dependency direction and authorization boundaries.
- Preserve the application-owned learning-policy boundary.
- Preserve Provider Router abstraction for runtime AI; do not couple domain/application logic directly to Gemini/Ollama/provider SDKs.
- Security-sensitive uncertainty fails closed.
- Do not weaken tests to obtain green output.
- Do not commit, push, or create/update a PR unless the implementation packet/current user explicitly authorizes publication.
- If a significant unresolved decision requires Document 27/ADR handling, stop instead of inventing the decision.

## Communication Policy — Quiet by Default

Do not narrate work while implementing.

Do not announce:

- files being opened;
- searches being performed;
- routine reasoning;
- intended edits;
- progress updates;
- commands intentionally skipped;
- obvious implementation details.

Speak before completion only when necessary:

1. a real blocker prevents implementation;
2. the packet conflicts with authority/repository reality;
3. a significant unresolved decision requires user input;
4. an eligible changed test fails and materially affects implementation.

When blocked, use only:

```text
BLOCKED

Issue:
<concise factual blocker>

Need:
<exact decision/input required>
```

## Implementation Report

Keep the final response terse. Do not produce an architecture/security essay unless a discovered issue requires it.

Use this shape:

```text
IMPLEMENTED — USER VALIDATION REQUIRED

Changed:
- <files or concise behavior>

Agent-run tests:
- <exact created/modified test method/file — PASS/FAIL>
```

If none were eligible:

```text
Agent-run tests:
- none
```

Then include exact commands the user or CI should run:

```text
USER VALIDATION
- <exact command>
- <exact command>
```

Include container/integration commands here rather than running them.

Optionally add one short `Notes:` entry only for a material caveat/blocker.

Do not claim `VALIDATION PASS`, `Ready for Review`, or `Done` merely because an eligible changed test passed. Broader validation remains user/CI evidence for the independent Review role.

## Stop Conditions

Stop instead of improvising when:

- the packet conflicts with a higher-authority document or accepted ADR;
- the tracker task/dependency state does not permit the requested behavior;
- implementation would require a significant new architecture/infrastructure decision;
- a security-sensitive boundary cannot be resolved safely;
- the change would pull later tracker work forward;
- the task cannot meet its Definition of Done under the approved scope.
