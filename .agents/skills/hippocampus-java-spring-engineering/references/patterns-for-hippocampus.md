# Recommended Patterns — Selective Use

## Application Use Case
For explicit user/system operations; keeps orchestration out of controllers.

## Policy / Strategy
For deterministic learning/review/routing rules with explicit inputs and outputs.

## Port + Adapter
For real replaceable boundaries: AI provider, retrieval, object storage, OCR.

## Repository
For persistence/query abstraction; do not expose Spring Data repositories to domain code.

## Projector
For deterministic projection of immutable EvidenceEvents into current LearningEvidence.

## Explicit State Machine / Transition Policy
For StudyMission, ReviewRecord, and Material processing where invalid transitions must be rejected.

## Typed Result / Sealed Hierarchy
For finite action/result sets when exhaustive handling improves correctness.

## Avoid
- generic base controllers/repositories;
- giant `Service` classes;
- provider-specific logic leaking upward;
- `shared` as a convenience dumping ground;
- giant JPA entity graphs;
- JSON maps replacing core relational/domain modeling;
- premature microservices.
