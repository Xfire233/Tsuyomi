<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Changelog

All notable changes use semantic versioning. Gate baselines additionally use annotated `gate-N-baseline` tags.

## [Unreleased]

## [0.1.0] - 2026-08-09

### Added

- Gate 1 Android host shell, global standard/E-ink display profile, semantic Compose components, Room/file/credential foundations, and Reader JVM transition contracts.
- API 29 runtime acceptance recipes, real-screen screenshot baselines, lint, dependency verification, and REUSE compliance.

### Changed

- E-ink manual redraw appears only when the effective profile is E-ink.
- Removed unimplemented logical refresh policy settings and persistence until a real coordinator consumes them.
