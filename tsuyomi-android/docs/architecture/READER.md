<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Reader architecture

## Scope and invariants

The initial reader serves normalized remote chapters and forum-thread pages. Local EPUB/TXT import is not a Phase 0–3 dependency, but it must later produce the same `ReaderDocument`; it must not introduce a second renderer or progress model.

The following values are never durable progress: rendered page index, dual-page spread index, pixel scroll position, scroll extent, image load state, or layout cache identity. The only durable truth is the semantic locator described in ADR 0015.

## Content boundary

```text
Extension parser
  → ReaderDocument (validated protocol DTO)
  → ReaderDocumentRepository
  → ReaderSessionCoordinator
  → ScrollSurface | PagedSurface | DualPageSurface
```

`ReaderDocument` is source-neutral and immutable:

```text
DocumentIdentity: sourceId + remoteBookId + chapterId/threadPageId + revision
Document: title, ordered Block[] and contentDigest
Block: heading | paragraph | image | divider | quote | post
Post: postId, author data, optional floor/timestamp/reply reference, Block[]
```

Block IDs are source-owned stable identifiers. For plain chapters they are deterministic normalized-content positions; for forum pages the top-level post block ID is the immutable remote `postId`. The protocol uses plain data only—no HTML, WebView object, Compose type, source parser callback, or raw network response crosses this boundary.

### Future capability-gated post/reply presentation — accepted 2026-10-07

The shared document vocabulary must leave room for typed posts and reply references across source plugins, not a Yamibo-only renderer or persistence model. Preserve stable source/thread/post identity, optional author/floor/time metadata and nested reading blocks; ordinary remote chapters and local publications continue through the same document/locator boundary. Source-neutral typed data enters the host; source HTML/XML, callbacks and platform UI objects do not.

The future Yamibo reply-stream presentation is explicitly switchable by the user, with visible behavior owned by UI Constitution §14. A validated source capability selects supported content modes; a source name, MIME sniff, empty placeholder or global switch does not invent support. Mode changes preserve semantic position only through the existing declared DocumentTransform precision contract, invalidate stale loading/layout/restore work and never turn a percentage fallback into an exact capture.

This is an architectural reservation during 0.5.0, not delivery of a forum source. Do not implement Yamibo parsing/network integration, add an inactive settings control, execute Yamibo validation or create a second Reader now. Future implementation extends the existing mounted Reader/content-mode transition seams; those checks remain NOT_EXECUTED until the source work is authorized.

### 0.5.0 local-input and resource policy — accepted 2026-10-06

The selected entry points are explicit Library import and system file open/share, including bounded multiple-file input. Picker, cold/warm Intent and resumed import share one host-owned admission/duplicate/cancellation state machine; they do not install an extension, import a backup or bypass validation. Read only the URI/FD authority actually granted by the platform, retain a complete private original and report unavailable/revoked providers truthfully. No new bottom navigation item or separate Data import workflow selected.

TXT uses reliable BOM/strict UTF decoding automatically; ambiguous or invalid input requires a decoded preview and explicit encoding choice, including Chinese legacy candidates. Never hide errors with replacement characters or alter original bytes. Chapter detection is a correctable suggestion; without reliable headings use deterministic stable segmentation, not destructive regex splitting. Encoding/segmentation/normalization revision is part of semantic restoration and cannot silently reset bookmarks/progress.

The maintainer selected optional per-publication limited HTTPS resources, superseding the plan's unconditional offline-resource proposal. Default remains no resource network access: import/parsing/normalization never fetches book-declared remote content. Only a trusted host resource owner may fetch supported resource types after explicit permission bound to publication ID/original digest and disclosed public HTTPS origins/types. No script/executable content, arbitrary HTML navigation or external CSS/font execution is granted; any supported style/font resource is normalized as bounded inert data, not a browser runtime.

Before every initial/redirect request, enforce the granted origin/type and public-address boundary; reject HTTP downgrade, local/private/link-local/metadata targets, credentials in URLs and ungranted redirects; DNS/address changes cannot bypass confinement. Bound bytes, decoded pixels, time, concurrency and publication-scoped cache. Never share source cookies/tokens/referrers or trust/capability grants; local resource permission does not authorize login, source API requests or website writes. Denial, missing network, quota or unsupported type has a truthful visible outcome without destroying the original/locator.

