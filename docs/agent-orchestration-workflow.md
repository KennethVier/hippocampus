# Hippocampus Agent Orchestration Workflow

## Purpose

This document defines the preferred agent workflow for Hippocampus. It supplements `AGENTS.md`; it does not override numbered Source-of-Truth documents, accepted ADRs, or `docs/IMPLEMENTATION-TRACKER.md`.

The workflow is role-based so model/tool changes do not change project authority. The goal is to minimize agent context, tool usage, command output, duplicated reasoning, and narration without weakening tracker scope, review quality, or security gates.

## Default Tool Assignment

Normal work uses three logical roles:

1. **Plan — Human + ChatGPT**
2. **Implement — Codex local OR Antigravity local**
3. **Review — ChatGPT external independent reviewer**

Default choices:

- ChatGPT is the planning/reasoning surface used with the human to prepare an implementation-ready tracker packet.
- Codex is the default local implementation executor.
- Google Antigravity is an approved alternate local executor. It replaces Codex for that execution; it is not an extra mandatory step.
- ChatGPT performs the external general review and must remain independent of the implementation execution context.
- The required security pass is independent and happens after general review is clean.

There is no separate validation agent in the normal loop. Broad validation is run by the user or GitHub Actions and supplied to review as evidence.

## 1. Plan — Human + ChatGPT

Resolve the hard reasoning once:

- exact tracker task and current phase scope;
- task dependencies and prerequisite completion;
- minimum relevant Source-of-Truth/ADR authority;
- current implementation boundary;
- required behavior and exclusions;
- architecture/framework decisions that actually matter;
- tests the implementation executor should create/modify;
- exact commands the user should run after implementation;
- security-sensitive cases;
- Definition of Done and stop conditions.

Planning is read-only unless the user explicitly requests a documentation-only planning change.

End with a compact implementation packet. The executor must not re-plan it merely because its coding product normally offers a planning step.

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

`AGENT TEST AUTHORIZATION` follows repository command policy: the executor may run only tests/test methods it creates or modifies, and it must not run them when they require Docker/Testcontainers/external infrastructure/application/browser startup or similarly expensive setup.

`USER VALIDATION` contains exact broader commands the user or CI should run.

### Planning output status

Use one of:

- `PLAN APPROVED`
- `APPROVED WITH CONTROLLING ADJUSTMENTS`
- `REPLAN REQUIRED`

Once an implementation packet is approved, that packet controls execution unless a concrete contradiction or blocker is discovered.

## 2. Implement — Codex or Antigravity

### 2.1 Codex — default local executor

Codex receives the approved packet, follows root `AGENTS.md`, and uses `hippocampus-implement-task`.

Default behavior:

- implement immediately from the approved packet;
- do not repeat/re-derive planning merely because a coding product offers a planning mode;
- read only affected code/authority needed by the packet;
- make the smallest complete change;
- use one agent;
- load detailed engineering guidance only for a concrete issue;
- do not perform broad repository/Git/agent/skill reconnaissance;
- do not run broad commands;
- remain quiet while working unless a blocker, contradiction, significant decision, or eligible changed-test failure must be reported.

### 2.2 Antigravity — approved alternate local executor

Antigravity receives the same approved packet and uses `hippocampus-implement-task`.

Antigravity natively discovers repository skills from `.agents/skills/` and agent profiles from `.agents/agents/`. It uses the `hippocampus-implementer` custom agent profile (`.agents/agents/hippocampus-implementer/agent.md`).

Antigravity execution policy:
- `commandExecutionPolicy: auto` permits running commands autonomously only when authorized by repository policy;
- repository command policy strictly bounds execution: run nothing by default except an eligible cheap test created or modified by the current implementation;
- broad validation remains user/CI-owned and must not be run by the agent.

Default behavior:

- implement immediately from the approved packet;
- do not repeat/re-derive planning;
- read only affected code/authority needed by the packet;
- make the smallest complete change;
- use one agent;
- load detailed engineering guidance only for a concrete issue;
- do not perform broad repository/Git/agent/skill reconnaissance;
- do not run broad commands;
- remain quiet while working unless a blocker, contradiction, significant decision, or eligible changed-test failure must be reported.

For either Codex or Antigravity, an internal/generated plan may restate the packet for execution but cannot override or redesign the externally approved Hippocampus packet.

### 2.3 One executor per normal task

Do not have Codex and Antigravity independently implement the same normal tracker task. Use a second executor only for a concrete recovery, comparison, or isolated parallelizable reason explicitly approved by the human.

## 3. Implementation Command Rule

Default: **run nothing**.

The only default exception is the narrowest practical test/test method that the executor itself created or modified in the current implementation.

Even that test is user-run instead when it needs:

- Docker or Docker Compose;
- Testcontainers;
- PostgreSQL/database/container startup;
- external services/infrastructure;
- application/server startup;
- Playwright/browser/E2E startup;
- similarly expensive environment setup.

The executor must not run unchanged tests, full suites, build/package/verify, lint/typecheck, validation scripts, architecture suites, application startup, container validation, or Git hygiene commands merely because they are normally useful.

The human may explicitly authorize an exact additional command when desired.

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

