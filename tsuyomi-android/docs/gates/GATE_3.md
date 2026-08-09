<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Gate 3 plan — local library and migration

## Status and review input

- Planner status: **DRAFT FOR DESIGNER / ADVISER REVIEW**
- Implementation authorization: **NOT GRANTED**
- Gate 2 baseline tag: `gate-2-baseline`
- Gate 2 baseline commit: `cfbbf4f5d6af6fc216d85b496a6d7f4362616591`
- Planning branch: `feature/gate-3-local-library-migration`
- Hikari behavior reference: `Xfire233/hikari_novel_flutter_plus` commit `a1feba6d1dd8dbbdd2b5ae042e44f2ec54d26bef`
- Protocol baseline: `@tsuyomi/protocol` `0.1.0`, `tsuyomi-transfer` v1
- Android baseline: `org.tsuyomi.android` `0.1.0` / versionCode `1`, Room schema `1`
- UI impact: **YES** — library, local book details, collection management, smart-rule editor, data transfer/migration, source search history, and import reports.
- Security-sensitive impact: **YES** — untrusted JSON import, redacted legacy credential handling, bounded file I/O, deterministic export, database migration and conflict application.

This document is the common frozen input for Designer and Adviser review. No production implementation begins until required review findings are closed and the user confirms the decision register in this document.

## Outcome

A local-first reader preserves books, semantic progress, ratings, local tags and organization across process/device restoration, and safely imports supported Hikari data without credentials, browser state, caches or remote side effects.

Gate 3 is complete only when a clean API 29 profile can:

```text
install app
→ import a portable Tsuyomi or sanitized Hikari document through Android SAF
→ inspect a redacted dry-run plan
→ explicitly confirm
→ restore books, newest valid semantic progress and manual organization
→ browse/search local library under Standard and E-ink profiles
→ observe books as dormant while their source package is absent
→ install the signed Wenku8 fixture source
→ reopen the same stable book/progress without identity replacement
→ export deterministic tsuyomi-transfer v1 through Android SAF
```

## User-visible scope

### Local library

- Replace the Gate 1 empty-only library root with a real observable library.
- Add an explicit local `加入书架` / `移出书架` action to source book details. Reading or viewing a source book continues to preserve metadata/progress but does not silently imply library membership.
- Display the immutable system collections:
  - 全部书籍;
  - 继续阅读;
  - 最近阅读;
  - 有未读更新;
  - 来源未安装.
- Create, rename, reorder, nest and delete manual collections.
- Add/remove multiple books to/from multiple manual collections.
- Edit local rating and local tags. Remote tags remain read-only source metadata.
- Search local titles, authors and normalized tags without network or extension execution.
- Sort by title, author, added time, last read, metadata update, progress, rating and source.
- Open a local book details screen from Room data. Dormant books remain readable as records; source-dependent actions are replaced by one truthful prerequisite explaining that the signed source must be installed.
- Preserve search and browsing history imported from Hikari as host-owned source-scoped history. Search suggestions appear only for an installed matching source; browsing timestamps may drive local sort but do not add a book to the library.

### Collections

- Manual collections use many-to-many membership and presentation-only hierarchy.
- Smart collections use a versioned bounded AST and live Room projection queries. No result membership list is persisted.
- The initial editor supports nested `全部满足` / `任一满足` groups and explicit exclusion, mapped to `All`, `Any` and `Not`; it never exposes raw JSON, SQL, regular expressions or extension code.
- Initial predicates are the already accepted local set: source, manual collection, normalized tag, author/title term, status, rating range, added/read/metadata time window, progress state, unread/source update and dormant source.
- Imported subscription configuration is retained as a disabled audit draft only. Gate 3 never creates a remote discovery request and never mutates a source website.

### Portable transfer

- Export `tsuyomi-transfer` v1 through `ActivityResultContracts.CreateDocument`.
- Import `tsuyomi-transfer` v1 through `ActivityResultContracts.OpenDocument`.
- Read at most 32 MiB of UTF-8 JSON before parsing.
- Produce a deterministic export for the same database snapshot and injected `createdAt`: stable library ordering, stable shelf ordering, stable set ordering and stable JSON serialization.
- Exclude credentials, publisher trust, capability grants, HXP files, WebView state, Cookie state, cache, local search/browsing history, E-ink/device classification and arbitrary extension state.
- Preserve stable identity, metadata, rating/tags, manual shelves, semantic progress and the portable reader preference subset defined by transfer v1.
- Keep smart rules and subscription drafts Android-local because transfer v1 explicitly excludes them.

