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

## Deterministic Wenku8 update fixture

`update-check-v2` is a signed read-only capability, not a directory-transport fallback. Its source export must build only the exact manifest-declared `NETWORK_ONLY` GET. It emits `{ complete: true, order: "source" }` only after confirming a closed HTML envelope, exactly one balanced canonical directory table (inside static `#list`, or the dynamic page's standalone `table.css`), matching book chapter evidence, and no continuation page; it parses only that table, excluding unrelated page links. Missing, truncated, ambiguous or noncanonical tables fail closed. Ordinary directory parsing remains deliberately more permissive. The update export must never emit a chapter URL, raw chapter text, HTML, credentials, or a source-controlled anchor. Anchors bind source/book/ordered chapter IDs; titles are display metadata and title corrections do not fabricate an order change.

After changing its source or manifest, rebuild the public fixture from this component root with `npm run package:fixture`, then run `npm test`. The regenerated `fixtures/wenku8/wenku8-fixture.sha256` is the required identity digest for the generated `.hxp`; it identifies signed `org.tsuyomi.wenku8` fixture version `0.2.30` with SHA-256 `36db147337636ddc1b5bb00a979e42bb82e97f419ab374c6a5cb62ce852d6af6`. Verify that exact value after every deliberate regeneration; never reuse a digest from a prior signed source version.

Wenku8 is the first vertical slice: install → grant → optional deliberate login/verification → search → detail → directory → chapter → locator/progress. Library organization and remote-library writes remain outside Phase 2.
