/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.tsuyomi.prototype.uiatlas.components.AtlasIcons
import org.tsuyomi.prototype.uiatlas.components.AtlasMutationPhase
import org.tsuyomi.prototype.uiatlas.components.AtlasMutationStatus
import org.tsuyomi.prototype.uiatlas.components.AtlasSelectionBar
import org.tsuyomi.prototype.uiatlas.components.AtlasTopBarAction
import org.tsuyomi.prototype.uiatlas.components.LibraryBookSortDirection
import org.tsuyomi.prototype.uiatlas.components.LibraryBookSortMode
import org.tsuyomi.prototype.uiatlas.components.LibraryDragCoordinator
import org.tsuyomi.prototype.uiatlas.components.LibraryDropDestination
import org.tsuyomi.prototype.uiatlas.components.orderedForLibrary
import org.tsuyomi.prototype.uiatlas.fixtures.LibraryAtlasFixtures
import org.tsuyomi.prototype.uiatlas.model.AtlasBook
import org.tsuyomi.prototype.uiatlas.model.AtlasContext
import org.tsuyomi.prototype.uiatlas.model.AtlasLayout
import org.tsuyomi.prototype.uiatlas.model.AtlasLibraryView
import org.tsuyomi.prototype.uiatlas.model.AtlasRoute
import org.tsuyomi.prototype.uiatlas.runtime.PrototypeRepository
import org.tsuyomi.prototype.uiatlas.runtime.PrototypeRuntime

internal class LibraryRootSavedState(
    val layout: MutableState<AtlasLayout>,
    val sortMode: MutableState<LibraryBookSortMode>,
    val sortDirection: MutableState<LibraryBookSortDirection>,
    val shortcutExpanded: MutableState<Boolean>,
    val shortcutLocked: MutableState<Boolean>,
    val sortOpen: MutableState<Boolean>,
    val shortcutEditing: MutableState<Boolean>,
    val shortcutPageOpen: MutableState<Boolean>,
)

