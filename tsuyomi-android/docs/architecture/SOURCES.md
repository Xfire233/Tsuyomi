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
| Encoding / alias / backoff | legacy pages and host aliases | extension selects `auto`, `utf-8`, `gb18030`, or `big5-hkscs`; legacy GET forms use bounded structured query parameters plus an explicit host-applied charset, never extension-local charset tables; a GET may provide a bounded source-local semantic key; only transient idempotent failures back off |
| Validated document cache | offline/retry resilience | raw transport cache is host private; normalized content is eligible only after DTO validation; rejected HTML is not a normal cache entry |
| Manual verification WebView | user interaction truly required | manifest `webLogin`, direct user action, declared origin, serialized ephemeral session, no bridge/bypass, user completes the interaction |
| Verified-page snapshot | page must be rendered after user interaction | user explicitly opens the paused GET and later presses use-current-page; exact current URLs bind directly, while allowed-origin HTTPS top-frame redirects retain originating-request provenance; any later non-redirect navigation invalidates the binding; snapshot is bounded and parser-validated with no automatic refresh/replay |
| External browser | user can solve a problem outside app | open-only; Tsuyomi does not read browser cookies or import session state |

Every level returns the same redacted diagnostic envelope. The UI can offer direct retry, cache retry, open verification, reopen source, or report diagnostic; it must not silently escalate to WebView or a remote write.

Recovery presentation is typed and actionable. Repository/package installation distinguishes file access, network connection, timeout, repository/server response, cancellation, storage, cryptographic verification, expired approval, and activation. Network and timeout failures may recommend the user's system proxy or a trusted proxy tool when the current network cannot reach the repository, but the host never configures, bundles, selects, or silently routes through a proxy. Home initial, replacement, and refresh failures preserve admitted content when available while exposing `sessionRequired` / `verificationRequired` as an explicit controlled-verification action rather than a generic retry or silent loading state.

One route-owned Search submission owns one in-flight operation. Repeated trailing-icon or IME commands while that operation is working coalesce before extension or transport execution. A fixed-length response that ends before its declared body length, or an I/O interruption while reading it, is a typed network failure and is never passed to source classification or parser admission. Connectivity recovery is explicit: one retry creates one fresh request; the host does not immediately replay a failed search into a source rate limit.

The active installed source is a durable host preference, not list-order state. Every successful explicit activation persists the exact source ID before returning; runtime recreation restores that source when its trusted archive remains installed, otherwise it selects the first trusted installed source and replaces the stale preference. Uninstalling the selected source clears the preference. Source selection never transfers credentials, sessions, caches, or request ownership across source IDs.

## Cookie and WebView lifecycle

HTTP browser-session credentials are an encrypted host-owned source/origin partition containing declared-origin request cookies and the exact user agent captured from the same user-visible verification WebView. Extension JavaScript never reads, writes, logs, exports, or supplies `Cookie`, `Set-Cookie`, or `User-Agent` headers.

Android WebView has a process-global cookie manager, so verification cannot share its persistent jar with normal source operation. A single verification controller clears and flushes it before each source session, selects only the active source's encrypted session for the declared initial origin, applies that session's exact captured user agent, restores only matching declared-origin request-cookie pairs, and then performs the first page load. Third-party cookies and local-file/content access remain disabled; top-level navigation is HTTPS-only and no JavaScript interface or message channel exists. After user-confirmed completion it replaces applicable declared-origin request cookies plus the current WebView user agent in the encrypted host partition. Completion and cancellation both clear the global WebView store without deleting the last verified encrypted session.

A legacy source may answer a host-native HTTPS `GET` or `HEAD` with a plain-HTTP redirect to the exact same host. The host never sends that HTTP request. If and only if the target host is a manifest-declared default-port HTTPS `webLogin` origin, the redirect target is upgraded before transport while preserving path/query and discarding the fragment. The resulting HTTPS response still passes the signed page classifier, so a login or challenge document becomes `SESSION_REQUIRED` / `VERIFICATION_REQUIRED` rather than a generic redirect failure. Cross-host, user-info, custom-port, undeclared, write-method and otherwise non-upgradable redirects remain blocked; the compatibility mapping grants no HTTP, cookie, source-origin or cache authority.

A controlled-verification navigation that targets plain HTTP is likewise never loaded. If and only if it is a top-frame URL on the exact host of a manifest-declared default-port HTTPS `webLogin` origin, the controller upgrades the navigation to that HTTPS origin while preserving path and query and discarding the fragment, then applies the normal allowed-origin and verified-page provenance rules. This compatibility repair covers legacy same-site login redirects without granting HTTP, mixed content, another host, user-info URLs, custom-port remapping, or extension-controlled navigation authority; every non-upgradable target remains blocked and visible to the user.


