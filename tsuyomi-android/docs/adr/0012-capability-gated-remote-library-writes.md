<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# ADR 0012: Capability-gated remote library writes

- Status: Accepted
- Date: 2026-08-08

## Problem

Hikari Novel Plus can optionally add or remove website favorites and move Wenku8 entries between remote shelves. These operations mutate external user data and have materially higher risk than browsing or pulling favorites.

## Constraints

- Remote library APIs differ by source and can change without notice.
- Import, login checks, WebView closure, local deletion, and ordinary refresh must never cause implicit remote mutation.
- Users need to understand which source and operation they authorize.

## Decision

Protocol v1 supports explicit remote-library write capabilities. A manifest declares the supported operations from `add`, `remove`, and `move`. Installation or update presents these operations separately from read-only network access. Adding an operation, changing its signed canonical request policy, increasing its resource policy, changing its applicable origin, or changing the trusted publisher is a capability escalation requiring approval.

The host keeps writeback disabled per source by default. Enabling it is an explicit user action after the package capability grant. A package-specific integrity approval is distinct from a stable canonical effective-grant fingerprint: a same-publisher policy-identical update may retain the setting, while every policy/capability/origin/key change disables it pending approval. Remote removal requires a separate source setting from remote add/move. UI actions state when they affect the website and identify the destination remote shelf when applicable.

Only a direct user library action may initiate a remote write. Host API 1.1 requires the host to mint an immutable operation context carrying the exact signed method/origin/path/referrer/redirect policy and a complete parameter grammar: values are canonical fixed literals or typed host bindings, never extension-chosen operation/folder/mode/target values. Native transport validates it before every initial or redirect request. `add` additionally requires a one-use reconciliation token bound to source, book, package/version, capability grant and owner generation. Backup import, login-state checks, WebView completion, background refresh, extension installation, local data migration and read/search/detail/chapter contexts cannot trigger it. Failure leaves local and remote states explicitly reported rather than pretending they are synchronized.

Transport acceptance is the cancellation linearization point. Proven pre-accept cancellation is `CANCELLED`; only a typed `applied` / `already-present` result is `CONFIRMED`; every other post-accept result is `UNRESOLVED`.

## Rejected alternatives

- Pull-only forever: rejected because users requested opt-in parity for supported source operations.
- Treat network permission as sufficient authorization: rejected because read access does not communicate mutation risk.
- Enable writeback automatically after login or migration: rejected because it turns local state transitions into surprising external mutations.

## Migration impact

Gate 3 ports only the public typed Wenku8 fixture read/add path: explicit remote-favourites pull plus default-off, direct-user, add-only writeback. ESJZone, remote remove/move/folder actions and all automatic or bidirectional synchronization remain later Gate 4 scope. Legacy writeback settings are not imported or enabled automatically.

- A package without the declared operation, exact approved policy/context or exact one-use token cannot invoke it; native transport records zero calls. Adding/changing an operation policy during update blocks until approved, while a same-publisher policy-identical update preserves the explicit setting.
- Writeback is off after fresh install, transfer import and Hikari import, and all updates except a preserving update.
- Gate 3 covers remote-favourites read, add, grant denial, malicious read-context/redirect attempts, lifecycle linearization, pre/post-accept cancellation, partial/ambiguous failure, source/API change and zero-implicit-write paths. Later Gate 4 evidence covers remove and move.
