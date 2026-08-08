<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Library shelves and smart collections

## Terms

A book is the host record keyed by `(sourceId, remoteBookId)`. A collection is a named library view. Source website folders/favorites are source state; they are never the same object as host collections.

| Collection kind | Membership source | Gate 0–3 behavior |
|---|---|---|
| System | fixed host query | All, Continue reading, Recent reading, Unread updates, Dormant sources |
| Manual | `collection_book` many-to-many relation | create, reorder, nest for presentation, multi-select add/remove |
| Smart | validated local rule compiled to Room query | create, edit, observe live result; no stored result list |
| Subscription | source discovery intent + observed candidate ledger | data model only; direct user refresh after compatible source contract |

A book may belong to any number of manual collections. Parent/child collections arrange navigation only; a child does not inherit its parent's predicate or membership.

## Tables and invariants

```text
collection(id, kind, name, parentId?, displayOrder, ruleVersion?, createdAt, updatedAt)
collection_book(collectionId, sourceId, remoteBookId, addedAt, displayOrder)
smart_rule(collectionId, version, astJson, compiledProjectionVersion)
subscription(collectionId, mode, sourceScope, queryJson, enabled=false)
subscription_candidate(collectionId, sourceId, remoteBookId, firstSeenAt, lastSeenAt, isNew, lastSuccessfulRunId)
collection_refresh_run(id, collectionId, startedAt, completedAt?, completeness, outcome)
```

- `collection_book` is unique on `(collectionId, sourceId, remoteBookId)`.
- Manual collection ordering is stable and repaired transactionally after moves/deletes.
- Parent graph is acyclic and depth-bounded.
- System collections cannot be renamed, moved, or deleted.
- Smart collections cannot receive direct membership writes.
- Candidate deletion in replace mode requires a completed run marked complete.
- Source uninstall makes related book rows dormant; it does not delete collection relations.

## Smart rule language

The persisted rule is a typed AST, not executable text:

```text
All([ SourceIn, Any([TagContains, AuthorContains]), Not(DormantSource) ])
```

Initial predicate set:

- `SourceIn(sourceIds)`;
- `InManualCollection(collectionIds)`;
- `TagContains(all|any, normalizedTags)` and `FacetIn(sourceId, facetIds)`;
- `TitleContains(terms)` and `AuthorContains(terms)` through normalized FTS;
- `StatusIn(ongoing|completed|hiatus|cancelled|unknown)`;
- `RatingBetween(min,max)`;
- `AddedWithin`, `LastReadWithin`, `MetadataUpdatedWithin`;
- `ProgressIn(unstarted|reading|finished)`;
- `HasUnreadUpdate`, `HasSourceUpdate`, and `IsDormantSource`.

The compiler validates max depth/node count/term length then produces parameterized Room queries over `book_search_projection`. It never concatenates user text into SQL and never calls source extensions during evaluation. Query and display sort are separate: title, author, added, last read, metadata update, progress, rating, and source order are initial sorts.

## Source subscription collections

A subscription is an explicit discovery query. Its source scope, keyword/tags/author/facet inputs, and request rate are validated against the installed extension's declared discovery contract. It is not a generic remote query blob.

```text
user refresh
→ source extension discovery request
→ typed CandidatePage { items, complete, next? }
→ run audit
→ candidate ledger
→ collection UI
→ user explicitly adds selected candidate to library
```

`incremental` only adds/updates seen candidates. `replace` removes stale candidates after a complete successful refresh. New badges clear only when the user views the collection or explicitly marks them read. A subscription refresh never changes source website favorites, local manual shelves, or library membership without user action.

Gate 3 implements system, manual, and smart collections. Subscription execution waits for the source discovery API after the Wenku8 read path is stable.

## Hikari migration

- Folder records become manual collections; legacy one-folder placement becomes one explicit membership.
- Simple tag/source/author/title/update/rating/date rules migrate to the AST.
- Legacy `section` maps only when the source provides a stable normalized facet; otherwise it becomes a disabled draft warning.
- Legacy subscription membership and sync metadata are imported for audit only; no remote query or source configuration is enabled.
- User-created folder cover file paths are discarded; visual covers are reselected locally.

## UI and E-ink

Collection list/result state is a single `StateFlow` per screen. Multi-select and paging commands are explicit. In E-ink profile, collection paging/reorder/refresh use stable fixed chrome and textual state; swipe-only gestures, animated result replacement, and color-only new/update badges are prohibited.
