<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Phase 4 plan — foundational UX and remaining authorized writeback
> Terminology supersession: this work scope was formerly named Gate 4. It is now Phase 4; manual design approval and production implementation authorization remain separate gates/checkpoints.


## Status and review input

- Planner status: **FIXTURE REVISION REQUIRED — pending a fresh Standard manual Atlas review; E-ink design/review is frozen.**
- Implementation authorization: **NOT GRANTED — production feature work is blocked**.
- Product-visible authority: `UI_CONSTITUTION.md` Active constraint spine. `DESIGN_DIRECTION_HANDOFF.md` is review provenance/history; this Phase document owns scope, sequencing and risk boundaries; authorization is decided by the separate checkpoints below. D1–D18 remain historical unless the active spine expressly retains their product-visible consequence.
- Product baseline: `org.tsuyomi.android` `0.2.0` / versionCode `2`; Host API `1.1.0`; HXP manifest v1; Room schema `2`
- Phase 3 baseline: `d3e335a11565ae79e15d374062db637f3f9979d9`; dual-portrait evidence rule: `26bad358ab2ef4afac01b63b30e6c6c3e6de9c1c`
- UI impact: **YES** — every root and task path, book details, Reader entry, library/collections/history, source and remote-library surfaces, transfer, settings, shared UI semantics and goldens.
- Security-sensitive impact: **YES** — Phase 4B adds separately signed/capability-gated remote remove/move and remote target selection. Phase 4A must preserve the existing host-only credential, direct-action, reconciliation, redirect and cookie boundaries.

Phase 4 has three ordered work partitions. **Phase 4A is foundational UX**: it repairs the current page model and every confirmed task-flow gap before remote writes are expanded. **Phase 4B is the original roadmap scope**: remaining authorized remote writeback. **Phase 4C is the independent update inbox/schedule scope.** Production implementation does not begin until the UI Constitution/atlas manual approval gate, independent Designer and Adviser reviews, all applicable P1 closure, and separate explicit user implementation authorization checkpoint are complete. A scope, protocol, route, persistence or risk-model change invalidates the affected approval.

### Temporary Phase 4 profile sequencing decision — 2026-08-20

Until all Phase 4 Standard routes, tasks and UX states are implemented and human-reviewed, `STANDARD` is the only blocking design/review profile. This is the **Phase 4 Standard UX milestone**. It does not claim E-ink readiness.

All E-ink architecture, shared route/state/persistence invariants, fixtures, tests, Review Graph obligations and evidence identities remain intact but deferred. Routine work must not create new E-ink design decisions, run the E-ink device/profile matrix, update E-ink goldens or require E-ink approval. A direct E-ink source change may receive only the bounded compile/non-visual/launch exception defined by `review-policy.json`.

After the Standard milestone, E-ink resumes only by explicit user decision as a separate full restoration pass. That pass reconciles every accumulated change since build `3be8ea00cdac3ed7164fd170929bfb97a1c498cf63032fbbec4630ea5bd095d7` and restores the complete E-ink design, 28-node review, route-state inventory, adaptive/device matrix and physical human evidence. No prior E-ink approval carries forward automatically.

This section changes blocking order only. Where the remainder of this Phase says “both profiles”, “both portrait flows”, or equivalent, read it during the freeze as “Standard now; the unchanged E-ink obligation returns in the restoration pass.”

## Outcome

A local-first reader whose standard tasks are direct, truthful and recoverable: one book means one detail surface; local organization supports a bounded two-level folder model plus desktop-style manual arrangement; common reading workflows are default-created system nodes whose immutable rules can be hidden/rebuilt; navigation returns to exact prior context; every mutation and network job is visible. Phase 4 then adds two explicit network layers: an opt-in per-source website mirror/writeback model and a visible update-check coordinator with manual checking plus user-enabled scheduling. Neither layer hides network activity, credentials, conflicts or remote effects.

## Scope partition and non-goals

### Phase 4A — foundational UX, collections and onboarding

1. Canonical stable-identity book detail, precise navigation graph and Back/Up/root-stack contract.
2. Complete local library operations: system/root view, rule and manual sorting, desktop-style arrangement, direct membership, two-level manual folders, smart leaf collections, hide/rebuild system nodes and many-to-many organization.
3. Full mutation-state feedback for local and existing remote-add actions.
4. Search/history/continue-reading, chapter/Reader entry, source/dormant/offline recovery and remote-library information architecture.
5. Transfer/report, Reader/Display/Data/Help settings, lightweight contextual feature introductions, accessibility/E-ink/adaptive behavior and task-flow review.

### Phase 4B — authorized writeback and website mirror collections

1. HXP v2 / Host API 1.2 **write subset**: signed target-list, remove and move contracts with per-operation authorization, credentials, confirmation and durable reconciliation. D33 advanced search descriptors are deferred and are not a 4A or 4B prerequisite.
2. One explicitly enabled website-mirror root per source, showing that source account’s remote folder/content snapshot with read-only remote structure, manual full calibration and confirmed foreground writeback.
3. No background/periodic mirror pull, no remote folder CRUD, no implicit local↔remote membership mapping outside the mirror, and no remote mutation from import, source install, login, timer, generic local remove or non-mirror collection action.

### Phase 4C — update inbox and visible scheduled checks

1. A default-created, hideable/re-creatable `追更` system node opens `library/updates`; the node's query definition remains immutable, while a separate coordinator owns update network work.
2. Manual `检查全部更新` covers eligible local-library identities once; automatic checking is off by default and can be enabled for 12h/daily/3-day/weekly periods with explicit constraints.
3. Background work is visible through persistent in-app status and a cancellable system notification; source rate limits, exclusions, verification failures and partial results remain explicit.

### Explicit non-goals

- Phase 5 sources (ESJZone, Yamibo), official extension catalogue, production publisher keys, source subscription execution, local EPUB/TXT, TTS, cloud/account sync, telemetry, crash reporting, remote feature flags, vendor E-ink SDKs and physical waveform claims.
- Copying Flutter/comparison-project code, brand, visual identity, GPL/AGPL implementation, credentials, cookies, caches or private content.
- Remote folder create/rename/delete/reorder, hidden/open-page mirror pulls, scheduled mirror sync, local collection auto-mirroring, auto-resolving remote conflicts, minute/hourly high-frequency update polling, CAPTCHA/Cloudflare automation or silent background source access.

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
├── search?origin=library
├── library/history
├── library/updates
├── library/collections/{collectionId}
├── library/collections/{collectionId}/rule
├── library/tags
├── library/mirror/{bindingId}
└── book/{sourceId}/{remoteBookId}?origin={library|collection|history|updates}

Browse root (independent stack)
├── search?origin=browse&selectedSourceId={sourceId?}  ← root action 与 source-card「搜索此来源」共用同一 route/state；后者将隐式 scope 绑定为该 active source，不显示来源 selector
├── browse/source/{sourceId}/remote-library
└── book/{sourceId}/{remoteBookId}?origin={search|remote}

More root (independent stack)
├── more/display
├── more/reader
├── more/data
├── more/data/report/{sessionId}
├── more/help
└── more/about

Reader route (inside the invoking root stack)
└── book/{sourceId}/{remoteBookId}/reader/{chapterId}

