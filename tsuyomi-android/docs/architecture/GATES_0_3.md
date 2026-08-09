<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Gate 0–3 delivery plan

These gates are ordering constraints, not separate products. A later gate must keep all earlier contracts passing.

## Gate 0: protocol and security baseline

**Outcome:** versioned contracts can reject malformed or unsafe inputs before Android/QuickJS integration.

- Finish transfer semantic conformance: size cap, deterministic ordering, duplicate book/shelf identities, shelf references/cycles, and newer-valid-`updatedAt` merge rule.
- Freeze and fixture-test `ReaderDocument`, reader locator/code-point offset, `ForumThreadNavigation` route/alias projection, forum `ThreadPage`/`Post`, and Host API network request/response/error DTOs.
- Freeze HXP archive/integrity/signature, publisher trust, signed revocation, rotation, rollback, and capability-diff rules; add cryptographic vectors before installer implementation.
- Add ADR 0018 credential lifecycle and Android backup exclusion.
- Build no runtime feature against an unspecified parser/network/credential shape.

**Exit evidence:** protocol test suite passes transfer, manifest, Host API, and policy fixtures; `reuse lint` passes in all repositories.

## Gate 1: Android host shell and global display profile

**Outcome:** installable Compose app with enforced module boundaries and the same route/state under standard or E-ink profiles.

- Generate Gradle wrapper, version catalog, convention plugins, app, shared/core/source/reader/feature modules, dependency rules, static analysis, and reproducible release inputs.
- Implement DataStore display preference, local device classifier, `DisplayEnvironment`, semantic components, a real E-ink-only root redraw request, and forced-profile previews/tests. Do not expose logical refresh strategies until a coordinator consumes them.
- Implement Room stable identity/progress/collection schema, file/cache quotas, Keystore credential port, and source partition storage without a real extension.
- Implement root navigation, Chinese strings, accessibility baseline, explicit E-ink pagination, and no Google Play Services.

- Define and JVM-test `SettledPositionSnapshot`, compatible presentation-switch transaction, `PreviewSession`, visual-commit witness, and layout/document/session epoch invalidation before composing a reader screen.

**Exit evidence:** app runs on API 29 emulator; forced `standard` and `eInk` use one route/state; profile/unit/instrumentation tests prove persistence and policy behavior.

## Gate 2: Wenku8 read-only vertical slice

**Outcome:** a signed fixture/extension can be installed and read through search → detail → directory → chapter → reader → progress.

- Build HXP verifier/package store, one-runtime-lane QuickJS adapter, Host API `network.request`, origin/cookie/cache/diagnostic enforcement, and controlled manual verification UI.
- Implement Wenku8 parser fixture suite, alias/charset handling, source errors, structured `ReaderDocument`, and no raw HTML reader boundary.
- Implement reader session/paged/scroll/dual surfaces, semantic locator restore, compatible-switch transaction, bounded layout/cache, frozen-plan WYSIWYG preview, image geometry, and forced E-ink immediate navigation.
- Test controlled login/verification only through deliberate human interaction. No remote library mutation, background subscription, CAPTCHA solver, or imported login state.

**Exit evidence:** an API 29 device completes the read-only flow with network fixture tests; process recreation restores locator; invalid/challenge pages are not admitted to normal cache; a physical E-ink device has the required profile evidence before any E-ink release claim.

## Gate 3: local library and migration

**Outcome:** a local-first reader retains books/progress/organization and moves safe Hikari data without secrets; signed capability-aware sources may offer the narrowly bounded remote-favourites flow below.

- Implement system/manual/smart collection queries, rating/tags, source dormancy, local search, explicit E-ink list pagination, and no automatic subscription execution.
- Implement `tsuyomi-transfer` export/import with dry-run warnings, per-record recovery, conflict report, deterministic output, and no credentials/cache/WebView data.
- Implement one-way Hikari import according to mapping: no cookies/accounts/browser state; compatible smart rules become local AST; subscriptions import disabled.
- For a source whose signed manifest explicitly declares `remoteLibrary.read` and/or `add`, provide only user-mediated remote-favourites pull and add-only writeback: manual-login prompt, explicit pull, default-off per-source add setting, direct local-add token, host reconciliation and no automatic request. Gate 3 acceptance ports Wenku8 fixture `0.2.0` only.
- Validate library/progress import with Wenku8 fixture data and clean-profile restoration via Android file picker.

**Exit evidence:** export/import round-trip preserves stable identity, newest valid progress, manual shelves, safe preferences, and explicit warnings for dropped/incompatible legacy fields. A clean profile proves imported books are dormant until the user installs their source. Remote-favourites fixture evidence proves signed capability approval, explicit pull, add-only direct writeback, reconciliation, no-write negative paths and Standard/E-ink parity.

## Explicitly later

Remote favourite removal/move/folder selection, bidirectional or automatic sync, subscription execution, forum reply/write flows, forum read/verified owner-catalogue slice, local EPUB/TXT import, TTS, scheduled updates, vendor E-ink APIs, cloud sync, telemetry, and automatic crash reporting are not Gate 0–3 scope. The forum read slice must consume the pre-frozen `ForumThreadNavigation` contract and remains read-only.
