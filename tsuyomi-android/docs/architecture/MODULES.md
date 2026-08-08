<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Module boundaries

## Direction

```text
app → feature/* → reader/*, source/*, core/*, shared/*
reader/* → core/*, shared/*
source/* → core/*, shared/*
core/* → shared/*
shared/* → Kotlin/JVM and protocol data only
```

No feature depends on another feature. `shared/*` has no Android UI, Room, WebView, QuickJS, or network implementation dependency. `core/*` provides infrastructure behind narrow interfaces. The Android app composes implementations only at the outer edge.

## Planned modules

| Group | Modules | Responsibility |
|---|---|---|
| App | `app` | Navigation, dependency composition, Android manifest. |
| Shared | `model`, `locator`, `backup`, `smart-shelf`, `source-contract` | Pure models and deterministic rules. |
| Core | `ui`, `display`, `database`, `preferences`, `network`, `files`, `security`, `webview` | Android infrastructure, global display/E-ink policy, and design primitives. |
| Source | `quickjs-runtime`, `extension-manager`, `extension-testkit` | `.hxp` lifecycle and constrained runtime. |
| Reader | `engine`, `ui`, `tts` | Structured document sessions, semantic locators, incremental layout, reader surfaces, and text-to-speech. |
| Features | `library`, `browse`, `search`, `book`, `reader`, `settings`, `backup`, `extensions` | User-facing screens and use-case coordination. |

`core/display` owns effective profile resolution, local device classification, root redraw requests, and the app-root `DisplayEnvironment`. A future logical refresh policy may be added here only with a real coordinator and exhaustive consumer tests. Feature, reader, and source modules cannot inspect device models or call panel/vendor APIs directly.

`reader/engine` owns source-neutral document traversal, locator restore, session revisions, layout keys, cancellation, and bounded page-plan policy. `reader/ui` implements Android text measurement/drawing and Compose surfaces. Neither feature code nor an extension owns rendered page indices or reader persistence.

The Gradle scaffold will encode these edges with convention plugins and dependency verification; this document is the source architecture constraint until then.
