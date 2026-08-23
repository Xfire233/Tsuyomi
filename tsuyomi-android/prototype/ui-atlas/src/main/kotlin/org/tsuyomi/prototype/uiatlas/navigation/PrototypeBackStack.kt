/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.navigation

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import org.tsuyomi.prototype.uiatlas.model.AtlasFamily
import org.tsuyomi.prototype.uiatlas.model.AtlasRoute

val PrototypeRouteStackSaver: Saver<List<AtlasRoute>, Any> = listSaver(
    save = { stack -> stack.map(AtlasRoute::name) },
    restore = { names -> names.mapNotNull { name -> runCatching { AtlasRoute.valueOf(name) }.getOrNull() } },
)

fun initialPrototypeStack(initial: AtlasRoute, family: AtlasFamily): List<AtlasRoute> {
    val root = AtlasRoute.rootOf(family)
    return if (initial.family == family && initial != root) listOf(root, initial) else listOf(root)
}
