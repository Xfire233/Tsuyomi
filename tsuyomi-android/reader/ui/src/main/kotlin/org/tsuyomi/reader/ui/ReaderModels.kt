/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.reader.ui

import androidx.compose.runtime.Immutable
import kotlin.math.roundToInt

@Immutable
enum class ReaderFlow(val label: String) {
    PAGED("分页"),
    SCROLL("连续滚动"),
    DUAL("双页"),
}

@Immutable
data class ReaderPosition(
    val progress: Int,
    val page: Int,
    val pageCount: Int,
    val pageStep: Int = 1,
) {
    val selectablePageCount: Int
        get() = ((pageCount.coerceAtLeast(1) - 1) / pageStep.coerceAtLeast(1)) + 1

    val seekProgress: Int
        get() {
            val stopCount = selectablePageCount
            if (stopCount == 1) return 0
            val stopIndex = ((page - 1) / pageStep.coerceAtLeast(1)).coerceIn(0, stopCount - 1)
            return (stopIndex * 100f / (stopCount - 1)).roundToInt()
        }

    fun adjacentPage(direction: Int): ReaderPosition? {
        if (direction == 0 || pageCount <= 1) return null
        val nextIndex = (page - 1) + if (direction < 0) -pageStep else pageStep
        if (nextIndex !in 0 until pageCount) return null
        return fromPageIndex(nextIndex, pageCount, pageStep)
    }

    companion object {
        val START = ReaderPosition(progress = 0, page = 1, pageCount = 1)

        fun fromProgress(progress: Int, pageCount: Int, pageStep: Int = 1): ReaderPosition {
            val safeCount = pageCount.coerceAtLeast(1)
            val safeProgress = progress.coerceIn(0, 100)
            val index = if (safeCount == 1) 0 else {
                (safeProgress / 100f * (safeCount - 1)).roundToInt()
            }
            return fromPageIndex(index, safeCount, pageStep)
        }
        fun fromSeekProgress(progress: Int, pageCount: Int, pageStep: Int = 1): ReaderPosition {
            val safeCount = pageCount.coerceAtLeast(1)
            val safeStep = pageStep.coerceAtLeast(1)
            val stopCount = ((safeCount - 1) / safeStep) + 1
            val stopIndex = if (stopCount == 1) 0 else {
                (progress.coerceIn(0, 100) / 100f * (stopCount - 1)).roundToInt()
            }
            return fromPageIndex(stopIndex * safeStep, safeCount, safeStep)
        }


        fun fromPageIndex(index: Int, pageCount: Int, pageStep: Int = 1): ReaderPosition {
            val safeCount = pageCount.coerceAtLeast(1)
            val safeIndex = index.coerceIn(0, safeCount - 1)
            val progress = if (safeCount == 1) 0 else {
                (safeIndex * 100f / (safeCount - 1)).roundToInt()
            }
            return ReaderPosition(progress, safeIndex + 1, safeCount, pageStep.coerceAtLeast(1))
        }
    }
}

@Immutable
enum class ReaderAuxiliaryTab(val label: String) {
    CONTENTS("目录"),
    BOOKMARKS("书签"),
    SEARCH("搜索"),
}

@Immutable
data class ReaderSettingsUiState(
    val fontSize: Float = 18f,
    val lineHeight: Float = 1.6f,
    val horizontalMargin: Float = 24f,
    val paragraphSpacing: Float = 12f,
    val flow: ReaderFlow = ReaderFlow.PAGED,
    val lockPortrait: Boolean = false,
    val progressVisible: Boolean = true,
    val immersive: Boolean = false,
    val keepAwake: Boolean = true,
)

sealed interface ReaderSettingsAction {
    data class FontSize(val value: Float) : ReaderSettingsAction
    data class LineHeight(val value: Float) : ReaderSettingsAction
    data class HorizontalMargin(val value: Float) : ReaderSettingsAction
    data class ParagraphSpacing(val value: Float) : ReaderSettingsAction
    data class Flow(val value: ReaderFlow) : ReaderSettingsAction
    data class LockPortrait(val value: Boolean) : ReaderSettingsAction
    data class ProgressVisible(val value: Boolean) : ReaderSettingsAction
    data class Immersive(val value: Boolean) : ReaderSettingsAction
    data class KeepAwake(val value: Boolean) : ReaderSettingsAction
}
