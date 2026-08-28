---
name: hippocampus-review-implementation
description: Use after a Hippocampus plan or code implementation is produced. Reviews tracker/SOT alignment, phase scope, SRP/SOLID, architecture, pattern choices, Java 25, Spring Boot, React/TypeScript idioms, tests, observability, and evidence, then returns an explicit approval verdict. Full adversarial security verification is performed separately by hippocampus-security-vulnerability-review after tests and this review.
---

# Review Hippocampus Plan or Implementation

## Purpose

Review whether the work is correct, maintainable, source-grounded, architecture-safe, framework-safe, and ready for the independent security gate. Do not approve code merely because it compiles or tests pass.

## Workflow

1. Identify the tracker task.
2. Read its authority docs.
3. Determine whether this is a PLAN or IMPLEMENTATION review.
4. Check scope and acceptance criteria first.
5. Check module ownership and dependency direction.
6. Check SRP, cohesion, naming, and class/function responsibility.
7. Check design-pattern choices and unnecessary abstraction.
8. Check Java/Spring or React/TypeScript idioms as applicable.
9. Check behavior, contracts, error handling, concurrency, and performance assumptions.
10. Check tests, observability, and security-relevant negative cases.
11. Check DoD and evidence.
12. Return a verdict and identify security-gate readiness.

## Architecture and Design Review

Ask:

- Does the code belong in this module/layer?
- Does each class/function have one cohesive responsibility?
- Are framework concerns leaking into domain/application code?
- Are ports narrow and tied to real boundaries?
- Does each introduced pattern solve an identifiable current problem?
- Would simpler composition be clearer?
- Is inheritance being used where composition/strategy would be safer?
- Are generic `Service`/`Manager`/`Processor`/`Util` buckets hiding responsibilities?
- Can invalid state be represented unnecessarily?
- Are domain rules duplicated across controller/application/persistence/frontend?

## Java Review

Apply `hippocampus-java-spring-engineering` and check:

- records/sealed types/value objects used where semantics fit, not mechanically;
- explicit local types by default; `var` only when the inferred type is obvious and readability improves;
- null/Optional usage communicates absence clearly;
- exceptions/results are typed and fail safely;
- collections/immutability do not leak mutable state;
- stream/concurrency constructs are clearer than simpler alternatives;
- preview language features were not introduced without approval.

## Spring Boot Review

Apply `hippocampus-spring-boot-engineering` and check:

- constructor injection and bean ownership are explicit; no field injection/circular dependency/service locator;
- controllers are thin and do not call repositories/provider SDKs directly;
- transport/application/domain/database validation responsibilities are separated;
- configuration is typed/validated/secret-safe where appropriate rather than scattered through `@Value`;
- transaction boundaries are short, intentional, and correct under Spring proxy semantics;
- external network/provider calls are not held inside DB transactions without approved justification;
- JPA entities stay out of API contracts and fetch/cascade/pagination/N+1 behavior is deliberate;
- backend authorization/ownership, session, CSRF, and CORS assumptions are explicit and fail closed;
- exception-to-`ProblemDetail` handling does not expose infrastructure/secrets;
- HTTP clients have intentional timeout/retry/idempotency behavior;
- logging/metrics/Actuator exposure is useful and sensitive-data safe;
- dependency versions normally follow the Spring Boot BOM;
- the smallest appropriate Spring test context is used;
- no Spring subsystem/feature/dependency was introduced merely because it exists.

## React / TypeScript Review

Apply `hippocampus-react-typescript-engineering` and check:

- feature/component responsibility is clear;
- TypeScript remains strict and avoids `any`/unsafe assertions;
- `unknown` external data is validated/narrowed;
- derived state is not duplicated;
- effects are used for external synchronization rather than ordinary derivation/event handling;
- server state is not duplicated into unrelated global client state;
- accessibility and error/loading/empty states are handled where required;
- web/native concerns are not prematurely forced into one presentation abstraction.

## Testing Review

Apply `hippocampus-testing-security`.

Look for behavior-oriented tests at the cheapest meaningful layer, negative authorization cases, deterministic time, real PostgreSQL verification where DB behavior matters, focused Spring test slices where framework behavior matters, and frontend tests focused on user-visible behavior.

Do not reward excessive mocking, blanket `@SpringBootTest`, or tests coupled to private implementation details.

## Security Handoff

This review must identify obvious security defects, but it does not replace the independent adversarial security gate.

An implementation is ready for `hippocampus-security-vulnerability-review` only when required tests pass and no known general-review blocker remains.

## Verdict

Return exactly one:

### APPROVED
No material changes required. For an implementation, this means ready for the independent security vulnerability review; it does not itself mean `SECURITY PASS`.

### APPROVED WITH REQUIRED CHANGES
Direction is correct, but fixes are required before completion/security handoff.

### REJECTED / REPLAN REQUIRED
Material architecture, scope, correctness, framework usage, security, or acceptance criteria are wrong.

## Finding Format

- Severity: BLOCKER / MAJOR / MINOR
- Area
- Source-of-Truth/engineering authority
- Problem
- Evidence
- Required correction
- Why it matters

Do not nitpick style without maintainability, correctness, security, readability, framework behavior, or architectural impact.
