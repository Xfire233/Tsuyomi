<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Gate 4 plan — foundational UX and remaining authorized writeback

## Status and review input

- Planner status: **DRAFT FOR DESIGNER / ADVISER REVIEW**
- Implementation authorization: **NOT GRANTED**
- Planning branch / input head: `docs/gate4-manual-device-intake` / `9fd8c50358d0105d32246fb41375ecab6365c43f`
- Product baseline: `org.tsuyomi.android` `0.2.0` / versionCode `2`; Host API `1.1.0`; HXP manifest v1; Room schema `2`
- Gate 3 baseline: `d3e335a11565ae79e15d374062db637f3f9979d9`; dual-portrait evidence rule: `26bad358ab2ef4afac01b63b30e6c6c3e6de9c1c`
- UI impact: **YES** — every root and task path, book details, Reader entry, library/collections/history, source and remote-library surfaces, transfer, settings, shared UI semantics and goldens.
- Security-sensitive impact: **YES** — Gate 4B adds separately signed/capability-gated remote remove/move and remote target selection. Gate 4A must preserve the existing host-only credential, direct-action, reconciliation, redirect and cookie boundaries.

Gate 4 has two ordered checkpoints. **4A is foundational UX**: it repairs the current page model and every confirmed task-flow gap before remote writes are expanded. **4B is the original roadmap scope**: remaining authorized remote writeback. Gate 4 does not begin production implementation until independent Designer and Adviser approvals bind this exact plan input, after which explicit user implementation authorization remains required. A scope, protocol, route, persistence or risk-model change invalidates the affected approval.

## Outcome

A local-first reader whose standard user tasks are direct, truthful and recoverable: one book always means one detail surface; organization, tags, ratings and reading work from the point where users expect them; navigation returns to the precise prior context; every change is visibly acknowledged; all core flows remain accessible and equally usable in Standard and E-ink. Only after that foundation is proven may a user explicitly authorize remote remove/move against a signed source policy—never automatically, in the background or bidirectionally.

## Scope partition and non-goals

### Checkpoint 4A — foundational UX

1. Canonical stable-identity book detail, precise navigation graph and Back/Up/root-stack contract.
2. Complete local library operations promised by Gate 3: system views, sorting, collection lifecycle and many-to-many membership.
3. Full mutation-state feedback for local and existing remote-add actions.
4. Search/history/continue-reading, chapter/Reader entry, source/dormant/offline recovery and remote-library information architecture.
5. Transfer/report information architecture, settings discovery and shared accessibility/E-ink/adaptive behavior.
6. A complete task-flow product review in addition to code/design review.

### Checkpoint 4B — remaining authorized writeback

1. Signed, separately granted `remove` and `move` operation contracts and an explicit signed remote target/shelf projection.
2. Per-source default-off remove/move settings, protected credential snapshots, one direct user command per operation and durable reconciliation.
3. No implicit selection, no remote folder creation/deletion, no automatic/bidirectional/background synchronization, and no remote mutation on local import, source install, login, pull, retry timer or local remove unless the user explicitly chooses the corresponding remote operation.

### Explicit non-goals

- Gate 5 sources (ESJZone, Yamibo), official extension catalogue, production publisher keys, source subscription execution, local EPUB/TXT, TTS, cloud/account sync, telemetry, crash reporting, remote feature flags, vendor E-ink SDKs and physical waveform claims.
- Copying Flutter or comparison-project code, brand, visual identity, GPL/AGPL implementation, credentials, cookies, caches or private content.
- Gate 4B remote folder creation/deletion/reordering, remote delete as a side effect of local removal, remote membership mirroring, automatic pull, scheduled work or conflict “auto-resolution”.

## Evidence base and product principles

Tsuyomi’s current code audit identified duplicate book routes, missing manual-membership UI, silent mutations, incomplete system-library/sort/transfer contracts, generic remote state, E-ink pagination gaps and semantic/accessibility gaps. The fixed Flutter reference `Xfire233/hikari_novel_flutter_plus` commit `a1feba6d1dd8dbbdd2b5ae042e44f2ec54d26bef` confirms the useful concepts of persistent roots, local fallback, detail-owned reading entry and display adaptations, but its hidden/overflow actions, ambiguous state feedback, dense settings and class-ID membership model are not adopted. Mature-reader behavior was independently compared against Mihon `0.20.4`, Kotatsu current source, LNReader `2.1.2`, and only a clearly-labelled historical Legado fork because the official Legado repository no longer contains product source. Links and evidence are recorded in `docs/architecture/UX_RESEARCH.md`.

1. **Task before screen.** A primary task must be available where a user naturally looks; core organization and reading never depend on an overflow menu, a prior screen’s stale state or an undiscoverable gesture.
2. **Identity before ownership.** Route identity is stable `BookIdentity`; local/source/remote ownership changes data availability and permitted actions, not which book page the user sees.
3. **One action, one truthful outcome.** Every mutation exposes `idle → working → success | recoverable failure | cancelled | unresolved`, prevents duplicate dispatch, uses persistent accessible feedback, and never calls a local change remotely synchronized without confirmed source evidence.
4. **Navigation is a contract.** Back is chronological transient-state/route reversal; Up is semantic parent traversal; root selection switches independently restorable stacks. They are specified and tested separately.
5. **Local-first is visible.** Room data, semantic progress and downloaded/local reader data remain useful when a source is dormant; source freshness, verification and remote capability are shown as provenance, not silently conflated with local truth.
6. **Same behavior, profile-specific presentation.** Standard and E-ink share route, state and persistence trees. E-ink changes only presentation: fixed chrome, explicit pagination, immediate replacement and persistent textual feedback.
7. **Progressive disclosure without concealment.** A compact detail prioritizes Read/Continue and organization. Secondary actions remain labelled and keyboard/TalkBack reachable; complex source/remote/security settings are grouped with clear summaries.

