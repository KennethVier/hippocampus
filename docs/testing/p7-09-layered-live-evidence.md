# P7-09 layered live evidence

Revision 1 — 2026-10-08. Executable artifact guide for accepted
[ADR-0011](../adr/ADR-0011-layered-p7-09-response-evaluation-qualification.md).
P7-09 remains not Done. This guide does not grant provider evaluation approval.

The test-only `GoldenAiLiveEvaluationRunner` collects evidence through the
production orchestrator, validator, source-reference validator, aggregator and
bounded repair. Production behavior is unchanged. For response-evaluation cases,
Maven asserts `evidenceCollectionStatus`, not semantic or qualification approval.
Objective configuration/filter/provider/contract/evidence/report failures still
fail Maven. Other Golden task types retain their previous semantic assertion.

| Field | Meaning |
| --- | --- |
| `evidenceCollectionStatus` | PASS/FAIL for automated evidence collection |
| `contractStatus` | PASS/FAIL through production validation; provider failure has no accepted output and is FAIL |
| `semanticMatcherStatus` | PASS/FAIL diagnostic rules; NOT_RUN when no matcher result exists |
| `semanticReviewStatus` | PASS/FAIL/PENDING from matching attributable human evidence; defaults PENDING |
| case `qualificationStatus` | Case contribution: FAIL on objective/semantic failure, PENDING without current review, PASS only for a reviewed contract-valid case |
| report `qualificationStatus` | Overall PASS only with all nine current v5 cases reviewed PASS plus current curated-regression and applicable-release-gate acceptance evidence |

`passed` keeps its legacy meaning: production-valid output with all deterministic
semantic rules passing. It may be false while evidence collection succeeds.
`failedRules` is retained. Matcher PASS never implies human PASS. Human FAIL
does not fail evidence collection by itself but makes qualification FAIL.
A targeted run cannot qualify the complete nine-case set.

## Collect and retain

Run from `backend`, using a provisioned `GEMINI_API_KEY` without embedding it in
commands or artifacts, and the intended model already set in
`HIPPOCAMPUS_GEMINI_SMOKE_MODEL`. An alias is not
an immutable provider version guarantee. No sampling/reliability thresholds
are introduced by this tooling.

```powershell
$env:HIPPOCAMPUS_LIVE_AI_GOLDEN = 'true'
$env:HIPPOCAMPUS_LIVE_AI_GOLDEN_PROVIDER = 'gemini'
$env:HIPPOCAMPUS_LIVE_AI_GOLDEN_TASK = 'response-evaluation'
$env:HIPPOCAMPUS_LIVE_AI_GOLDEN_CASE = 'P7-09-WRONG-REASONING-001'
Remove-Item Env:HIPPOCAMPUS_LIVE_AI_GOLDEN_REVIEW_FILE -ErrorAction SilentlyContinue
Remove-Item Env:HIPPOCAMPUS_LIVE_AI_GOLDEN_REVIEW_REPORT -ErrorAction SilentlyContinue
.\mvnw.cmd '-Dtest=GoldenAiLiveEvaluationRunner#runsP7GoldenEvaluationWhenExplicitlyEnabled' test
```

For all nine cases, remove the case filter and run the same command:

```powershell
Remove-Item Env:HIPPOCAMPUS_LIVE_AI_GOLDEN_CASE -ErrorAction SilentlyContinue
.\mvnw.cmd '-Dtest=GoldenAiLiveEvaluationRunner#runsP7GoldenEvaluationWhenExplicitlyEnabled' test
Get-Content -Raw .\target\ai-golden-evaluation\gemini.json
```

Reports retain the compatible latest `gemini.json` / `ollama-cloud.json` filename
and a separate UUID filename per run. They include run ID, provider/model,
qualification identity, validated outputs, bounded diagnostics, matcher rules,
layer statuses, and supplied review/acceptance records. Telemetry absence means
unavailable; latency is retained where provided. Unrestricted raw provider
payloads and secrets are not retained. Copy the report and subsequent reviews to
a durable, appropriately access-controlled qualification artifact before cleanup.
Retain failures and reruns; do not cherry-pick outputs or routinely rerun until green.

