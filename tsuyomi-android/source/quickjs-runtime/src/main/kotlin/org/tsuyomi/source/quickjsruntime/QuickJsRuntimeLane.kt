/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.source.quickjsruntime

import java.io.Closeable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
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
 * serialized on the same dedicated thread; cancellation is observed by QuickJS's interrupt hook.
 */
class QuickJsRuntimeLane(
    label: String,
    private val limits: QuickJsRuntimeLimits,
) : Closeable {
    private val closed = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "tsuyomi-quickjs-$label").apply { isDaemon = true }
    }
    private val nativeHandle: Long = try {
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

    suspend fun evaluateModule(source: ByteArray, filename: String) {
        require(source.isNotEmpty() && source.size <= 8 * 1024 * 1024) { "Invalid module source" }
        require(filename.matches(Regex("^[A-Za-z0-9._/-]+\\.mjs$"))) { "Invalid module filename" }
        submit {
            QuickJsNative.evaluateModule(
                nativeHandle,
                source,
                filename.encodeToByteArray(),
                limits.maxExecutionWallTimeMs,
            )
        }
    }

    suspend fun callJson(functionName: String, argumentsJson: String): String {
        require(functionName.matches(Regex("^[A-Za-z_$][A-Za-z0-9_$]{0,127}$"))) { "Invalid function name" }
        require(argumentsJson.encodeToByteArray().size <= 8 * 1024 * 1024) { "Arguments exceed host limit" }
        val output = submit {
            QuickJsNative.callJson(
                nativeHandle,
                functionName.encodeToByteArray(),
                argumentsJson.encodeToByteArray(),
                limits.maxExecutionWallTimeMs,
            )
        }
        return output.decodeToString(throwOnInvalidSequence = true)
    }

    private suspend fun <T> submit(operation: () -> T): T = suspendCancellableCoroutine { continuation ->
        if (closed.get()) {
            continuation.resumeWith(Result.failure(QuickJsRuntimeException(QuickJsRuntimeError.CLOSED)))
            return@suspendCancellableCoroutine
        }
        val started = AtomicBoolean(false)
        val future = executor.submit {
            if (!continuation.isActive) return@submit
            started.set(true)
            if (!continuation.isActive) {
                QuickJsNative.cancel(nativeHandle)
                return@submit
            }
            try {
                val result = operation()
                if (continuation.isActive) continuation.resumeWith(Result.success(result))
            } catch (error: QuickJsNativeException) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(mapNative(error)))
            } catch (error: Throwable) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(error))
            }
        }
        continuation.invokeOnCancellation {
            if (started.get()) QuickJsNative.cancel(nativeHandle)
            future.cancel(false)
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
            executor.submit { QuickJsNative.close(nativeHandle) }.get()
        } finally {
            executor.shutdownNow()
        }
    }

    private companion object {
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
    external fun evaluateModule(
        nativeHandle: Long,
        source: ByteArray,
        filename: ByteArray,
        wallTimeMillis: Int,
    )
    external fun callJson(
        nativeHandle: Long,
        functionName: ByteArray,
        argumentsJson: ByteArray,
        wallTimeMillis: Int,
    ): ByteArray
    external fun cancel(nativeHandle: Long)
    external fun close(nativeHandle: Long)
}
