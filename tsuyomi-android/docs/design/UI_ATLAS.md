<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Tsuyomi UI Prototype Atlas — executable evidence specification

This document proves `UI_CONSTITUTION.md` and does not define product behavior. Latest binding review: `tsuyomi-atlas-b4dc3465bf7d-review.json`, SHA-256 `92ef4715313a4266dd99f13f6572b671bc9b1bd5ee88e3640ac784925faea5b3`, manifest `b4dc3465bf7d4f1e85d0ac92aee50d8daa5f8235bfb650a0a84c9c408a57fbee`. The next bundle remains fixture-only and provisional; production implementation is not authorized.

---

## 1. Module and isolation rules (temporary; binding)

1. **Module**: exactly one temporary Gradle module `:prototype:ui-atlas`, an application with its own `applicationId` (`org.tsuyomi.prototype.uiatlas`), its own launcher activity, and its own resources. It is excluded from the release build graph (settings inclusion gated to a `prototype` build property; the release settings file never lists it).
2. **Dependency direction**: the atlas may depend on **nothing** in production. It contains its own copy-on-write forks of the constitution tokens/components (clearly namespaced `org.tsuyomi.prototype.*`), its own fixture data, and its own theme. Production never depends on it (constitution §16.4 check 8 rejects any prototype namespace/symbol/dependency in the release graph).
3. **Fixture-only:** no production Room/DataStore, network, source/extension manager, credentials/cookies, real WebView network loads, real source packages or branding assets. Canonical capture launches remain deterministic and bypass persisted state. The separately approved interactive debug host may keep two versioned atomic JSON snapshots in its own `noBackupFilesDir`: one for synthetic fake-product state and one for Review Graph node comments/progress. It may export review JSON only through an explicit user-selected SAF document or share action. It never reads production storage, and `重置假数据` / `清空审阅意见` remain separate confirmed operations.
4. **Full-screen rendering**: the atlas renders real full-screen pages — complete route screens with app bars, content, footers, modals — not component galleries. A reviewer navigates it like the product.
5. **Determinism**: fixed clock, fixed locale (zh-CN primary; en + an RTL locale for the RTL pass), seeded fixture data, animations driven by the profile switch (Standard / reduced-motion / E-ink instant), fontScale forced per capture config. Identical input → pixel-identical frame on repeat runs.
6. **Deletion/extraction**: only after manifest-bound manual Atlas approval **and** separate explicit Phase 4 production implementation authorization may §10 execute. Until both exist, the fixture-only module remains isolated and intact.
7. **M3-backed fairness:** Standard prototype wrappers delegate to actual Material 3 `Button` family, `IconButton`, FAB/Extended FAB, app bars, `ModalBottomSheet`, `ListItem`, `Card`, menus, selection controls and text fields. `Surface`/`Box + clickable` imitations do not count as M3 evidence. E-ink may specialize rendering behind the same Atlas semantic API.
8. **Platform-modal capture adapter:** the Compose screenshot host cannot capture `ModalBottomSheet`'s platform layer. Declarative screenshot previews therefore set an Atlas-only flag that reuses the exact sheet content inside an in-tree Material 3 `BottomSheetScaffold`; interactive Standard routes still execute the real `ModalBottomSheet`. The manifest labels these frames `converged-*`, and the adapter is deleted with the prototype rather than extracted to production.
9. **Visible viewport evidence:** canonical surface evidence uses direct API 29 AVD captures with real status/navigation bars. Declarative host stills may simulate insets/cutouts only for supplementary matrix coverage. A–H is visual-direction smoke, not full acceptance. When E-ink review is active again, every canonical E-ink frame includes real window chrome and passes full-frame grayscale.
10. **Temporary profile execution policy:** `tools/skills/tsuyomi-android-review/review-policy.json` selects the profiles executed by routine review. During `phase4-standard-first`, Standard is active and every E-ink artifact, assertion, fixture and node obligation remains retained but deferred. No E-ink design decision, golden update or readiness claim is made until the explicit restoration pass.

