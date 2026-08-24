/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.tsuyomi.prototype.uiatlas.AtlasStrings
import org.tsuyomi.prototype.uiatlas.components.AtlasIconButton
import org.tsuyomi.prototype.uiatlas.components.AtlasIcons
import org.tsuyomi.prototype.uiatlas.components.AtlasScaffold
import org.tsuyomi.prototype.uiatlas.components.AtlasStateKind
import org.tsuyomi.prototype.uiatlas.components.AtlasStateView
import org.tsuyomi.prototype.uiatlas.components.AtlasTopBar
import org.tsuyomi.prototype.uiatlas.components.AtlasTopBarAction
import org.tsuyomi.prototype.uiatlas.components.BookGridCard
import org.tsuyomi.prototype.uiatlas.components.BookListItemRow
import org.tsuyomi.prototype.uiatlas.components.CompactBookListItem
import org.tsuyomi.prototype.uiatlas.components.currentLayoutIcon
import org.tsuyomi.prototype.uiatlas.components.layoutToggleContentDescription
import org.tsuyomi.prototype.uiatlas.components.nextAtlasLayout
import org.tsuyomi.prototype.uiatlas.fixtures.SourceAtlasFixtures
import org.tsuyomi.prototype.uiatlas.model.AtlasBook
import org.tsuyomi.prototype.uiatlas.model.AtlasContext
import org.tsuyomi.prototype.uiatlas.model.AtlasLayout
import org.tsuyomi.prototype.uiatlas.model.AtlasPageState
import org.tsuyomi.prototype.uiatlas.model.AtlasRoute
import org.tsuyomi.prototype.uiatlas.model.LocalAtlasNavigation
import org.tsuyomi.prototype.uiatlas.runtime.LocalPrototypeRuntime
import org.tsuyomi.prototype.uiatlas.runtime.prototypeRepository
import org.tsuyomi.prototype.uiatlas.theme.AtlasSpacing
import org.tsuyomi.prototype.uiatlas.theme.LocalAtlasEnvironment
// -- #12 unified basic search ---------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GlobalSearch(context: AtlasContext, modifier: Modifier) {
    val runtime = LocalPrototypeRuntime.current
    val repository = prototypeRepository()
    val scope = rememberCoroutineScope()
    val eInk = LocalAtlasEnvironment.current.eInk
    val activeSourceIds = SourceAtlasFixtures.searchDescriptors
        .map { it.source }
        .filter { !it.dormant && !it.credentialExpired }
        .map { it.id }
        .toSet()
    val submittedSourceIds = context.selectedSearchSourceId
        ?.takeIf { it in activeSourceIds }
        ?.let(::setOf)
        ?: activeSourceIds
    var query by rememberSaveable(runtime.persistent) {
        mutableStateOf(if (runtime.persistent) repository.string("search.query", "雾港") else "雾港")
    }
    var submittedQuery by rememberSaveable { mutableStateOf(query) }
    var layout by rememberSaveable(context.layout?.name, runtime.persistent) {
        mutableStateOf(
            if (runtime.persistent) AtlasLayout.entries.firstOrNull { it.name == repository.string("search.layout") }
                ?: (context.layout ?: AtlasLayout.LIST) else context.layout ?: AtlasLayout.LIST,
        )
    }
    val submit: () -> Unit = {
        submittedQuery = query
        repository.putString("search.query", query, "SearchSubmitted", context.selectedSearchSourceId ?: "all")
        scope.launch { runtime.scenarios.run("search", context.selectedSearchSourceId ?: "all") }
    }
    AtlasScaffold(
        modifier = modifier,
        topBar = {
            AtlasTopBar(
                title = "搜索",
                actions = listOf(
                    AtlasTopBarAction(layout.currentLayoutIcon(), layout.layoutToggleContentDescription()) {
                        layout = layout.nextAtlasLayout()
                        repository.putString("search.layout", layout.name, "SearchLayoutChanged")
                    },
                ),
            )
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { if (it.length <= SourceAtlasFixtures.SEARCH_QUERY_CAP) query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = AtlasSpacing.Md, vertical = AtlasSpacing.Sm),
                label = { Text("搜索") },
                trailingIcon = { AtlasIconButton(AtlasIcons.Search, "提交搜索", submit) },
                supportingText = { Text("${query.length} / ${SourceAtlasFixtures.SEARCH_QUERY_CAP}") },
                singleLine = true,
            )
            when (context.primaryState) {
                AtlasPageState.LOADING -> AtlasStateView(AtlasStateKind.LOADING, "正在搜索", Modifier.weight(1f))
                AtlasPageState.EMPTY -> AtlasStateView(AtlasStateKind.EMPTY, "没有找到「$query」", Modifier.weight(1f), "可调整关键词后重新搜索。")
                AtlasPageState.ERROR -> AtlasStateView(AtlasStateKind.ERROR, "部分搜索失败", Modifier.weight(1f), "已完成的结果仍保留。", AtlasStrings.RETRY, submit)
                else -> AggregatedSearchResults(submittedQuery, layout, submittedSourceIds, eInk, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AggregatedSearchResults(
    submittedQuery: String,
    layout: AtlasLayout,
    submittedSourceIds: Set<String>,
    eInk: Boolean,
    modifier: Modifier,
) {
    val navigation = LocalAtlasNavigation.current
    val all = SourceAtlasFixtures.aggregatedSearchResults
    val submitted = submittedQuery.trim()
    val local = all.take(2).filter { submitted.isNotBlank() && it.title.contains(submitted, ignoreCase = true) }
    val remote = all.drop(2).filter {
        submitted.isNotBlank() && it.source?.id in submittedSourceIds && it.title.contains(submitted, ignoreCase = true)
    }
    val merged = buildList {
        addAll(local)
        remote.forEach { candidate ->
            if (candidate.identity == null || none { it.identity == candidate.identity }) add(candidate)
        }
    }.map { it.copy(dormantSource = false) }
    Column(if (eInk) modifier else modifier.verticalScroll(rememberScrollState())) {
        if (submitted.isBlank()) {
            AtlasStateView(AtlasStateKind.EMPTY, "输入关键词后搜索", message = "", modifier = Modifier.fillMaxWidth())
        } else {
            AggregatedSearchGroup(null, merged, layout) { navigation.navigate(AtlasRoute.BOOK_DETAIL) }
        }
    }
}
@Composable
private fun AggregatedSearchGroup(
    title: String?,
    books: List<AtlasBook>,
    layout: AtlasLayout,
    onBook: () -> Unit,
) {
    if (books.isEmpty()) return
    if (title != null) Section(title)
    when (layout) {
        AtlasLayout.LIST -> books.forEach { book -> BookListItemRow(book = book, onClick = onBook, showSourceChip = book.source != null) }
        AtlasLayout.COMPACT -> books.forEach { book -> CompactBookListItem(book = book, onClick = onBook) }
        AtlasLayout.GRID -> LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns()),
            modifier = Modifier.fillMaxWidth().heightIn(max = 720.dp),
            contentPadding = PaddingValues(AtlasSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(AtlasSpacing.Md),
            horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Md),
        ) {
            gridItems(books, key = { it.identity?.let { identity -> "${identity.sourceId}:${identity.remoteBookId}" } ?: it.id }) { book ->
                BookGridCard(book = book, onClick = onBook, showSourceChip = book.source != null)
            }
        }
    }
}
