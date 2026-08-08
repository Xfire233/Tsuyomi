<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# ADR 0010: Tsuyomi Ink Material 3 system and reader-specific low-stimulus themes

- Status: Accepted
- Date: 2026-08-08

## Problem

The application needs a coherent native Android visual system while keeping the reader stable, accessible, and low-distraction across phones, tablets, dark environments, and electronic-ink devices.

## Constraints

- The first public UI language is Simplified Chinese.
- General application surfaces and long-form reading have different contrast, motion, and density requirements.
- Dynamic color is not available or desirable on every device.

## Decision

Tsuyomi Ink is a Material 3 Compose design system owned by `core/ui`. It defines semantic color, typography, spacing, shape, elevation, motion, iconography, and adaptive layout tokens. Feature modules consume semantic components and tokens rather than defining independent visual systems.

Application chrome may use system dynamic color when available and enabled. Reader themes are separate low-stimulus palettes with explicit foreground/background values, controlled contrast, reduced ornamentation, and optional motion reduction. Reader content does not change color or pagination unexpectedly because the system palette changed.

Phone, tablet, landscape, and large-window layouts share behavior but may use different navigation and pane arrangements. The global E-ink display profile defined by ADR 0014 is a first-class Tsuyomi Ink token and interaction set, not a reader-only theme or a separate feature implementation. It replaces dynamic color, motion, translucency, elevation, and scroll-dependent chrome across the whole application while allowing controlled grayscale content.

## Rejected alternatives

- Copy the Flutter widget styling directly: rejected because it does not establish native Compose semantics or adaptive layouts.
- Apply dynamic color unconditionally to reading content: rejected because it reduces predictability and can harm long-form readability.
- Let each feature choose local colors and components: rejected because it creates inconsistent accessibility and maintenance behavior.

## Migration impact

Flutter settings are mapped to semantic reader and global display preferences where possible. The separate legacy browsing/reader E-ink switches converge into one global profile. Device-specific font paths, background image paths, and rendering-engine selections are not portable defaults. UI parity is measured by supported behavior and information hierarchy, not pixel copying.

## Verification

- Compose previews and device checks cover light, dark, dynamic, low-stimulus, global E-ink, large-font, tablet, and reduced-motion configurations.
- Core actions have accessibility labels and adequate contrast without relying on color alone.
- Display-profile and reader-theme changes preserve semantic progress and do not trigger unexpected navigation.
