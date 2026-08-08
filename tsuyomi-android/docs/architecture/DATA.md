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

The global display preference is one enum: `auto`, `standard`, or `eInk`. Advanced logical refresh override is `automatic`, `quality`, `balanced`, or `fast`. Effective device classification and transient refresh counters are derived runtime state, not durable protocol data. There are no separate browsing and reader E-ink settings.

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

Canonical progress contains `updatedAt`, stable chapter ID when known, text anchor, character offset, chapter progression, and whole-book progression. Rendered page number and mutable scroll extent are view state only.

Conflict selection follows protocol rules before writing Room. The newer valid `updatedAt` wins, including intentional backward movement. Legacy imports without row timestamps use the backup `createdAt` and retain a reduced-precision warning.

## Credentials

Hikari credentials are never imported. New login state is created only by host-controlled WebView or explicit source authentication. Credential values are partitioned by source origin and are unavailable to transfer export, extension enumeration, Room library queries, normal logs, and diagnostics.

The concrete protected-storage implementation must use Android Keystore-backed keys and authenticated encryption. Cryptographic material, aliases, rotation, and recovery behavior are fixed before credential persistence code is added.

## Cache

Cache entries are keyed by stable source identity, extension version, request contract version, and normalized request identity. Cache data is bounded, evictable, and never treated as authoritative user state. Updating or revoking an extension invalidates parser-dependent cached payloads without deleting library metadata or progress.
