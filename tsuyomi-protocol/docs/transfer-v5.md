<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# `tsuyomi-transfer` v5 boundary

`tsuyomi-transfer` v5 is the current portable UTF-8 JSON exchange contract. It retains v1–v4's stable `(sourceId, remoteBookId)` book identity, deterministic ordering, and 32 MiB maximum document size. It never carries chapter bodies, credentials, cookies, browser sessions, source-install state, caches, device display state, or source/website effects.

## Compatibility

Current exporters emit `version: 5`. Importers accept strict, closed v1–v4 documents as input only; a document claiming an earlier version must not carry v5 fields. In particular, v4's `bookmarkedChapterIds` remains a v4-only input field and is converted to degraded chapter-start locators with `chapterProgress: 0` and an epoch capture time. V1 and v2 imply `localPin: true`; v3–v5 require explicit `localPin`.

The normative v5 shape is [`schemas/tsuyomi-transfer-v5.schema.json`](../schemas/tsuyomi-transfer-v5.schema.json). The schema enforces record shape and bounds. The semantic validator additionally rejects duplicate book identities, duplicate shelf IDs, dangling shelf references, shelf cycles, noncanonical library order, bookmark-document/book mismatches, and duplicate bookmark positions before durable mutation.

## Semantic bookmarks

Every v5 `book` has a required `bookmarks` array, including `[]` when no bookmarks exist. It contains at most 20,000 `ReaderLocator` records. A locator carries its exact document identity (`sourceId`, `remoteBookId`, `contentId`, optional `revision`), optional stable block/anchor/fallback fields, and `capturedAt`. Its document's source and remote book IDs must equal its enclosing book identity.

A bookmark position is the exact document identity plus the strongest available semantic location:

1. `blockId` plus `characterOffset`, when both exist;
2. otherwise `blockId` plus `textAnchorDigest`;
3. otherwise the available `blockId`, `chapterProgress`, and `bookProgress` fallback values.

Two records with the same position are invalid even when their anchor digest, fallback values, or `capturedAt` differ. Capture time is returned to the user but never creates a second position. Rendered page/spread indices, pixel offsets, scroll metrics, and layout state are not part of the grammar.

Bookmarks are independent of local library membership, reading progress, and completion. A `localPin: false` book may therefore export semantic bookmark state and required metadata without recreating a local pin, installing a source, or writing to a website. An unpinned book cannot declare manual `shelfIds`. Import is additive and idempotent; an older format that omits bookmark state never deletes locally retained semantic bookmarks.

## Reader preferences and canonicalization

V5 retains v4's portable Reader preference grammar, including `scroll`, `paged`, and `dual` flow, typography, alignment, and uppercase `#RRGGBB` colors. All Reader fields remain optional, so a partial import does not rewrite an omitted preference.

Exporters sort books by `(sourceId, remoteBookId)`, shelves by parent/position/ID, set-valued arrays lexically, and bookmarks by their stable semantic position. V5 exporters emit both `localPin` and `bookmarks` explicitly. Re-exporting any legacy input produces v5 canonical bytes, so a legacy input digest is not a v5 export digest.
