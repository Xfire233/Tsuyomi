/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import android.content.ContentResolver
import android.content.res.Resources
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.job
import org.tsuyomi.shared.sourcecontract.ReaderResourceRef
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.tsuyomi.android.publication.MobiPublicationTransportException
import org.tsuyomi.android.publication.MobiQuickJsNative
import org.tsuyomi.core.publication.PublicationLimitException
import org.tsuyomi.core.publication.PublicationCompatibilityFinding
import org.tsuyomi.core.publication.PublicationCompatibilitySeverity
import org.tsuyomi.core.publication.PublicationOptions
import org.tsuyomi.core.publication.PublicationOriginalStore
import org.tsuyomi.core.publication.PublicationParseException
import org.tsuyomi.core.publication.PublicationParseResult
import org.tsuyomi.core.publication.PublicationFailureCode
import org.tsuyomi.core.publication.PublicationStorageException
import org.tsuyomi.core.publication.TxtCharsetCandidate
import org.tsuyomi.feature.library.LocalBookImportAction
import org.tsuyomi.feature.library.LocalBookImportEncoding
import org.tsuyomi.feature.library.LocalBookImportHeading
import org.tsuyomi.feature.library.LocalBookImportMatch
import org.tsuyomi.feature.library.LocalBookImportResult
import org.tsuyomi.feature.library.LocalBookImportReview
import org.tsuyomi.feature.library.LocalBookImportUiState
import org.tsuyomi.shared.librarydomain.LibraryBook
import org.tsuyomi.shared.librarydomain.LocalPublicationFormat
import org.tsuyomi.shared.librarydomain.LocalTextHeading
import org.tsuyomi.shared.librarydomain.LocalTextParseOptions
import org.tsuyomi.shared.model.BookIdentity

