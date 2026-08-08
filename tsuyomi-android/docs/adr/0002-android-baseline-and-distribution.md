<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# ADR 0002: Android 10 baseline and GitHub Releases/F-Droid distribution

- Status: Accepted
- Date: 2026-08-08

## Problem

The native host needs a supported Android baseline and release channels that determine API availability, dependency policy, signing, update behavior, and validation devices.

## Constraints

- The first supported platform is Android.
- WebView login, scoped storage, secure local persistence, background lifecycle handling, and modern Compose UI are required.
- The application must remain distributable outside proprietary app stores.

## Decision

The production Android application ID is `org.tsuyomi.android`. The host uses Kotlin and Jetpack Compose with `minSdk 29` (Android 10). It compiles and targets a current supported Android SDK while preserving runtime verification on API 29.

Public distribution targets GitHub Releases and F-Droid. The production application must not require Google Play Services. Release builds use a protected project-owned signing key; debug and isolated test packages are never represented as production artifacts.

## Rejected alternatives

- Preserve the Flutter application's lower Android baseline: rejected because it increases compatibility branches for devices outside the intended support window.
- Target only the newest Android release: rejected because it unnecessarily excludes Android 10–current devices.
- Depend on Play-only delivery or services: rejected because it conflicts with F-Droid distribution.

## Migration impact

Flutter platform wrappers are not migrated. Android behaviors are reimplemented against API 29+ contracts. The new application ID intentionally does not overwrite the Hikari installation, so user data moves only through explicit export/import. Device validation includes at least one API 29 image and one current API image.

## Verification

- A debug build installs and launches on API 29.
- A release build succeeds without Play Services.
- F-Droid-compatible builds use reproducible declared dependencies and no private artifact repository.
- Test package IDs and signing identities cannot overwrite or masquerade as production builds.
