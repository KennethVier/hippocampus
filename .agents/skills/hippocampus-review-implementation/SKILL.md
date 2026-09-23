---
name: hippocampus-review-implementation
description: Independent review role for a Hippocampus implementation or PR. Inspect the actual diff plus user/CI evidence for tracker/SOT alignment, scope, architecture, correctness, tests, and security readiness. Prefer a fresh reviewer context independent from the Antigravity/Jules implementation session; do not rerun broad commands by default.
---

# Review Hippocampus Implementation

## Purpose

Review the actual implementation, not the implementer's narration. Consume user/CI validation evidence instead of reproducing broad validation work.

The reviewer must be independent from the implementation execution context. A fresh Gemini/reviewer session is preferred. If Antigravity is used for review, use a separate review context/agent rather than allowing the implementation session to approve itself. Jules must not self-approve work it produced.

## Review Order

1. Identify the exact tracker task and controlling approved implementation packet.
2. Confirm the expected review head/diff when PR review is in scope.
3. Inspect the actual changed files/diff.
4. Check tracker acceptance criteria, dependencies, and later-task scope leakage.
5. Check Source-of-Truth/ADR alignment.
6. Check module/layer ownership and dependency direction.
7. Check correctness, invariants, failure behavior, and touched framework boundaries.
8. Check tests that were added/modified and the user/CI validation evidence supplied.
9. Check tracker/evidence accuracy.
10. If general review is clean, perform the independent `hippocampus-security-vulnerability-review` pass using the diff and available evidence.

Do not automatically load every engineering skill. Load detailed guidance only when the diff presents a concrete issue requiring it.

## Evidence Hierarchy

Prefer direct evidence in this order:

1. actual diff/current changed files;
2. controlling tracker task and approved packet;
3. applicable Source-of-Truth/accepted ADRs;
4. user/CI validation results;
5. implementation report as supporting context only.

Do not accept an implementation report as proof that a file, test, or behavior exists.

## Command Policy

Do not run builds, tests, Docker/Testcontainers, application startup, lint, typecheck, E2E, validation scripts, or other broad commands by default.

If required evidence is missing, request the exact command the user/CI should run. Review should not recreate expensive implementation/validation context merely for ceremony.

The reviewer may run a command only when the current user explicitly authorizes that exact command.

## Context Efficiency

- Start from the implementation packet, diff, and supplied evidence.
- Do not perform broad repository reconnaissance.
- Do not inspect unrelated history/branches/modules unless required to prove a finding.
- Do not spawn subagents by default.
- Do not re-plan unaffected work when a finding is narrow.
- Prefer a narrow correction packet for a narrow defect.
- Keep successful evidence concise; investigate detailed logs only when a failure needs diagnosis.

## General Review Verdict

Return exactly one general-review verdict:

### APPROVED
No material general-review changes required. Proceed to/complete the independent security pass. This is not `SECURITY PASS`, merge authorization, or task completion.

### APPROVED WITH REQUIRED CHANGES
Direction is correct, but specific fixes are required before completion/security handoff.

### REJECTED / REPLAN REQUIRED
Material scope, architecture, correctness, authority, dependency, or acceptance-criteria conflict requires re-planning.

### VALIDATION INCOMPLETE
Implementation may be acceptable, but required user/CI evidence is missing or inconclusive. State only the exact missing evidence/command.

## Findings

For each material finding state only:

- severity: BLOCKER / MAJOR / MINOR;
- area/source authority;
- evidence;
- required correction;
- user/CI validation required after correction.

Do not nitpick style without correctness, security, maintainability, readability, framework, or architectural impact.

## Correction Handoff

For narrow findings, produce a compact correction packet containing:

- exact defect;
- required behavior;
- affected scope;
- test to create/modify;
- exact user/CI validation commands;
- explicit no-scope-expansion rule.

Send the correction to the selected executor (normally Antigravity, or Jules if that task is explicitly cloud-executed). Do not resend the full original plan unless the finding invalidates it.

The corrected implementation must be reviewed again from the updated diff/head; never approve based on a superseded revision.

## Security Handoff

General `APPROVED` is not task completion.

After general review is clean and required validation evidence is available, run `hippocampus-security-vulnerability-review` as an independent adversarial pass.

Do not let the same implementation context manufacture its own security assurance.

## Runtime AI Review Boundary

When runtime AI/RAG/provider code is in scope, verify that:

- the Provider Router abstraction is preserved;
- provider-specific DTOs/SDK concerns remain inside provider adapters;
- AI/model output remains untrusted and validated;
- authorization occurs before retrieval/ranking;
- deterministic learning policy/state remains application-owned;
- provider fallback does not silently change grounding/safety/task contracts.

Development use of Gemini/Antigravity/Jules is not evidence that runtime Gemini behavior is authorized by the tracker task.

## Communication

Be concise. Do not narrate the review process. Report only material findings, missing evidence, the verdict, security handoff status, and next action.
