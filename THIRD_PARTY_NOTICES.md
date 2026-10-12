<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Third-party notices

This file records direct runtime/build/test dependencies and the public projects used for migration or architecture research. Transitive Android artifacts are fixed by Gradle lockfiles and `tsuyomi-android/gradle/verification-metadata.xml`; protocol development dependencies are fixed by `tsuyomi-protocol/package-lock.json`.

## Direct dependencies and build tools

| Project | Pinned version | License | Scope |
|---|---:|---|---|
| Gradle Wrapper | 9.3.1 | Apache-2.0 | Checked-in Android build wrapper |
| Android Gradle Plugin | 9.1.1 | Apache-2.0 | Android build tooling |
| Kotlin compiler and Compose plugin | 2.4.0 | Apache-2.0 | Build tooling/runtime metadata |
| Kotlin Symbol Processing | 2.3.11 | Apache-2.0 | Room code generation |
| AndroidX Compose BOM / UI / Foundation | 2026.08.00 / 1.12.0 | Apache-2.0 | Android runtime and matching screenshot classpaths |
| AndroidX Compose Material3 | 1.5.0-alpha27 | Apache-2.0 | Android runtime; selective graduated APIs only |
| AndroidX Core / Activity / Lifecycle / Navigation | 1.19.0 / 1.13.0 / 2.10.0 / 2.9.8 | Apache-2.0 | Android runtime |
| AndroidX DataStore / Room | 1.2.1 / 2.8.4 | Apache-2.0 | Android runtime and tests |
| AndroidX WorkManager / Concurrent Futures | 2.11.2 / 1.2.0 | Apache-2.0 | Persistent update scheduling and runtime compatibility |
| Material3 Adaptive | 1.3.0 | Apache-2.0 | Android runtime |
| Kotlinx Coroutines | 1.10.2 | Apache-2.0 | Android runtime and tests |
| Compose Preview Screenshot Testing | 0.0.1-alpha15 | Apache-2.0 | AGP9-compatible screenshot test tooling |
| AndroidX Test runner/JUnit extension/Espresso | 1.7.0 / 1.3.0 / 3.7.0 | Apache-2.0 | Instrumentation tests |
| JUnit 4 | 4.13.2 | EPL-1.0 | JVM tests; not shipped |
| Ajv | 8.20.0 | MIT | Protocol JSON Schema conformance tests |
| ajv-formats | 3.0.1 | MIT | Protocol format validation |
| actions/checkout | 4.2.2 (`11bd71901bbe5b1630ceea73d27597364c9af683`) | MIT | GitHub Actions checkout |
| REUSE Tool | 6.2.0 | GPL-3.0-or-later | Local/CI license validation; not distributed |
| Kotlinx Serialization JSON | 1.11.0 | Apache-2.0 | HXP manifest parsing |
| Bouncy Castle Provider | 1.85.2 | MIT | Ed25519 package-signature verification on API 29+; existing pinned library also signs deterministic test-only repository envelopes in Android instrumentation |
| java-json-canonicalization | 1.1 | Apache-2.0 | RFC 8785 HXP/catalog signing canonicalization, including deterministic Android instrumentation envelopes |
| Apache Commons Compress | 1.28.0 | Apache-2.0 | Bounded HXP ZIP archive inspection |
| TypeScript | 7.0.2 | Apache-2.0 | Wenku8 fixture extension build tooling; not shipped |
| [QuickJS-ng](https://github.com/quickjs-ng/quickjs) | v0.16.1 | MIT | Vendored native JavaScript runtime; source archive SHA-256 `4b3c11f37dab2c58bdeccbaeb23b923fa4a9798a45e50be6af55f3e75b616ea0` |

## Optional assistant Skills

The six project Skills below are copied/adapted from [mattpocock/skills](https://github.com/mattpocock/skills/tree/b0618bc436ad893b3c5e84e55fba86586d34a404), pinned at `b0618bc436ad893b3c5e84e55fba86586d34a404` (MIT; Copyright (c) 2026 Matt Pocock). They are documentation-only assistant procedures, not Android/protocol runtime dependencies. Tsuyomi adaptations remain MIT and retain upstream attribution.

| Project directory under `.agents/skills/` | Upstream path under `skills/` | Local adaptation |
|---|---|---|
| `writing-for-agents` | `productivity/writing-for-agents/{SKILL.md,SKILL-MECHANICS.md}` | Retains the writing reference; adds project authority, bounded completion and client-discovery caveats |
| `grill-with-docs` | `engineering/grill-with-docs/SKILL.md`, `productivity/grilling/SKILL.md` | Inlines the interview primitive; automatically handles only unresolved material choices, without a second `grilling` installation |
| `domain-modeling` | `engineering/domain-modeling/SKILL.md` | Uses existing Tsuyomi contracts rather than generating a parallel root glossary/ADR tree |
| `diagnosing-bugs` | `engineering/diagnosing-bugs/SKILL.md` | Bounds diagnosis and verification to authorized evidence; preserves reported failures and existing Android scope policy |
| `code-review` | `engineering/code-review/SKILL.md` | Separates Standards/Spec and covers dirty worktrees; preserves review-only, delegation and product-authority boundaries |
| `retro` | `engineering/retro/SKILL.md` | Version-end evidence-based retrospective with deduplicated actions, coverage limits and no automatic process/configuration mutation |

Updates are reviewed against the pinned source and merged deliberately into these canonical project directories; bulk upstream replacement would erase project constraints. No upstream scripts, installer hooks, credentials or executable dependencies are included.

### Matt Pocock Skills — retained MIT notice

```text
MIT License

Copyright (c) 2026 Matt Pocock

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

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

For the 0.5.0 local-reader plan, the maintainer selected permissive-first candidates with a compliant LGPL fallback, as recorded in [ADR 0001](tsuyomi-android/docs/adr/0001-independent-brand-and-license.md). Candidate research is not runtime adoption: no parser/renderer dependency or copied source is added by that selection. Exact provenance, inherited/transitive grants, library-modification/source/relink obligations and Android behavior must be verified before adoption. GPL/AGPL engine reuse, commercial procurement and external-reader substitution remain unselected.

No upstream site font, image, logo, layout, component implementation, or other site asset is copied or adapted into Tsuyomi. Vendored runtime source includes the MIT-licensed QuickJS-ng runtime identified above; its retained license and provenance are in `tsuyomi-android/source/quickjs-runtime/src/main/cpp/quickjs-ng/`. Optional assistant documentation adaptations are listed separately above; Android parser adoption has its own component notices.
