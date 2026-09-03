/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.shared.sourcecontract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.tsuyomi.shared.model.BookIdentity

class SourceHomeContractsTest {
    @Test
    fun versionedPageAcceptsOnlyDeclaredFilterSelections() {
        val summary = SourceBookSummary(
            identity = BookIdentity("org.tsuyomi.wenku8", "1234"),
            title = "雾港纪事",
            author = "林川",
            coverUrl = "https://pic.wenku8.com/cover.jpg",
            canonicalUrl = "https://www.wenku8.net/book/1234.htm",
        )
        val filter = SourceHomeFilter(
            id = "ranking",
            label = "排序",
            options = listOf(SourceHomeFilterOption("lastupdate", "最近更新")),
        )
        val page = SourceHomePage(
            schemaVersion = 1,
            title = "Wenku8 书库",
            filters = listOf(filter),
            selectedFilters = mapOf("ranking" to "lastupdate"),
            sections = listOf(SourceHomeSection("catalog", "最近更新", listOf(summary))),
            features = listOf(
                SourceHomeFeature(
                    id = "sugoi-2026",
                    title = "这本轻小说真厉害！2026",
                    supportingText = "TOP20 榜单",
                    selectedFilters = mapOf("view" to "recommend", "feature" to "sugoi-2026"),
                ),
            ),
            nextCursor = "page-2",
            complete = false,
        )
        assertEquals("page-2", page.nextCursor)
        assertEquals("sugoi-2026", page.features.single().id)

        assertThrows(IllegalArgumentException::class.java) {
            page.copy(schemaVersion = 2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            page.copy(selectedFilters = mapOf("ranking" to "source-controlled-layout"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            page.copy(nextCursor = null, complete = false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            page.copy(features = List(5) { index -> page.features.single().copy(id = "feature-$index") })
        }
    }
}
