---
ADR: ADR-0017
Title: Immutable Objective Origin for Generated Study-Mission Artifacts
Status: ACCEPTED
Date: 2026-10-17
Decision Owner: Project decision owner (explicit approval recorded 2026-10-17)
Categories: DATA, DOMAIN, BACKEND, EDUCATION, SECURITY, TESTING
Affected Documents: 06, 07, 11, 15, 18, 19, 26, 27, IMPLEMENTATION-TRACKER, ADR-0006, ADR-0016
Supersedes: None
Superseded By: None
---

# Context

P7-16 must classify the *selected* reused response target after a saved UNGRADED attempt. The application currently verifies that the selected prior LearningActivity belongs to the mission and has the requested objective, but a reused GeneratedArtifact can predate that activity. The activity's objective therefore does not prove the objective for which its content was generated.

The checked-in V20 schema gives `generated_artifacts` an owner and AI/content metadata but no objective or mission origin. `learning_activities.generated_artifact_id` is nullable and non-unique; its objective has a same-mission foreign key, but the artifact foreign key has no objective or owner relationship. V22 adds artifact source references, not an origin. `GeneratedArtifactRepository` and its JDBC implementation expose no origin. `MaterializeLearningActivityUseCase` resolves the action objective and checks a prior activity's objective; `PersistMaterializedActivity` creates an artifact and activity in one transaction, but its reuse path checks owner, validation, grounding, optional reusable flag, and sources without comparing an artifact origin. Existing activity links, source references, model payloads, and timestamps cannot establish original generation ownership. Reuse can make a later activity appear to be the origin.

Documents 11, 18 §59, and 19 require objective/context compatible reuse, with the Learning Engine owning pedagogical choice and the application owning artifact authorization. ADR-0006 requires exact prior activity identity but says it is insufficient by itself. Accepted ADR-0016 requires server-owned target facts and fail-closed unknown classification; its original P7-16 scope assumed no schema change. This newly identified persistence gap made that assumption untenable for objective-origin verification. Documents 06, 07, 15, 20, and 26 protect the continuation experience, presentation exceptions, and immutable learning evidence. Before this decision, no accepted document or ADR defined an authoritative artifact-generation objective or a policy for unbound legacy artifacts.

The ADR directory and Document 27 index ended at ADR-0016 when this decision was prepared on 2026-10-17; ADR-0017 was the next available identifier. Document 27 requires explicit approval before changing a significant persistence/domain boundary.

# Decision

**Accepted 2026-10-17:** Add a learning-owned, insert-only `generated_artifact_objective_origins` binding. It records one `generated_artifact_id`, authoritative `learning_objective_id`, originating `study_mission_id`, and `user_id` for each newly persisted Study Mission generated artifact. The objective ID comes from the server-resolved `LearningObjective` selected for generation, never from AI output, artifact payload, a client field, concept text, or a later activity. Mission and owner IDs come from the locked, authenticated, owned StudyMission. No origin may be inferred from an existing or later activity.

The artifact insert, origin insert, source links, new activity, and mission update must commit or roll back together in the existing short `PersistMaterializedActivity` transaction, after external AI work and after reacquiring the owned mission lock and checking its snapshot. Before insertion, require a non-null activity objective that belongs to the locked mission and matches the action objective. While its artifact exists, an origin record cannot be reassigned, updated, or deleted. A conflict on insertion fails the transaction; it is not an upsert or a rebinding opportunity. The artifact repository must expose the stored origin through a server-owned read/capability so both target classification and final reuse admission use the same authoritative fact.

For `RETRY`, `REDUCE_DIFFICULTY`, and `REUSE_VALIDATED_CONTENT` when an artifact would be carried forward, require the selected prior activity's objective, the requested objective, and the artifact's stored origin objective to be the **same objective ID**. Require the origin mission ID to equal the current mission ID and the binding owner to equal both artifact and authenticated mission owner. Cross-mission artifact reuse is ineligible, even where objective text or concept keys match. The existing exact-activity, validated/reusable as applicable, task/prompt/evaluation, grounding, source provenance, frozen scope, and material-lifecycle checks remain necessary. The origin check adds authorization evidence; it does not decide pedagogical compatibility or grading eligibility.

