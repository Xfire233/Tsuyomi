<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Tsuyomi UI Constitution — active fixture contract

Status: **FIXTURE REVISION REQUIRED; PRODUCTION IMPLEMENTATION NOT AUTHORIZED.** The latest binding review is `build/atlas-review/tsuyomi-atlas-b4dc3465bf7d-review.json`, SHA-256 `92ef4715313a4266dd99f13f6572b671bc9b1bd5ee88e3640ac784925faea5b3`, bound to manifest `b4dc3465bf7d4f1e85d0ac92aee50d8daa5f8235bfb650a0a84c9c408a57fbee`.

Document responsibilities are deliberately narrow:

1. **This constitution is the only active product-visible UI contract.**
2. `UI_ATLAS.md` contains executable evidence mechanics only; it cannot invent product behavior.
3. `DESIGN_DIRECTION_HANDOFF.md` is append-only review provenance and supersession history; it is not a second contract.
4. `DESIGN_REFERENCE_REVIEW.md` records research and license boundaries only.
5. `PHASE_4.md` owns scope, sequencing and authorization; ADR/architecture documents own domain and storage invariants.

If a later section, historical review, Phase draft or prototype comment conflicts with the active spine below, the spine wins and the conflict must be removed rather than implemented as a variant. Later detailed sections may explain or constrain implementation, but may not add a new visible element, route, action or persistent sentence absent from the spine.

## A. Active constraint spine

### A.1 Global invariants

| ID | Invariant |
|---|---|
| `G-01` | Jetpack Compose + real Material 3 is the single Standard component language. E-ink preserves the same task/state result with opaque monochrome, explicit borders, immediate replacement and discrete controls. |
| `G-02` | Persistent UI copy is limited to domain content, concise control labels, progress/date/count and actionable failure/destructive scope. No tutorials, merge rules, source scheduling explanations or redundant disabled-state prose in normal content. |
| `G-03` | Every screen is AppBar → Content → optional PersistentFooter. Primary actions live where the task is expected; standard icon actions use icons without explanatory sentences. |
| `G-04` | Layout is route-owned. Phone Library grid is three columns; wide Library grid derives columns from readable card width. Source layout hints never alter host layout. |
| `G-05` | E-ink evidence includes real system bars and must be full-frame grayscale. Long lists paginate; swipe-only, animation-only and color-only meaning are forbidden. |
| `G-06` | One `BookIdentity` owns one Detail. Detail owns chapters. Search has one route, one submit, one aggregate progress and one unified result stream. |
| `G-07` | Mutations are local or remote explicitly, never implied. Working/success/error/cancelled/unresolved are visible and keyed to the target operation. |
| `G-08` | Fixture approval freezes only the reviewed visual contract. Production work still requires separate explicit Phase 4 authorization. |
| `G-09` | In every cover-leading list row, the complete visible text/source-mark stack stays within the cover's top and bottom edges. The cover container may scale by row family; no label may hang below or above it. |
| `G-10` | A state-bearing icon action always depicts and announces the current state, never the state produced by activation. Layout controls show the current layout icon and `当前布局：<值>，点按切换布局`; activation cycles to the next supported layout without changing identity, order or selection. |
| `G-11` | Activating any Library system node, collection, child collection or website-mirror folder pushes a dedicated page with its own app bar and page-specific actions. The Library root never replaces its main content in place, and folder structures never expand inline. Back and Up traverse the same semantic parent chain. |

### A.2 Active surface contracts

| Board | Visible contract | Explicitly absent |
|---|---|---|
| A · Library | Mixed system/collection/mirror/book flow. AppBar has Search and one `+` creation action. No second textual creation control. Phone grid = 3 columns; wide grid adapts by readable card width. Shortcut items remain equal-height; E-ink uses pagination/move buttons. | Creation FAB/pill/final-page instruction; fixed three columns on a wide E-ink canvas; source-controlled layout. |
| B · Detail | Header places the fixed five-star local rating to the right of the cover, directly below reading progress. The full-width tag region keeps every actual tag in one left FlowRow and places one icon-only tonal `+` button immediately after the final tag; it stays on the same row whenever the measured content fits and never reserves an empty add-action row. `稍后再读` remains content-width at the trailing edge. Cache stays top-level. Identity, tags/actions, introduction and directory are distinct visual modules that share one compact spacing, icon and typography grammar; module dividers are absent because concise domain nouns and icons already establish hierarchy without extra rules or instructional copy. Introduction body text, the directory title and every chapter title share the same title-text column rather than drifting with their marker width. The directory owns one compact borderless header row containing the chapter-list icon, `全文目录`, total chapter count, filter icon plus `仅看未读`, and one adjacent direction icon. The direction icon and accessibility label report the current order; it has no visible `正序 / 倒序` copy. In Standard, recently updated chapters use a red dot and other unread chapters use a primary-blue dot of the exact same size; update takes visual precedence when both states apply. E-ink represents the same equal-size distinction with an outlined update ring versus a filled unread dot. Read chapters have no leading marker. Downloaded chapters retain one trailing download-complete icon, and current chapter retains its `当前` marker. Local removal plus website remove/move live only in the top-right overflow. | A standalone chapter heading/count paragraph; module dividers or module-specific visual systems; persistent instructions explaining obvious modules; a visible `添加标签` label; a separate add-tag column or reserved blank row; bordered, filled, full-row or equal-width directory buttons; visible sort-state prose; a current-chapter toolbar action; chapter-title indentation that differs from the introduction/title column; unequal update/unread marker sizing; an ambiguous single marker for both update and unread; visible `已读 / 已下载` status prose; generic `网站操作`; bottom-page duplicates of overflow actions; rating below the header or inside the tag region. |
| C · Reader seek | **Deferred from fixture visual approval.** Semantic locator and tap-to-jump remain architectural requirements, but WYSIWYG preview presentation is decided only during later physical-device Reader testing. | Current Atlas stills or emulator-only preview claims presented for approval. |
| D · Search | Query draft is inert. The field contains one actionable icon only: the trailing Search icon. One submit starts local plus every active source, or the active route-bound source when explicitly addressed; there is no source-selector or decorative leading icon in the field. Results use one unified stream and exact identity merge. In list rows, the enlarged cover spans the complete visible text/source-mark stack and every edge aligns. | Leading Search decoration; source-selector button beside submit; separate submit row; text/source mark extending beyond cover bounds; dormant-source status lanes; advanced filters; teaching copy. |
| E · Updates | During a check, result rows appear incrementally only as updates are found. The single visible working message is `正在检查更新`; no separate `正在刷新` overlay duplicates it. Every row shows identifiable title, update anchor, update date and primary action. Standard uses short M3 progress; E-ink uses a static glyph. | Duplicate working banners; pre-populating the full scan target as if every item updated; missing date; animation on E-ink. |
| F · Remote Library | Refresh list and copy all remain top-level. Existing local items are disabled/identified by control state and accessibility semantics. | `无需重复加入` or equivalent redundant explanation beside each row. |
| G · Tags | `本地 / 来源` ownership remains explicit. A visible AppBar action toggles compact chips and list rows. Compact chips omit counts; list rows show each tag's book count. Source tags remain read-only. | Hidden layout toggle; counts missing from list rows; counts inside compact chips; permanent capability explanation. |
| H · Reader settings | Standard retains one M3 sheet. E-ink uses a dedicated opaque full-screen settings page with a real AppBar and every `排版 / 页面 / 导航 / 设备` control visible on one scrollable route. Wide E-ink uses two balanced columns; compact E-ink stacks the same sections. No group-navigation buttons or sheet behavior remain in E-ink. | E-ink quick-controls-only page; E-ink sheet anchors/gestures; separate group subpages; giant full-width sliders followed by unused blank space. |

### A.3 B4 decisions recorded 2026-08-15

| ID | Decision |
|---|---|
| `B4-01` | Wide Library grid uses a 150dp minimum readable card width with no arbitrary maximum column count. Compact phone remains exactly three columns. |
| `B4-02` | **Superseded by `B041-D`.** The earlier normal Search source selector is removed. Dormant and credential-expired sources remain outside the implicit active-source scope and belong on Browse/source surfaces. |
| `B4-03` | A running update check displays only items newly discovered in that session. The complete pending inbox returns when the session completes. |
| `B4-04` | Only Reader seek-preview visual approval is deferred to physical-device testing. The Reader route and Board H settings remain Atlas-reviewable. |

### A.4 C3 review decisions recorded 2026-08-15

| ID | Decision |
|---|---|
| `C3-B` | Local rating is fixed inside the Detail header, right of the cover and below reading progress. |
| `C3-D` | Search list covers are enlarged and bound the complete visible text/source-mark stack; no row content crosses their top or bottom edges. |
| `C3-E` | Updates working state shows only `正在检查更新`; the generic `正在刷新` overlay is suppressed there. |
| `C3-G` | Tag list rows show book counts; compact chips do not. |
| `C3-H` | E-ink Reader settings starts at least 64dp below the safe-area top. |
| `C3-ALL` | Cover-leading list rows obey one cover/text vertical-alignment rule across surfaces. |

### A.5 B041 review decisions recorded 2026-08-15

| ID | Decision |
|---|---|
| `B041-B` | Detail tags and add-tag use one adaptive rounded-rectangle container family; add-tag is tonal and labelled, never icon-only or pill-shaped. |
| `B041-D` | Search field keeps only the trailing actionable Search icon; active-source scope is implicit and no selector/leading icon is shown. |
| `B041-H` | E-ink Reader settings is redesigned as one complete full-screen grouped page; Standard sheet behavior remains unchanged. |

### A.6 Final direct Detail decision recorded 2026-08-15

| ID | Decision |
|---|---|
| `B041-B2` | The Detail compact module is an equal-width split. Left: every available tag plus the always-complete add-tag action, with vertically centered labels and adaptive wrapping. Right: one rounded-rectangle `稍后再读` button fills the entire half and matches the left flow's adaptive height. Available tags never collapse into `+n`. |

### A.7 Final Detail width and E-ink alignment decision recorded 2026-08-15

| ID | Decision |
| `B041-B3` | **Superseded by `B041-B4` for outer-region width and by `B041-B5` for visible control height.** `稍后再读` uses its current localized text/font scale rather than an arbitrary half-width; the tag FlowRow wraps to at most two rows. |
| `B041-B4` | The tag region itself remains full-width. Only each tag, `添加标签` and `稍后再读` are content-width controls whose widths follow their text/font scale and padding. The region uses 4dp external vertical insets. Visible control sizing is superseded by `B041-B5`. |
| `B041-B5` | **Superseded by `B041-B7` for typography and horizontal inset.** This decision established compact 40dp visible controls, non-stretched read-later and separate 48dp minimum interactive targets; `B041-B7` later unifies all three controls on `labelLarge` and 8dp horizontal inset. |
| `B041-B6` | Tags occupy the same 48dp layout slot as add-tag/read-later, with their 40dp visible Surface centered inside it. Therefore all three visible borders share identical top and bottom coordinates; equal numeric height without equal vertical placement is rejected. |
| `B041-B7` | Confirmed before implementation: the left tag FlowRow consumes remaining width and `稍后再读` is content-width at the trailing edge. Tags, add-tag and read-later uniformly use `labelLarge`, 40dp visible height, 8dp horizontal content inset and centered 48dp slots. |
| `B041-B8` | Group consistency correction: tag, add-tag and read-later all use `shapes.small`. Standard therefore uses 8dp corners for all three; E-ink uses 0dp for all three. Group typography, insets, visible height and slot alignment remain shared rather than independently overridden. |

### A.8 Direct Detail correction recorded 2026-08-23

| ID | Decision |
|---|---|
| `B041-B9` | **Supersedes `B041-B`, `B041-B2` and the add-action parts of `B041-B4`–`B041-B8`.** Add-tag is one standard tonal Material icon button containing only `+`, placed immediately after the final actual tag inside the same FlowRow. Its accessibility label remains `添加标签`. The layout may wrap only when measured width or font scale requires it; it never reserves a dedicated add-action row. |
| `B041-B10` | The directory owns one compact borderless header row. A standard chapter-list icon, `全文目录` and the total chapter count occupy the leading identity group; the filter uses a standard text button with no visible container (`filter icon + 仅看未读`), followed by one direction icon button. All controls preserve 48dp targets. The order icon and accessibility label always describe the current `正序 / 倒序` state per `G-10`; no visible order text, standalone chapter heading/count block or `当前章节` action remains. |
| `B041-B11` | Chapter state is compact and explicit. Standard updated chapters use an 8dp error-red dot; other unread chapters use an equal-size 8dp primary-blue dot. Update takes precedence when a chapter is both updated and unread. E-ink uses an equal-size 8dp outlined ring for update and an 8dp filled dot for unread. Read titles use regular weight plus the stronger `outline` gray and no leading marker. Unread titles use dark medium weight. Downloaded chapters show one trailing download-complete icon, and current chapter retains its `当前` marker. No visible `已读 / 未读 / 已下载 / 有更新` prose is added; TalkBack receives explicit update/read/download/current state descriptions. |
| `B041-B12` | `移出书架`, `从网站移除收藏` and `移动网站收藏` live only in the Detail top-right overflow. The page body has no duplicate footer actions and no generic `网站操作` proxy. Dormant sources omit unavailable website mutations. |
| `B041-B13` | Detail is composed from independently styleable identity, tag/action, introduction and directory modules. Module boundaries use the same compact spacing, concise icon/title and typography grammar without horizontal divider lines; cover/content/control structure makes each purpose evident without persistent instructional prose. Introduction copy, the directory title and chapter titles align to one shared title-text column; chapter status markers occupy the icon column without changing text indentation. The layout avoids decorative vertical padding. The directory module owns its header, controls and chapter rows as one isolated component. |

No open product-visible boundary remains from the B4, C3, B041 or final direct Detail reviews. New ambiguity must be added here before implementation.

---

## 0. Terminology (reconciled Designer ↔ Adviser; unchanged from provisional)

