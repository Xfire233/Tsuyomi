/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.browse

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.tsuyomi.core.display.DisplayDecisionReason
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.core.display.DisplayEnvironmentProvider
import org.tsuyomi.core.display.DisplayPreference
import org.tsuyomi.core.display.DisplayPreferences
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.MotionPolicy
import org.tsuyomi.core.ui.theme.TsuyomiTheme

class BrowseScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun catalogErrorKeepsInstalledSourcesAndReachableActions() {
        val sourceActions = mutableListOf<BrowseSourceAction>()
        val catalogActions = mutableListOf<BrowseCatalogAction>()
        composeRule.setContent {
            DisplayEnvironmentProvider(standardEnvironment) {
                MaterialTheme {
                    BrowseScreen(
                        state = BrowseUiState.Empty,
                        installedSources = listOf(
                            BrowseInstalledSource(
                                sourceId = "org.tsuyomi.source.wenku8",
                                name = "Wenku8",
                                version = "0.2.26",
                                summary = "轻小说来源",
                                homeAvailable = true,
                                remoteLibraryAvailable = true,
                                verificationAvailable = true,
                            ),
                            BrowseInstalledSource(
                                sourceId = "org.tsuyomi.source.yamibo",
                                name = "Yamibo",
                                version = "0.1.0",
                                summary = "百合小说来源",
                                homeAvailable = false,
                                remoteLibraryAvailable = false,
                                verificationAvailable = false,
                            ),
                        ),
                        catalog = BrowseCatalogState(
                            status = BrowseCatalogStatus.ERROR,
                            stale = true,
                            problem = "网络不可用",
                            items = listOf(
                                BrowseCatalogItem(
                                    sourceId = "org.tsuyomi.source.catalog-example",
                                    name = "测试来源",
                                    version = "1.2.0",
                                    summary = "用于回归目录状态的来源",
                                    language = "中文",
                                    license = "AGPL-3.0-only",
                                    sourceUrl = "https://example.invalid/source",
                                    sourceRevision = "0123456789abcdef0123456789abcdef01234567",
                                    publisherFingerprint = "a1b2c3d4",
                                    compatible = true,
                                    installedVersion = null,
                                    updateAvailable = false,
                                ),
                            ),
                        ),
                        onRequestImport = {},
                        onApproveInstall = { _, _ -> },
                        onDismissApproval = {},
                        onDismissFailure = {},
                        onCatalogAction = { action -> catalogActions.add(action) },
                        onSourceAction = { action -> sourceActions.add(action) },
                    )
                }
            }
        }

        composeRule.onNodeWithText("Wenku8").assertIsDisplayed()
        composeRule.onNodeWithText("Yamibo").assertIsDisplayed()
        composeRule.onNodeWithTag("browse-source-search-org.tsuyomi.source.wenku8").performClick()
        assertEquals(
            listOf(BrowseSourceAction.Search("org.tsuyomi.source.wenku8")),
            sourceActions,
        )

        composeRule.onNodeWithText("可安装").performClick()
        composeRule.onNodeWithText("无法更新官方来源目录").assertIsDisplayed()
        composeRule.onNodeWithText("安装").assertIsNotEnabled()
        composeRule.onNodeWithTag("browse-catalog-search").performTextInput("不存在")
        composeRule.onNodeWithText("没有匹配的来源。").assertIsDisplayed()
        composeRule.onNodeWithTag("browse-catalog-search").performTextClearance()
        composeRule.onNodeWithTag("browse-catalog-search").performTextInput("测试")
        composeRule.onNodeWithText("测试来源").performClick()
        composeRule.onNodeWithText("发布者指纹").assertIsDisplayed()
        composeRule.onNodeWithText("查看源代码").performClick()
        composeRule.onNodeWithText("关闭").performClick()
        composeRule.onNodeWithText("重新加载目录").performClick()
        assertEquals(
            listOf(
                BrowseCatalogAction.OpenSourceCode("https://example.invalid/source"),
                BrowseCatalogAction.Refresh,
            ),
            catalogActions,
        )

