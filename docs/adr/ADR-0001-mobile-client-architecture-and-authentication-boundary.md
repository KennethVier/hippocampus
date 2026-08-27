---
ADR: ADR-0001
Title: Mobile Client Architecture and Authentication Boundary
Status: PROPOSED
Date: 2026-08-27
Decision Owners: Project Hippocampus Team
Categories:
  - ARCHITECTURE
  - FRONTEND
  - SECURITY
  - BACKEND
  - TESTING
Affected Documents:
  Direct expected patches after acceptance:
    - "17"
    - "22"
    - "26"
    - "27"
  Conditional contradiction/reference audit after acceptance:
    - "16"
    - "19"
    - "20"
    - "23"
    - "25"
    - IMPLEMENTATION-TRACKER.md
  Unchanged:
    - "09"
Supersedes: None
Superseded By: None
---

# Context

Hippocampus v1 is web-first. Its approved first-party client is the
React 19.2.x and TypeScript 6.0.x application built with Vite 8.1.x in
`frontend/`. Phase 0 has already delivered its application shell and
routing, centralized API client, and core UI foundation through P0-09
through P0-11. That investment remains part of the approved v1
architecture.

The Spring Boot modular monolith is the single application backend. It
owns authentication, authorization, internal identity, resource
ownership, application and domain rules, learning state, persistence,
RAG authorization, source authorization, AI-provider access, and
validation. Clients use its HTTPS API contracts and do not directly
access Gemini, Ollama, PostgreSQL, pgvector, private object storage, or
backend secrets.

Phase 1 is about to make identity, authentication, server-side sessions,
CSRF, CORS, current-user resolution, session recovery, and logout
behavior concrete. A future native client should not force those
backend boundaries to be rewritten. At the same time, Document 09
currently defers native mobile applications. Architecture preparation
must therefore preserve a credible future native path without adding a
native application to v1 or to Phase 1.

The existing web authentication baseline is Spring Security with a
server-side session persisted by Spring Session JDBC and transported to
the browser through a secure session cookie. Server-side session
authority is distinct from the transport a future native client will
use. No Expo, iOS, or Android session-transport experiment has occurred,
so this ADR cannot claim that native cookie receipt, persistence, or
attachment has been proven.

# Decision

Hippocampus will use separate web and future native presentation
applications over one Spring Boot backend, one application/domain model,
one server-side authorization model, and one server-side session
authority.

1. **Vite web remains the first-class v1 client.** `frontend/` remains
   the React/Vite web application. It will not be replaced by React
   Native Web or phased out, and P0-09 through P0-11 will not be
   discarded.
2. **Expo/React Native is the preferred eventual native client.** A
   future iOS/Android client should begin with Expo rather than bare
   React Native. Native implementation remains post-v1 unless a separate
   product/MVP decision is approved. This ADR does not select an Expo
   SDK version, router, notification provider, camera library, build
   channel, networking package, or native cookie library.
3. **Spring Boot remains the single backend.** Both clients use the same
   HTTPS API and backend-owned application/domain modules. No mobile
   BFF, alternate Firebase or Supabase application backend, separate
   mobile domain, client-owned authorization, or direct client-to-AI
   access is introduced. A client-specific adaptation inside the
   existing backend may be considered only after concrete impedance is
   demonstrated and must not become another domain or authorization
   boundary.
4. **Authorization remains server-side and client-independent.** A
   Spring Security authenticated principal resolves to internal
   `users.id`, which remains the ownership root. A client-supplied
   `userId` is never authoritative.
5. **Spring Security and Spring Session JDBC remain Hippocampus's
   server-side authentication/session authority.** The web client
   continues to use the approved secure-cookie model. The preferred
   future native client reuses the same opaque server-side session
   authority through a protected, platform-appropriate transport,
   subject to mandatory iOS/Android compatibility and security
   validation before native authentication implementation. Exact native
   transport is deferred. This ADR does not introduce JWT, refresh
   tokens, OAuth access-token transport, Expo SecureStore, a custom
   cookie jar, or a specific networking library.
6. **Presentation implementations remain separate by default.** Web and
   native do not force shared DOM/native components, routing/navigation,
   storage, file pickers, accessibility primitives, streaming adapters,
   session adapters, CSS/Tailwind components, or native gesture/layout
   implementations.
