<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Tsuyomi interactive review prototype plan

Status: **FINAL — revised through user decision `IP-07` on 2026-08-20.**

This plan implements only the isolated `:prototype:ui-atlas` debug application. It does not authorize production Gate 4 code, production persistence, network/source execution, commits, pushes, PRs, merges, or extraction into production modules.

## 1. Goal and deliverable

Produce one installable debug APK that lets the reviewer operate the proposed Tsuyomi experience instead of judging still images alone.

The APK must provide:

1. product-faithful navigation across the current 20-route Atlas inventory;
2. deterministic, editable synthetic data behind every reviewable task;
3. real Standard Material 3 controls and restrained Standard motion;
4. the retained E-ink presentation for the same task/state tree; its full design/review is frozen by `IP-07`, not removed;
5. a debug-only review button available from every reviewable surface;
6. one free-form comment per stable route, stored locally with read-only context metadata;
7. explicit JSON export through Android's Storage Access Framework and share sheet;
8. a resettable seeded dataset with no network, real credentials, real source package, real WebView content, telemetry, account, or production-module dependency.

Planned APK identity:

```text
applicationId: org.tsuyomi.prototype.uiatlas
variant:       debug
minSdk:        29
compileSdk:    36
output:        prototype/ui-atlas/build/outputs/apk/debug/ui-atlas-debug.apk
```

## 2. Authority and non-authority

Product-visible behavior follows, in order:

1. `docs/design/UI_CONSTITUTION.md` Active constraint spine;
2. compatible detailed rules in that Constitution;
3. `docs/gates/GATE_4.md` scope, sequencing and safety boundaries;
4. `docs/design/UI_ATLAS.md` evidence mechanics;
5. this plan only where the documents above leave an implementation boundary open.

This plan may choose prototype mechanics, review tooling and deterministic simulation behavior. It may not invent a new product route, remote effect, destructive scope, persistent product sentence or production architecture.

### 2.1 Operational execution

`tools/skills/tsuyomi-android-review/SKILL.md` is the only runbook. `review-policy.json` is the only machine-readable source for active/deferred profiles. This plan owns prototype architecture and acceptance behavior, not commands, tool fallback order, or per-run evidence selection.

R1 still accounts for all 28 nodes and every affected obligation. During `gate4-standard-first`, R2–R4 execute routine design/review only on Standard; E-ink obligations remain represented but deferred. Workflow-only changes stop after R1, and an empty delta never upgrades review state.

## 3. Implemented-state constraints

The Atlas is now the single interactive review application: typed route stacks, one prototype runtime, versioned fake-data/review stores, deterministic scenario control, visible navigation, review export/share, and capture isolation are implemented in `:prototype:ui-atlas`.

This plan no longer tracks implementation progress. Future changes must preserve one route tree, typed actions, fake/review storage separation, capture determinism, prototype isolation, and the human-only approval boundary. The runbook verifies those contracts; it does not create a second checklist here.

## 4. Target architecture

```text
MainActivity
└── AtlasPrototypeApp
    ├── PrototypeRuntime
    │   ├── PrototypeRepository
    │   ├── PrototypeScenarioController
    │   ├── PrototypeActionDispatcher
    │   ├── PrototypeNavigationState
    │   └── PrototypeEventTrace
    ├── AtlasTheme / DisplayEnvironment
    ├── one route tree and existing screen composables
    └── DebugReviewHost
        ├── ReviewTargetResolver
        ├── ReviewRepository
        ├── ReviewPanel
        ├── ReviewOverview
        └── ReviewJsonExporter
```

### 4.1 Ownership rules

- `PrototypeRuntime` is created once by the Activity and exposed through a prototype-only composition local or Activity-scoped ViewModel.
- Screens receive immutable `UiState` plus one typed `dispatch(PrototypeAction)` sink.
- Composables retain only ephemeral focus, scroll, sheet anchor and gesture state.
- Every domain-visible mutation goes through the dispatcher; no screen edits fixture lists locally.
- One reducer/repository update produces both the new state and a typed event trace.
- Capture mode bypasses persisted state and renders the exact intent-selected deterministic snapshot, preserving the existing screenshot workflow.

