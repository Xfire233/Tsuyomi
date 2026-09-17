/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.android

import org.junit.Assert.assertEquals
import org.junit.Test
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.SourceBookSummary
import org.tsuyomi.shared.sourcecontract.SourceHomeFilter
import org.tsuyomi.shared.sourcecontract.SourceHomeFilterOption
import org.tsuyomi.shared.sourcecontract.SourceHomePage
import org.tsuyomi.shared.sourcecontract.SourceHomeSection

/**
 * An appended source-home page accumulates for as long as the source keeps handing out cursors.
 *
 * The contract used to bound a home page by item count and by section count, so a source that paged
 * far enough eventually produced a page its own `init` rejected. The append coroutine runs uncaught
 * on the main dispatcher, which aborted the process mid-scroll. Those bounds are gone; these cases
 * lock in that accumulation is not truncated again.
 */
class SourceHomeMergeTest {
    @Test
    fun mergeAccumulatesPastTheFormerSectionItemBound() {
        val merged = mergeHomePages(singleSection("catalog", 80), singleSection("catalog", 80, skip = 80))

        assertEquals(160, merged.sections.single().items.size)
    }

    @Test
    fun mergeAccumulatesPastTheFormerPageSectionBound() {
        val merged = mergeHomePages(sections(20), singleSection("extra", 1))

        assertEquals(21, merged.sections.size)
    }

    @Test
    fun mergeDeduplicatesBooksTheNextPageRepeats() {
        val merged = mergeHomePages(singleSection("catalog", 3), singleSection("catalog", 3, skip = 1))

        assertEquals(listOf("1", "2", "3", "4"), merged.sections.single().items.map { it.identity.remoteBookId })
    }

    @Test
    fun mergeAppendsASectionTheCurrentPageDoesNotHave() {
        val merged = mergeHomePages(singleSection("catalog", 1), singleSection("awards", 2, skip = 10))

        assertEquals(listOf("catalog", "awards"), merged.sections.map { it.id })
    }

    private fun sections(count: Int): SourceHomePage = page(
        (1..count).map { index -> SourceHomeSection("section-$index", "分节 $index", items(1, skip = index * 100)) },
    )

    private fun singleSection(id: String, size: Int, skip: Int = 0): SourceHomePage =
        page(listOf(SourceHomeSection(id, "目录", items(size, skip = skip))))

    private fun page(sections: List<SourceHomeSection>) = SourceHomePage(
        title = "Wenku8 书库",
        schemaVersion = 1,
        filters = listOf(
            SourceHomeFilter(
                id = "view",
                label = "栏目",
                options = listOf(SourceHomeFilterOption("category", "分类")),
            ),
        ),
        selectedFilters = mapOf("view" to "category"),
        sections = sections,
        nextCursor = "page-2",
        complete = false,
    )

    private fun items(size: Int, skip: Int): List<SourceBookSummary> = (1..size).map { offset -> summary(skip + offset) }

    private fun summary(remoteBookId: Int) = SourceBookSummary(
        identity = BookIdentity("org.tsuyomi.wenku8", remoteBookId.toString()),
        title = "书目 $remoteBookId",
        author = "作者",
        coverUrl = null,
        canonicalUrl = "https://www.wenku8.net/book/$remoteBookId.htm",
    )
}
