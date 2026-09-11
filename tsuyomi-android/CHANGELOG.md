<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Changelog

All notable changes use semantic versioning. Future Phase baselines use annotated `phase-N-baseline` tags; immutable historical `gate-1-baseline` and `gate-2-baseline` tags retain their published names.

## [Unreleased]

### Added

- Official source discovery in Standard Browse: installed/available sections, independent plugin search, bounded rows and scrollable source/license/publisher details, explicit installation approval and manual updates; local HXP import remains available.
- Root-signed static catalog admission, exact HXP binding, durable replay and revocation state, total download deadlines, and shared foreground/background trust reconciliation without removing installed archives or user data. Production keys and formal catalog/package publication are separately authorized; unconfigured builds report the repository as unavailable.
- Persistent third-party repository subscriptions from canonical explicit-root links, with address/fingerprint confirmation, independent disable/removal, and retained revocation/antirollback history.
- Verified local publisher-key input and separate non-official execution consent bound to the exact source, publisher and archive; cancelling or supplying the wrong key never authorizes execution. Built-in identities cannot be shadowed by a subscribed root.
- Cancellable source uninstall and same-identity reinstall retaining host books, progress and credentials, while fencing requests and clearing obsolete source navigation. Cold start reconciles missing archives as dormant without repeatedly invalidating them.

- Phase 4A Standard Library production cutover; accepted Atlas-era behavior was migrated into production and the prototype was retired:
  - Stationary platform-threshold long-press multi-selection across Grid, List, and Compact layouts.
  - `SelectionAppBar` with item count, select all, clear all, batch add/move to collections, and local deletion.
  - Drag-and-drop interactions: book-on-book collection creation, book-into-collection, root shelf insertion with expanding zero-width gaps and sibling displacement, and shortcut reordering.
  - Locked and unlocked shortcut shelf: locked pins the full shelf below the AppBar while keeping full drag/drop, insertion, and reorder capability; unlocked scrolls inline, collapses to a >=48dp chevron handle, expands via click, reverse-scroll, or dragged-book hover, and preserves scroll anchors.
  - One-continuous-hold drag pickup for unselected books and direct shortcuts without requiring a prior selection step.
  - Room database v4 migration adding `display_order` to preserve custom root shelf and manual collection order.
- Source Home production integration:
  - Wenku8 recommendation sections (`7月新番`, `新书风云榜`, `本周会员推荐榜`) and “这本轻小说真厉害！” feature card opening dedicated cached ranking views.
  - Equal-width 4-tab layout, centered tag container with 200ms M3 expand/collapse animation, and scroll-direction-aware FAB.
- Book Detail and Reader production alignment with accepted Atlas-era decisions:
  - Cover image loader with media policy, rating badge alignment, and compact action headers.
  - Reader pagination contracts, reader chrome, and reorganized settings panel.

- Phase 4B website-library mirrors and explicit single-book writeback:
  - Durable Room v5–v8 mirror, reconciliation, COPY-consent and exact chapter-completion storage.
  - Root/group mirror shortcuts, frozen missing-target restoration and Library-native grid/list/compact rendering.
  - Operation-specific `ADD`/`MOVE`/`REMOVE` authorization, separately signed target discovery, cancellation-safe retry and process-resumable targeted ADD→MOVE.
  - Local-only COPY and ordinary `加入书架`, with website actions and persistent outcomes kept visually and semantically distinct.
- `tsuyomi-transfer` v3 export with explicit local pin state and strict v1/v2/v3 import, preserving retained unpinned annotations without repinning during restoration.
- Phase 4C local update inbox and optional scheduling:
  - Signed, read-only `update-check-v2` and Wenku8 ordered-directory evidence; the first trusted baseline is silent, and ambiguous directory changes preserve pending updates.
  - Room v9 sessions, fenced recovery, incremental inbox, bounded reports, per-book/source exclusions and exact-anchor handling/Undo.
  - Default-off WorkManager schedules with persistent constraints and in-app progress/results/cancellation even when notifications are denied.
  - Library-native cover grid, cover list and compact layouts; canonical Detail focus and direct reading also work for mirror-only books without creating local pins.
  - Automatic handling requires durable completion of every admitted new chapter, never a locator or only the last chapter.
- Dedicated local Library search with latest-wins metadata/folder matching, bounded recommendations and immediate explicit submission; source search remains separate.

### Changed

