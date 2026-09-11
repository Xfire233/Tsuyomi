<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# ADR 0006: Signed repository plus local `.hxp` import and explicit capability escalation

- Status: Accepted
- Date: 2026-08-08

## Problem

Tsuyomi must install and update executable source extensions without silently expanding their authority or trusting mutable network content.

## Constraints

- Packages may come from an official repository or local files.
- Package contents, publisher identity, compatibility, and requested capabilities must be validated before evaluation.
- Users need a recovery path when a repository is unavailable.

## Decision

Every `.hxp` package is content-addressed and signed with Ed25519. The host ships an official Tsuyomi repository root public key; repository metadata is signed under that trust root. Installation verifies archive paths, declared files, hashes, signature, publisher key, host API compatibility, and revocation state before module evaluation.

A same-key update with no capability expansion may follow the normal update flow. Added origins, cookie scope, controlled-WebView access, remote-library read/write operations, file capability, storage quota, or other privileged capability pauses installation until the user explicitly approves the new grant.

Local import is supported. Users may explicitly add a third-party publisher public key or approve a single local package without granting publisher-wide trust. The confirmation displays the key identity and requested capabilities. Trusting one package or publisher never silently trusts unrelated keys.

The 2026-09-11 implementation authorization selects one official repository, `Chachaanteng/tsuyomi-extensions`, with a signed static catalog and independently versioned signed HXP assets. The host consumes a versioned catalog, never a GitHub file listing or an inferred latest release. Catalog entries bind source ID, version, Host API range, exact download size/hash, publisher identity, corresponding source revision and license. Trust, expiry, monotonic catalog revision and signed revocation are checked before new repository installation; catalog unavailability alone does not disable installed packages. Removing a listing is not revocation. Repository downloads converge on the existing installer and explicit capability-grant flow; local imports retain their independent confirmation and downgrade semantics. The first scope supports discovery, search, details, installation, update checks and user-triggered updates only; there is no automatic installation or third-party repository management.

Public deterministic fixture keys are never production repository roots or valid authorities for production-key rotation. Test-to-production migration must be authorized by the new trusted root, bind the exact old package digest and publisher fingerprint, retain the source ID and user-owned state, and require visible user confirmation. Production publisher-key changes otherwise require the existing authenticated rotation policy. Source publication, production-key provisioning and canonical-device deployment require the explicit scope recorded in the owning Phase/checkpoint; they never follow from fixture success or an unreviewed license exception.

The 2026-09-11 follow-up selects an automation-first operating direction. Contributors must not need production signing keys or catalog-publication access to submit source changes. Routine validation, packaging and catalog renewal should be automated, with untrusted contribution execution separated from privileged signing/publication. The user accepts maintainer-reviewed release changes merged into the protected main branch as the authorization point for routine automated signing and publication, without a second per-release approval; catalog renewal is also automated. Documentation-only changes do not trigger package releases. Initial production-key configuration and first pipeline activation require explicit user authorization recorded in the owning Phase/checkpoint; that initial authorization does not grant unrelated deployment or human-review approval.

The accepted operating policy is implemented with independently versioned HXP assets in GitHub Releases and the signed index-v1.json on the repository branch. Source contributions stay on main; contributors do not edit generated catalogs or hold production signing keys. Release assets bind an exact source commit and are never overwritten under the same version. Publication verifies uploaded assets before atomically advancing the catalog; retries must reuse only byte-identical assets. Release and renewal share one writer and a monotonically increasing authenticated catalog sequence. Routine renewal retains package bindings and revocations, uses a 14-day validity window and renews when at most seven days remain. Untrusted builds and tests run without production keys; signing uses a separately pinned, reviewed tool revision on a clean protected runner. Automated distribution does not authorize automatic client installation or remove local HXP import. Production-key configuration and first pipeline activation remain gated as above.

The user selected single-maintainer protection on 2026-09-11: main requires a pull request and strict successful contributor CI, with explicit maintainer merge rather than automatic merge. Formal approving-review count is zero and mandatory CODEOWNER approval is disabled because the sole maintainer cannot approve their own pull request; CODEOWNERS remains ownership metadata. Direct pushes, force-pushes and deletion remain prohibited, administrators are covered, and signing remains restricted to the main-only protected Environment. This supersedes the initial mandatory second-account approval configuration; it is a standing operating policy, not a hidden one-off bypass.

## Rejected alternatives

- Repository transport security without package signatures: rejected because repository or CDN compromise would become code execution.
- Approve all future capabilities at first install: rejected because it hides meaningful privilege expansion.
- Disable local import: rejected because it prevents offline recovery and independent development.

## Migration impact

Built-in Flutter source code becomes separately versioned signed extensions. Extension uninstall never deletes host-owned books or progress; affected records become dormant until a compatible source is installed.

## Verification

- Altered, unsigned, revoked, incompatible, path-traversal, and hash-mismatched packages fail before evaluation.
- Capability expansion always produces a distinct approval transition.
- Rollback and key-rotation fixtures are covered by conformance tests before repository launch.