/** One owner for SAF and system open/share. A batch never holds more than one complete original. */
internal class PublicationImportCoordinator(
    private val application: TsuyomiApplication,
    private val saved: SavedStateHandle,
) : ViewModel() {
    var state by mutableStateOf(LocalBookImportUiState(results = restoreResults()))
        private set
    var navigationRequest by mutableLongStateOf(0L)
        private set
    var catalogGeneration by mutableLongStateOf(0L)
        private set
    private val queue = ArrayDeque<Uri>()
    private var batchJob: Job? = null
    private var currentBookJob: Job? = null
    private var confirmation: CompletableDeferred<LocalBookImportReview>? = null
    private var candidates: List<TxtCharsetCandidate> = emptyList()
    private val publications get() = application.localPublications

    init {
        if (saved.get<Boolean>(ActiveKey) == true) {
            state = state.copy(status = application.getString(R.string.publication_import_interrupted))
            saved[ActiveKey] = false
        }
    }

    fun show() { navigationRequest++ }
    fun consumeNavigation(request: Long) { if (navigationRequest == request) navigationRequest = 0L }

    fun removeDeletedBookActions(publicationId: String) {
        if (state.results.none { it.publicationId == publicationId }) return
        saveResults(state.results.map { row ->
            if (row.publicationId == publicationId) row.copy(publicationId = null) else row
        })
    }

    fun enqueue(uris: List<Uri>) {
        if (uris.isEmpty()) return
        show()
        if (uris.size > MaxBatchFiles - queue.size) {
            state = state.copy(status = application.getString(R.string.publication_batch_limit, MaxBatchFiles))
            return
        }
        for (uri in uris) {
            if (uri.scheme == "content") queue.addLast(uri)
            else addResult(application.getString(R.string.publication_unknown_file), application.getString(R.string.publication_input_unavailable))
        }
        if (batchJob?.isActive == true || queue.isEmpty()) return
        batchJob = viewModelScope.launch {
            try {
                publications.recover()
                while (queue.isNotEmpty()) {
                    val uri = queue.removeFirst()
                    supervisorScope {
                        val child = launch { importOne(uri) }
                        currentBookJob = child
                        child.join()
                        currentBookJob = null
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state = state.copy(status = application.getString(R.string.publication_storage_failure))
            } finally {
                saved[ActiveKey] = false
                state = state.copy(busy = false, fileName = null, review = null)
            }
        }
    }

    fun cancelCurrent() { currentBookJob?.cancel() }

    fun edit(action: LocalBookImportAction) {
        if (action == LocalBookImportAction.Cancel) { cancelCurrent(); return }
        val review = state.review ?: return
        if (state.busy) return
        val edited = when (action) {
            is LocalBookImportAction.ChooseAssociation -> review.copy(
                selectedPublicationId = action.publicationId,
                independentCopy = action.publicationId == null,
            )
            is LocalBookImportAction.ChooseCharset -> {
                val candidate = candidates.firstOrNull { it.charsetName == action.charsetName } ?: return
                review.copy(
                    selectedCharset = candidate.charsetName,
                    headings = candidate.tableOfContentsSuggestions.map {
                        LocalBookImportHeading(it.paragraphOrdinal, it.title, selected = false)
                    },
                )
            }
            is LocalBookImportAction.EditHeading -> review.copy(headings = review.headings.map {
                if (it.paragraphOrdinal == action.paragraphOrdinal) it.copy(title = action.title, selected = action.selected) else it
            })
            is LocalBookImportAction.EditNewHeadingParagraph -> review.copy(newHeadingParagraph = action.value)
            is LocalBookImportAction.EditNewHeadingTitle -> review.copy(newHeadingTitle = action.value)
            is LocalBookImportAction.EditParagraphsPerSection -> review.copy(paragraphsPerSection = action.value)
            LocalBookImportAction.AddHeading -> {
                val ordinal = review.newHeadingParagraph.toIntOrNull()
                if (ordinal == null || ordinal !in 0..20_000 || review.newHeadingTitle.isBlank() ||
                    review.headings.any { it.paragraphOrdinal == ordinal }
                ) {
                    state = state.copy(review = review.copy(error = application.getString(R.string.publication_invalid_txt_options)))
                    return
                }
                review.copy(
                    headings = (review.headings + LocalBookImportHeading(ordinal, review.newHeadingTitle)).sortedBy { it.paragraphOrdinal },
                    newHeadingParagraph = "", newHeadingTitle = "",
                )
            }
            LocalBookImportAction.Confirm -> {
                if (review.ready) {
                    confirmation?.complete(review)
                    state = state.copy(busy = true, review = null, status = application.getString(R.string.publication_importing))
                }
                return
            }
            else -> return
        }
        val error = reviewError(edited)
        state = state.copy(review = edited.copy(error = error, ready = error == null))
    }

    private suspend fun importOne(uri: Uri) {
        var fileName = application.getString(R.string.publication_unknown_file)
        var staged: PublicationOriginalStore.StagedOriginal? = null
        var handle: PublicationParseHandle? = null
        var interest: LocalPublicationManager.ImportInterest? = null
        var resultRecorded = false
        try {
            saved[ActiveKey] = true
            state = state.copy(busy = true, fileName = fileName, review = null, status = application.getString(R.string.publication_reading_input))
            val context = currentCoroutineContext()
            fileName = withContext(Dispatchers.IO) { displayName(uri) }
            state = state.copy(fileName = fileName)
            withContext(Dispatchers.IO) {
                openPublicationInput(application.contentResolver, uri).use { staged = publications.originals.stage(it) }
            }
            val original = checkNotNull(staged)
            val prospectiveId = UUID.randomUUID().toString()
            val admittedInterest = publications.registerImportInterest(original, context.job)
            interest = admittedInterest
            val matches = admittedInterest.matches
            withContext(Dispatchers.IO) { handle = publications.parse(prospectiveId, original, cancelled = context::ensureActive) }
            // User review retains immutable admission and staged bytes, never a native session/loader.
            val preview = try {
                val firstResult = checkNotNull(handle).result
                val ready = (firstResult as? PublicationParseResult.Ready)?.publication
                candidates = when (firstResult) {
                    is PublicationParseResult.NeedsCharsetChoice -> firstResult.candidates
                    is PublicationParseResult.Ready -> listOfNotNull(firstResult.publication.txtPreview)
                }
                val initial = LocalBookImportReview(
                    title = ready?.metadata?.title ?: fileName,
                    format = ready?.format?.name ?: LocalPublicationFormat.TXT.name,
                    compatibility = publicationCompatibilityMessages(application.resources, ready?.compatibility?.findings.orEmpty()),
                    matches = matches.map { book ->
                        val identity = book.identity as BookIdentity.Local
                        LocalBookImportMatch(identity.publicationId, book.title, checkNotNull(book.localPublication).originalAvailable)
                    },
                    encodings = candidates.map { LocalBookImportEncoding(it.charsetName, it.preview) },
                    selectedCharset = ready?.localMetadata?.txtOptions?.charsetName,
                    headings = candidates.firstOrNull()?.tableOfContentsSuggestions?.map {
                        LocalBookImportHeading(it.paragraphOrdinal, it.title, selected = false)
                    }.orEmpty(),
                )
                initial to ready?.admissionRecord
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    try { handle?.close() } finally { handle = null }
                }
            }
            val initial = preview.first
            val review = awaitConfirmation(initial.copy(ready = reviewError(initial) == null))
            val association = publications.selectImportDestination(admittedInterest, review.selectedPublicationId, prospectiveId)
            val chosenId = (association?.identity as? BookIdentity.Local)?.publicationId ?: prospectiveId
            val txtOptions = if (association != null) association.localPublication?.txtOptions else review.takeIf { it.encodings.isNotEmpty() }?.let {
                LocalTextParseOptions(
                    charsetName = it.selectedCharset,
                    headingSelections = it.headings.filter(LocalBookImportHeading::selected).map { heading ->
                        LocalTextHeading(heading.paragraphOrdinal, heading.title)
                    },
                    paragraphsPerSection = checkNotNull(it.paragraphsPerSection.toIntOrNull()),
                )
            }
            withContext(Dispatchers.IO) {
                handle = publications.parse(chosenId, original,
                    options = PublicationOptions(txt = txtOptions ?: LocalTextParseOptions()),
                    cancelled = context::ensureActive,
                    admissionRecord = preview.second?.takeIf { chosenId == prospectiveId })
            }
            val parsed = (checkNotNull(handle).result as? PublicationParseResult.Ready)?.publication
                ?: throw PublicationParseException(PublicationFailureCode.INVALID_TEXT_ENCODING)
            context.ensureActive()
            val cover = parsed.metadata.cover as? ReaderResourceRef.Local
            if (cover != null) withContext(Dispatchers.IO) {
                parsed.openResource(cover, context::ensureActive).use {
                    original.addResource(cover.resourceId, cover.mimeType, it, cover.sha256)
                }
            }
            withContext(Dispatchers.IO) {
                parsed.admissionRecord?.let { original.addAdmissionRecord(it, context::ensureActive) }
            }
            // The caller Job is checked under the same mutex as deletion before NonCancellable admission.
            publications.withImportAdmission(admittedInterest) { currentAssociation ->
                withContext(Dispatchers.IO) {
                    val existing = publications.originals.original(chosenId)
                    if (currentAssociation != null && existing != null) {
                        val metadata = checkNotNull(currentAssociation.localPublication)
                        if (existing.format != parsed.format || existing.format != metadata.format ||
                            existing.sha256 != original.sha256 || existing.sizeBytes != original.sizeBytes || !metadata.originalAvailable
                        ) throw PublicationStorageException("Association original changed")
                        parsed.admissionRecord?.let { publications.originals.putAdmissionRecord(existing, it) }
                        if (!application.libraryRepository.addToLibrary(currentAssociation) &&
                            application.libraryRepository.libraryEntry(currentAssociation.identity)?.localMembership != true
                        ) {
                            throw PublicationStorageException("Catalog rejected publication association")
                        }
                    } else {
                        val receipt = publications.originals.commit(original, chosenId, parsed.format, currentAssociation?.localPublication?.catalogOriginal())
                        try {
                            val now = Instant.now()
                            val book = currentAssociation?.copy(localPublication = checkNotNull(currentAssociation.localPublication).copy(originalAvailable = true))
                                ?: LibraryBook(
                                    identity = BookIdentity.Local(chosenId),
                                    title = parsed.metadata.title,
                                    addedAt = now, metadataUpdatedAt = now,
                                    author = parsed.metadata.authors.firstOrNull(),
                                    authors = parsed.metadata.authors.toSet(),
                                    localPublication = parsed.localMetadata,
                                )
                            if (!application.libraryRepository.addToLibrary(book) &&
                                application.libraryRepository.libraryEntry(book.identity)?.localMembership != true
                            ) {
                                throw PublicationStorageException("Catalog rejected publication admission")
                            }
                        } catch (error: Throwable) {
                            receipt.rollbackUncatalogued()
                            throw error
                        }
                        // A retained journal is safe: startup reconciliation observes committed Room metadata.
                        receipt.confirmCatalogCommit()
                    }
                }
                // Report before releasing the deletion mutex: no success action may arrive after pruning.
                catalogGeneration++
                addResult(fileName, application.getString(if (association == null) R.string.publication_import_complete else R.string.publication_association_complete), chosenId)
                resultRecorded = true
            }
        } catch (cancelled: CancellationException) {
            if (!resultRecorded) addResult(fileName, application.getString(R.string.publication_import_cancelled))
            throw cancelled
        } catch (error: Exception) {
            addResult(fileName, publicationFailureMessage(application, error))
        } finally {
            confirmation?.cancel()
            confirmation = null
            candidates = emptyList()
            withContext(NonCancellable) {
                try {
                    withContext(Dispatchers.IO) {
                        try { handle?.close() } finally { staged?.close() }
                    }
                } finally {
                    interest?.let { publications.releaseImportInterest(it) }
                    state = state.copy(busy = false, review = null, status = null)
                    saved[ActiveKey] = false
                }
            }
        }
    }

    private suspend fun awaitConfirmation(review: LocalBookImportReview): LocalBookImportReview {
        val pending = CompletableDeferred<LocalBookImportReview>()
        confirmation = pending
        state = state.copy(busy = false, status = null, review = review)
        return pending.await()
    }

    private fun reviewError(review: LocalBookImportReview): String? {
        if (review.matches.isNotEmpty() && review.selectedPublicationId == null && !review.independentCopy) {
            return application.getString(R.string.publication_choose_duplicate)
        }
        if (review.encodings.isNotEmpty() && review.selectedPublicationId == null) {
            if (review.selectedCharset == null || review.paragraphsPerSection.toIntOrNull() !in 1..4096 ||
                review.headings.any { it.selected && (it.title.isBlank() || it.title.codePointCount(0, it.title.length) > 512) }
            ) return application.getString(R.string.publication_invalid_txt_options)
        }
        return null
    }

    @OptIn(InternalCoroutinesApi::class)
    private suspend fun displayName(uri: Uri): String {
        val signal = CancellationSignal()
        val context = currentCoroutineContext()
        val cancellation = context.job.invokeOnCompletion(onCancelling = true, invokeImmediately = true) { signal.cancel() }
        val name = try {
            application.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null, signal)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        finally { cancellation.dispose() }
        context.ensureActive()
        return name?.takeIf { it.isNotBlank() }?.let {
            it.substring(0, it.offsetByCodePoints(0, minOf(512, it.codePointCount(0, it.length))))
        } ?: application.getString(R.string.publication_unknown_file)
    }


    private fun addResult(fileName: String, message: String, publicationId: String? = null) {
        val result = LocalBookImportResult(UUID.randomUUID().toString(), fileName, message, publicationId)
        saveResults((state.results + result).takeLast(MaxBatchFiles))
    }

    private fun saveResults(results: List<LocalBookImportResult>) {
        state = state.copy(results = results)
        saved[ResultsKey] = JSONArray().also { array -> results.forEach { row ->
            array.put(JSONObject().put("id", row.resultId).put("file", row.fileName).put("message", row.message).put("publication", row.publicationId))
        } }.toString()
    }

    private fun restoreResults(): List<LocalBookImportResult> = runCatching {
        val array = JSONArray(saved.get<String>(ResultsKey) ?: "[]")
        (0 until minOf(array.length(), MaxBatchFiles)).map { index ->
            val row = array.getJSONObject(index)
            LocalBookImportResult(row.getString("id"), row.getString("file"), row.getString("message"), row.optString("publication").takeIf { it.isNotEmpty() })
        }
    }.getOrDefault(emptyList())

    companion object {
        const val MaxBatchFiles = 64
        private const val ActiveKey = "publication.import.active"
        private const val ResultsKey = "publication.import.results"
    }
}

/** Project technical findings once per category without losing affected-content counts. */
internal fun publicationCompatibilityMessages(
    resources: Resources,
    findings: List<PublicationCompatibilityFinding>,
): List<String> = findings.distinct().groupBy {
    (if (it.code == "UNSUPPORTED_MARKUP_ELEMENT") "UNSUPPORTED_MARKUP" else it.code) to it.severity
}.map { (category, items) ->
    val (code, severity) = category
    val label = when (code) {
        "UNSUPPORTED_CSS" -> R.string.publication_compatibility_css
        "STYLESHEET_LIMIT" -> R.string.publication_compatibility_stylesheet_limit
        "EXTERNAL_STYLESHEET_DISABLED" -> R.string.publication_compatibility_external_stylesheet
        "EXTERNAL_HTTPS_RESOURCE_DISABLED" -> R.string.publication_compatibility_external_image
        "FONT_OBFUSCATION_UNSUPPORTED" -> R.string.publication_compatibility_font
        "INERT_SCRIPT" -> R.string.publication_compatibility_script
        "UNSUPPORTED_ACTIVE_CONTENT" -> R.string.publication_compatibility_active_content
        "XML_BASE_IGNORED" -> R.string.publication_compatibility_xml_base
        "TOC_ENTRY_WITHOUT_TARGET" -> R.string.publication_compatibility_toc
        "EPUB3_NCX_NAVIGATION" -> R.string.publication_compatibility_epub3_ncx
        "UNSUPPORTED_MARKUP_ELEMENT", "UNSUPPORTED_MARKUP" -> R.string.publication_compatibility_markup
        "QUOTE_NESTED_CONTENT_DEGRADED" -> R.string.publication_compatibility_quote
        "UNSUPPORTED_LINK_TARGET" -> R.string.publication_compatibility_link
        "EMPTY_LINK" -> R.string.publication_compatibility_empty_link
        "RUBY_ANNOTATION_LIMIT" -> R.string.publication_compatibility_ruby
        "UNSUPPORTED_IMAGE_TARGET" -> R.string.publication_compatibility_image
        "INVALID_IMAGE_DIMENSION" -> R.string.publication_compatibility_image_dimension
        "EMPTY_LIST" -> R.string.publication_compatibility_empty_list
        "LIST_START_UNSUPPORTED" -> R.string.publication_compatibility_list
        "TABLE_SPAN_DEGRADED" -> R.string.publication_compatibility_table_span
        "TABLE_UNSUPPORTED" -> R.string.publication_compatibility_table
        "TABLE_SHAPE_DEGRADED" -> R.string.publication_compatibility_table_shape
        "ANCHOR_ID_LIMIT" -> R.string.publication_compatibility_anchor
        "DUPLICATE_HTML_ATTRIBUTE" -> R.string.publication_compatibility_attribute
        "EMPTY_MOBI_SECTION" -> R.string.publication_compatibility_empty_section
        "UNSUPPORTED_SVG" -> R.string.publication_compatibility_svg
        "UNSUPPORTED_MATHML" -> R.string.publication_compatibility_math
        "UNSUPPORTED_MEDIA" -> R.string.publication_compatibility_media
        "FIXED_LAYOUT_REFLOWED" -> R.string.publication_compatibility_fixed_reflowed
        else -> null
    }
    val description = label?.let { resources.getString(it) }
        ?: resources.getString(R.string.publication_compatibility_other, code)
    val kind = resources.getString(when (severity) {
        PublicationCompatibilitySeverity.NOTICE -> R.string.publication_compatibility_notice
        PublicationCompatibilitySeverity.DEGRADED -> R.string.publication_compatibility_degraded
        PublicationCompatibilitySeverity.RESOURCE_DISABLED -> R.string.publication_compatibility_resource_disabled
    })
    val sections = items.mapNotNull { it.contentId }.distinct().size
    val sectionCount = if (sections == 0) "" else resources.getString(R.string.publication_compatibility_section_count, sections)
    val details = if (code == "UNSUPPORTED_CSS") items.mapNotNull { finding ->
        val detail = finding.message.substringAfter('(', "").substringBefore(");", "")
        when {
            detail.startsWith("CSS property ") -> resources.getString(R.string.publication_compatibility_css_property, detail.removePrefix("CSS property "))
            detail == "CSS resource reference" -> resources.getString(R.string.publication_compatibility_css_resource)
            detail == "stylesheet at-rule" -> resources.getString(R.string.publication_compatibility_css_at_rule)
            detail == "complex or unsupported selector" -> resources.getString(R.string.publication_compatibility_css_selector)
            detail.startsWith("malformed ") -> resources.getString(R.string.publication_compatibility_css_malformed)
            else -> null
        }
    }.distinct() else emptyList()
    buildString {
        append(resources.getString(R.string.publication_compatibility_summary, kind, description, items.size, sectionCount))
        if (details.isNotEmpty()) append('\n').append(details.joinToString("；"))
    }
}

internal suspend fun openPublicationInput(resolver: ContentResolver, uri: Uri): PolledPublicationInput {
    val context = currentCoroutineContext()
    return suspendCancellableCoroutine { continuation ->
        val signal = CancellationSignal()
        continuation.invokeOnCancellation { signal.cancel() }
        try {
            val descriptor = resolver.openFileDescriptor(uri, "r", signal) ?: throw IOException("Input unavailable")
            val input = try { PolledPublicationInput(descriptor, context::ensureActive) }
                catch (error: Exception) { descriptor.close(); throw error }
            continuation.resume(input) { _, owned, _ -> owned.close() }
        } catch (error: Exception) {
            continuation.resumeWith(Result.failure(error))
        }
    }
}


/** Polling a nonblocking owned descriptor makes cancelled/closed providers bounded, including pipes. */
internal class PolledPublicationInput(
    private val descriptor: ParcelFileDescriptor,
    private val cancelled: () -> Unit,
) : InputStream() {
    private val singleByte = ByteArray(1)
    private val poll = StructPollfd().apply {
        fd = descriptor.fileDescriptor
        events = (OsConstants.POLLIN or OsConstants.POLLHUP).toShort()
    }
    private val pollDescriptors = arrayOf(poll)
    init {
        val errno = MobiQuickJsNative.makeInputNonBlocking(descriptor.fd)
        if (errno != 0) throw IOException("Cannot configure publication input", ErrnoException("fcntl", errno))
    }
    override fun read(): Int = if (read(singleByte, 0, 1) < 0) -1 else singleByte[0].toInt() and 255
    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val started = SystemClock.elapsedRealtime()
        while (true) {
            cancelled()
            if (SystemClock.elapsedRealtime() - started >= 30_000L) throw IOException("Input provider timed out")
            try {
                if (Os.poll(pollDescriptors, 250) == 0) continue
                return Os.read(descriptor.fileDescriptor, bytes, offset, length).takeIf { it != 0 } ?: -1
            } catch (error: ErrnoException) {
                if (error.errno != OsConstants.EAGAIN && error.errno != OsConstants.EINTR) throw IOException("Cannot read publication input", error)
            }
        }
    }
    override fun close() = descriptor.close()
}

