<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# HXP package v1 signed and v2 local-unsigned integrity, trust, and update rules

This document is normative alongside the manifest schema. It specifies rules that JSON Schema alone cannot express.

## Archive and integrity

An `.hxp` is a ZIP containing `manifest.json`, the declared `entry`, optional `assets/` and `locales/`, and (only for signed v1) `signature.ed25519`. Paths use `/`, are relative, NFC-normalized, have no empty components, `.`/`..`, duplicate names, symlinks, or compression/encryption tricks. The uncompressed size, file count, individual file size, and compression ratio are host-bounded before extraction.

`integrity.files` is the complete map of every regular archive file except `manifest.json` and (for signed v1 only) `signature.ed25519`; all keys are normalized archive paths and all values are lowercase SHA-256 of raw file bytes. It includes the entry module. Excluding `manifest.json` avoids an impossible self-referential digest; only the v1 detached signature authenticates the canonical manifest itself. `integrity.contentDigest` is lowercase SHA-256 of the UTF-8 bytes of RFC 8785 canonical JSON for `integrity.files`. The host recomputes both maps before module evaluation. For unsigned v2 no `signature.ed25519` entry is permitted, and any other regular file missing from the map or listed without an archive entry rejects the package.

For signed v1, `signature.ed25519` contains exactly a 64-byte Ed25519 detached signature. Its message is:

```text
ASCII("tsuyomi-hxp-v1\0") || UTF8(RFC8785(manifest.json)) || 0x00 || ASCII(integrity.contentDigest)
```

The signed v1 manifest's signature metadata is therefore authenticated but the detached signature file itself is not recursively hashed. Unsupported algorithm, duplicate file, wrong digest, invalid archive path, or signature failure rejects installation before QuickJS sees package bytes.

## Explicit local-unsigned v2

`hxp-manifest-v2.schema.json` is a separate strict versioned shape: `manifestVersion: 2` and required `signing: { "algorithm": "none" }`, with no `keyId`, `signatureFile`, publisher public key or detached signature archive entry. The v1 schema remains strict and does not accept this shape. SHA-256 content integrity detects mismatches, not a maliciously replaced archive: the complete archive SHA-256 is an exact byte identity, **not authenticated publisher identity**. No key may be generated, fabricated, inferred from the digest, or entrusted merely because the package is locally readable.

Only a user-selected local file import can prepare v2. Repository downloads, catalog authorization, subscription transport, release signing/publishing and automatic updates require a valid signed v1 HXP with its independently authenticated publisher. Reject v2 before any repository approval path regardless of catalog metadata or archive digest. An unsigned local file requires visible, informed, explicit execution consent for this exact `(sourceId, complete archive SHA-256, capabilities)`; a new archive or changed capabilities cannot inherit that grant. A changed archive hash needs a new approval even when the version and manifest fields are identical. There is no automatic unsigned update, publisher rotation or repository migration authority.

The approval must disclose that code is unsigned, the exact package identity and capabilities, and that source-scoped existing credentials/session data for the same source ID may remain accessible; it must not promise isolation or a safe publisher. The host retains any earlier signed publisher pin/history when uninstalling. A different identity may take that source ID only after the signed package is uninstalled **and** the user explicitly approves the source identity transition; an active other publisher cannot be replaced by approving unsigned bytes. Uninstalling an extension does not entail deleting books, progress, credentials or the old publisher history. A prior exact consent does not silently authorize future bytes.

## Trust and revocation

For signed v1, a publisher key is trusted only through current or previously authenticated root-signed repository metadata, a user-added publisher key, or one explicit signed local-import confirmation. Signed trust is recorded by public-key fingerprint and key ID, not a mutable display name. Unsigned v2 has neither. A production repository root is explicit application configuration; no fixture or local key is a production fallback.

User-added signed v1 key material establishes only signature verification. Executing a user-added signed package additionally requires one durable explicit grant bound to its exact `(sourceId, publisherKeyId, publisher fingerprint, complete archive SHA-256)`. An exact grant neither trusts another package under the same key nor authorizes another source. The host preserves signed publisher identity history across uninstall; a missing active archive alone cannot launder a same-source publisher takeover without the separate explicit transition approval.

A root-signed catalog revocation for a signed publisher fingerprint or complete repository HXP archive SHA-256 disables affected installed signed packages and rejects new signed installs/updates. Current metadata is required to authorize a repository installation. Expired cached metadata may retain already authenticated identity and revocation state for installed signed packages, but cannot authorize a new download. Catalog authority does not sign, authorize or silently revoke an unrelated unsigned local v2 file.

Repository updates must retain their publisher key ID and strictly increase the installed semantic version. The sole key-change exception is an exact built-in-official-root-authorized `legacyMigration` entry whose prior publisher fingerprint and archive digest match the active package, followed by a separate explicit migration confirmation. A bare key-ID change, fixture-key cross-signature or user-added repository root is not migration authority. Local import behavior remains distinct.

## Updates, rollback, and grants

Repository updates must have a strictly higher semantic version for the same extension ID. A lower/equal repository package is rejected as rollback/replay even if correctly signed. A local lower-version import requires an explicit downgrade confirmation and is marked local-only; it cannot silently replace a package whose key is revoked or whose host API range is incompatible. For unsigned v2, a new exact archive always requires fresh consent regardless of semantic version.

For signed updates, the host compares normalized capability sets before activation. Adding an origin, cookie origin/mode, WebView origin/enabling, remote-library read/write operation, or storage quota requires a new explicit grant. Tightening a limit or removing a capability does not waive the separate exact-archive consent for an unsigned local v2 file. Resource limit increases remain host-capped and are shown in the review UI. If the user declines a needed grant, the previous active version remains active.

## Conformance cases

Conformance covers same-key updates, capability expansion, publisher and complete-archive-digest revocation, repository rollback/equivocation, exact catalog-to-manifest binding, valid and invalid root-authorized legacy migration, expiry at approval, and invalid capability origin subsets. Repository envelope vectors and rules are normative in [`tsuyomi-repository-v1.md`](tsuyomi-repository-v1.md).
