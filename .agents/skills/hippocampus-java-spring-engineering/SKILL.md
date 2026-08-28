---
name: hippocampus-java-spring-engineering
description: Use for Java 25 and Spring Boot 4.1.x backend planning, implementation, refactoring, or review in Hippocampus. Applies SRP/SOLID, deliberate design-pattern selection, modern Java 25 idioms, modular-monolith boundaries, transaction discipline, persistence, API design, testability, and secure maintainable engineering without adding unapproved architecture.
---

# Java / Spring Engineering for Hippocampus

## First Rule

Read the tracker task and relevant Source-of-Truth documents before selecting an abstraction or pattern. Use the simplest design that preserves approved boundaries, correctness, security, and domain intent.

Do not optimize for the number of patterns, newest syntax, or fewest lines of code.

## Engineering Priority

Prefer, in order:

1. correctness and explicit invariants;
2. security and authorization boundaries;
3. clear domain/use-case intent;
4. SRP, cohesion, and dependency direction;
5. appropriate design patterns;
6. modern Java idioms;
7. testability and observability;
8. measured performance;
9. conciseness.

## Architectural Shape

Prefer package-by-feature/module ownership:

```text
com.<base>.hippocampus
├── identity
├── learning
├── progress
├── review
├── materials
├── rag
├── ai
├── shared
└── bootstrap
```

Within a module, create `api`, `application`, `domain`, `port`, and `infrastructure` only when real code requires them. Avoid speculative empty architecture.

## Dependency Direction

```text
api
 ↓
application
 ↓
domain + ports
 ↑
infrastructure adapters
```

Domain must not depend on Spring MVC, JPA repositories, provider SDKs, HTTP clients, controllers, or frontend contracts.

## SRP and SOLID

SRP is a default design constraint.

- A class/module/function should own one cohesive responsibility and one primary reason to change.
- Do not split cohesive behavior merely to satisfy line-count or class-size metrics.
- Separate framework translation, orchestration, domain policy, persistence, and external-provider integration when they represent different responsibilities.
- Prefer narrow interfaces around real capabilities; do not create interfaces for every class.
- Depend inward on stable abstractions at meaningful boundaries.
- Subtypes/implementations must preserve their abstraction contracts.
- Prefer composition and policy/strategy objects over large inheritance hierarchies.

Avoid generic responsibility buckets such as `UserService`, `CommonService`, `Manager`, `Processor`, `Util`, or `Helper` when a precise use-case/domain name exists.

Prefer names such as `AuthenticateUser`, `RegisterUser`, `GenerateStudyMission`, `EvaluateRecallAttempt`, `ReviewEligibilityPolicy`, or `RetrieveAuthorizedEvidence` when those are the real responsibilities.

## Pattern Selection

Apply `hippocampus-architecture-patterns` when a design choice is non-trivial.

Before introducing a pattern, identify:

1. the existing design pressure;
2. the responsibility being isolated;
3. the expected axis of change;
4. why straightforward composition is insufficient;
5. the cognitive/maintenance cost introduced;
6. why the pattern is justified by current requirements.

Do not create speculative abstractions for hypothetical future implementations.

## Use Cases

Application services/use cases represent explicit user/system intentions. They coordinate authorization, state loading, domain policy, ports, transactions, and typed results.

Prefer one meaningful operation over giant CRUD-style services.

## Domain Policies

Use domain services/policies for deterministic rules spanning domain state, such as Learning Engine, Review Policy, Evidence Projector, eligibility rules, or transition rules.

AI orchestration does not belong inside deterministic domain policy.

## Ports and Adapters

Create a port for a real replaceable/external boundary, such as AI task execution, retrieval, object storage, OCR, persistence capability, or injected clock.

Provider SDK types and Spring Data types stay in adapters/infrastructure.

Do not create an interface solely so a class can have an interface.

## Java 25 Language Standard

### Stable Features

Use permanent Java features when they improve clarity or correctness, including:

- records;
- sealed classes/interfaces;
- record patterns;
- pattern matching for `instanceof` and `switch`;
- switch expressions;
- text blocks;
- unnamed variables/patterns where they genuinely clarify ignored values;
- flexible constructor bodies or other Java 25 permanent features where directly useful.

Do not introduce a feature merely to demonstrate Java 25.

### Preview Features

Preview features are disabled by default. They require explicit project approval because they alter compiler/runtime flags and may change between releases.

### Records

Prefer records for transparent immutable data carriers such as:

- API request/response DTOs;
- application commands, queries, and results;
- immutable evidence/event payloads;
- composite IDs/value carriers where record semantics fit.

Do not mechanically convert JPA entities, mutable aggregates, framework proxies, or behavior-rich lifecycle objects to records.

Use compact constructors for invariant validation only when the record itself owns that invariant.

### Sealed Hierarchies

Use sealed types for closed sets of meaningful alternatives, such as task outcomes, review decisions, ingestion results, authorization decisions, or mission transitions.

Prefer exhaustive pattern `switch` where the compiler can prove coverage. Avoid unnecessary `default` branches that hide missing cases in a closed hierarchy.