7. **Sharing is disciplined and evidence-driven.** After portable code
   exists, candidates may include API request/response types, API/domain
   DTO contracts, platform-neutral Zod schemas, validation functions and
   constants, pure utilities, stable error-code mappings, query-key
   factories, genuinely platform-neutral client logic, and semantic
   design tokens. Sharing percentage is not a goal.
8. **Repository change is incremental.** The current shape remains
   `backend/` plus `frontend/`. A separately authorized native effort may
   later add `mobile/`. The repository will not now move to `apps/web/`
   and `apps/mobile/`, and `packages/` will not be created before
   concrete, stable portable reuse makes separate ownership valuable.
9. **Existing API styles remain preferred.** REST/JSON, multipart
   uploads, backend-authoritative source references, and backend-owned
   streaming remain the baseline. Web and native may have separate
   streaming adapters. This ADR does not introduce WebSockets, offline
   synchronization, background downloads, or new streaming
   infrastructure. If future Expo/native SSE compatibility is
   inadequate, that transport decision returns to architecture review.
10. **Design identity is shared conceptually, not by forced component
    implementation.** Web and native preserve visual identity, semantic
    tokens, typography and spacing hierarchy, content hierarchy, guided
    Study Mission hierarchy, and accessibility principles through
    platform-appropriate components.

## Future Native Session Compatibility Gate

Before future native authentication implementation, the proposed native
transport must be validated on iOS and Android for:

- session establishment and cookie/session receipt;
- persistence and request attachment;
- session rotation, expiry, and revocation;
- logout and account switching;
- process-restart behavior; and
- prevention of session exposure to ordinary application state and
  logging.

If the preferred native transport fails this validation, native
authentication implementation stops and the authentication transport
returns to ADR review. Failure does not implicitly authorize bearer or
JWT authentication.

This compatibility gate blocks future native authentication, not the
current web-first Phase 1. After this ADR is accepted and required
Source-of-Truth patches and consistency review are complete, P1-01
planning and Phase 1 implementation may proceed without a native-device
spike.

## Phase 1 Implications

- P1-01 remains unchanged; `users.id` is the universal ownership root.
- P1-02 remains a server-side, session-based authentication boundary,
  with the current implementation remaining the approved web flow. It
  adds neither native UI nor token issuance.
- P1-03 keeps Spring Session JDBC authoritative. Backend tests should
  avoid unnecessary browser-only assumptions where practical, but no
  native-device test is required.
- P1-04 implements the approved browser CSRF model without weakening it
  or inventing a generalized native CSRF abstraction. Future native
  mutation protection receives separate security validation.
- P1-05 keeps credentialed browser CORS restricted to approved web
  origins. Native networking is not protected by browser CORS, and CORS
  is not native authorization.
- P1-06 remains client-independent and resolves identity through Spring
  Security.
- P1-07 keeps `/me` privacy-minimal and client-neutral without exposing
  session identifiers or tokens.
- P1-08 remains the Vite web login and session-recovery UX; native UX is
  later work.
- P1-09 remains web logout and private-state clearing; a future native
  client receives equivalent platform-specific privacy requirements.
- P1-10 remains a backend/API-focused, client-independent ownership
  authorization harness.

# Rationale

This architecture preserves completed web work and the approved v1
delivery path while making client-independent backend, identity, and
authorization boundaries explicit before Phase 1. Expo offers a
lower-complexity eventual iOS/Android starting point than owning bare
native projects without a demonstrated need, while retaining a future
escape path if an essential capability cannot be supported responsibly.

A single backend, domain, authorization model, and server-side session
authority minimize infrastructure, operational, migration, and security
complexity. Central session persistence retains straightforward expiry
and revocation. Deferring the exact native transport avoids treating an
untested platform mechanism as settled and avoids assuming that a native
client requires bearer tokens.

Separate presentation applications accept real platform differences in
navigation, accessibility, storage, files, layout, and streaming.
Curated sharing captures portable value without forcing abstractions
that make both clients harder to maintain. Incremental repository
evolution similarly avoids speculative monorepo work before reusable
code exists.

Most importantly, this decision protects the approved MVP: responsive
web serves v1 small-screen users, while native implementation remains a
post-v1 product decision.

