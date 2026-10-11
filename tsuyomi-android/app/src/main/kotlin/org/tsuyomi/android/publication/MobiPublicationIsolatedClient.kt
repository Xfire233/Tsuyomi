/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android.publication

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.DeadObjectException
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import android.util.Log
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.tsuyomi.core.publication.MobiPublicationCatalog
import org.tsuyomi.core.publication.MobiPublicationContent
import org.tsuyomi.core.publication.MobiPublicationResource
import org.tsuyomi.core.publication.MobiPublicationSection
import org.tsuyomi.core.publication.PublicationSection
import org.tsuyomi.core.publication.PublicationStylesheet
import org.tsuyomi.core.publication.PublicationTocEntry
import org.tsuyomi.core.publication.PublicationFailureCode
import org.tsuyomi.core.publication.PublicationParseException
import org.tsuyomi.shared.librarydomain.LocalPublicationFormat
import org.tsuyomi.shared.sourcecontract.ReaderLinkTarget

/** Opens one descriptor-backed MOBI session in the private isolated process. */
object MobiPublicationIsolatedClient {
    /** Takes ownership of [input], which must be a read-only descriptor for the private original. */
    fun open(
        context: Context,
        input: ParcelFileDescriptor,
        cancelled: () -> Unit = {},
    ): MobiPublicationContent {
        var session: PublicationSession? = null
        try {
            cancelled()
            session = PublicationSession(context.applicationContext)
            val document = session.open(input, cancelled)
            return session.withCatalog(document)
        } catch (error: Throwable) {
            session?.disposeAfterOpenFailure()
            rethrowPublicationFailure(error)
        } finally {
            runCatching { input.close() }
        }
    }

    private class PublicationSession(context: Context) {
        private val applicationContext = context
        private val connected = CountDownLatch(1)
        private val closed = AtomicBoolean(false)
        private val unbound = AtomicBoolean(false)
        private val requestLock = ReentrantLock()
        @Volatile private var remote: IBinder? = null
        @Volatile private var activeRequestId: String? = null
        private val sessionId = UUID.randomUUID().toString()
        private val deathRecipient = IBinder.DeathRecipient {
            remote = null
            connected.countDown()
        }
        private val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                if (closed.get()) return
                if (service == null) {
                    connected.countDown()
                    return
                }
                remote = service
                runCatching { service.linkToDeath(deathRecipient, 0) }
                connected.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                remote = null
            }

            override fun onBindingDied(name: ComponentName?) {
                remote = null
                connected.countDown()
            }

