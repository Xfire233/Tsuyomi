/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.core.content.IntentCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.tsuyomi.core.preferences.RequestedReaderFlow
import org.tsuyomi.core.ui.components.StateView
import org.tsuyomi.core.ui.components.TsuyomiButton
import org.tsuyomi.core.ui.components.TsuyomiCheckboxRow
import org.tsuyomi.core.ui.components.TsuyomiDialog
import org.tsuyomi.core.ui.components.TsuyomiMeasuredPager
import org.tsuyomi.core.ui.components.TsuyomiMeasuredTextPager
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.publication.PublicationDeleteStatus
import org.tsuyomi.core.ui.components.TsuyomiOverflowAction
import org.tsuyomi.core.ui.components.TsuyomiStateKind
import org.tsuyomi.core.ui.components.TsuyomiTextField
import org.tsuyomi.core.ui.components.TsuyomiTopBar
import org.tsuyomi.feature.book.BookDetailContent
import org.tsuyomi.feature.book.BookDetailScreen
import org.tsuyomi.feature.book.BookDestinationMenu
import org.tsuyomi.feature.book.bookDestinationMenuItemCount
import org.tsuyomi.feature.book.bookDestinationMenuItemKey
import org.tsuyomi.feature.book.BookDirectory
import org.tsuyomi.feature.book.BookScreenState
import org.tsuyomi.feature.book.DetailCacheState
import org.tsuyomi.feature.book.DetailCollectionDestination
import org.tsuyomi.feature.book.DetailMutationOperation
import org.tsuyomi.feature.library.LocalBookImportAction
import org.tsuyomi.feature.library.LocalBookImportScreen
import org.tsuyomi.feature.reader.ReaderScreen
import org.tsuyomi.shared.backup.PortableReaderPreferences
import org.tsuyomi.shared.librarydomain.CollectionKind
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.ReaderLinkTarget

internal const val LocalPublicationKey = "book.local.publication"
internal const val LocalContentKey = "book.local.content"
internal const val LocalFragmentKey = "book.local.fragment"
internal const val LocalSectionStartKey = "book.local.section.start"

internal class PublicationOwnerFactory(private val application: TsuyomiApplication) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = when (modelClass) {
        PublicationImportCoordinator::class.java -> PublicationImportCoordinator(application, extras.createSavedStateHandle())
        LocalBookFlow::class.java -> LocalBookFlow(application, extras.createSavedStateHandle())
        else -> error("Unknown publication state owner")
    } as T
}

/** Bound work and accept only temporary content grants, never a provider path or arbitrary URL. */
internal fun publicationIntentUris(intent: Intent?): List<Uri> {
    if (intent == null || intent.action !in setOf(Intent.ACTION_VIEW, Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)) return emptyList()
    val limit = PublicationImportCoordinator.MaxBatchFiles + 1
    val uris = linkedSetOf<Uri>()
    intent.data?.let(uris::add)
    when (intent.action) {
        Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let(uris::add)
        Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            ?.take(limit)?.let(uris::addAll)
    }
    intent.clipData?.let { clips ->
        for (index in 0 until minOf(clips.itemCount, limit)) clips.getItemAt(index).uri?.let(uris::add)
    }
    return uris.take(limit)
}

internal fun NavHostController.openLocalBook(publicationId: String, reader: Boolean = false, contentId: String? = null, fragment: String? = null, sectionStart: Boolean = false) {
    BookIdentity.Local(publicationId)
    navigateToBookRoute(if (reader) Routes.Reader else Routes.Detail)
    checkNotNull(currentBackStackEntry).savedStateHandle.apply {
        set(LocalPublicationKey, publicationId)
        contentId?.let { set(LocalContentKey, it) }
        fragment?.let { set(LocalFragmentKey, it) }
        if (sectionStart) set(LocalSectionStartKey, true)
    }
}

