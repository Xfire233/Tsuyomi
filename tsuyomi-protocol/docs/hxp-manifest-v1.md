<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# `.hxp` manifest v1 boundary

An `.hxp` archive contains `manifest.json`, `index.mjs`, optional `assets/` and `locales/`, content-integrity metadata, and an Ed25519 signature. The package distributes source modules only; host-specific QuickJS bytecode is never portable package content.

## Required manifest data

- immutable extension ID, semantic version, display metadata, and host API compatibility range;
- exact entry module and package-content hashes;
- requested capabilities: network, declared HTTPS domain allowlist, scoped cookies, controlled WebView login, explicit remote-library read/write operations, and bounded isolated storage;
- publisher key ID, signature metadata, and update channel;
- resource declarations: request timeout, concurrent request limit, response-size ceiling, CPU/wall-time budget, and memory budget.

## Trust and updates

Installation requires a signature chaining to a user-trusted key or an explicit local-import confirmation. A same-key update with no capability expansion may be offered as a normal update. Any added domain, cookie scope, controlled-WebView permission, file ability, or storage quota requires a new explicit grant. Revocation and key-rotation data is signed and evaluated before updates.

## Host security boundary

The host enforces network destinations, cookie partitions, storage quotas, resource limits, and user-mediated WebView flow. QuickJS is an execution engine, not a security sandbox. Extensions receive no Android- or iOS-specific APIs.

The normative Host API v1 network boundary is [`hxp-host-api-v1.md`](hxp-host-api-v1.md). It defines the only extension-facing transport request/response/error shapes; it never exposes raw cookies, an HTTP client, or a WebView.

Archive integrity, publisher trust, revocation, rotation, rollback, and capability-diff rules are normative in [`hxp-package-v1.md`](hxp-package-v1.md).

## Signed remote-library redirects

`capabilities.remoteLibrary.policies.{read,add}.redirects` is an optional, bounded list of exact success destinations. Every destination names one HTTPS origin, `GET`, path, fixed query parameters, and optional referrer path. The host follows a redirect for a signed remote operation only when its next request exactly matches one declared destination; undeclared locations and every non-`GET` follow-up fail closed. Redirect targets cannot bind cursors, book IDs, cookies, or arbitrary server-provided values, and they are included in the remote capability fingerprint.
