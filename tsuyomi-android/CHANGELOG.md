<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Changelog

All notable changes use semantic versioning. Future Phase baselines use annotated `phase-N-baseline` tags; immutable historical `gate-1-baseline` and `gate-2-baseline` tags retain their published names.

## [Unreleased]

### Added

- Phase 4A Standard Library production cutover matching UI Atlas behavior and contract:
  - Stationary platform-threshold long-press multi-selection across Grid, List, and Compact layouts.
  - `SelectionAppBar` with item count, select all, clear all, batch add/move to collections, and local deletion.
  - Drag-and-drop interactions: book-on-book collection creation, book-into-collection, root shelf insertion with expanding zero-width gaps and sibling displacement, and shortcut reordering.
  - Locked and unlocked shortcut shelf: locked pins the full shelf below the AppBar while keeping full drag/drop, insertion, and reorder capability; unlocked scrolls inline, collapses to a >=48dp chevron handle, expands via click, reverse-scroll, or dragged-book hover, and preserves scroll anchors.
  - One-continuous-hold drag pickup for unselected books and direct shortcuts without requiring a prior selection step.
  - Room database v4 migration adding `display_order` to preserve custom root shelf and manual collection order.
- Source Home production integration:
  - Wenku8 recommendation sections (`7月新番`, `新书风云榜`, `本周会员推荐榜`) and “这本轻小说真厉害！” feature card opening dedicated cached ranking views.
  - Equal-width 4-tab layout, centered tag container with 200ms M3 expand/collapse animation, and scroll-direction-aware FAB.
- Book Detail and Reader UI Atlas parity:
  - Cover image loader with media policy, rating badge alignment, and compact action headers.
  - Reader pagination contracts, reader chrome, and reorganized settings panel.

### Changed

- Superseded numbered delivery `Gate` scopes with `Phase 0–5` (including 4A/4B/4C); reserved gate terminology for explicit admission, review, authorization and release checkpoints.
- Restored Atlas resting shortcut tile dimensions (`80×116dp`), media field (`76dp`), single-line labels, and target-specific collection hover feedback.

## [0.1.0] - 2026-08-09

### Added

- Phase 1 Android host shell, global standard/E-ink display profile, semantic Compose components, Room/file/credential foundations, and Reader JVM transition contracts.
- API 29 runtime acceptance recipes, real-screen screenshot baselines, lint, dependency verification, and REUSE compliance.

### Changed

- E-ink manual redraw appears only when the effective profile is E-ink.
- Removed unimplemented logical refresh policy settings and persistence until a real coordinator consumes them.
