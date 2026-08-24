---
Audience: Frontend, architecture, product, UX, QA, backend, and
  accessibility contributors.
Authors: Project Hippocampus Team
Created: 2026-08-24
Document ID: 20
Last Updated: 2026-08-24
Owner: Project Hippocampus Team
Prerequisites:
- 04 - Product Requirements
- 05 - User Personas
- 06 - User Journey & Learning Flow
- 07 - Feature Specifications
- 08 - Non-Functional Requirements
- 09 - MVP Scope & Roadmap
- 11 - AI Learning Engine
- 13 - RAG Architecture
- 14 - Knowledge Base Design
- 16 - System Architecture v1.1+
- 17 - Technology Stack & ADR Baseline
- 18 - Domain Model & Database Design
- 19 - Backend Architecture
Purpose: Define the React frontend architecture for Hippocampus v1,
  including route structure, state ownership, guided Study Mission UI,
  upload and processing flows, streaming behavior, visual learning,
  accessibility, API integration, error handling, and frontend testing
  boundaries.
Related Documents:
- 21 - File Processing & Ingestion Architecture
- 22 - Security & Privacy Architecture
- 23 - Deployment & Infrastructure
- 24 - Observability & Operations
- 25 - Testing Strategy
- 26 - Development Roadmap & Implementation Phases
- 27 - Decision Log / ADR Index
Scope: React application structure, routing, feature/module
  organization, server state, local/global state, Study Mission
  rendering, material upload UX, processing status, SSE streaming,
  source references, visual assets, progress/review interfaces,
  accessibility, responsive behavior, API client design, form handling,
  loading/error states, and testing seams.
Status: Final
Title: Frontend Architecture
Version: 1.0.0
---

# 20 - Frontend Architecture

## 1. Purpose

This document defines how the Hippocampus v1 web frontend should be
structured and behave.

It answers:

> **How should the React application present the full learning system as
> a calm, guided, student-centered experience without exposing the
> complexity of AI, RAG, prompts, or backend orchestration?**

The frontend is not the Learning Engine.

It is the interaction layer through which the student experiences the
decisions made by Hippocampus.

------------------------------------------------------------------------

# 2. Locked Frontend Principle

> **Features should appear as a guided learning flow, not as an
> overwhelming collection of tools and sidebars.**

The anti-pattern is:

``` text
Sidebar
├── PDF Summarizer
├── Flashcards
├── Quiz Generator
├── Ask AI
├── Case Generator
├── Image Quiz
├── Review
├── Notes
├── Timer
└── ...
```

The intended experience is:

``` text
Choose What to Study
      ↓
Choose / Attach Material
      ↓
Start Study Mission
      ↓
Understand
      ↓
Retrieve
      ↓
Connect
      ↓
Apply
      ↓
Feedback
      ↓
Reflect / Continue / Review
```

The student should interact with the learning flow, not the
implementation feature list.

------------------------------------------------------------------------

# 3. Frontend Technology Baseline

The v1 frontend uses:

``` text
React 19.2.x
TypeScript 6.0.x
Vite 8.1.x
Tailwind CSS 4.3.x
React Router 7.x
TanStack Query 5.x
React Hook Form
Zod
Minimal Zustand 5.x
Vitest
React Testing Library
Playwright
```

The frontend communicates only with the Spring Boot backend.

It must never call Gemini or Ollama directly.

------------------------------------------------------------------------

# 4. Frontend Responsibilities

The frontend owns:

-   Rendering
-   User interaction
-   Route navigation
-   Form state
-   Local UI state
-   Server-state synchronization
-   Upload UX
-   Study Mission presentation
-   SSE display
-   Processing progress display
-   Source citation presentation
-   Visual asset presentation
-   Accessible interaction patterns
-   Responsive layout

The frontend does not own:

-   Learning Engine rules
-   Mastery/evidence calculation
-   Review scheduling
-   RAG scope
-   Provider routing
-   Prompt construction
-   AI safety
-   Source authorization

------------------------------------------------------------------------

# 5. Route Philosophy

Routes should follow the student's mental model.

Recommended top-level route areas:

``` text
/
├── /home
├── /subjects
├── /subjects/:subjectId
├── /topics/:topicId
├── /missions/:missionId
├── /materials
├── /materials/:materialId
├── /review
├── /progress
└── /settings
```

Not every feature gets a route.

For example:

-   Explanation
-   Retrieval question
-   Connection
-   Case
-   Reflection
-   Feedback

are all rendered inside:

``` text
/missions/:missionId
```

------------------------------------------------------------------------

# 6. Minimal Navigation

Primary navigation should stay compact.

Recommended:

``` text
Home
Subjects
Materials
Review
Progress
```

Settings may remain secondary.

Do not add a primary nav item simply because a backend capability
exists.

------------------------------------------------------------------------

# 7. Application Shell

Recommended shell:

``` text
┌───────────────────────────────────────┐
│ Top Bar                               │
│ Logo / Current Context / Profile      │
├───────────────┬───────────────────────┤
│ Optional      │                       │
│ compact nav   │ Main Learning Area    │
│               │                       │
│               │                       │
└───────────────┴───────────────────────┘
```

