/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.tsuyomi.prototype.uiatlas.AtlasStrings
import org.tsuyomi.prototype.uiatlas.components.AtlasBanner
import org.tsuyomi.prototype.uiatlas.components.AtlasIconButton
import org.tsuyomi.prototype.uiatlas.components.AtlasIcons
import org.tsuyomi.prototype.uiatlas.components.AtlasInfoBanner
import org.tsuyomi.prototype.uiatlas.components.AtlasMutationPhase
import org.tsuyomi.prototype.uiatlas.components.AtlasMutationStatus
import org.tsuyomi.prototype.uiatlas.components.AtlasOverflowItem
import org.tsuyomi.prototype.uiatlas.components.AtlasScaffold
import org.tsuyomi.prototype.uiatlas.components.AtlasTopBar
import org.tsuyomi.prototype.uiatlas.components.AtlasTopBarAction
import org.tsuyomi.prototype.uiatlas.components.LibraryBookInteractionCapabilities
import org.tsuyomi.prototype.uiatlas.components.LibraryBookSortDirection
import org.tsuyomi.prototype.uiatlas.components.LibraryBookSortMode
import org.tsuyomi.prototype.uiatlas.components.LibraryDragCoordinator
import org.tsuyomi.prototype.uiatlas.components.currentLayoutIcon
import org.tsuyomi.prototype.uiatlas.components.layoutToggleContentDescription
import org.tsuyomi.prototype.uiatlas.components.nextAtlasLayout
import org.tsuyomi.prototype.uiatlas.components.orderedForLibrary
import org.tsuyomi.prototype.uiatlas.components.summary
import org.tsuyomi.prototype.uiatlas.fixtures.LibraryAtlasFixtures
import org.tsuyomi.prototype.uiatlas.model.AtlasBook
import org.tsuyomi.prototype.uiatlas.model.AtlasContext
import org.tsuyomi.prototype.uiatlas.model.AtlasFamily
import org.tsuyomi.prototype.uiatlas.model.AtlasLayout
import org.tsuyomi.prototype.uiatlas.model.AtlasLibraryView
import org.tsuyomi.prototype.uiatlas.model.AtlasPageState
import org.tsuyomi.prototype.uiatlas.model.AtlasRoute
import org.tsuyomi.prototype.uiatlas.model.LocalAtlasNavigation
import org.tsuyomi.prototype.uiatlas.runtime.LocalPrototypeRuntime
import org.tsuyomi.prototype.uiatlas.runtime.prototypeRepository
import org.tsuyomi.prototype.uiatlas.theme.AtlasSpacing
import org.tsuyomi.prototype.uiatlas.theme.LocalAtlasEnvironment

