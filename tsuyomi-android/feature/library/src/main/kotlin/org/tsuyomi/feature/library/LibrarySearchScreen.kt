/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.shared.librarydomain.CollectionKind
import org.tsuyomi.shared.librarydomain.LibraryCollection
import org.tsuyomi.shared.librarydomain.LibraryEntry
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.media.api.FallbackSpec
import org.tsuyomi.core.ui.components.StateView
import org.tsuyomi.core.ui.components.TsuyomiIconButton
import org.tsuyomi.core.ui.components.PaginationBar
import org.tsuyomi.core.ui.components.TsuyomiMeasuredPager
import org.tsuyomi.core.ui.components.rememberTsuyomiMeasuredPagerState
import org.tsuyomi.core.ui.components.TsuyomiMenuItem
import org.tsuyomi.core.ui.components.TsuyomiMeasuredTextPager
import org.tsuyomi.core.ui.components.LocalTsuyomiPagerMaxContentHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import org.tsuyomi.core.ui.components.TsuyomiTextField
import org.tsuyomi.core.ui.components.TsuyomiStateKind
import org.tsuyomi.core.ui.icons.TsuyomiIcons
import org.tsuyomi.core.ui.theme.TsuyomiSpacing

internal data class LocalLibrarySearchResults(
    val collections: List<LibraryCollection>,
    val books: List<LibraryEntry>,
)

internal fun searchLocalLibrary(
    query: String,
    books: List<LibraryEntry>,
    collections: List<LibraryCollection>,
): LocalLibrarySearchResults {
    val normalizedQuery = query.normalizedSearchText()
    if (normalizedQuery.isEmpty()) return LocalLibrarySearchResults(emptyList(), emptyList())

    val matchedCollections = collections
        .asSequence()
        .filter { it.kind == CollectionKind.MANUAL || it.kind == CollectionKind.SMART }
        .filter { it.title.normalizedSearchText().contains(normalizedQuery) }
        .sortedBy { it.title.normalizedSearchText() }
        .toList()
    val matchedBooks = books
        .asSequence()
        .filter { entry ->
            sequenceOf(entry.book.title, entry.book.author.orEmpty())
                .plus(entry.book.authors.asSequence())
                .plus(entry.localTags.asSequence())
                .plus(entry.book.remoteTags.asSequence())
                .any { value -> value.normalizedSearchText().contains(normalizedQuery) }
        }
        .sortedBy { it.book.title.normalizedSearchText() }
        .toList()
    return LocalLibrarySearchResults(matchedCollections, matchedBooks)
}

internal fun recommendLocalLibrary(
    books: List<LibraryEntry>,
    collections: List<LibraryCollection>,
    preferredCollectionId: String?,
): LocalLibrarySearchResults {
    val recommendedBooks = books
        .sortedWith(
            compareByDescending<LibraryEntry> { it.progress != null }
                .thenByDescending { it.progress?.updatedAt ?: it.libraryAddedAt }
                .thenByDescending { it.libraryAddedAt }
                .thenBy { it.book.title.normalizedSearchText() },
        )
        .take(MaxBookRecommendations)
    val recommendedCollections = collections
        .asSequence()
        .filter { it.kind == CollectionKind.MANUAL || it.kind == CollectionKind.SMART }
        .sortedWith(
            compareByDescending<LibraryCollection> { it.collectionId == preferredCollectionId }
                .thenBy { it.displayOrder }
                .thenBy { it.title.normalizedSearchText() },
        )
        .take(MaxCollectionRecommendations)
        .toList()
    return LocalLibrarySearchResults(recommendedCollections, recommendedBooks)
}

private fun String.normalizedSearchText(): String = Normalizer
    .normalize(this, Normalizer.Form.NFKC)
    .lowercase(Locale.ROOT)
    .trim()
    .replace(Whitespace, " ")

private val Whitespace = Regex("\\s+")

