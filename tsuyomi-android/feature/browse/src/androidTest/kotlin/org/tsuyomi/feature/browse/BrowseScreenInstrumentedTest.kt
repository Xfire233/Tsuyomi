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
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.tsuyomi.core.display.DisplayDecisionReason
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.core.display.DisplayEnvironmentProvider
import org.tsuyomi.core.preferences.DisplayPreference
import org.tsuyomi.core.preferences.DisplayPreferences
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
                        onApproveInstall = { _, _, _ -> },
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
        composeRule.onNodeWithTag("browse-source-identity-org.tsuyomi.source.wenku8").performClick()
        assertEquals(
            listOf(BrowseSourceAction.OpenHome("org.tsuyomi.source.wenku8")),
            sourceActions,
        )

        composeRule.onNodeWithText("可安装").performClick()
        composeRule.onNodeWithText("无法更新官方来源目录").assertIsDisplayed()
        composeRule.onNodeWithText("网络不可用").assertDoesNotExist()
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
                            onApproveInstall = { _, _, _ -> },
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


    @Test
    fun installedSourcePrimaryEntryAndDisclosureActionsStaySeparate() {
        val sourceActions = mutableListOf<BrowseSourceAction>()
        val source = installedSource(
            sourceId = "org.tsuyomi.source.example",
            name = "示例来源",
            homeAvailable = true,
            remoteLibraryAvailable = true,
            verificationAvailable = true,
        )
        composeRule.setContent {
            DisplayEnvironmentProvider(standardEnvironment) {
                TsuyomiTheme {
                    BrowseScreen(
                        state = BrowseUiState.Empty,
                        installedSources = listOf(source),
                        catalog = BrowseCatalogState(),
                        onRequestImport = {},
                        onApproveInstall = { _, _, _ -> },
                        onDismissApproval = {},
                        onDismissFailure = {},
                        onCatalogAction = {},
                        onSourceAction = { action -> sourceActions.add(action) },
                    )
                }
            }
        }

        composeRule.onNodeWithText(source.summary).assertDoesNotExist()
        composeRule.onNodeWithText("网站收藏读取").assertDoesNotExist()
        composeRule.onNodeWithText(source.sourceId).assertDoesNotExist()

        composeRule.onNodeWithText("进入来源").performClick()
        composeRule.onNodeWithTag("browse-source-identity-${source.sourceId}").performClick()
        composeRule.onNodeWithContentDescription("更多 示例来源 操作").performClick()
        composeRule.onNodeWithText("搜索此来源").performClick()
        composeRule.onNodeWithText("搜索此来源").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("更多 示例来源 操作").performClick()
        composeRule.onNodeWithText("网站收藏").performClick()
        composeRule.onNodeWithContentDescription("更多 示例来源 操作").performClick()
        composeRule.onNodeWithText("来源信息").performClick()
        composeRule.onNodeWithText("来源 ID").assertIsDisplayed()
        composeRule.onNodeWithText(source.sourceId).assertIsDisplayed()
        composeRule.onNodeWithText("版本").assertIsDisplayed()
        composeRule.onNodeWithText(source.summary).assertIsDisplayed()
        composeRule.onNodeWithText("来源主页").assertIsDisplayed()
        composeRule.onNodeWithText("网站收藏读取").assertIsDisplayed()
        composeRule.onNodeWithText("登录验证").assertIsDisplayed()
        composeRule.onNodeWithText("关闭").performClick()
        assertEquals(
            listOf(
                BrowseSourceAction.OpenHome(source.sourceId),
                BrowseSourceAction.OpenHome(source.sourceId),
                BrowseSourceAction.Search(source.sourceId),
                BrowseSourceAction.OpenRemoteLibrary(source.sourceId),
            ),
            sourceActions,
        )
    }

    @Test
    fun searchOnlyInstalledSourceUsesSearchPrimaryAndOmitsDuplicateMenuAction() {
        val sourceActions = mutableListOf<BrowseSourceAction>()
        val source = installedSource(
            sourceId = "org.tsuyomi.source.search-only",
            name = "搜索来源",
            homeAvailable = false,
            remoteLibraryAvailable = true,
        )
        composeRule.setContent {
            DisplayEnvironmentProvider(standardEnvironment) {
                TsuyomiTheme {
                    BrowseScreen(
                        state = BrowseUiState.Empty,
                        installedSources = listOf(source),
                        catalog = BrowseCatalogState(),
                        onRequestImport = {},
                        onApproveInstall = { _, _, _ -> },
                        onDismissApproval = {},
                        onDismissFailure = {},
                        onCatalogAction = {},
                        onSourceAction = { action -> sourceActions.add(action) },
                    )
                }
            }
        }

        composeRule.onNodeWithText("进入来源").performClick()
        composeRule.onNodeWithTag("browse-source-identity-${source.sourceId}").performClick()
        composeRule.onNodeWithContentDescription("更多 搜索来源 操作").performClick()
        composeRule.onNodeWithText("搜索此来源").assertDoesNotExist()
        composeRule.onNodeWithText("网站收藏").performClick()
        composeRule.onNodeWithText("来源信息").assertDoesNotExist()
        assertEquals(
            listOf(
                BrowseSourceAction.Search(source.sourceId),
                BrowseSourceAction.Search(source.sourceId),
                BrowseSourceAction.OpenRemoteLibrary(source.sourceId),
            ),
            sourceActions,
        )
    }

    @Test
    fun installedSourceEntryWrapsWithIntactTargetsAtNarrowLargeFont() {
        val source = installedSource(
            sourceId = "org.tsuyomi.source.narrow",
            name = "窄屏来源",
            homeAvailable = false,
        )
        composeRule.setContent {
            DisplayEnvironmentProvider(standardEnvironment) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                    TsuyomiTheme {
                        BrowseScreen(
                            state = BrowseUiState.Empty,
                            installedSources = listOf(source),
                            catalog = BrowseCatalogState(),
                            onRequestImport = {},
                            onApproveInstall = { _, _, _ -> },
                            onDismissApproval = {},
                            onDismissFailure = {},
                            onCatalogAction = {},
                            onSourceAction = {},
                            modifier = Modifier.width(280.dp),
                        )
                    }
                }
            }
        }

        val identity = composeRule.onNodeWithTag("browse-source-identity-${source.sourceId}")
            .fetchSemanticsNode()
        val primary = composeRule.onNodeWithText("进入来源").fetchSemanticsNode()
        val disclosure = composeRule.onNodeWithContentDescription("更多 窄屏来源 操作").fetchSemanticsNode()
        val minimumTouchSize = with(primary.layoutInfo.density) { 48.dp.toPx() }
        assertTrue(primary.touchBoundsInRoot.width >= minimumTouchSize)
        assertTrue(primary.touchBoundsInRoot.height >= minimumTouchSize)
        assertTrue(disclosure.touchBoundsInRoot.width >= minimumTouchSize)
        assertTrue(disclosure.touchBoundsInRoot.height >= minimumTouchSize)
        assertTrue(primary.boundsInRoot.top >= identity.boundsInRoot.bottom)
    }

    @Test
    fun approvalCheckboxRowsToggleFromTheirLabelsAndGateInstallation() {
        val approvals = mutableListOf<Pair<Boolean, Boolean>>()
        composeRule.setContent {
            DisplayEnvironmentProvider(standardEnvironment) {
                TsuyomiTheme {
                    BrowseScreen(
                        state = BrowseUiState.Approval(
                            sourceName = "迁移来源",
                            sourceId = "org.tsuyomi.source.migration",
                            version = "2.0.0",
                            publisherFingerprint = "ab".repeat(32),
                            capabilities = emptyList(),
                            resourceLimitIncreases = emptyList(),
                            isDowngrade = true,
                            isLegacyMigration = true,
                        ),
                        installedSources = emptyList(),
                        catalog = BrowseCatalogState(),
                        onRequestImport = {},
                        onApproveInstall = { downgrade, migration, _ -> approvals += downgrade to migration },
                        onDismissApproval = {},
                        onDismissFailure = {},
                        onCatalogAction = {},
                        onSourceAction = {},
                    )
                }
            }
        }

        val downgradeLabel = "我确认安装较低版本，并接受其可能不兼容现有数据。"
        val migrationLabel = "我确认迁移到新的发布者身份；已保留的本地数据不会被删除。"
        composeRule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
            .assertCountEquals(2)
        composeRule.onNodeWithText("确认安装").assertIsNotEnabled()
        val downgrade = composeRule.onNodeWithText(downgradeLabel)
        val migration = composeRule.onNodeWithText(migrationLabel)
        downgrade.assertHasClickAction().assertIsOff()
            .performTouchInput { click(percentOffset(0.9f, 0.5f)) }
        downgrade.assertIsOn()
        composeRule.onNodeWithText("确认安装").assertIsNotEnabled()
        migration.assertHasClickAction().assertIsOff()
            .performTouchInput { click(percentOffset(0.9f, 0.5f)) }
        migration.assertIsOn()
        composeRule.onNodeWithText("确认安装").assertIsEnabled()
        migration.performClick().assertIsOff()
        composeRule.onNodeWithText("确认安装").assertIsNotEnabled()
        migration.performClick().assertIsOn()
        composeRule.onNodeWithText("确认安装").performClick()
        assertEquals(listOf(true to true), approvals)
    }

    @Test
    fun uninstallRequiresConfirmationAndCanBeCancelled() {
        val source = installedSource(
            sourceId = "org.tsuyomi.source.removable",
            name = "可卸载来源",
            homeAvailable = false,
        )
        val actions = mutableListOf<BrowseSourceAction>()
        composeRule.setContent {
            DisplayEnvironmentProvider(standardEnvironment) {
                TsuyomiTheme {
                    BrowseScreen(
                        state = BrowseUiState.Empty,
                        installedSources = listOf(source),
                        catalog = BrowseCatalogState(),
                        onRequestImport = {},
                        onApproveInstall = { _, _, _ -> },
                        onDismissApproval = {},
                        onDismissFailure = {},
                        onCatalogAction = {},
                        onSourceAction = { action -> actions.add(action) },
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("更多 可卸载来源 操作").performClick()
        composeRule.onNodeWithText("卸载来源").performClick()
        composeRule.onNodeWithText("取消").performClick()
        assertEquals(emptyList<BrowseSourceAction>(), actions)

        composeRule.onNodeWithContentDescription("更多 可卸载来源 操作").performClick()
        composeRule.onNodeWithText("卸载来源").performClick()
        composeRule.onNodeWithText("卸载来源").performClick()
        assertEquals(listOf(BrowseSourceAction.Uninstall(source.sourceId)), actions)
    }

    @Test
    fun nonOfficialApprovalRequiresExplicitConsentBeforeInstallation() {
        val approvals = mutableListOf<Triple<Boolean, Boolean, Boolean>>()
        composeRule.setContent {
            DisplayEnvironmentProvider(standardEnvironment) {
                TsuyomiTheme {
                    BrowseScreen(
                        state = BrowseUiState.Approval(
                            sourceName = "第三方来源",
                            sourceId = "org.tsuyomi.source.third-party",
                            version = "1.0.0",
                            publisherFingerprint = "ab".repeat(32),
                            capabilities = emptyList(),
                            resourceLimitIncreases = emptyList(),
                            isDowngrade = false,
                            requiresNonOfficialConsent = true,
                        ),
                        installedSources = emptyList(),
                        catalog = BrowseCatalogState(),
                        onRequestImport = {},
                        onApproveInstall = { downgrade, migration, nonOfficial ->
                            approvals += Triple(downgrade, migration, nonOfficial)
                        },
                        onDismissApproval = {},
                        onDismissFailure = {},
                        onCatalogAction = {},
                        onSourceAction = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("确认安装").assertIsNotEnabled()
        composeRule.onNodeWithTag("browse-approval-nonofficial").assertIsOff().performClick().assertIsOn()
        composeRule.onNodeWithText("确认安装").assertIsEnabled().performClick()
        assertEquals(listOf(Triple(false, false, true)), approvals)
    }

    @Test
    fun publisherKeyIsSubmittedForVerificationWithoutApprovingInstallation() {
        val providedKeys = mutableListOf<String>()
        val approvals = mutableListOf<Triple<Boolean, Boolean, Boolean>>()
        val rawPublicKey = "ab".repeat(32)
        composeRule.setContent {
            DisplayEnvironmentProvider(standardEnvironment) {
                TsuyomiTheme {
                    BrowseScreen(
                        state = BrowseUiState.PublisherKeyRequired(
                            keyId = "publisher-key-7",
                            problem = "未配置此发布者公钥",
                        ),
                        installedSources = emptyList(),
                        catalog = BrowseCatalogState(),
                        onRequestImport = {},
                        onApproveInstall = { downgrade, migration, nonOfficial ->
                            approvals += Triple(downgrade, migration, nonOfficial)
                        },
                        onDismissApproval = {},
                        onDismissFailure = {},
                        onCatalogAction = {},
                        onSourceAction = {},
                        onProvidePublisherKey = { key -> providedKeys += key },
                    )
                }
            }
        }

        composeRule.onNodeWithTag("browse-publisher-key-verify").assertIsNotEnabled()
        composeRule.onNode(hasSetTextAction()).performTextInput(rawPublicKey)
        composeRule.onNodeWithTag("browse-publisher-key-verify").assertIsEnabled().performClick()
        assertEquals(listOf(rawPublicKey), providedKeys)
        assertEquals(emptyList<Triple<Boolean, Boolean, Boolean>>(), approvals)
    }

    @Test
    fun repositoryProvenanceKeepsCollidingOffersDistinctAndAvailableOffersInstallable() {
        val actions = mutableListOf<BrowseCatalogAction>()
        val sourceId = "org.tsuyomi.source.shared"
        val official = catalogItem(
            sourceId = sourceId,
            name = "官方同名来源",
            repositoryId = "official-repository",
            repositoryName = "官方书库",
            official = true,
        )
        val unavailableThirdParty = catalogItem(
            sourceId = sourceId,
            name = "第三方同名来源",
            repositoryId = "third-party-repository",
            repositoryName = "合作书库",
            official = false,
            installable = false,
        )
        composeRule.setContent {
            DisplayEnvironmentProvider(standardEnvironment) {
                TsuyomiTheme {
                    BrowseScreen(
                        state = BrowseUiState.Empty,
                        installedSources = emptyList(),
                        catalog = BrowseCatalogState(
                            status = BrowseCatalogStatus.READY,
                            items = listOf(official, unavailableThirdParty),
                        ),
                        onRequestImport = {},
                        onApproveInstall = { _, _, _ -> },
                        onDismissApproval = {},
                        onDismissFailure = {},
                        onCatalogAction = { action -> actions.add(action) },
                        onSourceAction = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("可安装").performClick()
        composeRule.onNodeWithText(official.repositoryName, substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(unavailableThirdParty.repositoryName, substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("browse-catalog-install-third-party-repository-$sourceId")
            .assertIsNotEnabled()
        composeRule.onNodeWithTag("browse-catalog-install-official-repository-$sourceId").performClick()
        assertEquals(listOf(BrowseCatalogAction.Install(sourceId, "official-repository")), actions)
    }

    @Test
    fun repositoryFailureRoutesRetryAndReturnToCatalogWithoutOpeningPicker() {
        val action = BrowseCatalogAction.Install(
            sourceId = "org.tsuyomi.source.retry",
            repositoryId = "trusted-repository",
        )
        val catalogActions = mutableListOf<BrowseCatalogAction>()
        var pickerRequests = 0
        var failureDismissals = 0
        var state by mutableStateOf<BrowseUiState>(
            BrowseUiState.Failure(BrowseInstallFailure.DOWNLOAD, action),
        )
        composeRule.setContent {
            DisplayEnvironmentProvider(standardEnvironment) {
                TsuyomiTheme {
                    BrowseScreen(
                        state = state,
                        installedSources = emptyList(),
                        catalog = BrowseCatalogState(),
                        onRequestImport = { pickerRequests += 1 },
                        onApproveInstall = { _, _, _ -> },
                        onDismissApproval = {},
                        onDismissFailure = { failureDismissals += 1 },
                        onCatalogAction = { catalogActions += it },
                        onSourceAction = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("重试下载").performClick()
        assertEquals(listOf(action), catalogActions)
        assertEquals(0, pickerRequests)
        assertEquals(0, failureDismissals)

        composeRule.runOnIdle {
            state = BrowseUiState.Failure(BrowseInstallFailure.VERIFICATION, action)
        }
        composeRule.onNodeWithText("返回目录").performClick()
        assertEquals(0, pickerRequests)
        assertEquals(1, failureDismissals)
    }

    @Test
    fun subscriptionInspectionRequiresExplicitConfirmationAndCanBeCancelled() {
        val link = "https://example.invalid/repository#candidate"
        val rootFingerprint = "ab".repeat(32)
        val actions = mutableListOf<BrowseCatalogAction>()
        var catalog by mutableStateOf(
            BrowseCatalogState(
                status = BrowseCatalogStatus.READY,
                repositories = listOf(
                    BrowseRepository(
                        id = "official",
                        name = "官方书库",
                        indexUrl = "https://official.example.invalid/index.json",
                        rootFingerprint = "cd".repeat(32),
                        official = true,
                        enabled = true,
                    ),
                ),
            ),
        )
        composeRule.setContent {
            DisplayEnvironmentProvider(standardEnvironment) {
                TsuyomiTheme {
                    BrowseScreen(
                        state = BrowseUiState.Empty,
                        installedSources = emptyList(),
                        catalog = catalog,
                        onRequestImport = {},
                        onApproveInstall = { _, _, _ -> },
                        onDismissApproval = {},
                        onDismissFailure = {},
                        onCatalogAction = { action -> actions.add(action) },
                        onSourceAction = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("可安装").performClick()
        composeRule.onNodeWithTag("browse-repository-manage").performClick()
        composeRule.onNodeWithTag("browse-subscription-link").performScrollTo().performTextInput(link)
        composeRule.onNodeWithTag("browse-subscription-inspect").performScrollTo().performClick()
        assertEquals(listOf(BrowseCatalogAction.InspectSubscription(link)), actions)

        composeRule.runOnIdle {
            catalog = catalog.copy(
                subscription = BrowseSubscriptionState(
                    link = link,
                    repositoryId = "example",
                    indexUrl = "https://example.invalid/repository/index.json",
                    rootFingerprint = rootFingerprint,
                ),
            )
        }
        composeRule.onNodeWithText(rootFingerprint).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("browse-subscription-confirm").performScrollTo().performClick()
        composeRule.onNodeWithTag("browse-subscription-cancel").performScrollTo().performClick()
        assertEquals(
            listOf(
                BrowseCatalogAction.InspectSubscription(link),
                BrowseCatalogAction.ConfirmSubscription,
                BrowseCatalogAction.CancelSubscription,
            ),
            actions,
        )
    }

    @Test
    fun repository_removal_uses_one_modal_and_returns_to_manager_after_each_outcome() {
        val actions = mutableListOf<BrowseCatalogAction>()
        var catalog by mutableStateOf(
            BrowseCatalogState(
                status = BrowseCatalogStatus.READY,
                repositories = listOf(
                    BrowseRepository(
                        id = "third-party",
                        name = "测试仓库",
                        indexUrl = "https://example.invalid/index.json",
                        rootFingerprint = "ab".repeat(32),
                        official = false,
                        enabled = true,
                    ),
                ),
            ),
        )
        composeRule.setContent {
            DisplayEnvironmentProvider(standardEnvironment) {
                TsuyomiTheme {
                    BrowseScreen(
                        state = BrowseUiState.Empty,
                        installedSources = emptyList(),
                        catalog = catalog,
                        onRequestImport = {},
                        onApproveInstall = { _, _, _ -> },
                        onDismissApproval = {},
                        onDismissFailure = {},
                        onCatalogAction = { action ->
                            actions += action
                            if (action is BrowseCatalogAction.RemoveSubscription) {
                                catalog = catalog.copy(
                                    repositories = catalog.repositories.filterNot { it.id == action.repositoryId },
                                )
                            }
                        },
                        onSourceAction = {},
                    )
                }
            }
        }
        composeRule.onNodeWithText("测试仓库").assertDoesNotExist()
        composeRule.onNodeWithText("可安装").performClick()
        composeRule.onNodeWithTag("browse-repository-manage").performClick()
        composeRule.onNodeWithTag("browse-repository-remove-third-party").performClick()
        composeRule.onNodeWithText("测试仓库").assertDoesNotExist()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("取消").fetchSemanticsNodes().any { node ->
                SemanticsProperties.Focused in node.config && node.config[SemanticsProperties.Focused]
            }
        }
        composeRule.onNodeWithText("取消").assertIsFocused()
        composeRule.onNodeWithText("取消").performKeyInput { pressKey(Key.Enter) }
        composeRule.onNodeWithText("测试仓库").assertIsDisplayed()

        composeRule.onNodeWithTag("browse-repository-remove-third-party").performClick()
        composeRule.onNodeWithText("移除仓库").performClick()
        composeRule.onNodeWithText("关闭").assertIsDisplayed()
        assertEquals(listOf(BrowseCatalogAction.RemoveSubscription("third-party")), actions)
    }

    private companion object {
        fun installedSource(
            sourceId: String,
            name: String,
            homeAvailable: Boolean,
            remoteLibraryAvailable: Boolean = false,
            verificationAvailable: Boolean = false,
        ) = BrowseInstalledSource(
            sourceId = sourceId,
            name = name,
            version = "1.0.0",
            summary = "完整来源简介，仅在来源信息中显示。",
            homeAvailable = homeAvailable,
            remoteLibraryAvailable = remoteLibraryAvailable,
            verificationAvailable = verificationAvailable,
        )

        fun catalogItem(
            sourceId: String,
            name: String,
            repositoryId: String,
            repositoryName: String,
            official: Boolean,
            installable: Boolean = true,
        ) = BrowseCatalogItem(
            sourceId = sourceId,
            name = name,
            version = "1.0.0",
            summary = "用于验证仓库来源的测试来源。",
            language = "中文",
            license = "Apache-2.0",
            sourceUrl = "https://example.invalid/source",
            sourceRevision = "0123456789abcdef0123456789abcdef01234567",
            publisherFingerprint = "ab".repeat(32),
            compatible = true,
            installedVersion = null,
            updateAvailable = false,
            repositoryId = repositoryId,
            repositoryName = repositoryName,
            official = official,
            installable = installable,
        )

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