### `var`

`var` performs compile-time local-variable type inference; it does not infer types at runtime and has no runtime inference cost.

Hippocampus policy: prefer explicit local variable types by default.

Use `var` only when all are true:

- the inferred type is immediately obvious from the initializer/context;
- spelling the type adds no useful domain or API information;
- readability is equal or better;
- it does not hide an important abstraction, numeric type, collection element type, framework type, or result contract.

Do not use `var` as a blanket style or to avoid understanding a type.

### Immutability and Collections

- Prefer immutable values and `final` state.
- Make defensive copies at trust/ownership boundaries when needed.
- Prefer `List.copyOf`, `Set.copyOf`, and immutable results where callers should not mutate returned collections.
- Expose the narrowest useful collection abstraction.
- Do not leak mutable internal collections.

### `Optional`

Use `Optional<T>` primarily for return values that explicitly model absence.

Avoid `Optional` fields, method parameters, entity attributes, and collection elements unless the domain semantics clearly justify them.

Never call `get()` without a proven presence condition.

### Null Handling

Prefer contracts that make nullability unnecessary. Validate external/framework inputs at boundaries and fail with typed errors.

Do not use null as an undocumented success/failure/result protocol.

### Streams

Use streams for concise stateless transformations and aggregations.

Prefer an ordinary loop when it is clearer, requires complex control flow, mutates significant state, or would otherwise become a long/debug-hostile pipeline.

Do not use parallel streams without a measured workload and explicit concurrency reasoning.

### Exceptions and Results

- Use exceptions for exceptional failure, not routine branching.
- Map domain/application failures to stable typed results/errors.
- Do not catch broad `Exception` merely to continue or return null.
- Fail closed for authorization/security-critical uncertainty.
- Never expose SQL details, stack traces, provider raw errors, secrets, tokens, or private storage paths.

### Methods and Classes

- Keep methods cohesive and name them by intent.
- Prefer guard clauses when they simplify control flow.
- Reduce boolean-flag APIs that create hidden modes; consider separate operations or typed alternatives.
- Avoid parameter lists that indicate a missing value object/command.
- Comments should explain non-obvious reasoning or constraints, not restate code.
- Avoid premature generic frameworks, reflection-heavy abstractions, and deep inheritance.

## Spring Practices

- Constructor injection only for required dependencies.
- Thin controllers: HTTP translation, validation, delegation, response mapping.
- Validate transport shape at the API boundary.
- Validate authorization, ownership, state, and business invariants in application/domain boundaries where they cannot be bypassed by another transport.
- Centralize exception → `ProblemDetail` mapping.
- Never return JPA entities directly.
- Prefer explicit configuration when behavior affects security or architecture.
- Keep framework annotations out of domain code where possible.

## Transactions

Transactions belong primarily at application-use-case boundaries and should be short.

Never hold a DB transaction open while waiting for Gemini/Ollama or another remote network dependency unless an approved requirement makes it unavoidable.

Preferred flow where applicable:

```text
read state
→ external call
→ validate result
→ short write transaction
→ optimistic concurrency check
```

## Persistence / JPA

- PostgreSQL is authoritative.
- Flyway owns schema evolution.
- Hibernate production schema mode validates rather than auto-updates.
- Prefer lazy associations unless a deliberate query requires otherwise.
- Avoid unnecessary bidirectional relationships.
- Prevent N+1 with deliberate fetch/projection/query design.
- Spring Data types remain infrastructure concerns.
- Separate persistence entities from domain objects when JPA would distort the domain.
- Enforce critical integrity in both application logic and database constraints where appropriate.
- Do not solve domain modeling with arbitrary JSON/untyped maps when relational/domain structure is known.

## DTO Boundaries

Keep API DTOs, application commands/results, domain models, JPA entities, and provider DTOs conceptually separate.

Prefer explicit/manual mapping until volume and repeated mapping complexity justify a mapper.

## Concurrency / Idempotency

Use optimistic locking when stale writes matter. Make retryable jobs/commands idempotent. Never overwrite immutable attempt/evidence history.

Do not add concurrency mechanisms without identifying the race being prevented.

## Testing

Apply `hippocampus-testing-security`.

Use the cheapest meaningful test:

1. pure unit/domain test;
2. application test with fake ports;
3. Spring integration when framework behavior matters;
4. Testcontainers when PostgreSQL behavior matters;
5. E2E for critical cross-layer journeys.

Do not start Spring for pure domain rules.

## Review Checklist

- Correct module ownership and dependency direction?
- One cohesive responsibility per class/use case?
- Framework translation separated from application/domain behavior?
- Pattern solves a real problem and is simpler than alternatives?
- Record/sealed hierarchy/value object appropriate?
- Explicit types reveal important contracts?
- Invalid states and failures modeled explicitly?
- Transaction too broad?
- JPA/provider details leaking inward?
- Authorization/ownership enforced below transport?
- Tests at the correct layer?
- Any unnecessary dependency or later tracker scope introduced?