| Term | Meaning |
|---|---|
| **DisplayProfile** | `STANDARD` / `EINK` in code; "Standard" / "E-ink" in prose. Never "mode". Resolved once at root into an immutable `DisplayEnvironment`. |
| **Host** | The Tsuyomi application. **Source**: an installed extension package. |
| **Library context** / **LibraryContextId** | The current Library node or root flow: `All`, a visible SystemNode presentation, a LocalCollection, or a WebsiteMirrorBinding. SystemNode presentations may be hidden/rebuilt; context identity still keys layout, sort/manual order and page state. |
| **Root** | Top-level destination: `书架` Library / `浏览` Browse / `更多` More. Exactly three. |
| **Route** | A typed navigation destination string under one root's stack. |
| **Screen anatomy** | `AppBar` / `Content` / `PersistentFooter` (see §4). |
| **MutationState** | `idle → working → success \| error \| cancelled \| unresolved`, keyed per command target. |
| **Page vs Scroll** | E-ink long lists use explicit pagination ("page"); Standard uses scroll. Never mixed per surface. |
| **Presence / Pin / Read Later / Annotation** | Presence = `LOCAL_PIN`、`READ_LATER`、`MANUAL_COLLECTION`、`WEBSITE_MIRROR` 四类 origin 的派生并集。Pin = `LOCAL_PIN`。Read Later = 独立本地 presence origin，不是 tag/普通 collection。Annotation = rating/local tags；annotation 自身从不制造 presence。 |
| **Mirror** | A per-source website-favourites snapshot (`WebsiteMirrorBinding` + immutable `MirrorNode` snapshot). Canonical home = Library root; Browse holds only deep links. |
| **CoverUiState** | Immutable cover render state defined in the **public API** of `core:media` (`org.tsuyomi.core.media.api`), produced only by `core:media`; `Ready` carries a host-owned, explicitly renderable `android.graphics.Bitmap`. Consumed by route owners and the pure `core:ui` renderer (§2.3). |
| **Source identity context** | A compact sanitized mark, optionally followed by the source name when the surrounding title does not already name it. It never creates a full-width branding band. |

Supersessions recorded through reconciliation:
1. **[SUPERSEDES Phase4FoundationAdviser UD-5 default]** Standard and E-ink regular-phone contexts default to fixed three-column **GRID**, not LIST; source layout hints are ignored. Layout rule in §5.4.
2. **[SUPERSEDES PHASE_4 D3 recommended default]** Explicit `移出书架` removes only `LOCAL_PIN` and direct `MANUAL_COLLECTION` memberships. It **retains** `READ_LATER`, `WEBSITE_MIRROR`, rating/local tags, semantic progress, known metadata, and browsing history; clearing Read Later is a separate explicit local command. No website operation occurs, and implicit origin loss never deletes annotations. Confirmation copy states this (§9.4).
3. **[SUPERSEDS current implementation]** Direct Material interactive controls/dialogs inside `app`/`feature/*` are **forbidden**; only `core:ui` may wrap Material (§6.1). Current chips/switches/checkboxes/dialogs are defects, not variants (§17 P1).
4. **Folder delete** offers *reparent-children-to-current-parent* **or** *delete-subtree*; books are never deleted (worst case: membership removed, annotations retained) (§9.4).

---

## 1. Principles (ranked; on conflict, earlier wins)

1. **Task before screen.** Core tasks (read/continue, organize, add, import) are reachable from a visible labelled control where the user naturally looks — never gesture-only, overflow-only, or dependent on another screen's stale state.
2. **Product UI does not explain itself continuously.** Persistent copy is limited to user/domain data, control labels that icons cannot express clearly, current progress/count, actionable failure/offline state and destructive scope. Tutorials, implementation rules, identity/dedup logic, source scheduling and “you can do X here” guidance belong in first-entry introduction, Help or accessibility descriptions—not normal content screenshots. If an icon is standard and unambiguous, visible explanatory prose is prohibited.
3. **Compact means task-complete per viewport.** Touch targets remain ≥48dp, but visual glyphs, labels and sliders may share one row and use overlapping/invisible hit padding. A layout fails if decorative headings, duplicate close controls, repeated context or oversized containers prevent the expected high-frequency tasks from fitting in the initial viewport.
4. **One identity, one surface.** One `BookIdentity` = one canonical detail contract, regardless of entry root. Ownership changes data availability and enabled actions, never which screen renders.
5. **One action, one truthful outcome.** Every mutation exposes the full MutationState taxonomy with persistent, accessible feedback; ambiguity is never displayed as success.
6. **Navigation is a contract.** Back (chronological), Up (semantic parent), root selection (independent stacks) are specified and tested separately (§13).
7. **Local-first is an execution/result-order property, not extra user ceremony.** A submitted search starts local and the route's implicit active-source scope in one session; local truth may render first, but the user never performs a second “online search” confirmation and normal UI never narrates the merge algorithm.
8. **Same behavior, profile-specific presentation.** Standard/E-ink share routes, state trees, and task results; only presentation differs. No per-profile screen/state subclasses, no `eInk` boolean parameters in feature APIs.
9. **Progressive disclosure without concealment.** Secondary actions stay labelled and keyboard/TalkBack-reachable; disclosure patterns are the ones defined in §8, not ad-hoc hiding.
10. **Bounded and honest.** Every list, input, and result has a documented bound; truncation is always paired with an expansion path (§8); no unbounded network or rendering work.

---

## 2. Ownership model (complete; Adviser P1-1 CLOSED)

### 2.1 Module responsibilities

- **`core:display`** — owns device classification, the immutable `DisplayEnvironment` (profile, motion policy, redraw epoch), and the *policy decisions* derived from them. It owns policy inputs, **not** visual pixels, list queries, screen state, or bulk UI-preference storage.
- **`core:preferences`** — owns the **UI-preference schema** (§2.4): persisted interface preferences (display/theme/dynamic choices, per-context×profile layout overrides, saved per-context sorts, tutorial seen-versions), the integer schema version, the deterministic migrator chain, the unknown-newer preservation protocol, and the canonical reset implementation. Do not overload `core:display` with this storage.
- **`core:ui`** — owns *all* visual tokens, semantic components, modal/navigation/state/motion primitives, and the shared pure renderers for `BookListItem` (list/grid), covers (`CoverImage`), tags, mirror provenance, and update badges. The only module permitted to wrap Material.
- **`shared:library-domain`** — owns the non-Compose types: `BookListItem`, `CoverRef` (opaque request identity only — **no decoded payload types**), `LibraryContextId`, `TagCatalogEntry`/`SourceTagIdentity`, `WebsiteMirrorBinding`/`MirrorNode`, `LibraryViewInstance`/`Template`, presence origins, query/sort/layout keys. No Room/Compose/Android/URL/painter types cross this boundary.
- **`core:library`** — route-independent queries and mutation use cases; sole coordinator for library/search/mirror/update behavior. `SearchCoordinator` owns the immutable planner, incremental lane session and process-restorable status through injected `LocalSearchPort`/`SourceSearchPort`; it never depends directly on Room, QuickJS or transport implementations. Ports only.
- **`core:media`** — validates signed covers/branding (§15), owns binary cache partitions, produces `CoverUiState`. Exposes a **public API package** `org.tsuyomi.core.media.api` (`CoverRepository`, `CoverRequest`, `CoverUiState`); all implementations are Kotlin-`internal` under `org.tsuyomi.core.media.internal`. The API package is the Android boundary where the decoded payload becomes concrete (`android.graphics.Bitmap`); it contains **no Compose types and no I/O entry points beyond the repository interface**.
- **`core:database`** — implements `shared:library-domain` repository ports. Exposes no Room entity to UI.
- **`source:extension-manager`** — verifies package signatures and signed branding/layout-hint declarations. Never owns rendering or Library truth.
- **`reader:*`** — reader engine + reader-owned surface composition (§14 exception zone), using `core:ui` primitives only.
- **`feature/*`** — screen composition only: immutable feature `UiState` + **one typed `UiAction` sink** + `Modifier`. Route owner (ViewModel) holds durable, query, mutation, modal, draft, paging-identity, and process-restorable state; the composable holds focus/scroll only when truly ephemeral, and then `rememberSaveable`.
- **`app`** — installs `DisplayEnvironmentProvider` + `TsuyomiTheme` exactly once at root, owns the single NavHost and root stacks, wires DI. No visual decisions, no dialogs, no feature booleans.
- **`build-logic`** — owns the static enforcement of §16.4. No runtime edges.
- **"No new modules"** means no additional UI-only modules beyond Phase 4's already-planned `shared:library-domain` / `core:library` / `core:media`, plus the `core:preferences` extraction (a re-homing of existing preference code, not a new UI module). The temporary `:prototype:ui-atlas` is non-production and is deleted after approval/extraction (§16.3).

### 2.2 Module DAG (binding; Phase 4 target, enumerated per module)

Scope: this DAG is **complete for the UI-boundary modules** (every `feature/*`, `core:ui`, `core:display`, `core:preferences`, `core:library`, `core:media`, `core:database`, `reader:ui`, `reader:engine`, `shared:library-domain`, `app`). For these modules, any edge not listed below is forbidden and statically rejected (§16.4 check 1). Pure infrastructure edges outside the UI boundary that are declared in `docs/architecture/MODULES.md` and current build files (e.g., `source:extension-manager → source:quickjs-runtime`, `core:webview`, `reader:tts`) remain as declared there; this DAG overlays and wins wherever both speak.

```text
shared:model, shared:locator, shared:source-contract, shared:backup, shared:smart-shelf
                 → (pure leaves; no Android/Room/Compose/network edges)
shared:library-domain
                 → shared:model, shared:locator, shared:smart-shelf     (pure types only)

core:preferences → shared:backup, shared:library-domain
core:display     → core:preferences, shared:model, shared:locator
core:ui          → core:display, shared:library-domain, shared:model,
                   core:media (public API package org.tsuyomi.core.media.api ONLY)
core:library     → shared:library-domain, shared:source-contract, shared:backup (ports only)
core:database    → shared:library-domain, shared:model, shared:locator,
                   shared:smart-shelf, shared:backup
core:media       → core:network, core:files, core:security, core:display,
                   shared:library-domain, shared:source-contract
core:network / core:files / core:security
                 → shared contracts only                                 (pre-existing)

reader:engine    → shared:locator, shared:model, shared:source-contract, shared:library-domain
reader:ui        → core:ui, core:display, reader:engine, shared:library-domain,
                   shared:locator, shared:backup, shared:source-contract,
                   core:media (public API package ONLY)

feature:library    → core:ui, core:display, core:library, core:preferences,
                     shared:library-domain, shared:model, shared:locator,
                     core:media (public API ONLY)
feature:book       → core:ui, core:display, core:library, shared:library-domain,
                     shared:model, shared:locator, shared:source-contract,
                     core:media (public API ONLY)
feature:search     → core:ui, core:display, core:library, shared:library-domain,
                     shared:model, shared:source-contract, core:media (public API ONLY)
feature:browse     → core:ui, core:display, core:library, shared:library-domain,
                     shared:source-contract, core:media (public API ONLY)
feature:settings   → core:ui, core:display, core:preferences, shared:library-domain
feature:extensions → core:ui, core:display, source:extension-manager (public API),
                     shared:source-contract
feature:reader     → core:ui, core:display, core:preferences, reader:ui,
                     shared:library-domain, shared:locator, shared:source-contract,
                     shared:backup, core:media (public API ONLY)
feature:backup     → core:ui, core:display, core:library, core:files,
                     shared:backup, shared:library-domain

app              → feature/*, core:ui, core:display, core:preferences, core:library,
                   core:database, core:media, source:extension-manager, reader/*,
                   core:webview                                            (DI + navigation wiring only)
source:extension-manager
                 → shared:source-contract, shared:model, core:network, core:files,
                   core:security, core:database (extension registry only; never Library truth)
build-logic      → (no runtime edges)
:prototype:ui-atlas
                 → TEMPORARY; self-contained fixture module; zero edges to/from any
                   production module; excluded from the release graph (§16.3, Atlas Spec)
```

**Current-edge transition dispositions (verified against current build files, 2026-08-11):**

| Current edge | Target disposition |
|---|---|
| `feature:library → core:database` | **Removed at 4A cutover** — replaced by `core:library` repository ports (forbidden edge 2). |
| `feature:backup → core:database` | **Removed at 4A cutover** — transfer repositories move behind `core:library` ports. |
| `core:display → DataStore` (direct) | **Removed at 4A cutover** — preference storage re-homed to `core:preferences`; `core:display` keeps the `→ core:preferences` read edge. |
| `feature:extensions → source:extension-manager` | Retained, narrowed to the manager's public API (§15 signature/branding verification). |
| `source:extension-manager → core:database` | Retained for the extension package registry only; never Library truth. |
| All other current edges | Conform to the target DAG above (or are added by it: `core:library`, `core:media`, `shared:library-domain`). |

**Forbidden edges (release-blocking; enforced by §16.4 static checks):**

1. `feature → feature` (any pair).
2. `feature → core:database` (module, Room runtime, or any Room entity type).
3. `feature`/`core:ui`/`reader:ui → org.tsuyomi.core.media.internal.*` (any implementation-package import). Only the public API package is importable; `app` wires the internal implementation into route owners by DI.
4. `feature`/`app` → interactive Material controls/dialogs/animation APIs directly (only `core:ui` wraps Material; §6.1).
5. `core:ui → core:database`, `core:ui → core:network`, `core:ui → app`, `core:ui → feature`, `core:ui → source:extension-manager`, `core:ui → DataStore`, `core:ui → NavController`. (`core:ui → core:media.api` is the single sanctioned media edge — the immutable state/repository types only; `core:ui` never calls `CoverRepository`.)
6. `core:media → Compose/ui` (any UI toolkit type).
7. `shared:* → Android/Room/Compose/network` (any platform type).
8. `reader:* → feature/*`.
9. `source:extension-manager →` any UI module.
10. `core:preferences → Compose/Room/network`.
11. Raw cover URLs, SVG bytes, branding payloads, cookies, or Room entities crossing into `feature/*` or `core:ui` **at type level** (not merely by convention).
12. Any `:prototype:ui-atlas` namespace, symbol, or dependency in the release graph.

### 2.3 CoverUiState ownership (binding; Adviser P1-2 CLOSED)

