---
name: hippocampus-implement-task
description: Default execution workflow for an approved Hippocampus tracker task. Implement directly with minimal context, targeted repository reads, focused validation, and no unnecessary planning, Git/history inspection, skill chaining, or subagent discovery.
---

# Implement a Hippocampus Tracker Task

## Goal

Implement the approved task correctly with the smallest sufficient context and change set.

If an external detailed plan/execution packet exists, treat it as the working plan and start implementation. Do not reproduce or re-derive that plan unless implementation exposes a real contradiction or blocker.

## Execution

1. Confirm the tracker task ID and read its entry.
2. Read only authority/files required by the task or execution packet.
3. Inspect only the affected implementation/tests needed to make the change safely.
4. Implement the smallest complete change within current tracker scope.
5. Add/update behavior-oriented tests where required.
6. Run focused validation first; then run the broader validation explicitly required by the tracker/task.
7. Run diff hygiene checks when available.
8. Report concise factual evidence.

## Implementation Focus

Do not, by default:

- perform broad repository reconnaissance;
- inspect Git HEAD, commit history, branches, remotes, PR metadata, or unrelated diffs;
- search for or enumerate agents/subagents;
- spawn subagents;
- enumerate or load multiple skills “to be safe”;
- inspect unrelated modules/docs;
- install or download tools/dependencies/agents/CLIs;
- reread files already established as relevant;
- re-plan an already approved task.

Do any of those only when a concrete blocker, dependency, failing test, ambiguity, or security/architecture concern makes it necessary.

Single-agent execution is the default.

## Skill Routing

Root `AGENTS.md` contains the persistent baseline. Load at most the detailed skill(s) materially needed by the current problem:

- Java language/domain design → `hippocampus-java-spring-engineering`
- Spring framework behavior → `hippocampus-spring-boot-engineering`
- React/TypeScript → `hippocampus-react-typescript-engineering`
- non-trivial architecture/pattern choice → `hippocampus-architecture-patterns`
- specialized test/security-test design → `hippocampus-testing-security`

Do not automatically chain these skills. The independent `hippocampus-security-vulnerability-review` is a later completion gate, not an implementation-time skill load.

## Scope / Safety

- Do not pull later tracker work forward.
- Do not introduce undocumented architecture, dependencies, infrastructure, or product capability.
- Preserve module/dependency direction and authorization boundaries.
- Security-sensitive uncertainty fails closed.
- Do not weaken tests to obtain green output.
- If a significant unresolved decision requires Document 27/ADR handling, stop instead of inventing the decision.

## Validation

Prefer narrow-to-broad:

1. directly affected unit/integration tests;
2. architecture/database/frontend validation when relevant;
3. tracker-required full validation;
4. `git diff --check` and targeted/full diff inspection when available.

When validation fails, investigate the first causal failure and expand context only as needed to resolve it.

## Report

Keep the final implementation report short:

- changed: what was implemented;
- tests: focused + required validation results;
- scope: confirmation/no known drift;
- blockers: none or exact blocker;
- state: uncommitted/unpublished unless publication was explicitly requested.

Do not generate a long architecture/security essay unless the task or a discovered risk requires one.
