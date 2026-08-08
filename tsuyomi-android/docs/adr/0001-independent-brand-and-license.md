<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# ADR 0001: Independent Tsuyomi brand and Apache-2.0 codebase

- Status: Accepted
- Date: 2026-08-08

## Problem

Tsuyomi needs a durable identity, release lineage, and licensing policy independent from Hikari Novel Plus while still allowing behavior-compatible migration and properly attributed use of compatible upstream work.

## Constraints

- Existing upstream copyrights and licenses remain in force for copied or adapted material.
- New repositories must be suitable for GitHub Releases and F-Droid review.
- Branding, package identity, signing keys, and release metadata must not imply an official relationship with Hikari Novel, Wenku8, ESJZone, or Yamibo.

## Decision

Tsuyomi is an independent project. New project-owned code and documentation use Apache-2.0. Every source file carries SPDX metadata. Copied or adapted third-party material is accepted only after license compatibility review and is recorded in `THIRD_PARTY_NOTICES.md` with its pinned source revision and adoption scope.

Behavior, data formats, and user workflows may be migrated from the Flutter reference application. Its package identity, release signing identity, private runtime data, and branding are not inherited.

## Rejected alternatives

- Continue publishing under the Hikari Novel Plus identity: rejected because it couples release lineage and user expectations to the Flutter fork.
- Copy the Flutter repository and incrementally replace Dart: rejected because it obscures provenance and carries platform-specific architecture into the native host.
- Use an unspecified mixed-license baseline: rejected because it makes redistribution and contribution review unreliable.

## Migration impact

Migration work starts from observable contracts, sanitized fixtures, and explicitly reviewed compatible source. No source file is copied silently. Legacy backups are treated as input formats, not evidence that Tsuyomi is the same application.

## Verification

- `python -m reuse lint` passes from the Monorepo root.
- Every adopted dependency or source copy has a notice entry before release.
- Release metadata contains Tsuyomi branding and no inherited Hikari signing or package identity.
