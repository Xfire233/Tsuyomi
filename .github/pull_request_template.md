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
- [ ] Dependency locks, verification metadata, notices, REUSE, and repository artifact policy
- [ ] Documentation, changelog, version, and rollback notes updated
