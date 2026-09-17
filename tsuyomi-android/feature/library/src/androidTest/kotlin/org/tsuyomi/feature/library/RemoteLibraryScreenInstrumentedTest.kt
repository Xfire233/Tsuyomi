/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.feature.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.up
import androidx.compose.ui.test.advanceEventTime
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import kotlin.math.abs
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import org.tsuyomi.core.preferences.ColorSchemePreference
import org.tsuyomi.core.display.DisplayDecisionReason
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.core.display.DisplayEnvironmentProvider
import org.tsuyomi.core.preferences.DisplayPreference
import org.tsuyomi.core.preferences.DisplayPreferences
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.MotionPolicy
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.shared.librarydomain.LibraryBook
import org.tsuyomi.shared.librarydomain.LibraryEntry
import org.tsuyomi.core.media.api.FallbackSpec
import org.tsuyomi.core.ui.icons.TsuyomiIcons
import org.tsuyomi.core.ui.theme.TsuyomiTheme
import org.tsuyomi.core.ui.components.CoverCardPresentationProvider
import org.tsuyomi.shared.model.CoverCardPresentation

import org.tsuyomi.shared.model.BookIdentity
import androidx.compose.ui.unit.dp
import org.tsuyomi.shared.sourcecontract.RemoteTarget
import org.tsuyomi.shared.sourcecontract.SourceBookSummary

