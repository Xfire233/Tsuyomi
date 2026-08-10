<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

## Contract

- Gate / issue:
- User-visible behavior and non-goals:
- Affected components and versions:
- Rollback boundary:

## Cross-component order

- [ ] Protocol schemas/fixtures updated before producers and consumers
- [ ] Extension manifests/packages match the declared protocol version
- [ ] Android consumes only versioned contracts; no implementation import across boundaries

## Evidence

- [ ] Source fix; no suppression, dead compatibility path, credential, private content, or local machine state
- [ ] Relevant unit/JVM/conformance tests
- [ ] Android instrumentation where lifecycle/API/storage/security changed
- [ ] Real composable screenshot/golden diff where UI changed
- [ ] API 29 and Standard/E-ink matrix where applicable
- [ ] Separate API 29 portrait evidence for `Tsuyomi_API29` 1080×2400 forced Standard and `Tsuyomi_EInk_API29` 1264×1680 forced E-ink; landscape/goldens do not substitute
- [ ] Dependency locks, verification metadata, notices, REUSE, and repository artifact policy
- [ ] Documentation, changelog, version, and rollback notes updated