On smaller screens, navigation collapses into a drawer or bottom pattern
where appropriate.

The learning area should remain visually dominant.

------------------------------------------------------------------------

# 8. Home Screen

The Home screen should answer:

> **What should I study now?**

Possible sections:

``` text
Continue Current Mission
Upcoming Reviews
Recently Studied Topics
Quick Start Topic
Recently Added Materials
```

Avoid turning Home into an analytics dashboard full of charts.

------------------------------------------------------------------------

# 9. Subject Screen

A Subject screen groups Topics.

Example:

``` text
Anatomy

Brachial Plexus
Upper Limb Muscles
Thorax
Cranial Nerves
```

Each Topic card may show lightweight evidence:

``` text
Developing
Review Due
In Progress
Not Started
```

Avoid exposing fake numeric mastery percentages.

------------------------------------------------------------------------

# 10. Topic Screen

The Topic screen is the bridge into a Study Mission.

It may show:

``` text
Topic title
Relevant materials
Detected source sections
Recent learning evidence
Available review state
Start / Resume Mission
```

The main call to action should be:

``` text
Start Study Mission
```

not:

``` text
Choose AI Feature
```

------------------------------------------------------------------------

# 11. Study Mission Route

The most important frontend route is:

``` text
/missions/:missionId
```

The route should preserve mission continuity.

The student should be able to:

-   Resume
-   Continue
-   Pause
-   Stop
-   View current source
-   Submit answers
-   Request a simpler explanation where allowed

without manually selecting the next educational tool.

------------------------------------------------------------------------

# 12. Study Mission Layout

Recommended desktop layout:

``` text
┌──────────────────────────────────────────────┐
│ Topic / Mission Progress / Timer             │
├───────────────────────────────┬──────────────┤
│                               │              │
│ Current Learning Activity     │ Source       │
│                               │ Context      │
│ Explanation / Question /      │              │
│ Scenario / Feedback           │ Text/Image   │
│                               │ Citation     │
│                               │              │
├───────────────────────────────┴──────────────┤
│ Response / Continue / Retry / Reflect        │
└──────────────────────────────────────────────┘
```

Source context should be collapsible.

On mobile/tablet, source context becomes a drawer or stacked section.

------------------------------------------------------------------------

# 13. Mission Stage Indicator

A lightweight indicator may show:

``` text
Understand → Retrieve → Connect → Apply
```

But it must not imply every mission is rigidly linear.

Current stage should be highlighted.

Backtracking caused by Learning Engine decisions should appear natural.

Example:

``` text
Apply
↓
Need a little clarification
↓
Understand
```

The UI should not frame this as failure.

------------------------------------------------------------------------

# 14. Timer UX

Timer is part of the mission experience.

Recommended:

``` text
25:00 remaining
```

or:

``` text
12 min remaining
```

Behavior:

-   Always visible but not visually dominant
-   Does not count as mastery
-   Does not force premature activity completion
-   Backend remains authoritative for session timing logic
-   Frontend may animate countdown locally based on backend-provided
    timing data

------------------------------------------------------------------------

# 15. Learning Activity Renderer

Use a central activity-rendering boundary.

Conceptually:

``` text
LearningActivityRenderer
├── ExplanationActivity
├── RetrievalActivity
├── ConnectionActivity
├── ApplicationActivity
├── VisualActivity
├── FeedbackActivity
└── ReflectionActivity
```

The renderer receives a typed backend activity contract.

Avoid switching on provider/model names.

------------------------------------------------------------------------

# 16. Activity Contract

Frontend should receive a normalized payload.

Conceptually:

``` ts
type LearningActivity =
  | ExplanationActivity
  | RetrievalActivity
  | ConnectionActivity
  | ApplicationActivity
  | VisualActivity
  | FeedbackActivity
  | ReflectionActivity;
```

The frontend should not parse free-form AI output into activity types.

Backend must provide explicit type and structured content.

------------------------------------------------------------------------

# 17. Explanation Activity

May contain:

``` text
Title
Explanation
Key Points
Optional Source References
Optional Visual
Actions:
- Continue
- Explain More Simply
- Show Prerequisite
```

Only actions currently allowed by backend state should be displayed.

------------------------------------------------------------------------

# 18. Retrieval Activity

May support:

``` text
Short Answer
MCQ
Identification
Explanation Question
```

Frontend requirements:

-   Clear single task
-   No accidental answer reveal
-   Submit state
-   Disabled duplicate submission while pending
-   Retry state
-   Feedback shown only after backend evaluation

------------------------------------------------------------------------

# 19. Application Activity

Clinical/practical application should visually distinguish:

``` text
Scenario
Question
Response Area
```

But should not over-style cases as real clinical patient records.

Educational framing must remain clear.

------------------------------------------------------------------------

# 20. Visual Activity

Visual activity may present:

``` text
Original source image
Question
Zoom/pan
Optional caption
Source reference
Response
```

Important:

