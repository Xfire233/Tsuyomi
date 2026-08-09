<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Gate 3 plan — local library and migration

## Status and review input

- Planner status: **AMENDED DRAFT FOR DESIGNER / ADVISER REVIEW**
- Implementation authorization: **NOT GRANTED**
- Gate 2 baseline tag: `gate-2-baseline`
- Gate 2 baseline commit: `cfbbf4f5d6af6fc216d85b496a6d7f4362616591`
- Planning branch: `feature/gate-3-local-library-migration`
- Hikari behavior reference: `Xfire233/hikari_novel_flutter_plus` commit `a1feba6d1dd8dbbdd2b5ae042e44f2ec54d26bef`
- Protocol baseline: `@tsuyomi/protocol` `0.1.0`, `tsuyomi-transfer` v1
- Android baseline: `org.tsuyomi.android` `0.1.0` / versionCode `1`, Room schema `1`
- UI impact: **YES** — library, local book details, source remote-library controls, collection management, smart-rule editor, data transfer/migration, source search history, and import reports.
- Security-sensitive impact: **YES** — untrusted JSON import, redacted legacy credential handling, bounded file I/O, deterministic export, database migration/conflict application, capability-gated remote-library read/add and user-mediated credential use.

This amended document is the common frozen input for the renewed Designer and Adviser reviews. D1–D14 are confirmed planning inputs, but the D1 scope expansion invalidated prior approval. No production implementation begins until the renewed reviews close and the user explicitly authorizes this amended plan.

## Outcome

A local-first reader preserves books, semantic progress, ratings, local tags and organization across process/device restoration; it safely imports supported Hikari data without credentials, browser state or caches, and exposes only user-mediated, signed-capability remote favourite actions.

Gate 3 is complete only when a clean API 29 profile can:

