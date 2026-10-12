<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Reader document and locator v1

`ReaderDocument` is the source-to-reader data boundary. It is immutable, presentation-neutral JSON validated by `schemas/reader-document-v1.schema.json`. It contains a source/book/content identity, source revision, content digest, and ordered stable blocks. It never carries raw HTML, a network response, a WebView, Compose types, parser callbacks, cookies, or a rendered page number.

Initial blocks are heading, paragraph, image, divider, quote, and post. Block IDs are source-owned stable identifiers. A `post` is a forum block with immutable `postId`, author identity/display data, optional floor/timestamp/reply reference, and non-post visual body blocks. A source may change physical page/floor/index; its `postId` must remain the primary reader anchor.

`ReaderLocator` is validated by `schemas/reader-locator-v1.schema.json`. It contains document identity, optional source revision, stable block ID, SHA-256 text-anchor digest, Unicode code-point character offset, bounded chapter/book fallbacks, and capture time. The resolver tries exact block/anchor/offset, nearby matching anchor, block progress, then whole-content fallback. Rendered page/spread indices, scroll offsets/extents, font settings, image state, and layout cache data are forbidden durable state.

A document content digest identifies exactly the normalized block payload. A changed digest or revision creates a reflow/restore event, not a second identity. Hosts validate source result DTOs before cache admission; invalid documents and unknown blocks are typed parser errors.

Forum catalogue routing is deliberately outside `ReaderDocument` and specified by [`forum-navigation-v1.md`](forum-navigation-v1.md). A document renders one resolved physical page; navigation records which original source routes alias that page and which derived owner entries passed host verification. This separation lets the host preserve direct source selection, de-duplicate only directional traversal, and restrict semantic cross-page jumps to explicit history-resume transitions.

The fixture set begins with an ordinary thread page and semantic post locator. Phase 0 grows it with plain chapter, mixed image/text, malformed block, missing-anchor, content-revision, and cross-host Unicode-offset cases.

## Rich-content source-wire v2

[`reader-document-v2.schema.json`](../schemas/reader-document-v2.schema.json) adds bounded Unicode-code-point inline spans, ruby annotations, internal/external links, note references, semantic lists and basic tables, together with explicit anchor targets. V1 remains immutable and supported for existing sources. Both source-wire versions use remote source/book/content identity and HTTPS-only image references: a source cannot submit a local publication identity, file/content URI, native handle or host-owned resource reference. Host-owned local publications use the shared typed Reader model behind this trust boundary; neither a fabricated source ID nor an installed extension is required.

## Android host semantic admission

Schema validation checks the versioned wire shape, not every relation or resource budget. Android applies the same Reader admission policy to validated remote results, normalized host-cache replay and direct construction of the shared typed document. Local publication parsers retain their separate archive, markup and publication-wide budgets; one document's limits are not whole-book limits.

| Field or structure | Admission bound |
|---|---|
| Document title | 1–4,096 Unicode code points, matching both wire versions |
| Root blocks | 1–50,000, matching both wire versions |
| All ReaderBlock occurrences, including roots and list/note/post containers | At most 50,000 across the entire document |
| ReaderBlock depth | At most 96; root blocks have depth 1, and a block in a list item, note or post body has its parent block's depth + 1 |
| A list item, note or post body | 1–5,000 blocks, matching the applicable wire body-array bound |
| V2 paragraph, heading or quote text | 1–200,000 Unicode code points |
| V1 paragraph / heading / quote text | 1–65,536 / 1–16,384 / 1–65,536 Unicode code points; immutable v1 remains supported |
| V2 table cell text | At most 100,000 Unicode code points; empty text is allowed |
| Canonical table text, including cell/row separators | At most 100,000 Unicode code points as an additional host resource budget |
| Spans on one prose block or table cell | At most 20,000 |
| List items / table rows / table columns / explicit anchor targets | At most 10,000 / 20,000 / 16 / 50,000, respectively |
| Internal-link content ID / fragment | 1–1,024 / 1–256 Unicode code points, respectively |
| Image alternative text / quote attribution / note label | At most 4,096 / 4,096 / 256 Unicode code points; an explicitly present empty string is allowed |

Each block occurrence consumes the aggregate budget once, including container blocks. List-item wrappers, table rows/cells, spans and anchors are not ReaderBlock occurrences. The decoder reserves each complete block-array count against one shared per-document budget before mapping its elements; neither a new root nor a sibling body resets that budget. Depth is checked before child decoding. Typed document admission checks depth and aggregate count before appending each flattened reference. Invalid input never becomes an admitted partial document.

Before invoking the JSON tree parser, Android scans raw source-result and document-cache text iteratively, ignoring container-looking characters inside correctly escaped JSON strings. Its shared envelope is 390 object/array containers: `4 × 96 + 6`, derived from the deepest supported list chain ending in a table-cell link target. JSON container depth is not ReaderBlock depth. The subsequent decoder still enforces the 96-block bound. Recursive schema references and per-array `maxItems` do not independently express an aggregate descendant count or finite chain depth; neither the schema nor a serializer's internal recursion strategy replaces these guards.

Span ranges refer to canonical Unicode code-point offsets and must satisfy `0 ≤ start < end ≤ text length`. Input order is nondecreasing start; spans sharing a start place the enclosing span before its enclosed span. Equal ranges remain valid. Disjoint or touching siblings and proper nesting are allowed; crossing ranges are rejected. Hosts validate this ordered, laminar geometry without reordering or deduplicating source spans. JSON numbers and booleans must be literal primitives, never quoted strings that happen to parse as numbers or booleans.

Optional source-wire properties may be omitted, but a present value must have the published type; neither v1 nor v2 admits explicit nulls. This includes prose and table-cell spans, image metadata, block anchors, post metadata and internal-link fragments. V1 post author IDs and names are required nonempty strings, and v1 posts do not permit an `anchorId` field. Nullable metadata in the deployed prior adapter and normalized host-cache formats retains its own compatibility grammar; those host/legacy nulls are not valid substitutes for protocol-wire values.

Note identities must be unique, and every note-reference span must name an admitted note body, including forward references. Host admission retains one note-ID set for duplicate checking and reference lookup rather than repeatedly searching a note-ID list.

### Image ratio metadata is not a pixel declaration

V1's optional `aspectRatio` is an exact finite width/height ratio greater than zero and at most 100. An absent hint remains absent. The shared model and host cache retain it as optional Double metadata, separate from actual `width` and `height`; the host must not manufacture pixel dimensions from it. V2's wire image shape is unchanged and does not gain an `aspectRatio` alias. Existing local/remote host resource rules still constrain HTTPS URLs to 4,096 characters, declared dimensions to 1–100,000 per axis and at most 50,000,000 declared pixels when both dimensions are present; decoded-media admission retains its independent byte/pixel/frame policy.

Native layout prefers decoded image dimensions, then complete declared dimensions, then the ratio hint, then its existing missing-metadata fallback. Hint-based presentation computes bounded geometry from the actual available viewport in Double precision, rather than converting a very small valid ratio into a zero Float or inventing canonical dimensions. Viewport fitting does not change the stored hint or grant additional image resource authority. Existing host caches without a ratio remain readable and do not acquire a fabricated hint.
