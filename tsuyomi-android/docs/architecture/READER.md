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

Progress scrubbing creates `PreviewSession`, owned by `reader/engine`, with document identity/revision, session epoch, metric layout key, immutable plan/geometry revision, target, and preview witness. The preview surface shares the frozen page plan/layout inputs and visual components, but owns an independent viewport controller. It cannot alter active locator, history, prefetch direction, or source loading during pointer hold.

Input coalesces to one target per frame. A target outside the available plan is `preparing`, never a fabricated percentage preview. On release, the engine accepts only a visual witness for the latest target under the same epochs, then performs one semantic navigation and persists the resulting settled locator. Any key/revision change, user interaction, or cancellation discards the session. E-ink uses label/minimap feedback or release-only navigation; it does not repeatedly render a WYSIWYG viewport.

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