## Human review artifact

Review the exact retained validated outputs against learner responses, expected
concepts/answers, authorized synthetic source context, grounding mode and the
loader-resolved v5 rubric. Follow ADR-0011's independent human reviewer and
medical/adjudication requirements. Do not substitute the matcher for that review.

Create a JSON file with this shape. Copy `qualificationIdentity` and each
`outputIdentity` **exactly** from the retained report. The example below is a
shape description, not accepted review evidence:

```json
{
  "cases": [
    {
      "caseId": "P7-09-WRONG-REASONING-001",
      "qualificationIdentity": "COPY THE FULL IDENTITY OBJECT FROM REPORT",
      "outputIdentity": "COPY THE CASE OUTPUT IDENTITY",
      "contractStatus": "PASS",
      "semanticReviewStatus": "PASS",
      "reviewer": "designated independent human identifier",
      "reviewDate": "2026-10-08",
      "rationale": "Record field-specific medical, source and learner-grounding findings",
      "artifactReference": "durable reference to exact reviewed run",
      "priorReviews": [],
      "adjudication": null
    }
  ],
  "acceptance": null
}
```

Use FAIL for demonstrated violations and PENDING for unresolved findings. Omitted
case reviews are PENDING. Prior materially disagreeing review records must be
retained; list their durable references in `priorReviews` and record the designated
independent adjudicator's resolution in `adjudication`. Nonempty prior reviews
require a nonblank adjudication record. Tooling checks attribution and identity;
it cannot certify a human's independence or medical competence.

Optional overall `acceptance` is an object with `qualificationIdentity`,
`curatedRegressionStatus`, `curatedRegressionArtifact`, `releaseGateStatus`,
`reviewer`, `reviewDate`, `rationale`, and `artifactReference`. The two statuses
must both be PASS for overall PASS. Supply actual retained deterministic Layer 2
test evidence and owner acceptance of applicable ADR-0011/Document 15 release
gates, including resolution of material sampling/benchmark questions. Missing
acceptance is PENDING. Current FAIL evidence is FAIL. This record does not replace
independent implementation/security review or the tracker completion gates.

Apply reviews offline to the retained report, without provider invocation:

```powershell
$env:HIPPOCAMPUS_LIVE_AI_GOLDEN_REVIEW_REPORT = 'C:\path\to\retained-run.json'
$env:HIPPOCAMPUS_LIVE_AI_GOLDEN_REVIEW_FILE = 'C:\path\to\human-reviews.json'
.\mvnw.cmd '-Dtest=GoldenAiLiveEvaluationRunner#reviewsRetainedEvidenceWhenExplicitlyRequested' test
```

This writes a separate `retained-run.json.reviewed-<UUID>.json`; it never overwrites
the reviewed original. Unknown/duplicate cases, malformed or unattributed review
records and incomplete evidence fail closed. Stale retained configuration requires
new collection. Live-supplied stale human evidence is retained but current review
becomes PENDING. A changed exact validated output also invalidates its review.

## Identity and routing

`P7-09-layered-v1` uses SHA-256 over explicitly ordered non-secret inputs: provider,
configured model, V6/repair/contract identifiers, route/fallback, budgets and
generation/grounding descriptions; hashes of repository AI Java sources (including
prompts, schema, aggregation, validation, repair, routing and provider settings),
backend pom, runner/review/loader source, and the v2/v4/v5 response fixture resources.
This intentionally invalidates conservatively on source changes. Run from the
repository source checkout; missing identity files fail evidence generation.
The fingerprint does not hash rendered prompts, unrestricted learner content or
credentials. Canonically ordered JSON of the already retained validated output
provides the separate exact output identity. Provider aliases and transitive
dependency resolution retain their ordinary reproducibility limitations.

No runtime code consumes these artifacts. No status populates or authorizes
`evaluation-approved-tasks`. Complete layered qualification PASS is necessary
evidence for the explicit ADR-0008 deployment approval transition, with applicable
release/review gates; evidence/contract/matcher/Maven PASS alone is insufficient.
Provider Router integration and tracker completion remain outside this task.