> **The original source image should be preferred over AI-generated
> visual replacement.**

Useful frontend capabilities:

-   Zoom
-   Pan
-   Fullscreen/lightbox
-   Responsive scaling
-   Caption/source toggle

------------------------------------------------------------------------

# 21. Source Panel

Source context should help answer:

> **Where did Hippocampus get this?**

Possible display:

``` text
Upper Limb Lecture
Page 14
Posterior Cord
```

Actions may include:

``` text
View source
Open page
Show related figure
```

Internal chunk IDs should never be exposed to the student.

------------------------------------------------------------------------

# 22. Supplemental Knowledge Label

If backend classifies content as supplemental:

Display something like:

``` text
Additional medical context
```

rather than presenting it as part of the uploaded material.

The frontend must respect backend classification:

``` text
SOURCE_DERIVED
SOURCE_GROUNDED_GENERATED
SUPPLEMENTAL_GENERATED
GENERAL_GENERATED
```

------------------------------------------------------------------------

# 23. Feedback Activity

Feedback should visually separate:

``` text
What you got right
What is missing
What to focus on next
```

Avoid giant success/failure banners.

Use calm, formative presentation.

------------------------------------------------------------------------

# 24. Reflection Activity

Reflection should remain lightweight.

Examples:

``` text
How confident are you?
What still feels unclear?
```

Do not turn reflection into a long survey.

------------------------------------------------------------------------

# 25. Mission Completion Screen

Completion should show:

``` text
Mission complete
What you practiced
What seems strong
What still needs work
What may return for review
```

Avoid:

``` text
You mastered this topic: 87%
```

unless a future evidence system explicitly supports such a claim.

Primary actions:

``` text
Finish
Review another topic
Continue studying
```

------------------------------------------------------------------------

# 26. Resume Experience

When a student returns:

``` text
Continue:
Brachial Plexus
Last activity:
Application
```

Resume should restore:

-   Current mission
-   Current stage
-   Current activity
-   Existing timer state
-   Relevant source context

Do not replay completed mission setup.

------------------------------------------------------------------------

# 27. Review Route

The Review area should answer:

> **What should I revisit and why?**

Cards may display:

``` text
Posterior Cord
Reason: Application was weak
Available now
```

The backend provides review reason.

Frontend must not invent or reinterpret it.

------------------------------------------------------------------------

# 28. Progress Route

Progress should remain evidence-focused.

Possible presentation:

``` text
Topic: Brachial Plexus

Retrieval        Strong
Connections      Developing
Application      Weak
Review Retention Insufficient Evidence
```

Avoid false precision.

Charts may be added only where they improve comprehension.

------------------------------------------------------------------------

# 29. Materials Route

The Materials area should support:

-   Upload
-   Processing status
-   Detected structure
-   Topic linking
-   Source inspection
-   Delete/archive
-   Reprocess where allowed

It should not resemble a generic file manager unnecessarily.

------------------------------------------------------------------------

# 30. Material Card

Example:

``` text
Cardiac Physiology.pdf
624 pages
Processing: 72%

Detected:
Chapter 9 — Cardiac Muscle
Chapter 10 — Rhythmical Excitation
...
```

State:

``` text
PROCESSING
READY
PARTIALLY_READY
FAILED
UNSUPPORTED
```

------------------------------------------------------------------------

# 31. Material Processing UX

Processing should be transparent.

Display:

``` text
Extracting pages
Detecting sections
Preparing search
```

rather than technical terms such as:

``` text
Generating pgvector embeddings
```

unless shown in developer/admin diagnostics.

------------------------------------------------------------------------

# 32. Large PDF UX

For large multi-topic PDFs:

``` text
Detected Structure
├── Chapter 1
├── Chapter 2
│   ├── Section A
│   └── Section B
└── ...
```

Student may:

-   Browse
-   Select relevant sections
-   Use detected hierarchy to associate with Topics

The frontend should not force the student to manually tag all 600 pages.

------------------------------------------------------------------------

# 33. Upload Flow

Recommended flow:

``` text
Choose File
↓
Validate Client-Side Basics
↓
Upload
↓
Backend Accepts
↓
Show Processing State
↓
Detected Structure Appears
↓
Material Ready
```

Client validation is convenience only.

Backend validation remains authoritative.

------------------------------------------------------------------------

# 34. Upload Progress

There are two distinct progress concepts:

## Transfer Progress

``` text
Uploading 80%
```

## Processing Progress

``` text
Preparing material 34%
```

Do not conflate them.

------------------------------------------------------------------------

# 35. Upload Failure UX

Examples:

``` text
This PDF is password protected.
Please upload an unlocked copy.
```

``` text
We could read the file, but some pages could not be processed.
You can still use the available sections.
```

Backend error codes should map to user-friendly messages.

------------------------------------------------------------------------

# 36. PARTIALLY_READY UX

A partially ready material should clearly explain:

``` text
Most of this material is ready.
Some pages or images could not be processed.
```

Allow studying supported sections if backend permits.

Do not silently hide failures.

------------------------------------------------------------------------

