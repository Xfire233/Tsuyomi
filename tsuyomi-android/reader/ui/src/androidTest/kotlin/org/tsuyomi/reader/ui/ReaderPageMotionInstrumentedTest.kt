/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.reader.ui
import android.media.AudioManager
import android.view.KeyEvent

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.pressBack
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
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
import org.tsuyomi.shared.backup.PortableReaderPreferences
import org.tsuyomi.shared.locator.ReaderLocator
import org.tsuyomi.shared.sourcecontract.ReaderBlock
import org.tsuyomi.shared.sourcecontract.ReaderDocument
import org.tsuyomi.shared.sourcecontract.SourceChapter

@OptIn(ExperimentalTestApi::class)
class ReaderPageMotionInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tapPageTurnShowsIntermediateDirectionalPositionsAndLandsOnce() {
        val commits = AtomicInteger()
        val locator = AtomicReference<ReaderLocator>()
        mountReader(onLocatorChanged = {
            locator.set(it)
            commits.incrementAndGet()
        })

        val content = composeRule.onNodeWithTag("reader-content-surface")
        val initialLeft = pageColumnLefts().single()
        composeRule.mainClock.autoAdvance = false
        try {
            content.performTouchInput { click(Offset(width * 0.85f, center.y)) }
            assertIntermediateDirectionalFrame(initialLeft)
        } finally {
            composeRule.mainClock.autoAdvance = true
        }

