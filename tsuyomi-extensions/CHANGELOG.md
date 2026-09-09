<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Changelog

## [Unreleased]

### Changed

- Wenku8 推荐 now parses the source homepage into its source-ordered seasonal, new-book, and member-recommendation sections instead of substituting recommendation-metric toplists; its parsed “这本轻小说真厉害！” year link opens a typed read-only feature destination with separate 文库部门 and 单行本部门 sections. 分类 / 排行 / 完结 and bounded Tag filtering remain available.
- Wenku8 Detail emits a validated optional ISO source update date, and author search uses exact bounded GB18030 transport. Remote target discovery now has a separate signed request; ADD/REMOVE/MOVE parsers bind success to the exact book/target and reject ambiguous or fabricated evidence.
- Regenerated signed development fixture version `0.2.30` with SHA-256 `36db147337636ddc1b5bb00a979e42bb82e97f419ab374c6a5cb62ce852d6af6`; its signed update-check capability requires Host API compatibility `[1.2.0, 2.0.0)`.
- Wenku8 now exports signed read-only `update-check-v2` request and parser operations. It emits exact complete source-order evidence only after closed canonical-directory admission, then emits chapter IDs/titles; baseline, appended, reordered, wrong-identity, challenge, truncated, paginated and partial-directory fixtures prove fail-closed parsing, while Android admission tests cover title correction.
- Fixed live Wenku8 update checks incorrectly rejecting complete dynamic directory pages because they lack a `#list` wrapper. Admit the canonical standalone `table.css` as well as the static directory, require a unique complete table, and continue rejecting ambiguous, wrong-book, paginated or truncated evidence. Sanitized live-shape regression excludes unrelated links and preserves exact chapter ordering.

## [0.1.0] - 2026-08-09

### Added

- Phase 0/1 extension development, security, packaging, and Wenku8 acceptance contracts. Runtime extension code starts in Phase 2.
