/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import org.tsuyomi.prototype.uiatlas.AtlasStrings
import org.tsuyomi.prototype.uiatlas.components.AtlasButton
import org.tsuyomi.prototype.uiatlas.components.AtlasButtonStyle
import org.tsuyomi.prototype.uiatlas.components.AtlasIcons
import org.tsuyomi.prototype.uiatlas.components.AtlasMutationBanner
import org.tsuyomi.prototype.uiatlas.components.AtlasOverflowItem
import org.tsuyomi.prototype.uiatlas.components.AtlasScaffold
import org.tsuyomi.prototype.uiatlas.components.AtlasTopBar
import org.tsuyomi.prototype.uiatlas.components.AtlasTopBarAction
import org.tsuyomi.prototype.uiatlas.components.LibraryBookInteractionCapabilities
import org.tsuyomi.prototype.uiatlas.components.currentLayoutIcon
import org.tsuyomi.prototype.uiatlas.components.layoutToggleContentDescription
import org.tsuyomi.prototype.uiatlas.components.libraryDragOverlayHost
import org.tsuyomi.prototype.uiatlas.components.nextAtlasLayout
import org.tsuyomi.prototype.uiatlas.components.summary
import org.tsuyomi.prototype.uiatlas.model.AtlasContext
import org.tsuyomi.prototype.uiatlas.model.AtlasLayout
import org.tsuyomi.prototype.uiatlas.model.AtlasLibraryView
import org.tsuyomi.prototype.uiatlas.model.AtlasRoute
import org.tsuyomi.prototype.uiatlas.model.LocalAtlasNavigation
import org.tsuyomi.prototype.uiatlas.runtime.LocalPrototypeRuntime
import org.tsuyomi.prototype.uiatlas.runtime.prototypeRepository
import org.tsuyomi.prototype.uiatlas.theme.LocalAtlasEnvironment