# Alternatives Considered

## A. Continue web-only architecture with no native preparation

This has the lowest immediate cost and remains the current delivery
scope, but it leaves Phase 1 free to embed avoidable browser-only backend
assumptions. It is rejected as the architecture direction because a
small amount of boundary clarity now reduces future rewrite risk without
implementing mobile.

## B. Vite web plus a separate Expo/React Native client

This is the selected architecture. It preserves the approved web client,
uses one backend and authorization model, supports platform-appropriate
native UI, and permits disciplined sharing of genuinely portable code.

## C. Replace Vite with an Expo/React Native Web universal application

Rejected because it requires a large rewrite, unnecessarily discards
Phase 0 frontend investment, risks platform-compromised UI and
accessibility behavior, forces premature sharing, and has no current
product justification.

## D. Make PWA/responsive web the permanent mobile strategy

Responsive web remains the v1 small-screen and mobile-browser strategy.
It is not selected as the permanent substitute for the desired eventual
native direction because it would close native distribution and device
capability options before product evidence exists.

## E. Use bare React Native by default

Rejected as the default because Expo offers a lower-complexity starting
point and current requirements do not justify immediate ownership of the
bare native build surface. Bare React Native may be reconsidered if an
actual required capability cannot be responsibly supported through
Expo.

## F. Keep web sessions and add a separate mobile JWT/OAuth token transport

This can be a viable future architecture if validated native-transport
limitations or external identity requirements justify it. It is not the
minimum current requirement and would add token lifecycles, refresh
handling, secure credential storage, revocation complexity, dual
authentication transports, a larger test surface, and operational
complexity. It remains deferred rather than implicitly authorized.

## G. Replace sessions for web and native with bearer/JWT authentication

Rejected for the current v1 because it would replace the approved web
security model and add browser token-management complexity without
evidence that the change is necessary.

# Consequences

## Positive

- One backend, domain model, ownership root, and authorization model
  serve every first-party client.
- The approved Vite web application and Phase 0 investment are
  preserved.
- Expo provides a clear eventual native direction without expanding v1.
- Server-side sessions retain centralized expiry and revocation.
- Repository and shared-code evolution can occur incrementally.
- Explicit presentation boundaries allow each platform to implement
  appropriate navigation, accessibility, storage, and interaction.

## Negative / Tradeoffs

- A future native application creates a second presentation, release,
  maintenance, and testing surface.
- Intentional duplicate UI implementation will exist where platforms
  differ.
- Web/native behavior and visual semantics may diverge without active
  contract and design governance.
- Native session-transport compatibility remains unproven and must be
  validated before native authentication work.
- Future mobile builds, releases, and device tests add cost.
- Shared code must be curated rather than extracted mechanically.

# Security & Privacy Impact

Internal `users.id` remains the ownership root. Every client request is
authorized server-side from a Spring Security authenticated principal;
client-supplied identity is never authority. Private student materials
and learning evidence remain private by default, cross-user leakage
tolerance remains zero, source authorization still occurs before
retrieval/ranking, and AI-provider credentials remain backend-only.

Spring Security and Spring Session JDBC remain the server-side
authentication/session authority. The web keeps the approved session
cookie with appropriate HttpOnly, Secure, and SameSite settings. Browser
cookie-authenticated mutations retain Spring Security CSRF protection,
and credentialed browser CORS remains limited to approved web origins.
CORS is a browser-origin control, not native authorization.

The future native client should reuse the opaque server-side session
through a protected platform-appropriate transport, but no native cookie
mechanism is proven or selected here. Native session and mutation
semantics require the future compatibility and security gate. Session
identifiers or tokens must not enter ordinary application state or logs,
and long-lived secrets must not be stored in AsyncStorage. This ADR does
not select an exact secure-storage mechanism.

Server-side expiry and revocation remain available. Logout and account
switching must invalidate the applicable server-side authentication
state and clear user-scoped caches, drafts, streams, file previews, and
other private client state. Authentication and session failures fail
closed. Failed native transport validation returns to ADR review and
does not automatically authorize JWT.

# Educational/Product Impact

There is no change to the educational model, Study Missions, learning
policy, or approved v1 feature boundary. Vite web remains the first-class
v1 student client, and responsive web continues to serve current v1
small-screen users.

