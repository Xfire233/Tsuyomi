<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# ADR 0008: Dual backup model and Flutter `hikari_novel_backup` v1 import

- Status: Accepted
- Date: 2026-08-08

## Problem

Tsuyomi needs both portable user-data exchange and complete host recovery while allowing users to migrate useful state from Hikari Novel Plus.

## Constraints

- Portable data must not contain credentials, browser state, caches, or platform-specific settings.
- A full native backup may need implementation-specific records.
- Legacy backup rows have incomplete timestamps and mixed portable/private fields.

## Decision

Tsuyomi defines two distinct exports:

1. `tsuyomi-transfer`: portable UTF-8 JSON containing stable book identities, metadata, shelves, tags/ratings, durable semantic-position bookmarks, portable preferences, and reading locators. It never contains secrets.
2. Android native backup: host-specific recovery data. Including credentials requires a separate explicit opt-in and password-based encryption.

Android provides a one-way importer for `hikari_novel_backup` schema version 1. The importer validates the entire envelope before mutation, then processes independent records with field-level warnings. It never emits the legacy format and never performs network synchronization as an import side effect.

The Hikari importer never imports cookies, account fields, WebView state, or other credentials. Users authenticate again through the controlled WebView flow. Dropped non-empty credential fields are reported by field name without echoing secret values.

### Accepted semantic-bookmark portability — 2026-09-14

This supersedes the chapter-level bookmark decision of 2026-09-12. Bookmarks belong to the durable user-data domain and store a semantic `ReaderLocator`, independently of reading progress, completed chapters and Library presence. Distinct positions within one chapter remain distinct; identity/deduplication uses semantic position rather than capture timestamp or rendered page/pixel index. Portable export/import includes bookmarks and necessary stable book identity even for non-pinned books; importing such a record preserves `localPin=false`. No chapter body, credential, browser state, source installation or website writeback is included. Existing user selection/conflict handling remains authoritative; repeated import is idempotent, and formats omitting bookmarks never delete existing marks.

The installed private Beta has already introduced Room11 chapter bookmarks and transfer v4, so preserve both immutable legacy inputs. Migrate existing chapter marks to explicit degraded chapter-start locators with `chapterProgress=0` and no invented block, character offset or anchor digest. Use the existing deterministic legacy timestamp convention when a mark has no capture time; never label its location as exact. The new active storage/transfer successor stores semantic-position bookmarks only, removes the chapter-mark operations, and strictly validates identities, locator shape and aggregate bounds before mutation. Legacy v1-v4 readers remain supported without widening their closed schemas. Requested `DUAL` remains portable independently of the destination window's effective layout. This decision adds neither cloud synchronization nor a new native-backup feature.

`tsuyomi-transfer` v5 therefore replaces v4's `bookmarkedChapterIds` field with a required `bookmarks` list of validated `ReaderLocator` records. Every record carries its exact `DocumentIdentity`, optional semantic anchor/fallback fields, and capture timestamp; its stable bookmark identity is the exact document plus strongest available semantic position, not the timestamp. A v5 record's document must belong to its enclosing book and no two records may name the same bookmark position. v1–v4 remain input-only, closed schemas; specifically, v4 chapter IDs become degraded chapter-start locators at the epoch timestamp.

## Rejected alternatives

- One universal backup containing all state: rejected because portability and complete recovery have incompatible secrecy requirements.
- Preserve arbitrary legacy blobs for later use: rejected because opaque state creates security and compatibility debt.
- Import and immediately synchronize source state: rejected because a local restore must not cause remote mutations.
- Import legacy cookies or account state: rejected because the legacy backup is not a trusted credential-transfer container and the new application has an independent identity.

## Migration impact

Legacy IDs are converted to `(sourceId, remoteBookId)`. Valid locator data takes precedence over numeric fallback progress. Document `createdAt` is used when a legacy row has no independent update timestamp, and reduced conflict precision is reported.

## Verification

- Portable transfer fixtures contain no secret-bearing fields.
- Import rejects unsupported envelope versions before database mutation.
- Malformed records do not prevent unrelated valid records from importing.
- Native credential backup cannot be produced without explicit opt-in and encryption.
- Bookmark export/import preserves exact semantic locations and non-pinned status, distinguishes positions in one chapter, remains idempotent, migrates legacy chapter marks honestly to degraded chapter starts, preserves existing marks when old formats omit them, and never transfers content or credentials.
- Requested dual-page mode survives transfer and narrow-window import without being rewritten as the temporary effective single-page mode.