```text
install app
→ import a portable Tsuyomi or sanitized Hikari document through Android SAF
→ inspect a redacted dry-run plan
→ explicitly confirm
→ restore books, newest valid semantic progress and manual organization
→ browse/search local library under Standard and E-ink profiles
→ observe books as dormant while their source package is absent
→ install the signed Wenku8 fixture source at extension `0.2.0` and approve its declared remote-library read/add capability
→ deliberately complete the controlled login/verification flow
→ explicitly choose one-time remote-favourites import, then observe a merge-only local result
→ optionally enable add-only website writeback and explicitly add one book, with persisted reconciliation outcome
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
- Open a local book details screen from Room data. Dormant books remain readable as records; source-dependent actions are replaced by the message `此书的来源未安装。在「浏览」中安装对应签名来源后，书籍与进度自动恢复，无需重新添加。` and one explicit `前往浏览` action. The action switches to the Browse root; the library never embeds or automates source installation. Availability projection changes remove the dormant state immediately without manual refresh.
- Preserve search and browsing history imported from Hikari as host-owned source-scoped history. Search suggestions appear only for an installed matching source; browsing timestamps may drive local sort but do not add a book to the library.

### User-mediated remote favourites

- Only a verified active source whose signed manifest declares `remoteLibrary.read = true` may expose `导入远程收藏`; only one also declaring `writeOperations` containing `add` may expose the optional `新加入书架时同步到网站` setting. The Gate 3 acceptance implementation is Wenku8 `0.2.0`; ESJZone/Yamibo receive neither an inferred capability nor a source implementation in this Gate.
- After the first successful **user-mediated** controlled-login completion for an eligible source, return to the source remote-library screen and explicitly ask `导入该来源的远程收藏吗？`. `导入` executes one merge-only pull; `暂不导入` records that the prompt was dismissed and leaves an always-explicit manual `导入远程收藏` action. Login completion itself makes no network request beyond the user-controlled login session and never writes remotely.
- A remote-favourites pull is direct user intent, never launch/open-source/background work. It aggregates at most 100 source pages, 5,000 normalized records and 8 MiB of normalized aggregate data off-main; incomplete pagination, duplicate cursor, source/identity mismatch, unsafe URL, authentication/verification requirement, cancellation or any bound failure applies **no** records. A complete result is one Room transaction that upserts book metadata and explicit `library_entry` membership, preserving local rating/tags/progress/manual membership and never deleting local books absent from the remote result.
- `新加入书架时同步到网站` is off after fresh install, transfer import, Hikari import, source install/update, login completion and remote pull. The user may enable it only after the active package's separate `add` capability grant and a credential-ready source state. Its persistent grant reference is `(sourceId, publisherKeyId, approved capability fingerprint, declared origin)`; it disables itself when any part is no longer valid. Capability-preserving updates retain the setting.
- When the setting is enabled, only a direct `加入书架` click for that same source may enqueue one semantic remote `add`. The host commits the local library entry and a non-secret reconciliation operation atomically, then rechecks active package/version, capability grant, credential partition and direct-action intent immediately before invoking the source. No import, login/verification completion, local migration, remote pull, retry timer or background work can enqueue or execute an add.
- Remote `add` means idempotent semantic presence, not an opaque POST. A compatible extension must return a validated `applied` or `already-present` outcome for the requested identity; ambiguous timeout/cancellation/source-change outcomes remain visibly unresolved and can be retried only by a new explicit user action. Local membership remains present on every remote failure; the product never claims synchronization without a confirmed remote outcome.
- Gate 3 never removes, moves or selects remote folders/shelves. Local `移出书架` never mutates the website. No automatic, foreground-open or scheduled remote refresh exists; every later pull is the same explicit `导入远程收藏` action.

### Collections

- Manual collections use many-to-many membership and presentation-only hierarchy.
- Smart collections use a versioned bounded AST and live Room projection queries. No result membership list is persisted.
- The initial editor supports nested `全部满足` / `任一满足` groups and explicit exclusion, mapped to `All`, `Any` and `Not`; it never exposes raw JSON, SQL, regular expressions or extension code.
- Initial predicates are the already accepted local set: source, manual collection, normalized tag, author/title term, status, rating range, added/read/metadata time window, progress state, unread/source update and dormant source.
- Imported subscription configuration is retained as a disabled audit draft only. Gate 3 never creates a remote discovery request; the narrowly scoped remote-favourites actions above do not execute subscription logic.

### Portable transfer

- Export `tsuyomi-transfer` v1 through `ActivityResultContracts.CreateDocument`.
- Import `tsuyomi-transfer` v1 through `ActivityResultContracts.OpenDocument`.
- Read at most 32 MiB of UTF-8 JSON before parsing.
- Produce a deterministic export for the same database snapshot and injected `createdAt`: stable library ordering, stable shelf ordering, stable set ordering and stable JSON serialization.
- Before launching `CreateDocument`, canonical serialization writes to a bounded app-cache preflight file capped at exactly 32 MiB plus one sentinel byte. At 32 MiB the export may proceed; at 32 MiB + 1 the app deletes the preflight file, does not launch or mutate a SAF destination, and shows the accessible safe failure `transfer-too-large` with the 32 MiB limit. It never truncates, silently omits records or splits one v1 document. The remediation text tells the user that this format cannot represent the current snapshot within its bound and that they may reduce library/shelf/local-tag data before retrying.
- Every at-or-under-bound preflight is owned by `(ownerGeneration, canonicalDigest)` and is reusable only by that still-current export operation after digest verification. Picker cancel, successful destination close, provider open/write/close failure and owner destruction delete it in `finally`; a startup/foreground sweeper deletes orphaned or stale export preflights after process death. Missing files are already-clean success. No callback from an older generation may reuse or delete the current operation's file, and no success is persisted before destination close succeeds.
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

- Remote favourite removal, remote folder/shelf selection or move, bidirectional reconciliation, automatic foreground refresh, scheduled/background network work, or any remote mutation other than the explicit capability-gated add defined above.
- Source subscription execution, candidate refresh or remote discovery API changes.
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
10. Manifest v1 already names `remoteLibrary.read` and `writeOperations`, but Host API/source-contract/runtime expose no typed remote-favourites DTO or capability-enforced read/add invocation. Gate 3 must add one public path; a private Android/Wenku8 shortcut is prohibited.

## Component and module plan

### `tsuyomi-protocol`

- Keep `tsuyomi-transfer` wire version at v1.
- Clarify deterministic transfer producer requirements for a fixed snapshot/clock without adding secret or arbitrary extension fields.
- Freeze HXP Host API `1.1.0` remote-library contracts while retaining manifest version 1: `listRemoteLibrary({cursor?}) → {items,nextCursor?,complete}` and `addRemoteLibrary({remoteBookId}) → {identity,outcome=applied|already-present}`. Every item is a normalized `SourceBookSummary`; cursors are opaque bounded strings and never persisted as user data. The host validates source ID, stable identity, URLs, item/page bounds, completeness and exact return identity.
- Host API compatibility for the Wenku8 fixture becomes `minInclusive = 1.1.0`, `maxExclusive = 2.0.0`; an older host rejects the package before evaluation. Manifest `remoteLibrary.read` and `writeOperations` remain the authoritative install/update grant declarations.
- Add valid/invalid fixtures for transfer shelves, dangling membership, parent cycles, duplicate identities, deterministic ordering, tags/ratings, progress ties, unsupported version and maximum-size boundaries.
- Add Host API remote-library fixtures for empty/multi-page complete reads, duplicate/loop cursor, incomplete page, wrong source/identity, unsafe URL, over-bound page/item count and both add outcomes.
- Add sanitized `hikari_novel_backup` v1 fixtures covering Wenku8/ESJ/Yamibo identities, manual folders, compatible/incompatible smart rules, disabled subscription metadata, progress fallback and non-empty secret fields.
- Extend conformance to distinguish fatal envelope/graph errors from recoverable independent record warnings and to reject any remote-library invocation without the exact active capability grant.
- Proposed component version after implementation: `0.2.0`.

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

### `shared:source-contract` and `source:extension-manager`

- Add bounded host-owned `RemoteLibraryPage`, `RemoteLibraryItem`, `RemoteLibraryAddRequest` and `RemoteLibraryAddResult`; no raw HTML, request body, cookie, remote shelf payload or JavaScript value crosses the boundary.
- `SourceExtensionClient.listRemoteLibrary(cursor)` requires the active manifest's granted `remoteLibrary.read`; `addRemoteLibrary(remoteBookId, directActionToken)` requires granted `writeOperations.add`, enabled source policy and a single-use host token minted by the direct library action. Both recheck package identity/version and capability fingerprint immediately before every network hop and parse.
- The client invokes only reviewed extension exports (`buildRemoteLibraryRequest` / `parseRemoteLibrary`, `buildRemoteLibraryAddRequest` / `parseRemoteLibraryAdd`). Operation context is immutable and cannot be promoted by JavaScript output. Read calls cannot reuse an add token; a token is bound to source, book identity, reconciliation ID, package version and owner generation.
- Pagination cursor cycles, replayed tokens, mismatched return identities, source update/revocation, terminal QuickJS failure, deadline, cancellation and close follow existing per-operation isolation. No runtime or executor survives its feature owner.
- Add-result validation admits only `applied` / `already-present`; raw success text or HTTP 2xx alone never marks reconciliation confirmed.

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
import_session(id, kind, planDigest, normalizedPlanPath, status,
               sourceCreatedAt, startedAt, completedAt?, preferencePatchJson, summaryJson)
import_warning(sessionId, ordinal, safeCode, safeRecordRef?, fieldName?)
source_remote_policy(sourceId, approvedPublisherKeyId, approvedCapabilityFingerprint,
                     approvedOrigin, addWritebackEnabled, firstImportPromptState)
remote_library_reconciliation(id, sourceId, remoteBookId, operation=add,
                              packageVersion, capabilityFingerprint, state,
                              createdAt, updatedAt, safeCode?, diagnosticId?)

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
- Confirmed Transfer/Hikari import uses the durable cross-store journal defined below. Room mutations remain one off-main transaction, but Gate 3 does not claim that Room and DataStore share a physical transaction.
- Import warning/audit records are redacted and contain no credential values or raw invalid payload fragments.
- `source_remote_policy` contains no credential or cookie data. Enabling add writeback requires a currently valid package grant; invalidation disables it before any call.
- `remote_library_reconciliation` is created only by the direct source-detail `加入书架` handler in the same Room transaction as local membership. At most one nonterminal add exists per book/source. States are `PENDING_USER_ACTION`, `IN_FLIGHT`, `CONFIRMED`, `UNRESOLVED`, `FAILED_SAFE` and `CANCELLED`; only a new explicit user `重试同步` action may move a nonterminal row back to `IN_FLIGHT`.

#### Room 1 → 2 backfill

- Before adding the `collection_book → library_entry` invariant, migrate every distinct schema-1 `manual_collection_memberships` book into `library_entry`.
- The backfilled `library_entry.addedAt` is the existing schema-1 `book.addedAt`; no migration-time clock is used.
- Existing collection IDs, parent links and collection `displayOrder` are preserved exactly.
- Schema 1 has no per-membership order. Version 2 derives deterministic `collection_book.displayOrder` within each collection by `book.addedAt`, then `sourceId`, then `remoteBookId`; `collection_book.addedAt` also uses `book.addedAt`.
- A schema-1 book that has progress or browse metadata but no manual membership remains a non-library book row. This preserves D1: historical browsing/reading alone does not silently become library membership.
- The migration fixture contains nested collections, multiple memberships, books with and without progress and one nonmember progress row; all keys, hierarchy, collection order, backfilled membership and semantic progress are asserted after migration.

#### Normative system collection and progress queries

`valid progress` means a `ReadingProgress` row accepted by the existing semantic-locator validator: timestamp valid, numeric fallbacks within range and at least one semantic/fallback location. Invalid rows are treated as absent and are replaced only by a later valid write/import.

| Definition | Exact membership | Default order |
|---|---|---|
| 全部书籍 | Every `library_entry`. | `addedAt DESC`, then `(sourceId, remoteBookId) ASC`. |
| 继续阅读 | `library_entry` with valid progress and `bookProgress IS NULL OR bookProgress < 1.0`. Locator-only progress is reading, not unstarted. | progress `updatedAt DESC`, then stable identity. |
| 最近阅读 | `library_entry` with any valid progress, including finished. No hidden time cutoff; explicit pagination bounds the view. | progress `updatedAt DESC`, then stable identity. |
| 有未读更新 | `library_entry` whose canonical book has `hasUnreadUpdate = true`. | `metadataUpdatedAt DESC`, then stable identity. |
| 来源未安装 | `library_entry` with no currently available verified `source_availability` row. | `addedAt DESC`, then stable identity. |
| `ProgressIn(unstarted)` | No valid progress row. | Query predicate only. |
| `ProgressIn(reading)` | Valid progress and `bookProgress IS NULL OR bookProgress < 1.0`. A valid `bookProgress = 0` is reading because a semantic capture exists. | Query predicate only. |
| `ProgressIn(finished)` | Valid progress and `bookProgress = 1.0`. | Query predicate only. |

All user-selected sorts use stable identity as the final ascending tie-breaker. Title/author/source sorts use normalized ascending text with null/blank last. Added, last-read and metadata-update sorts are descending with null last. Rating is descending with unrated last. Progress uses `bookProgress` descending; locator-only valid progress sorts after numeric progress and before unstarted, then `updatedAt DESC`. Time-window smart predicates are inclusive at `>= injectedClock.now - duration`; missing timestamps do not match. Rating ranges are inclusive. Unknown status matches only an explicit `unknown` predicate.

Equal timestamps never use percentage, display order or import order as a conflict tie-breaker.

`authorSortKey(authors)` is one shared canonical function, not a UI- or query-local interpretation. Each author is Unicode NFKC-normalized, Unicode whitespace runs collapse to one ASCII space, ends are trimmed and case is normalized with `Locale.ROOT`; blanks are discarded. Values are sorted by Unicode scalar-value lexicographic order and deduplicated. Their UTF-8 bytes are encoded in that order as a BLOB by escaping byte `0x00` as `0x00 0xFF` and terminating each value with `0x00 0x00`; an empty set is SQL `NULL` and sorts last. Room search projection, author predicates/sort/pagination and transfer set ordering use this exact function. Author sort is `authorSortKey ASC`, then stable identity.

#### Durable cross-store import journal

Room and DataStore cannot share one physical transaction. Gate 3 therefore guarantees crash-atomic **user exposure** with an application-owned recovery gate and an idempotent journal.

Normative states are `PREPARED`, `ROOM_APPLIED`, `PREFERENCES_APPLIED`, `COMPLETED`, `ABORTED` and `ABORTED_CLEANUP_PENDING`; no implementation-private status may bypass the transition/recovery rules below.

1. After confirmation, serialize the already redacted/normalized `ImportPlan` to a bounded `NO_BACKUP` journal file, compute SHA-256, then insert an `import_session(PREPARED)` row containing that digest, path and the non-secret preference patch. A file without a session is an orphan and is deleted; a session never references a file whose digest was not verified.
2. While a session is `PREPARED`, the import route remains in applying/recovery state. One Room transaction rechecks the digest, applies all canonical database records idempotently and changes the session to `ROOM_APPLIED`.
3. DataStore then applies portable reader preferences and the Hikari global display preference in one `updateData`, together with `lastAppliedImportDigest`.
4. Room changes the session to `PREFERENCES_APPLIED`; journal cleanup and the final `COMPLETED` transition are idempotent. If final Room bookkeeping fails after DataStore succeeds, `lastAppliedImportDigest` proves that preference replay is unnecessary and recovery completes the remaining Room transition.
5. Application startup runs `ImportRecoveryCoordinator` before exposing the normal navigation graph. `PREPARED` resumes Room apply; `ROOM_APPLIED` replays the DataStore patch; `PREFERENCES_APPLIED` finalizes cleanup. Normal library/reader/settings content is never exposed while a confirmed mutating session is incomplete. The same startup/foreground sweeper processes `ABORTED` / `ABORTED_CLEANUP_PENDING` journal cleanup without blocking normal navigation because those states contain no canonical or preference mutation.
6. Before Room reaches `ROOM_APPLIED`, a persistent apply failure exposes explicit `重试` and `中止导入` actions. `中止导入` is a user action whose Room transaction verifies the session/digest and records the redacted audit as `ABORTED`; canonical user data is already rolled back and no preference patch was applied. Journal deletion is a separate idempotent cleanup step: missing is success, failure records `ABORTED_CLEANUP_PENDING`, remains visible with `重试清理` under `more/data`, and is retried on every startup/foreground until the file is absent, after which the retained audit returns to `ABORTED`. After `ROOM_APPLIED`, the operation is non-cancellable and the failure surface offers `重试` only until preferences/finalization complete.

Every transition and cleanup action is conditional on the same session ID and plan digest. Selecting another file cannot retarget a confirmed session. The normalized journal contains no credentials, raw rejected fields, Cookie values, cache or WebView state. It is deleted after completion or abort cleanup; the redacted `ABORTED` audit remains.

### Source availability

- Add an application-owned `InstalledSourceRegistry` over verified `InstalledExtensionStore` records.
- At startup and after install/remove/update, reverify active archives, publish the sorted available source set and synchronize `source_availability` transactionally.
- Gate 3 adds no independent source-enabled toggle. A source is available only when a verified active package is present; otherwise its books are dormant.
- Remote-policy reconciliation runs with the same verified snapshot. Missing/revoked package, publisher/grant mismatch, removed `read`/`add`, origin change or capability fingerprint change disables the affected action before UI dispatch; it does not delete local books or progress.
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
- Collection and membership reorder is available only in edit mode through explicit per-row `上移` / `下移` actions with at least 48dp targets. There is no drag-only path. Standard and E-ink use the same commands; E-ink replaces state immediately without animation.
- The library two-pane breakpoint is the existing 600dp application breakpoint. At 600dp and above, collection navigation and content are stable sibling panes. Below 600dp, including 360×320 double-compact, the collection selector is a single-pane top control.
- E-ink list paging reuses `core:ui` `PaginationBar`; no feature-local pagination component is introduced. Multi-selection survives page changes and exposes a persistent textual selected count. Removing the last item on the last page moves to the previous valid page and announces the new page through an accessibility live region.

### Source book details

- Extend the existing source detail screen with real local membership state and `加入书架` / `移出书架` actions.
- With add writeback disabled, `加入书架` writes only local state. With a valid enabled add policy, the button explicitly reads `加入书架并同步到网站`; one Room transaction persists local membership plus reconciliation, then the same direct handler attempts the remote add. Local `移出书架` is always local-only.
- Rating, local tags and manual collection membership are edited through host-owned routes/dialogs and survive source removal.

### `feature:browse` remote-library controls

- Add `browse/source/{sourceId}/remote-library`, entered from an eligible installed source and as the controlled-login return target. Route arguments contain only stable source ID.
- Show signed capability/grant status, credential-ready state, one explicit `导入远程收藏` action, and the default-off `新加入书架时同步到网站` switch only when `add` is granted. Enabling requires a confirmation naming the source, website effect and local-only removal behavior.
- The post-login prompt text is `已完成来源登录。是否导入该来源的远程收藏？`, with `导入收藏` and `暂不导入` actions; it has no preselected writeback state. Add writeback confirmation states `新加入书架时将同步到网站；移出书架不会删除网站收藏。` with explicit enable/cancel actions. Dialog/action containers honor safe-drawing insets; TalkBack focus enters the prompt/status text and each state change announces through a live region.
- The first credential transition from absent to user-confirmed present offers the import question once. Dismiss/failure never starts a hidden retry and the manual action remains available.
- Pull states are idle, confirmation, loading with page/count text, success summary, empty, cancellation, login required, verification required, incomplete/limit/source-changed failure and retry. E-ink uses immediate textual state replacement and no indeterminate animated spinner.
- Reconciliation state appears on source and local book details as `已同步`, `网站未确认`, `同步失败` or `等待确认同步`, with explicit `重试同步` only for a still-valid add capability. No color-only status is used.

### `feature:backup`

- Add a `数据与迁移` route under `更多`.
- Actions: `导出 Tsuyomi 数据`; `导入 Tsuyomi 数据`; `从 Hikari Novel 导入`; and `查看最近导入报告` when a redacted report exists.
- Use Android SAF only. No broad storage permission.
- Import flow: select → bounded read → parse/plan → redacted warnings/conflicts → explicit confirm → durable journal/recovery gate → persistent result.
- Cancel before confirm performs no mutation. After Room reaches `ROOM_APPLIED`, the confirmed import is non-cancellable and offers idempotent retry until DataStore/finalization completes. File-picker cancel returns unchanged state without an error.
- Export flow computes a stable snapshot, canonical preflight bytes and digest before launching the destination picker. Its `(ownerGeneration, canonicalDigest)` lifecycle deletes the preflight on cancel, successful close, provider open/write/close failure, owner destruction or startup orphan sweep. `transfer-too-large` never launches `CreateDocument`; provider/write failure is reported; no success is claimed before close succeeds.

### `app`

- Wire existing boundary modules; do not move parser, database or UI logic into `MainActivity`.
- Add routes for local book details, collection management/editor, smart-rule editor, reader preferences, data/migration and source remote-library controls.
- Preserve root navigation and process recreation state with stable IDs only; never place imported JSON, remote result pages or book lists in `SavedStateHandle`/DataStore navigation snapshots.

### `tsuyomi-extensions`

- Bump the deterministic signed Wenku8 fixture extension to `0.2.0`, Host API minimum `1.1.0`, and declare `remoteLibrary.read = true`, `writeOperations = ["add"]`. The install/update approval UI must display both capability additions and bind them into the approval fingerprint.
- Implement fixture-backed complete remote-favourites pagination and idempotent add presence using sanitized HTML; add login/challenge/incomplete/duplicate-cursor/wrong-identity/add-applied/add-already-present/ambiguous fixtures.
- Production and test code never embeds a real account, Cookie or private publisher key. Fixture Host transport is authoritative; anonymous live Wenku8 remains best-effort and never bypasses login/challenge.
- Two fixture builds remain byte-identical and publish a new reviewed SHA-256. Extensions required CI runs locked install, build, expanded tests, two packages, checksum and clean diff.
- If implementation discovers that a public source/remote-library DTO cannot represent Wenku8 safely, stop, update this plan/protocol and repeat review; do not add an Android-only parser shortcut.

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

### Remote-favourites merge and reconciliation

- A complete remote pull is merge-only. It adds explicit local `library_entry` rows and source metadata for returned stable identities; it never deletes local state, overwrites local rating/tags/progress/manual organization, enables writeback or modifies remote data.
- A direct local add with enabled writeback atomically creates local membership plus `PENDING_USER_ACTION`. The host attempts exactly one add under a single-use token; a confirmed semantic outcome transitions to `CONFIRMED`. Cancellation, deadline, package change/revocation or ambiguous transport leaves local membership intact and records `CANCELLED` / `UNRESOLVED`; a parsed safe source failure records `FAILED_SAFE`. No state is labelled synchronized before `CONFIRMED`.
- Retry is a new direct user action, not a worker, startup action or response to re-login. It revalidates the active package, grant, credential partition and book identity before creating a new token. `移出书架` neither retries nor cancels a remote mutation already dispatched.

## Option applicability matrix

| Entry/action | Visibility and effect |
|---|---|
| 加入书架 | Visible on a valid source detail when not locally added. Normally writes only host library state; if the user separately enabled a current signed `add` grant, the explicit label becomes `加入书架并同步到网站` and shows persistent reconciliation state. |
| 移出书架 | Visible for a library entry; confirmation explains preserved progress/history. |
| 打开来源/目录 | Visible when a verified source is available. Dormant state shows `此书的来源未安装。在「浏览」中安装对应签名来源后，书籍与进度自动恢复，无需重新添加。` plus a real `前往浏览` handler; the library does not offer source installation. |
| 评分/本地标签 | Visible for a library entry; durable local-only write with failure recovery. |
| 手动集合编辑 | Visible for manual collections only. System collections cannot be renamed/reordered/deleted; smart collections reject direct membership writes. |
| 智能规则编辑 | Visible for supported rule version; unknown versions are read-only disabled with explanation. |
| 来源订阅刷新 | Hidden in Gate 3 because no discovery consumer exists. Imported drafts are visible read-only only to explain retained blocked data. |
| 导入远程收藏 | Visible only for an installed, verified source with a granted `remoteLibrary.read` capability. After a user-mediated credential handoff, the first result is an explicit import question; later pulls remain explicit and merge-only. |
| 新加入书架时同步到网站 | Visible only in that source’s remote-library controls when separately granted `add` and credential-ready. Default off; confirmation names the source and website effect. Local removal remains local-only. |
| 导出 Tsuyomi 数据 | Visible because a real bounded preflight, SAF writer and result/error state exist. If canonical bytes exceed 32 MiB, `transfer-too-large` is shown before any destination is opened. |
| 导入 Tsuyomi/Hikari | Visible because bounded parser, dry-run, confirmation, durable cross-store journal, recovery gate and report exist. |
| 导入 legacy credential | Never offered. A warning states that sign-in data was deliberately skipped. |
| E-ink list pagination | Visible only when `effectiveProfile == EINK`; Standard uses its normal list behavior. |
| Reader theme incompatible with E-ink | Retained and shown disabled with the existing Standard-restoration explanation pattern. |
| 本地搜索/排序 | Visible when the library contains any book; hidden for a completely empty library. It remains usable when every result is dormant because it never depends on a source runtime. |
| 多选/批量操作 | Visible when the current collection is nonempty. Standard and E-ink use explicit actions with persistent selected-count text; write failure retains selection and offers retry. |
| 集合重排 | Visible only for manual collections as explicit `上移` / `下移` actions. System, smart and subscription-draft rows do not expose reorder because their membership/order semantics are not manually writable. |
| 查看最近导入报告 | Visible only when a completed `import_session` exists; otherwise hidden. The report contains only redacted codes, safe record references and field names. |
| 来源搜索历史建议 | Visible on source search only when the matching source is installed and verified. Dormant-source history is retained but does not produce suggestions. |

## UI and interaction acceptance

Designer review must bind the final information architecture and visual hierarchy. The implementation may not invent new screens beyond this reviewed list.

### Information architecture and routes

```text
Library root
├── library
├── library/book/{sourceId}/{remoteBookId}
├── library/collections
└── library/collections/{collectionId}/rule