Revocation stops new fetches and invalidates in-flight late results under the resource/session epoch. A restored or independent duplicate publication starts without a transferred network grant; grants are not portable metadata/archive authority. External HTTP(S) hyperlinks remain a separate explicit user action with confirmation before system-browser handoff; dangerous URI schemes never execute. Acceptance covers default/denied zero-request behavior, granted fetch, hostile redirect/address/type, revocation and stale results, plus correct offline reading/state retention.

### Real-publication compatibility — authorized 2026-10-09

EPUB admission includes image-based fixed-layout publications and bounded large collections. Preserve validated spine/navigation order, declared page progression, resource identity and complete content. Archive admission accounts for entry count, path uniqueness, per-entry/total expanded bytes and compression limits independently; a finite entry-count bound must accommodate ordinary large collections rather than replace byte accounting. Load/normalize chapters on demand; no all-book pagination or full-text index is a prerequisite for opening, jumping or restoring. Cross-chapter seeks and return anchors reuse the existing exact semantic navigation owner.

Complete initial import/reassociation admission retains its whole-publication safety, link/anchor, resource and compatibility checks. Ordinary reopening of an admitted EPUB/MOBI/AZW3 reuses the original-bound native admission record defined in DATA and normalizes only requested prose sections; it must not repeat every section's admission work. An older or archive-restored original without a current valid record undergoes one explicit re-admission under the native original owner, preserving identity and reading state, before subsequent opens use that path. A corrupt/stale record is never accepted as proof or replaced with partial content. Original-byte verification and necessary format-directory/catalog inspection remain required; this is not a promise that isolated Kindle reconstruction reads only one compressed source record.

EPUB3 prefers its declared navigation document; when none is declared, an actual spine-selected or manifest NCX may supply the directory through the same bounded validation. A malformed declared navigation document is not concealed by fallback. MOBI7 and KF8 retain their authored NCX hierarchy and exact targets rather than substituting a shorter inline guide when NCX exists. Navigation occurrences and spine sections are different counts: Detail and Reader retain authored labels, order, hierarchy and distinct fragment destinations, supplementing genuinely unrepresented sections without merging prose identities. Explicit directory selection targets the section start or authored fragment; Continue Reading retains the saved locator. Current-directory selection resolves semantic block/offset ordering, not only a shared section ID.

Normalize inert SVG wrappers around admitted publication images without executing SVG, scripts or external references. Retain fixed-layout image/progression metadata as typed native data, not raw markup or a separate WebView renderer. UI Constitution owns full-viewport independent-cover fitting, comic scrolling/paging/zoom and gesture boundaries. Zoom/pan is transient presentation state, never a substitute for the durable semantic locator; orientation/mode changes and late resource results retain the established session fencing.

For newly admitted verified image-only fixed-layout spines, use one canonical native image document with deterministic stable per-page block/fragment anchors. Preserve every original page and authored directory label; resolve original spine paths/fragments to the corresponding canonical page anchor. This explicit projection enables continuous images and meaningful dual pairs without a second renderer or cross-document progress model. It does not merge reflowable prose sections or alter their identities. Normalization revision participates in document identity; any incompatible prior location receives an honest precision result, never an invented exact match. Grouping image references does not authorize eager decoding of all images.

Kindle protection detection uses the actual format encryption/protection fields; EXTH identifier presence alone is not encryption. Preserve real DRM rejection, bounded isolated parsing and cancellation. Content reconstruction must terminate within the existing resource budgets without omitting sections or hiding pathological work behind larger timeouts. Parser metadata projection uses the selected parser's actual public fields. Visible compatibility outcomes remain localized and grouped without concealing unsupported or missing content.


## Locator

```text
ReaderLocator
├── document identity
├── stable block ID
├── compact anchor digest
├── Unicode code-point offset in block
└── bounded chapter / whole-book fallbacks
```

Restore order: exact block + anchor + offset, matching block + nearby anchor, matching block bounded progress, then chapter/whole-book fallback. Each result reports exact, degraded, or unavailable precision. A degraded or unavailable capture must not overwrite the latest valid semantic capture during a renderer rebuild.

For thread pages, `postId` is primary. Floor number, physical page, and post index are fallbacks only. This preserves position if pagination or post ordering changes.

## Navigation projections

`ReaderDocument` represents one resolved unit. It must not be overloaded with source-route aliases or a derived forum contents page. `ForumThreadNavigation` (protocol v1) is a separate immutable projection containing original catalogue entries, physical-page/content identity, canonical-versus-alias role, source order, and an optional host-verified owner catalogue.

