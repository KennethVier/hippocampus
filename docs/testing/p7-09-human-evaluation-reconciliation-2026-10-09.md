# P7-09 human evaluation reconciliation — 2026-10-09

Documentation/evidence reconciliation only. **Seven PASS, two FAIL** human
decisions are preserved. This record is not independent adjudication, medical
certification, Golden qualification approval, general review or security review.
P7-09 remains **Ready for Review**, not Done.

## Reviewer and scope

- Pseudonymous reviewer: `P7-09-HR-01`.
- Qualification supplied by the human coordinator: first-year medical student.
- Evaluation date supplied by the coordinator: 2026-10-09 (Asia/Manila).
- Accepted human-review source for this exercise: the supplied answered independent
  nine-case Golden v5 packet. The coordinator retains the original privately;
  it is not copied or linked here. Identity/signature records are managed separately.
- The packet checks independent completion before prior assessments/discussion.
  This is reviewer-reported independence, not independently verified identity.
  Qualification attestation is unchecked; qualification, signature and date/time
  fields are uncompleted. No identity verification or completed signature is claimed.
- This review concerns nine synthetic radial-nerve/wrist-drop assessments, not the
  application as a whole. A first-year student's feedback is not medical-expert
  correctness authority. No clinician/instructor review request is checked.
- PASS cases have no written rationale. Case 7's classification checklist is
  unchecked, but its explicit FAIL and classification rationale are unambiguous;
  the checklist does not replace the decision.

## Exact evidence identity

Ignored local evidence root (repository-relative):
`backend/ai-golden-evaluation/retained/d28c8b78-7caa-4cac-a15b-9bd1fa4c4c5a/`.
Artifact paths below are relative to that root unless stated otherwise. These
private/local artifacts are not published with this anonymized report.

- Run: `d28c8b78-7caa-4cac-a15b-9bd1fa4c4c5a`.
- Provider/model: GEMINI / `gemini-3.1-pro-preview` (available provider identifier;
  no immutable model-version guarantee is inferred).
- Dataset/rubric: Golden v5, resolved from v2 base cases, v4 rubric alternatives
  and the v5 input-contract overlay. See the [v5 convention](../../backend/src/test/resources/ai/golden/v5/README.md).
- Qualification fingerprint:
  `cd95c795b64a7306e5d571511a876e813febc0331fcde38a1e2f57afe92bd345`.
- Exact captured outputs: `gemini.frozen.json` (ignored local evidence),
  `cases[].validatedStructuredOutput`, identified by case ID and output identity.
  Retained qualification inputs identify prompt `RESPONSE_EVALUATION_V6` and
  effective model/configuration/resource/code inputs. This reconciliation does
  not assert those inputs match a new repository revision or current deployment.

File hashes read on 2026-10-09 match the private packet's Appendix A:

| Artifact | SHA-256 |
| --- | --- |
| `gemini.frozen.json` | `fbc27367df9424afb407d2dcfd6ba89c03e0ef02fe6cc1503ab7a02be80baaa7` |
| `backend/src/test/resources/ai/golden/v2/response-evaluation-cases.json` | `74bc2cfcf07d0f0d2ec1360872786c455af75be2632e65dd692812d30e57e2db` |
| `backend/src/test/resources/ai/golden/v4/response-evaluation-rubric-overrides.json` | `567d91ccf0c2102e7c98c81dd2edfa3c9961d0307949c53e5616609cc9fdabb0` |
| `backend/src/test/resources/ai/golden/v5/response-evaluation-input-contract.json` | `dfa6aaae72f48619bebd757a6990c18e2b6fb32955216cb92aff70efb045071e` |

All nine output identities below were independently recomputed from captured
validated output using sorted JSON property names, compact UTF-8 JSON and SHA-256,
matching `GoldenAiQualification.outputIdentity` for these outputs. They match
both the frozen report and private packet. This is artifact inspection, not a
test execution or a semantic/qualification pass.

## All nine original human decisions

The decision column evaluates Gemini's assessment; the classification column is
Gemini's evaluation of the learner answer. They are different judgments.