## 4A information architecture and navigation contract

### Canonical routes

Clean cutover removes `library/book/{sourceId}/{remoteBookId}`, state-only `source/detail`, and state-only `source/remote-library`. The route graph becomes:

```text
Library root (independent stack)
├── library/collections
├── library/collections/{collectionId}
└── book/{sourceId}/{remoteBookId}?origin={library|collection|history|search|remote}

Browse root (independent stack)
├── browse/source/{sourceId}/search
├── browse/source/{sourceId}/remote-library
└── book/{sourceId}/{remoteBookId}?origin={search|remote}

More root (independent stack)
├── more/display
├── more/reader
├── more/data
└── more/data/report/{sessionId}

Book detail (shared canonical stack node)
├── book/{sourceId}/{remoteBookId}/directory
└── book/{sourceId}/{remoteBookId}/reader/{chapterId}
```

Only stable IDs and a bounded caller-return token/context may enter a route. `origin` controls return destination and analytics-free UI wording only; it cannot select a source, capability, credential or book. Controller snapshots can restore source work but cannot retarget an already-addressed route. The host resolves current Room state first, then adds verified source detail/directory data when source ID, package generation and capability match. It never blocks local detail on a source request.

### Back, Up and root selection

| Input | Required behavior |
|---|---|
| System Back | Close modal → exit selection/edit mode → dismiss inline search/chapter drawer/settings sheet → pop current route → return to its explicit caller context. At a root, follow Android task policy; do not synthesize old nested routes. |
| App-bar Up | Go to the semantic parent: Reader/Directory → canonical detail; detail → its recorded caller list/root; collection editor → collection manager; report → data. It never behaves as “switch Browse”. |
| Bottom/rail root item | Switch to that root’s independently saved stack and UI state. Re-selecting an active root returns its stack to its root only through a labelled, deliberate command; it never changes another root’s stack. |
| Precise cross-root action | `查看本书来源详情` carries the current identity and creates/restores that detail, preserving a Library return context. `浏览内容源` explicitly opens source root/search. No action may use generic `saveState` restoration to fulfil a book-specific request. |

Every stack saves bounded list query, selected collection, sort, selection mode and position (or E-ink page); no scroll offset/page is reader progress. Process recreation restores only valid stable identifiers and drops invalid ephemeral UI safely.

### Canonical detail and Reader

The detail hierarchy is: title/author/source provenance/dormancy → primary `继续阅读` or `开始阅读` → local status (in library, progress, rating, tags, memberships) → source metadata and chapter entry → secondary source/remote actions. Continue resolution is deterministic: exact semantic locator → first unread source chapter → first chapter; unavailable content produces an explanatory disabled state and retry/use-local action, never a dead button. The directory is an explicit secondary destination; it may show chapter state, local availability and current chapter. Reader is given explicit book/chapter identity and returns to the same detail; its internal drawer/search/settings consume Back before Reader leaves.

### Library, collections, search and history

- Library has discoverable All, Continue, Recent, Unread and Dormant system views; local title/author/normalized-tag search; all Gate 3 sorts (title, author, added, last read, metadata update, progress, rating, source) with stable identity tie-breaking; contextual count/empty/error/retry state.
- Empty library primary action is `浏览并添加书籍`; collection management is secondary. Collection chips/tabs preserve the chosen collection and clamp/reset an E-ink page when the result domain changes. Long chip/filter rows wrap, scroll or become a picker—never clip at `fontScale = 2.0`.
- Manual collections support create, rename, explicit 48dp up/down reorder, presentation-only nesting/reparent with cycle protection, delete/reparent confirmation and a collection-detail list. Smart collections explain their rule and are read-only membership views; system collections are immutable.
- Both directions expose manual membership: book detail `管理所属集合` displays every permitted manual collection, current membership and multi-select save; manual collection detail `管理书籍` searches/selects local books. Membership writes are one Room transaction, deduplicated, and report actual changed/unchanged values.
- Library selection mode is explicit, count-labelled and keyboard/TalkBack operable. Back clears selection before route/root traversal. Gate 4A limits bulk actions to safe local operations (membership, local remove, local tags/rating when semantically unambiguous); source/network-heavy operations remain explicit per book until independently specified.
- Local search history and browsing history become a user-visible host-owned History surface: source-scoped search suggestions only when its source is installed; grouped recent reading; explicit resume; per-item and clear-all removal confirmations. Gate 4A does not introduce a stealth “incognito” state—if history pause is added, it must be an explicit persisted/readable setting and a separately reviewed scope change.

### Source, dormant/offline and remote-library surfaces

- Browse identifies installed source, installed version, capability status and the primary search/discovery action. Import/approval/failure content scrolls at large text and preserves existing source activation until a new verified package is approved.
- Source data on canonical detail distinguishes current, cached/stale and unavailable. Dormant books retain local title, membership, tags, rating, semantic progress and locally available chapters; source actions state why they are unavailable and offer `浏览内容源` only when that is the real destination.
- Every long source list (search, directory, remote favourites) uses a shared Standard scroll/E-ink explicit-pagination policy, stable `BookIdentity`/chapter keys and row button semantics. Source errors distinguish retryable network, offline-cache, login/verification and malformed-source outcomes without raw secrets/HTML.
- Remote-library route is source-ID-addressed. It shows signed read/add/remove/move capability/grant, credential-ready and source availability state before actions. Pull is user-confirmed, manual and cancellable, with page/item count, bounded-progress, empty/success/partial-or-failure semantics; it never starts due to route entry, login return, root change or restoration.

