/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.Closeable
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.tsuyomi.core.publication.MobiPublicationContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tsuyomi.android.publication.MobiPublicationIsolatedClient
import org.tsuyomi.core.files.StorageRoots
import org.tsuyomi.core.publication.MobiPublicationParser
import org.tsuyomi.core.publication.PublicationAdmissionRecord
import org.tsuyomi.core.publication.PublicationDeleteStatus
import org.tsuyomi.core.publication.PublicationFailureCode
import org.tsuyomi.core.publication.OriginalCatalogState
import org.tsuyomi.core.publication.PublicationOptions
import org.tsuyomi.core.publication.PublicationOriginalStore
import org.tsuyomi.core.publication.PublicationParseResult
import org.tsuyomi.core.publication.PublicationParseException
import org.tsuyomi.core.publication.PublicationParser
import org.tsuyomi.core.publication.PublicationSource
import org.tsuyomi.core.publication.PublicationStorageException
import org.tsuyomi.core.publication.detectPublicationFormat
import org.tsuyomi.core.publication.resources.PublicationResourceAccess
import org.tsuyomi.shared.librarydomain.LibraryBook
import org.tsuyomi.shared.librarydomain.LocalPublicationFormat
import org.tsuyomi.shared.librarydomain.LocalPublicationMetadata
import org.tsuyomi.shared.librarydomain.LocalTextParseOptions
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.ReaderResourceRef

/** App composition boundary. Source extensions and publication parsers never acquire each other's grants. */
internal class LocalPublicationManager(private val application: TsuyomiApplication) {
    val originals = PublicationOriginalStore(StorageRoots.from(application))
    val resources = PublicationResourceAccess(application)
    private val recoveryMutex = Mutex()
    private var recovered = false
    private val mobiSlot = Semaphore(1)
    private val importInterests = mutableSetOf<ImportInterest>()
    private val localOwners = mutableSetOf<LocalOwner>()

    internal class LocalOwner internal constructor(
        val job: Job,
        val book: LibraryBook,
        internal val retire: (LocalOwner, Boolean) -> PublicationParseHandle?,
    ) {
        @Volatile internal var valid = true
    }

    suspend fun localBook(identity: BookIdentity.Local): LibraryBook? = recoveryMutex.withLock {
        application.libraryRepository.book(identity)
    }

    suspend fun registerLocalOwner(
        identity: BookIdentity.Local,
        job: Job,
        retire: (LocalOwner, Boolean) -> PublicationParseHandle?,
    ): LocalOwner = recoveryMutex.withLock {
        job.ensureActive()
        val book = application.libraryRepository.book(identity)
            ?: throw PublicationStorageException("Missing publication catalog")
        checkNotNull(book.localPublication)
        job.ensureActive()
        LocalOwner(job, book, retire).also(localOwners::add)
    }

    suspend fun releaseLocalOwner(owner: LocalOwner) = withContext(NonCancellable) {
        recoveryMutex.withLock {
            owner.valid = false
            localOwners.remove(owner)
        }
    }

    /** Check the live session and current catalog within the same boundary as every state write. */
    suspend fun withLocalOwner(owner: LocalOwner?, mutation: suspend (LibraryBook) -> Boolean): Boolean =
        recoveryMutex.withLock {
            val current = currentLocalOwner(owner) ?: return@withLock false
            mutation(current)
        }

    /** The shared mutation mutex is held by both callers. */
    private suspend fun currentLocalOwner(owner: LocalOwner?): LibraryBook? {
        if (owner == null || !owner.valid || !owner.job.isActive || owner !in localOwners) return null
        currentCoroutineContext().ensureActive()
        val current = application.libraryRepository.book(owner.book.identity) ?: return null
        val expected = checkNotNull(owner.book.localPublication)
        val metadata = current.localPublication ?: return null
        if (metadata.catalogOriginal() != expected.catalogOriginal() || metadata.txtOptions != expected.txtOptions) return null
        currentCoroutineContext().ensureActive()
        return current.takeIf { owner.valid && owner.job.isActive }
    }