Replaying the exact verified user agent with its cookies is a measured compatibility requirement, not arbitrary browser impersonation, and remains a best-effort direct-HTTP optimization rather than the Cloudflare acceptance boundary. Browser clearance may depend on additional browser/device/session state and may be re-evaluated. If direct HTTP remains challenged after the exact session handoff, the host offers the explicit foreground verified-page snapshot path from the same controlled WebView session. The user explicitly opens the paused GET; the host may preserve its identity across allowed-origin HTTPS top-frame redirects and reports the admitted final URL to the signed parser, but clears that provenance after any later non-redirect navigation. It never fabricates a user agent, fakes other fingerprints, or retries a challenge loop.

The 2026-08-29 live experiment established the exact-UA compatibility requirement, but did not establish durable browser equivalence. On 2026-08-30 verification re-entry was fixed and the user confirmed that the real website remained logged in after completion and re-entry. A separately authorized second bounded production probe nevertheless returned `SESSION_REQUIRED` at `search-classify` with an encrypted session present. Direct cookie-plus-UA transport remains best-effort; the current Phase 4A path therefore activates the explicit foreground verified-page snapshot rung. No further native retry is automatic or authorized.

The host detects known source login/challenge/error documents only to guide the user and prevent cache admission. Detection never triggers a solver or modifies page JavaScript.

## Network cache and diagnostics

A cache record is scoped by extension ID, active extension version, request method, origin, selected decode mode, and either host-normalized URL or the extension's bounded semantic key. POST is never cacheable. A semantic key may unify declared alternate hosts only inside one source namespace; it is rejected if invalid or used for a non-idempotent request.

Which reads are cacheable is a contract, not a source-local preference. The cacheable reads — search,
home, detail, directory and chapter — declare cache-first so a repeat read may be served from the
host cache. The update check and every remote-library operation declare a network-only mode because
the host enforces it for those operations. Manual caching selects chapters for offline use and is not
the only path by which a read becomes cacheable; a source that declares network-only for a cacheable
read silently disables that part of the cache.

A cached response that fails source classification is evicted. A challenge, login or error document
is therefore never served as content on a later read, and it cannot displace a valid entry by sitting
in the cache.

The HTTP cache follows response directives where applicable. A separate normalized-document cache stores validated source DTOs and images with content revision/fingerprint. Its eviction is quota/LRU based. Cache lookup state is `fresh`, `validated`, `stale-offline`, `miss`, or `bypassed`; `stale-offline` is visibly labelled and cannot overwrite metadata as a current network result.

Validated `SourceHomePage` values are also eligible for durable normalized replay. A Home snapshot is
partitioned by source ID, exact package revision, stable credential revision, the complete sorted filter
query, and cursor. The default snapshot may be published before source-session opening so process
recreation does not replace known content with a global loading screen; the host rechecks the credential
revision after opening and after each transport result, invalidating or admitting under the resulting
partition as appropriate. Explicit refresh bypasses normalized replay and replaces the snapshot only
after a newly validated page succeeds. Raw responses, rejected pages, and one credential partition's
normalized pages are never replayed into another partition.

Host-owned source media reuse has four execution invariants. Credential-bound encoded files, their access metadata, and cache writes run off the main thread. Decoded media is admitted at no more than the request's target bounds after the original encoded dimensions pass the source-pixel safety limit. Cover thumbnails and Reader illustrations use separate bounded bitmap budgets so Reader pressure cannot evict the visible cover working set. Concurrent loads coalesce only for the complete normalized request identity (transport URL, referrer, target bounds, and media kind); each observer retains independent cancellation, and the shared load is cancelled when its last observer leaves. These invariants do not change the stable source/package/credential disk partition or the host's transport, classification, revocation, and authority checks.

Diagnostics contain a correlation ID, stage, response status when safe, origin, redirect count, selected decode, cache state, retry decision, and sanitized parser code. They exclude cookie values, authorization, request body, URL query secrets, raw HTML, account names, and JavaScript stack traces by default.

## `network.request` contract

The normative draft lives in `tsuyomi-protocol/docs/hxp-host-api-v1.md`. Its important host guarantees are:

- only manifest-declared HTTPS origins and bounded redirects;
- `GET`, `HEAD`, and bounded `POST` form/UTF-8 requests; signed extensions may supply a bounded structured URL query with an explicit host-applied `utf-8`, `gb18030`, or `big5-hkscs` encoding when a legacy GET form requires non-UTF-8 bytes; host-owned `User-Agent`, cookie, `Host`, `Origin`, `Referer`, connection, and security headers;
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

An owner catalogue begins as untrusted source links. The host creates it only after direct user initiation and bounded verification of resolved thread ID, post ID, physical page, owner, and nonempty target content; it persists with a source fingerprint and a typed failure/cancellation state. It cannot make remote writes. Forum actions such as reply, favorite, move, or moderation are not part of Phase 0–3; no network verb becomes writable merely because a parser can recognize a forum form.
