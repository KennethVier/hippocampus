---
name: hippocampus-review-implementation
description: Review a Hippocampus implementation or PR after coding. Inspect the actual diff and required evidence for tracker/SOT alignment, scope, architecture, correctness, tests, and security-gate readiness. This is separate from implementation and from the final adversarial security review.
---

# Review Hippocampus Implementation

## Purpose

Determine whether the implemented change is correct, source-grounded, scoped, maintainable, and ready for the independent security gate. Review the actual diff/PR, not only the implementation report.

## Review Order

1. Identify the tracker task and controlling plan/adjustments.
2. Inspect the changed files/diff.
3. Check task acceptance criteria and later-task scope leakage.
4. Check module/layer ownership and dependency direction.
5. Check correctness, invariants, error/failure behavior, and framework boundaries touched by the change.
6. Check focused tests, tracker-required validation, regression coverage, and relevant security-negative cases.
7. Check tracker/evidence accuracy.
8. Decide whether the implementation is ready for the independent `hippocampus-security-vulnerability-review`.

Load detailed Java, Spring Boot, React/TypeScript, architecture, or testing skills only when the changed code presents a concrete issue in that area. Do not automatically load every engineering skill for every review.

## Context Efficiency

- Start from the diff and task acceptance criteria.
- Do not perform broad repository reconnaissance.
- Do not inspect Git history, unrelated branches/PRs, or unrelated modules unless needed to prove a finding.
- Do not spawn subagents by default.
- Do not re-plan unaffected parts when a finding is narrow.
- Prefer a narrow correction packet for a narrow defect.

## Review Questions

- Does the change implement exactly the owning tracker task?
- Does it preserve approved module/dependency boundaries?
- Are responsibilities cohesive and abstractions justified by current requirements?
- Are framework/infrastructure concerns kept out of deterministic domain code?
- Are authorization/ownership and fail-closed behavior preserved where relevant?
- Are transaction/external-call/persistence boundaries safe where touched?
- Do tests prove externally meaningful behavior at the cheapest sufficient layer?
- Is required validation complete and factual?
- Is any significant architectural decision undocumented?

## Verdict

Return exactly one:

### APPROVED
No material general-review changes required. For an implementation, this means ready for the independent security vulnerability review; it does not mean `SECURITY PASS`.

### APPROVED WITH REQUIRED CHANGES
Direction is correct, but specific fixes are required before security handoff/completion.

### REJECTED / REPLAN REQUIRED
Material scope, architecture, correctness, framework, security, or acceptance-criteria conflict requires re-planning.

### VALIDATION INCOMPLETE
Implementation may be acceptable, but required evidence could not be produced.

## Findings

For each material finding state only:

- severity: BLOCKER / MAJOR / MINOR;
- area/source authority;
- evidence;
- required correction;
- required validation.

Do not nitpick style without maintainability, correctness, security, readability, framework behavior, or architectural impact.

## Correction Handoff

When changes are narrow, produce a compact correction packet containing the exact defect, required behavior, affected scope, tests/validation, and explicit instruction not to broaden scope. Do not resend the full original plan.
