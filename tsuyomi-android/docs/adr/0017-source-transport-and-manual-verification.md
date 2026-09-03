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
- Cloudflare documents non-JavaScript HTTP clients as unsupported for production Challenges, treats embedded WebViews as limited-support environments, and ties `cf_clearance` to the visitor/device while continuously re-evaluating session behavior. Cookie transfer from a browser engine into `HttpURLConnection` is therefore best-effort compatibility, never a deterministic acceptance seam. Sources: [supported browsers](https://developers.cloudflare.com/cloudflare-challenges/reference/supported-browsers/), [clearance](https://developers.cloudflare.com/cloudflare-challenges/concepts/clearance/), [challenge passage](https://developers.cloudflare.com/cloudflare-challenges/challenge-types/challenge-pages/challenge-passage/).
- Phase 4A live evidence on `Tsuyomi_Review_Work_API29` showed Wenku8 did not present Cloudflare before login. After user login, the exact host gateway with copied cookies plus Android's native default user agent remained session-required, while the same cookies plus the exact user agent captured from the verified WebView returned search content. The smallest proven handoff is therefore source/origin-scoped cookies plus the exact captured WebView user agent; arbitrary or fabricated browser identity remains forbidden.

## Decision

All source traffic runs through the Android host `core/network` and its versioned Host API. Extensions declare only HTTPS origins, quotas, cookie scope, and whether controlled web login is needed. They never receive raw cookies, OkHttp clients, WebView instances, Android handles, unrestricted headers, or the ability to bypass origin/rate/resource checks.

The host uses a compatibility ladder:

1. Direct host HTTP with source-scoped verified-browser cookies and the exact user agent captured from that same user-visible WebView session, bounded redirects, automatic or explicitly selected charset, standard HTTP cache semantics, retry/backoff only for transient idempotent failures, and typed diagnostics. The user agent is encrypted with the cookie partition and injected by the host, never extension-controlled. Direct HTTP never fabricates browser fingerprints, loops challenges, or claims that a copied session proves full browser equivalence.
2. Source-declared semantic cache keys unify equivalent URL aliases. Host stores raw network cache by request and normalized documents only after extension output validates against protocol DTOs. Invalid, challenge, login, or parser-rejected HTML is never promoted to normal content cache.
3. A direct user action may open a controlled, single-source WebView session when the manifest declares `webLogin` and the source requires interaction. The user, not Tsuyomi, completes login, CAPTCHA, Cloudflare, or other verification.
4. After the user explicitly selects “open the corresponding page”, the host visibly loads the exact paused GET in the controlled WebView. An allowed-origin HTTPS top-frame redirect may preserve that originating-request binding, but any later non-redirect navigation invalidates it. After the user explicitly selects “use this verified page”, the host creates a bounded top-frame snapshot whose request identity remains the exact paused GET and whose response metadata records the admitted final page URL. The snapshot is treated as untrusted input, never logged or persisted as raw HTML, and retained only after the parser yields a valid normalized document. This browser-bound snapshot is the required fallback when cookie rehydration into direct HTTP fails; it is not a background fetch queue or automatic challenge solver.

There is no CAPTCHA solving, challenge fingerprint emulation, bypass JavaScript, anti-bot evasion, automatic re-verification, third-party cookie use, or browser-cookie import. External browser opening is diagnostic/user convenience only; its cookies are not read.

A verification WebView is serialized globally. Before opening it, the host clears the WebView cookie store, selects the exact encrypted session for the active source and declared initial origin, applies that session's captured user agent, restores only its declared-origin request-cookie pairs, and only then performs the first page load. On completion it replaces only user-approved declared-origin request-cookie pairs plus the exact current WebView user agent in the encrypted source credential partition; on completion or cancellation it clears and flushes the process-global WebView cookies again without deleting the last verified encrypted session. Third-party cookies, file/content URL access, mixed content, universal file access, native JavaScript interfaces, message channels, and non-HTTPS navigation are disabled. The host must warn that browser state beyond cookies and user agent cannot be faithfully rehydrated and may require a later login or the explicit verified-page fallback. Route entry itself performs no native source request, automatic verification retry, remote-library refresh or website mutation.

For the explicitly authorized unattended Wenku8 acceptance run on 2026-08-30, the debug-only validation harness may recover the still-current same-app controlled-WebView cookie header for the single declared Wenku8 origin after test-side encrypted-store loss, pair it with the controlled WebView's default/current user agent, and immediately write it into the normal encrypted source/origin partition. It must not log, export, persist plaintext separately, read an external browser, cross source boundaries, or ship in production. The authorization expires when the real Search → Detail → Directory → Chapter → Reader chain passes; validation markers are then removed while the normal encrypted verified session remains governed by the production lifecycle above.

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
- Instrumented tests verify no bridge is exposed, third-party cookies and file access are off, unapproved navigation is blocked, WebView cookies clear after completion, the encrypted source partition retains the exact captured user agent with request cookies, and direct requests receive only the active source/origin session identity.
- Deterministic tests use a synthetic challenge/verified-page fixture and never solve live Cloudflare. A live Wenku8 pass is manual, foreground, best-effort evidence on a current physical browser/WebView environment and cannot be a CI dependency.
- Wenku8 acceptance requires manual verification evidence when the source requires it; it never treats a bypass result as a passing path.
The 2026-08-29 live experiment established that Wenku8 direct HTTP required the exact WebView user agent paired with its cookies, but did not establish durable browser equivalence. On 2026-08-30 the production build first returned `SESSION_REQUIRED` at `search-classify` and exposed a verification re-entry defect. Re-entry was fixed and deterministically verified; the user then confirmed the real website remained logged in after completion and re-entry. A separately authorized second one-request production probe still returned the same redacted `SESSION_REQUIRED` result with an encrypted session present. Direct cookie-plus-exact-UA handoff is therefore best-effort only in the current environment, and Phase 4A Wenku8 acceptance proceeds through the explicit foreground verified-page snapshot fallback in Decision 4. No further native retry is implied or authorized.
