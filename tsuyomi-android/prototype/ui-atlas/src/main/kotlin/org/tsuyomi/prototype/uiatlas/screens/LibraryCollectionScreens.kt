/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.tsuyomi.prototype.uiatlas.AtlasStrings
import org.tsuyomi.prototype.uiatlas.components.AtlasButton
import org.tsuyomi.prototype.uiatlas.components.AtlasButtonStyle
import org.tsuyomi.prototype.uiatlas.components.AtlasChip
import org.tsuyomi.prototype.uiatlas.components.AtlasIcons
import org.tsuyomi.prototype.uiatlas.components.AtlasMutationPhase
import org.tsuyomi.prototype.uiatlas.components.AtlasMutationStatus
import org.tsuyomi.prototype.uiatlas.components.AtlasOverflowItem
import org.tsuyomi.prototype.uiatlas.components.AtlasScaffold
import org.tsuyomi.prototype.uiatlas.components.AtlasSelectionBar
import org.tsuyomi.prototype.uiatlas.components.AtlasTopBar
import org.tsuyomi.prototype.uiatlas.components.AtlasTopBarAction
import org.tsuyomi.prototype.uiatlas.components.LibraryBookInteractionCapabilities
import org.tsuyomi.prototype.uiatlas.components.LibraryBookSortDirection
import org.tsuyomi.prototype.uiatlas.components.LibraryBookSortMode
import org.tsuyomi.prototype.uiatlas.components.LibraryDragCoordinator
import org.tsuyomi.prototype.uiatlas.components.LibraryDropDestination
import org.tsuyomi.prototype.uiatlas.components.currentLayoutIcon
import org.tsuyomi.prototype.uiatlas.components.layoutToggleContentDescription
import org.tsuyomi.prototype.uiatlas.components.libraryDragOverlayHost
import org.tsuyomi.prototype.uiatlas.components.nextAtlasLayout
import org.tsuyomi.prototype.uiatlas.components.orderedForLibrary
import org.tsuyomi.prototype.uiatlas.components.summary
import org.tsuyomi.prototype.uiatlas.fixtures.LibraryAtlasFixtures
import org.tsuyomi.prototype.uiatlas.model.AtlasBook
import org.tsuyomi.prototype.uiatlas.model.AtlasContext
import org.tsuyomi.prototype.uiatlas.model.AtlasLayout
import org.tsuyomi.prototype.uiatlas.model.AtlasLibraryView
import org.tsuyomi.prototype.uiatlas.model.AtlasPageState
import org.tsuyomi.prototype.uiatlas.model.AtlasRoute
import org.tsuyomi.prototype.uiatlas.model.LocalAtlasNavigation
import org.tsuyomi.prototype.uiatlas.runtime.prototypeRepository
import org.tsuyomi.prototype.uiatlas.theme.AtlasSpacing
import org.tsuyomi.prototype.uiatlas.theme.LocalAtlasEnvironment

@Composable
internal fun CollectionDetail(context: AtlasContext, modifier: Modifier) {
    val repository = prototypeRepository()
    val depth = when (context.route) {
        AtlasRoute.LIBRARY_COLLECTION -> 0
        AtlasRoute.LIBRARY_COLLECTION_CHILD -> 1
        else -> 2
    }
    val defaultCollection = if (context.libraryView == AtlasLibraryView.COLLECTION) {
        LibraryAtlasFixtures.smartSciFi
    } else {
        LibraryAtlasFixtures.manualNightBoat
    }
    val collectionId = repository.string("library.collection.level.$depth.id", defaultCollection.id)
    val collection = LibraryAtlasFixtures.collectionTree.findCollectionFixture(collectionId) ?: defaultCollection
    if (collection.smart) SmartCollection(context, modifier, collection)
    else ManualCollection(context, modifier, collection, depth)
}

