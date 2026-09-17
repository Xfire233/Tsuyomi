<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# ADR 0019: Stable credential-bound cache partitions

- Status: Accepted
- Date: 2026-09-18

## Problem

ADR 0018 holds source credentials in one encrypted record per source/origin partition and exposes a
non-secret revision so credential-bound caches can be scoped to the credentials they were fetched
with. That revision was derived from the whole encrypted record, which contains the random
initialisation vector of the write. Any write therefore produced a different revision, including a
write that stored byte-identical cookies.

The verified browser transport rewrites the captured cookie jar after it fetches a page. On a network
that answers direct HTTP with a challenge, that transport is the only path that returns content, so
credentials were rewritten constantly and each rewrite orphaned every cached page: the network cache
could be written but was never reachable again, and content that had already been fetched had to be
fetched again. Deriving the revision from the ciphertext also coupled a cache identity to an
encryption artifact rather than to the credential identity that actually matters.

## Constraints

- The revision must remain non-secret and must not reveal cookie bytes or values.
- It must stay inside the authenticated record, so a tampered revision fails decryption rather than
  silently redirecting a cache partition.
- An existing record written by an earlier version must remain readable, and an upgrade to the new
  layout must not require the user to sign in again.
- Isolation between identities must be preserved: a different login must not observe the previous
  login's credential-bound cache.

## Decision

The credential record gains schema version 2, which carries a random cache partition identifier as a
plaintext field of the record. The identifier is bound into the AEAD associated data alongside the
existing schema version, key version, source ID and origin, so it cannot be altered without failing
authentication.

- A write reuses the identifier already present in the record, and mints one only when no v2 record
  exists, when the caller requests a renewal, or after the partition was deleted.
- The session store requests a renewal when the admitted cookie-name set changes, which is what a
  login or logout does, and does not renew for a value-only refresh such as a rotated challenge
  cookie.
- A schema version 1 record stays readable and keeps its previous revision until its next write, which
  upgrades it to version 2 in place.
- The derived revision is the identifier itself for version 2 records and the previous digest for
  version 1 records, so no existing cache entry is addressed by a changed key mid-upgrade.

## Consequences

Credential-bound caches survive credential refreshes, so a rotated challenge cookie no longer discards
fetched content, and the identity that scopes a cache is the credential identity rather than an
encryption artifact. Deletion or an explicit renewal still starts a fresh partition, so identity
isolation and logout semantics are unchanged.

One consequence is deliberate and must be stated: a credential change that only alters cookie values
no longer invalidates credential-bound caches. A source that needs a cache discarded for a reason
other than a change of identity has to delete the partition or request a renewal explicitly.

The revision is now stable across writes, so it can no longer be used to detect that credentials were
rewritten. Nothing may treat a change of revision as evidence of a credential refresh.