# 37. Server State Ownership

Use TanStack Query for server-owned state.

Examples:

``` text
Subjects
Topics
Materials
Mission
Activities
Progress
Reviews
Processing Status
```

Do not copy these into Zustand by default.

------------------------------------------------------------------------

# 38. Query Key Strategy

Use consistent keys.

Examples:

``` ts
['subjects']
['subject', subjectId]
['topic', topicId]
['mission', missionId]
['material', materialId]
['material-processing', materialId]
['reviews']
['progress', topicId]
```

Keys should map cleanly to backend resource identity.

------------------------------------------------------------------------

# 39. Mutation Strategy

Mutations include:

``` text
Create topic
Upload material
Start mission
Submit response
Pause mission
Delete material
Complete reflection
```

After mutation:

-   Update known cache directly when safe
-   Invalidate only affected queries
-   Avoid broad `invalidate everything`

------------------------------------------------------------------------

# 40. Local UI State

Use React local state for:

-   Expanded/collapsed source panel
-   Modal visibility
-   Temporary selected option
-   Local form step
-   Image zoom state

Keep local state local.

------------------------------------------------------------------------

# 41. Zustand Usage

Use Zustand only when UI state is shared across distant components.

Potential examples:

``` text
Global upload drawer
Temporary mission UI preferences
Non-server navigation state
```

Do not store:

-   Topics
-   Missions
-   Learning evidence
-   Review state

as authoritative Zustand data.

------------------------------------------------------------------------

# 42. URL State

Use the URL for state that should be:

-   Bookmarkable
-   Navigable
-   Shareable within the user's account context

Examples:

``` text
selected material tab
subject/topic route
filter
page
```

Do not store sensitive answers in URL query strings.

------------------------------------------------------------------------

# 43. Form Architecture

Use React Hook Form + Zod for:

-   Subject/topic creation
-   Upload metadata
-   Study Mission setup
-   Free-text answers
-   Reflection

Server validation errors should map back to fields when appropriate.

------------------------------------------------------------------------

# 44. API Client

Use one centralized backend API client.

Responsibilities:

-   Base URL
-   Credentials/cookies
-   JSON parsing
-   ProblemDetail parsing
-   Correlation ID extraction
-   AbortSignal support
-   Multipart support

Do not call `fetch` ad hoc across components.

------------------------------------------------------------------------

# 45. Authentication Behavior

Session cookie authentication means:

``` text
Browser
↓
Spring Boot session cookie
```

Frontend should not:

-   Store access token in localStorage
-   Manage refresh tokens
-   Expose AI provider credentials

Unauthenticated responses route to login/session recovery.

------------------------------------------------------------------------

# 46. SSE Client

Use a centralized streaming client abstraction.

Responsibilities:

-   Start/connect
-   Abort
-   Reconnect only where safe
-   Normalize events
-   Surface completion
-   Surface provider/application failure
-   Protect against duplicate completion

------------------------------------------------------------------------

# 47. Streaming UI

For explanation streaming:

``` text
Request
↓
Loading skeleton
↓
First content
↓
Progressive render
↓
Complete
↓
Validated actions enabled
```

Do not enable answer-dependent controls from incomplete stream output.

------------------------------------------------------------------------

# 48. Streaming Failure

If stream ends unexpectedly:

Display:

``` text
The explanation was interrupted.
```

Offer:

``` text
Retry
```

where backend allows.

Do not persist partial explanation as a completed artifact client-side.

------------------------------------------------------------------------

# 49. Optimistic UI

Use optimistic updates selectively.

Good candidates:

-   Rename Topic
-   Toggle UI preference

Poor candidates:

-   Submit learning evidence
-   Complete mission
-   Delete source material
-   Mark review complete

For educational state, prefer confirmed backend responses.

------------------------------------------------------------------------

# 50. Loading States

Use explicit loading states.

Examples:

``` text
Loading topic…
Preparing mission…
Evaluating your answer…
Retrieving source…
Preparing material…
```

Avoid a single generic spinner for every operation.

------------------------------------------------------------------------

# 51. Long AI Wait UX

When AI requires longer processing:

``` text
Evaluating your response…
```

may show.

Do not expose provider name by default.

The student should not need to know whether Gemini or Ollama executed
the task.

------------------------------------------------------------------------

# 52. Provider Transparency

Provider identity is primarily diagnostic/operational.

Do not show:

``` text
Powered by Gemini
Fallback to Ollama
```

inside normal Study Missions unless product policy later requires
disclosure.

The source of educational evidence matters more to the student than
provider routing.

------------------------------------------------------------------------

# 53. Error Boundary Strategy

Use:

-   Route-level error boundaries
-   Activity-level fallback
-   Upload-level error display

One broken activity component should not crash the entire application
shell.

------------------------------------------------------------------------

# 54. API Error Mapping

Create user-facing mappings for stable backend codes.

Examples:

``` text
MATERIAL_NOT_READY
MATERIAL_PARTIALLY_READY
AI_TEMPORARILY_UNAVAILABLE
RETRIEVAL_INSUFFICIENT
MISSION_STATE_CONFLICT
UPLOAD_TOO_LARGE
UNSUPPORTED_FILE
```

