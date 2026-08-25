# Dependency and Secret Finding Triage

## Purpose

This runbook defines the Project Hippocampus process for handling secret-scanning and dependency-vulnerability findings without exposing sensitive values or weakening security checks.

It applies to findings from:

- Gitleaks pull-request, push, scheduled, and manual scans;
- GitHub native secret scanning and push protection;
- Dependabot alerts and security updates;
- Dependency Review;
- package-manager audit output.

## Security controls and their roles

- The `quality` workflow checks pull requests and pushes for newly introduced secrets and dependency vulnerabilities.
- The `security-monitoring` workflow runs weekly and manually, validates the Gitleaks detector using an isolated runtime fixture, and scans fetched Git history.
- Dependabot alerts surface supported vulnerabilities that become known after code has merged.
- Dependabot security updates may open remediation pull requests when GitHub can calculate an update.
- Dependabot version updates perform scheduled maintenance; they are not equivalent to vulnerability alerts.
- Dependency Review remains the pull-request gate for introduced High-or-Critical vulnerable dependencies.
- `npm audit --audit-level=high` remains a supplemental frontend lockfile signal.

## Evidence safety

Never copy a raw secret into documentation, tracker evidence, issues, pull-request descriptions, comments, screenshots, workflow artifacts, or incident titles.

Record only the minimum safe metadata:

- finding or alert identifier;
- scanner or advisory source;
- rule, GHSA, or CVE identifier where applicable;
- component or package;
- path only when safe;
- severity and classification;
- status and remediation action;
- detection and remediation dates;
- reviewer and redacted evidence link.

Scanner output must remain redacted. Do not upload an unredacted Gitleaks report.

## Finding lifecycle

Use this lifecycle for every finding:

```text
Detected -> Verify -> Classify -> Remediate or Rotate -> Rescan -> Close
```

Do not suppress a finding before verification.

## Secret finding classification

Classify a secret finding as exactly one of:

1. **Real secret** — a credential or sensitive value that may grant access.
2. **False positive** — verified non-secret content that matches a scanner rule.
3. **Runtime test fixture** — an isolated synthetic value generated only during a test run.
4. **Legacy or historical finding** — a finding in Git history, whether or not it remains in the current tree.

## Real-secret response

If a real credential reaches source or Git history:

1. Revoke or rotate the credential immediately.
2. Remove it from the current source and replace its use with protected configuration.
3. Verify that application configuration, CI, logs, frontend bundles, errors, and artifacts no longer expose or depend on it.
4. Treat a committed credential as exposed even if the latest source line is deleted.
5. Decide with an external reviewer whether a coordinated Git history rewrite is warranted.
6. Rescan the current tree and full fetched history.
7. Record a minimal, redacted incident entry and remediation status.

Never allowlist a real secret. Deleting it from the latest commit is not equivalent to revoking or rotating it.

## False-positive and suppression policy

For a possible false positive:

1. Prove why the matched value cannot authenticate, authorize, decrypt, or otherwise grant access.
2. Prefer changing harmless source text so it no longer resembles a credential.
3. Record the scanner rule, safe path, classification, justification, reviewer, and rescan result.
4. Add a suppression only when the source cannot reasonably be changed and an external reviewer approves it.
5. Rescan before closing the finding.

If suppression is unavoidable, use only the exact Gitleaks finding fingerprint in a repository-root `.gitleaksignore` file. The first such file or entry requires explicit review.

Do not:

- disable a Gitleaks rule;
- add global path, regex, stop-word, or commit allowlists;
- add broad `.gitleaksignore` entries;
- use `gitleaks:allow` for the synthetic detector test;
- suppress a value merely because remediation is inconvenient.

## Runtime test fixtures

Secret-shaped test values must be assembled at runtime in a temporary directory, scanned only in that isolated directory, redacted in output, and removed on exit.

Do not commit a complete secret-shaped fixture or permanently allowlist a test fixture. The repository's detector self-test uses a distinct Gitleaks detection exit code so scanner errors cannot be mistaken for successful detection.

## Legacy and historical findings

For a historical finding:

1. Determine whether the value was real at any time.
2. If real or uncertain, revoke or rotate it before other remediation.
3. Remove any current use and inspect logs, artifacts, and configuration for continued exposure.
4. Decide history remediation separately with an external reviewer; do not rewrite shared history automatically.
5. Suppress only when the finding is proven false and the narrow fingerprint policy is satisfied.
6. Rescan and record the outcome without copying the matched value.

## Dependency vulnerability lifecycle

Classify each dependency finding as one of:

- exploitable or relevant;
- affected but constrained;
- transitive;
- false positive;
- not applicable.

Available actions are:

- upgrade or patch;
- replace or remove;
- apply a documented mitigation;
- narrowly dismiss or suppress with evidence;
- temporarily accept with an explicit reason, owner, application context, and review trigger.

Use the following severity policy:

- **Critical:** release-blocking when exploitable; temporary acceptance requires a formal reviewed security decision.
- **High:** review and disposition before release.
- **Moderate:** track and prioritize using exploitability and Hippocampus application context.
- **Low:** track and address through ordinary maintenance when appropriate.

Every remediation pull request must pass the existing `backend-quality`, `frontend-quality`, and `security` checks. Do not auto-merge dependency updates or bypass required checks.

For a transitive Maven finding, Dependabot alerts remain the vulnerability signal. `mvn dependency:tree` may help locate the dependency but is not a vulnerability scan.

For Docker Compose updates to `pgvector/pgvector`, review whether the matching image in `.github/workflows/quality.yml` must be updated in the same pull request so local and CI PostgreSQL versions do not drift.

## Scheduled monitoring failures

When the scheduled workflow fails:

```text
GitHub Actions failure
-> Human verifies and classifies the finding
-> Remediation or rotation
-> Reviewed pull request if repository changes are needed
-> Manual rescan
-> Close with redacted evidence
```

Do not disable the schedule, weaken the scanner, automatically create unsafe changes, or auto-merge a remediation.

## GitHub security settings

The following external repository settings must remain active where available without a paid feature:

- Dependency Graph;
- Dependabot alerts;
- Dependabot security updates;
- native secret scanning for the public repository;
- repository push protection.

Validate these settings manually because they are not fully represented by repository files. If a feature is unavailable, record the result and retain the existing Gitleaks control.

## Completion evidence

P0-13 completion requires:

- successful Dependabot ecosystem recognition and update checks;
- active required GitHub security settings;
- a successful manual `security-monitoring` workflow run on `main`;
- a successful isolated Gitleaks detector self-test with redacted output;
- preserved green `backend-quality`, `frontend-quality`, and `security` gates;
- reviewed documentation and redacted evidence recorded in the Implementation Tracker.

A Dependabot-generated pull request is not required for completion. If one is naturally generated, its target branch, required checks, lack of auto-merge, and update contents may be recorded as additional evidence.