internal class LibraryRootStateHolder(
    context: AtlasContext,
    private val runtime: PrototypeRuntime,
    private val repository: PrototypeRepository,
    private val coroutineScope: CoroutineScope,
    saved: LibraryRootSavedState,
) {
    val standaloneNodePage = context.route == AtlasRoute.LIBRARY_SYSTEM
    val view = if (standaloneNodePage) context.libraryView else AtlasLibraryView.ALL
    val fixture = LibraryAtlasFixtures.viewFixture(view)
    private val rawBooks = if (context.variant?.id == 'E') LibraryAtlasFixtures.variantEBooks else fixture.books

    var layout by saved.layout
    var sortMode by saved.sortMode
    var sortDirection by saved.sortDirection
    var shortcutExpanded by saved.shortcutExpanded
    var shortcutLocked by saved.shortcutLocked
    var sortOpen by saved.sortOpen
    var shortcutEditing by saved.shortcutEditing
    var shortcutPageOpen by saved.shortcutPageOpen

    var removedBookIds by mutableStateOf(
        if (runtime.persistent) repository.stringList("library.removed.bookIds").toSet() else emptySet(),
    )
        private set
    var orderedBookIds by mutableStateOf(
        if (runtime.persistent) repository.stringList("library.order.all") else emptyList(),
    )
        private set
    var userCollections by mutableStateOf(loadUserCollections(repository))
        private set
    var hiddenShortcutIds by mutableStateOf(
        if (runtime.persistent) repository.stringList("library.shortcuts.hidden").toSet() else emptySet(),
    )
        private set
    var shortcuts by mutableStateOf(
        shortcutItems(
            shortcutBookCatalog,
            if (runtime.persistent) repository.stringList("library.shortcuts.order") else emptyList(),
            hiddenShortcutIds,
            userCollections,
        ),
    )
        private set
    var syncStatus by mutableStateOf<AtlasMutationStatus?>(null)
        private set
    var pendingRemoval by mutableStateOf<LibraryRemovalRequest?>(null)
    var pendingCollectionCreation by mutableStateOf<PendingCollectionCreation?>(null)
    var collectionName by mutableStateOf("")
    var collectionPicker by mutableStateOf<CollectionPickerRequest?>(null)
    var selectionKind by mutableStateOf<LibrarySelectionKind?>(null)
        private set
    var selectedBookIds by mutableStateOf(emptySet<String>())
        private set
    var selectedCollectionIds by mutableStateOf(emptySet<String>())
        private set
    var selectionConflictSignal by mutableIntStateOf(0)
        private set
    var selectionConflictTarget by mutableStateOf<String?>(null)
        private set

    val dragCoordinator = LibraryDragCoordinator()

    val books: List<AtlasBook>
        get() = rawBooks
            .filterNot { it.id in removedBookIds }
            .orderedForLibrary(sortMode, sortDirection, orderedBookIds)

    val shortcutBookCatalog: List<AtlasBook>
        get() = (LibraryAtlasFixtures.viewFixture(AtlasLibraryView.ALL).books + rawBooks)
            .distinctBy { it.id }
            .filterNot { it.id in removedBookIds }

    val shortcutBooksById: Map<String, AtlasBook>
        get() = shortcutBookCatalog.associateBy(AtlasBook::id)

    val libraryReorderEnabled: Boolean
        get() = view == AtlasLibraryView.ALL && sortMode == LibraryBookSortMode.CUSTOM

    init {
        bindDragCoordinator()
    }

    fun runLibrarySyncCheck() {
        if (syncStatus?.phase == AtlasMutationPhase.WORKING) return
        coroutineScope.launch {
            syncStatus = AtlasMutationStatus(AtlasMutationPhase.WORKING, "正在同步书架并检查更新…")
            val result = runtime.scenarios.run("updates-check", "library")
            syncStatus = if (result.successful) {
                AtlasMutationStatus(
                    AtlasMutationPhase.SUCCESS,
                    "同步完成 · 已检查 ${LibraryAtlasFixtures.updateEntries.size} 项更新",
                )
            } else {
                AtlasMutationStatus(
                    AtlasMutationPhase.ERROR,
                    "同步或检查更新未完成：${result.outcome}",
                )
            }
        }
    }

    fun clearSelection() {
        selectionKind = null
        selectedBookIds = emptySet()
        selectedCollectionIds = emptySet()
        selectionConflictTarget = null
    }

    fun selectBooks(ids: Set<String>) {
        when (selectionKind) {
            null, LibrarySelectionKind.BOOK -> {
                selectionConflictTarget = null
                selectionKind = LibrarySelectionKind.BOOK
                selectedBookIds = ids
            }
            LibrarySelectionKind.COLLECTION -> rejectSelection(ids.firstOrNull()?.let { "book:$it" })
        }
    }

    fun toggleBook(id: String) {
        when (selectionKind) {
            null, LibrarySelectionKind.BOOK -> {
                selectionConflictTarget = null
                selectionKind = LibrarySelectionKind.BOOK
                selectedBookIds = if (id in selectedBookIds) selectedBookIds - id else selectedBookIds + id
            }
            LibrarySelectionKind.COLLECTION -> rejectSelection("book:$id")
        }
    }

    fun toggleCollection(id: String) {
        if (id !in userCollections.map(UserCollection::id)) {
            rejectSelection("shortcut:$id")
            return
        }
        when (selectionKind) {
            null, LibrarySelectionKind.COLLECTION -> {
                selectionConflictTarget = null
                selectionKind = LibrarySelectionKind.COLLECTION
                selectedCollectionIds = if (id in selectedCollectionIds) selectedCollectionIds - id else selectedCollectionIds + id
            }
            LibrarySelectionKind.BOOK -> rejectSelection("shortcut:$id")
        }
    }

    fun setShortcuts(updated: List<Rc21ShortcutItem>, eventName: String) {
        shortcuts = updated
        repository.putStringList("library.shortcuts.order", updated.map(Rc21ShortcutItem::id), eventName, "library.shortcuts")
    }

    fun removeShortcutItems(target: List<Rc21ShortcutItem>) {
        val ids = target.map(Rc21ShortcutItem::id).toSet()
        if (ids.isEmpty()) return
        hiddenShortcutIds += ids
        repository.putStringList(
            "library.shortcuts.hidden",
            hiddenShortcutIds.sorted(),
            if (ids.size == 1) "ShortcutHidden" else "ShortcutsHidden",
            ids.sorted().joinToString(","),
        )
        setShortcuts(
            shortcuts.filterNot { it.id in ids },
            if (ids.size == 1) "ShortcutRemoved" else "ShortcutsRemoved",
        )
    }

    fun addBooksToCollection(collectionId: String, ids: Set<String>, eventName: String) {
        val key = "library.collection.$collectionId.books"
        val updated = repository.stringList(key).toSet() + ids
        repository.putStringList(key, updated.sorted(), eventName, collectionId)
        userCollections.firstOrNull { it.id == collectionId }?.let { collection ->
            val replacement = collection.copy(bookIds = updated)
            userCollections = userCollections.map { if (it.id == collectionId) replacement else it }
            shortcuts = shortcuts.map {
                if (it.id == collectionId) collectionShortcut(replacement, shortcutBooksById) else it
            }
        }
    }

    fun removeBooks(target: List<AtlasBook>) {
        removedBookIds += target.map(AtlasBook::id)
        repository.putStringList(
            "library.removed.bookIds",
            removedBookIds.sorted(),
            if (target.size == 1) "BookRemovedFromLibrary" else "BooksRemovedFromLibrary",
            target.joinToString(",", transform = AtlasBook::id),
        )
        val removedIds = target.map(AtlasBook::id).toSet()
        setShortcuts(shortcuts.filterNot { it.book?.id in removedIds }, "ShortcutBooksRemovedWithLibraryBooks")
    }

    fun removeCollection(id: String) {
        val item = shortcuts.firstOrNull { it.id == id } ?: return
        if (id in userCollections.map(UserCollection::id)) {
            userCollections = userCollections.filterNot { it.id == id }
            repository.putStringList("library.collections.ids", userCollections.map(UserCollection::id), "CollectionRemoved", id)
        } else {
            hiddenShortcutIds += id
            repository.putStringList("library.shortcuts.hidden", hiddenShortcutIds.sorted(), "ShortcutHidden", id)
        }
        setShortcuts(shortcuts.filterNot { it.id == item.id }, "CollectionRemovedFromShortcutShelf")
    }

    fun createCollection(request: PendingCollectionCreation, name: String) {
        val collection = createPersistedUserCollection(repository, name, request.memberBookIds)
        userCollections += collection
        if (request.replacedShortcutIds.isNotEmpty()) {
            hiddenShortcutIds += request.replacedShortcutIds
            repository.putStringList(
                "library.shortcuts.hidden",
                hiddenShortcutIds.sorted(),
                "ShortcutBooksReplacedByCollection",
                collection.id,
            )
        }
        val replacement = collectionShortcut(collection, shortcutBooksById)
        val mutable = shortcuts.filterNot { it.id in request.replacedShortcutIds }.toMutableList()
        mutable.add((request.insertIndex ?: mutable.size).coerceIn(0, mutable.size), replacement)
        setShortcuts(mutable, "CollectionShortcutCreated")
    }

    fun selectionBar(): AtlasSelectionBar? {
        val targetIds = selectionTargetIds()
        val currentIds = if (selectionKind == LibrarySelectionKind.BOOK) selectedBookIds else selectedCollectionIds
        val allSelected = targetIds.isNotEmpty() && currentIds.containsAll(targetIds)
        return selectionKind?.let { kind ->
            AtlasSelectionBar(
                count = selectedBookIds.size + selectedCollectionIds.size,
                onClose = ::clearSelection,
                allSelected = allSelected,
                onToggleAll = {
                    if (kind == LibrarySelectionKind.BOOK) {
                        selectBooks(if (allSelected) emptySet() else targetIds)
                    } else {
                        selectedCollectionIds = if (allSelected) emptySet() else targetIds
                    }
                },
                bulkActions = bulkActions(kind),
            )
        }
    }

    private fun selectionTargetIds(): Set<String> = when (selectionKind) {
        LibrarySelectionKind.BOOK -> if (shortcutPageOpen) {
            shortcuts.mapNotNull { it.book?.id }.toSet()
        } else {
            books.map(AtlasBook::id).toSet()
        }
        LibrarySelectionKind.COLLECTION -> shortcuts
            .filter { it.kind == ShortcutKind.COLLECTION && it.id in userCollections.map(UserCollection::id) }
            .map(Rc21ShortcutItem::id)
            .toSet()
        null -> emptySet()
    }

    private fun bulkActions(kind: LibrarySelectionKind): List<AtlasTopBarAction> = if (kind == LibrarySelectionKind.BOOK) {
        listOf(
            AtlasTopBarAction(AtlasIcons.FolderAdd, "用所选新建收藏夹") {
                pendingCollectionCreation = PendingCollectionCreation(memberBookIds = selectedBookIds)
                collectionName = ""
            },
            AtlasTopBarAction(AtlasIcons.FolderMove, "移入收藏夹") {
                collectionPicker = CollectionPickerRequest.Books(selectedBookIds)
            },
            AtlasTopBarAction(AtlasIcons.Delete, if (shortcutPageOpen) "移出快捷书架" else "移出书架") {
                if (shortcutPageOpen) removeShortcutItems(shortcuts.filter { it.book?.id in selectedBookIds })
                else pendingRemoval = LibraryRemovalRequest.Books(shortcutBookCatalog.filter { it.id in selectedBookIds })
            },
        )
    } else {
        listOf(
            AtlasTopBarAction(AtlasIcons.FolderMove, "移入收藏夹") {
                collectionPicker = CollectionPickerRequest.Collections(selectedCollectionIds)
            },
            AtlasTopBarAction(AtlasIcons.Delete, "删除收藏夹") {
                pendingRemoval = LibraryRemovalRequest.Collections(shortcuts.filter { it.id in selectedCollectionIds })
            },
        )
    }

    private fun rejectSelection(target: String?) {
        selectionConflictTarget = target
        selectionConflictSignal++
    }

    private fun bindDragCoordinator() {
        dragCoordinator.onLongPress = { subjectKey -> handleLongPress(subjectKey) }
        dragCoordinator.onDrop = drop@ { payload, destination -> handleDrop(payload, destination) }
    }

    private fun handleLongPress(subjectKey: String) {
        when {
            subjectKey.startsWith("shortcut-book:") -> {
                shortcutPageOpen = true
                toggleBook(subjectKey.removePrefix("shortcut-book:"))
            }
            subjectKey.startsWith("book:") -> toggleBook(subjectKey.removePrefix("book:"))
            subjectKey.startsWith("shortcut:") -> {
                val id = subjectKey.removePrefix("shortcut:")
                if (shortcuts.any { it.id == id && it.kind == ShortcutKind.COLLECTION } &&
                    id in userCollections.map(UserCollection::id)
                ) {
                    shortcutPageOpen = true
                    toggleCollection(id)
                }
            }
        }
    }

    private fun handleDrop(payload: String, destination: LibraryDropDestination): Boolean {
        val bookIds = payloadBookIds(payload)
        return when (destination) {
            LibraryDropDestination.Remove -> handleRemoveDrop(payload, bookIds)
            is LibraryDropDestination.Collection -> handleCollectionDrop(destination.id, bookIds)
            is LibraryDropDestination.Book -> handleBookDrop(destination.id, bookIds)
            is LibraryDropDestination.Root -> handleRootDrop(payload, destination.index, bookIds)
            is LibraryDropDestination.Library -> handleLibraryDrop(payload, destination.index, bookIds)
        }
    }

    private fun handleRemoveDrop(payload: String, bookIds: Set<String>): Boolean = when {
        isShortcutBookPayload(payload) -> shortcuts.filter { it.book?.id in bookIds }.let { target ->
            if (target.isEmpty()) false else {
                removeShortcutItems(target)
                clearSelection()
                true
            }
        }
        bookIds.isNotEmpty() -> {
            val target = shortcutBookCatalog.filter { it.id in bookIds }
            pendingRemoval = if (target.size == 1) LibraryRemovalRequest.Book(target.single()) else LibraryRemovalRequest.Books(target)
            true
        }
        payload.startsWith(SHORTCUT_DRAG_PREFIX) -> {
            val item = shortcuts.firstOrNull { it.id == payload.removePrefix(SHORTCUT_DRAG_PREFIX) } ?: return false
            pendingRemoval = if (item.kind == ShortcutKind.COLLECTION) {
                LibraryRemovalRequest.Collection(item.id, item.label)
            } else {
                LibraryRemovalRequest.Shortcut(item.id, item.label)
            }
            true
        }
        else -> false
    }

    private fun handleCollectionDrop(collectionId: String, bookIds: Set<String>): Boolean {
        if (bookIds.isEmpty()) return false
        addBooksToCollection(
            collectionId,
            bookIds,
            if (bookIds.size == 1) "ShortcutBookDroppedIntoCollection" else "BooksDroppedIntoCollection",
        )
        clearSelection()
        return true
    }

    private fun handleBookDrop(shortcutId: String, bookIds: Set<String>): Boolean {
        if (bookIds.isEmpty()) return false
        val target = shortcuts.firstOrNull { it.id == shortcutId }?.book ?: return false
        pendingCollectionCreation = PendingCollectionCreation(
            memberBookIds = bookIds + target.id,
            replacedShortcutIds = setOf(shortcutId),
            insertIndex = shortcuts.indexOfFirst { it.id == shortcutId },
        )
        collectionName = ""
        return true
    }

    private fun handleRootDrop(payload: String, index: Int, bookIds: Set<String>): Boolean = when {
        bookIds.size > 1 -> {
            pendingCollectionCreation = PendingCollectionCreation(memberBookIds = bookIds, insertIndex = index)
            collectionName = ""
            true
        }
        bookIds.size == 1 -> {
            val additions = shortcutBookCatalog.filter { it.id in bookIds }.map(::bookShortcut)
            val additionIds = additions.map(Rc21ShortcutItem::id).toSet()
            if (hiddenShortcutIds.any { it in additionIds }) {
                hiddenShortcutIds -= additionIds
                repository.putStringList(
                    "library.shortcuts.hidden",
                    hiddenShortcutIds.sorted(),
                    "ShortcutBooksUnhidden",
                    additionIds.sorted().joinToString(","),
                )
            }
            val mutable = shortcuts.toMutableList()
            additions.forEachIndexed { offset, item ->
                mutable.removeAll { it.id == item.id }
                mutable.add((index + offset).coerceIn(0, mutable.size), item)
            }
            setShortcuts(mutable, "ShortcutBookDropped")
            clearSelection()
            true
        }
        payload.startsWith(SHORTCUT_DRAG_PREFIX) -> {
            val id = payload.removePrefix(SHORTCUT_DRAG_PREFIX)
            val mutable = shortcuts.toMutableList()
            val from = mutable.indexOfFirst { it.id == id }
            if (from < 0) return false
            val item = mutable.removeAt(from)
            mutable.add(index.coerceIn(0, mutable.size), item)
            setShortcuts(mutable, "ShortcutMoved")
            true
        }
        else -> false
    }

    private fun handleLibraryDrop(payload: String, index: Int, bookIds: Set<String>): Boolean {
        if (bookIds.isEmpty() || !libraryReorderEnabled || !payload.startsWith(BOOK_DRAG_PREFIX)) return false
        val mutable = books.toMutableList()
        val moved = mutable.filter { it.id in bookIds }
        mutable.removeAll(moved.toSet())
        mutable.addAll(index.coerceIn(0, mutable.size), moved)
        orderedBookIds = mutable.map(AtlasBook::id)
        repository.putStringList(
            "library.order.all",
            orderedBookIds,
            "LibraryBooksReordered",
            bookIds.joinToString(","),
        )
        clearSelection()
        return true
    }
}

