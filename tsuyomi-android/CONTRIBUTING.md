<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Contributing

- New source files require SPDX copyright and license identifiers.
- New dependencies and copied/adapted upstream code require an entry in `THIRD_PARTY_NOTICES.md` with a pinned source revision and adoption scope.
- Do not add user accounts, cloud synchronization, telemetry, crash reporting, CAPTCHA bypass, or Android-specific APIs to the extension host contract without an ADR.
- Preserve the dependency direction in `docs/architecture/MODULES.md`.
- Do not place session credentials, cookies, source content, private signing keys, or local SDK paths under version control.
- Follow `docs/process/QUALITY_GATES.md`, `docs/process/REPOSITORY_GOVERNANCE.md`, and `docs/design/OPTION_APPLICABILITY.md`; a persisted field or enum is not evidence that a UI control is implemented.
- Every named admission or review gate approval and finding closure must bind to immutable Git input and be recorded under `docs/phases` / `docs/reviews`.
- Dependency changes must update the version catalog, Gradle lock state, verification metadata, `THIRD_PARTY_NOTICES.md`, and validation evidence in one reviewable change.
- Run `python ../tools/check_repository.py --scope android` before release; build output, local SDK state, dumps, credentials, and unknown root files are forbidden.