### Mutation, confirmation and feedback policy

Define shared, screen-independent `MutationUiState` and accessible `MutationResultBanner` in `core:ui`. Every affected command owns a keyed state machine; state is restored from durable truth after recreation where appropriate, while non-durable visual working state safely resolves to re-query/retry.

| Command class | Working and duplicate prevention | Completion feedback | Confirmation |
|---|---|---|---|
| Tags, rating, manual membership, collection edit | Disable only the scoped command and preserve editable drafts | Persistent inline status, normalized/read-back value, polite live region, safe retry | No confirmation for reversible single-field edit |
| Local remove / destructive local bulk action | Block repeat; preserve original list context | Clearly state what remains (progress/history/local metadata) and what does not happen remotely | Required before local removal |
| Source search/pull/read | Explicit working/count/cancel state; no accidental duplicate network operation | Typed result, source/provenance and retry/cache/verification action | Pull confirmation when it changes local library membership |
| Remote add/remove/move | Direct-action token accepted before transport; scoped disable; durable reconciliation is truth | Confirmed only on typed `applied`/`already-present`; otherwise persistent cancelled/unresolved with explicit retry | Required and source/website effect/target-specific before enable or destructive mutation |
| SAF transfer | Picker, review, apply/recovery and report remain distinct | Redacted durable report and reopen route | Import confirm after review; abort/destructive cleanup confirm when allowed |

`InfoBanner`/`InlineStatus` must gain an appropriate success/error live-region contract or a new semantic equivalent; transient Snackbar/toast/animation/color alone never proves completion. Result text names the affected book/collection/source safely, never leaks credentials, raw pages, cookies or diagnostics. All destructive dialogs make the default safe/cancel action accessible.

### Settings, transfer and adaptive foundations

- More becomes a grouped entry point for Display, Reader and Data. Reader settings are an explicit `more/reader` route, grouped by typography/layout, navigation/progress and effective E-ink constraints. They show persisted versus effective value; setting dependencies cannot create impossible combinations.
- Data becomes `more/data`: separate `导入 Tsuyomi 数据`, `从 Hikari Novel 导入`, `导出 Tsuyomi 数据` and `查看最近导入报告`. Review shows format, bounded file state, merge/conflicts/warnings and sensitive-data exclusion before mutation. Completion offers an accurate result and persisted report; recovery gate remains above ordinary navigation.
- Shared layouts use window-size classes rather than orientation alone. Compact, split-screen, landscape phone and ≥600dp pane variants preserve the same tasks, focus, selected state and route. Detail/forms scroll safely; no core action is beyond the viewport or gesture-only.
- All icon-only controls have localized descriptions; rows declare button/selected/disabled/expanded state; headings, lists, collection membership, pagination, reader progress and mutation results have semantic values. Test TalkBack, keyboard/DPAD and 48dp targets. Font scale 2.0, system insets and physical keyboard never hide the last action.

## 4B authorized remote writeback plan

### Contract and security model

4B extends—not generalizes—the existing signed remote operation policy. Protocol and parser introduce explicit `RemoteOperation.REMOVE` and `RemoteOperation.MOVE`, each with an exact signed HTTPS method/path/referrer/parameter grammar, declared exact redirect aliases and operation-specific typed host bindings. Remote targets are returned only by a signed read/list operation as bounded `(targetId, displayName, parentId?, kind)` data; target ID is opaque and is never extension-chosen during mutation. A move policy requires exactly one host-bound `remoteBookId` and one host-bound `remoteTargetId`; remove requires only the requested book identity. Operation methods and permissible parameter kinds are defined per operation in protocol tests before Android parsing.

Remote policy fingerprints, package grant diffing and protected transport surfaces include read/add/remove/move policies and aliases. A new operation, target grammar/origin/redirect/publisher-key change disables only affected persisted writeback setting(s) pending explicit reauthorization. Generic/read/add context cannot access remove/move surfaces, and each nonmatching signed context rejects their aliases hop-by-hop before transport.

Each remote command requires all of: verified active matching package/generation, signed operation policy and grant, the operation’s own default-off user setting, protected credential snapshot bound to that policy origin and credential revision, a visible direct user action/confirmation, a host-minted single-use token bound to source/book/operation/target/package/fingerprint/generation, and a durable operation-specific reconciliation row before network acceptance. One book-level mutex serializes conflicts. `NonCancellable` cancellation cleanup revokes unaccepted tokens and records the terminal durable state before rethrowing cancellation.

### User operation semantics

- **Remote remove:** never triggered by local `移出书架`. It is a separate explicit `从网站移除` action with source/website effect and local-retention choice made clear. Default is leave local library untouched; any optional local removal is a separately confirmed local transaction after remote outcome, never an implicit mirror.
- **Remote move:** first opens a source-labelled target picker populated by an explicit read. It explains website effect, leaves local collection membership unchanged by default, and disables stale/unavailable/unauthorized targets with explanation. The selected opaque target is shown again in final confirmation.
- **Retries:** `CANCELLED`/`UNRESOLVED` cannot auto-retry. A new explicit retry creates a new token/reconciliation from fresh Room/policy/credential/target state. Remote success is typed idempotent outcome; ambiguity never displays completed. Local data and semantic progress survive every remote failure/revocation.
- **No target discovery means no move.** The UI fails closed and offers no arbitrary text field, guessed folder, inferred default or hidden source-selected target.

