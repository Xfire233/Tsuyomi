/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.source.quickjsruntime

import java.io.Closeable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.suspendCancellableCoroutine

const val QUICKJS_NG_VERSION = "0.16.1"

data class QuickJsRuntimeLimits(
    val maxMemoryBytes: Long,
    val maxExecutionWallTimeMs: Int,
) {
    init {
        require(maxMemoryBytes in 1_048_576..67_108_864) { "QuickJS memory limit is out of range" }
        require(maxExecutionWallTimeMs in 100..30_000) { "QuickJS wall-time limit is out of range" }
    }
}

enum class QuickJsRuntimeError {
    NATIVE_UNAVAILABLE,
    MEMORY_LIMIT,
    EXECUTION_LIMIT,
    CANCELLED,
    JS_EXCEPTION,
    MISSING_FUNCTION,
    INVALID_ARGUMENTS,
    NON_JSON_RESULT,
    CLOSED,
}

class QuickJsRuntimeException(
    val error: QuickJsRuntimeError,
    cause: Throwable? = null,
) : Exception(error.name, cause)

class QuickJsNativeException(message: String) : RuntimeException(message)

/**
 * Owns one QuickJS-ng runtime for one installed extension version. Every evaluation and call is
 * serialized on the same dedicated thread; terminal execution failures discard the context and
 * the next operation recreates it from the saved verified module before serving the caller.
 */
