/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android.publication

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.LinkedHashMap
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Private, isolated QuickJS host for untrusted local MOBI/KF8 bytes and parser output. */
class MobiPublicationIsolationService : Service() {
    private val activeLock = Any()
    private val operationLock = ReentrantLock()
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "mobi-publication-watchdog").apply { isDaemon = true }
    }
    private val completedLock = Any()
    private val completed = LinkedHashMap<String, String?>()
    @Volatile private var activeJob: ServiceJob? = null
    @Volatile private var session: RuntimeSession? = null

    private val binder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) {
                reply?.writeString(DESCRIPTOR)
                return true
            }
            val response = reply ?: return false
            data.enforceInterface(DESCRIPTOR)
            return when (code) {
                TRANSACTION_OPEN -> startOpen(data, response)
                TRANSACTION_SECTION -> startSection(data, response)
                TRANSACTION_RESOURCE_METADATA -> startResourceMetadata(data, response)
                TRANSACTION_RESOURCE_BYTES -> startResourceBytes(data, response)
                TRANSACTION_RESULT -> writeResult(data, response)
                TRANSACTION_CANCEL -> cancel(data, response)
                TRANSACTION_CLOSE -> closeSession(data, response)
                else -> super.onTransact(code, data, response, flags)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        activeJob?.let { cancelJob(it, "CANCELLED") }
        scheduler.shutdownNow()
        operationLock.withLock {
            session?.let { closeRuntime(it.handle) }
            session = null
        }
        super.onDestroy()
    }

    private fun startOpen(data: Parcel, reply: Parcel): Boolean {
        val requestId = data.readString().orEmpty()
        val sessionId = data.readString().orEmpty()
        val input = data.readTypedObject(ParcelFileDescriptor.CREATOR)
        val output = data.readTypedObject(ParcelFileDescriptor.CREATOR)
        return accept(ServiceJob(requestId, sessionId, JobKind.OPEN, input, output), reply)
    }

    private fun startSection(data: Parcel, reply: Parcel): Boolean {
        val requestId = data.readString().orEmpty()
        val sessionId = data.readString().orEmpty()
        val contentId = data.readString().orEmpty()
        val output = data.readTypedObject(ParcelFileDescriptor.CREATOR)
        return accept(ServiceJob(requestId, sessionId, JobKind.SECTION, output = output, argument = contentId), reply)
    }

    private fun startResourceMetadata(data: Parcel, reply: Parcel): Boolean {
        val requestId = data.readString().orEmpty()
        val sessionId = data.readString().orEmpty()
        val resourceId = data.readString().orEmpty()
        val output = data.readTypedObject(ParcelFileDescriptor.CREATOR)
        return accept(ServiceJob(requestId, sessionId, JobKind.RESOURCE_METADATA, output = output, argument = resourceId), reply)
    }

    private fun startResourceBytes(data: Parcel, reply: Parcel): Boolean {
        val requestId = data.readString().orEmpty()
        val sessionId = data.readString().orEmpty()
        val resourceId = data.readString().orEmpty()
        val output = data.readTypedObject(ParcelFileDescriptor.CREATOR)
        return accept(ServiceJob(requestId, sessionId, JobKind.RESOURCE_BYTES, output = output, argument = resourceId), reply)
    }

    private fun accept(job: ServiceJob, reply: Parcel): Boolean {
        if (!REQUEST_ID.matches(job.requestId) || !SESSION_ID.matches(job.sessionId) || job.output == null ||
            (job.kind == JobKind.OPEN && job.input == null) || !validArgument(job.kind, job.argument)) {
            job.input.closeQuietly()
            job.output.closeQuietly()
            writeAccepted(reply, false, "INVALID_ARGUMENTS")
            return true
        }
        val rejection = synchronized(activeLock) {
            when {
                activeJob != null -> "BUSY"
                job.kind == JobKind.OPEN && session != null -> "SESSION_BUSY"
                job.kind != JobKind.OPEN && session?.id != job.sessionId -> "SESSION_CLOSED"
                else -> {
                    activeJob = job
                    null
                }
            }
        }
        if (rejection != null) {
            job.input.closeQuietly()
            job.output.closeQuietly()
            writeAccepted(reply, false, rejection)
            return true
        }

        val timeout = scheduler.schedule({ cancelJob(job, "TIMEOUT") }, MAX_OPERATION_TIME_MS, TimeUnit.MILLISECONDS)
        Thread({ execute(job, timeout) }, "mobi-publication-${UUID.randomUUID()}").apply {
            isDaemon = true
            start()
        }
        writeAccepted(reply, true, null)
        return true
    }

    private fun writeAccepted(reply: Parcel, accepted: Boolean, errorCode: String?) {
        reply.writeNoException()
        reply.writeInt(if (accepted) 1 else 0)
        reply.writeString(errorCode)
    }

    private fun validArgument(kind: JobKind, value: String?): Boolean = when (kind) {
        JobKind.OPEN -> value == null
        JobKind.SECTION -> value != null && value.isNotBlank() && value.length <= MAX_CONTENT_ID_CHARS
        JobKind.RESOURCE_METADATA, JobKind.RESOURCE_BYTES -> value != null && RESOURCE_ID.matches(value)
    }

    private fun execute(job: ServiceJob, timeout: java.util.concurrent.ScheduledFuture<*>) {
        var failure: String? = null
        var newHandle = 0L
        var stage = job.kind.name.lowercase(java.util.Locale.ROOT)
        try {
            operationLock.withLock {
                if (job.cancelled) throw MobiPublicationTransportException(job.cancelCode ?: "CANCELLED")
                when (job.kind) {
                    JobKind.OPEN -> {
                        stage = "module_read"
                        val module = readParserModule()
                        stage = "runtime_create"
                        newHandle = nativeCall(job) { MobiQuickJsNative.createBookRuntime() }
                        nativeCall(job) {
                            MobiQuickJsNative.prepareBookOperation(newHandle, MAX_OPERATION_TIME_MS.toInt())
                        }
                        stage = "module_evaluate"
                        nativeCall(job) {
                            MobiQuickJsNative.evaluateBookModule(newHandle, module, PARSER_FILENAME.encodeToByteArray())
                        }
                        nativeCall(job) {
                            MobiQuickJsNative.prepareBookOperation(newHandle, MAX_OPERATION_TIME_MS.toInt())
                        }
                        stage = "catalog_inspect"
                        nativeCall(job) {
                            MobiQuickJsNative.callBookInputToFd(
                                newHandle,
                                FUNCTION_INSPECT.encodeToByteArray(),
                                requireNotNull(job.input).fd,
                                requireNotNull(job.output).fd,
                                MAX_INPUT_BYTES,
                                MAX_JSON_OUTPUT_BYTES,
                            )
                        }
                        if (job.cancelled) throw MobiPublicationTransportException(job.cancelCode ?: "CANCELLED")
                        session = RuntimeSession(job.sessionId, newHandle)
                        newHandle = 0L
                    }
                    JobKind.SECTION -> {
                        val handle = requiredSession(job.sessionId)
                        nativeCall(job) { MobiQuickJsNative.prepareBookOperation(handle, MAX_OPERATION_TIME_MS.toInt()) }
                        nativeCall(job) {
                            MobiQuickJsNative.callBookJsonToFd(
                                handle,
                                FUNCTION_SECTION.encodeToByteArray(),
                                org.json.JSONArray().put(job.argument).toString().encodeToByteArray(),
                                requireNotNull(job.output).fd,
                                MAX_JSON_OUTPUT_BYTES,
                            )
                        }
                    }
                    JobKind.RESOURCE_METADATA -> {
                        val handle = requiredSession(job.sessionId)
                        nativeCall(job) { MobiQuickJsNative.prepareBookOperation(handle, MAX_OPERATION_TIME_MS.toInt()) }
                        nativeCall(job) {
                            MobiQuickJsNative.callBookJsonToFd(
                                handle,
                                FUNCTION_RESOURCE_METADATA.encodeToByteArray(),
                                org.json.JSONArray().put(job.argument).toString().encodeToByteArray(),
                                requireNotNull(job.output).fd,
                                MAX_JSON_OUTPUT_BYTES,
                            )
                        }
                    }
                    JobKind.RESOURCE_BYTES -> {
                        val handle = requiredSession(job.sessionId)
                        nativeCall(job) { MobiQuickJsNative.prepareBookOperation(handle, MAX_OPERATION_TIME_MS.toInt()) }
                        nativeCall(job) {
                            MobiQuickJsNative.callBookBinaryToFd(
                                handle,
                                FUNCTION_RESOURCE_BYTES.encodeToByteArray(),
                                requireNotNull(job.argument).encodeToByteArray(),
                                requireNotNull(job.output).fd,
                                MAX_RESOURCE_BYTES,
                            )
                        }
                    }
                }
            }
        } catch (error: Exception) {
            failure = if (job.cancelled) job.cancelCode ?: "CANCELLED" else transportCode(error)
            Log.w("MobiPublication", "service stage=$stage code=$failure")
            if (failure != null && (job.kind == JobKind.OPEN || failure !in SECTION_CONTENT_ERRORS)) {
                if (newHandle != 0L) {
                    closeRuntime(newHandle)
                    newHandle = 0L
                } else {
                    session?.takeIf { it.id == job.sessionId }?.let {
                        session = null
                        closeRuntime(it.handle)
                    }
                }
            }
        } finally {
            timeout.cancel(false)
            var handleToClose = 0L
            synchronized(activeLock) {
                if (activeJob === job) {
                    job.finished = true
                    if (job.cancelled) {
                        failure = job.cancelCode ?: "CANCELLED"
                        if (job.kind == JobKind.OPEN || failure !in SECTION_CONTENT_ERRORS) {
                            handleToClose = if (newHandle != 0L) newHandle
                            else session?.takeIf { it.id == job.sessionId }?.handle ?: 0L
                            if (session?.id == job.sessionId) session = null
                            if (handleToClose == newHandle) newHandle = 0L
                        }
                    }
                }
            }
            if (handleToClose != 0L) closeRuntime(handleToClose)
            if (newHandle != 0L) closeRuntime(newHandle)
            synchronized(completedLock) {
                completed[job.requestId] = failure
                while (completed.size > MAX_COMPLETED_RESULTS) {
                    completed.remove(completed.keys.first())
                }
            }
            job.input.closeQuietly()
            synchronized(activeLock) {
                if (activeJob === job) activeJob = null
            }
            job.output.closeQuietly()
        }
    }

    private inline fun <T> nativeCall(job: ServiceJob, operation: () -> T): T {
        synchronized(job.nativeLock) {
            if (job.cancelled) throw MobiPublicationTransportException(job.cancelCode ?: "CANCELLED")
            job.nativeActive = true
        }
        return try {
            operation()
        } finally {
            synchronized(job.nativeLock) { job.nativeActive = false }
        }
    }

    private fun requiredSession(sessionId: String): Long =
        session?.takeIf { it.id == sessionId }?.handle ?: throw MobiPublicationTransportException("SESSION_CLOSED")

    private fun readParserModule(): ByteArray {
        val output = ByteArrayOutputStream()
        assets.open(PARSER_ASSET).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > MAX_PARSER_MODULE_BYTES - output.size()) {
                    throw MobiPublicationTransportException("NATIVE_UNAVAILABLE")
                }
                output.write(buffer, 0, count)
            }
        }
        return output.toByteArray().also { if (it.isEmpty()) throw MobiPublicationTransportException("NATIVE_UNAVAILABLE") }
    }

    private fun writeResult(data: Parcel, reply: Parcel): Boolean {
        val requestId = data.readString().orEmpty()
        val found: Boolean
        val result: String?
        synchronized(completedLock) {
            found = completed.containsKey(requestId)
            result = completed.remove(requestId)
        }
        reply.writeNoException()
        reply.writeInt(if (found) 1 else 0)
        reply.writeString(result)
        return true
    }

    private fun cancel(data: Parcel, reply: Parcel): Boolean {
        val requestId = data.readString().orEmpty()
        val reason = data.readString().orEmpty().takeIf { it == "TIMEOUT" } ?: "CANCELLED"
        val job = synchronized(activeLock) { activeJob?.takeIf { it.requestId == requestId } }
        val cancelled = job != null && cancelJob(job, reason)
        reply.writeNoException()
        reply.writeInt(if (cancelled) 1 else 0)
        return true
    }

    private fun cancelJob(job: ServiceJob, reason: String): Boolean {
        val nativeActive = synchronized(activeLock) {
            if (activeJob !== job || job.finished) null
            else synchronized(job.nativeLock) {
                job.cancelCode = reason
                job.cancelled = true
                job.nativeActive
            }
        } ?: return false
        if (nativeActive) {
            // A JNI parser call cannot be interrupted by coroutine or thread cancellation.
            Process.killProcess(Process.myPid())
            return true
        }
        job.input.closeQuietly()
        job.output.closeQuietly()
        return true
    }

    private fun closeSession(data: Parcel, reply: Parcel): Boolean {
        val sessionId = data.readString().orEmpty()
        val result = if (activeJob != null) {
            "BUSY"
        } else {
            operationLock.withLock {
                val current = session
                if (current == null || current.id != sessionId) "SESSION_CLOSED"
                else {
                    session = null
                    closeRuntime(current.handle)
                    null
                }
            }
        }
        reply.writeNoException()
        reply.writeString(result)
        return true
    }

    private fun closeRuntime(handle: Long) {
        if (handle != 0L) runCatching { MobiQuickJsNative.closeBookRuntime(handle) }
    }

    private fun transportCode(error: Exception): String {
        if (error is MobiPublicationTransportException) return error.code
        if (error is java.io.InterruptedIOException) return "CANCELLED"
        val message = error.message.orEmpty()
        return when (message) {
            "MALFORMED_INPUT", "PROTECTED_CONTENT", "UNSUPPORTED_FORMAT", "UNSUPPORTED_COMPRESSION", "PRINT_REPLICA",
            "INPUT_LIMIT", "SECTION_LIMIT", "TEXT_LIMIT", "RESOURCE_LIMIT", "OUTPUT_LIMIT", "MISSING_SECTION",
            "MISSING_RESOURCE", "INVALID_RESOURCE", "EXTERNAL_RESOURCE_DENIED", "MALFORMED_XML", "MALFORMED_TOC",
            "FIXED_LAYOUT", "INPUT_FD_MODE", "OUTPUT_FD_MODE", "INPUT_FD_METADATA", "OUTPUT_FD_METADATA",
            "INPUT_FD_IO", "OUTPUT_FD_IO", "INVALID_BINARY_RESULT", "INVALID_ARGUMENTS", "MISSING_FUNCTION",
            "NON_JSON_RESULT", "CLOSED", "NATIVE_UNAVAILABLE", "MEMORY_LIMIT", "EXECUTION_LIMIT", "CANCELLED",
            "JS_EXCEPTION" -> message
            else -> "RUNTIME_FAILURE"
        }
    }

    private fun ParcelFileDescriptor?.closeQuietly() {
        runCatching { this?.close() }
    }

    private data class RuntimeSession(val id: String, val handle: Long)

    private data class ServiceJob(
        val requestId: String,
        val sessionId: String,
        val kind: JobKind,
        val input: ParcelFileDescriptor? = null,
        val output: ParcelFileDescriptor?,
        val argument: String? = null,
        var nativeActive: Boolean = false,
        var finished: Boolean = false,
        @Volatile var cancelled: Boolean = false,
        @Volatile var cancelCode: String? = null,
    ) {
        val nativeLock = Any()
    }

    private enum class JobKind { OPEN, SECTION, RESOURCE_METADATA, RESOURCE_BYTES }

    private companion object {
        const val DESCRIPTOR = "org.tsuyomi.android.publication.MobiPublicationIsolationService"
        const val TRANSACTION_OPEN = IBinder.FIRST_CALL_TRANSACTION
        const val TRANSACTION_SECTION = IBinder.FIRST_CALL_TRANSACTION + 1
        const val TRANSACTION_RESOURCE_METADATA = IBinder.FIRST_CALL_TRANSACTION + 2
        const val TRANSACTION_RESOURCE_BYTES = IBinder.FIRST_CALL_TRANSACTION + 3
        const val TRANSACTION_RESULT = IBinder.FIRST_CALL_TRANSACTION + 4
        const val TRANSACTION_CANCEL = IBinder.FIRST_CALL_TRANSACTION + 5
        const val TRANSACTION_CLOSE = IBinder.FIRST_CALL_TRANSACTION + 6
        const val PARSER_ASSET = "publication/mobi-quickjs.mjs"
        const val PARSER_FILENAME = "mobi-publication-parser.mjs"
        const val FUNCTION_INSPECT = "inspectPublication"
        const val FUNCTION_SECTION = "loadSection"
        const val FUNCTION_RESOURCE_METADATA = "resourceMetadata"
        const val FUNCTION_RESOURCE_BYTES = "resourceBytes"
        const val MAX_INPUT_BYTES = 32 * 1024 * 1024
        const val MAX_RESOURCE_BYTES = 8 * 1024 * 1024
        const val MAX_JSON_OUTPUT_BYTES = 4 * 1024 * 1024
        const val MAX_PARSER_MODULE_BYTES = 8 * 1024 * 1024
        const val MAX_CONTENT_ID_CHARS = 1024
        const val MAX_OPERATION_TIME_MS = 30_000L
        const val MAX_COMPLETED_RESULTS = 64
        val REQUEST_ID = Regex("[A-Za-z0-9_-]{1,64}")
        val SESSION_ID = Regex("[A-Za-z0-9_-]{1,64}")
        val RESOURCE_ID = Regex("[A-Za-z0-9_-]{1,32}")
        val SECTION_CONTENT_ERRORS = setOf(
            "SECTION_LIMIT", "TEXT_LIMIT", "RESOURCE_LIMIT", "OUTPUT_LIMIT", "MISSING_SECTION",
            "MISSING_RESOURCE", "INVALID_RESOURCE", "EXTERNAL_RESOURCE_DENIED", "MALFORMED_XML", "MALFORMED_TOC",
        )
    }
}
