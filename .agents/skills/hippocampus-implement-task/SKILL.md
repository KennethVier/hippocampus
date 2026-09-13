---
name: hippocampus-implement-task
description: Default execution workflow for an approved Hippocampus tracker task. Implement directly with minimal context, targeted repository reads, focused validation, required post-implementation validation, and no unnecessary planning or broad repository reconnaissance.
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
6. Run focused changed-behavior validation first.
7. Run `hippocampus-validate-implementation` as the required post-implementation gate.
8. Inspect diff hygiene and the complete changed diff when available.
9. Report concise factual evidence, including the validation verdict.

## Implementation Focus

By default, avoid broad repository reconnaissance, unrelated history/PR inspection, unnecessary skill discovery, subagents, unrelated modules/docs, speculative dependencies, and re-planning an already approved task. Expand context only when a concrete blocker, dependency, failing test, ambiguity, or security/architecture concern makes it necessary.

Single-agent execution is the default.

## Skill Routing

Root `AGENTS.md` contains the persistent baseline. Load detailed engineering guidance only when materially needed:

- Java language/domain design → `hippocampus-java-spring-engineering`
- Spring framework behavior → `hippocampus-spring-boot-engineering`
- React/TypeScript → `hippocampus-react-typescript-engineering`
- non-trivial architecture/pattern choice → `hippocampus-architecture-patterns`
- specialized test/security-test design → `hippocampus-testing-security`

`hippocampus-validate-implementation` is the required post-implementation gate rather than an optional engineering skill. Use it after focused implementation tests. The independent `hippocampus-security-vulnerability-review` remains a later completion gate.

## Scope / Safety

- Do not pull later tracker work forward.
- Do not introduce undocumented architecture, dependencies, infrastructure, or product capability.
- Preserve module/dependency direction and authorization boundaries.
- Security-sensitive uncertainty fails closed.
- Do not weaken tests to obtain green output.
- If a significant unresolved decision requires Document 27/ADR handling, stop instead of inventing the decision.

## Validation

Implementation validation is split deliberately:

1. run directly affected unit/integration/regression tests while implementing;
2. after the implementation candidate exists, use `hippocampus-validate-implementation` to run applicable broad deterministic checks and prove the changed real artifact when runtime behavior is part of the tracker Expected Result;
3. only `VALIDATION PASS` is ready for external implementation review;
4. `VALIDATION FAIL` requires correction and rerun;
5. `VALIDATION INCONCLUSIVE` requires the missing evidence in an authoritative environment and is not a pass.

When validation fails, investigate the first causal failure and expand context only as needed to resolve it.

## Report

Keep the final implementation report short:

- changed: what was implemented;
- tests: focused validation results;
- validation: `VALIDATION PASS`, `VALIDATION FAIL`, or `VALIDATION INCONCLUSIVE`;
- real-artifact: exact behavior proof, `not required`, or missing evidence;
- scope: confirmation/no known drift;
- blockers: none or exact blocker;
- state: publication state as explicitly authorized by the task packet.

Do not generate a long architecture/security essay unless the task or a discovered risk requires one.