- `CoverUiState` is a sealed, immutable value in the public API package `org.tsuyomi.core.media.api` — `Absent | Loading | Ready(bitmap) | StaleReady(bitmap, provenance) | Failed(reason) | Fallback(FallbackSpec)` — where `Ready`/`StaleReady` carry a **host-owned, explicitly renderable `android.graphics.Bitmap`** (decoded to request size by `core:media`). This is the sanctioned Android API boundary: `shared:library-domain` carries only `CoverRef`/request identity and never pixel or platform types, so no consumer needs to downcast, resolve, or perform I/O to render.
- **Producer:** `core:media` only, via the public `CoverRepository.observe(CoverRequest)` interface. All fetch/decode/cache implementations are Kotlin-`internal` (`org.tsuyomi.core.media.internal`); `app` wires them by DI.
- **Requester:** the feature **route owner** (ViewModel) — never a composable. The route owner subscribes keyed by the complete request identity (`CoverRef` + decode size + display-profile conversion version) for **foreground-visible Standard rows** or the **current E-ink page** only; visibility loss cancels the subscription; the shared fetch stops when no subscribers remain.
- **Renderer:** `core:ui` `CoverImage` is **pure**: it receives an already-resolved `CoverUiState` plus the §15 fallback spec and renders it (only `Bitmap → ImageBitmap` conversion; no repository calls). It never starts, owns, retries, or cancels I/O, and recomposition must never initiate work. The static checks (§16.4) forbid `core.media.internal` imports in `core:ui`/`feature`/`reader:ui` and forbid `CoverRepository` references inside `core:ui` renderers.
- Fallback rendering (title/source text on a host or validated source-color base) is a pure function of `FallbackSpec`; invalid branding resolves to the generic host fallback inside `core:media` before consumers ever see state.

### 2.4 `core:preferences` schema ownership and unknown-newer preservation (binding; Adviser P1-4 CLOSED)

- `core:preferences` owns a single integer **UI-preference schema version**, persisted beside the preference payload, and a deterministic migrator chain `v_n → v_n+1` with a written migration report (what mapped where, what reset).
- **Unknown/newer schema version** (e.g., after a downgrade): the stored payload is **preserved read-only, byte-for-byte**. The app:
  1. never writes, overwrites, "migrates down", or partially rewrites the unrecognized payload;
  2. runs on safe **effective defaults held in memory only** (no persistence of those defaults);
  3. **blocks all preference writes** — any attempted preference mutation is rejected with a visible explanation, because persisting anything would destroy the newer payload;
  4. surfaces an explicit, dismissible prompt (`界面设置来自更新版本；保留或重置`) whose only mutation path is the §16.2 canonical reset;
  5. resumes writes only after (a) explicit user reset, or (b) a compatible upgrade build whose schema version ≥ stored version performs the deterministic migration.
- Migrations never touch books, collections, annotations, progress, history, credentials, source packages, scheduler state, mirror state, or remote-operation rows.
- The **canonical reset** is implemented once in `core:preferences`, exposed at exactly one UI location (`更多 > 显示 > 重置界面设置`; `more/help` deep-links, never duplicates), resets only interface preferences (display/theme/dynamic, per-context×profile layout overrides, saved sorts, tutorial seen-versions) to constitution defaults, and confirms exact scope with domain-data-untouched copy.

---

> **Application boundary.** Sections 3–16 specify visual tokens, interaction primitives, production architecture and verification mechanics. For fixture-visible behavior, their clauses apply only when compatible with the Active constraint spine; the spine is intentionally the small, complete list of currently reviewable product decisions. A future reviewed change must amend the spine first, then reconcile detailed implementation clauses.

## 3. Visual system

### 3.1 Color (tokens exist; consumption rules are the constitution)

- **Standard light**: warm paper `background #FAF8F3`, `surface #FDFCF9`, ink-teal `primary #2E4A56`. **Standard dark**: `#151A1C` / `#1C2225` / `primary #A9C6D2`. Never pure `#000`/`#FFF` in chrome. Dynamic color: Standard + API 31+ + persisted opt-in only.
- **E-ink**: fixed opaque monochrome ramp `ink #000000, n90 #1A1A1A, n70 #4D4D4D, n50 #808080, n30 #B3B3B3, paper #FFFFFF`. Secondary text = `n70`; disabled = `n50` **plus** border/fill redundancy. Banned: alpha-dependent distinction, gradients, translucency, blur, shadows, scrims, background images, tonal elevation.
- **Semantic slots only.** Features reference `colorScheme` roles (primary/surface/outline/error…), never hex. New need → add token to `core:ui` first (§16.1 versioning).
- **Meaning redundancy.** Color is never the sole carrier: error = text + icon/border; selected = state description + indicator; unread update = badge glyph + count text.
- **Source identity color** (signed, validated by `core:media`, §15): the host may tone-map the validated source color only inside cover fallback and a compact source mark/chip. Source identity must consume the minimum space needed to disambiguate an operation. **Forbidden:** full-width identity bands, Library rows/cards, page/app-bar/navigation backgrounds, leading-slot branding, dialogs/sheets, Reader content, and mixed-source background washes. E-ink always renders the mark monochrome.

**E-ink window acceptance:** applying the E-ink profile includes window chrome. Status bar background/icons, navigation bar background/icons, edge/cutout surfaces and app content must render from the opaque monochrome ramp. Every direct AVD PNG is pixel-scanned; any pixel with `R != G || G != B` is stop-ship unless it belongs to an explicitly documented camera/emulator diagnostic overlay, which must be removed before reviewer evidence. Compose-only grayscale does not pass this gate.

### 3.2 Typography (scale exists; role assignment is the constitution)

System CJK sans-serif is the default chrome and Reader typeface; weights Regular 400 / Medium 500 only; no italic CJK, no Light; line-height ≥ 1.5× size; no fixed-height clipping at fontScale 2.0. Reader may later expose a user-selected system/imported font through its typography settings, but no bundled serif/default change is approved without glyph coverage, license, APK-size and real-text evidence.

| Role | Token (size/height) | Assigned use (mandatory) |
|---|---|---|
| Display | 28/42 M | Full-area StateView titles; transfer/install flow headers |
| Headline | 22/34 M | Screen-level content heading **only when app bar is absent** (never duplicates app-bar title) |
| Title | 18/28 M | App-bar title (`titleLarge`); section headers (`titleMedium` = Title role); book title on detail |
| Body | 16/24 R | Primary reading text, row primary line (`titleMedium` 16/24 M for row titles), form text |
| Label | 14/22 M | Buttons, chips, tabs, app-bar subtitle/count line, SettingsRow title |
| Caption | 12/18 R | Row secondary/status lines, provenance, timestamps, badge counts |

Rules: one role per information class across all screens; feature code references `MaterialTheme.typography` slots, never raw `sp` (current ReaderSurface 18/20sp raw styles are P1 defect UI-P1-9); row title/body/status mapping is fixed in §5.2. Long titles may select a smaller existing typography role or reflow within a documented range, but must remain readable and may not be ellipsized merely to preserve an arbitrary cover/card width.

### 3.3 Spacing, grid, alignment

- Base unit 4dp. Scale: `Xs 4 / Sm 8 / Md 16 / Lg 24 / Xl 32 / Xxl 48` (`TsuyomiSpacing`). **Any off-scale value found in features (current 12/14/18/20dp ad-hoc gaps) is a defect (UI-P1-10), not a variant.** New need → extend the scale in `core:ui` first.
- Screen gutters default to 16dp, but dense task containers may use 8dp internal spacing when 48dp hit targets are preserved. Section separation is evidence-driven: 8–24dp according to hierarchy; 24dp is not a mandatory tax between every block.
- Alignment: left/start-aligned text everywhere except centered StateView blocks and dialog titles; numeric/count columns right-aligned; app-bar title start-aligned.
- Touch targets ≥48×48dp; the visible control need not occupy the full hit box. Settings/form content max width 560dp, centered in wide windows. Label, current value and continuous/discrete adjustment should share one row when this remains legible at fontScale 2.0.
- Density reduction under E-ink happens by removing decoration, never by shrinking controls below 48dp. E-ink list/card spacing may increase where title legibility requires it; “dense” never means clipped content.

### 3.4 Shape, borders, elevation

- Standard: radii 8/12/16dp; elevation restrained, tonal surfaces allowed.
- E-ink: explicit angular geometry (0–4dp max where separation needs it); separation via 1–1.5dp opaque borders (`outline`/`n50`) and fill inversion; no shadows/elevation.
- Dividers: Standard 1dp `outlineVariant`; E-ink 1.5dp `outline`. The app bar carries a bottom divider in both profiles.

### 3.5 Iconography

- 24dp `ImageVector` glyphs, original or verified-license Material Symbols only (`TsuyomiIcons` pattern). No legacy project artwork traced or copied; no third-party marks (§15).
- Icon-only controls always have localized `contentDescription`; controls with visible text get no redundant announcement.
- Status glyphs must pair with text at TalkBack level (stateDescription).

### 3.6 Visible-copy budget

Normal content states permit only: domain identity/content; concise section nouns; action labels where icon-only meaning is not standard; progress/count/time; selected filter values; actionable errors/offline/permission scope. The following are forbidden as persistent page copy: instructions for operating visible controls; explanations of local-first, exact identity, deduplication or source concurrency; statements that a button is editable/read-only when control state and accessibility semantics already express it; repeated source/page context; implementation vocabulary. A board containing such copy fails before aesthetic review. A compact help icon may deep-link to Help when an unfamiliar domain concept genuinely needs explanation.

---

## 4. Screen anatomy and app-bar contract

### 4.1 Universal anatomy

Every screen composes exactly: **AppBar** → **Content** → **PersistentFooter (optional)**. Fixed chrome; nothing collapses or reacts to scroll. One `StateView` owns the whole content area when the page is Loading/Empty/Error (§9.1); overlay states (offline/refreshing/unresolved) use `InfoBanner` pinned under the AppBar and never replace content.

**Screen contract**: `@Composable fun XScreen(state: XUiState, onAction: (XUiAction) -> Unit, modifier: Modifier = Modifier)` — immutable state in, one typed action sink out, no NavController/DataStore/`Build.MODEL` access, no `eInk` boolean.

### 4.2 App-bar contract (extends `TsuyomiTopBar`)

| Slot | Rule |
|---|---|
| Up affordance | Present iff the screen has a semantic parent (non-root). Executes Up per §13, never Back. 48dp, labelled. When absent, no placeholder width is reserved: the title and any permitted leading content align to the normal start gutter. Source branding never occupies this slot. |
| Title | Stable screen noun (Title role, ≤ 2 lines ellipsized). `paneTitle` + heading semantics. **Content below never repeats the same heading** (current Reader shell duplication is defect UI-P1-1). |
| Subtitle/count slot | One caption line under/beside title carrying one structured live context: current node/view name and count, collection path, or filter scope. Label and count use spacing/typographic separation (for example `全部书籍  128`), never a free-form `·` metadata chain. Empty when no context; max 1 line, ellipsized. |
| Actions | Budget by window class: double-compact ≤ 1; compact/regular defaults to ≤ 2 icon actions + overflow menu (§7). Every icon action labelled. Primary task must not live only in overflow. The regular-width Library root is the measured exception: up to four frequent icon actions may remain visible when every 48dp target and the title floor fit; narrower widths fold the lowest-priority actions. |
| Selection mode | Normal app bar is replaced by **SelectionAppBar**: close (clears selection), count title (`已选 3`), select-all, bulk action(s), overflow. Never stacked over the normal bar. |
| Root bars | Library root bar: title `书架` + visible `同步并检查更新`, `聚合搜索` and layout actions when the measured budget fits; sort stays in overflow. Selection is entered from an item's semantic long-press action, never a separate root-bar button. System views, manual/smart collections and mirror roots render as first-class nodes in the Library content flow (§5.5), not behind a selector-only navigation model. Browse root: title `浏览` + visible `聚合搜索` and import-source action. More root: title `更多`. The shared search route is pushed on the invoking root stack. |

