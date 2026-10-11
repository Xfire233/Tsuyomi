/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.media.api.FallbackSpec
import org.tsuyomi.core.publication.ParsedPublication
import org.tsuyomi.core.publication.PublicationDeleteStatus
import org.tsuyomi.core.publication.PublicationParseResult
import org.tsuyomi.core.publication.PublicationTocEntry
import org.tsuyomi.feature.book.DetailLocalState
import org.tsuyomi.feature.book.DetailMutationOperation
import org.tsuyomi.feature.book.DetailMutationPhase
import org.tsuyomi.feature.book.DetailMutationStatus
import org.tsuyomi.reader.engine.ReaderDocumentSession
import org.tsuyomi.reader.engine.ReaderPresentation
import org.tsuyomi.shared.librarydomain.LibraryBook
import org.tsuyomi.shared.librarydomain.LibraryEntry
import org.tsuyomi.shared.librarydomain.ReadingProgress
import org.tsuyomi.shared.locator.DocumentIdentity
import org.tsuyomi.shared.locator.LocatorPrecision
import org.tsuyomi.shared.locator.ReaderLocator
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.ReaderBlock
import org.tsuyomi.shared.sourcecontract.ReaderDocument
import org.tsuyomi.shared.sourcecontract.ReaderDocumentPresentation
import org.tsuyomi.shared.sourcecontract.ReaderNavigationTarget
import org.tsuyomi.shared.sourcecontract.canonicalAnchorText
import org.tsuyomi.shared.sourcecontract.targetForAnchor