```text
source catalogue route ─┐
source catalogue alias ─┼→ ForumThreadNavigation → resolved ReaderDocument
owner catalogue entry ──┘                              ↓
                                             semantic ReaderLocator
```

Direct catalogue selection preserves the selected source route. Previous/next uses one distinct physical page in source order. A history resume alone may resolve a `postId`/floor target to another physical page before document load; this resolution is a versioned, cancellable engine transition. Owner catalogue construction is a direct user action outside reader rendering: resolve candidate PID, fetch only bounded target pages, verify thread/post/page/owner/nonempty content, then persist the derived projection under its source fingerprint. Partial, cancelled, unverified, or stale results are not catalogue data.

### Chapter-scoped seek and deliberate Standard scroll edges — accepted 2026-10-09

Reader seek always projects the current chapter only, for every profile and flow. Continuous Standard seek is a continuous chapter-local range; paged/dual seek uses actual measured page/spread stops. A known complete one-stop page plan presents a read-only chapter page position rather than a Slider with a dead range or SetProgress action. It never substitutes whole-book seek or silently changes scope, and an incomplete plan must not invent a one-page total.

Edge eligibility is admitted metadata, not a renderer heuristic. `ReaderDocumentKind` retains `CHAPTER`, `THREAD_PAGE` or `UNKNOWN`, with `UNKNOWN` as the conservative default. Remote decoding preserves declared protocol `chapter`/`threadPage`; declared `threadPage` always wins over caller provenance. For a legacy remote shape with no declared kind, the existing source operation owner may supply explicit `legacyOperationKind=CHAPTER` only after classified operation `chapter`, `parseChapter` and exact requested identity/content admission. Raw/default legacy decoding without that provenance remains `UNKNOWN`. Local section admission explicitly supplies `CHAPTER`. Only admitted `CHAPTER` plus actual mounted top/bottom geometry is eligible; `THREAD_PAGE` and unproven legacy/old normalized `UNKNOWN` cache entries remain ineligible. Never promote a cache entry based on its lookup path or refetch merely to manufacture eligibility: old offline content remains readable, but lacks edge crossing until a separately admitted replacement carries known kind. Viewport end or absent Post blocks never establishes completion. Do not infer forum-thread completion/pagination or guarantees beyond protocol and existing operation admission.

Standard SCROLL may cross only a known real chapter boundary after a NEW pointer drag begins at that boundary, exceeds the deliberate distance threshold in the outward direction and is released: upward at bottom requests the immediate next chapter start; downward at top requests the immediate previous chapter end. Reaching an edge within the existing scroll/fling sequence is insufficient. No velocity/inertial crossing, cancelled/under-threshold crossing or more than one chapter per gesture. The Reader gesture owner must yield to zoomed-image pan, nested table scroll, text selection, Slider and sheets; the transient edge cue is presentation, not a second navigation owner.

The existing generation-fenced foreground chapter owner admits the target before replacing the visible origin. Loading failure, missing adjacent chapter, cancellation or stale work preserves the current chapter's exact visible edge position and does not commit target progress. Previous-end positioning resolves the target's real semantic end through its actual presentation, not a saved unrelated locator. Successful edge traversal is ordinary adjacent reading, not seek/bookmark/directory exploration; preserve existing ordinary settled-position, completion and return-anchor accounting. A loaded subset of a dynamic forum/reply stream is not a complete chapter end. Seamless whole-book scroll remains future EXPERIMENTAL and deferred; no all-book pagination, speculative stream flattening or new cross-document progress model is authorized.

## Presentation transactions

`SettledPositionSnapshot` is the in-memory handoff contract for a compatible presentation transition. It contains locator, locator precision, document identity/revision/digest, session epoch, layout key/revision, and a visual-commit witness. It is not durable and cannot cross a different document identity.

```text
freeze input epoch → capture exact settled locator → cancel old work
→ mount target surface → bounded restore → target visual witness
→ resume durable writes/preload
```

The current mounted exact capture wins. A same-document settled snapshot may be used only while the mounted renderer is rebuilding; degraded/unavailable captures never overwrite it. Any user navigation, source-content change, new layout/session epoch, or cancellation invalidates the transaction. The target surface is allowed to commit only if every provenance value still matches.

Ordinary illustration observations use the document-content lifetime, not the locator/progress epoch. Selecting a bookmark or return position within the same document preserves pending image results; replacing content or disposing the owner cancels and fences them. A cross-chapter internal-link fragment is committed atomically with its accepted chapter selection after snapshot persistence; cancellation, newer navigation or same-chapter ABA cannot inject an old fragment into the new selection.

