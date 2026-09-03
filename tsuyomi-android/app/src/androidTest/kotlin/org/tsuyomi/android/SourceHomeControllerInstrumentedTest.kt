/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.feature.browse.SourceHomeViewState
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.SourceBookSummary
import org.tsuyomi.shared.sourcecontract.SourceHomeFilter
import org.tsuyomi.shared.sourcecontract.SourceHomeFeature
import org.tsuyomi.shared.sourcecontract.SourceHomeFilterOption
import org.tsuyomi.shared.sourcecontract.SourceHomePage
import org.tsuyomi.shared.sourcecontract.SourceHomeSection

@RunWith(AndroidJUnit4::class)
internal class SourceHomeControllerInstrumentedTest {
    @Test
    fun normalized_selection_reuses_query_and_package_revision_invalidates_cache() = runBlocking {
        val requests = AtomicInteger()
        val controller = SourceHomeController()
        val load: suspend (Map<String, String>, String?) -> Result<SourceHomePage> = { filters, _ ->
            requests.incrementAndGet()
            Result.success(recommendPage(filters["recommendation"] ?: "allvote"))
        }
        try {
            withContext(Dispatchers.Main) { controller.ensureInitial("revision-a", load) }
            awaitContent(controller)
            assertEquals(1, requests.get())

            withContext(Dispatchers.Main) {
                controller.selectFilters(
                    mapOf("view" to "recommend", "recommendation" to "allvote"),
                    load,
                )
            }
            assertEquals(1, requests.get())
            assertEquals("allvote", controller.activePage?.selectedFilters?.get("recommendation"))

            withContext(Dispatchers.Main) { controller.ensureInitial("revision-b", load) }
            awaitContent(controller)
            assertEquals(2, requests.get())
        } finally {
            controller.close()
        }
    }

    @Test
    fun replacement_keeps_cached_content_and_failure_is_query_scoped() = runBlocking {
        val controller = SourceHomeController()
        val requests = AtomicInteger()
        val load: suspend (Map<String, String>, String?) -> Result<SourceHomePage> = { filters, _ ->
            requests.incrementAndGet()
            when (filters["recommendation"]) {
                "goodnum" -> {
                    delay(100)
                    Result.failure(IllegalStateException("replacement-failure"))
                }
                else -> Result.success(recommendPage(filters["recommendation"] ?: "allvote"))
            }
        }
        try {
            withContext(Dispatchers.Main) { controller.ensureInitial("revision-a", load) }
            awaitContent(controller)
            withContext(Dispatchers.Main) {
                controller.selectFilters(
                    mapOf("view" to "recommend", "recommendation" to "goodnum"),
                    load,
                )
                val replacing = (controller.state as SourceHomeViewState.Content).activePageState
                assertNotNull(replacing?.page)
                assertTrue(replacing?.replacing == true)
            }
            withTimeout(5_000) {
                while ((controller.state as? SourceHomeViewState.Content)
                        ?.activePageState?.replacementFailure == null
                ) delay(10)
            }
            val failed = (controller.state as SourceHomeViewState.Content).activePageState
            assertNotNull(failed?.page)
            assertEquals("推荐 allvote", failed?.page?.sections?.single()?.title)
            assertNotNull(failed?.replacementFailure)
            assertEquals(2, requests.get())
        } finally {
            controller.close()
        }
    }

