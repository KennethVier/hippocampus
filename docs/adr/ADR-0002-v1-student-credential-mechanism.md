---
ADR: ADR-0002
Title: V1 Student Credential Mechanism
Status: PROPOSED
Date: 2026-08-28
Decision Owners: Project Hippocampus Team
Categories:
  - SECURITY
  - BACKEND
  - DATA
  - ARCHITECTURE
Affected Documents After Acceptance:
  - "18"
  - "22"
  - "26"
  - "27"
  - docs/adr/README.md
Supersedes: None
Superseded By: None
---

# Context

Hippocampus already requires Spring Security, an authenticated
server-side session, eventual Spring Session JDBC persistence, a secure
browser session cookie, and internal `users.id` as the sole ownership
root. ADR-0001 preserves those decisions, the web-first v1 boundary, and
the requirement that a future native transport receive separate review.

The approved architecture does not select the concrete credential used
for initial login. P1-02 requires the "chosen v1 login credential flow,"
so implementing it without a governed choice would create an
architectural and security decision in code. P1-02 therefore remains Not
Started until this ADR is accepted and the affected Source-of-Truth
documents are aligned.

This decision is deliberately limited to the initial small, controlled
pilot. It does not reconsider Spring Security, server-side sessions,
Spring Session JDBC, secure-cookie browser authentication, `users.id`
ownership, ADR-0001, or web-first v1.

# Proposed Decision

Hippocampus v1 will directly own student email/password credentials for
the initial pilot. Spring Security will verify a submitted credential and,
on success, establish the already-approved authenticated server-side
session. The authenticated principal will resolve to the persisted UUID
`users.id`, which remains the sole ownership root.

The flow remains:

```text
credential verification
-> Spring Security authentication
-> users.id-rooted principal
-> ordinary authenticated server-side session
```

Login returns no session token. This ADR introduces no JWT, bearer token,
refresh token, local-storage credential, or native authentication work.

Direct password authentication is proposed as the smallest current v1
scope and operational choice, not as a universally superior
authentication mechanism. It adds neither an external identity provider
and OAuth/OIDC dependency nor an email-delivery dependency; it is
deterministically testable with Spring Security and PostgreSQL; and it is
appropriate to the controlled pilot while preserving later migration to
an external provider without changing backend, domain, ownership, or
session authority.

## Credential Persistence Boundary

Password credentials will remain separate from the provider-neutral
`users` identity/profile table. P1-02 may introduce a one-to-one
credential record conceptually shaped as:

```text
user_password_credentials
  user_id       -> users.id
  password_hash
  created_at
  updated_at
```

The exact migration and physical names remain P1-02 implementation work.
P1-02 must not add `password`, `password_hash`, or `provider_subject` to
the existing V2 `users` table. Physical deletion of a user may cascade
credential cleanup.

Only adaptive encoded password hashes may be persisted. Plaintext
passwords are never persisted. Password hashes must not appear in API
DTOs or the authenticated principal. Raw passwords and hashes must not be
logged.

## Password Encoding

P1-02 will use Spring Security's password-encoding abstraction. The
preferred direction is `DelegatingPasswordEncoder`, with adaptive bcrypt
for newly created v1 hashes, so stored credentials retain algorithm
agility rather than coupling the domain model permanently to bcrypt.

This ADR does not freeze a bcrypt work factor. The exact cost is an
implementation/configuration choice supported by runtime and CI
measurements. Plaintext storage, `NoOpPasswordEncoder`, fast
general-purpose hashes, and home-grown password hashing are not
authorized.

## Email Identity Semantics

V1 login lookup will use the persisted email identifier consistently and
preserve the existing authoritative `email VARCHAR UNIQUE NOT NULL`
database semantics. P1-02 will not silently change V2, canonicalize email,
or introduce `lower(email)`, `citext`, or a case-insensitive unique index.
A future case-insensitive or canonical-email policy requires an explicit
data-model decision and migration.

## Pilot Account Provisioning

P1-02 will provide no public self-registration, public password-reset
endpoint, or admin account-management API. Initial pilot users will be
provisioned through a controlled operator-only, out-of-band process. The
exact tool, script, or procedure is an implementation detail, but it must
generate hashes through the application-approved `PasswordEncoder`.

Plaintext or reusable pilot credentials must not be committed to Git. Raw
passwords must not appear in migrations, and production password hashes
must not be committed as seed data.

## Authentication Eligibility and Failure Privacy

- `ACTIVE` users are eligible to authenticate when credentials are valid.
- `DISABLED` users must never establish authenticated state.
- `DELETED` users must never establish authenticated state.

External responses must not reveal whether authentication failed because
of an unknown email, wrong password, absent password credential,
`DISABLED` state, or `DELETED` state.

P1-02 must preserve account-enumeration resistance and avoid obvious
timing shortcuts. Unknown, credential-absent, disabled, or deleted users
must not return before equivalent adaptive password-verification work
protecting the normal invalid-credential path. The implementation may use
a real stored hash or safe dummy encoded hash as appropriate; exact code
is outside this ADR.

## Authentication Abuse Control

