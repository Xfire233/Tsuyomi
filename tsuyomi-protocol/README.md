<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Tsuyomi Protocol

Platform-neutral contracts shared by Tsuyomi hosts and source tooling:

- portable transfer data (`tsuyomi-transfer` v6 output with strict v1–v6 input);
- reading locators and progress merge rules;
- `.hxp` extension manifests and package integrity;
- signed read-only `hxp-update-check-v2` requests, normalized complete chapter evidence, and append-only admission fixtures;
- deterministic fixtures and conformance suites.

The protocol contains no Android, iOS, Compose, SwiftUI, or database-specific API. `hikari_novel_backup` is a legacy Flutter import format, not a Tsuyomi output format.

The current transfer shape is [`tsuyomi-transfer-v6.schema.json`](schemas/tsuyomi-transfer-v6.schema.json). It replaces implicit remote book IDs with explicit `remote` or `local` identities. Local records preserve a portable publication UUID, format, original size/SHA-256, optional cover resource ID and TXT normalization choices; they never carry original bytes, provider paths, URI grants or network permission. Bookmark documents must belong to their enclosing book. A metadata-only restore without independently verified original bytes is a missing, re-associable publication, not an offline-ready book.

V6 progress may carry its document `revision` (a non-empty string of at most 256 characters), preserving semantic reading positions through metadata/archive recovery. Same-ID original fingerprint conflicts retain the destination's original association independently of newer ordinary metadata; transfer timestamps do not authorize replacement of original bytes.

V1–v5 remain closed, remote-only input contracts; their identities and chapter-start/bookmark migration rules remain unchanged. See [`transfer-v5.md`](docs/transfer-v5.md), [`transfer-v4.md`](docs/transfer-v4.md), [`transfer-v2.md`](docs/transfer-v2.md) and [`transfer-v1.md`](docs/transfer-v1.md) for those legacy boundaries. Reader source-wire contracts are documented in [`reader-document-v1.md`](docs/reader-document-v1.md); source packages cannot manufacture host-owned local identities or resource grants. See also [`forum-navigation-v1.md`](docs/forum-navigation-v1.md), [`hxp-manifest-v1.md`](docs/hxp-manifest-v1.md), [`hxp-host-api-v1.md`](docs/hxp-host-api-v1.md), [`hxp-package-v1.md`](docs/hxp-package-v1.md) and [`tsuyomi-repository-v1.md`](docs/tsuyomi-repository-v1.md).