Fixed, non-collapsing, bottom divider, safe-drawing top inset; identical slot order across breakpoints so chrome moves without remounting content. **Collapsible/scrolling app bars are rejected permanently** (E-ink rule + research: Hikari's transparent-over-backdrop collapsing app bar and 700 ms collapse are explicitly rejected, §22 R-7/R-8).

**Action placement decision rule (binding; research-grounded §22 A-1/A-16):** exactly one primary semantic action per screen, placed by **frequency × scope × reversibility × available space**: frequent screen-level → app bar; one dominant creation or singular bulk action → *at most one* FAB (never competing FABs, never FAB + primary button for the same task; hidden when invalid or when a bottom action bar occupies the area); frequent reversible per-item → row trailing slot (§5.2), optionally mirrored by a Standard-only swipe shortcut with an equivalent visible control and full custom-a11y action (swipe never sole path, never destructive/rare); rare/contextual → overflow; irreversible/multi-step → dialog; multi-select → replacement selection bar. Default placement is the app-bar action unless the atlas comparison (variant A) plus manual review favors FAB for creation contexts.

### 4.3 PersistentFooter

Reserved for: `PaginationBar` (E-ink long lists), persistent selection/bulk action bar when content scrolls, reader position status (E-ink). Never used for ads, tips, or duplicate primary actions. A bottom action bar is permitted only for 2–3 peer actions whose labels remain readable at fontScale 2.0 (§22 A-HK-4); otherwise top app-bar or overflow.

---

## 5. Information design — rows, cards, lists, grids

### 5.1 Domain integration (mandatory; no parallel UI models)

Feature lists render **`BookListItem` from `shared:library-domain`** through the shared `core:ui` renderer. `BookListItem` may carry identity, title/authors, `CoverRef?`, source label/availability, progress summary, rating, local/source tag summaries, update state, presence origins and action capabilities because different contexts need different subsets. Renderers apply the context contract below: data availability never licenses information dumping. No Room entity, painter, URL or list/grid-specific field crosses into features. Cover handling follows §2.3.

### 5.2 Canonical Library row information order — confirmed direction

Library book rows answer four questions only: identity, reading position, one immediate state and the next action. They use M3 `ListItem` anatomy through a semantic `BookListItemRow` wrapper. Dense-cover rows normally use dividers; compact-text may use a tightly packed outlined/tonal M3-backed container when Atlas evidence proves that the boundary improves scanning.

1. **Cover** — dense-cover list: 48×64dp (3:4), fixed at all font scales; compact-text list omits it by explicit layout choice.
2. **Identity slot** — title `titleMedium`, max 2 lines; author/series occupies its own lower-contrast byline and is never concatenated with progress/state.
3. **Progress slot** — current chapter or percentage in its own icon/value line or fixed column.
4. **State slot** — exactly one cover badge or fixed trailing glyph: update count, completed, downloaded, dormant/frozen or working. Full explanations appear once in the page/session surface or on activation; the row does not repeat status sentences.
5. **Optional rating slot** — fixed trailing alignment when the active view/sort makes rating useful; an absent rating does not force the other slots to jitter. Library never renders local/source tag chips or tag text.
6. **Trailing action** — at most one frequent reversible action; otherwise activation opens canonical Detail and low-frequency actions use an aligned overflow slot.

Source provenance is omitted from Library. Mixed Browse/Search results may add one compact source mark when provenance is necessary. Tags remain query/filter inputs and may render only in Browse, Search or a bounded secondary Detail module.

### 5.3 Grid card anatomy — confirmed direction

GRID is the confirmed default Standard and E-ink Library surface. Regular portrait phones use exactly three columns; double-compact may fall to two only when three columns cannot preserve the 48dp target and readable title floor. Every grid item—book, system node, manual/smart collection, child collection or mirror node—uses identical outer geometry: one fixed 3:4 card field with the same in-card title/state placement. Standard uses a bounded bottom gradient overlay; E-ink uses an opaque aligned bottom band inside the same 3:4 bounds. Text never changes outer card height; row and column edges align across the complete page.

Absent/failed covers and non-book nodes still own the entire 3:4 field. Book fallback renders a neutral host-generated title field; collection/system/mirror nodes use host-generated preview or icon content. All variants retain the same title/state slots, opening semantics and selection treatment; no node kind may introduce a shorter, wider or separately aligned grid card.

### 5.4 Layout preference — reconciled binding

All book-bearing Library contexts, Updates, Search and Remote Library expose the same three layouts and preserve identity/order/selection when switching:

- **DENSE_COVER_LIST** — cover + separated identity/byline/progress/state slots.
- **COMPACT_TEXT_LIST** — coverless 48–56dp row for very large libraries; 0–2dp inter-row visual gap, fixed action/rating columns.
- **FIXED_GRID** — confirmed default only for Library contexts; regular-phone Library uses three fixed equal-height columns.

Updates, Search and Remote Library retain all three layouts but use route-specific defaults. Search defaults to dense unified list. Updates defaults to dense list; its E-ink grid column count may shrink when title/status/action readability requires it. Remote Library defaults to dense cover list; its E-ink grid likewise follows measured title/action readability. Preferences are stored per context × `DisplayProfile`, device-local and excluded from transfer. Explicit user override wins; source layout hints are ignored everywhere. Layout switching is a labelled app-bar action.

Every book-bearing Library context supports title, recent-reading and local-rating sorts in both ascending and descending order. Manual collections and the root additionally expose custom/manual order; smart collections and website mirrors omit custom order because they cannot accept manual reordering. Books without reading history or rating remain after books with comparable values in either direction. Sort mode and direction are persisted per Library context and included in E-ink page identity.

### 5.5 Library nodes and collection organization (reconciled user decision; binding)

The Library content flow contains `BookNode | SystemNode | LocalCollectionNode | WebsiteMirrorNode`. These node kinds are peers in one route and one layout model. Default rule sorting places system/collection/mirror nodes before books; another rule sort may fully intermix them. A distinct manual-order mode lets Standard drag nodes and lets E-ink move the same nodes through explicit buttons.

System nodes `继续阅读 / 最近阅读 / 稍后再读 / 休眠来源 / 追更` are created by default, may be hidden, and can be rebuilt from the shared create flow. Their definitions cannot be renamed or rewritten. Only `稍后再读` accepts explicit manual membership; every other system membership remains an automatic host query. Every system node, manual/smart collection and child collection opens a dedicated page; the Library root never swaps to a selected node in place. Child collections and books remain peers inside each collection page. Manual nesting is capped at two levels (root collection → child collection), with each level represented by a distinct Back/Up step.

Stationary long-press on an unselected Library book or folder enters selection mode and selects that item exactly when the platform `longPressTimeoutMillis` threshold is reached; release is not part of activation and no first-level item menu is interposed. In an active selection, long-pressing an unselected item only adds it to the selection. Long-pressing an already-selected draggable book instead arms the complete selected-book set as one drag payload: movement after the threshold may drop that batch on a collection or the applicable remove target, while release without movement performs no drop or mutation. Standard shows the theme Material indication/ripple from press-down through the threshold. E-ink/reduced-motion uses the instant opaque tonal press required by §11.2.

Before the long-press threshold, movement beyond `touchSlop` that is dominant on the owning container's scroll axis permanently yields that pointer sequence to scrolling; the drag recognizer must not reactivate when the timeout later expires. Outside an existing selection, a stationary long-press on an unselected item still selects it exactly at the threshold, but movement beyond `touchSlop` by that same held pointer then upgrades the item into a single-item drag without requiring release and a second long-press. In an existing selection, an unselected item only joins the selection, while an already-selected draggable item arms the selected batch at the threshold. Once either drag is armed, the drag source owns subsequent movement so valid reorder, collection drop and remove flows remain available.

In Standard manual-order mode, dropping one book on another opens a name/effect confirmation; confirming atomically creates one manual collection containing both books. E-ink exposes a selection-and-button equivalent. Shortcut-shelf drag/reorder always has explicit E-ink move/remove/replace controls.

Dropping one book on the shortcut-shelf root creates or repositions one direct shortcut. Dropping multiple selected books on the shortcut-shelf root instead opens the existing named-collection confirmation and, on confirmation, inserts exactly one manual collection at that position containing the complete selected set; it never fans the batch out into adjacent individual shortcuts. Dropping the same batch on an existing collection adds all selected books to that collection without creating another folder.

Website mirrors use the same dedicated-page folder model as local collections: folders and books are peers, grid/list/compact layouts use the shared renderers, and opening a mirror folder pushes its own page rather than expanding a tree row inline. The `本地整理` node appears only after explicit creation, opens as a separate local page, never masquerades as website structure and never causes website writes.

### 5.6 List and grid geometry

| Context | Standard | E-ink |
|---|---|---|
| Dense-cover list | Scroll; M3 `ListItem`; separated slots; divider | Explicit pages; fixed geometry; 1.5dp divider |
| Compact-text list | Scroll; 48–56dp M3-backed outlined/tonal row; 0–2dp visual gap | Explicit pages; identical row height; bold/outline selection + check |
| Fixed grid | Default; exactly 3 columns on regular portrait phones, 2 only for double-compact readability/target constraints; 8dp gaps; fixed card height | Default static paginated grid; same 3-column regular-phone rule; fixed equal-height monochrome cards; current page only |
| E-ink page sizes | — | Re-measure after density/fixed geometry is rendered; page size is evidence-driven, not a hardcoded legacy `6/8` assumption |

Pagination rule: **unbounded or long lists paginate on E-ink**; bounded short content may scroll in both profiles. Every paginated surface shows `第 x 页，共 y 页`, disabled endpoint controls and persistent inline loading/error. Page state keys include the full result-domain identity: Library context/node ID + query + filter + sort + layout. Domain changes clamp/reset page and announce the new page.

### 5.7 Bounded content (uniform caps, surfaced honestly)

Existing bounds become visible contract: local query 100 chars; names 256; tags/book 512 (tag input shows remaining-count when approaching); rule values 1024; rule conditions 64; transfer review lists cap 50 rows with `展开全部 n 条` expander (§8). E-ink page size is measured from fixed geometry and readable targets rather than a legacy hardcoded count. Caps live in domain/shared modules, are displayed in UI when reachable, and never silently truncate user input.

---

## 6. Controls and inputs

### 6.1 Wrapping rule (mandatory)

**Only `core:ui` wraps Material.** Feature/app code consumes semantic components exclusively. In Standard, those wrappers **must delegate to the real M3 implementation and defaults**: `Button` family, `IconButton`, `FloatingActionButton`/`ExtendedFloatingActionButton`, app bars, `ModalBottomSheet`, `ListItem`, `Card`, selection controls, menus and text fields. A low-level `Surface`/`Box + clickable` implementation is permitted only for a domain control with no M3 equivalent and must carry specific Atlas evidence. E-ink may replace presentation internals while preserving the same public API, semantics, state and operation order. Current direct Material usage in features and current hand-built Atlas button/FAB/sheet approximations are defects, not comparison evidence.

### 6.2 Control selection rules

| Need | Component | Never |
|---|---|---|
| Primary action (≤1 per surface) | `TsuyomiButton(PRIMARY)` | two primaries on one surface |
| Secondary/destructive-paired | `TsuyomiButton(SECONDARY)` | text-style for destructive confirm |
| Tertiary/inline | `TsuyomiButton(TEXT)` | PRIMARY inside lists/cards |
| Icon action | `TsuyomiIconButton` (mandatory description) | unlabelled icon button |
| Binary persisted setting | `SettingsSwitchRow` (whole-row toggle; checked = persisted value; disabled shows reason) | standalone Material Switch |
| Single choice ≤ 5 short options | `SegmentedSelector` (radio semantics, collection info) | dropdown for 2 options |
| Single choice > 5 or long labels | Dropdown (§7.2) | segmented with wrapping labels |
| Multi-select bounded set (tags, membership) | Checkbox rows in sheet (§7.3) | toggle chips as sole multi-select |
| Free text | `TsuyomiTextField`: label, supporting text, error text, cap counter, saveable draft | raw OutlinedTextField |
| Rating | `RatingInput`: 1–5, 48dp steps, clearable, announces value | star icons without text value |
| Reorder | Explicit 48dp `上移`/`下移` actions | drag-only reorder |
| Refresh long list | Visible labelled refresh action | pull-to-refresh as sole path (banned on E-ink) |

### 6.3 Input states

Every input renders: enabled / disabled+reason (visible text, not color-only) / error (message + `semantics.error`) / working (scoped disable, draft preserved). **Draft ownership rule**: any draft whose loss would affect a mutation (tag edits, rule editor conditions, rename/create forms, transfer review choices) is held by the route owner in the back-stack entry's `SavedStateHandle` — not composable `rememberSaveable` — so process recreation restores the exact in-flight edit; purely transient UI drafts (scroll-linked search suggestion text) may use `rememberSaveable`. Process recreation never silently loses typed content.

### 6.3a Aggregated search (binding; advanced filters deferred)

One root-neutral query draft and an implicit route-owned source scope drive the search route. Normal Library/Browse entry searches every active search-capable source; a source-card entry may bind the route to that one active source. No source selector or leading Search control is rendered. Editing, restoring or changing the draft performs zero Room or network work. One explicit trailing Search action creates one immutable session and starts local FTS plus the effective source scope together, under the host coordinator's global ≤3 and per-source =1 concurrency limits. Local results may appear first because they finish first; they are not a separate phase or second confirmation.

The current Atlas and Phase 4A expose only the bounded query, trailing submit action, layout/sort and one aggregate progress indicator. D33 public/local/source-specific filter descriptors, advanced filter UI and `search-v2` capability negotiation are deferred and impose no Phase 4A implementation dependency.

Successful items enter one incremental result flow. Exact `BookIdentity(sourceId, remoteBookId)` merges; same-title different identities remain separate. Internal lane state may isolate cancellation and failure, but normal per-source status strips, source lanes and scheduling explanations are not persistent UI. A failed source never removes returned items; a compact error affordance may open details/retry on demand.

### 6.4 Button hierarchy and placement (research-grounded; §22 A-HK-3)

- Hierarchy: **one** primary `FilledButton`-style action per surface; **one** secondary `OutlinedButton`-style action; `TextButton`-style for low-emphasis/cancel; destructive actions get an explicit destructive style and labelled confirm (never bare `确定`).
- Placement follows the §4.2 decision rule. Detail-page peer actions (e.g., 2–3 equal-weight operations) may use the §4.3 bottom action bar only while labels remain readable at fontScale 2.0; otherwise they move to app-bar actions + overflow. Competing button patterns without this selection rule — as observed in Hikari's mixed detail actions — are a defect class, not a variant (§22 R-HK-5).

---

## 7. Menu vs dropdown vs sheet vs dialog (one contract; research §22 A-11)

One modal layer at a time; Back dismisses topmost; focus enters on open and restores to trigger on close; E-ink renders every modal as fully opaque paper surface (full-window for dialog/sheet-class), no scrim/dim/alpha/shadow; Standard may use opaque card + scrim. Destructive confirmations never dismiss on outside tap; default focus lands on the safe action. Every sheet/dialog has bounded content, explicit focus order, safe-area handling, and deterministic cancel/destructive order (§22 A-HK-6).

### 7.1 Overflow menu (anchor menu)
Secondary, non-destructive, ≤ 6 items, each duplicating or extending visible affordances. **Never the sole path to a core task** (principle 1). Item = label (+optional leading glyph); destructive items text-labelled with confirm step downstream. E-ink: opaque paper, instant open.

### 7.2 Dropdown (value selection)
One value from a bounded set where the current value stays visible: sort order, update-check period, per-screen options. Trigger shows current value (Label role, chevron + text, never icon-only). > 5 options or long labels only; ≤ 5 short → `SegmentedSelector`. Radio/check items for mutually exclusive display modes (§22 A-3).

### 7.3 Sheet (structured selection/picking)
Entity picking needing context/counts/sections: collection membership, remote-copy/move target, and combined sort/filter. Standard: modal bottom sheet, max 90% height, drag handle + explicit close, safe-area clamped, no nested sheets. E-ink: full-window opaque variant with identical content/order. Sheets scroll internally; destructive picks confirm via dialog. Reader settings is the sole exception to the explicit-close rule and follows §14.1.

### 7.4 Dialog (`TsuyomiDialog`)
Confirmations (destructive or remote-effect), short forms (rename, create), must-acknowledge results. Title = action noun; body states exact effects (what remains, what does not happen remotely); confirm verb-specific (`移出书架`, `删除子树`), never bare `确定`. Informational: Back/explicit close allowed. Destructive: explicit buttons only.

### 7.5 Decision matrix

| Situation | Use |
|---|---|
| Confirm irreversible/remote-effect action | Dialog |
| Rename/create short form | Dialog |
| Pick from sectioned entities with counts | Sheet |
| Choose one bounded value, value stays visible | Dropdown (or Segmented ≤ 5) |
| Secondary actions that don't fit app bar | Overflow menu |
| Rich result requiring acknowledgement | Dialog; otherwise persistent inline banner |

Sheet = browse/select/adjust; dialog = confirm/edit/irreversible; menu = short contextual list. The same operation is never offered in all three surfaces (§22 A-11).

---

## 8. Expansion / collapse

For progressive disclosure *within* a screen: approval capability diffs, transfer warnings/conflicts > 50, mirror folder trees, smart-rule group nesting, help topics.

Contract: expander row = chevron **+ text label** (`展开全部 n 条` / `收起`), `Role.Button`, `expanded` state in semantics; never icon-only, never chevron-free text. State: session-ephemeral by default (`rememberSaveable`); domain-meaningful expansion (mirror tree path) held by route owner. Standard: 200–250ms ease-out size/fade (§11.1); E-ink/reduced-motion: instant swap, no intermediate frames. Capped lists always end with the expander row (never silent truncation). Nested expansion max 2 levels; deeper structure navigates (mirror node opens node page). Expansion never hides the primary task or the current state.

---

## 9. State system

### 9.1 Page states (per screen, exactly one primary)

`Loading → Content | Empty | Error`; `Offline`/`Refreshing` are overlays.

- **Loading**: reserved stable geometry + `正在加载…` text; no spinner in any profile; no skeleton animation.
- **Empty**: reason sentence + at most one primary action with a real handler (library empty → `浏览并添加书籍`; collection management secondary). Never "nothing here".
- **Error**: readable cause + explicit `重试`; no stack traces, no secrets/HTML.
- **Offline**: persistent top `InfoBanner`; cached content retained; without cache → offline empty state.

Motion or a spinner is never the sole state signal (§22 A-10).

### 9.2 Mutation states (every command, keyed per target)

Shared `MutationUiState` + `MutationResultBanner` in `core:ui` (PHASE_4 mandated). `idle → working → success | error | cancelled | unresolved`.

- Working: only the scoped command disables; unrelated rows stay interactive; drafts preserved.
- Success: persistent inline banner, normalized/read-back value echoed (tags echoed from Room), polite live region; never Snackbar/toast/color/animation alone.
- Error: safe retry; message names affected book/collection/source without credentials/raw pages.
- Duplicate dispatch for same target rejected.
- Non-durable working state after recreation resolves to re-query/retry, never fake success.

### 9.3 Unresolved (remote operations, 4B)

`PENDING_CONFIRMATION → PENDING_TOKEN → IN_FLIGHT → CONFIRMED | CANCELLED | UNRESOLVED`. This state machine is exclusive to accepted website ADD/REMOVE/MOVE writes. Only operation-correct typed echoed results (`applied`/`already-present` for ADD, `applied`/`already-absent` for REMOVE, `applied`/`already-at-target` for MOVE) confirm. CANCELLED/UNRESOLVED persist on canonical detail, or on an explicit projection of that same accepted attempt, with fresh-state retry; **no auto-retry**; while any website write for a book is pending/in-flight/unresolved, later website mutations for that book block with explanation (D17). Mirror calibration, update probes, local `标记已处理`, import, and other local/read operations use their own terminal unions and never enter remote UNRESOLVED.

### 9.4 Destructive confirmations (binding copy contracts)

- `移出书架`: removes only `LOCAL_PIN` and direct `MANUAL_COLLECTION` memberships; **retains `READ_LATER`, `WEBSITE_MIRROR`, rating, local tags, semantic progress, known metadata, and history**; re-adding restores the retained context. Clearing Read Later is a separate explicit local command. **No website operation occurs**.
- Collection folder delete: offers **reparent children to current parent** OR **delete subtree**; books never deleted either way; copy states counts and that book data is untouched.
- Mirror disable: freezes last snapshot; frozen mirror stays visible (labelled `已冻结`) in All and its section; excluded from update checks; zero remote writes. Separate explicit `清除本地镜像快照` erases locally, never remotely.
- History per-item remove / clear-all: named scope, cancel mutates nothing.
- Transfer import: confirm only after review; recovery gate blocks normal navigation until resolved.

### 9.5 Capability visibility (OPTION_APPLICABILITY is constitution)

Order: implemented? → capable? → effective now? → must explain? → feedback complete? Visible-disabled-with-reason only when a retained value needs understanding or the user can unblock; otherwise hidden. No placeholders, no "coming soon". Judgments derive from effective capability via `core:display`/domain, never from `Build.MODEL` or persisted enums.

---

## 10. Selection and bulk actions

- Entry: long-press an unselected book or folder; the platform semantic `LongClick` action is also exposed to TalkBack, keyboard and switch-access users. A separate app-bar `选择`/`多选` entry is prohibited because it adds a mode-entry step to a frequent task.
- The long-press threshold immediately enters selection and selects an unselected item; it never opens an intermediate item-action menu and never waits for release. In active selection, an unselected item is only added. An already-selected draggable book arms the current selected-book set for batch drag; only movement beyond touch slop may commit a drop to a collection or remove target. Press feedback follows §11.
- SelectionAppBar (§4.2): close-left, count title, one toggle-all action, and the highest-frequency scope-valid bulk actions directly visible. `全选` uses the select-all glyph; after every selectable item is selected it changes to the deselect glyph and `清除所有选择`. Activating it again clears the set while keeping selection mode available for a new selection. Bulk actions are hidden while the count is zero.
- For Library books the direct actions include create collection, move/add to collection and remove from the applicable shelf; for folders they include move and delete. Low-frequency or single-item actions belong to the item's detail/secondary page or labelled overflow, not another post-long-press popup.
- **Back clears selection and exits selection mode before any route/root traversal.**
- E-ink: after a domain-changing bulk action, page clamps and new page announces.

---

## 11. Motion

### 11.1 Standard profile — restrained functional set (§18 confirmed convergence; research-grounded §22)

All Standard motion ships **only as centralized `TsuyomiMotion` tokens** — never feature-local animation code or specs. Durations below are the constitutional token set; observed reference values that informed them are cited in §22 and are starting evidence, not imported constants.

| Class | Token (Standard) | Reduced/E-ink | Cancellation/end rule |
|---|---|---|---|
| Immediate state feedback (toggle, select, read/unread) | 0–50ms | 0ms | Commit state first; visual feedback never delays the action. |
| Fade status/content swap | 100–150ms in / 75–100ms out | 0–75ms opacity or instant | Retarget on new state; never queue stale states. |
| Expand/collapse (§8) | 200–250ms, top-anchored ease-out | Instant size change | Back/new tap reverses in-flight transition; scroll anchor preserved. |
| Spatial navigation (destination change only) | 200–250ms ease-out | Instant | Cancel on Back/reselect; never blocks interaction. |
| Sheet/backdrop | backdrop 100ms; sheet 200–250ms | Instant | Back/outside-tap cancels and restores prior state; no overdrag. |
| FAB / selection-bar visibility | 150–250ms | Instant | Hide immediately when invalid; snap to final state on interruption. |
| Swipe settle (Standard-only shortcut) | tween settle, haptic threshold | Instant snap; never color-motion alone | New item/state resets to center; cancellation never loses the committed action. |
| Reader paging | Optional animated paging (explicit user preference); default immediate | Always immediate | New paging command supersedes previous animation; clamp to document bounds. |

Banned in all profiles: springs/elastic/bounce easing, parallax, shimmer/skeleton animation, any duration > 250ms, always-on or decorative motion, bespoke per-screen transitions, decorative gradients behind dense text, motion as sole state signal, source projects' exact timings or components imported. Standard press feedback uses the theme Material indication/ripple from press-down; long-press actions commit at the platform timeout, never on release. The **fixed top bar and the ≤ 250ms ceiling** remain constraints; raising either requires a constitutional revision approved at manual review. Expressive-motion candidates (shared-element book→detail, animated reorder) stay **out of this RC**; they may be prototyped as atlas variant material and adopted only through the same revision path.

### 11.2 E-ink / reduced-motion (INSTANT) — binding

Trigger: `effectiveProfile == EINK` OR system reduced-motion (`MotionDurationScale.scaleFactor == 0f`). All state commits are immediate single-frame replacements: no ripples (instant opaque tonal press), no AnimatedContent/Visibility/animateContentSize, no overscroll, no dialog dim, no indicator animation, no spinner, no crossfade, no size/visibility animation. Navigation replaces content immediately and restores focus/scroll/page deterministically. Large updates assemble off-screen in immutable state and commit once; components keep stable keys/dimensions. E-ink does not merely set durations near-zero while retaining expensive gradient/SVG composition — decorative layers are removed, not sped up (§22 A-HK-8).

---

## 12. Responsive transformations

Runtime window only, never device model (existing `resolveNavigationLayout` is constitution):

- `width < 600 && height ≥ 480` → bottom `NavigationBar`.
- `width ≥ 600` → `NavigationRail`.
- `height < 480 && width ≥ 480` (landscape phone) → rail.
- `width < 480 && height < 480` (double-compact) → compact bottom bar, labels retained, app-bar actions ≤ 1, content scrolls.
- Crossing a breakpoint at runtime preserves route, per-root stacks, scroll/page, selection, and focus — same tasks, same mental model; layout changes are pane arrangement only (rendering may change, options/order never).
- Wide (≥ 600dp): list contexts may add a detail pane **only** where the task benefits (collection browsing, settings); book detail remains full-screen single surface (no forced dual-pane reading flow). Settings content stays 560dp max centered.
- fontScale 2.0: every screen scrolls; rows grow vertically; chips/filter rows wrap or become a labelled picker; cover thumbs stay fixed; **no clipped text, no unreachable action** (current non-scroll detail/filter/browse columns are P1 defects).
- Acceptance: dual portrait baselines mandatory — `Tsuyomi_API29` 1080×2400@420 Standard and `Tsuyomi_EInk_API29` 1264×1680@240 E-ink; landscape/split supplement, never replace.

### 12.1 System bars, cutouts and immersive scope

- Every non-Reader route keeps status and navigation bars visible. App bars and bottom navigation consume only the safe content area; no title, action, selector or row may sit under a camera cutout.
- Edge-to-edge implementation consumes `statusBars`, `navigationBars`, `displayCutout`, `safeDrawing` and `safeGestures` from the actual window. Insets are not duplicated between the window and M3 components.
- Reader may expose an explicit `全屏沉浸` preference. Only the body-reading state may hide system bars; opening Reader chrome, settings, directory/search or an error/verification surface restores them immediately.
- Even in Reader immersive mode, body text remains inside cutout-safe horizontal/top bounds. Full-screen means hiding system chrome, not drawing readable text through the camera hole.
- Atlas declarative screenshots render deterministic simulated system bars and a centered cutout so visible viewport loss is reviewable. AVD/device evidence remains authoritative for OEM bar dimensions and cutout behavior.

---

## 13. Navigation, Back, Up, focus

Roots: `书架` / `浏览` / `更多` — exactly three, independently restorable stacks (`saveState`/`restoreState`), labels ≤ 4 hanzi.

| Input | Contract |
|---|---|
| System Back | Close modal → exit selection/edit → dismiss inline search/chapter drawer/settings sheet → pop route → caller context. Library system pages, collections and mirror folders each pop to their immediate parent page before the top-level node returns to Library. At root: Android task policy; never synthesize old nested routes. |
| App-bar Up | Semantic parent: Library system page → Library; child collection/mirror folder → parent collection/mirror page; top-level collection/mirror/history/updates → Library; reader/directory → canonical detail; detail → recorded caller list/root; rule editor → collection; report → data. Invalid caller after recreation → fall back only to origin root's list, never another root. |
| Root item re-select | Pops **that root only** to its root destination (D2). Other stacks untouched. |
| Cross-root precise action | `在来源中打开本书` (D9): pushes same canonical detail onto Browse stack, Library preserved; disabled+reason when source dormant. `浏览内容源` opens the source root/search. Generic `saveState` restoration never fulfils a book-specific request. |

Focus: DPAD/keyboard order = visual order; modal open moves focus in, close restores to trigger; route changes never strand focus; first focus target on a new screen is the app-bar title region or primary content, deterministic per archetype; 48dp targets everywhere. Process recreation restores route + stable-arg context; ephemeral UI (dialogs, transient drafts without save) drops safely.

Canonical detail carries bounded `BookCallerContext` (origin root, caller route kind, stable IDs, bounded query/sort/filter, position/page) in its back-stack entry — never credentials/URLs/extension state. Two opens of one book from different callers remain distinct entries rendering the same contract.

---

## 14. Reader exceptions (sanctioned exception zone)

The Reader is the one screen family allowed to deviate, and only through the following bounded contract:

1. **Full-bleed content:** reading body auto-hides chrome; a center tap, keyboard action or Back reveals chrome without opening a modal. The invisible reading canvas carries no ripple/press indication but retains explicit click and accessibility actions. Standard chrome uses one 140ms fade plus a small edge-directed translation; reduced-motion and E-ink commit in one frame. Top and bottom chrome are overlays: showing or hiding them never changes the body constraints, page measure or current locator. A separately optional, compact reading-information strip overlays the bottom edge with chapter title, page/percentage text and the existing thin determinate progress line; E-ink retains an always-readable chapter/page status.
2. **Visible chrome path:** top bar exposes Back, chapter title, bookmark and search. The bottom bar directly exposes previous chapter, contents, `阅读设置` and next chapter around the current-chapter seek control. Contents and settings are not duplicated in the top bar. Tap zones and hardware keys remain shortcuts, never the only path.
3. **Material progress:** passive current-chapter position uses determinate M3 `LinearProgressIndicator`; interactive current-chapter seek uses a continuous M3 `Slider`. Tapping or dragging changes the visible body preview in real time without changing the durable locator; release commits exactly once and cancel restores the opening locator. The target is WYSIWYG against the final measured layout. A paged or dual-page surface never exposes vertical scrolling; only continuous flow scrolls vertically. The persisted result remains a semantic locator mapped from the preview, never the percentage, rendered page number or pixel offset. Exact preview visuals remain subject to direct Reader device review.
4. **Stable Reader palette and typography:** Reader foreground/background do not inherit source identity color. System CJK sans is default; complete typography settings remain available through the advanced page.
5. **Unified auxiliary container:** `目录 / 书签 / 搜索` share one Reader-owned three-tab auxiliary sheet in Standard. It opens from the bottom, initializes to the invoking tab and keeps the current chapter identifiable. Future annotations must join this same contract rather than creating a parallel surface; no inert annotation tab is shown before the capability exists. Closing restores locator and focus.
6. **Content normalization:** prose, headings, images, quotes, dividers, lists, preformatted text, tables, attachments and reply-stream posts render through one host-owned `ReaderDocument` block pipeline. A post owns a stable post ID, floor/author/time metadata and nested blocks; reply references point to stable posts. Inline emphasis/strong/strike/code/link/ruby are typed host spans. Sources never provide Composables or raw executable HTML/WebView content.

### 14.1 Reader chrome and settings contract

- Center tap toggles chrome only. The visible `阅读设置` action opens the settings container.
- **Standard:** one real M3 `ModalBottomSheet` with partial and full-height anchors. It opens partially with the quick controls. Upward drag first expands the same sheet, then reveals the remaining controls downward with the restrained expand token; replacing the entire subtree in one frame is prohibited. A full-height downward drag bypasses the partial anchor and closes the whole settings surface back to Reader chrome. From partial, downward drag, scrim or Back closes. A drag handle is always visible; there is no title/close row. **E-ink:** the same state/order in an opaque full-window surface without scrim, transparency or animation.
- Quick level fully shows four compact typography rows (font size, line spacing, margin preset and paragraph spacing), then one bottom row of exactly four high-density text buttons: `锁定竖屏 / 阅读信息 / 全屏沉浸 / 当前阅读方向`. Binary buttons express activation directly through selected color, without a redundant switch or check icon. The reading-direction button shows the current mode as its label and advances to the next available mode when pressed; unavailable dual-page mode is skipped on narrow windows. Low-frequency setup preferences such as `音量键翻页`, keep-awake and page animation remain in full settings. Every quick button preserves a ≥48dp target. A small `全部设置` action remains below the row.
- Full-height content continues below the unchanged quick controls and groups only `排版 / 页面 / 导航 / 设备`. Reader settings never expose `作用范围` (`全局 / 系列 / 本书`); that selector is not part of this surface.
- Typography minimum: font, continuous size range, weight, line/paragraph/letter spacing, first-line indent, horizontal/vertical margins, alignment and foreground/background. Navigation/device minimum: tap zones, hardware/volume keys, rotation, keep-awake, immersive behavior, position/progress presentation and effective E-ink constraints.
- Back from full-height content may collapse to the quick level; a downward drag from full height must instead close the entire sheet directly. At quick level Back closes settings → next Back hides chrome → next Back pops Reader. Closing restores focus and preserves the semantic locator.
- At fontScale 2.0 controls reflow; current values remain text-labelled. E-ink resolves before opening and locks paging with a visible reason.

### 14.2 Standard Reader Atlas decisions recorded 2026-08-23

- New books inherit the most recently used Standard reading flow; a per-book override remains authoritative when present.
- Standard bottom actions are exactly `上一章 / 目录 / 设置 / 下一章`. TTS has an architectural seam but no visible control until a real playback engine is implemented and verified.
- The Standard auxiliary sheet uses `目录 / 书签 / 搜索` tabs. Directory opens from the bottom; top chrome does not duplicate it.
- The isolated Standard Atlas may approximate scrub preview with stable host block/page slices while the production paginator is unavailable; this is review scaffolding, not approval to persist percentages/page indices or evidence that final WYSIWYG pagination is complete.
- This Atlas revision changes Standard only. The approved E-ink settings and opaque auxiliary-page contracts remain frozen and must not be restyled as part of the Standard pass.

All other Reader behavior follows the constitution: dialogs/menus/sheets come from `core:ui`; mutation feedback is shared; persisted progress is the semantic locator only; rendered page indices and scroll offsets are never durable truth. Dormant/offline local chapters remain readable and unavailable source actions show a reason.

---

## 15. Source branding and cover fallback (binding direction)

Architecture (four-layer contract, adapted from §22 A-HK-1): **(1)** signed source manifest + immutable source token; **(2)** validated static monochrome-capable mark with provenance/license metadata and a generic host fallback; **(3)** theme-role palette with a host-owned contrast budget, independent of extension-supplied color; **(4)** surface policy selecting Standard / reduced-motion / E-ink composition.

Binding rules (**no Hikari marks/trade dress/gradients may be copied** — its 3 bundled SVGs lack third-party provenance; its gradient `SourceBackdrop` has no E-ink branch and is rejected, §22 R-HK-1):

1. **Cover pipeline**: features receive `CoverRef` only; `core:media` resolves signed media reads, enforces HTTPS origin/redirect/referrer/cookie policy, validates MIME/byte/pixel/frame bounds, decodes to display size, caches in source-package/credential/display-profile partitions. Cache is optimization, never truth; excluded from transfer; retained stale cover may display when source dormant, labelled stale. (Ownership: §2.3.)
2. **Fetch policy**: covers fetch only for **foreground-visible rows** (Standard viewport) or the **current E-ink page**; no prefetch beyond one page; cancellation dedupes by complete cache key. Custom user covers: deferred (non-goal).
3. **Branding payload**: package-embedded, extension-signature-verified, hash-bound to package digest. Accept only: one opaque bounded color value, plus a restricted **static SVG** — bounded bytes/nodes/viewBox, no remote references, scripts, fonts, CSS URLs, filters, animation, raster embedding, `foreignObject`, or executable content. `core:media` sanitizes/rasterizes; invalid branding → nonfatal generic host-generated fallback (neutral token color + host-rendered title/source text). Remote or HTTP artwork is never trusted branding (§22 R-HK-3).
4. **Fallback chain** (deterministic, no broken-image icon, no retry loop): cover absent/failed/invalid → full-size host-generated 3:4 title field. The title receives up to 4 lines in compact grid or 6 in larger surfaces, optional author short name, neutral tonal color, and a monochrome high-contrast E-ink variant. Source identity is excluded from this fallback. A fixed external title/status footer preserves card geometry and semantics.
5. **Identity source**: identity derives only from the signed manifest's immutable source ID. Never from arbitrary aid/URL text prefixes, never defaulting unknown IDs to another source (Hikari's `sourceOfAid` Wenku8 default is rejected, §22 R-HK-4).
6. **E-ink**: flat surface + monochrome mark; covers decode bounded grayscale, static first frame; zero-fade commit only when a stable frame is ready.