Raw-route ↔ structured-forum-reply is a source-content transition, not a compatible renderer switch. It needs a source-declared `DocumentTransform` that returns `exact`, `degraded`, or `unavailable`; absent a tested exact mapping, the UI preserves only an explicit degraded fallback and reports its precision.

## Preview sessions

SCROLL scrubbing uses engine-owned `PreviewSession`, `FrozenPreviewPlan` and `PreviewVisualWitness` for its existing target/epoch transaction. PAGED/DUAL scrubbing does not use that page-engine catalog or visual-witness branch: Standard and EINK share the ORIGINAL mounted-body `seekPreview`/`displayPageIndex` path. The latest human correction deletes EINK-specific rendering, image pinning and seek-only chrome collapse, without changing Standard rendering or adding another canvas/overlay/body/window. Normal document layout, table presentation, system bars, immersion and insets remain owned by the original path. Hold does not write durable progress/history; candidatebe95d85d's special preview is human-REJECTED for ghosting and its static pixel equality cannot establish acceptance.

For SCROLL only, existing engine preview input/witness matching and epoch cancellation remain. PAGED/DUAL release captures the committed origin, calls the existing `commitPage` once and uses `commitInternalJump`; it does not await a page-engine visual witness or keep a pending draw-release. Preserve original Standard percentage input mapping; EINK retains native discrete chapter page-stop input mapping. Actual page/spread stops stay chapter-local. The removed bounded-window/60%-area and newer full-viewport SPECIAL paths must not return, and neither label-only nor release-only visible preview replaces mounted-body scrubbing.

Native cancellation or Back discards the uncommitted mounted-body target and restores the origin without progress writes. Callback lifetime, session/restoration owner and layout-generation invalidation reject stale paged releases; late callbacks cannot write or restore into a newer owner. SCROLL retains its own engine preview/witness fencing. Existing internal-jump origin capture and return behavior remain; page release is not upgraded into the deleted witness-gated transaction. Constitution §14/A.1 own the original shared live mounted-body presentation, not an independent EINK rendering surface.

## Non-linear return anchor

A successfully settled non-linear navigation may retain one in-memory `ReturnAnchor` for the active Reader book/session. It captures the exact committed semantic locator immediately before the first progress-track tap, committed seek release, bookmark selection, or directory chapter selection. Later non-linear jumps do not replace it. Preview-only, cancelled, failed, restoration, relayout, preload, and ordinary page/scroll movement never create an anchor.

The anchor carries book identity, locator, capture precision, document/session generation, and a return-in-progress witness. It may resolve across chapters of the same book through the existing bounded generic exact-locator navigation owner, but cannot cross book identity or survive leaving the Reader session/process. Cross-chapter return must not route through durable-bookmark membership validation: the session-only origin need not be a saved bookmark, and bookmark absence alone cannot become a chapter-unavailable failure. Return performs one ordinary generation-fenced semantic navigation, clears the anchor only after the target visibly settles, and never creates a second anchor from that return. Three successfully settled ordinary page/scroll advances also clear it. The anchor is not durable progress, history, a bookmark, or a second locator authority; normal settled-position persistence remains unchanged.

## Session and layout state

`ReaderSessionCoordinator` is the only component allowed to mutate active navigation state. It owns:

- active document/revision and current semantic locator;
- foreground navigation generation and cancellation token;
- requested versus settled location;
- profile/typography/viewport layout key;
- directional prelayout and adjacent-document preload budget;
- durable-progress debounce and lifecycle flush;
- typed load, layout, restore, and source errors.

All session transitions are immutable and versioned. A task may commit only when its document, session, layout key, and navigation generation still match the active session. This prevents late content, image, prefetch, or relayout work from jumping the reader backward.

## Paged layout

`reader/engine` calculates page boundaries incrementally via a `LayoutPort`. Android's `ComposeLayoutPort` wraps `TextMeasurer`. A measured fragment holds both boundary metadata and the same `TextLayoutResult`/draw data used by Canvas. The visible page plan holds only a bounded sliding window; it is never serialized.

```text
semantic locator
  → resolve block/span
  → measure visible fragment(s)
  → immutable PagePlan
  → one stable UI commit
  → low-priority directional expansion after idle
```