| Case | Case ID | Gemini classification | Human decision | Exact captured output identity |
| --- | --- | --- | --- | --- |
| 1 | `P7-09-CORRECT-001` | CORRECT | PASS | `ec2ff78010563a9a83c00c9e24fff7a07860595e3e48396241bb086c4eaee774` |
| 2 | `P7-09-PARAPHRASE-001` | CORRECT | PASS | `637229b84ee6ef580cfdc4a339a32c5d2fb68ed74d6e46248d83e32a004b9ff9` |
| 3 | `P7-09-PARTIAL-001` | PARTIAL | PASS | `9c142e44a968344538d4fcd933585654913aec65ffd125b2fb7a9c490366ab0e` |
| 4 | `P7-09-WRONG-REASONING-001` | PARTIAL | **FAIL** | `a39db845e3b1ce69e5f8bc12bec6eff633072e9a836af18ff260d92ef549e41c` |
| 5 | `P7-09-INCORRECT-001` | INCORRECT | PASS | `bb81c49eb8bdf64c06d3fe634551ef6c2b555aae4baa7a94fee2812a9d059a67` |
| 6 | `P7-09-OFFTOPIC-001` | INCORRECT | PASS | `cfd2bb6202e04ac64bdcd534efee67c573be76887399b21694adb3cbe414f680` |
| 7 | `P7-09-UNCERTAIN-001` | PARTIAL | **FAIL** | `a71190d6ea96fd7ac19cfa8f9511648da168e5d6e03600357dcab7850a5213e0` |
| 8 | `P7-09-EMPTY-001` | INCORRECT | PASS | `0e1a02dc930c9ae86770a2402e0791234afdff1fbd862814b0b3519ff1206df5` |
| 9 | `P7-09-INJECTION-001` | INCORRECT | PASS | `d617cdb3574713acbaf3414684df2deeea791c1f907ec2a493ba92cab1dfc1d5` |

## Cases 4 and 7: rubric comparison and unresolved policy

### Case 4 — Wrong reasoning

**Original human FAIL retained.** The reviewer accepts that radial nerve injury
and wrist drop were identified, but considers the incorrect flexor-activation
mechanism more important and would classify the learner answer INCORRECT.
The supplied revised FAIL is consistent with that rationale. No earlier version
of this student's decision is reconstructed or substituted.

Golden v5 permits **PARTIAL** and forbids CORRECT/INCORRECT for this case. The
captured output credits the stated injury/outcome, identifies the nerve supply
and functional-loss relationships in `missingConcepts`, records the actually
expressed flexor-overpowering claim in `misconceptions`, and corrects that mechanism
in feedback. Its PARTIAL classification complies with the existing rubric; the
reviewer's preference does not establish a classification defect in Gemini.
Literal denervation is not an additional v5 requirement.

**Unresolved grading-policy disagreement:** how much weight a wrong mechanism
should carry despite a correct conclusion. Independent human adjudication must
preserve the FAIL and compare the learner/source evidence with the approved rubric.
This record neither resolves that disagreement nor changes scoring policy.

### Case 7 — Uncertain answer

**Original human FAIL retained.** The reviewer considers the missing nerve name
and incomplete mechanism sufficient to prefer INCORRECT over Gemini's PARTIAL.
The explicit written rationale preserves the disagreement despite the unchecked
classification checklist.

Golden v5 permits **PARTIAL or UNCERTAIN**, and forbids CORRECT/INCORRECT. The
learner explicitly describes a nerve supplying muscles that lift the wrist.
Gemini credits that constituent, lists the radial nerve and loss-of-extension
relationship as gaps, supplies explanatory feedback and invents no misconception.
PARTIAL complies with the existing rubric; stricter grading is an educational
policy preference, not a confirmed model defect.

**Unresolved grading-policy disagreement:** whether incomplete but demonstrated
knowledge should be graded INCORRECT. Documents 11 §18 and 15 §§31–34 support
nuanced feedback and partial-credit evaluation; neither requires either case to
be INCORRECT. No higher-authority contradiction is established by these preferences.
A proposed scoring/rubric change needs a separate approved packet and, if a
significant policy decision is needed, Document 27 governance before implementation.