### One-way Hikari import

- Accept only `format = hikari_novel_backup` and `schemaVersion = 1`.
- Parse the fixed reference shape without importing Flutter/GetX/Hive/Drift implementation structure.
- Map source identity exactly as specified:
  - bare `aid` → `org.tsuyomi.wenku8`;
  - `esj:<id>` → `org.tsuyomi.esjzone`;
  - `yamibo:<tid>` → `org.tsuyomi.yamibo`.
- Import compatible bookshelf items, manual folders/order, local tags, rating, stable progress, safe app/reader preferences, local smart rules, source-scoped search history and browsing timestamps.
- Convert compatible smart rules into the typed local AST. Unsupported `section` rules and every subscription query become disabled drafts with warnings.
- Import `novelDetails` only when an installed verified source can validate the normalized metadata contract; otherwise drop it with a warning rather than retaining an opaque blob.
- Never import cookies, accounts, tokens, WebView state, source sync enablement, cache payloads, font/image paths, TTS engine/voice identifiers or device-specific refresh state.
- Report every dropped non-empty sensitive field by safe field code/name only; never echo its value.
- Never install/enable a source, open a WebView, execute QuickJS, perform network I/O or write back to a source as an import side effect.

## Explicit non-goals

- Remote favorite/folder writes, reconciliation or Gate 4 manifest write operations.
- Source subscription execution, candidate refresh, scheduled/background network work or remote discovery API changes.
- Additional live sources, official extension repository, production publisher keys or source auto-install.
- Smart rules in portable transfer v1; no transfer v2 in this Gate.
- Native credential backup, password-encrypted credential export or legacy credential migration.
- Cached chapter/image transfer, offline cache restoration or opaque source payload preservation.
- Cloud sync, account sync, telemetry, crash reporting, feature flags, local EPUB/TXT, TTS or forum flows.
- Physical E-ink waveform/vendor refresh claims.

## Current baseline and source fixes

The baseline already has stable `BookIdentity`, semantic `ReadingProgress`, Room schema 1, manual collection uniqueness/cycle checks, signed source package storage and a Gate 1 empty library screen. Gate 3 must fix these source limitations rather than layer parallel paths:

1. `BookEntity` currently contains only title/timestamps and conflates a known book row with visible library membership.
2. `SourceFlowController` saves metadata/progress during browse/reader activity, so membership needs a separate explicit host record.
3. `CollectionKind` and tables do not yet persist smart rules, disabled subscription drafts, import audit, ratings/tags, source availability or search projection.
4. The DAO has point reads/writes only; there are no observable library/system/smart queries or stable order repair operations.
5. `LibraryScreen` is an honest empty state only.
6. `InstalledExtensionStore` can enumerate verified source IDs, but the app exposes only one active package and no observable source-availability projection to Room.
7. `shared:backup`, `shared:smart-shelf` and `feature:backup` exist as empty boundary modules and must be filled rather than replaced by a second convention.
8. Transfer v1 schema/fixtures exist, but Android has no parser, canonical exporter, dry-run planner, SAF UI or importer.
9. Reader portable preferences exist in transfer v1 but have no durable Android consumer; Gate 3 must add the real DataStore state and reader/settings consumption before importing them.

## Component and module plan

### `tsuyomi-protocol`

- Keep `tsuyomi-transfer` wire version at v1.
- Clarify deterministic producer requirements for a fixed snapshot/clock without adding secret or arbitrary extension fields.
- Add valid/invalid fixtures for shelves, dangling membership, parent cycles, duplicate identities, deterministic ordering, tags/ratings, progress ties, unsupported version and maximum-size boundaries.
- Add sanitized `hikari_novel_backup` v1 fixtures covering Wenku8/ESJ/Yamibo identities, manual folders, compatible/incompatible smart rules, disabled subscription metadata, progress fallback and non-empty secret fields.
- Extend conformance to distinguish fatal envelope/graph errors from recoverable independent record warnings.
- Proposed component version after implementation: `0.2.0`; exact bump remains part of final version review.

### `shared:backup`

