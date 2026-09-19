# Hippocampus Agent Orchestration Workflow

## Purpose

This document defines the preferred Human/ChatGPT/Codex workflow for Hippocampus. It supplements `AGENTS.md`; it does not override numbered Source-of-Truth documents, accepted ADRs, or the implementation tracker.

The goal is to minimize agent context, tool usage, command output, and narration without weakening scope control, review, or security gates.

## Three-Role Model

Normal work uses only three roles:

1. **Plan — Human + ChatGPT**
2. **Implement — Codex**
3. **Review — independent reviewer**

There is no separate validation agent in the normal loop. Broad validation is run by the user or CI and supplied to review as evidence.

## 1. Plan — Human + ChatGPT

Resolve the hard reasoning once:

- exact tracker task and phase scope;
- minimum relevant Source-of-Truth/ADR authority;
- current implementation boundary;
- required behavior and exclusions;
- architecture/framework decisions that actually matter;
- tests Codex should create/modify;
- exact commands the user should run after implementation;
- security-sensitive cases;
- Definition of Done and stop conditions.

End with a compact implementation packet. Codex should not re-plan it.

The packet should contain:

```text
TASK
GOAL
AUTHORITY
IMPLEMENT
DO NOT
EXPECTED FILES (when useful)
AGENT TEST AUTHORIZATION
USER VALIDATION
STOP CONDITIONS
PUBLICATION
```

`AGENT TEST AUTHORIZATION` always follows the repository command policy: Codex may run only tests/test methods it creates or modifies, and it must not run them when they require Docker/Testcontainers/external infrastructure/application/browser startup or similarly expensive setup.

`USER VALIDATION` contains the exact broader commands the user should run manually.

## 2. Implement — Codex

Codex receives the packet and uses `hippocampus-implement-task`.

Default behavior:

- implement immediately; do not re-plan;
- read only affected code/authority needed by the packet;
- make the smallest complete change;
- use one agent;
- load detailed engineering guidance only for a concrete issue;
- do not perform broad repository/Git/agent/skill reconnaissance;
- do not run broad commands;
- remain quiet while working unless a blocker, contradiction, significant decision, or eligible changed-test failure must be reported.

### Command rule

Default: **run nothing**.

The only default exception is the narrowest practical test/test method that Codex itself created or modified in the current implementation.

Even that test is user-run instead when it needs:

- Docker or Docker Compose;
- Testcontainers;
- PostgreSQL/database/container startup;
- external services/infrastructure;
- application/server startup;
- Playwright/browser/E2E startup;
- similarly expensive environment setup.

Codex must not run unchanged tests, full suites, build/package/verify, lint/typecheck, validation scripts, architecture suites, application startup, container validation, or Git hygiene commands merely because they are normally useful.

The user may explicitly authorize an exact additional command when desired.

### Final implementation report

Keep it terse:

```text
IMPLEMENTED — USER VALIDATION REQUIRED

Changed:
- ...

Agent-run tests:
- <changed test — PASS>
# or: none

USER VALIDATION
- <exact command>
- <exact command>
```

Add a short note only when a material caveat exists.

Codex must not claim `VALIDATION PASS`, `Ready for Review`, or `Done` based only on its changed-test execution.

## 3. User / CI Validation

After implementation, the user runs the commands listed in `USER VALIDATION`.

Examples may include focused existing regression tests, broader Maven/npm validation, Docker/Testcontainers integration tests, application startup, E2E, lint/typecheck/build, or repository validation scripts when the tracker/task requires them.

Successful broad output does not need to be pasted into agent context. Prefer concise evidence such as:

```text
backend validation: PASS
integration test: PASS
frontend validation: PASS
```

When a command fails, provide only the first causal failure/relevant log section to Codex or the reviewer unless more context is required.

This keeps expensive deterministic work outside agent context while preserving evidence.

## 4. Review — Independent Reviewer

The reviewer inspects:

- implementation packet;
- actual diff/changed files;
- tracker/SOT alignment;
- scope discipline;
- architecture/module boundaries;
- correctness and failure behavior;
- created/modified tests;
- user/CI validation evidence;
- security-sensitive behavior;
- tracker/evidence accuracy.

The reviewer does not rerun broad validation by default. Missing evidence results in `VALIDATION INCOMPLETE` with the exact user/CI command required.

### Review pass 1 — general implementation review

Use `hippocampus-review-implementation`.

If a correction is narrow, send only a narrow correction packet back to Codex. Do not re-plan the whole task unless the finding invalidates the plan.

### Review pass 2 — independent security review

After general review is clean and required user/CI evidence is available, run `hippocampus-security-vulnerability-review` independently.

Use OWASP ASVS 5.0.0 as the verification baseline with OWASP Top 10:2025 and OWASP API Security Top 10 as threat lenses.

Critical/High findings block completion. Medium findings normally block unless a human explicitly accepts the risk. If a control cannot be adequately verified, return `MANUAL SECURITY REVIEW REQUIRED`.

The security pass belongs to the Review role; it is not a fourth normal agent role.

## Correction Loop

For a narrow defect:

```text
Reviewer finding
  -> narrow correction packet
  -> Codex implementation
  -> Codex runs only newly created/modified eligible test
  -> user runs listed affected validation commands
  -> reviewer checks updated diff/evidence
```

Do not resend the full plan or rerun unrelated validation.

## Communication Rule

Implementation agents are silent by default.

Do not narrate file reads, searches, routine reasoning, progress, skipped commands, or obvious edits. Speak only for a real blocker/contradiction/decision, a material eligible-test failure, or the final report.

Reviewers should likewise report only material findings, missing evidence, verdict, and next action.

## Context / Token Rule

Use agent tokens for decisions and code changes, not for waiting on containers, reading successful infrastructure logs, or repeating deterministic validation that the user/CI can run.

Prefer:

**detailed planning once -> compact packet -> quiet implementation -> changed-test-only agent execution -> user/CI validation -> evidence-based review -> narrow fixes**

Expand context only for a concrete dependency, contradiction, failure, architecture question, or security requirement.
