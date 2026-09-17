<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Changelog

All notable changes use semantic versioning. Future Phase baselines use annotated `phase-N-baseline` tags; immutable historical `gate-1-baseline` and `gate-2-baseline` tags retain their published names.

## [0.3.0-beta.4] - 2026-09-17

### Changed

- Private candidate builds are now individually identifiable: `versionCode` advanced to `6` and `versionName` to `0.3.0-beta.4`, and `-Ptsuyomi.buildFingerprint=<value>` appends a per-candidate suffix (for example `0.3.0-beta.4+justify6`) that appears in `dumpsys package org.tsuyomi.android` and the in-app About screen. Previously six distinct `0.3.0-beta.3` APKs shared `versionCode=5`, so `install -r` could silently keep an older build.
- Fix: Detail tag rows no longer overflow the outlined region and no longer wrap early. There is now
  exactly one horizontal gap, the minimum `Xs`: rows are packed at the same gap a non-justified row is
  placed with. Packing narrower let a row that only just fitted run past the edge once the wider gap was
  applied, and because the wrap decision had already been taken it never wrapped - measured before the fix
  as 11 overflowing rows across a sweep of 300-430dp widths and 2-14 tags, worst 38px past the flow and
  25px past the region. Packing wider cost row capacity: with an 8dp packing gap the same six-tag,
  13-character row wrapped until the flow reached 321.9dp, and with the 4dp minimum it fits from 301.6dp,
  a 20dp gain equal to the five gaps that row carries. Guarded by
  `BookDetailInstrumentedTest.tagRowsNeverOverflowTheOutlinedRegionAcrossWidthsAndCounts`, which asserts
  both that no row overflows and that no row wraps while its successor would still have fitted.
- Detail tag rows justify only when a row is full, and every full row shares **one** gap. Packing at the
  minimum gap makes every row but the last full by construction, so those rows share the smallest capacity
  any of them can absorb; when their content widths are equal, which is what identically sized tags wrapping
  produces, that shared gap also fills each row to the trailing edge, so a wrapped block is column-aligned
  and justified at once. A row that still had room for another tag is not full, so it keeps the minimum `Xs`
  gap on the leading edge and two tags in a wide row are never torn to opposite edges. The final row adopts
  the shared gap when it can afford it.
  Three earlier attempts are superseded: per-row gaps let adjacent rows show visibly different spacing
  (measured 136px vs 36px vs 30px at 320dp), forcing one gap on every row left full rows short of the edge
  while still stretching sparse rows apart, and a separate 8dp default gap both overflowed rows packed at
  the 4dp floor and silently reduced how many tags fitted per row.
- Performance: the normalized-source snapshot write (JSON encode, temp file, fsync, atomic rename) ran on
  the caller's dispatcher, which is the UI thread when reached from a `LaunchedEffect`, so it stalled the
  frame between parsing and rendering on every Detail open. It now runs on `Dispatchers.IO`. Measured on the
  review AVD: 12-29ms of UI-thread work removed per open for a 100-800 chapter directory.
- Performance: the cached-chapter probe read and decoded every chapter document in full, per chapter, on
  every Detail open, purely for the cached badges. Probing by path also canonicalised the target on each
  call, costing a filesystem syscall per chapter even when nothing was cached. A single directory listing
  now answers the same question with identical results. Measured on the review AVD over 800 chapters: 143ms
  to 68ms with nothing cached, 339ms to 69ms with all 800 cached.
- Performance: covers fetched two at a time and held that slot across decoding and the disk write, so a
  slow decode idled a network slot that a visible cover was waiting for. The bounded queue now covers the
  source exchange only - cache hits, decode and disk writes run outside it - and the bound is eight, paced
  against the reference reader's image fetcher, which runs ten concurrent cover downloads. Measured on the
  review AVD over the identical twelve uncached covers: arrivals were spread across 1.0-2.5s before and
  compressed to 1.1-1.2s after, with the grid complete at 1.7s instead of 3.1s. The gain scales with
  latency, because the round count falls from six to two.
- Detail's border-embedded add-tag `+` now uses the active theme primary action color while the region border and `标签` retain the outline role; its transparent 48dp target, compact 16dp glyph, page-matched border interruption, and borderless/fill-less presentation are unchanged.
- Standard Reader now starts one settled, bounded preload cycle for the immediate next chapter and up to two not-yet-requested current-chapter illustrations. Prefetched chapters remain in bounded memory only, foreground navigation consumes exact matches without a duplicate source request, and stale, offline, E-ink, verification-required, failed, or cancelled work cannot replace visible Reader state or write reading metadata.