### 4.2 Proposed source layout

```text
prototype/ui-atlas/src/main/kotlin/org/tsuyomi/prototype/uiatlas/
  runtime/
    PrototypeRuntime.kt
    PrototypeRepository.kt
    PrototypeSnapshotStore.kt
    PrototypeSeed.kt
    PrototypeAction.kt
    PrototypeEvent.kt
    PrototypeScenarioController.kt
  navigation/
    PrototypeDestination.kt
    PrototypeNavigator.kt
    PrototypeBackStack.kt
  review/
    ReviewTarget.kt
    ReviewComment.kt
    ReviewRepository.kt
    ReviewPanel.kt
    ReviewOverview.kt
    ReviewJsonExporter.kt
    ReviewBuildIdentity.kt
```

These remain in the prototype namespace. Production modules neither import nor depend on them.

## 5. Interactive fake-data store

### 5.1 Recommended mechanism

Use a normalized in-memory state tree with versioned JSON snapshots written atomically to the prototype app's private `noBackupFilesDir`.

Why this instead of Room:

- the data is intentionally synthetic and disposable;
- a Room schema would resemble production architecture without testing production repositories;
- atomic JSON is easier to seed, reset, inspect and export;
- no KSP/Room compiler is needed in the isolated prototype;
- the store can still behave like a database through repositories, stable IDs, normalized records and transactions.

The implementation dependency is limited to `kotlinx-serialization-json`. Android `AtomicFile` provides replace-on-success writes. There is no external storage access except explicit user-driven review JSON export.

### 5.2 Logical tables

The snapshot is normalized into bounded record maps/lists:

- `books`
- `chapters`
- `sources`
- `collections`
- `collectionMemberships`
- `systemNodeVisibilityAndOrder`
- `presenceOrigins` (`LOCAL_PIN`, `READ_LATER`, `MANUAL_COLLECTION`, `WEBSITE_MIRROR`)
- `localTags` and `bookTagLinks`
- `ratings`
- `semanticProgress`
- `history`
- `updateInbox` and `updateSessions`
- `remoteLibraryRows`
- `mirrorBindings` and `mirrorNodes`
- `displayPreferences` and `readerPreferences`
- `tutorialSeenVersions`

Review comments and action traces are stored separately from fake product data so resetting the dataset never silently deletes review work.

### 5.3 Seed and reset contract

- `PROTOTYPE_DATA_SCHEMA_VERSION` controls snapshot compatibility.
- `ATLAS_SEED` and a fixed clock generate the initial dataset.
- first launch seeds once;
- normal app relaunch preserves reviewer mutations;
- `重置假数据` restores the seed but preserves review comments;
- `清空审阅意见` is a separate destructive action with confirmation;
- an incompatible future snapshot is preserved as a stale file, then a fresh seed is created; it is never partially interpreted;
- uninstall clears all local prototype state; Android backup stays disabled.

### 5.4 Transaction and delay behavior

Each action executes as a bounded transaction:

```text
idle → working → success | recoverable failure | cancelled | unresolved
```

The scenario controller supplies deterministic outcomes and fixed delays. Standard may animate presentation changes; E-ink still observes asynchronous working/result states but replaces frames immediately and uses static working glyphs.

No simulator action opens a socket, reads production storage, accesses credentials or executes source code.

## 6. Navigation and state restoration

Replace the string-delimited route stack with typed prototype destinations and route arguments. Use Navigation Compose or an equivalent typed prototype back stack, while preserving:

- exactly three roots: Library, Browse, More;
- independent restorable root stacks;
- reselect current root pops only that root to its root;
- Back closes modal/selection/search/settings before popping route;
- Up follows semantic parent;
- one canonical Detail contract per `BookIdentity`;
- Reader returns to the exact Detail/caller state;
- shared Search records its origin and optional implicit source scope;
- process recreation restores bounded route arguments and fake-store truth, never controller objects.

Capture launches remain direct route renders and hide the debug review chrome.

## 7. Interaction implementation scope

### 7.1 Library family