- Repository downloads now detect truncated response bodies and recover once from transient EOF/connection resets within the original total deadline, discarding partial bytes and retaining all HTTPS, size, signature and approval checks.
- Browse separates download, verification, repository-state and storage failures. Repository download errors retry the exact source/repository instead of opening a file picker; unavailable or removed repositories return to the catalog, and retries still require package approval.
- Repository link inspection dismisses its input keyboard before root review; official signed revocations remain effective for active sessions even after the publisher leaves the current catalog.
- Superseded numbered delivery `Gate` scopes with `Phase 0–5` (including 4A/4B/4C); reserved gate terminology for explicit admission, review, authorization and release checkpoints.
- Restored Atlas resting shortcut tile dimensions (`80×116dp`), media field (`76dp`), single-line labels, and target-specific collection hover feedback.
- Remote Library refresh now exits its working state after every terminal content, empty, login-required, verification-required, cancelled, or safe-error result instead of leaving refresh controls stuck loading.
- Remote ADD now coalesces concurrent taps to one website write, preserves an accepted unresolved attempt when a later retry fails before acceptance, and closes the unresolved retry chain only after an explicit retry confirms `APPLIED` or `ALREADY_PRESENT`.
- Remote mirror and mutation summary writes now preserve richer catalog metadata instead of clearing authors, cover, status, tags or update state.
- Chapter completion is recorded only from an explicit chapter-end transition; semantic locator progress is character-based and no longer implies completion.
- Interface reset removes only display/reader/library-layout/introduction keys and preserves source-flow and import-digest state.
- Retired the `:prototype:ui-atlas` module and duplicate review fixtures; production modules and Review Graph catalog version 9 are the only active UI path.
- Active screenshot evidence now follows the review-policy profile set: retained E-ink previews and goldens stay frozen and unregistered until the explicit restoration pass.
- API 29 regression coverage now keeps long-press movement in one pointer stream, bounds aggregate-limit fixtures without large allocations, and deterministically replaces in-flight WebView navigation before verified-page parsing.
- Detail exposes website MOVE and REMOVE only for books present in the durable website mirror; remote removal keeps local Library and reading data.
- Remote mutation recovery now keeps operation and state from one reconciliation record, makes pre-acceptance cancelled ADD retryable, cancels complete acknowledged MOVE/REMOVE retry chains, and preserves operation-specific receipts across package restoration.
- Remote target decoding now rejects malformed, duplicate, oversized, cross-source, unsupported, and invalid-parent collections as typed source failures instead of leaking model-constructor exceptions.
- Transfer v2 now requires `completedChapterIds` to be an explicit bounded array of unique nonblank strings without tightening legacy v1 author/tag/shelf values.
- Shared segmented selectors now constrain their dividers to intrinsic content height instead of consuming a weighted list's viewport.
- Android CI planning now computes reverse transitive Gradle consumers and selects their existing unit, screenshot, and API 29 instrumentation families for shared production changes.
- Update-check reports preserve source error category, host stage and bounded safe diagnostic code instead of collapsing failures to an uninformative message; raw exception messages, URLs, credentials and response bodies remain excluded.
- Detail's destination dropdown puts website actions before all local manual collections so long local lists cannot bury a frequent remote action; preserves immediate independent actions and the complete local list without extra menu levels.
- Detail's SplitButton primary is text-only, with no redundant bookshelf icon or icon spacer; compact 12dp horizontal padding and content-sized fallback preserve full labels and the 48dp disclosure target.
- Selectively migrated ordinary Standard text buttons to graduated Material3 ButtonShapes with restrained press corners, unchanged labels/touch geometry, and immediate visible feedback under static motion. Toggle/IconButton families and the global theme identity are unchanged; E-ink remains frozen.
- Migrated existing Detail SplitButtonLayout and full-screen verification-toolbar ownership to the pinned graduated APIs without adding Expressive caller opt-ins or changing their business actions.
- Upgraded the compatible Compose/Material3, AGP/Gradle, built-in Kotlin/KSP, Screenshot and compile-SDK toolchain while retaining minSdk29, targetSdk36 and JVM17. Strict variant builds and bounded device checks pass; existing screenshot differences and service/human review still block full admission, and no goldens were accepted.
- Library now combines immediately applied filtering and sorting in one bounded panel, with separate sections, filter-only clearing and independent layout switching. System tabs preserve but do not expose the root filter.
- Fixed Library tabs use a short destination-only fade on tap; initial rendering, data updates and reduced-motion navigation remain immediate, without a horizontal pager.
- Detail's selected `已在书架` primary now requests the existing local-removal confirmation; its independent destination disclosure and working-state guard remain intact.
- Room v10 separates the local pin from retained annotations. Confirmed removal clears the pin and direct manual memberships while preserving rating, tags, Read Later and reading state; Read Later remains independently accessible and re-add preserves metadata.

## [0.1.0] - 2026-08-09

### Added

- Phase 1 Android host shell, global standard/E-ink display profile, semantic Compose components, Room/file/credential foundations, and Reader JVM transition contracts.
- API 29 runtime acceptance recipes, real-screen screenshot baselines, lint, dependency verification, and REUSE compliance.

### Changed

- E-ink manual redraw appears only when the effective profile is E-ink.
- Removed unimplemented logical refresh policy settings and persistence until a real coordinator consumes them.