### Persistence and migration

Room schema `2 → 3` replaces add-only reconciliation with operation-kind/target snapshot fields (or a normalized operation table), preserves historical add rows as `ADD`, and adds per-source/per-operation enabled settings. Migration is atomic, exports schema, preserves local books/progress/tags/memberships, and leaves remove/move false. Transfer/Hikari import/export still excludes all capability grants, writeback settings, remote target IDs, reconciliations, credential revisions and source state. Existing add behavior retains its current enabled state only when its unchanged signed policy remains preserving; no migration enables a new operation.

### 4B source fixture and protocol scope

The acceptance source remains an updated public Wenku8 fixture only after protocol/manifest review. It must implement deterministic list-targets/remove/move responses including applied/already-present, pre-accept cancellation, ambiguous terminal failure, declared success aliases, unauthorized target and source-change cases. Live Wenku8 remains anonymous best effort and never an authority for deterministic acceptance. No third-party source is granted inferred write capability.

## Components and clean-cutover order

| Order | Component(s) | Work and invariant |
|---:|---|---|
| 1 | `core:ui`, `core:display` | shared mutation/result semantics, list/pagination/row semantics, adaptive state restoration; no feature-local duplicate feedback components. |
| 2 | `core:database`, `shared:source-contract`, `source:extension-manager`, app navigation state | 4A stable detail projection, collection/history/sort/query persistence and source-safe route resolver. For 4B, protocol-first remove/move/target DTO, schema 3 and host operation context precede UI. |
| 3 | `feature:library`, `feature:book`, `feature:search`, `feature:reader`, `feature:browse` | canonical detail, library/collection/history tasks, source/remote capability surfaces and Reader-parent contract; remove obsolete local/source detail routes. |
| 4 | `feature:settings`, `feature:backup`, app root | grouped More, reader/data/report routes, recovery preservation, precise root-stack switching and route restoration. |
| 5 | `tsuyomi-protocol`, `tsuyomi-extensions`, Android fixture trust | only after 4A review locks UX route/operation names; signed 4B schema/fixture/package, deterministic tests and no release trust key. |

The implementation is deliberately split into reviewable, revertible commits: (1) shared UX state/semantics; (2) canonical route/detail cutover; (3) library/collection/history completion; (4) source/Reader/remote/transfer/settings completion; (5) 4A goldens/instrumentation/portrait evidence; (6) 4B protocol/schema/fixture; (7) 4B controller/UI/reconciliation; (8) 4B adversarial and device evidence. Each cutover migrates all callers and deletes obsolete routes, snapshots and duplicate screens in its own change; compatibility aliases or permanent routing shims are prohibited.

## Risk register and mitigations

| Risk | Source fix / guard |
|---|---|
| Canonical detail loses caller context or opens wrong book after recreation | stable IDs plus bounded caller token; no controller-selected object route truth; route/restore instrumentation and process-recreation tests. |
| Local detail blocks on untrusted/slow source | Room-first projection and source enhancement as cancellable overlay; dormant/offline tests. |
| Mutation result lies or duplicate operation runs | shared keyed state plus repository reload; scoped mutex/token/durable reconciliation; test repeated clicks/cancel/process death. |
| Larger UX scope regresses signed writeback protections | keep 4A local/source read actions separate; 4B protocol-first independent security review; no UI handler can build a network request. |
| Remove/move target substitution or redirect escalation | immutable host contexts with source/book/operation/target binding; exact policy/fingerprint; protected-surface check on every hop; adversarial fixtures prove zero transport calls. |
| Remote ambiguity deletes/moves local state | local state independent; all uncertain terminal states remain unresolved; only user retry resolves. |
| Large text/E-ink introduces unreachable controls | scroll-safe layouts, shared PaginationBar, no gesture-only essential action, semantics/golden/device matrix. |
| UX review becomes subjective screenshot review | task matrix with step-level expected route/state/feedback and independent product reviewer evidence. |

## 4A acceptance matrix

| Task | Required proof |
|---|---|
| Canonical detail | Search, Library, manual/smart collection, History and remote favourites open equal `BookIdentity` detail; no duplicate surface; source unavailable preserves local state; process recreation never retargets identity. |
| Reading flow | Detail exposes Continue/Start; locator/first-unread/first-chapter precedence works; directory and Reader Back/Up return to the exact detail/caller state. |
| Organization | Create/rename/reorder/nest/delete manual collection; multi-membership from book and collection; smart/system edit restrictions; all sorts/system filters/query/selection and E-ink page reset. |
| Mutations | Tags/rating/membership/add/remove/collection mutation success, controlled failure, cancellation and rapid repeat show truthful persistent outcome, retry where valid, normalized data and live announcement. |
| Source/remote | install/approval/cancel/failure; search/list pagination; offline/dormant behavior; remote credential/grant absent; explicit pull prompt/cancel/count/empty/failure/retry; no automatic network/write. |
| Transfer/settings | picker cancellation; separate import formats; bounded review/confirm/recovery/report; export outcomes; display/reader settings retention and effective E-ink explanation. |
| Accessibility/adaptation | TalkBack, keyboard/DPAD, 48dp, `fontScale=2.0`, portrait/landscape/split/≥600dp; Standard and E-ink share task result. |

