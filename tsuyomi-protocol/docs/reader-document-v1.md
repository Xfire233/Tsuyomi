<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Reader document and locator v1

`ReaderDocument` is the source-to-reader data boundary. It is immutable, presentation-neutral JSON validated by `schemas/reader-document-v1.schema.json`. It contains a source/book/content identity, source revision, content digest, and ordered stable blocks. It never carries raw HTML, a network response, a WebView, Compose types, parser callbacks, cookies, or a rendered page number.

Initial blocks are heading, paragraph, image, divider, quote, and post. Block IDs are source-owned stable identifiers. A `post` is a forum block with immutable `postId`, author identity/display data, optional floor/timestamp/reply reference, and non-post visual body blocks. A source may change physical page/floor/index; its `postId` must remain the primary reader anchor.

`ReaderLocator` is validated by `schemas/reader-locator-v1.schema.json`. It contains document identity, optional source revision, stable block ID, SHA-256 text-anchor digest, Unicode code-point character offset, bounded chapter/book fallbacks, and capture time. The resolver tries exact block/anchor/offset, nearby matching anchor, block progress, then whole-content fallback. Rendered page/spread indices, scroll offsets/extents, font settings, image state, and layout cache data are forbidden durable state.

A document content digest identifies exactly the normalized block payload. A changed digest or revision creates a reflow/restore event, not a second identity. Hosts validate source result DTOs before cache admission; invalid documents and unknown blocks are typed parser errors.

Forum catalogue routing is deliberately outside `ReaderDocument` and specified by [`forum-navigation-v1.md`](forum-navigation-v1.md). A document renders one resolved physical page; navigation records which original source routes alias that page and which derived owner entries passed host verification. This separation lets the host preserve direct source selection, de-duplicate only directional traversal, and restrict semantic cross-page jumps to explicit history-resume transitions.

The fixture set begins with an ordinary thread page and semantic post locator. Phase 0 grows it with plain chapter, mixed image/text, malformed block, missing-anchor, content-revision, and cross-host Unicode-offset cases.