internal fun publicationFailureMessage(context: android.content.Context, error: Exception): String = context.getString(when (error) {
    is PublicationLimitException -> R.string.publication_limit_failure
    is PublicationParseException -> when (error.code) {
        PublicationFailureCode.PROTECTED_CONTENT -> R.string.publication_protected_failure
        PublicationFailureCode.UNSUPPORTED_FORMAT, PublicationFailureCode.UNSUPPORTED_FIXED_LAYOUT,
        PublicationFailureCode.UNSUPPORTED_COMPRESSION, PublicationFailureCode.UNSUPPORTED_SPINE_CONTENT -> R.string.publication_unsupported_failure
        PublicationFailureCode.INVALID_CHARSET, PublicationFailureCode.INVALID_TEXT_ENCODING,
        PublicationFailureCode.INVALID_TEXT_CONTENT -> R.string.publication_encoding_failure
        PublicationFailureCode.ORIGINAL_LIMIT, PublicationFailureCode.ARCHIVE_LIMIT, PublicationFailureCode.TEXT_LIMIT,
        PublicationFailureCode.SECTION_LIMIT, PublicationFailureCode.BLOCK_LIMIT -> R.string.publication_limit_failure
        else -> R.string.publication_invalid_failure
    }
    is MobiPublicationTransportException -> R.string.publication_parser_failure
    is PublicationStorageException -> R.string.publication_storage_failure
    is SecurityException, is IOException -> R.string.publication_input_unavailable
    else -> R.string.publication_invalid_failure
})