## Case 6 — Source-reference finding

The source supplied is `00000000-0000-0000-0000-000000000306`; the exact captured
`sourceReferences` is `[]`. Human PASS is preserved. Gemini rejects the off-topic
answer, credits nothing, invents no misconception and requests a relevant retry.

The actual captured contract uses `RESPONSE_EVALUATION_V6`: references must be
exact supplied chunk UUIDs, and when no source is used it says to return `[]`.
`ProviderStructuredOutputSchema` requires the array field but has no nonempty
minimum. `AiSourceReferenceValidator.validate` explicitly accepts an empty list;
nonempty references must pass UUID, prompt inclusion, provenance and authorization
checks. `ResponseEvaluationAggregator` carries the list into the validated output.
The case's Golden rubric has no nonempty-reference requirement. Empty references
therefore do not demonstrate a structural contract or Golden qualification failure.

The missing-concept list nevertheless states source-supported relationships.
The empty list does not prove that no source was used, that every factual claim is
grounded, or that citation completeness is ideal. Independent semantic/release
review should assess that distinction against the current contract. Document 15's
requirements that citations resolve and that fabricated references fail do not
themselves impose a nonempty list on every output. No fabricated/foreign reference
is present here. A stronger completeness rule requires separately governed prompt,
contract and qualification work; this reconciliation makes no such change.

## Plain-language recommendation

The coordinator supplies the reviewer's recommendation for simpler, student-friendly
feedback. It is recorded separately from the packet's case verdicts.

Technical example: “Radial nerve injury denervates the wrist extensors.”

Preferred explanation: “Radial nerve injury causes the muscles that lift the wrist
to lose their normal nerve supply.” A concise alternative is “loss of nerve supply
to the wrist extensor muscles”; “loss of nerve supply to the wrist” loses the
anatomical target. This is a wording example, not a new source fact to insert into
the frozen synthetic v5 evidence, which does not require literal denervation.

Educational guideline:

- Explain the mechanism in accessible language.
- Preserve correct anatomical structures and causal relationships.
- Introduce medical terminology after explaining its meaning when useful.
- Avoid unnecessary jargon without removing essential medical concepts.
- Continue identifying correct concepts, missing concepts and actual misconceptions independently.

Existing approved requirements already cover the intent: Document 15 §26 requires
learner-level language, mechanism preservation, avoidance of unnecessary jargon
and excessive simplification; §34 requires specific corrective feedback without
overwhelming the student. Document 11 §18 makes feedback proportional to need, and
§44 calls for simple explanations of important student-facing decisions.
The example is an improvement recommendation, not an automatic Golden v5 failure.
No production prompt, scoring, fixture, routing or frozen output is changed.
Any implementation requires separate scoped approval and relevant requalification
under ADR-0011 if output-affecting inputs change.

## Existing evidence and remaining handoff gates

- The frozen report records evidence collection PASS and nine contract PASS
  results, with semantic review/overall qualification PENDING at capture time.
- `human-reviews.json` (ignored local evidence) and
  `gemini.frozen.json.reviewed-2fe07cb1-441e-4b85-983f-f1d024103343.json` preserve
  nine earlier 2026-10-08 PASS decisions. They are historical evidence, not this
  student's decisions or independent medical credentials. The new two FAILs must
  accompany any handoff; the earlier nine PASSs cannot erase them.
- Ignored local evidence:
  `curated-regressions-4f5c236e-3951-4135-9929-d26c4afa428b/release-readiness.md`
  reports 184 passing deterministic tests across seven suites with retained command,
  counts and hashes. This documents historical scope; no tests were rerun here.
- Ignored historical closeout: `closeout-659d6aa/acceptance-handoff.md` records supplied CI,
  general and scoped security decisions for head `659d6aa03b4f1217e9f50efb6d1e92bbc497cd46`.
  Those supplied historical decisions are not independent review of this new
  reconciliation and do not update the tracker's unrecorded review gates.
