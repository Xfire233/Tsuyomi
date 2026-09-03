<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Third-party notices

This file records direct runtime/build/test dependencies and the public projects used for migration or architecture research. Transitive Android artifacts are fixed by Gradle lockfiles and `tsuyomi-android/gradle/verification-metadata.xml`; protocol development dependencies are fixed by `tsuyomi-protocol/package-lock.json`.

## Direct dependencies and build tools

| Project | Pinned version | License | Scope |
|---|---:|---|---|
| Gradle Wrapper | 8.14.3 | Apache-2.0 | Checked-in Android build wrapper |
| Android Gradle Plugin | 8.13.1 | Apache-2.0 | Android build tooling |
| Kotlin compiler and Compose plugin | 2.3.0 | Apache-2.0 | Build tooling/runtime metadata |
| Kotlin Symbol Processing | 2.3.11 | Apache-2.0 | Room code generation |
| AndroidX Compose BOM and UI/Foundation/Material3 | 2026.06.01 | Apache-2.0 | Android runtime |
| AndroidX Core / Activity / Lifecycle / Navigation | 1.19.0 / 1.13.0 / 2.10.0 / 2.9.8 | Apache-2.0 | Android runtime |
| AndroidX DataStore / Room | 1.2.1 / 2.8.4 | Apache-2.0 | Android runtime and tests |
| Material3 Adaptive | 1.2.0 | Apache-2.0 | Android runtime |
| Kotlinx Coroutines | 1.10.2 | Apache-2.0 | Android runtime and tests |
| Compose Preview Screenshot Testing | 0.0.1-alpha11 | Apache-2.0 | Screenshot test tooling |
| AndroidX Test runner/JUnit extension/Espresso | 1.7.0 / 1.3.0 / 3.7.0 | Apache-2.0 | Instrumentation tests |
| JUnit 4 | 4.13.2 | EPL-1.0 | JVM tests; not shipped |
| Ajv | 8.20.0 | MIT | Protocol JSON Schema conformance tests |
| ajv-formats | 3.0.1 | MIT | Protocol format validation |
| actions/checkout | 4.2.2 (`11bd71901bbe5b1630ceea73d27597364c9af683`) | MIT | GitHub Actions checkout |
| REUSE Tool | 6.2.0 | GPL-3.0-or-later | Local/CI license validation; not distributed |
| Kotlinx Serialization JSON | 1.11.0 | Apache-2.0 | HXP manifest parsing |
| Bouncy Castle Provider | 1.85.2 | MIT | Ed25519 package-signature verification on API 29+ |
| java-json-canonicalization | 1.1 | Apache-2.0 | RFC 8785 HXP signing canonicalization |
| Apache Commons Compress | 1.28.0 | Apache-2.0 | Bounded HXP ZIP archive inspection |
| TypeScript | 7.0.2 | Apache-2.0 | Wenku8 fixture extension build tooling; not shipped |
| [QuickJS-ng](https://github.com/quickjs-ng/quickjs) | v0.16.1 | MIT | Vendored native JavaScript runtime; source archive SHA-256 `4b3c11f37dab2c58bdeccbaeb23b923fa4a9798a45e50be6af55f3e75b616ea0` |

## Research and migration references

The projects below informed migration requirements, architecture, or product design. Except for the explicitly identified MIT-licensed Wenku8 Home taxonomy adaptation, Tsuyomi does not distribute their source files, assets, fonts, logos, binaries, or modified versions.

| Project | Upstream copyright / license | Research boundary | Material distributed by Tsuyomi |
|---|---|---|---|
| [15dd/hikari_novel_flutter](https://github.com/15dd/hikari_novel_flutter) | Copyright © 2026 15dd; MIT | Fixed reference `9e126bade357573ca5973087aafa9353de20fdce`: Wenku8 category/ranking routes and filter taxonomy | Category Tag labels plus `tags.php` / `toplist.php` filter mapping adapted into the signed TypeScript Home DTO producer; Compose layout remains an independent host implementation |
| [Xfire233/hikari_novel_flutter_plus](https://github.com/Xfire233/hikari_novel_flutter_plus) | Retains the Hikari MIT notice; fork contributors retain copyright in their contributions | Fixed migration reference `a1feba6d1dd8dbbdd2b5ae042e44f2ec54d26bef` | None; behavior/specification and sanitized-fixture reference only |
| [EnableAria/Esjzone](https://github.com/EnableAria/Esjzone) | Copyright © 2025 EnableAria; MIT | ESJZone compatibility research | None |
| [prprbell/YamiboReaderPro](https://github.com/prprbell/YamiboReaderPro) | © prprbell; AGPL-3.0 | Public Yamibo/forum behavior research | None; no AGPL code is copied, translated, linked, or adapted |
| [belleangelina/300X](https://github.com/belleangelina/300X) | Copyright © 2026 belleangelina; GPL-3.0-only | Public Yamibo login, catalogue, reader/cache/offline interaction research | None; no GPL code is copied, translated, linked, or adapted |
| [Tachiyomi](https://github.com/tachiyomiorg) | Copyright © 2015 Javier Tomás and contributors; historical core was Apache-2.0 | Android reader UX, source extensions, library/category organization and accessible product design inspiration | None; acknowledgement only |
| [mihonapp/mihon](https://github.com/mihonapp/mihon) | Copyright © 2015 Javier Tomás; Copyright © 2024 Mihon Open Source Project; Apache-2.0 | Idle-gated chapter replacement, bounded preload and library relations | None |
| [radiumCN/inkwell](https://github.com/radiumCN/inkwell) | Copyright © 2026 radiumCN; MIT | Same-object text measurement/drawing and semantic progress | None |
| [dmzz-yyhyy/LightNovelReader](https://github.com/dmzz-yyhyy/LightNovelReader) | Copyright © 2024 NightFish; Copyright © 2024 yukonisen; Apache-2.0 | Source-neutral structured chapter API boundary | None |

MIT notices must accompany any future copy or substantial portion. Apache-2.0 adoption must retain applicable copyright, patent, trademark, attribution and NOTICE obligations and identify modified files. Direct copying, translation, linking, modification, or derivative use of GPL/AGPL code is prohibited by the current Apache-2.0 project policy unless a prior licensing decision satisfies the applicable copyleft license for the affected work as a whole.

No upstream site font, image, logo, layout, component implementation, or other site asset is copied or adapted into Tsuyomi. The only vendored upstream source is the MIT-licensed QuickJS-ng runtime identified above; its retained license and provenance are in `tsuyomi-android/source/quickjs-runtime/src/main/cpp/quickjs-ng/`.
