<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Migration architecture and delivery sequence

## Reference baseline

The fixed behavior reference is [`Xfire233/hikari_novel_flutter_plus`](https://github.com/Xfire233/hikari_novel_flutter_plus) commit `a1feba6d1dd8dbbdd2b5ae042e44f2ec54d26bef`, with the original [`15dd/hikari_novel_flutter`](https://github.com/15dd/hikari_novel_flutter) retained for provenance. They are specification and fixture references, not implementation dependencies. Private cookies, databases, WebView state, cached chapters, and user content are never copied into Tsuyomi source or test artifacts.

Migration preserves supported user contracts, not Flutter/GetX/Hive/Drift structure. Each migrated behavior is assigned to the host, protocol, or extension according to its security and ownership boundary.

## Ownership mapping

| Flutter reference responsibility | Tsuyomi destination |
|---|---|
| Source parsers and source-specific request semantics | Signed TypeScript extension |
| Dio transport, redirect and charset behavior | Android host network service exposed through Host API |
| WebView login and Cloudflare/manual verification | Android controlled WebView service |
| Hive settings/session state | DataStore plus source-scoped credential storage |
| Drift library/history/progress | Room host database |
| Reader layout and navigation | Native reader engine and Compose reader UI |
| Backup service | Portable protocol exporter plus Android native backup/importer |
| GetX routing/state | Navigation Compose, ViewModels, coroutines, and `StateFlow` |
| Separate browsing/reader E-ink flags and widget branches | One Android-local global `DisplayEnvironment` in `core/display` plus semantic Compose policies |

## Delivery gates

### Gate 0: protocol baseline

- freeze transfer v1 and HXP manifest v1 boundaries;
- add canonical valid/invalid fixtures and conflict cases;
- define typed Host API methods and error codes;
- prove schema, package integrity, signature, and capability-diff rules without Android.

### Gate 1: native foundation

- create the Gradle/Compose scaffold for `org.tsuyomi.android`;
- encode module dependency direction;
- implement Room, DataStore, file, credential, network, and extension-package stores behind interfaces;
- implement `core/display`, root-level `auto`/`standard`/`eInk` resolution, generic redraw coordination, and standard/E-ink semantic component previews before feature screens;
- install and reject fixture packages before any source behavior is ported.

### Gate 2: Wenku8 read path

- port sanitized search, detail, directory, and chapter fixtures;
- complete controlled WebView handoff and source-scoped cookies;
- normalize source results into `ReaderDocument` fixtures before UI rendering; raw HTML must not cross into reader modules;
- render a chapter and restore a semantic locator after process recreation;
- complete the same search, detail, directory, chapter, and locator flow under forced E-ink mode with explicit pagination, fixed chrome, immediate navigation, and no decorative animation;
- prove scroll, paged, and dual-page surfaces share one semantic locator contract; Yamibo migration later must use `postId` blocks rather than a separate reader persistence path.

### Gate 3: library, migration and user-mediated remote favourites

- add many-to-many manual collections, system collections, validated local smart-rule collections, rating/tags, and dormant-source behavior;
- do not execute source subscription collections until a compatible explicit discovery contract exists; imports create disabled drafts only;
- implement `tsuyomi-transfer` export/import and clean-profile restoration through Android's system file picker;
- port a public typed remote-library contract and Wenku8 fixture `0.2.0` only: after a manual controlled-login handoff, explicitly pull remote favourites; keep add-only writeback default-off and invoke it only from a direct local-add action with a host reconciliation record. Each signed read/add operation declares an exact request policy whose parameter values are fixed literals or host-owned bindings only; a declared read cursor is omitted iff the host has no cursor and otherwise appears exactly once with its canonical host-issued value. The host mints its immutable operation context, enforces it natively for every initial/redirect hop, and uses the lifecycle lease/mutex to make full pull apply atomic against source change.

### Gate 4: remaining authorized writeback

- add remote `remove` and `move`, remote folder/shelf selection and their separate grants/settings;
- retain per-source writeback disabled by default and forbid automatic/bidirectional/background synchronization;
- broaden source-specific write paths only after explicit grants, reconciliation/failure evidence and a reviewed source contract.

### Gate 5: subsequent sources

- implement ESJZone using the proven public contract;
- implement Yamibo, including its thread/reply identity and variable-height semantic locator requirements;
- extend the public protocol only when a source requirement cannot be represented without a private host API.

## Acceptance rule

A gate is complete only when its end-to-end scenario runs in both forced `standard` and forced `eInk` profiles. Compiling an unused module or validating an isolated parser is not gate completion. Live-site checks supplement sanitized fixture evidence but never replace deterministic tests. Physical E-ink evidence is required before the E-ink profile is release-ready.
