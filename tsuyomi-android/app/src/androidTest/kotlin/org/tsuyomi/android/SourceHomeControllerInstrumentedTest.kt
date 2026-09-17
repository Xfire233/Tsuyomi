/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.withTimeoutOrNull
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
    fun same_source_package_reuses_query_and_package_replacement_resets_cache() = runBlocking {
        val requests = AtomicInteger()
        val controller = SourceHomeController()
        val load: suspend (Map<String, String>, String?) -> Result<SourceHomePage> = { filters, _ ->
            requests.incrementAndGet()
            Result.success(recommendPage(filters["recommendation"] ?: "allvote"))
        }
        try {
            withContext(Dispatchers.Main) { controller.ensureInitial("source-a", "revision-a", load) }
            awaitContent(controller)
            assertEquals(1, requests.get())
            withContext(Dispatchers.Main) { controller.bindSource("source-a", "revision-a") }
            assertEquals(1, requests.get())

            withContext(Dispatchers.Main) {
                controller.selectFilters(
                    mapOf("view" to "recommend", "recommendation" to "allvote"),
                    load,
                )
            }
            assertEquals(1, requests.get())
            assertEquals("allvote", controller.activePage?.selectedFilters?.get("recommendation"))

            withContext(Dispatchers.Main) { controller.ensureInitial("source-a", "revision-b", load) }
            awaitContent(controller)
            assertEquals(2, requests.get())
            assertEquals("推荐 allvote", controller.activePage?.sections?.single()?.title)
        } finally {
            controller.close()
        }
    }

    @Test
    fun session_renewal_discards_settled_home_pages_before_reentry() = runBlocking {
        val requests = AtomicInteger()
        val controller = SourceHomeController()
        val load: suspend (Map<String, String>, String?) -> Result<SourceHomePage> = { _, _ ->
            requests.incrementAndGet()
            Result.success(recommendPage("allvote"))
        }
        try {
            withContext(Dispatchers.Main) { controller.ensureInitial("source-a", "revision-a", load) }
            awaitContent(controller)
            withContext(Dispatchers.Main) { controller.invalidateSession() }
            withContext(Dispatchers.Main) { controller.ensureInitial("source-a", "revision-a", load) }
            awaitContent(controller)

            assertEquals(2, requests.get())
        } finally {
            controller.close()
        }
    }

    @Test
    fun primary_tabs_restore_cached_page_and_viewport_without_another_request() = runBlocking {
        val requests = AtomicInteger()
        val controller = SourceHomeController()
        val load: suspend (Map<String, String>, String?) -> Result<SourceHomePage> = { filters, _ ->
            requests.incrementAndGet()
            Result.success(primaryPage(filters["view"] ?: "recommend"))
        }
        try {
            withContext(Dispatchers.Main) { controller.ensureInitial("source-a", "revision-a", load) }
            awaitContent(controller)
            val recommended = (controller.state as SourceHomeViewState.Content).activePageState!!
            withContext(Dispatchers.Main) {
                controller.updateScrollPosition("recommend", recommended.queryKey, index = 8, offset = 20)
                controller.selectPrimary("category", load)
            }
            awaitContent(controller)
            val category = (controller.state as SourceHomeViewState.Content).activePageState!!
            withContext(Dispatchers.Main) {
                controller.updateScrollPosition("category", category.queryKey, index = 3, offset = 9)
                controller.selectPrimary("recommend", load)
            }

            val restored = (controller.state as SourceHomeViewState.Content).activePageState!!
            assertEquals(recommended.queryKey, restored.queryKey)
            assertEquals(8, restored.firstVisibleItemIndex)
            assertEquals(20, restored.firstVisibleItemScrollOffset)
            assertEquals(2, requests.get())
        } finally {
            controller.close()
        }
    }

    @Test
    fun late_background_pages_do_not_replace_cached_tab_or_filter_selection() = runBlocking {
        val controller = SourceHomeController()
        val categoryStarted = CompletableDeferred<Unit>()
        val releaseCategory = CompletableDeferred<Unit>()
        val categoryReturned = CompletableDeferred<Unit>()
        val filterStarted = CompletableDeferred<Unit>()
        val releaseFilter = CompletableDeferred<Unit>()
        val filterReturned = CompletableDeferred<Unit>()
        val requests = AtomicInteger()
        val load: suspend (Map<String, String>, String?) -> Result<SourceHomePage> = { filters, _ ->
            requests.incrementAndGet()
            when {
                filters["view"] == "category" -> {
                    categoryStarted.complete(Unit)
                    releaseCategory.await()
                    categoryReturned.complete(Unit)
                    Result.success(primaryPage("category"))
                }
                filters["recommendation"] == "goodnum" -> {
                    filterStarted.complete(Unit)
                    releaseFilter.await()
                    filterReturned.complete(Unit)
                    Result.success(recommendPage("goodnum"))
                }
                else -> Result.success(recommendPage("allvote"))
            }
        }
        try {
            withContext(Dispatchers.Main) {
                controller.ensureInitial("source-a", "revision-a", load)
                controller.selectPrimary("category", load)
            }
            withTimeout(5_000) { categoryStarted.await() }
            withContext(Dispatchers.Main) { controller.selectPrimary("recommend", load) }
            releaseCategory.complete(Unit)
            withTimeout(5_000) { categoryReturned.await() }
            withContext(Dispatchers.Main) {
                assertEquals("recommend", (controller.state as SourceHomeViewState.Content).selectedPrimary)
                assertEquals("推荐 allvote", controller.activePage?.sections?.single()?.title)
                controller.selectPrimary("category", load)
                assertEquals("category", controller.activePage?.sections?.single()?.title)
                assertEquals(2, requests.get())
                controller.selectPrimary("recommend", load)
                controller.selectFilters(mapOf("view" to "recommend", "recommendation" to "goodnum"), load)
            }
            withTimeout(5_000) { filterStarted.await() }
            withContext(Dispatchers.Main) {
                controller.selectFilters(mapOf("view" to "recommend", "recommendation" to "allvote"), load)
            }
            releaseFilter.complete(Unit)
            withTimeout(5_000) { filterReturned.await() }
            withContext(Dispatchers.Main) {
                assertEquals("allvote", controller.selectedFilters["recommendation"])
                assertEquals("推荐 allvote", controller.activePage?.sections?.single()?.title)
                controller.selectFilters(mapOf("view" to "recommend", "recommendation" to "goodnum"), load)
                assertEquals("推荐 goodnum", controller.activePage?.sections?.single()?.title)
                assertEquals(3, requests.get())
            }
        } finally {
            releaseCategory.complete(Unit)
            releaseFilter.complete(Unit)
            controller.close()
        }
    }

    @Test
    fun cached_secondary_queries_restore_their_own_scroll_positions() = runBlocking {
        val controller = SourceHomeController()
        val load: suspend (Map<String, String>, String?) -> Result<SourceHomePage> = { filters, _ ->
            Result.success(recommendPage(filters["recommendation"] ?: "allvote"))
        }
        try {
            withContext(Dispatchers.Main) { controller.ensureInitial("source-a", "revision-a", load) }
            awaitContent(controller)
            val allVote = (controller.state as SourceHomeViewState.Content).activePageState!!
            withContext(Dispatchers.Main) {
                controller.updateScrollPosition("recommend", allVote.queryKey, index = 12, offset = 36)
                controller.selectFilters(
                    mapOf("view" to "recommend", "recommendation" to "goodnum"),
                    load,
                )
            }
            awaitContent(controller)
            val goodNum = (controller.state as SourceHomeViewState.Content).activePageState!!
            withContext(Dispatchers.Main) {
                controller.updateScrollPosition("recommend", goodNum.queryKey, index = 5, offset = 18)
                controller.selectFilters(
                    mapOf("view" to "recommend", "recommendation" to "allvote"),
                    load,
                )
            }

            val restoredAllVote = (controller.state as SourceHomeViewState.Content).activePageState!!
            assertEquals(allVote.queryKey, restoredAllVote.queryKey)
            assertEquals(12, restoredAllVote.firstVisibleItemIndex)
            assertEquals(36, restoredAllVote.firstVisibleItemScrollOffset)
            withContext(Dispatchers.Main) {
                controller.selectFilters(
                    mapOf("view" to "recommend", "recommendation" to "goodnum"),
                    load,
                )
            }
            val restoredGoodNum = (controller.state as SourceHomeViewState.Content).activePageState!!
            assertEquals(goodNum.queryKey, restoredGoodNum.queryKey)
            assertEquals(5, restoredGoodNum.firstVisibleItemIndex)
            assertEquals(18, restoredGoodNum.firstVisibleItemScrollOffset)
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
            withContext(Dispatchers.Main) { controller.ensureInitial("source-a", "revision-a", load) }
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
                ) delay(20)
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
    fun initial_failure_can_explicitly_recover_from_offline_cache() = runBlocking {
        val controller = SourceHomeController()
        val online: suspend (Map<String, String>, String?) -> Result<SourceHomePage> = { _, _ ->
            Result.failure(IllegalStateException("verification-required"))
        }
        val offline: suspend (Map<String, String>, String?) -> Result<SourceHomePage> = { _, _ ->
            Result.success(recommendPage("allvote"))
        }
        try {
            withContext(Dispatchers.Main) { controller.ensureInitial("source-a", "revision-a", online) }
            withTimeout(5_000) {
                while (controller.state !is SourceHomeViewState.Failure) delay(10)
            }

            withContext(Dispatchers.Main) { controller.useOfflineCache(offline) }
            awaitContent(controller)

            assertEquals("推荐 allvote", controller.activePage?.sections?.single()?.title)
        } finally {
            controller.close()
        }
    }

    @Test
    fun explicit_retry_after_initial_failure_issues_a_new_request() = runBlocking {
        val controller = SourceHomeController()
        var requests = 0
        val load: suspend (Map<String, String>, String?) -> Result<SourceHomePage> = { _, _ ->
            requests += 1
            if (requests == 1) Result.failure(IllegalStateException("initial-failure"))
            else Result.success(recommendPage("allvote"))
        }
        try {
            withContext(Dispatchers.Main) {
                controller.ensureInitial("source-a", "revision-a", load)
                assertTrue(controller.state is SourceHomeViewState.Failure)
                controller.retryReplacement(load)
            }
            awaitContent(controller)
            assertEquals(2, requests)
            assertEquals("推荐 allvote", controller.activePage?.sections?.single()?.title)
        } finally {
            controller.close()
        }
    }

    @Test
    fun cancelled_secondary_seed_is_not_a_cache_hit_and_reentry_preserves_its_filter() = runBlocking {
        val controller = SourceHomeController()
        val secondaryStarted = CompletableDeferred<Unit>()
        val releaseSecondary = CompletableDeferred<Unit>()
        var secondaryRequests = 0
        val load: suspend (Map<String, String>, String?) -> Result<SourceHomePage> = { filters, _ ->
            when {
                filters["view"] == "category" -> Result.success(primaryPage("category"))
                filters["recommendation"] == "goodnum" -> {
                    secondaryRequests += 1
                    if (secondaryRequests == 1) {
                        secondaryStarted.complete(Unit)
                        releaseSecondary.await()
                    }
                    Result.success(recommendPage("goodnum"))
                }
                else -> Result.success(recommendPage("allvote"))
            }
        }
        try {
            withContext(Dispatchers.Main) {
                controller.ensureInitial("source-a", "revision-a", load)
                controller.selectFilters(mapOf("view" to "recommend", "recommendation" to "goodnum"), load)
            }
            withTimeout(5_000) { secondaryStarted.await() }
            withContext(Dispatchers.Main) {
                controller.selectPrimary("category", load)
                controller.selectPrimary("recommend", load)
            }
            assertEquals(2, secondaryRequests)
            assertEquals("goodnum", controller.selectedFilters["recommendation"])
            assertEquals("推荐 goodnum", controller.activePage?.sections?.single()?.title)
        } finally {
            releaseSecondary.complete(Unit)
            controller.close()
        }
    }

    @Test
    fun late_append_cannot_overwrite_a_refreshed_first_page_or_cursor() = runBlocking {
        val controller = SourceHomeController()
        val appendStarted = CompletableDeferred<Unit>()
        val releaseAppend = CompletableDeferred<Unit>()
        var appendJob: kotlinx.coroutines.Job? = null
        var roots = 0
        val fresh = recommendPage("allvote").copy(
            sections = listOf(SourceHomeSection("catalog", "刷新结果", listOf(summary("fresh")))),
            nextCursor = "fresh-next",
            complete = false,
        )
        val load: suspend (Map<String, String>, String?) -> Result<SourceHomePage> = { _, cursor ->
            if (cursor == "old-next") {
                appendJob = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]
                appendStarted.complete(Unit)
                withContext(NonCancellable) { releaseAppend.await() }
                Result.success(recommendPage("allvote").copy(
                    sections = listOf(SourceHomeSection("catalog", "旧追加", listOf(summary("stale")))),
                ))
            } else if (cursor == "fresh-next") {
                Result.success(recommendPage("allvote").copy(
                    sections = listOf(SourceHomeSection("catalog", "刷新结果", listOf(summary("fresh-more")))),
                ))
            } else {
                roots += 1
                Result.success(if (roots == 1) recommendPage("allvote").copy(nextCursor = "old-next", complete = false) else fresh)
            }
        }
        try {
            withContext(Dispatchers.Main) {
                controller.ensureInitial("source-a", "revision-a", load)
                controller.append(load)
            }
            withTimeout(5_000) { appendStarted.await() }
            withContext(Dispatchers.Main) {
                controller.refresh(load)
                controller.append(load)
            }
            val freshItems = controller.activePage?.sections?.single()?.items?.map { it.identity.remoteBookId }
            assertEquals(listOf("fresh", "fresh-more"), freshItems)
            val settledFresh = controller.activePage
            releaseAppend.complete(Unit)
            withTimeout(5_000) { requireNotNull(appendJob).join() }
            assertEquals(settledFresh, controller.activePage)
            assertEquals(null, controller.activePage?.nextCursor)
        } finally {
            releaseAppend.complete(Unit)
            controller.close()
        }
    }

    @Test
    fun invalidating_session_cancels_all_cached_page_appends_before_reentry() = runBlocking {
        val controller = SourceHomeController()
        val cancelled = mutableSetOf<String>()
        var roots = 0
        val load: suspend (Map<String, String>, String?) -> Result<SourceHomePage> = { filters, cursor ->
            val primary = filters["view"] ?: "recommend"
            if (cursor != null) {
                try {
                    kotlinx.coroutines.awaitCancellation()
                } finally {
                    cancelled += primary
                }
            } else {
                roots += 1
                Result.success(primaryPage(primary).copy(nextCursor = "next", complete = false))
            }
        }
        try {
            withContext(Dispatchers.Main) {
                controller.ensureInitial("source-a", "revision-a", load)
                controller.append(load)
                controller.selectPrimary("category", load)
                controller.append(load)
                controller.invalidateSession()
                assertEquals(SourceHomeViewState.Idle, controller.state)
            }
            withTimeout(5_000) { while (cancelled.size != 2) delay(10) }
            assertEquals(setOf("recommend", "category"), cancelled)
            withContext(Dispatchers.Main) { controller.ensureInitial("source-b", "revision-b", load) }
            assertEquals(3, roots)
            assertEquals("recommend", controller.activePage?.selectedFilters?.get("view"))
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
            withContext(Dispatchers.Main) { controller.ensureInitial("source-a", "revision-a", load) }
            awaitContent(controller)
            val rootState = controller.state as SourceHomeViewState.Content
            withContext(Dispatchers.Main) {
                controller.updateScrollPosition("recommend", rootState.activePageState!!.queryKey, 4, 21)
                controller.openFeature(feature, load)
            }
            withTimeout(5_000) {
                while ((controller.state as? SourceHomeViewState.Content)?.title != feature.title) {
                    delay(10)
                }
            }
            val featureState = controller.state as SourceHomeViewState.Content
            assertTrue(featureState.featureOpen)
            assertEquals("文库部门 TOP10", featureState.activePage?.sections?.first()?.title)
            assertEquals(2, requests.get())

            withContext(Dispatchers.Main) { assertTrue(controller.navigateBackFromFeature(load)) }
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

    @Test
    fun verified_feature_renewal_preserves_query_and_refetches_the_previous_root_only_on_back() = runBlocking {
        val feature = SourceHomeFeature(
            id = "sugoi-2026",
            title = "这本轻小说真厉害！2026",
            supportingText = "TOP20 榜单",
            selectedFilters = mapOf("view" to "recommend", "feature" to "sugoi-2026"),
        )
        val root = featureRootPage(feature)
        val award = featureAwardPage()
        val requests = mutableListOf<Map<String, String>>()
        val returnQuery = mapOf("view" to "recommend", "season" to "summer")
        val returnStarted = CompletableDeferred<Unit>()
        val releaseReturn = CompletableDeferred<Unit>()
        val controller = SourceHomeController()
        val load: suspend (Map<String, String>, String?) -> Result<SourceHomePage> = { filters, _ ->
            requests += filters
            if (filters["feature"] == null && requests.size > 3) {
                returnStarted.complete(Unit)
                releaseReturn.await()
            }
            Result.success(if (filters["feature"] == feature.id) award else root)
        }
        try {
            withContext(Dispatchers.Main) {
                controller.ensureInitial("source-a", "revision-a", load)
                controller.selectFilters(returnQuery, load)
                controller.openFeature(feature, load)
                controller.acceptVerifiedPage(award)
                controller.retainVerifiedPageAfterSessionRenewal()
            }
            assertEquals(3, requests.size)
            assertEquals(feature.id, controller.selectedFilters["feature"])
            assertTrue((controller.state as SourceHomeViewState.Content).featureOpen)

            withContext(Dispatchers.Main) { controller.refresh(load) }
            assertEquals(feature.id, requests.last()["feature"])
            assertEquals(4, requests.size)
            withContext(Dispatchers.Main) { assertTrue(controller.navigateBackFromFeature(load)) }
            withTimeout(5_000) { returnStarted.await() }
            withContext(Dispatchers.Main) { controller.selectPrimary("recommend", load) }
            assertEquals(5, requests.size)
            assertEquals(returnQuery, controller.selectedFilters)
            releaseReturn.complete(Unit)
            awaitContent(controller)
            assertEquals(5, requests.size)
            assertEquals(returnQuery, requests.last())
            assertTrue(!(controller.state as SourceHomeViewState.Content).featureOpen)
            assertEquals(root, controller.activePage)
        } finally {
            controller.close()
        }
    }
    @Test
    fun different_source_identity_replaces_home_and_rejects_a_delayed_old_result() = runBlocking {
        val oldLoadStarted = CompletableDeferred<Unit>()
        val releaseOldLoad = CompletableDeferred<Unit>()
        var oldRequest: kotlinx.coroutines.Job? = null
        val controller = SourceHomeController()
        val oldLoad: suspend (Map<String, String>, String?) -> Result<SourceHomePage> = { _, _ ->
            oldRequest = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]
            oldLoadStarted.complete(Unit)
            withContext(NonCancellable) { releaseOldLoad.await() }
            Result.success(recommendPage("allvote"))
        }
        val newLoad: suspend (Map<String, String>, String?) -> Result<SourceHomePage> = { _, _ ->
            Result.success(recommendPage("goodnum"))
        }
        try {
            withContext(Dispatchers.Main) { controller.ensureInitial("source-a", "revision-a", oldLoad) }
            oldLoadStarted.await()

            withContext(Dispatchers.Main) { controller.ensureInitial("source-b", "revision-b", newLoad) }
            val targetPublished = withTimeoutOrNull(5_000) {
                while (controller.activePage?.sections?.single()?.title != "推荐 goodnum") delay(10)
                true
            } ?: false
            check(targetPublished) { "Target source Home was not published: ${controller.state}" }
            releaseOldLoad.complete(Unit)
            withTimeout(5_000) { requireNotNull(oldRequest).join() }
            assertEquals("推荐 goodnum", controller.activePage?.sections?.single()?.title)
        } finally {
            releaseOldLoad.complete(Unit)
            controller.close()
        }
    }


    private suspend fun awaitContent(controller: SourceHomeController) {
        withTimeout(5_000) {
            while ((controller.state as? SourceHomeViewState.Content)?.activePage == null) {
                delay(10)
            }
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

    private fun primaryPage(view: String): SourceHomePage {
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
            selectedFilters = mapOf("view" to view),
            sections = listOf(SourceHomeSection(view, view, listOf(summary(view)))),
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