P1-02 owns the minimum v1 abuse-control behavior introduced with the
login endpoint, including configurable, bounded behavior appropriate to
the pilot for throttling, generic failure responses, enumeration
resistance, and lockout or backoff where appropriate. Exact thresholds
and algorithms are not frozen here. This ADR authorizes no Redis or other
new distributed-infrastructure dependency.

## Principal and Authorization Boundary

The future Spring Security principal must contain persisted UUID
`users.id`. It may include privacy-minimal identity metadata, such as
email, when useful for Spring Security or audit context. It must not
contain a raw password, password hash, credential entity, session
identifier, or provider secret.

This ADR does not authorize `ROLE_STUDENT` or another role/domain
authorization model. Authorization remains ownership-centered. If Spring
Security mechanically requires authorities, P1-02 must use the smallest
non-domain-role representation justified by the framework. A role or
domain authorization model requires separate authority.

## Session and Phase 1 Boundaries

Credential selection does not change the accepted session architecture.
P1-03 separately owns the Spring Session JDBC dependency and schema,
restart persistence, expiry and idle timeout, and persistence tests.

The remaining Phase 1 ownership also stays unchanged:

- P1-04 owns full browser CSRF acquisition and submission behavior;
  global CSRF disablement is not authorized.
- P1-05 owns credentialed browser CORS.
- P1-06 owns the reusable current-user abstraction.
- P1-07 owns `/me`.
- P1-08 owns production login and session-recovery frontend UX.
- P1-09 owns clearing private frontend state on logout.

This ADR does not select exact Java classes, filters, packages, HTTP
success status, test helpers, or test class names.

# Alternatives Considered

## A. Direct Hippocampus Email/Password — Proposed

This is the smallest current pilot choice: it avoids new identity and
email infrastructure, supports local integration testing, fits Spring
Security and PostgreSQL, and leaves internal identity and sessions
unchanged. Its password-security and abuse-control costs are explicitly
accepted below.

## B. External OAuth/OIDC Identity Provider

An external provider could reduce direct password custody and may become
appropriate later. For the initial pilot it adds an external operational
dependency, provider configuration and secrets, redirect handling,
provider-subject mapping, account linking/provisioning policy, integration
test complexity, and provider availability concerns. It is not selected
for current v1 scope, but the separate credential table keeps migration
possible.

## C. Email Magic Link

Magic links avoid a remembered password but require a dependable email
provider and its operational configuration, secure token generation and
persistence, expiration and single-use enforcement, delivery/retry
handling, and careful enumeration-resistant responses. That new
dependency and lifecycle are not the smallest controlled-pilot choice.

## D. JWT/Bearer Replacement

Rejected. Replacing server-side sessions with JWT or bearer access and
refresh tokens is outside the approved web/session architecture and
ADR-0001. Credential selection does not justify replacing session
authority or adding browser token storage.

## E. Store `password_hash` Directly on `users`

Rejected for v1. `users.id` and profile identity should remain
provider-neutral, while credential-mechanism data remains isolated and
replaceable. A separate one-to-one record avoids coupling the ownership
root to a particular login provider.

# Consequences

## Positive

- No new external IdP, OAuth/OIDC, or email-delivery infrastructure is
  required for the pilot.
- Spring Security and PostgreSQL integration tests can be deterministic
  and local.
- Spring Security integration is straightforward without changing the
  backend or domain authority.
- `users.id`, ownership-centered authorization, and server-side sessions
  remain intact.
- Separating credentials preserves algorithm agility and a future IdP
  migration path.

## Negative / Tradeoffs

- Hippocampus owns secure password hashing, handling, and verification.
- A secure pilot provisioning procedure is required.
- Abuse controls become application responsibilities.
- Password change, reset, and recovery flows require future design if
  product scope needs them.
- A future IdP migration requires credential retirement and account
  linking/provisioning planning.
- The application must continuously protect credential and
  account-enumeration boundaries in code, tests, logs, and operations.

# Security and Privacy Impact

The proposal adds custody of password hashes but no new ownership or
session authority. Credential access must be narrowly contained;
authentication fails closed; disabled and deleted accounts cannot create
authenticated state; generic responses and equivalent adaptive
verification work protect account existence; and neither passwords nor
hashes cross API, principal, logging, migration-seed, or source-control
boundaries.

Server-side expiry and revocation, secure browser cookies, CSRF, CORS,
logout, and private-state clearing remain governed by their existing
authorities and Phase 1 tasks.

# Documentation Changes After Acceptance

Because this ADR is **PROPOSED**, it does not patch the frozen Source of
Truth. If externally accepted, the acceptance work should update:

| Document | Expected alignment |
| --- | --- |
| 18 — Domain Model and Database Design | Record the separate password-credential persistence boundary and relationship to `users.id`. |
| 22 — Security and Privacy Architecture | Record credential, status, provisioning, enumeration, and abuse-control rules. |
| 26 — Development Roadmap and Implementation Phases | Record the accepted P1-02 prerequisite and implementation boundary. |
| 27 — Decision Log / ADR Index | Index the accepted decision and its relationship to the frozen baseline. |
| `docs/adr/README.md` | Change ADR-0002 status from PROPOSED to ACCEPTED. |

Those patches occur only after external acceptance. ADR-0001 remains
ACCEPTED and unchanged. P1-02 and P1-03 remain Not Started, and this
proposal contains no authentication implementation or migration.
