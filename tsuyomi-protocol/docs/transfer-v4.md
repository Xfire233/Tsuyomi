<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# `tsuyomi-transfer` v4 boundary

`tsuyomi-transfer` v4 is the current portable UTF-8 JSON exchange contract. It retains the v1–v3 envelope, stable `(sourceId, remoteBookId)` identity, deterministic ordering, and 32 MiB maximum document size. It never contains credentials, cookies, browser sessions, source-install state, caches, device display state, or source/website effects.

## Compatibility

Current exporters emit `version: 4`. Importers accept strict v1, v2, v3, and v4 documents. Each historical version is closed: a v1, v2, or v3 document carrying v4 fields is invalid rather than widened. V1 and v2 records imply `localPin: true`; v3 and v4 require explicit `localPin`.

The normative v4 shape is [`schemas/tsuyomi-transfer-v4.schema.json`](../schemas/tsuyomi-transfer-v4.schema.json). The schema validates structural bounds; importers also reject duplicate book identities, duplicate shelf IDs, dangling shelf references, shelf cycles, and noncanonical record ordering before mutating durable state.

## Chapter bookmarks

Every v4 `book` has a required `bookmarkedChapterIds` array, including `[]` when no chapters are marked. It contains at most 20,000 distinct, nonblank stable chapter IDs, each at most 512 Unicode code points. Bookmark identity is the enclosing full book identity plus chapter ID.

Bookmarks are independent of local library membership, reading progress, and completion. A v4 record with `localPin: false` may therefore export bookmark state and required metadata without recreating a local pin, installing a source, or writing to a website. As in v3, an unpinned record cannot declare a manual `shelfIds` membership. Importing the array is additive and idempotent; its absence in a legacy v1–v3 record never deletes locally retained bookmarks.

## Reader preferences

V4 retains all v2 Reader fields and adds these portable global Reader fields, each optional so partial import remains possible:

| Field | Values | Default when absent |
| --- | --- | --- |
| `flow` | `scroll`, `paged`, `dual` | host default |
| `fontFamily` | `system`, `sans`, `serif`, `monospace` | `system` |
| `fontWeight` | `400`, `500` | `400` |
| `letterSpacing` | `-0.05` through `0.20` | `0` |
| `firstLineIndent` | `0` through `4` em | `0` |
| `verticalMargin` | `0` through `64` dp | `24` |
| `textAlignment` | `start`, `justify`, `center`, `end` | `start` |
| `foregroundColor` / `backgroundColor` | uppercase `#RRGGBB` | theme-derived |

A color is omitted, not serialized as `null`, when it is theme-derived. The flow field is the portable global default only. A host may keep an individual book's local flow override, but that local override is never transferred.

## Canonicalization

Exporters sort books by `(sourceId, remoteBookId)`, shelves by parent/position/ID, and all set-valued arrays lexically. V4 exporters emit both `localPin` and `bookmarkedChapterIds` explicitly. Parsing a legacy document and exporting it produces v4 canonical bytes, so its legacy input digest is not a v4 export digest.
