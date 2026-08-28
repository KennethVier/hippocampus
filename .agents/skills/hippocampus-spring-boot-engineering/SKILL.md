---
name: hippocampus-spring-boot-engineering
description: Use for Spring Boot 4.1.x backend planning, implementation, refactoring, or review in Hippocampus. Defines framework-level gold standards for dependency injection, configuration, MVC/API boundaries, validation, transactions, Spring Data/JPA, security, HTTP clients, observability, testing, and operational behavior while preserving the approved modular monolith and avoiding unnecessary Spring features.
---

# Spring Boot Engineering Gold Standards for Hippocampus

## Purpose

This skill governs Spring Boot framework usage. Apply it together with `hippocampus-java-spring-engineering` and `hippocampus-architecture-patterns` for backend work.

Java 25 standards govern language design. This skill governs how Spring Boot is used to assemble, expose, persist, secure, observe, and test the application.

Do not use a Spring feature merely because it exists. Select only the framework capabilities that solve a current Hippocampus requirement and preserve the approved architecture.

## Framework Priorities

Prefer, in order:

1. explicit behavior and safe defaults;
2. correct module/layer ownership;
3. secure framework configuration;
4. simple constructor-based dependency graphs;
5. short, intentional transaction boundaries;
6. predictable persistence behavior;
7. observable failures and operations;
8. focused tests at the cheapest meaningful Spring layer;
9. framework convenience only when it does not hide important behavior.

## Approved Architectural Role of Spring

Spring Boot is the application/framework shell around the modular monolith. It must not become the domain model.

```text
HTTP / Spring Security / configuration
               ↓
              api
               ↓
          application
               ↓
        domain + ports
               ↑
 infrastructure / Spring adapters
```

Keep Spring MVC, Spring Security, Spring Data, HTTP clients, provider SDKs, configuration objects, and framework lifecycle concerns outside deterministic domain logic.

Do not add Spring Cloud, WebFlux/reactive architecture, messaging infrastructure, distributed caches, or other major Spring subsystems without approved architectural need.

## Dependency Injection and Bean Design

- Use constructor injection for required dependencies.
- Required collaborator fields should normally be `final`.
- Do not use field injection.
- Do not use static service locators or manually fetch application beans from `ApplicationContext` in business code.
- Avoid circular dependencies. A circular dependency is normally a responsibility/boundary design defect, not something to solve with lazy injection.
- Do not annotate every class with `@Component`. Pure domain objects, policies, value objects, and utilities that need no framework lifecycle should remain ordinary Java objects.
- Use `@Bean`/configuration methods where explicit construction is clearer or where integrating third-party types.
- A constructor with many unrelated dependencies is an SRP warning. Refactor responsibilities rather than hiding the dependency graph.
- Do not introduce an interface only to make a Spring bean injectable; interfaces represent real contracts/boundaries.

## Stereotype Discipline

Use stereotypes according to actual responsibility:

- `@RestController`: HTTP transport boundary.
- `@Service`: application/framework service only when the responsibility is genuinely service-like; prefer precise class names such as `AuthenticateUser` or `GenerateStudyMission` over generic `UserService`.
- `@Repository`: persistence adapter/repository responsibility and persistence exception translation where applicable.
- `@Configuration`: framework wiring/configuration, not business rules.
- `@Component`: use sparingly for framework-managed collaborators that do not fit a more meaningful stereotype.

Annotations do not define architecture by themselves. Package/module ownership and dependency direction remain authoritative.

## Configuration Gold Standard

- Prefer typed `@ConfigurationProperties` for coherent configuration groups over scattered `@Value` fields.
- Validate required configuration at startup when absence/invalid values would make operation unsafe or undefined.
- Keep environment-specific values outside source code.
- Never commit secrets, provider keys, passwords, tokens, or private credentials.
- Keep security-sensitive defaults explicit and reviewable.
- Do not make domain/application behavior depend directly on environment variable names or raw property access.
- Keep configuration classes focused by subsystem/responsibility.
- Avoid profile-driven business logic. Profiles configure infrastructure/environment differences; they should not become a hidden feature-rule system.
- Document non-obvious production-critical configuration and defaults.

## Controller and API Boundary

Controllers should be thin.

A controller may:

1. receive HTTP input;
2. bind/validate transport shape;
3. obtain authenticated principal/request context through approved security mechanisms;
4. map transport DTOs to application commands/queries;
5. invoke one clear application use case;
6. map the application result to an HTTP response.

A controller should not:

- contain domain/business rules;
- perform repository queries directly;
- build JPA queries;
- manage transactions manually;
- call Gemini/Ollama/provider SDKs directly;
- decide resource ownership solely in presentation code;
- expose persistence entities;
- catch broad exceptions to manufacture arbitrary responses.

Prefer stable API contracts and explicit status/error behavior.

## Validation Layers

Keep validation responsibilities distinct:

### Transport Validation

Use Jakarta Bean Validation or explicit request parsing for malformed/missing/shape constraints at the HTTP boundary.

### Application Validation

Enforce authorization, ownership, use-case preconditions, and operation-specific state rules where another transport cannot bypass them.