/** One host publication session across canonical Detail and Reader, independent of installed sources. */
internal class LocalBookFlow(
    private val application: TsuyomiApplication,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val repository get() = application.libraryRepository
    private val publications get() = application.localPublications
    private val images = PublicationImages(publications)
    private var handle: PublicationParseHandle? = null
    private var work: Job? = null
    private var generation = 0L
    private var sessionScope: CoroutineScope? = null
    var lifecycleOwner by mutableStateOf<LocalPublicationManager.LocalOwner?>(null)
        private set
    var admittedRouteEntryId by mutableStateOf<String?>(null)
        private set
    var routeInvalidationGeneration by mutableLongStateOf(0L)
        private set
    var identity by mutableStateOf<BookIdentity.Local?>(null)
        private set
    var book by mutableStateOf<LibraryBook?>(null)
        private set
    var entry by mutableStateOf<LibraryEntry?>(null)
        private set
    var publication by mutableStateOf<ParsedPublication?>(null)
        private set
    var document by mutableStateOf<ReaderDocument?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var failure by mutableStateOf<String?>(null)
        private set
    var status by mutableStateOf<String?>(null)
        private set
    var restoredLocator by mutableStateOf<ReaderLocator?>(null)
        private set
    var documentGeneration by mutableLongStateOf(0L)
        private set
    var imageGeneration by mutableLongStateOf(0L)
        private set
    var restorationGeneration by mutableLongStateOf(0L)
        private set
    var catalogGeneration by mutableLongStateOf(0L)
        private set
    var completedContentIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var bookmarks by mutableStateOf<List<ReaderLocator>>(emptyList())
        private set
    var resourceOrigins by mutableStateOf<Set<String>>(emptySet())
        private set
    var mutation by mutableStateOf<DetailMutationStatus?>(null)
        private set
    val imageStates get() = images.states
    var navigationTargets by mutableStateOf<List<ReaderNavigationTarget>>(emptyList())
        private set
    private var navigationFragments: Map<String, String> = emptyMap()
    private var documentBlockIndices: Map<String, Int> = emptyMap()
    private var requestedReaderContent: String? = null
    private var requestedReaderLocator: ReaderLocator? = null
    private var requestedReaderFragment: String? = null
    private var requestedReaderStart = false
    private var requestedReaderEnd = false
    private var requestedReaderAdjacent = false
    private var mountedBoundaryLocator: ReaderLocator? = null
    val navigationFragment: String?
        get() {
            val locator = entry?.progress?.locator ?: restoredLocator ?: return null
            val current = document ?: return navigationFragments[locator.blockId]
            if (!locator.document.namesSameDocumentAs(current.identity)) return null
            val currentBlock = documentBlockIndices[locator.blockId] ?: return null
            val currentOffset = locator.characterOffset ?: 0
            var bestBlock = -1
            var bestOffset = -1
            var fragment: String? = null
            for (target in navigationTargets) {
                if (target.contentId != current.identity.contentId) continue
                val anchor = target.fragment?.let(current::targetForAnchor)
                val block = if (target.fragment == null) 0 else documentBlockIndices[anchor?.blockId] ?: continue
                val offset = anchor?.codePointOffset ?: 0
                if ((block < currentBlock || block == currentBlock && offset <= currentOffset) &&
                    (block > bestBlock || block == bestBlock && offset > bestOffset)) {
                    bestBlock = block
                    bestOffset = offset
                    fragment = target.fragment
                }
            }
            return fragment
        }
    val localState: DetailLocalState
        get() = DetailLocalState(
            inLibrary = entry?.localMembership == true,
            rating = entry?.rating,
            localTags = entry?.localTags?.toList().orEmpty(),
            localTagsEditable = entry != null,
            readLater = entry?.readLater == true,
            progressChapterId = entry?.progress?.locator?.document?.contentId,
            progressChapterFraction = entry?.progress?.locator?.chapterProgress,
            progressFragment = navigationFragment,
            completedChapterIds = completedContentIds,
        )
    val coverState: CoverUiState
        get() = images.states[CoverKey] ?: CoverUiState.Fallback(FallbackSpec(book?.title ?: application.getString(R.string.publication_unknown_file), null))

    /** Revalidate restored routes against retained metadata, not original-byte availability. */
    suspend fun admitRoute(entryId: String, publicationId: String): Boolean {
        if (admittedRouteEntryId != entryId) admittedRouteEntryId = null
        val target = BookIdentity.Local(publicationId)
        val retained = try {
            publications.localBook(target)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            close(retainRoute = true)
            identity = target
            book = null
            entry = null
            failure = application.getString(R.string.publication_storage_failure)
            admittedRouteEntryId = entryId
            return true
        }
        if (retained == null) {
            admittedRouteEntryId = null
            close()
            return false
        }
        val pending = open(publicationId)
        if (identity == target) admittedRouteEntryId = entryId
        pending?.join()
        currentCoroutineContext().ensureActive()
        return true
    }

    fun open(publicationId: String, force: Boolean = false): Job? {
        val target = BookIdentity.Local(publicationId)
        if (!force && identity == target && isCurrentOwner(lifecycleOwner) && (publication != null || loading)) return work
        val previous = work
        val previousOwner = lifecycleOwner
        val previousHandle = handle
        sessionScope?.cancel()
        previous?.cancel()
        lifecycleOwner = null
        handle = null
        val sessionJob = SupervisorJob(viewModelScope.coroutineContext.job)
        val scope = CoroutineScope(viewModelScope.coroutineContext + sessionJob)
        sessionScope = scope
        val token = ++generation
        identity = target
        book = null
        entry = null
        publication = null
        navigationTargets = emptyList()
        navigationFragments = emptyMap()
        document = null
        documentBlockIndices = emptyMap()
        documentGeneration++
        failure = null
        status = null
        loading = true
        mutation = null
        images.clear()
        imageGeneration++
        work = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var pending: PublicationParseHandle? = null
            var registered: LocalPublicationManager.LocalOwner? = null
            try {
                previous?.join()
                previousOwner?.let { publications.releaseLocalOwner(it) }
                withContext(Dispatchers.IO) { previousHandle?.close() }
                publications.recover()
                registered = publications.registerLocalOwner(target, sessionJob, ::retireLocalOwner)
                sessionJob.ensureActive()
                if (token != generation) return@launch
                lifecycleOwner = registered
                val stored = checkNotNull(registered).book
                val metadata = checkNotNull(stored.localPublication)
                if (token != generation) return@launch
                book = stored
                refreshCatalog(target)
                if (!metadata.originalAvailable) {
                    failure = application.getString(R.string.publication_original_missing)
                    return@launch
                }
                val context = currentCoroutineContext()
                pending = publications.openPublication(stored)
                context.ensureActive()
                val parsed = (checkNotNull(pending).result as? PublicationParseResult.Ready)?.publication
                    ?: throw IOException("Saved text normalization is unavailable")
                if (token != generation) return@launch
                navigationFragments = if (parsed.presentation == ReaderDocumentPresentation.FIXED_IMAGE) {
                    parsed.sections.single().anchors.filterKeys { it.startsWith("page:") }
                        .entries.associate { it.value.blockId to it.key }
                } else emptyMap()
                navigationTargets = buildList {
                    val representedSections = HashSet<String>()
                    val fixedAnchors = if (parsed.presentation == ReaderDocumentPresentation.FIXED_IMAGE) parsed.sections.single().anchors else emptyMap()
                    fun append(entries: List<PublicationTocEntry>, parentTitle: String?) {
                        for (row in entries) {
                            val page = row.fragment?.let { fixedAnchors[it]?.blockId }?.let(navigationFragments::get) ?: row.fragment
                            add(ReaderNavigationTarget(row.contentId, row.title, parentTitle, page))
                            representedSections += row.contentId
                            append(row.children, row.title)
                        }
                    }
                    append(parsed.tableOfContents, null)
                    for (section in parsed.sections) {
                        if (section.contentId !in representedSections) add(ReaderNavigationTarget(section.contentId, section.title))
                    }
                }
                val progress = entry?.progress?.locator?.takeIf { locator ->
                    locator.document.book == target && locator.document.revision == parsed.contentRevision &&
                        parsed.sections.any { it.contentId == locator.document.contentId }
                }
                if (parsed.presentation == ReaderDocumentPresentation.REFLOWABLE && progress != null &&
                    navigationTargets.any { it.contentId == progress.document.contentId && it.fragment != null }) {
                    // Resolve current directory targets without recording a Reader visit or loading images.
                    val loaded = withContext(Dispatchers.IO) { parsed.loadSection(progress.document.contentId, progress.document, context::ensureActive) }
                    context.ensureActive()
                    if (token != generation) return@launch
                    updateDocument(loaded)
                    restoredLocator = progress
                    restorationGeneration++
                }
                resourceOrigins = publications.resources.origins(target.publicationId, parsed.originalSha256)
                parsed.metadata.cover?.let { cover ->
                    images.load(ReaderBlock.Image(CoverKey, cover, parsed.metadata.title), parsed, scope, 512, 768)
                }
                context.ensureActive()
                if (token != generation || !isCurrentOwner(registered)) return@launch
                // Publish readiness only after native restoration and resource setup finish.
                handle = pending
                pending = null
                publication = parsed
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (token == generation) failure = publicationFailureMessage(application, error)
            } finally {
                withContext(NonCancellable) {
                    try {
                        withContext(Dispatchers.IO) {
                            try { pending?.close() } finally { previousHandle?.close() }
                        }
                    } finally {
                        registered?.takeIf { lifecycleOwner !== it }?.let { publications.releaseLocalOwner(it) }
                    }
                }
                if (token == generation) loading = false
            }
        }
        return work
    }

    fun read(contentId: String? = null, locator: ReaderLocator? = null, fragment: String? = null, sectionStart: Boolean = false, sectionEnd: Boolean = false, adjacentBoundary: Boolean = false, owner: LocalPublicationManager.LocalOwner? = lifecycleOwner): Job? {
        if (!isCurrentOwner(owner)) return null
        val scope = sessionScope ?: return null
        val parsed = publication ?: return null
        val book = identity ?: return null
        val resume = locator ?: entry?.progress?.locator
        val selected = contentId ?: resume?.document?.contentId?.takeIf { id -> parsed.sections.any { it.contentId == id } }
            ?: parsed.sections.first().contentId
        if (selected == document?.identity?.contentId && locator == null && fragment == null && !sectionStart && !sectionEnd) return null
        val existing = document?.takeIf { it.identity.book == book && it.identity.contentId == selected && it.identity.revision == parsed.contentRevision }
        requestedReaderContent = selected
        requestedReaderLocator = locator
        requestedReaderFragment = fragment
        requestedReaderStart = sectionStart
        requestedReaderEnd = sectionEnd
        requestedReaderAdjacent = adjacentBoundary
        mountedBoundaryLocator = null
        val previous = work
        previous?.cancel()
        val token = ++generation
        val captureEpoch = ++documentGeneration
        loading = true
        failure = null
        work = scope.launch {
            try {
                previous?.join()
                val context = currentCoroutineContext()
                val loaded = existing ?: withContext(Dispatchers.IO) {
                    parsed.loadSection(selected, DocumentIdentity(book, selected, parsed.contentRevision), context::ensureActive)
                }
                context.ensureActive()
                if (!isCurrentOwner(owner) || token != generation || identity != book || captureEpoch != documentGeneration) return@launch
                val requested = if (sectionEnd) {
                    val block = loaded.blocks.last()
                    val text = block.canonicalAnchorText()
                    val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
                        .joinToString("") { "%02x".format(it.toInt() and 255) }
                    ReaderLocator(loaded.identity, block.blockId, digest, text.codePointCount(0, text.length),
                        chapterProgress = 1.0, capturedAt = Instant.now())
                } else if (fragment != null || sectionStart) {
                    val anchor = if (fragment != null) loaded.targetForAnchor(fragment) ?: throw IOException("Missing internal anchor")
                        else org.tsuyomi.shared.sourcecontract.ReaderAnchorTarget(loaded.blocks.first().blockId, 0)
                    val block = loaded.blocks.first { it.blockId == anchor.blockId }
                    val digest = MessageDigest.getInstance("SHA-256").digest(block.canonicalAnchorText().toByteArray(Charsets.UTF_8))
                        .joinToString("") { "%02x".format(it.toInt() and 255) }
                    ReaderLocator(loaded.identity, anchor.blockId, digest, anchor.codePointOffset,
                        chapterProgress = if (adjacentBoundary && fragment == null) 0.0 else null, capturedAt = Instant.now())
                } else if (locator != null) {
                    ReaderDocumentSession(loaded, locator, ReaderPresentation.SCROLL).capture()
                } else resume?.takeIf { it.document.namesSameDocumentAs(loaded.identity) }
                if (document !== loaded) {
                    images.clear()
                    imageGeneration++
                }
                // Retire captures taken from the retained origin while this request was loading.
                documentGeneration++
                updateDocument(loaded)
                restoredLocator = requested
                // Explicit navigation commits on mount; passive restoration does not stage a write.
                if (adjacentBoundary || fragment != null || sectionStart || sectionEnd || locator != null) {
                    mountedBoundaryLocator = requested
                }
                restorationGeneration++
                saved[ContentKey] = selected
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (token == generation) failure = publicationFailureMessage(application, error)
            } finally {
                if (token == generation) loading = false
            }
        }
        return work
    }

    fun dismissReaderFailure() { failure = null }

    fun retryRead(): Job? = read(requestedReaderContent, requestedReaderLocator, requestedReaderFragment, requestedReaderStart, requestedReaderEnd, requestedReaderAdjacent)

    private fun updateDocument(loaded: ReaderDocument) {
        if (document !== loaded) documentBlockIndices = loaded.blocks.mapIndexed { index, block -> block.blockId to index }.toMap()
        document = loaded
    }

    private fun isCurrentOwner(owner: LocalPublicationManager.LocalOwner?): Boolean =
        owner != null && lifecycleOwner === owner && owner.valid && owner.job.isActive

    private suspend fun withOwner(owner: LocalPublicationManager.LocalOwner?, action: suspend (LibraryBook) -> Boolean): Boolean =
        publications.withLocalOwner(owner) { current ->
            if (!isCurrentOwner(owner)) false else action(current)
        }

    suspend fun mounted(expectedGeneration: Long, owner: LocalPublicationManager.LocalOwner? = lifecycleOwner): Boolean = withOwner(owner) {
        val current = document ?: return@withOwner false
        if (expectedGeneration != documentGeneration || loading) return@withOwner false
        val boundary = mountedBoundaryLocator
        repository.recordReaderVisit(current.identity.book)
        if (expectedGeneration != documentGeneration || document !== current || loading || !isCurrentOwner(owner)) return@withOwner false
        if (boundary != null) {
            saveProgressWithinOwner(boundary, boundary.precision, expectedGeneration, explicitNavigation = true)
        } else if (restoredLocator == null) {
            saveProgressWithinOwner(
                ReaderLocator(
                    document = current.identity,
                    blockId = current.blocks.first().blockId,
                    characterOffset = 0,
                    chapterProgress = 0.0,
                    capturedAt = Instant.now(),
                ),
                LocatorPrecision.DEGRADED,
                expectedGeneration,
            )
        }
        if (expectedGeneration != documentGeneration || document !== current || loading || !isCurrentOwner(owner)) return@withOwner false
        if (boundary != null && mountedBoundaryLocator === boundary) mountedBoundaryLocator = null
        catalogGeneration++
        true
    }

    suspend fun saveProgress(locator: ReaderLocator, precision: LocatorPrecision, expectedGeneration: Long, owner: LocalPublicationManager.LocalOwner? = lifecycleOwner): Boolean = withOwner(owner) {
        saveProgressWithinOwner(locator, precision, expectedGeneration)
        true
    }

    private suspend fun saveProgressWithinOwner(locator: ReaderLocator, precision: LocatorPrecision, expectedGeneration: Long, explicitNavigation: Boolean = false) {
        val current = document ?: return
        if (expectedGeneration != documentGeneration || !locator.document.namesSameRevisionAs(current.identity) || precision != locator.precision) return
        if (loading) return
        val prior = entry?.progress?.locator
        // A layout/late-image fallback must not replace an existing exact capture of the same revision.
        if (!explicitNavigation && precision != LocatorPrecision.EXACT && prior?.precision == LocatorPrecision.EXACT &&
            prior.document.namesSameRevisionAs(locator.document)) return
        repository.saveProgress(ReadingProgress(current.identity.book, locator))
        if (expectedGeneration == documentGeneration) refreshCatalog(current.identity.book as BookIdentity.Local)
    }

    suspend fun toggleBookmark(locator: ReaderLocator, owner: LocalPublicationManager.LocalOwner? = lifecycleOwner): Boolean = withOwner(owner) {
        if (locator.document.book != identity || document == null) return@withOwner false
        repository.toggleBookmark(locator)
        val retained = repository.bookmarks(locator.document.book)
        if (isCurrentOwner(owner)) bookmarks = retained
        true
    }
    suspend fun removeBookmark(locator: ReaderLocator, owner: LocalPublicationManager.LocalOwner? = lifecycleOwner): Boolean = withOwner(owner) {
        if (locator.document.book != identity) return@withOwner false
        repository.removeBookmark(locator)
        val retained = repository.bookmarks(locator.document.book)
        if (isCurrentOwner(owner)) bookmarks = retained
        true
    }
    suspend fun complete(contentId: String, expectedGeneration: Long, owner: LocalPublicationManager.LocalOwner? = lifecycleOwner): Boolean = withOwner(owner) {
        val current = document ?: return@withOwner false
        if (expectedGeneration != documentGeneration || current.identity.contentId != contentId) return@withOwner false
        repository.markChapterCompleted(current.identity.book, contentId, Instant.now())
        val completed = repository.completedChapterIds(current.identity.book)
        if (isCurrentOwner(owner)) {
            completedContentIds = completed
            catalogGeneration++
        }
        true
    }

    fun loadImage(block: ReaderBlock.Image, retry: Boolean = false, expectedGeneration: Long = imageGeneration, owner: LocalPublicationManager.LocalOwner? = lifecycleOwner) {
        if (!isCurrentOwner(owner) || expectedGeneration != imageGeneration) return
        val scope = sessionScope ?: return
        val parsed = publication ?: return
        val metrics = application.resources.displayMetrics
        images.load(block, parsed, scope, metrics.widthPixels, metrics.heightPixels, retry, expectedGeneration)
    }

    fun releaseImage(block: ReaderBlock.Image, expectedGeneration: Long) {
        images.release(block, expectedGeneration)
    }

    fun mutate(operation: DetailMutationOperation, owner: LocalPublicationManager.LocalOwner? = lifecycleOwner, action: suspend (LibraryBook) -> Unit): Job? {
        if (!isCurrentOwner(owner)) return null
        val scope = sessionScope ?: return null
        return scope.launch {
            mutation = DetailMutationStatus(operation, DetailMutationPhase.WORKING)
            try {
                val applied = withOwner(owner) { current ->
                    action(current)
                    refreshCatalog(current.identity as BookIdentity.Local)
                    true
                }
                if (isCurrentOwner(owner)) {
                    mutation = DetailMutationStatus(operation, if (applied) DetailMutationPhase.SUCCESS else DetailMutationPhase.ERROR)
                    if (applied) catalogGeneration++
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (isCurrentOwner(owner)) mutation = DetailMutationStatus(operation, DetailMutationPhase.ERROR)
            }
        }
    }
    fun addToLibrary(owner: LocalPublicationManager.LocalOwner? = lifecycleOwner) = mutate(DetailMutationOperation.ADD_TO_LIBRARY, owner) { repository.addToLibrary(it) }
    fun removeFromLibrary(owner: LocalPublicationManager.LocalOwner? = lifecycleOwner) = mutate(DetailMutationOperation.REMOVE_FROM_LIBRARY, owner) { repository.removeFromLibrary(it.identity) }
    fun rate(rating: Int?, owner: LocalPublicationManager.LocalOwner? = lifecycleOwner) = mutate(DetailMutationOperation.SET_RATING, owner) { repository.setRating(it.identity, rating) }
    fun tags(tags: Collection<String>, owner: LocalPublicationManager.LocalOwner? = lifecycleOwner) = mutate(DetailMutationOperation.ADD_TAG, owner) { repository.setLocalTags(it.identity, tags) }
    fun toggleReadLater(owner: LocalPublicationManager.LocalOwner? = lifecycleOwner) = mutate(DetailMutationOperation.TOGGLE_READ_LATER, owner) {
        repository.setReadLater(it.identity, repository.libraryEntry(it.identity)?.readLater != true)
    }

    suspend fun grantResources(origins: Set<String>, owner: LocalPublicationManager.LocalOwner? = lifecycleOwner): Boolean = withOwner(owner) { book ->
        val current = publication ?: return@withOwner false
        val metadata = checkNotNull(book.localPublication)
        if (!metadata.originalAvailable || current.originalSha256 != metadata.originalSha256) return@withOwner false
        publications.resources.grant(current.publicationId, current.originalSha256, origins, setOf("image/jpeg", "image/png"))
        if (isCurrentOwner(owner) && publication === current) {
            images.clear()
            imageGeneration++
            resourceOrigins = publications.resources.origins(current.publicationId, current.originalSha256)
        }
        true
    }
    suspend fun revokeResources(owner: LocalPublicationManager.LocalOwner? = lifecycleOwner): Boolean = withOwner(owner) { book ->
        images.clear()
        imageGeneration++
        publications.resources.revoke((book.identity as BookIdentity.Local).publicationId)
        if (isCurrentOwner(owner)) resourceOrigins = emptySet()
        true
    }

    suspend fun setFlowOverride(requested: String?, owner: LocalPublicationManager.LocalOwner? = lifecycleOwner): Boolean = withOwner(owner) { book ->
        application.readerPreferencesRepository.setFlowOverride(book.identity, requested)
        true
    }

    suspend fun delete(
        originalOnly: Boolean,
        owner: LocalPublicationManager.LocalOwner? = lifecycleOwner,
        onDeleted: ((BookIdentity.Local) -> Unit)? = null,
    ): PublicationDeleteStatus {
        if (!isCurrentOwner(owner)) return PublicationDeleteStatus.CATALOG_REJECTED
        val target = checkNotNull(owner).book.identity as BookIdentity.Local
        val routeOwner = admittedRouteEntryId
        val outcome = publications.deletePublication(target, originalOnly, owner, onDeleted)
        if (outcome != PublicationDeleteStatus.COMPLETED) {
            if (lifecycleOwner == null && identity == null) {
                admittedRouteEntryId = routeOwner
                open(target.publicationId, force = true)?.join()
            }
            return outcome
        }
        // The manager already retired every live owner, including this one, before removal.
        if (originalOnly && lifecycleOwner == null && identity == null) {
            admittedRouteEntryId = routeOwner
            open(target.publicationId, force = true)?.join()
        }
        return outcome
    }

    suspend fun close(retainRoute: Boolean = false) {
        ++generation
        documentGeneration++
        if (!retainRoute) admittedRouteEntryId = null
        val previousOwner = lifecycleOwner
        val previousScope = sessionScope
        val previous = work
        val previousHandle = handle
        lifecycleOwner = null
        sessionScope = null
        work = null
        handle = null
        previousScope?.cancel()
        previous?.cancel()
        clearReader()
        withContext(NonCancellable) {
            try {
                previousOwner?.let { publications.releaseLocalOwner(it) }
                previous?.join()
            } finally {
                withContext(Dispatchers.IO) { previousHandle?.close() }
            }
        }
    }

    /** Called on Main under the manager mutex; detach/cancel only, never join or call the manager. */
    private fun retireLocalOwner(owner: LocalPublicationManager.LocalOwner, originalOnly: Boolean): PublicationParseHandle? {
        if (lifecycleOwner !== owner && sessionScope?.coroutineContext?.job !== owner.job) return null
        ++generation
        documentGeneration++
        lifecycleOwner = null
        sessionScope?.cancel()
        sessionScope = null
        work?.cancel()
        work = null
        val retired = handle
        handle = null
        clearReader()
        admittedRouteEntryId = null
        identity = null
        book = null
        entry = null
        failure = null
        restoredLocator = null
        bookmarks = emptyList()
        completedContentIds = emptySet()
        resourceOrigins = emptySet()
        mutation = null
        if (!originalOnly) saved.remove<String>(ContentKey)
        catalogGeneration++
        routeInvalidationGeneration++
        return retired
    }

    private fun clearReader() {
        images.clear()
        imageGeneration++
        publication = null
        navigationTargets = emptyList()
        navigationFragments = emptyMap()
        document = null
        documentBlockIndices = emptyMap()
        loading = false
    }

    private suspend fun refreshCatalog(target: BookIdentity.Local) {
        val entry = repository.libraryEntry(target) ?: repository.readerHistoryEntry(target)
        val bookmarks = repository.bookmarks(target)
        val completed = repository.completedChapterIds(target)
        if (identity != target) return
        this.entry = entry
        this.bookmarks = bookmarks
        completedContentIds = completed
    }

    override fun onCleared() {
        // Cleanup is finite and must survive cancellation of the ViewModel's ordinary work.
        viewModelScope.launch(NonCancellable) { close() }
    }

    companion object {
        const val ContentKey = "publication.reader.content"
        private const val CoverKey = "publication-cover"
    }
}
