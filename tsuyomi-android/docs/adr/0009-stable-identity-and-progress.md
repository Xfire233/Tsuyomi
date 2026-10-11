<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# ADR 0009: Stable remote identity and `updatedAt` progress conflict resolution

- Status: Accepted
- Date: 2026-08-08

## Problem

Local database IDs, display titles, URLs, layout page numbers, and raw scroll percentages are not stable enough to identify books, chapters, or reading positions across imports, extension updates, and reader modes.

## Constraints

- Extensions may be uninstalled and reinstalled without deleting library records.
- Chapter content and pagination can change.
- Horizontal, vertical, dual-page, and variable-height reply readers need compatible restoration semantics.

## Decision

A remote book is identified by `(sourceId, remoteBookId)`. Local database keys are implementation details. An absent extension leaves the record dormant while retaining metadata, shelves, tags, rating, and progress.

Progress uses a stable chapter ID when available plus a semantic locator: text anchor, character offset, chapter progression, and whole-book progression in descending precision. Reader implementations persist semantic position rather than treating a rendered page number or mutable scroll extent as canonical.

When two valid progress records conflict, the record with the newer `updatedAt` wins. A numerically later position does not win merely because it is further through the book. Equal timestamps use a deterministic protocol-defined tie break; they are never merged by taking maximum progress.

### 0.5.0 local-publication identity and duplicate intake — accepted 2026-10-06

Remote identity remains unchanged. Local publications require a portable host-generated ID distinct from private Room row IDs, filenames, provider URIs and raw-byte fingerprints. Metadata edits, file renaming, cache rebuild and display-profile changes cannot change it. A fingerprint identifies original bytes, not a unique reading-state owner: independent copies may share identical bytes.

The maintainer selected confirmation on duplicate import, superseding the plan's proposed automatic same-byte association. After verifying matching bytes, offer association with an existing publication or creation of an independent copy; association never resets progress/bookmarks. Multiple matches require an explicit target, not the first match. An independent copy has a new publication ID and independent state. Different bytes/editions/formats remain independent by default; never merge by title or silently replace an original. Missing-original reassociation requires a verified matching fingerprint.

## Rejected alternatives

- Use local row IDs: rejected because they do not survive import or cross-host transfer.
- Use canonical URL alone: rejected because URLs can change while source identity remains stable.
- Keep the furthest numeric progress: rejected because rereads and intentional backward movement are valid.

## Migration impact

Flutter `aid` prefixes are normalized into source IDs and remote IDs. Existing `locatorJson` is preferred; legacy location and progress values are bounded fallbacks. Reader migrations must preserve semantic location across layout modes.

## Verification

- Identity remains stable when titles, URLs, or local row IDs change.
- Progress conformance tests cover newer-backward movement, stale-forward movement, equal timestamps, missing anchors, and chapter-content changes.
- Reader recreation restores the same semantic location within a documented bounded fallback.