### Domain Validation

Enforce invariants owned by domain values/entities/policies.

### Database Integrity

Use appropriate constraints for persistent invariants such as uniqueness, foreign keys, nullability, checks, and concurrency-sensitive integrity.

Do not rely on controller validation alone for security or domain integrity.

## Transaction Gold Standard

Treat `@Transactional` as an architectural boundary, not decoration.

- Prefer transaction boundaries around cohesive application write use cases.
- Keep transactions short and deterministic.
- Do not hold database transactions open during Gemini/Ollama calls, remote HTTP calls, object-storage operations, or other potentially slow external I/O unless an approved design explicitly requires it.
- Understand proxy-based transaction semantics. Do not rely on self-invocation to trigger transactional behavior in the default proxy model.
- Avoid annotating every repository/service method transactionally without reasoning about the actual unit of work.
- Use `readOnly = true` only when it accurately communicates/read-optimizes a read transaction; do not treat it as an authorization or hard write-prevention control.
- Define isolation/propagation explicitly only when the use case requires behavior different from the project default.
- Understand rollback behavior for checked/unchecked exceptions before depending on it.
- Use optimistic locking/version checks when stale concurrent writes are a real risk.
- Make retryable operations idempotent where duplicate execution is possible.

Preferred external-call workflow where applicable:

```text
short read / capture required state
→ commit/close transaction
→ external provider call
→ validate provider result
→ short write transaction
→ verify version/state still valid
→ persist
```

## Spring Data / JPA Gold Standard

- PostgreSQL remains authoritative persistent storage.
- Flyway owns schema evolution.
- Production Hibernate schema handling validates; it must not silently create/update production schema.
- Do not expose JPA entities through API contracts.
- Do not let controllers depend directly on Spring Data repositories.
- Keep Spring Data repository interfaces and persistence entities in infrastructure where the approved module structure requires that separation.
- Prefer lazy association loading unless a deliberate query requires otherwise.
- Do not use `EAGER` to hide query-design problems.
- Avoid giant bidirectional object graphs.
- Use cascades only when aggregate ownership/lifecycle semantics justify them.
- Prevent N+1 deliberately with projections, fetch joins/entity graphs, or purpose-built queries where appropriate.
- Use pagination or bounded queries for collections that can grow materially.
- Prefer explicit readable queries when derived query method names become hard to understand.
- Avoid loading whole aggregates/entities when a projection is sufficient for read-only views.
- Keep DB constraints aligned with application/domain invariants where persistent integrity matters.
- Do not depend on Open Session in View to make lazy loading accidentally work across HTTP rendering. Fetch required data intentionally within the application/persistence boundary.
- Avoid arbitrary JSON columns/untyped maps when the domain structure is known and relational modeling is appropriate.

## Persistence Mapping

Do not force one object model to serve all layers.

Keep conceptual separation among:

- API DTOs;
- application commands/results;
- domain models/value objects;
- persistence entities/projections;
- external-provider DTOs.

Manual explicit mapping is preferred while mapping volume remains manageable. Introduce mapping frameworks only after repeated complexity justifies the dependency and generated behavior remains understandable.

## Spring Security Gold Standard

Spring Security implementation must follow the approved authentication/session architecture and the dedicated security skills.

- Deny by default unless a route/action is intentionally public.
- Authentication and authorization are separate concerns.
- Enforce resource ownership/authorization on the backend below the UI layer.
- Never trust frontend visibility/route guards as authorization.
- Preserve CSRF defenses for cookie/session-authenticated browser mutations unless a reviewed architecture explicitly replaces that threat model.
- Configure CORS narrowly from actual client requirements; never use permissive origins/credentials as a development shortcut in production configuration.
- Use secure password encoders and approved credential handling.
- Rotate/invalidate sessions appropriately for login/logout/security-sensitive identity changes according to project requirements.
- Configure cookies/session properties deliberately (`HttpOnly`, `Secure`, `SameSite`, scope/lifetime) according to environment and approved auth design.
- Do not leak authentication distinctions that enable unnecessary account enumeration.
- Security-critical uncertainty must fail closed.
- Sensitive endpoints/configuration/Actuator exposure must be explicitly authorized.

After implementation/tests/general review, `hippocampus-security-vulnerability-review` remains mandatory. This skill does not grant `SECURITY PASS`.

## Exception and ProblemDetail Handling

Use centralized exception-to-HTTP mapping.

Preferred flow:

```text
domain/application typed failure
→ API exception/error mapping
→ stable ProblemDetail/error contract
```

- Preserve stable machine-readable error codes where the API contract requires them.
- Do not expose stack traces, SQL/Hibernate details, raw provider errors, credentials, storage paths, internal hostnames, or sensitive configuration.
- Do not catch broad `Exception` just to return success/null/default state.
- Differentiate expected application/domain failures from unexpected infrastructure defects.
- Exceptional security conditions must fail closed.

## HTTP Clients and External Adapters

