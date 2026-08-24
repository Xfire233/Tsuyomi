<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# `tsuyomi-transfer` v1 boundary

`tsuyomi-transfer` is a portable, UTF-8 JSON exchange document. It is deliberately readable and never contains cookies, authentication tokens, browser sessions, WebView state, cache files, or device secrets.

## Envelope

```json
{
  "format": "tsuyomi-transfer",
  "version": 1,
  "createdAt": "2026-08-08T00:00:00Z",
  "library": [],
  "shelves": [],
  "preferences": {}
}
```

## Stable book identity

A remote book is identified by the tuple `(sourceId, remoteBookId)`, never by a local database row ID. A missing extension leaves the book as a dormant record and preserves its metadata and progress.

## Progress

A progress record carries `updatedAt`, a stable chapter ID when known, an optional text anchor, a Unicode code-point character offset, chapter progression, and last-resort whole-book progression. Conflicting records select the newer valid `updatedAt`; a numerically further location never wins merely for being further. Equal timestamps retain the existing valid host record, preventing import-order-dependent changes.

## Deterministic import and export

The complete UTF-8 JSON document is limited to 32 MiB before parsing. Library entries are emitted in ascending `(sourceId, remoteBookId)` order. Hosts reject duplicate stable book identities, duplicate shelf IDs, dangling shelf references, and shelf parent cycles rather than guessing. A host normalizes deterministic shelf order by parent, then `position`, then ID; a future canonical serializer will additionally lock JSON member ordering. Arbitrary `extensionData`, smart rules, subscription definitions, secrets, and opaque source state are excluded from v1 until they have a versioned portable schema.


## Display preferences

`tsuyomi-transfer` v1 may carry portable reader typography and theme preferences only. E-ink auto-detection, manual display-profile choice, device classification, logical refresh override, redraw counters, and panel behavior are host-local implementation state and are excluded.

## Native backup distinction

Each host may retain a complete native backup containing implementation-specific state. A host that ever includes credentials must require explicit opt-in and password encryption; Tsuyomi Android v1 does not import legacy credentials. Native backups are not portable protocol artifacts.

The normative JSON Schema and semantic conformance fixtures cover progress conflict resolution, deterministic library ordering, duplicate identities, and size limits. Reader-document and locator schemas/fixtures are available separately; HXP package cryptographic vectors remain Phase 0 work. No secret-bearing field may be added.
