<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Changelog

## Unreleased

- Added a repository-owned context-router Skill with bounded trigger/bypass rules, existing-authority routing, versioned cross-session discovery fallback, and adoption gated on preserved answer quality plus measured time and token savings.
- Consolidated Android, protocol, and extension components into one public monorepo.
- Preserved independent component versions and path-scoped quality workflows.
- Added public provenance, licensing boundaries, and local-only development-state rules.
- Added the Phase 3 local-first library, explicit bounded transfer migration, smart collections, and signed add-only remote favourites with credential-gated retries and exact redirect policy.
- Superseded numbered delivery `Gate` scopes with `Phase 0–5`; future baselines use `phase-N-baseline`, while published `gate-1-baseline` and `gate-2-baseline` tags remain immutable historical facts.
- Added Phase 4A Standard Library cutover from UI Atlas: stationary long-press selection across Grid/List/Compact, SelectionAppBar, drag-and-drop book/collection/shelf management, locked/unlocked shortcut shelf with hover expansion, one-hold pickup, and Room v4 custom order persistence.
- Added Source Home Wenku8 recommendation sections, “这本轻小说真厉害！” feature destination, symmetrical tab layout, centered tags panel, and directional FAB.
- Aligned Book Detail and Reader chrome with UI Atlas visual specifications and interaction models.
- Added the Phase 4B website-library mirror: durable Room v6 mirror bindings/targets/items, a first-class mirror-root shortcut, optional per-source remote-folder navigation and pins, frozen missing-target restoration, shared Library-native grid/list/compact pages, and explicit foreground refresh only.
- Added single-book cross-boundary Library operations with operation-specific `ADD`/`MOVE` authorization receipts, per-invocation destructive `REMOVE` confirmation, fresh direct-action admission, immediate mirror projection updates, and retry-only `MOVE` recovery after a confirmed `ADD`.
- Replaced Book Detail’s plain shelf action with the official Material 3 split button: it uses a compact 48dp Detail container with theme-large outer corners and unchanged icon/text scale, adds directly to the local root, and exposes one anchored labelled dropdown for read-later, local placements, website targets and recovery without a bottom sheet.
- Added Wenku8 remote target-membership parsing and GB18030 target-list transport, plus bounded Android quality workflows and canonical repository/user Skill and MCP ownership.
- Fixed Wenku8 rows whose default `classlist` option is implicit rather than marked `selected`, normalized missing mirror membership to the visible default target, refreshed shortcut state immediately after pinning, and removed the empty-folder copy no-op.
- Made website grouping default-off per source: the normal mirror aggregates all remote books into `全部网站收藏`, while an overflow opt-in restores retained folder pages, pins, target selection and MOVE workflows without reorganizing website data; existing folder pins preserve advanced mode during migration.
- Replaced generic Detail reading-progress text with an optional source last-update date and made author names native blue links that submit one source-bound author search and retain applied results without lifecycle replay.
- Refined measured Detail title overflow: only the unmodified book title determines two-line overflow, exact two-line titles remain complete, and genuine overflow uses a text-scale circular Material Info control with accessible expansion. Source dates require compatible normalized metadata rather than a host substitute.
- Softened the Detail author link after canonical review: removed the persistent underline and replaced vivid browser blue with WCAG-AA muted blue tokens for Standard light and dark, while preserving native link semantics, exact same-source author search, keyboard/TalkBack activation, visible failure and lifecycle non-replay.
- Distributed the Standard Detail cover-right column across six visual lines: one/two title lines, a dedicated author line, a plain status-plus-update line, borderless compact rating and the official framed SplitButton aligned to the cover bottom. The first visible star—not its invisible layout band—shares the exact left edge of title metadata and SplitButton. Measured remaining height is shared across inter-block gaps; no Badge, combined author/status row, rating capsule, horizontal baseline action pair or bottom void remains.
- Fixed landscape content extending beneath side system bars: the shared scaffold consumes horizontal safe-drawing insets once, keeping the adaptive Detail controls and reading FAB fully visible without duplicated M3 insets.
- Fixed website-collection covers learned by canonical Detail: mirror-only catalog entries now receive the authoritative cover on return, and later coverless remote-list refreshes preserve it instead of reverting to the fallback.
- Added canonical documentation and tooling responsibility registries with explicit purpose, trigger, preconditions, method, output, scope/exclusions, completion, fallback, health and lifecycle. The tool inventory now covers native file/code tools, orchestration, subagents, persistent evaluation, AST/LSP/debug paths, memory, browser/image creation, Android Skills and MCP owners; repository checks reject unregistered first-party Markdown, duplicate/incomplete document ownership, incomplete or duplicate tool/Skill/MCP records, obsolete Skill paths and copied dynamic review-policy values.
- Fixed Phase 4B remote ADD reconciliation: concurrent taps no longer duplicate website writes, pre-accept retry failures preserve the earlier unresolved result, and a later explicit `APPLIED`/`ALREADY_PRESENT` confirmation atomically closes the unresolved ADD chain.
- Hardened every website-library boundary: target discovery has its own exact signed policy, Wenku8 parsers reject ambiguous success and fabricated targets, compound ADD→MOVE intent survives coordinator/process recreation, and sparse remote snapshots preserve rich local catalog metadata.
- Added exact `BookIdentity + chapterId` completion independent of locator/resume progress, strict `tsuyomi-transfer` v2 output with v1/v2 import compatibility, and interface-only preference reset that preserves source-flow and import state.
- Expanded the merge-base CI planner to every test-bearing module and overlapping integration owner, removed the duplicate lock-rewriting CI build, fixed Review Graph catalog scope classification, enforced active-profile-only screenshot registration while E-ink is frozen, and recorded the package-review prevention checklist.
- Retired the UI Atlas prototype, fixture review bridge and duplicate reviewer infrastructure; the production implementation and versioned Review Graph catalog now own Android UI behavior and evidence.
- Hardened Android verification against scope drift: active/deferred profile selection now precedes every run, frozen-profile instrumentation is mechanically excluded from routine gates, debugging uses an exact-test-to-adjacent-group ladder with one final suite at most, and PR CI owns the complete matrix after bounded local proof.

## 0.1.0 - 2026-08-09

- Established the Phase 1 Android, protocol, and extension-contract baseline.