@RunWith(AndroidJUnit4::class)
class RemoteLibraryScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun groupedMirrorUsesFolderFirstSharedLibrarySurfaceWithoutPinAction() {
        var openedTarget: String? = null
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(840.dp, 900.dp))) {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    RemoteLibraryScreen(
                        sourceId = SourceId,
                        sourceName = "文库8",
                        books = books,
                        selectedIds = emptySet(),
                        state = RemoteLibraryViewState.CONTENT,
                        message = null,
                        copyConfirmationVisible = false,
                        onNavigateUp = {},
                        onRefresh = {},
                        onToggleSelection = {},
                        onClearSelection = {},
                        onRequestCopy = {},
                        onDismissCopy = {},
                        onConfirmCopy = {},
                        onOpenVerification = {},
                        onOpenBook = {},
                        targets = targets,
                        groupingEnabled = true,
                        onOpenTarget = { openedTarget = it },
                    )
                }
            }
            }
        }

        composeRule.onNodeWithTag("remote-library-folder-favorites").assertIsDisplayed().performClick()
        assertEquals("favorites", openedTarget)
        composeRule.onNodeWithTag("library-book-$SourceId-1").assertIsDisplayed()
        val viewportWidth = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.width
        val cardWidth = composeRule.onNodeWithTag("library-book-$SourceId-1").fetchSemanticsNode().boundsInRoot.width
        val cellFraction = cardWidth / viewportWidth
        assertTrue("Wide grid cell occupies $cellFraction of the viewport", cellFraction in (150f / 840f)..(200f / 840f))
        composeRule.onNodeWithContentDescription("更多操作").performClick()
        composeRule.onNodeWithText("固定到快捷书架").assertDoesNotExist()
        composeRule.onNodeWithText("全部复制到本地书架").assertIsDisplayed()
    }

    @Test
    fun simpleMirrorAggregatesBooksAndOffersGroupingOptInWithoutFolderChrome() {
        var groupingEnabled: Boolean? = null
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    RemoteLibraryScreen(
                        sourceId = SourceId,
                        sourceName = "文库8",
                        books = books,
                        selectedIds = emptySet(),
                        state = RemoteLibraryViewState.CONTENT,
                        message = null,
                        copyConfirmationVisible = false,
                        onNavigateUp = {},
                        onRefresh = {},
                        onToggleSelection = {},
                        onClearSelection = {},
                        onRequestCopy = {},
                        onDismissCopy = {},
                        onConfirmCopy = {},
                        onOpenVerification = {},
                        onOpenBook = {},
                        targets = targets,
                        groupingEnabled = false,
                        onGroupingEnabledChange = { groupingEnabled = it },
                    )
                }
            }
        }

        composeRule.onNodeWithText("网站收藏").assertIsDisplayed()
        composeRule.onNodeWithTag("remote-library-folder-favorites").assertDoesNotExist()
        composeRule.onNodeWithTag("library-book-$SourceId-1").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("更多操作").performClick()
        composeRule.onNodeWithText("启用网站分组").assertIsDisplayed().performClick()
        assertEquals(true, groupingEnabled)
    }

    @Test
    fun multiBookSelectionExposesSingleBookWebsiteBoundary() {
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    RemoteLibraryScreen(
                        sourceId = SourceId,
                        sourceName = "文库8",
                        books = books,
                        selectedIds = books.mapTo(linkedSetOf(), ::remoteLibrarySelectionId),
                        state = RemoteLibraryViewState.CONTENT,
                        message = null,
                        copyConfirmationVisible = false,
                        onNavigateUp = {},
                        onRefresh = {},
                        onToggleSelection = {},
                        onClearSelection = {},
                        onRequestCopy = {},
                        onDismissCopy = {},
                        onConfirmCopy = {},
                        onOpenVerification = {},
                        onOpenBook = {},
                        targets = targets,
                    )
                }
            }
        }

        composeRule.onNodeWithText("批量复制；网站移动/移除仅限单本").assertIsDisplayed()
    }

    @Test
    fun singleSelectionExposesLabelledMoveAndRemoveActions() {
        var movedBookId: String? = null
        var removedBookId: String? = null
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    RemoteLibraryScreen(
                        sourceId = SourceId,
                        sourceName = "文库8",
                        books = books,
                        selectedIds = setOf(remoteLibrarySelectionId(books.first())),
                        state = RemoteLibraryViewState.CONTENT,
                        message = null,
                        copyConfirmationVisible = false,
                        onNavigateUp = {},
                        onRefresh = {},
                        onToggleSelection = {},
                        onClearSelection = {},
                        onRequestCopy = {},
                        onDismissCopy = {},
                        onConfirmCopy = {},
                        onOpenVerification = {},
                        onOpenBook = {},
                        groupingEnabled = true,
                        onRequestMoveBook = { movedBookId = it.identity.remoteBookId },
                        onRequestRemoveBook = { removedBookId = it.identity.remoteBookId },
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("移至网站分类").performClick()
        composeRule.onNodeWithContentDescription("更多操作").performClick()
        composeRule.onAllNodesWithText("从网站收藏移除").filterToOne(hasClickAction()).performClick()
        assertEquals("1", movedBookId)
        assertEquals("1", removedBookId)
    }

    @Test
    fun simpleModeKeepsRemoveButHidesMoveForSingleSelection() {
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    RemoteLibraryScreen(
                        sourceId = SourceId,
                        sourceName = "文库8",
                        books = books,
                        selectedIds = setOf(remoteLibrarySelectionId(books.first())),
                        state = RemoteLibraryViewState.CONTENT,
                        message = null,
                        copyConfirmationVisible = false,
                        onNavigateUp = {},
                        onRefresh = {},
                        onToggleSelection = {},
                        onClearSelection = {},
                        onRequestCopy = {},
                        onDismissCopy = {},
                        onConfirmCopy = {},
                        onOpenVerification = {},
                        onOpenBook = {},
                        groupingEnabled = false,
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("移至网站分类").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("从网站收藏移除").assertIsDisplayed()
    }

    @Test
    fun remoteRemoveRequiresVerbAndEffectSpecificConfirmation() {
        var confirmedBookId: String? = null
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    RemoteLibraryScreen(
                        sourceId = SourceId,
                        sourceName = "文库8",
                        books = books,
                        selectedIds = emptySet(),
                        state = RemoteLibraryViewState.CONTENT,
                        message = null,
                        copyConfirmationVisible = false,
                        onNavigateUp = {},
                        onRefresh = {},
                        onToggleSelection = {},
                        onClearSelection = {},
                        onRequestCopy = {},
                        onDismissCopy = {},
                        onConfirmCopy = {},
                        onOpenVerification = {},
                        onOpenBook = {},
                        removeConfirmationBook = books.first(),
                        onConfirmRemove = { confirmedBookId = it.identity.remoteBookId },
                    )
                }
            }
        }

        composeRule.onAllNodesWithText("从网站收藏移除").filterToOne(hasClickAction()).assertIsDisplayed()
        composeRule.onNodeWithText("确定要从网站收藏中移除《文学少女》吗？\n此操作只修改网站收藏；本地书架、稍后再读、评分、标签和阅读进度均保留。")
            .assertIsDisplayed()
        composeRule.onAllNodesWithText("从网站收藏移除").filterToOne(hasClickAction()).performClick()
        assertEquals("1", confirmedBookId)
    }

    @Test
    fun stationaryLongPressSelectsBeforeMovementRevealsDistinctDragTargets() {
        var selectedIds by mutableStateOf(emptySet<String>())
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    RemoteLibraryScreen(
                        sourceId = SourceId,
                        sourceName = "文库8",
                        books = books,
                        selectedIds = selectedIds,
                        state = RemoteLibraryViewState.CONTENT,
                        message = null,
                        copyConfirmationVisible = false,
                        onNavigateUp = {},
                        onRefresh = {},
                        onToggleSelection = { book ->
                            val selectionId = remoteLibrarySelectionId(book)
                            selectedIds = if (selectionId in selectedIds) {
                                selectedIds - selectionId
                            } else {
                                selectedIds + selectionId
                            }
                        },
                        onClearSelection = { selectedIds = emptySet() },
                        onRequestCopy = {},
                        onDismissCopy = {},
                        onConfirmCopy = {},
                        onOpenVerification = {},
                        onOpenBook = {},
                    )
                }
            }
        }

        val book = composeRule.onNodeWithTag("library-book-$SourceId-1")
        book.performSemanticsAction(SemanticsActions.OnLongClick)
        composeRule.waitUntil(5_000) { selectedIds.size == 1 }
        composeRule.onNodeWithTag("library-drag-preview").assertDoesNotExist()
        composeRule.onNodeWithTag("remote-local-copy").assertDoesNotExist()
        book.performTouchInput {
            down(center)
            advanceEventTime(1_000)
            moveBy(Offset(0f, 80f), delayMillis = 120)
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("library-drag-preview").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("remote-local-copy").assertExists()
        composeRule.onNodeWithText("网站收藏保持不变").assertIsDisplayed()
        composeRule.onNodeWithTag("remote-remove").assertExists()
        composeRule.onNodeWithText("本地书籍与数据保留").assertIsDisplayed()
        composeRule.onNodeWithTag("library-delete-drop-target").assertDoesNotExist()
        book.performTouchInput { up() }
    }

    @Test
    fun listDragPreviewRetainsLeadingCoverAndCurrentTextStack() {
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    LibraryBookDragPreview(
                        entries = listOf(shortcutEntry),
                        layout = LibraryLayout.LIST,
                        coverState = { CoverUiState.Fallback(FallbackSpec(it.book.title, it.book.identity.sourceId)) },
                    )
                }
            }
        }

        val coverBounds = composeRule.onNodeWithTag("library-drag-preview-list-cover", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(kotlin.math.abs(coverBounds.width / coverBounds.height - 5f / 7f) < 0.02f)
        composeRule.onNodeWithText("快捷书籍").assertIsDisplayed()
        composeRule.onNodeWithText("测试作者").assertIsDisplayed()
        composeRule.onNodeWithText("未开始").assertIsDisplayed()
    }

    @Test
    fun compactDragPreviewRetainsHeadlineSupportingAndTrailingStructure() {
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    LibraryBookDragPreview(
                        entries = listOf(shortcutEntry),
                        layout = LibraryLayout.COMPACT,
                        coverState = { CoverUiState.Fallback(FallbackSpec(it.book.title, it.book.identity.sourceId)) },
                    )
                }
            }
        }

        composeRule.onNodeWithTag("library-drag-preview-compact-content").assertIsDisplayed()
        composeRule.onNodeWithText("快捷书籍").assertIsDisplayed()
        composeRule.onNodeWithText("测试作者").assertIsDisplayed()
        composeRule.onNodeWithText("★ 4").assertIsDisplayed()
    }

    @Test
    fun bookCenterAndInsertionEdgeResolveToMutuallyExclusiveDestinations() {
        val coordinator = LibraryDragCoordinator()
        val moving = BookIdentity(SourceId, "moving")
        val target = BookIdentity(SourceId, "target")
        coordinator.registerSource("moving", Rect(0f, 0f, 100f, 140f))
        coordinator.registerBook(target, index = 1, bounds = Rect(120f, 0f, 220f, 140f))
        coordinator.registerLibrary(Rect(0f, 0f, 300f, 500f), reorderEnabled = true)
        coordinator.start(
            subjectKey = "moving",
            localPosition = Offset(50f, 70f),
            payload = LibraryDragPayload.Books(setOf(moving)),
            canRemove = true,
            libraryReorderSource = true,
        )

        coordinator.moveBy(Offset(120f, 0f))
        assertEquals(target, coordinator.bookTargetIdentity)
        assertEquals(-1, coordinator.libraryInsertionIndex)

        coordinator.moveBy(Offset(-45f, 0f))
        assertEquals(null, coordinator.bookTargetIdentity)
        assertEquals(1, coordinator.libraryInsertionIndex)

        coordinator.moveBy(Offset(45f, 0f))
        assertEquals(target, coordinator.bookTargetIdentity)
        assertEquals(-1, coordinator.libraryInsertionIndex)
    }

    @Test
    fun mirrorGridCardsAndDragPreviewFollowTheSelectedPresentation() {

        val coordinator = LibraryDragCoordinator()
        val presentation = mutableStateOf(CoverCardPresentation.STANDARD)
        val drops = mutableListOf<LibraryDropDestination>()
        coordinator.onDrop = { _, destination -> drops += destination }
        var mirrorVisible by mutableStateOf(true)
        val mirror = LibraryMirrorShortcut(
            sourceId = SourceId,
            targetId = null,
            label = "Wenku8 超长网站来源名称用于验证省略",
            count = 4,
            frozen = false,
        )
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                CoverCardPresentationProvider(presentation.value) {

                TsuyomiTheme(environment) {
                    Column(Modifier.width(180.dp)) {
                        if (mirrorVisible) {
                        LibraryRootNodeGridCard(
                            item = LibraryRootItem.Mirror(mirror),
                            index = 0,
                            selected = false,
                            selectionActive = false,
                            reorderEnabled = false,
                            dragCoordinator = coordinator,
                            onOpenCollection = {},
                            onOpenMirror = {},
                            onLongPressCollection = {},
                            onToggleCollectionSelection = {},
                        )
                        }
                        LibraryBookGridCard(
                            entry = shortcutEntry,
                            update = null,
                            index = 1,
                            selected = false,
                            selectionActive = false,
                            selectedBookIds = emptySet(),
                            dragCoordinator = coordinator,
                            dragEnabled = false,
                            canRemove = false,
                            coverState = { CoverUiState.Fallback(FallbackSpec(it.book.title, it.book.identity.sourceId)) },
                            onCoverVisibility = { _, _ -> },
                            onOpenBook = {},
                            onLongPressBook = {},
                            onToggleBookSelection = {},
                            onIgnoreUpdate = {},
                        )
                        LibraryBookDragPreview(
                            entries = listOf(shortcutEntry.copy(book = shortcutEntry.book.copy(title = "拖拽预览书籍"))),
                            layout = LibraryLayout.GRID,
                            coverState = {
                                CoverUiState.Fallback(FallbackSpec(it.book.title, it.book.identity.sourceId))
                            },
                        )

                    }
                }
                }

            }
        }

        val standardRootBounds = composeRule.onNodeWithTag("library-root-node-${libraryMirrorRootId(SourceId)}")
            .fetchSemanticsNode().boundsInRoot
        val standardBookBounds = composeRule.onNodeWithTag("library-book-$SourceId-shortcut-book")
            .fetchSemanticsNode().boundsInRoot
        val standardDragPreviewBounds = composeRule.onNodeWithTag("library-drag-preview-grid-content")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(abs(standardRootBounds.width / standardRootBounds.height - 5f / 7f) < 0.02f)
        assertTrue(abs(standardBookBounds.width / standardBookBounds.height - 5f / 7f) < 0.02f)
        assertTrue(abs(standardDragPreviewBounds.width / standardDragPreviewBounds.height - 5f / 7f) < 0.02f)

        composeRule.runOnIdle { presentation.value = CoverCardPresentation.WIDE }
        composeRule.waitForIdle()

        val rootBounds = composeRule.onNodeWithTag("library-root-node-${libraryMirrorRootId(SourceId)}")
            .fetchSemanticsNode().boundsInRoot
        val bookBounds = composeRule.onNodeWithTag("library-book-$SourceId-shortcut-book")
            .fetchSemanticsNode().boundsInRoot
        val rootStatusBounds = composeRule.onNodeWithText("网站收藏 · 4 本", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val bookTitleBounds = composeRule.onNodeWithText("快捷书籍", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val bookStatusBounds = composeRule.onNode(
            hasText("未开始") and hasAnyAncestor(hasTestTag("library-book-$SourceId-shortcut-book")),
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        val dragPreviewBounds = composeRule.onNodeWithTag("library-drag-preview-grid-content")
            .fetchSemanticsNode().boundsInRoot

        assertTrue(abs(rootBounds.height - bookBounds.height) <= 1f)
        assertTrue(abs(rootBounds.width - bookBounds.width) <= 1f)
        assertTrue(abs(rootBounds.width / rootBounds.height - 16f / 9f) < 0.02f)
        assertTrue(abs(bookBounds.width / bookBounds.height - 16f / 9f) < 0.02f)
        assertTrue(abs(dragPreviewBounds.width / dragPreviewBounds.height - 16f / 9f) < 0.02f)
        assertTrue(rootStatusBounds.bottom <= rootBounds.bottom)
        assertTrue(abs((rootBounds.bottom - rootStatusBounds.bottom) - (bookBounds.bottom - bookStatusBounds.bottom)) <= 1f)
        assertTrue(bookBounds.top <= bookTitleBounds.top)
        assertTrue(bookTitleBounds.bottom <= bookStatusBounds.top)
        assertTrue(bookStatusBounds.bottom <= bookBounds.bottom)
        composeRule.onNodeWithText("网站收藏 · 4 本").assertIsDisplayed()
        val mirrorWindow = composeRule.onNodeWithTag("library-root-node-${libraryMirrorRootId(SourceId)}")
            .fetchSemanticsNode().boundsInWindow
        val bookWindow = composeRule.onNodeWithTag("library-book-$SourceId-shortcut-book")
            .fetchSemanticsNode().boundsInWindow
        composeRule.runOnIdle {
            coordinator.registerSource("mounted-card-drag", bookWindow)
            coordinator.start(
                subjectKey = "mounted-card-drag",
                localPosition = Offset.Zero,
                payload = LibraryDragPayload.Books(setOf(shortcutEntry.book.identity)),
                canRemove = false,
                libraryReorderSource = false,
            )
            coordinator.moveBy(mirrorWindow.center - bookWindow.topLeft)
            assertEquals(LibraryDropDestination.RemoteMirror(SourceId, null, mirror.label), coordinator.externalDestination)
            mirrorVisible = false
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("library-root-node-${libraryMirrorRootId(SourceId)}").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(null, coordinator.externalDestination)
            coordinator.finish(minimumDragDistance = 0f)
            assertEquals(emptyList<LibraryDropDestination>(), drops)
        }
    }

    private companion object {
        const val SourceId = "org.tsuyomi.wenku8"
        val shortcutEntry = LibraryEntry(
            book = LibraryBook(
                identity = BookIdentity(SourceId, "shortcut-book"),
                title = "快捷书籍",
                addedAt = Instant.EPOCH,
                metadataUpdatedAt = Instant.EPOCH,
                author = "测试作者",
            ),
            libraryAddedAt = Instant.EPOCH,
            rating = 4,
            localTags = emptySet(),
            sourceAvailable = true,
            reconciliation = null,
        )
        val books = listOf(
            SourceBookSummary(BookIdentity(SourceId, "1"), "文学少女", "野村美月", null, "https://example.com/1"),
            SourceBookSummary(BookIdentity(SourceId, "2"), "狼与香辛料", "支仓冻砂", null, "https://example.com/2"),
        )
        val targets = listOf(
            RemoteTarget("default", "默认书架", kind = "default"),
            RemoteTarget("favorites", "特别收藏"),
        )
        val environment = DisplayEnvironment(
            preferences = DisplayPreferences(DisplayPreference.STANDARD, ColorSchemePreference.LIGHT),
            effectiveProfile = DisplayProfile.STANDARD,
            decisionReason = DisplayDecisionReason.MANUAL_STANDARD,
            detectedDeviceLabel = null,
            dynamicColorEligible = false,
            dynamicColorEffective = false,
            effectiveDarkTheme = false,
            motionPolicy = MotionPolicy.INSTANT,
            redrawEpoch = 0,
        )
    }
}