## 4B acceptance matrix

| Task | Required proof |
|---|---|
| Admission | missing/revoked operation grant, policy, active package/generation, credential revision, user setting, direct action or target prevents token/transport and disables/explains UI. |
| Remove | final confirmation; typed applied/already-present only confirms; local remove alone emits zero remote call; cancellation/unresolved persists and explicit retry uses fresh state. |
| Move | explicit target list and final target confirmation; hidden/stale/unauthorized target, wrong binding, generic context and redirect alias attempts fail closed before transport. |
| Lifecycle | source update/removal/credential loss during each phase preserves local state, disables setting, revokes token, writes durable terminal reconciliation and never claims success. |
| Migration/privacy | schema 2→3 preserves records; remove/move false after upgrade/import; transfer/backup/logs never carry target, token, credential, policy grant or reconciliation secrets. |

## Verification and evidence requirements

Automated proof includes feature-level semantic/UI tests, navigation and process-recreation instrumentation, Room migration/query/reconciliation contracts, extension/runtime/network adversarial tests, screenshots/goldens in all affected components, and the normal Android/protocol/extensions/REUSE/artifact-policy gates. Tests assert observable contracts, not implementation text.

Every 4A/4B target head separately passes the mandatory portrait flow on both baselines: `Tsuyomi_API29` (`1080×2400`, 420dpi, forced Standard) and `Tsuyomi_EInk_API29` (`1264×1680`, 240dpi, forced E-ink). Each record includes target HEAD, `wm size`, `wm density`, orientation, profile, `font_scale`, user-flow result and screenshot SHA-256. Landscape, split-screen, Layoutlib and a profile switch on one device supplement but never replace these records. Physical E-ink tests remain required before release-ready waveform/ergonomic claims.

Before implementation: independent Designer reviews the 4A/4B information architecture, task flows, accessibility and profile behavior; independent Adviser reviews module/persistence/protocol/security/lifecycle/concurrency/test plan. Before merge: both re-review affected final head; all P0/P1 findings close with source fix, regression proof, reusable rule and reversible commit. User explicitly authorizes implementation, PR creation and merge separately.

## Independent review closure and user decision register

### Review conclusions bound to this draft

| Review | Verdict | Blocking amendments |
|---|---|---|
| Product / UX | **APPROVE WITH CHANGES** | Add a concrete History home/route; restore or explicitly defer smart-rule editing; define precise cross-root wording, active-root reselect, local-removal retention, post-login prompt and 4B action placement; register all audited UX defects. |
| Architecture / security | **REJECT** | Define stable canonical-detail lifecycle/caller ownership; version 4B protocol; persist per-operation authorization; define operation-specific result/recovery/conflict projections; bind data/privacy/remote-effect defaults to user approval; define schema 2→3 rollback. |

The reviewer reports are retained only in the private planning record; this public plan carries their evidence-backed requirements and decisions. The following entries are **not implementation authorization**. `PENDING` decisions block the affected 4A/4B work. After confirmation, the selected values replace these placeholders, affected sections receive a delta independent review, and only then may the user authorize implementation.

### Confirmed additional findings

| ID | Status | Problem / affected contract | Severity | Required regression tier |
|---|---|---|---|---|
| G4-UX-004 | READY | Library hides the existing `RECENT` filter, lacks all promised sort modes and does not clamp/reset E-ink page on selected-collection change. | P1 | Room query/sort contract; navigation/UI instrumentation; Standard/E-ink golden and portrait flow |
| G4-UX-005 | READY | Library has no explicit selection mode; local-detail/filter layouts can clip or leave actions unreachable at `fontScale = 2.0`; long source lists lack uniform explicit E-ink pagination and row semantics. | P1 | Compose semantics/layout tests; TalkBack/DPAD; goldens; both portrait flows |
| G4-UX-006 | READY | More/Data lacks separate Tsuyomi/Hikari import and report routes; More lacks the promised Reader settings route. | P1 | route/SAF/recovery instrumentation; persistent-report contracts; goldens; both portrait flows |
| G4-UX-007 | READY | Source remote-library has no stable source route, capability/credential/pull state taxonomy, count progress or stable identity list key; essential result/error announcements are inconsistent. | P1 | source lifecycle/remote pull tests; semantics; E-ink pagination; both portrait flows |
| G4-UX-008 | TRIAGED | History/resume surface is required by the intended local-first flow but has no root, route, entry point, retention policy or process-recreation contract. Pending D4/D8/D14. | P1 | route/recreation/history retention instrumentation; confirmation/UI semantics; both portrait flows |
| G4-UX-009 | TRIAGED | Smart collection rule create/edit, unsaved-change and readable-rule flows promised by Gate 3 have no explicit 4A disposition. Pending D7. | P1 | AST/editor/Back semantics; TalkBack/goldens; both portrait flows |
| G4-REMOTE-001 | TRIAGED | 4B remove/move/target protocol, grant receipts, reconciliation/recovery, result taxonomy, compatibility and schema rollback are not safely specified. Pending D5/D11–D18. | P1 | protocol/schema/runtime/network adversarial tests; API 29 migration/restart/rollback; fixture portrait flows |

### User decisions — UX and local-data behavior