- Keep external-provider calls behind ports/adapters.
- Provider SDK/client DTOs must not leak into application/domain contracts.
- Configure connection/read/request timeouts deliberately.
- Do not allow unbounded waits.
- Retry only operations that are safe/idempotent or have explicit deduplication/idempotency semantics.
- Use bounded retries with backoff/jitter where appropriate; never retry indefinitely.
- Distinguish timeout, unavailable, rejected, invalid-response, and business-level provider failures when the application needs different handling.
- Validate untrusted provider responses before committing application state.
- Never log API keys, authorization headers, raw secrets, or sensitive full payloads.
- User-supplied URLs, if ever introduced, require explicit SSRF threat review before implementation.

## Async, Scheduling, and Concurrency

- Do not add `@Async`, schedulers, executors, or concurrency simply for perceived performance.
- Identify the workload, ownership, failure semantics, backpressure/bounds, and shutdown behavior first.
- Use bounded executors/queues where application-created concurrency is required.
- Do not rely on self-invocation to trigger proxy-based `@Async` behavior.
- Propagate only the security/request context that is explicitly safe and required.
- Background work must have observable failure handling and idempotency/retry semantics where applicable.
- Do not use in-memory scheduling as durable job state when the requirement demands persistence/recovery.

## Observability

Observability is part of production behavior, not debug decoration.

### Logging

- Log meaningful state transitions, failures, and operational context.
- Use appropriate log levels.
- Avoid repetitive enter/exit logs with no diagnostic value.
- Never log passwords, tokens, session identifiers, provider keys, private documents, sensitive medical-study content unnecessarily, or other secrets/PII.
- Prefer identifiers/correlation context that help trace a request without leaking protected data.

### Metrics / Tracing

Add metrics/observations only for meaningful operational questions: latency, failure rate, queue/job/provider behavior, important workflow outcomes, and resource pressure.

Avoid high-cardinality labels such as raw user IDs, document contents, arbitrary URLs, or exception messages.

### Actuator

Expose only required Actuator endpoints. Health/readiness information must not reveal secrets or internal topology unnecessarily.

## Dependency Management

- Prefer Spring Boot dependency management/BOM for managed ecosystem versions.
- Do not override managed Spring/testing/library versions merely to chase the newest release.
- Override only when the project needs a capability/security fix and compatibility is verified.
- Add dependencies only for real requirements after checking whether Java/Spring already provides the needed capability.
- Treat new dependencies as supply-chain/security decisions as well as implementation conveniences.

## Spring Testing Gold Standard

Apply `hippocampus-testing-security` and choose the smallest Spring context that proves the framework behavior.

- Pure domain rule: plain JUnit, no Spring context.
- Application orchestration: plain JUnit with fakes/mocks where possible.
- MVC/controller serialization/validation: focused MVC test slice when appropriate.
- Security filter/authorization/session behavior: Spring Security integration at the layer that exercises the real rule.
- Repository/JPA/SQL behavior: integration test with real PostgreSQL/Testcontainers when database semantics matter.
- Whole application wiring/critical integration: `@SpringBootTest` only when full context is actually part of what must be proven.

Do not use `@SpringBootTest` as the default test annotation.

Be aware that transactional test behavior can mask production transaction mistakes. Verify real commit/constraint/concurrency behavior where those semantics are the subject of the test.

## Spring-Specific Anti-Patterns

Flag/reject unless a concrete justified exception exists:

- field injection;
- circular dependencies;
- `ApplicationContext` service locator usage;
- God `@Service` / `@Configuration` classes;
- business logic in controllers;
- repository calls directly from controllers;
- JPA entities as API DTOs;
- `@Transactional` sprayed across methods without unit-of-work reasoning;
- long transactions around external I/O;
- self-invocation assumptions for proxy annotations;
- `EAGER` used as a default fix;
- accidental N+1 behavior;
- scattered `@Value` for structured subsystem configuration;
- permissive CORS/security settings copied from tutorials;
- broad exception swallowing;
- raw provider/framework exceptions exposed through APIs;
- `@Component` on classes that do not need framework management;
- unbounded `@Async`/executor work;
- business rules encoded in Spring profiles;
- hidden reliance on Open Session in View;
- unnecessary framework/dependency additions.

## Review Checklist

- Is Spring used only at the correct framework/application boundaries?
- Are dependencies constructor-injected and responsibilities cohesive?
- Is configuration typed, validated, secret-safe, and explicit?
- Are controllers thin and repositories/provider clients kept out of transport code?
- Are transport/application/domain/database validation responsibilities distinct?
- Are transaction boundaries short and correct under Spring proxy semantics?
- Are external calls outside database transactions where possible?
- Are JPA fetches, cascades, pagination, constraints, and concurrency intentional?
- Is security backend-enforced, deny-by-default, session/CSRF/CORS-aware, and fail-closed?
- Are external clients bounded by timeouts/retry/idempotency rules?
- Are errors safe and centrally mapped?
- Are logs/metrics/Actuator endpoints useful without leaking sensitive data?
- Does the test use the smallest context that proves the behavior?
- Did the change avoid unnecessary Spring features/dependencies?
- Is the implementation ready for the independent OWASP vulnerability review?
