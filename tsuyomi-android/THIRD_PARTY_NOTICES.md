<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Third-party notices

Direct dependencies and checked-in build tools in the Phase 1 baseline are recorded below. Transitive artifacts are fixed by Gradle lock state and `gradle/verification-metadata.xml`; their license metadata remains available in the upstream artifacts.

| Project | Pinned version | License | Scope / distribution |
|---|---:|---|---|
| Gradle Wrapper | 8.14.3 | Apache-2.0 | `gradle-wrapper.jar` is checked in; build tooling only |
| Android Gradle Plugin | 8.13.1 | Apache-2.0 | build tooling only |
| Kotlin compiler and Compose plugin | 2.3.0 | Apache-2.0 | build tooling/runtime metadata |
| Kotlin Symbol Processing | 2.3.11 | Apache-2.0 | Room code generation only |
| AndroidX Compose BOM and UI/Foundation/Material3 | 2026.06.01 | Apache-2.0 | application runtime |
| AndroidX Core | 1.19.0 | Apache-2.0 | application runtime |
| AndroidX Activity | 1.13.0 | Apache-2.0 | application runtime |
| AndroidX Lifecycle | 2.10.0 | Apache-2.0 | application runtime |
| AndroidX Navigation | 2.9.8 | Apache-2.0 | application runtime |
| AndroidX DataStore | 1.2.1 | Apache-2.0 | application runtime |
| AndroidX Room | 2.8.4 | Apache-2.0 | application runtime and test tooling |
| Material3 Adaptive | 1.2.0 | Apache-2.0 | application runtime |
| actions/checkout | 4.2.2 (`11bd71901bbe5b1630ceea73d27597364c9af683`) | MIT | GitHub Actions source checkout only |
| REUSE Tool | 6.2.0 | GPL-3.0-or-later | local/CI license validation only; not distributed |
| Kotlinx Coroutines | 1.10.2 | Apache-2.0 | application runtime and tests |
| Compose Preview Screenshot Testing | 0.0.1-alpha11 | Apache-2.0 | screenshot test tooling only |
| AndroidX Test runner/JUnit extension/Espresso | 1.7.0 / 1.3.0 / 3.7.0 | Apache-2.0 | instrumentation tests only |
| JUnit 4 | 4.13.2 | EPL-1.0 | JVM tests only; not shipped in the APK |

## Research and migration references

The projects below informed migration requirements or architecture research. Except for the explicitly identified MIT-licensed Wenku8 Home taxonomy adaptation, the Phase 1 baseline does not distribute their source files, assets, fonts, logos, binaries, or modified versions. Listing them here preserves provenance; it does not claim affiliation or change their licenses.

| Project | Upstream copyright / license | Research boundary | Material distributed by Tsuyomi |
|---|---|---|---|
| [15dd/hikari_novel_flutter](https://github.com/15dd/hikari_novel_flutter) | Copyright © 2026 15dd; [MIT](https://github.com/15dd/hikari_novel_flutter/blob/main/LICENSE) | Fixed reference `9e126bade357573ca5973087aafa9353de20fdce`: Wenku8 category/ranking routes and filter taxonomy | Category Tag labels plus `tags.php` / `toplist.php` filter mapping adapted into the signed TypeScript Home DTO producer; Compose layout remains an independent host implementation |
| [Xfire233/hikari_novel_flutter_plus](https://github.com/Xfire233/hikari_novel_flutter_plus) | Retains the Hikari MIT notice; fork contributors retain copyright in their contributions; [MIT](https://github.com/Xfire233/hikari_novel_flutter_plus/blob/main/LICENSE) | Fixed migration reference `a1feba6d1dd8dbbdd2b5ae042e44f2ec54d26bef`: source behavior, semantic reading position, global E-ink migration, smart shelves, ESJZone/Yamibo compatibility | None; behavior/specification and sanitized-fixture reference only |
| [EnableAria/Esjzone](https://github.com/EnableAria/Esjzone) | Copyright © 2025 EnableAria; [MIT](https://github.com/EnableAria/Esjzone/blob/master/LICENSE) | Indirect Hikari Plus upstream for ESJZone compatibility research | None |
| [prprbell/YamiboReaderPro](https://github.com/prprbell/YamiboReaderPro) | © prprbell; [AGPL-3.0](https://github.com/prprbell/YamiboReaderPro/blob/master/LICENSE) | Indirect Hikari Plus upstream for public Yamibo/forum behavior research | None; no AGPL code is copied, translated, linked, or adapted |
| [belleangelina/300X](https://github.com/belleangelina/300X) | Copyright © 2026 belleangelina; [GPL-3.0-only](https://github.com/belleangelina/300X/blob/main/LICENSE) | Public Yamibo login, catalogue, content organization, reader/cache/offline interaction and engineering trade-off research | None; no GPL code is copied, translated, linked, or adapted |
| [Tachiyomi](https://github.com/tachiyomiorg) | Copyright © 2015 Javier Tomás and contributors; historical core was Apache-2.0; the original core repository was retired by its maintainers | Inspiration for Android reader UX, source-extension ecosystems, library/category organization and accessible product design | None; acknowledgement and product inspiration only |
| [mihonapp/mihon](https://github.com/mihonapp/mihon) | Copyright © 2015 Javier Tomás; Copyright © 2024 Mihon Open Source Project; [Apache-2.0](https://github.com/mihonapp/mihon/blob/main/LICENSE) | Idle-gated chapter replacement, bounded adjacent preload, category/library relation research | None |
| [radiumCN/inkwell](https://github.com/radiumCN/inkwell) | Copyright © 2026 radiumCN; [MIT](https://github.com/radiumCN/inkwell/blob/main/LICENSE) | Same-object text measurement/drawing and semantic text-position progress research | None |
| [dmzz-yyhyy/LightNovelReader](https://github.com/dmzz-yyhyy/LightNovelReader) | Copyright © 2024 NightFish; Copyright © 2024 yukonisen; [Apache-2.0](https://github.com/dmzz-yyhyy/LightNovelReader/blob/refactoring/LICENSE) | Source-neutral structured chapter content/API boundary research | None |

The MIT notices above must accompany any future copy or substantial portion. Apache-2.0 source adoption must retain copyright, patent, trademark, attribution and upstream NOTICE obligations and must identify modified files. `YamiboReaderPro` is AGPL-3.0 and `300X` is GPL-3.0-only: direct copying, translation, linking, modification, or derivative use is prohibited by the current Apache-2.0 project policy unless a prior licensing decision satisfies the applicable copyleft license for the affected work as a whole.

## Asset and source-copy status

No upstream source file, font, image, site logo, layout, component implementation, or other asset is copied or adapted into the Phase 1 application. Dependency upgrades or future source adoption must update this file, the applicable upstream notices/licenses, version catalog or protocol lock state, verification metadata, REUSE metadata, and validation evidence together.