## [0.3.0-beta.3] - 2026-09-15

### Added

- Official source discovery in Standard Browse: installed/available sections, independent plugin search, bounded rows and scrollable source/license/publisher details, explicit installation approval and manual updates; local HXP import remains available.
- Root-signed static catalog admission, exact HXP binding, durable replay and revocation state, total download deadlines, and shared foreground/background trust reconciliation without removing installed archives or user data. Production keys and formal catalog/package publication are separately authorized; unconfigured builds report the repository as unavailable.
- Persistent third-party repository subscriptions from canonical explicit-root links, with address/fingerprint confirmation, independent disable/removal, and retained revocation/antirollback history.
- Verified local publisher-key input and separate non-official execution consent bound to the exact source, publisher and archive; cancelling or supplying the wrong key never authorizes execution. Built-in identities cannot be shadowed by a subscribed root.
- Cancellable source uninstall and same-identity reinstall retaining host books, progress and credentials, while fencing requests and clearing obsolete source navigation. Cold start reconciles missing archives as dormant without repeatedly invalidating them.
- Private-Beta corrections: unbacked verification controls, shared three-column Standard phone source grids, Ready-first bounded cover reentry, and cached source content/viewport restoration without a duplicate pre-navigation session open.
- Detail Cache now opens explicit chapter selection and persists only selected normalized chapter documents, with queued/working/cached/failed/cancelled states, cancellation and unchanged Reader progress; metadata-only cache success is removed. Compact tags and the existing unread FilterChip retain accessible interaction bounds.
- Reader presets now provide complete paired semantic palettes and settings sheets explicitly pair foreground/container roles; custom document colors do not recolor settings/chrome. Selected navigation and segmented controls use their matching container foreground roles.
- Semantic-position Reader bookmarks replace chapter toggles, with honest Room v12 migration of legacy chapter marks, transfer v5 union semantics, idempotent removal, same-chapter restoration and unchanged pin/progress state. Transactional bounded reads retain the 20,000-position limit without materializing duplicate full entity lists.
- Standard Reader quick/full settings keep complete typography controls in their owned scroll viewport; directory and bookmark sheets use compact rows, actual partial-viewport centering and reliable Back dismissal. Page motion respects reduced motion and cancelled seek previews cannot commit later.
- Standard paged Reader swipes now move the current and adjacent spreads directly with the finger, settle from the release position, and commit one semantic page only after accepted settlement. Enabled volume-key paging consumes the complete hardware press so Android does not change volume or show its overlay; disabled paging leaves the system keys unowned.
- Tag management restores local/source ownership and chip/list layout, preserves source-qualified identities and read-only source tags, and includes independently present unpinned Read Later books. Detail tag entry preserves one full 48dp hit target through wrapping and large fonts.
- Continue Reading records actual Reader visits independently of pinning and progress, including unpinned and completed books, with Room v13 persistence and legacy semantic-progress fallback.
- Standard cover presentation defaults to 5:7 with a persisted 16:9 alternative, complete portrait artwork and a smaller shared title role. Unsupported cover tokens remain read-only until explicit preference reset; frozen E-ink presentation is unchanged.
- Reader auxiliary state survives temporary chapter loading. Continuous reading restores and captures within-paragraph Unicode positions across bookmark jumps and typography reflow without writing progress merely for restoration.
- Retained Source Home distinguishes admitted query pages from temporary replacement content, preserves cached selection against late results, makes initial retry actionable, and fences retired pagination across refresh and session replacement.
- Revoked cover gateways remain revoked after an identical source partition is reactivated. Standard wide-card selection, drag previews and insertion slots share accessible, font-aware geometry.
- Aggregate Search coalesces repeated in-flight submissions before source execution. Interrupted or fixed-length-truncated responses now surface as retryable network failures instead of source-page structure errors.
- Explicit installed-source selection is now persisted atomically and restored across runtime or Activity recreation instead of falling back to installed-list order; stale or uninstalled selections fail closed to an available trusted source.

- Phase 4A Standard Library production cutover; accepted Atlas-era behavior was migrated into production and the prototype was retired:
  - Stationary platform-threshold long-press multi-selection across Grid, List, and Compact layouts.
  - `SelectionAppBar` with item count, select all, clear all, batch add/move to collections, and local deletion.
  - Drag-and-drop interactions: book-on-book collection creation, book-into-collection, root shelf insertion with expanding zero-width gaps and sibling displacement, and shortcut reordering.
  - Locked and unlocked shortcut shelf: locked pins the full shelf below the AppBar while keeping full drag/drop, insertion, and reorder capability; unlocked scrolls inline, collapses to a >=48dp chevron handle, expands via click, reverse-scroll, or dragged-book hover, and preserves scroll anchors.
  - One-continuous-hold drag pickup for unselected books and direct shortcuts without requiring a prior selection step.
  - Room database v4 migration adding `display_order` to preserve custom root shelf and manual collection order.
