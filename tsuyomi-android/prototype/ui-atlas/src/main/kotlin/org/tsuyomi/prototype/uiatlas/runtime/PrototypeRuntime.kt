/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.runtime

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.staticCompositionLocalOf
import org.tsuyomi.prototype.uiatlas.review.ReviewRepository

class PrototypeRuntime(
    context: Context,
    val persistent: Boolean,
) {
    val repository = PrototypeRepository(context.applicationContext, persistent)
    val scenarios = PrototypeScenarioController(repository)
    val reviews = ReviewRepository(context.applicationContext, persistent)
}

val LocalPrototypeRuntime = staticCompositionLocalOf<PrototypeRuntime> {
    error("PrototypeRuntime is not installed")
}

@Composable
fun prototypeRepository(): PrototypeRepository {
    val runtime = LocalPrototypeRuntime.current
    val snapshot by runtime.repository.snapshot.collectAsStateWithLifecycle()
    snapshot.revision
    return runtime.repository
}