@Composable
internal fun rememberLibraryRootState(
    context: AtlasContext,
    runtime: PrototypeRuntime,
    repository: PrototypeRepository,
    coroutineScope: CoroutineScope,
): LibraryRootStateHolder {
    val layout = rememberSaveable(context.profile.name, context.layout?.name, runtime.persistent) {
        mutableStateOf(
            if (runtime.persistent) {
                AtlasLayout.entries.firstOrNull { it.name == repository.string("library.layout") }
                    ?: context.effectiveLayout
            } else {
                context.effectiveLayout
            },
        )
    }
    val sortMode = rememberSaveable(runtime.persistent) {
        mutableStateOf(
            if (runtime.persistent) {
                LibraryBookSortMode.entries.firstOrNull { it.name == repository.string("library.sort") }
                    ?: LibraryBookSortMode.CUSTOM
            } else {
                LibraryBookSortMode.CUSTOM
            },
        )
    }
    val sortDirection = rememberSaveable(runtime.persistent) {
        mutableStateOf(
            if (runtime.persistent) {
                LibraryBookSortDirection.entries.firstOrNull {
                    it.name == repository.string("library.sort.direction")
                } ?: LibraryBookSortDirection.ASCENDING
            } else {
                LibraryBookSortDirection.ASCENDING
            },
        )
    }
    val shortcutExpanded = rememberSaveable(runtime.persistent) {
        mutableStateOf(if (runtime.persistent) repository.boolean("library.shortcuts.expanded") else false)
    }
    val shortcutLocked = rememberSaveable(runtime.persistent) {
        mutableStateOf(if (runtime.persistent) repository.boolean("library.shortcuts.locked") else false)
    }
    val sortOpen = rememberSaveable { mutableStateOf(false) }
    val shortcutEditing = rememberSaveable { mutableStateOf(false) }
    val shortcutPageOpen = rememberSaveable { mutableStateOf(false) }
    val saved = remember(
        layout,
        sortMode,
        sortDirection,
        shortcutExpanded,
        shortcutLocked,
        sortOpen,
        shortcutEditing,
        shortcutPageOpen,
    ) {
        LibraryRootSavedState(
            layout,
            sortMode,
            sortDirection,
            shortcutExpanded,
            shortcutLocked,
            sortOpen,
            shortcutEditing,
            shortcutPageOpen,
        )
    }
    return remember(context, runtime, repository, coroutineScope, saved) {
        LibraryRootStateHolder(context, runtime, repository, coroutineScope, saved)
    }
}