Pure Kotlin/JVM; no Android `Uri`, Room entity, Compose type, extension runtime or credential API.

- Transfer/Hikari DTOs and bounded parse results.
- Envelope discriminator and per-record validators.
- Redacted `ImportPlan`, `ImportWarning`, `ImportConflict`, `ImportSummary` and deterministic export snapshot models.
- Canonical identity and legacy mapping functions.
- Pure conflict selection: newer valid metadata/progress timestamp wins; equal timestamps retain existing valid host state.
- Deterministic serializer with injected `Clock`; no ambient current time in tests.

### `shared:smart-shelf`

Pure Kotlin/JVM.

- Versioned AST: `All`, `Any`, `Not` and typed predicates.
- Validation limits proposed for review: maximum depth 8, maximum nodes 128, maximum text term 256 code points, maximum terms per predicate 64.
- Stable JSON encoding for Android-local persistence.
- Hikari compatible-rule translator producing either a valid AST or a disabled-draft warning.
- No SQL generation in this module.

### `core:database`

Room schema 1 → 2 with an explicit migration and exported schema.

Proposed logical state:

```text
book(sourceId, remoteBookId, title, authors, canonicalUrl, coverUrl,
     status, remoteTags, metadataUpdatedAt, sourceUpdateKey, hasUnreadUpdate)
library_entry(sourceId, remoteBookId, addedAt, rating?, localTags)
reading_progress(sourceId, remoteBookId, semantic locator..., updatedAt)
collection(id, kind=manual|smart|subscription, name, parentId?, displayOrder, createdAt, updatedAt)
collection_book(collectionId, sourceId, remoteBookId, addedAt, displayOrder)
smart_rule(collectionId, ruleVersion, astJson, compiledProjectionVersion)
subscription_draft(collectionId, mode, sourceScopeJson, queryJson, enabled=false, importSessionId?)
source_availability(sourceId, verifiedVersion, available)
book_search_projection / FTS(sourceId, remoteBookId, normalized title/authors/tags)
search_history(sourceId, normalizedQuery, lastUsedAt)
browsing_history(sourceId, remoteBookId, lastViewedAt)
import_session(id, kind, sourceCreatedAt, status, startedAt, completedAt?, summaryJson)
import_warning(sessionId, ordinal, safeCode, safeRecordRef?, fieldName?)
```

Invariants:

- `book` existence is not library membership; `library_entry` is the explicit membership source.
- Progress may exist without library membership.
- Manual membership requires both a manual collection and a library entry.
- System collections are fixed query definitions, never mutable rows.
- Collection parent graph is acyclic and depth-bounded; order is repaired transactionally after move/delete.
- Smart collections reject direct membership writes.
- Subscription rows remain `enabled=false` and have no execution method in Gate 3.
- Local rating is nullable; Hikari's default zero maps to unrated unless the legacy record proves an explicit value.
- Remote and local tags remain separate; only local tags are editable.
- Dormancy is derived from the verified `source_availability` projection, not cached in each book.
- Search SQL uses only enumerated fragments plus bound parameters through a reviewed query compiler; user text is never concatenated.
- Transfer/Hikari apply occurs off-main in one Room transaction after a complete dry-run plan. Cancellation is accepted before confirmation; process death or database failure rolls the transaction back.
- Import warning/audit records are redacted and contain no credential values or raw invalid payload fragments.

### Source availability

- Add an application-owned `InstalledSourceRegistry` over verified `InstalledExtensionStore` records.
- At startup and after install/remove, reverify active archives, publish the sorted available source set and synchronize `source_availability` transactionally.
- Gate 3 adds no independent source-enabled toggle. A source is available only when a verified active package is present; otherwise its books are dormant.
- Imported ESJZone/Yamibo books remain dormant because Gate 3 does not install those sources.
- No library query opens an extension or performs network I/O.

### `core:preferences` and reader consumption

- Add DataStore-backed portable reader preferences for transfer v1: flow, font scale, line height and reader theme.
- Reader UI and layout key consume these values; they are not inert imported fields.
- Global `DisplayEnvironment` remains authoritative for E-ink motion/color restrictions. An incompatible imported reader theme is retained but E-ink applies its fixed high-contrast effective palette and explains restoration under Standard.
- Hikari's two E-ink booleans map to the one Android-local manual E-ink display preference exactly as ADR 0014 specifies; false/absent never forces Standard.
- Credential, device classifier and redraw state remain outside portable transfer.

