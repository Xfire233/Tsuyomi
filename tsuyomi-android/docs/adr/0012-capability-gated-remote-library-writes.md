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

Protocol v1 supports explicit remote-library write capabilities. A manifest declares the supported operations from `add`, `remove`, and `move`. Installation or update presents these operations separately from read-only network access. Adding any write operation is a capability escalation requiring approval.

The host keeps writeback disabled per source by default. Enabling it is an explicit user action after the package capability grant. Remote removal requires a separate source setting from remote add/move. UI actions state when they will affect the website and identify the destination remote shelf when applicable.

Only a direct user library action may initiate a remote write. Backup import, transfer import, login-state checks, WebView completion, background refresh, extension installation, and local data migration cannot trigger it. Failure leaves local and remote states explicitly reported rather than pretending they are synchronized.

## Rejected alternatives

- Pull-only forever: rejected because users requested opt-in parity for supported source operations.
- Treat network permission as sufficient authorization: rejected because read access does not communicate mutation risk.
- Enable writeback automatically after login or migration: rejected because it turns local state transitions into surprising external mutations.

## Migration impact

Gate 3 ports only the public typed Wenku8 fixture read/add path: explicit remote-favourites pull plus default-off, direct-user, add-only writeback. ESJZone, remote remove/move/folder actions and all automatic or bidirectional synchronization remain later Gate 4 scope. Legacy writeback settings are not imported or enabled automatically.

## Verification

- A package without the declared operation cannot invoke it; adding an operation during update blocks until approved.
- Writeback is off after fresh install, transfer import and Hikari import.
- Gate 3 covers remote-favourites read, add, grant denial, cancellation, partial/ambiguous failure, source/API change and zero-implicit-write paths. Later Gate 4 evidence covers remove and move.