@Composable
private fun ManualCollection(
    context: AtlasContext,
    modifier: Modifier,
    collection: LibraryAtlasFixtures.CollectionFixture,
    depth: Int,
) {
    val navigation = LocalAtlasNavigation.current
    val repository = prototypeRepository()
    val eInk = LocalAtlasEnvironment.current.eInk
    val collectionId = collection.id
    val membershipKey = "library.collection.$collectionId.books"
    val fixtureBooks = LibraryAtlasFixtures.booksForCollection(collection)
    var books by remember(collectionId) {
        val storedIds = repository.stringList(membershipKey).toSet()
        val initialized = repository.boolean("$membershipKey.initialized")
        mutableStateOf(if (initialized) fixtureBooks.filter { it.id in storedIds } else fixtureBooks)
    }
    var orderedBookIds by remember(collectionId) { mutableStateOf(repository.stringList("library.collection.$collectionId.order")) }
    var sortMode by rememberSaveable(collectionId) {
        mutableStateOf(
            LibraryBookSortMode.entries.firstOrNull { it.name == repository.string("library.collection.$collectionId.sort") }
                ?: LibraryBookSortMode.CUSTOM,
        )
    }
    var sortDirection by rememberSaveable(collectionId) {
        mutableStateOf(
            LibraryBookSortDirection.entries.firstOrNull {
                it.name == repository.string("library.collection.$collectionId.sort.direction")
            } ?: LibraryBookSortDirection.ASCENDING,
        )
    }
    val shownBooksInOrder = books.orderedForLibrary(sortMode, sortDirection, orderedBookIds)
    var childItems by remember(collectionId) {
        val childRoute = collectionRouteForDepth(depth + 1)
        val fixtures = collection.children.map { child -> collectionFixtureShortcut(child, childRoute) }
        val stored = repository.stringList("library.collection.$collectionId.children.order")
        val byId = fixtures.associateBy { it.id }
        mutableStateOf(stored.mapNotNull(byId::get) + fixtures.filterNot { it.id in stored })
    }
    var layout by rememberSaveable(context.profile.name, collectionId) {
        mutableStateOf(
            AtlasLayout.entries.firstOrNull { it.name == repository.string("library.collection.$collectionId.layout") }
                ?: context.layout ?: if (eInk) AtlasLayout.GRID else AtlasLayout.LIST,
        )
    }
    var selectionKind by remember(context.state) {
        mutableStateOf<LibrarySelectionKind?>(if (context.state == AtlasPageState.SELECTION) LibrarySelectionKind.BOOK else null)
    }
    var selectedBookIds by remember(context.state) {
        mutableStateOf(if (context.state == AtlasPageState.SELECTION) books.take(3).map { it.id }.toSet() else emptySet())
    }
    var selectedCollectionIds by remember { mutableStateOf(emptySet<String>()) }
    var conflictTarget by remember { mutableStateOf<String?>(null) }
    var conflictSignal by remember { mutableIntStateOf(0) }
    var sheet by remember(context.state) { mutableStateOf(context.state == AtlasPageState.MODAL) }
    var sortOpen by rememberSaveable(collectionId) { mutableStateOf(false) }
    var pendingChildCollectionName by remember { mutableStateOf<String?>(null) }
    var pendingBookRemoval by remember { mutableStateOf(emptySet<String>()) }
    var pendingChildRemoval by remember { mutableStateOf(emptySet<String>()) }
    val dragCoordinator = remember { LibraryDragCoordinator() }
    fun clearSelection() {
        selectionKind = null
        selectedBookIds = emptySet()
        selectedCollectionIds = emptySet()
        conflictTarget = null
    }
    fun toggleBook(id: String) {
        if (selectionKind == null || selectionKind == LibrarySelectionKind.BOOK) {
            selectionKind = LibrarySelectionKind.BOOK
            selectedBookIds = if (id in selectedBookIds) selectedBookIds - id else selectedBookIds + id
        } else {
            conflictTarget = "book:$id"
            conflictSignal += 1
        }
    }
    fun toggleCollection(id: String) {
        if (selectionKind == null || selectionKind == LibrarySelectionKind.COLLECTION) {
            selectionKind = LibrarySelectionKind.COLLECTION
            selectedCollectionIds = if (id in selectedCollectionIds) selectedCollectionIds - id else selectedCollectionIds + id
        } else {
            conflictTarget = "shortcut:$id"
            conflictSignal += 1
        }
    }
    fun persistBooks(updated: List<AtlasBook>, event: String) {
        books = updated
        repository.putBoolean("$membershipKey.initialized", true, "CollectionMembershipInitialized")
        repository.putStringList(membershipKey, updated.map { it.id }, event, collectionId)
    }
    fun persistChildren(updated: List<Rc21ShortcutItem>, event: String) {
        childItems = updated
        repository.putStringList("library.collection.$collectionId.children.order", updated.map { it.id }, event, collectionId)
    }
    fun createChildCollection(name: String) {
        val created = createPersistedUserCollection(
            repository = repository,
            name = name,
            bookIds = selectedBookIds,
            parentId = collectionId,
        )
        val item = collectionShortcut(created, books.associateBy(AtlasBook::id)).copy(
            route = collectionRouteForDepth(depth + 1),
        )
        persistChildren(childItems + item, "CollectionChildCreatedFromSelection")
        clearSelection()
    }
    dragCoordinator.onLongPress = { subjectKey ->
        when {
            subjectKey.startsWith("book:") -> toggleBook(subjectKey.removePrefix("book:"))
            subjectKey.startsWith("shortcut:") -> toggleCollection(subjectKey.removePrefix("shortcut:"))
        }
    }
    dragCoordinator.onDrop = drop@ { payload, destination ->
        val bookIds = payloadBookIds(payload)
        when (destination) {
            LibraryDropDestination.Remove -> {
                if (bookIds.isNotEmpty()) pendingBookRemoval = bookIds
                else if (payload.startsWith(SHORTCUT_DRAG_PREFIX)) pendingChildRemoval = setOf(payload.removePrefix(SHORTCUT_DRAG_PREFIX))
                else return@drop false
                true
            }
            is LibraryDropDestination.Collection -> {
                if (bookIds.isEmpty()) return@drop false
                val key = "library.collection.${destination.id}.books"
                repository.putStringList(key, (repository.stringList(key).toSet() + bookIds).sorted(), "CollectionBooksDroppedIntoChild", destination.id)
                clearSelection()
                true
            }
            is LibraryDropDestination.Root -> {
                if (!payload.startsWith(SHORTCUT_DRAG_PREFIX)) return@drop false
                val id = payload.removePrefix(SHORTCUT_DRAG_PREFIX)
                val updated = childItems.toMutableList()
                val from = updated.indexOfFirst { it.id == id }
                if (from < 0) return@drop false
                val item = updated.removeAt(from)
                updated.add(destination.index.coerceIn(0, updated.size), item)
                persistChildren(updated, "CollectionChildMoved")
                true
            }
            is LibraryDropDestination.Library -> {
                if (sortMode != LibraryBookSortMode.CUSTOM || !payload.startsWith(BOOK_DRAG_PREFIX) || bookIds.isEmpty()) return@drop false
                val updated = shownBooksInOrder.toMutableList()
                val moved = updated.filter { it.id in bookIds }
                updated.removeAll(moved.toSet())
                updated.addAll(destination.index.coerceIn(0, updated.size), moved)
                orderedBookIds = updated.map { it.id }
                repository.putStringList("library.collection.$collectionId.order", orderedBookIds, "CollectionBooksReordered", collectionId)
                clearSelection()
                true
            }
            is LibraryDropDestination.Book -> false
        }
    }
    BackHandler(selectionKind != null) { clearSelection() }
    BackHandler(sheet) { sheet = false }
    val selectedCount = selectedBookIds.size + selectedCollectionIds.size
    val selectionBar = selectionKind?.let { kind ->
        val allIds = if (kind == LibrarySelectionKind.BOOK) shownBooksInOrder.map { it.id }.toSet() else childItems.map { it.id }.toSet()
        val selectedIds = if (kind == LibrarySelectionKind.BOOK) selectedBookIds else selectedCollectionIds
        val allSelected = allIds.isNotEmpty() && selectedIds.containsAll(allIds)
        AtlasSelectionBar(
            count = selectedCount,
            onClose = ::clearSelection,
            allSelected = allSelected,
            onToggleAll = {
                if (kind == LibrarySelectionKind.BOOK) selectedBookIds = if (allSelected) emptySet() else allIds
                else selectedCollectionIds = if (allSelected) emptySet() else allIds
            },
            bulkActions = if (kind == LibrarySelectionKind.BOOK) {
                buildList {
                    if (depth < 2) {
                        add(AtlasTopBarAction(AtlasIcons.FolderAdd, "用所选新建子收藏夹") {
                            pendingChildCollectionName = ""
                        })
                    }
                    add(AtlasTopBarAction(AtlasIcons.FolderMove, "加入其他收藏夹") { sheet = true })
                    add(AtlasTopBarAction(AtlasIcons.Delete, "移出此收藏夹") { pendingBookRemoval = selectedBookIds })
                }
            } else {
                listOf(
                    AtlasTopBarAction(AtlasIcons.FolderMove, "移到上级") {
                        val parentId = repository.string("library.collection.$collectionId.parent")
                        selectedCollectionIds.forEach { id ->
                            repository.putString("library.collection.$id.parent", parentId, "CollectionMovedToParent", id)
                        }
                        persistChildren(childItems.filterNot { it.id in selectedCollectionIds }, "CollectionChildrenMovedToParent")
                        clearSelection()
                    },
                    AtlasTopBarAction(AtlasIcons.Delete, "删除收藏夹") { pendingChildRemoval = selectedCollectionIds },
                )
            },
        )
    }
    val page = pageSlice(
        "manual-$collectionId-${layout.name}-${sortMode.name}-${sortDirection.name}",
        shownBooksInOrder,
        if (layout == AtlasLayout.GRID) 9 else if (layout == AtlasLayout.COMPACT) 8 else 6,
    )
    val shown = if (eInk) page.items else shownBooksInOrder
    val mutation = if (context.state == AtlasPageState.MUTATION) {
        AtlasMutationStatus(AtlasMutationPhase.SUCCESS, "已更新《纸灯巷的守夜人》的收藏夹归属（2 个）")
    } else null
    val activeDragItem = dragCoordinator.activeBookIds.firstOrNull()?.let { id -> books.firstOrNull { it.id == id }?.let(::bookShortcut) }
        ?: dragCoordinator.activePayload?.removePrefix(SHORTCUT_DRAG_PREFIX)?.let { id -> childItems.firstOrNull { it.id == id } }
    Box(modifier.fillMaxSize().libraryDragOverlayHost(dragCoordinator)) {
        AtlasScaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                Column {
                    AtlasTopBar(
                        title = collection.name,
                        subtitle = if (childItems.isEmpty()) "${books.size} 本" else "${books.size} 本 · ${childItems.size} 个收藏夹",
                        onUp = navigation.up,
                        selection = selectionBar,
                        actionBudgetOverride = 3,
                        actions = listOf(
                            AtlasTopBarAction(
                                layout.currentLayoutIcon(),
                                layout.layoutToggleContentDescription(),
                            ) {
                                layout = layout.nextAtlasLayout()
                                repository.putString("library.collection.$collectionId.layout", layout.name, "CollectionLayoutChanged", collectionId)
                            },
                        ),
                        overflow = listOf(
                            AtlasOverflowItem("排序：${sortMode.summary(sortDirection)}") { sortOpen = true },
                            AtlasOverflowItem("编辑收藏夹规则") { navigation.navigate(AtlasRoute.LIBRARY_COLLECTION_RULE) },
                            AtlasOverflowItem("添加书籍") { sheet = true },
                        ),
                    )
                    OverlayState(context.state, mutation)
                }
            },
            footer = if (eInk && context.state.showsContent && books.isNotEmpty()) {
                { PaginationFooter(page.page, page.pages, page.setPage) }
            } else null,
        ) {
            StateOrContent(
                context.state,
                "收藏夹为空",
                null,
                "收藏夹加载失败",
                "本地收藏夹索引不可用；收藏关系未受影响。",
                "添加书籍",
                { sheet = true },
            ) {
                Column(Modifier.fillMaxSize()) {
                    if (childItems.isNotEmpty()) {
                        Text("收藏夹（${childItems.size}）", modifier = Modifier.padding(horizontal = AtlasSpacing.Md, vertical = AtlasSpacing.Sm), style = MaterialTheme.typography.titleMedium)
                        ShortcutExpandedGrid(
                            items = childItems,
                            locked = eInk,
                            onOpen = { item ->
                                val childDepth = depth + 1
                                repository.putString("library.collection.level.$childDepth.id", item.id, "CollectionChildOpened", item.id)
                                navigation.navigate(item.route)
                            },
                            dragCoordinator = dragCoordinator,
                            modifier = Modifier.fillMaxWidth().height(196.dp),
                            selectionKind = selectionKind,
                            selectedCollectionIds = selectedCollectionIds,
                            onToggleCollection = ::toggleCollection,
                            conflictSignal = conflictSignal,
                            conflictTargetKey = conflictTarget,
                            acceptBookAtRoot = false,
                        )
                    }
                    Text("书籍（${books.size}）", modifier = Modifier.padding(horizontal = AtlasSpacing.Md, vertical = AtlasSpacing.Sm), style = MaterialTheme.typography.titleMedium)
                    BookSurface(
                        context = context,
                        books = shown,
                        layout = layout,
                        selected = if (selectionKind == LibrarySelectionKind.BOOK) selectedBookIds else emptySet(),
                        toggle = ::toggleBook,
                        selectionActive = selectionKind == LibrarySelectionKind.BOOK,
                        selectionConflictTarget = conflictTarget,
                        selectionConflictSignal = conflictSignal,
                        interaction = LibraryBookInteractionCapabilities(drag = !eInk, reorder = sortMode == LibraryBookSortMode.CUSTOM),
                        dragCoordinator = dragCoordinator,
                        dragDescriptionSuffix = if (sortMode == LibraryBookSortMode.CUSTOM) "长按多选，移动可拖动排序或移出收藏夹" else "长按多选，移动可拖动移出收藏夹",
                        onLongPress = { toggleBook(it.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        ShortcutDeleteDropTarget(
            visible = dragCoordinator.activePayload != null,
            label = if (dragCoordinator.activePayload?.startsWith(SHORTCUT_DRAG_PREFIX) == true) "删除收藏夹" else "移出此收藏夹",
            active = dragCoordinator.isOverDelete,
            dragCoordinator = dragCoordinator,
        )
        ShortcutDragGhost(activeItem = activeDragItem, dragCoordinator = dragCoordinator, bookLayout = layout, modifier = Modifier.fillMaxSize())
    }
    if (sortOpen) {
        LibrarySortDialog(
            mode = sortMode,
            direction = sortDirection,
            onModeChange = {
                sortMode = it
                repository.putString("library.collection.$collectionId.sort", it.name, "CollectionSortChanged", collectionId)
            },
            onDirectionChange = {
                sortDirection = it
                repository.putString(
                    "library.collection.$collectionId.sort.direction",
                    it.name,
                    "CollectionSortDirectionChanged",
                    collectionId,
                )
            },
            onDismiss = { sortOpen = false },
        )
    }
    pendingChildCollectionName?.let { name ->
        FullDialog("新建子收藏夹", { pendingChildCollectionName = null }) {
            OutlinedTextField(
                value = name,
                onValueChange = { pendingChildCollectionName = it },
                label = { Text("收藏夹名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            DialogButtons("创建", {
                if (name.isNotBlank()) {
                    createChildCollection(name)
                    pendingChildCollectionName = null
                }
            }, { pendingChildCollectionName = null })
        }
    }
    if (pendingBookRemoval.isNotEmpty()) {
        FullDialog("从「${collection.name}」移出 ${pendingBookRemoval.size} 本书？", { pendingBookRemoval = emptySet() }, destructive = true) {
            Text("仅移除当前收藏关系；书籍仍保留在总书架。")
            DialogButtons("移出", {
                persistBooks(books.filterNot { it.id in pendingBookRemoval }, "CollectionBooksRemoved")
                pendingBookRemoval = emptySet()
                clearSelection()
            }, { pendingBookRemoval = emptySet() })
        }
    }
    if (pendingChildRemoval.isNotEmpty()) {
        FullDialog("删除 ${pendingChildRemoval.size} 个收藏夹？", { pendingChildRemoval = emptySet() }, destructive = true) {
            Text("书籍仍保留在总书架；收藏关系会被移除。")
            DialogButtons("删除", {
                persistChildren(childItems.filterNot { it.id in pendingChildRemoval }, "CollectionChildrenDeleted")
                pendingChildRemoval = emptySet()
                clearSelection()
            }, { pendingChildRemoval = emptySet() })
        }
    }
    if (sheet) MembershipSheet(context, { sheet = false }) { sheet = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MembershipSheet(context: AtlasContext, dismiss: () -> Unit, done: () -> Unit) {
    val repository = prototypeRepository()
    val full = LocalAtlasEnvironment.current.eInk || context.isVariant('C', "b")
    var members by remember {
        mutableStateOf(LibraryAtlasFixtures.membershipRows.associate { it.id to it.member })
    }
    val content: @Composable ColumnScope.() -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("管理收藏夹成员", style = MaterialTheme.typography.titleLarge)
                Text("《纸灯巷的守夜人》", style = MaterialTheme.typography.bodySmall)
            }
            if (full) AtlasButton(AtlasStrings.CLOSE, dismiss, style = AtlasButtonStyle.SECONDARY)
        }
        LibraryAtlasFixtures.membershipRows.forEach { row ->
            val checked = members[row.id] ?: row.member
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !row.smartLocked) { members = members + (row.id to !checked) }
                    .heightIn(min = 48.dp)
                    .padding(start = AtlasSpacing.Md + AtlasSpacing.Lg * row.depth, end = AtlasSpacing.Md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked, null, enabled = !row.smartLocked)
                Text(row.name, modifier = Modifier.weight(1f).padding(start = AtlasSpacing.Md))
                if (row.smartLocked) AtlasChip("规则维护")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = AtlasSpacing.Md),
            horizontalArrangement = Arrangement.End,
        ) {
            AtlasButton(AtlasStrings.CANCEL, dismiss, style = AtlasButtonStyle.TEXT)
            AtlasButton("完成", {
                repository.putStringList("collection.memberships", members.filterValues { it }.keys.toList(), "CollectionMembershipChanged")
                done()
            })
        }
    }
    if (full) {
        Dialog(
            onDismissRequest = dismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(AtlasSpacing.Md), content = content)
            }
        }
    } else {
        ModalBottomSheet(onDismissRequest = dismiss) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(AtlasSpacing.Md),
                content = content,
            )
        }
    }
}

