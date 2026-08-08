<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Architecture decision records

Each ADR records the problem, constraints, options, decision, rejected alternatives, migration impact, and verification method.

| ID | Decision | Status |
|---|---|---|
| 0001 | [Independent Tsuyomi brand and Apache-2.0 codebase](0001-independent-brand-and-license.md) | Accepted |
| 0002 | [Android 10 baseline and GitHub Releases/F-Droid distribution](0002-android-baseline-and-distribution.md) | Accepted |
| 0003 | [Local-first operation without accounts, cloud sync, telemetry, or crash reporting](0003-local-first-operation.md) | Accepted |
| 0004 | [Protocol-first cross-platform strategy; defer KMP](0004-protocol-first-cross-platform.md) | Accepted |
| 0005 | [TypeScript ES Modules on QuickJS-ng rather than APK extensions](0005-typescript-quickjs-extensions.md) | Accepted |
| 0006 | [Signed repository plus local `.hxp` import and explicit capability escalation](0006-signed-extension-distribution.md) | Accepted |
| 0007 | [User-mediated WebView verification; no CAPTCHA or anti-bot bypass](0007-user-mediated-webview-verification.md) | Accepted |
| 0008 | [Dual backup model and Flutter `hikari_novel_backup` v1 import](0008-backup-and-flutter-import.md) | Accepted |
| 0009 | [Stable remote identity and `updatedAt` progress conflict resolution](0009-stable-identity-and-progress.md) | Accepted |
| 0010 | [Tsuyomi Ink Material 3 system and reader-specific low-stimulus themes](0010-tsuyomi-ink-design-system.md) | Accepted |
| 0011 | [Wenku8-first end-to-end vertical slice](0011-wenku8-first-vertical-slice.md) | Accepted |
| 0012 | [Capability-gated remote library writes](0012-capability-gated-remote-library-writes.md) | Accepted |
| 0013 | [In-process QuickJS runtime with a constrained host boundary](0013-in-process-quickjs-runtime.md) | Accepted |
| 0014 | [Global E-ink display profile from the first implementation slice](0014-global-eink-display-profile.md) | Accepted |
| 0015 | [Unified structured reader document and incremental layout engine](0015-unified-reader-document-and-layout.md) | Accepted |
| 0016 | [Many-to-many novel library shelves and deterministic smart collections](0016-novel-library-and-smart-shelves.md) | Accepted |
| 0017 | [Host-owned source transport, validated cache, and manual WebView verification](0017-source-transport-and-manual-verification.md) | Accepted |
| 0018 | [Android Keystore AES-GCM source credential partitions](0018-keystore-aead-source-credentials.md) | Accepted |

Individual ADRs are written before implementation that depends on them.
