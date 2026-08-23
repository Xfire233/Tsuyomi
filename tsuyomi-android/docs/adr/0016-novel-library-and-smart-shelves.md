<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# ADR 0016: Many-to-many novel library shelves and deterministic smart collections

- Status: Accepted
- Date: 2026-08-08

## Problem

A novel reader needs manual organization, continue-reading views, update triage, source/author/tag discovery, rating-based selection, and eventually source subscription shelves. Hikari's one-class bookshelf arrangement and preference-JSON smart shelf evaluation do not give stable multi-shelf membership, transactional behavior, efficient filtering, or a clean source-extension boundary.

## Research basis

- Mihon stores categories independently and models library membership as a many-to-many relation with indexed foreign keys; category behavior and update filters are separated from source implementations.
- Hikari already has valuable smart conditions (tag, source, section, author, title, update, rating, date), nested all/any groups, subscription `replace`/`incremental`, and new-item markers.
- Hikari's smart folders and memberships reside in settings JSON and are evaluated from UI/controller paths. Those mechanisms are not carried forward.

No code is adopted. Mihon is Apache-2.0; any later source adoption requires the normal notice process.

## Decision

The host library uses typed collections with stable host IDs:

- `manual`: user-created shelves with explicit many-to-many book membership;
- `smart`: a local, deterministic rule over host-owned book projection data;
- `subscription`: an explicit source-discovery intent plus an observed-candidate ledger.

Collection hierarchy is presentation-only: parent collections group the navigation tree but never alter membership semantics. System collections—All library, Continue reading, Recent reading, Unread updates, and Dormant sources—are immutable definitions, not rows users can delete.

**2026 RC2.1 conflict-reconciliation amendment:** “immutable” applies to each system collection's stable identity and query definition, not to a mandatory always-visible presentation row. `Continue reading`, `Recent reading`, `Read later`, `Updates` and `Dormant sources` are created by default as Library `SystemNode` presentations; users may hide them and rebuild them from the shared create flow. They may be repositioned in manual-order mode without renaming or changing their rules. Only `Read later` accepts explicit membership writes; all other system membership remains derived. Manual collection presentation depth is capped at two levels. Creating a folder by dropping one book onto another requires a name/effect confirmation and atomically creates one manual collection containing both books.

A smart rule is a versioned, bounded AST: `all`, `any`, `not`, and typed predicates. Initial predicates are source identity, manual shelf membership, normalized tags/facets, author/title terms, source status, rating range, added/read/metadata-update windows, progress state, unread/update state, and dormant-source state. Human text predicates use normalized Room FTS/search projection. There is no arbitrary SQL, JavaScript, regular expression, source HTML, or network condition in a smart rule.

Smart membership is computed from Room projections and is never stored as a mutable duplicate list. Room invalidation re-evaluates affected collection flows after a transaction. Rule schema validation limits AST depth, node count, term size, and supported fields before persistence; unknown rule versions are read-only/disabled rather than guessed.

A subscription collection is not a remote write target and does not run automatically in Gate 0–3. A direct user refresh invokes a declared extension discovery operation and records candidates using stable source identity, first/last seen timestamps, source-response completeness, and `isNew`. `incremental` retains earlier candidates; `replace` removes only candidates absent from a successful, complete response. A failed, cancelled, or incomplete response never removes candidates. Subscription candidates become library books only through an explicit user action.

## Rejected alternatives

- One mutable `classId` per book: rejected because books legitimately belong to several shelves and it conflates placement with source ownership.
- Persist smart result IDs as canonical membership: rejected because results become stale and duplicate database query semantics.
- Execute arbitrary user rules or extension code against the host database: rejected for security, performance, portability, and migration reasons.
- Silent scheduled source refresh: rejected by the local-first policy; it can create unexpected network activity and source-side load.
- Treat a smart collection as a remote favorite folder: rejected because local organization and website mutation must remain separate.

## Migration impact

Hikari manual folders map to manual collections and explicit memberships. Compatible local smart conditions map into the typed rule AST. Unsupported conditions and all subscription source queries import as disabled drafts with warnings; neither source enabling nor remote synchronization occurs. Existing subscription `replace`/`incremental` semantics are retained only after a source declares a compatible discovery contract.

## Verification

- Foreign-key, unique-membership, collection-cycle, sort-order, and system-collection tests pass.
- Rule parser/compiler tests cover validity limits, all/any/not precedence, FTS terms, time boundaries, and source update invalidation.
- A source refresh proves complete replace, incomplete response, incremental merge, duplicate identity, new-marker clearing, cancellation, and no implicit remote write.
- System-node verification distinguishes immutable query definition from hide/rebuild/reorder presentation state; only Read Later accepts membership writes.
- Folder-drop verification proves cancel is a no-op and confirm atomically creates the named collection with both memberships.
- E-ink and standard profiles expose the same collection actions through explicit pagination and non-color-only update state.
