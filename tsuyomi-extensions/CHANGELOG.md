<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Changelog

## [Unreleased]

### Changed

- Wenku8 推荐 now parses the source homepage into its source-ordered seasonal, new-book, and member-recommendation sections instead of substituting recommendation-metric toplists; its parsed “这本轻小说真厉害！” year link opens a typed read-only feature destination with separate 文库部门 and 单行本部门 sections. 分类 / 排行 / 完结 and bounded Tag filtering remain available.
- Wenku8 Detail emits a validated optional ISO source update date, and author search uses exact bounded GB18030 transport. Remote target discovery now has a separate signed request; ADD/REMOVE/MOVE parsers bind success to the exact book/target and reject ambiguous or fabricated evidence.
- Regenerated signed development fixture version 0.2.28 with SHA-256 `af8c94d85e3e5faa2021c48891cb170198be050df829d7c981a11b1add5ee3ad`; Host API compatibility remains `[1.1.0, 2.0.0)`.

## [0.1.0] - 2026-08-09

### Added

- Phase 0/1 extension development, security, packaging, and Wenku8 acceptance contracts. Runtime extension code starts in Phase 2.
