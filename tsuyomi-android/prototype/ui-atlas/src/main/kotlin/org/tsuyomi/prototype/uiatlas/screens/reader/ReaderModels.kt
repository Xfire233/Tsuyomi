/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.screens.reader

import androidx.compose.runtime.Immutable
import kotlin.math.roundToInt
import kotlin.math.sign

@Immutable
enum class ReaderFlow(val label: String) {
    SCROLL("连续滚动"),
    PAGED("左右分页"),
    DUAL("双页"),
}

@Immutable
data class ReaderPosition(
    val progress: Int,
    val page: Int,
    val pageCount: Int,
    val pageStep: Int = 1,
) {
    fun adjacentPage(direction: Int): ReaderPosition? {
        if (direction == 0 || pageCount <= 1) return null
        val safeStep = pageStep.coerceAtLeast(1)
        val unitCount = ((pageCount - 1) / safeStep) + 1
        val currentUnit = ((page - 1) / safeStep).coerceIn(0, unitCount - 1)
        val targetUnit = (currentUnit + direction.sign).coerceIn(0, unitCount - 1)
        if (targetUnit == currentUnit) return null
        val targetPageIndex = (targetUnit * safeStep).coerceAtMost(pageCount - 1)
        val targetProgress = if (unitCount == 1) 0 else {
            (targetUnit * 100f / (unitCount - 1)).roundToInt()
        }
        return ReaderPosition(targetProgress, targetPageIndex + 1, pageCount, safeStep)
    }

    companion object {
        val START = ReaderPosition(progress = 0, page = 1, pageCount = 1)

        fun fromProgress(progress: Int, pageCount: Int): ReaderPosition {
            val safeCount = pageCount.coerceAtLeast(1)
            val safeProgress = progress.coerceIn(0, 100)
            val pageIndex = if (safeCount == 1) 0 else {
                ((safeProgress / 100f) * (safeCount - 1)).toInt().coerceIn(0, safeCount - 1)
            }
            return ReaderPosition(safeProgress, pageIndex + 1, safeCount)
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
enum class ReaderDocumentKind {
    PROSE,
    MIXED_MEDIA,
    REPLY_STREAM,
}

@Immutable
data class ReaderDocument(
    val id: String,
    val title: String,
    val kind: ReaderDocumentKind,
    val blocks: List<ReaderBlock>,
)

@Immutable
sealed interface ReaderBlock {
    val id: String
}

@Immutable
data class ReaderHeading(
    override val id: String,
    val text: String,
    val level: Int,
) : ReaderBlock

@Immutable
data class ReaderParagraph(
    override val id: String,
    val content: List<ReaderInline>,
) : ReaderBlock

@Immutable
data class ReaderImage(
    override val id: String,
    val title: String,
    val alternative: String,
    val caption: String,
    val aspectRatio: Float,
) : ReaderBlock

@Immutable
data class ReaderQuote(
    override val id: String,
    val content: List<ReaderInline>,
    val attribution: String? = null,
) : ReaderBlock

@Immutable
data class ReaderDivider(override val id: String) : ReaderBlock

@Immutable
data class ReaderListBlock(
    override val id: String,
    val ordered: Boolean,
    val items: List<List<ReaderInline>>,
) : ReaderBlock

@Immutable
data class ReaderCodeBlock(
    override val id: String,
    val language: String?,
    val code: String,
) : ReaderBlock

@Immutable
data class ReaderTableBlock(
    override val id: String,
    val headers: List<String>,
    val rows: List<List<String>>,
) : ReaderBlock

@Immutable
data class ReaderReplyReference(
    override val id: String,
    val targetPostId: String,
    val floor: String,
    val author: String,
    val excerpt: String,
) : ReaderBlock

@Immutable
data class ReaderAttachment(
    override val id: String,
    val name: String,
    val meta: String,
) : ReaderBlock

@Immutable
data class ReaderPost(
    override val id: String,
    val floor: String,
    val author: String,
    val time: String,
    val isOriginalPoster: Boolean,
    val blocks: List<ReaderBlock>,
) : ReaderBlock

@Immutable
sealed interface ReaderInline {
    val text: String

    @Immutable
    data class Plain(override val text: String) : ReaderInline

    @Immutable
    data class Strong(override val text: String) : ReaderInline

    @Immutable
    data class Emphasis(override val text: String) : ReaderInline

    @Immutable
    data class Strike(override val text: String) : ReaderInline

    @Immutable
    data class Code(override val text: String) : ReaderInline

    @Immutable
    data class Link(override val text: String, val destination: String) : ReaderInline

    @Immutable
    data class Ruby(override val text: String, val reading: String) : ReaderInline
}
