<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# `tsuyomi-transfer` v2 boundary

`tsuyomi-transfer` v2 is the current portable UTF-8 JSON exchange contract. It preserves the v1 envelope, stable `(sourceId, remoteBookId)` identity, deterministic ordering, 32 MiB limit, and explicit exclusion of credentials, cookies, sessions, caches, and device-local display state.

## Compatibility

Current exporters emit `version: 2`. Importers accept both strict v1 and strict v2 documents. A v1 document is interpreted only with the fields declared by `tsuyomi-transfer-v1.schema.json`; v2-only fields in a document claiming `version: 1` are rejected instead of silently widening the historical contract.

## Added portable state

V2 adds:

- `book.readLater`, defaulting to `false` when absent;
- `book.completedChapterIds`, an explicit JSON array of at most 20,000 unique nonblank stable chapter-ID strings, each at most 512 Unicode code points; object values, numeric elements and duplicates are invalid;
- reader layout and behavior preferences: horizontal margin, paragraph spacing, portrait lock, progress visibility, immersive mode, keep-awake, and volume-key paging.

Chapter completion is independent of the latest resume locator. Importing completion IDs never infers additional completed chapters from ordering or progress fractions.

## Validation and canonicalization

The normative schema is `schemas/tsuyomi-transfer-v2.schema.json`. Exporters sort books by stable identity, shelves by parent/position/ID, and set-valued fields lexically. Importers reject duplicate identities, duplicate shelf IDs, dangling shelf references, shelf cycles, unsupported fields, malformed preferences, whitespace-only completion IDs, and all declared bounds violations before mutation.