@Composable
fun LibrarySearchScreen(
    query: String,
    searchQuery: String,
    books: List<LibraryEntry>,
    collections: List<LibraryCollection>,
    preferredCollectionId: String?,
    loading: Boolean,
    failure: String?,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onRetry: () -> Unit,
    onOpenCollection: (LibraryCollection) -> Unit,
    onOpenBook: (LibraryEntry) -> Unit,
    modifier: Modifier = Modifier,
    coverState: @Composable (LibraryEntry) -> CoverUiState = { entry ->
        val sourceId = (entry.book.identity as? BookIdentity.Remote)?.sourceId
        CoverUiState.Fallback(FallbackSpec(entry.book.title, sourceId))
    },
    onCoverVisibility: (LibraryEntry, Boolean) -> Unit = { _, _ -> },
) {
    var content by remember(books, collections, preferredCollectionId) {
        mutableStateOf(recommendLocalLibrary(books, collections, preferredCollectionId))
    }
    LaunchedEffect(searchQuery, books, collections, preferredCollectionId) {
        content = withContext(Dispatchers.Default) {
            if (searchQuery.isBlank()) {
                recommendLocalLibrary(books, collections, preferredCollectionId)
            } else {
                searchLocalLibrary(searchQuery, books, collections)
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        TsuyomiTextField(
            value = query,
            onValueChange = { value -> onQueryChange(value.take(MaxQueryLength)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Sm),
            label = stringResource(R.string.library_search_query_label),
            trailingIcon = {
                TsuyomiIconButton(
                    imageVector = TsuyomiIcons.Search,
                    contentDescription = stringResource(R.string.library_search_submit),
                    enabled = query.isNotBlank(),
                    onClick = onSearch,
                )
            },
            supportingText = stringResource(R.string.library_search_query_count, query.length, MaxQueryLength),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) onSearch() }),
        )

        when {
            loading -> StateView(
                kind = TsuyomiStateKind.LOADING,
                title = stringResource(R.string.library_loading),
                modifier = Modifier.weight(1f),
            )
            failure != null -> StateView(
                kind = TsuyomiStateKind.ERROR,
                title = stringResource(R.string.library_load_failed),
                message = failure,
                actionLabel = stringResource(R.string.library_retry),
                onAction = onRetry,
                modifier = Modifier.weight(1f),
            )
            else -> {
                if (content.collections.isEmpty() && content.books.isEmpty()) {
                    StateView(
                        kind = TsuyomiStateKind.EMPTY,
                        title = if (searchQuery.isBlank()) {
                            stringResource(R.string.library_search_idle_title)
                        } else {
                            stringResource(R.string.library_search_empty_title, searchQuery)
                        },
                        message = if (searchQuery.isBlank()) {
                            stringResource(R.string.library_search_idle_message)
                        } else {
                            stringResource(R.string.library_search_empty_message)
                        },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    LocalLibrarySearchResultsList(
                        searchQuery = searchQuery,
                        results = content,
                        recommendations = searchQuery.isBlank(),
                        onOpenCollection = onOpenCollection,
                        onOpenBook = onOpenBook,
                        coverState = coverState,
                        onCoverVisibility = onCoverVisibility,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun LocalLibrarySearchResultsList(
    searchQuery: String,
    results: LocalLibrarySearchResults,
    recommendations: Boolean,
    onOpenCollection: (LibraryCollection) -> Unit,
    onOpenBook: (LibraryEntry) -> Unit,
    coverState: @Composable (LibraryEntry) -> CoverUiState,
    onCoverVisibility: (LibraryEntry, Boolean) -> Unit,
    modifier: Modifier,
) {
    val eink = LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK
    if (!eink) {
        LazyColumn(
            state = rememberLazyListState(),
            modifier = modifier.fillMaxSize(),
        ) {
            if (recommendations) {
                bookSearchItems(
                    books = results.books,
                    headingResId = R.string.library_search_recommended_books_heading,
                    onOpenBook = onOpenBook,
                    coverState = coverState,
                    onCoverVisibility = onCoverVisibility,
                )
                collectionSearchItems(results.collections, onOpenCollection)
            } else {
                collectionSearchItems(results.collections, onOpenCollection)
                bookSearchItems(
                    books = results.books,
                    headingResId = R.string.library_search_books_heading,
                    onOpenBook = onOpenBook,
                    coverState = coverState,
                    onCoverVisibility = onCoverVisibility,
                )
            }
        }
        return
    }

    val itemCount = results.books.size + results.collections.size +
        (if (results.books.isNotEmpty()) 1 else 0) + (if (results.collections.isNotEmpty()) 1 else 0)
    androidx.compose.runtime.key(searchQuery, recommendations) {
        val pagerState = rememberTsuyomiMeasuredPagerState(
            initialAnchorKey = results.searchPageItemAt(recommendations, 0).key,
        )
        TsuyomiMeasuredPager(
            itemCount = itemCount,
            itemKey = { index -> results.searchPageItemAt(recommendations, index).key },
            itemContent = { index ->
                val item = results.searchPageItemAt(recommendations, index)
                when {
                    item.headingResId != null -> SearchSectionHeading(stringResource(item.headingResId))
                    item.collection != null -> CollectionSearchPageItem(item.collection, onOpenCollection)
                    item.book != null -> BookSearchPageItem(item.book, onOpenBook, coverState, onCoverVisibility)
                    else -> error("Search page item has no content")
                }
            },
            modifier = modifier.fillMaxSize(),
            state = pagerState,
            pageControls = { page, canGoPrevious, canGoNext, onPrevious, onNext, pageCount, onFirst, onLast ->
                PaginationBar(
                    page = page,
                    pageCount = pageCount,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    hasPreviousPage = canGoPrevious,
                    hasNextPage = canGoNext,
                    onFirst = onFirst,
                    onLast = onLast,
                    modifier = Modifier.testTag("library-search-eink-pagination"),
                )
            },
        )
    }
}

private data class SearchPageItem(
    val key: String,
    val headingResId: Int? = null,
    val collection: LibraryCollection? = null,
    val book: LibraryEntry? = null,
)

private fun LocalLibrarySearchResults.searchPageItemAt(
    recommendations: Boolean,
    index: Int,
): SearchPageItem {
    var remaining = index
    fun booksSection(headingResId: Int): SearchPageItem? {
        if (books.isEmpty()) return null
        if (remaining == 0) return SearchPageItem("books-heading:$headingResId", headingResId = headingResId)
        remaining--
        if (remaining < books.size) {
            val book = books[remaining]
            return SearchPageItem("book:${entryKey(book)}", book = book)
        }
        remaining -= books.size
        return null
    }
    fun collectionsSection(): SearchPageItem? {
        if (collections.isEmpty()) return null
        if (remaining == 0) return SearchPageItem("collections-heading", headingResId = R.string.library_search_collections_heading)
        remaining--
        if (remaining < collections.size) {
            val collection = collections[remaining]
            return SearchPageItem("collection:${collection.collectionId}", collection = collection)
        }
        remaining -= collections.size
        return null
    }
    val item = if (recommendations) {
        booksSection(R.string.library_search_recommended_books_heading) ?: collectionsSection()
    } else {
        collectionsSection() ?: booksSection(R.string.library_search_books_heading)
    }
    return requireNotNull(item) { "Search page index is outside its item count" }
}

@Composable
private fun CollectionSearchPageItem(
    collection: LibraryCollection,
    onOpenCollection: (LibraryCollection) -> Unit,
) {
    TsuyomiMeasuredPager(itemCount = 2, itemKey = { "collection:${collection.collectionId}:$it" }, fillViewport = false,
        itemContent = { index ->
            if (index == 0) TsuyomiMenuItem(collection.title, { onOpenCollection(collection) })
            else TsuyomiMeasuredTextPager(stringResource(when (collection.kind) {
                CollectionKind.MANUAL -> R.string.collection_kind_manual
                CollectionKind.SMART -> R.string.collection_kind_smart
                CollectionKind.SUBSCRIPTION -> R.string.collection_kind_disabled_draft
            }), fillViewport = false, alwaysShowPageControls = false)
        })
}

@Composable
private fun BookSearchPageItem(
    entry: LibraryEntry,
    onOpenBook: (LibraryEntry) -> Unit,
    coverState: @Composable (LibraryEntry) -> CoverUiState,
    onCoverVisibility: (LibraryEntry, Boolean) -> Unit,
) {
    TsuyomiMeasuredPager(itemCount = 3, itemKey = { "${entryKey(entry)}:$it" }, fillViewport = false,
        itemContent = { index -> when (index) {
            0 -> ProductionBookCover(entry, coverState, onCoverVisibility,
                Modifier.height(minOf(64.dp, LocalTsuyomiPagerMaxContentHeight.current ?: 64.dp)).width(48.dp))
            1 -> TsuyomiMenuItem(entry.book.title, { onOpenBook(entry) })
            else -> TsuyomiMeasuredTextPager(entry.book.authors.ifEmpty { setOf(stringResource(R.string.library_unknown_author)) }.joinToString("、"), fillViewport = false, alwaysShowPageControls = false)
        } })
}

private fun LazyListScope.collectionSearchItems(
    collections: List<LibraryCollection>,
    onOpenCollection: (LibraryCollection) -> Unit,
) {
    if (collections.isEmpty()) return
    item(key = "collections-heading") {
        SearchSectionHeading(stringResource(R.string.library_search_collections_heading))
    }
    items(collections, key = { collection -> "collection:${collection.collectionId}" }) { collection ->
        ListItem(
            headlineContent = { Text(collection.title) },
            supportingContent = {
                Text(
                    stringResource(
                        when (collection.kind) {
                            CollectionKind.MANUAL -> R.string.collection_kind_manual
                            CollectionKind.SMART -> R.string.collection_kind_smart
                            CollectionKind.SUBSCRIPTION -> R.string.collection_kind_disabled_draft
                        },
                    ),
                )
            },
            leadingContent = {
                Icon(
                    imageVector = if (collection.kind == CollectionKind.SMART) {
                        TsuyomiIcons.SmartCollection
                    } else {
                        TsuyomiIcons.Folder
                    },
                    contentDescription = null,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenCollection(collection) },
        )
        HorizontalDivider()
    }
}

private fun LazyListScope.bookSearchItems(
    books: List<LibraryEntry>,
    headingResId: Int,
    onOpenBook: (LibraryEntry) -> Unit,
    coverState: @Composable (LibraryEntry) -> CoverUiState,
    onCoverVisibility: (LibraryEntry, Boolean) -> Unit,
) {
    if (books.isEmpty()) return
    item(key = "books-heading") {
        SearchSectionHeading(stringResource(headingResId))
    }
    items(books, key = { entry -> "book:${entry.book.identity.storageKey}" }) { entry ->
        ListItem(
            headlineContent = { Text(entry.book.title) },
            supportingContent = {
                Text(entry.book.authors.ifEmpty { setOf(stringResource(R.string.library_unknown_author)) }.joinToString("、"))
            },
            leadingContent = {
                ProductionBookCover(
                    entry = entry,
                    coverState = coverState,
                    onCoverVisibility = onCoverVisibility,
                    modifier = Modifier.size(width = 48.dp, height = 64.dp),
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenBook(entry) },
        )
        HorizontalDivider()
    }
}

@Composable
private fun SearchSectionHeading(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { heading() }
            .padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Sm),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}

private const val MaxQueryLength = 100
private const val MaxBookRecommendations = 6
private const val MaxCollectionRecommendations = 6