---

## 2. Screen and state inventory (every current and Phase 4 route)

### 2.1 Coverage rule

- **Every RC2.1 target surface** in constitution §20 renders in the atlas (20 route/surface rows, table §2.2).
- **Every current route** is represented via its RC2.1 successor (mapping §2.3), including removed management/directory routes through their in-place successors.
- Removed-at-cutover routes are not rendered as standalone screens; the containing Library/Detail surface is the coverage proof.

### 2.2 Route × state inventory (20 route/surface rows; S = states captured per route)

State legend: **L** Loading · **C** Content · **E** Empty · **Er** Error + retry · **Off** Offline overlay/cached · **Ref** Refreshing overlay · **Sel** Selection mode · **Mut** Mutation working/success/error · **Unr** unresolved accepted remote write only · **Mod** Modal layer(s) · **Tut** required first-entry `FeatureIntroduction` · **Fs2** fontScale 2.0 no-clip pass.

| # | Route/surface | Arch | Required states | RC2.1-specific notes |
|---|---|---|---|---|
| 1 | `library` mixed root | RL | L C E Er Off Sel Mut Mod Fs2 | Phone grid is three columns; wide grid adapts by readable card width. One flow contains System/Collection/Mirror/Book nodes. AppBar Search + `+` are the only creation chrome. |
| 2 | `library` system view | RL | C E Sel Mut | Default-created system nodes may hide/rebuild; only Read Later accepts manual membership; other memberships remain automatic. |
| 3 | `library/history` | CL | L C E Er Sel Mut Mod | AppBar clear-all; ≤7-day relative time, older exact timestamp; per-item remove. |
| 4 | `library/updates` | CL | L C E Er Off Sel Mut Mod Tut | Working state appends only found updates; every visible item includes update anchor/date/action. Standard indicator, E-ink static glyph; three layouts. |
| 5 | `library/collections/{id}` | CL | L C E Sel Mut Mod Tut | Child collections and books are peers; root→child max depth; create/edit/manage; three layouts. |
| 6 | `library/collections/{id}/rule` | FM | C Mut Mod Tut Fs2 | Real picker/dropdown/checkbox/range controls; inline AST errors; unsaved Back confirm; caps visible. |
| 7 | `library/tags` | CL | L C E Mut Mod | `本地 / 来源`; visible compact/list toggle; compact chips omit counts; list rows show counts; local edits and source read-only states. |
| 8 | `library/mirror/{bindingId}` | CL | L C E Er Mut Mod Tut | `网站结构` is default; `本地整理` appears only after explicit local creation; no remote masquerade/write. |
| 9 | `book/{sourceId}/{remoteBookId}` | DT | L C Er Off Mut Unr Sel Mod Tut Fs2 | Rating fixed inside the header at cover right and outside the compact tag/read-later split container; cache action; left-aligned chapter tools; full chapters. |
| 10 | `book/…/reader/{chapterId}` | RD | L C Er Off Mod Fs2 | Reader route and settings remain covered. Seek-preview visual approval is deferred to physical-device testing and is excluded from the next reviewer. |
| 11 | `browse` | RL | L C E Er Mut | Unified M3 source rows/cards and consistent action hierarchy. |
| 12 | `search?origin=…` | CL | L C E Er Off Mod Fs2 | Inert draft; submit inside the field; one session/progress/result stream; no dormant/status row, advanced filter or teaching prose. |
| 13 | `browse/source/{sourceId}/remote-library` | CL | L C E Er Sel Mut Mod | Visible refresh/copy-all; readable E-ink spacing; no redundant per-row disabled explanation. |
| 14 | `source/verification` | FL | C Er | Host notice/status + stub WebView; source identity only where host/site distinction requires it. |
| 15 | `more` | RL | C | Compact grouped M3 settings rows. |
| 16 | `more/display` | FM | C Mut Mod Fs2 | Profile/theme/dynamic/E-ink/redraw/reset/unknown-newer with compact real controls. |
| 17 | `more/reader` | FM | C Mut Fs2 | Full Reader defaults grouped as typography/page/navigation/device; scope/effective-value rules. |
| 18 | `more/data` | FM | C Mod Tut | Import/export/report entries and explicit transfer inclusion/exclusion. |
| 19 | `more/data/report/{sessionId}` | RP | C Mod | 87 warnings collapsed at 50; recovery gate. |
| 20 | `more/help` + `more/about` | IN | C Mod | Searchable help accordion, route-owned introductions, reset deep link and license text. |

