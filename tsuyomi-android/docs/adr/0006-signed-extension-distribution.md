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