### 15.5 Compact source identity — user-confirmed correction

The current route, page title and source-owned navigation history normally establish which source the user is viewing. Repeating that fact in a full-width colored block wastes the primary reading area and is prohibited.

- When the page title already includes the source name, show no second source label; a sanitized 18–24dp mark may accompany a secondary status line only when useful.
- In a source-specific section of canonical Detail, use one compact inline mark + source name + one short status. Never render a separate colored band.
- Mixed Browse/Search results may use one compact mark/chip only when provenance is needed to distinguish otherwise similar results. A source lane heading already carrying the source name suppresses per-row source chips.
- Library, History, Updates, collections, navigation, Reader, sheets/dialogs and generic app bars never render source branding.
- The mark carries a text alternative; color is decorative. Invalid/missing branding uses the host generic mark. E-ink is monochrome.

This correction retires Atlas decision `H-source`; the oversized band is rejected rather than left for another vote.

---

## 16. Versioning, replacement, reset, enforcement — no dual standards

### 16.1 Versions

- **UI constitution version** (`UI-CONST x.y`): this document; every token/component/contract change names the version introducing it. Recorded in `docs/design/` + an ADR per major revision.
- **UI preference schema version**: separate integer owned by `core:preferences` (§2.4) with deterministic migrator chain and the unknown-newer read-only preservation protocol. Migrations never delete domain data.

