<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# ADR 0013: In-process QuickJS runtime with a constrained host boundary

- Status: Accepted
- Date: 2026-08-08

## Problem

QuickJS-ng can run inside the Android application process, a normal secondary process, or an Android isolated process. The choice changes implementation cost, call overhead, failure containment, and the strength of the security boundary.

## Constraints

- Extension calls must not execute JavaScript on the main thread.
- Extensions receive only the versioned host API and cannot be given Java, JNI, Android, file, or raw socket objects.
- QuickJS-ng is native code and is not, by itself, a complete security sandbox.
- The first vertical slice must keep runtime integration and debugging tractable.

## Decision

Version 1 runs QuickJS-ng inside the application process on dedicated runtime threads. Each loaded extension version owns a serialized runtime lane; a QuickJS context is never called concurrently or moved between threads. Host calls cross a typed message boundary and return immutable protocol values or typed errors.

The host configures QuickJS memory limits, stack limits, interrupt/deadline checks, cancellation flags, response-size limits, and per-call wall-time budgets. Timeouts discard the affected context before reuse. JavaScript exceptions are translated at the runtime boundary and never escape into feature code.

Package verification, publisher trust, capability grants, URL policy, cookies, WebView, storage, and network execution remain host-owned. No JNI callback accepts an unvalidated extension-controlled pointer, path, class name, or platform handle.

This decision accepts that a defect in QuickJS-ng or its JNI bridge can terminate or compromise the application process. Publisher-key approval therefore represents process-level code trust, even though ordinary extension operations remain capability constrained. UI for third-party publisher trust must state this risk accurately.

## Rejected alternatives

- Android `isolatedProcess`: rejected for v1 because the additional Binder protocol, process lifecycle, package transfer, and crash-recovery work was not selected for the first milestone.
- Normal secondary process: rejected because it adds IPC and lifecycle complexity without removing same-UID access after native compromise.
- Execute on the main thread: rejected because parser work, infinite loops, and garbage collection would block UI rendering.

## Migration impact

Flutter parsers are moved into signed ES modules, but all HTTP, cookie, WebView, and persistent storage implementations remain native host services. Extension code is reviewed and fixture-tested before signing by the official root.

## Verification

- Runtime calls execute off the main thread and are serialized per context.
- Infinite-loop, allocation-pressure, stack-depth, cancellation, malformed-result, and context-recreation fixtures are covered.
- A timed-out context is not reused.
- Capability and URL checks occur again in the native host service immediately before privileged work.
- Third-party publisher approval text does not describe QuickJS as a secure process sandbox.
