<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# ADR 0005: TypeScript ES Modules on QuickJS-ng rather than APK extensions

- Status: Accepted
- Date: 2026-08-08

## Problem

Source integrations change independently from the Android host and need portable execution, constrained capabilities, deterministic packaging, and reviewable updates.

## Constraints

- Source code must not receive unrestricted Android APIs.
- Extensions must work across future hosts without shipping host-specific bytecode.
- Execution and network resource use require host-enforced limits.

## Decision

Source integrations are authored in TypeScript, compiled to standards-based JavaScript ES Modules, and packaged as signed `.hxp` archives. Android evaluates source modules with QuickJS-ng through a narrow, versioned host API.

Packages contain source modules, metadata, assets, integrity data, and signatures. They never contain Android DEX/APK code or host-specific QuickJS bytecode. The host owns HTTP transport, cookies, controlled WebView login, storage, timeouts, concurrency, response ceilings, memory limits, and cancellation.

QuickJS-ng is an execution engine, not the sole security boundary. Package verification and every privileged host operation are enforced outside the JavaScript runtime.

## Rejected alternatives

- Android APK plug-ins: rejected because they create unrestricted platform code execution and cannot be portable.
- WebView as the general extension runtime: rejected because it has a broader attack surface and weak deterministic resource control.
- Remote JavaScript downloaded without signed packaging: rejected because update integrity and capability review would be unreliable.

## Migration impact

Flutter source APIs and parsers are ported source by source into the public extension contract. Android-specific WebView and cookie behavior moves into host services. Source-specific semantics remain in each extension rather than a generic parser abstraction.

## Verification

- An extension cannot access Java, Android classes, arbitrary files, or undeclared network origins.
- CPU, wall-time, memory, response-size, and cancellation limits are tested.
- The same `.hxp` fixture is consumable by any conforming host implementation.