| ID | Decision | Recommended default | Material alternatives | Scope |
|---|---|---|---|---|
| D1 | Supersede Gate 3’s two-detail route model with one canonical stable `book/{sourceId}/{remoteBookId}` page. | **Approve unification.** Host-composed Room-first detail with source enhancement; no visual or route-level dual detail. | Retain two surfaces, or only visually unify while keeping duplicate routes. Both retain the reproduced G4-UX-001 defect. | 4A |
| D2 | Re-selecting the active bottom/rail root. | **Pop that root’s own stack to its root.** Other roots retain their stacks. | Do nothing plus a named root-reset action in the app bar. This needs a separately specified label/placement/announcement. | 4A |
| D3 | Exact local-retention contract for `移出书架`. | **Delete library membership, manual memberships, rating and local tags; retain book metadata, semantic progress and browsing history.** Re-adding restores progress; confirmation says no website action occurs. | Full local erase including progress/history; or retain rating/tags too, requiring a different data model and explicit privacy semantics. | 4A |
| D4 | Reading a book that is not in the local library. | **Record host-owned browsing history and semantic progress without adding the book to Library.** No source/network write follows. | Require 加入书架 before reading; or store history without resumable progress. | 4A |
| D5 | 4B remote remove/move entry points. | **Canonical detail only**, in a labelled source/remote action section; per-book, visible-disabled-with-reason when unavailable; never bulk, gesture-only or duplicate remote-list actions. | Also place actions on remote-library rows or selection mode; both multiply high-risk action surfaces. | 4B |
| D6 | Once-only post-login import prompt. | **Retain** `现在导入` / `暂不导入`; confirmation, not login completion, starts a pull. | Remove prompt and expose only manual pull in remote-library. | 4A |
| D7 | Smart collection rule create/edit in 4A. | **Ship create and edit** at `library/collections/{collectionId}/rule`, including validation and unsaved-change Back confirmation. | Explicitly defer with a Gate 3 supersession note; create-only remains insufficient. | 4A |
| D8 | History placement. | **`library/history`**, entered through a labelled Library app-bar action; Standard scroll/E-ink explicit pages; Back/Up return to Library. | Fourth root (breaks 3-root shell) or More (buries frequent resume task). | 4A |
| D9 | Precise cross-root action label/destination. | **`在来源中打开本书`** opens the same addressed canonical detail on Browse’s stack while preserving the Library stack; disabled with explanation for dormant source. | `查看本书来源详情` implies a second detail; `前往浏览` describes only root switching and is the current defect. | 4A |
| D10 | 4A library bulk-action boundary. | **Local-only:** collection membership, and only semantically unambiguous local metadata/remove actions. Source/network operations stay explicit per book. | Bulk source refresh/write actions; require separate source-load and security specification. | 4A |

### User decisions — privacy, remote effects and compatibility

| ID | Decision | Recommended default | Material alternatives | Scope |
|---|---|---|---|---|
| D11 | 4B compatibility boundary. | **HXP manifest v2 + Host API 1.2.0.** New packages require 1.2; old v1 read/add packages remain usable on new host; new packages are rejected by old host before extension evaluation/transport. | Extend manifest v1; rejected because its signed schema/parser defines only read/add and cannot safely express new policy/binding semantics. | 4B |
| D12 | Remote target discovery and move scope. | **Explicit signed list-target operation; opaque target IDs; no free text/default/guessed target; no remote folder CRUD.** | Let extension choose a target or accept user text; rejects host validation and fail-closed target binding. | 4B |
| D13 | Operation-specific authorization persistence. | **One receipt per `(sourceId, operation)`** with enabled value, publisher, operation-policy fingerprint, origin, package/manifest generation and approval revision; add migrates only if exact receipt matches; remove/move false. | A shared source fingerprint/flag; cannot preserve unaffected operations safely. | 4B |
| D14 | History data policy. | **No new tracking category; host-local history/progress only; per-item and clear-all confirmation; exclude history from transfer unless its inclusion is explicit in export review.** | Always export history, or omit it permanently; each changes portability/privacy expectations. | 4A |
| D15 | Remote remove/local data coupling. | **Remote remove never deletes local library data and UI offers no coupled delete option in Gate 4.** Local removal remains a later independent action under D3. | Offer a combined remove option; couples two failure domains and needs another confirmation/reconciliation model. | 4B |
| D16 | Remote target/reconciliation retention. | **App-private Room stores opaque target ID plus bounded redacted display snapshot; exclude from transfer, export and logs; clear with source removal/explicit data erasure; retain unresolved evidence until user resolves/erases source.** | Persist richer target metadata or export it; expands privacy/portability surface. | 4B |
| D17 | Unresolved remote-operation policy. | **Block later remote mutations for that book while any operation is PENDING, IN_FLIGHT or UNRESOLVED; only explicit retry/reconciliation for that row is permitted.** | Allow a different operation despite unresolved state; risks contradictory website state and hidden audit rows. | 4B |
| D18 | Room schema 2→3 rollback. | **Forward-only schema 3; rollback build retains schema-3 reader but disables Gate 4B UI/transport.** Preserve local books, memberships, progress, history and unresolved evidence; no remote replay. | A tested non-destructive 3→2 downgrade; higher implementation/migration risk but allows old binary rollback. | 4B |

### Mandatory post-decision technical amendments

Once the corresponding decision is confirmed, the plan must make these objective additions before delta review:

1. Add `library/history`, `more/about`, and—if D7 accepts—`library/collections/{collectionId}/rule` to the canonical graph; name their entry/empty/Back/Up/recreation behavior.
2. Place directory/Reader inside the caller’s root stack; persist typed bounded caller-list arguments in its back-stack `SavedStateHandle`, encode arbitrary Unicode/reserved IDs safely, and fall back only to that origin root’s list when the caller is invalid.
3. Create a `BookIdentity`-keyed detail lifecycle owner outside any root route; capture identity, package digest/version, source generation and cache/credential revision before source work; cancel/revalidate before every Room/UI commit.
4. Define 4B result unions: ADD `applied|already-present`; REMOVE `applied|already-absent`; MOVE `applied|already-at-target`, each echoing exact identity and MOVE target. Raw HTTP success, wrong IDs/outcomes and success redirects never confirm reconciliation.
5. Define operation phases and startup recovery: unaccepted/recovered orphan PENDING → CANCELLED; accepted IN_FLIGHT cancellation, timeout, source change, process death or lease loss → UNRESOLVED; only validated typed result → CONFIRMED. Project reconciliation by operation (and MOVE target), never by one latest row per book.
6. Replace generic schema-revert language with D18’s chosen compatibility contract and API 29 upgrade/feature-disable/re-upgrade evidence.

## 本轮手工输入基线

| 字段 | 固定值 |
|---|---|
| target head | `26bad358ab2ef4afac01b63b30e6c6c3e6de9c1c` 或测试时展示的实际构建 revision |
| device | `Tsuyomi_API29`，API 29，`1080×2400` portrait，420 dpi |
| profile | forced Standard |
| font scale | 1.0；发现文字、布局或焦点问题时重测 2.0 |
| app | `org.tsuyomi.android`，debug `0.2.0` |
| fixture | `/sdcard/Download/wenku8-fixture.hxp`，仅作脱敏验收数据 |

## 手工验收流

1. 从空书架进入“浏览”，选择“导入内容源包”，在 DocumentsUI 选择 `wenku8-fixture.hxp`；核对安装说明并确认安装。
2. 选择“进入内容源”，搜索 `fixture`，打开 `雾港纪事`，检查搜索结果、详情、目录与章节 `第一章 雾中的灯塔` 的可见性、触控和返回栈。
3. 在书籍详情执行本地加入、标签/评分/集合操作；在页面中观察反馈、禁用状态、文本换行、滚动、底部导航与返回行为。
4. 在“更多”中检查数据迁移与备份入口；取消 DocumentsUI，再次进入，确认应用没有误报成功、没有卡死或丢失当前路由。
5. 在“设置”强制切换 Standard 与 E-ink，再切回 Standard；确认已保存主题/动态色偏好恢复，E-ink 不夸大硬件刷新能力。
6. 旋转到横屏、回到竖屏，并将字体缩放设为 2.0；检查文字裁切/重叠、焦点可达性、系统栏与底部导航。横屏仅为附加覆盖，不能替代本表中的手机竖屏基线。

不要输入真实 Wenku8 凭证、验证码或私有内容；受控 WebView 的登录/验证仅检查取消、返回和错误恢复路径。

## 发现项登记与准入

每条收到的发现项都追加到下表，并在进入实现前补全严重度、受影响用户契约和回归层级。只有 `READY` 项可进入 Gate 4 实现计划。

| ID | 状态 | 设备/构建 | 复现步骤 | 实际结果 | 预期结果 | 证据 | 严重度 | 回归层级 |
|---|---|---|---|---|---|---|---|---|
| G4-UX-001 | READY | API 29 phone portrait / `0.2.0`；用户实机流与代码路径复核 | 从搜索结果打开 `雾港纪事`；加入书架后从本地书架打开同一书；点击“前往浏览/打开来源” | 搜索进入 source-owned `source/detail`，书架进入 Room-owned `library/book/{sourceId}/{remoteBookId}`；标题、操作和返回栈分裂。本地页动作只切换/恢复 Browse 根栈，实际进入来源浏览页，且可能恢复到进入来源前的旧 Browse 页面，不保证打开当前书；详情→目录→章节→Reader 增加重复层级 | 同一 `BookIdentity` 无论从搜索、书架、历史或远程收藏进入，都落到一个 canonical book detail。页面先显示本地缓存/书架状态，再按来源可用性增强元数据和目录；来源不可用时本地详情仍可用。目标写明“查看本书来源详情”时必须打开该书，不能恢复无关 Browse 历史；主要阅读动作不得要求经过重复详情页 | 用户在固定 phone portrait 基线稳定复现；`MainActivity.Routes.Detail`、`Routes.LocalBook`、`LocalBookDetailsScreen.onOpenSource`；`GATE_3.md` 294、301–305、428–430 | P1 | route/navigation instrumentation；Room/source race and dormant-source tests；统一 screen semantics/goldens；两套 portrait AVD 用户流 |
| G4-UX-002 | READY | API 29 phone portrait / `0.2.0`；用户实机流与代码路径复核 | 创建手动集合；将来源书加入本地书架；分别检查本地书籍详情和“管理本地集合” | 可以创建/删除手动集合，但没有任何可发现路径把已入书架的书加入或移出集合。数据库已有 `addManualMembership` / `removeManualMembership`，UI 没有调用入口 | 本地书籍详情提供可访问的“管理所属集合”，显示当前 membership 并允许多选保存；集合详情或编辑态也应提供从书架选择/移除书籍的对向入口。保存必须有持久结果反馈，智能集合不显示手动 membership 编辑 | 用户在固定 phone portrait 基线发现；`LocalBookDetailsScreen` 无 collection 参数/动作，`CollectionManagerScreen` 只管理集合本身，`RoomLibraryRepository` 已有 membership API；违反 `GATE_3.md` 305、438 | P1 | repository membership contracts；book/collection UI instrumentation；TalkBack/DPAD；两套 portrait AVD 双向发现流 |
| G4-UX-003 | READY | API 29 phone portrait / `0.2.0`；用户实机流与代码路径复核 | 在本地书籍详情输入标签并点击“保存标签” | 点击后没有 loading、禁用、成功、失败、标准化结果或可访问公告；即使 Room 写入成功，用户也无法判断操作是否发生，失败同样不可见 | 保存期间防止重复提交；成功后以持久 inline 状态显示“已保存”并回显 Room 标准化后的标签；失败显示安全、可重试错误。TalkBack 使用 live region；E-ink 不依赖短暂 Snackbar 或动画 | 用户在固定 phone portrait 基线稳定观察；`MainActivity.onSaveTags` 启动协程后没有 outcome state，`LocalBookDetailsScreen` 只保留无状态按钮；违反 `GATE_3.md` 296 与 `EINK.md` 65–66 | P1 | repository success/failure；Compose mutation-state tests；semantics/live-region；两套 portrait AVD 保存/失败流 |

