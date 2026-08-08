<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# ADR 0003: Local-first operation without accounts, cloud sync, telemetry, or crash reporting

- Status: Accepted
- Date: 2026-08-08

## Problem

Tsuyomi needs a clear ownership and privacy model for libraries, progress, settings, credentials, diagnostics, and backups.

## Constraints

- Source websites may require their own sessions, but Tsuyomi does not operate an application account service.
- Reader state must remain usable without a Tsuyomi-operated backend.
- Credentials, source content, and reading history are sensitive local data.

## Decision

The host is local-first. It provides no Tsuyomi account, cloud synchronization, telemetry, remote feature flags, or automatic crash-report upload. Library state, progress, settings, extension grants, and source-scoped credentials are stored locally.

Diagnostics are user-initiated and must be reviewable before sharing. Logs exclude credentials, cookies, raw private pages, chapter content, and secret-bearing backup fields. Network access is performed only for explicit source operations, extension metadata updates, or user-requested release checks.

## Rejected alternatives

- Mandatory account-backed synchronization: rejected because it creates an unnecessary service and trust boundary.
- Anonymous telemetry or hosted crash reporting: rejected because it still exports sensitive behavioral and device data.
- Store all state inside extensions: rejected because it prevents stable host-owned library and progress behavior.

## Migration impact

Legacy local records can be imported. Legacy source login state is handled separately from portable data and only with explicit consent. Imported source settings never trigger network operations automatically.

## Verification

- A fresh installation reaches local UI without creating an account.
- Network inspection shows no telemetry or crash-report endpoint.
- Exported portable transfer files contain no secrets.
- Source credentials are partitioned and can be cleared without deleting the local library.
