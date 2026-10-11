/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android.publication

/** Publication-only JNI surface, separate from the extension runtime limits/API. */
internal object MobiQuickJsNative {
    init {
        System.loadLibrary("tsuyomi_quickjs")
    }

    // Os.fcntlInt is public only from API 30; libc keeps provider pipes cancellable on API 29.
    external fun makeInputNonBlocking(fd: Int): Int
    external fun createBookRuntime(): Long
    external fun prepareBookOperation(nativeHandle: Long, wallTimeMillis: Int)
    external fun evaluateBookModule(nativeHandle: Long, source: ByteArray, filename: ByteArray)
    external fun callBookJsonToFd(
        nativeHandle: Long,
        functionName: ByteArray,
        argumentsJson: ByteArray,
        outputFd: Int,
        maxOutputBytes: Int,
    )
    external fun callBookInputToFd(
        nativeHandle: Long,
        functionName: ByteArray,
        inputFd: Int,
        outputFd: Int,
        maxInputBytes: Int,
        maxOutputBytes: Int,
    )
    external fun callBookBinaryToFd(
        nativeHandle: Long,
        functionName: ByteArray,
        resourceId: ByteArray,
        outputFd: Int,
        maxOutputBytes: Int,
    )
    external fun cancelBookRuntime(nativeHandle: Long)
    external fun closeBookRuntime(nativeHandle: Long)
}
