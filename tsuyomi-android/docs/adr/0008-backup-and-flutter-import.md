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

1. `tsuyomi-transfer`: portable UTF-8 JSON containing stable book identities, metadata, shelves, tags/ratings, portable preferences, and reading locators. It never contains secrets.
2. Android native backup: host-specific recovery data. Including credentials requires a separate explicit opt-in and password-based encryption.

Android provides a one-way importer for `hikari_novel_backup` schema version 1. The importer validates the entire envelope before mutation, then processes independent records with field-level warnings. It never emits the legacy format and never performs network synchronization as an import side effect.

The Hikari importer never imports cookies, account fields, WebView state, or other credentials. Users authenticate again through the controlled WebView flow. Dropped non-empty credential fields are reported by field name without echoing secret values.

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