    internal class ImportInterest internal constructor(val owner: Job, val matches: List<LibraryBook>) {
        internal var destination: BookIdentity.Local? = null
        internal var association: LibraryBook? = null
        internal fun references(identity: BookIdentity.Local): Boolean =
            destination?.let { it == identity } ?: matches.any { it.identity == identity }
    }

    /** The candidate snapshot and its live owner are admitted together, before any user wait. */
    suspend fun registerImportInterest(original: PublicationOriginalStore.StagedOriginal, owner: Job): ImportInterest =
        recoveryMutex.withLock {
            owner.ensureActive()
            val matches = application.libraryRepository.localPublications().filter { book ->
                book.localPublication?.let {
                    it.originalSha256 == original.sha256 && it.originalSizeBytes == original.sizeBytes
                } == true
            }
            owner.ensureActive()
            ImportInterest(owner, matches).also(importInterests::add)
        }

    suspend fun selectImportDestination(interest: ImportInterest, selectedId: String?, prospectiveId: String): LibraryBook? =
        recoveryMutex.withLock {
            requireLiveImport(interest)
            val association = selectedId?.let { id ->
                val expected = interest.matches.firstOrNull { it.identity == BookIdentity.Local(id) }
                    ?: throw PublicationStorageException("Association target was not admitted for review")
                currentAssociation(expected)
            }
            requireLiveImport(interest)
            interest.destination = BookIdentity.Local(selectedId ?: prospectiveId)
            interest.association = association
            association
        }

    /** The original child Job, not NonCancellable's replacement Job, fences the final admission. */
    suspend fun withImportAdmission(interest: ImportInterest, admission: suspend (LibraryBook?) -> Unit) =
        recoveryMutex.withLock {
            requireLiveImport(interest)
            val destination = interest.destination ?: throw PublicationStorageException("Import destination is not admitted")
            withContext(NonCancellable) {
                val current = interest.association?.let { currentAssociation(it) }
                if (current == null && application.libraryRepository.book(destination) != null) {
                    throw PublicationStorageException("Independent import destination is occupied")
                }
                requireLiveImport(interest)
                admission(current)
            }
        }

    suspend fun releaseImportInterest(interest: ImportInterest) = withContext(NonCancellable) {
        recoveryMutex.withLock { importInterests.remove(interest) }
    }

    private fun requireLiveImport(interest: ImportInterest) {
        interest.owner.ensureActive()
        if (interest !in importInterests) throw PublicationStorageException("Import owner is no longer admitted")
    }

    private suspend fun currentAssociation(expected: LibraryBook): LibraryBook {
        val current = application.libraryRepository.book(expected.identity)
            ?: throw PublicationStorageException("Association target was deleted")
        val before = checkNotNull(expected.localPublication)
        val now = current.localPublication ?: throw PublicationStorageException("Association target is not local")
        if (now.catalogOriginal() != before.catalogOriginal() || now.txtOptions != before.txtOptions) {
            throw PublicationStorageException("Association target changed")
        }
        return current
    }

    private val coverlessOriginals = linkedSetOf<Pair<BookIdentity.Local, OriginalCatalogState>>()
    suspend fun recover() = recoveryMutex.withLock {
        if (recovered) return@withLock
        val transferOwnedOriginals = application.transferCoordinator.recoverPendingImport()
        withContext(Dispatchers.IO) {
            val books = application.libraryRepository.localPublications()
            originals.recover(books.catalogOriginals(), transferOwnedOriginals.orEmpty())
            for (book in books) {
                val identity = book.identity as BookIdentity.Local
                val metadata = checkNotNull(book.localPublication)
                if (metadata.originalAvailable && originals.original(identity.publicationId) == null) {
                    application.libraryRepository.setLocalOriginalAvailable(identity, false)
                }
            }
        }
        recovered = transferOwnedOriginals == null
    }

