<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# ADR 0007: User-mediated WebView verification; no CAPTCHA or anti-bot bypass

- Status: Accepted
- Date: 2026-08-08

## Problem

Some sources require interactive login, Cloudflare checks, CAPTCHA, or browser state that cannot be completed through normal HTTP requests.

## Constraints

- Tsuyomi must respect source security controls and terms.
- Extensions cannot receive unrestricted WebView control or enumerate host cookies.
- Session data must remain source-scoped.

## Decision

An extension may request a controlled WebView only when its manifest declares the capability and the user granted it. The host opens an allowlisted, user-visible WebView. The user performs login or verification manually.

The host may transfer only source-scoped cookies and narrowly defined completion metadata into the corresponding host-managed cookie partition. Extensions cannot automate CAPTCHA, Cloudflare, or comparable anti-bot challenges, inject bypass scripts, read unrelated cookies, or navigate outside the granted origin policy.

WebView closure and login-state checks do not implicitly trigger remote favorite synchronization or other mutations.

## Rejected alternatives

- Automated CAPTCHA or anti-bot bypass: rejected for security, reliability, and source-policy reasons.
- Give extensions direct WebView objects: rejected because it exposes platform APIs and broad browser state.
- Use one shared cookie jar for all sources: rejected because compromise of one extension would expose other sessions.

## Migration impact

Flutter WebView-assisted flows are decomposed into host-controlled browser sessions and source-specific completion checks. Existing cookies may be imported only through the explicit credential migration path.

## Verification

- Cross-source cookie reads and writes fail.
- Navigation outside declared origins is blocked or requires a host-owned external-browser transition.
- Closing a WebView produces no remote mutation unless the user separately requested it.
- Device tests cover manual login handoff and cookie partitioning.