Native mobile implementation is not authorized for v1 by this ADR. It
remains post-v1 under Document 09. Promoting native into v1, adding it to
Phases 1--12, reducing web priority, or replacing web requires a separate
higher-authority product/MVP review and corresponding Source-of-Truth
patches. This ADR alone cannot make that change.

# Cost / Infrastructure Impact

The proposed decision has minimal current runtime-infrastructure impact.
The existing Vite deployment and Spring Boot backend remain in place,
and no second backend or identity service is introduced.

Future native implementation may add Expo/native build tooling,
app-store release work, iOS/Android device testing, and ongoing mobile
release maintenance. This ADR selects no store vendor, build service,
release channel, or notification infrastructure.

# Migration Impact

There is no Vite migration, Phase 0 frontend rewrite, or repository
restructure. The current `backend/` and `frontend/` structure remains.
A future, separately authorized native implementation may add `mobile/`.
Shared packages should be extracted only after concrete, stable portable
reuse exists and separate ownership improves maintainability.

Existing backend contracts should remain client-neutral where practical.
Concrete client impedance may justify a narrow adapter inside the
existing backend later, but not a separate domain or authorization
boundary.

# Testing Impact

Existing web and backend tests remain authoritative for v1. Phase 1
backend authentication, session, authorization, expiry, revocation, and
API tests should avoid unnecessary browser-only assumptions where
practical. Browser CSRF, CORS, login, session recovery, logout, and
private-state-clearing tests remain required as already documented.

Future native authentication implementation must add bounded iOS and
Android validation for session transport, login/logout, rotation,
expiration, revocation, account switching, process restart, and
private-state clearing. Future native client work must also test API
contract conformance, multipart upload, its streaming adapter, and zero
cross-user leakage.

This future native compatibility gate is not a prerequisite for the
current web-first Phase 1.

# Documentation Changes Required

No frozen Source-of-Truth or tracker document is patched while this ADR
remains PROPOSED. Document 27 requires documents actually affected by an
accepted ADR to be patched; "affected" means a real contradiction or
authority impact, not every adjacent document.

| Document | Classification | Purpose after acceptance |
|---|---|---|
| 09 — MVP Scope & Roadmap | NO CHANGE | Web-first v1 and post-v1 native preserve its current product boundary. Patch only after a separate product decision. |
| 16 — System Architecture | MAY PATCH | Clarify client wording only if web-only language creates a real contradiction; preserve the single backend. |
| 17 — Technology Stack & ADR Baseline | MUST PATCH | Record the future Expo/React Native direction while preserving Vite web and the Spring Session authority. |
| 19 — Backend Architecture | MAY PATCH | Clarify client-neutral backend ownership only if required. |
| 20 — Frontend Architecture | MAY PATCH | At most clarify that it remains the Vite web authority and that future native presentation is separate; do not rewrite it as universal. |
| 22 — Security & Privacy Architecture | MUST PATCH | Distinguish shared server-side session authority from the unproven, validation-gated native transport. |
| 23 — Deployment & Infrastructure | MAY PATCH | Current v1 topology remains; add no speculative app-store infrastructure. |
| 25 — Testing Strategy | MAY PATCH | Add only a narrow future compatibility requirement if necessary, not a full post-v1 native program. |
| 26 — Development Roadmap & Implementation Phases | MUST PATCH | Record ADR resolution as the pre-Phase 1 architecture checkpoint while keeping native implementation post-v1. |
| 27 — Decision Log / ADR Index | MUST PATCH AFTER ACCEPTANCE | Record ADR-0001 only after external disposition. |
| `IMPLEMENTATION-TRACKER.md` | MAY PATCH | Clarify existing P1 wording or authority references only where materially necessary; create no competing task series. |

After acceptance, patch actual contradictions and authority impacts only,
perform a documentation-consistency review, and preserve the prohibition
on native v1 scope. The intended sequence is:

```text
Accept ADR
→ required Source-of-Truth patches
→ documentation consistency review
→ P1-01 planning
→ web-first Phase 1 implementation
```

# Approval

Status remains **PROPOSED**. External architecture, security, and product
review is required. This ADR is not self-approved and does not authorize
implementation until it receives external disposition and the required
governance steps are complete.
