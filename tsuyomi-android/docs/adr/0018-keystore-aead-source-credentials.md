<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# ADR 0018: Android Keystore AES-GCM source credential partitions

- Status: Accepted
- Date: 2026-08-08

## Problem

The source transport needs durable per-source HTTP session state after a user-mediated login. It must not expose cookies to extension code, inherit Hikari cookies, place secrets in normal backup/export paths, or rely on a global WebView cookie store.

## Decision

`core/security` owns a single Android Keystore AES-256-GCM master key with alias `org.tsuyomi.android.source-credentials.v1`. The key is generated in AndroidKeyStore, uses `AES/GCM/NoPadding`, has randomized 96-bit IVs, is not exportable, and does not require per-use device authentication. Per-use authentication is rejected because a direct source retry must not unexpectedly block behind a biometric/lock prompt; the device's encryption and app sandbox remain the baseline protection.

Each encrypted source credential record is bound by AES-GCM additional authenticated data to the format version, source/extension identity, and exact declared HTTPS origin. It stores only schema version, key version, random IV, ciphertext, and nonsecret bookkeeping. Cookie pairs, tokens, usernames, raw request headers, plaintext diagnostics, and secret query values never enter Room search/index columns, DataStore, logs, transfer files, Hikari imports, crash reports, or Android backup.

Credential rotation creates a new versioned Keystore alias, decrypts and re-encrypts each accessible record transactionally, then retires the predecessor after a successful audit. The host may rotate eagerly during a foreground maintenance operation or lazily on first access; it never keeps two plaintext copies. A missing, invalidated, or permanently inaccessible key invalidates only affected credential records, clears the source session, and asks the user to log in again. Ciphertext restoration without the original Keystore key is intentionally unrecoverable.

Android automatic backup excludes all credential storage and Keystore key material. `tsuyomi-transfer` excludes every credential. Future explicit native backup may include only a separately user-authorized, password-encrypted credential export, behind a new ADR and protocol; it is not part of v1.

## Rejected alternatives

- Plaintext cookie header in DataStore/Room: rejected because database/files/logs can leak credentials.
- Android WebView `CookieManager` as durable source storage: rejected because it is process-global and not per-source.
- `EncryptedSharedPreferences`: rejected because it is not a sufficient source partition/cache abstraction for the required record lifecycle.
- Require user authentication for each decrypt: rejected because it impairs ordinary reading/source retry without materially improving the app's local-first threat model.
- Migrate Hikari cookies: rejected by ADR 0008 and because their origin/consent/lifecycle cannot be validated.

## Verification

- Unit tests cover AAD binding, IV uniqueness, record/source/origin swap rejection, rotation, partial rotation recovery, and redacted failures.
- Instrumented tests cover keystore invalidation behavior, lock/unlock continuity, app data clear, backup exclusion, uninstall/reinstall, source uninstall, and no plaintext in Room/DataStore/log fixtures.
- A controlled WebView test confirms only user-approved declared-origin request cookies are admitted to the partition and that clear/flush runs on every terminal path.