@Composable
internal fun LibraryRoot(context: AtlasContext, modifier: Modifier) {
    val navigation = LocalAtlasNavigation.current
    val runtime = LocalPrototypeRuntime.current
    val repository = prototypeRepository()
    val coroutineScope = rememberCoroutineScope()
    val state = rememberLibraryRootState(context, runtime, repository, coroutineScope)
    val eInk = LocalAtlasEnvironment.current.eInk
    val books = state.books
    val selectionBar = state.selectionBar()

    BackHandler(state.selectionKind != null) { state.clearSelection() }
    val page = pageSlice(
        "library-${state.view.name}-${state.layout.name}-${state.sortMode.name}-${state.sortDirection.name}",
        books,
        if (state.layout == AtlasLayout.GRID) 8 else 11,
    )
    val shownBooks = if (eInk) page.items else books
    val openShortcut: (Rc21ShortcutItem) -> Unit = { item ->
        item.view?.let { selectedView ->
            navigation.selectLibraryView(selectedView)
            repository.putString("library.view", selectedView.name, "LibraryViewChanged", item.id)
        }
        when (item.kind) {
            ShortcutKind.COLLECTION -> repository.putString(
                "library.collection.level.0.id",
                item.id,
                "CollectionPageSelected",
                item.id,
            )
            ShortcutKind.MIRROR -> {
                val bindingId = if (item.id == "pine-mirror") "mirror-pine" else item.id
                repository.putString("library.mirror.binding", bindingId, "MirrorPageSelected", bindingId)
            }
            else -> Unit
        }
        repository.record("ShortcutOpened", item.id, "success")
        if (item.route != AtlasRoute.LIBRARY) navigation.navigate(item.route)
    }
    val shortcutShelf: @Composable () -> Unit = {
        ShortcutShelf(
            items = state.shortcuts,
            expanded = state.shortcutExpanded,
            locked = state.shortcutLocked,
            editing = state.shortcutEditing,
            onExpanded = {
                state.shortcutExpanded = it
                repository.putBoolean("library.shortcuts.expanded", it, "ShortcutExpanded")
            },
            onLocked = {
                if (it) state.dragCoordinator.cancel()
                state.shortcutLocked = it
                repository.putBoolean("library.shortcuts.locked", it, "ShortcutLocked")
            },
            onEditing = { state.shortcutEditing = it },
            onOpen = openShortcut,
            onReturnToAll = {},
            onMove = { from, to ->
                if (to in state.shortcuts.indices) {
                    state.setShortcuts(
                        state.shortcuts.toMutableList().also { it.add(to, it.removeAt(from)) },
                        "ShortcutMoved",
                    )
                }
            },
            onRemove = { id ->
                state.shortcuts.firstOrNull { it.id == id }?.let { item ->
                    when {
                        item.book != null -> state.removeShortcutItems(listOf(item))
                        item.kind == ShortcutKind.COLLECTION -> {
                            state.pendingRemoval = LibraryRemovalRequest.Collection(item.id, item.label)
                        }
                        else -> state.pendingRemoval = LibraryRemovalRequest.Shortcut(item.id, item.label)
                    }
                }
            },
            onViewAll = { state.shortcutPageOpen = true },
            activeView = AtlasLibraryView.ALL,
            onCreate = {
                state.pendingCollectionCreation = PendingCollectionCreation()
                state.collectionName = ""
            },
            selectionKind = state.selectionKind,
            selectedBookIds = state.selectedBookIds,
            selectedCollectionIds = state.selectedCollectionIds,
            onToggleBook = state::toggleBook,
            onToggleCollection = state::toggleCollection,
            conflictSignal = state.selectionConflictSignal,
            conflictTargetKey = state.selectionConflictTarget,
            dragCoordinator = state.dragCoordinator,
        )
    }
    val activeDragItem = state.dragCoordinator.activeBookIds.firstOrNull()?.let { id ->
        state.shortcutBookCatalog.firstOrNull { it.id == id }?.let(::bookShortcut)
    } ?: state.dragCoordinator.activePayload?.removePrefix(SHORTCUT_DRAG_PREFIX)?.let { id ->
        state.shortcuts.firstOrNull { it.id == id }
    }

    Box(modifier.fillMaxSize().libraryDragOverlayHost(state.dragCoordinator)) {
        AtlasScaffold(
            modifier = Modifier
                .fillMaxSize()
                .then(if (state.shortcutPageOpen) Modifier.clearAndSetSemantics { } else Modifier),
            topBar = {
                Column {
                    AtlasTopBar(
                        title = if (state.standaloneNodePage) state.view.label else AtlasStrings.ROOT_LIBRARY,
                        subtitle = "${books.size} 本",
                        onUp = if (state.standaloneNodePage) navigation.up else null,
                        selection = selectionBar,
                        actionBudgetOverride = 3,
                        actions = listOf(
                            AtlasTopBarAction(AtlasIcons.Refresh, "同步并检查更新", state::runLibrarySyncCheck),
                            AtlasTopBarAction(AtlasIcons.Search, "搜索") { navigation.navigateSearch(null) },
                            AtlasTopBarAction(
                                state.layout.currentLayoutIcon(),
                                state.layout.layoutToggleContentDescription(),
                            ) {
                                state.layout = state.layout.nextAtlasLayout()
                                repository.putString("library.layout", state.layout.name, "LibraryLayoutChanged")
                            },
                        ),
                        overflow = listOf(
                            AtlasOverflowItem("排序：${state.sortMode.summary(state.sortDirection)}") {
                                state.sortOpen = true
                            },
                            AtlasOverflowItem("标签") { navigation.navigate(AtlasRoute.LIBRARY_TAGS) },
                        ),
                    )
                    state.syncStatus?.let { AtlasMutationBanner(it) }
                }
            },
            footer = if (eInk && context.state.showsContent && books.isNotEmpty()) {
                { PaginationFooter(page.page, page.pages, page.setPage) }
            } else {
                null
            },
        ) {
            StateOrContent(
                context.state,
                state.fixture.emptyTitle,
                state.fixture.emptyMessage,
                "书架加载失败",
                "本地书架索引不可用；书籍数据未受影响。",
                state.fixture.emptyActionLabel,
                { repository.record("LibraryEmptyAction", state.view.name, "success") },
            ) {
                Column(Modifier.fillMaxSize()) {
                    if (!state.standaloneNodePage) shortcutShelf()
                    BookSurface(
                        context = context,
                        books = shownBooks,
                        layout = state.layout,
                        selected = if (state.selectionKind == LibrarySelectionKind.BOOK) {
                            state.selectedBookIds
                        } else {
                            emptySet()
                        },
                        selectionActive = state.selectionKind == LibrarySelectionKind.BOOK,
                        selectionConflictTarget = state.selectionConflictTarget,
                        selectionConflictSignal = state.selectionConflictSignal,
                        toggle = state::toggleBook,
                        interaction = LibraryBookInteractionCapabilities(
                            drag = !state.shortcutLocked,
                            reorder = state.libraryReorderEnabled,
                        ),
                        denseGrid = !eInk,
                        dragCoordinator = state.dragCoordinator,
                        onLongPress = { state.toggleBook(it.id) },
                        onRemoveRequest = { state.pendingRemoval = LibraryRemovalRequest.Book(it) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (state.shortcutPageOpen) {
            ShortcutAllPage(
                onReturnToAll = {},
                items = state.shortcuts,
                activeView = AtlasLibraryView.ALL,
                locked = state.shortcutLocked,
                onDismiss = { state.shortcutPageOpen = false },
                onOpen = {
                    state.shortcutPageOpen = false
                    openShortcut(it)
                },
                selectionKind = state.selectionKind,
                selection = selectionBar,
                selectedBookIds = state.selectedBookIds,
                selectedCollectionIds = state.selectedCollectionIds,
                onClearSelection = state::clearSelection,
                onToggleBook = state::toggleBook,
                onToggleCollection = state::toggleCollection,
                conflictSignal = state.selectionConflictSignal,
                conflictTargetKey = state.selectionConflictTarget,
                dragCoordinator = state.dragCoordinator,
            )
        }
        ShortcutDeleteDropTarget(
            visible = state.dragCoordinator.activePayload?.let {
                payloadBookIds(it).isNotEmpty() || it.startsWith(SHORTCUT_DRAG_PREFIX)
            } == true,
            label = if (state.dragCoordinator.activePayload?.let {
                    payloadBookIds(it).isNotEmpty() && !isShortcutBookPayload(it)
                } == true
            ) {
                "移出总书架"
            } else {
                "移出快捷书架"
            },
            active = state.dragCoordinator.isOverDelete,
            dragCoordinator = state.dragCoordinator,
            modifier = Modifier.fillMaxSize(),
        )
        ShortcutDragGhost(
            activeItem = activeDragItem,
            dragCoordinator = state.dragCoordinator,
            bookLayout = state.layout,
            modifier = Modifier.fillMaxSize(),
        )
    }

    if (state.sortOpen) {
        LibrarySortDialog(
            mode = state.sortMode,
            direction = state.sortDirection,
            onModeChange = {
                state.sortMode = it
                repository.putString("library.sort", it.name, "LibrarySortChanged")
            },
            onDirectionChange = {
                state.sortDirection = it
                repository.putString("library.sort.direction", it.name, "LibrarySortDirectionChanged")
            },
            onDismiss = { state.sortOpen = false },
        )
    }
    state.pendingCollectionCreation?.let { request ->
        FullDialog("新建收藏夹", { state.pendingCollectionCreation = null }) {
            OutlinedTextField(
                state.collectionName,
                { state.collectionName = it },
                label = { Text("收藏夹名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            DialogButtons(
                "创建",
                {
                    if (state.collectionName.isNotBlank()) {
                        state.createCollection(request, state.collectionName)
                        state.pendingCollectionCreation = null
                        state.clearSelection()
                    }
                },
                { state.pendingCollectionCreation = null },
            )
        }
    }
    state.collectionPicker?.let { request ->
        val candidates = state.shortcuts.filter {
            it.kind == ShortcutKind.COLLECTION && it.id !in state.selectedCollectionIds
        }
        FullDialog("移入收藏夹", { state.collectionPicker = null }) {
            if (candidates.isEmpty()) Text("还没有可用收藏夹。")
            candidates.forEach { collection ->
                AtlasButton(
                    collection.label,
                    {
                        when (request) {
                            is CollectionPickerRequest.Books -> {
                                state.addBooksToCollection(collection.id, request.ids, "BooksMovedIntoCollection")
                            }
                            is CollectionPickerRequest.Collections -> request.ids.forEach { id ->
                                repository.putString(
                                    "library.collection.$id.parent",
                                    collection.id,
                                    "CollectionsMovedIntoCollection",
                                    id,
                                )
                            }
                        }
                        state.clearSelection()
                        state.collectionPicker = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    style = AtlasButtonStyle.TEXT,
                )
            }
        }
    }
    state.pendingRemoval?.let { request ->
        val (title, message, confirm) = when (request) {
            is LibraryRemovalRequest.Shortcut -> Triple(
                "移出快捷书架？",
                "「${request.label}」将只从快捷书架移除；总书架、收藏关系和阅读进度不受影响。",
            ) { state.removeShortcutItems(state.shortcuts.filter { it.id == request.id }) }
            is LibraryRemovalRequest.Book -> Triple(
                "移出总书架？",
                "《${request.book.title}》将从总书架及其快捷项移出；本地文件、收藏关系和阅读进度不受影响。",
            ) { state.removeBooks(listOf(request.book)) }
            is LibraryRemovalRequest.Books -> Triple(
                "移出 ${request.books.size} 本书？",
                "所选书籍将从总书架及其快捷项移出；本地文件、收藏关系和阅读进度不受影响。",
            ) { state.removeBooks(request.books) }
            is LibraryRemovalRequest.Collection -> Triple(
                "删除收藏夹？",
                "「${request.label}」将从快捷书架移除；书籍、阅读进度和本地文件不受影响。",
            ) { state.removeCollection(request.id) }
            is LibraryRemovalRequest.Collections -> Triple(
                "删除 ${request.items.size} 个收藏夹？",
                "所选收藏夹将从快捷书架移除；书籍、阅读进度和本地文件不受影响。",
            ) { request.items.forEach { state.removeCollection(it.id) } }
        }
        FullDialog(title, { state.pendingRemoval = null }, destructive = true) {
            Text(message)
            DialogButtons(
                if (request is LibraryRemovalRequest.Book ||
                    request is LibraryRemovalRequest.Books ||
                    request is LibraryRemovalRequest.Shortcut
                ) {
                    "移出"
                } else {
                    "删除"
                },
                {
                    confirm()
                    state.clearSelection()
                    state.pendingRemoval = null
                },
                { state.pendingRemoval = null },
            )
        }
    }
}