Browse root
├── existing source detail/directory/reader routes
└── browse/source/{sourceId}/remote-library

More root
├── more/reader
├── more/data
└── more/data/report/{sessionId}
```

- `library/book/{sourceId}/{remoteBookId}` is a host-owned Room screen. Route arguments are stable IDs only.
- `library/collections` is entered through a `管理集合` item in the compact collection selector and expanded collection pane. It contains `新建手动集合`, `新建智能集合`, and edit actions for existing user collections.
- `library/collections/{collectionId}/rule` is entered from `新建智能集合` or an existing smart collection's edit action.
- `more/reader` is the portable reader-settings screen owned by `feature:settings`.
- `more/data` is owned by `feature:backup`. `查看最近导入报告` opens `more/data/report/{sessionId}` for the latest completed session; the stored session ID permits later report-history expansion without changing the route contract.
- The existing Browse source-detail route gains local add/remove actions but remains source-owned.
- `browse/source/{sourceId}/remote-library` is a source-owned, host-composed capability screen. It is entered only from a verified installed source, or returned to after the user finishes/cancels the existing controlled WebView. Back returns to the prior Browse source surface; it never auto-starts login, pull or writeback.
- `打开来源/目录` from a local book switches to the Browse root and opens/restores that stable book through the existing source flow. System Back remains within the Browse root. Selecting Library again uses the existing root `saveState`/`restoreState` behavior to restore the library stack, selected collection, page and selection state.
- `ImportRecoveryCoordinator` is a pre-navigation app-root surface, not a navigable destination. `feature:backup` owns its content and `app` composes it in place of `NavHost` while a confirmed session is incomplete. It reuses `StateView` with fixed chrome and persistent text; E-ink replaces states immediately without an indeterminate animated spinner. TalkBack initial focus moves to the recovery status text, transition announcements use a live region, and every recoverable state exposes the explicit actions defined by the journal state machine.


Required states:

- Library: empty, populated, selected collection, local search results, no results, multi-select, dormant source, write failure.
- Collections: system/manual/smart/disabled subscription draft, create/edit validation, cycle/depth rejection, delete/reparent confirmation.
- Local book: active source, dormant source, rating/tags, multiple collection membership, progress summary and remote-add reconciliation state.
- Source remote library: capability/grant absent, credential absent, first-login import question, manual pull confirmation/loading/empty/success, cancellation, session/verification required, incomplete/limit/source-change failure, default-off add setting confirmation, pending/confirmed/unresolved/failed add reconciliation and explicit retry.
- Import: picker cancelled, parsing, fatal document error, dry-run with warnings/conflicts, confirmation, applying, startup recovery resume, pre-`ROOM_APPLIED` persistent failure with explicit `重试` / `中止导入`, post-`ROOM_APPLIED` non-cancellable retry-only failure, non-blocking `已中止，等待清理` with `重试清理`, success and persisted report.
- Export: empty/nonempty snapshot, canonical preflight, `transfer-too-large`, destination cancelled, writing, success with digest, provider failure.
- Reader preferences: Standard effective values and E-ink retained-but-overridden explanation.
- Smart rule editor: empty group, valid nested groups, per-predicate parameter validation, AST-bound rejection (depth, nodes, term length and terms per predicate) with the offending node identified in text, unsupported rule version as read-only disabled state, save failure with the draft retained, and back navigation with unsaved-changes confirmation.

Window/profile matrix:

- 360×800 portrait, 800×360 landscape, 360×320 split/double-compact, 599dp boundary-below, 600dp boundary-at and 840×900 expanded.
- `fontScale = 1.0`, `1.3` and `2.0`.
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
- Import journal/recovery, export snapshot and smart query work have explicit owners and cancellation boundaries. Late results cannot replace a newer query/selection; confirmed incomplete import sessions are recovered before the normal navigation graph is exposed.
- Database migration 1→2 has no destructive fallback. Migration failure aborts app database open and is covered by schema migration instrumentation.
- Export preflight uses a capped temporary cache file and retains at most 32 MiB plus one sentinel byte. Exact-bound output may proceed; over-bound output is deleted before SAF launch. No unbounded warning list, AST, search term or UI result list is retained.
- Import/export never touches source credential storage, HXP trust/grants or cache roots.
- Remote favourites remain host-mediated. Credential readiness is an opaque host result; extensions never see Cookies, WebView state, account names or raw pages. `remoteLibrary.read` / `add` are separately capability-approved, revalidated at dispatch and bounded to declared HTTPS origins.
- No automatic remote call exists: initial prompt, pull, add and retry each require a direct visible user action. A single-use add token is invalidated on owner destruction, source/package/grant change and cancellation; late work cannot target a later click. Any uncertain outbound result stays unresolved rather than being retried or called successful.

## Implementation sequence and revert boundaries

Each commit includes its tests and affected documentation.

1. `test(protocol): complete Gate 3 transfer, Hikari and remote-library fixtures`
   - Transfer/Hikari plus Host API 1.1 remote contracts/conformance only; revert independently before Android consumes new guarantees.
2. `feat(model): define backup, smart collection and remote-library contracts`
   - Fill `shared:backup`, `shared:smart-shelf` and typed `shared:source-contract`; pure JVM tests.
3. `feat(database): migrate local library schema for Gate 3`
   - Room 1→2 migration, projections, import audit, remote policy/reconciliation and migration tests.
4. `feat(source): enforce verified availability and remote capability actions`
   - Registry, extension-manager typed read/add client, grants, direct-action token and reconciliation; no UI-owned network shortcut.
5. `feat(extensions): add signed Wenku8 remote favourites fixture`
   - HXP 0.2.0, capability escalation approval, sanitized remote fixtures and deterministic package evidence.
6. `feat(preferences): add portable reader settings`
   - DataStore state, reader consumer and settings UI/goldens.
7. `feat(library): deliver local collections, search and remote-library controls`
   - Library/local-book/Browse UI, system/manual/smart collections, explicit pull/add reconciliation, Standard/E-ink behavior and tests.
8. `feat(backup): deliver transfer and Hikari import`
   - SAF routes, dry-run/apply/report, clean-profile instrumentation and fixtures.
9. `test(gate3): record end-to-end admission evidence`
   - Gate document evidence, AVD recipes, checksums and known boundaries.

Rollback is forward-only whenever an import journal or remote reconciliation may exist. A rollback build hides Gate 3 import/export and remote-library entry points/settings, retains `import_session`, normalized-plan parser/digest verification, `lastAppliedImportDigest`, pre-navigation coordinator, recovery/abort UI and import/export sweepers. It also retains `source_remote_policy` and reconciliation rows but dispatches **no** remote call; any `PENDING_USER_ACTION` / `IN_FLIGHT` row becomes visibly `UNRESOLVED` without retry. Every build accepting a direct upgrade from Gate 3/schema 2 retains these compatibility paths; it never relies on release-wide state assertions or telemetry. A future on-device database-open/pre-navigation migration may replace them only after consuming every defined import-journal and remote-reconciliation state/file form while preserving no-partial-exposure and no-implicit-write contracts. Schema 2 user data is retained and never destructively recreated.

## Verification and acceptance matrix

### Protocol / pure JVM

- Transfer v1 valid and invalid schema fixtures; fatal duplicate/dangling/cycle/order/size/version cases; deterministic export bytes for fixed snapshot/clock and two repeated serializations.
- Hikari identity mapping, secret redaction, record recovery, disabled-draft translation, smart AST bounds/all-any-not precedence, unknown version, time boundaries, normalized terms and conflict matrix for newer/older/equal/invalid/intentional backward progress.
- Host API 1.1 remote-library contract fixtures: bounded complete list pagination, malformed/incomplete/looped cursor rejection, unsafe URL/source/identity rejection, add identity/outcome validation, capability denial and deterministic fixture semantics.

### Room / Android unit and instrumentation

- Schema 1→2 migration preserves every Gate 2 book and semantic progress row; nested collection rows/order remain exact, schema-1 manual members receive deterministic `library_entry`/membership backfill, and nonmember progress rows remain non-library.
- Explicit library membership does not appear from browse metadata or progress alone. Manual membership uniqueness, deterministic backfill/order repair, delete/reparent and concurrent cycle assignment are covered.
- Normative system/progress query fixtures cover absent/invalid/locator-only/0/fractional/1 progress, equal timestamps, null metadata, dormant transitions, time boundaries and stable sort ties. Canonical author-key fixtures cover permuted, duplicate, mixed-case, Unicode, blank and multi-author inputs across query/migration/transfer round-trip.
- FTS/local search escaping and parameter binding; malicious terms cannot alter query shape. Smart query invalidation follows rating/tag/progress/source-availability changes.
- Remote policy/reconciliation tests prove default off after fresh/transfer/Hikari/install/update/login/pull; package-grant-origin invalidation; at-most-one nonterminal add; local membership atomic with operation; direct-token single use/generation binding; no automatic retry; local removal has no remote call; retry produces a new action/token; process death leaves an unconfirmed add unresolved.
- Controlled WebView and remote transport are proved separately: user-confirmed/cancelled declared-origin handoff tests protect the credential partition, while source fixture tests inject only an opaque test credential partition and prove read/add gating. No test supplies a real account, password or Cookie.
- Cross-store import journal fault injection covers `PREPARED`, Room commit, DataStore apply, Room finalization and completion cleanup. Abort tests inject failure/process death before/after `ABORTED` and before/during/after file deletion; restart leaves canonical/preferences unchanged, does not incorrectly block normal navigation, retains only redacted audit and eventually removes the journal.
- Direct-upgrade matrix opens every Gate 3 import-journal and remote-reconciliation state/file snapshot in the current schema-2-compatible build, skipping any feature-disabled intermediate. It recovers or safely marks unresolved before `NavHost`, without a remote call or retained journal.
- Process death/recreate restores selected stable collection/page/report state without serializing whole payloads.

### Product end-to-end on API 29

1. Install signed Wenku8 `0.2.0` fixture and approve its displayed `remoteLibrary.read` / `add` expansion. Verify a source without the matching grant has neither pull nor writeback control.
2. Exercise controlled-login cancellation/completion handoff separately without real credentials. With the deterministic host-owned test credential partition, enter `browse/source/org.tsuyomi.wenku8/remote-library`, explicitly confirm remote-favourites pull and verify complete multi-page results merge into local library with no remote write.
3. Explicitly enable add-only writeback; search `fixture`, open `雾港纪事`, invoke `加入书架并同步到网站` and observe the persisted fixture reconciliation outcome. Remove it locally and verify no second remote request occurs.
4. Set local rating/tags, add books to two manual collections, create a matching smart collection and read `第一章 雾中的灯塔` to the second semantic block. Kill/recreate: library organization, local search, remote reconciliation and semantic locator restore.
5. Export through the real system document picker and record SHA-256. Clear the app profile; import it through the real picker. Before source install, the book appears in `来源未安装` with rating/tags/shelves/progress intact and no source action.
6. Install the same signed Wenku8 fixture. The existing stable book becomes available without duplication; opening the reader restores the imported semantic locator. Remote writeback remains off.
7. Repeat the library/import/remote-controls path under forced E-ink with explicit list pagination, fixed chrome, immediate state replacement and no decorative animation.
8. Import the sanitized Hikari fixture. Verify Wenku8/ESJ/Yamibo identity mapping, compatible manual/smart organization, disabled subscription draft, reduced-precision progress warning and credential field-name warnings with no values; remote policy remains off.

### Negative and recovery product paths

- Picker cancel; unsupported format/version; 32 MiB + 1 byte import rejection; invalid UTF-8/JSON; duplicate identity/dangling shelf/parent cycle; malformed Hikari record recovery; stale plan digest; destination provider/write and database-apply failure.
- Exact and below-bound exports cover picker cancel, destination success, provider open/write/close failure, owner destruction/rotation and process death/relaunch; each leaves no preflight artifact and never reports false success. Exact 32 MiB may proceed. A 32 MiB + 1 export reports `transfer-too-large`, does not launch `CreateDocument` and leaves no destination/temp artifact.
- Process death and injected failure at every import journal transition run startup recovery before normal navigation exposure.
- Remote library covers missing `read`/`add` grant, disabled writeback, absent credential, session/verification required, user declines first import, cancel before pull apply, empty/multi-page success, cursor loop/incomplete/5,000-record or 8 MiB normalized aggregate bound, source/identity mismatch, source update/revocation during action, replay/late token, add-safe failure, ambiguous timeout/cancellation, explicit retry, local removal and process death. Every forbidden/failed path proves no unintended remote request or false synchronization claim.
- Dormant source action and later source availability transition; API 29, process recreation, rotation, split window, `fontScale = 2.0`, TalkBack and keyboard/DPAD.

### Screenshot/golden ownership

| Owner | Required states | Windows | Profiles / font scale | Fixture rationale |
|---|---|---|---|---|
| `feature:library` | empty, populated system/manual/smart, dormant, search/no-result, multi-select, collection manager, smart editor, local book active/dormant/rating-tags/multi-membership/progress and reconciliation state | full shell set: 360×800, 800×360, 360×320, 599×800, 600×800, 840×900; state-heavy variants at 360×800, 600×800 and 840×900 | standard-light, standard-dark, E-ink at 1.0; populated/local-book/manager at 1.3 and 2.0 on 360×800 and 600×800 | All breakpoints prove pane cutover; representative windows carry the combinatorial business states. |
| `feature:book` | source detail not-in-library, added-local-only, added-with-pending/confirmed/unresolved/failed add, remove-confirmation and write failure | 360×800, 600×800, 840×900 | standard-light and E-ink at 1.0; reconciliation states at 2.0 on 360×800 | This module owns book-level direct local/add reconciliation actions. |
| `feature:browse` | remote capability/grant absent, credential absent, first-login import question, pull confirmation/loading/empty/success/cancel/failure, writeback confirmation, add reconciliation/retry | 360×800, 600×800, 840×900 | standard-light, standard-dark, E-ink at 1.0; question/failure/retry at 2.0 on 360×800 | Source capability is explicit, no state is color-only, and compact text must not clip. |
| `feature:backup` import | parsing, fatal, dry-run warnings/conflicts, confirmation, applying, startup recovery resume, pre-`ROOM_APPLIED` retry/abort failure, post-`ROOM_APPLIED` retry-only failure, aborted-cleanup retry, success and persisted report | 360×800, 600×800, 840×900 | standard-light, standard-dark, E-ink at 1.0; dry-run/report/recovery/cleanup failures at 2.0 on 360×800 | Compact, pane boundary and expanded report density are the material layouts; pre-navigation recovery remains feature-owned content while aborted cleanup remains non-blocking under `more/data`. |
| `feature:backup` export | empty/nonempty snapshot, `transfer-too-large`, writing, success with digest/summary, provider failure | 360×800, 600×800, 840×900 | standard-light and E-ink at 1.0; `transfer-too-large` and provider failure at 2.0 on 360×800 | Export has distinct progress/success/failure semantics and cannot borrow import references. |
| `feature:settings` | portable reader settings under Standard; values retained but effectively overridden under E-ink; write failure | 360×800, 600×800, 840×900 | standard-light, standard-dark, E-ink at 1.0, 1.3 and 2.0 | Reuses the established settings and retained-value pattern at compact/pane/expanded widths. |

No app-module copied UI golden is permitted; production feature composables own every reference.

### Repository / hosted admission

- Protocol install/test/conformance steps actually execute when protocol files change.
- Android assemble/lint/JVM/Room migration/instrumentation/goldens and lock rewrite check actually execute.
- API 29 hosted instrumentation actually installs/starts the emulator and runs Gate 3 product tests.
- Extensions baseline actually runs locked install, build, expanded remote-favourites tests, two fixture packages, checksum and clean-diff proof after Wenku8 `0.2.0` changes.
- Root REUSE/artifact policy always executes.
- Check conclusion alone is insufficient; final evidence records target head and non-skipped substantive job steps.

## Risks and mitigations

| Risk | Source fix / proof |
| Partial import, abort cleanup or rollback across Room/DataStore/process death | Digest-bound journal; Room atomic mutation; DataStore applied-digest marker; startup recovery gate; idempotent `ABORTED` sweeper with explicit cleanup retry; every schema-2-compatible direct-upgrade build retains recovery or replaces it through an on-device pre-navigation migration that consumes every prior state/file form. |
| Browsed books silently become library books | Separate `book` from `library_entry`; regression test browse/read without add. Remote pull is separately user-confirmed and is the only capability-gated bulk membership path. |
| Credential or remote-response exposure | Host-owned opaque credential readiness; no raw Cookie/account/page across DTO, persistence, diagnostic, transfer or extension boundary; sentinel tests assert absence. |
| Source causes an implicit or unauthorized remote write | Exact signed `add` declaration and approved fingerprint; default-off policy; single-use direct-action token; host recheck at dispatch; tests assert login/import/pull/remove/startup/background create zero write requests. |
| Local and remote outcome diverge or a timeout duplicates an add | Atomic local membership/reconciliation row; semantic idempotent `applied`/`already-present` result; uncertain response remains unresolved; retry only by new direct user action; no false synchronization label. |
| Extension update/revocation changes write semantics | Active package/version/grant/origin recheck; policy invalidation disables dispatch; late/in-flight operation becomes unresolved; no automatic retry. |
| Dynamic smart SQL injection or FTS/projection drift | Typed AST, bounded validator, enum-owned SQL fragments and bound args; malicious-term and transaction/rebuild tests. |
| Source uninstall deletes user state | Availability projection only; uninstall transition tests preserve library/progress/organization and disables remote controls. |
| Room migration blocks existing Gate 2 users | Exported schema-1 fixture; deterministic membership backfill before new FK/query invariants; exact hierarchy/order/book/progress assertions; explicit nonmember-progress behavior; no destructive fallback. |
| Large import/export or remote pull causes allocation/partial output | Existing 32 MiB input/export bounds; remote read page/count/8 MiB aggregate bounds; complete-then-transaction apply; capped preflight/sweeper; no partial remote apply. |
| E-ink receives a second business path | Shared ViewModel/query state; only presentation/paging policy differs; remote states use immediate textual replacement. |
| Portable preference or transfer rule has no consumer | Implement consumer in the same commit; source policy/reconciliation and credentials remain outside transfer and Hikari import. |

## Decision register and confirmed planning inputs

The following decisions are user-confirmed. D1 and D11–D14 expand the previous plan, so they require the renewed reviews recorded below; they are not implementation authorization.

| ID | Confirmed decision | Boundary |
|---|---|---|
| D1 | Browsing/reading never adds a book. Local addition remains explicit; an eligible source may add returned favourites only after the user explicitly confirms a post-login remote pull. | No auto-add on first read or connection. |
| D2 | Merge-only import; no destructive replace. | Imports never delete newer local state. |
| D3 | Guided nested smart-rule editor. | No raw expression, JSON or SQL. |
| D4 | Delete collection/memberships, reparent children, preserve books. | No recursive book deletion. |
| D5 | Remove local library membership/rating/tags but preserve progress/history. | Local removal never removes a remote favourite. |
| D6 | Implement and consume portable flow/font scale/line height/theme. | E-ink safely overrides effective incompatible values. |
| D7 | Preserve imported subscription drafts as read-only `尚不可执行`. | No refresh/execution. |
| D8 | Verified installed package means source available; no generic source-enable switch. | Remote policy is a separate capability-gated setting, not availability. |
| D9 | Import source-scoped Hikari search suggestions and browsing timestamps; exclude them from transfer v1. | No credential or sync-setting migration. |
| D10 | Android `0.2.0`/versionCode 2 and protocol `0.2.0`. | Persistent schema and public Host API boundary are versioned. |
| D11 | On the first user-completed manual login for a read-capable source, explicitly ask before a one-time favourites pull. | User may dismiss and use a later manual pull; no automatic request. |
| D12 | Optional writeback is add-only. | Local removal never writes remotely; no remove/move/folder support. |
| D13 | Remote favourites refresh only through an explicit manual action. | No open-source, startup or background refresh. |
| D14 | Wenku8 signed fixture extension becomes `0.2.0`. | It declares/obtains approval for `remoteLibrary.read` and `add`; extensions `0.1.0` cannot perform this scope. |

## Review records

### Designer

- Initial target plan input: `63ab05e7b486275502d1111f7932c7a018eab829`.
- Initial verdict: **APPROVE_WITH_CHANGES**.
- Findings: F1 route/IA binding; F2 reorder, breakpoint and paged-selection behavior; F3 option applicability gaps; F4 dormant prerequisite path; F5 golden fixture ownership; F6 smart-editor state contract.
- Closure review input: `70da4faa562f7fc0650685b4e3316de7a7f85255`; final verdict **APPROVE**; F1–F6 **CLOSED**; Blocking findings: none.
- Adviser-amendment UX review input: `a96c5b6e2a52836e94cb74cd8ea0a90f50a49b0b`; findings B1 pre-navigation recovery states/actions/accessibility and B2 `transfer-too-large` golden ownership.
- Closure review input: `0ad64ce75a755444743282dacafe4eed2fb7255d`; final verdict **APPROVE**; B1–B2 **CLOSED**; Blocking findings: none.
- Recovery-lifecycle UX review input: `a919bc54a73f830fc8ffbae137f7c90ab116ea9e`; final verdict **APPROVE**; Blocking findings: none.
- The D1/D11–D14 remote-favourites scope amendment invalidates the preceding Designer approval; renewed review is pending.

### Adviser

- Initial target plan input: `76ea19042e413f0173f0fb0cd4b8e077d04bf7c2`; verdict **REQUEST_CHANGES**. Findings: A1 cross-store Room/DataStore crash atomicity; A2 schema-1 manual-membership backfill; A3 normative system/progress query semantics; A4 over-limit export outcome.
- First remediation review input: `947503f490808ea4299ddfeb0879b3d978715d14`; verdict **REQUEST_CHANGES**. A2 **CLOSED**; A1 partial due abort-cleanup crash window and rollback recovery removal, A3 partial due plural-author key ambiguity, A4 partial due export preflight lifecycle gaps.
- Second remediation review input: `79981b8543f8088aaae2e7c49820bfe59e39dd58`; verdict **REQUEST_CHANGES**. A1–A4 and the four lifecycle closures were accepted; direct-upgrade rollback compatibility remained open because a later build cannot prove every device has passed through an intermediate recovery build.
- Final review input: `d8d34ae0ee3f0f1935101308ffef8cba1dcfabac`; final verdict **APPROVE**. All A1–A4 and follow-up findings **CLOSED**; Blocking findings: none.
- The D1/D11–D14 remote-favourites scope amendment invalidates the preceding Adviser approval; renewed review is pending.

## Authorization boundary

Designer and Adviser approval do not authorize implementation. After both renewed reviews close, the user must explicitly authorize the confirmed D1–D14 plan. Any later expansion of data fields, UI routes, source capabilities, network behavior, transfer version or risk model invalidates the affected approval and returns Gate 3 to plan review.
