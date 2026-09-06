<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Tsuyomi production UI evidence specification

## Purpose

This document defines how production Android UI obligations are proved. It does not define product behavior, duplicate the Review Graph catalog, select active profiles, or grant approval.

Unique owners:

| Fact | Owner |
|---|---|
| Product-visible UI behavior | `UI_CONSTITUTION.md` |
| Review node IDs, states, operations and checks | `.agents/skills/tsuyomi-android-review/review-node-catalog.json` |
| Current profiles, stages and online lanes | `.agents/skills/tsuyomi-android-review/review-policy.json` |
| Review execution procedure | `.agents/skills/tsuyomi-android-review/SKILL.md` |
| Device recipes and evidence fields | `docs/verification/AVD_MATRIX.md` |
| Phase scope and authorization conditions | `docs/phases/PHASE_4.md` |

A successful test or screenshot is evidence only. Human review, implementation authorization, canonical replacement, remote writes, merge and release remain separate decisions.

## Production-only review surface

The temporary `:prototype:ui-atlas` application was retired after its adopted behavior, fixtures and regression seams moved to production modules. New UI work uses production Composables, production strings, production state owners, production screenshot tests and production instrumentation. A second fixture UI or review application is prohibited.

Historical Atlas screenshots, exports and design plans remain provenance only. They cannot become current product authority or approval evidence.

## Review Graph

The standalone catalog contains 28 stable nodes:

- `L01–L08`: Library.
- `B01–B03`: Book and Reader.
- `S01–S04`: Source surfaces.
- `M01–M07`: More/settings/data.
- `X01–X06`: cross-cutting capabilities.

Do not copy node operations or checklists into this document, Phase documents, Skills or handoffs. UI-R1 reads the catalog and selects only nodes affected by the current change packet. A product/Phase contract, catalog schema, shared theme/scaffold or genuinely unknown Android source may still require conservative full scope.

## Evidence ownership

One observable claim has one owner:

| Claim | Evidence owner |
|---|---|
| Pure logic or state transition | JVM/contract test |
| Android lifecycle, Room, navigation, gesture or process restoration | Focused instrumentation/Journey |
| Deterministic static geometry or copy | Production screenshot assertion |
| Bounds, semantics, focusability or overlap | Android CLI layout tree/diff |
| System bars, IME, drag/drop or real-window composition | Isolated AVD interaction plus one Android CLI capture when visual judgment matters |
| Long-reading comfort, trust, wording quality, brand judgment or TalkBack experience | Human review |

Do not run a Journey, screenshot, hierarchy dump and manual replay to prove the same deterministic fact. Use another owner only when the first owner lacks the required capability or reports a concrete failure.

## Production state inventory

Each selected Review Graph node declares its required states. Evidence covers only changed or contract-required states, not every state on every edit. At minimum, select from:

- loading/content/empty/error;
- offline/refreshing;
- selection/mutation/unresolved;
- modal/input/focus;
- process recreation where persisted state or work ownership changed.

A screenshot never proves reachability or state transition. A test that only forwards fields, checks source text or asserts implementation plumbing is not evidence.

## Active Standard device

Routine production UI review uses the active profile selected by `review-policy.json`. The retained Standard phone baseline is:

```text
API 29
portrait 1080×2400
420 dpi
fontScale 1.0
zh-CN
```

Verify effective size, density, orientation, locale and font scale before capture. A Pixel 2 default 1080×1920 device or an unverified temporary `wm` override is not equivalent. Landscape, split-screen, large-font and forced-window checks are additional affected-state evidence.

E-ink requirements remain preserved but do not run while the policy marks E-ink deferred/frozen. Restoring that profile reactivates its physical-device and human evidence.

## Evidence record

Each formal production device record contains:

- immutable Git/change-packet input;
- APK/application ID and SHA-256 when installed;
- selected node/state/profile;
- device/AVD name, serial at execution time, API, size, density, orientation, locale and font scale;
- exact command or Journey;
- result and artifact path/hash;
- evidence owner;
- unresolved human-only item.

Generated layouts, screenshots, traces and reports stay under ignored `.local/` or build output. Version control keeps only stable contracts, catalog/policy, regression tests and concise Phase/checkpoint outcomes.

## Human boundary

AI may select scope, execute permitted automation, attach evidence and draft a `PENDING` result. AI must not set human review timestamps, approve visual quality, accept a node, replace a canonical APK, perform a production website write, merge or release.