The finite artifact set is defined only by the machine-readable canonical inventory in §6.1. The route/state table above defines requirements, not an implicit Cartesian product; themes, windows, fontScale, locales, inputs and motion variants are sampled only where §6.1 assigns an exact logical artifact identity.

### 2.3 Current-route → atlas coverage map (nothing reachable today is unrepresented)

| Current route/code | RC2.1 successor | Notes |
|---|---|---|
| Library tab (`LibraryScreen`) | `library` (#1–2) | Filter chips/selector-only context are replaced by the mixed node flow and layout/sort actions. |
| Collection manager/templates | Library shortcut shelf + `library/collections/{id}` / rule | Legacy deep links resolve to the Library successor; no standalone manager/template page. |
| Browse tab (`BrowseScreen`) | `browse` (#11) | Real M3 hierarchy and shared search entry. |
| Search (`SearchScreen`) | shared `search?origin=…` (#12) | Unified one-submit incremental results; exact identity merge; no advanced filters or permanent source lanes. |
| Source/local detail | canonical `book/{sourceId}/{remoteBookId}` (#9) | One Room-first detail surface. |
| Standalone directory | chapter section inside Detail (#9) | Legacy directory deep links resolve to Detail's integrated chapter section; no normal separate directory screen. |
| Reader | `book/…/reader/{chapterId}` (#10) | Direct progress seek + complete settings; semantic locator remains truth. |
| Remote library | `browse/source/{id}/remote-library` (#13) | Explicit local-copy flow and three layouts. |
| Verification flow | `source/verification` (#14) | Retained host boundary. |
| Transfer/settings/more/about | #15–20 | Canonical More routes; no legacy aliases. |
| Missing today: history/updates/tags/mirror/rules/help | #3–8, #20 | New fixture-only RC2.1 coverage. |

### 2.4 Review Graph coverage

`ReviewNodeCatalog.kt` version 3 is the executable review-scope authority. Its 28 stable nodes cover the 20 route/surface rows above, Reader seek/settings high-risk work and six cross-cutting tasks. The table's required-state cells total 105 route-state obligations; a node may aggregate related states, but may not erase them. Version 3 makes the Standard Reader partial→full upward-drag, unchanged quick-controls tree, full-height downward-dismiss and Back-collapse state machine explicit in B03.

Node families are `L01–L08` Library, `B01–B03` Book/Reader, `S01–S04` Source, `M01–M07` More and `X01–X06` cross-cutting. Route entry records only `visitedAt`. AI triage, human review, approval and visual/interaction evidence remain independent. Removed routes migrate to their successor node rather than reappearing as standalone screens.

A–H remains useful as the fastest visual-direction smoke set. It currently samples approximately seven of the 20 formal route/surface rows and therefore never satisfies the full Review Graph or 105-obligation gate.

---

## 3. A–H visual-direction smoke — B041 revision

Binding input: `tsuyomi-atlas-b0417a5d6ea7-review.json`, SHA-256 `4886767c7ab846f07e8c9afe59d0e1f82e2dc10f9843cd378792f8a9686a5c49`, manifest `b0417a5d6ea76e07c5d06cf93524c2d92defd3089186d133aaba7fe1d8a3f73b`. Its images/comments remain immutable rejected evidence. Earlier C3/B4 inputs remain bound provenance. Product assertions come only from Constitution A.2/A.5; this table assigns proof.

| ID | Smoke board | Required proof before capture |
|---|---|---|
| A | Library mixed flow | Phone three-column and wide adaptive-column geometry; AppBar `+` only; no final-page creation pill/text; equal shortcut/card geometry; E-ink explicit paging. |
| B | Detail + chapters | Full-width tag region with 4dp external vertical insets. The left FlowRow consumes remaining width and wraps to at most two rows; content-width `稍后再读` stays at the trailing edge. Tags, complete tonal add-tag and read-later share one geometry rule: `labelLarge`, 40dp visible height, 8dp horizontal inset, centered 48dp slots and `shapes.small`; border coordinates and corner radii match exactly. Clickable controls retain 48dp minimum targets; tags never use `+n`. Rating remains fixed at cover right. |
| C | Reader seek | **Deferred.** No emulator still is presented as approval evidence. Physical-device Reader testing closes the presentation contract later. |
| D | Unified Search | Field contains only one actionable trailing Search icon; no leading Search or source-selector control; submit searches local plus implicit active-source scope; result geometry remains aligned. |
| E | Updates | Only `正在检查更新` appears during working; no duplicate `正在刷新`; only incrementally found rows with dates. |
| F | Remote Library | Top-level refresh/copy-all; no `无需重复加入` copy; readable E-ink spacing. |
| G | Tags | Visible compact/list action; compact chips omit counts; list rows show book counts in both profiles; source read-only. |
| H | Reader settings | Standard remains the M3 sheet. E-ink is a dedicated full-screen AppBar page showing all `排版 / 页面 / 导航 / 设备` sections together; wide two-column and compact stacked evidence. |

Global smoke proof: every E-ink AVD PNG used by A–H, including status and navigation bars, passes full-frame grayscale (`R=G=B`). Board C has no approval image until the physical-device phase. Passing this section does not approve unsampled routes or states from §2.

---

## 4. Fixtures (deterministic, synthetic, seeded)

All fixtures are code-defined data in the atlas module; names/titles are synthetic (no real source/brand text). Seed constant `ATLAS_SEED = 20260813`; fixed clock `2026-08-13T09:30:00+08:00`.

| Fixture | Contents | Serves |
|---|---|---|
| F1 Mixed Library | 128 books + five default-created hideable/rebuildable system nodes + manual/smart collections + mirrors; nodes-first, fully mixed and manual-order modes; two-book drop fixture | routes #1–5; board A; S1–S4 |
| F2 Empty/view-empty | Real reason + recovery action + reviewer-selected restrained monochrome emoticon | routes #1–5; S14 |
| F3 Long text | 60+ hanzi titles, long authors, mixed CJK/Japanese/Latin/emoji/RTL; absent covers | routes #1/#9/#13; boards A/B/F; S15 font/locale stress |
| F4 Collection depth | Root + child maximum, blocked third level, smart rule, membership and counts | routes #5–6; S2/S4/S11 |
| F5 Mirror partitions | Website tree; local partition absent/present states after explicit creation; frozen/calibration states; one unrelated accepted remote unresolved write | route #8; S8 |
| F6 Updates | Date groups, Standard working indicator, E-ink static working glyph, partial/failed/cancelled sessions, exact anchors, schedule states | route #4; board E; S9 |
| F7 Transfer | 87 warnings + 23 conflicts; recovery and inclusion/exclusion states | routes #18–19; S14/S17 |
| F8 Sources/remote | Installed and gated sources; 100-book remote library; refresh-list, copy-all, selection/target import | routes #9/#11–14; boards B/F; S5/S12/S17 |
| F9 Branding | Synthetic valid/invalid/missing compact mark only; never used as cover fallback | routes #9/#11–14; S12/S17 |
| F10 Covers | Procedural covers + failed/absent/stale; fixed 3:4 geometry; long-title fallback | routes #1/#9/#13; boards A/B/F; S1/S5/S12/S15 |
| F11 Reader | Chapters with semantic anchors/ticks, tap seek, drag preview, origin history, Standard partial/full sheet anchors, E-ink full-screen complete settings sections, complete setting ranges, offline/verification | route #10; boards C/H; S6–S7/S14/S15 |
| F12 Preferences/tutorials | Unknown schema + route-owned introductions; concise and task-specific | routes #4/#6/#8/#9/#18/#20; S11/S17 |
| F13 Search | Exact-identity local/remote duplicate, same-title distinct identities, implicit all-active and source-bound scopes, source success/failure and aggregate progress; no selector/leading Search control and no D33 descriptors/advanced filters | route #12; board D; S10 |
| F14 Tags | 120 local + 180 source tags, collisions and read-only ownership; compact chips omit counts and list rows show counts | route #7; board G; S13 |

---

## 5. Motion storyboard (recordings; §7 matrix applies)

Recording format: Standard scenes use 60fps/30fps as assigned by §6.1; reduced-motion and E-ink record only state replacements needed to prove their profile-specific contract. Keyboard/DPAD and TalkBack passes accompany scenes marked †.

| Scene | Flow | Expected proof |
|---|---|---|
| S1 † | Cold start → phone three-column / wide 150dp-minimum adaptive Library grid → move shortcut shelf | Equal chrome/cards; Standard horizontal cue, scroll and drag; E-ink button page/move; visible non-gesture equivalent and focus restore |
| S2 † | Hide/rebuild system node; open system/collection/mirror nodes → Back | Automatic membership restrictions, direct navigation, two-level cap, focus restore, no management hub |
| S3 † | Grid → dense → compact; nodes-first → fully mixed → manual order | Same identity rules, manual position persistence, stable anchor, page clamp and announcement |
| S4 † | Long-press edit; Standard drag one book onto another → name/confirm; E-ink select/button equivalent; select books/nodes; swipe one Standard row | Both books atomically enter the new folder; replacement AppBar, explicit check, visible equivalent, cancel |
| S5 † | Detail rating/tags/cache → verify full-width region, remaining-width two-line FlowRow, trailing content-width read-later, complete add-tag and shared group geometry → scroll down/up/idle FAB states → chapter filter/sort/jump → reader → Back | Tag/add/read-later all use `labelLarge`, 40dp visible height, 8dp horizontal inset, centered 48dp slots and `shapes.small`; visible border coordinates/radii match, click targets remain 48dp, no `+n`, read-later stays trailing, and caller/focus restoration remains intact |
| S6 † | **Deferred to physical device:** Standard hold/move/cancel/release/return origin; E-ink discrete target/confirm/cancel | No current Atlas still or emulator trace approves seek-preview presentation; later physical-device evidence must prove exactly one `LocatorCommit` |
| S7 † | Standard: partial quick settings → full height → dismiss/reopen/groups/Back/scrim. E-ink: open dedicated full-screen AppBar page → scroll complete `排版 / 页面 / 导航 / 设备` inventory → Back | Standard proves four first-viewport controls, two anchors and close rules; E-ink proves opaque immediate replacement, complete controls on one route, wide two-column/compact stacked layouts, safe insets and focus order without sheet gestures or group subpages |
| S8 † | Mirror default → explicitly create local organization → website operation | Local partition absent before creation; typed trace contains `MirrorLocalMutation` and zero `RemoteIntent` |
| S9 † | Updates check → Standard/E-ink working → partial/failure detail → confirm seen | Profile-specific indicator; E-ink title/status/action readable; exact-anchor local event |
| S10 † | Enter query → verify inert → trailing Search submit → local/remote unified results → source failure | Trace shows zero pre-submit query events, no selector/leading Search control, one `SearchSessionStarted` carrying local plus the implicit route-owned active-source scope, one progress/flow and exact merge |
| S11 † | Rule editor all control types + invalid AST | Correct M3 controls, caps, applicability and draft restoration |
| S12 † | Remote Library refresh → select → copy-all target picker → cancel/success | Visible commands, readable E-ink titles/spacing, aligned actions and typed local-only intent trace |
| S13 † | Tags local/source tabs → search/sort/edit | Ownership never mixed; compact chips omit counts, list rows show counts; source tags read-only; no `·` or instructional prose |
| S14 | Empty/error/offline/system bars/cutout | Real state content, one recovery, no duplicate chrome; E-ink full-frame grayscale |
| S15 † | Exact inventory subcases: Library rotate, Detail split, Reader fontScale 2.0, modal DPAD/TalkBack, cutout/system-bar pair | Route, selection, locator, focus and safe insets preserved for each separately inventoried artifact |
| S16 † | History content → clear confirm/cancel → clear; cross seven-day boundary | Only task-relevant metadata; exact date/time after seven days; cancel no-op; clear preserves semantic progress |
| S17 † | Browse → More → Display → Help search/accordion → source context | Consistent real-M3 hierarchy; no renderer-internal narration; searchable help; compact source mark only when disambiguation is needed |

---

## 6. Device / window / profile matrix

Baseline devices remain `Tsuyomi_API29` 1080×2400@420 and `Tsuyomi_EInk_API29` 1264×1680@240. During `phase4-standard-first`, only the Standard device is active for routine review; the wide and compact E-ink devices remain frozen restoration targets and are not routine completion requirements.

### 6.1 Canonical inventory schema

The route/state table and `ReviewNodeCatalog.kt` are the checked-in finite inventory. Each R2 run writes its exact active-profile artifact index under `.local/` or `build/`, including unique ID, route, state, profile, device facts, artifact kind, file path and assertion/evidence owner. Do not require a nonexistent checked-in generated inventory file; deferred E-ink identities remain reserved by the table/catalog until restoration.

| Dimension | Inventory rule |
|---|---|
| Profile | Execute every assigned Standard entry now. Retain exact E-ink entries as deferred inventory; do not generate, approve or update them until the restoration trigger. Board C seek-preview remains deferred independently. |
| Theme | Light is canonical; dark/dynamic are supplementary assigned cases. E-ink ignores requested color theme. |
| Window/device | Standard phone is the active reviewer device. Wide and compact E-ink geometries remain exactly enumerated deferred targets; wide Library still uses the 150dp-minimum adaptive-grid contract when restoration begins. |
| fontScale | 1.0 canonical; 1.3 assigned archetype cases; 2.0 on every Fs2 route and every modal through explicit entries. |
| Locale | zh-CN canonical; en and ar/RTL assigned to exact archetype/F3 entries. |
| Input | Touch canonical; keyboard/DPAD/TalkBack on every † scene, modal and selection entry. |

API 29 capture setup fixes locale/time zone, clock, battery, notification state, navigation mode and bar icon appearance before every run. The manifest records the observed system-bar configuration and measured content bounds; nondeterministic system chrome rejects the run.

Physical-device evidence: Standard human review remains active. E-ink panel ghosting, focus visibility, volume keys, dialog opacity, WebView return and reading comfort are preserved as restoration requirements and are not claimed during the freeze.

---

## 7. Capture workflow — correctness before artifact completeness

This section applies when producing a final immutable manifest-bound evidence bundle or the later E-ink restoration bundle. Routine affected-state and complete AI review execution is owned by `SKILL.md`; it must not repeat this legacy reconciliation/bundling pipeline.

1. **Review reconciliation:** build a versioned canonical `review-input-set.json` containing all four immutable source hashes from Handoff §1.2, all 79 expected stable IDs, and each ID's normalized obligation/supersession. Its canonical JSON SHA-256 is the aggregate review-input-set hash. Require `expectedIds == ledgerIds == assertedIds` with no extras or duplicates.
2. **Contract conflict scan:** search all binding documents and reference behavior before edits. Any old rule conflicting with Handoff §1.2 is rewritten or explicitly marked historical first; implementation may not carry both through flags or special cases.
3. **Pre-render ledger and canonical inventory:** freeze every logical artifact from §6.1 plus visible-element allowlist, initial-viewport task inventory, density target, system-window requirements and assertion IDs. A board without explicit stop-ship assertions cannot be implemented or captured.
4. **Prototype implementation:** fixture-only Compose, Standard real M3 delegates, profile-shared task/state order and deterministic synthetic fixture ports. Ports emit typed action/event traces; they never import production interfaces or execute Room/network/source code.
5. **Interactive task smoke:** operate the complete task on the assigned API 29 AVDs. Record typed traces such as `SearchSessionStarted`, `MirrorLocalMutation`, `RemoteIntent`, `LocatorPreview` and `LocatorCommit`; stateful contracts require recordings/semantics plus event assertions.
6. **Visual counter-review:** inspect every candidate frame at fit and 1:1 while reading every mapped review obligation. Any unresolved counterexample rejects the frame before bundling.
7. **Automated assertions:** exact inventory identity, `expectedIds == ledgerIds == assertedIds`, event counts/order, content-copy denylist, fixed geometry/readable text bounds, initial-viewport inventory, expected icons/actions, system-bar visibility and E-ink full-frame grayscale. Missing assertion means failure.
8. **Immutable bundle:** only after 1–7 pass, produce the browser reviewer and manifest-bound comment storage. Manual review remains separate and does not authorize production extraction.

Capture outputs:

- **Stills:** every required route/state/window frame for active profiles; deferred E-ink identities remain reserved and unchanged.
- **Recordings:** interaction contracts selected for active profiles. E-ink paging/seek recordings return with the restoration bundle.
- **Semantics logs:** TalkBack/focus traces for † scenes and every modal.
- **Direct-render smoke boards:** A–H product-faithful visual-direction evidence with external reviewer explanation only; not a replacement for the 28-node Review Graph.

The active Standard API 29 AVD remains mandatory for platform windows, real interaction, keyboard/DPAD and task smoke. Host rendering may supplement component/state coverage. E-ink AVD and physical-device passes resume as a complete retained scope after the explicit policy trigger.

---

## 8. Artifact identity, lineage and immutability

```
atlas/<const-ver>/<review-input-set-hash>/<prototype-source-hash>/<capture-run-id>/
  review-input-set.json
  artifact-index.json
  stills/<inventory-id>__<sha12>.png
  recordings/<inventory-id>__<sha12>.mp4
  semantics/<inventory-id>__<sha12>.txt
  traces/<inventory-id>__<sha12>.json
  acceptance-ledger.json
  manifest.json
  manifest.sha256
```

- Collection uses the per-run `artifact-index.json`; exact filenames only. Recursive regex discovery from mixed directories is prohibited.
- The bundle generator fails on duplicate/extra/missing artifact identities, unknown files, review-ID set mismatch, absent required evidence, source/output hash mismatch or rejected-run input.
- Image URLs contain the content hash. Reviewer localStorage keys contain the manifest hash. A new manifest cannot display cached prior pixels or inherit comments.
- `manifest.json` records the aggregate review-input-set hash plus all constituent review hashes, constitution/handoff/reference hashes, prototype source and artifact-index hashes, exact capture tool/configuration, measured dp bounds/system chrome, per-file SHA-256, assertion/event results, generator version and timestamp. `provisional=true` and `productionAuthorized=false` are explicit.
- Output directories are write-once. Regeneration creates a new run directory/unique entry filename and never overwrites prior evidence.
- Reviewer entry displays generator version, manifest/review-input-set/source/ledger hash prefixes. A parent index is only a redirect/link.
- Manual approval binds exact manifest + review-input-set + artifact index + acceptance ledger + route/module comments. Mechanical integrity without ledger success is not approvable.

---

## 9. Manual reviewer contract

The in-app reviewer is driven by `ReviewNodeCatalog.kt`, not by the historical A–H browser tabs. It presents all 28 stable nodes, their required states, operations, visual checks and human-only checks. A–H may remain as a fast read-only visual smoke index.

Review state is layered:

- `visitedAt` proves only that a node-resolving surface was opened;
- `aiTriagedAt` records an AI draft;
- `humanReviewedAt` records human task execution or verdict;
- `approvedAt` exists only after a human `ACCEPT` verdict.

Node comments identify `AI`, `HUMAN` or `MIXED` authorship and auto-save on debounce plus node exit. Visual and interaction evidence use separate lowercase SHA-256 fields. `AUTOMATION`, `PAUSED` and `HUMAN` control modes permit immediate same-state handoff. AI may draft and attach evidence but may not approve, update goldens or replace a human conclusion.

Before the current Phase 4 Standard UX milestone, the reviewer must show:

- all 20 route/surface rows and 105 route-state obligations accounted for on Standard by one evidence owner each;
- exact counts for visited, AI-triaged, human-reviewed and approved nodes;
- required Standard traces/semantics, active-profile device facts and artifact hashes;
- every E-ink obligation explicitly deferred against the policy SHA and frozen baseline build ID;
- explicit unresolved human-only items, including Reader seek, TalkBack, brand judgment and high-risk copy.

Before any later full design freeze, E-ink readiness claim, or prototype extraction/deletion, the retained wide/compact E-ink inventory, full-frame grayscale, E-ink Journeys, and physical E-ink human review return in full. The Standard milestone never satisfies that restoration gate.

Any failed assertion prevents approval. Empty comments are omitted. Old builds are read-only and excluded by default. Review comments, AI triage or a passing smoke set never authorize production work.

## 10. Extraction and deletion protocol (only after RC2.1 manual approval **and** separate implementation authorization)

1. Record the §9 approval tuple: RC2.1 version, handoff/reference hashes, prototype build ID, review catalog version, full 20-surface/105-obligation evidence, node comments/progress, manifest and evidence hashes. This freezes reviewed choices but authorizes no production change.
2. Obtain separate, explicit Phase 4 production implementation authorization. Without it, keep the Atlas fixture-only; do not edit production UI, move production fixtures/goldens or delete the prototype.
3. Reissue the constitution as **v1.0** with approved RC2.1 outcomes incorporated and register route/component amendments.
4. Extract only approved tokens/contracts into `core:ui` and production feature API shapes. Standard semantic components must wrap the real M3 delegates proven by the Atlas; rewrite—never copy—the namespaced prototype forks.
5. Move approved fixtures (sanitized of atlas namespace) and golden images into the owning production modules' screenshot-test sources.
6. **Delete** `:prototype:ui-atlas` entirely: module, settings inclusion, fixtures not moved, recordings working copies (approval-evidence copies retained outside the repo), every component fork. No `Legacy`/`V2`/compat aliases.
7. Run constitution §16.4 static checks: build fails if any prototype namespace/symbol/dependency remains in the release graph; DAG check green; denied-import check green.
8. Regenerate the production golden matrix from production composables (atlas goldens are reference, not reused pixels).
9. Close UI-P0-B with both the approval tuple and explicit implementation authorization as evidence; continue only within the separately authorized 4A–4C scope.

---

## 11. Explicit non-goals

- No production code, repository, network, extension, or credential use in the atlas.
- No copied/traced artwork from any reference project (Hikari marks, Mihon assets, etc.); atlas covers/marks are procedurally generated originals (F9/F10).
- No production edits of any kind in this phase (planning/artifact work only).
- The atlas is not a component gallery, not a second visual system, and never ships.
