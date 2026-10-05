<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# `.hxp` manifest v1 signed and v2 local-unsigned boundary

The signed v1 archive contains `manifest.json`, the declared entry module, optional `assets/` and `locales/`, content-integrity metadata, and an Ed25519 signature. The local-unsigned v2 archive has the same content structure but no signature or publisher key. Both distribute source modules only; host-specific QuickJS bytecode is never portable package content.

## Required manifest data

- immutable extension ID, semantic version, display metadata, and host API compatibility range;
- exact entry module and package-content hashes;
- requested capabilities: network, declared HTTPS domain allowlist, scoped cookies, controlled WebView login, explicit remote-library read/write operations, optional read-only source Home, and bounded isolated storage;
- signing metadata and update channel: v1 requires publisher key ID and detached Ed25519 signature; v2 requires precisely `signing: { "algorithm": "none" }`, never a publisher key or signature;
- resource declarations: request timeout, concurrent request limit, response-size ceiling, CPU/wall-time budget, and memory budget.

## Trust and updates

Signed v1 installation requires a publisher signature trusted through current or retained root-signed repository metadata, a user-trusted key, or an explicit local-import confirmation. A same-key signed update with no capability expansion may be offered as a normal update. Only a user-selected local file may use the distinct unsigned v2 contract, with exact-package informed consent and no automatic update or repository authorization. Existing source-scoped credentials might be available to code under the same source ID, including after uninstall; the host cannot claim unsigned code is isolated from them. Any added domain, cookie scope, controlled-WebView permission, file ability, or storage quota requires a new explicit grant. Catalog expiry, root-signed revocation, and the exact legacy-migration exception are rechecked before signed repository activation.

## Host security boundary

The host enforces network destinations, cookie partitions, storage quotas, resource limits, and user-mediated WebView flow. QuickJS is an execution engine, not a security sandbox. Extensions receive no Android- or iOS-specific APIs.

The normative Host API v1 network boundary is [`hxp-host-api-v1.md`](hxp-host-api-v1.md). It defines the only extension-facing transport request/response/error shapes; it never exposes raw cookies, an HTTP client, or a WebView.

Archive integrity, signed publisher trust, unsigned local approval, revocation, rotation, rollback, and capability-diff rules are normative in [`hxp-package-v1.md`](hxp-package-v1.md).

## Optional source Home capability

`capabilities.home` is optional. When present, it contains only `{ "enabled": boolean }`; absence and `enabled: false` are equivalent. Enabling it is a capability expansion that requires explicit install/update approval.

The capability permits the host to invoke the versioned normalized Home projection in [`hxp-host-api-v1.md`](hxp-host-api-v1.md). It does not grant UI injection, arbitrary navigation, background refresh, browser/WebView access, website mutation, additional origins, cookies, or storage. All requests still pass through `capabilities.network` and the host starts them only after an explicit user action.

## Declared read-only update check v2

`capabilities.updateCheck` is optional. When present, it declares exactly `{ version: 2, origin, method: "GET", path, parameters, referrerPath? }`. Its parameters permit fixed literals plus exactly one `remoteBookId` binding; `cursor`, target and write bindings are forbidden. The origin must already be in the manifest's network allowlist. Enabling this capability is a visible `source-update:read` grant during install/update approval. In signed v1 this grammar is authenticated by the publisher signature; in local-unsigned v2 it is only the declared policy of the exact file the user approved.

The host invokes an update check only through this exact manifest policy, with `NETWORK_ONLY` GET and no body. A generic source request is not an update-check fallback. Redirects are not permitted. A source may use the same site endpoint for ordinary directory reads, but that does not authorize the update-check API to substitute generic directory transport or a same-origin GET mutation.

## Declared remote-library operations

`capabilities.remoteLibrary.read = true` requires separate `policies.read` and `policies.targets` entries: paginated book listing and target discovery are distinct exact request grammars. Every declared write operation similarly requires its matching `add`, `remove`, or `move` policy. The host rejects generic transport calls to these protected surfaces and accepts only the exact operation context derived from the active manifest. In v2 these policies are not publisher-authenticated and require exact-file consent; approval of read-only testing does not authorize invoking write operations.

`capabilities.remoteLibrary.policies.{read,targets,add,remove,move}.redirects` is an optional, bounded list of exact success destinations. Every destination names one HTTPS origin, `GET`, path, fixed query parameters, and optional referrer path. The host follows a redirect for a signed remote operation only when its next request exactly matches one declared destination; undeclared locations and every non-`GET` follow-up fail closed. Redirect targets cannot bind cursors, book IDs, target IDs, cookies, or arbitrary server-provided values, and they are included in the remote capability fingerprint.
