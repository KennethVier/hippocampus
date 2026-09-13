---
name: hippocampus-validate-implementation
description: Required post-implementation validation gate for Hippocampus tracker work. Run deterministic local checks, prove the changed artifact actually works when runtime behavior is relevant, classify failures without weakening tests, and return PASS, FAIL, or INCONCLUSIVE evidence before external review.
---

# Validate a Hippocampus Implementation

## Goal

Prove the implementation is ready for external review using the cheapest reliable evidence.

Validation has two layers:

1. **Deterministic validation** — tests, architecture checks, migrations, typecheck/lint/build, diff hygiene, and other task-required commands.
2. **Real-artifact verification** — when the task changes observable runtime behavior, exercise the actual application/use case sufficiently to prove the tracker Expected Result instead of relying only on compilation or isolated unit tests.

This is a post-implementation gate. It does not replace external implementation review, CI, or the independent security review.

## Entry Conditions

Use this skill after the approved implementation and focused changed-behavior tests exist, and before reporting `Ready for Review`.

Start from the task's existing authority and execution packet. Do not reopen broad planning or repository reconnaissance.

## 1. Derive the Finish Conditions

Before running broad validation, derive a short checklist from the tracker task:

- `Tests / validation`;
- `Expected result`;
- `Definition of Done`;
- approved execution-plan acceptance criteria;
- security/authorization behavior materially changed by the task.

Do not invent extra product requirements. Do not treat later tracker work as a finish condition.

## 2. Deterministic Validation

Run the narrowest relevant checks first, then the task-required broad checks.

Prefer this order:

1. focused unit/integration/regression tests for changed behavior;
2. architecture tests if module/dependency boundaries changed;
3. Flyway/PostgreSQL/Testcontainers validation if persistence changed;
4. focused frontend tests if frontend behavior changed;
5. repository broad validation required by the task;
6. `git diff --check`;
7. full changed-diff inspection.

For ordinary broad local validation, use the dependency-free runner when it matches the task:

- backend: `node scripts/validation/validate.mjs backend`
- frontend: `node scripts/validation/validate.mjs frontend`
- both: `node scripts/validation/validate.mjs all`

The runner writes complete command output under `.validation/logs/` and a machine-readable `.validation/summary.json`, while keeping console output concise. Read detailed logs only when a check fails or evidence is genuinely needed.

Do not rerun broad checks that already passed unchanged unless a subsequent fix could invalidate them.

## 3. Real-Artifact Verification

Ask one question: **What observable claim did this task change, and what is the lowest-cost way to prove that claim against the real artifact?**

Runtime verification is required when tests/build alone do not demonstrate the tracker Expected Result. Examples:

- API/backend behavior → start the required local infrastructure/application when practical, invoke the changed endpoint/use case, and verify response plus durable state where relevant;
- persistence/migration → migrate a real PostgreSQL database from the required starting state and prove constraints/read-write behavior;
- frontend journey → run the application/preview and use the existing Playwright/browser path for the changed interaction when practical;
- processing/background work → execute the production-composed handler/job path or an integration test that uses the real production wiring;
- authorization/retrieval → use distinct identities/scopes and prove allowed behavior succeeds while foreign/out-of-scope access fails closed;
- CLI/tooling → run the actual command and inspect its produced artifact/output.

A real integration/E2E test using the same production composition may satisfy this layer when starting another copy of the application would add no material evidence. State that explicitly instead of duplicating work.

Do not perform unrelated end-to-end journeys merely for ceremony.

## 4. Failure Classification

On the first causal failure, classify it before changing code:

- `IMPLEMENTATION_DEFECT`
- `REGRESSION`
- `TEST_DEFECT`
- `ENVIRONMENT_LIMITATION`
- `DEPENDENCY_OR_TOOLING_FAILURE`

Then act narrowly:

- implementation defect/regression → fix production code and add/adjust regression coverage as needed;
- genuine test defect → correct the test without weakening the required behavior;
- environment/tooling limitation → preserve the failed/unavailable evidence and report it honestly;
- never delete, skip, loosen, or rewrite a valid failing test merely to obtain green output.

Expand repository context only enough to resolve the classified failure.

## 5. Verdict

Return exactly one validation verdict:

### `VALIDATION PASS`

Use only when all locally available required deterministic checks pass and every required real-artifact claim has been demonstrated.

### `VALIDATION FAIL`

Use when a reproducible implementation/regression/test defect remains unresolved.

A failed implementation must not be reported `Ready for Review`.

### `VALIDATION INCONCLUSIVE`

Use when required evidence cannot be produced because of a genuine environment/tooling/external-capability limitation.

Inconclusive is not a pass. State the missing evidence and the authoritative environment where it must run. Do not fabricate expected results.

## 6. Evidence / Context Efficiency

Keep successful output compact:

- command/check name;
- pass/fail/inconclusive status;
- relevant test count or behavior proof;
- log path when useful.

Do not paste full successful Maven/npm/Playwright logs into the agent response.

On failure, inspect only the relevant failing log section first. Complete logs remain local under `.validation/` and are ignored by Git.

## 7. Final Validation Report

Report:

- `verdict`: `VALIDATION PASS`, `VALIDATION FAIL`, or `VALIDATION INCONCLUSIVE`;
- `deterministic`: focused and broad checks actually run;
- `real-artifact`: exact runtime/behavior claim proved, `not required`, or missing evidence;
- `failure classification`: only when applicable;
- `scope`: confirmation that no later tracker work was introduced;
- `next`: external implementation review only after `VALIDATION PASS`; otherwise fix or obtain the missing evidence.

Do not call the tracker task `Done`. Validation only establishes whether the implementation candidate is ready for external review.