### `feature:library`

- One `LibraryViewModel`/state owner exposing selected collection, query, sort, page, selection and mutation outcomes.
- Standard and E-ink share one business state/query tree.
- Compact windows: collection selector plus one content pane; expanded windows: stable collection navigation pane plus content pane.
- Standard uses bounded lazy list/grid scrolling. E-ink uses explicit previous/next list pages with fixed chrome, stable item keys and textual page state.
- Library item opens host-owned local details first; source actions are explicit and capability-gated.
- Multi-select actions are explicit buttons/menu items and available to TalkBack, keyboard and DPAD; no swipe-only or long-press-only contract.
- Empty, loading, populated, no-result, dormant, write-failure and retry states have persistent accessible feedback.

### Source book details

- Extend the existing source detail screen with real local membership state and `加入书架` / `移出书架` actions.
- Adding persists current normalized source metadata and a `library_entry`; it never calls a remote write operation.
- Rating, local tags and manual collection membership are edited through host-owned routes/dialogs and survive source removal.

### `feature:backup`

- Add a `数据与迁移` route under `更多`.
- Actions:
  - `导出 Tsuyomi 数据`;
  - `导入 Tsuyomi 数据`;
  - `从 Hikari Novel 导入`;
  - `查看最近导入报告` when a redacted report exists.
- Use Android SAF only. No broad storage permission.
- Import flow: select → bounded read → parse/plan → redacted warnings/conflicts → explicit confirm → non-cancellable atomic apply → persistent result.
- Cancel before confirm performs no mutation. File-picker cancel returns unchanged state without an error.
- Export flow computes a stable snapshot and digest before opening/writing the destination result. Provider/write failure is reported; no success is claimed before close succeeds.

### `app`

- Wire existing boundary modules; do not move parser, database or UI logic into `MainActivity`.
- Add routes for local book details, collection management/editor, smart-rule editor, reader preferences and data/migration.
- Preserve root navigation and process recreation state with stable IDs only; never place imported JSON or book lists in `SavedStateHandle`/DataStore navigation snapshots.

### `tsuyomi-extensions`

- No production extension behavior change is planned.
- Reuse the signed Wenku8 fixture as the active-source acceptance input.
- If implementation discovers that library metadata requires a new source DTO field, stop, update the plan/protocol first and repeat Adviser review; do not add an Android-only parser shortcut.

## Import classification and conflict policy

### Fatal before mutation

- File exceeds 32 MiB or is not valid UTF-8/JSON.
- Unsupported/missing format or version.
- Root shape cannot be identified safely.
- Duplicate stable book identity or duplicate shelf ID in a portable transfer.
- Dangling shelf reference, dangling parent or collection parent cycle.
- Import plan exceeds bounded item/warning limits.

### Recoverable with warning

- One Hikari bookshelf/history record has invalid identity or malformed optional fields.
- Unknown enum/predicate/reader preference.
- Unsupported smart-rule condition or source facet.
- Non-empty credential/cache/device-specific field is dropped.
- Legacy progress has only reduced-precision numeric fallback.
- `novelDetails` cannot be validated by an installed source contract.

### Merge rules

- Gate 3 import is merge-only; it never deletes host records merely because they are absent from an import.
- Existing valid metadata/progress wins on equal or newer host timestamps; a farther numeric location is not a tie-breaker.
- Manual shelves are upserted by stable shelf ID. Membership is additive and deduplicated.
- Local tags are normalized and unioned; remote tags follow the newer accepted metadata record.
- Rating follows the newer accepted metadata record; absent/unrated input does not clear an existing rating.
- Smart collections from Hikari receive new host IDs derived deterministically from import provenance and legacy stable keys; collisions are reported rather than guessed.
- Disabled subscription drafts never change library membership or source state.

## Option applicability matrix