### 16.2 Whole-system revision/reset mechanism (the explicit user-required escape hatch)

- **User-visible reset (single owner)**: exactly one canonical reset action lives at `更多 > 显示 > 重置界面设置` (implemented in `core:preferences`, §2.4); `more/help` **deep-links to that action** rather than owning a second reset. It resets *only* interface preferences. Confirmation dialog lists exact scope and states domain data is untouched. Resets to constitution defaults, not to "no preference".
- **Major revision (redesign)**: requires (a) ADR naming the superseded constitution version; (b) **clean cutover in one release** — old tokens/components deleted in the same change, compatibility aliases/routing shims prohibited; (c) preference migration report; (d) full golden matrix regeneration; (e) delta Designer+Adviser review of the diff; (f) prototype/manual approval when appearance or interaction changes. **Dual standards are forbidden**: at no time may two visual systems coexist in production; the prototype module is the only sanctioned staging area and never ships.

### 16.3 Provisional status and pre-production prototype gate (mandatory governance)

1. RC2.1 direction finalizes only through manual review of the fresh direct-render prototype Atlas.
2. **Prototype atlas:** before any Phase 4 production UI implementation, revise the standalone `:prototype:ui-atlas` per `UI_ATLAS.md` RC2.1. The old RC2 renders are rejected evidence, not a baseline to patch incrementally.
3. **Approval binding:** manual approval binds RC2.1 research revisions, prototype revision, artifact hashes and route/module comments, but authorizes no production change.
4. **Implementation authorization checkpoint:** after manual approval, obtain separate explicit user authorization before changing production modules, moving fixtures/goldens or deleting the atlas.
5. **Extraction:** only after both checkpoints, extract accepted contracts into `core:ui` and feature APIs; delete the prototype and every fork. No compatibility aliases or dual standards.

### 16.4 Static enforcement (binding; Adviser P1-3 CLOSED — checks owned by `build-logic`)

All checks run in CI and release builds with **no per-file suppression, no lint baseline, no feature allowlist**. A platform-interop exception is implemented in the owning core component and changes the rule itself through Designer+Adviser review — never via a local escape hatch.

1. **Dependency-DAG verification**: the §2.2 DAG is machine-checked; any unlisted edge fails the build.
2. **Denied imports outside `core:ui`**: interactive Material dialogs/buttons/fields/switches/checkboxes/chips/menus/sheets/snackbars and all animation APIs (`AnimatedContent`, `AnimatedVisibility`, `animateContentSize`, `animate*AsState`, `rememberInfiniteTransition`, …).
3. **`NavController` forbidden outside `app` route hosts.**
4. **Forbidden in features**: `core.database`/Room entities, raw cover URLs, raw branding/SVG payloads, cookies/credentials, `Build.MODEL`/device checks, feature-local profile booleans, any `org.tsuyomi.core.media.internal.*` import (public API package only), and any `CoverRepository` reference inside a composable (route-owner-only rule, §2.3).
5. **Forbidden raw visuals in features**: raw `sp`, hex colors, `RoundedCornerShape`, dp literals outside the spacing scale, animation specs/durations (tokens only).
6. **Required patterns**: stable keyed lazy items; shared `core:ui` row/card renderers for `BookListItem`; public screen signature `state + onAction + modifier`; modal state inside route `UiState` (no `remember` modal booleans in `app`); no domain I/O launched from composables (route-owner-only §2.3 cover requests included).
7. **Deprecated constitution APIs fail the build** rather than being suppressed.
8. **Prototype isolation**: any `:prototype:ui-atlas` symbol, namespace, or dependency in the release graph fails the build.

### 16.5 Verification matrix (binding Gate criteria; Adviser P1-3 CLOSED)

| Layer | Required coverage |
|---|---|
| Policy/unit | Layout precedence for every context×profile×hint combination; modal/mutation reducers; branding valid/invalid corpus (§15.3); contrast/tone mapping; preference migration/reset/unknown-newer preservation (§2.4); motion policy resolution; source availability and frozen/active mirror/update eligibility. |
| Compose semantics | Every shared component and screen state; roles, selected/checked/expanded/error/live-region/stateDescription; modal background isolation and focus restoration; selection Back behavior; safe destructive default; keyboard/DPAD-only traversal; TalkBack; fontScale 2.0. |
| Goldens | Every route archetype and Loading/Empty/Error/Content plus modal/menu/sheet/expand/mutation states; Standard light/dark/deterministic dynamic and E-ink; required window breakpoints/splits; fontScale 1.0/1.3/2.0; both portrait acceptance devices; list/grid; branding loaded/fallback/invalid/frozen/update states. Production composables and real strings only. Content-copy allowlist, initial-viewport density and E-ink full-frame grayscale are assertions, not manual impressions. |
| Instrumentation | Root/profile/window switch without state loss; process recreation for route/form/modal/query/page; Back vs Up vs root reselection; canonical Detail from every caller; mixed Library node/collection navigation; Reader in-place WYSIWYG seek cancel/commit/return-origin with no popup preview; dialog focus/inert background; page/scroll restore; duplicate mutation prevention; Search performs one explicit trailing submit that starts local work plus the implicit active-source scope together, with no selector or leading Search control; exact-identity merge; cover cancellation; reset and unknown-newer preservation. |
| Device | API 29 Standard AVD and E-ink AVD; every E-ink evidence PNG includes real system bars and passes full-frame grayscale scan; physical E-ink evidence for ghosting/focus/tap/volume keys/dialogs/WebView return; Standard regression pass on the same build. |
| Static/release | §16.4 checks green; no prototype symbols; no deprecated paths; artifact hashes and exact head binding. |
| Prototype | Per `docs/design/UI_ATLAS.md` RC2.1: fixture-only full-screen atlas, direct-render boards, applicable interaction recordings, research/review/artifact hashes, acceptance-ledger results, route/module comments and immutable evidence. Rejected RC2 and RC2.1-3 remain separate; neither review set authorizes production extraction. |

---

## 17. Findings register (constitution-relevant; consolidates scout + prior audits)

### P0 — conditional release-blockers (none currently triggered; any occurrence blocks release)
| ID | Condition |
|---|---|
| UI-P0-A | Raw, remote-referenced, or executable branding/cover payload reaches a feature or production render path, bypassing the §15 `core:media` validation pipeline; **or** §15.5 band bounds violated (branding in dense list background/app chrome/reader/mixed-source contexts). |
| UI-P0-B | The mandatory prototype-atlas + manual-approval + separate implementation-authorization gate (§16.3) is bypassed: any Phase 4 production UI change or clean prototype extraction begins before both approvals are recorded. |
| UI-P0-C | Any preference write (or migration) that overwrites an unknown/newer UI-preference payload, or any reset/migration touching domain data (§2.4). |
| UI-P0-D | Any `:prototype:ui-atlas` namespace, symbol, or dependency present in the release graph. |

