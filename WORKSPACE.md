<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Tsuyomi monorepo workspace

This directory is one Git repository containing three independently versioned components.

| Component | Responsibility |
|---|---|
| `tsuyomi-android` | Kotlin/Compose Android host application. |
| `tsuyomi-protocol` | Platform-neutral JSON Schemas, fixtures, Host API, transfer/backup contracts, and conformance rules. |
| `tsuyomi-extensions` | TypeScript `.hxp` source extensions, packager, signing tools, and sanitized acceptance fixtures. |

## Local prerequisites

- JDK 17.
- Android SDK with API 36, platform-tools, emulator, and `system-images;android-29;default;x86_64`.
- Node.js and npm versions accepted by CI.
- Python with `python -m reuse` 6.2.0.

Set `ANDROID_SDK_ROOT` (or `ANDROID_HOME`) locally, then run `tsuyomi-android/tools/Doctor.ps1`. The script validates tools and generates ignored `local.properties`; no user-specific SDK path belongs in version control.

## Component boundary

The monorepo permits atomic cross-component PRs but not source-level boundary violations. Android, protocol, and extensions interoperate through versioned schemas, DTOs, sanitized fixtures, signed `.hxp` artifacts, and release metadata. Android must not import extension implementation code, and extensions must not receive Android platform handles.

## Change and release order

A cross-component PR updates protocol contracts and fixtures first, extension producers second, and Android consumers last within the same commit series. Components retain independent SemVer and tags:

```text
protocol-vX.Y.Z
extensions-vX.Y.Z
android-vX.Y.Z
gate-N-baseline
```

Every Gate records one monorepo Git SHA plus the exact protocol, extension manifest/SDK, and Android application versions. `latest`, uncommitted local paths, and branch names are not compatibility references.