| Entry/action | Visibility and effect |
|---|---|
| 加入书架 | Visible on a valid source detail when not locally added; writes only host library state. |
| 移出书架 | Visible for a library entry; confirmation explains preserved progress/history. |
| 打开来源/目录 | Visible when a verified source is available; dormant state replaces it with the source-install prerequisite. |
| 评分/本地标签 | Visible for a library entry; durable local-only write with failure recovery. |
| 手动集合编辑 | Visible for manual collections only. System collections cannot be renamed/reordered/deleted; smart collections reject direct membership writes. |
| 智能规则编辑 | Visible for supported rule version; unknown versions are read-only disabled with explanation. |
| 来源订阅刷新 | Hidden in Gate 3 because no discovery consumer exists. Imported drafts are visible read-only only to explain retained blocked data. |
| 导出 Tsuyomi 数据 | Visible because a real SAF writer and result/error state exist. |
| 导入 Tsuyomi/Hikari | Visible because bounded parser, dry-run, confirmation, atomic apply and report exist. |
| 导入 legacy credential | Never offered. A warning states that sign-in data was deliberately skipped. |
| E-ink list pagination | Visible only when `effectiveProfile == EINK`; Standard uses its normal list behavior. |
| Reader theme incompatible with E-ink | Retained and shown disabled with the existing Standard-restoration explanation pattern. |

## UI and interaction acceptance

Designer review must bind the final information architecture and visual hierarchy. The implementation may not invent new screens beyond this reviewed list.

Required states:

- Library: empty, populated, selected collection, local search results, no results, multi-select, dormant source, write failure.
- Collections: system/manual/smart/disabled subscription draft, create/edit validation, cycle/depth rejection, delete/reparent confirmation.
- Local book: active source, dormant source, rating/tags, multiple collection membership, progress summary.
- Import: picker cancelled, parsing, fatal document error, dry-run with warnings/conflicts, confirmation, applying, success, storage/database failure, persisted report.
- Export: empty/nonempty snapshot, destination cancelled, writing, success with digest, provider failure.
- Reader preferences: Standard effective values and E-ink retained-but-overridden explanation.

Window/profile matrix:

- 360×800 portrait, 800×360 landscape, 360×320 split/double-compact, 599dp boundary-below, 600dp boundary-at and 840×900 expanded.
- `fontScale = 1.0` and `2.0`.
- forced Standard, forced E-ink, auto-recognized E-ink and auto-unknown Standard.
- TalkBack traversal/actions, keyboard/DPAD focus order, orientation and process recreation.
- No color-only update/dormant/warning state, no swipe-only collection action, no animated replacement dependency and no fixed-height text clipping.

## Security, privacy, lifecycle and resource model

- Import bytes are untrusted. Bound file size before parse, collection counts before allocation, string/code-point lengths, AST depth/nodes and warning/report counts.
- Never deserialize executable types, arbitrary classes, SQL, regex or source requests.
- Never log/import/report raw credential values, Cookie strings, account fields or raw rejected payloads.
- SAF read/write runs on IO dispatchers. Picker launch/result remains Activity-owned; pure parser and import planner remain lifecycle-independent.
- A stale parse result is generation-bound to the selected URI/document digest. Selecting another file, cancelling or destroying the owner invalidates the earlier plan.
- Only the currently confirmed plan digest may apply. Apply cannot accidentally target a later selection.
- Import transaction, export snapshot and smart query work have explicit owners and cancellation boundaries. Late results cannot replace a newer query/selection.
- Database migration 1→2 has no destructive fallback. Migration failure aborts app database open and is covered by schema migration instrumentation.
- Export holds at most the protocol size bound plus bounded serializer overhead. No unbounded warning list, AST, search term or UI result list.
- Import/export never touches source credential storage, HXP trust/grants or cache roots.

## Implementation sequence and revert boundaries

Each commit includes its tests and affected documentation.

1. `test(protocol): complete Gate 3 transfer and Hikari fixtures`
   - Protocol fixtures/conformance and mapping clarification only.
   - Revert independently before Android consumes new fixture guarantees.
2. `feat(model): define backup and smart collection contracts`
   - Fill `shared:backup` and `shared:smart-shelf`; pure JVM tests.
3. `feat(database): migrate local library schema for Gate 3`
   - Room 1→2 migration, projections, repositories, audit and migration tests.
4. `feat(source): expose verified source availability`
   - Application registry and installed-source synchronization; no source network behavior.
5. `feat(preferences): add portable reader settings`
   - DataStore state, reader consumer and settings UI/goldens.
6. `feat(library): deliver local collections and search`
   - Library/local-book UI, system/manual/smart collections, Standard/E-ink behavior and tests.
