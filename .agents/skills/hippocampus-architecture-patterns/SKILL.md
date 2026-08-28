---
name: hippocampus-architecture-patterns
description: Use when selecting or reviewing responsibilities, SOLID principles, design patterns, domain/application boundaries, or abstractions in Hippocampus. Makes SRP foundational and requires every pattern to solve a current identifiable design pressure without speculative over-engineering.
---

# Architecture, SRP, and Design Patterns for Hippocampus

## Core Principle

Design patterns are important because they give recurring design problems recognizable, testable structures. They are not a scorecard.

Apply a pattern whenever it materially improves responsibility, dependency direction, change isolation, correctness, or readability. Do not apply patterns merely because they are considered "gold standard" or currently fashionable.

## SRP First

Before naming a pattern, identify responsibilities.

A class/module/function should have one cohesive responsibility and one primary reason to change.

Separate when concerns change for different reasons, especially:

- transport/framework translation;
- application/use-case orchestration;
- deterministic domain policy;
- authorization/ownership policy;
- persistence;
- external provider/network integration;
- presentation/state management.

Do not fragment cohesive code merely to create small files/classes. SRP is about reasons to change, not line count.

## SOLID as Design Guidance

### Single Responsibility

Default constraint. Make responsibilities explicit and name them by intent.

### Open/Closed

Use extensible composition when there is a real axis of variation (for example provider or policy strategies). Do not pre-build extension points for imagined variants.

### Liskov Substitution

Every implementation behind an abstraction must honor the abstraction's behavioral contract, failure semantics, and invariants.

### Interface Segregation

Prefer narrow capability-oriented ports/interfaces. Avoid giant service interfaces that force unrelated responsibilities together.

### Dependency Inversion

Application/domain code depends on abstractions at real external/replaceable boundaries; infrastructure implements those abstractions. Do not create an interface for every concrete class.

## Mandatory Pattern Selection Test

Before introducing a significant pattern, answer:

1. What concrete problem/design pressure exists now?
2. Which responsibility or dependency needs isolation?
3. What is expected to vary/change?
4. Why is straightforward code/composition insufficient?
5. What complexity/indirection does the pattern add?
6. Is that cost justified by current requirements?
7. How will the design be tested?

If these answers are weak, prefer simpler code.

During planning/review, briefly record important pattern choices and important patterns deliberately rejected. Production code should not contain tutorial commentary merely to explain the pattern.

## Preferred Patterns When They Fit

### Application Use Case / Application Service

Use for an explicit user/system intention that coordinates authorization, state, domain policy, ports, transactions, and a result.

Prefer `AuthenticateUser`, `GenerateStudyMission`, or `EvaluateRecallAttempt` over a giant generic `UserService`/`LearningService`.

### Policy / Strategy

Use for deterministic behavior with a real interchangeable axis, such as review scheduling, retrieval ranking, AI-provider selection policy, difficulty adjustment, or eligibility rules.

Prefer composition over conditional chains when variants are genuine and independently testable.

### Port + Adapter

Use at real external/replaceable boundaries: Gemini/Ollama, object storage, OCR, HTTP integrations, persistence capabilities, clocks, notification providers.

Keep provider/framework DTOs in the adapter.

### Repository

Use as a persistence capability boundary when domain/application code needs persistence behavior.

Prefer domain/use-case meaningful operations over speculative generic `BaseRepository<T, ID>` hierarchies.

### Explicit State Machine / Transition Policy

Use when an entity/workflow has meaningful finite states and illegal transitions must be prevented, such as Study Mission, ingestion, review, attempt, or asynchronous job lifecycle.

Centralize transition legality rather than scattering `if (status == ...)` checks across layers.

### Typed Result / Sealed Hierarchy

Use when an operation has a finite set of outcomes that callers must handle exhaustively. In Java, sealed types + pattern switch may express this well; in TypeScript, discriminated unions often fit.

### Projector

Use when authoritative events/evidence are transformed deterministically into derived learning/progress state while preserving traceability.

### Factory / Named Factory

Use when valid construction requires non-trivial invariants, policy, or coordinated creation. Do not add factories that merely wrap a single obvious constructor.

### Builder

Use selectively when constructing complex test data or objects with many valid combinations. Do not use a builder for simple records/DTOs whose constructors are already clear.

### Decorator / Interceptor / Filter

Use for genuine cross-cutting behavior at the correct layer (for example HTTP/session concerns, metrics, retries around a port) when composition is clearer than scattering duplicate logic.

Do not hide business policy in infrastructure middleware.

### Facade

Use when one stable entry point legitimately coordinates a cohesive subsystem. Do not turn a facade into a new god service.

## React / TypeScript Pattern Translation

Do not force object-oriented GoF class structures into React.

Equivalent design pressure may be expressed with:

- functions as strategies;
- adapter functions;
- custom hooks;
- reducers/state machines;
- providers/context;
- controlled components;
- compound components;
- feature modules;
- discriminated unions.

Prefer functional composition when it is simpler and more idiomatic.

## Patterns/Structures Requiring Extra Scrutiny

Treat these as warning signs, not absolute bans:

- generic base controllers/services/repositories;
- giant `Service`, `Manager`, or `Processor` classes;
- `Common`, `Util`, or `Helper` dumping grounds;
- deep inheritance/template-method hierarchies;
- manual Singleton implementations inside Spring;
- reflection-heavy generic frameworks;
- factories/builders that only wrap obvious constructors;
- provider-specific types leaking into application/domain code;
- giant bidirectional JPA graphs;
- untyped JSON/maps replacing known domain/relational models;
- premature domain events/distributed messaging for simple in-process sequencing;
- Redux/Zustand/global context for state that is local or server-owned;
- wrapper hooks/components that only rename an existing API.

## Architecture Boundaries

Patterns must preserve the approved modular-monolith architecture:

```text
api -> application -> domain/ports <- infrastructure
```

Patterns do not authorize microservices, CQRS, event sourcing, Kafka, Redis, WebFlux, GraphQL, or any other architecture listed in `AGENTS.md` as decision-gated.

## Review Questions

- What problem does this pattern solve that simpler code would not?
- Does it improve SRP or merely increase file count?
- Is the expected axis of change real today?
- Is the abstraction named in domain/use-case language?
- Can each policy/strategy/adapter be tested independently?
- Does the pattern preserve dependency direction?
- Is the implementation more readable to a future engineer than the straightforward alternative?
- Are we building for current requirements rather than hypothetical scale?