Unknown errors receive generic fallback plus correlation ID.

------------------------------------------------------------------------

# 55. Stale Mission Conflict

If two tabs submit the same activity:

Backend may return conflict.

Frontend should display:

``` text
This mission changed in another tab.
Reloading your current activity…
```

Then refetch mission state.

Do not silently double-submit.

------------------------------------------------------------------------

# 56. Responsive Design

Primary target:

``` text
Desktop
Laptop
Tablet
Mobile
```

Study Mission must remain usable on smaller screens.

On mobile:

-   Source panel → drawer
-   Navigation → compact
-   Visuals → fit/zoom
-   Actions → thumb-friendly
-   Long text → readable width

------------------------------------------------------------------------

# 57. Reading Width

Educational text should use a controlled readable width.

Avoid full-screen 1600px-wide paragraphs.

Use max-width containers for explanation text.

------------------------------------------------------------------------

# 58. Typography

Medical terminology benefits from clear hierarchy.

Use:

-   Strong heading hierarchy
-   Comfortable body size
-   Adequate line height
-   Consistent term emphasis

Avoid tiny dense text.

------------------------------------------------------------------------

# 59. Color Usage

Color should communicate:

-   Status
-   Feedback
-   Current stage
-   Warnings

but should not be the only signal.

Example:

``` text
Weak
```

must have textual/icon support, not only red color.

------------------------------------------------------------------------

# 60. Accessibility Baseline

Target WCAG 2.2 AA-aligned behavior.

Key requirements:

-   Keyboard navigation
-   Visible focus
-   Semantic headings
-   Form labels
-   ARIA only where needed
-   Sufficient contrast
-   Reduced-motion respect
-   Screen-reader-friendly activity status
-   Accessible modal/drawer behavior

------------------------------------------------------------------------

# 61. Accessibility for Streaming

When text streams:

-   Avoid announcing every token
-   Use a polite live region for completion/status
-   Let screen-reader users read the completed content normally

Do not create noisy live-region output.

------------------------------------------------------------------------

# 62. Accessibility for Images

Source images should include:

-   Existing source caption where available
-   Appropriate alt text when reliable
-   Explicit "visual description unavailable" when not reliable

Do not fabricate alt descriptions for medical images if interpretation
is uncertain.

------------------------------------------------------------------------

# 63. Keyboard Support

Critical Study Mission actions must be keyboard usable:

-   Select MCQ
-   Submit
-   Continue
-   Open source
-   Close source drawer
-   Pause mission

------------------------------------------------------------------------

# 64. Visual Zoom Accessibility

Image zoom/lightbox must support:

-   Keyboard close
-   Escape key
-   Focus trapping
-   Zoom controls with labels
-   Touch gestures where practical

------------------------------------------------------------------------

# 65. Empty States

Examples:

## No Topics

``` text
Create your first topic to start studying.
```

## No Materials

``` text
Add lecture notes, a PDF, image, or transcript when you're ready.
```

## No Review Due

``` text
Nothing needs review right now.
```

Do not fill empty screens with irrelevant features.

------------------------------------------------------------------------

# 66. Offline / Connectivity State

v1 is not offline-first.

If network is unavailable:

-   Clearly indicate connection loss
-   Preserve unsent typed text locally in component state where
    practical
-   Do not claim submission succeeded
-   Retry safe GET requests automatically through TanStack Query
-   Avoid automatic retries for non-idempotent learning submissions
    unless backend idempotency protects them

------------------------------------------------------------------------

# 67. Browser Persistence

Use browser persistence sparingly.

Permitted candidates:

-   Non-sensitive UI preferences
-   Draft answer for current activity if needed
-   Last-opened local UI tab

Avoid localStorage for:

-   Session secrets
-   AI provider keys
-   authoritative learning evidence
-   source documents
-   full Study Mission state

------------------------------------------------------------------------

# 68. Source File Display

When backend provides secure source access:

-   PDF may open in controlled viewer/new route
-   Image may open in lightbox
-   Transcript may jump to timestamp
-   Page reference should be visible

Source access must use authorized backend URLs.

------------------------------------------------------------------------

# 69. PDF Viewer Scope

v1 does not need to build a full PDF editor.

Required baseline:

-   View page
-   Navigate to cited page
-   Zoom
-   Possibly highlight cited region later

Annotation tooling is deferred.

------------------------------------------------------------------------

# 70. Visual Evidence Surface

A source reference component should be reusable.

Conceptually:

``` text
<SourceReferenceCard
  title
  page
  section
  type
  onOpen
/>
```

The same visual language should appear in:

-   Explanation
-   Feedback
-   Question review
-   Material inspection

------------------------------------------------------------------------

# 71. Detected Structure Component

Material hierarchy UI:

``` text
DocumentStructureTree
├── Chapter
│   ├── Section
│   └── Section
└── Chapter
```

Requirements:

-   Lazy/collapsible rendering for large structures
-   No rendering of 600-page trees fully expanded
-   Selection support
-   Search/filter may be added if necessary

------------------------------------------------------------------------

# 72. Performance Baseline

Frontend should optimize for perceived responsiveness.

Key tactics:

-   Route-level code splitting
-   Lazy load heavy PDF/image viewer
-   Avoid over-fetching
-   TanStack Query cache
-   Virtualize very long lists/trees where needed
-   Defer non-critical panels
-   Compress/size visual assets appropriately from backend

------------------------------------------------------------------------

# 73. Code Splitting

Suggested lazy chunks:

``` text
Materials viewer
PDF viewer
Study Mission
Progress
Review
Settings
```

Do not split tiny components excessively.

------------------------------------------------------------------------

# 74. Large Document UI Performance

Detected structure for a 600-page source may contain many nodes.

Use:

-   Lazy expansion
-   Virtualization if required
-   Server-side paging/search for extremely large metadata sets

Do not render every chunk in the browser.

Chunks are backend/RAG internals.

------------------------------------------------------------------------

# 75. Frontend Folder Structure

Recommended:

``` text
src/
├── app/
│   ├── router/
│   ├── providers/
│   └── layout/
├── features/
│   ├── auth/
│   ├── subjects/
│   ├── topics/
│   ├── materials/
│   ├── study-mission/
│   ├── review/
│   └── progress/
├── components/
│   ├── ui/
│   └── source/
├── api/
├── hooks/
├── state/
├── schemas/
├── types/
├── utils/
└── test/
```

Feature code should remain cohesive.

------------------------------------------------------------------------

# 76. Feature Module Shape

Example:

``` text
features/study-mission/
├── api/
├── components/
├── hooks/
├── schemas/
├── types/
├── routes/
└── tests/
```

Do not create generic global folders for every component if feature
ownership is clear.

------------------------------------------------------------------------

# 77. Shared UI Components

Good shared candidates:

``` text
Button
Input
Textarea
Select
Card
Badge
Dialog
Drawer
Tabs
Progress
Skeleton
EmptyState
ErrorState
SourceReferenceCard
StatusMessage
```

Avoid prematurely creating a huge internal component library.

------------------------------------------------------------------------

# 78. Activity Component Boundary

Each activity component should be purely
presentational/interaction-focused.

Example:

``` text
RetrievalActivity
```

receives:

-   question
-   options
-   state
-   submit callback
-   feedback state

It does not:

-   call AI
-   infer correctness
-   update evidence locally

------------------------------------------------------------------------

# 79. Mission Controller Hook

A feature-level hook may coordinate client behavior.

Example:

``` ts
useStudyMission(missionId)
```

Responsibilities:

-   Query mission
-   Submit activity
-   Connect stream
-   Refetch after completion
-   Handle conflict
-   Expose normalized UI states

This is client orchestration, not Learning Engine logic.

------------------------------------------------------------------------

# 80. Material Processing Hook

Example:

``` ts
useMaterialProcessing(materialId)
```

May use:

-   polling
-   backend events if later added

until state reaches:

``` text
READY
PARTIALLY_READY
FAILED
UNSUPPORTED
```

Polling frequency should back off for long processing jobs.

------------------------------------------------------------------------

# 81. Review Hook

Example:

``` ts
useReviews()
```

Returns backend-provided:

-   due records
-   reasons
-   priorities
-   availability

Frontend does not calculate review schedules.

------------------------------------------------------------------------

# 82. Progress Hook

Example:

``` ts
useTopicProgress(topicId)
```

Returns backend evidence summary.

Do not derive "mastery" locally.

------------------------------------------------------------------------

# 83. Schema Validation

Use Zod for:

-   Form input
-   Selected runtime API validation where useful

Do not duplicate every backend DTO schema manually if code generation
becomes justified later.

For v1, handwritten shared TypeScript types are acceptable.

------------------------------------------------------------------------

# 84. API Type Generation

OpenAPI-based TypeScript generation may be introduced if:

-   Endpoint count becomes large
-   DTO drift becomes frequent

It is not mandatory for initial v1 implementation.

If introduced, generated types should remain isolated from
presentation-specific view models.

------------------------------------------------------------------------

# 85. Loading/Error State Matrix

Every major feature should define:

``` text
INITIAL
LOADING
SUCCESS
EMPTY
ERROR
STALE/REFETCHING
```

Study Mission additionally needs:

``` text
WAITING_FOR_AI
STREAMING
EVALUATING
CONFLICT
```

------------------------------------------------------------------------

# 86. UI State Machine --- Study Mission

``` mermaid
stateDiagram-v2

    [*] --> LOADING
    LOADING --> READY
    LOADING --> ERROR

    READY --> SUBMITTING
    SUBMITTING --> EVALUATING
    EVALUATING --> READY
    EVALUATING --> STREAMING
    STREAMING --> READY

    READY --> PAUSED
    PAUSED --> READY

    READY --> COMPLETED
    READY --> CONFLICT
    CONFLICT --> LOADING

    ERROR --> LOADING
```