- Source Home production integration:
  - Wenku8 recommendation sections (`7月新番`, `新书风云榜`, `本周会员推荐榜`) and “这本轻小说真厉害！” feature card opening dedicated cached ranking views.
  - Shared start-aligned actual-label-width text tabs, centered tag container with bounded M3 expand/collapse motion, and scroll-direction-aware FAB.
- Book Detail and Reader production alignment with accepted Atlas-era decisions:
  - Cover image loader with media policy, rating badge alignment, and compact action headers.
  - Reader pagination contracts, reader chrome, and reorganized settings panel.

- Phase 4B website-library mirrors and explicit single-book writeback:
  - Durable Room v5–v8 mirror, reconciliation, COPY-consent and exact chapter-completion storage.
  - Root/group mirror shortcuts, frozen missing-target restoration and Library-native grid/list/compact rendering.
  - Operation-specific `ADD`/`MOVE`/`REMOVE` authorization, separately signed target discovery, cancellation-safe retry and process-resumable targeted ADD→MOVE.
  - Local-only COPY and ordinary `加入书架`, with website actions and persistent outcomes kept visually and semantically distinct.
- `tsuyomi-transfer` v3 export with explicit local pin state and strict v1/v2/v3 import, preserving retained unpinned annotations without repinning during restoration.
- Phase 4C local update inbox and optional scheduling:
  - Signed, read-only `update-check-v2` and Wenku8 ordered-directory evidence; the first trusted baseline is silent, and ambiguous directory changes preserve pending updates.
  - Room v9 sessions, fenced recovery, incremental inbox, bounded reports, per-book/source exclusions and exact-anchor handling/Undo.
  - Default-off WorkManager schedules with persistent constraints and in-app progress/results/cancellation even when notifications are denied.
  - Library-native cover grid, cover list and compact layouts; canonical Detail focus and direct reading also work for mirror-only books without creating local pins.
  - Automatic handling requires durable completion of every admitted new chapter, never a locator or only the last chapter.
- Dedicated local Library search with latest-wins metadata/folder matching, bounded recommendations and immediate explicit submission; source search remains separate.

### Changed