### G4-UX-001 设计约束

- 使用稳定身份路由 `book/{sourceId}/{remoteBookId}` 作为唯一书籍详情入口；搜索、书架、历史和远程收藏只传稳定身份，不各自拥有详情页。
- host 组合一个详情状态：Room 中的本地书架、评分、标签、集合、进度与 reconciliation 是本地真值；已验证来源只增量提供可刷新的简介、状态和目录，不得让网络或扩展阻塞本地内容。
- dormant source 在同一页面内降级，保留本地信息与阅读进度，并把来源相关动作禁用/解释；不能跳到另一种“本地详情页”。
- `继续阅读` / `开始阅读` 是详情页主动作。目录可作为同页章节区或一个明确的次级目的地，但不得再经过第二个书籍详情；Back 必须回到调用方列表并保留其查询、滚动和筛选状态。
- source/local 的安全与生命周期所有权继续分离在状态层和 controller 层，不用重复页面或重复 route 表达内部边界。

## Gate 4 导航与操作逻辑审阅准入

Gate 4 的最终审阅必须把产品操作逻辑与代码正确性作为两个独立准入面。代码测试通过不能替代真实任务流审阅；reviewer 必须实际执行下面的 route/task matrix，并对每一次点击的目标、返回结果、状态恢复和动作反馈给出结论。

### 导航不变量

1. 同一领域对象只有一个 canonical detail route。不同入口可以携带来源上下文，但不能生成互不一致的详情页或动作集合。
2. 动作标签必须准确描述目的地。“查看本书来源详情”打开稳定身份对应的书；“浏览来源”才允许进入来源根页。任何跨 root 的精确动作不得通过 `saveState` / `restoreState` 恢复无关历史页面。
3. 系统 Back 按实际访问历史返回；应用栏 Up 按当前信息层级返回；底部导航切换独立根栈。三者不得互相冒充，也不得产生循环、跳过调用方或意外退出。
4. 从搜索结果或书架项目到开始/继续阅读最多经过一个统一详情；长目录可以是一个明确次级目的地，但不能再经过第二个书籍详情。
5. 返回调用方列表时保留查询词、筛选、集合、选中项、滚动或 E-ink 页码；来源更新、进程恢复和显示 profile 切换不能把用户送到另一个根页。
6. 每个用户可变更动作必须有 `idle → working → success/error` 的可观察状态。重要结果使用持久 inline/dialog 反馈和 TalkBack live region；不得只依赖无响应按钮、短暂 Snackbar、颜色或动画。
7. 双向关系必须可发现：书籍可以管理所属手动集合，手动集合也可以选择书籍。智能集合只解释规则结果，不伪装成可手动修改 membership。
8. 每个页面都要审计主动作、次动作、空态动作、错误恢复、取消、重复点击、跨 root 跳转和 Back/Up；不存在无目标按钮、死路、依赖旧栈偶然状态的跳转或只能靠猜测发现的核心操作。

### 必须执行的任务流

- 搜索 → 统一书籍详情 → 加入书架 → 管理集合/标签/评分 → 继续阅读 → Back 返回原搜索结果。
- 书架/手动集合/智能集合 → 同一书籍详情 → 查看本书来源信息或目录 → Back 返回原列表和原位置。
- 来源不可用 → 本地书籍详情降级 → 可执行本地动作 → 来源动作禁用并解释 → Back 不切换 root。
- Browse 根页、搜索、详情、目录、Reader 间连续 Back/Up；随后切换 Library/Browse 底部项，验证两套根栈各自恢复且精确动作不消费旧根栈。
- 每个创建、保存、加入、移除和重试动作分别验证成功、失败、取消、重复点击、进程重建及 `font_scale = 2.0`；Standard 与 E-ink 共用同一操作语义。

最终 review 记录必须包含逐步点击序列、每步预期/实际 route、Back/Up 结果、恢复的页面状态、Standard 与 E-ink portrait 截图证据，以及未通过项对应的回归层级。仅检查 composable、controller、repository 或测试覆盖率不足以批准 Gate 4。

状态：`NEW`（待复现）→ `TRIAGED`（已归类）→ `READY`（可实现）→ `FIXED`（修复和回归证明完成）→ `DECLINED`（不属于产品缺陷，说明理由）。

报告时至少提供：所在页面、操作顺序、是否可稳定复现、截图或录屏时间点，以及期望行为。可直接自然语言描述；维护者负责转写为表中的完整条目。