        composeRule.onNodeWithText("已安装").performClick()
        composeRule.onNodeWithText("Wenku8").assertIsDisplayed()
        composeRule.onNodeWithText("Yamibo").assertIsDisplayed()
    }

    @Test
    fun maximumCatalogRemainsSearchableAndRestoresCancelledApproval() {
        val restoration = StateRestorationTester(composeRule)
        val summary = "A detailed source summary. ".repeat(32)
        val catalog = BrowseCatalogState(
            status = BrowseCatalogStatus.READY,
            items = List(512) { index ->
                BrowseCatalogItem(
                    sourceId = "org.tsuyomi.source.source$index",
                    name = "Source $index",
                    version = "1.2.0",
                    summary = summary,
                    language = "中文",
                    license = "AGPL-3.0-only",
                    sourceUrl = "https://example.invalid/source/$index",
                    sourceRevision = "0123456789abcdef0123456789abcdef01234567",
                    publisherFingerprint = "ab".repeat(32),
                    compatible = true,
                    installedVersion = null,
                    updateAvailable = false,
                )
            },
        )
        var state by mutableStateOf<BrowseUiState>(BrowseUiState.Empty)
        restoration.setContent {
            DisplayEnvironmentProvider(standardEnvironment) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                    TsuyomiTheme {
                        BrowseScreen(
                            state = state,
                            installedSources = emptyList(),
                            catalog = catalog,
                            onRequestImport = {},
                            onApproveInstall = { _, _ -> },
                            onDismissApproval = { state = BrowseUiState.Empty },
                            onDismissFailure = {},
                            onCatalogAction = { action ->
                                if (action is BrowseCatalogAction.Install) {
                                    state = BrowseUiState.Approval(
                                        sourceName = "Source 511",
                                        sourceId = action.sourceId,
                                        version = "1.2.0",
                                        publisherFingerprint = "ab".repeat(32),
                                        capabilities = emptyList(),
                                        resourceLimitIncreases = emptyList(),
                                        isDowngrade = false,
                                    )
                                }
                            },
                            onSourceAction = {},
                            modifier = Modifier.width(320.dp),
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("可安装").performClick()
        composeRule.onNodeWithText("Source 1").assertIsDisplayed()
        composeRule.onNodeWithTag("browse-catalog-search").performClick()
        composeRule.onNodeWithTag("browse-catalog-search").performTextInput("source511")
        composeRule.onNodeWithText("Source 511").assertIsDisplayed().performClick()
        restoration.emulateSavedInstanceStateRestore()
        val sourceLink = composeRule.onNodeWithText("查看源代码").assertIsDisplayed().fetchSemanticsNode()
        val minimumTouchSize = with(sourceLink.layoutInfo.density) { 48.dp.toPx() }
        assertTrue(sourceLink.touchBoundsInRoot.height >= minimumTouchSize)
        assertTrue(sourceLink.touchBoundsInRoot.width >= minimumTouchSize)
        composeRule.onNodeWithText("https://example.invalid/source/511").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("关闭").performClick()
        composeRule.onNodeWithText("安装").performScrollTo().performClick()
        composeRule.onNodeWithText("取消").performScrollTo().performClick()
        composeRule.onNodeWithText("已安装").performClick()
        composeRule.onNodeWithText("可安装").performClick()
        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithTag("browse-catalog-search").assertTextContains("source511")
        composeRule.onNodeWithText("Source 511").assertIsDisplayed()
    }


    private companion object {
        val standardEnvironment = DisplayEnvironment(
            preferences = DisplayPreferences(displayPreference = DisplayPreference.STANDARD),
            effectiveProfile = DisplayProfile.STANDARD,
            decisionReason = DisplayDecisionReason.MANUAL_STANDARD,
            detectedDeviceLabel = null,
            dynamicColorEligible = false,
            dynamicColorEffective = false,
            effectiveDarkTheme = false,
            motionPolicy = MotionPolicy.STANDARD,
            redrawEpoch = 0,
        )
    }
}
