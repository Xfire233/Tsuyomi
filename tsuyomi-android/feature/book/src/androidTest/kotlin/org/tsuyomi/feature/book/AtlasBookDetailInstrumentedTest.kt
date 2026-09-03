/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.book

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.media.api.FallbackSpec
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.SourceBookDetail
import org.tsuyomi.shared.sourcecontract.SourceBookSummary
import org.tsuyomi.shared.sourcecontract.SourceChapter
import org.tsuyomi.shared.sourcecontract.SourceDirectory

@RunWith(AndroidJUnit4::class)
class AtlasBookDetailInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun directoryGroupsChaptersByVolumeAndDefaultsToCurrentVolume() {
        val book = sourceBook()
        val chapters = listOf(
            chapter("v1-c1", "第一卷 第一章", "第一卷"),
            chapter("v1-c2", "第一卷 第二章", "第一卷"),
            chapter("v2-c1", "第二卷 第一章", "第二卷"),
            chapter("v2-c2", "第二卷 第二章", "第二卷"),
            chapter("v3-c1", "第三卷 第一章", "第三卷"),
        )
        compose.setContent {
            var unreadOnly by remember { mutableStateOf(false) }
            MaterialTheme {
                StandardAtlasBookDetailScreen(
                    state = SourceBookState.Content(SourceBookDetail(book, "简介", emptyList(), "连载")),
                    directoryState = SourceBookState.Content(SourceDirectory(book.identity, chapters)),
                    localState = DetailLocalState(
                        inLibrary = true,
                        progressChapterId = "v2-c1",
                        progressChapterFraction = 0.4,
                    ),
                    mutation = null,
                    coverState = CoverUiState.Fallback(FallbackSpec(book.title, null)),
                    unreadOnly = unreadOnly,
                    descending = false,
                    selectedChapterId = null,
                    onSetRating = {},
                    onAddTag = {},
                    onToggleReadLater = {},
                    onToggleUnreadOnly = { unreadOnly = !unreadOnly },
                    onToggleOrder = {},
                    onSelectChapter = {},
                    onContinueReading = {},
                    onAddToLibrary = {},
                    onRetry = {},
                    onUseOfflineCache = {},
                    onOpenVerification = {},
                )
            }
        }

        compose.onNodeWithText("第二卷").assert(hasStateDescription("已展开"))
        compose.onNodeWithText("第二卷 第一章").assertIsDisplayed()
        compose.onNodeWithText("第一卷").assert(hasStateDescription("已收起"))
        compose.onNodeWithText("第一卷 第一章").assertDoesNotExist()
        compose.onNodeWithContentDescription("当前顺序：正序，点按切换").assertExists()

