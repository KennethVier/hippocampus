# Hippocampus Agent Orchestration Workflow

## Purpose

This document describes the preferred human/ChatGPT/Codex working loop for Hippocampus. It supplements `AGENTS.md`; it does not override numbered Source-of-Truth documents, accepted ADRs, or the implementation tracker.

The goal is to reduce unnecessary Codex context/tool usage without weakening implementation quality, review, testing, runtime proof, or security gates.

## Default Loop

### 1. Human + ChatGPT: detailed planning

Before implementation, resolve the hard reasoning here:

- exact tracker task and phase scope;
- relevant Source-of-Truth authority;
- current implementation boundary;
- required behavior and exclusions;
- architecture/framework decisions that actually matter;
- expected files/change shape when useful;
- test/validation strategy;
- real-artifact behavior that must be proved after implementation;
- security-sensitive cases;
- Definition of Done and stop conditions.

The output should be detailed enough to avoid Codex rediscovering the task, then end with a compact execution packet.

### 2. Codex: implement directly

Codex receives the execution packet and uses `hippocampus-implement-task`.

Default behavior:

- implement immediately rather than re-planning;
- read only tracker/authority/code needed for the task;
- use targeted searches and focused tests;
- keep the change minimal and reviewable;
- use one agent;
- avoid Git/head/history/branch/PR reconnaissance unless implementation genuinely depends on it;
- avoid agent/subagent/skill discovery unless a concrete blocker requires it;
- avoid new tools/dependencies unless required for the approved implementation;
- load detailed engineering skills only when their guidance is materially needed;
- do not report the candidate ready for review until the validation gate completes.

For ordinary implementation, prefer balanced/medium reasoning. Escalate reasoning only when the task is genuinely ambiguous, cross-cutting, unfamiliar, or architecturally difficult.

### 3. Codex: post-implementation validation gate

After the implementation and focused changed-behavior tests exist, Codex must use `hippocampus-validate-implementation` before finishing the task report.

The gate has two layers:

1. **Deterministic local validation** — run the task-required automated checks from narrow to broad. Use `scripts/validation/validate.mjs` for the ordinary broad backend/frontend checks when applicable. The runner stores complete logs under `.validation/` and prints only concise pass/fail evidence so successful validation does not consume unnecessary model context.
2. **Real-artifact verification** — when the tracker Expected Result is observable runtime behavior, exercise the actual application/use case with the lowest-cost reliable proof. A production-composed integration/E2E test may satisfy this when an additional live run would add no material evidence.

Typical commands:

- backend: `node scripts/validation/validate.mjs backend`
- frontend: `node scripts/validation/validate.mjs frontend`
- both: `node scripts/validation/validate.mjs all`

The validation verdict must be one of:

- `VALIDATION PASS` — required local deterministic evidence passed and required real-artifact behavior was proved;
- `VALIDATION FAIL` — a reproducible defect remains;
- `VALIDATION INCONCLUSIVE` — required evidence could not be produced because of a genuine environment/tooling/external-capability limitation.

Only `VALIDATION PASS` proceeds to ordinary external implementation review. A failure returns to narrow correction. Inconclusive validation must name the missing evidence and where it must be run; it is never converted into a pass by assumption.

### 4. Human + ChatGPT: review implementation report and PR/diff

Review the actual implementation, not only Codex's report:

- tracker/SOT alignment;
- scope discipline;
- architecture/module boundaries;
- correctness and failure behavior;
- test quality and required validation;
- real-artifact proof and any validation limitation;
- security-sensitive behavior;
- tracker/evidence accuracy.

Do not make Codex run a second broad AI review of its own work by default. Deterministic validation plus real-artifact proof are not substitutes for this external diff review; they are evidence for it.

### 5. Codex or ChatGPT: narrow correction when needed

If review or validation finds a defect, send only a correction packet:

- exact finding;
- required behavior;
- affected scope;
- tests/validation;
- explicit no-scope-expansion rule.

Do not resend the full original plan unless the finding invalidates the plan itself.

After a correction, rerun only the focused checks and broader validation that the change could invalidate, then repeat review on the updated diff/PR.

### 6. Independent security gate

After required tests pass and general review has no blocker, run `hippocampus-security-vulnerability-review` independently as required by `AGENTS.md`.

This gate is not removed or merged into routine implementation validation to save tokens.

## Validation Cost Rule

Prefer deterministic local computation over model reasoning whenever a machine can prove the same fact more reliably.

- Let Maven, Vitest, TypeScript, ESLint, Flyway/Testcontainers, Playwright, Git, and task-specific scripts produce evidence.
- Keep successful command output in `.validation/logs/` rather than pasting it into Codex context.
- Let Codex read detailed logs only for the first causal failure or when a specific piece of evidence is needed.
- Do not add another AI validation/review agent by default.

The agent orchestrates and interprets validation; the expensive work should remain local and deterministic wherever practical.

## Context Expansion Rule

Start narrow. Expand context only when a concrete dependency, contradiction, failure, architecture question, or security requirement justifies it.

Do not optimize by making prompts vague. The desired tradeoff is:

**detailed planning once -> compact execution packet -> focused implementation -> deterministic/runtime validation -> evidence-based review -> narrow fixes**

not repeated full-context planning/review inside every Codex run.

## Fresh-Thread Guidance

Prefer a fresh Codex thread for a materially new stage or narrow correction when the previous thread contains substantial planning/tool history that is no longer needed.

Typical separation:

- planning: Human + ChatGPT;
- implementation + validation: Codex thread A;
- narrow correction/CI root-cause fix: Codex thread B when useful;
- general review: Human + ChatGPT;
- independent security review: separate gate.

Preserve only the execution facts needed by the next stage rather than carrying the full prior conversation.
