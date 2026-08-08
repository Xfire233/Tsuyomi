<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# `hikari_novel_backup` v1 import mapping

This is an Android one-way compatibility importer. The legacy document remains an input only; Tsuyomi never emits it. The importer records field-level warnings and continues with unrelated valid records.

## Envelope and source identities

| Legacy field | Tsuyomi target | Rule |
|---|---|---|
| `format = hikari_novel_backup`, `schemaVersion = 1` | importer discriminator | Reject any other format/version before mutation. |
| `createdAt` | import audit timestamp | Preserve as source provenance, not per-record `updatedAt`. |
| legacy bare `aid` | `identity: {sourceId: org.tsuyomi.wenku8, remoteBookId: aid}` | Valid only when non-empty and not prefixed. |
| `aid = esj:<bookId>` | `identity: {sourceId: org.tsuyomi.esjzone, remoteBookId: bookId}` | Strip exactly one `esj:` prefix. |
| `aid = yamibo:<tid>` | `identity: {sourceId: org.tsuyomi.yamibo, remoteBookId: tid}` | Strip exactly one `yamibo:` prefix. |

## Section mapping

| Legacy path | Target | Handling |
|---|---|---|
| `payload.auth.cookies.*`, `wenku8UserInfo` | Explicitly dropped | Never import legacy credentials. Report that non-empty credential fields were skipped without echoing their values; the user signs in again through the controlled WebView flow. |
| `payload.appSettings.language`, `themeMode`, `dynamicColor`, color history | Android preferences | Migrate only through the native importer. Enum indices are validated; Tsuyomi defaults win for absent/unknown values. |
| `payload.appSettings.browsingEInkMode`, `payload.readerSettings.readerEInkMode` | Android global display preference | If either legacy value is `true`, import the manual `eInk` preference. False or absent values keep Tsuyomi's default `auto` detection and never force `standard`. The preference is Android-local and excluded from `tsuyomi-transfer`. |
| `payload.appSettings.smartShelfMemberships`, `smartShelfSyncMetadata` | Android smart-collection migration | Map only compatible local tag/source/author/title/update/rating/date conditions into the host rule AST. Preserve subscription metadata as disabled audit drafts; never enable sources, run discovery, or perform remote sync from imported configuration. |
| `payload.appSettings.sourceSyncConfigs`, source-local hidden IDs, Wenku8/Yamibo browsing state | source-local migration metadata | Never enable a source or perform remote sync from imported configuration. |
| `payload.readerSettings` | Android reader preference migration | Map flow, font scale, spacing, low-stimulus colors, and accessibility-safe options after unit conversion. Drop device-specific engine, font path, image path, wake lock, and platform TTS engine/voice fields. |
| `payload.bookshelf.items[]` | library records | Map `aid` through the identity table; retain title, URL, cover URL, update metadata, rating, remote/local tags, and a resolved shelf membership. Invalid JSON tag lists produce warnings and empty lists. |
| `payload.bookshelf.{folders,sortTypes,aidOrders}` | shelf placement/order hints | Use only to reconstruct manual shelf order. Source remote folder IDs never cause server-side mutations. |
| `payload.readingData.readHistory[]` | progress | Convert `cid` to source-specific chapter ID; parse `locatorJson` when valid. Prefer stable locator fields, then legacy `location`/`progress` as fallback. Legacy rows lack per-row time; use document `createdAt` and report the reduced conflict precision. |
| `payload.readingData.browsingHistory[]` | Android-only history | Import after stable identity conversion. Omit from portable transfer v1. |
| `payload.readingData.searchHistory[]` | Android-only search history | Import as local UI data. Omit from portable transfer v1. |
| `payload.readingData.novelDetails[]` | cached metadata candidate | Parse only if it validates against the active extension contract; otherwise retain no opaque cached payload. Never export as transfer cache. |

## Explicit drops

`textStyleFilePath`, day/night background image paths, platform TTS engine/voice selection, cookie strings, account fields, cache payloads, local database IDs, and raw extension implementation blobs are not portable. The importer must list each dropped non-empty field in its result summary without echoing secret values.
