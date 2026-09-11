<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Tsuyomi Protocol

Platform-neutral contracts shared by Tsuyomi hosts and source tooling:

- portable transfer data (`tsuyomi-transfer` v3 output with strict v1/v2/v3 input);
- reading locators and progress merge rules;
- `.hxp` extension manifests and package integrity;
- signed read-only `hxp-update-check-v2` requests, normalized complete chapter evidence, and append-only admission fixtures;
- deterministic fixtures and conformance suites.

The protocol contains no Android, iOS, Compose, SwiftUI, or database-specific API. `hikari_novel_backup` is a legacy Flutter import format, not a Tsuyomi output format.

See `docs/transfer-v2.md` and `docs/transfer-v1.md` for current/legacy transfer contracts, plus `docs/reader-document-v1.md`, `docs/forum-navigation-v1.md`, `docs/hxp-manifest-v1.md`, `docs/hxp-host-api-v1.md`, `docs/hxp-package-v1.md`, and `docs/tsuyomi-repository-v1.md`.
