<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Contributing

- Schemas must remain platform-neutral and explicitly versioned.
- Every schema behavior change requires valid and invalid fixtures plus conformance coverage.
- Portable transfer artifacts must never contain cookies, tokens, browser sessions, cache files, or device secrets.
- New dependencies and copied/adapted upstream code require a pinned-source entry in `THIRD_PARTY_NOTICES.md`.
- Use SemVer and update `CHANGELOG.md`; breaking schema behavior requires a new versioned contract, migration note, and downstream compatibility evidence.
- Dependency changes must update `package-lock.json`, `THIRD_PARTY_NOTICES.md`, REUSE metadata, and conformance evidence together.
- Phase evidence must identify the exact protocol Git SHA, version, fixture digest, and downstream Android/extensions SHAs.
- Run `python ../tools/check_repository.py --scope protocol`; generated output, secrets, local state, and unknown root files are forbidden.
