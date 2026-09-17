/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import org.junit.Assert.assertEquals
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

    @Composable
    private fun Screen(
        entries: List<LibraryEntry>,
        layout: LibraryTagLayout,
        sourceLabels: Map<String, String> = emptyMap(),
        onOpenTag: (LibraryTagDestination) -> Unit,
    ) {
        DisplayEnvironmentProvider(environment) {
            TsuyomiTheme(environment) {
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
            identity = BookIdentity(sourceId, id),
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
    }
}
