/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

internal data class DisplayWriteTicket(
    val field: String,
    val key: String,
    val generation: Long,
)

@Stable
internal class DisplayWriteArbiter private constructor(
    initialGenerations: Map<String, Long>,
    initialFailures: Map<String, String>,
    initialFailureSequence: Int,
) {
    constructor() : this(emptyMap(), emptyMap(), 0)

    private var generations by mutableStateOf(initialGenerations)
    private var failures by mutableStateOf(initialFailures)

    var failureSequence by mutableIntStateOf(initialFailureSequence)
        private set

    val retryKey: String?
        get() = failures.values.firstOrNull()

    val hasFailure: Boolean
        get() = failures.isNotEmpty()

    fun begin(key: String): DisplayWriteTicket {
        val field = key.substringBefore(':')
        val generation = (generations[field] ?: 0L) + 1L
        check(generation > 0L) { "Display write generation exhausted for $field" }
        generations = generations + (field to generation)
        return DisplayWriteTicket(field, key, generation)
    }

    fun succeed(ticket: DisplayWriteTicket) {
        if (isCurrent(ticket)) failures = failures - ticket.field
    }

    fun fail(ticket: DisplayWriteTicket) {
        if (isCurrent(ticket)) {
            failures = failures + (ticket.field to ticket.key)
            failureSequence += 1
        }
    }

    fun acknowledge() {
        val field = failures.keys.firstOrNull() ?: return
        failures = failures - field
        if (failures.isNotEmpty()) failureSequence += 1
    }

    private fun isCurrent(ticket: DisplayWriteTicket): Boolean = generations[ticket.field] == ticket.generation

    internal fun savedValues(): List<String> = buildList {
        add(failureSequence.toString())
        add(generations.size.toString())
        generations.forEach { (field, generation) ->
            add(field)
            add(generation.toString())
        }
        add(failures.size.toString())
        failures.forEach { (field, key) ->
            add(field)
            add(key)
        }
    }

    internal companion object {
        fun restore(values: List<String>): DisplayWriteArbiter {
            var index = 0
            fun next(): String = values[index++]
            val failureSequence = next().toInt()
            val generations = buildMap {
                repeat(next().toInt()) { put(next(), next().toLong()) }
            }
            val failures = buildMap {
                repeat(next().toInt()) { put(next(), next()) }
            }
            check(index == values.size) { "Unexpected display write state payload" }
            return DisplayWriteArbiter(generations, failures, failureSequence)
        }
    }
}

@Composable
internal fun rememberDisplayWriteArbiter(): DisplayWriteArbiter {
    val saver = remember {
        listSaver<DisplayWriteArbiter, String>(
            save = { it.savedValues() },
            restore = DisplayWriteArbiter::restore,
        )
    }
    return rememberSaveable(saver = saver) { DisplayWriteArbiter() }
}