    /** Repairs only visible, available EPUBs; successful negative results are bounded to this process/original. */
    suspend fun recoverCoverResourceId(book: LibraryBook): String? {
        val identity = book.identity as? BookIdentity.Local ?: return null
        val metadata = book.localPublication ?: return null
        if (!metadata.originalAvailable || metadata.format != LocalPublicationFormat.EPUB) return null
        val expected = metadata.catalogOriginal()
        recover()
        return recoveryMutex.withLock {
            withContext(Dispatchers.IO) {
                val context = currentCoroutineContext()
                val current = application.libraryRepository.book(identity)?.localPublication ?: return@withContext null
                if (current.catalogOriginal() != expected) return@withContext null
                current.coverResourceId?.let { return@withContext it }
                val key = identity to expected
                if (key in coverlessOriginals) return@withContext null
                val original = originals.original(identity.publicationId) ?: return@withContext null
                if (original.format != expected.format || original.sha256 != expected.sha256 || original.sizeBytes != expected.sizeBytes) {
                    return@withContext null
                }
                val resourceId = PublicationParser(identity.publicationId, expected.format, original)
                    .recoverEpubCover(context::ensureActive) { reference, input ->
                        originals.putResource(identity.publicationId, reference.resourceId, reference.mimeType, input,
                            reference.sha256, expectedOriginal = original)
                    }
                context.ensureActive()
                if (resourceId != null && !application.libraryRepository.compareAndSetLocalCoverResourceId(
                        identity, LocalPublicationFormat.EPUB, expected.sha256, expected.sizeBytes, null, resourceId,
                    )) {
                    return@withContext application.libraryRepository.book(identity)?.localPublication
                        ?.takeIf { it.catalogOriginal() == expected }?.coverResourceId
                }
                context.ensureActive()
                if (resourceId == null) {
                    coverlessOriginals += key
                    if (coverlessOriginals.size > MAX_COVER_RECOVERY_RESULTS) {
                        coverlessOriginals.remove(coverlessOriginals.first())
                    }
                }
                resourceId
            }
        }
    }

    /** Recovery, cover repair, import admission and removal share one application mutation owner. */
    suspend fun deletePublication(
        identity: BookIdentity.Local,
        originalOnly: Boolean,
        caller: LocalOwner? = null,
        onDeleted: ((BookIdentity.Local) -> Unit)? = null,
    ): PublicationDeleteStatus = recoveryMutex.withLock {
        if (caller != null && (caller.book.identity != identity || currentLocalOwner(caller) == null)) {
            return@withLock PublicationDeleteStatus.CATALOG_REJECTED
        }
        importInterests.forEach { if (it.references(identity)) it.owner.cancel() }
        val affected = localOwners.filter { it.book.identity == identity }
        affected.forEach {
            it.valid = false
            it.job.cancel()
            localOwners.remove(it)
        }
        // Retiring a session only detaches UI/work/handle state. It never acquires this mutex or joins.
        // Admission stays cancellable; retiring the Detail must not cancel its admitted deletion or delivery.
        withContext(NonCancellable) {
            val handles = withContext(Dispatchers.Main.immediate) { affected.mapNotNull { it.retire(it, originalOnly) } }
            withContext(Dispatchers.IO) { handles.forEach(PublicationParseHandle::close) }
            val outcome = withContext(Dispatchers.IO) {
                coverlessOriginals.removeAll { it.first == identity }
                resources.revoke(identity.publicationId)
                if (originalOnly) originals.removeOriginal(identity.publicationId) {
                    application.libraryRepository.setLocalOriginalAvailable(it, false)
                } else originals.deletePublication(identity.publicationId, application.libraryRepository::deleteLocalPublication)
            }
            if (outcome == PublicationDeleteStatus.COMPLETED && !originalOnly && onDeleted != null) {
                withContext(Dispatchers.Main.immediate) {
                    onDeleted(identity)
                }
            }
            outcome
        }
    }

