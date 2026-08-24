---
name: hippocampus-java-spring-engineering
description: Use for Java 25 and Spring Boot 4.1.x backend planning, implementation, refactoring, or review in Hippocampus. Applies modular-monolith, domain/application/port/infrastructure boundaries, transaction discipline, persistence, API, and maintainable Java engineering practices without adding unapproved architecture.
---

# Java / Spring Engineering for Hippocampus

## First Rule

Read the tracker task and relevant Source-of-Truth documents before selecting a pattern. A pattern is useful only when it clarifies an approved boundary.

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

## Use Cases

Use application services for explicit user/system intentions. They coordinate authorization, state loading, domain policy, ports, transactions, and results. Avoid generic all-purpose `FooService` classes.

## Domain Policies

Use domain services/policies for deterministic rules spanning domain state, such as Learning Engine, Review Policy, and Evidence Projector. AI orchestration does not belong inside domain policy.

## Ports and Adapters

Create a port only for a real replaceable/external boundary, such as AI task execution, retrieval, object storage, OCR, or injected clock. Do not create an interface for every class.

## Java 25 Practices

Prefer:
- records for immutable DTOs/commands/value carriers;
- enums or sealed hierarchies for finite task/action/result types when useful;
- final fields and constructor injection;
- small cohesive methods;
- `Optional` mainly as a return type;
- `java.time` and injected `Clock` for deterministic time rules;
- explicit validation;
- modern language features only where they improve clarity.

Avoid:
- field injection;
- static service locators;
- large inheritance trees;
- reflection-heavy abstraction without a need;
- `Util`/`Helper` dumping grounds;
- untyped maps for core contracts.

## Spring Practices

- Thin controllers.
- Validate transport input at API boundary.
- Validate ownership/state in application/domain.
- Centralize exception → ProblemDetail mapping.
- Never return JPA entities directly.
- Prefer explicit configuration when behavior affects security/architecture.

## Transactions

Transactions belong primarily at application-use-case boundaries and should be short.

Never hold a DB transaction open while waiting for Gemini/Ollama or another remote network dependency unless unavoidable.

Preferred flow:

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
- Prefer lazy associations unless a specific query requires otherwise.
- Avoid unnecessary bidirectional relationships.
- Prevent N+1 with deliberate fetch/projection/query design.
- Spring Data types remain infrastructure concerns.
- Separate persistence entities from domain objects when JPA would distort the domain.
- Enforce critical integrity in both application and DB constraints.

## DTO Boundaries

Keep separate concepts for API DTOs, application commands/results, domain models, JPA entities, and provider DTOs.

Prefer manual explicit mapping until volume justifies a mapper.

## Errors

Use typed errors/stable codes. Never expose SQL details, stack traces, provider raw errors, secrets, or storage paths.

## Concurrency / Idempotency

Use optimistic locking when stale writes matter. Make retryable jobs/commands idempotent. Never overwrite immutable attempt history.

## Testing

Use the cheapest meaningful test:
1. pure unit/domain test;
2. application test with fake ports;
3. Spring integration when framework behavior matters;
4. Testcontainers when PostgreSQL behavior matters;
5. E2E for critical cross-layer journeys.

Do not start Spring for pure domain rules.

## Review Checklist

- Correct module ownership?
- Correct dependency direction?
- Controller business logic?
- Domain leaking infrastructure/provider concerns?
- Transaction too broad?
- Interface/pattern actually needed?
- DB/application integrity both protected?
- Typed safe errors?
- Tests at correct layer?
- Any later tracker task implemented early?
