<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Data architecture

## Ownership boundaries

| State | Owner | Storage | Portable transfer |
|---|---|---|---|
| Books, shelves, ratings, tags | Host | Room | Yes |
| Reading locator and timestamps | Host | Room | Yes |
| Installed extension versions | Host | Room + verified package files | No |
| Publisher keys and capability grants | Host | Room / protected preferences | No |
| UI, reader, and global display preferences | Host | DataStore | Portable subset only; E-ink/device refresh preference is local |
| Source cookies and credentials | Host, source-scoped | protected credential storage | Never |
| Small extension-local state | Host on behalf of extension | quota-bound namespaced storage | No |
| Chapter/image cache | Host | cache/files with bounded metadata | No |
| Import results and warnings | Host | Room audit records | No |

Extensions never own canonical library or progress records. Uninstalling an extension removes executable/package state and its optional source-local storage only after confirmation; books and progress remain dormant.

The global display preference is `auto`, `standard`, or `eInk`. The final2026-10-06 0.5.0 scope is monochrome E-ink, generic optimization and manual redraw only; the old persisted automatic/quality/balanced/fast override is superseded. No unimplemented advanced preference or unused color-panel capability state. Device classification/redraw epoch stay Android-local/derived, not portable. No separate browsing/reader E-ink switches; default E-ink pagination and explicit flow choice do not overwrite Standard requested-flow preference.

## Relational invariants

The initial Room model must enforce these logical keys:

- collection: stable host ID, kind (`manual`, `smart`, or `subscription`), bounded acyclic presentation parent relation, and stable display order;
- manual collection membership: unique `(collectionId, sourceId, remoteBookId)`;
- smart collection rule: one validated versioned AST per collection; evaluated from Room projections and never stored as mutable result membership;
- subscription candidate: unique `(collectionId, sourceId, remoteBookId)` with source-refresh audit state;
- installed extension: unique `(extensionId, version)` with one active version;
- capability grant: unique `(extensionId, publisherKeyId, capability, scope)`;
- source credential partition: unique source/extension ownership, never joined into export queries.

Room row IDs are private implementation details and never appear in protocol documents, extension APIs, or durable file names.

### 0.5.0 local-publication lifecycle — accepted scope 2026-10-06

These invariants govern the local-publication implementation. Room v14 preserves existing remote data and rekeys the shared book state to explicit remote/local identities without destructive fallback; transfer v6 preserves the closed legacy v1–v5 input boundaries.

- Import keeps a complete application-private durable original, independent of later provider availability; no URI-reference mode selected. Originals/indispensable resources are not LRU cache and survive cache clearing or Library unpinning. Android uninstall removes them; archive recovery is defined by ADR0008.
- Portable publication ID and verified byte fingerprint have distinct roles. ADR0009 owns user-confirmed duplicate association/independent copy, not automatic merging. Originals, metadata, rebuildable rendition/index/page plans, remote caches and per-job staging have distinct lifecycles.
- A same-ID format/size/SHA-256 conflict retains the existing original descriptor and availability, including a missing-original reassociation descriptor. Import review reports it independently of the ordinary newer-title/author merge; metadata timestamps never detach admitted originals.
- File admission and Room commit are recoverable stages, not a cross-filesystem ACID transaction. Cancellation, provider failure, insufficient space and process death must preserve existing books and give truthful per-book results; cleanup touches only the job's uncommitted data.
- Two separately confirmed deletes: remove private original/resources while preserving metadata, collections, progress, history and bookmarks as a missing/re-associable publication; or remove that publication's original and all its reading/library state, including independent bookmarks. Neither deletes the provider's original file, another publication, global preferences or remote source data.
- Cancel/invalidate active Reader/import/layout/resource owners before deletion. Recover interrupted file/Room removal consistently; late callbacks cannot recreate deleted books or erase retained state. Missing-original state is not an uninstalled-source error.
- Portable metadata excludes original bytes, paths, provider URIs, credentials and derived layout. Optional original-containing archive is an explicit separate versioned manifest/digest/path/quota-validated recoverable export, not an expanded transfer v5.
- A local book's optional HTTPS resource permission is non-portable private state bound to its publication ID/original digest and reviewed public origins/resource types, per READER. Backup restore or an independent duplicate cannot grant network authority. Revocation and late resource callbacks obey the active owner/epoch; no source cookie or capability inheritance.
- A verified local original may retain a bounded native admission record beside its private bytes. This derived record stores validated catalog/navigation/resource descriptors and compatibility findings, never chapter prose, executable handles, layout, provider authority or network grants. Bind it to publication identity, original format/size/SHA, parser/normalizer revisions and parse options; validate it before reuse. Stage and atomically publish it through the existing original owner, charge it against the existing private-store budget, and remove it with either original-removal scope. It is not an evictable rendition cache or portable archive authority. Missing, corrupt or obsolete records require one complete re-admission without changing the original, reading state or identity; failure preserves those user-owned data and is reported truthfully.
- EPUB cover selection prefers the manifest `cover-image` property, then accepts an explicit legacy `meta name="cover"` reference in either supported EPUB version. Both paths use the same admitted resource checks; the first spine image is not an implicit cover. For an already imported EPUB with no cover ID, bounded cover-only recovery may use its verified private original without reimporting or reparsing chapter bodies. Persist the admitted cover resource before conditionally filling only the missing cover ID for that same available original; preserve book identity, original bytes, progress, bookmarks, history and unrelated metadata. Recovery and removal share lifecycle fencing so late work cannot recreate deleted resources or records. Missing-cover results may be reused for the exact original within the bounded process cache, never treating cancellation as a successful absence.

## Transactions

Library actions update local state transactionally. When optional remote writeback is enabled, local and remote systems cannot share one atomic transaction, so the operation records an explicit reconciliation state:

```text
pending → remote_succeeded → committed
        ↘ remote_failed
        ↘ cancelled
```

A remote failure does not get reported as synchronized. Retry requires an explicit user action or a narrowly defined idempotent retry policy; import and background refresh never convert local changes into remote writes.

Transfer and Hikari imports use two phases:

1. parse, validate, normalize, and produce a redacted import plan;
2. apply valid records in bounded Room transactions and persist warnings.

An invalid envelope aborts before mutation. Invalid independent records are skipped without discarding unrelated valid records.

## Progress

Canonical progress contains `updatedAt`, stable chapter ID and document revision when known, text anchor, character offset, chapter progression, and whole-book progression. Transfer v6 preserves document revision through Room export/restore; legacy inputs stay closed and do not invent one. Rendered page number and mutable scroll extent are view state only.

Conflict selection follows protocol rules before writing Room. The newer valid `updatedAt` wins, including intentional backward movement. Legacy imports without row timestamps use the backup `createdAt` and retain a reduced-precision warning.

## Credentials

Hikari credentials are never imported. New login state is created only by host-controlled WebView or explicit source authentication. Credential values are partitioned by source origin and are unavailable to transfer export, extension enumeration, Room library queries, normal logs, and diagnostics.

The concrete protected-storage implementation must use Android Keystore-backed keys and authenticated encryption. Cryptographic material, aliases, rotation, and recovery behavior are fixed before credential persistence code is added.

## Cache

Cache entries are keyed by stable source identity, extension version, request contract version, and normalized request identity. Cache data is bounded, evictable, and never treated as authoritative user state. Updating or revoking an extension invalidates parser-dependent cached payloads without deleting library metadata or progress.