1. **Library root/system views**
   - open mixed system/collection/mirror/book items;
   - expand/edit shortcut shelf;
   - switch grid/list/compact and supported sort modes;
   - enter/exit selection through visible action and long-press shortcut;
   - create manual collection through the sole AppBar `+` entry;
   - hide/rebuild/reorder system nodes;
   - Standard drag/reorder and confirmed two-book folder creation;
   - E-ink explicit move/page controls with the same result.

2. **History**
   - resume, remove one item, clear-all confirmation/cancel;
   - preserve semantic progress when history is removed.

3. **Updates**
   - manual check, deterministic incremental discoveries, cancel, partial failure and retry;
   - scheduling controls remain fake and local;
   - working copy remains exactly `正在检查更新`.

4. **Collections/rules**
   - create, rename, two-level nesting, membership, sorting, delete choices;
   - smart-rule draft, picker/range/checkbox edits, validation, unsaved Back confirmation.

5. **Tags**
   - local/source ownership tabs;
   - compact/list toggle;
   - local create/rename/delete and book-count updates;
   - source tags remain disabled/read-only.

6. **Mirror**
   - website structure browsing;
   - explicit creation of local organization;
   - typed local-only mutations and zero simulated remote writes.

### 7.2 Detail, Reader and source family

1. **Canonical Detail**
   - rating, local tags, add-tag, Read Later, cache state, membership management;
   - full chapter filter/sort/jump and Reader entry;
   - scroll-direction FAB behavior;
   - local remove confirmation with retained-data wording;
   - contracted remote-operation lifecycle simulations only where already present.

2. **Reader**
   - center-tap chrome, page navigation, immersive restoration and Reader settings;
   - semantic progress and exactly one commit per completed seek action;
   - volume-key and physical E-ink claims remain later device evidence.

3. **Browse/Search/Remote Library/Verification**
   - browse source cards and source-scoped search entry;
   - inert search draft, one trailing submit, one aggregate progress/result stream;
   - deterministic source success/failure/retry with exact-identity merge;
   - remote-list refresh, selection and explicit copy-to-local flow;
   - verification remains a local stub page; no real WebView or credentials.

### 7.3 More family

- Display preferences, theme/profile, dynamic-color and E-ink redraw behavior;
- Reader defaults and dependency rules;
- import/export/report staged simulations with no real product-data import;
- warning expansion/recovery flow;
- Help search/accordion/feature introduction replay;
- About/license modal.

## 8. Motion and profile review

### 8.1 Standard

Use existing `AtlasMotion` tokens with a hard ceiling of 250ms:

- press/state feedback: immediate to 120ms;
- expand/collapse and spatial route changes: 200–220ms;
- sheet transitions: 220ms;
- no decorative loops, parallax, spring overshoot or animation-only meaning.

Every transition must be cancellable and must settle on repository truth after rapid repeated input.

### 8.2 Reduced motion and E-ink

- duration resolves to zero and the animation composable is skipped, not merely accelerated;
- E-ink uses fixed light monochrome surfaces, opaque replacement and explicit paging;
- async jobs still expose visible working/result states;
- profile switch operates at the root and never creates profile-specific business state.

### 8.3 Review scenarios

The debug panel exposes deterministic scenario selection for the current task:

```text
success / slow / offline / recoverable-error / cancelled / unresolved
```

Only scenarios valid for that action are shown. Scenario selection is debug metadata, not product UI.

## 9. In-app reviewer

### 9.1 Review node identity and context

`ReviewNodeCatalog.kt` is the single source of truth for review scope. Catalog version 2 contains 28 stable nodes covering all 20 route/surface rows, Reader high-risk work and cross-cutting tasks. Route changes resolve to a node; they do not imply that the node was reviewed.

Opening or editing the reviewer captures read-only context metadata:

```text
buildId + reviewCatalogVersion + nodeId
route + stable route arguments
profile + theme + reduced-motion
window width/height/density/fontScale/locale
primary state + overlays/modal + library view/Reader seek mode
last typed action and bounded recent event trace
```

The editable record is keyed by `buildId + nodeId`. Context is retained as `lastEditedContext` for diagnosis but never creates a second thread. A later APK build gets a new `buildId`; prior comments remain read-only stale history rather than silently attaching to the new build.

