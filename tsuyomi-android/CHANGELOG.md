<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Changelog

All notable changes use semantic versioning. Future Phase baselines use annotated `phase-N-baseline` tags; immutable historical `gate-1-baseline` and `gate-2-baseline` tags retain their published names.

## [Unreleased]

### Added

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
- `tsuyomi-transfer` v2 export with strict v1/v2 import, completed chapter IDs and expanded reader preferences.

### Changed

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
- Android CI planning now computes reverse transitive Gradle consumers and selects their existing unit, screenshot, and API 29 instrumentation families for shared production changes.

## [0.1.0] - 2026-08-09

### Added

- Phase 1 Android host shell, global standard/E-ink display profile, semantic Compose components, Room/file/credential foundations, and Reader JVM transition contracts.
- API 29 runtime acceptance recipes, real-screen screenshot baselines, lint, dependency verification, and REUSE compliance.

### Changed

- E-ink manual redraw appears only when the effective profile is E-ink.
- Removed unimplemented logical refresh policy settings and persistence until a real coordinator consumes them.
