<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Tsuyomi Extensions

TypeScript ES Module source extensions and tooling for signed `.hxp` packages. Wenku8, ESJZone, and Yamibo must use the same public contract as all other sources.

Extensions receive only platform-neutral Host APIs: constrained HTTP, encoding, scoped cookies, controlled Web login, and isolated storage. They cannot access Android framework APIs, arbitrary files, or unrestricted network destinations.

No CI test may depend on a live content site. Use recorded, sanitized fixtures and the protocol conformance runner.
