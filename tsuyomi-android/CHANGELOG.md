<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Changelog

All notable changes use semantic versioning. Future Phase baselines use annotated `phase-N-baseline` tags; immutable historical `gate-1-baseline` and `gate-2-baseline` tags retain their published names.

## [Unreleased]

### Changed

- Superseded numbered delivery `Gate` scopes with `Phase 0–5` (including 4A/4B/4C); reserved gate terminology for explicit admission, review, authorization and release checkpoints.


## [0.1.0] - 2026-08-09

### Added

- Phase 1 Android host shell, global standard/E-ink display profile, semantic Compose components, Room/file/credential foundations, and Reader JVM transition contracts.
- API 29 runtime acceptance recipes, real-screen screenshot baselines, lint, dependency verification, and REUSE compliance.

### Changed

- E-ink manual redraw appears only when the effective profile is E-ink.
- Removed unimplemented logical refresh policy settings and persistence until a real coordinator consumes them.
