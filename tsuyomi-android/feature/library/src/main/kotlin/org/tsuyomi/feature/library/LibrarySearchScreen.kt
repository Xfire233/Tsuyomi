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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale
import org.tsuyomi.shared.librarydomain.CollectionKind
import org.tsuyomi.shared.librarydomain.LibraryCollection
import org.tsuyomi.shared.librarydomain.LibraryEntry
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.media.api.FallbackSpec
import org.tsuyomi.core.ui.components.StateView
import org.tsuyomi.core.ui.components.TsuyomiIconButton
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
        CoverUiState.Fallback(FallbackSpec(entry.book.title, entry.book.identity.sourceId))
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
    results: LocalLibrarySearchResults,
    recommendations: Boolean,
    onOpenCollection: (LibraryCollection) -> Unit,
    onOpenBook: (LibraryEntry) -> Unit,
    coverState: @Composable (LibraryEntry) -> CoverUiState,
    onCoverVisibility: (LibraryEntry, Boolean) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(modifier) {
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
    items(books, key = { entry -> "book:${entry.book.identity.sourceId}:${entry.book.identity.remoteBookId}" }) { entry ->
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
