<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Wenku8 vertical-slice acceptance matrix

All automated rows use sanitized recorded fixtures and a mocked host transport. Android host rows run in forced `standard` and forced `eInk` profiles. WebView/storage rows require an AVD or device; release readiness for the E-ink profile also requires physical E-ink-device evidence.

| Flow | Acceptance evidence |
|---|---|
| Install | Valid signed `org.tsuyomi.wenku8` `.hxp` installs; unsigned, altered, revoked, incompatible-host, path-traversal, and hash-mismatched packages are rejected before module evaluation. |
| Permission grant | The extension can request only declared HTTPS origins and remote-library operations. A package update adding an origin, cookie scope, WebView login, remote `add`/`remove`/`move`, or storage quota pauses for explicit approval. |
| Search | Fixture query returns normalized stable remote ID, title, author, cover, and canonical URL; malformed cards are skipped with structured diagnostics. |
| Detail | Fixture detail maps metadata, tags, status, and cover without retaining source HTML as durable app state. |
| Directory | Fixture table of contents emits stable chapter IDs, ordered display labels, and URLs. Duplicate/changed display text does not alter the chapter identity. |
| Chapter | Fixture chapter is normalized into ordered paragraphs; empty or malformed content returns a typed source error rather than a crash. |
| Reader + progress | Open chapter, persist chapter ID plus text anchor/offset, recreate reader state, and restore the same locator. If the anchor is absent, use bounded fallback progress and report degraded precision. |
| Library | Add, rate, tag, shelf, and remove the book using `(sourceId, remoteBookId)`; uninstalling the extension makes the record dormant without deletion. |
| Remote library writes | Writeback is disabled by default. After the user grants declared `add`, `remove`, or `move` operations and enables writeback for Wenku8, a direct user action performs only the selected operation. Import, login checks, WebView closure, and refresh never write remotely. |
| Transfer | Export contains book identity, metadata, shelf membership, rating/tags, and progress, but contains no cookies, credentials, HTML cache, or source-local hidden IDs. |
| Rate/cache | Transport respects host concurrency/timeout/response limits. Repeated fixture reads demonstrate cache hit and invalidation behavior without contacting the live site. |
| Login boundary | Extension may request controlled WebView login only when the declared capability is granted; no code automates CAPTCHA, Cloudflare, or similar verification. |
| Android WebView | User completes a manually-driven login/verification in a controlled WebView; only source-scoped cookies are handed back to the host cookie jar. |
| Android export | User exports a transfer JSON, selects it through the system file picker, and reimports it into a clean local profile with the same locator. |
| Android E-ink profile | Forced `eInk` mode completes install, search, detail, directory, chapter, reader restore, library action, and transfer import/export. Lists use explicit pagination; chrome remains fixed; navigation/progress changes are immediate; no source result depends on animation, color alone, pull-to-refresh, infinite scroll, or swipe-only input. |
| E-ink display evidence | On a physical E-ink device, record model, Android version, effective profile, logical refresh policy, observed ghosting/refresh limitations, tap/volume-key behavior, image loading, dialog feedback, and WebView return. “Refresh screen” is documented as a generic redraw request, not a vendor hardware waveform claim. |
