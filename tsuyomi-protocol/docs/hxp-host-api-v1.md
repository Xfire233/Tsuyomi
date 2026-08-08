<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# HXP Host API v1: network boundary

This document freezes the network portion of Host API v1. It is normative for Android hosts and HXP extensions once Gate 0 conformance fixtures are added. It does not grant a source API or a WebView object to extension code.

## Invocation

Extension code calls the asynchronous host method:

```ts
host.network.request(request: NetworkRequest): Promise<NetworkResponse>
```

The host independently verifies the active extension identity, manifest capability grant, runtime-lane cancellation state, and every field below. An extension must not rely on an unchecked client-side validation result.

## Request

```ts
type NetworkRequest = {
  url: string; // absolute HTTPS URL; origin must be manifest capabilities.network.origins
  method: "GET" | "HEAD" | "POST";
  headers?: Record<string, string>; // allowlisted end-to-end headers only
  form?: Record<string, string>; // POST only, UTF-8 application/x-www-form-urlencoded
  utf8Body?: string; // POST only; mutually exclusive with form
  referrerUrl?: string; // optional HTTPS URL in the same manifest allowlist
  decode: "auto" | "utf-8" | "gb18030" | "big5-hkscs";
  cache: "default" | "network-only" | "validate" | "offline-only";
  semanticCacheKey?: string; // GET/HEAD only; 1–160 [A-Za-z0-9._:-] chars
};
```

`form` keys/values and `utf8Body` are bounded by the manifest request/CPU ceilings and a host hard cap of 64 KiB before encoding. Hosts must reject NUL header names/values and invalid UTF-8. Hosts supply and protect `User-Agent`, `Cookie`, `Host`, `Origin`, `Referer`, `Content-Length`, connection, proxy, `Sec-*`, and `Set-Cookie` headers; extensions cannot set or observe them. `referrerUrl` is validated and serialized by the host, or rejected.

The host follows at most five redirects. Each redirect must resolve to a declared HTTPS origin; otherwise the request fails with `NETWORK_REDIRECT_DISALLOWED`. POST redirect behavior follows the HTTP method rules but a resulting non-idempotent request is never cached. `semanticCacheKey` is namespaced by extension ID and active extension version; it exists only to unify source-declared equivalent GET/HEAD aliases.

`decode: auto` chooses BOM, then a syntactically valid HTTP charset, then UTF-8. Explicit decode is for legacy sources and is included in cache identity. A host must use a deterministic decoder and reject unsupported/malformed sequences according to its documented replacement policy.

## Response

```ts
type NetworkResponse = {
  status: number;                 // 100–599; HTTP status is a successful transport response
  finalUrl: string;               // allowed HTTPS URL after redirect validation
  headers: {
    contentType?: string;
    etag?: string;
    lastModified?: string;
  };
  text: string;                   // decoded, bounded by capabilities.network.maxResponseBytes
  decodeUsed: "utf-8" | "gb18030" | "big5-hkscs";
  cache: "fresh" | "validated" | "stale-offline" | "miss" | "bypassed";
  diagnosticId: string;
};
```

The response never exposes raw response headers, cookies, peer/TLS data, an OkHttp response, a stream, a WebView, Android objects, or another extension's cache. A host may return `NETWORK_RESPONSE_LIMIT` before allocating a body beyond its limit.

`offline-only` returns a `stale-offline` response only when a permitted cached record exists. The extension/parser must preserve that state when deciding whether metadata is current. A raw response enters only host-private transport cache; a normalized document cache entry is admitted only after its parsed protocol DTO validates.

## Failures

The promise rejects with one `HostApiError`:

```ts
type HostApiError = {
  code:
    | "NETWORK_INVALID_REQUEST"
    | "NETWORK_DISALLOWED_ORIGIN"
    | "NETWORK_HEADER_DISALLOWED"
    | "NETWORK_BODY_LIMIT"
    | "NETWORK_RESPONSE_LIMIT"
    | "NETWORK_TIMEOUT"
    | "NETWORK_CANCELLED"
    | "NETWORK_DNS"
    | "NETWORK_CONNECT"
    | "NETWORK_TLS"
    | "NETWORK_REDIRECT_LIMIT"
    | "NETWORK_REDIRECT_DISALLOWED"
    | "NETWORK_OFFLINE_MISS"
    | "NETWORK_DECODE"
    | "NETWORK_RATE_LIMITED"
    | "SESSION_REQUIRED"
    | "VERIFICATION_REQUIRED";
  diagnosticId: string;
  retryAfterMs?: number; // only NETWORK_RATE_LIMITED, bounded and advisory
  remediation?: "retry" | "open-login" | "open-verification" | "check-network" | "none";
};
```

Failure text is host-localized for users. Diagnostic detail is redacted: it contains no cookie, authorization, request body, raw HTML, secret query value, or JavaScript stack trace. HTTP error statuses remain `NetworkResponse` so the extension can parse a documented source error page safely.

`SESSION_REQUIRED` and `VERIFICATION_REQUIRED` do not open a browser. The feature layer can offer a direct user action to start the manifest-approved controlled WebView flow. Completion, cache refresh, imports, and login checks are read-only.

## WebView and credentials

This method does not expose a WebView or cookies. A separate host-controlled UI flow may transfer user-approved declared-origin request-cookie pairs to the source credential partition. It must clear the process-global WebView cookie store before and after each serialized session, disable third-party cookies and local-file/content access, expose no JavaScript bridge/message channel, and require user interaction for every login/challenge/verification.

## Conformance minimum

Gate 0 fixtures must prove: origin and redirect rejection; header/body/response limits; each decoder; semantic alias cache isolation; offline stale marker; cancellation; error redaction; no extension cookie observation; and no implicit WebView/remote-write transition.
