/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.core.display.DisplayDecisionReason
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.core.display.DisplayEnvironmentProvider
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.MotionPolicy
import org.tsuyomi.core.preferences.ColorSchemePreference
import org.tsuyomi.core.preferences.DisplayPreference
import org.tsuyomi.core.preferences.DisplayPreferences
import org.tsuyomi.core.ui.theme.TsuyomiTheme
import org.tsuyomi.shared.librarydomain.LibraryBook
import org.tsuyomi.shared.librarydomain.LibraryEntry
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.librarydomain.LocalPublicationFormat
import org.tsuyomi.shared.librarydomain.LocalPublicationMetadata

@RunWith(AndroidJUnit4::class)
class LibraryTagsScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun compactLocalTagsNormalizeAndDeduplicateWithoutPublishingCounts() {
        var destination by mutableStateOf<LibraryTagDestination?>(null)
        composeRule.setContent {
            Screen(
                entries = listOf(
                    entry("local-1", localTags = setOf("Ｆｏｏ")),
                    entry("local-2", localTags = setOf(" foo ")),
                ),
                layout = LibraryTagLayout.CHIPS,
                onOpenTag = { destination = it },
            )
        }

        composeRule.onNodeWithTag("library-tag-LOCAL--foo").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("2 本").assertDoesNotExist()
        assertEquals(
            LibraryTagDestination(LibraryTagOwnership.LOCAL, "foo"),
            destination,
        )
    }

    @Test
    fun sourceTagRowsKeepSourceIdentityAndPublishOnlyListCounts() {
        var destination by mutableStateOf<LibraryTagDestination?>(null)
        composeRule.setContent {
            Screen(
                entries = listOf(
                    entry("source-a-1", sourceId = "source.a", remoteTags = setOf("奇幻")),
                    entry("source-a-2", sourceId = "source.a", remoteTags = setOf("奇幻")),
                    entry("source-b-1", sourceId = "source.b", remoteTags = setOf("奇幻")),
                ),
                layout = LibraryTagLayout.LIST,
                sourceLabels = mapOf("source.a" to "来源甲", "source.b" to "来源乙"),
                onOpenTag = { destination = it },
            )
        }

        composeRule.onNodeWithTag("tsuyomi-tab-SOURCE").performClick()
        composeRule.onNodeWithText("2 本").assertIsDisplayed()
        composeRule.onNodeWithText("1 本").assertIsDisplayed()
        composeRule.onNodeWithTag("library-tag-SOURCE-source.a-奇幻").performClick()
        assertEquals(
            LibraryTagDestination(LibraryTagOwnership.SOURCE, "奇幻", "source.a"),
            destination,
        )
    }

    @Test
    fun einkSourceTagViewExcludesRemoteTagsFromLocalPublications() {
        val local = LibraryEntry(
            book = LibraryBook(
                identity = BookIdentity.Local("00000000-0000-4000-8000-000000000003"),
                title = "离线书籍",
                addedAt = Instant.EPOCH,
                metadataUpdatedAt = Instant.EPOCH,
                remoteTags = setOf("不应成为来源标签"),
                localPublication = LocalPublicationMetadata(
                    format = LocalPublicationFormat.EPUB,
                    originalSha256 = "b".repeat(64),
                    originalSizeBytes = 2,
                    originalAvailable = true,
                ),
            ),
            libraryAddedAt = Instant.EPOCH,
            rating = null,
            localTags = setOf("个人标签"),
            sourceAvailable = false,
            reconciliation = null,
        )

        composeRule.setContent {
            Screen(
                entries = listOf(local),
                layout = LibraryTagLayout.CHIPS,
                onOpenTag = {},
                displayEnvironment = einkEnvironment,
            )
        }

        composeRule.onNodeWithTag("library-tag-LOCAL--个人标签").assertIsDisplayed()
        composeRule.onNodeWithTag("tsuyomi-tab-SOURCE").performClick()
        composeRule.onNodeWithText("不应成为来源标签").assertDoesNotExist()
        composeRule.onNodeWithText("还没有来源标签").assertIsDisplayed()
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun einkTagPageSurvivesProfileChangeWithoutChangingTheOpenedTag() {
        var currentEnvironment by mutableStateOf(einkEnvironment)
        var destination: LibraryTagDestination? = null
        val entries = (0..24).map { index ->
            entry("tag-book-$index", localTags = setOf("topic-${index.toString().padStart(2, '0')}"))
        }
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 480.dp))) {
                Screen(entries, LibraryTagLayout.LIST, onOpenTag = { destination = it }, displayEnvironment = currentEnvironment)
            }
        }
        val target = composeRule.onNodeWithTag("library-tag-LOCAL--topic-15")
        val contentPager = hasAnyAncestor(hasTestTag("library-tags-eink-pagination"))
        val first = composeRule.onNode(hasText("首页") and contentPager)
        val last = composeRule.onNode(hasText("末页") and contentPager)
        val status = composeRule.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription) and contentPager,
        )
        val initialStatus = status.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        val pageCount = requireNotNull(Regex("""第 1 页，共 (\d+) 页""").matchEntire(initialStatus)) {
            "Expected a known initial tag page count, got $initialStatus"
        }.groupValues[1].toInt()
        assertTrue("Expected multiple measured tag pages", pageCount > 1)
        // Endpoint jumps depend on measured pages, not the number of tags.
        val showEndpoints = pageCount > 5
        if (showEndpoints) {
            first.assertIsNotEnabled()
            last.assertIsEnabled().performClick()
        } else {
            first.assertDoesNotExist()
            last.assertDoesNotExist()
            repeat(pageCount - 1) {
                composeRule.onNode(hasText("下一页") and contentPager).assertIsEnabled().performClick()
            }
        }
        composeRule.onNodeWithTag("library-tag-LOCAL--topic-24").assertIsDisplayed()
        assertEquals("第 $pageCount 页，共 $pageCount 页", status.fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        if (showEndpoints) last.assertIsNotEnabled()
        composeRule.onNode(hasText("下一页") and contentPager).assertIsNotEnabled()
        if (showEndpoints) {
            first.assertIsEnabled().performClick()
        } else {
            repeat(pageCount - 1) {
                composeRule.onNode(hasText("上一页") and contentPager).assertIsEnabled().performClick()
            }
        }
        composeRule.onNodeWithTag("library-tag-LOCAL--topic-00").assertIsDisplayed()
        assertEquals(initialStatus, status.fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        if (showEndpoints) first.assertIsNotEnabled()
        composeRule.onNode(hasText("上一页") and contentPager).assertIsNotEnabled()
        var pages = 0
        while (!target.isDisplayed()) {
            composeRule.onNode(hasText("下一页") and contentPager).assertIsEnabled().performClick()
            assertTrue("Selected tag was unreachable", ++pages < entries.size * 3)
        }
        composeRule.runOnIdle { currentEnvironment = environment }
        composeRule.waitForIdle()
        composeRule.runOnIdle { currentEnvironment = einkEnvironment }
        composeRule.waitForIdle()
        target.assertIsDisplayed().performClick()
        assertEquals(LibraryTagDestination(LibraryTagOwnership.LOCAL, "topic-15"), destination)
    }

    @Composable
    private fun Screen(
        entries: List<LibraryEntry>,
        layout: LibraryTagLayout,
        sourceLabels: Map<String, String> = emptyMap(),
        onOpenTag: (LibraryTagDestination) -> Unit,
        displayEnvironment: DisplayEnvironment = environment,
    ) {
        DisplayEnvironmentProvider(displayEnvironment) {
            TsuyomiTheme(displayEnvironment) {
                LibraryTagsScreen(
                    entries = entries,
                    sourceLabels = sourceLabels,
                    layout = layout,
                    onOpenTag = onOpenTag,
                )
            }
        }
    }


    private fun entry(
        id: String,
        sourceId: String = "fixture.tags",
        localTags: Set<String> = emptySet(),
        remoteTags: Set<String> = emptySet(),
    ): LibraryEntry = LibraryEntry(
        book = LibraryBook(
            identity = BookIdentity.Remote(sourceId, id),
            title = id,
            addedAt = Instant.EPOCH,
            metadataUpdatedAt = Instant.EPOCH,
            remoteTags = remoteTags,
        ),
        libraryAddedAt = Instant.EPOCH,
        rating = null,
        localTags = localTags,
        sourceAvailable = true,
        reconciliation = null,
    )

    private companion object {
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
        val einkEnvironment = environment.copy(
            preferences = DisplayPreferences(DisplayPreference.EINK, ColorSchemePreference.LIGHT),
            effectiveProfile = DisplayProfile.EINK,
            decisionReason = DisplayDecisionReason.MANUAL_EINK,
        )
    }
}