        compose.onNodeWithText("第一卷").performClick()
        compose.onNodeWithText("第一卷").assert(hasStateDescription("已展开"))
        compose.onNodeWithText("第一卷 第一章").assertIsDisplayed()
        compose.onNodeWithText("仅看未读").performClick()
        compose.onNodeWithText("第一卷").assertDoesNotExist()
        compose.onNodeWithText("第二卷 第一章").assertIsDisplayed()
    }

    @Test
    fun headerAndIntroductionUseCompactDensity() {
        val book = sourceBook().copy(title = "文学少女")
        val description = "文艺社的两位成员调查十年前的人间失格事件。随着线索逐步出现，他们发现每个人都在用自己的方式保护重要的人，也必须面对被隐藏多年的真相。这个过程改变了他们对故事、记忆与彼此关系的理解。"
        compose.setContent {
            var localState by remember { mutableStateOf(DetailLocalState()) }
            MaterialTheme {
                StandardAtlasBookDetailScreen(
                    state = SourceBookState.Content(SourceBookDetail(book, description, emptyList(), "已完结")),
                    directoryState = SourceBookState.Content(
                        SourceDirectory(book.identity, listOf(chapter("c1", "第一章", "第一卷"))),
                    ),
                    localState = localState,
                    mutation = null,
                    coverState = CoverUiState.Fallback(FallbackSpec(book.title, null)),
                    unreadOnly = false,
                    descending = false,
                    selectedChapterId = null,
                    onSetRating = { localState = localState.copy(rating = it) },
                    onAddTag = {},
                    onToggleReadLater = {
                        localState = localState.copy(
                            inLibrary = true,
                            readLater = !localState.readLater,
                        )
                    },
                    onToggleUnreadOnly = {},
                    onToggleOrder = {},
                    onSelectChapter = {},
                    onContinueReading = {},
                    onAddToLibrary = { localState = localState.copy(inLibrary = true) },
                    onRetry = {},
                    onUseOfflineCache = {},
                    onOpenVerification = {},
                )
            }
        }

        compose.waitForIdle()
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        val coverBounds = compose.onNodeWithTag("detail-cover").fetchSemanticsNode().boundsInRoot
        assertTrue(kotlin.math.abs(coverBounds.width - 135f * density) <= 1f)
        assertTrue(kotlin.math.abs(coverBounds.height - 180f * density) <= 1f)

        compose.onNodeWithText("已完结").assertIsDisplayed()
        val titleBounds = compose.onNodeWithTag("detail-title-flow").fetchSemanticsNode().boundsInRoot
        val statusBounds = compose.onNodeWithTag(
            "detail-publication-status",
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        val introductionBounds = compose.onNodeWithTag("detail-introduction-module").fetchSemanticsNode().boundsInRoot
        assertTrue(statusBounds.top >= titleBounds.top)
        assertTrue(statusBounds.bottom <= titleBounds.bottom)
        assertTrue(statusBounds.right <= titleBounds.right + 1f)
        assertTrue(statusBounds.bottom <= introductionBounds.top)
        val firstRatingGlyphBounds = compose.onNodeWithTag(
            "detail-rating-star-1-glyph",
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "titleLeft=${titleBounds.left}, firstVisibleStarLeft=${firstRatingGlyphBounds.left}",
            kotlin.math.abs(firstRatingGlyphBounds.left - titleBounds.left) <= 1f,
        )
        repeat(5) { index ->
            val touchBounds = compose.onNodeWithTag("detail-rating-star-${index + 1}-touch")
                .fetchSemanticsNode().touchBoundsInRoot
            assertTrue(touchBounds.width >= 48f * density - 1f)
            assertTrue(touchBounds.height >= 48f * density - 1f)
        }

        val previewBounds = compose.onNodeWithTag("detail-introduction-preview").fetchSemanticsNode().boundsInRoot
        val expandBounds = compose.onNodeWithTag("detail-introduction-expand").fetchSemanticsNode().boundsInRoot
        val expandLabelBounds = compose.onNodeWithTag(
            "detail-introduction-expand-label",
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        assertTrue(expandBounds.bottom <= previewBounds.bottom + 1f)
        assertTrue(expandBounds.top >= previewBounds.bottom - 48f * density - 1f)
        assertTrue(previewBounds.bottom - expandLabelBounds.bottom <= 2f * density)
        val identityBounds = compose.onNodeWithTag("detail-identity-module").fetchSemanticsNode().boundsInRoot
        val libraryActionBounds = compose.onNodeWithTag("detail-library-action").fetchSemanticsNode().boundsInRoot
        val readLaterActionBounds = compose.onNodeWithTag("detail-read-later-action").fetchSemanticsNode().boundsInRoot
        val tagBounds = compose.onNodeWithTag("detail-tag-module").fetchSemanticsNode().boundsInRoot
        assertTrue(libraryActionBounds.bottom <= identityBounds.bottom + 1f)
        assertTrue(readLaterActionBounds.bottom <= identityBounds.bottom + 1f)
        assertTrue(tagBounds.top >= identityBounds.bottom - 1f)
        val actionBottom = maxOf(libraryActionBounds.bottom, readLaterActionBounds.bottom)
        assertTrue(
            "coverBottom=${coverBounds.bottom}, actionBottom=$actionBottom, density=$density",
            kotlin.math.abs(coverBounds.bottom - actionBottom) <= 2f * density,
        )
        compose.onNodeWithTag("detail-read-later-action")
            .assert(hasStateDescription("未稍后再读"))
            .assertIsNotSelected()
            .performClick()
        compose.onNodeWithTag("detail-read-later-action")
            .assert(hasStateDescription("已稍后再读"))
            .assertIsSelected()
        compose.onNodeWithTag("detail-library-action").assert(hasStateDescription("已在书架"))
        compose.onNodeWithTag("detail-rating-star-1-touch")
            .assertIsEnabled()
            .performClick()
            .assertIsSelected()
        compose.onNodeWithText("已在书架").assertIsDisplayed()
        val collapsedHeight = compose.onNodeWithTag("detail-introduction-text").fetchSemanticsNode().boundsInRoot.height

        compose.onNodeWithTag("detail-introduction-expand").performClick()
        compose.onNodeWithTag("detail-introduction-collapse").assertExists()
        val expandedHeight = compose.onNodeWithTag("detail-introduction-text").fetchSemanticsNode().boundsInRoot.height
        assertTrue(expandedHeight > collapsedHeight)
    }

    @Test
    fun longTitleKeepsMaterialStatusBadgeAligned() {
        val book = sourceBook().copy(title = "文学少女与渴望死亡的小丑以及被隐藏在漫长书页后的秘密")
        compose.setContent {
            MaterialTheme {
                StandardAtlasBookDetailScreen(
                    state = SourceBookState.Content(SourceBookDetail(book, "简介", emptyList(), "已完结")),
                    directoryState = SourceBookState.Content(
                        SourceDirectory(book.identity, listOf(chapter("c1", "第一章", "第一卷"))),
                    ),
                    localState = DetailLocalState(),
                    mutation = null,
                    coverState = CoverUiState.Fallback(FallbackSpec(book.title, null)),
                    unreadOnly = false,
                    descending = false,
                    selectedChapterId = null,
                    onSetRating = {},
                    onAddTag = {},
                    onToggleReadLater = {},
                    onToggleUnreadOnly = {},
                    onToggleOrder = {},
                    onSelectChapter = {},
                    onContinueReading = {},
                    onAddToLibrary = {},
                    onRetry = {},
                    onUseOfflineCache = {},
                    onOpenVerification = {},
                )
            }
        }

        compose.waitForIdle()
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        val titleBounds = compose.onNodeWithTag("detail-title-flow").fetchSemanticsNode().boundsInRoot
        val statusBounds = compose.onNodeWithTag(
            "detail-publication-status",
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        assertTrue(titleBounds.height > 64f * density)
        assertTrue(statusBounds.top >= titleBounds.top)
        assertTrue(statusBounds.bottom <= titleBounds.bottom)
        assertTrue(titleBounds.bottom - statusBounds.bottom <= 8f * density)
        assertTrue(statusBounds.height in 16f * density..24f * density)
        assertTrue(statusBounds.right <= titleBounds.right + 1f)
    }

    private fun sourceBook(): SourceBookSummary = SourceBookSummary(
        identity = BookIdentity("org.tsuyomi.fixture", "book-1"),
        title = "分卷目录测试",
        author = "作者",
        coverUrl = null,
        canonicalUrl = "https://example.com/book/1",
    )

    private fun chapter(id: String, title: String, volume: String): SourceChapter = SourceChapter(
        chapterId = id,
        title = title,
        url = "https://example.com/chapter/$id",
        volumeTitle = volume,
    )
}
