<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# HXP package v1 integrity, trust, and update rules

This document is normative alongside the manifest schema. It specifies rules that JSON Schema alone cannot express.

## Archive and integrity

An `.hxp` is a ZIP containing `manifest.json`, the declared `entry`, optional `assets/` and `locales/`, and `signature.ed25519`. Paths use `/`, are relative, NFC-normalized, have no empty components, `.`/`..`, duplicate names, symlinks, or compression/encryption tricks. The uncompressed size, file count, individual file size, and compression ratio are host-bounded before extraction.

`integrity.files` is the complete map of every regular archive file except `manifest.json` and `signature.ed25519`; all keys are normalized archive paths and all values are lowercase SHA-256 of raw file bytes. It includes the entry module. Excluding `manifest.json` avoids an impossible self-referential digest; the detached signature authenticates the canonical manifest itself. `integrity.contentDigest` is lowercase SHA-256 of the UTF-8 bytes of RFC 8785 canonical JSON for `integrity.files`. The host recomputes both maps before module evaluation.

`signature.ed25519` contains exactly a 64-byte Ed25519 detached signature. Its message is:

```text
ASCII("tsuyomi-hxp-v1\0") || UTF8(RFC8785(manifest.json)) || 0x00 || ASCII(integrity.contentDigest)
```

The manifest's signature metadata is therefore signed but the detached signature file itself is not recursively hashed. Unsupported algorithm, duplicate file, wrong digest, invalid archive path, or signature failure rejects installation before QuickJS sees package bytes.

## Trust and revocation

A publisher key is trusted only through current or previously authenticated root-signed repository metadata, a user-added publisher key, or one explicit local-import confirmation. Trust is recorded by public-key fingerprint and key ID, not a mutable display name. A production repository root is explicit application configuration; no fixture or local key is a production fallback.

A root-signed catalog revocation for a publisher fingerprint or complete HXP archive SHA-256 disables affected installed packages and rejects new installs/updates. Current metadata is required to authorize an installation. Expired cached metadata may retain already authenticated identity and revocation state for installed packages, but cannot authorize a new download.

Repository updates must retain their publisher key ID and strictly increase the installed semantic version. The sole key-change exception is an exact root-authorized `legacyMigration` entry whose prior publisher fingerprint and archive digest match the active package, followed by a separate explicit migration confirmation. A bare key-ID change and a fixture-key cross-signature are not migration authority. Local import behavior remains distinct.

## Updates, rollback, and grants

Repository updates must have a strictly higher semantic version for the same extension ID. A lower/equal repository package is rejected as rollback/replay even if correctly signed. A local lower-version import requires an explicit downgrade confirmation and is marked local-only; it cannot silently replace a package whose key is revoked or whose host API range is incompatible.

The host compares normalized capability sets before activating an update. Adding an origin, cookie origin/mode, WebView origin/enabling, remote-library read/write operation, or storage quota requires a new explicit grant. Tightening a limit or removing a capability does not. Resource limit increases remain host-capped and are shown in the review UI. If the user declines a needed grant, the previous active version remains active.

## Conformance cases

Conformance covers same-key updates, capability expansion, publisher and complete-archive-digest revocation, repository rollback/equivocation, exact catalog-to-manifest binding, valid and invalid root-authorized legacy migration, expiry at approval, and invalid capability origin subsets. Repository envelope vectors and rules are normative in [`tsuyomi-repository-v1.md`](tsuyomi-repository-v1.md).