### 9.2 Debug affordance and presentation

Approved presentation:

- a compact debug-only edge affordance labelled for accessibility as `审阅`; it must minimize overlap with reviewed product content;
- it is hidden in capture mode;
- Reader shows it only when chrome is visible, so immersive reading is not permanently obstructed;
- Standard opens a real Material 3 modal bottom sheet whose content is vertically scrollable to every action;
- E-ink opens an opaque full-screen review page with AppBar and immediate replacement;
- closing returns to the exact route, scroll/page and modal context captured at opening.

The review surface itself is excluded from product design approval and is visibly marked as a debug reviewer.

### 9.3 Comment, progress and authority model

Each stable node owns one free-form comment across states and profiles. The record identifies its author as `AI`, `HUMAN` or `MIXED`; edits auto-save after approximately 450 ms and are flushed again when switching nodes or leaving the panel.

Progress fields are independent:

- `visitedAt`: the resolved surface/state was opened;
- `aiTriagedAt`: an AI draft was completed;
- `humanReviewedAt`: a human performed the node task or set a verdict;
- `approvedAt`: created only by a human `ACCEPT` verdict.

Verdicts are `PENDING`, `ACCEPT`, `REVISE`, `BLOCKED` or `NOT_APPLICABLE`. AI may write a pending draft and attach evidence, but may not set a human verdict, approve a node, update a golden or overwrite a human conclusion. `AUTOMATION`, `PAUSED` and `HUMAN` control modes allow immediate handoff in the same APK/state.

Visual and interaction evidence use separate normalized lowercase SHA-256 fields. A screenshot hash never substitutes for an interaction-trace hash. The overview shows all 28 catalog nodes, required states, operations, visual checks, human-only checks, stale builds, one whole-prototype comment, JSON export/share, fake-data reset and separate comment clearing.

### 9.4 Export contract

Use `ActivityResultContracts.CreateDocument("application/json")`; no storage permission is requested. A second explicit `分享 JSON` action uses a prototype-owned `FileProvider` URI.

Schema version 2 exports the complete catalog plus node-keyed review state:

```json
{
  "schema": "tsuyomi-interactive-prototype-review-v2",
  "provisional": true,
  "productionAuthorized": false,
  "build": {
    "applicationId": "org.tsuyomi.prototype.uiatlas",
    "buildId": "<sha256>",
    "designRulesSha256": "<sha256>",
    "dataSchemaVersion": 1,
    "reviewSchemaVersion": 2,
    "reviewCatalogVersion": 2
  },
  "device": {"sdk": 29, "locale": "zh-CN"},
  "reviewCatalog": [],
  "nodeComments": [
    {"nodeId": "L01", "route": "library", "author": "ai", "comment": "...", "lastEditedContext": {}}
  ],
  "progress": {
    "L01": {
      "visitedAt": "...",
      "aiTriagedAt": "...",
      "verdict": "pending",
      "visualEvidenceHash": "<sha256>",
      "interactionEvidenceHash": "<sha256>"
    }
  },
  "summary": {"totalNodes": 28, "visited": 1, "aiTriaged": 1, "humanReviewed": 0, "approved": 0},
  "controlMode": "paused",
  "exportedAt": "..."
}
```

Empty comments are omitted. Export contains no fake database dump, credentials, raw HTML, external files or hidden device identifiers. A v1 route comment migrates once to the route's catalog successor node; deleted historical routes are not reintroduced.

## 10. Build identity and stale-review isolation

At build time, generate a deterministic `buildId` from:

```text
sorted non-build prototype source bytes
+ active design-rule bytes
+ data schema version
+ reviewer schema version
```

The debug app displays the first 12 characters in the review overview and exports the full value. Comments and progress are keyed by `buildId + nodeId`; route/state/profile/action context remains diagnostic metadata, not a review partition.

Installing a newer APK over the old one preserves old review records but starts a new active review namespace. The reviewer displays old records read-only and exports them only when the user explicitly enables `包含旧版本意见`.

## 11. Implementation sequence and acceptance

### Phase 0 — Contract amendment