        composeRule.waitForIdle()
        assertEquals(1, pageColumnLefts().size)
        assertEquals(1, commits.get())
        assertTrue(requireNotNull(locator.get()?.characterOffset) > 0)
    }

    @Test
    fun swipePageTurnFollowsTheFingerAndLandsOnceFromTheReleasePosition() {
        val commits = AtomicInteger()
        val locator = AtomicReference<ReaderLocator>()
        mountReader(onLocatorChanged = {
            locator.set(it)
            commits.incrementAndGet()
        })

        val content = composeRule.onNodeWithTag("reader-content-surface")
        val initialLeft = pageColumnLefts().single()
        content.performTouchInput {
            down(Offset(width * 0.85f, center.y))
            moveTo(Offset(width * 0.45f, center.y))
        }
        composeRule.waitForIdle()
        val draggedLefts = pageColumnLefts()
        assertEquals(2, draggedLefts.size)
        assertTrue(draggedLefts.any { it < initialLeft - 20f })
        assertTrue(draggedLefts.any { it > initialLeft + 20f })
        assertEquals(0, commits.get())

        content.performTouchInput { up() }
        composeRule.waitForIdle()
        assertEquals(1, pageColumnLefts().size)
        assertEquals(1, commits.get())
        assertTrue(requireNotNull(locator.get()?.characterOffset) > 0)
    }

    @Test
    fun volumePagingConsumesTheSystemKeyPressAndTurnsExactlyOnce() {
        val commits = AtomicInteger()
        mountReader(onLocatorChanged = { commits.incrementAndGet() })
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val audioManager = instrumentation.targetContext.getSystemService(AudioManager::class.java)
        val initialVolume = (audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 2).coerceAtLeast(1)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, initialVolume, 0)

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_VOLUME_DOWN)

        composeRule.waitForIdle()
        assertEquals(initialVolume, audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
        assertEquals(1, commits.get())
    }

    @Test
    fun disabledVolumePagingDoesNotTurnTheReaderPage() {
        val commits = AtomicInteger()
        mountReader(
            preferences = PortableReaderPreferences(flow = "paged", volumePaging = false),
            onLocatorChanged = { commits.incrementAndGet() },
        )

        composeRule.onNodeWithTag("reader-content-surface").performKeyInput {
            keyDown(Key.VolumeDown)
            keyUp(Key.VolumeDown)
        }

        composeRule.waitForIdle()
        assertEquals(0, commits.get())
    }

    @Test
    fun instantMotionPageTurnHasNoIntermediatePage() {
        val commits = AtomicInteger()
        mountReader(
            environment = standardEnvironment.copy(motionPolicy = MotionPolicy.INSTANT),
            onLocatorChanged = { commits.incrementAndGet() },
        )

        val content = composeRule.onNodeWithTag("reader-content-surface")
        composeRule.mainClock.autoAdvance = false
        try {
            content.performTouchInput { click(Offset(width * 0.85f, center.y)) }
            composeRule.mainClock.advanceTimeByFrame()
            assertEquals(1, pageColumnLefts().size)
        } finally {
            composeRule.mainClock.autoAdvance = true
        }

        composeRule.waitForIdle()
        assertEquals(1, commits.get())
    }

    @Test
    fun pagedSeekPreviewSwapsImmediatelyAndCancellationDoesNotWriteProgress() {
        val commits = AtomicInteger()
        mountReader(onLocatorChanged = { commits.incrementAndGet() })

        val slider = composeRule.onNodeWithTag("reader-chapter-progress-slider")
        composeRule.mainClock.autoAdvance = false
        try {
            slider.performTouchInput {
                down(Offset(width * 0.20f, center.y))
                moveTo(Offset(width * 0.80f, center.y))
            }
            composeRule.mainClock.advanceTimeBy(110)
            assertEquals(1, pageColumnLefts().size)
            assertEquals(0, commits.get())
            pressBack()
            slider.performTouchInput { cancel() }
        } finally {
            composeRule.mainClock.autoAdvance = true
        }

        composeRule.waitForIdle()
        assertEquals(1, pageColumnLefts().size)
        assertEquals(0, commits.get())
    }

    @Test
    fun cancelledAndReversedPageGesturesDoNotCreatePhantomProgressOrCompletion() {
        val commits = AtomicInteger()
        val completed = mutableListOf<String>()
        val locator = AtomicReference<ReaderLocator>()
        mountReader(
            onLocatorChanged = {
                locator.set(it)
                commits.incrementAndGet()
            },
            onChapterCompleted = { completed += it },
        )

        val content = composeRule.onNodeWithTag("reader-content-surface")
        content.performTouchInput {
            down(Offset(width * 0.70f, center.y))
            moveTo(Offset(width * 0.60f, center.y))
            cancel()
        }
        composeRule.runOnIdle {
            assertEquals(0, commits.get())
            assertTrue(completed.isEmpty())
        }

        composeRule.mainClock.autoAdvance = false
        try {
            content.performTouchInput { click(Offset(width * 0.85f, center.y)) }
            composeRule.mainClock.advanceTimeBy(110)
            content.performTouchInput { click(Offset(width * 0.15f, center.y)) }
            composeRule.mainClock.advanceTimeBy(250)
        } finally {
            composeRule.mainClock.autoAdvance = true
        }

        composeRule.waitForIdle()
        assertEquals(1, pageColumnLefts().size)
        assertEquals(2, commits.get())
        assertEquals(0, requireNotNull(locator.get()).characterOffset)
        assertTrue(completed.isEmpty())
    }

    @Test
    fun nonlinearSeekReturnsToFirstOriginAndExpiresAfterThreeOrdinaryTurns() {
        val restored = mutableStateOf<ReaderLocator?>(null)
        val restorationGeneration = mutableStateOf(0L)
        val returned = AtomicReference<ReaderLocator>()
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(420.dp, 760.dp))) {
                DisplayEnvironmentProvider(standardEnvironment) {
                    TsuyomiTheme(standardEnvironment) {
                        ReaderSurface(
                            document = motionDocument,
                            auxiliarySheetState = rememberReaderAuxiliarySheetState(
                                motionDocument.sourceId,
                                motionDocument.remoteBookId,
                            ),
                            restoredLocator = restored.value,
                            restorationGeneration = restorationGeneration.value,
                            onLocatorChanged = { _, _ -> },
                            chapters = listOf(motionChapter),
                            currentChapterId = motionChapter.chapterId,
                            bookmarks = emptyList(),
                            onToggleBookmark = {},
                            onRemoveBookmark = {},
                            onSelectBookmark = { locator ->
                                returned.set(locator)
                                restored.value = locator
                                restorationGeneration.value += 1L
                            },
                            requestedFlow = "paged",
                            hasFlowOverride = false,
                            onFlowOverrideChanged = {},
                            onSelectChapter = {},
                            onNavigateUp = {},
                            preferences = PortableReaderPreferences(flow = "paged"),
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        fun seek(fraction: Float) {
            composeRule.onNodeWithTag("reader-chapter-progress-slider").performTouchInput {
                click(Offset(width * fraction, centerY))
            }
            composeRule.waitForIdle()
        }

        seek(0.75f)
        composeRule.onNodeWithTag("reader-return-origin").assertIsDisplayed()
        seek(0.4f)
        composeRule.onNodeWithTag("reader-return-origin").performTouchInput { click(center) }
        composeRule.waitForIdle()
        assertEquals(0, requireNotNull(returned.get()).characterOffset)
        composeRule.onNodeWithTag("reader-return-origin").assertDoesNotExist()

        seek(0.8f)
        repeat(2) {
            composeRule.onNodeWithTag("reader-content-surface").performTouchInput {
                click(Offset(width * 0.15f, center.y))
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("reader-return-origin").assertIsDisplayed()
        }
        composeRule.onNodeWithTag("reader-content-surface").performTouchInput {
            click(Offset(width * 0.15f, center.y))
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("reader-return-origin").assertDoesNotExist()
    }

    private fun assertIntermediateDirectionalFrame(initialLeft: Float) {
        composeRule.mainClock.advanceTimeBy(110)
        val intermediateLefts = pageColumnLefts()
        assertEquals(2, intermediateLefts.size)
        assertTrue(intermediateLefts.any { it < initialLeft - 1f })
        assertTrue(intermediateLefts.any { it > initialLeft + 1f })
        composeRule.mainClock.advanceTimeBy(250)
    }

    private fun pageColumnLefts(): List<Float> = composeRule
        .onAllNodesWithTag("reader-page-column-0")
        .fetchSemanticsNodes()
        .map { it.boundsInRoot.left }

    private fun mountReader(
        environment: DisplayEnvironment = standardEnvironment,
        preferences: PortableReaderPreferences = PortableReaderPreferences(flow = "paged"),
        onLocatorChanged: (ReaderLocator) -> Unit = {},
        onChapterCompleted: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(420.dp, 760.dp))) {
                DisplayEnvironmentProvider(environment) {
                    TsuyomiTheme(environment) {
                        ReaderSurface(
                            document = motionDocument,
                            auxiliarySheetState = rememberReaderAuxiliarySheetState(motionDocument.sourceId, motionDocument.remoteBookId),
                            restoredLocator = null,
                            restorationGeneration = 0L,
                            onLocatorChanged = { locator, _ -> onLocatorChanged(locator) },
                            chapters = listOf(motionChapter),
                            currentChapterId = motionChapter.chapterId,
                            bookmarks = emptyList(),
                            onToggleBookmark = {},
                            onRemoveBookmark = {},
                            onSelectBookmark = {},
                            requestedFlow = "paged",
                            hasFlowOverride = false,
                            onFlowOverrideChanged = {},
                            onSelectChapter = {},
                            onNavigateUp = {},
                            onChapterCompleted = onChapterCompleted,
                            preferences = preferences,
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private companion object {
        val motionChapter = SourceChapter(
            chapterId = "chapter-motion",
            title = "分页动画",
            url = "https://example.test/chapter-motion",
        )
        val motionDocument = ReaderDocument(
            sourceId = "org.tsuyomi.reader.test",
            remoteBookId = "page-motion",
            contentId = motionChapter.chapterId,
            revision = null,
            title = motionChapter.title,
            blocks = listOf(
                ReaderBlock.Paragraph(
                    blockId = "page-segment",
                    text = "分页动画保留段落切片。" + "连续正文用于验证整页过渡。".repeat(1000),
                ),
            ),
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
            redrawEpoch = 0L,
        )
    }
}
