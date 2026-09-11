<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# ADR 0006: Signed repository plus local `.hxp` import and explicit capability escalation

- Status: Accepted
- Date: 2026-08-08

## Problem

Tsuyomi must install and update executable source extensions without silently expanding their authority or trusting mutable network content.

## Constraints

- Packages may come from the official repository, local files, or explicitly subscribed third-party repositories; all paths preserve cryptographic verification and explicit execution approval.
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

### Third-party subscriptions and safe uninstall — authorized 2026-09-11

The user authorized implementation and isolated-device testing of link-based third-party subscriptions, explicit non-official installation consent and safe uninstall/reinstall. This supersedes the earlier future-only scope, not production deployment authorization.

Subscriptions use an HTTPS catalog link with an explicit repository identity, root key ID and Ed25519 public key in its fragment. The fragment is not sent to the server. Confirmation displays normalized address and fingerprint; root changes cannot silently replace a previously pinned identity. The existing signed catalog format remains authoritative; protocol owns exact link parsing. Root approval permits authenticated discovery, not execution approval or official status. User-added roots cannot authorize another publisher to take over an installed source through legacy migration.

Before non-official package installation, explicitly acknowledge in-process code risk and approve the verified exact package, publisher and capabilities. Local unknown publishers require explicit public-key input and complete archive verification before this approval. Persist package-specific grants and source/publisher identity pins, not a generic trust-all acknowledgement. Existing package updates still require their exact activation approval, with separate capability expansion, downgrade and authenticated migration checks. Invalid signatures, incompatibility and revocations cannot be overridden. ADR0013's in-process QuickJS/JNI compromise risk must be disclosed accurately; this is not a secure process sandbox or legal transfer-of-liability guarantee.

Subscriptions have independent root-bound cache/high-water state; disable/removal stops discovery without uninstalling packages or erasing authenticated revocations, publisher pins or antirollback history. Re-adding cannot reset security history. Installed package verification remains possible after subscription removal. Colliding source IDs remain distinct repository offers and cannot silently substitute publishers. User-added keys cannot shadow built-in official/test identities or revoke their packages merely by naming their fingerprints/digests; execution checks carry publisher provenance. Conflicting user-added key material fails closed rather than choosing an authority. No automatic installation or refresh-driven code activation.

Uninstall is serialized with installation/updates and source activation. Confirmed removal invalidates the active source's requests, session and navigation state, removes its archive and marks its host availability dormant, without deleting books/progress or website data and without issuing website writes. Retain source/publisher identity and trust/revocation tombstones so uninstall cannot launder a publisher takeover. Missing archives on restoration are reconciled dormant, including interrupted uninstall. Reinstall of the same trusted source identity restores availability and host data association; a failed or cancelled attempt preserves the prior state. Credentials remain host-owned and may only be used under the retained publisher/capability identity checks.

Repository transport may recover once from an interrupted response or connection reset during an idempotent HTTPS GET. Both attempts and their redirects share the original total deadline; discard partial bytes and reacquire redirects from the original catalog-bound URL. Exhausted deadline, cancellation, TLS/certificate/hostname failure, invalid redirects, HTTP error status, size violations and cryptographic/binding failures never acquire automatic retries. Recovery cannot relax HTTPS, byte limits, signatures, revocation or explicit package approval. A failed download remains distinguishable from verified-byte integrity failure and local storage failure at the host UI boundary; explicit user retry rechecks the selected repository/source rather than reusing stale authority.

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
