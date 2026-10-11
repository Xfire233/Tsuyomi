<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# ADR 0015: Unified structured reader document and incremental layout engine

- Status: Accepted
- Date: 2026-08-08

## Problem

Tsuyomi must migrate ordinary chapters, image-bearing chapters, and Yamibo-style forum replies while supporting scroll, paged, dual-page, E-ink, font changes, process recreation, WYSIWYG progress preview, source-mode changes, verified owner catalogues, and future sources. A renderer-specific page index, scroll extent, or a separate reply-reader data model cannot remain stable through those changes.

## Research basis

- Hikari's `ReaderLocator` deliberately excludes viewport offsets, scroll extents, and horizontal page indices from durable state; its Yamibo locator prefers `postId` over floor number.
- Hikari's compatible dual-page switch captures a synchronous semantic locator, otherwise uses only a consistent same-content settled capture, cancels old restore work, then retries bounded restoration. A fallback-only capture never replaces the settled semantic capture.
- Hikari's WYSIWYG scrub freezes geometry, coalesces input, and confirms a real painted target before committing; its vertical preview uses an independent non-interactive viewport with the same lazy-item builder.
- Hikari separates forum physical-page normalization from source catalogue rows. Direct selection retains a real row; directional traversal skips duplicate physical pages; only history resume may cross-load a locator's other physical page. Its optional owner catalogue verifies PID → thread/page/post/owner/content before caching.
- Mihon's pager defers chapter replacement until the pager is idle and bounds adjacent preload, avoiding visible state jumps during active navigation.
- Inkwell's MIT implementation demonstrates the useful principle that text measurement output should be the object drawn and that reader progress is chapter plus text position, not page number.
- LightNovelReader's Apache-2.0 public API separates chapter content into composable components, confirming that a source-neutral structured content boundary supports multiple sources.

No source code is adopted by this decision. Any later code adoption requires separate compatibility review and notice entry.

## Decision

Every readable source result is normalized at the extension boundary into an immutable `ReaderDocument`. The initial document vocabulary is `heading`, `paragraph`, `image`, `divider`, `quote`, and `post`. A `post` contains stable forum metadata (`postId`, author identity/display data, optional floor, timestamp, reply reference) plus nested visual blocks. Unknown or malformed blocks fail as typed source data errors; they are not silently rendered as raw HTML.

`ReaderDocument` answers how to render one resolved reading unit. It intentionally does not own source catalogue aliases, physical-page grouping, or derived owner navigation. Forum sources additionally provide a validated `ForumThreadNavigation` projection: original selectable catalogue routes, their canonical physical-page/content identity, source order, and an optional host-verified owner catalogue. Direct catalogue selection retains its requested route; automatic next/previous traverses distinct physical pages; only an explicit history-resume transition may retarget to a different resolved physical page. Source navigation discovery, validation, cache admission, and cancellation remain outside reader rendering.

A document has a stable content identity, source revision/fingerprint, ordered stable block IDs, and a content digest. Source extensions own parsing and normalized block identities. The host owns navigation policy, typography, layout, image decode, cache, reader session state, preview state, and durable progress.

Durable position is a semantic locator: content identity, stable block ID, compact text-anchor digest, Unicode code-point offset inside the block when available, and bounded progress fallbacks. Rendered page number, display spread, pixel position, scroll extent, image decode state, preview geometry, and layout cache key are never durable position. A capture is usable only with its document revision/content digest, content identity, and settled session/layout provenance; `exact`, `degraded`, and `unavailable` are distinct outcomes. A degraded/unavailable result cannot replace an exact settled capture.

`reader/engine` owns session coordination, document traversal, navigation projection resolution, locator restoration, incremental pagination policy, layout keys, cancellation, immutable page-plan state, and presentation transactions. It exposes a measurement port. The Android implementation in `reader/ui` measures with Compose `TextMeasurer` and retains the resulting layout object for the same Canvas draw pass; production pagination must not calculate boundaries with one text implementation and paint with another. Pure engine tests use a deterministic fake measurement port; Android tests validate the Compose port.

Layout is incremental. Initial open resolves the locator, lays out the visible page/blocks plus a small directional window, commits one stable plan, then expands only when idle and within bounded CPU/memory budgets. Every layout task carries a session/document/layout/navigation revision and is discarded on cancellation, profile change, content change, viewport change, or a newer navigation request. Adjacent chapter preload is advisory and cannot change the visible session until the user crosses the boundary.

