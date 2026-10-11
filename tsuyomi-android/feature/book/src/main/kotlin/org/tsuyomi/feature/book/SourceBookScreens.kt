/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.book

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.ReaderNavigationTarget
import org.tsuyomi.shared.sourcecontract.SourceBookDetail
import org.tsuyomi.shared.sourcecontract.SourceDirectory
import org.tsuyomi.shared.sourcecontract.SourceDiagnostic
import org.tsuyomi.shared.sourcecontract.SourceErrorCode

@Immutable
data class BookDetailContent(
    val identity: BookIdentity,
    val title: String,
    val author: String? = null,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val status: String? = null,
    val lastUpdatedDate: String? = null,
)

@Immutable
data class BookDirectory(
    val identity: BookIdentity,
    val navigationTargets: List<ReaderNavigationTarget>,
    val authoredNavigation: Boolean = false,
) {
    init {
        require(navigationTargets.isNotEmpty()) { "Directory cannot be empty" }
        require(!authoredNavigation || identity is BookIdentity.Local) { "Authored publication navigation requires a local book" }
        require(authoredNavigation || navigationTargets.map { it.contentId }.distinct().size == navigationTargets.size) {
            "Duplicate chapter identity"
        }
    }
}

sealed interface BookScreenState<out T> {
    data object Loading : BookScreenState<Nothing>
    data class Content<T>(val value: T) : BookScreenState<T>
    data class Failure(
        val message: String? = null,
        val verificationRequired: Boolean = false,
        val offlineCacheAvailable: Boolean = !verificationRequired,
        val retryLabel: String? = null,
    ) : BookScreenState<Nothing>
}

sealed interface SourceBookState<out T> {
    data object Loading : SourceBookState<Nothing>
    data class Content<T>(val value: T) : SourceBookState<T>
    data class Failure(val code: SourceErrorCode, val diagnostic: SourceDiagnostic) : SourceBookState<Nothing>
}

@Composable
fun BookDetailScreen(
    state: BookScreenState<BookDetailContent>,
    directoryState: BookScreenState<BookDirectory>,
    localState: DetailLocalState,
    mutation: DetailMutationStatus?,
    coverState: CoverUiState,
    unreadOnly: Boolean,
    descending: Boolean,
    selectedChapterId: String?,
    cacheState: DetailCacheState,
    onCacheAction: (DetailCacheAction) -> Unit,
    onSetRating: (Int?) -> Unit,
    onSearchAuthor: ((String) -> Unit)?,
    onToggleUnreadOnly: () -> Unit,
    onToggleOrder: () -> Unit,
    onSelectChapter: (ReaderNavigationTarget) -> Unit,
    onContinueReading: (ReaderNavigationTarget) -> Unit,
    onAddToLibrary: () -> Unit,
    onRequestRemoveFromLibrary: () -> Unit,
    onRetry: () -> Unit,
    onUseOfflineCache: () -> Unit,
    onOpenVerification: () -> Unit,
    modifier: Modifier = Modifier,
    tagEditorOpen: Boolean = false,
    tagDraft: String = "",
    onOpenTagEditor: () -> Unit = {},
    onTagDraftChange: (String) -> Unit = {},
    onDismissTagEditor: () -> Unit = {},
    onConfirmTag: () -> Unit = {},
    onOpenDestinations: () -> Unit = {},
    destinationMenuExpanded: Boolean = false,
    onDestinationMenuExpandedChange: (Boolean) -> Unit = {},
    destinationMenuItemCount: Int = 0,
    destinationMenuItemKey: (Int) -> String = { it.toString() },
    destinationMenuItemContent: @Composable ColumnScope.(Int, dismissMenu: () -> Unit) -> Unit = { _, _ -> },
    destinationMessage: String? = null,
    partialMoveTargetName: String? = null,
    onRetryMoveOnly: () -> Unit = {},
    onKeepDefaultLibrary: () -> Unit,
    onRetryRemoteReconciliation: () -> Unit = {},
    onResyncRemoteLibrary: () -> Unit = {},
    resyncMessage: String? = null,
    focusChapterId: String? = null,
    onFocusHandled: () -> Unit = {},
) {
    StandardBookDetailScreen(
        state = state,
        directoryState = directoryState,
        localState = localState,
        mutation = mutation,
        coverState = coverState,
        unreadOnly = unreadOnly,
        descending = descending,
        selectedChapterId = selectedChapterId,
        cacheState = cacheState,
        onCacheAction = onCacheAction,
        onSetRating = onSetRating,
        onSearchAuthor = onSearchAuthor,
        tagEditorOpen = tagEditorOpen,
        tagDraft = tagDraft,
        onOpenTagEditor = onOpenTagEditor,
        onTagDraftChange = onTagDraftChange,
        onDismissTagEditor = onDismissTagEditor,
        onConfirmTag = onConfirmTag,
        onToggleUnreadOnly = onToggleUnreadOnly,
        onToggleOrder = onToggleOrder,
        onSelectChapter = onSelectChapter,
        onContinueReading = onContinueReading,
        onAddToLibrary = onAddToLibrary,
        onRequestRemoveFromLibrary = onRequestRemoveFromLibrary,
        onOpenDestinations = onOpenDestinations,
        destinationMenuExpanded = destinationMenuExpanded,
        onDestinationMenuExpandedChange = onDestinationMenuExpandedChange,
        destinationMenuItemCount = destinationMenuItemCount,
        destinationMenuItemKey = destinationMenuItemKey,
        destinationMenuItemContent = destinationMenuItemContent,
        destinationMessage = destinationMessage,
        partialMoveTargetName = partialMoveTargetName,
        onRetryMoveOnly = onRetryMoveOnly,
        onKeepDefaultLibrary = onKeepDefaultLibrary,
        onRetry = onRetry,
        onUseOfflineCache = onUseOfflineCache,
        onOpenVerification = onOpenVerification,
        onRetryRemoteReconciliation = onRetryRemoteReconciliation,
        onResyncRemoteLibrary = onResyncRemoteLibrary,
        resyncMessage = resyncMessage,
        focusChapterId = focusChapterId,
        onFocusHandled = onFocusHandled,
        einkPagination = LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK,
        modifier = modifier,
    )
}
