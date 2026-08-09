<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Gate 2 contract and evidence

## Status

- Scope: **FROZEN**
- Implementation: **COMPLETE**
- Local verification: **PASS**
- Adviser remediation: **PASS** — final independent review approved all seven remediations; no blocking findings.
- Hosted implementation verification: **PASS** — [PR #1](https://github.com/Xfire233/Tsuyomi/pull/1) required checks succeeded for remediation head `b9f526ef7c6752023b7e1bb6976f851594c6cb36`.
- Hosted required checks: `protocol-conformance`, `extensions-baseline`, `android-build-test-lint-goldens`, `android-api29-instrumentation`, and `repository-policy` were all successful for that remediation head; protected-branch approval remains a separate GitHub requirement.
- Branch: `feature/gate-2-wenku8-read-slice`
- Outcome: a locally imported, test-publisher-signed Wenku8 `.hxp` completes the read-only path through semantic progress restoration.

## Frozen user path

```text
local .hxp import
→ package verification
→ capability approval
→ optional deliberate login/verification
→ search
→ detail
→ directory
→ chapter
→ reader
→ semantic progress restoration
```

Gate 2 stops at progress. Library organization, rating/tags, transfer export/import, remote-library writes, background subscription, additional sources, TTS and local EPUB/TXT import remain later gates.

## Product decisions

- Distribution: local `.hxp` import through the Android system file picker.
- Trust: deterministic test publisher fixtures only. No production private signing key, official online extension repository or silent publisher-wide trust.
- Site evidence: sanitized fixtures and mocked host transport are authoritative. Anonymous live Wenku8 checks are best-effort supplementary evidence and cannot replace deterministic tests.
- Login: controlled WebView and source-scoped cookie handoff are implemented and tested without credential import, CAPTCHA solving or automated challenge bypass. A real account interaction remains deliberate user action.
- Reader default: Standard defaults to continuous scroll; E-ink defaults to paged. Scroll, paged and dual-page surfaces share one semantic locator and persisted progress contract.
- Git: all work stays on feature branches and pull requests. Protected `main` is not bypassed.

## Required architecture

- Verify archive paths, canonical integrity, Ed25519 signature, publisher identity, compatibility, revocation and capability grants before evaluating JavaScript.
- Run QuickJS-ng off the main thread on one serialized runtime lane per installed extension version. Apply memory, stack, deadline, cancellation and context-discard rules.
- Keep HTTP transport, origins, redirects, headers, cookies, cache, WebView lifecycle, quotas and diagnostics host-owned.
- Pass only validated source DTOs and structured `ReaderDocument` values into reader modules. Raw HTML is never durable reader state.
- Preserve one route, business state and persisted data model under Standard and E-ink profiles.

## Acceptance evidence

1. Protocol conformance passes 19/19 tests. HXP verification rejects unsigned, altered, revoked, incompatible, path-traversal, hash-mismatched and capability-escalating packages before module evaluation.
2. Wenku8 extension tests pass 6/6 cases across search, detail, directory, chapter, origin restrictions and typed login/challenge remediation. Two consecutive fixture builds produced the same SHA-256:
   ```text
   833025fc99999df8f44892f827114e78918d3fe27478a8f2872bbf3c231dfb68
   ```
3. API 29 product instrumentation runs the controlled verification path in forced Standard and forced E-ink profiles. Each run observes the challenge error, displays the blocked-navigation warning, persists only the declared-origin Cookie after explicit completion, reopens the source, bypasses the anonymous challenge cache partition and returns `雾港纪事`.
4. Controlled WebView instrumentation passes explicit finish, cancel and undeclared-origin rejection. The product route applies bottom safe-drawing insets; cancellation returns to the original typed verification error without bypassing the challenge.
5. Credential-backed network cache keys use an opaque revision derived from the randomized encrypted credential record. Anonymous, previous-credential and current-credential responses cannot satisfy one another; the current credential partition remains stable across process recreation.
6. API 29 emulator acceptance completed signed local import, approval, search, detail, directory, chapter and reader. Online population created four bounded persistent response entries. After APK update/process recreation and forced offline transport, explicit `使用已有离线缓存` actions restored search, detail, directory and chapter with `NetworkCacheState.STALE_OFFLINE`.
7. Reader progress restoration uses the semantic chapter/block locator. Scroll and paged presentation survived `am kill` and restored the second block as `2 / 2`; initial `LazyListState` emission no longer overwrites the persisted locator.
8. The final Android static matrix passed `1193 actionable tasks`, including debug assembly, release Kotlin compilation, lint, JVM tests and four screenshot suites. The API 29 instrumentation matrix includes app, settings, UI, security, database, WebView and QuickJS runtime modules.
9. Root REUSE 3.3 compliance passed for 395 files. Root and Android artifact policies passed for 405 and 324 candidate files respectively.
10. Anonymous live Wenku8 homepage and search probes returned HTTP 403/login gating on 2026-08-09. This is recorded as best-effort live-site behavior; sanitized fixtures remain the acceptance authority and no automated verification bypass was attempted.
11. Adviser remediation preserves the signed-central-directory executable entry, enforces signed Cookie mode/origins for WebView and transport, discards terminally failed QuickJS contexts, binds cancellation to one operation, closes source clients on Compose-owner disposal, and makes resource-limit increases approval-bound. The focused Android/API 29 run passed QuickJS runtime (5), app (3), and WebView (3) instrumentation tests plus debug assembly; the extension fixture run passed 6/6 tests, two deterministic rebuilds, and the committed checksum.

Gate 2 的交付复盘、challenge WebView 的证据边界和未来 Gate 的 Planner/Designer/Adviser/人工合并流程见 [GATE_2_RETROSPECTIVE.md](../process/GATE_2_RETROSPECTIVE.md)。

## Evidence boundary

A physical E-ink device is required before making a release-readiness claim about ghosting, vendor refresh behavior, image quality or hardware key interaction. Emulator evidence may prove Android/Compose profile behavior but cannot substitute for panel evidence.
