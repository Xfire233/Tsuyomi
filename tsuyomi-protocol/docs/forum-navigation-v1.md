<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Forum navigation v1

`ForumThreadNavigation` is the source-to-host navigation projection for a thread. It is separate from `ReaderDocument`: the document tells the reader how to render one physical thread page; navigation tells the host which source catalogue routes refer to that page and which route must remain selectable.

Each catalogue entry has a stable source `entryId`, source `contentId`, `physicalPage`, source order, label, and role. Multiple alias entries may point to the same physical page/content. Semantic policy is host-owned: direct catalogue selection retains the exact requested entry; automatic previous/next moves between distinct physical pages in source order; non-directional restore chooses the canonical entry when present, otherwise the first alias. The host never fabricates a route absent from source data.

`ownerCatalogue` is a host-derived, optional navigation projection. It is never accepted directly from an unverified HTML link list. The host creates an entry only after a bounded user-initiated build verifies that the resolved target has the expected thread ID, post ID, physical page, owner identity, and nonempty content. It is keyed by the source fingerprint/revision and becomes stale on source content change. Failure, cancellation, partial work, and a result with fewer than two verified entries are not cache successes.

A history-resume locator may request a cross-page load after the navigation projection resolves its semantic post/floor target. A direct route must not be silently redirected to a different page by stale history. Page/floor/order are navigation fallbacks; `postId` remains the semantic reading anchor.

Source extensions return pure DTOs. The host owns request bounds, URL/origin checks, cancellation, cache admission, diagnostic redaction, derived-index persistence, and rendering.