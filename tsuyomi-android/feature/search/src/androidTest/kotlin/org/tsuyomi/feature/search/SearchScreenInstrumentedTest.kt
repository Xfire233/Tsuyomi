/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.search

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.tsuyomi.core.display.DisplayDecisionReason
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.core.display.DisplayEnvironmentProvider
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.MotionPolicy
import org.tsuyomi.core.preferences.DisplayPreference
import org.tsuyomi.core.preferences.DisplayPreferences
import org.tsuyomi.core.ui.theme.TsuyomiTheme
import org.tsuyomi.core.ui.components.CoverCardPresentationProvider
import org.tsuyomi.shared.model.CoverCardPresentation

import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.SourceBookSummary

class SearchScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun listCoverContainsTheCompleteTextStackAtLargeFonts() {
        val fontScale = mutableStateOf(2f)
        val book = SourceBookSummary(
            identity = BookIdentity("source.test", "long-title"),
            title = "这是一本在大字号下仍须保持封面和文字边界的很长书名 A long title",
            author = "作者 Author",
            coverUrl = null,
            canonicalUrl = "https://example.test/book",
        )
        var selected: BookIdentity? = null
        val environment = DisplayEnvironment(
            preferences = DisplayPreferences(displayPreference = DisplayPreference.STANDARD),
            effectiveProfile = DisplayProfile.STANDARD,
            decisionReason = DisplayDecisionReason.MANUAL_STANDARD,
            detectedDeviceLabel = null,
            dynamicColorEligible = false,
            dynamicColorEffective = false,
            effectiveDarkTheme = false,
            motionPolicy = MotionPolicy.STANDARD,
            redrawEpoch = 0L,
        )
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(412.dp, 900.dp))) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale.value)) {
                    DisplayEnvironmentProvider(environment) {
                        TsuyomiTheme(environment) {
                            SearchScreen(
                                query = "book",
                                state = SearchResultState.Results(listOf(book)),
                                layout = SearchLayout.LIST,
                                onQueryChange = {},
                                onSearch = {},
                                onSelectBook = { selected = it.identity },
                                onRetry = {},
                                onUseOfflineCache = {},
                                onOpenVerification = {},
                            )
                        }
                    }
                }
            }
        }
        fun assertTextBounds() {
            val cover = composeRule.onNodeWithTag("search-list-cover-source.test:long-title", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val title = composeRule.onNodeWithText(book.title, useUnmergedTree = true)
            val layout = mutableListOf<TextLayoutResult>()
            title.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
            assertTrue(layout.single().lineCount <= 2)
            val titleBounds = title.fetchSemanticsNode().boundsInRoot
            val sourceBounds = composeRule.onNodeWithText("source.test", substring = true, useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            assertTrue("Title crosses cover top: $titleBounds versus $cover", titleBounds.top >= cover.top - 1f)
            assertTrue("Source crosses cover bottom: $sourceBounds versus $cover", sourceBounds.bottom <= cover.bottom + 1f)
            assertEquals(5f / 7f, cover.width / cover.height, 0.01f)
        }
        assertTextBounds()
        composeRule.runOnIdle { fontScale.value = 1f }
        assertTextBounds()
        composeRule.onNodeWithText(book.title).performClick()
        composeRule.runOnIdle { assertEquals(book.identity, selected) }
    }

    @Test
    fun gridCardsFollowTheSelectedSharedCoverPresentation() {
        val presentation = mutableStateOf(CoverCardPresentation.STANDARD)
        val book = SourceBookSummary(
            identity = BookIdentity("source.test", "grid-book"),
            title = "网格书籍",
            author = "作者",
            coverUrl = null,
            canonicalUrl = "https://example.test/grid-book",
        )
        val environment = DisplayEnvironment(
            preferences = DisplayPreferences(displayPreference = DisplayPreference.STANDARD),
            effectiveProfile = DisplayProfile.STANDARD,
            decisionReason = DisplayDecisionReason.MANUAL_STANDARD,
            detectedDeviceLabel = null,
            dynamicColorEligible = false,
            dynamicColorEffective = false,
            effectiveDarkTheme = false,
            motionPolicy = MotionPolicy.STANDARD,
            redrawEpoch = 0L,
        )
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                CoverCardPresentationProvider(presentation.value) {
                    TsuyomiTheme(environment) {
                        SearchScreen(
                            query = "grid",
                            state = SearchResultState.Results(listOf(book)),
                            layout = SearchLayout.GRID,
                            onQueryChange = {},
                            onSearch = {},
                            onSelectBook = {},
                            onRetry = {},
                            onUseOfflineCache = {},
                            onOpenVerification = {},
                        )
                    }
                }
            }
        }

        fun cardRatio(): Float {
            val bounds = composeRule.onNodeWithTag("search-grid-source.test:grid-book")
                .fetchSemanticsNode().boundsInRoot
            return bounds.width / bounds.height
        }

        assertEquals(5f / 7f, cardRatio(), 0.01f)
        composeRule.runOnIdle { presentation.value = CoverCardPresentation.WIDE }
        assertEquals(16f / 9f, cardRatio(), 0.01f)
    }

    @Test
    fun loadingSearchDisablesTheOnlySubmitAction() {
        val environment = DisplayEnvironment(
            preferences = DisplayPreferences(displayPreference = DisplayPreference.STANDARD),
            effectiveProfile = DisplayProfile.STANDARD,
            decisionReason = DisplayDecisionReason.MANUAL_STANDARD,
            detectedDeviceLabel = null,
            dynamicColorEligible = false,
            dynamicColorEffective = false,
            effectiveDarkTheme = false,
            motionPolicy = MotionPolicy.STANDARD,
            redrawEpoch = 0L,
        )
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    SearchScreen(
                        query = "进行中的搜索",
                        state = SearchResultState.Loading,
                        layout = SearchLayout.LIST,
                        onQueryChange = {},
                        onSearch = {},
                        onSelectBook = {},
                        onRetry = {},
                        onUseOfflineCache = {},
                        onOpenVerification = {},
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("提交搜索").assertIsNotEnabled()
    }
}
