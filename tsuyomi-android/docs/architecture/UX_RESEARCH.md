<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Phase 4 UX research evidence

## Method and limits

This note compares observable behavior only. It does not authorize copying source code, visual identity, brand, credentials, private data or GPL/AGPL implementation. Tsuyomi keeps its own Kotlin/Compose design system and local-first/privacy boundaries.

The fixed migration reference is `Xfire233/hikari_novel_flutter_plus` commit `a1feba6d1dd8dbbdd2b5ae042e44f2ec54d26bef`. Mature-reader research used current Mihon `0.20.4`, Kotatsu current source, and LNReader `2.1.2`. The official `gedoor/legado` repository has removed its product source; the listed Legado fork is historical derivative evidence only and is not a current official reference.

## Evidence matrix

| Area | Observed pattern | Phase 4 decision |
|---|---|---|
| Detail and Reader | Hikari detail combines source/local metadata and offers a continuation path; LNReader's stable novel screen owns category actions and chapter/Reader entry; Kotatsu Reader exposes detail as parent. | One host-composed stable-identity Tsuyomi detail owns local status, source enhancement, chapter entry and Continue/Start. Reader returns to that detail. |
| Collections | Mihon categories expose counts/selection; LNReader retains the last category and separates category management; Hikari supports folders but hides important moves in selection/menus. | Preserve selected collection and counts; expose membership from both book and manual-collection surfaces; use explicit 48dp reorder controls rather than Hikari-style opaque class IDs or drag-only interaction. |
| Selection and Back | Mihon/LNReader/Kotatsu use explicit selection; LNReader Back clears selection before leaving. Hikari consumes overlays, detail selection and nested routes one logical level at a time. | Back precedence is modal/edit/selection/search/drawer → route → explicit caller context. Selection count and cancel are always visible. |
| Feedback | Hikari uses shared loading/error/empty pages but several writes have only SnackBar/no success result. Kotatsu/LNReader pair actions with retry/error/confirmation. | Persistent inline/dialog success/error state plus TalkBack live region; SnackBar may supplement but never be sole proof. |
| E-ink/adaptive | Hikari switches long lists to explicit E-ink page mode; Mihon groups E-ink reader controls; Kotatsu/LNReader adapt phone/tablet layouts. | Shared Standard scroll/E-ink pagination behavior, fixed E-ink chrome and window-size—not orientation-only—adaptation. |
| Offline/source state | Hikari falls back to locally cached detail; Kotatsu/LNReader distinguish offline/downloaded content. | Room/progress/local content remain usable for dormant source; clearly label freshness/source availability and do not fabricate online state. |
| Settings and transfer | Hikari groups many settings in a long page and exposes backup choices, but lacks Tsuyomi-style safe preflight/report constraints. Mature readers group reader controls. | Group More into Display/Reader/Data; retain Tsuyomi bounded preflight, redaction and recovery, with separate import formats and persisted report route. |
| Network-heavy operations | Mihon cautions that bulk source work can trigger anti-bot controls. | Phase 4 keeps remote work explicit, bounded and per-user action; it does not add background/batch source mutations. |

## Direct evidence

### Hikari Flutter reference

- `lib/pages/main/view.dart`: persistent three-root shell, nested content navigator and ordered system-Back handling.
- `lib/router/app_sub_router.dart`: centralized nested destination transitions.
- `lib/pages/bookshelf/view.dart` and `controller.dart`: folder/selection/move flows; retain explicit selection concept, reject hidden membership semantics.
- `lib/pages/novel_detail/view.dart` and `controller.dart`: detail-owned source/local state, chapter entry and continuation; local fallback when source detail fails.
- `lib/pages/reader/view.dart`: Reader loading/error/continuation and E-ink adaptation.
- `lib/pages/setting/view.dart` and `lib/service/backup_service.dart`: grouped settings and versioned backup concepts; reject unreviewed credential defaults and generic error results.

### Mature projects

- Mihon `0.20.4` library categories/selection/Continue: <https://raw.githubusercontent.com/mihonapp/mihon/main/app/src/main/java/eu/kanade/presentation/library/components/LibraryContent.kt>
- Mihon grouped reader/E-ink settings: <https://raw.githubusercontent.com/mihonapp/mihon/main/app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsReaderScreen.kt>
- Mihon source-request guidance: <https://mihon.app/docs/faq/library>
- Kotatsu categories/offline/history claims: <https://raw.githubusercontent.com/KotatsuApp/Kotatsu/master/README.md>
- Kotatsu Reader parent/retry behavior: <https://raw.githubusercontent.com/KotatsuApp/Kotatsu/master/app/src/main/kotlin/org/koitharu/kotatsu/reader/ui/ReaderActivity.kt>
- Kotatsu explicit history action confirmation: <https://raw.githubusercontent.com/KotatsuApp/Kotatsu/master/app/src/main/kotlin/org/koitharu/kotatsu/history/ui/HistoryListFragment.kt>
- LNReader library selection/back/category state: <https://raw.githubusercontent.com/LNReader/lnreader/master/src/screens/library/LibraryScreen.tsx>
- LNReader canonical detail/actions: <https://raw.githubusercontent.com/LNReader/lnreader/master/src/screens/novel/NovelScreen.tsx>
- LNReader Reader transient Back handling: <https://raw.githubusercontent.com/LNReader/lnreader/master/src/screens/reader/ReaderScreen.tsx>
- LNReader history grouping/removal: <https://raw.githubusercontent.com/LNReader/lnreader/master/src/screens/history/HistoryScreen.tsx>
- LNReader category manager: <https://raw.githubusercontent.com/LNReader/lnreader/master/src/screens/Categories/CategoriesScreen.tsx>
- Official Legado availability limitation: <https://raw.githubusercontent.com/gedoor/legado/main/README.md>
- Historical fork only, labeled substitute: <https://raw.githubusercontent.com/HapeLee/legado-with-MD3/main/app/src/main/assets/web/help/md/appHelp.md>

## Tsuyomi-specific findings driving Phase 4

- `MainActivity.Routes.LocalBook`, `Routes.Detail`, and `LocalBookDetailsScreen.onOpenSource` split the same `BookIdentity` and can restore unrelated Browse state.
- `RoomLibraryRepository.addManualMembership` / `removeManualMembership` exist, but `LocalBookDetailsScreen` and `CollectionManagerScreen` expose no membership UI.
- Tag/rating/add/remove callbacks lack shared working/success/error state; current plain text is not a live result.
- Library omits Recent from visible filters, lacks required sorts, and has incomplete E-ink page reset behavior.
- Source lists/remote lists lack uniform E-ink pagination, durable state feedback and stable identity routing.
- Transfer lacks its promised distinct Tsuyomi/Hikari/report paths; More lacks a Reader settings route.

These are product requirements and review targets, not permission to weaken signed-source, credential, cookie, semantic-progress, transfer-redaction or E-ink constraints.
