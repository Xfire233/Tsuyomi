<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# ADR 0014: Global E-ink display profile from the first implementation slice

- Status: Accepted
- Date: 2026-08-08

## Problem

Electronic-ink compatibility affects the entire application: color, motion, invalidation size, image treatment, navigation, list loading, reader input, progress feedback, and refresh cadence. Treating it as a late reader-only switch would require every feature to be rewritten and would leave browsing and system UI poorly matched to E-ink hardware.

## Constraints

- The mode must cover application chrome, browsing, library, settings, dialogs, extension management, WebView handoff, and the reader.
- Android exposes no universal public API for selecting E-ink panel waveforms or forcing a hardware full refresh.
- Device detection is imperfect and must never trap a user in the wrong profile.
- Standard LCD/OLED behavior must remain available on the same build.

## Decision

Tsuyomi implements E-ink as a root-level display profile from the first Compose screen. The persisted preference is `auto`, `standard`, or `eInk`. `auto` enables the profile for locally recognized E-ink manufacturer/model signatures; the user can always force either result. Detection data ships with application releases and is not fetched through telemetry or a remote service.

The application root resolves one immutable `DisplayEnvironment` and provides it to all UI through `core/display` and `core/ui`. Features do not read an E-ink setting directly and do not create feature-specific E-ink switches. Reader and browsing behavior derive from the same effective profile.

The E-ink profile uses opaque monochrome high-contrast chrome, bounded grayscale covers/illustrations and immediate stable replacement, without decorative animation, crossfade, ripple, animated size, blur, gradient, shadow, background image or indefinite spinner. On 2026-10-06 the maintainer explicitly narrowed 0.5.0 to monochrome and deferred color-panel original-image support, superseding the earlier selection made during the same intake. No color-panel capability branch or original-color E-ink mode belongs to this release. Standard image colors are unaffected; do not rewrite verification/login behavior to satisfy a grayscale assertion.

The 2026-10-08 interaction correction supersedes the former E-ink Reader scroll option; the subsequent human-feedback correction supersedes blanket application relayout and numeric/seek slider removal. UI_CONSTITUTION G-05/A.1 alone owns the precise visible layout and pending design choices: retain existing composition except for named corrections, fit actual overflowing content with measured explicit pages, preserve static selected-state semantics, and support accepted sliders through track tap and drag. The Reader settings route retains directly selectable narrow left project navigation and the remaining right content pane, not two full-form columns or sequential-item switching; preserve drafts, values and selected project identity. E-ink Reader uses immediate paged or eligible dual-page presentation with one semantic locator; no continuous scrolling. E-ink presentation cannot overwrite Standard requested-flow preference; return restores that preference. No paper curl/background image under E-ink. Controlled WebView gets host page buttons without rewriting login/verification semantics or native input coordinates; platform-owned system UI is not misrepresented as host-rendered.

Refresh is host-owned generic Android optimization plus the real manual root redraw epoch/invalidation request. The maintainer selected this for 0.5.0 on 2026-10-06, superseding the earlier v1 automatic/quality/balanced/fast coordinator and persisted advanced override commitments. Do not expose/store unimplemented policy values or add a vendor SDK/backend in this scope; stable composition/region optimization does not claim waveform/full-refresh/ghosting control. Future advanced/OEM work needs a separately accepted scope/ADR, real consumers, license review and physical proof.

These decisions define the selected 0.5.0 behavior; current implementation, E-ink execution and deployment authority are recorded separately in PHASE_4 and the owning gate. The maintainer reports one iReader Ocean4 Turbo2025, monochrome 7-inch, matching1264×1680, and explicitly confirms Android10+/API29+ and third-party APK installation. The maintainer will operate it and provide evidence; no agent access is selected. Record exact installed runtime/density/system settings with future authorized evidence, not invented here. Bind exact APK/panel and human results; color-panel physical acceptance is not a 0.5.0 obligation after the explicit scope reduction.

## Rejected alternatives

- Separate browsing and reader E-ink switches: rejected because they create inconsistent global chrome, duplicated conditions, and impossible cross-screen guarantees.
- Add E-ink after the standard UI is complete: rejected because component APIs, navigation, lists, images, and state feedback would already assume animated color displays.
- Force E-ink on every detected model: rejected because device identification can be wrong and users need an override.
- Require vendor SDKs in v1: rejected because the selected scope is portable generic Android optimization.

## Migration impact

The Flutter reference has separate `browsingEInkMode` and `readerEInkMode` settings. During Hikari import, either legacy value being true maps to the single manual `eInk` preference. False or absent legacy values do not disable `auto` detection. E-ink display preference remains Android-local and is not written to `tsuyomi-transfer`.

Every migrated screen must pass both standard and E-ink acceptance before its delivery gate is complete. Extension code never receives the device manufacturer/model or controls rendering and panel refresh.

## Verification

- Every app route can be exercised with forced `standard` and forced `eInk` profiles.
- E-ink mode has no decorative animation, translucent overlay, gradient, blur, shadow elevation, animated spinner, background image, or automatic collapsing chrome.
- Browsing and library flows remain operable with explicit pagination and no swipe-only action.
- Reader page turns, progress seeks, dialogs, images, login handoff, and process recreation preserve state under E-ink mode.
- Generic Android verification distinguishes a full-window redraw request from vendor-specific hardware refresh claims.
- At least one physical E-ink device is required before calling the E-ink profile release-ready.