The executor must not claim `VALIDATION PASS`, `Ready for Review`, or `Done` based only on its changed-test execution.

## 4. User / CI Validation

After implementation, the user runs the commands listed in `USER VALIDATION` or obtains equivalent authoritative GitHub Actions evidence.

Examples may include focused existing regression tests, broader Maven/npm validation, Docker/Testcontainers integration tests, application startup, E2E, lint/typecheck/build, or repository validation scripts when the tracker/task requires them.

Successful broad output does not need to be pasted into agent context. Prefer concise factual evidence such as:

```text
backend validation: PASS
integration test: PASS
frontend validation: PASS
GitHub Actions <job/run>: PASS
```

When a command fails, provide the first causal failure/relevant log section to the executor or reviewer unless more context is required.

## 5. Review — ChatGPT External Independent Reviewer

The reviewer must not simply trust the implementation report. Review the actual changed files/diff plus supplied evidence.

ChatGPT is the normal external reviewer. When Antigravity is explicitly selected as a fallback reviewer, use the `hippocampus-reviewer` custom agent profile (`.agents/agents/hippocampus-reviewer/agent.md`). The fallback profile is explicitly constrained to read-only inspection and review capabilities (`view_file`, `grep_search`, `list_dir`, `search_web`, `read_url_content`, MCP read tools, `ask_question`). Write and file modification tools (`write_to_file`, `replace_file_content`, `multi_replace_file_content`) are disabled, and terminal command execution is disabled (`commandExecutionPolicy: off`). Reviewers inspect code and evidence without write/edit or unrestricted command capabilities.

The reviewer inspects:

- implementation packet;
- actual diff/changed files;
- tracker/Source-of-Truth alignment;
- scope discipline and later-task leakage;
- architecture/module boundaries;
- correctness and failure behavior;
- created/modified tests;
- user/CI validation evidence;
- security-sensitive behavior;
- tracker/evidence accuracy.

The reviewer does not rerun broad validation by default. Missing evidence results in `VALIDATION INCOMPLETE` with the exact user/CI command required.

### Review pass 1 — general implementation review

Use `hippocampus-review-implementation`.

The preferred reviewer is a fresh independent reasoning context. Do not use the same implementation agent/session to approve its own work.

If a correction is narrow, send only a narrow correction packet back to the selected executor. Do not re-plan the whole task unless the finding invalidates the plan.

### Review pass 2 — independent security review

After general review is clean and required user/CI evidence is available, run `hippocampus-security-vulnerability-review` independently.

Use OWASP ASVS 5.0.0 as the verification baseline with OWASP Top 10:2025 and OWASP API Security Top 10 as threat lenses.

Critical/High findings block completion. Medium findings normally block unless a human explicitly accepts the risk. If a material control cannot be adequately verified, return `MANUAL SECURITY REVIEW REQUIRED`.

The security pass belongs to the Review role; it is not a fourth normal implementation agent.

## 6. Correction Loop

For a narrow defect:

```text
Reviewer finding
  -> narrow correction packet
  -> selected executor applies correction
  -> executor runs only newly created/modified eligible test
  -> user/CI runs listed affected validation commands
  -> reviewer checks updated diff/evidence
  -> security review reruns when security-relevant
```

Do not resend the full plan or rerun unrelated validation.

## 7. Publication and Completion

Implementation is not completion.

Publication/merge proceeds only after the appropriate review state allows it. The tracker remains the operational progress source.

`Done` requires factual evidence for:

- implementation exists;
- required validation passed;
- expected behavior is demonstrated;
- general review has no blocking finding;
- independent security gate passed or required manual review/risk acceptance is documented;
- PR/merge/CI facts required by the tracker are satisfied;
- Definition of Done is satisfied;
- evidence is recorded;
- no undocumented architectural deviation remains.

An executor must not mark its own task `Done`.

## 8. Communication Rule

Implementation agents are silent by default.

Do not narrate file reads, searches, routine reasoning, progress, skipped commands, or obvious edits. Speak only for a real blocker/contradiction/decision, a material eligible-test failure, or the final implementation report.

Reviewers should likewise report only material findings, missing evidence, verdict, and next action.

## 9. Context / Token Rule

Use model tokens for decisions and code changes, not for waiting on containers, reading successful infrastructure logs, or repeating deterministic validation that the user/CI can run.

Prefer:

**detailed planning once -> compact approved packet -> one executor -> quiet implementation -> changed-test-only agent execution -> user/CI validation -> evidence-based independent review -> independent security pass -> narrow fixes**

Expand context only for a concrete dependency, contradiction, failure, architecture question, or security requirement.

## 10. Runtime AI Is Separate From Developer Tooling

ChatGPT, Codex, and Antigravity are development workflow tools. They do not change Hippocampus runtime AI authority.

Runtime AI continues to follow Documents 10–15 and the owning tracker tasks:

- application/learning policy remains deterministic and application-owned;
- Gemini API and remote Ollama API are provider adapters behind the Provider Router;
- provider output is untrusted and validated;
- provider credentials remain server-side;
- RAG authorization/scope precedes ranking;
- no development-agent decision may silently change the runtime provider architecture.

Implement runtime Gemini/Ollama behavior only when the exact tracker task owns that behavior.