    /** Current admission records avoid whole-book reparse; legacy records rebuild under removal fencing. */
    suspend fun openPublication(book: LibraryBook): PublicationParseHandle {
        val identity = book.identity as BookIdentity.Local
        val metadata = checkNotNull(book.localPublication)
        recover()
        var reservedNativeSlot = false
        var pending: PublicationParseHandle? = null
        try {
            // Never wait for an import's native lease while holding the mutex needed to cancel it.
            if (metadata.format == LocalPublicationFormat.MOBI || metadata.format == LocalPublicationFormat.AZW3) {
                withContext(Dispatchers.IO) {
                    val context = currentCoroutineContext()
                    acquireMobiSlot(context::ensureActive)
                    reservedNativeSlot = true
                }
            }
            val opened = recoveryMutex.withLock {
                withContext(Dispatchers.IO) {
                    val current = currentAssociation(book)
                    val currentMetadata = checkNotNull(current.localPublication)
                    if (!currentMetadata.originalAvailable) throw PublicationStorageException("Publication original is unavailable")
                    val original = originals.original(identity.publicationId)
                        ?: throw PublicationStorageException("Missing private original")
                    if (original.format != currentMetadata.format || original.sha256 != currentMetadata.originalSha256 ||
                        original.sizeBytes != currentMetadata.originalSizeBytes
                    ) throw PublicationStorageException("Original fingerprint no longer matches catalog")
                    val context = currentCoroutineContext()
                    val record = originals.admissionRecord(original, context::ensureActive)
                    val options = PublicationOptions(txt = currentMetadata.txtOptions ?: LocalTextParseOptions())
                    reservedNativeSlot = false // parseWithFormat now owns the acquired native permit.
                    pending = parseWithFormat(identity.publicationId, original.format, original, options, context::ensureActive, record)
                    val handle = checkNotNull(pending)
                    if (handle.admissionRebuilt) {
                        (handle.result as? PublicationParseResult.Ready)?.publication?.admissionRecord?.let {
                            originals.putAdmissionRecord(original, it, context::ensureActive)
                        }
                    }
                    val parsed = (handle.result as? PublicationParseResult.Ready)?.publication
                    val retainedCover = currentMetadata.coverResourceId?.let { originals.resource(identity.publicationId, it) }
                    val coverAvailable = retainedCover?.let { resource ->
                        originals.openResource(ReaderResourceRef.Local(identity.publicationId, resource.resourceId, resource.mimeType, resource.sha256))
                            ?.use { true }
                    } == true
                    if (!coverAvailable && parsed != null) {
                        (parsed.metadata.cover as? ReaderResourceRef.Local)?.let { reference ->
                            parsed.openResource(reference, context::ensureActive).use { input ->
                                originals.putResource(identity.publicationId, reference.resourceId, reference.mimeType, input,
                                    reference.sha256, expectedOriginal = original)
                            }
                            context.ensureActive()
                            if (!application.libraryRepository.compareAndSetLocalCoverResourceId(identity, original.format,
                                    original.sha256, original.sizeBytes, currentMetadata.coverResourceId, reference.resourceId)) {
                                throw PublicationStorageException("Cover catalog descriptor changed")
                            }
                        }
                    }
                    context.ensureActive()
                    handle
                }
            }
            pending = null
            return opened
        } finally {
            if (pending != null || reservedNativeSlot) withContext(NonCancellable + Dispatchers.IO) {
                try { pending?.close() } finally { if (reservedNativeSlot) mobiSlot.release() }
            }
        }
    }

    /** The caller supplies cancellation and disposes the handle before changing book/session. */
    fun parse(
        publicationId: String,
        source: PublicationSource,
        options: PublicationOptions = PublicationOptions(),
        cancelled: () -> Unit = {},
        admissionRecord: PublicationAdmissionRecord? = null,
    ): PublicationParseHandle {
        cancelled()
        val format = detectPublicationFormat(source, cancelled)
        if (format == LocalPublicationFormat.MOBI || format == LocalPublicationFormat.AZW3) acquireMobiSlot(cancelled)
        return parseWithFormat(publicationId, format, source, options, cancelled, admissionRecord)
    }

    private fun acquireMobiSlot(cancelled: () -> Unit) {
        while (!mobiSlot.tryAcquire(100, TimeUnit.MILLISECONDS)) cancelled()
    }

