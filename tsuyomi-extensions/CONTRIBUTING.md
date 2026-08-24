<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Contributing

- Extensions are TypeScript ES modules packaged as signed `.hxp` archives; APK-based extensions are out of scope.
- Add every domain, cookie scope, controlled WebView request, and storage requirement to the manifest.
- Do not automate CAPTCHA, Cloudflare, or anti-bot verification. The host may only let the user complete these flows in a controlled WebView.
- Use sanitized fixtures, never credentials, cookies, copyrighted chapter payloads, or live-site CI dependencies.
- Record every third-party adoption in `THIRD_PARTY_NOTICES.md`.
- Use SemVer and update `CHANGELOG.md`; every package/tool change records the compatible protocol version and deterministic artifact digest.
- Dependency changes must update lock state, `THIRD_PARTY_NOTICES.md`, REUSE metadata, and package verification evidence together.
- Phase evidence binds the exact extensions SHA to protocol and Android SHAs; do not test against ‘latest’ contracts.
- Run `python ../tools/check_repository.py --scope extensions`; built `.hxp`, secrets, live cookies, dumps, and unknown root files are forbidden.
