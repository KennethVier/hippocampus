---
name: hippocampus-react-typescript-engineering
description: Use for Hippocampus React 19 + TypeScript frontend planning, implementation, refactoring, or review, and for future Expo/React Native work when authorized. Applies SRP, composition, strict typing, state/effect discipline, platform boundaries, testability, accessibility, and secure frontend engineering without introducing unapproved dependencies.
---

# React / TypeScript Engineering for Hippocampus

## First Rule

Read the tracker task, frontend architecture/design authority, and accepted mobile ADRs before changing presentation architecture or adding dependencies.

Use the simplest component/composition model that makes responsibility, data flow, and user behavior obvious.

## Responsibility and Composition

SRP applies to frontend code.

- Components primarily render UI and coordinate local interaction.
- Keep API transport, persistence/storage, domain/business rules, and unrelated global state out of large UI components.
- Extract a custom hook when it represents reusable/cohesive stateful behavior, not merely to move lines elsewhere.
- Prefer function composition and typed callbacks over class-pattern translations from Java.
- Split components by cohesive responsibility and meaningful rendering boundaries, not arbitrary line counts.
- Avoid giant page components that own fetching, mapping, business rules, forms, navigation, and rendering simultaneously.

## TypeScript Standard

Keep strict TypeScript enabled.

- Avoid `any`. If unavoidable at a narrow third-party boundary, isolate and document it.
- Treat external/untrusted data as `unknown` until narrowed or runtime-validated.
- Prefer discriminated unions for finite UI/result states.
- Prefer `readonly`/immutable contracts where mutation is not part of the API.
- Avoid broad type assertions (`as`) that bypass validation.
- Do not use `!` non-null assertions to silence unresolved state-model problems.
- Model optionality intentionally; do not make fields optional merely to simplify construction.
- Prefer precise domain names over `data`, `info`, `payload`, or generic objects where a meaningful type exists.

Evaluate stricter compiler options when compatible with the codebase/task, such as `noUncheckedIndexedAccess`, `exactOptionalPropertyTypes`, and `noImplicitOverride`; do not enable a project-wide flag as incidental feature scope without fixing/validating its impact.

## UI State Modeling

Prefer explicit state models.

Example:

```ts
type MissionState =
  | { type: 'idle' }
  | { type: 'loading' }
  | { type: 'ready'; mission: Mission }
  | { type: 'failed'; error: ApiError };
```

This is usually safer than unrelated `isLoading`, `error?`, and `mission?` fields that can represent contradictory combinations.

## State Ownership

Use the narrowest owner that needs the state.

- URL state: router/search params when state should be shareable/navigation-aware.
- Server state: use the project-approved fetching/cache mechanism; do not duplicate server truth into Zustand or another client store without a specific offline/client-state requirement.
- Local UI state: `useState` or `useReducer`.
- Form state: use the project-approved form approach.
- Cross-feature client-only state: global store only when multiple distant consumers genuinely need it.

Do not introduce TanStack Query, Redux, another state manager, or another form library solely because it is popular; follow approved dependencies/architecture.

## Effects

Treat `useEffect` as synchronization with an external system, not a default place for application logic.

Do not use effects for:

- deriving values that can be computed during render;
- handling ordinary user events that belong in event handlers;
- copying props/server data into redundant local state;
- chains of state updates that can be modeled directly.

Effects must have explicit dependencies and cleanup where external subscriptions/resources require it.

## Memoization and Performance

Do not add `useMemo`, `useCallback`, or `memo` everywhere.

Use memoization when profiling/known identity requirements justify it or when a library contract depends on stable identity. Prefer clear code first and optimize measured bottlenecks.

Avoid accidental expensive work in render, unnecessary network duplication, and oversized rerender surfaces where evidence shows impact.

## API Boundaries

- Centralize approved HTTP transport behavior rather than scattering raw fetch configuration.
- Keep credentials/session/CSRF behavior aligned with backend security architecture.
- Treat response bodies as untrusted until status/content type/schema are validated where appropriate.
- Use typed API errors/stable codes; do not infer behavior from arbitrary error strings.
- Never place server-only secrets/provider keys in frontend environment variables or bundles.
- Frontend authorization controls UI; backend authorization owns security.

## Forms and Validation

- Validate for user feedback in the client and independently validate on the server.
- Do not treat client validation as a security boundary.
- Keep form schemas/types aligned with approved API contracts without sharing browser-only/platform-specific code into backend/domain layers.

## Accessibility and UX Correctness

When UI work is in scope:

- use semantic elements;
- preserve keyboard interaction/focus behavior;
- associate labels/errors with controls;
- expose meaningful loading/error/empty states;
- do not make color alone carry required meaning;
- respect reduced-motion/other design requirements where applicable.

## Pattern Guidance

Apply `hippocampus-architecture-patterns`.

React patterns are usually expressed through functions/composition rather than GoF classes.

Useful patterns when design pressure exists include:

- custom hooks for cohesive reusable stateful behavior;
- reducer/state machine for non-trivial finite transitions;
- controlled component for owner-managed value state;
- compound component/provider for a genuinely cohesive component family/context;
- adapter functions for external/API-to-view-model translation;
- feature modules for ownership boundaries.

Avoid pattern theater: `useButton`, `useLabel`, wrapper providers, or generic component factories that add indirection without responsibility or reuse.

## Web vs Future React Native

Follow the accepted mobile architecture ADR:

- web remains a first-class React client;
- future native client is Expo/React Native when authorized;
- one backend/application/domain authorization model remains authoritative;
- do not replace web with React Native Web;
- keep web and native presentation/navigation/storage/file-picker/styling concerns separate by default;
- share only genuinely platform-neutral contracts, schemas, pure utilities, error mappings, query-key factories, and semantic design tokens when real reuse exists;
- do not create `mobile/` or `packages/` before an authorized task and proven shared code need;
- choose exact Expo/React Native versions when native work begins rather than freezing today's version in advance.

## Testing

Apply `hippocampus-testing-security`.

Prefer tests that exercise student-visible behavior with React Testing Library. Avoid asserting implementation details, internal hook state, or arbitrary component structure.

## Review Checklist

- Clear component/feature responsibility?
- Strict types with no casual `any` or unsafe assertions?
- External data narrowed/validated?
- State represented without contradictory flags?
- Derived state computed rather than synchronized by effect?
- Server state duplicated into global client state?
- API/security/session concerns centralized?
- Accessibility behavior covered where required?
- Pattern/composition reduces complexity rather than adding indirection?
- Web/native boundaries preserved?
- New dependency actually approved/necessary?