### P1 — must close in 4A
| ID | Finding |
|---|---|
| UI-P1-0 | Direct Material dialogs/controls bypass core:ui: login-import/writeback `AlertDialog` (`MainActivity.kt:433-480`); remote switch (`RemoteLibraryScreen.kt:69`); collection switches (`CollectionManagerScreen.kt:138,172`); browse checkbox (`BrowseScreen.kt:269`); library FilterChips/fields (`LibraryScreen.kt:121-195`) |
| UI-P1-0b | Silent mutations: tags/rating/add/remove/pull lack working/success/error/cancelled/unresolved states, duplicate-submit protection, live regions (= P4-UX-003 class) |
| UI-P1-0c | Duplicate detail surfaces + imprecise cross-root action (= P4-UX-001); constitution §13 canonical-detail cutover is the fix |
| UI-P1-0d | Source long lists lack shared E-ink pagination, stable `BookIdentity` keys, row semantics; remote list keys by `canonicalUrl` (`SearchScreen.kt:56-158`; `SourceBookScreens.kt:119-129`; `RemoteLibraryScreen.kt:54-104`) |
| UI-P1-1 | App-bar: title-only, no subtitle/count convention, action slot unused, reader title duplicates document title (`MainActivity.kt:374-388`; `ReaderSurface.kt:68-72`) |
| UI-P1-2 | Production lacks the RC2.1 mixed Library node flow and collection membership UI (= P4-UX-002); remains blocked behind Atlas approval. |
| UI-P1-3 | RECENT/sort/page identity require RC2.1 context keys; E-ink page state must include node/context/layout (= P4-UX-004). |
| UI-P1-4 | Production lacks selection mode and RC2.1 Detail/Reader/Search surfaces; fontScale 2.0 evidence remains required (= P4-UX-005). |
| UI-P1-5 | Non-saveable drafts remain in current collection/source flows and must be migrated only after authorization. |
| UI-P1-6 | Bare `Modifier.clickable` rows without Role/collection semantics; live regions only on loading |
| UI-P1-7 | Remote-library: generic route (no sourceId), Boolean loading, no pull taxonomy/count/cancel (= P4-UX-007) |
| UI-P1-8 | Missing routes/surfaces: reader settings, data/report, help and history (= P4-UX-006/008); current collection/template manager must be replaced by the mixed-flow create/rebuild model; single-picker transfer |
| UI-P1-9 | Reader raw 18/20sp styles, no position live status, E-ink can switch away from paged surface |
| UI-P1-10 | Off-scale raw spacing (8/12/14/18/20/24dp ad hoc) and untokenized feature Text styles across feature screens |
| UI-P1-11 | Golden coverage absent for Search/Book/Reader/Transfer/Collections/Remote/Verification/LocalBook |

### Adviser P1 architecture findings against the provisional — status: CLOSED
| Finding | Closed by |
|---|---|
| Complete Compose module DAG undefined/unenforced | §2.1–2.2 complete per-module target DAG (verified against current build files + MODULES.md; includes `shared:library-domain → shared:model/locator/smart-shelf`, `core:preferences → shared:backup`, per-feature enumeration, current-edge transition dispositions) + forbidden edges + §16.4 check 1 |
| Cover loading could enter the pure UI renderer | §2.3 CoverUiState ownership: state defined in `core:media` public API (`Ready` = host-owned `android.graphics.Bitmap` at the Android boundary); `shared:library-domain` carries `CoverRef` identity only; implementations Kotlin-internal; route owner subscribes, `core:ui` renders Bitmap→ImageBitmap pure; DAG edges 3/5 + §16.4 check 4 forbid internal-package imports and renderer-side repository use |
| Static checks and full verification matrix missing | §16.4, §16.5 |
| Unknown/newer preference schemas unsafe | §2.4 read-only preservation + write block; UI-P0-C; §16.5 instrumentation |

### P2
Hand-built failure surfaces duplicating `StateView` (Search/Book/Reader `BookFailure`); smart-field enum cycling instead of dropdown; approval/review lists always expanded without caps; verification host feedback not profile-shared; behavior caps not surfaced in UI; reader degraded-precision text presentation.

### Mandatory vs legitimate exception vs preference (classification)
- **Mandatory consistency**: tokens, anatomy, app-bar contract, row/card info order, state system, modal/menu/sheet/dialog rules, selection/bulk, navigation/Back/Up/focus, accessibility, E-ink policies, layout-preference precedence, cover/branding pipeline.
- **Legitimate domain exceptions**: reader surface (§14 enumerated only); controlled WebView boundary (verification/login — host chrome and feedback remain constitutional); source security approval detail (capability diff content); transfer conflict/warning detail; DisplaySettings E-ink-only section (effective-profile-gated visibility); compact source context (§15.5).
- **User preferences**: display profile, color scheme, dynamic color, per-context×profile layout override, reader settings, sort per context, tutorial enablement, interface reset.

---

## 18. Confirmed direction and M3-backed Atlas convergence

The RC2 decision table is closed. `DESIGN_DIRECTION_HANDOFF.md` RC2.1 is binding; the prior review bundle provides defect evidence, not selectable variants. The fresh Atlas proves execution inside the fixed direction and may not resurrect selector-only Library navigation, template manager, separate routine directory, giant source lanes, incomplete Reader controls, tag-heavy rows or pseudo-M3 controls.

| Item | RC2.1 status | Direction carried into fresh Atlas | Required proof |
|---|---|---|---|
| Q1 Standard motion | Converged | Restrained functional M3 motion ≤250ms; E-ink/reduced immediate | 30/60fps cancellation and reduced-motion evidence |
| Q2 Library row | RC2 rejected | Separated identity/byline/progress/state slots; compact 48–56dp M3-backed container; no Library tag/source | Dense-cover vs compact-text geometry, fontScale and long-title evidence |
| Q3 Library grid | User selected default; RC2 geometry rejected | Fixed three-column regular-phone grid in both profiles; fixed equal card heights; full-size long-title fallback; double-compact only may use two columns | Standard/E-ink fixed-grid and absent-cover stress boards |
| Q4 empty state | User selected emoticon; evidence invalid | Restrained monochrome emoticon + real reason/recovery action | Nonblank empty-state boards; scenario explanation remains outside the PNG |
| Q5 selection entry | Superseded by the high-frequency Library selection decision | Semantic long-press enters SelectionAppBar immediately and selects the pressed book/folder; no explicit AppBar entry and no intermediate item menu; Material press feedback plus explicit check glyph/high-contrast selected container | List/grid Standard/E-ink selection evidence; TalkBack/keyboard semantic LongClick evidence |
| Q6 Reader navigation | Visual approval deferred after RC2.1-3 rejection | Center-tap chrome + direct rail remain architectural; seek-preview presentation is not approved by current emulator evidence | Physical-device hold/update/cancel/release/return-origin evidence only |
| Q7 source identity | Converged | No duplicate label; compact mark only where disambiguation remains | Invalid/missing fallback and E-ink monochrome evidence |
| Q8 Reader settings | Superseded by B041-H after RC2.1-3 reconciliation | Standard retains the partial/full M3 sheet and its compact first viewport. E-ink instead uses one opaque full-screen AppBar route containing every `排版 / 页面 / 导航 / 设备` control; wide uses two balanced columns and compact stacks the same sections. | Standard sheet anchors/dismiss plus E-ink wide/compact complete-page inventory, safe area, Back, focus and fontScale |
| A creation action | Reconciled by B4-A | Library AppBar `+` is the only creation entry; no final-page card or creation FAB | Named AppBar task screenshots |
| B item action | User selected swipe | Visible trailing/overflow remains; Standard-only swipe shortcut | Alignment, cancel and TalkBack equivalent |
| C modal container | Converged | Standard real M3 bottom sheet; E-ink full-window opaque | Safe area, focus, Back and fontScale 2.0 |

None of these rows authorizes production extraction. The user must review fresh RC2.1 renders/evidence, then separately authorize production migration.

---

## 19. Shared component inventory

**Current code audit status:** named production `core:ui` APIs are not presumed RC2.1-conformant. No production implementation is authorized by this document.

**Atlas-first additions:** mixed Library node renderer, fixed-grid/compact slots, title-field cover fallback, integrated Detail chapters, Reader progress seek/preview, compact/complete Reader settings, unified deduped Search flow, Updates/Remote/Tags three-layout surfaces and existing state/media/security contracts. Atlas forks remain namespaced prototypes and never enter the release graph.


**Forbidden:** feature-local copies; a second visual system; a fake M3 comparison built from custom clickable surfaces; Library tag renderers; full-width source identity bands; nested Reader sheets; or production migration before manual Atlas approval plus separate implementation authorization.

---

## 20. Route × screen anatomy matrix

Archetypes: **RL** RootList · **CL** CollectionList · **DT** Detail · **FM** Form · **FL** Flow · **RD** Reader · **RP** Report · **IN** Info.

| Route | Root | Arch | App bar (title / Up / actions) | Content & states | Footer / E-ink | Exceptions & notes |
|---|---|---|---|---|---|---|
| `library` | Library | RL | `书架` / – / visible sync-check, search, layout; sort overflow | One mixed flow of system nodes, collections, mirrors and books; custom/title/recent-reading/rating sorts with applicable ascending/descending direction; semantic long-press selection; fixed three-column regular-phone grid and fixed geometry; long-title fallback; Loading/Empty/Error | E-ink explicit pages and move controls | System nodes default-created, hideable and rebuildable; only Read Later has manual membership; sync-check exposes working/final status |
| `library/system/{viewId}` | Library | CL | System-node name + count / Up→Library / sync-check, layout; sort overflow | Node-specific sorted book result and actions; semantic long-press selection; no shortcut shelf or in-place root replacement; three layouts where books are present | Scroll / explicit pages | Membership and mutation rules come from the selected system node |
| `library/history` | Library | CL | `历史` / Up→Library / clear-all | Recency groups; ≤7 days relative, older exact date/time; per-item remove | Scroll / explicit pages | Remove/clear never affect progress |
| `library/updates` | Library | CL | `追更` / Up→Library / check, settings, layout | Mihon-style date groups and 56dp rows; Standard short M3 working indicator, E-ink static working glyph; compact session/exact-anchor report; three layouts | Scroll / explicit pages | `确认已看过` semantics; key success/failure/partial results retain short text |
| `library/collections/{id}` | Library | CL | Collection name + count / Up→parent collection or Library / layout; sort overflow | Child collections and books as peers; every child opens a dedicated page; semantic long-press selection; title/recent-reading/rating asc/desc plus manual order where writable; three layouts; two-level max; concise rule data only; fixed shared 3:4 grid geometry | Scroll / explicit pages | No inline tree expansion or persistent tutorial/exclusion copy; Back and Up preserve the collection hierarchy |
| `library/collections/{id}/rule` | Library | FM | `规则` / Up→collection / save, help | Database-backed fields use picker/dropdown; only free text/ranges use text/range controls; inline validation | Scroll | Creation path starts from the Library AppBar `+` |
| `library/tags` | Library | CL | `标签` / Up / search, layout, sort | `本地 / 来源` tabs; compact chips omit counts; list rows show book counts; local edit, source read-only | Scroll / pages | No vertical mixing of ownership groups |
| `library/mirror/{bindingId}` | Library | CL | Source/folder name + status / Up→parent mirror page or Library / calibrate, layout; sort overflow where books exist | Website folders and books as peers through the shared collection renderers; title/recent-reading/rating asc/desc; each folder and optional `本地整理` opens a dedicated page | Scroll / pages | No inline tree expansion; local pages never imply or perform website writes |
| `book/{sourceId}/{remoteBookId}?origin=…` | caller's | DT | Book title / Up→caller / cache, overflow for refresh, source-open, local removal and available website mutations | Identity module → tag/action module → introduction module → isolated directory module; the directory header keeps chapter icon, `全文目录`, total count, unread filter and current-state direction icon on one compact row before full chapters. Read titles use regular outline gray, unread titles are dark medium-weight with one leading dot, and downloaded state is trailing-icon-only. Modules share one divider/icon/typography grammar and contain no explanatory teaching copy. | Standard scroll / E-ink pages | Rating/tags/progress local; remote freshness labelled; website writes only when capability exists; no generic website-action proxy or duplicate footer actions |
| `book/…/reader/{chapterId}` | caller's | RD | Center-tap overlay chrome / Up→detail / contents/search/bookmark/settings | Current-chapter M3 Slider with live body preview and one semantic commit; optional compact chapter/page/percentage strip with thin determinate line; paged modes reject vertical scroll; Standard partial→full M3 settings sheet; E-ink dedicated full-screen AppBar settings page with complete `排版 / 页面 / 导航 / 设备` sections, two columns wide and stacked compact | Standard scroll or fixed pages; E-ink persistent status; final WYSIWYG seek preview requires production paginator evidence | Semantic locator only; percentage/page/pixel values are never durable truth; Reader-only immersion |
| `search?origin={library|browse}&selectedSourceId={sourceId?}` | caller's | CL | `聚合搜索` / Up / layout | Query draft + one trailing Search action starts local work and the implicit active-source scope together; normal entry covers all active capable sources, source-card entry binds one source; no selector or leading Search control; one aggregate progress; unified exact-identity result flow; no advanced filters in Phase 4A; three layouts | Scroll / pages | No result update/network before submit; no persistent teaching, identity or normal per-source status prose |
| `browse` | Browse | RL | `浏览` / – / search, import | Unified M3 source rows/cards with consistent button hierarchy | Scroll | Source identity contextual only |
| `browse/source/{sourceId}/remote-library` | Browse | CL | Source name / Up / refresh list, copy all, select, layout | Capability gates; three layouts; multi-select; fixed trailing alignment; local target picker | Scroll / pages | No duplicate source banner or website write |
| `source/verification` | Browse | FL | `登录验证` / Up / – | Host notice + controlled WebView + explicit actions | Scroll | Host feedback constitutional |
| `more` | More | RL | `更多` / – / – | Grouped rows: 显示 / 阅读 / 数据 / 帮助 / 关于 (real handlers only) | Scroll (bounded) | 560dp max width |
| `more/display` | More | FM | `显示` / Up / – | Profile selector, theme, dynamic color (capability matrix §9.5), E-ink section (redraw) + `重置界面设置` | Scroll | Write-failure banner + retry; persisted vs effective; unknown-newer prompt surface (§2.4) |
| `more/reader` | More | FM | `阅读` / Up / – | Groups: typography/layout, navigation/progress, effective E-ink constraints; persisted vs effective | Scroll | Dependencies can't create invalid combos |
| `more/data` | More | FM | `数据` / Up / – | First-entry introduction; `导入 Tsuyomi 数据` / `从 Hikari Novel 导入` / `导出` / `查看最近导入报告` separate entries; export review shows semantic progress default-included, browsing/search history separately default-off, and immutable exclusions | Scroll | Picker cancel = zero state change; import never enables mirror/schedule/writeback |
| `more/data/report/{sessionId}` | More | RP | `导入报告` / Up→data / – | Redacted durable report: format, counts, warnings/conflicts (expand > 50), result | Scroll | Recovery gate above navigation until resolved |
| `more/help` | More | IN | `帮助` / Up / – | Replay the five required feature introductions (mirror, Updates, smart rule, website writeback, data); global intro enable/disable + reset seen versions; deep link to canonical interface reset | Scroll | Actual first-entry surfaces own the same content; dismissal never approves capability or starts work |
| `more/about` | More | IN | `关于` / Up / – | App name, version, license text | Scroll | No fake entries |