7. `feat(backup): deliver transfer and Hikari import`
   - SAF routes, dry-run/apply/report, clean-profile instrumentation and fixtures.
8. `test(gate3): record end-to-end admission evidence`
   - Gate document evidence, AVD recipes, checksums and known boundaries.

Rollback order is the reverse. Reverting UI/feature commits first leaves schema 2 readable. The schema migration itself is not downgraded in-place: rollback retains schema-2 user data and ships a forward-compatible patch or feature-disable revert; it never destructively recreates the database.

## Verification and acceptance matrix

### Protocol / pure JVM

- Transfer v1 valid and invalid schema fixtures.
- Fatal duplicate/dangling/cycle/order/size/version cases.
- Deterministic export bytes for fixed snapshot/clock and two repeated serializations.
- Hikari identity mapping, secret redaction, record recovery and disabled-draft translation.
- Smart AST bounds, all/any/not precedence, unknown version, time boundaries and normalized terms.
- Conflict matrix: newer, older, equal, invalid incoming and intentional backward progress.

### Room / Android unit and instrumentation

- Schema 1→2 migration preserves every Gate 2 book and semantic progress row.
- Explicit library membership does not appear from browse metadata alone.
- Manual membership uniqueness, order repair, delete/reparent and concurrent cycle assignment.
- System collection query semantics and source dormancy transitions.
- FTS/local search escaping and parameter binding; malicious terms cannot alter query shape.
- Smart query invalidation after rating/tag/progress/source-availability changes.
- Atomic import: cancel/fatal/DB failure produce no partial user mutation.
- Process death/recreate restores selected stable collection/page/report state without serializing whole payloads.

### Product end-to-end on API 29

1. Install signed Wenku8 fixture, search `fixture`, open `雾港纪事`, explicitly add it, set local rating/tags, add it to two manual collections, create a matching smart collection and read `第一章 雾中的灯塔` to the second semantic block.
2. Kill/recreate the process. Library organization, local search and semantic locator restore.
3. Export through the real system document picker and record SHA-256.
4. Clear the app profile. Import that document through the real system picker. Before source install, the book appears in `来源未安装` with rating/tags/shelves/progress intact and no source action.
5. Install the same signed Wenku8 fixture. The existing stable book becomes available without duplication; opening the reader restores the imported semantic locator.
6. Repeat the library/import path under forced E-ink with explicit list pagination, fixed chrome, immediate state replacement and no decorative animation.
7. Import the sanitized Hikari fixture. Verify Wenku8/ESJ/Yamibo identity mapping, compatible manual/smart organization, disabled subscription draft, reduced-precision progress warning and credential field-name warnings with no values.

### Negative and recovery product paths

- Picker cancel.
- Unsupported format/version.
- 32 MiB + 1 byte input.
- Invalid UTF-8/JSON.
- Duplicate identity, dangling shelf and parent cycle.
- Malformed one-record Hikari input with unrelated valid records retained in the dry-run.
- Stale plan digest after selecting a second file.
- Destination provider/write failure.
- Database apply failure/transaction rollback.
- Dormant source action and later source availability transition.
- API 29, process recreation, rotation, split window, `fontScale = 2.0`, TalkBack and keyboard/DPAD.

### Screenshot/golden ownership

- `feature:library`: system/manual/smart/dormant/populated/search/multi-select states across required window/profile fixtures.
- `feature:backup`: dry-run warning, fatal error, applying, success/report and E-ink states.
- `feature:settings`: portable reader settings and E-ink retained-value explanation.
- `feature:book`: local add/remove membership and dormant prerequisite states.
- No app-module copied UI golden; production feature composables own references.

### Repository / hosted admission

- Protocol install/test/conformance steps actually execute when protocol files change.
- Android assemble/lint/JVM/Room migration/instrumentation/goldens and lock rewrite check actually execute.
- API 29 hosted instrumentation actually installs/starts the emulator and runs Gate 3 product tests.
- Root REUSE/artifact policy always executes.
- Extensions workflow may legitimately report no affected extension files, but the committed Wenku8 fixture checksum and local extension baseline are recorded as unchanged inputs.
- Check conclusion alone is insufficient; final evidence records target head and non-skipped substantive job steps.

## Risks and mitigations

