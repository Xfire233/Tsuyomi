<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Source transport, compatibility, and forum data

## Boundary

```text
QuickJS extension
  → HostApi.network.request (validated DTO)
  → core/network: origin policy, source cookie partition, HTTP cache, decoding, limits
  → HTTPS
  → typed NetworkResult
  → extension parser
  → validated source DTO / ReaderDocument
```

Extensions describe source semantics—URLs, request parameters, expected pages, normalized identities, parsers, and user-visible error remediation. The host controls transport, credentials, cache, WebView lifecycle, resource ceilings, redacted diagnostics, and cancellation.

## Compatibility ladder

| Level | Use | Boundaries |
|---|---|---|
| Direct HTTP | default for all declared HTTPS origins | source cookie partition; max five same-allowlist redirects; host UA; cache policy and response ceiling enforced |
| Encoding / alias / backoff | legacy pages and host aliases | extension selects `auto`, `utf-8`, `gb18030`, or `big5-hkscs`; a GET may provide a bounded source-local semantic key; only transient idempotent failures back off |
| Validated document cache | offline/retry resilience | raw transport cache is host private; normalized content is eligible only after DTO validation; rejected HTML is not a normal cache entry |
| Manual verification WebView | user interaction truly required | manifest `webLogin`, direct user action, declared origin, serialized ephemeral session, no bridge/bypass, user completes the interaction |
| Verified-page snapshot | page must be rendered after user interaction | user presses an explicit command; top-frame allowed-origin snapshot is bounded and parser-validated; no automatic refresh/replay |
| External browser | user can solve a problem outside app | open-only; Tsuyomi does not read browser cookies or import session state |

Every level returns the same redacted diagnostic envelope. The UI can offer direct retry, cache retry, open verification, reopen source, or report diagnostic; it must not silently escalate to WebView or a remote write.

## Cookie and WebView lifecycle

HTTP credentials are an encrypted host-owned source partition, keyed by extension/source and declared origin. Extension JavaScript never reads, writes, logs, exports, or supplies `Cookie`/`Set-Cookie` headers.

Android WebView has a process-global cookie manager, so verification cannot share its persistent jar with normal source operation. A single verification controller clears and flushes it before/after each source session. It loads only the active source's user-approved request-cookie pairs, disables third-party cookies and local-file/content access, uses HTTPS-only top-level navigation, and exposes no JavaScript interface or message channel. After a user-confirmed completion, it captures applicable declared-origin request cookies into the host partition and clears the WebView store. The app warns when a site relies on cookie attributes Android cannot faithfully rehydrate.

The host detects known source login/challenge/error documents only to guide the user and prevent cache admission. Detection never triggers a solver or modifies page JavaScript.

## Network cache and diagnostics

A cache record is scoped by extension ID, active extension version, request method, origin, selected decode mode, and either host-normalized URL or the extension's bounded semantic key. POST is never cacheable. A semantic key may unify declared alternate hosts only inside one source namespace; it is rejected if invalid or used for a non-idempotent request.

The HTTP cache follows response directives where applicable. A separate normalized-document cache stores validated source DTOs and images with content revision/fingerprint. Its eviction is quota/LRU based. Cache lookup state is `fresh`, `validated`, `stale-offline`, `miss`, or `bypassed`; `stale-offline` is visibly labelled and cannot overwrite metadata as a current network result.

Diagnostics contain a correlation ID, stage, response status when safe, origin, redirect count, selected decode, cache state, retry decision, and sanitized parser code. They exclude cookie values, authorization, request body, URL query secrets, raw HTML, account names, and JavaScript stack traces by default.

## `network.request` contract

The normative draft lives in `tsuyomi-protocol/docs/hxp-host-api-v1.md`. Its important host guarantees are:

- only manifest-declared HTTPS origins and bounded redirects;
- `GET`, `HEAD`, and bounded `POST` form/UTF-8 requests; host-owned `User-Agent`, cookie, `Host`, `Origin`, `Referer`, connection, and security headers;
- strict request/response, timeout, concurrency, cancellation, and decode limits;
- structured response/cache metadata or stable typed error; HTTP status itself remains a response, not an exception;
- `sessionRequired` / `verificationRequired` only provide a remediation category. They do not open a WebView, log in, or write remotely.

## Forum model

A forum source exposes normalized pagination, never an HTML web page to the reader:

```text
ForumSection { sourceSectionId, title, page, complete, ThreadSummary[] }
ThreadSummary { threadId, title, author, replyCount, lastActivity, tags? }
ThreadPage { threadId, pageId, page, complete, Post[] }
Post { postId, authorId, authorName, floor?, createdAt?, replyToPostId?, blocks[] }
ForumThreadNavigation { catalogueEntries[], physical-page aliases, ownerCatalogue? }
```

`threadId` and `postId` are source identities. Page/floor/index are navigation fallbacks. The reader converts each post into a `post` ReaderDocument block and restores by `postId`. `ForumThreadNavigation` is the separate validated route projection described in `tsuyomi-protocol/docs/forum-navigation-v1.md`: direct selection keeps the requested source entry, directional navigation de-duplicates physical pages, and history resume alone can authorize a semantic cross-page load.

An owner catalogue begins as untrusted source links. The host creates it only after direct user initiation and bounded verification of resolved thread ID, post ID, physical page, owner, and nonempty target content; it persists with a source fingerprint and a typed failure/cancellation state. It cannot make remote writes. Forum actions such as reply, favorite, move, or moderation are not part of Gate 0–3; no network verb becomes writable merely because a parser can recognize a forum form.
