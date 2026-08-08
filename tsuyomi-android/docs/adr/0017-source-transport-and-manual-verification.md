<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# ADR 0017: Host-owned source transport, validated cache, and manual WebView verification

- Status: Accepted
- Date: 2026-08-08

## Problem

Sources vary in encoding, redirects, aliases, login state, JavaScript rendering, transient rate limits, and challenge pages. Hikari combines direct Dio traffic, application-stored cookies, a Wenku8-specific WebView queue, raw HTML cache fallback, and Cloudflare-specific interception. That arrangement couples compatibility behavior to one source and creates unsafe automatic verification pressure.

## Research basis

- Hikari shows concrete compatibility needs: alternate Wenku8 hosts, byte-first GBK/Big5 decode, redirect normalization, validated cached HTML, login-page detection, serialized WebView work, and a stable post ID for forum content.
- Android's `CookieManager` is a process-wide WebView cookie store and `flush()` persists its accessible cookies; it cannot be treated as a durable per-source credential database.
- Android WebView security guidance warns that JavaScript interfaces and message bridges can be callable by untrusted frames and must not be exposed to untrusted content.

Sources: [CookieManager](https://developer.android.com/reference/android/webkit/CookieManager), [Android WebView native-bridge security](https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges).

## Decision

All source traffic runs through the Android host `core/network` and its versioned Host API. Extensions declare only HTTPS origins, quotas, cookie scope, and whether controlled web login is needed. They never receive raw cookies, OkHttp clients, WebView instances, Android handles, unrestricted headers, or the ability to bypass origin/rate/resource checks.

The host uses a compatibility ladder:

1. Direct host HTTP with source-scoped cookies, bounded redirects, automatic or explicitly selected charset, standard HTTP cache semantics, retry/backoff only for transient idempotent failures, and typed diagnostics.
2. Source-declared semantic cache keys to unify equivalent URL aliases. Host stores raw network cache by request and normalized documents only after extension output validates against protocol DTOs. Invalid, challenge, login, or parser-rejected HTML is never promoted to normal content cache.
3. A direct user action may open a controlled, single-source WebView session when the manifest declares `webLogin` and the source requires interaction. The user, not Tsuyomi, completes login, CAPTCHA, Cloudflare, or other verification.
4. After the user explicitly selects “use this verified page”, the host may create a bounded, top-frame snapshot from the current allowed origin for the extension parser. It is treated as untrusted input and retained only after the parser yields a valid document. It is not a background fetch queue or automatic challenge solver.

There is no CAPTCHA solving, challenge fingerprint emulation, bypass JavaScript, anti-bot evasion, automatic re-verification, third-party cookie use, or browser-cookie import. External browser opening is diagnostic/user convenience only; its cookies are not read.

A verification WebView is serialized globally. Before opening it, the host clears the WebView cookie store and initializes only the active source session; on completion/cancellation it exports only user-approved declared-origin request-cookie pairs into the encrypted source credential partition, then clears and flushes WebView cookies again. Third-party cookies, file/content URL access, mixed content, universal file access, native JavaScript interfaces, message channels, and non-HTTPS navigation are disabled. The host must warn that certain cookie attributes cannot be faithfully preserved by the Android API and may require a later login.

Remote library writes remain outside this ladder. Login checks, cache refresh, imports, parser recovery, and WebView completion are read-only. A write needs a direct user command, matching declared capability, per-source grant, and reconciliation record.

## Rejected alternatives

- Let each extension implement HTTP, cookie, and WebView behavior: rejected because capability enforcement and diagnostics become unverifiable.
- Reuse Hikari's source-specific Cloudflare interceptor: rejected because automatic challenge bypass violates the security and release boundary.
- Persist WebView's global cookie jar as a source credential store: rejected because it lacks source partitioning and leaks state across extensions.
- Return raw WebView/OkHttp handles or unrestricted `Cookie` headers to QuickJS: rejected because a trusted extension still must not gain cross-source credential access.
- Treat HTML cache as valid merely because it is nonempty: rejected because stale login/challenge pages can corrupt parsed books and progress.

## Verification

- Host API tests reject disallowed origins, redirects, headers, bodies, response sizes, cookie scopes, cache-key collisions, and stale task commits.
- Integration tests cover UTF-8/GB18030/Big5 decode, alias-key cache hits, valid document cache admission, invalid/login/challenge cache rejection, rate-limit backoff, and offline stale-read policy.
- Instrumented tests verify no bridge is exposed, third-party cookies and file access are off, unapproved navigation is blocked, WebView cookies clear after completion, and direct requests receive only the active source partition.
- Wenku8 acceptance requires manual verification evidence when the source requires it; it never treats a bypass result as a passing path.