| Risk | Source fix / proof |
|---|---|
| Browsed books silently become library books | Separate `book` from `library_entry`; regression test browse/read without add. |
| Legacy secret exposure in reports/logs | Structured warning codes/field names only; fixtures contain sentinel secrets and tests assert absence from output/log-safe models. |
| Partial import after crash/failure | Complete dry-run then one off-main Room transaction; cancellation before confirmation; failure rollback instrumentation. |
| Stale file plan applied after another selection | URI/document digest + generation bound confirmation token. |
| Dynamic smart SQL injection | Typed AST, bounded validator, enum-owned SQL fragments and bound args; malicious-term tests. |
| FTS/projection drift | Same Room transaction updates canonical row and projection; migration/rebuild test. |
| Source uninstall deletes user state | Availability projection only; uninstall transition tests preserve library/progress/organization. |
| Imported source causes network/write side effects | Pure planner and database apply have no extension/network dependency; instrumentation asserts zero source transport calls. |
| Room migration blocks existing Gate 2 users | Exported schema 1 fixture, explicit 1→2 migration, API 29 migration test and no destructive fallback. |
| Large import causes allocation/ANR | 32 MiB byte bound, item/string/AST/report limits, IO dispatcher, bounded UI summaries. |
| E-ink receives a second business path | Shared ViewModel/query state; only list presentation/paging policy differs. |
| Portable preference has no consumer | Implement DataStore + reader/settings consumer in the same commit; otherwise omit/hide and fail plan acceptance. |
| Transfer v1 cannot carry smart rules | Explicitly retain smart rules Android-local; document/report this boundary; no opaque extension field. |

## Decision register for user confirmation after Adviser review

The plan uses the recommended defaults below. Adviser may require a narrower or safer choice before these are presented for user confirmation.

| ID | Decision | Recommended default | Alternative / trade-off |
|---|---|---|---|
| D1 | When a source book becomes a library member | Explicit `加入书架`; browsing/reading alone never adds it. | Auto-add on first read is convenient but silently changes organization and makes history indistinguishable from membership. |
| D2 | Import apply mode | Merge-only, no destructive replace. | Replace mode simplifies clean restoration but risks deleting newer local data and needs a much larger rollback design. |
| D3 | Smart collection editor | Guided nested groups with exclusion toggle; no raw expression. | A flat one-group editor is simpler but would not expose the accepted AST contract; raw JSON is unsafe/unusable. |
| D4 | Collection deletion | Remove the collection/memberships, reparent child collections to the deleted parent, keep books/library data. | Recursive delete is simpler visually but destructive and harder to undo. |
| D5 | Removing a book from the library | Remove library entry, manual memberships, local rating/tags; preserve semantic progress and history until a separate future data-delete action. | Deleting progress too is more private but makes an organization action destructive. |
| D6 | Portable reader preferences | Implement and consume flow/font scale/line height/theme now; E-ink retains incompatible values but applies safe effective overrides. | Drop with warnings would fail the Gate 3 safe-preference exit contract. |
| D7 | Imported subscription drafts | Show read-only in collection management with an explicit “尚不可执行” explanation; no refresh action. | Hiding them avoids clutter but makes preserved migration data undiscoverable. |
| D8 | Source availability | Verified installed package means available; no new enable toggle in Gate 3. | A separate enabled switch adds source-management state/lifecycle beyond Gate 3. |
| D9 | Hikari histories | Import source-scoped search suggestions and browsing timestamps; do not include them in portable transfer. | Dropping them narrows scope but contradicts the accepted Hikari mapping contract. |
| D10 | Component versions | Android `0.2.0`/versionCode 2 and protocol `0.2.0`; extensions unchanged `0.1.0`. | Keeping `0.1.0` hides a new persistent schema and public transfer implementation boundary. |

## Review records

### Designer

- Target plan input: pending plan commit SHA.
- Verdict: pending.
- Blocking findings: pending.

### Adviser

- Target plan input: pending plan commit SHA after Designer closure.
- Verdict: pending.
- Blocking findings: pending.

## Authorization boundary

Designer and Adviser approval do not authorize implementation. After both reviews close, the user must confirm D1–D10 (or amended decisions) and explicitly authorize implementation. Any later expansion of data fields, UI routes, source capabilities, network behavior, transfer version or risk model invalidates the affected approval and returns Gate 3 to plan review.
