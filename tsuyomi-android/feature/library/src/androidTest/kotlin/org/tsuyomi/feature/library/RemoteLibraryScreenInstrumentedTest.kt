/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.feature.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.core.display.ColorSchemePreference
import org.tsuyomi.core.display.DisplayDecisionReason
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.core.display.DisplayEnvironmentProvider
import org.tsuyomi.core.display.DisplayPreference
import org.tsuyomi.core.display.DisplayPreferences
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.MotionPolicy
import org.tsuyomi.core.ui.theme.TsuyomiTheme
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.RemoteTarget
import org.tsuyomi.shared.sourcecontract.SourceBookSummary

@RunWith(AndroidJUnit4::class)
class RemoteLibraryScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun groupedMirrorUsesFolderFirstSharedLibrarySurfaceAndPinAction() {
        var pinned: Boolean? = null
        var openedTarget: String? = null
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
                        groupingEnabled = true,
                        mirrorPinned = false,
                        onToggleMirrorPinned = { pinned = true },
                        onOpenTarget = { openedTarget = it },
                    )
                }
            }
        }

        composeRule.onNodeWithTag("remote-library-folder-favorites").assertIsDisplayed().performClick()
        assertEquals("favorites", openedTarget)
        composeRule.onNodeWithTag("library-book-$SourceId-1").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("更多操作").performClick()
        composeRule.onNodeWithText("固定到快捷书架").assertIsDisplayed().performClick()
        assertEquals(true, pinned)
        composeRule.onNodeWithContentDescription("更多操作").performClick()
        composeRule.onNodeWithText("全部复制").assertIsDisplayed()
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
                        selectedIds = books.mapTo(linkedSetOf()) { it.canonicalUrl },
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

        composeRule.onNodeWithText("网站操作仅支持单本").assertIsDisplayed()
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
                        selectedIds = setOf(books.first().canonicalUrl),
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

        composeRule.onNodeWithContentDescription("更多操作").performClick()
        composeRule.onNodeWithText("移至网站分类").assertIsDisplayed().performClick()
        composeRule.onNodeWithContentDescription("更多操作").performClick()
        composeRule.onAllNodesWithText("从网站移除").filterToOne(hasClickAction()).performClick()
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
                        selectedIds = setOf(books.first().canonicalUrl),
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

        composeRule.onNodeWithContentDescription("更多操作").performClick()
        composeRule.onNodeWithText("移至网站分类").assertDoesNotExist()
        composeRule.onAllNodesWithText("从网站移除").filterToOne(hasClickAction()).assertIsDisplayed()
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

        composeRule.onNodeWithText("从远端书架移除").assertIsDisplayed()
        composeRule.onNodeWithText("确定要从远端书架移除《文学少女》吗？\n注意：远端删除仅影响网站书架，不会删除已保存在本地的数据。")
            .assertIsDisplayed()
        composeRule.onNodeWithText("移除").performClick()
        assertEquals("1", confirmedBookId)
    }

    private companion object {
        const val SourceId = "org.tsuyomi.wenku8"
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
