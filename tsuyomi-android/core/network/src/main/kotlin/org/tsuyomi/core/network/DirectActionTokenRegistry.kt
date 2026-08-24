/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.network

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class DirectActionBinding(
    val sourceId: String,
    val remoteBookId: String,
    val reconciliationId: String,
    val packageDigest: String,
    val packageVersion: String,
    val capabilitySetFingerprint: String,
    val registryGeneration: Long,
    val ownerGeneration: Long,
)

class DirectActionTokenRegistry {
    private data class Record(
        val binding: DirectActionBinding,
        val onAccept: suspend () -> Boolean,
        var terminal: Boolean = false,
    )

    private val records = ConcurrentHashMap<String, Record>()
    private val mutex = Mutex()

    fun mint(binding: DirectActionBinding, onAccept: suspend () -> Boolean): String {
        val token = UUID.randomUUID().toString()
        check(records.putIfAbsent(token, Record(binding, onAccept)) == null)
        return token
    }

    suspend fun accept(sourceId: String, remoteBookId: String, token: String): DirectActionBinding = mutex.withLock {
        val record = records[token] ?: throw HostNetworkException(HostNetworkError.CANCELLED)
        if (record.terminal || record.binding.sourceId != sourceId || record.binding.remoteBookId != remoteBookId) {
            throw HostNetworkException(HostNetworkError.CANCELLED)
        }
        record.terminal = true
        if (!record.onAccept()) {
            records.remove(token)
            throw HostNetworkException(HostNetworkError.CANCELLED)
        }
        records.remove(token)
        record.binding
    }

    suspend fun revoke(token: String): DirectActionBinding? = mutex.withLock {
        val record = records.remove(token) ?: return@withLock null
        record.terminal = true
        record.binding
    }

}
