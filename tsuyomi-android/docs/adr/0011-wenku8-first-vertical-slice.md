<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# ADR 0011: Wenku8-first end-to-end vertical slice

- Status: Accepted
- Date: 2026-08-08

## Problem

The migration spans a native host, a protocol, an extension runtime, three volatile sources, a reader, persistence, and backup. Building every subsystem or every source horizontally before exercising the complete path would defer integration risk.

## Constraints

- Wenku8, ESJZone, and Yamibo must ultimately use the same public extension contract.
- WebView, cookies, extension execution, reader locators, storage, and transfer cannot be validated independently as a complete product.
- Live-site behavior is unsuitable for deterministic CI.

## Decision

The first deliverable is one complete Wenku8 vertical slice:

`install → grant → login/verification → search → detail → directory → chapter → reader → progress → library → transfer export/import`.

Automated coverage uses sanitized recorded fixtures and mocked host transport. Only controlled WebView handoff, Android persistence, file picker, and final reader restoration require AVD/device evidence.

ESJZone and Yamibo implementation begins only after Wenku8 proves the public contract. Source-specific behavior may extend data returned through the contract, but it cannot introduce a private host API unavailable to third-party extensions.

## Rejected alternatives

- Implement all three sources concurrently: rejected because it multiplies incomplete integration paths before the contract is proven.
- Finish the Android shell before any extension: rejected because it would validate UI scaffolding rather than the load-bearing runtime boundary.
- Build a local-file reader first: rejected because it does not exercise the extension security and identity model driving this migration.

## Migration impact

Wenku8 behavior and sanitized parser fixtures are extracted first from the Flutter reference project. ESJZone and Yamibo remain migration references, not blockers for the first usable alpha.

## Verification

- The full acceptance matrix in `tsuyomi-extensions/docs/WENKU8_ACCEPTANCE.md` passes.
- A clean Android profile installs the signed fixture extension and completes the vertical slice.
- Export/import restores the same stable identity and semantic locator.
