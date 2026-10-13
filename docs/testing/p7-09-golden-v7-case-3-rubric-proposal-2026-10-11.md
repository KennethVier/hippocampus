# Proposed Golden v7 Case 3 rubric correction — 2026-10-11

Status: **PROJECT OWNER APPROVED 2026-10-14; TARGETED MEDICAL APPROVE STATEMENT
SUPPLIED, PROVENANCE VERIFICATION PENDING; V7 OVERLAY IMPLEMENTED TEST-ONLY.**
Golden v6 and historical reports remain unchanged. The owner's approval was supplied in the
2026-10-14 P7-09 instruction; no individual owner name or medical credential is
inferred from that instruction.

## Conflict

`P7-09-PARTIAL-001` asks why radial nerve injury causes wrist drop. The
student answers, “It denervates the wrist extensors.” This independently
demonstrates wrist-extensor denervation, but does not independently identify
the radial nerve. The inherited Golden v4
`requiredCorrectConceptAlternatives` also accepts “radial nerve supplies the
wrist extensors” as credited Case 3 output. That alternative can reward a fact
borrowed from the question. It conflicts with ADR-0012's student-attribution
rule and prevents clean semantic approval of the exact case as currently
assembled in Golden v6.

## Prospective correction

Create a **Golden dataset v7** derived from frozen v6, with an explicit Case 3
rubric overlay. Keep the original question, student answer, authorized source,
PARTIAL-only allowed classification, and the requirement to recognize actual
wrist-extensor denervation. Remove only the inherited alternative that treats
radial-nerve identification/supply as demonstrated credit. Require the
structured missing concepts and feedback to preserve both the unexpressed
radial-nerve identity/supply relation and the downstream loss of wrist
extension/wrist-drop link; do not manufacture either as student knowledge.
Case 5 remains PARTIAL only when wrist-extensor paralysis is independently
verified; the wrong median-nerve attribution stays a misconception and the
correct radial-nerve relation remains missing and corrected in feedback.
Keep all other case expectations unless a separately reviewed correction is
documented. This is a rubric correction, not a weaker medical standard or a
test-only matcher bypass.

Version the new overlay and loader path explicitly, retain exact source and
rubric hashes, and include the selected dataset version in qualification
identity. `Golden v7` is a **dataset** version; it is unrelated to
`RESPONSE_EVALUATION_V7` (a prompt candidate). Production prompt selection
remains V6. New live output and independent human semantic review under
ADR-0011 are required before Golden v7 qualification can pass. Prior Golden
v5/v6 reports, original human judgments, and V9 evidence remain immutable and
keep their original identities and verdicts.

## Approval record and remaining gate

**Project owner decision — APPROVED, 2026-10-14.** The prospective Golden
dataset v7 Case 3 criterion awards `PARTIAL` only for independently demonstrated
wrist-extensor denervation; does not credit radial-nerve identification absent
from the student response; lists and explains the missing radial-nerve relation
and wrist-drop mechanism; and invents no misconception. The existing Case 5
`PARTIAL` criterion is preserved: credit independently demonstrated
wrist-extensor paralysis, record and correct the median-nerve attribution as a
misconception, and retain the radial-nerve relation as missing. This approval
does not approve the dataset overlay's implementation or assert medical sign-off.

**Targeted medical statement — Cases 3 and 5 marked APPROVE, attribution
unverified.** The user supplied reviewer reference `MED-REV-NEURO-2026-088`
and attestation reference `AUTH-SIG-SHA256-7B93E10C-20261014` on 2026-10-14.
No authentic, attributable approval record or credential/independence
verification was available in the workspace. These references are preserved
as supplied identifiers, not proof of reviewer identity, qualification,
signature validity, or independence. Provenance verification remains **PENDING**.

The historical Golden v5 reviewer record
identifies a first-year medical student, has incomplete qualification/signature
attestation, and reviews a prior output and rubric. Its Case 3 PASS credited
radial-nerve innervation as demonstrated knowledge, contrary to the proposed
no-borrowed-credit criterion. Earlier retained reviews and the V7/V8 worksheets
are historical output reviews; available attribution does not establish that
a medically qualified reviewer explicitly approved these exact prospective
Case 3 and Case 5 interpretations. The 2026-10-10 Case 5 record is a product
owner rubric decision, not a medical credential or sign-off.

The additive v7 overlay and focused deterministic regressions have now been
implemented. Their existence does not verify the supplied medical references.
Use the targeted [Case 3/Case 5 medical sign-off packet](p7-09-golden-v7-case-3-case-5-medical-signoff-2026-10-14.md)
to reconcile the authentic approval record and its scope before any live
qualification claim. Do not edit v2/v4/v5/v6 resources or rewrite historical
reports.
