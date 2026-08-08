<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Global E-ink architecture

## Product invariant

E-ink is a global display profile, not a reader feature. The first launch, welcome flow, permissions, extension installation, browsing, search, library, book detail, settings, backup, dialogs, WebView transition, and reader must all be usable with it enabled.

The visual direction is print-like and utilitarian: black-and-white application chrome, strong typographic hierarchy, visible boundaries, stable geometry, and deliberate whitespace. Color is never the only carrier of meaning. Covers, illustrations, and browser content may use controlled grayscale.

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
- refresh policy and redraw coordinator;
- reduced-overdraw and stable-layout flags;
- accessibility contrast requirements.

It is provided at the app root. A component receives semantic tokens and policies; it does not query DataStore or inspect `Build.MODEL`.

## Rendering policy

### Standard profile

Uses the normal Tsuyomi Ink Material 3 tokens, optional dynamic color, elevation, and restrained motion.

### E-ink profile

- UI chrome uses black, white, and a small documented neutral ramp.
- Covers and illustrations are decoded to bounded grayscale; animated images render a static frame.
- Dynamic color, gradients, alpha-dependent distinction, blur, scrims, tonal elevation, and background images are disabled.
- Surfaces use spacing, one-pixel/high-density-safe rules, weight, and fill inversion instead of shadows.
- Crossfade, ripple, shimmer, animated visibility/size, scrolling app bars, paper curl, overscroll glow, and indefinite progress animation are disabled.
- Loading uses stable skeleton geometry only when it does not animate; otherwise use reserved space plus text status.
- Large state updates are assembled off-screen in immutable state and committed once, avoiding multiple visible intermediate recompositions.
- Components keep stable keys and dimensions so a content update invalidates the smallest practical region.

Features must not implement `if (eInk)` branches for colors and durations throughout screen code. Those differences belong in semantic components, motion specifications, list policies, reader policies, and `DisplayEnvironment`.

## Interaction policy

- Infinite scroll becomes explicit pagination with previous, next, page status, and refresh.
- Pull-to-refresh receives an always-visible refresh action; no essential action is gesture-only.
- Collapsing app bars and animated tab/source transitions become fixed chrome and immediate selection.
- Navigation replaces content immediately and restores focus/scroll/page state deterministically.
- Transient messages that could disappear between slow panel refreshes use persistent inline or dialog feedback for important outcomes.
- Touch targets remain at least the accessibility minimum; density is reduced by simplifying decoration, not shrinking controls.
- Reader page turns use tap zones and optional hardware volume keys. Scrolling mode uses screen-sized moves for hardware-key navigation.

## Refresh behavior

Gate 1 exposes only a real manual redraw request while the effective profile is E-ink. It increments
the root redraw epoch and invalidates the stable Compose drawing layer. The control is hidden under
standard profile and never claims a vendor waveform or hardware full refresh.

Future logical policies may be introduced only with a coordinator that consumes every exposed value
and produces testable differences. Candidate semantics are:

| Policy | Required observable behavior before exposure |
|---|---|
| Automatic | Select behavior from semantic hints and coalesce related state transactions. |
| Quality | Prefer complete stable commits and broader invalidation. |
| Balanced | Bound invalidation while periodically scheduling a stable-root redraw. |
| Fast | Minimize invalidation and defer nonessential detail until interaction settles. |

Until that coordinator exists, these values are design vocabulary only: they are not persisted,
shown in settings, or represented as effective runtime state.

Future refresh hints remain semantic (`navigation`, `dialog`, `pageTurn`, `scrollPage`, `imageReady`,
`contentBatch`, `webViewReturn`, `manualRefresh`, `ghostingRecovery`). Features may emit them only
after the coordinator contract and exhaustive dispatch tests exist.

## Images and WebView

Image loading reserves final geometry before decode. The E-ink profile avoids progressive fades, animated placeholders, and repeated resolution swaps. Decode size is bounded to the displayed region. Covers may be grayscale-converted and cached by source digest plus display-profile version.

A controlled WebView cannot be forced to follow every Compose rendering rule. The host uses high-contrast surrounding chrome, disables host transitions, keeps navigation explicit, and requests one stable redraw after return. It must not inject scripts that alter source verification or login behavior merely to optimize E-ink rendering.

## Reader

The reader consumes the same global profile; there is no independent reader E-ink switch. E-ink defaults are:

- high-contrast solid foreground/background;
- no background image, paper curl, animated bars, slider tween, or page transition;
- stable page replacement and semantic locator preservation;
- bounded prelayout/predecode work before the visible commit;
- no spinner over chapter content;
- screen-sized volume-key movement in scroll mode;
- explicit page/position status that updates once per committed navigation.

Changing display profile must preserve the semantic locator and rebuild layout from that locator rather than from a rendered page index.

## Testing contract

Every reusable UI component needs standard and E-ink previews or host tests. Tests verify policy selection and stable state transitions, not source-text checks for animation APIs.

End-to-end E-ink acceptance requires:

- forced profile on an emulator for deterministic navigation and state checks;
- physical E-ink device evidence for ghosting, perceived refresh cadence, tap/volume-key behavior, images, dialogs, and WebView return;
- a standard-profile regression pass on the same build;
- captured device/model, Android version, selected logical refresh policy, and observed limitations without private user data.