- record the approved `IP-01` through `IP-06` decisions;
- amend `UI_ATLAS.md` fixture-only storage and review-coverage language while retaining zero production/network edges;
- add machine-readable node-comment and export schemas.

Acceptance: no conflict remains between active Atlas rules and the interactive prototype mechanics.

### Phase 1 — Runtime and persistence

- add lifecycle/ViewModel, navigation and serialization dependencies only to `:prototype:ui-atlas`;
- implement seed, normalized snapshot, atomic persistence, reset and review store;
- implement typed actions/events and deterministic scenario controller.

Acceptance: seed is repeatable; mutation persists across process death; fake-data reset preserves comments; comment clear does not mutate fake data; capture mode ignores persisted state.

### Phase 2 — Navigation cutover

- replace string stacks with typed destinations and independent root restoration;
- migrate every current navigation caller;
- preserve intent-driven direct capture.

Acceptance: Back, Up, root reselect, cross-root detail and Reader return pass behavioral smoke on API 29.

### Phase 3 — A–H visual-direction smoke

Implement and verify the eight fast visual-direction paths first:

- A Library create/layout/selection/reorder;
- B Detail rating/tags/read-later/chapter/FAB;
- C Reader semantic seek prototype under the chosen boundary;
- D Search inert draft/submit/progress/results;
- E Updates incremental check;
- F Remote Library refresh/copy;
- G Tags mutations/layout;
- H Standard sheet/E-ink full-screen settings.

Acceptance: each smoke path has a complete touch path, visible working/outcome state and typed trace. Passing A–H proves only visual-direction continuity; it does not satisfy the 20-surface or 105 route-state gate.

### Phase 4 — Full Review Graph behavior

Complete all 20 route/surface rows and every successor obligation, including History, collections, rule editor, mirror, Browse, verification, Display/Data/Report/Help/About and error/offline/modal variants. Resolve the surface and high-risk work through the 28-node `ReviewNodeCatalog`.

Acceptance: all 20 inventory rows are reachable, all 105 route-state obligations remain represented, and every visible primary/secondary action either performs its documented simulation or is disabled with an actionable reason. The active review profile is selected by `review-policy.json`.

### Phase 5 — Motion/profile pass

- centralize Standard transitions on `AtlasMotion`;
- remove ad-hoc animation durations;
- verify Standard normal and reduced-motion behavior now;
- preserve E-ink instant-replacement code and contracts without new design decisions or routine evidence while `IP-07` is active.

Acceptance during the freeze: actual Standard interactions demonstrate intended motion and reduced-motion replacement. E-ink motion/profile acceptance returns in the restoration pass.

### Phase 6 — In-app reviewer and export

- implement the compact debug trigger, catalog resolver, 28-node panel, overview and versioned storage;
- bind comments/progress to `buildId + nodeId` and retain route/state/profile/trace only as `lastEditedContext`;
- separate visited, AI triage, human review and approval; keep AI drafts pending until human verdict;
- persist comments with debounce plus node-exit flush, and store visual/interaction evidence hashes independently;
- implement CreateDocument/share export plus the debug-only ADB live-review bridge;
- on explicit live submission, flush current comments, atomically write the current-build export and a monotonic signal envelope in `noBackupFilesDir`, then log only revision/build/node/hash metadata;
- preserve exact return state after closing review UI.

Acceptance: comments survive restart, v1 route comments migrate to successor nodes, old-build comments do not silently attach to the new build, AI cannot approve, invalid evidence hashes are discarded, exported JSON validates against schema, and no storage permission is requested. Live submission must be readable only through debuggable `adb run-as`, reject inactive profiles or hash/build mismatches on the host, survive a missed logcat marker through startup pull, and never place comment text in logs.

### Phase 7 — Self-review and APK delivery

Execute the current runbook in `SKILL.md`; do not duplicate it here. During `gate4-standard-first`, a complete AI review accounts for all 28 nodes, 20 surfaces and 105 obligations on the Standard profile, reusing the designated evidence owner for each obligation rather than producing a duplicate PNG, hierarchy and Journey.