@Composable
internal fun LibraryMirror(context: AtlasContext, modifier: Modifier) {
    val bindingDefault = if (context.libraryView == AtlasLibraryView.MIRROR) "mirror-bamboo" else "mirror-pine"
    val navigation = LocalAtlasNavigation.current
    val runtime = LocalPrototypeRuntime.current
    val repository = prototypeRepository()
    val eInk = LocalAtlasEnvironment.current.eInk
    val bindingId = repository.string("library.mirror.binding", bindingDefault)
    val binding = if (bindingId == LibraryAtlasFixtures.mirrorBamboo.id) {
        LibraryAtlasFixtures.mirrorBamboo
    } else {
        LibraryAtlasFixtures.mirrorPine
    }
    val depth = when (context.route) {
        AtlasRoute.LIBRARY_MIRROR -> 0
        AtlasRoute.LIBRARY_MIRROR_FOLDER -> 1
        else -> 2
    }
    val nodeId = if (depth == 0) null else repository.string("library.mirror.level.$depth.id")
    val currentNode = nodeId?.let(binding.roots::findMirrorNode)
    val localPage = nodeId == MIRROR_LOCAL_NODE_ID
    val pageNodes = currentNode?.children ?: if (depth == 0) binding.roots else emptyList()
    val folderNodes = pageNodes.filter { it.kind == LibraryAtlasFixtures.MirrorNodeKind.FOLDER }
    val bookNodes = pageNodes.filter { it.kind == LibraryAtlasFixtures.MirrorNodeKind.BOOK }
    val bookCatalog = (LibraryAtlasFixtures.viewFixture(AtlasLibraryView.MIRROR).books +
        LibraryAtlasFixtures.viewFixture(AtlasLibraryView.ALL).books).associateBy(AtlasBook::title)
    val books = bookNodes.map { node ->
        val template = bookCatalog[node.name] ?: LibraryAtlasFixtures.viewFixture(AtlasLibraryView.MIRROR).books.first()
        template.copy(id = "mirror:${binding.id}:${node.id}", title = node.name, source = binding.source)
    }
    var layout by rememberSaveable(context.profile.name, binding.id, nodeId) {
        mutableStateOf(
            AtlasLayout.entries.firstOrNull { it.name == repository.string("library.mirror.${binding.id}.layout") }
                ?: context.layout ?: if (eInk) AtlasLayout.GRID else AtlasLayout.LIST,
        )
    }
    var sortMode by rememberSaveable(binding.id, nodeId) {
        mutableStateOf(
            LibraryBookSortMode.entries.firstOrNull {
                it.name == repository.string("library.mirror.${binding.id}.sort")
            }?.takeUnless { it == LibraryBookSortMode.CUSTOM } ?: LibraryBookSortMode.TITLE,
        )
    }
    var sortDirection by rememberSaveable(binding.id, nodeId) {
        mutableStateOf(
            LibraryBookSortDirection.entries.firstOrNull {
                it.name == repository.string("library.mirror.${binding.id}.sort.direction")
            } ?: LibraryBookSortDirection.ASCENDING,
        )
    }
    val sortedBooks = books.orderedForLibrary(sortMode, sortDirection)
    var sortOpen by rememberSaveable(binding.id, nodeId) { mutableStateOf(false) }
    var disableOpen by remember(context.state) { mutableStateOf(context.state == AtlasPageState.MODAL) }
    var localOrganizationCreated by rememberSaveable(binding.id) {
        mutableStateOf(repository.boolean("mirror.${binding.id}.localOrganization"))
    }
    val dragCoordinator = remember { LibraryDragCoordinator() }
    val coroutineScope = rememberCoroutineScope()
    var interactiveCalibration by remember { mutableStateOf<AtlasMutationStatus?>(null) }
    val calibrate: () -> Unit = {
        interactiveCalibration = AtlasMutationStatus(AtlasMutationPhase.WORKING, "正在校准网站镜像…")
        coroutineScope.launch {
            val result = runtime.scenarios.run("mirror-calibration", binding.id)
            interactiveCalibration = if (result.successful) {
                AtlasMutationStatus(AtlasMutationPhase.SUCCESS, "校准完成；网站结构快照已更新。")
            } else {
                AtlasMutationStatus(AtlasMutationPhase.ERROR, "校准未完成：${result.outcome}。可再次使用顶部校准操作。")
            }
        }
    }
    BackHandler(disableOpen) { disableOpen = false }
    val calibration = when (context.variant?.option) {
        "b" -> LibraryAtlasFixtures.CalibrationPhase.SUCCESS
        "c" -> LibraryAtlasFixtures.CalibrationPhase.FAILED
        else -> LibraryAtlasFixtures.CalibrationPhase.WORKING
    }
    val capturedMutation = if (context.state == AtlasPageState.MUTATION) {
        when (calibration) {
            LibraryAtlasFixtures.CalibrationPhase.WORKING -> AtlasMutationStatus(AtlasMutationPhase.WORKING, LibraryAtlasFixtures.calibrationMessage(calibration))
            LibraryAtlasFixtures.CalibrationPhase.SUCCESS -> AtlasMutationStatus(AtlasMutationPhase.SUCCESS, LibraryAtlasFixtures.calibrationMessage(calibration))
            LibraryAtlasFixtures.CalibrationPhase.FAILED -> AtlasMutationStatus(AtlasMutationPhase.ERROR, LibraryAtlasFixtures.calibrationMessage(calibration), "重新校准", calibrate)
        }
    } else null
    val mutation = interactiveCalibration ?: capturedMutation
    val mirrorState = if (context.state == AtlasPageState.UNRESOLVED) AtlasPageState.ERROR else context.state
    val nextDepth = depth + 1
    val nextRoute = mirrorRouteForDepth(nextDepth)
    val folderItems = buildList {
        addAll(folderNodes.map { mirrorShortcut(it, nextRoute, bookCatalog) })
        if (depth == 0 && localOrganizationCreated) {
            add(
                Rc21ShortcutItem(
                    id = MIRROR_LOCAL_NODE_ID,
                    label = "本地整理",
                    supporting = "本地",
                    kind = ShortcutKind.MIRROR,
                    icon = AtlasIcons.Folder,
                    route = nextRoute,
                ),
            )
        }
    }
    val subtitle = when {
        localPage -> "本地"
        depth == 0 && binding.frozen -> "网站镜像 · ${AtlasStrings.FROZEN_MIRROR}"
        depth == 0 -> "网站镜像"
        folderItems.isEmpty() -> "${books.size} 本"
        else -> "${books.size} 本 · ${folderItems.size} 个收藏夹"
    }
    AtlasScaffold(
        modifier = modifier,
        topBar = {
            Column {
                AtlasTopBar(
                    title = if (localPage) "本地整理" else currentNode?.name ?: binding.source.name,
                    subtitle = subtitle,
                    onUp = navigation.up,
                    actionBudgetOverride = 2,
                    actions = buildList {
                        if (!localPage) {
                            add(AtlasTopBarAction(layout.currentLayoutIcon(), layout.layoutToggleContentDescription()) {
                                layout = layout.nextAtlasLayout()
                                repository.putString("library.mirror.${binding.id}.layout", layout.name, "MirrorLayoutChanged", binding.id)
                            })
                        }
                        add(AtlasTopBarAction(AtlasIcons.Refresh, "校准镜像", calibrate))
                    },
                    overflow = buildList {
                        if (books.isNotEmpty()) {
                            add(AtlasOverflowItem("排序：${sortMode.summary(sortDirection)}") { sortOpen = true })
                        }
                        if (depth == 0 && !localOrganizationCreated) add(AtlasOverflowItem("新建本地整理") {
                            localOrganizationCreated = true
                            repository.putBoolean("mirror.${binding.id}.localOrganization", true, "MirrorLocalOrganizationCreated", binding.id)
                        })
                        add(AtlasOverflowItem(if (binding.frozen) "启用镜像" else "停用镜像…") { disableOpen = true })
                        add(AtlasOverflowItem("在浏览中打开来源") { navigation.selectRoot(AtlasFamily.SOURCE) })
                    },
                )
                if (binding.frozen) {
                    AtlasInfoBanner(AtlasBanner(AtlasStrings.FROZEN_MIRROR, "展示最后完整快照。"))
                }
                OverlayState(mirrorState, mutation)
            }
        },
    ) {
        if (localPage) {
            Column(Modifier.fillMaxSize()) {
                ListItem(
                    headlineContent = { Text("快捷书架") },
                    trailingContent = {
                        Switch(
                            checked = repository.boolean("mirror.${binding.id}.shortcut", false),
                            onCheckedChange = { repository.putBoolean("mirror.${binding.id}.shortcut", it, "MirrorShortcutChanged", binding.id) },
                        )
                    },
                )
                ListItem(
                    headlineContent = { Text("本地标签") },
                    trailingContent = { AtlasIconButton(AtlasIcons.Edit, "整理本地标签", { navigation.navigate(AtlasRoute.LIBRARY_TAGS) }) },
                )
            }
        } else {
            StateOrContent(
                mirrorState,
                "暂无内容",
                null,
                "快照加载失败",
                "最后完整快照保持不变。",
                "校准网站镜像",
                calibrate,
            ) {
                Column(Modifier.fillMaxSize()) {
                    if (folderItems.isNotEmpty()) {
                        Text("收藏夹（${folderItems.size}）", modifier = Modifier.padding(horizontal = AtlasSpacing.Md, vertical = AtlasSpacing.Sm), style = MaterialTheme.typography.titleMedium)
                        val rows = (folderItems.size + 2) / 3
                        ShortcutExpandedGrid(
                            items = folderItems,
                            locked = true,
                            onOpen = { item ->
                                repository.putString("library.mirror.level.$nextDepth.id", item.id, "MirrorFolderOpened", item.id)
                                navigation.navigate(item.route)
                            },
                            dragCoordinator = dragCoordinator,
                            modifier = Modifier.fillMaxWidth().height((196 * rows).dp),
                            acceptBookAtRoot = false,
                        )
                    }
                    if (books.isNotEmpty()) {
                        Text("书籍（${books.size}）", modifier = Modifier.padding(horizontal = AtlasSpacing.Md, vertical = AtlasSpacing.Sm), style = MaterialTheme.typography.titleMedium)
                        BookSurface(
                            context = context,
                            books = sortedBooks,
                            layout = layout,
                            selected = emptySet(),
                            toggle = {},
                            interaction = LibraryBookInteractionCapabilities(multiSelect = false, longPress = false, drag = false, reorder = false),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
    if (sortOpen) {
        LibrarySortDialog(
            mode = sortMode,
            direction = sortDirection,
            onModeChange = {
                sortMode = it
                repository.putString("library.mirror.${binding.id}.sort", it.name, "MirrorSortChanged", binding.id)
            },
            onDirectionChange = {
                sortDirection = it
                repository.putString(
                    "library.mirror.${binding.id}.sort.direction",
                    it.name,
                    "MirrorSortDirectionChanged",
                    binding.id,
                )
            },
            onDismiss = { sortOpen = false },
            allowCustom = false,
        )
    }
    if (disableOpen) {
        FullDialog(if (binding.frozen) "启用镜像？" else "停用镜像？", { disableOpen = false }) {
            Text("只改变本地镜像状态；不会自动读取或写入网站。")
            DialogButtons(if (binding.frozen) "启用镜像" else "停用镜像", {
                repository.putBoolean("mirror.${binding.id}.enabled", binding.frozen, "MirrorEnabledChanged", binding.id)
                disableOpen = false
            }, { disableOpen = false })
        }
    }
}
// -------------------------------------------------------------------------------------------
// #11 — library/mirror/{bindingId}
// -------------------------------------------------------------------------------------------

private const val MIRROR_LOCAL_NODE_ID = "mirror-local-organization"

private fun mirrorShortcut(
    node: LibraryAtlasFixtures.MirrorNodeFixture,
    route: AtlasRoute,
    booksByTitle: Map<String, AtlasBook>,
): Rc21ShortcutItem = Rc21ShortcutItem(
    id = node.id,
    label = node.name,
    supporting = "${node.childCount} 项",
    kind = ShortcutKind.MIRROR,
    icon = AtlasIcons.Folder,
    route = route,
    collectionBooks = node.children
        .filter { it.kind == LibraryAtlasFixtures.MirrorNodeKind.BOOK }
        .mapNotNull { booksByTitle[it.name] }
        .take(3),
)