- Repository downloads now detect truncated response bodies and recover once from transient EOF/connection resets within the original total deadline, discarding partial bytes and retaining all HTTPS, size, signature and approval checks.
- Browse separates download, verification, repository-state and storage failures. Repository download errors retry the exact source/repository instead of opening a file picker; unavailable or removed repositories return to the catalog, and retries still require package approval.
- Repository link inspection dismisses its input keyboard before root review; official signed revocations remain effective for active sessions even after the publisher leaves the current catalog.
- Shared Standard cover metadata keeps its transparent gradient transition above the text so bright artwork and large fonts retain contrast; structural cards and grid drag previews reuse the same renderer.
- Superseded numbered delivery `Gate` scopes with `Phase 0–5` (including 4A/4B/4C); reserved gate terminology for explicit admission, review, authorization and release checkpoints.
- Restored Atlas resting shortcut tile dimensions (`80×116dp`), media field (`76dp`), single-line labels, and target-specific collection hover feedback.
- Remote Library refresh now exits its working state after every terminal content, empty, login-required, verification-required, cancelled, or safe-error result instead of leaving refresh controls stuck loading.
- Remote ADD now coalesces concurrent taps to one website write, preserves an accepted unresolved attempt when a later retry fails before acceptance, and closes the unresolved retry chain only after an explicit retry confirms `APPLIED` or `ALREADY_PRESENT`.
- Remote mirror and mutation summary writes now preserve richer catalog metadata instead of clearing authors, cover, status, tags or update state.
- Chapter completion is recorded only from an explicit chapter-end transition; semantic locator progress is character-based and no longer implies completion.
- Interface reset removes only display/reader/library-layout/introduction keys and preserves source-flow and import-digest state.
- Retired the `:prototype:ui-atlas` module and duplicate review fixtures; production modules and Review Graph catalog version 9 are the only active UI path.
- Active screenshot evidence now follows the review-policy profile set: retained E-ink previews and goldens stay frozen and unregistered until the explicit restoration pass.
- API 29 regression coverage keeps long-press movement in one pointer stream, bounds aggregate-limit fixtures without large allocations, and waits for the exact fixture DOM before verified-page capture instead of treating a matching URL and 100% load progress as document readiness.
- Detail exposes website MOVE and REMOVE only for books present in the durable website mirror; remote removal keeps local Library and reading data.
- Remote mutation recovery now keeps operation and state from one reconciliation record, makes pre-acceptance cancelled ADD retryable, cancels complete acknowledged MOVE/REMOVE retry chains, and preserves operation-specific receipts across package restoration.
- Remote target decoding now rejects malformed, duplicate, oversized, cross-source, unsupported, and invalid-parent collections as typed source failures instead of leaking model-constructor exceptions.
- Transfer v2 now requires `completedChapterIds` to be an explicit bounded array of unique nonblank strings without tightening legacy v1 author/tag/shelf values.
- Shared segmented selectors now constrain their dividers to intrinsic content height instead of consuming a weighted list's viewport.
- Android CI planning now computes reverse transitive Gradle consumers and selects their existing unit, screenshot, and API 29 instrumentation families for shared production changes.
- HIGH-mode local CI and hosted Android checks share one planner-driven disposable API 29 runner and profile, with isolated device ownership, failure artifacts and phase timings; LOW uses hosted CI only.
- Accepted verified pages reopen their native source session before returning to the requesting route, preventing resumed directory reads from racing a closed session while preserving the parsed result without native replay.
- Update-check reports preserve source error category, host stage and bounded safe diagnostic code instead of collapsing failures to an uninformative message; raw exception messages, URLs, credentials and response bodies remain excluded.
- Detail's destination dropdown puts website actions before all local manual collections so long local lists cannot bury a frequent remote action; preserves immediate independent actions and the complete local list without extra menu levels.
- Detail's SplitButton primary is text-only, with no redundant bookshelf icon or icon spacer; compact 12dp horizontal padding and content-sized fallback preserve full labels and the 48dp disclosure target.
- Selectively migrated ordinary Standard text buttons to graduated Material3 ButtonShapes with restrained press corners, unchanged labels/touch geometry, and immediate visible feedback under static motion. Toggle/IconButton families and the global theme identity are unchanged; E-ink remains frozen.
- Migrated existing Detail SplitButtonLayout and full-screen verification-toolbar ownership to the pinned graduated APIs without adding Expressive caller opt-ins or changing their business actions.
- Upgraded the compatible Compose/Material3, AGP/Gradle, built-in Kotlin/KSP, Screenshot and compile-SDK toolchain while retaining minSdk29, targetSdk36 and JVM17.
- Library now combines immediately applied filtering and sorting in one bounded panel, with separate sections, filter-only clearing and independent layout switching. System tabs preserve but do not expose the root filter.
- Library and Source Home shared text tabs use native horizontal Pager settling with a shorter intentional-drag threshold, velocity-based switching and per-page context preservation; sub-threshold returns stay continuous and book dragging retains gesture ownership. Selection commits only after full page alignment, preventing past-center releases from interrupting native settling and leaving adjacent covers visible.
- Continuing a Library book opens Reader directly and preserves the caller instead of inserting a hidden Browse relay into root history. Returning to Library refreshes its local snapshot so saved progress immediately affects Continue Reading; cancellation no longer becomes a spurious Library refresh failure.
- Compact Detail filters size to their labels rather than pushing the ordering action offscreen. Narrow or large-font directory headers wrap their control group while keeping both 48dp targets reachable; the order action uses the current direction glyph.
- Floating verification exit/reopen actions use independent tonal icon surfaces, keeping night-theme glyphs readable over a white webpage without restoring the removed shared toolbar backplate.
- Detail's selected `已在书架` primary now requests the existing local-removal confirmation; its independent destination disclosure and working-state guard remain intact.
- Room v10 separates the local pin from retained annotations. Confirmed removal clears the pin and direct manual memberships while preserving rating, tags, Read Later and reading state; Read Later remains independently accessible and re-add preserves metadata.

## [0.1.0] - 2026-08-09

### Added

- Phase 1 Android host shell, global standard/E-ink display profile, semantic Compose components, Room/file/credential foundations, and Reader JVM transition contracts.
- API 29 runtime acceptance recipes, real-screen screenshot baselines, lint, dependency verification, and REUSE compliance.

### Changed

- E-ink manual redraw appears only when the effective profile is E-ink.
- Removed unimplemented logical refresh policy settings and persistence until a real coordinator consumes them.
