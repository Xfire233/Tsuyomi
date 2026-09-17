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

## Presentation transactions

`SettledPositionSnapshot` is the in-memory handoff contract for a compatible presentation transition. It contains locator, locator precision, document identity/revision/digest, session epoch, layout key/revision, and a visual-commit witness. It is not durable and cannot cross a different document identity.

```text
freeze input epoch → capture exact settled locator → cancel old work
→ mount target surface → bounded restore → target visual witness
→ resume durable writes/preload
```

The current mounted exact capture wins. A same-document settled snapshot may be used only while the mounted renderer is rebuilding; degraded/unavailable captures never overwrite it. Any user navigation, source-content change, new layout/session epoch, or cancellation invalidates the transaction. The target surface is allowed to commit only if every provenance value still matches.

Raw-route ↔ structured-forum-reply is a source-content transition, not a compatible renderer switch. It needs a source-declared `DocumentTransform` that returns `exact`, `degraded`, or `unavailable`; absent a tested exact mapping, the UI preserves only an explicit degraded fallback and reports its precision.

## Preview sessions

Progress scrubbing creates `PreviewSession`, owned by `reader/engine`, with document identity/revision, session epoch, metric layout key, immutable plan/geometry revision, target, and preview witness. Preview state is isolated from committed navigation while the mounted body presents the temporary target using frozen layout inputs. This does not require a second visible viewport or coordinator. During pointer hold it cannot alter the committed locator, history, prefetch direction, or source loading.

Input coalesces to one target per frame. A target outside the available plan is `preparing`, never a fabricated percentage preview. On release, the engine accepts only a visual witness for the latest target under the same epochs, then performs one semantic navigation and persists the resulting settled locator. Any key/revision change, user interaction, or cancellation discards the session. E-ink uses label/minimap feedback or release-only navigation; it does not repeatedly render a WYSIWYG viewport.

Cancellation restores the opening semantic viewport before durable settled-position observers resume. A document/session/layout epoch change invalidates the old preview and its witness; late preview work cannot write or restore into a newer owner. Visible-body preview and cancellation are owned by Constitution §14 rather than an independently scrolling preview surface.

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

E-ink uses the global `DisplayEnvironment`; it is not a reader preference. It turns off page transitions, paper curl, animated bars, background images, intermediate layout animation, and nonessential prelayout. The coordinator emits semantic refresh hints only after a settled reader transition.

## Tests

| Level | Contract |
|---|---|
| JVM | locator ordering, settled-capture precedence, switch transaction cancellation, preview witness/epoch invalidation, fake-measurer page boundaries, cache eviction, navigation and chapter traversal |
| Protocol fixture | normalized documents, malformed blocks, stable IDs, thread post identities, catalogue aliases, canonical roles, and verified owner entries |
| Android instrumentation | Compose measurement/draw consistency, reflow, independent preview surface, images, process recreation, volume keys, E-ink commits |
| AVD/device | source chapter open, page/scroll/dual switch, preview commit/cancel, progress restore, no visible stale-layout jump |
