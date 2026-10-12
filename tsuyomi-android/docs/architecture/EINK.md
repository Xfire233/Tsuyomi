<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Global E-ink architecture

## Product invariant

E-ink is a global display profile, not a reader feature. The first launch, welcome flow, permissions, extension installation, browsing, search, library, book detail, settings, backup, dialogs, WebView transition, and reader must all be usable with it enabled.

The direction is monochrome high-contrast application chrome, grayscale covers/illustrations, visible boundaries, stable geometry and non-color-only meaning. Final 2026-10-06 scope is monochrome-only for 0.5.0; color-panel original images are deferred, superseding the earlier same-intake selection. Standard colors and verification/login behavior remain unaffected. PHASE_4 owns implementation authorization; current execution follows the UI Constitution's pause/major-UI boundary and the owning gate; profile selection is neither execution permission nor hardware readiness or human approval.

## Effective profile

DataStore persists one preference:

```text
auto | standard | eInk
```

The application root resolves:

```text
preference + local device classification → DisplayEnvironment
```

Precedence is explicit manual choice first, then local `auto` classification. Unknown devices resolve to `standard`. Settings show the effective result and detection reason and always allow manual override. Device signatures are shipped with application versions; no device identifier or classification result is uploaded.

`DisplayEnvironment` is immutable for one composition snapshot and contains:

- effective display kind: standard or E-ink;
- monochrome/grayscale palette policy;
- motion policy;
- image and animated-image policy;
- list and navigation interaction policy;
- real manual-redraw epoch/invalidation policy; no advanced logical coordinator in the selected 0.5.0 scope;
- reduced-overdraw and stable-layout flags;
- accessibility contrast requirements.

It is provided at the app root. A component receives semantic tokens and policies; it does not query DataStore or inspect `Build.MODEL`.

## Rendering policy

### Standard profile

Uses the normal Tsuyomi Ink Material 3 tokens, optional dynamic color, elevation, and restrained motion.

### E-ink profile

- UI chrome uses black, white, and a small documented neutral ramp.
- Covers/illustrations are bounded grayscale; animated images render a static frame. No color-panel detection/original-color E-ink branch is added in0.5.0; later color support requires a separately accepted scope and panel evidence.
- The host root and separate dialog/menu/sheet drawing windows enforce grayscale pixels, including color-font emoji. This complements the bounded image policy without altering document text, layout or accessible labels; returning to Standard removes the drawing filter. Captures are never post-processed to claim compliance.
- Grayscale compositing preserves native WebView drawing, hit targets, focus and page state at the same coordinates. Use the framework-managed drawing layer; do not wrap native-view drawing in a raw root `Canvas.saveLayer`, compensate pointer coordinates, force page zoom or exempt WebView from grayscale. Standard removes the color filter without rewriting the page or its form.
- Dynamic color, gradients, alpha-dependent distinction, blur, scrims, tonal elevation, and background images are disabled.
- Surfaces use spacing, one-pixel/high-density-safe rules, weight, and fill inversion instead of shadows.
- Crossfade, ripple, shimmer, animated visibility/size, scrolling app bars, paper curl, overscroll glow, and indefinite progress animation are disabled.
- Loading uses stable skeleton geometry only when it does not animate; otherwise use reserved space plus text status.
- Large state updates are assembled off-screen in immutable state and committed once, avoiding multiple visible intermediate recompositions.
- Components keep stable keys and dimensions so a content update invalidates the smallest practical region.

Features must not implement `if (eInk)` branches for colors and durations throughout screen code. Those differences belong in semantic components, motion specifications, list policies, reader policies, and `DisplayEnvironment`.

## Interaction policy