Removed at cutover: standalone `library/collections`, `library/collections/templates`, routine `book/…/directory` screen (deep link → Detail chapter section), `library/book/…`, state-only source detail/directory/reader, generic source remote library, legacy settings/transfer/about routes. No aliases or shims.

---

## 21. Accessibility contract (summary; binding everywhere)

TalkBack: every row/action announces role + selected/disabled/expanded/update state; pagination reads `第 x 页，共 y 页`; mutation results via polite live regions (not unconditional `announceForAccessibility`); destructive dialogs default to safe action; E-ink outcomes persist until dismissed. Keyboard/DPAD: full traversal of every row/action/dialog; focus never stranded; 48dp targets. fontScale 2.0: no clipping, no unreachable actions. Reduced-motion honored via INSTANT policy. Color never sole carrier; decorative branding marks excluded from the accessibility tree with identity carried by text. These are gate criteria, not aspirations.

---

## 22. Research synthesis — observed → adopt/adapt/reject (explicit citations)

Licenses: Mihon Apache-2.0; LNReader MIT; Feeder/Read You/Book's Story GPL-3; Hikari MIT (bundled source marks unprovenanced). **All are behavior reference only; Tsuyomi implementations are original.** "Observed" cites repo file:lines as recorded by `ModernReaderUiResearch` / `HikariVisualLanguageAudit`.

### 22.1 Modern project comparison (binding deltas)

| # | Disposition | Rule | Observed evidence |
|---|---|---|---|
| A-1 | ADOPT | One primary semantic action per surface, identifiable without opening a menu | Mihon `AppBar.kt:69-153`; Book's Story `LibraryTopBar.kt:95-180` |
| A-2 | ADOPT | App bar for screen-level actions (back, search, filter, display mode); direct slots for frequent ops | Mihon `BrowseSourceToolbar.kt:35-130`; Book's Story `LibraryTopBar.kt:95-180` |
| A-3 | ADOPT | Overflow for infrequent/contextual actions; short, frequency-ordered; radio/check for exclusive display modes | Mihon `BrowseSourceToolbar.kt:35-130` |
| A-4 | ADOPT | FAB only for one dominant creation or singular bulk action; never competing FABs; hide when invalid | Mihon `CategoryScreen.kt:35-130`; Feeder `FeedScreen.kt:1096-1210` |
| A-5 | ADOPT | Selection mode **replaces** the app bar: cancel, count, select-all, bulk actions | Mihon `AppBar.kt:69-153`; Book's Story `LibraryTopBar.kt:180-275`; LNReader `Actionbar.tsx:39-69` |
| A-6 | ADOPT | Row trailing actions for edit/delete/reorder when discoverability matters; destructive behind confirmation | Mihon `CategoryListItem.kt:20-85` |
| A-7 | ADAPT | Swipe only for frequent reversible transitions; duplicate in semantics + accessible action path; Standard-only | Feeder `SwipeableFeedItemPreview.kt:144-258` |
| A-8 | ADOPT | List/grid are user preferences with one default per density target; Tsuyomi defaults per §5.4 (user decision) | Mihon grid modes; LNReader `NovelList.tsx:20-110` |
| A-9 | ADOPT | Lazy containers, stable keys, full-span loading rows; item-identity motion only | Mihon `BrowseSourceComfortableGrid.kt:20-80` |
| A-10 | ADOPT | Separate loading/empty/error/retry states; spinner/animation never stands in for error | Feeder `FeedScreen.kt:1269-1348`; Read You `FeedsPage.kt:130-240` |
| A-11 | ADAPT | Sheets choose/filter/adjust; dialogs confirm/edit/irreversible; menus short contextual; never all three for one op | Read You `GroupOptionDrawer.kt:35-120`; Book's Story `BookInfoChangeCoverBottomSheet.kt:48-110` |
| A-12 | ADOPT | Low-chroma tonal surfaces; saturated accent reserved for selection/CTA/status | Read You `DynamicTonalPalette.kt:15-48`, `TonalPalettes.kt:42-96` |
| A-13 | REJECT | Permanent page-wide decorative gradients, animated decorative backgrounds, low-contrast text over arbitrary images (media-only contrast mask allowed) | Research matrix (Mihon media gradients constrained) |
| A-14 | REJECT | Gesture-only navigation/action, hidden destructive actions, nested sheets, always-visible competing action bar | Research comparative matrix |
| A-15 | ADAPT | Standard: Material-style spatial transitions; E-ink/reduced: instant or brief opacity-only | Read You `MotionConstants.kt:20-32`; Feeder `Settings.kt:739-768`, `ArticleScreen.kt:99-140` |
| A-16 | ADOPT | Placement by frequency × scope × reversibility × space (§4.2 decision rule) | Research decision-rule synthesis |

### 22.2 Motion evidence → tokens (§11.1)

| Observed | Value | Tsuyomi disposition |
|---|---|---|
| Read You `MotionConstants.kt:20-32` | 300ms default / 150 fade-in / 75 fade-out / 30dp slide | ADAPT: fade 100–150 in / 75–100 out adopted; 300ms default reduced to ≤250ms constitutional ceiling |
| Read You `RYExtensibleVisibility.kt:15-35` | fade + expand/shrink visibility | ADOPT as §8 pattern (200–250ms, top-anchored) |
| Feeder `FeedScreen.kt:1096-1210` | FAB `scaleIn/scaleOut(tween(256))` | ADAPT: 150–250ms visibility token |
| Feeder `ArticleScreen.kt:99-140` + `Settings.kt:739-768` | animated paging is explicit user preference, else immediate | ADOPT verbatim as reader-paging rule |
| LNReader `BottomSheetBackdrop.tsx:18-22`, `BottomSheet.tsx:30-75` | backdrop fade 100ms; safe-area clamp; no overdrag/dynamic sizing | ADOPT (Standard); E-ink instant |
| LNReader `Actionbar.tsx:39-69` | selection bar slide 150ms | ADAPT: 150–250ms visibility token |
| Hikari `home/view.dart` | source switch slide+fade 220ms; title 160ms | ADAPT: source/context change may use one directional 200–250ms transition; ordinary updates static |
| Hikari `home/view.dart` | section collapse up to 700ms | REJECT: exceeds ceiling on a reading surface |
| Book's Story `ReaderScreen.kt:112-118` | `animateColorAsState` for reader preset | ADAPT: 0–50ms immediate-feedback class; reader preset change only |
| Book's Story `AppModule.kt:55-66` | Room `.allowMainThreadQueries()` | REJECT (persistence anti-pattern; recorded for completeness) |

### 22.3 Hikari visual language → source identity (§15)

| # | Disposition | Rule | Observed evidence |
|---|---|---|---|
| A-HK-1 | ADAPT | Four-layer contract: signed manifest token → validated mark + fallback → host contrast-budget palette → surface policy | `source_backdrop.dart`, `source_config.dart:1-13` (behavior only) |
| A-HK-2 | ADOPT | Deterministic title/source fallback for absent/failed covers; zero-fade cover commits; DPR-bounded decode; RepaintBoundary isolation | `novel_cover_card.dart` |
| A-HK-3 | ADOPT | Button hierarchy: one primary filled, one secondary outlined, text for cancel/low-emphasis, explicit destructive | `custom_tile.dart`, `bottom_action_bar.dart` (mixed in source; rule synthesized) |
| A-HK-4 | ADAPT | Bottom action bar only for 2–3 peer actions with readable labels | `bottom_action_bar.dart` (72px, 2–3 item assertion) |
| A-HK-5 | ADOPT | Sheets safe-area aware, bounded, drag handle; dialogs bounded with focus trap and deterministic destructive order | `common_widgets.dart`, `custom_tile.dart` |
| A-HK-6 | ADOPT | E-ink browsing: deterministic local pagination + page controls instead of free scroll | `browsing_novel_grid.dart` |
| A-HK-7 | ADOPT | Reader E-ink: suppress images/backgrounds/motion; ≥3.5:1 contrast fallback to black/white; bounded margins | `reader/controller.dart`, `reader/widgets/reader_background.dart` |
| A-HK-8 | ADOPT | E-ink removes decorative layers rather than near-zero-ing their durations | `source_backdrop.dart` (no E-ink branch = counter-evidence) |
| R-HK-1 | REJECT | Gradient + radial wash + rotated oversized SVG backdrop; no E-ink branch; content over backdrop | `source_backdrop.dart` |
| R-HK-2 | REJECT | Black image gradient as sole title contrast; 12px overlay-only identity | `novel_cover_card.dart` |
| R-HK-3 | REJECT | Remote/HTTP artwork as trusted identity (no license metadata) | `network/esj_api.dart:12-14` |
| R-HK-4 | REJECT | Identity inferred from aid text prefix; unknown IDs defaulted to another source | `service/source_favorite_adapter.dart` |
| R-HK-5 | REJECT | Competing button patterns without a selection rule | `bottom_text_icon_button.dart` vs detail text buttons |
| R-HK-6 | REJECT | Copying any bundled mark: `wenku8.svg` (SVG Repo, no URL/license), `esj.svg` (local extraction), `yamibo.svg` (no attribution) | `assets/images/source/*.svg` |
| R-HK-7 | REJECT | Transparent app bar over backdrop with dense text beneath | `pages/novel_detail/view.dart:78-85` |
| R-HK-8 | REJECT | Chrome collapse/opacity-only state change on E-ink; 700ms collapse | `pages/home/view.dart` |

### 22.4 Research risks carried into gate criteria

LNReader v2.0.0 is pre-release React Native — interaction constraints only, never Compose evidence. Read You's `RYScaffold` is deprecated — adopt behavior, not wrapper API. No reference ships a complete E-ink/reduced-motion policy — Tsuyomi's §11.2 is novel and must be proven in the atlas. Reference durations are starting tokens; cancellation, frame pacing, and E-ink rendering are verified in the atlas before standardization. Broad per-screen configurability is rejected as variant explosion.

---

## Change log — v1.0-RC2 → v1.0-RC2.1

| # | Change | Driver |
|---|---|---|
| 1 | Treated the manifest-bound RC2 review bundle as rejected/comment evidence and required a fresh direct-render Atlas. | 2026-08-13 user review |
| 2 | Made a fixed three-column regular-phone grid the Library default and replaced selector/template management with mixed, hideable/rebuildable System/Collection/Mirror/Book nodes. | Reviewer decisions + conflict reconciliation |
| 3 | Bound fixed grid height, 48–56dp compact rows, separated information slots, explicit E-ink checks and full-size title cover fallback. | Library/E-ink review comments |
| 4 | Integrated full chapters into Detail and retained a deep-link anchor rather than a routine directory screen. | Hikari + Mihon deep review |
| 5 | Corrected Reader seek to the Hikari-visible contract: the reading viewport itself presents the live same-layout preview while the durable reader/locator remain uncommitted until release; popup preview UI is forbidden. | RC2.1-3 review + Hikari source re-check |
| 6 | Corrected Search to one explicit trailing submit that starts local work plus the implicit route-owned active-source scope; removed the selector, leading Search control and persistent instructional/identity/source-status prose from normal UI. | RC2.1-3 review + B041-D |
| 7 | Added per-route content allowlists, first-viewport density gates, matching adaptive Detail tag/add-tag containers, readable E-ink Updates/Remote rules and the complete E-ink Reader settings page. | RC2.1-3 A–H comments + B041-B/H |
| 8 | Made real system bars part of the E-ink monochrome contract and added full-frame grayscale evidence. | RC2.1-3 module comment |
| 9 | Reconciled every substantive review export and the initial protocols; recorded one authoritative conflict-decision layer instead of treating the latest JSON as the only source. | Full review/protocol audit |
| 10 | Fixed both profiles to a three-column regular-phone default, ignored source layout hints, made system nodes hideable/rebuildable with automatic rule membership, and added desktop-style manual ordering/create gestures. | Explicit conflict decisions |
| 11 | Deferred D33 advanced filtering from both Atlas and Phase 4A; retained one-submit concurrent basic search, one result flow, aggregate progress and exact-identity dedupe. | Explicit conflict decisions |
| 12 | Kept the Standard Reader partial/full sheet and replaced E-ink quick/group settings with one complete full-screen grouped page; bound Updates working state by profile. | Explicit conflict decisions + B041-H |

*Adoption path: (1) revise and capture the fixture-only direct-render Atlas RC2.1 → (2) manual review with one comment per route and one module comment → (3) record the manifest-bound approval tuple, with no production change authorized → (4) obtain separate explicit Phase 4 production implementation authorization → (5) reissue v1.0 and perform a clean production extraction → (6) delete the prototype and regenerate evidence from production composables.*
