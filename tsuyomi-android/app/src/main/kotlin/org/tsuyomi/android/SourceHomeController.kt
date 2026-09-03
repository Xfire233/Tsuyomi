/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.Closeable
import java.util.LinkedHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.tsuyomi.feature.browse.SourceHomeFailure
import org.tsuyomi.feature.browse.SourceHomePageViewState
import org.tsuyomi.feature.browse.SourceHomeViewState
import org.tsuyomi.shared.sourcecontract.SourceHomeFilter
import org.tsuyomi.shared.sourcecontract.SourceHomeFeature
import org.tsuyomi.shared.sourcecontract.SourceHomePage
import org.tsuyomi.shared.sourcecontract.SourceException

internal class SourceHomeController : Closeable {
    private data class CacheEntry(
        val queryKey: String,
        val selectedFilters: Map<String, String>,
        val page: SourceHomePage? = null,
        val replacing: Boolean = false,
        val appending: Boolean = false,
        val replacementFailure: SourceHomeFailure? = null,
        val appendFailure: SourceHomeFailure? = null,
        val firstVisibleItemIndex: Int = 0,
        val firstVisibleItemScrollOffset: Int = 0,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val cache = LinkedHashMap<String, CacheEntry>(MAX_CACHED_QUERIES, 0.75f, true)
    private val lastQueryByPrimary = linkedMapOf<String, String>()
    private val appendJobs = mutableMapOf<String, Job>()
    private var replacementJob: Job? = null
    private var packageRevision: String? = null
    private var generation = 0L
    private var title = ""
    private var primaryFilter: SourceHomeFilter? = null
    private var activePrimary = DEFAULT_PRIMARY
    private var activeQueryKey: String? = null
    private var featureReturnQueryKey: String? = null
    private var featureTitle: String? = null

    var state: SourceHomeViewState by mutableStateOf(SourceHomeViewState.Idle)
        private set

    val activePage: SourceHomePage?
        get() = (state as? SourceHomeViewState.Content)?.activePage

    fun ensureInitial(
        revision: String?,
        load: suspend (Map<String, String>, String?) -> Result<SourceHomePage>,
    ) {
        ensurePackageRevision(revision)
        if (state !is SourceHomeViewState.Idle) return
        state = SourceHomeViewState.Loading
        startReplacement(
            requestedFilters = emptyMap(),
            primary = DEFAULT_PRIMARY,
            force = true,
            initial = true,
            load = load,
        )
    }

    fun selectPrimary(
        value: String,
        load: suspend (Map<String, String>, String?) -> Result<SourceHomePage>,
    ) {
        val filter = primaryFilter ?: return
        if (filter.options.none { it.value == value }) return
        activePrimary = value
        val cachedKey = lastQueryByPrimary[value]
        val cached = cachedKey?.let(cache::get)
        if (cached?.page != null) {
            activeQueryKey = cachedKey
            cache[cachedKey] = cached.copy(replacing = false, replacementFailure = null)
            publish()
            return
        }
        startReplacement(
            requestedFilters = mapOf(filter.id to value),
            primary = value,
            force = false,
            initial = false,
            load = load,
        )
    }

    fun selectFilters(
        filters: Map<String, String>,
        load: suspend (Map<String, String>, String?) -> Result<SourceHomePage>,
    ) {
        val primary = primaryValue(filters)
        val key = queryKey(filters)
        val cached = cache[key]
        activePrimary = primary
        if (cached?.page != null) {
            activeQueryKey = key
            lastQueryByPrimary[primary] = key
            cache[key] = cached.copy(replacing = false, replacementFailure = null)
            publish()
            return
        }
        startReplacement(filters, primary, force = false, initial = false, load = load)
    }
    fun openFeature(
        feature: SourceHomeFeature,
        load: suspend (Map<String, String>, String?) -> Result<SourceHomePage>,
    ) {
        val current = activeEntry() ?: return
        if (featureReturnQueryKey != null) return
        if (current.page?.features?.none { it == feature } != false) return
        featureReturnQueryKey = current.queryKey
        featureTitle = feature.title
        startReplacement(
            requestedFilters = feature.selectedFilters,
            primary = activePrimary,
            force = false,
            initial = false,
            load = load,
        )
    }

    fun navigateBackFromFeature(): Boolean {
        val returnKey = featureReturnQueryKey ?: return false
        val returnEntry = cache[returnKey] ?: return false
        generation += 1L
        replacementJob?.cancel()
        replacementJob = null
        featureReturnQueryKey = null
        featureTitle = null
        activePrimary = primaryValue(returnEntry.selectedFilters)
        activeQueryKey = returnKey
        lastQueryByPrimary[activePrimary] = returnKey
        title = returnEntry.page?.title ?: title
        primaryFilter = returnEntry.page?.filters?.firstOrNull() ?: primaryFilter
        publish()
        return true
    }


    fun refresh(load: suspend (Map<String, String>, String?) -> Result<SourceHomePage>) {
        val entry = activeEntry() ?: return
        startReplacement(
            requestedFilters = entry.selectedFilters,
            primary = activePrimary,
            force = true,
            initial = entry.page == null && primaryFilter == null,
            load = load,
        )
    }

    fun retryReplacement(load: suspend (Map<String, String>, String?) -> Result<SourceHomePage>) {
        val entry = activeEntry()
        if (entry == null) {
            ensureInitial(packageRevision, load)
        } else {
            startReplacement(
                requestedFilters = entry.selectedFilters,
                primary = activePrimary,
                force = true,
                initial = entry.page == null && primaryFilter == null,
                load = load,
            )
        }
    }

    fun append(load: suspend (Map<String, String>, String?) -> Result<SourceHomePage>) {
        val key = activeQueryKey ?: return
        val entry = cache[key] ?: return
        val page = entry.page ?: return
        val cursor = page.nextCursor ?: return
        if (page.complete || entry.appending || appendJobs[key]?.isActive == true) return
        cache[key] = entry.copy(appending = true, appendFailure = null)
        publish()
        appendJobs[key] = scope.launch {
            val result = try {
                load(entry.selectedFilters, cursor)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Result.failure(error)
            }
            result.fold(
                onSuccess = { incoming ->
                    val current = cache[key] ?: return@fold
                    cache[key] = current.copy(
                        page = mergeHomePages(page, incoming),
                        appending = false,
                        appendFailure = null,
                        selectedFilters = normalizedEntrySelection(incoming, current.selectedFilters),
                    )
                    if (activeQueryKey == key) publish()
                },
                onFailure = { error ->
                    val current = cache[key] ?: return@fold
                    cache[key] = current.copy(
                        appending = false,
                        appendFailure = error.toHomeFailure(),
                    )
                    if (activeQueryKey == key) publish()
                },
            )
            appendJobs.remove(key)
        }
    }

    fun updateScrollPosition(primary: String, queryKey: String, index: Int, offset: Int) {
        if (lastQueryByPrimary[primary] != queryKey) return
        val entry = cache[queryKey] ?: return
        if (entry.firstVisibleItemIndex == index && entry.firstVisibleItemScrollOffset == offset) return
        cache[queryKey] = entry.copy(
            firstVisibleItemIndex = index.coerceAtLeast(0),
            firstVisibleItemScrollOffset = offset.coerceAtLeast(0),
        )
    }

    fun reset() {
        generation += 1L
        replacementJob?.cancel()
        replacementJob = null
        appendJobs.values.forEach(Job::cancel)
        appendJobs.clear()
        cache.clear()
        lastQueryByPrimary.clear()
        title = ""
        primaryFilter = null
        activePrimary = DEFAULT_PRIMARY
        activeQueryKey = null
        featureReturnQueryKey = null
        featureTitle = null
        state = SourceHomeViewState.Idle
    }

    private fun ensurePackageRevision(revision: String?) {
        if (packageRevision == revision) return
        reset()
        packageRevision = revision
    }

    private fun startReplacement(
        requestedFilters: Map<String, String>,
        primary: String,
        force: Boolean,
        initial: Boolean,
        load: suspend (Map<String, String>, String?) -> Result<SourceHomePage>,
    ) {
        val key = queryKey(requestedFilters)
        val cached = cache[key]
        if (!force && cached?.page != null) {
            activePrimary = primary
            activeQueryKey = key
            lastQueryByPrimary[primary] = key
            publish()
            return
        }

        val previousKey = activeQueryKey
        val previous = previousKey?.let(cache::get)
        val seed = previous?.takeIf { primaryValue(it.selectedFilters) == primary }
        val preserveScroll = force && previousKey == key
        val requestEntry = CacheEntry(
            queryKey = key,
            selectedFilters = requestedFilters,
            page = seed?.page,
            replacing = true,
            firstVisibleItemIndex = if (preserveScroll) seed?.firstVisibleItemIndex ?: 0 else 0,
            firstVisibleItemScrollOffset = if (preserveScroll) seed?.firstVisibleItemScrollOffset ?: 0 else 0,
        )
        activePrimary = primary
        activeQueryKey = key
        lastQueryByPrimary[primary] = key
        cache[key] = requestEntry
        trimCache()
        if (!initial || primaryFilter != null) publish() else state = SourceHomeViewState.Loading

        generation += 1L
        val requestGeneration = generation
        replacementJob?.cancel()
        replacementJob = scope.launch {
            val result = try {
                load(requestedFilters, null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Result.failure(error)
            }
            result.fold(
                onSuccess = { incoming ->
                    if (generation != requestGeneration) return@fold
                    acceptReplacement(key, incoming)
                },
                onFailure = { error ->
                    if (generation != requestGeneration) return@fold
                    rejectReplacement(key, error.toHomeFailure(), initial)
                },
            )
        }
    }

    private fun acceptReplacement(requestKey: String, incoming: SourceHomePage) {
        val incomingPrimaryFilter = incoming.filters.firstOrNull()
        val existing = cache[requestKey]
        val normalizedSelection = normalizedEntrySelection(incoming, existing?.selectedFilters.orEmpty())
        val normalizedPrimary = incomingPrimaryFilter
            ?.let { filter -> normalizedSelection[filter.id] }
            ?: DEFAULT_PRIMARY
        val normalizedKey = queryKey(normalizedSelection)
        if (normalizedKey != requestKey) cache.remove(requestKey)
        title = incoming.title
        if (featureReturnQueryKey != null) featureTitle = incoming.title
        primaryFilter = incomingPrimaryFilter
        activePrimary = normalizedPrimary
        activeQueryKey = normalizedKey
        cache[normalizedKey] = CacheEntry(
            queryKey = normalizedKey,
            selectedFilters = normalizedSelection,
            page = incoming,
            firstVisibleItemIndex = existing?.firstVisibleItemIndex ?: 0,
            firstVisibleItemScrollOffset = existing?.firstVisibleItemScrollOffset ?: 0,
        )
        lastQueryByPrimary[normalizedPrimary] = normalizedKey
        trimCache()
        publish()
    }

    private fun rejectReplacement(requestKey: String, failure: SourceHomeFailure, initial: Boolean) {
        val current = cache[requestKey]
        if (current == null || (initial && current.page == null && primaryFilter == null)) {
            cache.remove(requestKey)
            state = SourceHomeViewState.Failure(failure.code, failure.safeCode)
            return
        }
        cache[requestKey] = current.copy(replacing = false, replacementFailure = failure)
        publish()
    }

    private fun publish() {
        val filter = primaryFilter
        val values = filter?.options?.map { it.value } ?: listOf(DEFAULT_PRIMARY)
        if (activePrimary !in values) activePrimary = values.first()
        val pages = values.associateWith { primary ->
            val key = lastQueryByPrimary[primary]
            val entry = key?.let(cache::get)
            entry?.toViewState() ?: SourceHomePageViewState(
                queryKey = queryKey(filter?.let { mapOf(it.id to primary) }.orEmpty()),
                selectedFilters = filter?.let { mapOf(it.id to primary) }.orEmpty(),
            )
        }
        state = SourceHomeViewState.Content(
            title = featureTitle ?: title,
            primaryFilter = filter,
            selectedPrimary = activePrimary,
            pages = pages,
            featureOpen = featureReturnQueryKey != null,
        )
    }

    private fun activeEntry(): CacheEntry? = activeQueryKey?.let(cache::get)
    private fun normalizedEntrySelection(
        incoming: SourceHomePage,
        requested: Map<String, String>,
    ): Map<String, String> {
        val declaredFilterIds = incoming.filters.mapTo(mutableSetOf()) { it.id }
        return incoming.selectedFilters + requested.filterKeys { it !in declaredFilterIds }
    }

    private fun primaryValue(filters: Map<String, String>): String =
        primaryFilter?.let { filters[it.id] } ?: DEFAULT_PRIMARY

    private fun queryKey(filters: Map<String, String>): String = buildString {
        append(packageRevision.orEmpty())
        append('|')
        filters.toSortedMap().forEach { (id, value) ->
            append(id)
            append('=')
            append(value)
            append('&')
        }
    }

    private fun trimCache() {
        while (cache.size > MAX_CACHED_QUERIES) {
            val removable = cache.keys.firstOrNull {
                it != activeQueryKey && it != featureReturnQueryKey
            } ?: break
            cache.remove(removable)
            lastQueryByPrimary.entries.removeAll { it.value == removable }
        }
    }

    private fun CacheEntry.toViewState() = SourceHomePageViewState(
        queryKey = queryKey,
        selectedFilters = selectedFilters,
        page = page,
        replacing = replacing,
        appending = appending,
        replacementFailure = replacementFailure,
        appendFailure = appendFailure,
        firstVisibleItemIndex = firstVisibleItemIndex,
        firstVisibleItemScrollOffset = firstVisibleItemScrollOffset,
    )

    private fun Throwable.toHomeFailure(): SourceHomeFailure {
        val sourceFailure = this as? SourceException
        return SourceHomeFailure(
            code = sourceFailure?.code,
            safeCode = sourceFailure?.diagnostic?.safeCode ?: "source-home-runtime-failure",
        )
    }

    override fun close() {
        scope.cancel()
    }

    private companion object {
        const val DEFAULT_PRIMARY = "__home__"
        const val MAX_CACHED_QUERIES = 12
    }
}

private fun mergeHomePages(current: SourceHomePage, incoming: SourceHomePage): SourceHomePage {
    val incomingById = incoming.sections.associateBy { it.id }
    val mergedSections = current.sections.map { existing ->
        val next = incomingById[existing.id] ?: return@map existing
        existing.copy(
            title = next.title,
            items = (existing.items + next.items).distinctBy { it.identity },
        )
    } + incoming.sections.filter { next -> current.sections.none { it.id == next.id } }
    return incoming.copy(sections = mergedSections)
}
