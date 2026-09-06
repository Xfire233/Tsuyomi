<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Changelog

## [Unreleased]

### Changed

- Wenku8 推荐 now parses the source homepage into its source-ordered seasonal, new-book, and member-recommendation sections instead of substituting recommendation-metric toplists; its parsed “这本轻小说真厉害！” year link opens a typed read-only feature destination with separate 文库部门 and 单行本部门 sections. 分类 / 排行 / 完结 and bounded Tag filtering remain available.
- Wenku8 Detail now emits a validated optional ISO source update date, and the optional author-search entry point uses author matching with the existing bounded GB18030 transport. Signed development fixture version advances to 0.2.26; Host API v1.x compatibility is retained.
- Regenerated the signed 0.2.26 development fixture with SHA-256 `b49f256917a21a14c14fcd2d06b8a599fbc14af576aa9a05d8ec5f7d3bc0b25d`; it remains compatible with Host API `[1.1.0, 2.0.0)`.

## [0.1.0] - 2026-08-09

### Added

- Phase 0/1 extension development, security, packaging, and Wenku8 acceptance contracts. Runtime extension code starts in Phase 2.