A compatible presentation switch—scroll, paged, dual page, profile, or metric change over the same normalized document—is a transaction: freeze the input epoch; capture the mounted exact locator or the latest same-document settled capture; cancel old restore/layout work; mount the target surface; restore under bounded retry; require a target-surface visual-commit witness; then resume progress writes and prefetch. User navigation/cancellation wins over every queued restore. A source-content mode change (for example raw forum route to structured reply document) is not compatible by default: it requires a declared, tested semantic translator, otherwise only an explicitly labelled degraded fallback is permitted.

WYSIWYG scrubbing is a transient `PreviewSession`, never progress persistence. It pins document revision, session epoch, metric layout key, immutable page-plan/geometry revision, requested target, and presentation witness. Pointer updates are coalesced. The preview surface reuses the exact renderer inputs and frozen layout snapshot but has independent viewport state; it cannot move the real reader or write history while held. Release commits only a currently valid, visually witnessed target; an invalidated layout or user interaction cancels/re-resolves it. If a target lies outside the bounded measured window, UI shows preparation rather than a false preview. E-ink uses a static textual/minimap preview or release-only navigation—never rapid live preview redraw.

Scroll, paged, and dual-page surfaces consume the same document and locator contract. E-ink is the global display profile: it selects immediate page replacement, constrained prelayout, no paper curl/transition, and a stable visual commit, but it does not create an independent reader data model. A display-profile or typography change reflows from the semantic locator.

### 0.5.0 local-publication selection inputs — accepted 2026-10-06

The maintainer selected comparative P0 feasibility/fidelity before parser integration, within ADR0001's permissive-first/compliant-LGPL policy. The 2026-10-07 comparison below selects the implementation route under the explicit 0.5.0 authorization in PHASE_4. Selection does not relax the single document/locator/Reader invariants or constitute production acceptance.

The selected EPUB profile includes reflowable publication with Chinese mixed text/images, emphasis, ruby, real TOC/internal links, footnotes/endnotes, lists and basic tables. The maintainer's 2026-10-09 real-publication repair authorization extends it to image-based fixed-layout comics, their declared reading progression, bounded large collections and inert SVG image wrappers; it supersedes treating those supplied publications as out of scope. This is not plain-text extraction or a promise of complete arbitrary SVG, complex-vertical or EPUB3 interactivity support. Unsupported features still require truthful compatibility outcomes, but cannot excuse omitted content in the authorized corpus. MOBI/AZW3 delivery remains required, with format-specific protection checks rather than treating identifiers as encryption. All formats use the same native document/locator/Reader; production Library import/open/restore and mounted Reader pagination/locator behavior are the observable seams. The earlier P0 comparison is selection evidence, not acceptance of this expanded profile.

The selected protected-content policy is non-DRM local input only for 0.5.0: identify and explicitly reject protected publications, not misclassify them as corrupt data or wait indefinitely. No LCP/DRM subsystem, key collection or external protected-reader path was selected. This does not conflate ordinary resource encoding/font obfuscation with publication DRM without format-specific inspection.

### 0.5.0 parser route — selected after P0 evidence, 2026-10-07

Use Android's bounded ZIP/XML primitives and strict charset decoding for EPUB/TXT, a scoped MIT `@lingo-reader/mobi-parser` 0.4.6 derivative at `bc912542d9c09f144257205943c6bd3e421d8743` for MOBI7/KF8/hybrid, and the existing source-neutral Compose measurement/rendering route. The app composes the QuickJS implementation inside a non-exported, network-less isolated parser service; `core/publication` consumes narrow content ports and never depends on `source/*`. No HTML WebView, external reader, GPL engine or second layout engine is adopted.

On the owned API29/x86_64 proof device, the MIT route passed exact content/TOC/link/resource oracles for six uncompressed/PalmDOC cases, protected/replica/malformed rejection, a 930,084-byte Chinese body with 10,000 paragraphs and its final anchor, and active-native cancellation followed by a fresh independent book. The large proof used the unchanged 64 MiB QuickJS heap and took 4,509 ms including binding, parse and pipe output. Fix the derivative's quoted-guide lookup and full-input per-byte string/iterator allocations; do not repin the oracle or raise the proof limit to hide the failure.

