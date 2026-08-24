---
name: hippocampus-review-implementation
description: Use after a Hippocampus plan or code implementation is produced. Reviews alignment with the tracker, Source of Truth, phase scope, Java/Spring architecture, tests, security, and evidence, and returns an explicit approval verdict.
---

# Review Hippocampus Plan or Implementation

## Workflow

1. Identify the tracker task.
2. Read its authority docs.
3. Determine whether this is a PLAN or IMPLEMENTATION review.
4. Check scope first.
5. Check architecture.
6. Check required behavior.
7. Check tests/security/observability.
8. Check DoD and evidence.
9. Return a verdict.

## Verdict

Return exactly one:

### APPROVED
No material changes required.

### APPROVED WITH REQUIRED CHANGES
Direction is correct, but fixes are required before Done.

### REJECTED / REPLAN REQUIRED
Material architecture, scope, security, or acceptance criteria are wrong.

## Finding Format

- Severity: BLOCKER / MAJOR / MINOR
- Area
- Source-of-Truth authority
- Problem
- Required correction
- Why it matters

Do not nitpick style without maintainability/correctness impact.