- ADR-0011 requires attributable independent human review and adjudication of
  material disagreement. This pseudonymous record supplies traceable decisions;
  the coordinator must retain the private identity mapping and supply genuine
  designation/attestation and any required release-owner acceptance. Uncompleted
  attestation/signature fields cannot be declared complete by this executor.
- **Qualification blocker:** unresolved required-case FAILs in Cases 4 and 7
  preclude a Golden PASS under ADR-0011. If applied as unresolved semantic FAILs,
  qualification is FAIL; existing frozen/reviewed PENDING artifacts remain unchanged.
  No supported finalizer input or acceptance record is manufactured here.
- No additional student or clinician evaluation is requested for this exercise.
  Document 15 §51 and ADR-0011 require medically qualified review when disputed or
  high-impact medical claims require expert validation. The present disagreements
  concern grading weight, not a newly identified disputed medical claim. The
  independent reviewer must determine applicability; if expert validation is
  required, report that exact requirement as a blocker rather than infer credentials.
- Remaining completion work: independent general implementation/evidence review
  of the actual diff and current applicable user/CI evidence; separate independent
  `hippocampus-security-vulnerability-review` after general review is clean; genuine
  adjudication/qualification/release acceptance; then human-authorized publication
  and tracker completion evidence. No review/security PASS is asserted here.

## User/CI checks for this documentation change

No application/test rerun or new Gemini call is necessary solely for this
documentation correction. From repository root:

```powershell
Test-Path -LiteralPath 'docs/testing/p7-09-human-evaluation-reconciliation-2026-10-09.md'
Select-String -LiteralPath 'docs/IMPLEMENTATION-TRACKER.md' -SimpleMatch '(testing/p7-09-human-evaluation-reconciliation-2026-10-09.md)'
git check-ignore -- docs/testing/p7-09-human-evaluation-reconciliation-2026-10-09.md
git check-ignore -- backend/ai-golden-evaluation/retained/d28c8b78-7caa-4cac-a15b-9bd1fa4c4c5a/human-evaluation-reconciliation-2026-10-09.md
git status --short -- docs/IMPLEMENTATION-TRACKER.md docs/testing/p7-09-human-evaluation-reconciliation-2026-10-09.md
git diff -- docs/IMPLEMENTATION-TRACKER.md
git diff --no-index -- /dev/null docs/testing/p7-09-human-evaluation-reconciliation-2026-10-09.md
git diff --check
```

Require the file-existence check to return True and the tracker search to find
the P7-09 notes link. The first ignore check should print nothing (exit 1 means
not ignored); the retained-artifact check should print its path (exit 0 means
still ignored). The no-index diff shows the new report before staging and returns
exit 1 when it contains differences. The new report is eligible for normal Git
tracking but remains untracked until the human stages it; no staging or publication
is performed by this correction.

After the human stages only the two authorized documentation files, confirm
tracked inclusion and inspect the complete publication diff:

```powershell
git ls-files --error-unmatch -- docs/testing/p7-09-human-evaluation-reconciliation-2026-10-09.md
git diff --cached --name-only
git diff --cached -- docs/IMPLEMENTATION-TRACKER.md docs/testing/p7-09-human-evaluation-reconciliation-2026-10-09.md
git diff --cached --check
```

Require the staged publication set to contain only the anonymized report and
P7-09 tracker edit. Check all nine decisions/identities against the privately held
original, the 7/2 total, both FAIL rationales, unresolved adjudication, qualification
limitations, hashes and plain-language recommendation. Inspect the report for any
real reviewer name, initials, institution, contact information, signature or private
packet content; only the pseudonymous reviewer identifier belongs here. References
to ignored evidence are non-clickable paths relative to the explicit evidence root.
The v5 convention link resolves from this report to tracked repository documentation.

Confirm `backend/.gitignore`, frozen outputs/resources and existing private
review/acceptance files are unchanged. P7-09 remains Ready for Review. Independent
review should assess whether current implementation evidence requires additional
validation; historical test/CI success alone does not certify a new revision or
satisfy unresolved gates.