Legacy directory deep links resolve to the chapter section of canonical Detail; no routine directory route remains.
```

This plan explicitly supersedes Phase 3’s separate source-owned and local-owned detail routes. Each root may hold its own **route instance** for the same canonical `book/{sourceId}/{remoteBookId}` destination, but every instance renders the same host-composed screen/state contract; there is no second detail implementation or source-selected route truth.

A typed host route encoder/decoder owns arbitrary Unicode, reserved characters and length bounds for `sourceId`, `remoteBookId`, `chapterId`, collection ID and session ID. The root-neutral `search?origin={library|browse}` route is pushed onto the invoking root stack and records that caller; it never creates a fourth root. A canonical detail entry carries a bounded `BookCallerContext` in that back-stack entry’s `SavedStateHandle`: origin root, caller route kind, stable source/collection ID where applicable, bounded query/sort/filter, list item identity and Standard list position or E-ink page. It never carries credentials, source output, cookies, raw URLs or extension state. Two opens of the same book from different callers remain distinct stack entries. Invalid/deleted caller context falls back only to the recorded origin root’s list; it never selects another root or restores unrelated history.

Reader is pushed onto the caller’s current root stack. A legacy directory deep link pushes/opens canonical Detail with a chapter-section anchor instead of creating a second screen. Context survives process recreation through stable bounded arguments; no global token map chooses a caller. Controller snapshots may restore cancellable source work but cannot retarget an already addressed route.

Canonical detail is owned by a `BookIdentity`-keyed state owner independent of Library/Browse back-stack owners. It renders Room-first state immediately. Before source work it captures identity, package digest/version, source generation, owner generation and applicable cache/credential revision; identity/source/owner change cancels the work, and the same lease is revalidated immediately before every UI or Room commit. A stale response performs no UI or database write. Source latency/failure never blocks local detail.

### Back, Up and root selection

| Input | Required behavior |
|---|---|
| System Back | Close modal → exit selection/edit mode → dismiss inline search/chapter auxiliary/settings sheet → pop current route → return to its explicit caller context. At a root, follow Android task policy; do not synthesize old nested routes. |
| App-bar Up | Go to the semantic parent: Reader or a chapter deep-link anchor → canonical detail; detail → its recorded caller list/root; smart-rule editor → collection detail; report → data; History → Library. If caller context is invalid after recreation, fall back only to that origin root. |
| Bottom/rail root item | Switch to that root’s independently saved stack and UI state. Re-selecting the already-active root pops **that root only** to its root destination; every other root stack remains untouched. |
| Precise cross-root action | `在来源中打开本书` addresses the current `BookIdentity`, selects Browse and pushes the same canonical detail onto Browse’s stack while preserving Library. It is disabled with a persistent dormant-source reason. `浏览内容源` explicitly opens the source root/search instead. Generic `saveState` restoration never fulfils a book-specific request. |

Each root saves its bounded list query, selected collection, sort/manual-order mode, selection mode and position/E-ink page. The aggregated search entry saves the bounded query draft, route-owned effective source scope, aggregate session state and result position in its own back-stack owner; internal per-source cursor/status may be restored by the coordinator but is not a persistent UI lane. It never persists credentials, raw responses or extension state. Reader progress remains only the semantic locator; no rendered Reader offset/page is persisted as progress.

### Canonical detail and Reader

The detail hierarchy is: title/author → primary local reading/progress/rating/tag actions → source metadata only where needed → integrated full chapter section → bounded source/remote actions. Continue resolution is deterministic: exact semantic locator → first unread source chapter → first chapter; unavailable content produces an explanatory disabled state and retry/use-local action, never a dead button. Starting a non-library book records host-local browsing history and semantic progress without adding it to Library or causing a source/network write. The normal directory route is removed: a directory deep link addresses the chapter section inside canonical Detail. Reader receives exact book/chapter identity and returns to that same detail; its auxiliary/search/settings state consumes Back before Reader navigation. Detail uses one dynamic FAB whose recent real scroll direction selects quick-bottom/quick-top and whose idle state returns to Continue/Start.

### Library, collections, search and history

- Library exposes a visible app-bar `聚合搜索` action instead of a second inline-only search implementation. The shared `search?origin={library|browse}` route keeps query editing inert. Normal entry implicitly scopes every active search-capable source; source-card entry may bind one active source. No source selector or leading Search control is rendered. One explicit trailing Search submit starts local Room FTS and that effective scope together. Local results may appear first only because they finish first. Advanced public/local/source-specific filter UI and D33 descriptors are deferred from 4A.
- `全部书籍` remains the immutable derived root. `继续阅读 / 最近阅读 / 稍后再读 / 休眠来源 / 追更` are default-created system presentation nodes: users may hide/rebuild/reorder them, but cannot change their stable IDs or query rules. Only Read Later accepts direct membership writes; the other memberships remain derived. There is no `library/collections/templates` route.
- Library AppBar `+` is the only normal creation entry; there is no final-page creation card or explanatory creation copy. The empty-library recovery remains `浏览并添加书籍`. Tag compact chips omit counts while tag list rows show book counts. Long controls wrap, scroll or become a labelled picker—never clip at `fontScale = 2.0`.
- A local MANUAL folder may contain directly assigned books and child collections, with presentation depth capped at root → child and cycle protection. Opening it shows direct books plus child folders; it does not recursively aggregate descendants. SMART collections are leaf-only, cannot have children/direct membership, and provide create/edit, readable summary, AST validation and unsaved-change confirmation.
- Both directions expose manual membership: book detail `管理所属集合` displays permitted local manual folders and current membership; local manual folder `管理书籍` searches/selects books. System nodes other than Read Later, mirror nodes and smart results reject manual membership edits with explanation. Standard dropping one book on another first confirms/name the operation, then atomically creates one folder containing both; E-ink exposes an explicit selection/button equivalent.
- Library selection mode is explicit, count-labelled and keyboard/TalkBack operable. Back clears selection before route/root traversal. Bulk scope is local-only: membership and only semantically unambiguous local metadata/removal. Source/network operations remain explicit per book.
- **[SUPERSEDED BY D29]** `library/history` is a labelled Library app-bar destination with grouped recent reading, explicit resume, source-scoped suggestions while installed, per-item removal and clear-all confirmation. Standard scrolls; E-ink pages. Browsing/search history is host-local and excluded from transfer unless the export review explicitly includes it; semantic progress is a distinct portable record under D29.
- **[SUPERSEDED BY D19]** Local `移出书架` removes only the `LOCAL_PIN` presence origin and direct manual memberships under the confirmed command scope; it retains known metadata, semantic progress, browsing history, rating and local tags. Other presence origins (including an enabled or frozen mirror snapshot) are untouched. Confirmation names the removed origins, retained annotations/data and the fact that no website operation occurs.

### Source, dormant/offline and remote-library surfaces

- Browse identifies installed source, installed version, capability status and the primary search/discovery action. Import/approval/failure content scrolls at large text and preserves existing source activation until a new verified package is approved.
- Source data on canonical detail distinguishes current, cached/stale and unavailable. Dormant books retain local title, membership, tags, rating, semantic progress and locally available chapters; source actions state why they are unavailable and offer `浏览内容源` only when that is the real destination.
- Every long source list uses shared Standard scroll/E-ink explicit pagination, stable `BookIdentity` keys and row action semantics. Source errors distinguish retryable network, offline-cache, login/verification and malformed-source outcomes without raw secrets/HTML. Full chapters live inside Detail rather than a normal directory list route.
- Aggregated source search is read-only. No Room or network request starts while typing, opening/restoring the route or changing the draft. One trailing submit starts local work plus the implicit effective scope: every active search-capable source on normal entry, or one route-bound active source from a source card. The coordinator runs at most 3 source jobs concurrently (hard per-source =1). The UI exposes no source selector or leading Search control, one aggregate progress indicator and one incremental result flow; normal per-source lanes/status strips and `仅关键词` labels are not persistent UI. Internal source jobs retain isolated cancellation/failure/retry. Host aggregation deduplicates only exact `BookIdentity`; same-title books from different sources remain separate and use a compact source mark only when disambiguation is necessary. D33 `search-capabilities-v2` / `search-v2` advanced descriptors remain a deferred historical design, not a current gate dependency.
- **[SUPERSEDED BY D24]** Remote-library route is source-ID-addressed. It shows signed read/add/remove/move capability/grant, credential-ready and source availability state before actions. The former generic pull contract is replaced by the distinct, explicitly named import and calibration commands in D24; neither starts on entry, root change, restoration or login return.

### Mutation, confirmation and feedback policy

Define shared, screen-independent `MutationUiState` and accessible `MutationResultBanner` in `core:ui`. Every affected command owns a keyed state machine; state is restored from durable truth after recreation where appropriate, while non-durable visual working state safely resolves to re-query/retry.

| Command class | Working and duplicate prevention | Completion feedback | Confirmation |
|---|---|---|---|
| Tags, rating, manual membership, collection edit | Disable only the scoped command and preserve editable drafts | Persistent inline status, normalized/read-back value, polite live region, safe retry | No confirmation for reversible single-field edit |
| Local remove / destructive local bulk action | Block repeat; preserve original list context | Clearly state what remains (progress/history/local metadata) and what does not happen remotely | Required before local removal |
| Source search/read | Explicit working/count/cancel state; no accidental duplicate network operation | Typed result, source/provenance and retry/cache/verification action | No confirmation for read-only source data |
| **[SUPERSEDED BY D24 / D31]** Generic pull | No implementation may use this row. Import, calibration, and remote add/remove/move each use their named contract and operation-specific outcome. | No generic `already-present` completion grammar. | See D24/D31. |
| Remote add/remove/move | Direct-action token accepted before transport; scoped disable; durable reconciliation is truth | ADD confirms only `applied`/`already-present`; REMOVE only `applied`/`already-absent`; MOVE only `applied`/`already-at-target`; otherwise persistent cancelled/unresolved with explicit retry | Required and source/website effect/target-specific before enable or destructive mutation |
| SAF transfer | Picker, review, apply/recovery and report remain distinct | Redacted durable report and reopen route | Import confirm after review; abort/destructive cleanup confirm when allowed |

`InfoBanner`/`InlineStatus` must gain an appropriate success/error live-region contract or a new semantic equivalent; transient Snackbar/toast/animation/color alone never proves completion. Result text names the affected book/collection/source safely, never leaks credentials, raw pages, cookies or diagnostics. All destructive dialogs make the default safe/cancel action accessible.

### Settings, transfer and adaptive foundations

- More becomes a grouped entry point for Display, Reader and Data. Reader settings are an explicit `more/reader` route, grouped by typography/layout, navigation/progress and effective E-ink constraints. They show persisted versus effective value; setting dependencies cannot create impossible combinations.
- Data becomes `more/data`: separate `导入 Tsuyomi 数据`, `从 Hikari Novel 导入`, `导出 Tsuyomi 数据` and `查看最近导入报告`. Review shows format, bounded file state, merge/conflicts/warnings and sensitive-data exclusion before mutation. Completion offers an accurate result and persisted report; recovery gate remains above ordinary navigation.
- Shared layouts use window-size classes rather than orientation alone. Compact, split-screen, landscape phone and ≥600dp pane variants preserve the same tasks, focus, selected state and route. Detail/forms scroll safely; no core action is beyond the viewport or gesture-only.
- All icon-only controls have localized descriptions; rows declare button/selected/disabled/expanded state; headings, lists, collection membership, pagination, reader progress and mutation results have semantic values. Test TalkBack, keyboard/DPAD and 48dp targets. Font scale 2.0, system insets and physical keyboard never hide the last action.

### Lightweight contextual feature introductions

`core:ui` provides one non-guided `FeatureIntroduction` surface: title, 3–5 short facts, network/remote/privacy effect where applicable, and the safe actions `稍后再看` / `知道了`. It has no coach marks, arrows, animation, forced interaction path or automatic operation. First entry to mirror setup, Updates, smart-rule editor, website writeback and Data import/export displays its current `featureId + tutorialVersion` once **before** network or mutation. Global automatic introductions are enabled by default; `more/help` can disable/re-enable them and reset all seen versions. Each feature retains a labelled `功能说明` action for manual replay. Only material behavior/remote/privacy changes increment the tutorial version and display the revised introduction once; visual/copy-only changes do not.

Tutorial dismissal or disabling never approves a capability, starts a job, changes confirmation settings or replaces install/write/import/destructive confirmations. TalkBack focus starts at the title and returns to the trigger; Standard/E-ink share text, while E-ink uses immediate modal replacement with no transient-only result.

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

The acceptance source remains an updated public Wenku8 fixture only after protocol/manifest review. It must implement deterministic list-targets/remove/move responses including operation-correct `applied`/`already-absent`/`already-at-target`, pre-accept cancellation, ambiguous terminal failure, declared success aliases, unauthorized target and source-change cases. Live Wenku8 remains anonymous best effort and never an authority for deterministic acceptance. No third-party source is granted inferred write capability.

## Components and clean-cutover order

| Order | Component(s) | Work and invariant |
|---:|---|---|
| 1 | `core:ui`, `core:display` | shared mutation/result semantics, list/pagination/row semantics, adaptive state restoration; no feature-local duplicate feedback components. |
| 2 | `core:database`, `shared:source-contract`, `source:extension-manager`, app navigation state | 4A stable detail projection, collection/history/sort/query persistence and source-safe route resolver. For 4B, protocol-first remove/move/target DTO, schema 3 and host operation context precede UI. |
| 3 | `feature:library`, `feature:book`, `feature:search`, `feature:reader`, `feature:browse` | canonical detail, mixed Library/SystemNode presentation, two-level collection/history tasks, root-neutral basic aggregated search plus source-scoped entry, source/remote capability surfaces and Reader-parent contract; remove obsolete local/source detail routes, routine directory route, template manager, D33 advanced-filter dependency and duplicate Library inline search. |
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
| Canonical detail | Aggregated search, source search, Library, manual/smart collection, History and remote favourites open equal `BookIdentity` detail; no duplicate surface; source unavailable preserves local state; process recreation never retargets identity. |
| Reading flow | Detail exposes Continue/Start and integrated full chapters; locator/first-unread/first-chapter precedence works; chapter deep links target Detail; Reader Back/Up returns to the exact detail/caller state. Tap seek jumps immediately; drag seek presentation and single-commit behavior require physical-device Reader evidence rather than Atlas still approval. |
| Search | Visible entry from Library and Browse; typing/restoration produce zero work; no selector or leading Search control; one explicit trailing submit starts local work plus the implicit effective scope, with ≤3 concurrent source jobs and per-source =1; one aggregate progress and one incremental flow; internal per-source failure/retry is isolated without persistent lanes; exact-identity dedupe holds; no advanced-filter UI; Standard scroll/E-ink pagination, Back/Up and process recreation preserve query/effective scope/result position. |
| Organization | AppBar-only creation; hide/rebuild/reorder system nodes without changing their rules; only Read Later manual membership; create/rename/manual-order/nest/delete two-level manual collections; confirmed two-book drop; all sorts/query/selection and E-ink page reset. |
| Mutations | Tags/rating/membership/add/remove/collection mutation success, controlled failure, cancellation and rapid repeat show truthful persistent outcome, retry where valid, normalized data and live announcement. |
| Source/remote | install/approval/cancel/failure; precise source-search/list pagination; offline/dormant behavior; read capability/credential gates; explicit `复制到本地书架` review/cancel/count/local partial/retry; separate `校准网站镜像` staging→complete proof; no automatic network/write. |
| Transfer/settings | picker cancellation; separate import formats; bounded review/confirm/recovery/report; export outcomes; display/reader settings retention and effective E-ink explanation. |
| Accessibility/adaptation | TalkBack, keyboard/DPAD, 48dp, `fontScale=2.0`, portrait/landscape/split/≥600dp; Standard and E-ink share task result. |

## 4B acceptance matrix

| Task | Required proof |
|---|---|
| Admission | missing/revoked operation grant, policy, active package/generation, credential revision, user setting, direct action or target prevents token/transport and disables/explains UI. |
| Remove | final verb-specific confirmation; typed `applied`/`already-absent` only confirms; local remove alone emits zero remote call; cancellation/unresolved persists and explicit retry uses fresh state. |
| Move | explicit target list and final target confirmation; hidden/stale/unauthorized target, wrong binding, generic context and redirect alias attempts fail closed before transport. |
| Lifecycle | source update/removal/credential loss during each phase preserves local state, disables setting, revokes token, writes durable terminal reconciliation and never claims success. |
| Migration/privacy | schema 2→3 preserves records; remove/move false after upgrade/import; transfer/backup/logs never carry target, token, credential, policy grant or reconciliation secrets. |

## Verification and evidence requirements

Automated proof includes feature-level semantic/UI tests, navigation and process-recreation instrumentation, Room migration/query/reconciliation contracts, extension/runtime/network adversarial tests, screenshots/goldens in all affected components, and the normal Android/protocol/extensions/REUSE/artifact-policy gates. Tests assert observable contracts, not implementation text.

Every 4A/4B target head passes the mandatory Standard portrait flow on `Tsuyomi_API29` (`1080×2400`, 420dpi). Each record includes target HEAD, `wm size`, `wm density`, orientation, profile, `font_scale`, user-flow result and screenshot SHA-256. Landscape, split-screen and Layoutlib supplement but never replace this record.

The retained `Tsuyomi_EInk_API29` (`1264×1680`, 240dpi), compact E-ink, grayscale and physical-panel requirements are non-blocking during the freeze and return together in the restoration pass. Before implementation, independent Designer reviews Standard information architecture, task flows and accessibility; independent Adviser reviews module/persistence/protocol/security/lifecycle/concurrency/test plan. Before merge, both re-review affected final head; all applicable P0/P1 findings close with source fix, regression proof, reusable rule and reversible commit. User explicitly authorizes implementation, PR creation and merge separately.

## Independent review closure and user decision register

### Review conclusions bound to this draft

| Review | Verdict | Blocking amendments |
|---|---|---|
| Product / UX | **SUPERSEDED — original APPROVE WITH CHANGES verdict closed by the Foundation Amendment and UI Constitution RC evidence** | Its route/history/rule/cross-root/local-removal/post-login/action-placement requirements are restated as binding D19–D30 plus the D31 closure contract and 4A acceptance gates below. It is not implementation authorization. |
| Architecture / security | **SUPERSEDED — original REJECT verdict closed only at the planning/RC level** | Its foundation debts and mandatory implementation-time acceptance gates are recorded below. Production remains blocked pending atlas manual review and explicit implementation authorization. |
The reviewer reports are retained only in the private planning record; this public plan carries their evidence-backed requirements. This legacy narrative is **[SUPERSEDED FOR IMPLEMENTATION ORDER BY THE FOUNDATION AMENDMENT]**: D19–D30, D31 and the P1/atlas gates control. Historical labels never authorize implementation.

### Confirmed additional findings

| ID | Status | Problem / affected contract | Severity | Required regression tier |
|---|---|---|---|---|
| P4-UX-004 | READY | Library hides the existing `RECENT` filter, lacks all promised sort modes and does not clamp/reset E-ink page on selected-collection change. | P1 | Room query/sort contract; navigation/UI instrumentation; Standard/E-ink golden and portrait flow |
| P4-UX-005 | READY | Library has no explicit selection mode; local-detail/filter layouts can clip or leave actions unreachable at `fontScale = 2.0`; long source lists lack uniform explicit E-ink pagination and row semantics. | P1 | Compose semantics/layout tests; TalkBack/DPAD; goldens; both portrait flows |
| P4-UX-006 | READY | More/Data lacks separate Tsuyomi/Hikari import and report routes; More lacks the promised Reader settings route. | P1 | route/SAF/recovery instrumentation; persistent-report contracts; goldens; both portrait flows |
| P4-UX-007 | **SUPERSEDED — incorporated into D24/D31 and atlas route #17** | The old source remote-library lacked stable routing and typed state; the amended contract is a bounded read list plus explicit local-pin import, separate from mirror calibration and website writeback. | P1 | import/calibration source lifecycle tests; semantics; E-ink pagination; both portrait flows |
| P4-UX-008 | **SUPERSEDED — incorporated into 4A / D29** | History/resume surface was missing its root, route, entry point, retention policy and process-recreation contract. | P1 | The canonical route, history distinction and portable-progress policy are now binding; implementation proof remains required by 4A/P1-F5. |
| P4-UX-009 | **SUPERSEDED — incorporated into 4A / D7/D27** | Smart collection rule create/edit, unsaved-change and readable-rule flows lacked a 4A disposition. | P1 | Rule route, Back semantics, onboarding and accessibility remain mandatory implementation proof. |
| P4-REMOTE-001 | **SUPERSEDED — incorporated into 4B / D11–D18/D24** | 4B remove/move/target protocol, grants, reconciliation/recovery, results, compatibility and schema rollback were underspecified. | P1 | 4B protocol/security/P1-F1/P1-F6 evidence remains mandatory before implementation/merge. |

### User decisions — UX and local-data behavior

| ID | Decision | Recommended default | Material alternatives | Scope |
|---|---|---|---|---|
| D1 | Supersede Phase 3’s two-detail route model with one canonical stable `book/{sourceId}/{remoteBookId}` page. | **Approve unification.** Host-composed Room-first detail with source enhancement; no visual or route-level dual detail. | Retain two surfaces, or only visually unify while keeping duplicate routes. Both retain the reproduced P4-UX-001 defect. | 4A |
| D2 | Re-selecting the active bottom/rail root. | **Pop that root’s own stack to its root.** Other roots retain their stacks. | Do nothing plus a named root-reset action in the app bar. This needs a separately specified label/placement/announcement. | 4A |
| D3 | Historical decision, **expressly superseded by D19**: local retention for `移出书架`. | Formerly: delete library/manual memberships, rating and local tags; retain metadata, semantic progress and browsing history. **Do not implement this former default.** | D19 retains annotations and separates origin removal from local data. | 4A |
| D4 | Reading a book that is not in the local library. | **Record host-owned browsing history and semantic progress without adding the book to Library.** No source/network write follows. | Require 加入书架 before reading; or store history without resumable progress. | 4A |
| D5 | 4B remote remove/move entry points. | **Canonical detail only**, in a labelled source/remote action section; per-book, visible-disabled-with-reason when unavailable; never bulk, gesture-only or duplicate remote-list actions. | Also place actions on remote-library rows or selection mode; both multiply high-risk action surfaces. | 4B |
| D6 | Once-only post-login import prompt. | **[SUPERSEDED BY D24]** Login return performs zero work. A prompt may only explain and navigate to the separate `复制到本地书架` review; confirmation there, never login completion, starts local-pin import. | Generic pull/sync from login or route entry is forbidden. | 4A/4B |
| D7 | Smart collection rule create/edit in 4A. | **Ship create and edit** at `library/collections/{collectionId}/rule`, including validation and unsaved-change Back confirmation. | Explicitly defer with a Phase 3 supersession note; create-only remains insufficient. | 4A |
| D8 | History placement. | **`library/history`**, entered through a labelled Library app-bar action; Standard scroll/E-ink explicit pages; Back/Up return to Library. | Fourth root (breaks 3-root shell) or More (buries frequent resume task). | 4A |
| D9 | Precise cross-root action label/destination. | **`在来源中打开本书`** opens the same addressed canonical detail on Browse’s stack while preserving the Library stack; disabled with explanation for dormant source. | `查看本书来源详情` implies a second detail; `前往浏览` describes only root switching and is the current defect. | 4A |
| D10 | 4A library bulk-action boundary. | **Local-only:** collection membership, and only semantically unambiguous local metadata/remove actions. Source/network operations stay explicit per book. | Bulk source refresh/write actions; require separate source-load and security specification. | 4A |

### User decisions — privacy, remote effects and compatibility

| ID | Decision | Recommended default | Material alternatives | Scope |
|---|---|---|---|---|
| D11 | HXP v2 compatibility and phase boundary. | **AMENDED:** HXP manifest v2 / Host API 1.2 base negotiation may be introduced when a separately approved 4B write or later advanced-search contract requires it; D33 advanced search is not a 4A prerequisite. Existing v1 read/add/plain-search packages remain under explicit capability negotiation. | The former mandatory 4A D33 descriptor/search-v2 subset is superseded by the user's explicit deferral. | 4B/future search spike |
| D12 | Remote target discovery and move scope. | **Explicit signed list-target operation; opaque target IDs; no free text/default/guessed target; no remote folder CRUD.** | Let extension choose a target or accept user text; rejects host validation and fail-closed target binding. | 4B |
| D13 | Operation-specific authorization persistence. | **One receipt per `(sourceId, operation)`** with enabled value, publisher, operation-policy fingerprint, origin, package/manifest generation and approval revision; add migrates only if exact receipt matches; remove/move false. | A shared source fingerprint/flag; cannot preserve unaffected operations safely. | 4B |
| D14 | Historical decision, **expressly superseded by D29**: transfer/privacy policy for progress and history. | Formerly: history/progress host-local and excluded from transfer unless export review explicitly includes them. **Do not implement this former default.** | D29 keeps semantic progress portable by default and makes browsing/search history a separate explicit opt-in export choice. | 4A |
| D15 | Remote remove/local data coupling. | **Remote remove never deletes local library data and UI offers no coupled delete option in Phase 4.** Local removal remains a separate action under D19. | Offer a combined remove option; couples two failure domains and needs another confirmation/reconciliation model. | 4B |
| D16 | Remote target/reconciliation retention. | **App-private Room stores opaque target ID plus bounded redacted display snapshot; exclude from transfer, export and logs; clear with source removal/explicit data erasure; retain unresolved evidence until user resolves/erases source.** | Persist richer target metadata or export it; expands privacy/portability surface. | 4B |
| D17 | Unresolved remote-operation policy. | **Block later remote mutations for that book while any operation is PENDING, IN_FLIGHT or UNRESOLVED; only explicit retry/reconciliation for that row is permitted.** | Allow a different operation despite unresolved state; risks contradictory website state and hidden audit rows. | 4B |
| D18 | Room schema 2→3 rollback. | **Forward-only schema 3; rollback build retains schema-3 reader but disables Phase 4B UI/transport.** Preserve local books, memberships, progress, history and unresolved evidence; no remote replay. | A tested non-destructive 3→2 downgrade; higher implementation/migration risk but allows old binary rollback. | 4B |

### Historical post-decision technical amendments

The following original requirements are retained as historical review evidence. **[SUPERSEDED FOR IMPLEMENTATION ORDER BY D19–D32, D31's non-search contract and the reconciliation amendment]** They no longer create a separate confirmation path.

1. Add `library/history`, `more/about`, `library/collections/{collectionId}/rule`, `library/tags` and `library/mirror/{bindingId}` to the canonical graph; name entry/empty/Back/Up/recreation behavior.
2. Place Reader inside the caller’s root stack; replace the former directory subroute with a typed Detail chapter anchor; persist typed bounded caller-list arguments in `SavedStateHandle`, encode arbitrary Unicode/reserved IDs safely, and fall back only to that origin root when invalid.
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

每条收到的发现项都追加到下表，并在进入实现前补全严重度、受影响用户契约和回归层级。只有 `READY` 项可进入 Phase 4 实现计划。

| ID | 状态 | 设备/构建 | 复现步骤 | 实际结果 | 预期结果 | 证据 | 严重度 | 回归层级 |
|---|---|---|---|---|---|---|---|---|
| P4-UX-001 | READY | API 29 phone portrait / `0.2.0`；用户实机流与代码路径复核 | 从搜索结果打开 `雾港纪事`；加入书架后从本地书架打开同一书；点击“前往浏览/打开来源” | 搜索进入 source-owned `source/detail`，书架进入 Room-owned `library/book/{sourceId}/{remoteBookId}`；标题、操作和返回栈分裂。本地页动作只切换/恢复 Browse 根栈，实际进入来源浏览页，且可能恢复到进入来源前的旧 Browse 页面，不保证打开当前书；详情→目录→章节→Reader 增加重复层级 | 同一 `BookIdentity` 无论从搜索、书架、历史或远程收藏进入，都落到一个 canonical book detail。页面先显示本地缓存/书架状态，再按来源可用性增强元数据和目录；来源不可用时本地详情仍可用。目标写明“查看本书来源详情”时必须打开该书，不能恢复无关 Browse 历史；主要阅读动作不得要求经过重复详情页 | 用户在固定 phone portrait 基线稳定复现；`MainActivity.Routes.Detail`、`Routes.LocalBook`、`LocalBookDetailsScreen.onOpenSource`；`PHASE_3.md` 294、301–305、428–430 | P1 | route/navigation instrumentation；Room/source race and dormant-source tests；统一 screen semantics/goldens；两套 portrait AVD 用户流 |
| P4-UX-002 | READY | API 29 phone portrait / `0.2.0`；用户实机流与代码路径复核 | 创建手动集合；将来源书加入本地书架；分别检查本地书籍详情和“管理本地集合” | 可以创建/删除手动集合，但没有任何可发现路径把已入书架的书加入或移出集合。数据库已有 `addManualMembership` / `removeManualMembership`，UI 没有调用入口 | 本地书籍详情提供可访问的“管理所属集合”，显示当前 membership 并允许多选保存；集合详情或编辑态也应提供从书架选择/移除书籍的对向入口。保存必须有持久结果反馈，智能集合不显示手动 membership 编辑 | 用户在固定 phone portrait 基线发现；`LocalBookDetailsScreen` 无 collection 参数/动作，`CollectionManagerScreen` 只管理集合本身，`RoomLibraryRepository` 已有 membership API；违反 `PHASE_3.md` 305、438 | P1 | repository membership contracts；book/collection UI instrumentation；TalkBack/DPAD；两套 portrait AVD 双向发现流 |
| P4-UX-003 | READY | API 29 phone portrait / `0.2.0`；用户实机流与代码路径复核 | 在本地书籍详情输入标签并点击“保存标签” | 点击后没有 loading、禁用、成功、失败、标准化结果或可访问公告；即使 Room 写入成功，用户也无法判断操作是否发生，失败同样不可见 | 保存期间防止重复提交；成功后以持久 inline 状态显示“已保存”并回显 Room 标准化后的标签；失败显示安全、可重试错误。TalkBack 使用 live region；E-ink 不依赖短暂 Snackbar 或动画 | 用户在固定 phone portrait 基线稳定观察；`MainActivity.onSaveTags` 启动协程后没有 outcome state，`LocalBookDetailsScreen` 只保留无状态按钮；违反 `PHASE_3.md` 296 与 `EINK.md` 65–66 | P1 | repository success/failure；Compose mutation-state tests；semantics/live-region；两套 portrait AVD 保存/失败流 |

### P4-UX-001 设计约束

- 使用稳定身份路由 `book/{sourceId}/{remoteBookId}` 作为唯一书籍详情入口；搜索、书架、历史和远程收藏只传稳定身份，不各自拥有详情页。
- host 组合一个详情状态：Room 中的本地书架、评分、标签、集合、进度与 reconciliation 是本地真值；已验证来源只增量提供可刷新的简介、状态和目录，不得让网络或扩展阻塞本地内容。
- dormant source 在同一页面内降级，保留本地信息与阅读进度，并把来源相关动作禁用/解释；不能跳到另一种“本地详情页”。
- `继续阅读` / `开始阅读` 是详情页主动作。目录可作为同页章节区或一个明确的次级目的地，但不得再经过第二个书籍详情；Back 必须回到调用方列表并保留其查询、滚动和筛选状态。
- source/local 的安全与生命周期所有权继续分离在状态层和 controller 层，不用重复页面或重复 route 表达内部边界。

## Phase 4 导航与操作逻辑审阅准入

Phase 4 的最终审阅必须把产品操作逻辑与代码正确性作为两个独立准入面。代码测试通过不能替代真实任务流审阅；reviewer 必须实际执行下面的 route/task matrix，并对每一次点击的目标、返回结果、状态恢复和动作反馈给出结论。

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

最终 review 记录必须包含逐步点击序列、每步预期/实际 route、Back/Up 结果、恢复的页面状态、Standard 与 E-ink portrait 截图证据，以及未通过项对应的回归层级。仅检查 composable、controller、repository 或测试覆盖率不足以通过 Phase 4 design review gate。

状态：`NEW`（待复现）→ `TRIAGED`（已归类）→ `READY`（可实现）→ `FIXED`（修复和回归证明完成）→ `DECLINED`（不属于产品缺陷，说明理由）。

报告时至少提供：所在页面、操作顺序、是否可稳定复现、截图或录屏时间点，以及期望行为。可直接自然语言描述；维护者负责转写为表中的完整条目。

## Foundation Amendment — 4A–4C 统一基础计划（后续条款优先）

### 适用性、证据状态与历史处理
本节是 Phase 4 的后续计划，但 `DESIGN_DIRECTION_HANDOFF.md` §1.2 的显式用户裁决优先。被替代的历史决定仍保留并以 **[SUPERSEDED]** 或 **[DEFERRED]** 标记，不能再产生实施依赖。D19、D21、D24、D26–D30 及 D31 的非搜索安全合同继续有效；D20/D22/D25/D32 已按 Library/Search 决定修订；D33 是延期的研究草案。

- 已完成的基础证据：UI Constitution 已完成全量 review/protocol 冲突归并；旧 Atlas 捕获全部是历史或 rejected evidence。
- 未完成的人工证据：用户尚未批准基于新裁决重做的完整 Atlas 页面、录屏、语义日志或 evidence ledger。
- **绝对阻断：**在 fresh atlas 人工审阅完成并取得显式用户实现授权前，4A、4B、4C 的任何生产功能实现均不得开始；不得以编译、旧截图、自动选择、设计审阅或本计划替代该授权。
### D19–D32：经冲突归并的 Foundation 决定；D31：非搜索 source-contract 闭合；D33：延期历史草案

| ID | 已确认决定 | 约束范围 |
|---|---|---|
| D19 | **[SUPERSEDES D3]** 书架可见性是 `LOCAL_PIN`、`READ_LATER`、手动集合 membership、网站镜像 membership 的派生并集；不是单一 `inLibrary` 布尔值。`READ_LATER` 是独立的本地 presence flag，不是 tag 或普通文件夹；其唯一书籍可见于 All、稍后读和更新范围。显式 `移出书架` 只移除 local pin 与直接手动 membership，保留评分、本地标签、已知元数据、语义进度和浏览历史；是否清除 Read Later 必须作为独立、明确的本地动作。其他 origin 不受影响，且绝不发起网站操作。隐式 origin 变化也不得删除 annotation。 | 4A schema 3、书架查询、删除确认、更新范围 |
| D20 | Library IA 使用同一 mixed content flow：不可删除的派生 `全部书籍` 根、默认创建但可隐藏/重建/手动排列的 SystemNode、最多两级的 `LocalCollection`（`MANUAL` 或只读规则的 `SMART` leaf）以及每来源至多一个 `WebsiteMirrorBinding`。系统节点定义不存储 mutable membership；仅 Read Later 是显式 membership origin。 | 4A 导航、集合、镜像；schema 3 |
| D21 | `TagCatalog`/`TagId` 是本地 annotation 身份，按 NFKC、空白归一和 locale-independent case fold 唯一化；重命名保持 TagId，合并在一个事务重写 joins。来源标签为 `SourceTagIdentity(sourceId, opaqueSourceTagId)`；无 opaque ID 时才使用来源范围的规范化 label 并标为 label-derived。相同展示文字不得合并本地标签、不同来源标签或本地/来源标签。 | 4A 标签、搜索、智能规则、迁移 |
| D22 | 布局偏好本机保存，键为 `LibraryContextId × DisplayProfile`，且不随传输导出。显式用户 override > host 默认；来源 layout hint 在所有 profile/context 一律忽略。Standard/E-ink 常规手机 host 默认均为固定三列 grid，宽屏按 150dp 最小可读卡宽自适应且不设任意列数上限；double-compact 只有无法满足 48dp/可读下限时降两列。布局仅改变呈现，不能改变书、顺序、动作或权限。 | 4A `core:preferences`、列表/网格 |
| D23 | 来源品牌只能经 host 验证后作为紧凑、低饱和 identity role 使用。来源名已由 route/title 建立时不重复；混合 Browse/Search 只有确需消歧才显示 compact mark。无效、过大、含脚本/远程引用或缺失 payload 使用确定性 host fallback；不得进入 Library、app chrome、Reader 或 E-ink 彩色表面。 | 4A `core:media`、来源 UI、安全 |
| D24 | 网站操作分裂为两个可见命令：`复制到本地书架` 是一次性、明确确认的 import，可创建 `LOCAL_PIN`；`校准网站镜像` 只取得并原子替换该 source 的 `MirrorNode` 快照，绝不创建 pin 或手动 membership。旧的含混 remote-library pull 在 4B clean cutover 中改名为前者或删除，绝不可被当作 calibration。禁用镜像会取消当前校准并保留最后完成快照为 **已冻结**；冻结、禁用和本地快照擦除均不得远程写入。 | 4B 镜像、来源路由、迁移、确认文案 |
| D25 | `追更` SystemNode 的显示/隐藏/重建与更新调度完全独立。隐藏只影响 Library presentation；调度只有在 Updates 设置中经显式用户动作才启用、禁用或变更，默认关闭。重建节点不会重启调度。 | 4C Library presentation、设置、调度 |
| D26 | 更新工作由独立 `UpdateCoordinator` 管理，镜像校准由独立 `MirrorCoordinator` 管理；可共享受限 source lane、计数进度与取消原语，但禁止抽象为通用 sync engine。Updates 对每个有 active local origin 或 enabled mirror origin 的 `BookIdentity` 去重；仅冻结镜像的书默认不检查，排除项、休眠/未验证来源和 partial outcome 必须可见。update anchors、session/item 报告是持久本地状态，保留至明确数据擦除；隐藏视图不改变它们，且按 D29 永不传输。 | 4B/4C 并发、范围与保留 |
| D27 | 首次进入镜像设置、追更、智能规则、网站写入、数据导入/导出时，显示一次 `FeatureIntroduction(featureId, tutorialVersion)`；它只说明任务、网络/隐私影响和开始入口。关闭、禁用、重放介绍均不授予 capability、不启动网络任务、不改变确认设置。 | 4A–4C onboarding、无障碍 |
| D28 | `Tsuyomi UI Constitution v1.0-RC2.1` 是 Phase 4 UI 结构、状态、无障碍、E-ink、模块边界、动画和品牌安全的候选权威。任何生产 UI 实现前，必须完成基于 §1.2 裁决的 fixture-only Atlas 捕获与用户手工审阅/签署。 | 全部 UI 计划、强制 atlas gate |
| D29 | **[SUPERSEDES D14]** 语义进度（定位器及相关时间）保持版本化、默认可移植的 host 数据；浏览/搜索历史单独处理，默认不导出，只有 export review 的明确选择才包含。镜像绑定/节点、更新 session/调度、远程尝试/target/receipt、凭证、来源状态、CoverRef transport locator 与二进制缓存永不传输。导入不得启用调度、镜像或写回。 | 4A/4C transfer、隐私、兼容 |
| D30 | Phase 4 采用 Constitution 的 production DAG、禁止边与 prototype isolation：`shared:library-domain` 只放纯领域类型/ports；`core:library` 协调 library、search、mirror、update；`core:media` 是唯一 cover/branding 请求、验证、解码与缓存边界；`core:preferences` 拥有 UI preference schema/migration/reset。feature 只组合不可变 UiState 和 typed action，不能持有 Room entity、URL、cookie、Media internals 或 Network/Material 直接依赖。 | 4A 基础模块与 clean cutover |
| D32 | **[CONFIRMED, AMENDED BY B041-D]** Phase 4A 使用 root-neutral `search?origin={library|browse}&selectedSourceId={sourceId?}` 单搜索栏，删除重复搜索壳。query draft inert；normal entry 隐式覆盖所有 active search-capable sources，source-card entry 可通过 route 绑定一个 active source；UI 不显示来源 selector 或 leading Search control。一次 trailing Search submit 同时启动 local FTS 与 effective scope；source jobs ≤3 concurrent、per-source =1。只显示一个 aggregate progress 和一个增量结果流。结果只按 exact `BookIdentity` 去重；同名不同 identity 保留。内部 source jobs 可独立取消/重试，但 normal per-source lanes/status prose 不常驻。恢复页面不自动重发网络；搜索历史只在提交时写入并按 D29 默认不导出。 | 4A `core:library` basic SearchCoordinator、`feature:search`、导航、Room FTS、E-ink/无障碍 |
| D33 | **[DEFERRED BY USER — NOT IN ATLAS OR 4A]** 保留为未来隔离 spike 的研究草案：host-owned advanced `SearchIntent`、extension-declared `SearchCapabilityDescriptor v2`、公共/本地/来源专属 filter descriptors 与 `search-v2`。当前不得实现其 UI、协议依赖、fixtures 或 P1 gate；重新纳入必须重新裁决范围和 source-contract 版本。 | Future isolated search/HXP spike only |

### 4A 的基础模型、schema、传输与 UI 边界

**身份与独立所有权。** `BookIdentity(sourceId, remoteBookId)` 是唯一书键；元数据、来源标签、封面引用、local pin、annotation、手动 membership、语义进度、历史、更新 anchor、镜像快照及远程操作记录必须是独立 ownership domain。`BookListItem` 是 `shared:library-domain` 的无 UI/Room/URL 纯投影。`全部书籍`、SystemNode、集合与镜像查询从 origins 的 distinct union 派生；进度、历史和 annotation 自身不制造 presence。SystemNode 的 hide/order 是 presentation state，不改变 query definition 或调度。

**Room schema 2 → 3。** 单一原子迁移必须建立或重构：`books`、`local_library_pins`、`read_later_entries`、`local_book_annotations`、`local_collections`、`manual_collection_memberships`、`smart_rules`、`tag_catalog`/`book_local_tags`、`source_tag_catalog`/`book_source_tags`、system-node presentation/order state、`website_mirror_bindings`/snapshots/nodes/memberships、remote operation records、update policy/session/state，以及可重建的 local search documents/FTS。Search document 不是 metadata/annotation truth；所有 ownership write 使用专用 transaction/API。

回填必须可审计且不丢失：旧 library entry → pin；旧 membership → book identity；rating/tags → annotation/TagCatalog joins；update flag → latest/acknowledged anchor；旧 reconciliation → `ADD` attempt；不能验证的旧封面 locator → 无 `CoverRef`；system nodes 默认 visible、mirror/update tables 为空、schedule disabled。删除公共 whole-row `saveBook`；progress/update/history/annotation 各有专用命令，互不覆盖。

**媒体与 transport。** Feature/UI 只能收到不透明 `CoverRef` 与已解析 `CoverUiState`。`core:media` 经已签名 HTTPS media policy、每跳 redirect/origin/referrer/cookie 检查、MIME/byte/pixel/frame 上限、请求去重、取消、原子写入与 source/package/credential-revision partitioned binary cache 处理图片。缓存是可清除、非真值、非便携优化；无效/失败确定性 fallback，绝不让 composable 以 raw URL 取图。Transfer 升级为 allowlist 的 v2：继续解析 v1，v2 默认导出安全元数据、pins、Read Later、集合/规则、local tags、rating 与语义进度；浏览/搜索 history 是默认关闭的独立 export-review 开关。v2 始终排除 mirror binding/node/snapshot、update session/item/anchor/schedule、remote attempt/target/receipt、credential、source state、CoverRef transport locator、cache 与所有 UI preference。

**持久 UI preference。** `core:preferences` 拥有单一 UI preference schema、逐版 deterministic migrator、unknown-newer payload 的 byte-for-byte 只读保留与唯一的“更多 > 显示 > 重置界面设置”。未知较新 schema 时所有 preference 写入被阻止并解释；内存 effective defaults 不可覆盖原 payload。迁移/重置绝不触碰领域、来源、调度、镜像或远程操作数据。

### 4A、4B、4C 的有序执行计划（尚未获准执行）

| 阶段 | 仅在前置阻断解除后可实施的 clean-cutover 工作 | 不可违反的完成条件 |
|---|---|---|
| 4A — foundation / local UX | 先冻结 D19–D32 的当前 contracts，明确排除 D33；实现 basic SearchCoordinator，通过注入的 local/source ports 同时协调 inert-draft 后的一次 local FTS + source fan-out；再建立 domain/media/preferences/library ports 与 static DAG enforcement；完成一次 schema 2→3、全 caller 迁移和旧 whole-row 写入/旧 presence truth/JSON tag consumer 删除；切换 canonical detail+chapters、root-neutral basic search、mixed Library/SystemNode presentation、collections/tags/history、Reader/More/onboarding 和 UI Constitution 组件。 | 旧 local/source detail、routine directory route、重复 search/detail screen、兼容 route alias、template manager、来源 layout hint、advanced-filter UI/descriptor dependency、临时 snapshot store 与 feature→database/Material/URL 直连在同一 cutover 删除；一次搜索同时覆盖 local+selected sources、one progress/one flow、exact identity dedupe；系统节点规则与可隐藏 presentation 分离。 |
| 4B — website mirror / authorized writeback | 先实现 D31 的 HXP v2 / Host API 1.2 `library-import-v2`、`mirror-calibration-v2` 及签名 read/list-target/add/remove/move contract 与 fixture；随后实现 mirror staging snapshot、frozen behavior、每 operation receipt/token/reconciliation 及 canonical-detail 上的确认动作。 | 无签名 policy/grant、active package/lease、credential、direct confirmation 或准确 target 时不得 token/transport；镜像不后台/周期拉取、不 CRUD remote folder、不隐式映射 local membership；所有旧含混 pull/sync 调用已删除或改名。 |
| 4C — update inbox / visible schedule | 在 source update anchor/result contract 完整后，实现独立 UpdateCoordinator、durable session/item 模型、manual check、default-off WorkManager schedule、source-lane 去重/限流、notification 与 Updates inbox。 | Session 为 `QUEUED → RUNNING → COMPLETED|PARTIAL|FAILED|CANCELLED`；item 有明确 terminal result；取消先 durable 标记；进程重启仅在 lease 失效后恢复安全只读 item；打开 Updates 不 ack，显式“标记已处理”记录 exact anchor。 |

### D31：分阶段 source-contract v2 闭合契约（4B write；4C update；advanced search deferred）

HXP manifest v2 / Host API 1.2 base envelope、parser/dispatcher negotiation 仅在获批的 4B write 或未来独立 advanced-search spike 需要时交付；D33 不再是 4A 前置。4B 前交付本节 import/calibration/target/write subset，4C 前交付 update subset。所有响应继续遵守 source/package/policy/nonce echo 和 host parser/client/router 三层验证。

- `library-import-v2` 是只读的 bounded flat BookIdentity 列表。它驱动唯一可创建 `LOCAL_PIN` 的 `复制到本地书架`；用户先看到 count、重复项和明确确认，取消不写入。完成 union 结果只报告 local pin changed/unchanged，绝不称为 sync。
- `mirror-calibration-v2` 返回 bounded preorder `MirrorNodeDto(nodeId,parentNodeId?,kind,name,bookIdentity?)` 与 `snapshotId`/`complete`。host 验证单 root、opaque IDs、parent ordering、最大深度/节点数和 identity echoes；Room 以 staging snapshot 写入，只有 complete terminal result 原子替换 active snapshot。取消/失败/缺页保留最后 complete snapshot，不保留 partial mirror membership。
- `update-check-v2` 返回每个 BookIdentity 的 `UpdateProbeResult`：`UNCHANGED(anchor)`、`UPDATED(previousAnchor,newAnchor,orderedChapterEvidence)`、`UNAVAILABLE(reason)` 或 `FAILED(retryClass)`；结果回显 request identity、source/version lease 和 checked-at。host 不接受未证明 ordering 的 anchor 作为自动 handled 依据。`UpdateCheckSession` 的 item terminal union 是 `UNCHANGED|UPDATED|EXCLUDED|UNAVAILABLE|FAILED|CANCELLED`，而 session 是 D26 的队列状态；两者均仅为本地持久数据。
- v1 extension/host responses可继续服务现有 Phase 3 read/add surface，但必须 capability-negotiate 为不支持 v2：不得显示 import/calibration/update 写入入口或伪造兼容结果。host parser、extension dispatcher/router 与 public Wenku8 fixture 为三处明确分支；没有对应签名 operation/context 一律零 transport。

`标记已处理` 不是来源操作、不获取 token、不发 transport、不产生 UNRESOLVED 或跨操作阻断。它在单一 Room transaction 中以当前 item 的 exact `newAnchor` 写入 acknowledged anchor，读回后显示本地成功/失败；reader 自动 handled 同样仅在可信 ordered evidence 到达/越过时更新本地状态。

### D33：延期的高级聚合搜索研究草案（非当前执行合同）

以下旧设计只作为 future-spike provenance 保留：host-owned advanced `SearchIntent`、descriptor-declared query kinds、公共/本地/来源专属 filters、bounded `SearchCapabilityDescriptor` / `SourceSearchRequestV2`、动态 options 及每来源 plan。它们不得进入当前 Atlas、Phase 4A schema/fixture/UI、HXP v2 前置或 P1 blocker。

如果未来重新授权，仍须满足：descriptor 只传 bounded data，不传 UI/脚本/SQL/HTML；global concurrency ≤3、per-source=1；恢复不自动重发；exact identity dedupe；内部 source lane failure 不删除已返回项。用户可见呈现仍必须服从 D32 的 one aggregate progress、one incremental result flow 和零常驻 normal per-source status prose，除非新的显式用户裁决再次修改该合同。

### P1 基础债务与强制验收门

下列原架构/设计审阅的 P1 已由 RC 计划逐项定义，但**尚未以生产代码证明**；它们是 implementation-time blockers，不是可延后缺陷。P0：无。任一 P1 未以 source fix、回归证明、可复用规则和可回滚 cutover 关闭，即不得开始/合并其影响的阶段。

| P1 | 债务与 source fix | 强制验收 |
|---|---|---|
| P1-F1 | schema 3 不得只覆盖 remote reconciliation；必须实现完整三模型、tags、mirror、update 及 deletion/index ownership。 | API 29 2→3 计数/约束/中断打开/feature-disabled rollback/re-upgrade；真实基数 `EXPLAIN QUERY PLAN` 与 keyset/page 稳定性。 |
| P1-F2 | UpdateCoordinator 必须有 durable session/item、独立 scheduler、取消/恢复/partial 语义，不能借用 mirror 或 view visible 状态。 | WorkManager/API 29 process-death、取消、source-lane、notification、manual/scheduled dedupe、anchor ack 回归。 |
| P1-F3 | Cover/branding 必须只走 host `core:media`，不能把 raw source URL 交给 UI loader。 | MIME spoof、oversize/decompression bomb/animation、redirect/origin/cookie/referrer、credential revision、atomic cache/cancel/fallback 测试。 |
| P1-F4 | 元数据写入必须按 ownership patch/complete snapshot；删除 whole-row save。 | progress、reader、summary、update 并发写入互不抹除的 repository/race/process-death 回归。 |
| P1-F5 | D29 不得回退既有 portable semantic progress；history 与 progress 不得被混为一个 privacy class。 | transfer v1 parse / v2 export-import；证明 progress 默认往返，history opt-in，且排除 locator/security/cache/mirror/update runtime 数据。 |
| P1-F6 | import 与 mirror calibration 必须有不同 command、路由、确认、storage、迁移和文案。 | calibration 只替换 staging→complete snapshot；import 才创建 pin；freeze/disable/erase 零远程写；旧 pull 无残留 caller。 |
| P1-F7 | Basic 聚合搜索不得显示硬编码来源 selector/leading Search control，也不得用 untyped map 直传 extension；D33 advanced descriptors 明确不在当前 Phase。 | 零 work before submit；one trailing submit local+implicit active scope；normal all-active/source-card route-bound scope；≤3 concurrent/1 per-source；internal cancel/failure isolation；one aggregate progress/one result flow；exact-identity dedupe；same-title distinct identities；Standard/E-ink/process recreation 回归。 |

另有 UI Constitution 的实现门：production build 必须静态拒绝未经 `core:ui` 的 Material interactive control、`feature → core:database`、对 `core:media.internal` 的 import、`core:ui` 的 network/Room/NavController/DataStore 依赖、以及任何 prototype namespace/symbol/dependency。每个受影响 feature 必须遵守 immutable UiState + one typed UiAction sink；Standard/E-ink 共享状态树，不能以 feature `eInk` boolean 分叉。

### UI Constitution、atlas 人工审阅与提取

`Tsuyomi UI Constitution v1.0-RC` 与 `Tsuyomi UI Prototype Atlas — Executable Specification v1.0-RC` 是本计划的 UI gate 输入。Atlas 必须保持临时、自包含、fixture-only：不得依赖 production 模块、Room、DataStore、network、真实 source package/branding/credentials/cookies 或 release graph；production 也不得依赖 atlas。它渲染完整 route pages、确定性 fixtures、Standard/E-ink、窗口、字体、locale、TalkBack/DPAD 和 A–K 对照变体，而非 component gallery。

**自动与人工审批边界：**Constitution §18 的 auto-selection 仅用于让 atlas 可比较地编译/渲染；它从不选择最终设计，更不构成用户批准。唯一能选定或推翻自动默认值的是 atlas 手工审阅表中的逐项 named yes/no、备注、A–K 选择、constitution conformance、research binding、`manifest.sha256` 与签署日期。该人工 approval tuple 必须绑定 constitution version、research hash、atlas commit、manifest hash、A–K 每项 evidence path 与决策表。

完整人工批准只会冻结 approval tuple 和允许保留 approval evidence；它**不允许**修改 production 模块、移动 production fixture/golden 或删除 atlas。取得人工批准后，仍必须先获得用户对 Phase 4 production implementation 的单独、明确授权。只有该授权到位，才允许在一个 clean extraction change set 中：(1) 把已批准的 tokens/contracts 重写进生产 `core:ui`/feature API；(2) 移动已批准且已脱敏的 fixtures/goldens；(3) 从 production composables 重新生成 goldens；(4) 彻底删除 `:prototype:ui-atlas`、settings inclusion、未迁移 fixture、component forks 与所有 `Legacy`/`V2`/compat alias；(5) 运行 prototype-leak/DAG/forbidden-import static checks；(6) 用 approval tuple 与 implementation authorization 共同关闭 UI-P0-B。人工 atlas 审批本身永远不是该授权。

### 自动验收、人工批准与最终实现阻断清单

| 类别 | 可自动验证 | 必须人工/显式确认 | 对生产实现的效果 |
|---|---|---|---|
| Foundation contracts | schema/migration、ports/DAG、static forbidden edges、source/transport adversarial tests、transfer allowlist、route/recreation、P1 proof | 不适用 | 未全部通过即阻断相应 4A–4C 实现/合并。 |
| UI Constitution conformance | screenshot/golden、semantics、fontScale、profile/window/locale matrix、deterministic fixture hash | 用户核对完整 page/state/recording、无障碍与 E-ink 体验 | 自动通过不能代替 atlas 人工 gate。 |
| Atlas variants | 完整 A–K renders、capture manifest/hash、initial device capture 可重复性 | 用户逐项选择或否决 layout、row/grid、motion、state art、selection、E-ink reader、source identity、typeface 等方案，并为 A–K 每项登记 evidence path | 未签署或缺证据的选择保持候选，绝不可实现为“用户已批准”。 |
| Production authorization | 不适用 | 用户在 atlas gate 关闭后单独授权实施；之后独立 Designer/Adviser 对最终 head 复审 | 未授权时任何 production feature code 仍被阻断。 |

本 Amendment 不授予 PR、commit、merge 或 production code 的权限；它只使 Phase 4 的后续审阅、原型验证和未来 clean cutover 有同一份无矛盾的基础契约。