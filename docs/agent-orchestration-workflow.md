# Hippocampus Agent Orchestration Workflow

## Purpose

This document describes the preferred human/ChatGPT/Codex working loop for Hippocampus. It supplements `AGENTS.md`; it does not override numbered Source-of-Truth documents, accepted ADRs, or the implementation tracker.

The goal is to reduce unnecessary Codex context/tool usage without weakening implementation quality, review, testing, or security gates.

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
- return a short factual implementation report.

For ordinary implementation, prefer balanced/medium reasoning. Escalate reasoning only when the task is genuinely ambiguous, cross-cutting, unfamiliar, or architecturally difficult.

### 3. Human + ChatGPT: review implementation report and PR/diff

Review the actual implementation, not only Codex's report:

- tracker/SOT alignment;
- scope discipline;
- architecture/module boundaries;
- correctness and failure behavior;
- test quality and required validation;
- security-sensitive behavior;
- tracker/evidence accuracy.

Do not make Codex run a second broad review of its own work by default.

### 4. Codex or ChatGPT: narrow correction when needed

If review finds a defect, send only a correction packet:

- exact finding;
- required behavior;
- affected scope;
- tests/validation;
- explicit no-scope-expansion rule.

Do not resend the full original plan unless the finding invalidates the plan itself.

Repeat review on the updated diff/PR.

### 5. Independent security gate

After required tests pass and general review has no blocker, run `hippocampus-security-vulnerability-review` independently as required by `AGENTS.md`.

This gate is not removed or merged into routine implementation to save tokens.

## Context Expansion Rule

Start narrow. Expand context only when a concrete dependency, contradiction, failure, architecture question, or security requirement justifies it.

Do not optimize by making prompts vague. The desired tradeoff is:

**detailed planning once -> compact execution packet -> focused implementation -> evidence-based review -> narrow fixes**

not repeated full-context planning/review inside every Codex run.

## Fresh-Thread Guidance

Prefer a fresh Codex thread for a materially new stage or narrow correction when the previous thread contains substantial planning/tool history that is no longer needed.

Typical separation:

- planning: Human + ChatGPT;
- implementation: Codex thread A;
- narrow correction/CI root-cause fix: Codex thread B when useful;
- general review: Human + ChatGPT;
- independent security review: separate gate.

Preserve only the execution facts needed by the next stage rather than carrying the full prior conversation.
