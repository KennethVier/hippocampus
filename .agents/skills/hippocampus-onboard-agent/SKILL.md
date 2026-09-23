---
name: hippocampus-onboard-agent
description: One-time orientation for a fresh Hippocampus coding/review agent session. Load durable product, authority, architecture, AI/RAG, workflow, command, security, and completion boundaries without scanning the whole repository. Use before task work only when repository context is not already established.
---

# Onboard a Hippocampus Agent

## Purpose

Establish the minimum durable repository context needed to work safely in Hippocampus.

This skill is orientation only. It must not implement a tracker task, modify files, run commands, change tracker state, commit, push, create a PR, or perform review approval.

Do not use this skill repeatedly in the same session once the repository context is established.

## Read

Read only:

1. root `AGENTS.md`;
2. `docs/agent-context-and-bootstrap.md`;
3. `docs/agent-orchestration-workflow.md` only if the workflow detail is not already clear from the first two files.

Do **not** read all numbered Source-of-Truth documents during onboarding.
Do **not** scan the implementation tracker for a current task unless the user has already named a task or explicitly asks for current progress.
Do **not** inspect Git history, branches, PRs, CI, or broad repository structure merely for orientation.

## Establish

Confirm understanding of these durable facts:

### Product

- primary user is a medical student;
- Study Missions are the core experience;
- Hippocampus is not primarily upload/chat, summarization, quiz, or flashcard generation;
- Learning Engine owns pedagogical sequencing/state;
- Material != Topic;
- learning evidence maps to real student activity.

### Authority

- follow root `AGENTS.md` authority order;
- code never silently overrides higher authority;
- tracker is the operational task/status/evidence source;
- current task/phase must be resolved live, not from remembered conversation state;
- significant unresolved decisions use Document 27/ADR governance.

### Architecture

- Spring Boot modular monolith;
- approved modules and `api -> application -> domain/ports <- infrastructure` direction;
- Java 25 / Spring Boot 4.1.x / Maven baseline;
- PostgreSQL authoritative persistence, pgvector retrieval foundation;
- uploaded binaries in private object storage;
- no unapproved architecture/infrastructure additions.

### Runtime AI / RAG

- Gemini API + remote Ollama API remain runtime provider adapters behind the Provider Router;
- development use of Gemini/Antigravity/Jules does not change runtime AI authority;
- provider/model output is untrusted and validated;
- provider-specific SDK/DTO concerns stay in adapters;
- authorization/scope occurs before retrieval/ranking;
- cross-user leakage tolerance is zero;
- application owns learning policy/state/evidence truth.

### Agent workflow

- Plan: Human + Gemini;
- Implement: Antigravity local by default OR Jules cloud when explicitly selected;
- Validate: user/GitHub Actions for broad evidence;
- Review: independent actual-diff review;
- Security: independent `hippocampus-security-vulnerability-review`;
- completion requires tracker Definition of Done/evidence, not merely code/tests.

### Implementation behavior

- approved implementation packet is controlling;
- do not re-plan without a concrete contradiction/blocker;
- smallest complete/reviewable change;
- do not pull later tracker work forward;
- single-agent execution by default;
- do not load every skill automatically;
- command default is run nothing except an eligible cheap changed test;
- broad validation is user/CI-run unless explicitly authorized;
- implementation agent does not self-approve or mark Done.

## Tool-Specific Orientation

### Antigravity

Antigravity is the default local executor and discovers `.agents/skills/` automatically. Use skill names directly when the task requires them; do not duplicate their full content into every prompt.

### Jules

Jules is an optional cloud executor. It reads root `AGENTS.md`, but a task prompt should explicitly name `.agents/skills/hippocampus-implement-task/SKILL.md` and any additional materially relevant skill. A Jules-generated plan is an execution restatement, not authority to redesign the approved packet.

## Output

Return only:

```text
ONBOARDING READY
- authority understood
- product boundaries understood
- architecture understood
- runtime AI/RAG boundaries understood
- tracker-first workflow understood
- command/validation policy understood
- review/security/completion gates understood
- waiting for exact tracker task or approved implementation packet
```

If any of those cannot be established from the allowed onboarding files, return:

```text
ONBOARDING BLOCKED
Issue:
<exact missing/contradictory repository guidance>

Need:
<exact file/decision required>
```

Do not broaden context automatically to resolve a contradiction; report it first.