/** Remove only invalid book entries; unrelated callers retain their own saved state and anchors. */
internal fun NavHostController.pruneLocalBookRoutes(publicationId: String) {
    val callerRoot = currentBackStackEntry?.savedStateHandle?.get<String>(BookCallerRootKey) ?: Routes.Library
    while (currentBackStackEntry?.let { entry ->
        (entry.destination.route == Routes.Detail || entry.destination.route == Routes.Reader) &&
            entry.savedStateHandle.get<String>(LocalPublicationKey) == publicationId
    } == true) {
        if (!popBackStack()) {
            navigate(callerRoot) { launchSingleTop = true }
            return
        }
    }
}

@Composable
internal fun PublicationImportRoute(imports: PublicationImportCoordinator, navController: NavHostController) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), imports::enqueue)
    LocalBookImportScreen(imports.state, { action ->
        when (action) {
            LocalBookImportAction.ChooseFiles -> picker.launch(arrayOf("*/*"))
            is LocalBookImportAction.OpenBook -> navController.openLocalBook(action.publicationId)
            else -> imports.edit(action)
        }
    })
}

@Composable
internal fun LocalBookDetailRoute(
    flow: LocalBookFlow,
    navController: NavHostController,
    imports: PublicationImportCoordinator,
    library: LibraryFlowController,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val application = context.applicationContext as TsuyomiApplication
    val scope = rememberCoroutineScope()
    val id = flow.identity?.publicationId
    val owner = flow.lifecycleOwner
    var unread by rememberSaveable(id) { mutableStateOf(false) }
    var descending by rememberSaveable(id) { mutableStateOf(false) }
    var tagEditor by rememberSaveable(id) { mutableStateOf(false) }
    var tagDraft by rememberSaveable(id) { mutableStateOf("") }
    var destinations by rememberSaveable(id) { mutableStateOf(false) }
    var selectedCollections by remember(id) { mutableStateOf<Set<String>>(emptySet()) }
    var pendingDelete by rememberSaveable(id) { mutableStateOf<Boolean?>(null) }
    var resourceDialog by rememberSaveable(id) { mutableStateOf(false) }
    var origins by rememberSaveable(id) { mutableStateOf("") }
    var consent by rememberSaveable(id) { mutableStateOf(false) }
    var working by remember(id) { mutableStateOf(false) }
    var result by rememberSaveable(id) { mutableStateOf<String?>(null) }
    LaunchedEffect(id, flow.catalogGeneration) {
        flow.identity?.let { selectedCollections = application.libraryRepository.manualCollectionIds(it) }
    }
    fun open(contentId: String, fragment: String? = null, sectionStart: Boolean = false) {
        id?.let { navController.openLocalBook(it, reader = true, contentId = contentId, fragment = fragment, sectionStart = sectionStart) }
    }
    Column(Modifier.fillMaxSize()) {
        TsuyomiTopBar(
            title = stringResource(R.string.title_book_detail),
            onNavigateUp = { navController.navigateUp() },
            overflow = buildList {
                if (flow.localState.inLibrary) add(TsuyomiOverflowAction(resources.getString(R.string.publication_remove_from_library), {
                    pendingDelete = null
                    flow.removeFromLibrary(owner)
                }))
                if (flow.publication != null) add(TsuyomiOverflowAction(resources.getString(R.string.publication_resource_settings), {
                    origins = flow.resourceOrigins.joinToString("\n")
                    consent = false
                    resourceDialog = true
                }))
                if (flow.book?.localPublication?.originalAvailable == true) {
                    add(TsuyomiOverflowAction(resources.getString(R.string.publication_remove_original), { pendingDelete = true }, destructive = true))
                } else add(TsuyomiOverflowAction(resources.getString(R.string.publication_reassociate), imports::show))
                if (flow.book != null) add(TsuyomiOverflowAction(resources.getString(R.string.publication_delete_all), { pendingDelete = false }, destructive = true))
            },
        )
        result?.let { Text(it) }
        val book = flow.book
        val parsed = flow.publication
        val compatibility = remember(parsed, resources.configuration) {
            publicationCompatibilityMessages(resources, parsed?.compatibility?.findings.orEmpty())
        }
        val originalMissing = book?.localPublication?.originalAvailable == false
        val retryLabel = resources.getString(if (originalMissing) R.string.publication_reassociate else R.string.publication_retry)
        val state: BookScreenState<BookDetailContent> = if (book != null) {
            BookScreenState.Content(BookDetailContent(
                identity = book.identity,
                title = book.title,
                author = book.authors.joinToString("、"),
                status = resources.getString(R.string.publication_local_status, book.localPublication?.format?.name.orEmpty()),
                description = buildString {
                    append(resources.getString(if (book.localPublication?.originalAvailable == true) R.string.publication_cache_owned else R.string.publication_original_missing))
                    compatibility.forEach { append('\n').append(it) }
                },
            ))
        } else if (flow.loading) BookScreenState.Loading else BookScreenState.Failure(flow.failure, offlineCacheAvailable = false, retryLabel = retryLabel)
        val directory: BookScreenState<BookDirectory> = if (parsed != null) {
            BookScreenState.Content(BookDirectory(book!!.identity, flow.navigationTargets, authoredNavigation = parsed.tableOfContents.isNotEmpty()))
        } else if (flow.loading) BookScreenState.Loading else BookScreenState.Failure(flow.failure, offlineCacheAvailable = false, retryLabel = retryLabel)
        val destinationCollections = remember(library.collections, selectedCollections) {
            library.collections.filter { it.kind == CollectionKind.MANUAL }.map {
                DetailCollectionDestination(it.collectionId, it.title, it.collectionId in selectedCollections)
            }
        }
        BookDetailScreen(
            state = state, directoryState = directory, localState = flow.localState,
            mutation = flow.mutation, coverState = flow.coverState, unreadOnly = unread, descending = descending,
            selectedChapterId = flow.document?.identity?.contentId,
            cacheState = DetailCacheState(), onCacheAction = { imports.show() },
            onSetRating = { flow.rate(it, owner) }, onSearchAuthor = null,
            onToggleUnreadOnly = { unread = !unread }, onToggleOrder = { descending = !descending },
            onSelectChapter = { open(it.contentId, it.fragment, sectionStart = true) }, onContinueReading = { open(it.contentId) },
            onAddToLibrary = { flow.addToLibrary(owner) }, onRequestRemoveFromLibrary = { pendingDelete = null; flow.removeFromLibrary(owner) },
            onRetry = { if (originalMissing) imports.show() else id?.let { flow.open(it, force = true) } },
            onUseOfflineCache = imports::show, onOpenVerification = imports::show,
            tagEditorOpen = tagEditor, tagDraft = tagDraft,
            onOpenTagEditor = { tagDraft = flow.localState.localTags.joinToString("，"); tagEditor = true },
            onTagDraftChange = { tagDraft = it }, onDismissTagEditor = { tagEditor = false },
            onConfirmTag = {
                val tags = tagDraft.split(',', '，', '\n').map(String::trim).filter(String::isNotEmpty).distinct()
                if (tags.any { it.codePointCount(0, it.length) > 128 }) result = resources.getString(R.string.publication_tag_failed)
                else { flow.tags(tags, owner); tagEditor = false }
            },
            onOpenDestinations = { destinations = true }, destinationMenuExpanded = destinations,
            onDestinationMenuExpandedChange = { destinations = it },
            destinationMenuItemCount = bookDestinationMenuItemCount(destinationCollections, emptyList(), false, false, false),
            destinationMenuItemKey = { index -> bookDestinationMenuItemKey(index, destinationCollections, emptyList(), false, false, false) },
            destinationMenuItemContent = { index, dismiss ->
                BookDestinationMenu(
                    itemIndex = index,
                    readLater = flow.localState.readLater,
                    collections = destinationCollections,
                    remoteTargets = emptyList(), selectedRemoteTargetId = null, loadingRemoteTargets = false, websiteGroupingEnabled = false,
                    onToggleReadLater = { flow.toggleReadLater(owner) },
                    onToggleCollection = { collection ->
                        flow.mutate(DetailMutationOperation.ADD_TO_LIBRARY, owner) { book ->
                            if (collection in selectedCollections) application.libraryRepository.removeManualMembership(collection, book.identity)
                            else application.libraryRepository.addManualMembership(collection, book.identity)
                        }
                    },
                    onApplyWebsite = null, onDismiss = dismiss,
                )
            },
            onKeepDefaultLibrary = { result = null },
            modifier = Modifier.weight(1f),
        )
    }
    pendingDelete?.let { originalOnly ->
        TsuyomiDialog(
            title = stringResource(if (originalOnly) R.string.publication_remove_original else R.string.publication_delete_all),
            text = stringResource(if (originalOnly) R.string.publication_remove_original_scope else R.string.publication_delete_all_scope),
            destructive = true,
            onDismissRequest = { if (!working) pendingDelete = null },
            dismissLabel = stringResource(R.string.publication_cancel),
            confirmLabel = stringResource(R.string.publication_confirm), confirmEnabled = !working,
            onConfirm = {
                working = true
                scope.launch {
                    try {
                        val outcome = flow.delete(originalOnly, owner, onDeleted = if (originalOnly) null else { deleted ->
                            imports.removeDeletedBookActions(deleted.publicationId)
                            navController.pruneLocalBookRoutes(deleted.publicationId)
                        })
                        pendingDelete = null
                        if (outcome != PublicationDeleteStatus.COMPLETED) {
                            result = resources.getString(R.string.publication_mutation_failed)
                        } else if (originalOnly) result = resources.getString(R.string.publication_delete_complete)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) {
                        pendingDelete = null
                        result = resources.getString(R.string.publication_mutation_failed)
                    }
                    finally { working = false }
                }
            },
        )
    }
    if (resourceDialog) {
        TsuyomiDialog(
            title = stringResource(R.string.publication_resource_settings),
            text = stringResource(R.string.publication_resource_scope),
            onDismissRequest = { if (!working) resourceDialog = false },
            dismissLabel = stringResource(R.string.publication_cancel),
            body = {
                val eInk = LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK
                val hasRevoke = flow.resourceOrigins.isNotEmpty()
                val itemCount = 2 + (if (hasRevoke) 1 else 0) + (if (result != null) 1 else 0)
                val itemKey: (Int) -> String = { index ->
                    when {
                        index == 0 -> "origins"
                        index == 1 -> "consent"
                        index == 2 && hasRevoke -> "revoke"
                        else -> "result"
                    }
                }
                val resourceControl: @Composable (Int) -> Unit = { index ->
                    when (itemKey(index)) {
                        "origins" -> TsuyomiTextField(origins, { origins = it }, stringResource(R.string.publication_resource_origins), Modifier.fillMaxWidth())
                        "consent" -> TsuyomiCheckboxRow(stringResource(R.string.publication_resource_allow), consent, { consent = it })
                        "revoke" -> TsuyomiButton(stringResource(R.string.publication_resource_revoke), {
                            scope.launch {
                                val applied = flow.revokeResources(owner)
                                result = resources.getString(if (applied) R.string.publication_resource_revoked else R.string.publication_mutation_failed)
                                if (applied) resourceDialog = false
                            }
                        })
                        else -> result?.let { message ->
                            if (eInk) TsuyomiMeasuredTextPager(
                                text = message,
                                modifier = Modifier.fillMaxWidth(),
                                fillViewport = false,
                                alwaysShowPageControls = false,
                            ) else Text(message)
                        }
                    }
                }
                if (eInk) {
                    TsuyomiMeasuredPager(
                        itemCount = itemCount,
                        itemKey = itemKey,
                        itemContent = resourceControl,
                        fillViewport = false,
                    )
                } else {
                    repeat(itemCount) { resourceControl(it) }
                }
            },
            confirmLabel = stringResource(R.string.publication_resource_allow), confirmEnabled = consent && origins.isNotBlank() && !working,
            onConfirm = {
                working = true
                scope.launch {
                    try {
                        val applied = flow.grantResources(origins.lineSequence().map(String::trim).filter(String::isNotEmpty).toSet(), owner)
                        result = resources.getString(if (applied) R.string.publication_resource_saved else R.string.publication_mutation_failed)
                        if (applied) resourceDialog = false
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { result = resources.getString(R.string.publication_resource_invalid) }
                    finally { working = false }
                }
            },
        )
    }
}

@Composable
internal fun LocalBookReaderRoute(
    entry: NavBackStackEntry,
    flow: LocalBookFlow,
    navController: NavHostController,
    imports: PublicationImportCoordinator,
    preferences: PortableReaderPreferences,
    onPreferencesChanged: (PortableReaderPreferences) -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val application = context.applicationContext as TsuyomiApplication
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val owner = flow.lifecycleOwner
    val publicationId by remember(entry) {
        entry.savedStateHandle.getStateFlow<String?>(LocalPublicationKey, null)
    }.collectAsStateWithLifecycle()
    val id = publicationId ?: return
    val requestedContent by remember(entry) {
        entry.savedStateHandle.getStateFlow<String?>(LocalContentKey, null)
    }.collectAsStateWithLifecycle()
    val requestedFragment by remember(entry) {
        entry.savedStateHandle.getStateFlow<String?>(LocalFragmentKey, null)
    }.collectAsStateWithLifecycle()
    val requestedSectionStart by remember(entry) {
        entry.savedStateHandle.getStateFlow(LocalSectionStartKey, false)
    }.collectAsStateWithLifecycle()
    var external by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var linkFailure by rememberSaveable(id) { mutableStateOf<String?>(null) }
    LaunchedEffect(id, owner, flow.publication, requestedContent, requestedFragment, requestedSectionStart) {
        val hasRequestedTarget = requestedContent != null || requestedFragment != null || requestedSectionStart
        // Consuming the entry must not replay older progress or cancel an in-flight mounted selection.
        if (flow.publication?.publicationId == id && (hasRequestedTarget || flow.document == null)) {
            val request = flow.read(requestedContent, fragment = requestedFragment, sectionStart = requestedSectionStart, owner = owner)
            request?.join()
            val document = flow.document
            if (hasRequestedTarget && request?.isCancelled != true && owner?.valid == true && owner.job.isActive &&
                flow.lifecycleOwner === owner && flow.identity?.publicationId == id && !flow.loading && flow.failure == null &&
                document != null && document.identity.book == flow.identity &&
                (requestedContent == null || document.identity.contentId == requestedContent)) {
                // Every successful entry target is one-shot; recreation resumes the durable locator.
                entry.savedStateHandle[LocalContentKey] = null
                entry.savedStateHandle[LocalFragmentKey] = null
                entry.savedStateHandle[LocalSectionStartKey] = false
            }
        }
    }
    val identity = flow.identity
    val requestedFlowState = remember(identity, preferences.flow) {
        identity?.let { application.readerPreferencesRepository.requestedFlow(it, preferences) }
            ?: flowOf(RequestedReaderFlow(preferences.flow ?: "scroll", false))
    }
    val requestedFlow by requestedFlowState.collectAsStateWithLifecycle(initialValue = null)
    val epoch = flow.documentGeneration
    val imageEpoch = flow.imageGeneration
    LaunchedEffect(owner, flow.document, epoch, requestedFlow != null, flow.loading) {
        if (flow.document != null && !flow.loading && flow.failure == null && requestedFlow != null) flow.mounted(epoch, owner)
    }
    if (flow.failure != null && !flow.loading && flow.document == null) {
        val originalMissing = flow.book?.localPublication?.originalAvailable == false
        StateView(
            kind = TsuyomiStateKind.ERROR, title = stringResource(R.string.publication_unknown_file), message = flow.failure,
            actionLabel = stringResource(if (originalMissing) R.string.publication_reassociate else R.string.publication_retry), onAction = {
                if (originalMissing) imports.show()
                else scope.launch { flow.open(id, force = true)?.join(); flow.read(requestedContent, fragment = requestedFragment, sectionStart = requestedSectionStart) }
            },
            secondaryActionLabel = if (originalMissing) null else stringResource(R.string.publication_reassociate),
            onSecondaryAction = if (originalMissing) null else imports::show,
        )
    } else ReaderScreen(
        document = flow.document, bookIdentity = identity, loading = flow.loading || flow.document == null || requestedFlow == null,
        failure = null, restoredLocator = flow.restoredLocator, restorationGeneration = flow.restorationGeneration,
        navigationTargets = flow.navigationTargets, currentContentId = flow.document?.identity?.contentId.orEmpty(),
        bookmarks = flow.bookmarks, onToggleBookmark = { scope.launch { flow.toggleBookmark(it, owner) } },
        onSelectBookmark = { flow.read(it.document.contentId, it, owner = owner) }, onReturnToOrigin = { flow.read(it.document.contentId, it, owner = owner) },
        onRemoveBookmark = { scope.launch { flow.removeBookmark(it, owner) } },
        requestedFlow = requestedFlow?.flow ?: preferences.flow ?: "scroll", hasFlowOverride = requestedFlow?.overridden == true,
        onFlowOverrideChanged = { value -> scope.launch { flow.setFlowOverride(value, owner) } },
        onSelectContent = { selected -> flow.read(selected.contentId, fragment = selected.fragment, sectionStart = true, owner = owner) },
        onSelectAdjacentContent = { selected, end -> flow.read(selected.contentId, fragment = if (end) null else selected.fragment,
            sectionStart = !end, sectionEnd = end, adjacentBoundary = true, owner = owner) },
        onNavigateLink = { target -> when (target) {
            is ReaderLinkTarget.Internal -> flow.read(target.contentId, fragment = target.fragment, sectionStart = true, owner = owner)
            is ReaderLinkTarget.ExternalHttps -> external = target.url
        } },
        onNavigateUp = { navController.navigateUp() }, imageStates = flow.imageStates,
        onImageVisible = { flow.loadImage(it, expectedGeneration = imageEpoch, owner = owner) },
        onImageHidden = { flow.releaseImage(it, imageEpoch) },
        onRetryImage = { flow.loadImage(it, retry = true, expectedGeneration = imageEpoch, owner = owner) },
        onLocatorChanged = { locator, precision -> scope.launch { flow.saveProgress(locator, precision, epoch, owner) } },
        onChapterCompleted = { scope.launch { flow.complete(it, epoch, owner) } }, preferences = preferences, onPreferencesChanged = onPreferencesChanged,
        onRetry = { flow.read(requestedContent, fragment = requestedFragment, sectionStart = requestedSectionStart, owner = owner) }, onUseOfflineCache = { flow.read(requestedContent, fragment = requestedFragment, sectionStart = requestedSectionStart, owner = owner) }, onOpenVerification = imports::show,
    )
    if (flow.failure != null && !flow.loading && flow.document != null) {
        TsuyomiDialog(
            title = stringResource(R.string.publication_unknown_file),
            text = flow.failure,
            onDismissRequest = flow::dismissReaderFailure,
            dismissLabel = stringResource(R.string.publication_cancel),
            confirmLabel = stringResource(R.string.publication_retry),
            onConfirm = { flow.retryRead() },
        )
    }
    external?.let { url ->
        TsuyomiDialog(
            title = stringResource(R.string.publication_external_link_title),
            text = resources.getString(R.string.publication_external_link_scope) + "\n" + url,
            onDismissRequest = { external = null }, dismissLabel = stringResource(R.string.publication_cancel),
            confirmLabel = stringResource(R.string.publication_open_browser), onConfirm = {
                try { uriHandler.openUri(url); external = null }
                catch (_: Exception) { linkFailure = resources.getString(R.string.publication_external_unavailable); external = null }
            },
        )
    }
    linkFailure?.let { message -> TsuyomiDialog(
        title = stringResource(R.string.publication_external_link_title), text = message, onDismissRequest = { linkFailure = null },
        confirmLabel = stringResource(R.string.publication_close), onConfirm = { linkFailure = null },
    ) }
}