- Follow UI_CONSTITUTION G-05/A.1's 2026-10-08 minimal-change and subsequent human-feedback corrections: keep existing composition, styles and grayscale hierarchy except for the named corrections. Only content that actually needs scrolling receives necessary explicit previous/next pages, fitted to the measured safe viewport. Accepted numeric/progress sliders retain track tap and drag; a blanket gesture ban or slider replacement is not the policy. Preserve whole cards/rows, grouping, identity anchors, drafts, task results and Standard preferences. Fitting content acquires no extra page bar or per-control decomposition; no clipped slices, reduced font/targets or fixed-distance button-scroll masquerading as pagination.
- Pull-to-refresh receives an always-visible refresh action; no essential action is gesture-only.
- Collapsing app bars and animated tab/source transitions become fixed chrome and immediate selection.
- Navigation replaces content immediately and restores focus/scroll/page state deterministically.
- Transient messages that could disappear between slow panel refreshes use persistent inline or dialog feedback for important outcomes.
- Touch targets remain at least the accessibility minimum; density is reduced by simplifying decoration, not shrinking controls.
- Reader exposes immediate previous/next page buttons, optional tap zones and hardware volume keys over the same measured paged/eligible dual-page layout. E-ink does not offer continuous scrolling; Standard requested flow remains preserved for return.

## Refresh behavior

The selected 0.5.0 scope is generic stable-rendering optimization plus the real manual redraw request. It increments the root redraw epoch and invalidates the stable Compose layer under E-ink only; never label it vendor waveform/hardware full refresh or guaranteed ghosting removal. ADR0014's prior advanced-policy promise and DATA's persisted override were explicitly superseded on 2026-10-06.

Automatic/quality/balanced/fast strategies, semantic-hint dispatch and OEM backends are not part of the selected 0.5.0 scope. They are neither persisted nor shown as implemented settings. Reopen only through a new accepted scope/ADR with observable consumers, cancellation semantics, dependencies/licenses and physical proof; do not prebuild unused interfaces or configuration.

## Images and WebView

Reserve geometry before bounded decode; no progressive fades, animated placeholders or repeated resolution swaps. Source/resource digest, bounded size and display-profile policy distinguish grayscale E-ink from Standard color cache variants; they must not overwrite each other. Mode changes and late callbacks preserve semantic position/epoch. The image policy is root-owned, without per-feature device guessing or unused color-panel machinery.

A controlled WebView cannot be forced to follow every Compose rendering rule. The host uses high-contrast surrounding chrome, disables host transitions, keeps navigation explicit, and requests one stable redraw after return. It must not inject scripts that alter source verification or login behavior merely to optimize E-ink rendering.

## Reader

The reader consumes the same global profile; there is no independent reader E-ink switch. E-ink defaults are:

For 0.5.0, default to immediate paged presentation without overwriting persisted Standard requested flow. The 2026-10-08 content-pagination correction supersedes E-ink scroll availability, not accepted slider input: eligible dual pages remain available, continuous scrolling does not, and seek/adjustment tracks support tap and drag under UI_CONSTITUTION A.1. Effective E-ink presentation and Standard preference stay distinct; returning restores Standard. All flows share the measured document/semantic locator and stable transaction; block count is never page count.

- high-contrast solid foreground/background;
- no background image, paper curl, animated bars, slider tween, or page transition;
- stable page replacement and semantic locator preservation;
- bounded prelayout/predecode work before the visible commit;
- no spinner over chapter content;
- immediate page-sized hardware-key navigation, with Standard scrolling semantics unchanged;
- explicit page/position status that updates once per committed navigation.

Changing display profile must preserve the semantic locator and rebuild layout from that locator rather than from a rendered page index.

## Testing contract

E-ink execution follows the current pause/major-UI boundary in `UI_CONSTITUTION.md` and the authorization in `QUALITY_GATES.md`. Retained restoration/readiness requirements below remain deferred and unpassed, not current Standard/local-reading execution prerequisites. Reuse existing scoped evidence; profile activation does not authorize a separate component, AVD or physical-device round. Tests verify policy selection and stable state transitions, not source-text checks for animation APIs.

Initial global restoration and an E-ink readiness claim require end-to-end evidence; these are not routine per-change obligations:

- forced profile on an emulator for deterministic navigation and state checks;
- physical E-ink device evidence for ghosting, perceived refresh cadence, tap/volume-key behavior, images, dialogs, and WebView return;
- a standard-profile regression pass on the same build;
- captured device/model, Android version, selected logical refresh policy, and observed limitations without private user data.