This is client interaction state, not the backend mission state machine.

------------------------------------------------------------------------

# 87. Study Mission Sequence

``` mermaid
sequenceDiagram
    actor Student
    participant UI as React
    participant API as Spring Boot
    participant SSE as Streaming Endpoint

    Student->>UI: Submit response
    UI->>API: POST activity response
    API-->>UI: evaluation/next action metadata

    alt stream required
        UI->>SSE: Connect
        SSE-->>UI: content chunks
        SSE-->>UI: complete event
        UI->>API: refetch mission state
    else no stream
        UI->>API: refetch/update mission state
    end

    UI-->>Student: Render next activity
```

------------------------------------------------------------------------

# 88. Upload Sequence

``` mermaid
sequenceDiagram
    actor Student
    participant UI as React
    participant API as Spring Boot

    Student->>UI: Select PDF
    UI->>UI: Validate basic type/size
    UI->>API: Upload multipart
    API-->>UI: Material + processing state

    loop Until terminal processing state
        UI->>API: Get material processing state
        API-->>UI: progress + stage
    end

    UI-->>Student: Ready / Partial / Failed
```

------------------------------------------------------------------------

# 89. Route Guarding

Authenticated application routes require session.

When session expires:

``` text
401
↓
session recovery / login
```

Preserve current route if safe for return after login.

Do not cache private route content after logout.

------------------------------------------------------------------------

# 90. Logout Behavior

On logout:

-   Clear TanStack Query cache
-   Clear sensitive in-memory state
-   Abort active streams
-   Clear user-scoped Zustand state
-   Navigate to login

Do not depend only on page refresh.

------------------------------------------------------------------------

# 91. Privacy in UI

Avoid displaying:

-   API provider keys
-   Internal prompt text
-   Hidden system instructions
-   Raw diagnostic model output
-   Cross-user material

Source content is shown only within authorized user context.

------------------------------------------------------------------------

# 92. Telemetry Boundary

Frontend telemetry may capture:

-   route
-   feature action
-   request duration
-   error code
-   rendering issue

Avoid capturing:

-   full student answers
-   uploaded source text
-   medical source content

unless explicitly required and privacy-reviewed.

------------------------------------------------------------------------

# 93. Frontend Error Correlation

When backend returns correlation ID:

``` text
Something went wrong.
Reference: ABC123
```

may be available for support.

Do not expose stack traces.

------------------------------------------------------------------------

# 94. Testing Strategy --- Unit

Unit test:

-   Utility functions
-   Mapping
-   Small hooks
-   state reducers
-   schema behavior

Avoid testing React internals.

------------------------------------------------------------------------

# 95. Testing Strategy --- Component

React Testing Library should verify:

-   Student can submit MCQ
-   Feedback renders
-   Source panel opens
-   Processing state displays
-   Review reason displays
-   Accessibility labels exist

Test behavior, not component implementation.

------------------------------------------------------------------------

# 96. Testing Strategy --- E2E

Playwright should cover critical journeys.

Examples:

``` text
Create Subject
Create Topic
Upload Material
Wait/Mock Processing
Start Study Mission
Submit Retrieval Answer
Receive Feedback
Continue
Complete Mission
Open Review
Resume Mission
```

------------------------------------------------------------------------

# 97. Streaming Tests

Test:

-   First chunk
-   Completion
-   Interruption
-   cancellation
-   duplicate completion protection
-   refetch after stream

------------------------------------------------------------------------

# 98. Accessibility Tests

Automated accessibility tooling may be used, but manual
keyboard/screen-reader checks remain necessary for:

-   Mission interactions
-   Source drawers
-   Image viewer
-   dialogs
-   live streaming status

------------------------------------------------------------------------

# 99. Visual Regression

Optional later.

Not required for first implementation unless UI instability becomes
costly.

------------------------------------------------------------------------

# 100. Frontend Anti-Patterns

Avoid:

## 100.1 Tool Dashboard UX

Every AI capability displayed as separate feature card.

## 100.2 Provider-Aware Components

``` text
GeminiExplanationComponent
```

No.

Use:

``` text
ExplanationActivity
```

## 100.3 Global State for Everything

Do not mirror all backend data in Zustand.

## 100.4 Frontend Mastery Calculation

Never calculate evidence or review state in React.

## 100.5 Raw AI Rendering

Do not take arbitrary markdown/provider output and assume it is a valid
activity contract.

## 100.6 Giant Sidebar

Navigation must not represent every feature.

## 100.7 Full PDF Processing in Browser

Source processing belongs to backend.

------------------------------------------------------------------------

# 101. UX Quality Rules

1.  One primary action per learning step where possible.
2.  Hide technical complexity.
3.  Do not expose internal AI terminology unnecessarily.
4.  Keep source provenance visible but unobtrusive.
5.  Use visuals when they improve learning.
6.  Avoid overwhelming the learner with simultaneous panels.
7.  Preserve mission continuity.
8.  Provide clear recovery when something fails.
9.  Do not equate completion with mastery.
10. Do not create gamification that competes with learning unless later
    validated.