@Composable
private fun SmartCollection(
    context: AtlasContext,
    modifier: Modifier,
    collection: LibraryAtlasFixtures.CollectionFixture,
) {
    val navigation = LocalAtlasNavigation.current
    val repository = prototypeRepository()
    val eInk = LocalAtlasEnvironment.current.eInk
    var removedBookIds by remember(collection.id) {
        mutableStateOf(repository.stringList("library.removed.bookIds").toSet())
    }
    val books = LibraryAtlasFixtures.booksForCollection(collection).filterNot { it.id in removedBookIds }
    var layout by rememberSaveable(context.profile.name, collection.id) {
        mutableStateOf(
            AtlasLayout.entries.firstOrNull { it.name == repository.string("library.collection.${collection.id}.layout") }
                ?: context.layout ?: if (eInk) AtlasLayout.GRID else AtlasLayout.LIST,
        )
    }
    var sortMode by rememberSaveable(collection.id) {
        mutableStateOf(
            LibraryBookSortMode.entries.firstOrNull {
                it.name == repository.string("library.collection.${collection.id}.sort")
            }?.takeUnless { it == LibraryBookSortMode.CUSTOM } ?: LibraryBookSortMode.RECENTLY_READ,
        )
    }
    var sortDirection by rememberSaveable(collection.id) {
        mutableStateOf(
            LibraryBookSortDirection.entries.firstOrNull {
                it.name == repository.string("library.collection.${collection.id}.sort.direction")
            } ?: LibraryBookSortDirection.DESCENDING,
        )
    }
    val sortedBooks = books.orderedForLibrary(sortMode, sortDirection)
    var selectionActive by remember(context.state) { mutableStateOf(context.state == AtlasPageState.SELECTION) }
    var selected by remember(context.state) {
        mutableStateOf(if (context.state == AtlasPageState.SELECTION) sortedBooks.take(3).map { it.id }.toSet() else emptySet())
    }
    var sortOpen by rememberSaveable(collection.id) { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(false) }
    var pendingCollectionName by remember { mutableStateOf<String?>(null) }
    var pendingRemoval by remember { mutableStateOf(emptySet<String>()) }
    fun clearSelection() {
        selectionActive = false
        selected = emptySet()
    }
    fun toggleBook(id: String) {
        selectionActive = true
        selected = if (id in selected) selected - id else selected + id
    }
    BackHandler(selectionActive) { clearSelection() }
    BackHandler(sheet) { sheet = false }
    val selectionBar = if (selectionActive) {
        val allIds = sortedBooks.map { it.id }.toSet()
        val allSelected = allIds.isNotEmpty() && selected.containsAll(allIds)
        AtlasSelectionBar(
            count = selected.size,
            onClose = ::clearSelection,
            allSelected = allSelected,
            onToggleAll = { selected = if (allSelected) emptySet() else allIds },
            bulkActions = listOf(
                AtlasTopBarAction(AtlasIcons.FolderAdd, "用所选新建收藏夹") { pendingCollectionName = "" },
                AtlasTopBarAction(AtlasIcons.FolderMove, "加入其他收藏夹") { sheet = true },
                AtlasTopBarAction(AtlasIcons.Delete, "移出总书架") { pendingRemoval = selected },
            ),
        )
    } else null
    val page = pageSlice(
        "smart-${collection.id}-${layout.name}-${sortMode.name}-${sortDirection.name}",
        sortedBooks,
        if (layout == AtlasLayout.GRID) 9 else if (layout == AtlasLayout.COMPACT) 8 else 6,
    )
    val shown = if (eInk) page.items else sortedBooks
    val ruleConditions = collection.ruleSummary.orEmpty().split(" · ").filter(String::isNotBlank)
    AtlasScaffold(
        modifier = modifier,
        topBar = {
            AtlasTopBar(
                title = collection.name,
                subtitle = "${books.size} 本",
                onUp = navigation.up,
                selection = selectionBar,
                actionBudgetOverride = 3,
                actions = listOf(
                    AtlasTopBarAction(
                        layout.currentLayoutIcon(),
                        layout.layoutToggleContentDescription(),
                    ) {
                        layout = layout.nextAtlasLayout()
                        repository.putString("library.collection.${collection.id}.layout", layout.name, "SmartCollectionLayoutChanged", collection.id)
                    },
                ),
                overflow = listOf(
                    AtlasOverflowItem("排序：${sortMode.summary(sortDirection)}") { sortOpen = true },
                    AtlasOverflowItem("编辑规则") {
                        repository.record("SmartRuleEditOpened", collection.id, "success")
                        navigation.navigate(AtlasRoute.LIBRARY_COLLECTION_RULE)
                    },
                ),
            )
        },
        footer = if (eInk && context.state.showsContent && books.isNotEmpty()) {
            { PaginationFooter(page.page, page.pages, page.setPage) }
        } else null,
    ) {
        StateOrContent(
            context.state,
            "没有书籍匹配当前规则",
            null,
            "规则求值失败",
            "规则定义未受影响；可重试匹配。",
        ) {
            Column(Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(AtlasSpacing.Md),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(Modifier.padding(AtlasSpacing.Md)) {
                        Text("规则", style = MaterialTheme.typography.titleMedium)
                        ruleConditions.forEach { condition ->
                            Text(condition, modifier = Modifier.padding(top = AtlasSpacing.Xs))
                        }
                        AtlasButton("编辑规则", {
                            repository.record("SmartRuleEditOpened", collection.id, "success")
                            navigation.navigate(AtlasRoute.LIBRARY_COLLECTION_RULE)
                        }, modifier = Modifier.padding(top = AtlasSpacing.Sm), style = AtlasButtonStyle.SECONDARY)
                    }
                }
                BookSurface(
                    context = context,
                    books = shown,
                    layout = layout,
                    selected = selected,
                    toggle = ::toggleBook,
                    selectionActive = selectionActive,
                    interaction = LibraryBookInteractionCapabilities(drag = false, reorder = false),
                    onLongPress = { toggleBook(it.id) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    if (sortOpen) {
        LibrarySortDialog(
            mode = sortMode,
            direction = sortDirection,
            onModeChange = {
                sortMode = it
                repository.putString("library.collection.${collection.id}.sort", it.name, "SmartCollectionSortChanged", collection.id)
            },
            onDirectionChange = {
                sortDirection = it
                repository.putString(
                    "library.collection.${collection.id}.sort.direction",
                    it.name,
                    "SmartCollectionSortDirectionChanged",
                    collection.id,
                )
            },
            onDismiss = { sortOpen = false },
            allowCustom = false,
        )
    }
    pendingCollectionName?.let { name ->
        FullDialog("新建收藏夹", { pendingCollectionName = null }) {
            OutlinedTextField(
                value = name,
                onValueChange = { pendingCollectionName = it },
                label = { Text("收藏夹名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            DialogButtons("创建", {
                if (name.isNotBlank()) {
                    createPersistedUserCollection(repository, name, selected)
                    repository.record("SmartSelectionCollectionCreated", collection.id, "success")
                    pendingCollectionName = null
                    clearSelection()
                }
            }, { pendingCollectionName = null })
        }
    }
    if (pendingRemoval.isNotEmpty()) {
        FullDialog("移出总书架 ${pendingRemoval.size} 本书？", { pendingRemoval = emptySet() }, destructive = true) {
            Text("书籍文件、收藏关系和阅读进度都会从当前本地书架视图中移除。")
            DialogButtons("移出", {
                removedBookIds += pendingRemoval
                repository.putStringList("library.removed.bookIds", removedBookIds.sorted(), "SmartCollectionBooksRemovedFromLibrary")
                pendingRemoval = emptySet()
                clearSelection()
            }, { pendingRemoval = emptySet() })
        }
    }
    if (sheet) MembershipSheet(context, { sheet = false }) { sheet = false }
}

// -------------------------------------------------------------------------------------------
// #9 — library/collections/{id}/rule
// -------------------------------------------------------------------------------------------
