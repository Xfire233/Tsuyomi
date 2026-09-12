<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Tsuyomi signed extension repository catalog v1

This document is normative alongside `tsuyomi-repository-v1.schema.json`. It defines the root-signed, read-only catalog from which a host may offer an extension package. It does not authorize publication, formal releases, or a production signing key.

## Envelope and signature

A catalog is a UTF-8 JSON object with exactly these envelope fields:

```json
{
  "format": "tsuyomi-repository",
  "version": 1,
  "keyId": "repository-root-key-id",
  "signed": { "...": "catalog metadata" },
  "signature": "base64-ed25519-signature"
}
```

The envelope, every nested object, and every package object reject unknown fields and duplicate JSON keys. `signature` is exactly 64 raw Ed25519 bytes encoded as base64. The signature message is:

```text
ASCII("tsuyomi-repository-v1\0") || UTF8(RFC8785(signed))
```

`keyId` must identify the configured repository root. The host verifies the signature against that explicit root; neither a user-added publisher key nor a development fixture key is a fallback root.

## Signed metadata

`signed` contains `repositoryId`, a positive JavaScript-safe integer `sequence`, UTC `issuedAt` and `expiresAt`, `publishers`, `packages`, and `revocations`.

- A catalog must be issued no more than five minutes in the future and live for no more than 30 days.
- A host keeps a durable highest accepted sequence and canonical digest of `signed`. A lower sequence is rollback; an equal sequence with a different digest is equivocation. Both are rejected. An equal identical body may refresh the cache, but never extends the signed expiry.
- The catalog transport is bounded to 1 MiB. At most 32 publishers and 512 packages are permitted.
- Metadata past `expiresAt` is retained only as already-authenticated publisher identity and revocation state for installed packages. It never authorizes a new download or installation.

A publisher has a unique `keyId`, base64 raw-32-byte `publicKey`, and lowercase SHA-256 `fingerprint` of that raw key. A package has a unique `id`; its version is SemVer; its source revision is a lowercase 40-character Git commit; and its download digest is lowercase SHA-256 of the complete HXP archive. `sourceUrl` and `downloadUrl` are HTTPS URLs without userinfo or fragments. Package size is 1 through 16 MiB.

The catalog binds a download to its source ID, version, archive size and digest, publisher key and fingerprint, and exact host API interval. A verified HXP whose manifest differs from any of those bindings is not installable from the catalog.

## Revocation and migration

`revocations.publisherFingerprints` and `revocations.packageDigests` are root-signed, unique, bounded lists. A valid current or previously authenticated catalog revocation disables affected installed packages and rejects new packages. A missing or stale catalog alone does not disable an otherwise valid installed package.

An explicitly configured official root's authenticated revocation remains effective even when the affected publisher is no longer listed. Hosts must propagate that authority through composed resolvers; filtering only by a currently resolvable publisher must not revive an already-revoked active session.

Repository packages must strictly increase the installed semantic version. Equal versions, rollbacks, and any repository downgrade override are rejected. A publisher key change is rejected unless the root-signed target package has `legacyMigration` whose `fromPublisherFingerprint` and `fromPackageSha256` exactly match the active archive. The host still requires an explicit, separate migration confirmation at approval. This migration mechanism is not a publisher cross-signature and does not change local-import rules.

## Transport and cache

Production fetchers make bounded HTTPS GET requests with one finite monotonic deadline spanning connection, redirects, and body reads; each connect/read timeout is capped to the remaining deadline. They disable automatic redirects, use a small controlled redirect limit, attach no request cookies or source browser session, and disconnect on every exit. Each redirect is validated as an HTTPS URL without credentials or a fragment. The host checks the declared package size before and while reading, then checks the exact size and SHA-256 before HXP verification.

The durable cache first fsyncs an authenticated recovery envelope, then appends and fsyncs a repository-ID/sequence/digest high-water journal before atomically replacing the verified-envelope state file. Android hosts also fsync the containing directory after metadata changes. If a crash leaves state behind the floor, the host restores trust (including revocations) only from the recovery envelope bound exactly to that floor; otherwise it fails closed. The journal is capped and atomically compacted to its newest record before another state replacement. A failed fetch, parse, signature, expiry, rollback, package transfer, HXP verification, binding, or approval check must leave the previously active package unchanged.

## Fixtures and conformance

`fixtures/repository/valid-catalog.json` and its matching `fixture-root-key.json` are deterministic injected test data only. They are not a publication channel and do not represent an official index, release, or production root. Conformance covers schema shape, duplicate identities, expiry and sequence policy, key/digest revocation, digest and manifest binding, rollback/equivocation rejection, and explicit root-authorized legacy migration.

## User-added subscription bootstrap

A user-added repository is discovered only through one bounded HTTPS link. The existing
`tsuyomi-repository` v1 catalog envelope and schema are unchanged; the fragment is bootstrap
metadata and is removed before every network fetch:

```text
https://publisher.example/extensions/index-v1.json#repositoryId=org.example.extensions&keyId=example-root-key&publicKey=<canonical-base64-raw-ed25519-key>
```

The link is at most 4,512 ASCII characters. Its fragment has exactly the ordered fields shown,
with no percent encoding, duplicate fields, unknown fields or alternate base64 encoding.
`repositoryId` uses the catalog source-ID grammar, `keyId` uses the catalog key-ID grammar, and
`publicKey` is exactly 32 raw Ed25519 bytes in canonical standard base64. Hosts parse and display
the root fingerprint locally; parsing performs no network I/O. Only the HTTPS portion before `#`
is passed to the existing bounded catalog transport.

The user-visible confirmation makes the exact `(repositoryId, indexUrl, keyId, root fingerprint)`
durable in caller-provided no-backup storage. A repository ID, index URL, or root key may not be
rebound to another identity, including after removal. Disable and removal retain the authenticated
catalog cache, high-water floor and signed revocations; re-adding the identical root reuses them.
A removed or disabled root may continue to supply previously authenticated key identity and
revocations for an installed archive, but it cannot be selected to fetch or authorize a new
catalog package.

Roots, publishers and executable-package approval are separate authorities. A link root is always
`USER_ADDED`: publishers in its valid catalog are `USER_ADDED`, never official, even if their
names or key IDs resemble official values. A built-in official identity takes precedence over a
built-in test identity, and either takes precedence over user-added declarations for that key ID.
User-added material or revocations cannot shadow those pinned identities. Within the selected trust
tier, different raw keys for one ID fail closed. Identical user-added material may be shared by a
local grant and declaring repositories; their scoped revocations apply to that declared identity.
Scoped checks preserve official root authority recursively; legacy unscoped consumers fail closed. A
user-added root cannot carry `legacyMigration`;
only the built-in official root may authorize that exact exception. A package still requires its
own valid HXP signature, catalog byte binding, compatibility and revocation checks.

For a user-added publisher, signing key material permits verification but not execution. A host
persists an explicit exact grant for `(sourceId, publisherKeyId, publisher fingerprint, archive
SHA-256)` after visible package consent. The grant does not authorize another archive, source or
publisher. Hosts preserve the source publisher pin across uninstall; a different publisher for the
same source is rejected unless the built-in official root's separately authenticated exact
`legacyMigration` is approved and activated. A local HXP reveals no public key: an untrusted,
bounded manifest key-ID extraction is only a key-entry label, and the user-entered raw public key
must cryptographically verify the complete archive before consent.