class QuickJsRuntimeLane(
    label: String,
    private val limits: QuickJsRuntimeLimits,
) : Closeable {
    private val closed = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "tsuyomi-quickjs-$label").apply { isDaemon = true }
    }
    @Volatile
    private var nativeHandle: Long = createInitialHandle()
    private var verifiedModule: VerifiedModule? = null
    private var resetRequired = false
    private val nextOperationArmedObserver = AtomicReference<(() -> Unit)?>(null)

    internal fun onNextOperationArmedForTest(observer: () -> Unit) {
        check(nextOperationArmedObserver.compareAndSet(null, observer)) { "An operation observer is already registered" }
    }

    suspend fun evaluateModule(source: ByteArray, filename: String) {
        require(source.isNotEmpty() && source.size <= 8 * 1024 * 1024) { "Invalid module source" }
        require(filename.matches(Regex("^[A-Za-z0-9._/-]+\\.mjs$"))) { "Invalid module filename" }
        val module = VerifiedModule(source.copyOf(), filename.encodeToByteArray())
        submit { handle ->
            QuickJsNative.evaluateModule(
                handle,
                module.source,
                module.filename,
            )
            verifiedModule = module
        }
    }

    suspend fun callJson(functionName: String, argumentsJson: String): String {
        require(functionName.matches(Regex("^[A-Za-z_$][A-Za-z0-9_$]{0,127}$"))) { "Invalid function name" }
        require(argumentsJson.encodeToByteArray().size <= 8 * 1024 * 1024) { "Arguments exceed host limit" }
        val output = submit { handle ->
            QuickJsNative.callJson(
                handle,
                functionName.encodeToByteArray(),
                argumentsJson.encodeToByteArray(),
            )
        }
        return output.decodeToString(throwOnInvalidSequence = true)
    }

    private suspend fun <T> submit(operation: (Long) -> T): T = suspendCancellableCoroutine { continuation ->
        if (closed.get()) {
            continuation.resumeWith(Result.failure(QuickJsRuntimeException(QuickJsRuntimeError.CLOSED)))
            return@suspendCancellableCoroutine
        }
        val cancellationTarget = OperationCancellationTarget()
        val future = executor.submit {
            if (!continuation.isActive) return@submit
            if (closed.get()) {
                continuation.resumeWith(Result.failure(QuickJsRuntimeException(QuickJsRuntimeError.CLOSED)))
                return@submit
            }
            try {
                resetIfRequired()
                if (!continuation.isActive || closed.get()) return@submit
                val operationHandle = nativeHandle
                QuickJsNative.prepareOperation(operationHandle, limits.maxExecutionWallTimeMs)
                if (!continuation.isActive || closed.get()) return@submit
                cancellationTarget.activate(operationHandle)
                nextOperationArmedObserver.getAndSet(null)?.invoke()
                if (!continuation.isActive || closed.get()) return@submit
                val result = try {
                    operation(operationHandle)
                } finally {
                    cancellationTarget.deactivate()
                }
                if (continuation.isActive) continuation.resumeWith(Result.success(result))
            } catch (error: QuickJsNativeException) {
                val mapped = mapNative(error)
                if (invalidatesContext(mapped.error)) discardContext()
                if (continuation.isActive) continuation.resumeWith(Result.failure(mapped))
            } catch (error: Throwable) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(error))
            } finally {
                cancellationTarget.deactivate()
            }
        }
        continuation.invokeOnCancellation {
            cancellationTarget.cancel()
            future.cancel(false)
        }
    }

    private fun resetIfRequired() {
        if (!resetRequired) return
        val previousHandle = nativeHandle
        nativeHandle = 0
        if (previousHandle != 0L) QuickJsNative.close(previousHandle)
        if (closed.get()) throw QuickJsRuntimeException(QuickJsRuntimeError.CLOSED)
        val replacementHandle = QuickJsNative.create(limits.maxMemoryBytes)
        nativeHandle = replacementHandle
        try {
            verifiedModule?.let { module ->
                QuickJsNative.prepareOperation(replacementHandle, limits.maxExecutionWallTimeMs)
                QuickJsNative.evaluateModule(
                    replacementHandle,
                    module.source,
                    module.filename,
                )
            }
            resetRequired = false
        } catch (error: Throwable) {
            nativeHandle = 0
            QuickJsNative.close(replacementHandle)
            throw error
        }
    }

    private fun discardContext() {
        resetRequired = true
        val previousHandle = nativeHandle
        nativeHandle = 0
        if (previousHandle != 0L) QuickJsNative.close(previousHandle)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val activeHandle = nativeHandle
        if (activeHandle != 0L) QuickJsNative.cancel(activeHandle)
        try {
            executor.submit {
                val handle = nativeHandle
                nativeHandle = 0
                if (handle != 0L) QuickJsNative.close(handle)
            }.get()
        } finally {
            executor.shutdownNow()
        }
    }

    private fun createInitialHandle(): Long = try {
        executor.submit<Long> { QuickJsNative.create(limits.maxMemoryBytes) }.get()
    } catch (error: ExecutionException) {
        executor.shutdownNow()
        val cause = error.cause
        if (cause is QuickJsNativeException) throw mapNative(cause)
        throw QuickJsRuntimeException(QuickJsRuntimeError.NATIVE_UNAVAILABLE, cause)
    } catch (error: InterruptedException) {
        Thread.currentThread().interrupt()
        executor.shutdownNow()
        throw QuickJsRuntimeException(QuickJsRuntimeError.NATIVE_UNAVAILABLE, error)
    }

    private class OperationCancellationTarget {
        private val lock = Any()
        private var activeHandle = 0L

        fun activate(handle: Long) {
            check(handle != 0L) { "Cannot execute with a closed QuickJS runtime" }
            synchronized(lock) {
                activeHandle = handle
            }
        }

        fun deactivate() {
            synchronized(lock) {
                activeHandle = 0L
            }
        }

        fun cancel() {
            synchronized(lock) {
                if (activeHandle != 0L) QuickJsNative.cancel(activeHandle)
            }
        }
    }

    private data class VerifiedModule(
        val source: ByteArray,
        val filename: ByteArray,
    )

    private companion object {
        fun invalidatesContext(error: QuickJsRuntimeError): Boolean = when (error) {
            QuickJsRuntimeError.MISSING_FUNCTION,
            QuickJsRuntimeError.INVALID_ARGUMENTS,
            QuickJsRuntimeError.NON_JSON_RESULT,
            QuickJsRuntimeError.CLOSED -> false
            else -> true
        }

        fun mapNative(error: QuickJsNativeException): QuickJsRuntimeException {
            val code = runCatching { QuickJsRuntimeError.valueOf(error.message.orEmpty()) }
                .getOrDefault(QuickJsRuntimeError.JS_EXCEPTION)
            return QuickJsRuntimeException(code, error)
        }
    }
}


private object QuickJsNative {
    init {
        System.loadLibrary("tsuyomi_quickjs")
    }

    external fun create(memoryLimitBytes: Long): Long
    external fun prepareOperation(nativeHandle: Long, wallTimeMillis: Int)
    external fun evaluateModule(
        nativeHandle: Long,
        source: ByteArray,
        filename: ByteArray,
    )
    external fun callJson(
        nativeHandle: Long,
        functionName: ByteArray,
        argumentsJson: ByteArray,
    ): ByteArray
    external fun cancel(nativeHandle: Long)
    external fun close(nativeHandle: Long)
}
