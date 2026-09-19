---
name: hippocampus-review-implementation
description: Independent review role for a Hippocampus implementation or PR. Inspect the actual diff plus user/CI evidence for tracker/SOT alignment, scope, architecture, correctness, tests, and security readiness. Do not rerun broad commands by default.
---

# Review Hippocampus Implementation

## Purpose

Review the actual implementation, not the implementer's narration. Consume user/CI validation evidence instead of reproducing broad validation work.

## Review Order

1. Identify the tracker task and controlling implementation packet.
2. Inspect the actual changed files/diff.
3. Check tracker acceptance criteria and later-task scope leakage.
4. Check module/layer ownership and dependency direction.
5. Check correctness, invariants, failure behavior, and touched framework boundaries.
6. Check tests that were added/modified and the user/CI validation evidence supplied.
7. Check tracker/evidence accuracy.
8. If general review is clean, perform the independent `hippocampus-security-vulnerability-review` pass using the diff and available evidence.

Do not automatically load every engineering skill. Load detailed guidance only when the diff presents a concrete issue requiring it.

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

## Verdict

Return exactly one general-review verdict:

### APPROVED
No material general-review changes required. Proceed to/complete the independent security pass. This is not `SECURITY PASS` or task completion.

### APPROVED WITH REQUIRED CHANGES
Direction is correct, but specific fixes are required before completion/security handoff.

### REJECTED / REPLAN REQUIRED
Material scope, architecture, correctness, authority, or acceptance-criteria conflict requires re-planning.

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
- exact user validation commands;
- explicit no-scope-expansion rule.

Do not resend the full original plan unless the finding invalidates it.

## Communication

Be concise. Do not narrate the review process. Report only material findings, missing evidence, the verdict, and the next action.