    @Test
    fun feature_page_is_cached_and_back_restores_root_scroll_without_request() = runBlocking {
        val requests = AtomicInteger()
        val feature = SourceHomeFeature(
            id = "sugoi-2026",
            title = "这本轻小说真厉害！2026",
            supportingText = "TOP20 榜单",
            selectedFilters = mapOf("view" to "recommend", "feature" to "sugoi-2026"),
        )
        val root = featureRootPage(feature)
        val award = featureAwardPage()
        val controller = SourceHomeController()
        val load: suspend (Map<String, String>, String?) -> Result<SourceHomePage> = { filters, _ ->
            requests.incrementAndGet()
            Result.success(if (filters["feature"] == "sugoi-2026") award else root)
        }
        try {
            withContext(Dispatchers.Main) { controller.ensureInitial("revision-a", load) }
            awaitContent(controller)
            val rootState = controller.state as SourceHomeViewState.Content
            withContext(Dispatchers.Main) {
                controller.updateScrollPosition("recommend", rootState.activePageState!!.queryKey, 4, 21)
                controller.openFeature(feature, load)
            }
            withTimeout(5_000) {
                while ((controller.state as? SourceHomeViewState.Content)?.title != feature.title) delay(10)
            }
            val featureState = controller.state as SourceHomeViewState.Content
            assertTrue(featureState.featureOpen)
            assertEquals("文库部门 TOP10", featureState.activePage?.sections?.first()?.title)
            assertEquals(2, requests.get())

            withContext(Dispatchers.Main) { assertTrue(controller.navigateBackFromFeature()) }
            val restored = controller.state as SourceHomeViewState.Content
            assertTrue(!restored.featureOpen)
            assertEquals("7月新番", restored.activePage?.sections?.single()?.title)
            assertEquals(4, restored.activePageState?.firstVisibleItemIndex)
            assertEquals(21, restored.activePageState?.firstVisibleItemScrollOffset)
            assertEquals(2, requests.get())

            withContext(Dispatchers.Main) { controller.openFeature(feature, load) }
            val cached = controller.state as SourceHomeViewState.Content
            assertTrue(cached.featureOpen)
            assertEquals(2, requests.get())
        } finally {
            controller.close()
        }
    }

    private suspend fun awaitContent(controller: SourceHomeController) {
        withTimeout(5_000) {
            while ((controller.state as? SourceHomeViewState.Content)?.activePage == null) delay(10)
        }
    }

    private fun featureRootPage(feature: SourceHomeFeature): SourceHomePage {
        val primary = SourceHomeFilter(
            id = "view",
            label = "栏目",
            options = listOf(
                SourceHomeFilterOption("recommend", "推荐"),
                SourceHomeFilterOption("category", "分类"),
            ),
        )
        return SourceHomePage(
            title = "Wenku8 书库",
            schemaVersion = 1,
            filters = listOf(primary),
            selectedFilters = mapOf("view" to "recommend"),
            sections = listOf(SourceHomeSection("seasonal", "7月新番", listOf(summary("root")))),
            features = listOf(feature),
            nextCursor = null,
            complete = true,
        )
    }

    private fun featureAwardPage(): SourceHomePage {
        val primary = SourceHomeFilter(
            id = "view",
            label = "栏目",
            options = listOf(
                SourceHomeFilterOption("recommend", "推荐"),
                SourceHomeFilterOption("category", "分类"),
            ),
        )
        return SourceHomePage(
            title = "这本轻小说真厉害！2026",
            schemaVersion = 1,
            filters = listOf(primary),
            selectedFilters = mapOf("view" to "recommend"),
            sections = listOf(
                SourceHomeSection("bunko", "文库部门 TOP10", listOf(summary("award"))),
                SourceHomeSection("tankobon", "单行本部门 TOP10", listOf(summary("award-2"))),
            ),
            nextCursor = null,
            complete = true,
        )
    }

    private fun summary(id: String) = SourceBookSummary(
        identity = BookIdentity("org.tsuyomi.wenku8", id),
        title = "书目 $id",
        author = "作者",
        coverUrl = null,
        canonicalUrl = "https://www.wenku8.net/book/$id.htm",
    )

    private fun recommendPage(recommendation: String): SourceHomePage {
        val primary = SourceHomeFilter(
            id = "view",
            label = "栏目",
            options = listOf(
                SourceHomeFilterOption("recommend", "推荐"),
                SourceHomeFilterOption("category", "分类"),
            ),
        )
        val secondary = SourceHomeFilter(
            id = "recommendation",
            label = "推荐",
            options = listOf(
                SourceHomeFilterOption("allvote", "总推荐"),
                SourceHomeFilterOption("goodnum", "总收藏"),
            ),
        )
        return SourceHomePage(
            title = "Wenku8 书库",
            schemaVersion = 1,
            filters = listOf(primary, secondary),
            selectedFilters = mapOf("view" to "recommend", "recommendation" to recommendation),
            sections = listOf(
                SourceHomeSection(
                    id = "catalog",
                    title = "推荐 $recommendation",
                    items = listOf(
                        SourceBookSummary(
                            identity = BookIdentity("org.tsuyomi.wenku8", recommendation),
                            title = "书目 $recommendation",
                            author = "作者",
                            coverUrl = null,
                            canonicalUrl = "https://www.wenku8.net/book/$recommendation.htm",
                        ),
                    ),
                ),
            ),
            nextCursor = null,
            complete = true,
        )
    }
}
