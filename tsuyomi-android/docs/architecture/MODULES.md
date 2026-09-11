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

## Modules

| Group | Modules | Responsibility |
|---|---|---|
| App | `app` | Navigation, dependency composition, Android manifest. |
| Shared | `model`, `locator`, `backup`, `smart-shelf`, `source-contract`, `library-domain` | Pure models and deterministic rules; `library-domain` owns update state and persistence/probe ports without Android dependencies. |
| Core | `ui`, `display`, `database`, `library`, `preferences`, `network`, `files`, `security`, `webview` | Android infrastructure, global display/E-ink policy and design primitives; `library` owns update coordination through narrow ports, while `database` implements durable storage. |
| Source | `quickjs-runtime`, `extension-manager`, `extension-testkit` | `.hxp` verification, installation, persistent repository subscriptions, exact-package execution trust, safe uninstall/reinstall, constrained runtime, and deterministic host replay. |
| Reader | `engine`, `ui`, `tts` | Structured document sessions, semantic locators, incremental layout, reader surfaces, and text-to-speech. |
| Features | `library`, `browse`, `search`, `book`, `reader`, `settings`, `backup`, `extensions` | User-facing screens and use-case coordination. |

`feature/browse` currently owns the user-facing installed/available source catalog, repository management and installation lifecycle because discovery remains part of Browse. `feature/extensions` remains a target-DAG reservation, not a second extension-management screen; it may be activated only by a separately authorized route cutover. Never duplicate the shipped Browse lifecycle merely to occupy the reserved module.

`core/display` owns effective profile resolution, local device classification, root redraw requests, and the app-root `DisplayEnvironment`. A future logical refresh policy may be added here only with a real coordinator and exhaustive consumer tests. Feature, reader, and source modules cannot inspect device models or call panel/vendor APIs directly.

`reader/engine` owns source-neutral document traversal, locator restore, session revisions, layout keys, cancellation, and bounded page-plan policy. `reader/ui` implements Android text measurement/drawing and Compose surfaces. Neither feature code nor an extension owns rendered page indices or reader persistence.

Phase 4C composition remains at `app`: installed-source leases adapt the signed `update-check-v2` client to the domain probe, and WorkManager/notifications dispatch the core coordinator. Features consume immutable update snapshots and typed actions; neither the scheduler nor node visibility owns update acknowledgements. Update state is local runtime data and remains outside portable transfer. Source request lanes are shared across foreground and background gateway instances so separate clients cannot bypass per-source serialization.

The Gradle scaffold will encode these edges with convention plugins and dependency verification; this document is the source architecture constraint until then.