Missing, duplicated, malformed, owner-mismatched, or objective/mission-conflicting origin is **unknown/ineligible**, never assumed compatible. Reject reuse before any new activity/artifact/attempt/evidence write or provider call. In P7-16 continuation, unknown origin must not be classified as known assessment unavailability or silently treated as assessable; use the existing safe unknown/limitation or sanitized failure path according to the already approved decision hierarchy. Recheck origin and authorization in the final write transaction to close read/write races. No client flag can override this result.

# Rationale

The shared `generated_artifacts` row currently carries AI/content provenance and can exist without a Study Mission objective. A separate narrow binding lets mission-generated artifacts have a mandatory origin at their creation path while legacy and any non-mission artifacts remain unbound. It avoids adding a nullable learning-owned field to the shared artifact model and avoids interpreting `NULL` as a valid objective. One immutable row per artifact is enough; no general provenance framework, compatibility service, or historical reconstruction is needed. The binding supplies identity and scope only; existing Learning Engine and application responsibilities remain intact.

# Alternatives Considered

## Nullable immutable `generated_artifacts.origin_learning_objective_id`

One column and one foreign key are superficially smaller. It would place mission-specific learning identity in the shared artifact row, still need mission/owner consistency and an immutability mechanism, and conflate legacy/unbound with non-mission uses. A nullable mission and owner composite on the same row could enforce scope but increases coupling there. This is viable if future evidence shows all artifacts are exclusively Study Mission artifacts, but is not the smallest cohesive boundary under the current shared model.

## Dedicated immutable binding table

Recommended. It needs a migration, a repository capability, and an atomic second insert, but isolates the new invariant and distinguishes an absent legacy binding. Its artifact-key primary key prevents multiple origins; composite foreign keys can enforce owner and mission relationships.

## Existing activity links, artifact payload/source provenance, or first activity by time

Insufficient. Activity rows can reuse an artifact and can have different objective IDs. A timestamp or earliest row is not a generation receipt. Payload, model, and source IDs are not server-owned objective identity. A uniqueness constraint on `learning_activities.generated_artifact_id` would break approved reuse.

# Consequences

New mission artifacts carry a durable, queryable origin; reuse cannot launder content into another objective or mission. Additional persistence/read checks and a migration are required. Same-objective reuse remains possible inside the originating mission when all existing checks pass. Legitimate cross-mission reuse, if later desired, requires a separate policy decision with provenance and frozen-source semantics; this ADR does not introduce it.

Approved historical presentation-only interpretation and completion remain available for an owned, valid existing activity. Existing activity presentation is not itself reuse authorization. Do not rewrite or delete historical activities, attempts, source records, completion records, or immutable submission receipts to satisfy this decision. Do not manufacture correctness, failure, mastery, or Phase 8 learning evidence from missing origin.

# Security and Privacy Impact

Origin is server-owned authorization metadata. The read and final write paths must bind artifact, objective, mission, and user before evaluating content, and return sanitized failures for foreign or inconsistent references. The new table stores UUIDs only, no student answer, prompt, or source body. Preserve existing source scope and revocation checks. Database constraints and application checks are complementary; an artifact UUID, prior activity UUID, or `reusable` flag alone grants no access. Independent security review must include cross-user, cross-objective, cross-mission, concurrency, and direct persistence bypass cases.

# Educational Impact

The Learning Engine still selects the exact compatible prior activity. Origin prevents an artifact made for one objective from being used as evidence or a response target for another. A missing origin may reduce reuse for legacy content, but must not turn uncertainty into a grade or claim a student failed. Preserve learner-controlled Pause, explicit Finish where independently selected, and legitimate presentation progression under ADR-0016.

# Database / Migration Impact

