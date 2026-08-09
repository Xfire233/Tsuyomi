<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Extension development contract

## Package layout

```text
extension.hxp
├── manifest.json        # canonical manifest and integrity.files map
├── index.mjs
├── assets/
├── locales/
└── signature.ed25519    # detached Ed25519 signature of canonical manifest.json
```

`integrity.files` MUST contain every archive entry other than `manifest.json` and
`signature.ed25519`; excluding those two avoids an impossible manifest self-digest. The detached
signature authenticates the manifest and its integrity map.

## Rules

1. Declare every network origin before use; HTTP is forbidden unless a future protocol version names an exceptional migration path.
2. Treat cookies as host-managed, source-scoped state. An extension cannot enumerate or export them.
3. Request Web login only when the source requires user authentication or verification. The host opens a controlled view; no automation bypasses CAPTCHAs, Cloudflare, or similar controls.
4. Store only small source-local durable state through the quota-bound host storage API.
5. Declare every supported remote-library write operation. Network access alone never authorizes `add`, `remove`, or `move`; writeback remains disabled until the user grants the capability and enables it for that source.
6. Extensions receive no device model, display-profile, panel-refresh, Compose, or Android rendering API. Their results must be presentation-neutral and must not require animation, color-only meaning, infinite scroll, pull-to-refresh, or a swipe-only action.
7. Test against sanitized fixtures. Do not commit credentials, raw session data, copyrighted chapter payloads, or live-site test dependencies.

The normative transport boundary is [`tsuyomi-protocol/docs/hxp-host-api-v1.md`](../../tsuyomi-protocol/docs/hxp-host-api-v1.md); package integrity and update trust rules are in [`tsuyomi-protocol/docs/hxp-package-v1.md`](../../tsuyomi-protocol/docs/hxp-package-v1.md).

Wenku8 is the first vertical slice: install → grant → optional deliberate login/verification → search → detail → directory → chapter → locator/progress. Library organization and remote-library writes remain outside Gate 2.