------------------------------------------------------------------------

# 102. Frontend Performance Targets

Exact numeric budgets are deferred, but v1 should aim for:

-   Fast route transitions
-   Responsive answer submission state
-   Progressive streaming display
-   Lazy heavy viewers
-   Minimal unnecessary rerenders
-   Bounded list rendering
-   Efficient mobile experience

Performance should be measured on realistic student devices, not only
high-end developer machines.

------------------------------------------------------------------------

# 103. Backend Contract Dependence

Frontend implementation assumes backend returns:

-   typed activity payloads
-   stable error codes
-   source-reference metadata
-   material processing status
-   review reason
-   progress evidence labels
-   mission status
-   stream identifiers/events

If backend contracts change materially, update this architecture and API
contract intentionally.

------------------------------------------------------------------------

# 104. Locked v1 Frontend Decisions

The following are approved:

1.  React is a presentation/interaction layer, not the Learning Engine.
2.  Primary navigation remains compact.
3.  AI capabilities are surfaced inside guided Study Missions rather
    than separate tool pages.
4.  `/missions/:missionId` is the primary learning route.
5.  Explanation, retrieval, connection, application, feedback, and
    reflection use typed activity components.
6.  Frontend never parses provider output into educational meaning.
7.  TanStack Query owns server state.
8.  React local state is preferred for local UI state.
9.  Zustand is minimal and only for genuinely shared client-owned state.
10. URL state is used for navigable non-sensitive state.
11. React Hook Form + Zod handles form UX validation.
12. Backend remains authoritative for validation and educational state.
13. One centralized API client is used.
14. Session cookies are used; no JWT token storage in localStorage.
15. One centralized SSE abstraction handles AI streaming.
16. Partial stream output does not update learning evidence.
17. Source citations are learner-friendly and hide internal chunk IDs.
18. Supplemental generated knowledge is visually distinguishable when
    backend classifies it.
19. Original source visuals are preferred over generated replacements.
20. Large material structure is shown hierarchically and lazily.
21. Upload transfer progress and processing progress are separate.
22. PARTIALLY_READY material exposes limitations clearly.
23. Review reasons come from backend evidence.
24. Progress uses broad evidence states rather than fake percentages.
25. Mission resume preserves continuity.
26. Timer is visible but not treated as mastery.
27. Accessibility targets WCAG 2.2 AA-aligned behavior.
28. Streaming must be accessible and non-noisy for screen readers.
29. Source images support accessible zoom/pan behavior.
30. Optimistic UI is avoided for critical learning-state changes.
31. Stale mission conflicts trigger refetch/recovery.
32. Browser persistence is minimal and excludes authoritative learning
    data.
33. Frontend never calls Gemini or Ollama directly.
34. Provider identity is normally hidden from the student.
35. Heavy PDF/image viewers are lazy loaded.
36. Long structure trees use lazy expansion/virtualization where
    necessary.
37. Feature folders preserve ownership.
38. Shared UI components remain intentionally small.
39. Critical user journeys are covered by Playwright.
40. Frontend implementation must preserve the guided learning philosophy
    from Documents 00--19.

------------------------------------------------------------------------

# 105. Out of Scope

This document does not define:

-   Final visual design system
-   Exact colors/typography tokens
-   Exact screen mockups
-   Exact API payload schemas
-   Exact component code
-   Exact PDF viewer library
-   Exact icon library
-   Native mobile app architecture
-   Offline-first architecture
-   Gamification
-   Collaborative study
-   Faculty dashboards

These may be defined later as needed.

------------------------------------------------------------------------

# 106. Next Document

**21 - File Processing & Ingestion Architecture**

The next document should define:

-   Upload validation
-   File-size/page limits
-   PDF parsing
-   image extraction
-   OCR adapter
-   large-file batching
-   structure detection
-   table handling
-   transcript processing
-   video transcript boundary
-   processing-job pipeline
-   idempotency
-   retry
-   progress
-   storage lifecycle
-   processing security
-   activation rules
-   failure states

It should operationalize the large-material and RAG decisions from
Documents 13, 18, and 19.

------------------------------------------------------------------------

# 107. Revision History

  ---------------------------------------------------------------------------
  Version           Date              Author            Changes
  ----------------- ----------------- ----------------- ---------------------
  1.0.0             2026-08-24        Project           Initial finalized
                                      Hippocampus Team  Frontend Architecture
                                                        defining guided Study
                                                        Mission UX, compact
                                                        routing/navigation,
                                                        activity rendering,
                                                        source/visual
                                                        presentation,
                                                        upload/processing
                                                        flows, TanStack Query
                                                        state ownership,
                                                        minimal Zustand use,
                                                        SSE streaming,
                                                        accessibility,
                                                        responsive behavior,
                                                        and frontend testing
                                                        boundaries

  ---------------------------------------------------------------------------

------------------------------------------------------------------------

# 108. Approval

**Status:** Final

**Reviewed By:** Project Hippocampus Team

**Approved By:** Project Hippocampus Team