    /** For MOBI/AZW3 the caller has acquired the permit; every failure releases it. */
    private fun parseWithFormat(
        publicationId: String,
        format: LocalPublicationFormat,
        source: PublicationSource,
        options: PublicationOptions,
        cancelled: () -> Unit,
        record: PublicationAdmissionRecord? = null,
    ): PublicationParseHandle {
        var rebuilt = record == null
        if (format != LocalPublicationFormat.MOBI && format != LocalPublicationFormat.AZW3) {
            cancelled()
            val parser = PublicationParser(publicationId, format, source)
            val result = if (record == null) parser.parse(options, cancelled) else try {
                PublicationParseResult.Ready(parser.openAdmitted(record, options, cancelled))
            } catch (error: PublicationParseException) {
                if (error.code != PublicationFailureCode.INVALID_ORIGINAL) throw error
                rebuilt = true
                parser.parse(options, cancelled)
            }
            return PublicationParseHandle(result, admissionRebuilt = rebuilt)
        }
        var session: MobiPublicationContent? = null
        try {
            cancelled()
            session = source.openInputStream().use { stream ->
                check(stream is FileInputStream) { "Isolated parser requires a private original descriptor" }
                MobiPublicationIsolatedClient.open(application, ParcelFileDescriptor.dup(stream.fd), cancelled)
            }
            cancelled()
            val parser = MobiPublicationParser(publicationId, source.sha256, source.sizeBytes, session)
            val publication = if (record == null) parser.parse(cancelled) else try {
                parser.openAdmitted(record, options, cancelled)
            } catch (error: PublicationParseException) {
                if (error.code != PublicationFailureCode.INVALID_ORIGINAL) throw error
                rebuilt = true
                parser.parse(cancelled)
            }
            return PublicationParseHandle(PublicationParseResult.Ready(publication), session, mobiSlot, rebuilt)
        } catch (error: Throwable) {
            safeMobiAdmissionDiagnostic(error)?.let { Log.w("LocalPublication", it) }
            try { session?.close() } finally { mobiSlot.release() }
            throw error
        }
    }

    private fun safeMobiAdmissionDiagnostic(error: Throwable): String? {
        val match = MobiAdmissionDiagnostic.matchEntire(error.message ?: return null) ?: return null
        val stage = match.groupValues[1]
        val ordinal = match.groupValues[2].toIntOrNull()?.takeIf { it in -1..4095 } ?: return null
        val code = match.groupValues[3]
        if (stage !in MobiAdmissionStages) return null
        when (error) {
            is PublicationParseException -> if (code != error.code.name) return null
            is IOException -> if (code != "TRANSPORT_FAILURE") return null
            else -> return null
        }
        return "MOBI_ADMISSION stage=$stage sectionOrdinal=$ordinal code=$code"
    }

    private companion object {
        const val MAX_COVER_RECOVERY_RESULTS = 128
        val MobiAdmissionDiagnostic = Regex("MOBI_ADMISSION stage=([a-z_]{1,40}) sectionOrdinal=(-1|[0-9]{1,4}) code=([A-Z_]{1,48})")
        val MobiAdmissionStages = setOf("original_validation", "catalog_transport", "catalog_validation",
            "cover_resource_validation", "section_transport", "section_payload_validation", "section_resource_validation",
            "markup_normalization", "normalized_section_validation", "toc_anchor_validation", "internal_anchor_validation")
    }
}

internal class PublicationParseHandle(
    val result: PublicationParseResult,
    private val nativeSession: Closeable? = null,
    private val lease: Semaphore? = null,
    val admissionRebuilt: Boolean = false,
) : Closeable {
    private val closed = AtomicBoolean(false)
    override fun close() {
        if (closed.compareAndSet(false, true)) {
            try { nativeSession?.close() } finally { lease?.release() }
        }
    }
}

internal fun LocalPublicationMetadata.catalogOriginal(): OriginalCatalogState =
    OriginalCatalogState(format, originalSha256, originalSizeBytes, originalAvailable)

internal fun Collection<LibraryBook>.catalogOriginals(): Map<String, OriginalCatalogState> = associate { book ->
    val identity = book.identity as BookIdentity.Local
    identity.publicationId to checkNotNull(book.localPublication).catalogOriginal()
}
