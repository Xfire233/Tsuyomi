<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# ADR 0004: Protocol-first cross-platform strategy; defer KMP

- Status: Accepted
- Date: 2026-08-08

## Problem

Tsuyomi needs cross-platform-compatible source packages and portable user data without forcing the first Android implementation into premature shared-runtime abstractions.

## Constraints

- Android is the only committed host implementation.
- Source extensions and transfer documents must remain platform-neutral.
- Host security, storage, WebView, lifecycle, and UI behavior are platform-specific.

## Decision

Cross-platform compatibility is defined by versioned protocols: JSON Schemas, canonical fixtures, conformance cases, `.hxp` package rules, and host API semantics. The Android host implements those contracts natively.

Kotlin Multiplatform is deferred. No Android module may be shaped around a hypothetical iOS host. If another host is created, it implements the same protocol independently and shares code only after two real implementations demonstrate a stable common boundary.

## Rejected alternatives

- Start with KMP shared domain and runtime modules: rejected because no second host currently validates the abstraction.
- Keep source implementations embedded in each host: rejected because it duplicates volatile parser and source logic.
- Treat informal TypeScript types as the protocol: rejected because they are not language-neutral or independently testable.

## Migration impact

Flutter models are translated into protocol concepts only when they have portable semantics. Android-only preferences, credentials, caches, and histories remain native data.

## Verification

- Protocol repositories contain no Android, Compose, Room, WebView, or platform database types.
- Android consumes released/versioned protocol artifacts rather than protocol implementation source.
- Fixtures can be validated without an Android SDK.