Text layout is expensive and depends on width, density, font resolver, line spacing, font scale, and layout direction. The layout key includes all metric-affecting parameters plus document revision. Color-only changes do not invalidate pagination. Cache memory is bounded by layout count and byte budget; a low-memory event discards plans, never locators.

## Scroll and dual page

The scroll surface virtualizes blocks and publishes semantic captures after a settled layout. The paged surface consumes `PagePlan`; dual-page pairs adjacent logical pages but stores the same locator. Crossing a chapter boundary is a coordinator transition, not an adapter side effect. Adjacent preloading is bounded and never replaces the visible document while a gesture or navigation is in flight.

### Bounded adjacent preloading

STANDARD may preload only after the current exact document has visibly mounted and the active route has remained settled for a short bounded interval. One cycle may fetch at most the immediate next chapter plus two not-yet-requested illustrations from the current document. The next document is retained in the existing bounded in-memory `ReaderDocumentCache`; it is not written as an explicit offline/downloaded chapter and cannot create a cache marker. Illustration requests use the existing host media cache and retain its origin, credential, byte and decode limits.

The preload owner captures book identity, current document generation, selected chapter, source package revision and credential revision. Any mismatch, foreground navigation, owner disposal or cancellation stops the work and rejects late results. Offline loads, E-ink, verification-required responses and other typed failures do not start recovery UI or mutate the visible Reader. Preload never writes progress, completion, history, pin/read-later state or a non-linear return anchor. A foreground transition may consume an exact prefetched document; otherwise the normal typed request path remains authoritative.

The accepted 2026-09-12 adaptive-flow contract separates durable requested flow from effective presentation. For a Standard `DUAL` request, an ineligible window temporarily renders `PAGED`; eligible width restores `DUAL` automatically. Window changes never write back the fallback to preferences or replace the global-default/per-book precedence. Use the presentation transaction above to preserve semantic position; a changed layout epoch discards uncommitted preview work. Page/spread indexes remain transient. UI presentation is owned by Constitution §14.1a; portable requested flow is owned by ADR 0008.

Semantic-position bookmarks are separately durable identity-keyed data, not a renderer-owned set or progress mutation. Each bookmark is a `ReaderLocator`; a block plus Unicode code-point offset distinguishes positions in the same document while recapture time and rendered metrics never create another position. Reader receives bookmark state/actions from the existing domain owner; auxiliary tabs do not create independent copies. Their transfer and presence boundaries are owned by ADR 0008.

## Images

Image blocks reserve validated display geometry before decode. Decode and cache keys include document revision, source URL identity, requested dimensions, and display-profile version. Replacing an image never changes a saved locator. E-ink uses bounded grayscale/static rendering and commits the image only after a stable frame is ready.

## E-ink

E-ink uses the global `DisplayEnvironment`; it is not a reader preference. It turns off page transitions, paper curl, animated bars, background images, intermediate layout animation, and nonessential prelayout. Settled transitions share the root's stable drawing and manual-redraw epoch; no advanced refresh coordinator, vendor backend or hardware-waveform guarantee is introduced.

EINK effective presentation remains immediate measured PAGED or eligible DUAL, not continuous scroll; Standard requested flow and one semantic locator survive profile changes. Previous/next controls and optional hardware keys remain. Chapter-only seek/numeric sliders retain point/drag, actual stops, one final commit and cancellation safety, and a known one-stop chapter shows read-only position. The latest2026-10-09 human correction deletes EINK-specific preview and reuses ORIGINAL unchanged Standard live mounted-body preview: no special full-viewport canvas, overlay/second render or old bounded window. Contents/bookmarks/settings retain safe-viewport access and measured narrow left selector/right selected content; project/draft identity and overflow paging remain. UI_CONSTITUTION owns these rules; rollback does not resume global EINK restoration.

## Tests

| Level | Contract |
|---|---|
| JVM | locator ordering, settled-capture precedence, switch transaction cancellation, SCROLL preview witness/epoch invalidation, fake-measurer page boundaries, cache eviction, navigation and chapter traversal |
| Protocol fixture | normalized documents, malformed blocks, stable IDs, thread post identities, catalogue aliases, canonical roles, and verified owner entries |
| Android instrumentation | Compose measurement/draw consistency, reflow, original shared mounted-body scrub preview with transaction isolation (no independent EINK preview surface), images, process recreation, volume keys, E-ink commits |
| AVD/device | source chapter open, page/scroll/dual switch, preview commit/cancel, progress restore, no visible stale-layout jump |