Implementation requires a forward-only Flyway migration; do not edit V20 or V22. Use `generated_artifact_id` as primary key and non-null objective, mission, and user IDs. Add the necessary composite unique keys on referenced artifact `(id, user_id)` and mission `(id, user_id)`; reference those pairs and existing objective `(id, study_mission_id)` so SQL rejects cross-owner and cross-mission bindings. Use restrictive foreign-key deletion behavior. Enforce no reassignment, UPDATE, or DELETE of an origin while its artifact exists through the database write boundary, with no update/upsert API; ordinary unique/FK constraints alone do not make a row immutable. Any future retention-driven deletion must follow an explicitly authorized artifact lifecycle and cannot silently detach an origin from a surviving artifact. Review legitimate retention/deletion obligations before fixing the exact enforcement mechanism. No new infrastructure or dependency is proposed.

The migration leaves existing artifacts unbound. Do not backfill from their current/earliest activity, source, prompt, or timestamps. Database-level mandatory binding for *all* artifacts is inappropriate while the table is shared; require the binding in the mission generation use case and verify atomicity. Existing rows and receipts remain unchanged.

# Legacy Artifact Policy

An artifact already present when the migration is applied, or otherwise lacking a verified origin row, is ineligible for **future artifact reuse** in P7-16 regardless of `reusable = true`, apparent single-activity history, or matching objective text. No inferred or speculative backfill is authorized. Existing owned activities may still be displayed and their approved historical presentation-only exception applied through existing contract and source checks. Already saved attempts/receipts remain immutable. Where current continuation cannot establish a response target's authoritative origin, fail closed as unknown and communicate or recover through the existing safe path; do not erase the saved attempt or silently regenerate the same target. A later separately approved remediation could evaluate specific evidence, but is outside this ADR.

# Testing Requirements

After separate implementation authorization, focused tests must prove: atomic artifact/origin/source/activity commit and rollback; one immutable origin per artifact; SQL owner/mission/objective FK rejection and update/delete rejection; correct same-objective same-mission reuse; rejection of cross-objective and cross-mission chains even when the selected later activity appears compatible; foreign owner, missing/legacy, and corrupted/conflicting origin fail closed before writes or provider calls; final transaction revalidation under concurrent mission/source changes. Cover both RETRY/REDUCE_DIFFICULTY and explicit REUSE_VALIDATED_CONTENT. Preserve grounded-scope, validated/reusable, presentation-only, no-repeat UNGRADED, legacy/opt-in API, no-false-evidence, and byte-stable receipt regressions. PostgreSQL integration is required for constraints and atomicity; user/CI validation, external implementation review, and independent security review remain separate gates. No tests are run for this proposal.

# Required Documentation Changes

Acceptance was synchronized in Documents 11 (target facts and reuse), 18 §§34 and 59 (model/schema and eligibility, consistent with §§30 and 35), and 19 (generation/reuse transaction and authorization). ADR-0016's no-schema assumption and target-resolution/testing references were amended without changing its educational rule. Document 26 Phase 7, Document 27's index/summary, and the P7-16 tracker were aligned and versioned under Document 27. Documents 06, 07, 15, 20 and accepted ADR-0006 were reviewed; their existing continuation, presentation and exact-activity rules need no normative edit. Do not mark P7-16 complete or treat this decision as implementation authorization.

# Explicit Human Approval Gate

**Decision accepted 2026-10-17.** The project decision owner explicitly approved ADR-0017 in the user instruction titled “HIPPOCAMPUS — ADR-0017 APPROVED” in this conversation, including immutable artifact-origin binding, same-objective/same-mission reuse, fail-closed legacy treatment, a forward-only Flyway migration, and retention-aware immutability. This records that owner's approval only; it does not claim separate education, architecture/API, security, or data-owner approvals. Implementation authorization, required testing, external implementation review, independent security review, and rollout gates remain separate. No schema/code/test work or tracker completion follows from this acceptance alone.