The libmobi 0.12 comparison reconstructed raw content and resources rapidly (35 ms for its large JNI/pipe primitive), but that raw interface is not the same normalized TOC/link/Reader work. Keep it as comparison evidence, not a second shipped parser or an automatic fallback. The permissive route meets the observed fidelity seam; its license obligations and inherited notices must accompany the exact shipped source and bundle.

P0 measurements are single synthetic-device observations, not P95, complete EPUB conformance, physical E-ink performance or a production capability claim. Production must retain original/expanded/section/resource quotas, bounded on-demand section and binary-resource output, actual process cancellation, trust-boundary validation and typed over-budget outcomes. The old extension runtime's memory/request limits must not be widened for book parsing. Host-owned originals are never transported as giant Binder values or exposed to scripts through filesystem/network APIs.

The production publication runtime retains the proven 64 MiB QuickJS heap. Serialized requests may enter JNI on different worker threads; each operation refreshes QuickJS's stack top before evaluation so the previous thread's stack boundary cannot reject a valid section or binary-resource request. Cancellation still terminates the isolated process; the extension runtime's quotas remain unchanged.

### Future switchable forum presentation — accepted 2026-10-07

The maintainer requires an explicitly enableable/disableable Yamibo reply-stream presentation when that source is implemented, using Hikari Flutter Plus as a visual/interaction reference. Reserve typed post/reply content and source-declared presentation capability in the shared Reader architecture now so other plugins can use it without another renderer, bookmark store or progress model. UI Constitution §14 owns the visible behavior; Reader architecture owns the data/capability boundary. Reuse the existing source-content transform and exact/degraded/unavailable restoration contract rather than Hikari's percentage-based mode conversion. This does not select optional owner-header/owner-catalogue features or authorize Yamibo implementation or validation within 0.5.0.

## Rejected alternatives

- Persist page number or scroll percentage: rejected because layout and content changes invalidate them.
- Keep separate normal-chapter and forum-reply reader persistence: rejected because reply `postId` identity is a first-class semantic anchor and mode transitions would lose precision.
- Treat every source-content mode change as a presentation switch: rejected because source blocks/identities can differ and a percentage conversion silently corrupts progress.
- Render source HTML directly in WebView: rejected because it makes typography, E-ink behavior, progress, caching, and security host-inconsistent.
- Fully paginate every chapter eagerly: rejected because it delays first paint and wastes memory on long chapters.
- Make a preview by mutating the live reader during pointer movement: rejected because it creates false progress/history, stale callbacks, and E-ink redraw churn.
- Use distinct measurement and drawing implementations: rejected because a one-pixel metric difference can cascade into incorrect boundaries.

## Migration impact

Flutter ordinary chapter locators map to block/text anchors. Yamibo locators map `postId` first and retain a verified physical page/floor only as navigation fallbacks. Flutter's mode-switch capture cache maps to a host `SettledPositionSnapshot`; its preview geometry maps to non-durable `PreviewSession` state. Yamibo source catalogue aliases and owner entries map to `ForumThreadNavigation`; unverified source links, failed builds, and partial owner lists are not imported as trusted catalogue entries. Paper curl is not a compatibility requirement. Reader-local cache files are replaced by host-owned normalized document/navigation/cache metadata and invalidated on content/extension revision changes.

## Verification

- Deterministic tests cover exact/degraded/unavailable capture precedence; same-document switch transaction order; stale generation cancellation; visual-commit gating; source-mode translator success/failure; font/width/profile reflow; content revision; missing anchor; adjacent-chapter transition; and intentional backward progress.
- Preview tests cover frozen plan/key use, input coalescing, no history write before release, witness mismatch, layout invalidation, out-of-window preparation, cancellation, and E-ink release-only path.
- Forum-navigation tests cover alias grouping, canonical selection, directional de-duplication, direct-route preservation, history-only cross-page jump, owner PID/thread/page/post/owner/content verification, cache fingerprint invalidation, bounded concurrency, timeout, cancellation, and partial-result rejection.
- Instrumentation tests verify that measured text is the object drawn, no visible mixed revision occurs, and process recreation restores a semantic position.
- Fixture tests cover ordinary chapter, mixed text/image chapter, forum post document, forum navigation projection, and malformed/contradictory source data.
- Forced E-ink profile tests prove immediate navigation and locator preservation without reader-specific E-ink state.