            override fun onNullBinding(name: ComponentName?) {
                remote = null
                connected.countDown()
            }
        }
        private val bound: Boolean

        init {
            val intent = Intent().setComponent(ComponentName(applicationContext, MobiPublicationIsolationService::class.java))
            // A retiring/killed book parser must not be rebound by the next book's session.
            bound = applicationContext.bindIsolatedService(
                intent, Context.BIND_AUTO_CREATE, sessionId.replace('-', '_'),
                applicationContext.mainExecutor, serviceConnection,
            )
            if (!bound) throw MobiPublicationTransportException("NATIVE_UNAVAILABLE")
        }

        fun open(input: ParcelFileDescriptor, cancelled: () -> Unit): MobiPublicationCatalog = requestLock.withLock {
            publicationCall("catalog_transport_decode") {
                val result = requestBytes(
                    transaction = TRANSACTION_OPEN,
                    argument = null,
                    input = input,
                    maximumBytes = MAX_JSON_OUTPUT_BYTES,
                    cancelled = cancelled,
                )
                decodeCatalog(decodeUtf8(result)) { ordinal ->
                    applicationContext.getString(org.tsuyomi.android.R.string.publication_numbered_section, ordinal + 1)
                }
            }
        }

        fun withCatalog(value: MobiPublicationCatalog): MobiPublicationContent = object : MobiPublicationContent {
            override val catalog: MobiPublicationCatalog = value

            override fun section(contentId: String, cancelled: () -> Unit): MobiPublicationSection {
                require(contentId.isNotBlank() && contentId.length <= MAX_CONTENT_ID_CHARS)
                return requestLock.withLock {
                    publicationCall("section_transport_decode") {
                        val json = requestBytes(TRANSACTION_SECTION, contentId, null, MAX_JSON_OUTPUT_BYTES, cancelled)
                        val allowedIds = catalog.sections.mapTo(HashSet()) { it.contentId }
                        decodeSection(decodeUtf8(json), contentId, allowedIds)
                    }
                }
            }

            override fun resource(resourceId: String, cancelled: () -> Unit): java.io.InputStream {
                requireResourceId(resourceId)
                return requestLock.withLock {
                    publicationCall("resource_bytes_transport") {
                        ByteArrayInputStream(
                            requestBytes(TRANSACTION_RESOURCE_BYTES, resourceId, null, MAX_RESOURCE_BYTES, cancelled),
                        )
                    }
                }
            }

            override fun resourceMetadata(resourceId: String): MobiPublicationResource {
                requireResourceId(resourceId)
                return requestLock.withLock {
                    publicationCall("resource_metadata_transport_decode") {
                        val json = requestBytes(
                            TRANSACTION_RESOURCE_METADATA,
                            resourceId,
                            null,
                            MAX_JSON_OUTPUT_BYTES,
                            {},
                        )
                        decodeResourceMetadata(decodeUtf8(json), resourceId)
                    }
                }
            }

            override fun close() {
                closeSession()
            }
        }

        fun disposeAfterOpenFailure() {
            closeSession()
        }

        private fun requestBytes(
            transaction: Int,
            argument: String?,
            input: ParcelFileDescriptor?,
            maximumBytes: Int,
            cancelled: () -> Unit,
        ): ByteArray {
            ensureConnected(cancelled)
            ensureOpen()
            val requestId = UUID.randomUUID().toString()
            activeRequestId = requestId
            val pipe = try {
                ParcelFileDescriptor.createPipe()
            } catch (error: IOException) {
                activeRequestId = null
                throw MobiPublicationTransportException("RUNTIME_FAILURE", error)
            }
            val readEnd = pipe[0]
            val writeEnd = pipe[1]
            var accepted = false
            var completed = false
            try {
                accepted = startRequest(transaction, requestId, argument, input, writeEnd)
                val payload = readBounded(readEnd, requestId, maximumBytes, cancelled)
                val errorCode = requestResult(requestId)
                completed = true
                if (errorCode != null) throw MobiPublicationTransportException(errorCode)
                if (payload.isEmpty()) throw MobiPublicationTransportException("RUNTIME_FAILURE")
                return payload
            } catch (error: Throwable) {
                if (accepted && !completed) {
                    cancelRequest(requestId, "CANCELLED")
                    invalidate()
                } else if (
                    error is MobiPublicationTransportException &&
                    (transaction == TRANSACTION_OPEN || error.code in TERMINAL_ERRORS)
                ) {
                    invalidate()
                }
                throw error
            } finally {
                activeRequestId = null
                runCatching { readEnd.close() }
                runCatching { writeEnd.close() }
            }
        }

        private fun startRequest(
            transaction: Int,
            requestId: String,
            argument: String?,
            input: ParcelFileDescriptor?,
            output: ParcelFileDescriptor,
        ): Boolean {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeString(requestId)
                data.writeString(sessionId)
                when (transaction) {
                    TRANSACTION_OPEN -> {
                        data.writeTypedObject(input, 0)
                        data.writeTypedObject(output, 0)
                    }
                    TRANSACTION_SECTION, TRANSACTION_RESOURCE_METADATA, TRANSACTION_RESOURCE_BYTES -> {
                        data.writeString(argument)
                        data.writeTypedObject(output, 0)
                    }
                    else -> throw IllegalArgumentException("Unsupported publication transaction")
                }
                if (checkNotNull(remote).transact(transaction, data, reply, 0).not()) {
                    throw MobiPublicationTransportException("SERVICE_DIED")
                }
                reply.readException()
                val accepted = reply.readInt() != 0
                val errorCode = reply.readString()
                if (!accepted) throw MobiPublicationTransportException(errorCode ?: "RUNTIME_FAILURE")
                return true
            } catch (error: DeadObjectException) {
                throw MobiPublicationTransportException("SERVICE_DIED", error)
            } catch (error: RemoteException) {
                throw MobiPublicationTransportException("SERVICE_DIED", error)
            } finally {
                data.recycle()
                reply.recycle()
                runCatching { output.close() }
            }
        }

        private fun readBounded(
            descriptor: ParcelFileDescriptor,
            requestId: String,
            maximumBytes: Int,
            cancelled: () -> Unit,
        ): ByteArray {
            val output = ByteArrayOutputStream(minOf(maximumBytes, 8 * 1024))
            val buffer = ByteArray(8 * 1024)
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(MAX_OPERATION_TIME_MS)
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                while (true) {
                    try {
                        cancelled()
                    } catch (error: Throwable) {
                        cancelRequest(requestId, "CANCELLED")
                        invalidate()
                        throw error
                    }
                    if (Thread.currentThread().isInterrupted) {
                        cancelRequest(requestId, "CANCELLED")
                        invalidate()
                        throw MobiPublicationTransportException("CANCELLED")
                    }
                    if (System.nanoTime() >= deadline) {
                        cancelRequest(requestId, "TIMEOUT")
                        invalidate()
                        throw MobiPublicationTransportException("TIMEOUT")
                    }
                    val poll = StructPollfd().apply {
                        fd = descriptor.fileDescriptor
                        events = (OsConstants.POLLIN or OsConstants.POLLHUP or OsConstants.POLLERR).toShort()
                    }
                    val ready = try {
                        Os.poll(arrayOf(poll), POLL_INTERVAL_MS)
                    } catch (error: Exception) {
                        throw MobiPublicationTransportException("RUNTIME_FAILURE", error)
                    }
                    if (ready == 0) continue
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    if (count > maximumBytes - output.size()) {
                        cancelRequest(requestId, "OUTPUT_LIMIT")
                        invalidate()
                        throw MobiPublicationTransportException("OUTPUT_LIMIT")
                    }
                    output.write(buffer, 0, count)
                }
            }
            return output.toByteArray()
        }

        private fun requestResult(requestId: String): String? {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeString(requestId)
                if (checkNotNull(remote).transact(TRANSACTION_RESULT, data, reply, 0).not()) {
                    throw MobiPublicationTransportException("SERVICE_DIED")
                }
                reply.readException()
                if (reply.readInt() == 0) throw MobiPublicationTransportException("SERVICE_DIED")
                return reply.readString()
            } catch (error: DeadObjectException) {
                throw MobiPublicationTransportException("SERVICE_DIED", error)
            } catch (error: RemoteException) {
                throw MobiPublicationTransportException("SERVICE_DIED", error)
            } finally {
                data.recycle()
                reply.recycle()
            }
        }

        private fun cancelRequest(requestId: String, reason: String) {
            val binder = remote ?: return
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeString(requestId)
                data.writeString(reason)
                binder.transact(TRANSACTION_CANCEL, data, reply, 0)
                reply.readException()
                reply.readInt()
            } catch (_: Exception) {
                // Cancellation may kill the isolated process, in which case Binder reports its death.
            } finally {
                data.recycle()
                reply.recycle()
            }
        }

        private fun closeSession() {
            if (!closed.compareAndSet(false, true)) return
            activeRequestId?.let { cancelRequest(it, "CANCELLED") }
            requestLock.withLock {
                val binder = remote
                if (binder != null && binder.isBinderAlive) {
                    val data = Parcel.obtain()
                    val reply = Parcel.obtain()
                    try {
                        data.writeInterfaceToken(DESCRIPTOR)
                        data.writeString(sessionId)
                        binder.transact(TRANSACTION_CLOSE, data, reply, 0)
                        reply.readException()
                        val errorCode = reply.readString()
                        if (errorCode != null && errorCode != "SESSION_CLOSED") {
                            throw MobiPublicationTransportException(errorCode)
                        }
                    } catch (_: Exception) {
                        // Closing the last binding also releases the isolated service process.
                    } finally {
                        data.recycle()
                        reply.recycle()
                    }
                }
                invalidate()
            }
        }

        private fun ensureConnected(cancelled: () -> Unit) {
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(BIND_TIMEOUT_MS)
            var signalled = remote != null
            while (!signalled) {
                try {
                    cancelled()
                } catch (error: Throwable) {
                    invalidate()
                    throw error
                }
                val remainingNanos = deadline - System.nanoTime()
                if (remainingNanos <= 0L) break
                try {
                    signalled = connected.await(
                        minOf(TimeUnit.NANOSECONDS.toMillis(remainingNanos).coerceAtLeast(1), POLL_INTERVAL_MS.toLong()),
                        TimeUnit.MILLISECONDS,
                    )
                } catch (error: InterruptedException) {
                    Thread.currentThread().interrupt()
                    invalidate()
                    throw MobiPublicationTransportException("CANCELLED", error)
                }
            }
            if (remote == null) {
                invalidate()
                throw MobiPublicationTransportException(if (!signalled) "TIMEOUT" else "SERVICE_DIED")
            }
        }

        private fun ensureOpen() {
            if (closed.get()) throw MobiPublicationTransportException("SESSION_CLOSED")
            if (remote?.isBinderAlive != true) {
                invalidate()
                throw MobiPublicationTransportException("SERVICE_DIED")
            }
        }

        private fun invalidate() {
            closed.set(true)
            if (!unbound.compareAndSet(false, true)) return
            remote?.let { runCatching { it.unlinkToDeath(deathRecipient, 0) } }
            remote = null
            if (bound) runCatching { applicationContext.unbindService(serviceConnection) }
        }
    }

    private inline fun <T> publicationCall(stage: String, block: () -> T): T = try {
        block()
    } catch (error: MobiPublicationTransportException) {
        Log.w("MobiPublication", "client stage=$stage code=${error.code}")
        rethrowPublicationFailure(error)
    }

    private fun rethrowPublicationFailure(error: Throwable): Nothing {
        if (error !is MobiPublicationTransportException) throw error
        if (error.code == "CANCELLED") {
            throw CancellationException("Local publication parsing was cancelled").also { it.initCause(error) }
        }
        val code = when (error.code) {
            "MALFORMED_INPUT", "PARSER_FAILURE" -> PublicationFailureCode.INVALID_ORIGINAL
            "PROTECTED_CONTENT" -> PublicationFailureCode.PROTECTED_CONTENT
            "UNSUPPORTED_FORMAT", "PRINT_REPLICA" -> PublicationFailureCode.UNSUPPORTED_FORMAT
            "UNSUPPORTED_COMPRESSION" -> PublicationFailureCode.UNSUPPORTED_COMPRESSION
            "FIXED_LAYOUT" -> PublicationFailureCode.UNSUPPORTED_FIXED_LAYOUT
            "INPUT_LIMIT" -> PublicationFailureCode.ORIGINAL_LIMIT
            "SECTION_LIMIT" -> PublicationFailureCode.SECTION_LIMIT
            "TEXT_LIMIT", "OUTPUT_LIMIT" -> PublicationFailureCode.TEXT_LIMIT
            "RESOURCE_LIMIT" -> PublicationFailureCode.ARCHIVE_LIMIT
            "INVALID_RESOURCE" -> PublicationFailureCode.INVALID_RESOURCE
            "EXTERNAL_RESOURCE_DENIED" -> PublicationFailureCode.EXTERNAL_RESOURCE_DENIED
            "MISSING_SECTION" -> PublicationFailureCode.MISSING_SPINE_ITEM
            "MISSING_RESOURCE" -> PublicationFailureCode.MISSING_RESOURCE
            "MALFORMED_XML" -> PublicationFailureCode.MALFORMED_XML
            "MALFORMED_TOC" -> PublicationFailureCode.MALFORMED_TOC
            else -> throw error
        }
        throw PublicationParseException(code, cause = error)
    }

    private fun decodeCatalog(
        text: String,
        untitledSectionTitle: (Int) -> String,
    ): MobiPublicationCatalog = parseJson(text, "PARSER_FAILURE") { root ->
        val format = when (root.getString("format")) {
            "MOBI" -> LocalPublicationFormat.MOBI
            "AZW3" -> LocalPublicationFormat.AZW3
            else -> throw MobiPublicationTransportException("UNSUPPORTED_FORMAT")
        }
        val title = boundedString(root.getString("title"), MAX_TITLE_CHARS)
        val authorsJson = root.getJSONArray("authors")
        if (authorsJson.length() > MAX_AUTHORS) throw MobiPublicationTransportException("PARSER_FAILURE")
        val authors = List(authorsJson.length()) { boundedString(authorsJson.getString(it), MAX_TITLE_CHARS) }
        val sectionsJson = root.getJSONArray("sections")
        if (sectionsJson.length() !in 1..MAX_SECTIONS) throw MobiPublicationTransportException("SECTION_LIMIT")
        val sectionIds = HashSet<String>(sectionsJson.length())
        val authoredSectionIds = HashSet<String>()
        val sections = List(sectionsJson.length()) { index ->
            val section = sectionsJson.getJSONObject(index)
            val contentId = boundedString(section.getString("contentId"), MAX_CONTENT_ID_CHARS)
            if (!sectionIds.add(contentId)) throw MobiPublicationTransportException("PARSER_FAILURE")
            val rawTitle = boundedString(section.getString("title"), MAX_TITLE_CHARS)
            val hasAuthoredTitle = section.getBoolean("hasAuthoredTitle")
            val sectionTitle = if (hasAuthoredTitle) rawTitle
                else boundedString(untitledSectionTitle(index), MAX_TITLE_CHARS)
            if (hasAuthoredTitle) authoredSectionIds.add(contentId)
            PublicationSection(
                contentId = contentId,
                title = sectionTitle,
                ordinal = index,
                linear = section.optBoolean("linear", true),
            )
        }
        val tocState = TocParseState()
        val toc = decodeToc(root.getJSONArray("tableOfContents"), sectionIds, tocState, 0, untitledSectionTitle)
        val cover = root.optString("coverResourceId").takeIf { it.isNotEmpty() && it != "null" }
            ?.also(::requireResourceId)
        MobiPublicationCatalog(
            format = format,
            title = title,
            authors = authors,
            language = optionalBoundedString(root, "language", MAX_METADATA_CHARS),
            identifier = optionalBoundedString(root, "identifier", MAX_METADATA_CHARS),
            sections = sections,
            tableOfContents = toc,
            coverResourceId = cover,
            authoredSectionIds = authoredSectionIds,
        )
    }

    private fun decodeSection(
        text: String,
        requestedContentId: String,
        validSectionIds: Set<String>,
    ): MobiPublicationSection = parseJson(text, "PARSER_FAILURE") { root ->
        val contentId = boundedString(root.getString("contentId"), MAX_CONTENT_ID_CHARS)
        if (contentId != requestedContentId) throw MobiPublicationTransportException("MISSING_SECTION")
        val html = root.getString("html")
        if (html.toByteArray(Charsets.UTF_8).size > MAX_SECTION_TEXT_BYTES) {
            throw MobiPublicationTransportException("TEXT_LIMIT")
        }
        val stylesJson = root.getJSONArray("stylesheets")
        if (stylesJson.length() > MAX_STYLESHEETS) throw MobiPublicationTransportException("TEXT_LIMIT")
        val styles = List(stylesJson.length()) { index ->
            val style = stylesJson.getJSONObject(index)
            val textValue = style.getString("text")
            if (textValue.toByteArray(Charsets.UTF_8).size > MAX_SECTION_TEXT_BYTES) {
                throw MobiPublicationTransportException("TEXT_LIMIT")
            }
            PublicationStylesheet(
                sourceName = boundedString(style.getString("sourceName"), MAX_METADATA_CHARS),
                text = textValue,
            )
        }
        val linksJson = root.getJSONArray("links")
        if (linksJson.length() > MAX_LINKS) throw MobiPublicationTransportException("SECTION_LIMIT")
        val links = LinkedHashMap<String, ReaderLinkTarget.Internal>()
        for (index in 0 until linksJson.length()) {
            val link = linksJson.getJSONObject(index)
            val href = boundedString(link.getString("href"), MAX_HREF_CHARS)
            if (link.isNull("contentId")) continue
            val targetId = boundedString(link.getString("contentId"), MAX_CONTENT_ID_CHARS)
            val fragment = optionalBoundedString(link, "fragment", MAX_FRAGMENT_CHARS)
            if (targetId !in validSectionIds) continue
            links.putIfAbsent(href, ReaderLinkTarget.Internal(targetId, fragment))
        }
        val resourceJson = root.getJSONArray("resourceIds")
        if (resourceJson.length() > MAX_RESOURCES) throw MobiPublicationTransportException("RESOURCE_LIMIT")
        val resourceIds = LinkedHashSet<String>(resourceJson.length())
        for (index in 0 until resourceJson.length()) resourceIds += requireResourceId(resourceJson.getString(index))
        MobiPublicationSection(contentId, html, styles, links, resourceIds.toList())
    }

    private fun decodeResourceMetadata(text: String, requestedId: String): MobiPublicationResource =
        parseJson(text, "PARSER_FAILURE") { root ->
            val resourceId = requireResourceId(root.getString("resourceId"))
            if (resourceId != requestedId) throw MobiPublicationTransportException("MISSING_RESOURCE")
            val mimeType = boundedString(root.getString("mimeType"), MAX_MIME_CHARS)
            val size = root.getLong("sizeBytes")
            if (size !in 1..MAX_RESOURCE_BYTES.toLong()) throw MobiPublicationTransportException("RESOURCE_LIMIT")
            MobiPublicationResource(resourceId, mimeType, size)
        }

    private class TocParseState(var entries: Int = 0)

    private fun decodeToc(
        input: JSONArray,
        validSectionIds: Set<String>,
        state: TocParseState,
        depth: Int,
        untitledSectionTitle: (Int) -> String,
    ): List<PublicationTocEntry> {
        if (depth > MAX_TOC_DEPTH || input.length() > MAX_TOC_ENTRIES) {
            throw MobiPublicationTransportException("SECTION_LIMIT")
        }
        return List(input.length()) { index ->
            if (++state.entries > MAX_TOC_ENTRIES) throw MobiPublicationTransportException("SECTION_LIMIT")
            val item = input.getJSONObject(index)
            val contentId = boundedString(item.getString("contentId"), MAX_CONTENT_ID_CHARS)
            if (contentId !in validSectionIds) throw MobiPublicationTransportException("MISSING_SECTION")
            val rawTitle = item.getString("title")
            if (rawTitle.length > MAX_TITLE_CHARS) throw MobiPublicationTransportException("TEXT_LIMIT")
            val title = if (item.getBoolean("hasAuthoredTitle")) boundedString(rawTitle, MAX_TITLE_CHARS)
                else boundedString(untitledSectionTitle(state.entries - 1), MAX_TITLE_CHARS)
            PublicationTocEntry(
                title = title,
                contentId = contentId,
                fragment = optionalBoundedString(item, "fragment", MAX_FRAGMENT_CHARS),
                children = decodeToc(item.getJSONArray("children"), validSectionIds, state, depth + 1, untitledSectionTitle),
            )
        }
    }

    private inline fun <T> parseJson(text: String, fallback: String, decode: (JSONObject) -> T): T = try {
        decode(JSONObject(text))
    } catch (error: MobiPublicationTransportException) {
        throw error
    } catch (error: JSONException) {
        throw MobiPublicationTransportException(fallback, error)
    } catch (error: IllegalArgumentException) {
        throw MobiPublicationTransportException(fallback, error)
    }

    private fun decodeUtf8(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(bytes))
            .toString()
    } catch (error: Exception) {
        throw MobiPublicationTransportException("PARSER_FAILURE", error)
    }

    private fun boundedString(value: String, maxChars: Int): String {
        if (value.isEmpty() || value.length > maxChars) throw MobiPublicationTransportException("PARSER_FAILURE")
        return value
    }

    private fun optionalBoundedString(json: JSONObject, key: String, maxChars: Int): String? {
        if (!json.has(key) || json.isNull(key)) return null
        return json.getString(key).takeIf(String::isNotEmpty)?.let { boundedString(it, maxChars) }
    }

    private fun requireResourceId(value: String): String {
        if (!RESOURCE_ID.matches(value)) throw MobiPublicationTransportException("MISSING_RESOURCE")
        return value
    }

    private val TERMINAL_ERRORS = setOf(
        "CANCELLED", "TIMEOUT", "SERVICE_DIED", "NATIVE_UNAVAILABLE", "MEMORY_LIMIT", "EXECUTION_LIMIT",
        "CLOSED", "SESSION_CLOSED", "MALFORMED_INPUT", "PROTECTED_CONTENT", "UNSUPPORTED_FORMAT", "UNSUPPORTED_COMPRESSION",
        "PRINT_REPLICA", "FIXED_LAYOUT", "INPUT_LIMIT", "INPUT_FD_MODE", "INPUT_FD_METADATA", "INPUT_FD_IO",
        "OUTPUT_FD_MODE", "OUTPUT_FD_METADATA", "OUTPUT_FD_IO", "INVALID_BINARY_RESULT", "INVALID_ARGUMENTS",
        "MISSING_FUNCTION", "NON_JSON_RESULT", "RUNTIME_FAILURE", "JS_EXCEPTION",
    )
    private val DESCRIPTOR = "org.tsuyomi.android.publication.MobiPublicationIsolationService"
    private val TRANSACTION_OPEN = IBinder.FIRST_CALL_TRANSACTION
    private val TRANSACTION_SECTION = IBinder.FIRST_CALL_TRANSACTION + 1
    private val TRANSACTION_RESOURCE_METADATA = IBinder.FIRST_CALL_TRANSACTION + 2
    private val TRANSACTION_RESOURCE_BYTES = IBinder.FIRST_CALL_TRANSACTION + 3
    private val TRANSACTION_RESULT = IBinder.FIRST_CALL_TRANSACTION + 4
    private val TRANSACTION_CANCEL = IBinder.FIRST_CALL_TRANSACTION + 5
    private val TRANSACTION_CLOSE = IBinder.FIRST_CALL_TRANSACTION + 6
    private val MAX_JSON_OUTPUT_BYTES = 4 * 1024 * 1024
    private val MAX_RESOURCE_BYTES = 8 * 1024 * 1024
    private val MAX_SECTION_TEXT_BYTES = 2 * 1024 * 1024
    private val MAX_CONTENT_ID_CHARS = 1024
    private val MAX_TITLE_CHARS = 512
    private val MAX_METADATA_CHARS = 1024
    private val MAX_AUTHORS = 128
    private val MAX_SECTIONS = 4096
    private val MAX_STYLESHEETS = 256
    private val MAX_LINKS = 16384
    private val MAX_RESOURCES = 512
    private val MAX_HREF_CHARS = 4096
    private val MAX_FRAGMENT_CHARS = 1024
    private val MAX_MIME_CHARS = 128
    private val MAX_TOC_ENTRIES = 4096
    private val MAX_TOC_DEPTH = 32
    private val MAX_OPERATION_TIME_MS = 30_000L
    private val BIND_TIMEOUT_MS = 10_000L
    private val POLL_INTERVAL_MS = 100
    private val RESOURCE_ID = Regex("[A-Za-z0-9_-]{1,32}")
}
