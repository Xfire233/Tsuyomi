/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class SourceHomeInstrumentedTest : SourceFlowInstrumentedTestFixture() {
    @Test
    fun routeOwnerCanStayIdleThenExplicitlyLoadBoundedHomePage() = runBlocking {
        val packageInfo = installFixture()
        val controller = controller()
        Phase2SourceGateway.resetOperationCounts()
        try {
            controller.open(packageInfo)
            assertEquals(0, Phase2SourceGateway.sourceRequestCount())

            val first = controller.loadHome(emptyMap()).getOrThrow()
            assertEquals(1, first.schemaVersion)
            assertEquals(mapOf("view" to "recommend"), first.selectedFilters)
            assertEquals(listOf("7月新番", "新书风云榜", "本周会员推荐榜"), first.sections.map { it.title })
            assertEquals(listOf("1234", "5678"), first.sections.first().items.map { it.identity.remoteBookId })
            val feature = first.features.single()
            assertEquals("sugoi-2026", feature.id)
            assertEquals(mapOf("view" to "recommend", "feature" to "sugoi-2026"), feature.selectedFilters)
            assertTrue(first.complete)
            assertEquals(1, Phase2SourceGateway.sourceRequestCount())
            assertEquals(0, Phase2SourceGateway.websiteMutationCount())
            val award = controller.loadHome(feature.selectedFilters).getOrThrow()
            assertEquals("这本轻小说真厉害！2026", award.title)
            assertEquals(
                listOf("文库部门 TOP10", "单行本部门 TOP10"),
                award.sections.map { it.title },
            )
            assertEquals(listOf("3988", "2580", "3057", "2930"), award.sections.first().items.map { it.identity.remoteBookId })
            assertEquals(2, Phase2SourceGateway.sourceRequestCount())
            assertEquals(0, Phase2SourceGateway.websiteMutationCount())


            val category = controller.loadHome(
                mapOf("view" to "category", "tag" to "fantasy", "sort" to "2"),
            ).getOrThrow()
            assertEquals("page-2", category.nextCursor)
            assertFalse(category.complete)
            val categorySecond = controller.loadHome(category.selectedFilters, category.nextCursor).getOrThrow()
            assertTrue(categorySecond.complete)
            assertEquals(4, Phase2SourceGateway.sourceRequestCount())
            assertEquals(0, Phase2SourceGateway.websiteMutationCount())
        } finally {
            controller.close()
        }
    }
}
