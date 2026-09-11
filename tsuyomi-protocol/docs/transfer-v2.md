<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# `tsuyomi-transfer` v2 and v3 boundary

`tsuyomi-transfer` v3 is the current portable UTF-8 JSON exchange contract. It preserves the v1/v2 envelope, stable `(sourceId, remoteBookId)` identity, deterministic ordering, 32 MiB limit, and explicit exclusion of credentials, cookies, sessions, caches, and device-local display state.

## Compatibility

Current exporters emit `version: 3`. Importers accept strict v1, v2, and v3 documents. A v1 or v2 document is interpreted only with the fields declared by its closed versioned schema; fields introduced later are rejected rather than silently widening historical contracts. V1 and v2 carry no local-pin field, so their records imply `localPin: true` when imported.

## Added portable state

V2 added:

- `book.readLater`, defaulting to `false` when absent;
- `book.completedChapterIds`, an explicit JSON array of at most 20,000 unique nonblank stable chapter-ID strings, each at most 512 Unicode code points; object values, numeric elements and duplicates are invalid;
- reader layout and behavior preferences: horizontal margin, paragraph spacing, portrait lock, progress visibility, immersive mode, keep-awake, and volume-key paging.

V3 adds required `book.localPin`. A `false` value preserves the retained record and its portable metadata, local tags, rating, read-later state, progress, and completion IDs without restoring a local library pin. An unpinned record cannot declare a manual `shelfIds` membership; importers reject that invalid relationship instead of repinning the record or creating an invalid membership.

Chapter completion is independent of the latest resume locator. Importing completion IDs never infers additional completed chapters from ordering or progress fractions.

## Validation and canonicalization

The normative schemas are `schemas/tsuyomi-transfer-v2.schema.json` and `schemas/tsuyomi-transfer-v3.schema.json` for their respective closed document versions. Exporters sort books by stable identity, shelves by parent/position/ID, and set-valued fields lexically. V3 exporters emit `localPin` explicitly. Importers reject duplicate identities, duplicate shelf IDs, dangling shelf references, shelf cycles, unsupported fields, malformed preferences, whitespace-only completion IDs, an absent or non-boolean v3 local pin, unpinned manual memberships, and all declared bounds violations before mutation.