The delivery still binds one exact APK to build ID, R1 report, AI `PENDING` draft, selected Journey traces, review export/recovery state, permission check and installed-APK byte comparison. E-ink artifacts remain frozen and are absent from routine completion counts until the explicit restoration pass.

Deliverables:

```text
prototype/ui-atlas/build/outputs/apk/debug/ui-atlas-debug.apk
build/interactive-prototype/<buildId>/verification.json
build/interactive-prototype/<buildId>/smoke-traces/
build/interactive-prototype/<buildId>/sample-review-export.json
```

## 12. Self-review closure and final boundary decisions

The plan was counter-reviewed against local-first execution, truthful mutation states, E-ink behavior, accessibility, fixture isolation and immutable-review lineage. The user resolved every material boundary:

| ID | Final decision | Plan consequence |
|---|---|---|
| `IP-01` | **Atomic JSON dual storage.** | Fake product data and review records use separate versioned `AtomicFile` snapshots in `noBackupFilesDir`. Both survive relaunch; fake-data reset and comment clear remain independent. No Room or DataStore. |
| `IP-02` | **Compact review affordance.** | A debug edge control is available on ordinary routes without materially covering product content, hidden during capture and while Reader immersive chrome is absent. Standard uses a scrollable M3 sheet; E-ink uses an opaque full-screen page. |
| `IP-03` | **Stable Review Graph nodes.** | Catalog version 2 owns 28 stable nodes. Each node has one comment, independent progress timestamps, verdict, author and separate visual/interaction evidence hashes across route states/profiles. |
| `IP-04` | **Experimental Reader seek-preview.** | Implement it for interaction evidence, retain semantic locator and single-commit assertions, and require physical-device human approval for presentation, ghosting and gesture comfort. |
| `IP-05` | **Complete route-state scope.** | The debug APK covers 20 route/surface rows and 105 route-state obligations. A–H remains a fast visual-direction smoke set and never substitutes for full coverage. |
| `IP-06` | **AI advisory, human authority.** | AI may operate the APK, attach evidence and write a pending draft. Only a human verdict may set `humanReviewedAt` or `approvedAt`; automation can be paused and handed off in the same state. |
| `IP-07` | **Gate 4 Standard-first review freeze.** | Until the Gate 4 Standard UX milestone is complete and the user explicitly resumes E-ink work, routine design/review executes only Standard. All E-ink implementation, contracts, fixtures, node obligations and inventory remain intact and deferred. Restoration reconciles every accumulated change since the frozen build ID and runs the complete retained E-ink design/review scope; no E-ink readiness is implied during the freeze. |
| `IP-08` | **ADB live human-review bridge.** | Manual SAF/share transfer is fallback, not the routine same-host path. Explicit in-app submission writes a current-build JSON payload plus a monotonic metadata-only signal in private no-backup storage. A host watcher validates policy, schema, build identity and SHA-256 before emitting an OMP event; it never infers approval or installs onto the human-review AVD without the batch-ready signal. |

Resolved cross-cutting safeguards:

- storage: the current Atlas no-file rule must receive a narrow interactive-debug exception before runtime implementation; production storage remains forbidden;
- review lineage: comments are namespaced by deterministic `buildId`; old-build comments are read-only and excluded from export by default;
- simulation honesty: remote, verification, updates and import paths carry explicit debug simulation marking and never execute network/source/credential code;
- privacy: comments are never uploaded, backed up or logged; the live bridge logs metadata only, reads private payloads through debuggable `adb run-as`, and stores host copies under ignored `.local/`; export/share remains explicit fallback and requests no storage permission;
- code lifetime: all runtime/reviewer code remains in the single prototype module and is deleted under the existing extraction protocol rather than copied into production.

## 13. Stop conditions

Implementation stops if any of the following appears:

- production module dependency or import;
- `INTERNET` permission, real source execution, credential access or production data access;
- a new visible product behavior absent from the active contract;
- an inert visible primary action presented as complete;
- comment persistence not bound to build/target identity;
- E-ink continuous animation or color-only state;
- review UI prevents exact return to the reviewed context;
- fake-data reset deletes comments or comment clear mutates fake product data;
- APK export cannot be parsed and schema-validated.
