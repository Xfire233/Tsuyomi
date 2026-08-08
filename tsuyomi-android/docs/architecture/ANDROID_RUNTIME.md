<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Android runtime architecture

## Application shape

Tsuyomi uses a single-activity Compose application with unidirectional data flow:

```text
Compose route
  → feature ViewModel
  → use case / repository interface
  → core or source implementation
  → Room, DataStore, files, WebView, network, or QuickJS
```

Compose renders immutable screen state and emits user intents. ViewModels own screen-lifetime coordination through structured coroutines and `StateFlow`; they do not expose Android infrastructure to pure domain code. One-shot navigation and system-picker requests are explicit effects, not hidden mutable flags in repositories.

The `app` module is the composition root. Initial dependency wiring uses constructor injection and explicit factories. Runtime implementations are selected only at the application edge; domain and feature modules depend on interfaces.

## Global display environment

Before composing navigation, the application resolves the persisted `auto`/`standard`/`eInk` preference and local device classification into one immutable `DisplayEnvironment`. The root provides it to `core/ui`, all features, and reader UI. Standard and E-ink rendering therefore use the same routes and state holders with different semantic components, motion, image, navigation, and refresh policies.

Features never read device manufacturer/model or maintain their own E-ink flags. They emit semantic refresh hints after stable state commits; `core/display` coalesces those hints and applies the selected automatic, quality, balanced, or fast generic Android redraw policy. The coordinator does not claim vendor waveform control.

Changing the effective profile rebuilds presentation from durable screen state and semantic reader locators. It must not restart source requests, duplicate remote mutations, lose form input, or restore from rendered page numbers.

## Source request path

```text
Feature intent
  → SourceRepository
  → ExtensionManager: installed version + grants
  → QuickJsRuntime: typed method invocation
  → Host API request
  → CapabilityEnforcer
  → Network / Cookie / WebLogin / SourceStorage service
  → normalized protocol result
```

Every privileged operation is checked at the native service immediately before execution. A prior manifest check or JavaScript wrapper check is not sufficient.

Remote library writes require all of the following:

1. the installed manifest declares the exact operation;
2. the user granted that package capability;
3. writeback is enabled for the source;
4. remote removal is separately enabled for `remove`;
5. the call originates from a direct user library action;
6. the target origin and remote shelf are valid for the active extension version.

Import, refresh, login checks, WebView closure, and background work do not satisfy condition 5.

## QuickJS lifecycle

QuickJS-ng runs in the application process but outside the main thread. Each `(extensionId, version)` has one serialized runtime lane. The manager may discard and recreate its context after update, timeout, cancellation failure, memory pressure, or protocol corruption.

Calls carry an ID, deadline, cancellation token, extension identity, and granted-capability snapshot. Results are copied into host-owned immutable values before leaving the runtime boundary. No feature retains a QuickJS value or JNI handle.

The runtime enforces:

- JavaScript memory and stack ceilings;
- wall-time deadline through the QuickJS interrupt handler;
- bounded host-call concurrency and response bytes;
- cancellation when the requesting scope ends;
- no evaluation before package integrity and signature verification;
- no direct filesystem, socket, WebView, Android, Java reflection, or JNI API.

JavaScript exceptions become typed source errors. JNI or engine faults can still terminate the app process; this accepted risk is recorded in ADR 0013.

## Concurrency and state

- Room is the source of truth for library, shelves, progress, installed-extension metadata, grants, and import audit state.
- DataStore is the source of truth for non-secret user preferences, including the local global display preference and advanced logical refresh override.
- Source credentials and cookies are host-owned, source-scoped state.
- Feature code observes database-backed flows; it does not maintain a second durable cache.
- Network and parser work runs off the main thread.
- A book-level mutex serializes conflicting local/remote library mutations without globally blocking unrelated books.
- Progress writes are conflated during active reading and flushed on chapter change, app backgrounding, reader exit, and explicit navigation.

## Failure model

Errors crossing feature boundaries are typed as cancellation, offline, authentication required, verification required, permission denied, rate limited, source changed, malformed source data, extension incompatible, extension revoked, runtime limit exceeded, storage failure, or unknown internal failure.

Raw HTML, cookies, tokens, JavaScript stack payloads containing page content, and database exceptions are never shown directly to users or written to normal logs. Diagnostics use stable error codes plus redacted context.
