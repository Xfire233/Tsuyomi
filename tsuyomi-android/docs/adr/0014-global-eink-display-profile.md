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

The E-ink profile uses monochrome, high-contrast UI tokens while allowing controlled grayscale for covers, illustrations, and WebView content. It disables decorative animation, crossfades, ripples, animated size changes, translucent layers, blur, gradients, background images, elevation shadows, and indefinite animated progress. State changes use stable layout, explicit borders, static status text, and immediate visual replacement.

Long lists use explicit page-sized loading and previous/next/refresh actions instead of infinite scroll or pull-to-refresh. Home and section chrome remains stable rather than collapsing with scroll. Reader navigation uses immediate page replacement, stable semantic locators, tap zones and hardware keys; paper curl and background images are unavailable under the effective E-ink profile.

Refresh handling is host-owned. Version 1 provides generic Android optimization only: it coordinates Compose invalidation, suppresses intermediate frames, batches state changes, limits redraw regions where practical, and offers automatic plus advanced quality/balanced/fast policies and an explicit full-window redraw request. It does not claim to control vendor panel waveforms or guarantee a hardware full refresh. Vendor SDK integration requires a later ADR, dependency/license review, and physical-device evidence.

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
