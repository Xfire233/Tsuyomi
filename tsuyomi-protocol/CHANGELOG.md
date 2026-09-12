<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Changelog

## [Unreleased]

### Added

- Optional signed read-only source Home capability and versioned normalized `SourceHomePage` projection, with strict schema and conformance coverage.
- Source Home pages may expose up to four optional typed feature destinations as bounded display text plus opaque normalized selections; URLs and source-controlled UI remain forbidden.
- Optional normalized Detail `lastUpdatedDate` and a bounded read-only author-search request entry point, with explicit-action-only invocation, preserved verification identity, and no silent title-search fallback.
- Separate signed `remoteLibrary.policies.targets` grammar and Host API target-discovery boundary; generic read transport cannot list destinations and hosts never synthesize target IDs.
- Strict `tsuyomi-transfer` v2 schema and fixture for completed chapter IDs, read-later state and expanded reader preferences. Importers retain strict v1 compatibility while v1 documents reject v2-only fields.
- Strict `tsuyomi-transfer` v3 schema and conformance fixtures preserve explicit local-pin state. A retained unpinned record may carry local metadata, ratings, read-later state, progress, and completion state, but cannot carry a manual collection membership; strict v1/v2 import remains pinned by definition.
- Signed read-only `updateCheck` capability and `hxp-update-check-v2` normalized parser schema. Exact `complete: true` and `order: "source"` assertions, the exact GET request policy, bounded no-URL/no-body evidence, and semantic fixtures reject generic transport fallback, incomplete evidence, raw chapter payloads, duplicate IDs, and invalid source dates.
- Strict `tsuyomi-repository` v1 schema, deterministic signed fixture, and normative catalog contract for root signatures, bounded HTTPS download binding, durable sequence anti-rollback, expiry, revocation, and explicit root-authorized legacy publisher migration.
- Strict third-party repository subscription-link bootstrap contract and fixtures: a user-added HTTPS root remains separate from official publisher authority, survives remove/re-add with cached anti-rollback and revocation state, forbids third-party publisher migration, and requires exact package-scoped execution consent.

## [0.1.0] - 2026-08-09

### Added

- Versioned ReaderDocument, forum navigation, Host API, HXP package/trust, transfer, backup-mapping schemas, fixtures, and conformance tests required by the Phase 0/1 baseline.
