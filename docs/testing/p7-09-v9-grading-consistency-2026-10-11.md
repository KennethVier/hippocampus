# P7-09 V9 grading-consistency candidate — 2026-10-11

Golden v6 live run `ee5511fc-0891-44ac-b6c9-355b3c4f0e67` collected all nine
outputs but failed Case 5 semantic acceptance. For “Median nerve injury
paralyzes the wrist extensors,” Gemini V8 returned INCORRECT with
`correctConcepts: []`, although its feedback recognized the learner's stated
wrist-extensor paralysis. The approved Golden v6 classification is PARTIAL with
credit confined to that functional component and an explicit median-to-radial
nerve correction. Collection success is not qualification or semantic PASS.

`RESPONSE_EVALUATION_V9` is a test-only candidate derived from frozen V8. Its
instructions distinguish an explicitly stated, source-supported functional
component from a false attribution: credit the component only, retain the
wrong cause in `demonstratedMisconceptions`, and identify the missing correct
cause or explanation in `missingComponents`. A pronoun referring to the nerve
named in the question does not establish that the student identified that nerve;
Case 3 can credit only the stated wrist-extensor denervation. An answer with
only a wrong attribution receives no invented correct component. Empty,
irrelevant, ambiguous, and injected input remain subject to the existing
student-claim, source, and output-contract boundaries.

The Golden v6 dataset and historical reports/reviews remain unchanged. V6, V7,
and V8 prompt contents and snapshots remain frozen. Production and test-runner
defaults remain `RESPONSE_EVALUATION_V6` and Golden v5 respectively. V9 requires
explicit test-runner selection and a distinct qualification identity; earlier
V8 live calls and reviews cannot qualify V9. Complete ADR-0011
qualification, independent human semantic review/adjudication, applicable
release acceptance, general review, and security review remain outstanding.
P7-09 remains Ready for Review; no Golden PASS or Done is asserted.

Initial agent-run focused Maven stopped before compilation because the Spring
Boot 4.1.1 parent POM was absent from the offline cache. Initial repository
backend validation stopped at `backend-clean-verify` because the execution
environment blocked the Maven wrapper download. Subsequent user-verified
validation passed: focused Maven 21 tests, 0 failures, 0 errors, 0 skipped;
repository-root `node scripts/validation/validate.mjs backend` reported
`backend-clean-verify` PASS, `git-diff-check` PASS, and overall PASS. These
results do not constitute live V9 qualification.

## Final Golden v6 + V9 qualification attempt

The original, unmodified report is retained at
[gemini-v6-0aab7267-e8b8-4c55-b3d5-bdc193d44295.json](evidence/p7-09/gemini-v6-0aab7267-e8b8-4c55-b3d5-bdc193d44295.json),
copied from `backend/target/ai-golden-evaluation/` before cleanup. Its SHA-256 is
`21146FBAFF6C1F54A5B22FA0D59152BCA67727D5FB8F212F0F7486EE3614CEA3`
for both source and retained copy. Run ID:
`f44dd5df-4413-4ba7-8a71-c65af2d1fb14`; dataset: Golden v6;
explicit test-only prompt: `RESPONSE_EVALUATION_V9`; provider/model: Gemini
`gemini-3.1-pro-preview`. The report retains the full qualification identity,
case/output identities, structured outputs, and diagnostics.

Evidence collection and all nine production contracts passed. Seven of nine
deterministic semantic matchers passed. Case 4
(`P7-09-WRONG-REASONING-001`) failed the feedback wording matcher even though
the feedback says the injury eliminates wrist extension; this appears to be a
matcher wording false negative and still needs independent human review. Its
duplicated missing concepts also remain for review. Case 5
(`P7-09-INCORRECT-001`) is a genuine grading failure: the output is `INCORRECT`
with `correctConcepts: []`, while Golden v6 requires `PARTIAL` credit for the
stated wrist-extensor paralysis component. The median-nerve misconception was
recorded, but the component received no credit. Case 3
(`P7-09-PARTIAL-001`) credits radial-nerve supply in `correctConcepts` and
requires review for possible overcrediting of a nerve identification supplied
only by the question. These findings do not amend the rubric or replace the
original reviewer evidence.

The report records overall `qualificationStatus: PENDING` because no attributable
human review was supplied. The observed Case 5 grading failure blocks approval
of this V9 attempt regardless of its nine contract passes; Golden qualification
has **not passed**. Independent review of Cases 3 and 4, resolution of Case 5,
the original reviewer disagreement/adjudication, release-owner acceptance, and
general/security review remain outstanding. Do not select this V9 attempt for
evaluation-approved production routing or mark P7-09 Done.

## Phase 7 planning disposition

Independent implementation and security reviews for already implemented P7
items, review of retained V9 output, medical adjudication of disputed cases,
and P7-14 test/evidence planning can proceed. P7-14 is the next `Not Started`
Phase 7 tracker item, but its `All P7` dependency and AI Golden checks prevent
the M3 gate from passing while P7-09 qualification is blocked. Phase 8
implementation remains downstream of Phase 7. Any proposal to remove or defer
P7-09 qualification from P7-14, declare M3 despite this failure, or start Phase
8 implementation before the Phase 7 gate requires an explicit Document 27
decision and corresponding roadmap/tracker reconciliation. No such change is
approved by this evidence note.
